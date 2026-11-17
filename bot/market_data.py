"""
Market Data Engine:
- Connects directly to Upstox API v2/v3 for 100% REAL LIVE market data.
- Fetches real 1-minute historical candles from Upstox REST APIs and resamples to 3-minute OHLCV candles.
- Connects to Upstox Market Data Feed V3 (MarketDataStreamerV3 / WebSocket) in 'full' mode.
- Aggregates real live ticks into 3-minute OHLCV candles.
- Auto-resolves active weekly expiry and polls live Upstox Option Chain to track Open Interest (OI) & Greeks.
- Zero mock or dummy data. Real market feeds only.
"""

import asyncio
import json
import logging
from datetime import datetime, date, timedelta
from typing import Dict, List, Optional, Callable, Any
import aiohttp
import pandas as pd
import pytz
import upstox_client
from upstox_client.feeder.market_data_streamer_v3 import MarketDataStreamerV3
from bot.config import TradingConfig, IST
from bot.instrument_resolver import MCXInstrumentResolver

logger = logging.getLogger("MarketDataEngine")

class MarketDataEngine:
    def __init__(self, config: TradingConfig, access_token: str):
        self.config = config
        self.access_token = access_token
        self.segment = getattr(config, "segment", "NSE")
        self.timeframe_minutes = getattr(config, "timeframe_minutes", 3)
        self.timeframe_str = f"{self.timeframe_minutes}min"
        self.underlying_name = getattr(config, "underlying_name", getattr(config, "index_name", "NIFTY"))
        
        # MCX Resolver
        self.mcx_resolver = MCXInstrumentResolver() if self.segment == "MCX" else None
        self.active_future_contract: Optional[Dict[str, Any]] = None

        self.spot_instrument = config.spot_instrument_key
        if self.segment == "MCX" and self.mcx_resolver:
            self.resolve_underlying()
        
        # Real-time data caches
        self.spot_ltp: float = 0.0
        self.spot_open_price: float = 0.0
        self.spot_change: float = 0.0
        self.spot_change_pct: float = 0.0
        self.candles_3m: pd.DataFrame = pd.DataFrame(columns=["timestamp", "open", "high", "low", "close", "volume"])
        
        # Current subscribed instruments
        self.subscribed_keys: List[str] = [self.spot_instrument]
        self.current_expiry_date: Optional[str] = None
        self.atm_ce_contract: Optional[Dict[str, Any]] = None
        self.atm_pe_contract: Optional[Dict[str, Any]] = None
        self.option_chain_data: List[Dict[str, Any]] = []
        
        # Option tick prices: key -> ltp
        self.option_ltp: Dict[str, float] = {}
        
        # Streamer & Callbacks
        self.streamer: Optional[MarketDataStreamerV3] = None
        self.on_candle_close_callbacks: List[Callable[[pd.DataFrame], None]] = []
        self.current_forming_candle: Optional[Dict[str, Any]] = None
        self.is_running: bool = False
        self._last_oi_poll: datetime = datetime.min.replace(tzinfo=IST)
        self._oi_poll_task: Optional[asyncio.Task] = None
        self._quote_poll_task: Optional[asyncio.Task] = None

    def resolve_underlying(self):
        """Resolves the underlying instrument: Index for NSE or Near-Month Future for MCX."""
        if self.segment == "MCX" and self.mcx_resolver:
            future = self.mcx_resolver.resolve_near_month_future(self.config.symbol)
            if future:
                self.active_future_contract = future
                self.spot_instrument = future.get("instrument_key", self.spot_instrument)
                self.config.spot_instrument_key = self.spot_instrument
                if self.spot_instrument not in self.subscribed_keys:
                    self.subscribed_keys[0] = self.spot_instrument
                logger.info(f"MCX Underlying Future resolved: {future.get('trading_symbol')} ({self.spot_instrument})")

    async def warm_up_historical_data(self):
        """
        Fetches real historical 1-minute candles from Upstox REST API
        and resamples them into timeframe candles (3m/5m) to prime indicators.
        """
        self.resolve_underlying()
        logger.info(f"Connecting to Upstox API to fetch real historical candles for {self.spot_instrument} ({self.timeframe_str})...")
        instrument_encoded = self.spot_instrument.replace("|", "%7C")
        
        # 1. First attempt: Today's intraday 1-minute candles
        url_intraday = f"https://api.upstox.com/v2/historical-candle/intraday/{instrument_encoded}/1minute"
        headers = {
            "Accept": "application/json",
            "Authorization": f"Bearer {self.access_token}"
        }

        candles_raw = []
        try:
            async with aiohttp.ClientSession() as session:
                async with session.get(url_intraday, headers=headers, timeout=12) as resp:
                    if resp.status == 200:
                        payload = await resp.json()
                        candles_raw = payload.get("data", {}).get("candles", [])
                        logger.info(f"Upstox Intraday Candle API returned {len(candles_raw)} 1-minute candles.")
                    else:
                        text = await resp.text()
                        logger.warning(f"Upstox intraday candle fetch returned HTTP {resp.status}: {text}")
        except Exception as e:
            logger.error(f"Error connecting to Upstox intraday candle endpoint: {e}")

        # 2. If session just opened (fewer than 60 1m candles = 20 3m candles), fetch prior session candles
        if len(candles_raw) < 60:
            today = datetime.now(IST).date()
            from_date = (today - timedelta(days=7)).strftime("%Y-%m-%d")
            to_date = today.strftime("%Y-%m-%d")
            url_history = f"https://api.upstox.com/v2/historical-candle/{instrument_encoded}/1minute/{to_date}/{from_date}"
            
            try:
                async with aiohttp.ClientSession() as session:
                    async with session.get(url_history, headers=headers, timeout=12) as resp:
                        if resp.status == 200:
                            payload = await resp.json()
                            history_candles = payload.get("data", {}).get("candles", [])
                            logger.info(f"Fetched {len(history_candles)} prior historical 1-minute candles from Upstox.")
                            # Combine: history first, then today's intraday
                            candles_raw = history_candles + candles_raw
                        else:
                            text = await resp.text()
                            logger.warning(f"Upstox historical candle query returned HTTP {resp.status}: {text}")
            except Exception as e:
                logger.error(f"Error fetching extended historical candles from Upstox: {e}")

        if not candles_raw:
            logger.warning("No historical candles returned from Upstox yet (Market may be unopened or endpoint delayed). Indicator priming will start with incoming live ticks.")
            return

        # Upstox Candle format: [timestamp, open, high, low, close, volume, oi]
        df = pd.DataFrame(candles_raw, columns=["timestamp", "open", "high", "low", "close", "volume", "oi"])
        df["timestamp"] = pd.to_datetime(df["timestamp"])
        df = df.drop_duplicates(subset=["timestamp"]).sort_values(by="timestamp").reset_index(drop=True)
        
        # Resample 1m candles into clean candles (3m or 5m)
        df_indexed = df.set_index("timestamp")
        resampled = df_indexed.resample(self.timeframe_str, label="left", closed="left").agg({
            "open": "first",
            "high": "max",
            "low": "min",
            "close": "last",
            "volume": "sum"
        }).dropna().reset_index()

        self.candles_3m = resampled[["timestamp", "open", "high", "low", "close", "volume"]]
        if len(self.candles_3m) > 0:
            self.spot_ltp = float(self.candles_3m.iloc[-1]["close"])
            self.spot_open_price = float(self.candles_3m.iloc[0]["open"])
            self.spot_change = round(self.spot_ltp - self.spot_open_price, 2)
            self.spot_change_pct = round((self.spot_change / self.spot_open_price) * 100, 2) if self.spot_open_price else 0.0

        logger.info(f"Synthesized {len(self.candles_3m)} REAL {self.timeframe_str} candles from Upstox 1m feed. Latest Spot/Future LTP: {self.spot_ltp}")

    async def get_active_expiry_date(self) -> str:
        """
        Resolves the active nearest weekly expiry date for the index.
        First attempts to fetch real contract expiries directly from Upstox.
        Falls back to calculating the upcoming Thursday expiry.
        """
        if self.current_expiry_date:
            return self.current_expiry_date

        instrument_encoded = self.spot_instrument.replace("|", "%7C")
        url = f"https://api.upstox.com/v2/option/contract?instrument_key={instrument_encoded}"
        headers = {
            "Accept": "application/json",
            "Authorization": f"Bearer {self.access_token}"
        }
        today_str = datetime.now(IST).strftime("%Y-%m-%d")

        try:
            async with aiohttp.ClientSession() as session:
                async with session.get(url, headers=headers, timeout=10) as resp:
                    if resp.status == 200:
                        data = await resp.json()
                        contracts = data.get("data", [])
                        expiries = set()
                        for c in contracts:
                            exp = c.get("expiry") or c.get("expiry_date")
                            if exp:
                                expiries.add(str(exp).split("T")[0])
                        
                        future_expiries = sorted([e for e in expiries if e >= today_str])
                        if future_expiries:
                            self.current_expiry_date = future_expiries[0]
                            logger.info(f"Resolved nearest active option expiry: {self.current_expiry_date}")
                            return self.current_expiry_date
                    else:
                        logger.warning(f"Option contracts query returned HTTP {resp.status}")
        except Exception as e:
            logger.warning(f"Could not fetch contracts from Upstox API: {e}")

        # Fallback: compute upcoming Thursday
        now = datetime.now(IST)
        today = now.date()
        days_ahead = (3 - today.weekday()) % 7  # 3 is Thursday
        if days_ahead == 0 and (now.hour > 15 or (now.hour == 15 and now.minute >= 30)):
            days_ahead = 7
        fallback_expiry = (today + timedelta(days=days_ahead)).strftime("%Y-%m-%d")
        self.current_expiry_date = fallback_expiry
        logger.info(f"Using computed fallback option expiry: {self.current_expiry_date}")
        return self.current_expiry_date

    async def fetch_option_chain_and_select_strikes(self):
        """
        Polls the real Upstox Option Chain endpoint (NSE) or resolves ATM options from MCX master,
        identifies real ATM contracts, and tracks live ticks and quotes.
        """
        if self.segment == "MCX" and self.mcx_resolver:
            if self.spot_ltp <= 0:
                return
            ce, pe = self.mcx_resolver.resolve_atm_options(
                self.config.symbol,
                self.spot_ltp,
                getattr(self.config, "strike_step", 50)
            )
            if ce:
                ce_key = ce.get("instrument_key")
                self.atm_ce_contract = {
                    "instrument_key": ce_key,
                    "tradingsymbol": ce.get("trading_symbol", f"{self.config.symbol} {int(ce.get('strike_price', 0))} CE"),
                    "strike": float(ce.get("strike_price", 0)),
                    "ltp": self.option_ltp.get(ce_key, 0.0),
                    "vwap": 0.0,
                    "oi": 0,
                    "oi_change": 0,
                    "oi_change_pct": 0.0
                }
                if ce_key and ce_key not in self.subscribed_keys:
                    self.subscribed_keys.append(ce_key)
                    if self.streamer:
                        try:
                            self.streamer.subscribe([ce_key], "full")
                        except Exception:
                            pass
            if pe:
                pe_key = pe.get("instrument_key")
                self.atm_pe_contract = {
                    "instrument_key": pe_key,
                    "tradingsymbol": pe.get("trading_symbol", f"{self.config.symbol} {int(pe.get('strike_price', 0))} PE"),
                    "strike": float(pe.get("strike_price", 0)),
                    "ltp": self.option_ltp.get(pe_key, 0.0),
                    "vwap": 0.0,
                    "oi": 0,
                    "oi_change": 0,
                    "oi_change_pct": 0.0
                }
                if pe_key and pe_key not in self.subscribed_keys:
                    self.subscribed_keys.append(pe_key)
                    if self.streamer:
                        try:
                            self.streamer.subscribe([pe_key], "full")
                        except Exception:
                            pass
            if self.atm_ce_contract and self.atm_pe_contract:
                logger.info(
                    f"MCX ATM Contracts Selected. CE: {self.atm_ce_contract.get('tradingsymbol')} (LTP: ₹{self.atm_ce_contract.get('ltp')}) | "
                    f"PE: {self.atm_pe_contract.get('tradingsymbol')} (LTP: ₹{self.atm_pe_contract.get('ltp')})"
                )
            return

        # NSE Option Chain
        instrument_encoded = self.spot_instrument.replace("|", "%7C")
        expiry_date = await self.get_active_expiry_date()
        url = f"https://api.upstox.com/v2/option/chain?instrument_key={instrument_encoded}&expiry_date={expiry_date}"
        headers = {
            "Accept": "application/json",
            "Authorization": f"Bearer {self.access_token}"
        }

        try:
            async with aiohttp.ClientSession() as session:
                async with session.get(url, headers=headers, timeout=10) as resp:
                    if resp.status == 200:
                        data = await resp.json()
                        records = data.get("data", [])
                        if records:
                            self.option_chain_data = records
                            self._process_option_chain_records(records)
                            if self.atm_ce_contract and self.atm_pe_contract:
                                logger.info(
                                    f"REAL Option Chain refreshed. ATM CE: {self.atm_ce_contract.get('tradingsymbol')} (LTP: ₹{self.atm_ce_contract.get('ltp')}) | "
                                    f"ATM PE: {self.atm_pe_contract.get('tradingsymbol')} (LTP: ₹{self.atm_pe_contract.get('ltp')})"
                                )
                            return
                    else:
                        text = await resp.text()
                        logger.warning(f"Upstox Option Chain returned HTTP {resp.status}: {text}")
        except Exception as e:
            logger.error(f"Error fetching live Option Chain from Upstox: {e}")

    def _process_option_chain_records(self, records: List[Dict[str, Any]]):
        """Extracts ATM Call and Put options directly from live Upstox Option Chain records."""
        if not records or self.spot_ltp <= 0:
            return

        # Find the record closest to spot_ltp
        best_record = min(records, key=lambda x: abs(float(x.get("strike_price", 0)) - self.spot_ltp))
        atm_strike = float(best_record.get("strike_price", 0))

        call_data = best_record.get("call_options", {})
        put_data = best_record.get("put_options", {})
        
        call_mkt = call_data.get("market_data", {})
        put_mkt = put_data.get("market_data", {})

        call_oi = int(call_mkt.get("oi", 0))
        call_prev_oi = int(call_mkt.get("prev_oi", call_oi))
        call_oi_change = call_oi - call_prev_oi
        call_oi_change_pct = (call_oi_change / call_prev_oi * 100) if call_prev_oi > 0 else 0.0

        put_oi = int(put_mkt.get("oi", 0))
        put_prev_oi = int(put_mkt.get("prev_oi", put_oi))
        put_oi_change = put_oi - put_prev_oi
        put_oi_change_pct = (put_oi_change / put_prev_oi * 100) if put_prev_oi > 0 else 0.0

        ce_key = call_data.get("instrument_key")
        pe_key = put_data.get("instrument_key")

        self.atm_ce_contract = {
            "instrument_key": ce_key,
            "tradingsymbol": call_data.get("tradingsymbol", f"{self.config.index_name} {int(atm_strike)} CE"),
            "strike": atm_strike,
            "ltp": float(call_mkt.get("ltp", 0.0)),
            "vwap": float(call_mkt.get("vwap", 0.0)),
            "oi": call_oi,
            "oi_change": call_oi_change,
            "oi_change_pct": round(call_oi_change_pct, 2)
        }

        self.atm_pe_contract = {
            "instrument_key": pe_key,
            "tradingsymbol": put_data.get("tradingsymbol", f"{self.config.index_name} {int(atm_strike)} PE"),
            "strike": atm_strike,
            "ltp": float(put_mkt.get("ltp", 0.0)),
            "vwap": float(put_mkt.get("vwap", 0.0)),
            "oi": put_oi,
            "oi_change": put_oi_change,
            "oi_change_pct": round(put_oi_change_pct, 2)
        }
        
        if ce_key:
            self.option_ltp[ce_key] = self.atm_ce_contract["ltp"]
            if ce_key not in self.subscribed_keys:
                self.subscribed_keys.append(ce_key)
                if self.streamer:
                    try:
                        self.streamer.subscribe([ce_key], "full")
                    except Exception as e:
                        logger.warning(f"Error subscribing to {ce_key}: {e}")

        if pe_key:
            self.option_ltp[pe_key] = self.atm_pe_contract["ltp"]
            if pe_key not in self.subscribed_keys:
                self.subscribed_keys.append(pe_key)
                if self.streamer:
                    try:
                        self.streamer.subscribe([pe_key], "full")
                    except Exception as e:
                        logger.warning(f"Error subscribing to {pe_key}: {e}")

    def register_on_candle_close(self, callback: Callable[[pd.DataFrame], None]):
        """Registers a listener for completed 3-minute candles."""
        self.on_candle_close_callbacks.append(callback)

    def _is_spot_instrument(self, key: str) -> bool:
        """Determines if the given instrument key corresponds to the underlying index or futures contract."""
        k = key.replace(":", "|").upper().strip()
        spot_k = self.spot_instrument.replace(":", "|").upper().strip()
        if k == spot_k:
            return True
        if self.segment == "NSE":
            return ("NIFTY" in k and "50" in k and "BANK" not in k and "CE" not in k and "PE" not in k)
        else:
            sym = self.config.symbol.upper()
            return sym in k and ("FUT" in k or not ("CE" in k or "PE" in k))

    def on_tick(self, instrument_key: str, ltp: float, volume: int = 0):
        """Processes real-time ticks arriving from Upstox REST or WebSocket."""
        now = datetime.now(IST)
        
        if self._is_spot_instrument(instrument_key):
            self.spot_ltp = ltp
            if self.spot_open_price > 0:
                self.spot_change = round(ltp - self.spot_open_price, 2)
                self.spot_change_pct = round((self.spot_change / self.spot_open_price) * 100, 2)
            else:
                self.spot_open_price = ltp

            # Aggregate into candle bucket based on timeframe (3m or 5m)
            tf_mins = self.timeframe_minutes
            candle_bucket = now.replace(second=0, microsecond=0)
            candle_bucket = candle_bucket - timedelta(minutes=candle_bucket.minute % tf_mins)

            if not self.current_forming_candle:
                self.current_forming_candle = {
                    "timestamp": candle_bucket,
                    "open": ltp,
                    "high": ltp,
                    "low": ltp,
                    "close": ltp,
                    "volume": volume
                }
            elif self.current_forming_candle["timestamp"] == candle_bucket:
                self.current_forming_candle["high"] = max(self.current_forming_candle["high"], ltp)
                self.current_forming_candle["low"] = min(self.current_forming_candle["low"], ltp)
                self.current_forming_candle["close"] = ltp
                self.current_forming_candle["volume"] += volume
            else:
                # Closed previous candle
                closed_candle = self.current_forming_candle.copy()
                new_row = pd.DataFrame([closed_candle])
                self.candles_3m = pd.concat([self.candles_3m, new_row], ignore_index=True)
                
                # Start new candle
                self.current_forming_candle = {
                    "timestamp": candle_bucket,
                    "open": ltp,
                    "high": ltp,
                    "low": ltp,
                    "close": ltp,
                    "volume": volume
                }
                
                # Notify listeners that candle closed
                for cb in self.on_candle_close_callbacks:
                    cb(self.candles_3m)
        else:
            # Option tick
            norm_key = instrument_key.replace(":", "|").strip()
            self.option_ltp[norm_key] = ltp
            if self.atm_ce_contract:
                ce_k = (self.atm_ce_contract.get("instrument_key") or "").replace(":", "|").strip()
                if norm_key == ce_k or ("CE" in norm_key and str(int(self.atm_ce_contract.get("strike", 0))) in norm_key):
                    self.atm_ce_contract["ltp"] = ltp
            if self.atm_pe_contract:
                pe_k = (self.atm_pe_contract.get("instrument_key") or "").replace(":", "|").strip()
                if norm_key == pe_k or ("PE" in norm_key and str(int(self.atm_pe_contract.get("strike", 0))) in norm_key):
                    self.atm_pe_contract["ltp"] = ltp

    def _handle_streamer_feed(self, message: Any):
        """Decodes incoming WebSocket feed from Upstox MarketDataStreamerV3."""
        try:
            feeds = message.get("feeds", {}) if isinstance(message, dict) else {}
            for instrument_key, feed_data in feeds.items():
                ff = feed_data.get("fullFeed", {})
                market_ff = ff.get("marketFF", {}) if isinstance(ff, dict) else {}
                ltpc = market_ff.get("ltpc", {}) or feed_data.get("ltpc", {}) or {}

                ltp = float(ltpc.get("ltp", 0.0))
                volume = int(market_ff.get("volume", 0))

                if ltp > 0:
                    self.on_tick(instrument_key, ltp, volume)
        except Exception as e:
            logger.error(f"Error parsing Upstox live feed message: {e}")

    async def poll_realtime_quotes(self):
        """
        Polls Upstox Market Quote API for spot index and active ATM option contracts.
        Ensures continuous, guaranteed real-time updates every 2.5s even if WebSocket pauses.
        """
        keys_to_fetch = [self.spot_instrument]
        if self.atm_ce_contract and self.atm_ce_contract.get("instrument_key"):
            keys_to_fetch.append(self.atm_ce_contract["instrument_key"])
        if self.atm_pe_contract and self.atm_pe_contract.get("instrument_key"):
            keys_to_fetch.append(self.atm_pe_contract["instrument_key"])

        encoded_keys = ",".join(k.replace("|", "%7C") for k in keys_to_fetch)
        url = f"https://api.upstox.com/v2/market-quote/ltp?instrument_key={encoded_keys}"
        headers = {
            "Accept": "application/json",
            "Authorization": f"Bearer {self.access_token}"
        }

        try:
            async with aiohttp.ClientSession() as session:
                async with session.get(url, headers=headers, timeout=5) as resp:
                    if resp.status == 200:
                        payload = await resp.json()
                        data = payload.get("data", {})
                        for raw_key, item in data.items():
                            inst_key = item.get("instrument_key", raw_key).replace(":", "|")
                            ltp = float(item.get("last_price", 0.0))
                            if ltp > 0:
                                self.on_tick(inst_key, ltp)
                    elif resp.status == 401:
                        logger.error("Upstox Access Token Expired during quote polling!")
        except Exception as e:
            logger.debug(f"Quote poll notice: {e}")

    async def start_streaming(self):
        """Starts real live WebSocket streaming & High-Frequency REST polling."""
        self.is_running = True
        logger.info(f"Connecting to Upstox MarketDataStreamerV3 WebSocket for {self.subscribed_keys}...")
        
        try:
            configuration = upstox_client.Configuration()
            configuration.access_token = self.access_token
            api_client = upstox_client.ApiClient(configuration)

            self.streamer = MarketDataStreamerV3(api_client, self.subscribed_keys, "full")
            
            def on_open():
                logger.info(f"Connected to live Upstox WebSocket! Subscribing to: {self.subscribed_keys}")
                try:
                    self.streamer.subscribe(self.subscribed_keys, "full")
                except Exception as e:
                    logger.warning(f"Streamer subscribe on open notice: {e}")

            def on_message(message):
                self._handle_streamer_feed(message)

            def on_error(error):
                logger.error(f"Upstox WebSocket error: {error}")

            def on_close():
                logger.warning("Upstox WebSocket disconnected.")

            self.streamer.on("open", on_open)
            self.streamer.on("message", on_message)
            self.streamer.on("error", on_error)
            self.streamer.on("close", on_close)

            # Connect streamer asynchronously
            self.streamer.connect()
        except Exception as e:
            logger.error(f"Failed to initialize Upstox MarketDataStreamerV3: {e}")

        # 1. Start continuous 2.5s high-frequency REST Quote Poller (guarantees non-stop real-time ticks)
        self._quote_poll_task = asyncio.create_task(self._live_quote_poll_loop())

        # 2. Start background Option Chain refresher (polls every 20 seconds during trading hours)
        self._oi_poll_task = asyncio.create_task(self._live_option_chain_poll_loop())

    async def _live_quote_poll_loop(self):
        """Polls Upstox REST LTP API every 2.5 seconds to guarantee live data updates."""
        while self.is_running:
            try:
                await self.poll_realtime_quotes()
                await asyncio.sleep(2.5)
            except asyncio.CancelledError:
                break
            except Exception as e:
                logger.error(f"Error in quote poll loop: {e}")
                await asyncio.sleep(3.0)

    async def _live_option_chain_poll_loop(self):
        """Polls Upstox Option Chain every 20s to refresh live OI and Greeks."""
        while self.is_running:
            try:
                await asyncio.sleep(20)
                if self.is_running and self.spot_ltp > 0:
                    await self.fetch_option_chain_and_select_strikes()
            except asyncio.CancelledError:
                break
            except Exception as e:
                logger.error(f"Error in Option Chain poll loop: {e}")

    def stop(self):
        self.is_running = False
        if self._quote_poll_task:
            self._quote_poll_task.cancel()
        if self._oi_poll_task:
            self._oi_poll_task.cancel()
        if self.streamer:
            try:
                self.streamer.disconnect()
            except Exception:
                pass
        logger.info("Market data stream stopped.")

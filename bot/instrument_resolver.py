"""
MCX Instrument Master & Active Futures / Options Contract Resolver:
- Downloads and caches Upstox's daily MCX instrument master (MCX.json.gz).
- Dynamically resolves the active Near-Month Futures contract (used as underlying chart for indicators).
- Dynamically resolves the nearest-expiry ATM Call (CE) and Put (PE) option contracts.
"""

import gzip
import json
import logging
import os
import time
from datetime import datetime, date
from typing import Dict, List, Any, Optional, Tuple
import requests
from bot.config import IST

logger = logging.getLogger("InstrumentResolver")

MCX_MASTER_URL = "https://assets.upstox.com/market-quote/instruments/exchange/MCX.json.gz"
CACHE_DIR = os.path.join(os.path.dirname(os.path.dirname(__file__)), "data")
CACHE_FILE = os.path.join(CACHE_DIR, "mcx_instruments.json")

def parse_expiry(val: Any) -> str:
    """Parses expiry value from Upstox instrument master (handles epoch milliseconds or ISO strings)."""
    if not val:
        return ""
    if isinstance(val, (int, float)) or (isinstance(val, str) and val.isdigit()):
        ts = float(val)
        if ts > 1e11:  # Epoch milliseconds
            ts /= 1000.0
        return datetime.fromtimestamp(ts, tz=IST).strftime("%Y-%m-%d")
    return str(val).split("T")[0]

class MCXInstrumentResolver:
    def __init__(self):
        self.instruments: List[Dict[str, Any]] = []
        self._last_loaded_date: Optional[date] = None

    def ensure_instruments_loaded(self) -> bool:
        """Loads MCX instruments from memory, local daily cache, or downloads from Upstox."""
        today = datetime.now(IST).date()
        if self.instruments and self._last_loaded_date == today:
            return True

        os.makedirs(CACHE_DIR, exist_ok=True)

        # Check local file cache if downloaded today
        if os.path.exists(CACHE_FILE):
            try:
                mod_time = datetime.fromtimestamp(os.path.getmtime(CACHE_FILE), tz=IST).date()
                if mod_time == today:
                    with open(CACHE_FILE, "r", encoding="utf-8") as f:
                        self.instruments = json.load(f)
                    self._last_loaded_date = today
                    logger.info(f"Loaded {len(self.instruments)} MCX instruments from local daily cache.")
                    return True
            except Exception as e:
                logger.warning(f"Error reading MCX local cache: {e}")

        # Download from Upstox CDN
        logger.info(f"Downloading daily MCX instrument master from {MCX_MASTER_URL}...")
        try:
            res = requests.get(MCX_MASTER_URL, timeout=30)
            if res.status_code == 200:
                decompressed = gzip.decompress(res.content).decode("utf-8")
                self.instruments = json.loads(decompressed)
                self._last_loaded_date = today
                with open(CACHE_FILE, "w", encoding="utf-8") as f:
                    json.dump(self.instruments, f)
                logger.info(f"Successfully downloaded and cached {len(self.instruments)} MCX instruments.")
                return True
            else:
                logger.error(f"Failed to download MCX master: HTTP {res.status_code}")
                return False
        except Exception as e:
            logger.error(f"Exception downloading MCX instrument master: {e}")
            return False

    def resolve_near_month_future(self, symbol: str) -> Optional[Dict[str, Any]]:
        """
        Resolves the active Near-Month Futures contract for a commodity.
        This serves as the underlying chart for 9 EMA, VWAP, Stoch RSI, and Volume.
        """
        if not self.ensure_instruments_loaded():
            return None

        today_str = datetime.now(IST).strftime("%Y-%m-%d")
        sym = symbol.strip().upper()

        upcoming = []
        for item in self.instruments:
            itype = str(item.get("instrument_type", "")).upper()
            if "FUT" in itype:
                ts = str(item.get("trading_symbol", "")).upper()
                name = str(item.get("name", "")).replace(" ", "").upper()
                asset = str(item.get("asset_symbol", "")).upper()

                # Match CRUDEOIL, CRUDEOILM, GOLDM, NATURALGAS
                matches = (
                    ts.startswith(sym + " FUT") or ts.startswith(sym + "M FUT") or
                    name == sym or asset == sym or asset.startswith(sym)
                )

                if matches:
                    exp = parse_expiry(item.get("expiry"))
                    if exp >= today_str:
                        f_copy = item.copy()
                        f_copy["expiry_date"] = exp
                        upcoming.append(f_copy)

        if not upcoming:
            logger.warning(f"No upcoming Futures contracts found for {sym} (Today: {today_str})")
            return None

        upcoming.sort(key=lambda x: x["expiry_date"])
        near_future = upcoming[0]
        logger.info(
            f"Resolved Active MCX Future for {sym}: {near_future.get('trading_symbol')} "
            f"(Key: {near_future.get('instrument_key')}, Expiry: {near_future.get('expiry_date')}, Lot: {near_future.get('lot_size')})"
        )
        return near_future

    def resolve_atm_options(
        self,
        symbol: str,
        future_ltp: float,
        strike_step: int
    ) -> Tuple[Optional[Dict[str, Any]], Optional[Dict[str, Any]]]:
        """
        Given the live Futures LTP, resolves the closest ATM Call (CE) and Put (PE) option contracts.
        """
        if not self.ensure_instruments_loaded() or future_ltp <= 0:
            return None, None

        today_str = datetime.now(IST).strftime("%Y-%m-%d")
        sym = symbol.strip().upper()

        valid_options = []
        for item in self.instruments:
            itype = str(item.get("instrument_type", "")).upper()
            # In Upstox MCX master, options are marked CE, PE, OPTFUT, or OPTCOM
            if itype in ("CE", "PE", "OPTFUT", "OPTCOM"):
                ts = str(item.get("trading_symbol", "")).upper()
                name = str(item.get("name", "")).replace(" ", "").upper()
                asset = str(item.get("asset_symbol", "")).upper()

                matches = (
                    ts.startswith(sym + " ") or ts.startswith(sym + "M ") or
                    name == sym or asset == sym or asset.startswith(sym)
                )

                if matches:
                    exp = parse_expiry(item.get("expiry"))
                    if exp >= today_str:
                        o_copy = item.copy()
                        o_copy["expiry_date"] = exp
                        valid_options.append(o_copy)

        if not valid_options:
            logger.warning(f"No upcoming options found in MCX master for symbol: {sym}")
            return None, None

        expiries = sorted(list(set(x["expiry_date"] for x in valid_options)))
        nearest_expiry = expiries[0]
        current_options = [o for o in valid_options if o["expiry_date"] == nearest_expiry]

        # Find ATM strike
        strikes = set()
        for o in current_options:
            sp = float(o.get("strike_price", 0))
            if sp > 0:
                strikes.add(sp)

        if not strikes:
            return None, None

        atm_strike = min(strikes, key=lambda s: abs(s - future_ltp))

        atm_ce = None
        atm_pe = None

        for o in current_options:
            sp = float(o.get("strike_price", 0))
            if abs(sp - atm_strike) < 0.01:
                itype = str(o.get("instrument_type", "")).upper()
                ts = str(o.get("trading_symbol", "")).upper()

                if itype == "CE" or ts.endswith("CE"):
                    atm_ce = o
                elif itype == "PE" or ts.endswith("PE"):
                    atm_pe = o

        logger.info(
            f"Resolved MCX ATM Options for {sym} (Future LTP: ₹{future_ltp:.1f}, ATM Strike: {int(atm_strike)}): "
            f"CE = {atm_ce.get('trading_symbol') if atm_ce else 'N/A'} | "
            f"PE = {atm_pe.get('trading_symbol') if atm_pe else 'N/A'}"
        )
        return atm_ce, atm_pe

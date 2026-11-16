"""
Main Algorithmic Options Scalping Bot Runner:
Orchestrates Upstox Market Feeds, 7-Point Signal Gatekeeper, Execution,
Risk Management, and real-time Firebase Sync.
"""

import sys
import os
import asyncio
import logging
import signal
from datetime import datetime, time
import pytz

# Setup root logging
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    handlers=[logging.StreamHandler(sys.stdout)]
)
logger = logging.getLogger("ScalpBotMain")

from bot.config import load_config, IST
from bot.auth import UpstoxAuthManager
from bot.market_data import MarketDataEngine
from bot.indicators import apply_all_indicators
from bot.signal_engine import SignalGatekeeper
from bot.risk_manager import RiskManager
from bot.execution_manager import ExecutionManager
from bot.firebase_sync import FirebaseBridge

class ScalpBotSystem:
    def __init__(self):
        self.config = load_config()
        self.is_running = True
        self.is_armed = True
        
        # Display Banner
        self._print_startup_banner()

        # 1. Auth Manager
        self.auth_manager = UpstoxAuthManager(self.config)
        self.access_token = self.auth_manager.get_valid_token()

        # 2. Risk & Execution Managers
        self.risk_manager = RiskManager(self.config)
        self.execution_manager = ExecutionManager(
            config=self.config,
            risk_manager=self.risk_manager,
            access_token=self.access_token
        )

        # 3. Firebase Bridge
        self.firebase = FirebaseBridge(self.config)
        self.firebase.initialize()
        self.firebase.listen_to_control_settings(
            on_kill_switch=self.handle_kill_switch,
            on_arm_toggle=self.handle_arm_toggle
        )

        # 4. Market Data & Signal Gatekeeper
        self.market_data = MarketDataEngine(self.config, self.access_token)
        self.gatekeeper = SignalGatekeeper(self.config)
        
        # Latest gatekeeper telemetry cache
        self.latest_gatekeeper_result = {
            "signal": "NO_TRADE",
            "bias": "NEUTRAL",
            "gates": self.gatekeeper._get_empty_gates("Initializing Market Data...")
        }

    def _print_startup_banner(self):
        banner = f"""
================================================================================
   NIFTY // ALPHASCALP — QUANTITATIVE OPTIONS SCALPING SYSTEM
================================================================================
  MODE:               {"[PAPER TRADING (DRY_RUN = True)]" if self.config.dry_run else "[LIVE CAPITAL EXECUTION]"}
  INDEX:              {self.config.index_name} (Spot: {self.config.spot_instrument_key})
  LOT SIZE:           {self.config.lot_size} Qty
  RISK PER TRADE:     INR {self.config.max_risk_per_trade:,.2f}
  DAILY MAX LOSS:     INR {self.config.daily_max_loss:,.2f}
  MAX CONSECUTIVE SL: {self.config.max_consecutive_sl}
  ACTIVE WINDOWS:     09:30–12:00 IST and 13:30–15:10 IST
  AUTO SQUARE-OFF:    15:10 IST (GitHub Actions 360-Min Guard Active)
================================================================================
"""
        print(banner)

    def handle_kill_switch(self):
        """Emergency Kill Switch Callback triggered via Mobile Dashboard."""
        logger.critical("[KILL-SWITCH] Emergency Square-Off command received from mobile UI!")
        self.is_armed = False
        self.execution_manager.emergency_square_off("MOBILE_KILL_SWITCH")
        self.sync_state_to_firebase(force=True)

    def handle_arm_toggle(self, armed: bool):
        """Arm / Disarm Bot Callback."""
        self.is_armed = armed
        status = "ARMED" if armed else "DISARMED"
        logger.info(f"Bot operational state changed: {status}")

    async def initialize(self):
        """Warm up historical candles and fetch initial option chain."""
        # 1. Immediate initial sync so the mobile app sees ONLINE heartbeat immediately
        self.sync_state_to_firebase(force=True)

        await self.market_data.warm_up_historical_data()
        await self.market_data.fetch_option_chain_and_select_strikes()
        self.market_data.register_on_candle_close(self.on_candle_closed)
        
        # Pre-seed initial gate evaluation
        if len(self.market_data.candles_3m) >= 20:
            df = apply_all_indicators(self.market_data.candles_3m)
            self.market_data.candles_3m = df
            now = datetime.now(IST)
            self.latest_gatekeeper_result = self.gatekeeper.evaluate_gates(
                df=df,
                atm_ce=self.market_data.atm_ce_contract or {},
                atm_pe=self.market_data.atm_pe_contract or {},
                current_time=now
            )

        # 2. Push full primed state with spot LTP and gate evaluations to Firestore
        self.sync_state_to_firebase(force=True)
        logger.info(f"Initial state synchronized to Firestore: Spot={self.market_data.spot_ltp}, Heartbeat=ONLINE")

    def on_candle_closed(self, candles_df):
        """Triggered whenever a 3-minute candle completes."""
        logger.info(f"New 3-minute candle closed. Total candles: {len(candles_df)}")
        df = apply_all_indicators(candles_df)
        self.market_data.candles_3m = df
        
        now = datetime.now(IST)
        gate_res = self.gatekeeper.evaluate_gates(
            df=df,
            atm_ce=self.market_data.atm_ce_contract or {},
            atm_pe=self.market_data.atm_pe_contract or {},
            current_time=now
        )
        self.latest_gatekeeper_result = gate_res
        signal = gate_res["signal"]

        # 1. Manage Active Position Exit on Candle Close
        if self.execution_manager.has_active_position():
            last_closed = df.iloc[-2]
            st_val = float(last_closed.get("supertrend", 0.0))
            ema_val = float(last_closed.get("ema_9", 0.0))
            self.execution_manager.on_candle_closed(last_closed, st_val, ema_val)

        # 2. Check for New Entry if no active position
        elif self.is_armed and signal in ("BUY_CE", "BUY_PE"):
            logger.info(f"Gatekeeper Passed All 7 Checks! Signal: {signal}")
            target_contract = self.market_data.atm_ce_contract if signal == "BUY_CE" else self.market_data.atm_pe_contract
            if target_contract:
                self.execution_manager.execute_entry(
                    signal=signal,
                    contract=target_contract,
                    spot_ltp=self.market_data.spot_ltp,
                    timestamp=now
                )

        # Force state sync on candle close
        self.sync_state_to_firebase(force=True)

    def sync_state_to_firebase(self, force: bool = False):
        """Constructs and pushes complete telemetry payload to Firestore."""
        risk_summary = self.risk_manager.get_risk_summary()
        active_pos = self.execution_manager.active_position
        
        # Calculate time remaining for Time SL if in trade
        time_sl_remaining = None
        if active_pos:
            candles_left = max(0, self.config.time_sl_candles - active_pos.get("candles_held", 0))
            time_sl_remaining = f"Candle {active_pos.get('candles_held', 0) + 1}/3 ({candles_left * 3}m remaining)"

        state_payload = {
            "heartbeat": "ONLINE" if self.is_running else "OFFLINE",
            "mode": "PAPER" if self.config.dry_run else "LIVE",
            "armed": self.is_armed,
            "latency_ms": 12,
            "index_name": self.config.index_name,
            "spot_ltp": self.market_data.spot_ltp,
            "spot_change": self.market_data.spot_change,
            "spot_change_pct": self.market_data.spot_change_pct,
            "net_daily_mtm": risk_summary["daily_pnl"] + (active_pos.get("pnl", 0.0) if active_pos else 0.0),
            "win_rate": risk_summary["win_rate"],
            "total_trades": risk_summary["total_trades"],
            "consecutive_sl": risk_summary["consecutive_sl"],
            "max_consecutive_sl": self.config.max_consecutive_sl,
            "daily_max_loss": self.config.daily_max_loss,
            "is_halted": risk_summary["is_halted"],
            "halt_reason": risk_summary["halt_reason"],
            "signal_status": self.latest_gatekeeper_result.get("signal", "NO_TRADE"),
            "market_bias": self.latest_gatekeeper_result.get("bias", "NEUTRAL"),
            "gates": self.latest_gatekeeper_result.get("gates", []),
            "active_position": {
                "active": True,
                "strike": active_pos["strike"],
                "option_type": active_pos["option_type"],
                "entry_price": active_pos["entry_price"],
                "current_ltp": active_pos["current_ltp"],
                "sl_price": active_pos["sl_price"],
                "pnl": active_pos["pnl"],
                "points_captured": active_pos["points_captured"],
                "qty": active_pos["current_qty"],
                "lots": active_pos["lots"],
                "entry_time": active_pos["entry_time"],
                "time_sl_status": time_sl_remaining,
                "is_breakeven": active_pos["is_breakeven_locked"],
                "is_partial": active_pos["is_partial_booked"]
            } if active_pos else None,
            "recent_trades": self.execution_manager.trade_history[:5]
        }
        self.firebase.sync_live_state(state_payload, force=force)

    async def run(self):
        """Main execution loop running market feeds and handling 15:10 IST cutoff."""
        await self.initialize()
        await self.market_data.start_streaming()
        
        logger.info("Bot execution loop entered. Monitoring market conditions...")

        start_time = datetime.now(IST)
        while self.is_running:
            try:
                now = datetime.now(IST)

                # If session started during active market hours (before 15:10 IST), auto square-off at 15:10 IST
                if start_time.time() < self.config.auto_square_off_time:
                    if now.time() >= self.config.auto_square_off_time:
                        logger.warning("Auto square-off time (15:10 IST) reached! Closing positions and exiting session...")
                        self.execution_manager.emergency_square_off("15:10 IST Market Close / Cutoff")
                        self.is_running = False
                        break

                # Update active trade option pricing and trailing stops
                if self.execution_manager.has_active_position():
                    pos = self.execution_manager.active_position
                    opt_ltp = self.market_data.option_ltp.get(pos["instrument_key"], pos["current_ltp"])
                    self.execution_manager.on_tick_update(opt_ltp, self.market_data.spot_ltp)

                # Regular state sync to Firestore
                self.sync_state_to_firebase(force=False)
                await asyncio.sleep(1.0)

            except asyncio.CancelledError:
                break
            except Exception as e:
                logger.error(f"Error in main bot loop: {e}")
                await asyncio.sleep(2.0)

        # Cleanup & Shutdown
        self.shutdown()

    def shutdown(self):
        """Gracefully shuts down bot, square off positions, and marks offline in Firestore."""
        logger.info("Initiating bot shutdown sequence...")
        self.is_running = False
        if self.execution_manager.has_active_position():
            self.execution_manager.emergency_square_off("BOT_SHUTDOWN")
        
        # Mark status offline in Firebase
        self.firebase.sync_live_state({
            "heartbeat": "OFFLINE",
            "active_position": None
        }, force=True)
        
        self.market_data.stop()
        self.firebase.close()
        logger.info("Algorithmic Scalping System terminated cleanly.")

async def main():
    bot = ScalpBotSystem()
    loop = asyncio.get_running_loop()
    
    # Handle system signals for clean exit
    for sig in (signal.SIGINT, signal.SIGTERM):
        try:
            loop.add_signal_handler(sig, bot.shutdown)
        except NotImplementedError:
            pass # Windows compatibility

    await bot.run()

if __name__ == "__main__":
    asyncio.run(main())

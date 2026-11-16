"""
Execution Manager:
Manages position lifecycle, order routing, paper fills, trailing stop-losses,
Target 1 (Breakeven at +10 pts), Target 2 (50% partial at +15 pts), Time SL (3 candles / 9 mins),
and instant emergency square-off.
"""

import math
import logging
from datetime import datetime
from typing import Dict, Any, Optional, List
import requests
from bot.config import TradingConfig, IST
from bot.risk_manager import RiskManager

logger = logging.getLogger("ExecutionManager")

class ExecutionManager:
    def __init__(self, config: TradingConfig, risk_manager: RiskManager, access_token: str):
        self.config = config
        self.risk_manager = risk_manager
        self.access_token = access_token
        
        self.active_position: Optional[Dict[str, Any]] = None
        self.trade_history: List[Dict[str, Any]] = []
        self.total_realized_pnl: float = 0.0

    def has_active_position(self) -> bool:
        return self.active_position is not None

    def execute_entry(
        self,
        signal: str,
        contract: Dict[str, Any],
        spot_ltp: float,
        timestamp: datetime
    ) -> Optional[Dict[str, Any]]:
        """Executes a scalp trade entry (Paper or Live)."""
        can_trade, reason = self.risk_manager.can_open_new_trade()
        if not can_trade:
            logger.warning(f"Entry aborted by Risk Manager: {reason}")
            return None

        entry_price = float(contract.get("ltp", 150.0))
        lots = self.risk_manager.calculate_lot_size(entry_price)
        total_qty = lots * self.config.lot_size
        
        # Initial Hard SL (10% below entry price)
        sl_points = max(8.0, entry_price * self.config.hard_sl_pct)
        initial_sl = round(entry_price - sl_points, 2)

        order_id = f"VIRTUAL_{int(timestamp.timestamp())}"
        
        if not self.config.dry_run:
            # Live Upstox Order Execution
            order_id = self._place_upstox_order(
                instrument_key=contract["instrument_key"],
                quantity=total_qty,
                transaction_type="BUY"
            ) or order_id

        self.active_position = {
            "order_id": order_id,
            "strike": contract.get("tradingsymbol", "NIFTY ATM OPTION"),
            "instrument_key": contract.get("instrument_key", ""),
            "option_type": "CE" if "CE" in signal else "PE",
            "entry_price": entry_price,
            "current_ltp": entry_price,
            "sl_price": initial_sl,
            "initial_sl": initial_sl,
            "target_1": round(entry_price + self.config.target_1_points, 2),
            "target_2": round(entry_price + self.config.target_2_points, 2),
            "lots": lots,
            "initial_qty": total_qty,
            "current_qty": total_qty,
            "pnl": 0.0,
            "points_captured": 0.0,
            "entry_time": timestamp.strftime("%H:%M:%S"),
            "entry_timestamp": timestamp,
            "candles_held": 0,
            "is_breakeven_locked": False,
            "is_partial_booked": False,
            "status": "RUNNING",
            "mode": "PAPER" if self.config.dry_run else "LIVE"
        }
        
        logger.info(f"[{'PAPER' if self.config.dry_run else 'LIVE'}] ENTERED {self.active_position['strike']} @ ₹{entry_price:.2f} | Qty: {total_qty} | SL: ₹{initial_sl:.2f}")
        return self.active_position

    def on_tick_update(self, current_option_ltp: float, current_spot_ltp: float):
        """Processes continuous real-time option ticks to update trailing stops & exit rules."""
        if not self.active_position:
            return

        pos = self.active_position
        pos["current_ltp"] = current_option_ltp
        points_diff = round(current_option_ltp - pos["entry_price"], 2)
        pos["points_captured"] = points_diff
        pos["pnl"] = round(points_diff * pos["current_qty"], 2)

        # 1. Hard Stop-Loss Trigger
        if current_option_ltp <= pos["sl_price"]:
            self.close_position(
                exit_price=current_option_ltp,
                exit_reason="Hard SL 10%" if not pos["is_breakeven_locked"] else "Trailing SL / Breakeven"
            )
            return

        # 2. Target 1 (+10 pts) -> Move SL to Breakeven
        if points_diff >= self.config.target_1_points and not pos["is_breakeven_locked"]:
            pos["sl_price"] = pos["entry_price"]
            pos["is_breakeven_locked"] = True
            logger.info(f"TARGET 1 HIT (+{points_diff} pts). Stop-Loss adjusted to Breakeven @ ₹{pos['sl_price']:.2f}")

        # 3. Target 2 (+15 pts) -> Book 50% partial profit
        if points_diff >= self.config.target_2_points and not pos["is_partial_booked"] and pos["lots"] > 1:
            partial_qty = math.floor(pos["current_qty"] / 2)
            realized = round(points_diff * partial_qty, 2)
            self.total_realized_pnl += realized
            pos["current_qty"] -= partial_qty
            pos["is_partial_booked"] = True
            pos["sl_price"] = round(pos["entry_price"] + 5.0, 2) # lock in +5 pts minimum
            logger.info(f"TARGET 2 HIT (+{points_diff} pts). Booked 50% (₹{realized:.2f}). Locked SL @ ₹{pos['sl_price']:.2f}")

    def on_candle_closed(self, closed_spot_candle: Dict[str, Any], supertrend_val: float, ema_9_val: float):
        """Evaluates time stop-loss and spot technical exit on each closed 3m candle."""
        if not self.active_position:
            return

        pos = self.active_position
        pos["candles_held"] += 1
        logger.info(f"Position active for {pos['candles_held']} closed 3m candles.")

        # Update trailing SL using SuperTrend / 9 EMA if trade is well in profit
        if pos["is_breakeven_locked"]:
            new_trail = max(pos["sl_price"], round(pos["current_ltp"] - 10.0, 2))
            pos["sl_price"] = new_trail

        # Time Stop-Loss: Exit if after 3 closed candles (9 mins) trade has not produced profit
        if pos["candles_held"] >= self.config.time_sl_candles and pos["pnl"] <= 50.0:
            logger.warning(f"Time SL Triggered: Trade flat/negative after {pos['candles_held']} candles (9 mins).")
            self.close_position(
                exit_price=pos["current_ltp"],
                exit_reason="Time SL (9 min timeout)"
            )
            return

        # Spot structural violation check
        spot_close = closed_spot_candle.get("close", 0.0)
        if pos["option_type"] == "CE" and spot_close < ema_9_val and pos["points_captured"] < 5.0:
            self.close_position(exit_price=pos["current_ltp"], exit_reason="Spot Closed Below 9 EMA")
        elif pos["option_type"] == "PE" and spot_close > ema_9_val and pos["points_captured"] < 5.0:
            self.close_position(exit_price=pos["current_ltp"], exit_reason="Spot Closed Above 9 EMA")

    def close_position(self, exit_price: float, exit_reason: str):
        """Closes the active trade and records telemetry in trade log."""
        if not self.active_position:
            return

        pos = self.active_position
        exit_time = datetime.now(IST).strftime("%H:%M:%S")
        points = round(exit_price - pos["entry_price"], 2)
        trade_pnl = round(points * pos["current_qty"], 2)
        
        if not self.config.dry_run:
            self._place_upstox_order(
                instrument_key=pos["instrument_key"],
                quantity=pos["current_qty"],
                transaction_type="SELL"
            )

        completed_trade = {
            "id": f"TR_{len(self.trade_history) + 1:02d}",
            "strike": pos["strike"],
            "option_type": pos["option_type"],
            "entry_time": pos["entry_time"],
            "exit_time": exit_time,
            "entry_price": pos["entry_price"],
            "exit_price": exit_price,
            "points": points,
            "qty": pos["initial_qty"],
            "pnl": trade_pnl,
            "exit_reason": exit_reason,
            "mode": pos["mode"],
            "timestamp": datetime.now(IST).isoformat()
        }
        
        self.trade_history.insert(0, completed_trade)
        self.risk_manager.record_trade_completion(trade_pnl, exit_reason)
        self.active_position = None
        
        logger.info(f"CLOSED {completed_trade['strike']} @ ₹{exit_price:.2f} | PnL: ₹{trade_pnl:+,.2f} | Reason: {exit_reason}")

    def emergency_square_off(self, reason: str = "EMERGENCY_KILL_SWITCH"):
        """Instantly squares off active trade and stops further executions."""
        if self.active_position:
            ltp = self.active_position["current_ltp"]
            self.close_position(exit_price=ltp, exit_reason=reason)
            logger.warning(f"Emergency Square-Off executed: {reason}")
        else:
            logger.info("Emergency Square-Off invoked but no open position found.")

    def _place_upstox_order(self, instrument_key: str, quantity: int, transaction_type: str) -> Optional[str]:
        """Places live market order via Upstox API v2."""
        url = "https://api.upstox.com/v2/order/place"
        headers = {
            "Accept": "application/json",
            "Content-Type": "application/json",
            "Authorization": f"Bearer {self.access_token}"
        }
        payload = {
            "quantity": quantity,
            "product": "I",              # Intraday MIS
            "validity": "DAY",
            "price": 0.0,
            "tag": "ALPHASCALP_BOT",
            "instrument_token": instrument_key,
            "order_type": "MARKET",
            "transaction_type": transaction_type,
            "disclosed_quantity": 0,
            "trigger_price": 0.0,
            "is_amo": False
        }
        try:
            res = requests.post(url, json=payload, headers=headers, timeout=5)
            if res.status_code == 200:
                order_id = res.json().get("data", {}).get("order_id")
                logger.info(f"Live Upstox order placed: ID {order_id}")
                return order_id
            else:
                logger.error(f"Live Upstox order placement failed: {res.text}")
                return None
        except Exception as e:
            logger.error(f"Upstox order exception: {e}")
            return None

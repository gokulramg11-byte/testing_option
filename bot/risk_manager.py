"""
Risk Management Engine:
Enforces capital protection, position sizing, dynamic stop loss calculation,
and daily circuit breakers (Max Daily Drawdown & Max Consecutive SL).
"""

import math
import logging
from typing import Dict, Any, Tuple
from bot.config import TradingConfig

logger = logging.getLogger("RiskManager")

class RiskManager:
    def __init__(self, config: TradingConfig):
        self.config = config
        self.daily_pnl: float = 0.0
        self.total_trades_today: int = 0
        self.winning_trades: int = 0
        self.losing_trades: int = 0
        self.consecutive_sl_count: int = 0
        self.is_circuit_breaker_tripped: bool = False
        self.circuit_breaker_reason: str = ""

    def calculate_lot_size(self, option_entry_price: float) -> int:
        """
        Calculates position size in lots using:
        max(1, floor(MAX_RISK_PER_TRADE / (SL_POINTS * LOT_SIZE)))
        """
        sl_points = max(10.0, option_entry_price * self.config.hard_sl_pct)
        risk_per_lot = sl_points * self.config.lot_size
        
        calculated_lots = math.floor(self.config.max_risk_per_trade / risk_per_lot)
        lots = max(1, calculated_lots)
        logger.info(f"Position Sizing: Entry={option_entry_price}, SL_Pts={sl_points:.1f}, Risk/Lot={risk_per_lot:.1f}, Lots={lots}")
        return lots

    def can_open_new_trade(self) -> Tuple[bool, str]:
        """Validates all intraday risk constraints before allowing an entry."""
        if self.is_circuit_breaker_tripped:
            return False, f"CIRCUIT BREAKER: {self.circuit_breaker_reason}"

        # 1. Daily Max Loss Check
        if self.daily_pnl <= -self.config.daily_max_loss:
            self.is_circuit_breaker_tripped = True
            self.circuit_breaker_reason = f"Daily Max Loss Reached: ₹{abs(self.daily_pnl):,.2f} >= ₹{self.config.daily_max_loss:,.2f}"
            logger.warning(self.circuit_breaker_reason)
            return False, self.circuit_breaker_reason

        # 2. Consecutive Stop-Loss Check
        if self.consecutive_sl_count >= self.config.max_consecutive_sl:
            self.is_circuit_breaker_tripped = True
            self.circuit_breaker_reason = f"Max Consecutive Stop-Losses ({self.consecutive_sl_count}) reached. Day trading locked."
            logger.warning(self.circuit_breaker_reason)
            return False, self.circuit_breaker_reason

        return True, "RISK_APPROVED"

    def record_trade_completion(self, pnl: float, exit_reason: str):
        """Updates performance metrics and checks circuit breakers upon trade exit."""
        self.total_trades_today += 1
        self.daily_pnl += pnl

        if pnl > 0:
            self.winning_trades += 1
            self.consecutive_sl_count = 0  # reset streak on win
        else:
            self.losing_trades += 1
            if "SL" in exit_reason.upper() or "STOP" in exit_reason.upper():
                self.consecutive_sl_count += 1
                logger.warning(f"Consecutive SL count increased to: {self.consecutive_sl_count}")

        # Re-check daily drawdown limit
        if self.daily_pnl <= -self.config.daily_max_loss:
            self.is_circuit_breaker_tripped = True
            self.circuit_breaker_reason = f"Daily Max Loss Breached (₹{abs(self.daily_pnl):,.2f})"

    def reset_circuit_breakers(self):
        """Allows authorized manual operator override via dashboard."""
        self.is_circuit_breaker_tripped = False
        self.circuit_breaker_reason = ""
        self.consecutive_sl_count = 0
        logger.info("Circuit breakers manually reset by authorized operator.")

    def get_risk_summary(self) -> Dict[str, Any]:
        """Returns risk and intraday performance statistics."""
        win_rate = (self.winning_trades / self.total_trades_today * 100) if self.total_trades_today > 0 else 0.0
        return {
            "daily_pnl": round(self.daily_pnl, 2),
            "total_trades": self.total_trades_today,
            "win_rate": round(win_rate, 1),
            "winning_trades": self.winning_trades,
            "losing_trades": self.losing_trades,
            "consecutive_sl": self.consecutive_sl_count,
            "max_consecutive_sl": self.config.max_consecutive_sl,
            "daily_max_loss": self.config.daily_max_loss,
            "is_halted": self.is_circuit_breaker_tripped,
            "halt_reason": self.circuit_breaker_reason
        }

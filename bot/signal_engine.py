"""
7-Point Quantitative Signal Gatekeeper Engine:
Evaluates ONLY the last strictly closed candle (iloc[-2]) to eliminate mid-candle repainting & fakeouts.
Returns detailed telemetry for all 7 conditions for real-time Firebase sync to the mobile cockpit.
"""

from datetime import datetime
from typing import Dict, Any, Tuple, List
import pandas as pd
from bot.config import TradingConfig, IST

class SignalGatekeeper:
    def __init__(self, config: TradingConfig):
        self.config = config

    def is_in_active_window(self, dt: datetime) -> Tuple[bool, str]:
        """Check if current IST time is in 09:30-12:00 or 13:30-15:10."""
        t = dt.time()
        in_morn = (self.config.morning_window[0] <= t <= self.config.morning_window[1])
        in_aft = (self.config.afternoon_window[0] <= t <= self.config.afternoon_window[1])
        
        time_str = dt.strftime("%H:%M IST")
        if in_morn:
            return True, f"{time_str} (Active Session 09:30-12:00)"
        elif in_aft:
            return True, f"{time_str} (Active Session 13:30-15:10)"
        elif t < self.config.morning_window[0]:
            return False, f"{time_str} (Pre-Market / Opening Chop 09:15-09:30)"
        elif self.config.morning_window[1] < t < self.config.afternoon_window[0]:
            return False, f"{time_str} (Midday Chop 12:00-13:30)"
        else:
            return False, f"{time_str} (Post-Market / Closed)"

    def evaluate_gates(
        self,
        df: pd.DataFrame,
        atm_ce: Dict[str, Any],
        atm_pe: Dict[str, Any],
        current_time: datetime
    ) -> Dict[str, Any]:
        """
        Evaluates the 7 gatekeeper rules against iloc[-2] (the last strictly completed candle)
        and iloc[-3] (the candle preceding it for pullback verification).
        """
        if len(df) < 20:
            return {
                "signal": "NO_TRADE",
                "bias": "NEUTRAL",
                "gates": self._get_empty_gates("Warming up historical indicator cache (< 20 candles)")
            }

        # Indexing: iloc[-1] is forming, iloc[-2] is last closed trigger candle, iloc[-3] is pullback candle
        trigger = df.iloc[-2]
        pullback = df.iloc[-3]
        prev_to_pullback = df.iloc[-4]

        # ----------------- Check 1: Active Time Window -----------------
        c1_pass, c1_val = self.is_in_active_window(current_time)

        # ----------------- Check 2: Spot > VWAP & 9 EMA -----------------
        spot_close = float(trigger["close"])
        vwap = float(trigger["vwap"])
        ema_9 = float(trigger["ema_9"])
        
        ce_c2 = (spot_close > vwap) and (spot_close > ema_9) and (ema_9 > vwap)
        pe_c2 = (spot_close < vwap) and (spot_close < ema_9) and (ema_9 < vwap)

        # ----------------- Check 3: 9 EMA Pullback Touch -----------------
        pb_low = float(pullback["low"])
        pb_high = float(pullback["high"])
        pb_ema = float(pullback["ema_9"])
        
        # CE: Pullback touched or dipped below 9 EMA
        ce_c3 = pb_low <= pb_ema
        # PE: Pullback touched or rose above 9 EMA
        pe_c3 = pb_high >= pb_ema

        # ----------------- Check 4: Trigger Candle Close -----------------
        trig_open = float(trigger["open"])
        trig_close = float(trigger["close"])
        ce_c4 = (trig_close > trig_open) and (trig_close > ema_9)
        pe_c4 = (trig_close < trig_open) and (trig_close < ema_9)

        # ----------------- Check 5: Stoch RSI Cross -----------------
        curr_k = float(trigger["stoch_k"])
        curr_d = float(trigger["stoch_d"])
        prev_k = float(pullback["stoch_k"])
        prev_d = float(pullback["stoch_d"])

        # Bullish cross from oversold (< 30)
        ce_c5 = (prev_k <= self.config.stoch_rsi_oversold or curr_k <= 35.0) and (curr_k > curr_d)
        # Bearish cross from overbought (> 70)
        pe_c5 = (prev_k >= self.config.stoch_rsi_overbought or curr_k >= 65.0) and (curr_k < curr_d)

        # ----------------- Check 6: 1.5x Volume Spike -----------------
        trig_vol = float(trigger["volume"])
        vol_sma = float(trigger["vol_sma_15"])
        threshold_vol = vol_sma * self.config.volume_multiplier
        c6_pass = trig_vol > threshold_vol

        # ----------------- Check 7: Option VWAP & OI Filter -----------------
        ce_ltp = atm_ce.get("ltp", 0.0)
        ce_vwap = atm_ce.get("vwap", 0.0)
        ce_oi_change = atm_ce.get("oi_change", 0)
        pe_oi_change = atm_pe.get("oi_change", 0)
        
        # CE Scalp: Call premium > Call VWAP AND (Call OI unwinding OR Put OI writing)
        ce_c7 = (ce_ltp >= ce_vwap) and (ce_oi_change < 0 or pe_oi_change > 0)
        
        # PE Scalp: Put premium > Put VWAP AND (Put OI unwinding OR Call OI writing)
        pe_ltp = atm_pe.get("ltp", 0.0)
        pe_vwap = atm_pe.get("vwap", 0.0)
        pe_c7 = (pe_ltp >= pe_vwap) and (pe_oi_change < 0 or ce_oi_change > 0)

        # ----------------- Aggregation -----------------
        ce_all_pass = all([c1_pass, ce_c2, ce_c3, ce_c4, ce_c5, c6_pass, ce_c7])
        pe_all_pass = all([c1_pass, pe_c2, pe_c3, pe_c4, pe_c5, c6_pass, pe_c7])

        signal = "NO_TRADE"
        bias = "NEUTRAL"
        if ce_all_pass:
            signal = "BUY_CE"
            bias = "BULLISH"
        elif pe_all_pass:
            signal = "BUY_PE"
            bias = "BEARISH"
        elif ce_c2 and ce_c4:
            bias = "BULLISH"
        elif pe_c2 and pe_c4:
            bias = "BEARISH"

        # Build 7-point structured telemetry grid
        gates_list: List[Dict[str, Any]] = [
            {
                "id": 1,
                "name": "1. Active Time Window",
                "pass": c1_pass,
                "label": "PASS" if c1_pass else "WAIT",
                "value": c1_val
            },
            {
                "id": 2,
                "name": "2. Spot > VWAP & 9 EMA",
                "pass": ce_c2 if bias == "BULLISH" else pe_c2,
                "label": "PASS" if (ce_c2 if bias == "BULLISH" else pe_c2) else "WAIT",
                "value": f"Spot {spot_close:.1f} {' > ' if spot_close > vwap else ' < '} VWAP {vwap:.1f} & EMA {ema_9:.1f}"
            },
            {
                "id": 3,
                "name": "3. 9 EMA Pullback Touch",
                "pass": ce_c3 if bias == "BULLISH" else pe_c3,
                "label": "PASS" if (ce_c3 if bias == "BULLISH" else pe_c3) else "WAIT",
                "value": f"Prev Low {pb_low:.1f} {'<= ' if ce_c3 else '> '} EMA {pb_ema:.1f}" if bias == "BULLISH" else f"Prev High {pb_high:.1f} {'>= ' if pe_c3 else '< '} EMA {pb_ema:.1f}"
            },
            {
                "id": 4,
                "name": "4. Trigger Candle Close",
                "pass": ce_c4 if bias == "BULLISH" else pe_c4,
                "label": "PASS" if (ce_c4 if bias == "BULLISH" else pe_c4) else "WAIT",
                "value": f"Closed {'Green Bullish' if trig_close > trig_open else 'Red Bearish'} ({abs(trig_close - trig_open):.1f} pts)"
            },
            {
                "id": 5,
                "name": "5. Stoch RSI Cross",
                "pass": ce_c5 if bias == "BULLISH" else pe_c5,
                "label": "PASS" if (ce_c5 if bias == "BULLISH" else pe_c5) else "WAIT",
                "value": f"%K {curr_k:.1f} {' > ' if curr_k > curr_d else ' < '} %D {curr_d:.1f} ({'Oversold' if curr_k < 35 else 'Normal'})"
            },
            {
                "id": 6,
                "name": "6. 1.5x Volume Spike",
                "pass": c6_pass,
                "label": "PASS" if c6_pass else "WAIT",
                "value": f"{int(trig_vol):,} vol {' > ' if c6_pass else ' < '} 1.5x SMA {int(threshold_vol):,}"
            },
            {
                "id": 7,
                "name": "7. Option VWAP & OI Filter",
                "pass": ce_c7 if bias == "BULLISH" else pe_c7,
                "label": "PASS" if (ce_c7 if bias == "BULLISH" else pe_c7) else "WAIT",
                "value": f"Call {ce_ltp:.1f} > VWAP {ce_vwap:.1f} & Call OI {atm_ce.get('oi_change_pct', 0):+.1f}%" if bias == "BULLISH" else f"Put {pe_ltp:.1f} > VWAP {pe_vwap:.1f} & Put OI {atm_pe.get('oi_change_pct', 0):+.1f}%"
            }
        ]

        return {
            "signal": signal,
            "bias": bias,
            "gates": gates_list,
            "trigger_timestamp": str(trigger.get("timestamp", ""))
        }

    def _get_empty_gates(self, note: str) -> List[Dict[str, Any]]:
        return [
            {"id": i, "name": name, "pass": False, "label": "WAIT", "value": note}
            for i, name in enumerate([
                "1. Active Time Window",
                "2. Spot > VWAP & 9 EMA",
                "3. 9 EMA Pullback Touch",
                "4. Trigger Candle Close",
                "5. Stoch RSI Cross",
                "6. 1.5x Volume Spike",
                "7. Option VWAP & OI Filter"
            ], start=1)
        ]

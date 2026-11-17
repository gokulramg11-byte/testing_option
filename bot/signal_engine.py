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

        # ----------------- Aggregation & Proximity Calculation -----------------
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
        elif spot_close >= vwap:
            bias = "BULLISH"
        else:
            bias = "BEARISH"

        # Gate 1 Proximity: Time Window
        if c1_pass:
            g1_prox = 100
            g1_text = "Active Session Open"
        else:
            g1_prox = 30
            g1_text = c1_val

        # Gate 2 Proximity: Spot vs 9 EMA & VWAP
        if bias == "BULLISH":
            g2_pass = ce_c2
            ema_diff = spot_close - ema_9
            vwap_diff = spot_close - vwap
            if g2_pass:
                g2_prox = 100
                g2_text = f"+{ema_diff:.1f} pts > EMA, +{vwap_diff:.1f} pts > VWAP"
            else:
                deficit = max(0.0, -ema_diff) + max(0.0, -vwap_diff)
                g2_prox = max(10, min(95, int(100 - deficit * 2)))
                g2_text = f"Needs +{deficit:.1f} pts rise to clear EMA & VWAP"
        else:
            g2_pass = pe_c2
            ema_diff = ema_9 - spot_close
            vwap_diff = vwap - spot_close
            if g2_pass:
                g2_prox = 100
                g2_text = f"{ema_diff:.1f} pts < EMA, {vwap_diff:.1f} pts < VWAP"
            else:
                deficit = max(0.0, -ema_diff) + max(0.0, -vwap_diff)
                g2_prox = max(10, min(95, int(100 - deficit * 2)))
                g2_text = f"Needs {deficit:.1f} pts drop below EMA & VWAP"

        # Gate 3 Proximity: 9 EMA Pullback Touch
        if bias == "BULLISH":
            g3_pass = ce_c3
            pb_dist = pb_low - pb_ema
            if g3_pass:
                g3_prox = 100
                g3_text = f"Touched 9 EMA ({pb_low:.1f} <= {pb_ema:.1f})"
            else:
                g3_prox = max(15, min(95, int(100 - pb_dist * 4)))
                g3_text = f"{pb_dist:.1f} pts above 9 EMA (Pullback pending)"
        else:
            g3_pass = pe_c3
            pb_dist = pb_ema - pb_high
            if g3_pass:
                g3_prox = 100
                g3_text = f"Touched 9 EMA ({pb_high:.1f} >= {pb_ema:.1f})"
            else:
                g3_prox = max(15, min(95, int(100 - pb_dist * 4)))
                g3_text = f"{pb_dist:.1f} pts below 9 EMA (Pullback pending)"

        # Gate 4 Proximity: Trigger Candle Close
        if bias == "BULLISH":
            g4_pass = ce_c4
            body = trig_close - trig_open
            if g4_pass:
                g4_prox = 100
                g4_text = f"Bullish Green Bar (+{body:.1f} pts)"
            else:
                g4_prox = 30 if body < 0 else 70
                g4_text = f"Forming {'Red' if body < 0 else 'Doji'} ({body:.1f} pts) — Needs green close"
        else:
            g4_pass = pe_c4
            body = trig_open - trig_close
            if g4_pass:
                g4_prox = 100
                g4_text = f"Bearish Red Bar (-{body:.1f} pts)"
            else:
                g4_prox = 30 if body < 0 else 70
                g4_text = f"Forming {'Green' if body < 0 else 'Doji'} — Needs red close"

        # Gate 5 Proximity: Stoch RSI Cross
        if bias == "BULLISH":
            g5_pass = ce_c5
            k_d_gap = curr_k - curr_d
            if g5_pass:
                g5_prox = 100
                g5_text = f"%K ({curr_k:.1f}) crossed above %D ({curr_d:.1f})"
            else:
                g5_prox = max(20, min(95, int(50 + k_d_gap * 2)))
                g5_text = f"%K: {curr_k:.1f}, %D: {curr_d:.1f} (Gap: {abs(k_d_gap):.1f} pts)"
        else:
            g5_pass = pe_c5
            k_d_gap = curr_d - curr_k
            if g5_pass:
                g5_prox = 100
                g5_text = f"%K ({curr_k:.1f}) crossed below %D ({curr_d:.1f})"
            else:
                g5_prox = max(20, min(95, int(50 + k_d_gap * 2)))
                g5_text = f"%K: {curr_k:.1f}, %D: {curr_d:.1f} (Gap: {abs(k_d_gap):.1f} pts)"

        # Gate 6 Proximity: 1.5x Volume Spike
        vol_pct = int((trig_vol / threshold_vol) * 100) if threshold_vol > 0 else 0
        g6_pass = c6_pass
        g6_prox = min(100, max(15, vol_pct))
        g6_text = f"{int(trig_vol):,} / {int(threshold_vol):,} vol ({vol_pct}% of target)"

        # Gate 7 Proximity: Option VWAP & OI
        if bias == "BULLISH":
            g7_pass = ce_c7
            diff = ce_ltp - ce_vwap
            if g7_pass:
                g7_prox = 100
                g7_text = f"CE ₹{ce_ltp:.1f} >= VWAP ₹{ce_vwap:.1f} (+₹{diff:.1f})"
            else:
                g7_prox = max(20, min(90, int(60 + diff * 3)))
                g7_text = f"CE ₹{ce_ltp:.1f} vs VWAP ₹{ce_vwap:.1f} (Diff: ₹{diff:.1f})"
        else:
            g7_pass = pe_c7
            diff = pe_ltp - pe_vwap
            if g7_pass:
                g7_prox = 100
                g7_text = f"PE ₹{pe_ltp:.1f} >= VWAP ₹{pe_vwap:.1f} (+₹{diff:.1f})"
            else:
                g7_prox = max(20, min(90, int(60 + diff * 3)))
                g7_text = f"PE ₹{pe_ltp:.1f} vs VWAP ₹{pe_vwap:.1f} (Diff: ₹{diff:.1f})"

        # Overall readiness score (0-100%)
        readiness_pct = int(sum([g1_prox, g2_prox, g3_prox, g4_prox, g5_prox, g6_prox, g7_prox]) / 7)
        passing_count = sum(1 for p in [c1_pass, g2_pass, g3_pass, g4_pass, g5_pass, g6_pass, g7_pass] if p)

        # Build 7-point structured telemetry grid
        gates_list: List[Dict[str, Any]] = [
            {
                "id": 1,
                "name": "1. Active Time Window",
                "pass": c1_pass,
                "label": "PASS" if c1_pass else "WAIT",
                "value": c1_val,
                "proximity_pct": g1_prox,
                "proximity_text": g1_text
            },
            {
                "id": 2,
                "name": "2. Spot vs VWAP & 9 EMA",
                "pass": g2_pass,
                "label": "PASS" if g2_pass else f"{g2_prox}%",
                "value": f"Spot {spot_close:.1f} | VWAP {vwap:.1f} | EMA {ema_9:.1f}",
                "proximity_pct": g2_prox,
                "proximity_text": g2_text
            },
            {
                "id": 3,
                "name": "3. 9 EMA Pullback Touch",
                "pass": g3_pass,
                "label": "PASS" if g3_pass else f"{g3_prox}%",
                "value": f"Prev Low {pb_low:.1f} vs EMA {pb_ema:.1f}" if bias == "BULLISH" else f"Prev High {pb_high:.1f} vs EMA {pb_ema:.1f}",
                "proximity_pct": g3_prox,
                "proximity_text": g3_text
            },
            {
                "id": 4,
                "name": "4. Trigger Candle Close",
                "pass": g4_pass,
                "label": "PASS" if g4_pass else f"{g4_prox}%",
                "value": f"Closed {'Green Bullish' if trig_close > trig_open else 'Red Bearish'} ({abs(trig_close - trig_open):.1f} pts)",
                "proximity_pct": g4_prox,
                "proximity_text": g4_text
            },
            {
                "id": 5,
                "name": "5. Stoch RSI Cross",
                "pass": g5_pass,
                "label": "PASS" if g5_pass else f"{g5_prox}%",
                "value": f"%K {curr_k:.1f} vs %D {curr_d:.1f}",
                "proximity_pct": g5_prox,
                "proximity_text": g5_text
            },
            {
                "id": 6,
                "name": "6. 1.5x Volume Spike",
                "pass": g6_pass,
                "label": "PASS" if g6_pass else f"{g6_prox}%",
                "value": f"{int(trig_vol):,} vol vs 1.5x SMA {int(threshold_vol):,}",
                "proximity_pct": g6_prox,
                "proximity_text": g6_text
            },
            {
                "id": 7,
                "name": "7. Option VWAP & OI Filter",
                "pass": g7_pass,
                "label": "PASS" if g7_pass else f"{g7_prox}%",
                "value": f"Call ₹{ce_ltp:.1f} | Put ₹{pe_ltp:.1f}",
                "proximity_pct": g7_prox,
                "proximity_text": g7_text
            }
        ]

        return {
            "signal": signal,
            "bias": bias,
            "readiness_pct": readiness_pct,
            "passing_count": passing_count,
            "gates": gates_list,
            "trigger_timestamp": str(trigger.get("timestamp", ""))
        }

    def _get_empty_gates(self, note: str) -> List[Dict[str, Any]]:
        return [
            {
                "id": i,
                "name": name,
                "pass": False,
                "label": "WAIT",
                "value": note,
                "proximity_pct": 0,
                "proximity_text": "Awaiting market feed initialization"
            }
            for i, name in enumerate([
                "1. Active Time Window",
                "2. Spot vs VWAP & 9 EMA",
                "3. 9 EMA Pullback Touch",
                "4. Trigger Candle Close",
                "5. Stoch RSI Cross",
                "6. 1.5x Volume Spike",
                "7. Option VWAP & OI Filter"
            ], start=1)
        ]

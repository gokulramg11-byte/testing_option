package com.alphascalp.quant.data

data class GateCheck(
    val id: Int,
    val name: String,
    val isPass: Boolean,
    val label: String,
    val value: String,
    val proximityPct: Int = 0,
    val proximityText: String = ""
)

data class ActivePosition(
    val strike: String,
    val optionType: String,
    val entryPrice: Double,
    val currentLtp: Double,
    val slPrice: Double,
    val pnl: Double,
    val pointsCaptured: Double,
    val lots: Int,
    val qty: Int,
    val timeSlStatus: String,
    val isBreakeven: Boolean,
    val isPartial: Boolean
)

data class AtmContract(
    val strike: Double = 0.0,
    val symbol: String = "",
    val ltp: Double = 0.0,
    val vwap: Double = 0.0,
    val oiChangePct: Double = 0.0
)

data class TradeRecord(
    val id: String,
    val strike: String,
    val optionType: String,
    val qty: Int,
    val entryPrice: Double,
    val exitPrice: Double,
    val entryTime: String,
    val exitTime: String,
    val points: Double,
    val pnl: Double,
    val exitReason: String
)

data class TelemetryState(
    val heartbeat: String = "STANDBY",
    val mode: String = "PAPER",
    val isArmed: Boolean = true,
    val latencyMs: Int = 0,
    val indexName: String = "NIFTY 50",
    val spotLtp: Double = 0.0,
    val spotChange: Double = 0.0,
    val spotChangePct: Double = 0.0,
    val netDailyMtm: Double = 0.0,
    val winRate: Double = 0.0,
    val totalTrades: String = "0",
    val consecutiveSl: Int = 0,
    val maxConsecutiveSl: Int = 3,
    val signalStatus: String = "STANDBY",
    val marketBias: String = "NEUTRAL",
    val readinessPct: Int = 0,
    val passingCount: Int = 0,
    val atmCe: AtmContract? = null,
    val atmPe: AtmContract? = null,
    val gates: List<GateCheck> = listOf(
        GateCheck(1, "1. Active Time Window", false, "WAIT", "Awaiting live session (09:30-12:00 / 13:30-15:10 IST)", 30, "Time Window Gate"),
        GateCheck(2, "2. Spot vs VWAP & 9 EMA", false, "WAIT", "Awaiting live candle calculation", 40, "Calculating EMA & VWAP distance"),
        GateCheck(3, "3. 9 EMA Pullback Touch", false, "WAIT", "Awaiting closed 3m candle evaluation", 20, "Monitoring pullback touch"),
        GateCheck(4, "4. Trigger Candle Close", false, "WAIT", "Awaiting trigger candle", 50, "Checking candle direction"),
        GateCheck(5, "5. Stoch RSI Cross", false, "WAIT", "Awaiting momentum confirmation", 35, "Tracking %K and %D crossover"),
        GateCheck(6, "6. 1.5x Volume Surge", false, "WAIT", "Awaiting volume validation", 25, "Comparing against 15 SMA"),
        GateCheck(7, "7. Option VWAP & OI Filter", false, "WAIT", "Awaiting live Option Chain feed", 45, "Comparing Premium vs VWAP & OI")
    ),
    val activePosition: ActivePosition? = null
)

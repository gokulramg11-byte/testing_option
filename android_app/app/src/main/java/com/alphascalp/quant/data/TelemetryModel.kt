package com.alphascalp.quant.data

data class GateCheck(
    val id: Int,
    val name: String,
    val isPass: Boolean,
    val label: String,
    val value: String
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
    val heartbeat: String = "ONLINE",
    val mode: String = "PAPER",
    val isArmed: Boolean = true,
    val latencyMs: Int = 12,
    val indexName: String = "NIFTY 50",
    val spotLtp: Double = 24350.25,
    val spotChange: Double = 125.40,
    val spotChangePct: Double = 0.52,
    val netDailyMtm: Double = 3450.00,
    val winRate: Double = 75.0,
    val totalTrades: String = "3/4",
    val consecutiveSl: Int = 0,
    val maxConsecutiveSl: Int = 3,
    val signalStatus: String = "BUY_CE",
    val gates: List<GateCheck> = listOf(
        GateCheck(1, "1. Active Time Window", true, "PASS", "10:15 IST (Active Session 09:30-12:00)"),
        GateCheck(2, "2. Spot > VWAP & 9 EMA", true, "PASS", "Spot 24350 > VWAP 24310 & EMA 24325"),
        GateCheck(3, "3. 9 EMA Pullback Touch", true, "PASS", "Prev Candle Low 24322 <= EMA 24325"),
        GateCheck(4, "4. Trigger Candle Close", true, "PASS", "Closed Green +14.5 pts above 9 EMA"),
        GateCheck(5, "5. Stoch RSI Cross", true, "PASS", "%K 28.4 crossed %D 22.1 (Oversold < 30)"),
        GateCheck(6, "6. 1.5x Volume Spike", true, "PASS", "182.4K vol > 1.5x SMA 112K"),
        GateCheck(7, "7. Option VWAP & OI Filter", true, "PASS", "Call > VWAP (154.2 > 148.0) & Call OI -12.4% Short Covering")
    ),
    val activePosition: ActivePosition? = ActivePosition(
        strike = "NIFTY 24350 CE 21-NOV",
        optionType = "CE",
        entryPrice = 142.50,
        currentLtp = 168.20,
        slPrice = 152.00,
        pnl = 1927.50,
        pointsCaptured = 25.70,
        lots = 1,
        qty = 75,
        timeSlStatus = "4m 12s remaining (Candle 2/3)",
        isBreakeven = true,
        isPartial = false
    )
)

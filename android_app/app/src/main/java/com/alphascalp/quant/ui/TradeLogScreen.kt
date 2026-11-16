package com.alphascalp.quant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alphascalp.quant.data.TradeRecord
import com.alphascalp.quant.ui.theme.*

@Composable
fun TradeLogScreen(
    modifier: Modifier = Modifier,
    trades: List<TradeRecord> = defaultTrades
) {
    var selectedFilter by remember { mutableStateOf("ALL") }

    val filteredTrades = remember(selectedFilter, trades) {
        when (selectedFilter) {
            "CE" -> trades.filter { it.optionType == "CE" }
            "PE" -> trades.filter { it.optionType == "PE" }
            else -> trades
        }
    }

    val totalPnl = trades.sumOf { it.pnl }
    val winCount = trades.count { it.pnl > 0 }
    val winRate = if (trades.isNotEmpty()) (winCount.toDouble() / trades.size * 100) else 0.0

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Canvas)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "AUDIT LOG & ANALYTICS",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Intraday Execution History (Immutable)",
                        color = TextMuted,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                IconButton(
                    onClick = { /* Export CSV */ },
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Surface)
                        .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.FileDownload,
                        contentDescription = "Export CSV",
                        tint = Cyan
                    )
                }
            }
        }

        item {
            // 2x2 Performance Grid Card
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceLow),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Metric 1: Net P&L
                        PerformanceMetric(
                            label = "REALIZED MTM",
                            value = "${if (totalPnl >= 0) "+" else ""}₹${"%,.2f".format(totalPnl)}",
                            valueColor = if (totalPnl >= 0) EmeraldBright else CrimsonBright,
                            modifier = Modifier.weight(1f)
                        )
                        // Metric 2: Win Rate
                        PerformanceMetric(
                            label = "WIN RATE",
                            value = "${"%.1f".format(winRate)}% ($winCount/${trades.size})",
                            valueColor = if (winRate >= 60) Emerald else Amber,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Divider(color = BorderSubtle, thickness = 1.dp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Metric 3: Profit Factor
                        PerformanceMetric(
                            label = "PROFIT FACTOR",
                            value = "2.84",
                            valueColor = EmeraldBright,
                            modifier = Modifier.weight(1f)
                        )
                        // Metric 4: Avg Duration
                        PerformanceMetric(
                            label = "AVG DURATION",
                            value = "5m 24s",
                            valueColor = TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        item {
            // Filter Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChipItem(
                    label = "ALL (${trades.size})",
                    isSelected = selectedFilter == "ALL",
                    onClick = { selectedFilter = "ALL" }
                )
                FilterChipItem(
                    label = "CE LONGS (${trades.count { it.optionType == "CE" }})",
                    isSelected = selectedFilter == "CE",
                    accentColor = Emerald,
                    onClick = { selectedFilter = "CE" }
                )
                FilterChipItem(
                    label = "PE SHORTS (${trades.count { it.optionType == "PE" }})",
                    isSelected = selectedFilter == "PE",
                    accentColor = Crimson,
                    onClick = { selectedFilter = "PE" }
                )
            }
        }

        items(filteredTrades) { trade ->
            TradeCard(trade = trade)
        }
    }
}

@Composable
private fun PerformanceMetric(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            color = TextDim,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = value,
            color = valueColor,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun FilterChipItem(
    label: String,
    isSelected: Boolean,
    accentColor: Color = Cyan,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) accentColor.copy(alpha = 0.15f) else SurfaceLow)
            .border(
                1.dp,
                if (isSelected) accentColor else BorderSubtle,
                RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = if (isSelected) accentColor else TextMuted,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun TradeCard(trade: TradeRecord) {
    val isWin = trade.pnl >= 0
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceLow),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderSubtle, RoundedCornerShape(10.dp))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header: Strike, Option badge, PnL
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (trade.optionType == "CE") EmeraldDark else CrimsonDark)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = trade.optionType,
                            color = if (trade.optionType == "CE") EmeraldBright else CrimsonBright,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Text(
                        text = trade.strike,
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (isWin) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                        contentDescription = null,
                        tint = if (isWin) EmeraldBright else CrimsonBright,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "${if (isWin) "+" else ""}₹${"%,.2f".format(trade.pnl)}",
                        color = if (isWin) EmeraldBright else CrimsonBright,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Subgrid: Qty, Entry, Exit, Points
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(SurfaceLowest)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricColumn(label = "QTY", value = "${trade.qty}")
                MetricColumn(label = "ENTRY", value = "₹${"%.2f".format(trade.entryPrice)}")
                MetricColumn(label = "EXIT", value = "₹${"%.2f".format(trade.exitPrice)}")
                MetricColumn(
                    label = "POINTS",
                    value = "${if (trade.points >= 0) "+" else ""}${"%.2f".format(trade.points)}",
                    valueColor = if (trade.points >= 0) Emerald else Crimson
                )
            }

            // Footer: Timestamps and Exit Reason
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${trade.entryTime}  ➔  ${trade.exitTime}",
                    color = TextDim,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Surface)
                        .border(1.dp, BorderSubtle, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = trade.exitReason,
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricColumn(
    label: String,
    value: String,
    valueColor: Color = TextPrimary
) {
    Column {
        Text(
            text = label,
            color = TextDim,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = value,
            color = valueColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace
        )
    }
}

val defaultTrades = listOf(
    TradeRecord(
        id = "TR-101",
        strike = "NIFTY 24350 CE",
        optionType = "CE",
        qty = 75,
        entryPrice = 142.50,
        exitPrice = 168.20,
        entryTime = "10:18:22",
        exitTime = "10:24:10",
        points = 25.70,
        pnl = 1927.50,
        exitReason = "TARGET 2 (+15 pts trail)"
    ),
    TradeRecord(
        id = "TR-102",
        strike = "NIFTY 24300 CE",
        optionType = "CE",
        qty = 75,
        entryPrice = 120.00,
        exitPrice = 135.50,
        entryTime = "10:45:14",
        exitTime = "10:52:01",
        points = 15.50,
        pnl = 1162.50,
        exitReason = "PARTIAL 50% & TRAIL"
    ),
    TradeRecord(
        id = "TR-103",
        strike = "NIFTY 24400 PE",
        optionType = "PE",
        qty = 75,
        entryPrice = 98.40,
        exitPrice = 88.50,
        entryTime = "11:12:05",
        exitTime = "11:15:30",
        points = -9.90,
        pnl = -742.50,
        exitReason = "HARD STOP LOSS (10%)"
    ),
    TradeRecord(
        id = "TR-104",
        strike = "NIFTY 24350 CE",
        optionType = "CE",
        qty = 75,
        entryPrice = 155.00,
        exitPrice = 170.00,
        entryTime = "13:40:19",
        exitTime = "13:48:42",
        points = 15.00,
        pnl = 1102.50,
        exitReason = "TARGET 2 REACHED"
    )
)

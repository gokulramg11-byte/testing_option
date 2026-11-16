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
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alphascalp.quant.data.TradeRecord
import com.alphascalp.quant.ui.theme.*

@Composable
fun TradeLogScreen(
    trades: List<TradeRecord>,
    modifier: Modifier = Modifier
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
            Column {
                Text(
                    text = "AUDIT LOG & ANALYTICS",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "Live Workflow Execution History (Direct from Firestore)",
                    color = TextMuted,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
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
                        // Metric 1: Realized P&L
                        PerformanceMetric(
                            label = "REALIZED MTM",
                            value = "${if (totalPnl >= 0) "+" else ""}₹${"%,.2f".format(totalPnl)}",
                            valueColor = if (totalPnl > 0) EmeraldBright else if (totalPnl < 0) CrimsonBright else TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        // Metric 2: Win Rate
                        PerformanceMetric(
                            label = "WIN RATE",
                            value = "${"%.1f".format(winRate)}% ($winCount/${trades.size})",
                            valueColor = if (winRate >= 60) Emerald else if (trades.isEmpty()) TextDim else Amber,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    HorizontalDivider(color = BorderSubtle, thickness = 1.dp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Metric 3: Total Trades
                        PerformanceMetric(
                            label = "TOTAL TRADES",
                            value = "${trades.size} Executions",
                            valueColor = TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        // Metric 4: Avg Profit/Loss
                        val avgPnl = if (trades.isNotEmpty()) totalPnl / trades.size else 0.0
                        PerformanceMetric(
                            label = "AVG TRADE PNL",
                            value = "${if (avgPnl >= 0) "+" else ""}₹${"%,.2f".format(avgPnl)}",
                            valueColor = if (avgPnl > 0) EmeraldBright else if (avgPnl < 0) CrimsonBright else TextDim,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        if (trades.isEmpty()) {
            item {
                // Professional Empty State (No Dummy Data)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = TextDim,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "NO LIVE TRADES RECORDED",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Trades executed by your GitHub Actions bot session will appear here in real-time. Zero simulated records.",
                            color = TextMuted,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
            }
        } else {
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
            fontSize = 16.sp,
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

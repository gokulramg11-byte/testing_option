package com.alphascalp.quant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alphascalp.quant.data.TelemetryState
import com.alphascalp.quant.ui.theme.*

@Composable
fun CockpitScreen(
    telemetry: TelemetryState,
    onOpenRules: () -> Unit,
    onEmergencyKill: () -> Unit,
    onToggleArm: (Boolean) -> Unit = {}
) {
    var isArmed by remember(telemetry.isArmed) { mutableStateOf(telemetry.isArmed) }
    var showKillConfirm by remember { mutableStateOf(false) }

    if (showKillConfirm) {
        AlertDialog(
            onDismissRequest = { showKillConfirm = false },
            title = {
                Text(
                    "CONFIRM EMERGENCY KILL SWITCH",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = CrimsonBright
                )
            },
            text = {
                Text(
                    "This sends an immediate square-off command to Firebase Firestore. The active workflow bot will instantly exit all open option positions at market price and disarm itself. Continue?",
                    fontSize = 11.sp,
                    color = TextPrimary,
                    fontFamily = FontFamily.Monospace
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showKillConfirm = false
                        onEmergencyKill()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Crimson)
                ) {
                    Text(
                        "CONFIRM SQUARE OFF",
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showKillConfirm = false }) {
                    Text("Cancel", color = TextMuted, fontFamily = FontFamily.Monospace)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Canvas)
    ) {
        // Cockpit Sub-header
        Surface(
            color = SurfaceLowest,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderSubtle)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(if (telemetry.heartbeat == "ONLINE") Emerald else Amber, CircleShape)
                    )
                    Text(
                        text = "LIVE QUANT COCKPIT",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onOpenRules, modifier = Modifier.size(28.dp)) {
                    Text("⚙️", fontSize = 14.sp)
                }
            }
        }

        // Main Scroll View
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Status Pills
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .background(SurfaceLow, RoundedCornerShape(4.dp))
                        .border(1.dp, BorderSubtle, RoundedCornerShape(4.dp))
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = telemetry.heartbeat,
                        color = if (telemetry.heartbeat == "ONLINE") EmeraldBright else Amber,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (telemetry.latencyMs > 0) "${telemetry.latencyMs}ms Feed" else "Awaiting Ticks",
                        color = TextDim,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .background(SurfaceLow, RoundedCornerShape(4.dp))
                        .border(1.dp, BorderSubtle, RoundedCornerShape(4.dp))
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (telemetry.mode == "LIVE") "LIVE CAPITAL" else "PAPER TRADING",
                        color = if (telemetry.mode == "LIVE") CrimsonBright else Amber,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (telemetry.mode == "LIVE") "REAL ORDERS" else "NO ORDER PLACED",
                        color = TextDim,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Spot Ticker Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderSubtle, RoundedCornerShape(6.dp)),
                colors = CardDefaults.cardColors(containerColor = SurfaceLow),
                shape = RoundedCornerShape(6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "UNDERLYING INDEX  NSE_INDEX",
                            color = TextMuted,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = telemetry.indexName,
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = if (telemetry.spotLtp > 0) String.format("%.2f", telemetry.spotLtp) else "AWAITING TICKS",
                            color = if (telemetry.spotLtp > 0) EmeraldBright else TextDim,
                            fontSize = 16.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        if (telemetry.spotLtp > 0) {
                            Text(
                                text = "${if (telemetry.spotChange >= 0) "▲ +" else "▼ "}${telemetry.spotChange} (${telemetry.spotChangePct}%)",
                                color = if (telemetry.spotChange >= 0) EmeraldBright else CrimsonBright,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        } else {
                            Text(
                                text = "Market Feed Idle",
                                color = TextDim,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            // Net MTM P&L Card
            val isPnlPositive = telemetry.netDailyMtm > 0
            val isPnlNegative = telemetry.netDailyMtm < 0
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderSubtle, RoundedCornerShape(6.dp)),
                colors = CardDefaults.cardColors(containerColor = SurfaceLow),
                shape = RoundedCornerShape(6.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "NET INTRADAY REALIZED MTM",
                            color = TextMuted,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Box(
                            modifier = Modifier
                                .background(
                                    if (isPnlPositive) Color(0x2610B981) else if (isPnlNegative) Color(0x26EF4444) else Surface,
                                    RoundedCornerShape(3.dp)
                                )
                                .border(
                                    1.dp,
                                    if (isPnlPositive) Color(0x4D10B981) else if (isPnlNegative) Color(0x4DEF4444) else BorderSubtle,
                                    RoundedCornerShape(3.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (isPnlPositive) "PROFITABLE" else if (isPnlNegative) "DRAWDOWN" else "FLAT",
                                color = if (isPnlPositive) EmeraldBright else if (isPnlNegative) CrimsonBright else TextDim,
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${if (telemetry.netDailyMtm >= 0) "+" else ""}₹${String.format("%,.2f", telemetry.netDailyMtm)}",
                        color = if (isPnlPositive) EmeraldBright else if (isPnlNegative) CrimsonBright else TextPrimary,
                        fontSize = 24.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = Color(0x33374151))
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Win Rate: ${telemetry.winRate}%",
                            color = TextMuted,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text("|", color = TextDim, fontSize = 9.sp)
                        Text(
                            text = "Trades: ${telemetry.totalTrades}",
                            color = TextMuted,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text("|", color = TextDim, fontSize = 9.sp)
                        Text(
                            text = "Consec SL: ${telemetry.consecutiveSl}/${telemetry.maxConsecutiveSl}",
                            color = TextMuted,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // 7-Point Signal Gatekeeper Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderSubtle, RoundedCornerShape(6.dp)),
                colors = CardDefaults.cardColors(containerColor = SurfaceLow),
                shape = RoundedCornerShape(6.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Surface)
                            .border(width = 1.dp, color = BorderSubtle)
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "⚡ 7-POINT QUANT SIGNAL GATEKEEPER",
                            color = TextPrimary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Box(
                            modifier = Modifier
                                .background(
                                    if (telemetry.signalStatus.contains("BUY")) EmeraldDark else SurfaceLow,
                                    RoundedCornerShape(3.dp)
                                )
                                .border(
                                    1.dp,
                                    if (telemetry.signalStatus.contains("BUY")) Emerald else BorderSubtle,
                                    RoundedCornerShape(3.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = telemetry.signalStatus,
                                color = if (telemetry.signalStatus.contains("BUY")) EmeraldBright else TextDim,
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    telemetry.gates.forEach { gate ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = gate.name,
                                    color = TextPrimary,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (gate.isPass) "[PASS]" else "[${gate.label}]",
                                    color = if (gate.isPass) EmeraldBright else TextDim,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = gate.value,
                                color = if (gate.isPass) EmeraldBright else TextMuted,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            // Active Position Card OR Scanning Status
            if (telemetry.activePosition != null) {
                val pos = telemetry.activePosition
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderSubtle, RoundedCornerShape(6.dp)),
                    colors = CardDefaults.cardColors(containerColor = SurfaceLow),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "${pos.optionType} SCALP // ${pos.strike}",
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${pos.lots} Lot (${pos.qty} Qty) // Intraday MIS",
                                    color = TextMuted,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .background(Color(0x3310B981), RoundedCornerShape(3.dp))
                                    .border(1.dp, Emerald, RoundedCornerShape(3.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "ACTIVE",
                                    color = EmeraldBright,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // 4-cell stats grid
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(Surface, RoundedCornerShape(4.dp))
                                    .border(1.dp, BorderSubtle, RoundedCornerShape(4.dp))
                                    .padding(8.dp)
                            ) {
                                Text("ENTRY PRICE", color = TextDim, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                                Text("₹${pos.entryPrice}", color = TextPrimary, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(Surface, RoundedCornerShape(4.dp))
                                    .border(1.dp, BorderSubtle, RoundedCornerShape(4.dp))
                                    .padding(8.dp)
                            ) {
                                Text("LIVE OPTION LTP", color = TextDim, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                                Text("₹${pos.currentLtp} (${if (pos.pointsCaptured >= 0) "+" else ""}${pos.pointsCaptured})", color = EmeraldBright, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(Surface, RoundedCornerShape(4.dp))
                                    .border(1.dp, BorderSubtle, RoundedCornerShape(4.dp))
                                    .padding(8.dp)
                            ) {
                                Text("TRAILING SL", color = TextDim, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                                Text("₹${pos.slPrice}", color = Amber, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(Color(0x0D10B981), RoundedCornerShape(4.dp))
                                    .border(1.dp, Color(0x4D10B981), RoundedCornerShape(4.dp))
                                    .padding(8.dp)
                            ) {
                                Text("LIVE TRADE P&L", color = EmeraldBright, fontSize = 8.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                Text("${if (pos.pnl >= 0) "+" else ""}₹${pos.pnl}", color = if (pos.pnl >= 0) EmeraldBright else CrimsonBright, fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            }
                        }

                        Text(
                            text = "⏱️ Time SL: ${pos.timeSlStatus}",
                            color = TextMuted,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            } else {
                // Clean scanning card when no trade is active
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderSubtle, RoundedCornerShape(6.dp)),
                    colors = CardDefaults.cardColors(containerColor = SurfaceLow),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "NO ACTIVE POSITION",
                                color = TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "Monitoring live candles for 7/7 agreement.",
                                color = TextMuted,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Box(
                            modifier = Modifier
                                .background(Surface, RoundedCornerShape(3.dp))
                                .border(1.dp, BorderSubtle, RoundedCornerShape(3.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "READY",
                                color = Cyan,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Arm / Disarm Bot Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceLow, RoundedCornerShape(4.dp))
                    .border(1.dp, BorderSubtle, RoundedCornerShape(4.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "AUTONOMOUS EXECUTION",
                        color = TextPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = if (isArmed) "Armed: Bot will enter when gates pass" else "Disarmed: Execution paused",
                        color = TextMuted,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Switch(
                    checked = isArmed,
                    onCheckedChange = {
                        isArmed = it
                        onToggleArm(it)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Emerald,
                        uncheckedThumbColor = TextDim,
                        uncheckedTrackColor = Surface
                    )
                )
            }

            // Crimson Emergency Kill Button
            Button(
                onClick = { showKillConfirm = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CrimsonDark)
            ) {
                Text(
                    text = "⚠️ EMERGENCY SQUARE OFF & STOP BOT",
                    color = CrimsonBright,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

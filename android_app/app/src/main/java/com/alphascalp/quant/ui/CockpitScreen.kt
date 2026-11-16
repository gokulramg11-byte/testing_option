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
    onEmergencyKill: () -> Unit
) {
    var isArmed by remember { mutableStateOf(telemetry.isArmed) }
    var showKillConfirm by remember { mutableStateOf(false) }

    if (showKillConfirm) {
        AlertDialog(
            onDismissRequest = { showKillConfirm = false },
            title = { Text("CONFIRM EMERGENCY KILL SWITCH", fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CrimsonBright) },
            text = { Text("This will immediately square off any active option position at market price and halt all automated scalping executions. Continue?", fontSize = 11.sp, color = TextPrimary) },
            confirmButton = {
                Button(
                    onClick = {
                        showKillConfirm = false
                        onEmergencyKill()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Crimson)
                ) {
                    Text("CONFIRM SQUARE OFF", color = Color.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showKillConfirm = false }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = SurfaceLow
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Canvas)
    ) {
        // Sticky Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Canvas)
                .border(width = 1.dp, color = BorderSubtle)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("⚡ NIFTY // ALPHASCALP", color = EmeraldBright, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .background(Surface, RoundedCornerShape(4.dp))
                        .border(1.dp, BorderSubtle, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text("● ${telemetry.latencyMs}MS", color = EmeraldBright, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onOpenRules, modifier = Modifier.size(24.dp)) {
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
                    Text("ONLINE", color = EmeraldBright, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    Text("14ms Feed", color = TextDim, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
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
                    Text("PAPER TRADING", color = Amber, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    Text("(DRY_RUN)", color = TextDim, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                }
            }

            // Spot Ticker Card
            Card(
                modifier = Modifier.fillMaxWidth().border(1.dp, BorderSubtle, RoundedCornerShape(6.dp)),
                colors = CardDefaults.cardColors(containerColor = SurfaceLow),
                shape = RoundedCornerShape(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("UNDERLYING INDEX  NSE_INDEX", color = TextMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                        Text(telemetry.indexName, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(String.format("%.2f", telemetry.spotLtp), color = EmeraldBright, fontSize = 16.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        Text("▲ +${telemetry.spotChange} (+${telemetry.spotChangePct}%)", color = EmeraldBright, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }

            // Net MTM P&L Card
            Card(
                modifier = Modifier.fillMaxWidth().border(1.dp, BorderSubtle, RoundedCornerShape(6.dp)),
                colors = CardDefaults.cardColors(containerColor = SurfaceLow),
                shape = RoundedCornerShape(6.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("NET TODAY MTM P&L", color = TextMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                        Box(
                            modifier = Modifier
                                .background(Color(0x2610B981), RoundedCornerShape(3.dp))
                                .border(1.dp, Color(0x4D10B981), RoundedCornerShape(3.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("TARGET LOCKED", color = EmeraldBright, fontSize = 8.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "+₹" + String.format("%.2f", telemetry.netDailyMtm),
                        color = EmeraldBright,
                        fontSize = 24.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Divider(color = Color(0x33374151))
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Win Rate: ${telemetry.winRate}%", color = TextMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                        Text("|", color = TextDim, fontSize = 9.sp)
                        Text("Trades: ${telemetry.totalTrades}", color = TextMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                        Text("|", color = TextDim, fontSize = 9.sp)
                        Text("Consec SL: ${telemetry.consecutiveSl}/${telemetry.maxConsecutiveSl}", color = TextMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }

            // 7-Point Signal Gatekeeper Card
            Card(
                modifier = Modifier.fillMaxWidth().border(1.dp, BorderSubtle, RoundedCornerShape(6.dp)),
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
                        Text("⚡ 7-POINT QUANT SIGNAL GATEKEEPER", color = TextPrimary, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        Box(
                            modifier = Modifier
                                .background(Color(0x2610B981), RoundedCornerShape(3.dp))
                                .border(1.dp, Color(0x4D10B981), RoundedCornerShape(3.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("READY FOR CE SCALP", color = EmeraldBright, fontSize = 8.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                    }
                    telemetry.gates.forEach { gate ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(width = 0.5.dp, color = Color(0x26374151))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(gate.name, color = TextPrimary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                Text(if (gate.isPass) "✓ PASS" else "⏳ WAIT", color = if (gate.isPass) EmeraldBright else TextDim, fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            }
                            Text(gate.value, color = if (gate.isPass) EmeraldBright else TextMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }

            // Active Position Card & Emergency Kill Switch
            telemetry.activePosition?.let { pos ->
                Card(
                    modifier = Modifier.fillMaxWidth().border(1.dp, BorderSubtle, RoundedCornerShape(6.dp)),
                    colors = CardDefaults.cardColors(containerColor = SurfaceLow),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("LONG SCALP // ${pos.strike}", color = TextPrimary, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                Text("${pos.lots} Lot (${pos.qty} Qty) // Intraday MIS", color = TextMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                            }
                            Box(modifier = Modifier.background(Color(0x3310B981), RoundedCornerShape(3.dp)).border(1.dp, Emerald, RoundedCornerShape(3.dp)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                                Text("RUNNING", color = EmeraldBright, fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            }
                        }

                        // 4-cell stats grid
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Column(modifier = Modifier.weight(1f).background(Surface, RoundedCornerShape(4.dp)).border(1.dp, BorderSubtle, RoundedCornerShape(4.dp)).padding(8.dp)) {
                                Text("ENTRY PRICE", color = TextDim, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                                Text("₹${pos.entryPrice}", color = TextPrimary, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            }
                            Column(modifier = Modifier.weight(1f).background(Surface, RoundedCornerShape(4.dp)).border(1.dp, BorderSubtle, RoundedCornerShape(4.dp)).padding(8.dp)) {
                                Text("LIVE OPTION LTP", color = TextDim, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                                Text("₹${pos.currentLtp} (+${pos.pointsCaptured})", color = EmeraldBright, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Column(modifier = Modifier.weight(1f).background(Surface, RoundedCornerShape(4.dp)).border(1.dp, BorderSubtle, RoundedCornerShape(4.dp)).padding(8.dp)) {
                                Text("TRAILING SL", color = TextDim, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                                Text("₹${pos.slPrice}", color = Amber, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            }
                            Column(modifier = Modifier.weight(1f).background(Color(0x0D10B981), RoundedCornerShape(4.dp)).border(1.dp, Color(0x4D10B981), RoundedCornerShape(4.dp)).padding(8.dp)) {
                                Text("LIVE TRADE P&L", color = EmeraldBright, fontSize = 8.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                Text("+₹${pos.pnl}", color = EmeraldBright, fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Safety bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Canvas, RoundedCornerShape(4.dp))
                                .border(1.dp, BorderSubtle, RoundedCornerShape(4.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("⏱️ Time SL: ${pos.timeSlStatus}", color = TextMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("ARM BOT", color = TextDim, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                Switch(
                                    checked = isArmed,
                                    onCheckedChange = { isArmed = it },
                                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Emerald)
                                )
                            }
                        }

                        // Crimson Emergency Button
                        Button(
                            onClick = { showKillConfirm = true },
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            shape = RoundedCornerShape(4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = CrimsonDark)
                        ) {
                            Text("⚠️ EMERGENCY SQUARE OFF & STOP BOT", color = CrimsonBright, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

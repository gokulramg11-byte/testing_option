package com.alphascalp.quant.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alphascalp.quant.data.AtmContract
import com.alphascalp.quant.data.GateCheck
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

    // Pulsing heartbeat animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    if (showKillConfirm) {
        AlertDialog(
            onDismissRequest = { showKillConfirm = false },
            containerColor = SurfaceLow,
            title = {
                Text(
                    "⚠️ CONFIRM EMERGENCY SQUARE-OFF",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = CrimsonBright
                )
            },
            text = {
                Text(
                    "This immediately sends a Kill Switch command to Firebase Firestore. The active workflow bot will instantly exit all open option positions at market price and disarm itself. Continue?",
                    fontSize = 11.sp,
                    color = TextPrimary,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 16.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showKillConfirm = false
                        onEmergencyKill()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Crimson),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        "CONFIRM SQUARE OFF",
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showKillConfirm = false },
                    shape = RoundedCornerShape(4.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
                ) {
                    Text("CANCEL", color = TextMuted, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Canvas)
    ) {
        // Top Navigation Sub-header
        Surface(
            color = SurfaceLowest,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderSubtle)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .background(
                                color = when {
                                    telemetry.heartbeat == "ONLINE" -> Emerald.copy(alpha = pulseAlpha)
                                    telemetry.heartbeat.contains("ERR") || telemetry.heartbeat.contains("RULES") -> CrimsonBright
                                    else -> Amber
                                },
                                shape = CircleShape
                            )
                    )
                    Text(
                        text = "QUANT COCKPIT // V3 LIVE",
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Arm / Disarm switch pill
                    Box(
                        modifier = Modifier
                            .background(if (isArmed) Color(0x2610B981) else Color(0x26F59E0B), RoundedCornerShape(4.dp))
                            .border(1.dp, if (isArmed) Emerald else Amber, RoundedCornerShape(4.dp))
                            .clickable {
                                isArmed = !isArmed
                                onToggleArm(isArmed)
                            }
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = if (isArmed) "● ARMED" else "○ DISARMED",
                            color = if (isArmed) EmeraldBright else Amber,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(
                        onClick = onOpenRules,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Text("⚙️", fontSize = 13.sp)
                    }
                }
            }
        }

        // Scrollable Body
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Diagnostic Alert Banner if rules or connection error occurs
            if (telemetry.heartbeat.contains("ERR") || telemetry.heartbeat.contains("RULES") || telemetry.heartbeat.contains("PROJECT")) {
                DiagnosticBanner(telemetry.heartbeat, telemetry.gates.firstOrNull()?.value ?: "")
            }

            // Status Bar Pills
            StatusBarPills(telemetry)

            // Spot Ticker Hero Card
            SpotTickerCard(telemetry)

            // Strategy Proximity & Readiness Progress Card (HERO SECTION)
            StrategyProximityCard(telemetry)

            // Active ATM Strikes Tracker (Live CE & PE Option Quotes)
            AtmStrikesCard(telemetry)

            // 7-Point Quantitative Signal Gatekeeper Card
            SignalGatekeeperCard(telemetry)

            // Net Realized MTM & Risk Card
            NetMtmCard(telemetry)

            // Active Trade Card (if in position)
            if (telemetry.activePosition != null) {
                ActivePositionCard(telemetry.activePosition)
            }

            // Emergency Kill Switch Button
            EmergencyKillButton(onEmergencyKill = { showKillConfirm = true })

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun DiagnosticBanner(status: String, detail: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CrimsonBright, RoundedCornerShape(6.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0x33DC2626)),
        shape = RoundedCornerShape(6.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("⚠️", fontSize = 12.sp)
                Text(
                    text = "CLOUD BRIDGE NOTICE: $status",
                    color = CrimsonBright,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
            if (detail.isNotBlank()) {
                Text(
                    text = detail,
                    color = TextPrimary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 4.dp),
                    lineHeight = 14.sp
                )
            }
        }
    }
}

@Composable
fun StatusBarPills(telemetry: TelemetryState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Heartbeat Pill
        Row(
            modifier = Modifier
                .weight(1f)
                .background(SurfaceLow, RoundedCornerShape(4.dp))
                .border(1.dp, BorderSubtle, RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = telemetry.heartbeat,
                color = when {
                    telemetry.heartbeat == "ONLINE" -> EmeraldBright
                    telemetry.heartbeat.contains("ERR") || telemetry.heartbeat.contains("RULES") -> CrimsonBright
                    else -> Amber
                },
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (telemetry.latencyMs > 0) "${telemetry.latencyMs}ms Feed" else "Polling 2.5s",
                color = TextDim,
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        // Mode Pill
        Row(
            modifier = Modifier
                .weight(1f)
                .background(SurfaceLow, RoundedCornerShape(4.dp))
                .border(1.dp, BorderSubtle, RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
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
}

@Composable
fun SpotTickerCard(telemetry: TelemetryState) {
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
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (telemetry.spotLtp > 0) String.format("₹%,.2f", telemetry.spotLtp) else "CONNECTING...",
                    color = if (telemetry.spotLtp > 0) EmeraldBright else TextDim,
                    fontSize = 18.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                if (telemetry.spotLtp > 0) {
                    val isPositive = telemetry.spotChange >= 0
                    Text(
                        text = "${if (isPositive) "▲ +" else "▼ "}${String.format("%.2f", telemetry.spotChange)} (${String.format("%.2f", telemetry.spotChangePct)}%)",
                        color = if (isPositive) EmeraldBright else CrimsonBright,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    Text(
                        text = "Awaiting Upstox Tick",
                        color = TextDim,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
fun StrategyProximityCard(telemetry: TelemetryState) {
    val readiness = telemetry.readinessPct.coerceIn(0, 100)
    val passing = telemetry.passingCount.coerceIn(0, 7)
    val bias = telemetry.marketBias.ifBlank { "NEUTRAL" }

    val readinessColor = when {
        readiness >= 85 -> EmeraldBright
        readiness >= 50 -> Amber
        else -> Cyan
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0x3306B6D4), RoundedCornerShape(8.dp)),
        colors = CardDefaults.cardColors(containerColor = SurfaceLow),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Strategy Readiness & Passing Gates Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "STRATEGY CONVERGENCE",
                        color = Cyan,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$readiness% READINESS",
                        color = readinessColor,
                        fontSize = 20.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .background(
                            if (passing == 7) Color(0x3310B981) else if (passing >= 4) Color(0x26F59E0B) else Surface,
                            RoundedCornerShape(4.dp)
                        )
                        .border(
                            1.dp,
                            if (passing == 7) Emerald else if (passing >= 4) Amber else BorderSubtle,
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "$passing / 7 GATES QUALIFIED",
                        color = if (passing == 7) EmeraldBright else if (passing >= 4) Amber else TextMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Proximity Linear Progress Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .background(Surface, RoundedCornerShape(4.dp))
                    .clip(RoundedCornerShape(4.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(readiness / 100f)
                        .fillMaxHeight()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(Cyan, readinessColor)
                            )
                        )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Bias and Proximity Description
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("MARKET BIAS:", color = TextDim, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    Text(
                        text = bias,
                        color = when (bias) {
                            "BULLISH" -> EmeraldBright
                            "BEARISH" -> CrimsonBright
                            else -> TextPrimary
                        },
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = when {
                        passing == 7 -> "⚡ ENTRY SIGNAL QUALIFIED"
                        passing >= 5 -> "👀 1-2 GATES PENDING"
                        else -> "🔍 SCANNING PULLBACK"
                    },
                    color = if (passing == 7) EmeraldBright else TextDim,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun AtmStrikesCard(telemetry: TelemetryState) {
    val ce = telemetry.atmCe
    val pe = telemetry.atmPe

    if (ce != null || pe != null) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderSubtle, RoundedCornerShape(6.dp)),
            colors = CardDefaults.cardColors(containerColor = SurfaceLow),
            shape = RoundedCornerShape(6.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "ACTIVE ATM OPTION STRIKES // LIVE PREMIUMS",
                    color = TextMuted,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // CE Card
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(Surface, RoundedCornerShape(4.dp))
                            .border(1.dp, if (telemetry.marketBias == "BULLISH") Emerald.copy(alpha = 0.5f) else BorderSubtle, RoundedCornerShape(4.dp))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = if (ce != null && ce.symbol.isNotBlank()) ce.symbol else "ATM CALL (CE)",
                            color = EmeraldBright,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (ce != null && ce.ltp > 0) String.format("₹%.2f", ce.ltp) else "--",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (ce != null && ce.vwap > 0) "VWAP: ₹${String.format("%.1f", ce.vwap)}" else "Awaiting VWAP",
                            color = TextDim,
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // PE Card
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(Surface, RoundedCornerShape(4.dp))
                            .border(1.dp, if (telemetry.marketBias == "BEARISH") Crimson.copy(alpha = 0.5f) else BorderSubtle, RoundedCornerShape(4.dp))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = if (pe != null && pe.symbol.isNotBlank()) pe.symbol else "ATM PUT (PE)",
                            color = CrimsonBright,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (pe != null && pe.ltp > 0) String.format("₹%.2f", pe.ltp) else "--",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (pe != null && pe.vwap > 0) "VWAP: ₹${String.format("%.1f", pe.vwap)}" else "Awaiting VWAP",
                            color = TextDim,
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SignalGatekeeperCard(telemetry: TelemetryState) {
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

            telemetry.gates.forEachIndexed { index, gate ->
                GateItem(gate = gate)
                if (index < telemetry.gates.size - 1) {
                    HorizontalDivider(color = Color(0x1A374151), thickness = 1.dp)
                }
            }
        }
    }
}

@Composable
fun GateItem(gate: GateCheck) {
    val prox = gate.proximityPct.coerceIn(0, 100)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = gate.name,
                color = TextPrimary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Box(
                modifier = Modifier
                    .background(
                        if (gate.isPass) Color(0x2610B981) else Color(0x1AF59E0B),
                        RoundedCornerShape(3.dp)
                    )
                    .border(
                        1.dp,
                        if (gate.isPass) Color(0x6610B981) else Color(0x4DF59E0B),
                        RoundedCornerShape(3.dp)
                    )
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            ) {
                Text(
                    text = if (gate.isPass) "PASS" else "$prox%",
                    color = if (gate.isPass) EmeraldBright else Amber,
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Mini Proximity Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(Surface, RoundedCornerShape(2.dp))
                .clip(RoundedCornerShape(2.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(prox / 100f)
                    .fillMaxHeight()
                    .background(if (gate.isPass) Emerald else Amber)
            )
        }

        // Value text
        Text(
            text = gate.value,
            color = if (gate.isPass) EmeraldBright else TextMuted,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace
        )

        // Proximity detail text
        if (gate.proximityText.isNotBlank()) {
            Text(
                text = "↳ ${gate.proximityText}",
                color = TextDim,
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun NetMtmCard(telemetry: TelemetryState) {
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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
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

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${if (telemetry.netDailyMtm >= 0) "+" else ""}₹${String.format("%,.2f", telemetry.netDailyMtm)}",
                color = if (isPnlPositive) EmeraldBright else if (isPnlNegative) CrimsonBright else TextPrimary,
                fontSize = 22.sp,
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
                    text = "SL Streak: ${telemetry.consecutiveSl}/${telemetry.maxConsecutiveSl}",
                    color = TextMuted,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun ActivePositionCard(pos: com.alphascalp.quant.data.ActivePosition) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Emerald.copy(alpha = 0.5f), RoundedCornerShape(6.dp)),
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
                StatCell("ENTRY PRICE", String.format("₹%.2f", pos.entryPrice), Modifier.weight(1f))
                StatCell("CURRENT LTP", String.format("₹%.2f", pos.currentLtp), Modifier.weight(1f))
                StatCell("HARD SL", String.format("₹%.2f", pos.slPrice), Modifier.weight(1f), CrimsonBright)
                val isProfit = pos.pnl >= 0
                StatCell(
                    "P&L",
                    "${if (isProfit) "+" else ""}₹${String.format("%.1f", pos.pnl)}",
                    Modifier.weight(1f),
                    if (isProfit) EmeraldBright else CrimsonBright
                )
            }
        }
    }
}

@Composable
fun StatCell(label: String, value: String, modifier: Modifier, valueColor: Color = TextPrimary) {
    Column(
        modifier = modifier
            .background(Surface, RoundedCornerShape(4.dp))
            .border(1.dp, BorderSubtle, RoundedCornerShape(4.dp))
            .padding(6.dp)
    ) {
        Text(label, color = TextDim, fontSize = 7.sp, fontFamily = FontFamily.Monospace)
        Text(value, color = valueColor, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun EmergencyKillButton(onEmergencyKill: () -> Unit) {
    Button(
        onClick = onEmergencyKill,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Crimson),
        shape = RoundedCornerShape(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("🚨", fontSize = 14.sp)
            Text(
                text = "EMERGENCY SQUARE-OFF (KILL SWITCH)",
                color = Color.White,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

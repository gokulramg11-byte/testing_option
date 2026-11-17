package com.alphascalp.quant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Shield
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
import androidx.compose.ui.window.Dialog
import com.alphascalp.quant.ui.theme.*

@Composable
fun RiskRulesDialog(
    onDismissRequest: () -> Unit,
    projectId: String = "",
    onSwitchProject: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceLow),
            shape = RoundedCornerShape(16.dp),
            modifier = modifier
                .fillMaxWidth()
                .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Dialog Title Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = EmeraldBright,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "RISK & SENTINEL RULES",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextMuted
                        )
                    }
                }

                Text(
                    text = "Strict hard-coded circuit breakers enforced by the execution engine and cloud workers.",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )

                Divider(color = BorderSubtle, thickness = 1.dp)

                // Rule Group: Hard Risk Limits
                RiskSectionHeader(title = "MANDATORY CIRCUIT BREAKERS")
                RuleRow(label = "Max Daily Loss Limit", value = "₹4,000 (Cutoff Trigger)", color = CrimsonBright)
                RuleRow(label = "Max Per-Trade Risk", value = "₹1,500 (1 Lot / 75 Qty)", color = Amber)
                RuleRow(label = "Consecutive SL Breaker", value = "3 Trades (Day Terminated)", color = CrimsonBright)
                RuleRow(label = "Hard Time SL", value = "3 Candles / 9 Mins max hold", color = TextPrimary)
                RuleRow(label = "EOD Auto-Squareoff", value = "15:10 IST strictly", color = Amber)

                Divider(color = BorderSubtle, thickness = 1.dp)

                // Rule Group: Dynamic Profit Protection
                RiskSectionHeader(title = "DYNAMIC TARGET & TRAIL RULES")
                RuleRow(label = "Initial Stop Loss", value = "-10% of Premium Entry", color = CrimsonBright)
                RuleRow(label = "Breakeven Trigger", value = "+10 Points Gain ➔ SL to Cost", color = Cyan)
                RuleRow(label = "Partial Profit Target", value = "+15 Points Gain ➔ 50% Qty Exit", color = EmeraldBright)
                RuleRow(label = "Runner Trailing SL", value = "+20 Points Gain ➔ Trail to +10 pts", color = Emerald)

                Divider(color = BorderSubtle, thickness = 1.dp)

                // Rule Group: Session Filter
                RiskSectionHeader(title = "INTRADAY TRADING WINDOWS")
                RuleRow(label = "Morning Active Scalp", value = "09:30 - 12:00 IST", color = Emerald)
                RuleRow(label = "Afternoon Active Scalp", value = "13:30 - 15:10 IST", color = Emerald)
                RuleRow(label = "Lunch Blackout", value = "12:00 - 13:30 IST (No Orders)", color = TextDim)

                Spacer(modifier = Modifier.height(6.dp))

                HorizontalDivider(color = BorderSubtle, thickness = 1.dp)

                // Project Configuration & Switch
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    RiskSectionHeader(title = "CONNECTED FIREBASE PROJECT")
                    Text(
                        text = if (projectId.isNotBlank()) "Project ID: $projectId" else "Project ID: Not Configured",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    OutlinedButton(
                        onClick = {
                            onDismissRequest()
                            onSwitchProject()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(6.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
                    ) {
                        Text(
                            text = "CHANGE FIREBASE PROJECT / RE-LOGIN",
                            color = Amber,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                HorizontalDivider(color = BorderSubtle, thickness = 1.dp)

                Button(
                    onClick = onDismissRequest,
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldDark),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .border(1.dp, Emerald, RoundedCornerShape(8.dp))
                ) {
                    Text(
                        text = "CONFIRM & CLOSE",
                        color = EmeraldBright,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
private fun RiskSectionHeader(title: String) {
    Text(
        text = title,
        color = Cyan,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        letterSpacing = 1.sp
    )
}

@Composable
private fun RuleRow(
    label: String,
    value: String,
    color: Color = TextPrimary
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = TextMuted,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = value,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace
        )
    }
}

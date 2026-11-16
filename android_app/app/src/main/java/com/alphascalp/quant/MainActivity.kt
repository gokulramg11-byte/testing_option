package com.alphascalp.quant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alphascalp.quant.data.FirestoreSyncRepository
import com.alphascalp.quant.data.TelemetryState
import com.alphascalp.quant.data.TradeRecord
import com.alphascalp.quant.ui.CockpitScreen
import com.alphascalp.quant.ui.LoginScreen
import com.alphascalp.quant.ui.RiskRulesDialog
import com.alphascalp.quant.ui.TradeLogScreen
import com.alphascalp.quant.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AlphaScalpTheme {
                MainAppScaffold()
            }
        }
    }
}

@Composable
fun MainAppScaffold() {
    val context = LocalContext.current
    val repository = remember { FirestoreSyncRepository(context) }
    val coroutineScope = rememberCoroutineScope()

    var isAuthenticated by remember { mutableStateOf(false) }
    var operatorEmail by remember { mutableStateOf("") }
    var projectId by remember { mutableStateOf(repository.getSavedProjectId()) }
    var currentTab by remember { mutableStateOf(0) }
    var showRulesDialog by remember { mutableStateOf(false) }

    // Live state from Firestore (zero dummy data)
    var telemetry by remember { mutableStateOf(TelemetryState()) }
    var tradeList by remember { mutableStateOf<List<TradeRecord>>(emptyList()) }

    // Real-time synchronization loop with Cloud Workflow
    LaunchedEffect(isAuthenticated, projectId) {
        if (isAuthenticated && projectId.isNotBlank()) {
            while (isActive) {
                try {
                    // 1. Fetch live telemetry document
                    val liveState = repository.fetchLiveState(projectId)
                    if (liveState != null) {
                        telemetry = liveState
                    }

                    // 2. Fetch trade execution records
                    val trades = repository.fetchTradeHistory(projectId)
                    tradeList = trades
                } catch (e: Exception) {
                    // Network or connection pause; maintain current state
                }
                delay(2500) // Poll every 2.5s to preserve quota
            }
        }
    }

    if (!isAuthenticated) {
        LoginScreen(
            initialProjectId = projectId,
            onLoginSuccess = { email, enteredProjectId ->
                operatorEmail = email
                projectId = enteredProjectId
                repository.saveProjectId(enteredProjectId)
                isAuthenticated = true
            }
        )
    } else {
        Scaffold(
            topBar = {
                AppTopBar(
                    heartbeat = telemetry.heartbeat,
                    latencyMs = telemetry.latencyMs,
                    mode = telemetry.mode,
                    onOpenRules = { showRulesDialog = true }
                )
            },
            bottomBar = {
                AppBottomBar(
                    selectedTab = currentTab,
                    onTabSelected = { currentTab = it }
                )
            },
            containerColor = Canvas
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when (currentTab) {
                    0 -> CockpitScreen(
                        telemetry = telemetry,
                        onOpenRules = { showRulesDialog = true },
                        onEmergencyKill = {
                            coroutineScope.launch {
                                repository.sendEmergencyKillSwitch(projectId)
                                telemetry = telemetry.copy(
                                    isArmed = false,
                                    activePosition = null
                                )
                            }
                        },
                        onToggleArm = { newArmedState ->
                            coroutineScope.launch {
                                repository.toggleArming(projectId, newArmedState)
                                telemetry = telemetry.copy(isArmed = newArmedState)
                            }
                        }
                    )
                    1 -> SentinelRulesScreen(
                        telemetry = telemetry,
                        onOpenModal = { showRulesDialog = true }
                    )
                    2 -> TradeLogScreen(
                        trades = tradeList
                    )
                }

                if (showRulesDialog) {
                    RiskRulesDialog(onDismissRequest = { showRulesDialog = false })
                }
            }
        }
    }
}

@Composable
private fun AppTopBar(
    heartbeat: String,
    latencyMs: Int,
    mode: String,
    onOpenRules: () -> Unit
) {
    Surface(
        color = SurfaceLowest,
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = BorderSubtle)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "⚡ NIFTY // SCALP",
                    color = EmeraldBright,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Box(
                    modifier = Modifier
                        .background(if (mode == "LIVE") CrimsonDark else AmberDark, MaterialTheme.shapes.small)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = mode,
                        color = if (mode == "LIVE") CrimsonBright else Amber,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .background(SurfaceLow, MaterialTheme.shapes.small)
                        .border(1.dp, BorderSubtle, MaterialTheme.shapes.small)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (heartbeat == "ONLINE") "● ONLINE ${latencyMs}ms" else "● $heartbeat",
                        color = if (heartbeat == "ONLINE") EmeraldBright else Amber,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
private fun AppBottomBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    NavigationBar(
        containerColor = SurfaceLowest,
        tonalElevation = 0.dp,
        modifier = Modifier.border(1.dp, BorderSubtle)
    ) {
        val items = listOf(
            Triple(0, "COCKPIT", Icons.Default.Dashboard),
            Triple(1, "SENTINEL", Icons.Default.Security),
            Triple(2, "TRADE LOGS", Icons.Default.Assessment)
        )

        items.forEach { (index, title, icon) ->
            val isSelected = selectedTab == index
            NavigationBarItem(
                selected = isSelected,
                onClick = { onTabSelected(index) },
                icon = {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = if (isSelected) EmeraldBright else TextDim
                    )
                },
                label = {
                    Text(
                        text = title,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) EmeraldBright else TextDim
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = Surface
                )
            )
        }
    }
}

@Composable
fun SentinelRulesScreen(
    telemetry: TelemetryState,
    onOpenModal: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Canvas)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "SENTINEL GATEKEEPER MATRIX",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "7-Point Execution Pre-conditions (Closed Candles)",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            Button(
                onClick = onOpenModal,
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceLow),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.border(1.dp, BorderAccent, MaterialTheme.shapes.small)
            ) {
                Text(
                    text = "RULES",
                    color = Cyan,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        telemetry.gates.forEach { gate ->
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceLow),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderSubtle, MaterialTheme.shapes.small)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = gate.name,
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = gate.value,
                            color = TextMuted,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Box(
                        modifier = Modifier
                            .background(if (gate.isPass) EmeraldDark else Surface, MaterialTheme.shapes.small)
                            .border(1.dp, if (gate.isPass) Emerald else BorderSubtle, MaterialTheme.shapes.small)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (gate.isPass) "PASS" else gate.label,
                            color = if (gate.isPass) EmeraldBright else TextDim,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

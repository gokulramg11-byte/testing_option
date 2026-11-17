package com.alphascalp.quant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alphascalp.quant.data.FirestoreSyncRepository
import com.alphascalp.quant.data.TelemetryState
import com.alphascalp.quant.data.TradeRecord
import com.alphascalp.quant.data.TradingSession
import com.alphascalp.quant.ui.CockpitScreen
import com.alphascalp.quant.ui.LoginScreen
import com.alphascalp.quant.ui.RiskRulesDialog
import com.alphascalp.quant.ui.TradeLogScreen
import com.alphascalp.quant.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

val AVAILABLE_SESSIONS = listOf(
    TradingSession("NSE_NIFTY", "NIFTY (NSE)", "NSE", "NIFTY", "MARKET"),
    TradingSession("MCX_CRUDEOIL", "CRUDE OIL (MCX)", "MCX", "CRUDEOIL", "LIMIT"),
    TradingSession("MCX_GOLDM", "GOLDM (MCX)", "MCX", "GOLDM", "LIMIT")
)

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

    // Session selection (time-aware: 9:00-15:30 -> NSE_NIFTY, else -> MCX_CRUDEOIL)
    var userManuallySelectedSession by remember { mutableStateOf(false) }
    var selectedSessionId by remember {
        val istHour = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Kolkata")).get(java.util.Calendar.HOUR_OF_DAY)
        val defaultId = if (istHour in 9..15) "NSE_NIFTY" else "MCX_CRUDEOIL"
        mutableStateOf(defaultId)
    }
    var sessionHeartbeats by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    // Live state from Firestore (zero dummy data)
    var telemetry by remember { mutableStateOf(TelemetryState()) }
    var tradeList by remember { mutableStateOf<List<TradeRecord>>(emptyList()) }

    // Real-time synchronization loop with Cloud Workflow
    LaunchedEffect(isAuthenticated, projectId, selectedSessionId) {
        if (isAuthenticated && projectId.isNotBlank()) {
            while (isActive) {
                try {
                    // 1. Fetch live telemetry document for selected session
                    val liveState = repository.fetchLiveState(projectId, selectedSessionId)
                    if (liveState != null) {
                        telemetry = liveState
                        sessionHeartbeats = sessionHeartbeats + (selectedSessionId to liveState.heartbeat)
                    }

                    // 2. Fetch trade execution records
                    val trades = repository.fetchTradeHistory(projectId)
                    tradeList = trades

                    // 3. Update heartbeat of other sessions
                    for (s in AVAILABLE_SESSIONS) {
                        if (s.id != selectedSessionId) {
                            val other = repository.fetchLiveState(projectId, s.id)
                            if (other != null) {
                                sessionHeartbeats = sessionHeartbeats + (s.id to other.heartbeat)
                            }
                        }
                    }

                    // 4. Smart Auto-Switch: If current view is in STANDBY but another session is ONLINE,
                    // auto-switch immediately to show the active live market session (unless user explicitly selected)!
                    if (!userManuallySelectedSession && telemetry.heartbeat == "STANDBY") {
                        val onlineSession = AVAILABLE_SESSIONS.firstOrNull { sessionHeartbeats[it.id] == "ONLINE" }
                        if (onlineSession != null && onlineSession.id != selectedSessionId) {
                            selectedSessionId = onlineSession.id
                        }
                    }
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
                Column {
                    AppTopBar(
                        title = "⚡ ${telemetry.symbol.ifBlank { "NIFTY" }} // SCALP",
                        heartbeat = telemetry.heartbeat,
                        latencyMs = telemetry.latencyMs,
                        mode = telemetry.mode,
                        onOpenRules = { showRulesDialog = true }
                    )
                    MarketSessionTabs(
                        sessions = AVAILABLE_SESSIONS,
                        selectedSessionId = selectedSessionId,
                        sessionHeartbeats = sessionHeartbeats,
                        onSelectSession = { newId ->
                            userManuallySelectedSession = true
                            selectedSessionId = newId
                            coroutineScope.launch {
                                val s = repository.fetchLiveState(projectId, newId)
                                if (s != null) telemetry = s
                            }
                        }
                    )
                }
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
                                repository.sendEmergencyKillSwitch(projectId, selectedSessionId)
                                telemetry = telemetry.copy(
                                    isArmed = false,
                                    activePosition = null
                                )
                            }
                        },
                        onToggleArm = { newArmedState ->
                            coroutineScope.launch {
                                repository.toggleArming(projectId, selectedSessionId, newArmedState)
                                telemetry = telemetry.copy(isArmed = newArmedState)
                            }
                        },
                        combinedDayPnl = tradeList.sumOf { it.pnl }
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
                    RiskRulesDialog(
                        onDismissRequest = { showRulesDialog = false },
                        projectId = projectId,
                        onSwitchProject = { isAuthenticated = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun AppTopBar(
    title: String = "⚡ NIFTY // SCALP",
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
                    text = title,
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

@Composable
fun MarketSessionTabs(
    sessions: List<TradingSession>,
    selectedSessionId: String,
    sessionHeartbeats: Map<String, String>,
    onSelectSession: (String) -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "tabPulse")
    val tabPulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "tabPulseAlpha"
    )

    Surface(
        color = SurfaceLowest,
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = BorderSubtle)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            sessions.forEach { session ->
                val isSelected = session.id == selectedSessionId
                val hb = sessionHeartbeats[session.id] ?: "STANDBY"
                val isOnline = hb == "ONLINE"

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) SurfaceHigh else SurfaceLow)
                        .border(
                            1.dp,
                            if (isSelected) (if (session.segment == "MCX") Amber else Cyan) else BorderSubtle,
                            RoundedCornerShape(6.dp)
                        )
                        .clickable { onSelectSession(session.id) }
                        .padding(vertical = 7.dp, horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        // Live pulsing indicator
                        if (isOnline) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(
                                        color = EmeraldBright.copy(alpha = tabPulseAlpha),
                                        shape = CircleShape
                                    )
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .background(
                                        color = TextDim,
                                        shape = CircleShape
                                    )
                            )
                        }
                        Text(
                            text = session.label,
                            color = if (isSelected) TextPrimary else TextMuted,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

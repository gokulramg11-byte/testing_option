package com.alphascalp.quant.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class FirestoreSyncRepository(private val context: Context) {
    companion object {
        private const val TAG = "FirestoreSync"
        private const val PREFS_NAME = "alphascalp_prefs"
        private const val KEY_PROJECT_ID = "firebase_project_id"
        private const val DEFAULT_PROJECT_ID = "alphascalp-quant"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getSavedProjectId(): String {
        return prefs.getString(KEY_PROJECT_ID, DEFAULT_PROJECT_ID) ?: DEFAULT_PROJECT_ID
    }

    fun saveProjectId(projectId: String) {
        prefs.edit().putString(KEY_PROJECT_ID, projectId.trim()).apply()
    }

    /**
     * Fetches live telemetry document directly from Firestore REST API:
     * Tries 'sessions/{sessionId}' first, falls back to 'live_state/current' for legacy single-session.
     */
    suspend fun fetchLiveState(projectId: String, sessionId: String = "NSE_NIFTY"): TelemetryState? = withContext(Dispatchers.IO) {
        try {
            var urlString = "https://firestore.googleapis.com/v1/projects/$projectId/databases/(default)/documents/sessions/$sessionId"
            var conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("Accept", "application/json")
            }

            var responseCode = conn.responseCode
            // Fallback to legacy path if session document not found yet on NSE
            if (responseCode == 404 && sessionId == "NSE_NIFTY") {
                urlString = "https://firestore.googleapis.com/v1/projects/$projectId/databases/(default)/documents/live_state/current"
                conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 5000
                    readTimeout = 5000
                    setRequestProperty("Accept", "application/json")
                }
                responseCode = conn.responseCode
            }

            if (responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val response = reader.readText()
                reader.close()
                return@withContext parseFirestoreTelemetry(response, sessionId)
            } else {
                Log.w(TAG, "Firestore sessions/$sessionId fetch HTTP $responseCode")
                val (status, detail) = when (responseCode) {
                    403 -> Pair("RULES 403", "Firestore Rules block read. In Firebase Console > Firestore Database > Rules, set: allow read, write: if true;")
                    404 -> Pair("STANDBY", "Session '$sessionId' awaiting cloud bot execution.")
                    else -> Pair("HTTP $responseCode", "Server returned HTTP $responseCode for project '$projectId'.")
                }
                return@withContext TelemetryState(
                    heartbeat = status,
                    sessionId = sessionId,
                    gates = listOf(
                        GateCheck(1, "Session Status", false, status, detail)
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching live state for $sessionId: ${e.message}")
            return@withContext TelemetryState(
                heartbeat = "CONN ERR",
                sessionId = sessionId,
                gates = listOf(
                    GateCheck(1, "Network Status", false, "ERROR", e.localizedMessage ?: "Cannot connect to Firestore")
                )
            )
        }
    }

    /**
     * Fetches real trade history documents directly from Firestore REST API:
     * Queries 'trades' root collection first, then falls back to 'trade_history'.
     */
    suspend fun fetchTradeHistory(projectId: String): List<TradeRecord> = withContext(Dispatchers.IO) {
        try {
            var urlString = "https://firestore.googleapis.com/v1/projects/$projectId/databases/(default)/documents/trades"
            var conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("Accept", "application/json")
            }

            var responseCode = conn.responseCode
            if (responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val response = reader.readText()
                reader.close()
                val parsed = parseFirestoreTrades(response)
                if (parsed.isNotEmpty()) return@withContext parsed
            }

            // Fallback to trade_history collection
            urlString = "https://firestore.googleapis.com/v1/projects/$projectId/databases/(default)/documents/trade_history"
            conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("Accept", "application/json")
            }
            responseCode = conn.responseCode
            if (responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val response = reader.readText()
                reader.close()
                return@withContext parseFirestoreTrades(response)
            }
            return@withContext emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching trade history from Firestore: ${e.message}")
            return@withContext emptyList()
        }
    }

    /**
     * Sends Kill Switch command to Firestore 'controls/{sessionId}' (and 'control/settings'):
     */
    suspend fun sendEmergencyKillSwitch(projectId: String, sessionId: String = "NSE_NIFTY"): Boolean = withContext(Dispatchers.IO) {
        try {
            val urlString = "https://firestore.googleapis.com/v1/projects/$projectId/databases/(default)/documents/controls/$sessionId?updateMask.fieldPaths=kill_switch&updateMask.fieldPaths=armed"
            val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "PATCH"
                connectTimeout = 5000
                readTimeout = 5000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }

            val body = JSONObject().apply {
                val fields = JSONObject().apply {
                    put("kill_switch", JSONObject().put("booleanValue", true))
                    put("armed", JSONObject().put("booleanValue", false))
                }
                put("fields", fields)
            }

            val writer = OutputStreamWriter(conn.outputStream)
            writer.write(body.toString())
            writer.flush()
            writer.close()

            val responseCode = conn.responseCode
            Log.i(TAG, "Kill Switch command sent for $sessionId. HTTP $responseCode")

            // Backwards compatibility mirror for legacy NSE
            if (sessionId == "NSE_NIFTY") {
                try {
                    val legacyUrl = "https://firestore.googleapis.com/v1/projects/$projectId/databases/(default)/documents/control/settings?updateMask.fieldPaths=kill_switch&updateMask.fieldPaths=armed"
                    val legConn = (URL(legacyUrl).openConnection() as HttpURLConnection).apply {
                        requestMethod = "PATCH"
                        connectTimeout = 3000
                        readTimeout = 3000
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json")
                    }
                    val legWriter = OutputStreamWriter(legConn.outputStream)
                    legWriter.write(body.toString())
                    legWriter.flush()
                    legWriter.close()
                    legConn.responseCode
                } catch (e: Exception) {
                    // Ignore legacy mirror error
                }
            }

            return@withContext responseCode in 200..299
        } catch (e: Exception) {
            Log.e(TAG, "Error triggering Kill Switch for $sessionId: ${e.message}")
            return@withContext false
        }
    }

    suspend fun toggleArming(projectId: String, sessionId: String = "NSE_NIFTY", isArmed: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val urlString = "https://firestore.googleapis.com/v1/projects/$projectId/databases/(default)/documents/controls/$sessionId?updateMask.fieldPaths=armed"
            val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "PATCH"
                connectTimeout = 5000
                readTimeout = 5000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }

            val body = JSONObject().apply {
                val fields = JSONObject().apply {
                    put("armed", JSONObject().put("booleanValue", isArmed))
                }
                put("fields", fields)
            }

            val writer = OutputStreamWriter(conn.outputStream)
            writer.write(body.toString())
            writer.flush()
            writer.close()

            val responseCode = conn.responseCode
            return@withContext responseCode in 200..299
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling arming state for $sessionId: ${e.message}")
            return@withContext false
        }
    }

    private fun parseFirestoreTelemetry(jsonString: String, fallbackSessionId: String = "NSE_NIFTY"): TelemetryState {
        val root = JSONObject(jsonString)
        val fields = root.optJSONObject("fields") ?: return TelemetryState(heartbeat = "STANDBY", sessionId = fallbackSessionId)

        val heartbeat = getString(fields, "heartbeat", "ONLINE")
        val mode = getString(fields, "mode", "PAPER")
        val armed = getBoolean(fields, "armed", true)
        val latencyMs = getInt(fields, "latency_ms", 12)
        val sessionId = getString(fields, "session_id", fallbackSessionId)
        val segment = getString(fields, "segment", if (sessionId.startsWith("MCX")) "MCX" else "NSE")
        val underlyingName = getString(fields, "underlying_name", if (segment == "MCX") "MCX Commodity" else "Nifty 50")
        val symbol = getString(fields, "symbol", if (segment == "MCX") "CRUDEOIL" else "NIFTY")
        val orderType = getString(fields, "order_type", if (segment == "MCX") "LIMIT" else "MARKET")
        val lotSize = getInt(fields, "lot_size", if (segment == "MCX") 100 else 75)
        val indexName = getString(fields, "index_name", underlyingName)
        val spotLtp = getDouble(fields, "spot_ltp", 0.0)
        val spotChange = getDouble(fields, "spot_change", 0.0)
        val spotChangePct = getDouble(fields, "spot_change_pct", 0.0)
        val netDailyMtm = getDouble(fields, "net_daily_mtm", 0.0)
        val winRate = getDouble(fields, "win_rate", 0.0)
        val totalTrades = getString(fields, "total_trades", "0")
        val consecutiveSl = getInt(fields, "consecutive_sl", 0)
        val maxConsecutiveSl = getInt(fields, "max_consecutive_sl", 3)
        val signalStatus = getString(fields, "signal_status", "NO_TRADE")
        val marketBias = getString(fields, "market_bias", "NEUTRAL")
        val rawReadiness = getInt(fields, "readiness_pct", 0)
        val rawPassing = getInt(fields, "passing_count", 0)

        // Parse 7-Point Gates with Live Proximity
        val gatesList = mutableListOf<GateCheck>()
        val gatesField = fields.optJSONObject("gates")
        val arrayValues = gatesField?.optJSONObject("arrayValue")?.optJSONArray("values")
        if (arrayValues != null) {
            for (i in 0 until arrayValues.length()) {
                val gateObj = arrayValues.optJSONObject(i)?.optJSONObject("mapValue")?.optJSONObject("fields")
                if (gateObj != null) {
                    val passVal = getBoolean(gateObj, "is_pass", false) || getBoolean(gateObj, "pass", false)
                    val prox = getInt(gateObj, "proximity_pct", if (passVal) 100 else 35)
                    val proxText = getString(gateObj, "proximity_text", "")
                    gatesList.add(
                        GateCheck(
                            id = getInt(gateObj, "id", i + 1),
                            name = getString(gateObj, "name", "Gate ${i + 1}"),
                            isPass = passVal,
                            label = getString(gateObj, "label", if (passVal) "PASS" else "$prox%"),
                            value = getString(gateObj, "value", ""),
                            proximityPct = prox,
                            proximityText = proxText
                        )
                    )
                }
            }
        }

        val calculatedPassing = if (rawPassing > 0) rawPassing else gatesList.count { it.isPass }
        val calculatedReadiness = if (rawReadiness > 0) rawReadiness else if (gatesList.isNotEmpty()) (gatesList.sumOf { it.proximityPct } / gatesList.size) else (calculatedPassing * 100 / 7)

        // Parse Active ATM Option Contracts
        var atmCeContract: AtmContract? = null
        val ceField = fields.optJSONObject("atm_ce")?.optJSONObject("mapValue")?.optJSONObject("fields")
        if (ceField != null) {
            atmCeContract = AtmContract(
                strike = getDouble(ceField, "strike", 0.0),
                symbol = getString(ceField, "symbol", ""),
                ltp = getDouble(ceField, "ltp", 0.0),
                vwap = getDouble(ceField, "vwap", 0.0),
                oiChangePct = getDouble(ceField, "oi_change_pct", 0.0)
            )
        }

        var atmPeContract: AtmContract? = null
        val peField = fields.optJSONObject("atm_pe")?.optJSONObject("mapValue")?.optJSONObject("fields")
        if (peField != null) {
            atmPeContract = AtmContract(
                strike = getDouble(peField, "strike", 0.0),
                symbol = getString(peField, "symbol", ""),
                ltp = getDouble(peField, "ltp", 0.0),
                vwap = getDouble(peField, "vwap", 0.0),
                oiChangePct = getDouble(peField, "oi_change_pct", 0.0)
            )
        }

        // Parse Active Position
        var activePos: ActivePosition? = null
        val activeField = fields.optJSONObject("active_position")?.optJSONObject("mapValue")?.optJSONObject("fields")
        if (activeField != null && getBoolean(activeField, "active", false)) {
            val timeSl = getString(activeField, "time_sl_status", "")
            val timeSlResolved = if (timeSl.isNotBlank()) timeSl else getString(activeField, "time_sl_remaining", "Scanning")
            activePos = ActivePosition(
                strike = getString(activeField, "strike", "ATM OPTION"),
                optionType = getString(activeField, "option_type", "CE"),
                entryPrice = getDouble(activeField, "entry_price", 0.0),
                currentLtp = getDouble(activeField, "current_ltp", 0.0),
                slPrice = getDouble(activeField, "sl_price", 0.0),
                pnl = getDouble(activeField, "pnl", 0.0),
                pointsCaptured = getDouble(activeField, "points_captured", 0.0),
                lots = getInt(activeField, "lots", 1),
                qty = getInt(activeField, "qty", 75),
                timeSlStatus = timeSlResolved,
                isBreakeven = getBoolean(activeField, "is_breakeven", false),
                isPartial = getBoolean(activeField, "is_partial", false)
            )
        }

        return TelemetryState(
            heartbeat = heartbeat,
            mode = mode,
            isArmed = armed,
            latencyMs = latencyMs,
            sessionId = sessionId,
            segment = segment,
            underlyingName = underlyingName,
            symbol = symbol,
            orderType = orderType,
            lotSize = lotSize,
            indexName = indexName,
            spotLtp = spotLtp,
            spotChange = spotChange,
            spotChangePct = spotChangePct,
            netDailyMtm = netDailyMtm,
            winRate = winRate,
            totalTrades = totalTrades,
            consecutiveSl = consecutiveSl,
            maxConsecutiveSl = maxConsecutiveSl,
            signalStatus = signalStatus,
            marketBias = marketBias,
            readinessPct = calculatedReadiness,
            passingCount = calculatedPassing,
            atmCe = atmCeContract,
            atmPe = atmPeContract,
            gates = if (gatesList.isNotEmpty()) gatesList else TelemetryState().gates,
            activePosition = activePos
        )
    }

    private fun parseFirestoreTrades(jsonString: String): List<TradeRecord> {
        val root = JSONObject(jsonString)
        val documents = root.optJSONArray("documents") ?: return emptyList()
        val trades = mutableListOf<TradeRecord>()

        for (i in 0 until documents.length()) {
            val doc = documents.optJSONObject(i) ?: continue
            val fields = doc.optJSONObject("fields") ?: continue
            val sesId = getString(fields, "session_id", "NSE_NIFTY")
            val seg = getString(fields, "segment", if (sesId.startsWith("MCX")) "MCX" else "NSE")
            trades.add(
                TradeRecord(
                    id = getString(fields, "id", "TR-${i + 1}"),
                    strike = getString(fields, "strike", "OPTION"),
                    optionType = getString(fields, "option_type", "CE"),
                    qty = getInt(fields, "qty", 75),
                    entryPrice = getDouble(fields, "entry_price", 0.0),
                    exitPrice = getDouble(fields, "exit_price", 0.0),
                    entryTime = getString(fields, "entry_time", "--"),
                    exitTime = getString(fields, "exit_time", "--"),
                    points = getDouble(fields, "points", 0.0),
                    pnl = getDouble(fields, "pnl", 0.0),
                    exitReason = getString(fields, "exit_reason", "EXIT"),
                    sessionId = sesId,
                    segment = seg
                )
            )
        }
        return trades
    }

    private fun getString(obj: JSONObject, key: String, default: String): String {
        val f = obj.optJSONObject(key) ?: return default
        return when {
            f.has("stringValue") -> f.optString("stringValue", default)
            f.has("integerValue") -> f.optString("integerValue", default)
            f.has("doubleValue") -> f.optString("doubleValue", default)
            f.has("booleanValue") -> f.optBoolean("booleanValue").toString()
            else -> default
        }
    }

    private fun getDouble(obj: JSONObject, key: String, default: Double): Double {
        val f = obj.optJSONObject(key) ?: return default
        if (f.has("doubleValue")) return f.optDouble("doubleValue", default)
        if (f.has("integerValue")) return f.optString("integerValue").toDoubleOrNull() ?: default
        if (f.has("stringValue")) return f.optString("stringValue").toDoubleOrNull() ?: default
        return default
    }

    private fun getInt(obj: JSONObject, key: String, default: Int): Int {
        val f = obj.optJSONObject(key) ?: return default
        if (f.has("integerValue")) return f.optString("integerValue").toIntOrNull() ?: default
        if (f.has("doubleValue")) return f.optDouble("doubleValue", default.toDouble()).toInt()
        if (f.has("stringValue")) return f.optString("stringValue").toIntOrNull() ?: default
        return default
    }

    private fun getBoolean(obj: JSONObject, key: String, default: Boolean): Boolean {
        val f = obj.optJSONObject(key) ?: return default
        if (f.has("booleanValue")) return f.optBoolean("booleanValue", default)
        if (f.has("stringValue")) return f.optString("stringValue").toBoolean()
        return default
    }
}

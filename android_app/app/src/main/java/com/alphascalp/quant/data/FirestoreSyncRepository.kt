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
     * GET https://firestore.googleapis.com/v1/projects/{projectId}/databases/(default)/documents/live_state/current
     */
    suspend fun fetchLiveState(projectId: String): TelemetryState? = withContext(Dispatchers.IO) {
        try {
            val urlString = "https://firestore.googleapis.com/v1/projects/$projectId/databases/(default)/documents/live_state/current"
            val url = URL(urlString)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.setRequestProperty("Accept", "application/json")

            val responseCode = conn.responseCode
            if (responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val response = reader.readText()
                reader.close()
                return@withContext parseFirestoreTelemetry(response)
            } else {
                Log.w(TAG, "Firestore live_state fetch HTTP $responseCode")
                val (status, detail) = when (responseCode) {
                    403 -> Pair("RULES 403", "Firestore Rules block read. In Firebase Console > Firestore Database > Rules, set: allow read, write: if true;")
                    404 -> Pair("PROJECT 404", "Project '$projectId' not found or 'live_state/current' not written yet. Check Project ID.")
                    else -> Pair("HTTP $responseCode", "Server returned HTTP $responseCode for project '$projectId'.")
                }
                return@withContext TelemetryState(
                    heartbeat = status,
                    gates = listOf(
                        GateCheck(1, "Cloud Bridge Diagnostics", false, status, detail)
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching live state from Firestore: ${e.message}")
            return@withContext TelemetryState(
                heartbeat = "CONN ERR",
                gates = listOf(
                    GateCheck(1, "Network Status", false, "ERROR", e.localizedMessage ?: "Cannot connect to Firestore")
                )
            )
        }
    }

    /**
     * Fetches real trade history documents directly from Firestore REST API:
     * GET https://firestore.googleapis.com/v1/projects/{projectId}/databases/(default)/documents/trade_history
     */
    suspend fun fetchTradeHistory(projectId: String): List<TradeRecord> = withContext(Dispatchers.IO) {
        try {
            val urlString = "https://firestore.googleapis.com/v1/projects/$projectId/databases/(default)/documents/trade_history"
            val url = URL(urlString)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.setRequestProperty("Accept", "application/json")

            val responseCode = conn.responseCode
            if (responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val response = reader.readText()
                reader.close()
                return@withContext parseFirestoreTrades(response)
            } else {
                Log.w(TAG, "Firestore trade_history fetch HTTP $responseCode")
                return@withContext emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching trade history from Firestore: ${e.message}")
            return@withContext emptyList()
        }
    }

    /**
     * Sends Kill Switch or Arm command to Firestore 'control/settings':
     * PATCH https://firestore.googleapis.com/v1/projects/{projectId}/databases/(default)/documents/control/settings?updateMask.fieldPaths=kill_switch&updateMask.fieldPaths=armed
     */
    suspend fun sendEmergencyKillSwitch(projectId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val urlString = "https://firestore.googleapis.com/v1/projects/$projectId/databases/(default)/documents/control/settings?updateMask.fieldPaths=kill_switch&updateMask.fieldPaths=armed"
            val url = URL(urlString)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "PATCH"
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")

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
            Log.i(TAG, "Kill Switch command sent. Firestore HTTP $responseCode")
            return@withContext responseCode in 200..299
        } catch (e: Exception) {
            Log.e(TAG, "Error triggering Kill Switch on Firestore: ${e.message}")
            return@withContext false
        }
    }

    suspend fun toggleArming(projectId: String, isArmed: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val urlString = "https://firestore.googleapis.com/v1/projects/$projectId/databases/(default)/documents/control/settings?updateMask.fieldPaths=armed"
            val url = URL(urlString)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "PATCH"
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")

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
            Log.e(TAG, "Error toggling arming state: ${e.message}")
            return@withContext false
        }
    }

    private fun parseFirestoreTelemetry(jsonString: String): TelemetryState {
        val root = JSONObject(jsonString)
        val fields = root.optJSONObject("fields") ?: return TelemetryState(heartbeat = "STANDBY")

        val heartbeat = getString(fields, "heartbeat", "ONLINE")
        val mode = getString(fields, "mode", "PAPER")
        val armed = getBoolean(fields, "armed", true)
        val latencyMs = getInt(fields, "latency_ms", 12)
        val indexName = getString(fields, "index_name", "NIFTY")
        val spotLtp = getDouble(fields, "spot_ltp", 0.0)
        val spotChange = getDouble(fields, "spot_change", 0.0)
        val spotChangePct = getDouble(fields, "spot_change_pct", 0.0)
        val netDailyMtm = getDouble(fields, "net_daily_mtm", 0.0)
        val winRate = getDouble(fields, "win_rate", 0.0)
        val totalTrades = getString(fields, "total_trades", "0")
        val consecutiveSl = getInt(fields, "consecutive_sl", 0)
        val maxConsecutiveSl = getInt(fields, "max_consecutive_sl", 3)
        val signalStatus = getString(fields, "signal_status", "NO_TRADE")

        // Parse 7-Point Gates
        val gatesList = mutableListOf<GateCheck>()
        val gatesField = fields.optJSONObject("gates")
        val arrayValues = gatesField?.optJSONObject("arrayValue")?.optJSONArray("values")
        if (arrayValues != null) {
            for (i in 0 until arrayValues.length()) {
                val gateObj = arrayValues.optJSONObject(i)?.optJSONObject("mapValue")?.optJSONObject("fields")
                if (gateObj != null) {
                    val passVal = getBoolean(gateObj, "is_pass", false) || getBoolean(gateObj, "pass", false)
                    gatesList.add(
                        GateCheck(
                            id = getInt(gateObj, "id", i + 1),
                            name = getString(gateObj, "name", "Gate ${i + 1}"),
                            isPass = passVal,
                            label = getString(gateObj, "label", if (passVal) "PASS" else "WAIT"),
                            value = getString(gateObj, "value", "")
                        )
                    )
                }
            }
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
                    exitReason = getString(fields, "exit_reason", "EXIT")
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

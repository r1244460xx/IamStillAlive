package com.imsa.app.util

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object GuardianAuditLogger {

    private const val TAG = "GuardianAudit"
    private const val FILE_NAME = "guardian_audit_log.json"
    private const val MAX_ENTRIES = 100

    private val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
    private val displayFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    data class AuditRecord(
        val timestamp: String,
        val epochMillis: Long,
        val eventType: String,
        val detail: String
    ) {
        fun toJsonObject(): JSONObject = JSONObject().apply {
            put("timestamp", timestamp)
            put("epochMillis", epochMillis)
            put("eventType", eventType)
            put("detail", detail)
        }

        companion object {
            fun fromJsonObject(json: JSONObject): AuditRecord = AuditRecord(
                timestamp = json.optString("timestamp", ""),
                epochMillis = json.optLong("epochMillis", 0L),
                eventType = json.optString("eventType", "UNKNOWN"),
                detail = json.optString("detail", "")
            )
        }
    }

    private val records = mutableListOf<AuditRecord>()
    private var isLoaded = false

    @Synchronized
    private fun ensureLoaded(context: Context) {
        if (isLoaded) return
        try {
            val file = File(context.filesDir, FILE_NAME)
            if (file.exists()) {
                val content = file.readText()
                if (content.isNotBlank()) {
                    val array = JSONArray(content)
                    records.clear()
                    for (i in 0 until array.length()) {
                        records.add(AuditRecord.fromJsonObject(array.getJSONObject(i)))
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ 讀取審計日誌異常: ${e.localizedMessage}")
        } finally {
            isLoaded = true
        }
    }

    @Synchronized
    private fun save(context: Context) {
        try {
            val file = File(context.filesDir, FILE_NAME)
            val array = JSONArray()
            records.forEach { array.put(it.toJsonObject()) }
            file.writeText(array.toString())
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ 儲存審計日誌異常: ${e.localizedMessage}")
        }
    }

    @Synchronized
    fun record(context: Context, eventType: String, detail: String) {
        ensureLoaded(context)
        val now = LocalDateTime.now()
        val entry = AuditRecord(
            timestamp = now.format(timeFormatter),
            epochMillis = System.currentTimeMillis(),
            eventType = eventType,
            detail = detail
        )
        records.add(entry)
        while (records.size > MAX_ENTRIES) {
            records.removeAt(0)
        }
        save(context)
        Log.d(TAG, "📝 [AUDIT] [${entry.eventType}] ${entry.detail}")
    }

    @Synchronized
    fun getFormattedSummary(context: Context): String {
        ensureLoaded(context)
        val sb = StringBuilder()
        sb.appendLine("================================================================")
        sb.appendLine("🛡️ IMSA 守護系統狀態與審計軌跡 (Diagnostics Report)")
        sb.appendLine("================================================================")
        sb.appendLine("🕒 傾印時間: ${LocalDateTime.now().format(displayFormatter)}")
        sb.appendLine("⏱️ 目前預警間隔: ${HeartbeatSyncManager.currentPreAlertDelayMs / 3600000} 小時")
        sb.appendLine("📊 審計紀錄筆數: ${records.size} (上限 $MAX_ENTRIES 筆)")
        sb.appendLine("----------------------------------------------------------------")

        if (records.isEmpty()) {
            sb.appendLine("（尚無任何審計日誌紀錄）")
        } else {
            val recent = records.takeLast(30).reversed()
            recent.forEachIndexed { index, record ->
                val icon = when (record.eventType) {
                    "UNLOCK_DETECTED" -> "📱"
                    "ALARM_SCHEDULED" -> "⏰"
                    "CARD_DISMISSED" -> "🔕"
                    "PRE_ALERT_TRIGGERED" -> "🔔"
                    "CHECK_IN_SUCCESS" -> "💚"
                    "CHECK_IN_FAILED" -> "❌"
                    "ALARM_CANCELLED" -> "🛑"
                    else -> "🔹"
                }
                sb.appendLine(String.format("#%02d [%s] %s %-18s | %s", 
                    index + 1, record.timestamp, icon, record.eventType, record.detail))
            }
        }
        sb.appendLine("================================================================")
        return sb.toString()
    }

    @Synchronized
    fun clearLogs(context: Context) {
        ensureLoaded(context)
        records.clear()
        save(context)
        Log.i(TAG, "🧹 審計日誌已完全清空")
    }

    fun formatEpochTime(epochMillis: Long): String {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault())
            .format(displayFormatter)
    }
}

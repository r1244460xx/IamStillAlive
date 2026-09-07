package com.imsa.app.receiver

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.imsa.app.data.SessionManager
import com.imsa.app.util.GuardianAuditLogger
import com.imsa.app.util.HeartbeatSyncManager

class GuardianDebugReceiver : BroadcastReceiver() {

    companion object {
        const val TAG = "GuardianDebug"
        const val ACTION_DUMP_STATUS = "com.imsa.app.action.DUMP_STATUS"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (action != ACTION_DUMP_STATUS) return

        if (intent.getBooleanExtra("clear", false)) {
            GuardianAuditLogger.clearLogs(context)
            val msg = "✅ IMSA 審計日誌已清空！"
            Log.i(TAG, msg)
            resultData = msg
            resultCode = Activity.RESULT_OK
            return
        }

        val session = SessionManager(context)
        val sb = StringBuilder()
        sb.append(GuardianAuditLogger.getFormattedSummary(context))
        sb.appendLine("👤 使用者資訊: ID=${session.userId}, 門號=${session.phone}, 狀態=${session.safetyStatus}")
        sb.appendLine("📡 連線狀態: 斷線中=${session.isDisconnected}, 伺服器=${session.serverUrl}")
        sb.appendLine("⏳ 下次截止時間: ${session.nextDeadline ?: "無"}")
        val pending = session.getPendingCheckIn()
        sb.appendLine("📦 本地暫存心跳: ${if (pending != null) "有 (時間=${pending.timestamp}, 類型=${pending.networkType})" else "無"}")
        sb.appendLine("================================================================")

        val output = sb.toString()
        Log.i(TAG, "\n" + output)

        resultData = output
        resultCode = Activity.RESULT_OK
    }
}

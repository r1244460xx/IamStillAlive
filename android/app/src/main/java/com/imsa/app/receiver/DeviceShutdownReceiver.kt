package com.imsa.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.imsa.app.util.HeartbeatSyncManager

class DeviceShutdownReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        Log.i("DeviceShutdownReceiver", "⚡ 收到系統廣播事件: $action")

        if (action == Intent.ACTION_SHUTDOWN ||
            action == Intent.ACTION_BATTERY_LOW ||
            action == "android.intent.action.QUICKBOOT_POWEROFF" ||
            action == "com.htc.intent.action.QUICKBOOT_POWEROFF" ||
            action == "com.imsa.app.action.TEST_SHUTDOWN"
        ) {
            Log.w("DeviceShutdownReceiver", "🔌 [系統關機/低電量] 偵測到手機即將關機或低電量 ($action)，立即發送關機心跳報備！")
            HeartbeatSyncManager.handleDeviceShutdown(context)
        }
    }
}
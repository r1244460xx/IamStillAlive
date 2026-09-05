package com.imsa.app.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import com.imsa.app.data.SessionManager
import com.imsa.app.worker.SafetyCheckInWorker

import com.imsa.app.util.HeartbeatSyncManager

class SafetyGuardianAccessibilityService : AccessibilityService() {

    private var unlockReceiver: BroadcastReceiver? = null
    private var lastUnlockTime: Long = 0L

    companion object {
        private const val TAG = "SafetyGuardianAcc"
        private const val THROTTLE_MS = 15_000L // 15 秒防抖節流
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "🛡️ IMSA 無障礙守護服務已創建 (onCreate)")
        HeartbeatSyncManager.init(applicationContext)
        registerUnlockReceiver()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "🛡️ IMSA 無障礙守護服務已連接啟動！(系統直接綁定，抗滑掉、零通知欄干擾)")
        HeartbeatSyncManager.init(applicationContext)
        registerUnlockReceiver()
    }

    private fun registerUnlockReceiver() {
        if (unlockReceiver != null) return

        unlockReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val action = intent?.action
                val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
                val isKeyguardLocked = keyguardManager?.isKeyguardLocked ?: false

                val isUnlockEvent = when (action) {
                    Intent.ACTION_USER_PRESENT -> true
                    Intent.ACTION_SCREEN_ON -> !isKeyguardLocked // 若無設定鎖定密碼，點亮螢幕即算解鎖
                    else -> false
                }

                if (isUnlockEvent) {
                    val now = System.currentTimeMillis()
                    if (now - lastUnlockTime < THROTTLE_MS) {
                        Log.d(TAG, "⏱️ 偵測到解鎖動作 ($action)，但處於節流冷卻期內（${(now - lastUnlockTime) / 1000}s < 15s），略過此次打卡")
                        return
                    }
                    lastUnlockTime = now
                    Log.i(TAG, "📱 偵測到裝置解鎖事件 ($action, KeyguardLocked=$isKeyguardLocked)！準備觸發無感心跳打卡...")

                    HeartbeatSyncManager.handleScreenUnlock(applicationContext)
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        ContextCompat.registerReceiver(
            this,
            unlockReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )
        Log.i(TAG, "✅ 已成功動態註冊螢幕解鎖廣播監聽 (ACTION_USER_PRESENT, ACTION_SCREEN_ON)")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 本服務僅利用系統綁定機制確保背景常駐與螢幕解鎖廣播監聽，不擷取或分析任何畫面無障礙事件
    }

    override fun onInterrupt() {
        Log.w(TAG, "⚠️ 無障礙服務被中斷")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.w(TAG, "🛑 無障礙守護服務被銷毀，解除廣播監聽")
        unlockReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                Log.e(TAG, "反註冊廣播接收器失敗", e)
            }
            unlockReceiver = null
        }
    }
}

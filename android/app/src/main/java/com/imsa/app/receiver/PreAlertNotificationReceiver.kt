package com.imsa.app.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.imsa.app.data.SessionManager

class PreAlertNotificationReceiver : BroadcastReceiver() {

    companion object {
        const val TAG = "PreAlertReceiver"
        const val CHANNEL_ID = "imsa_pre_alert_warning"
        const val NOTIFICATION_ID = 2201
        const val ACTION_TRIGGER_PRE_ALERT = "com.imsa.app.action.TRIGGER_PRE_ALERT"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        Log.i(TAG, "🔔 [預警廣播] 收到預警定時/推播觸發事件 ($action)")

        val session = SessionManager(context)
        val isForce = intent?.getBooleanExtra("force", false) == true
        if (!session.isLoggedIn() && !isForce) {
            Log.w(TAG, "⚠️ 使用者未登入，略過 22 小時預警通知")
            return
        }

        // 1. 黑屏點亮喚醒 (WakeLock 點亮螢幕 3 秒)
        wakeUpScreen(context)

        // 2. 建立通知渠道 (Android 8.0+)
        createNotificationChannel(context)

        // 3. 點選卡片解鎖直達「手機主桌面」(不進入主 App，保持透明守護)
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            homeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val vibrationPattern = longArrayOf(0, 250, 100, 250)

        val hours = (com.imsa.app.util.HeartbeatSyncManager.currentPreAlertDelayMs / (3600 * 1000L)).toInt()
        val timeDesc = if (hours > 0) "${hours} 小時" else "${com.imsa.app.util.HeartbeatSyncManager.currentPreAlertDelayMs / 1000} 秒"

        // 4. 構建 LINE 風格的高優先級通知卡片 (可在鎖定螢幕上完整顯示)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_def_app_icon)
            .setContentTitle("⚠️ IMSA 健在守護・平安提醒")
            .setContentText("您已長達 $timeDesc 未解鎖手機！請滑動解鎖以確認平安，避免通報緊急聯絡人。")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("您已超過 $timeDesc 未解鎖手機！\n請隨意點選本卡片或直接解鎖手機以報平安，避免後續向緊急聯絡人發出警報。")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // 鎖定螢幕完整顯示卡片
            .setSound(soundUri)
            .setVibrate(vibrationPattern)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)

        Log.i(TAG, "✅ 已成功發送 22 小時平安預警卡片 (響鈴+震動+點亮螢幕)！")
    }

    private fun wakeUpScreen(context: Context) {
        try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
            val wakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                "IMSA:PreAlertWakeLock"
            )
            wakeLock.acquire(3000L) // 點亮 3 秒
            Log.d(TAG, "💡 已觸發黑屏復甦喚醒 (Screen WakeLock)")
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ 螢幕喚醒失敗: ${e.localizedMessage}")
        }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
                val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "IMSA 平安守護提醒",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "當超過 22 小時未解鎖手機時發出平安提醒"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 250, 100, 250)
                    setSound(soundUri, null)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                }
                notificationManager.createNotificationChannel(channel)
                Log.d(TAG, "📡 成功建立 NotificationChannel [$CHANNEL_ID]")
            }
        }
    }
}
package com.imsa.app.util

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log
import com.imsa.app.data.ImsaApiService
import com.imsa.app.data.SessionManager
import com.imsa.app.data.UserCheckInRequest
import com.imsa.app.receiver.PreAlertNotificationReceiver
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object HeartbeatSyncManager {

    private const val TAG = "HeartbeatSyncMgr"
    private const val INITIAL_RETRY_INTERVAL_MS = 10_000L // 初始 10 秒
    private const val MAX_RETRY_INTERVAL_MS = 300_000L     // 最長 5 分鐘 (300 秒)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var retryJob: Job? = null
    private val syncMutex = Mutex()

    private var currentRetryIntervalMs = INITIAL_RETRY_INTERVAL_MS
    private var lastInstantRetryTime = 0L

    private val _isDisconnected = MutableStateFlow(false)
    val isDisconnectedFlow: StateFlow<Boolean> = _isDisconnected.asStateFlow()

    private var isNetworkCallbackRegistered = false

    // 供外部或 ViewModel 註冊連線恢復回調以即時刷新資料
    var onConnectionRestoredListener: (() -> Unit)? = null
    // 供外部或 ViewModel 註冊打卡成功回調以即時刷新安全狀態與紀錄
    var onCheckInSuccessListener: (() -> Unit)? = null

    fun init(context: Context) {
        val application = context.applicationContext
        val session = SessionManager(application)
        _isDisconnected.value = session.isDisconnected

        registerNetworkCallback(application)

        if (session.isDisconnected && session.getPendingCheckIn() != null) {
            startRetryLoop(application)
        }

        if (session.isLoggedIn()) {
            schedulePreAlertAlarm(application)
        }
    }

    /**
     * 註冊 ConnectivityManager.NetworkCallback，感知手機連網狀態
     * 只要 Wi-Fi 或行動數據開關被開啟且具備 Internet 能力，即時觸發瞬時重試
     */
    private fun registerNetworkCallback(context: Context) {
        if (isNetworkCallbackRegistered) return
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        try {
            cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Log.i(TAG, "🌐 [網路感知] 偵測到手機網路連通 (onAvailable)，準備執行瞬時補傳重試！")
                    triggerInstantRetry(context)
                }

                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    if (hasInternet) {
                        Log.d(TAG, "🌐 [網路感知] 網路具備驗證有效之網際網路能力 (NET_CAPABILITY_VALIDATED)")
                        triggerInstantRetry(context)
                    }
                }

                override fun onLost(network: Network) {
                    Log.d(TAG, "🌐 [網路感知] 網路連線已中斷 (onLost)")
                }
            })
            isNetworkCallbackRegistered = true
            Log.i(TAG, "📡 [網路監聽] ConnectivityManager.NetworkCallback 已成功掛載")
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ 註冊 NetworkCallback 異常: ${e.localizedMessage}")
        }
    }

    /**
     * 瞬時重試：當手機網路開關打開或連上 Wi-Fi 時被 NetworkCallback 觸發
     */
    fun triggerInstantRetry(context: Context) {
        val session = SessionManager(context)
        val userId = session.userId
        val pending = session.getPendingCheckIn()

        // 僅在使用者已登入且（處於斷線狀態或有未完成補傳紀錄）時觸發
        if (userId.isNullOrBlank() || (!_isDisconnected.value && pending == null)) {
            return
        }

        // 避免 2 秒內連續重疊觸發
        val now = System.currentTimeMillis()
        if (now - lastInstantRetryTime < 2_000L) {
            return
        }
        lastInstantRetryTime = now

        Log.i(TAG, "⚡ [瞬時重試] 網路開關開啟或連上 Wi-Fi，立即瞬時補傳心跳！")
        // 重置退避間隔為初始 10 秒
        currentRetryIntervalMs = INITIAL_RETRY_INTERVAL_MS

        scope.launch {
            val success = executeCheckInSync(context, isInstantRetry = true)
            if (success) {
                stopRetryLoop()
            }
        }
    }

    /**
     * 處理螢幕解鎖事件：
     * 1. 將本地暫存更新為「此時此刻解鎖」的資料（保證手機端只保留最新一筆）。
     * 2. 立即嘗試打心跳 API（就讓後端決定時間為當前伺服器時間）。
     * 3. 若打成功：清除暫存、解除斷線狀態（轉綠色）、後端記錄該筆解鎖、刷新 App。
     * 4. 若打失敗：維持/進入斷線狀態、保留該筆最新解鎖紀錄、啟動指數退避自動重試。
     */
    fun handleScreenUnlock(context: Context) {
        val session = SessionManager(context)
        val userId = session.userId
        if (userId.isNullOrBlank()) {
            Log.w(TAG, "⚠️ 使用者尚未登入，略過解鎖打卡處理")
            return
        }

        val nowIso = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        // 1. 無論當前是否斷線，先將手機端待發打卡覆寫為此時此刻的這筆解鎖資料 (確保只留最新一筆)
        session.savePendingCheckIn(
            timestamp = nowIso,
            remark = "螢幕解鎖自動報平安",
            networkType = "ScreenUnlock"
        )
        // 2. 解鎖即時清除已顯示的 22 小時預警卡片，並重新預約 22 小時預警鬧鐘
        cancelPreAlertNotification(context)
        schedulePreAlertAlarm(context)
        Log.i(TAG, "📱 偵測到手機螢幕解鎖！已更新手機端最近一筆打卡紀錄 [$nowIso]，立即嘗試發送心跳 API...")

        scope.launch {
            val success = executeCheckInSync(context, isInstantRetry = false, overrideIsoTime = null)
            if (success) {
                Log.i(TAG, "💚 [解鎖即時打卡] 成功！")
            } else {
                Log.w(TAG, "⚠️ [解鎖即時打卡] 失敗，進入斷線狀態並啟動退避重試")
                setDisconnected(true, context)
            }
        }
    }

    /**
     * 處理手機關機廣播事件 (ACTION_SHUTDOWN / QUICKBOOT_POWEROFF)
     */
    fun handleDeviceShutdown(context: Context) {
        val session = SessionManager(context)
        val userId = session.userId
        if (userId.isNullOrBlank()) {
            Log.w(TAG, "⚠️ 使用者尚未登入，略過關機打卡處理")
            return
        }

        val nowIso = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        // 1. 先寫入本地 Pending 暫存，確保即使當下關機斷電未發送成功，下次開機連網也能立即補傳該關機時間
        session.savePendingCheckIn(
            timestamp = nowIso,
            remark = "手機關機前自動報平安",
            networkType = "DeviceShutdown"
        )
        Log.i(TAG, "🔌 偵測到手機即將關機！已記錄關機時間 [$nowIso]，正在進行斷電前快速心跳報備...")

        // 2. 啟動非同步 Coroutine，嘗試在系統斷電前最後幾秒內把心跳送出
        scope.launch {
            val success = executeCheckInSync(
                context,
                isInstantRetry = false,
                overrideIsoTime = nowIso,
                customNetworkType = "DeviceShutdown",
                customRemark = "手機關機前自動報平安"
            )
            if (success) {
                Log.i(TAG, "🔌 [關機心跳] 成功在手機斷電前送達伺服器！")
            } else {
                Log.w(TAG, "⚠️ [關機心跳] 斷電前未能送達伺服器，已留置於本地暫存，待下次開機連網瞬時補傳")
            }
        }
    }

    /**
     * 核心同步執行方法（具備 Mutex 防重疊並發鎖）
     */
    private suspend fun executeCheckInSync(
        context: Context,
        isInstantRetry: Boolean,
        overrideIsoTime: String? = "USE_PENDING",
        customNetworkType: String? = null,
        customRemark: String? = null
    ): Boolean {
        if (!syncMutex.tryLock()) {
            Log.d(TAG, "已有同步任務正在執行中，略過重疊執行")
            return false
        }
        try {
            val session = SessionManager(context)
            val userId = session.userId
            val pending = session.getPendingCheckIn()

            if (userId.isNullOrBlank()) {
                Log.d(TAG, "使用者未登入，略過打卡同步")
                return false
            }

            val checkInTimestamp = if (overrideIsoTime == "USE_PENDING") {
                if (pending == null) {
                    Log.d(TAG, "無待補傳紀錄，略過打卡")
                    return false
                }
                pending.timestamp
            } else {
                overrideIsoTime
            }

            val finalNetworkType = customNetworkType ?: if (isInstantRetry) {
                if (pending?.networkType == "DeviceShutdown") "DeviceShutdown" else "NetworkRestoredInstant"
            } else if (checkInTimestamp == null) {
                "ScreenUnlock"
            } else {
                pending?.networkType ?: "OfflineBackoffRetry"
            }

            val finalRemark = customRemark ?: if (isInstantRetry) {
                if (pending?.networkType == "DeviceShutdown") "手機關機補傳 (連網瞬時補傳)" else "螢幕解鎖自動報平安 (連網瞬時補傳)"
            } else if (checkInTimestamp == null) {
                "螢幕解鎖自動報平安"
            } else {
                pending?.remark ?: "螢幕解鎖自動報平安 (離線退避補傳)"
            }

            val tagPrefix = if (customNetworkType != null) "🔌 [$customNetworkType]" else if (isInstantRetry) "⚡ [瞬時重試]" else if (checkInTimestamp == null) "📱 [解鎖即時]" else "⏱️ [退避重試]"
            val effectiveClientRequestId = pending?.clientRequestId ?: java.util.UUID.randomUUID().toString()
            Log.d(TAG, "$tagPrefix 正在發送打卡心跳 API (類型: $finalNetworkType, 冪等ID: $effectiveClientRequestId, 打卡時間: $checkInTimestamp)...")

            val api = ImsaApiService.create(session.serverUrl)
            val request = UserCheckInRequest(
                phone = session.phone,
                deviceInfo = "${Build.MANUFACTURER} ${Build.MODEL}",
                networkType = finalNetworkType,
                remark = finalRemark,
                checkInTime = checkInTimestamp,
                clientRequestId = effectiveClientRequestId
            )
            val response = api.checkIn(userId, request)

            if (response.isSuccessful && response.body() != null) {
                val data = response.body()!!
                Log.i(TAG, "🎉 $tagPrefix 成功上傳心跳！後端已記錄打卡時間: ${data.checkInTime}，下次截止: ${data.nextCheckInDeadline}")
                session.clearPendingCheckIn()
                session.safetyStatus = data.safetyStatus
                session.nextDeadline = data.nextCheckInDeadline
                currentRetryIntervalMs = INITIAL_RETRY_INTERVAL_MS
                setDisconnected(false, context)
                cancelPreAlertNotification(context)
                schedulePreAlertAlarm(context)
                onCheckInSuccessListener?.invoke()
                return true
            } else {
                Log.w(TAG, "⚠️ $tagPrefix 打心跳 API 回應失敗 (${response.code()})")
                return false
            }
        } catch (e: Exception) {
            val tagPrefix = if (customNetworkType != null) "🔌 [$customNetworkType]" else if (isInstantRetry) "⚡ [瞬時重試]" else "⏱️ [同步打卡]"
            Log.w(TAG, "❌ $tagPrefix 連線異常 (${e.localizedMessage})")
            return false
        } finally {
            syncMutex.unlock()
        }
    }

    /**
     * 啟動指數退避重試循環 (10s -> 20s -> 40s -> ... 最長 5m)
     */
    fun startRetryLoop(context: Context) {
        if (retryJob?.isActive == true) return

        Log.i(TAG, "🔄 啟動指數退避自動重試引擎 (初始間隔 ${currentRetryIntervalMs / 1000} 秒)...")
        retryJob = scope.launch {
            while (_isDisconnected.value) {
                Log.d(TAG, "⏳ 等待下一輪重試 (${currentRetryIntervalMs / 1000} 秒後)...")
                delay(currentRetryIntervalMs)
                if (!_isDisconnected.value) break

                val success = executeCheckInSync(context, isInstantRetry = false)
                if (success) {
                    break
                } else {
                    // 指數退避：間隔翻倍，最長 5 分鐘 (300 秒)
                    currentRetryIntervalMs = (currentRetryIntervalMs * 2).coerceAtMost(MAX_RETRY_INTERVAL_MS)
                    Log.w(TAG, "⚠️ 背景重試未成功，退避延長下次重試間隔至 ${currentRetryIntervalMs / 1000} 秒 (省電抗殺)")
                }
            }
        }
    }

    fun stopRetryLoop() {
        if (retryJob != null) {
            Log.i(TAG, "🛑 停止自動重試循環")
            retryJob?.cancel()
            retryJob = null
        }
    }

    fun setDisconnected(disconnected: Boolean, context: Context) {
        val wasDisconnected = _isDisconnected.value
        _isDisconnected.value = disconnected
        SessionManager(context).isDisconnected = disconnected

        if (disconnected) {
            startRetryLoop(context)
        } else {
            stopRetryLoop()
            currentRetryIntervalMs = INITIAL_RETRY_INTERVAL_MS
            if (wasDisconnected) {
                // 狀態由斷線轉為連線，觸發監聽以刷新 App 畫面
                onConnectionRestoredListener?.invoke()
            }
        }
    }

    /**
     * 使用者登出時徹底重置連線狀態、停止重試迴圈並清空離線暫存
     */
    fun resetOnLogout(context: Context) {
        stopRetryLoop()
        _isDisconnected.value = false
        currentRetryIntervalMs = INITIAL_RETRY_INTERVAL_MS
        val session = SessionManager(context)
        session.isDisconnected = false
        session.clearPendingCheckIn()
        cancelPreAlertNotification(context)
        cancelPreAlertAlarm(context)
        Log.i(TAG, "🚪 [登出重置] 已終止背景重試迴圈、取消預警鬧鐘與卡片並清空打卡暫存")
    }

    /**
     * 消除目前彈出的 22 小時平安提醒通知卡片
     */
    fun cancelPreAlertNotification(context: Context) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(PreAlertNotificationReceiver.NOTIFICATION_ID)
            Log.d(TAG, "🔕 [預警消除] 已消除 22 小時平安提醒通知卡片")
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ 清除預警通知失敗: ${e.localizedMessage}")
        }
    }

    /**
     * 排程 22 小時平安預警鬧鐘 (RTC_WAKEUP, setExactAndAllowWhileIdle)
     */
    fun schedulePreAlertAlarm(context: Context, delayMillis: Long = 22 * 3600 * 1000L) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, PreAlertNotificationReceiver::class.java).apply {
                action = PreAlertNotificationReceiver.ACTION_TRIGGER_PRE_ALERT
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                PreAlertNotificationReceiver.NOTIFICATION_ID,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val triggerAtMillis = System.currentTimeMillis() + delayMillis
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
            Log.i(TAG, "⏰ [預警排程] 已設定 22 小時平安預警鬧鐘於 ${delayMillis / 1000 / 3600} 小時後觸發")
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ 設定預警鬧鐘失敗: ${e.localizedMessage}")
        }
    }

    /**
     * 取消已排程之 22 小時平安預警鬧鐘
     */
    fun cancelPreAlertAlarm(context: Context) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, PreAlertNotificationReceiver::class.java).apply {
                action = PreAlertNotificationReceiver.ACTION_TRIGGER_PRE_ALERT
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                PreAlertNotificationReceiver.NOTIFICATION_ID,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
            Log.d(TAG, "🛑 [預警排程] 已取消 22 小時平安預警鬧鐘")
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ 取消預警鬧鐘失敗: ${e.localizedMessage}")
        }
    }
}

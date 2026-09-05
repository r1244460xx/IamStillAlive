package com.imsa.app.util

import android.content.Context
import android.os.Build
import android.util.Log
import com.imsa.app.data.ImsaApiService
import com.imsa.app.data.SessionManager
import com.imsa.app.data.UserCheckInRequest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object HeartbeatSyncManager {

    private const val TAG = "HeartbeatSyncMgr"
    private const val RETRY_INTERVAL_MS = 10_000L // 每 10 秒重試一次

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var retryJob: Job? = null

    private val _isDisconnected = MutableStateFlow(false)
    val isDisconnectedFlow: StateFlow<Boolean> = _isDisconnected.asStateFlow()

    // 供外部或 ViewModel 註冊連線恢復回調以即時刷新資料
    var onConnectionRestoredListener: (() -> Unit)? = null

    fun init(context: Context) {
        val session = SessionManager(context)
        _isDisconnected.value = session.isDisconnected
        if (session.isDisconnected && session.getPendingCheckIn() != null) {
            startRetryLoop(context)
        }
    }

    /**
     * 處理螢幕解鎖事件：
     * 1. 將本地暫存更新為「此時此刻解鎖」的資料（保證手機端只保留最新一筆）。
     * 2. 立即嘗試打心跳 API（就讓後端決定時間為當前伺服器時間）。
     * 3. 若打成功：清除暫存、解除斷線狀態（轉綠色）、後端記錄該筆解鎖、刷新 App。
     * 4. 若打失敗：維持/進入斷線狀態、保留該筆最新解鎖紀錄、啟動 10 秒自動重試。
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
        Log.i(TAG, "📱 偵測到手機螢幕解鎖！已更新手機端最近一筆打卡紀錄 [$nowIso]，立即嘗試發送心跳 API...")

        scope.launch {
            try {
                val api = ImsaApiService.create(session.serverUrl)
                val request = UserCheckInRequest(
                    phone = session.phone,
                    deviceInfo = "${Build.MANUFACTURER} ${Build.MODEL}",
                    networkType = "ScreenUnlock",
                    remark = "螢幕解鎖自動報平安",
                    checkInTime = null // 讓後端決定心跳時間為當前伺服器時間（即解鎖當下那一筆）
                )
                val response = api.checkIn(userId, request)

                if (response.isSuccessful && response.body() != null) {
                    val data = response.body()!!
                    Log.i(TAG, "💚 [解鎖即時打卡] 成功！後端記錄解鎖時間: ${data.checkInTime}，下次截止: ${data.nextCheckInDeadline}")
                    // 打卡成功，清除暫存並切回正常連線狀態
                    session.clearPendingCheckIn()
                    session.safetyStatus = data.safetyStatus
                    session.nextDeadline = data.nextCheckInDeadline
                    setDisconnected(false, context)
                } else {
                    Log.w(TAG, "⚠️ [解鎖即時打卡] 伺服器回應失敗 (${response.code()})，進入斷線狀態並啟動 10 秒重試")
                    setDisconnected(true, context)
                }
            } catch (e: Exception) {
                Log.w(TAG, "❌ [解鎖即時打卡] 呼叫心跳 API 異常 (${e.localizedMessage})，進入斷線狀態並啟動 10 秒重試")
                setDisconnected(true, context)
            }
        }
    }

    /**
     * 啟動 10 秒一次的重試循環
     */
    fun startRetryLoop(context: Context) {
        if (retryJob?.isActive == true) return

        Log.i(TAG, "🔄 啟動 10 秒自動重試補傳引擎...")
        retryJob = scope.launch {
            while (_isDisconnected.value) {
                delay(RETRY_INTERVAL_MS)
                if (!_isDisconnected.value) break

                val session = SessionManager(context)
                val pending = session.getPendingCheckIn()
                val userId = session.userId

                if (userId.isNullOrBlank() || pending == null) {
                    Log.d(TAG, "無待補傳紀錄或尚未登入，暫停 10 秒重試")
                    continue
                }

                Log.d(TAG, "⏱️ [10s重試] 正在嘗試補傳手機端最近一筆解鎖資料 (解鎖時間: ${pending.timestamp})...")
                try {
                    val api = ImsaApiService.create(session.serverUrl)
                    val request = UserCheckInRequest(
                        phone = session.phone,
                        deviceInfo = "${Build.MANUFACTURER} ${Build.MODEL}",
                        networkType = "Offline10sRetry",
                        remark = "螢幕解鎖自動報平安 (斷線重試補傳)",
                        checkInTime = pending.timestamp // 傳送解鎖那一筆的原始時間
                    )
                    val response = api.checkIn(userId, request)

                    if (response.isSuccessful && response.body() != null) {
                        val data = response.body()!!
                        Log.i(TAG, "🎉 [10s重試] 成功上傳心跳！後端已記錄解鎖時間: ${data.checkInTime}，立即解除斷線狀態！")
                        session.clearPendingCheckIn()
                        session.safetyStatus = data.safetyStatus
                        session.nextDeadline = data.nextCheckInDeadline
                        setDisconnected(false, context)
                        break
                    } else {
                        Log.w(TAG, "⚠️ [10s重試] 打心跳 API 回應失敗 (${response.code()})，維持斷線狀態，10 秒後再次重試...")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "❌ [10s重試] 連線異常 (${e.localizedMessage})，維持斷線狀態，10 秒後再次重試...")
                }
            }
        }
    }

    fun stopRetryLoop() {
        if (retryJob != null) {
            Log.i(TAG, "🛑 停止 10 秒自動重試循環")
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
            if (wasDisconnected) {
                // 狀態由斷線轉為連線，觸發監聽以刷新 App 畫面
                onConnectionRestoredListener?.invoke()
            }
        }
    }
}

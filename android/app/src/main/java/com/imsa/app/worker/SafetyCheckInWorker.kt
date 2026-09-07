package com.imsa.app.worker

import android.content.Context
import android.util.Log
import androidx.work.*
import com.imsa.app.data.ImsaApiService
import com.imsa.app.data.SessionManager
import com.imsa.app.data.UserCheckInRequest
import java.util.concurrent.TimeUnit

class SafetyCheckInWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val session = SessionManager(applicationContext)
        val userId = session.userId

        if (userId.isNullOrBlank()) {
            Log.d("SafetyCheckInWorker", "尚未登入，略過背景打卡")
            return Result.success()
        }

        // 優先檢查是否有離線暫存的打卡 (單一最新一筆)
        val pendingCheckIn = session.getPendingCheckIn()
        val effectiveRemark = pendingCheckIn?.remark 
            ?: inputData.getString(KEY_REMARK) 
            ?: "系統背景定時心跳包報平安 (無感守護)"
        val effectiveNetworkType = pendingCheckIn?.networkType 
            ?: inputData.getString(KEY_NETWORK_TYPE) 
            ?: "WorkManager"
        val effectiveCheckInTime = pendingCheckIn?.timestamp 
            ?: inputData.getString(KEY_CHECK_IN_TIME)

        Log.i("SafetyCheckInWorker", "🚀 [WorkManager] 正在執行心跳打卡 (User: $userId, Phone: ${session.phone}, 類型: $effectiveNetworkType, 原始時間: ${effectiveCheckInTime ?: "即時"})...")

        return try {
            val api = ImsaApiService.create(session.serverUrl)
            val effectiveClientRequestId = pendingCheckIn?.clientRequestId ?: java.util.UUID.randomUUID().toString()
            val request = UserCheckInRequest(
                phone = session.phone,
                deviceInfo = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
                networkType = effectiveNetworkType,
                remark = effectiveRemark,
                checkInTime = effectiveCheckInTime,
                clientRequestId = effectiveClientRequestId
            )
            val response = api.checkIn(userId, request)

            if (response.isSuccessful && response.body() != null) {
                val data = response.body()!!
                // 打卡成功，清除手機端保存的唯一暫存
                if (pendingCheckIn != null) {
                    session.clearPendingCheckIn()
                    Log.i("SafetyCheckInWorker", "🧹 [WorkManager] 已清除手機端離線暫存打卡紀錄")
                }
                // 自動自我修復：若後端 Canonical ID 不同，自動同步 Session
                if (!data.userId.isNullOrBlank() && session.userId != data.userId) {
                    Log.i("SafetyCheckInWorker", "🔄 自動修復本地 Session 使用者 ID: ${session.userId} -> ${data.userId}")
                    session.userId = data.userId
                }
                session.safetyStatus = data.safetyStatus
                session.nextDeadline = data.nextCheckInDeadline
                Log.i("SafetyCheckInWorker", "💚 [WorkManager] 打卡成功！狀態: SAFE, 記錄時間: ${data.checkInTime}, 下次截止: ${data.nextCheckInDeadline}")
                Result.success()
            } else if (response.code() in 400..499) {
                Log.w("SafetyCheckInWorker", "⚠️ [WorkManager] 客戶端錯誤 (${response.code()})，停止重試以避免無效循環")
                Result.failure()
            } else {
                Log.w("SafetyCheckInWorker", "⚠️ [WorkManager] 背景打卡回應失敗: ${response.code()}")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e("SafetyCheckInWorker", "❌ [WorkManager] 背景打卡網路連線異常", e)
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "imsa_safety_heartbeat"
        const val PENDING_SYNC_WORK_NAME = "imsa_safety_sync_pending"
        const val KEY_REMARK = "key_remark"
        const val KEY_NETWORK_TYPE = "key_network_type"
        const val KEY_CHECK_IN_TIME = "key_check_in_time"

        /**
         * 取消既有的 12 小時定時排程（唯一打卡時機為螢幕解鎖，不進行盲目定時打卡）
         */
        fun cancelPeriodicHeartbeat(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.i("SafetyCheckInWorker", "🛑 已取消 12 小時背景定時心跳任務 (打卡唯一依據為螢幕解鎖)")
        }

        /**
         * 排程在恢復網際網路連線時自動補傳離線暫存紀錄 (鎖屏或解鎖皆會觸發)
         */
        fun triggerPendingCheckInSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<SafetyCheckInWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                PENDING_SYNC_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
            Log.i("SafetyCheckInWorker", "⚡ 已排程離線打卡補傳任務 (鎖定或解鎖皆會在恢復連線時觸發)")
        }

        /**
         * 立即觸發一次背景 Worker 打卡
         */
        fun triggerImmediateHeartbeat(
            context: Context,
            remark: String = "手動背景心跳打卡",
            networkType: String = "WorkManager",
            checkInTime: String? = null
        ) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val data = workDataOf(
                KEY_REMARK to remark,
                KEY_NETWORK_TYPE to networkType,
                KEY_CHECK_IN_TIME to checkInTime
            )

            val oneTimeRequest = OneTimeWorkRequestBuilder<SafetyCheckInWorker>()
                .setConstraints(constraints)
                .setInputData(data)
                .build()

            WorkManager.getInstance(context).enqueue(oneTimeRequest)
            Log.i("SafetyCheckInWorker", "⚡ 已派發單次背景 Worker 任務 (備註: $remark, 類型: $networkType, 時間: $checkInTime)")
        }
    }
}

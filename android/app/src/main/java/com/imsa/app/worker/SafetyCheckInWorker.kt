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

        val remark = inputData.getString(KEY_REMARK) ?: "系統背景定時心跳包報平安 (無感守護)"
        val networkType = inputData.getString(KEY_NETWORK_TYPE) ?: "WorkManager"

        Log.i("SafetyCheckInWorker", "🚀 [WorkManager] 正在執行背景心跳打卡 (User: $userId, 類型: $networkType)...")

        return try {
            val api = ImsaApiService.create()
            val request = UserCheckInRequest(
                deviceInfo = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
                networkType = networkType,
                remark = remark
            )
            val response = api.checkIn(userId, request)

            if (response.isSuccessful && response.body() != null) {
                val data = response.body()!!
                session.safetyStatus = data.safetyStatus
                session.nextDeadline = data.nextCheckInDeadline
                Log.i("SafetyCheckInWorker", "💚 [WorkManager] 背景打卡成功！狀態已更新為 SAFE，下次截止時間：${data.nextCheckInDeadline}")
                Result.success()
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
        const val KEY_REMARK = "key_remark"
        const val KEY_NETWORK_TYPE = "key_network_type"

        /**
         * 排定每 12 小時定時執行的背景心跳打卡
         */
        fun schedulePeriodicHeartbeat(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED) // 需在聯網時執行
                .build()

            val periodicRequest = PeriodicWorkRequestBuilder<SafetyCheckInWorker>(12, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicRequest
            )
            Log.i("SafetyCheckInWorker", "📅 已排定每 12 小時背景自動打卡任務")
        }

        /**
         * 立即觸發一次背景 Worker 打卡
         */
        fun triggerImmediateHeartbeat(
            context: Context,
            remark: String = "手動背景心跳打卡",
            networkType: String = "WorkManager"
        ) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val data = workDataOf(
                KEY_REMARK to remark,
                KEY_NETWORK_TYPE to networkType
            )

            val oneTimeRequest = OneTimeWorkRequestBuilder<SafetyCheckInWorker>()
                .setConstraints(constraints)
                .setInputData(data)
                .build()

            WorkManager.getInstance(context).enqueue(oneTimeRequest)
            Log.i("SafetyCheckInWorker", "⚡ 已派發單次背景 Worker 任務 (備註: $remark, 類型: $networkType)")
        }
    }
}

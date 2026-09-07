package com.imsa.app.data

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {

    private val prefs: SharedPreferences = 
        context.getSharedPreferences("imsa_user_prefs", Context.MODE_PRIVATE)

    companion object {
        const val HARDCODED_TEST_USER_ID = "00000000-0000-0000-0000-000000000001"
        const val HARDCODED_TEST_PHONE = "0912345678"
        const val HARDCODED_TEST_NICKNAME = "測試者"
        const val HARDCODED_TEST_EMERGENCY = "0987654321"
    }

    init {
        val current = prefs.getString("user_id", null)
        val currentPhone = prefs.getString("phone", null)
        // 初次初始化或舊隨機 UUID 自動遷移至固定測試帳號
        if (current.isNullOrBlank() || (!current.equals(HARDCODED_TEST_USER_ID) && (currentPhone == null || currentPhone == HARDCODED_TEST_PHONE))) {
            initDefaultTestUser()
        }
    }

    fun initDefaultTestUser() {
        prefs.edit()
            .putString("user_id", HARDCODED_TEST_USER_ID)
            .putString("phone", HARDCODED_TEST_PHONE)
            .putString("nickname", HARDCODED_TEST_NICKNAME)
            .putString("emergency_contact", HARDCODED_TEST_EMERGENCY)
            .putString("safety_status", "SAFE")
            .apply()
    }

    var userId: String?
        get() = prefs.getString("user_id", null)
        set(value) = prefs.edit().putString("user_id", value).apply()

    var nickname: String?
        get() = prefs.getString("nickname", null)
        set(value) = prefs.edit().putString("nickname", value).apply()

    var phone: String?
        get() = prefs.getString("phone", null)
        set(value) = prefs.edit().putString("phone", value).apply()

    var emergencyContact: String?
        get() = prefs.getString("emergency_contact", null)
        set(value) = prefs.edit().putString("emergency_contact", value).apply()

    var safetyStatus: String?
        get() = prefs.getString("safety_status", "SAFE")
        set(value) = prefs.edit().putString("safety_status", value).apply()

    var nextDeadline: String?
        get() = prefs.getString("next_deadline", null)
        set(value) = prefs.edit().putString("next_deadline", value).apply()

    var serverUrl: String
        get() = prefs.getString("server_url", "http://192.168.0.137:8080/") ?: "http://192.168.0.137:8080/"
        set(value) = prefs.edit().putString("server_url", value).apply()

    var isDisconnected: Boolean
        get() = prefs.getBoolean("is_disconnected", false)
        set(value) = prefs.edit().putBoolean("is_disconnected", value).apply()

    fun isLoggedIn(): Boolean = !userId.isNullOrBlank()

    fun saveUser(user: UserResponse) {
        prefs.edit()
            .putString("user_id", user.id)
            .putString("nickname", user.nickname)
            .putString("phone", user.phone)
            .putString("emergency_contact", user.emergencyContactPhone)
            .putString("safety_status", user.safetyStatus)
            .apply()
    }

    fun savePendingCheckIn(
        timestamp: String, 
        remark: String, 
        networkType: String = "ScreenUnlock",
        clientRequestId: String = java.util.UUID.randomUUID().toString()
    ) {
        prefs.edit()
            .putString("pending_checkin_timestamp", timestamp)
            .putString("pending_checkin_remark", remark)
            .putString("pending_checkin_network_type", networkType)
            .putString("pending_checkin_client_request_id", clientRequestId)
            .apply()
    }

    fun getPendingCheckIn(): PendingCheckIn? {
        val timestamp = prefs.getString("pending_checkin_timestamp", null) ?: return null
        val remark = prefs.getString("pending_checkin_remark", "螢幕解鎖自動報平安 (離線暫存補傳)") ?: "螢幕解鎖自動報平安 (離線暫存補傳)"
        val networkType = prefs.getString("pending_checkin_network_type", "ScreenUnlock") ?: "ScreenUnlock"
        val clientRequestId = prefs.getString("pending_checkin_client_request_id", null) ?: java.util.UUID.randomUUID().toString()
        return PendingCheckIn(timestamp, remark, networkType, clientRequestId)
    }

    fun clearPendingCheckIn() {
        prefs.edit()
            .remove("pending_checkin_timestamp")
            .remove("pending_checkin_remark")
            .remove("pending_checkin_network_type")
            .remove("pending_checkin_client_request_id")
            .apply()
    }

    fun logout() {
        val currentServer = serverUrl
        prefs.edit().clear().apply()
        serverUrl = currentServer
    }
}

package com.imsa.app.data

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {

    private val prefs: SharedPreferences = 
        context.getSharedPreferences("imsa_user_prefs", Context.MODE_PRIVATE)

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

    fun logout() {
        val currentServer = serverUrl
        prefs.edit().clear().apply()
        serverUrl = currentServer
    }
}

package com.imsa.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.imsa.app.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class MainUiState(
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false,
    val isDisconnected: Boolean = false,
    val pendingCheckIn: PendingCheckIn? = null,
    val userId: String = "",
    val nickname: String = "",
    val phone: String = "",
    val emergencyContact: String? = null,
    val safetyStatus: String = "SAFE",
    val nextDeadline: String? = null,
    val recentRecords: List<LoginRecordResponse> = emptyList(),
    val message: String? = null,
    val isError: Boolean = false,
    val errorMessage: String? = null,
    val isPhoneConflict: Boolean = false,
    val serverUrl: String = "http://192.168.0.137:8080/"
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val session = SessionManager(application)
    private var api = ImsaApiService.create(session.serverUrl)

    private val _uiState = MutableStateFlow(
        MainUiState(
            serverUrl = session.serverUrl,
            isDisconnected = session.isDisconnected,
            pendingCheckIn = session.getPendingCheckIn()
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        com.imsa.app.util.HeartbeatSyncManager.init(application)
        com.imsa.app.util.HeartbeatSyncManager.onConnectionRestoredListener = {
            refreshData()
        }
        loadSession()
        observeSyncManager()
    }

    private fun observeSyncManager() {
        viewModelScope.launch {
            com.imsa.app.util.HeartbeatSyncManager.isDisconnectedFlow.collect { disconnected ->
                val wasDisconnected = _uiState.value.isDisconnected
                _uiState.value = _uiState.value.copy(
                    isDisconnected = disconnected,
                    pendingCheckIn = session.getPendingCheckIn()
                )
                // 若由斷線轉為連線，自動刷新資料
                if (wasDisconnected && !disconnected && session.isLoggedIn()) {
                    refreshData()
                }
            }
        }
    }

    fun loadSession() {
        val pending = session.getPendingCheckIn()
        if (session.isLoggedIn()) {
            _uiState.value = _uiState.value.copy(
                isLoggedIn = true,
                serverUrl = session.serverUrl,
                userId = session.userId.orEmpty(),
                nickname = session.nickname.orEmpty(),
                phone = session.phone.orEmpty(),
                emergencyContact = session.emergencyContact,
                safetyStatus = session.safetyStatus ?: "SAFE",
                nextDeadline = session.nextDeadline,
                pendingCheckIn = pending
            )
            com.imsa.app.worker.SafetyCheckInWorker.cancelPeriodicHeartbeat(getApplication())
            refreshData()
        } else {
            _uiState.value = _uiState.value.copy(isLoggedIn = false, serverUrl = session.serverUrl, pendingCheckIn = pending)
        }
    }

    fun login(phone: String, pass: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, message = null)
            try {
                val req = UserLoginRequest(phone = phone, password = pass)
                val resp = api.login(req)
                if (resp.isSuccessful && resp.body() != null) {
                    val user = resp.body()!!
                    session.saveUser(user)
                    com.imsa.app.worker.SafetyCheckInWorker.cancelPeriodicHeartbeat(getApplication())
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isLoggedIn = true,
                        userId = user.id,
                        nickname = user.nickname,
                        phone = user.phone,
                        emergencyContact = user.emergencyContactPhone,
                        safetyStatus = user.safetyStatus,
                        message = "登入成功！歡迎回來，${user.nickname}。",
                        isError = false
                    )
                    refreshData()
                } else {
                    val rawErr = resp.errorBody()?.string()
                    val parsedMsg = parseError(rawErr, "登入失敗 (${resp.code()})")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        message = parsedMsg,
                        errorMessage = parsedMsg,
                        isError = true
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "無法連線至後端: ${e.localizedMessage}",
                    errorMessage = "無法連線至後端: ${e.localizedMessage}",
                    isError = true
                )
            }
        }
    }

    fun register(
        phone: String,
        pass: String,
        nickname: String,
        emergencyPhone: String
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true, 
                message = null,
                errorMessage = null,
                isPhoneConflict = false
            )
            try {
                val req = UserRegisterRequest(
                    phone = phone,
                    password = pass,
                    nickname = nickname,
                    gender = "OTHER",
                    birthdate = "1995-01-01",
                    emergencyContactPhone = emergencyPhone.ifBlank { null }
                )
                val resp = api.register(req)
                if (resp.isSuccessful && resp.body() != null) {
                    val user = resp.body()!!
                    session.saveUser(user)
                    com.imsa.app.worker.SafetyCheckInWorker.cancelPeriodicHeartbeat(getApplication())
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isLoggedIn = true,
                        userId = user.id,
                        nickname = user.nickname,
                        phone = user.phone,
                        emergencyContact = user.emergencyContactPhone,
                        safetyStatus = user.safetyStatus,
                        message = "註冊成功！守護已啟動。",
                        errorMessage = null,
                        isPhoneConflict = false,
                        isError = false
                    )
                    refreshData()
                } else {
                    val rawErr = resp.errorBody()?.string()
                    val parsedMsg = parseError(rawErr, "註冊失敗 (${resp.code()})")
                    val isConflict = resp.code() == 400 && (parsedMsg.contains("已註冊") || parsedMsg.contains("電話") || parsedMsg.contains("手機"))
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        message = parsedMsg,
                        errorMessage = parsedMsg,
                        isPhoneConflict = isConflict,
                        isError = true
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "無法連線至後端: ${e.localizedMessage}",
                    errorMessage = "無法連線至後端: ${e.localizedMessage}",
                    isError = true
                )
            }
        }
    }

    fun checkIn(remark: String = "手機 App 一鍵打卡報平安") {
        val currentUserId = _uiState.value.userId
        if (currentUserId.isBlank() && session.phone.isNullOrBlank()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, message = null)
            try {
                val req = UserCheckInRequest(
                    phone = session.phone,
                    deviceInfo = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (Android ${android.os.Build.VERSION.RELEASE})",
                    networkType = "Wi-Fi",
                    remark = remark
                )
                val resp = api.checkIn(currentUserId, req)
                if (resp.isSuccessful && resp.body() != null) {
                    val data = resp.body()!!
                    if (!data.userId.isNullOrBlank() && data.userId != currentUserId) {
                        session.userId = data.userId
                        _uiState.value = _uiState.value.copy(userId = data.userId)
                    }
                    session.safetyStatus = data.safetyStatus
                    session.nextDeadline = data.nextCheckInDeadline

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        safetyStatus = data.safetyStatus,
                        nextDeadline = data.nextCheckInDeadline,
                        message = data.message,
                        isError = false
                    )
                    com.imsa.app.util.HeartbeatSyncManager.setDisconnected(false, getApplication())
                    fetchHistory()
                } else {
                    val err = resp.errorBody()?.string() ?: "打卡失敗"
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        message = err,
                        isError = true
                    )
                    com.imsa.app.util.HeartbeatSyncManager.setDisconnected(true, getApplication())
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "連線失敗: ${e.localizedMessage}",
                    isError = true
                )
                com.imsa.app.util.HeartbeatSyncManager.setDisconnected(true, getApplication())
            }
        }
    }

    fun refreshData() {
        val pending = session.getPendingCheckIn()
        _uiState.value = _uiState.value.copy(pendingCheckIn = pending)

        val currentUserId = _uiState.value.userId
        if (currentUserId.isBlank() && session.phone.isNullOrBlank()) return

        viewModelScope.launch {
            try {
                val profileResp = api.getUserProfile(currentUserId, session.phone)
                if (profileResp.isSuccessful && profileResp.body() != null) {
                    val user = profileResp.body()!!
                    if (session.userId != user.id) {
                        session.userId = user.id
                        _uiState.value = _uiState.value.copy(userId = user.id)
                    }
                    session.saveUser(user)
                    _uiState.value = _uiState.value.copy(
                        nickname = user.nickname,
                        phone = user.phone,
                        emergencyContact = user.emergencyContactPhone,
                        safetyStatus = user.safetyStatus
                    )
                    com.imsa.app.util.HeartbeatSyncManager.setDisconnected(false, getApplication())
                } else {
                    com.imsa.app.util.HeartbeatSyncManager.setDisconnected(true, getApplication())
                }
                fetchHistory()
            } catch (e: Exception) {
                com.imsa.app.util.HeartbeatSyncManager.setDisconnected(true, getApplication())
            }
        }
    }

    private suspend fun fetchHistory() {
        val currentUserId = _uiState.value.userId
        if (currentUserId.isBlank() && session.phone.isNullOrBlank()) return
        try {
            val historyResp = api.getLoginRecords(currentUserId, session.phone)
            if (historyResp.isSuccessful && historyResp.body() != null) {
                _uiState.value = _uiState.value.copy(
                    recentRecords = historyResp.body()!!.take(5)
                )
            }
        } catch (e: Exception) {
            // Ignore
        }
    }

    fun changePassword(
        oldPass: String,
        newPass: String,
        onSuccess: () -> Unit
    ) {
        val currentUserId = _uiState.value.userId
        if (currentUserId.isBlank()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, message = null, errorMessage = null, isError = false)
            try {
                val req = UserPasswordChangeRequest(oldPassword = oldPass, newPassword = newPass)
                val resp = api.changePassword(currentUserId, req)
                if (resp.isSuccessful) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        message = "密碼已成功變更！",
                        isError = false
                    )
                    onSuccess()
                } else {
                    val rawErr = resp.errorBody()?.string()
                    val msg = parseError(rawErr, "修改密碼失敗 (${resp.code()})")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        message = msg,
                        errorMessage = msg,
                        isError = true
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "連線失敗: ${e.localizedMessage}",
                    errorMessage = "連線失敗: ${e.localizedMessage}",
                    isError = true
                )
            }
        }
    }

    fun updateEmergencyContact(
        newPhone: String,
        onSuccess: () -> Unit
    ) {
        val currentUserId = _uiState.value.userId
        if (currentUserId.isBlank()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, message = null, errorMessage = null, isError = false)
            try {
                val req = UserEmergencyContactUpdateRequest(emergencyContactPhone = newPhone)
                val resp = api.updateEmergencyContact(currentUserId, req)
                if (resp.isSuccessful && resp.body() != null) {
                    val user = resp.body()!!
                    session.saveUser(user)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        emergencyContact = user.emergencyContactPhone,
                        message = "緊急聯絡人電話已更新！",
                        isError = false
                    )
                    onSuccess()
                } else {
                    val rawErr = resp.errorBody()?.string()
                    val msg = parseError(rawErr, "更新失敗 (${resp.code()})")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        message = msg,
                        errorMessage = msg,
                        isError = true
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "連線失敗: ${e.localizedMessage}",
                    errorMessage = "連線失敗: ${e.localizedMessage}",
                    isError = true
                )
            }
        }
    }

    fun triggerBackgroundHeartbeat() {
        com.imsa.app.worker.SafetyCheckInWorker.triggerImmediateHeartbeat(getApplication())
        _uiState.value = _uiState.value.copy(message = "⚡ 已觸發 WorkManager 背景心跳打卡！")
    }

    fun logout() {
        session.logout()
        _uiState.value = MainUiState(serverUrl = session.serverUrl)
    }

    fun updateServerUrl(newUrl: String) {
        val formatted = if (newUrl.startsWith("http://") || newUrl.startsWith("https://")) {
            if (newUrl.endsWith("/")) newUrl else "$newUrl/"
        } else {
            "http://$newUrl/"
        }
        session.serverUrl = formatted
        api = ImsaApiService.create(formatted)
        _uiState.value = _uiState.value.copy(
            serverUrl = formatted,
            message = "伺服器網址已更新為: $formatted"
        )
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(
            message = null,
            errorMessage = null,
            isPhoneConflict = false,
            isError = false
        )
    }

    private fun parseError(rawBody: String?, defaultMsg: String): String {
        if (rawBody.isNullOrBlank()) return defaultMsg
        return try {
            val json = org.json.JSONObject(rawBody)
            when {
                json.has("error") -> json.getString("error")
                json.has("message") -> json.getString("message")
                json.has("phone") -> json.getString("phone")
                else -> {
                    val keys = json.keys()
                    if (keys.hasNext()) json.getString(keys.next()) else defaultMsg
                }
            }
        } catch (e: Exception) {
            rawBody
        }
    }
}

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
    val userId: String = "",
    val nickname: String = "",
    val phone: String = "",
    val emergencyContact: String? = null,
    val safetyStatus: String = "SAFE",
    val nextDeadline: String? = null,
    val recentRecords: List<LoginRecordResponse> = emptyList(),
    val message: String? = null,
    val isError: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val api = ImsaApiService.create()
    private val session = SessionManager(application)

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        loadSession()
    }

    fun loadSession() {
        if (session.isLoggedIn()) {
            _uiState.value = _uiState.value.copy(
                isLoggedIn = true,
                userId = session.userId.orEmpty(),
                nickname = session.nickname.orEmpty(),
                phone = session.phone.orEmpty(),
                emergencyContact = session.emergencyContact,
                safetyStatus = session.safetyStatus ?: "SAFE",
                nextDeadline = session.nextDeadline
            )
            com.imsa.app.worker.SafetyCheckInWorker.schedulePeriodicHeartbeat(getApplication())
            refreshData()
        } else {
            _uiState.value = _uiState.value.copy(isLoggedIn = false)
        }
    }

    fun register(
        phone: String,
        pass: String,
        nickname: String,
        emergencyPhone: String
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, message = null)
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
                    com.imsa.app.worker.SafetyCheckInWorker.schedulePeriodicHeartbeat(getApplication())
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isLoggedIn = true,
                        userId = user.id,
                        nickname = user.nickname,
                        phone = user.phone,
                        emergencyContact = user.emergencyContactPhone,
                        safetyStatus = user.safetyStatus,
                        message = "註冊成功！守護已啟動。",
                        isError = false
                    )
                    refreshData()
                } else {
                    val err = resp.errorBody()?.string() ?: "註冊失敗 (${resp.code()})"
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        message = err,
                        isError = true
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "無法連線至後端: ${e.localizedMessage}",
                    isError = true
                )
            }
        }
    }

    fun checkIn(remark: String = "手機 App 一鍵打卡報平安") {
        val currentUserId = _uiState.value.userId
        if (currentUserId.isBlank()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, message = null)
            try {
                val req = UserCheckInRequest(
                    deviceInfo = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (Android ${android.os.Build.VERSION.RELEASE})",
                    networkType = "Wi-Fi",
                    remark = remark
                )
                val resp = api.checkIn(currentUserId, req)
                if (resp.isSuccessful && resp.body() != null) {
                    val data = resp.body()!!
                    session.safetyStatus = data.safetyStatus
                    session.nextDeadline = data.nextCheckInDeadline

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        safetyStatus = data.safetyStatus,
                        nextDeadline = data.nextCheckInDeadline,
                        message = data.message,
                        isError = false
                    )
                    fetchHistory()
                } else {
                    val err = resp.errorBody()?.string() ?: "打卡失敗"
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        message = err,
                        isError = true
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "連線失敗: ${e.localizedMessage}",
                    isError = true
                )
            }
        }
    }

    fun refreshData() {
        val currentUserId = _uiState.value.userId
        if (currentUserId.isBlank()) return

        viewModelScope.launch {
            try {
                val profileResp = api.getUserProfile(currentUserId)
                if (profileResp.isSuccessful && profileResp.body() != null) {
                    val user = profileResp.body()!!
                    session.saveUser(user)
                    _uiState.value = _uiState.value.copy(
                        nickname = user.nickname,
                        phone = user.phone,
                        emergencyContact = user.emergencyContactPhone,
                        safetyStatus = user.safetyStatus
                    )
                }
                fetchHistory()
            } catch (e: Exception) {
                // Background refresh error, don't block UI
            }
        }
    }

    private suspend fun fetchHistory() {
        val currentUserId = _uiState.value.userId
        if (currentUserId.isBlank()) return
        try {
            val historyResp = api.getLoginRecords(currentUserId)
            if (historyResp.isSuccessful && historyResp.body() != null) {
                _uiState.value = _uiState.value.copy(
                    recentRecords = historyResp.body()!!.take(5)
                )
            }
        } catch (e: Exception) {
            // Ignore
        }
    }

    fun triggerBackgroundHeartbeat() {
        com.imsa.app.worker.SafetyCheckInWorker.triggerImmediateHeartbeat(getApplication())
        _uiState.value = _uiState.value.copy(message = "⚡ 已觸發 WorkManager 背景心跳打卡！")
    }

    fun logout() {
        session.logout()
        _uiState.value = MainUiState()
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}

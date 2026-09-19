package com.imsa.app.data

import com.google.gson.annotations.SerializedName

data class UserRegisterRequest(
    val phone: String,
    val password: String,
    val nickname: String,
    val gender: String = "OTHER", // MALE, FEMALE, OTHER
    val birthdate: String = "1995-01-01", // yyyy-MM-dd
    val email: String? = null,
    val nationalId: String? = null,
    val emergencyContactName: String,
    val emergencyContactPhone: String
)

data class UserLoginRequest(
    val phone: String,
    val password: String
)

data class EmergencyContactRequest(
    val name: String,
    val phone: String
)

data class EmergencyContactResponse(
    val id: String,
    val userId: String? = null,
    val name: String,
    val phone: String,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

data class UserResponse(
    val id: String,
    val phone: String,
    val email: String?,
    val nationalId: String?,
    val emergencyContacts: List<EmergencyContactResponse> = emptyList(),
    val nickname: String,
    val gender: String,
    val birthdate: String,
    val createdAt: String,
    val updatedAt: String,
    val status: String,
    val safetyStatus: String, // SAFE, ALERTED
    val lastActiveAt: String?
)

data class UserUpdateRequest(
    val nickname: String,
    val gender: String, // MALE, FEMALE, OTHER
    val birthdate: String, // yyyy-MM-dd
    val email: String? = null,
    val nationalId: String? = null
)

data class UserCheckInRequest(
    val phone: String? = null,
    val location: String? = null,
    val ipAddress: String? = null,
    val deviceInfo: String? = null,
    val networkType: String? = null,
    val remark: String? = null,
    val checkInTime: String? = null,
    val clientRequestId: String? = null
)

data class PendingCheckIn(
    val timestamp: String,
    val remark: String,
    val networkType: String = "ScreenUnlock",
    val clientRequestId: String = java.util.UUID.randomUUID().toString()
)

data class UserCheckInResponse(
    val userId: String,
    val loginRecordId: String,
    val checkInTime: String,
    val safetyStatus: String, // SAFE
    val nextCheckInDeadline: String,
    val message: String
)

data class LoginRecordResponse(
    val id: String,
    val userId: String,
    val loginTime: String,
    val location: String?,
    val ipAddress: String?,
    val deviceInfo: String?,
    val networkType: String?,
    val remark: String?
)

data class UserPasswordChangeRequest(
    val oldPassword: String,
    val newPassword: String
)

data class MessageResponse(
    val message: String
)

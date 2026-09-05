package com.imsa.app.data

import com.google.gson.annotations.SerializedName

data class UserRegisterRequest(
    val phone: String,
    val password: String,
    val nickname: String,
    val gender: String, // MALE, FEMALE, OTHER
    val birthdate: String, // yyyy-MM-dd
    val email: String? = null,
    val nationalId: String? = null,
    val emergencyContactPhone: String? = null
)

data class UserLoginRequest(
    val phone: String,
    val password: String
)

data class UserResponse(
    val id: String,
    val phone: String,
    val email: String?,
    val nationalId: String?,
    val emergencyContactPhone: String?,
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
    val nationalId: String? = null,
    val emergencyContactPhone: String? = null
)

data class UserCheckInRequest(
    val location: String? = null,
    val ipAddress: String? = null,
    val deviceInfo: String? = null,
    val networkType: String? = null,
    val remark: String? = null
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

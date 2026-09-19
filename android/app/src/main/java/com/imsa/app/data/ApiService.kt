package com.imsa.app.data

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface ImsaApiService {

    @POST("api/users/register")
    suspend fun register(@Body request: UserRegisterRequest): Response<UserResponse>

    @POST("api/users/login")
    suspend fun login(@Body request: UserLoginRequest): Response<UserResponse>

    @GET("api/users/{id}")
    suspend fun getUserProfile(
        @Path("id") id: String,
        @Query("phone") phone: String? = null
    ): Response<UserResponse>

    @PUT("api/users/{id}")
    suspend fun updateUserProfile(
        @Path("id") id: String,
        @Body request: UserUpdateRequest
    ): Response<UserResponse>

    @PUT("api/users/{id}/password")
    suspend fun changePassword(
        @Path("id") id: String,
        @Body request: UserPasswordChangeRequest
    ): Response<MessageResponse>

    @GET("api/users/{userId}/emergency-contacts")
    suspend fun getEmergencyContacts(
        @Path("userId") userId: String
    ): Response<List<EmergencyContactResponse>>

    @POST("api/users/{userId}/emergency-contacts")
    suspend fun addEmergencyContact(
        @Path("userId") userId: String,
        @Body request: EmergencyContactRequest
    ): Response<EmergencyContactResponse>

    @PUT("api/users/{userId}/emergency-contacts/{contactId}")
    suspend fun updateEmergencyContact(
        @Path("userId") userId: String,
        @Path("contactId") contactId: String,
        @Body request: EmergencyContactRequest
    ): Response<EmergencyContactResponse>

    @DELETE("api/users/{userId}/emergency-contacts/{contactId}")
    suspend fun deleteEmergencyContact(
        @Path("userId") userId: String,
        @Path("contactId") contactId: String
    ): Response<Map<String, String>>

    @POST("api/users/{id}/check-in")
    suspend fun checkIn(
        @Path("id") id: String,
        @Body request: UserCheckInRequest
    ): Response<UserCheckInResponse>

    @GET("api/login-records/user/{userId}")
    suspend fun getLoginRecords(
        @Path("userId") userId: String,
        @Query("phone") phone: String? = null
    ): Response<List<LoginRecordResponse>>

    @DELETE("api/login-records/{id}")
    suspend fun deleteLoginRecord(@Path("id") id: String): Response<Unit>

    companion object {
        const val DEFAULT_BASE_URL = "http://64.181.242.49:8080/"

        fun create(baseUrl: String = DEFAULT_BASE_URL): ImsaApiService {
            val validUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            val client = OkHttpClient.Builder()
                .addInterceptor(logging)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()

            return Retrofit.Builder()
                .baseUrl(validUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ImsaApiService::class.java)
        }
    }
}

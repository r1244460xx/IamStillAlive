package com.imsa.app.data

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

interface ImsaApiService {

    @POST("api/users/register")
    suspend fun register(@Body request: UserRegisterRequest): Response<UserResponse>

    @GET("api/users/{id}")
    suspend fun getUserProfile(@Path("id") id: String): Response<UserResponse>

    @POST("api/users/{id}/check-in")
    suspend fun checkIn(
        @Path("id") id: String,
        @Body request: UserCheckInRequest
    ): Response<UserCheckInResponse>

    @GET("api/login-records/user/{userId}")
    suspend fun getLoginRecords(@Path("userId") userId: String): Response<List<LoginRecordResponse>>

    companion object {
        // 10.0.2.2 為 Android 模擬器訪問本機電腦 localhost 的專用 IP
        private const val BASE_URL = "http://10.0.2.2:8080/"

        fun create(): ImsaApiService {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            val client = OkHttpClient.Builder()
                .addInterceptor(logging)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()

            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ImsaApiService::class.java)
        }
    }
}

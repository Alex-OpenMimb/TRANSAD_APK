package com.transad.app.api

import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {
    @POST("api/auth/login")
    fun login(@Body body: LoginRequest): Call<LoginResponse>

    @POST("api/auth/me")
    fun getMe(): Call<MeResponse>

    @POST("api/auth/logout")
    fun logout(): Call<ResponseBody>
}

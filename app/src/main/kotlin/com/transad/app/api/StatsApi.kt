package com.transad.app.api

import retrofit2.Call
import retrofit2.http.GET

interface StatsApi {
    @GET("api/stats")
    fun getStats(): Call<StatsResponse>
}

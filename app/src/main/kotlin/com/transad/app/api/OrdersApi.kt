package com.transad.app.api

import retrofit2.Call
import retrofit2.http.GET

interface OrdersApi {
    @GET("api/orders")
    fun getOrders(): Call<OrdersResponse>
}

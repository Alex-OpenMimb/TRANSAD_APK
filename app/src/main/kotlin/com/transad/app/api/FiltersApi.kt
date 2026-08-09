package com.transad.app.api

import com.google.gson.JsonElement
import com.google.gson.reflect.TypeToken
import retrofit2.Call
import retrofit2.http.GET

interface FiltersApi {
    @GET("api/filters/product-types")
    fun getProductTypes(): Call<JsonElement>

    @GET("api/filters/brands")
    fun getBrands(): Call<JsonElement>

    @GET("api/filters/conditions")
    fun getConditions(): Call<JsonElement>

    @GET("api/filters/order-statuses")
    fun getOrderStatuses(): Call<OrderStatusesResponse>
}

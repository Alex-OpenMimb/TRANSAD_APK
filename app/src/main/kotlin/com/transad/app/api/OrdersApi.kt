package com.transad.app.api

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface OrdersApi {
    @GET("api/orders")
    fun getOrders(): Call<OrdersResponse>

    @POST("api/product-entities")
    fun createProductEntities(
        @Body body: ProductEntitiesRequest
    ): Call<ProductEntitiesResponse>
}

interface ProductsApi {
    @GET("api/products")
    fun getProducts(): Call<ProductsResponse>
}

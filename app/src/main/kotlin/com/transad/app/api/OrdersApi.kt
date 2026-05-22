package com.transad.app.api

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface OrdersApi {
    @GET("api/orders")
    fun getOrders(
        @Query("reference") reference: String? = null,
        @Query("date_from") dateFrom: String? = null,
        @Query("date_to") dateTo: String? = null,
        @Query("cost_center_id") costCenterId: Int? = null,
        @Query("license_plate") licensePlate: String? = null
    ): Call<OrdersResponse>

    @GET("api/orders/{id}")
    fun getOrder(@Path("id") orderId: Int): Call<OrderResponse>

    @POST("api/product-entities")
    fun createProductEntities(
        @Body body: ProductEntitiesRequest
    ): Call<ProductEntitiesResponse>
}

interface ProductsApi {
    @GET("api/products")
    fun getProducts(): Call<ProductsResponse>
}

interface CostCentersApi {
    @GET("api/cost-centers")
    fun getCostCenters(
        @Query("search") search: String? = null,
        @Query("status") status: Int? = null
    ): Call<CostCentersResponse>
}

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
    fun getProducts(
        @Query("search") search: String? = null,
        @Query("reference") reference: String? = null,
        @Query("product_type_id") productTypeId: Int? = null,
        @Query("brand_id") brandId: Int? = null,
        @Query("condition_id") conditionId: Int? = null,
        @Query("status") status: Int? = null,
        @Query("cost_min") costMin: String? = null,
        @Query("cost_max") costMax: String? = null
    ): Call<ProductsResponse>
}

interface CostCentersApi {
    @GET("api/cost-centers")
    fun getCostCenters(
        @Query("search") search: String? = null,
        @Query("status") status: Int? = null
    ): Call<CostCentersResponse>

    @GET("api/cost-centers/{id}")
    fun getCostCenter(@Path("id") id: Int): Call<CostCenterDetailResponse>
}

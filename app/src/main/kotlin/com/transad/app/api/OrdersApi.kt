package com.transad.app.api

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface OrdersApi {
    @GET("api/orders")
    fun getOrders(): Call<OrdersResponse>

    /** Envía las lecturas RFID de una orden. Ajusta la ruta si tu API usa otra. */
    @POST("api/orders/{orderId}/scans")
    fun submitScans(
        @Path("orderId") orderId: Int,
        @Body body: RfidScansRequest
    ): Call<RfidScansResponse>
}

interface ProductsApi {
    @GET("api/products")
    fun getProducts(): Call<ProductsResponse>
}

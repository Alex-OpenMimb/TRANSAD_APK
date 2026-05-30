package com.transad.app.api

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException

object ApiGson {

    val instance: Gson = GsonBuilder()
        .registerTypeAdapter(OrdersResponse::class.java, OrdersResponseAdapter())
        .registerTypeAdapter(OrderResponse::class.java, OrderResponseAdapter())
        .registerTypeAdapter(CostCentersResponse::class.java, CostCentersResponseAdapter())
        .registerTypeAdapter(ProductsResponse::class.java, ProductsResponseAdapter())
        .registerTypeAdapter(CostCenterDetailResponse::class.java, CostCenterDetailResponseAdapter())
        .create()
}

fun Throwable.isJsonParseError(): Boolean =
    this is JsonSyntaxException ||
        cause is JsonSyntaxException ||
        message?.contains("JsonReader", ignoreCase = true) == true ||
        message?.contains("Expected ", ignoreCase = true) == true ||
        message?.contains("End of input", ignoreCase = true) == true

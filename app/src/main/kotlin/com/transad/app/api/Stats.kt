package com.transad.app.api

import com.google.gson.annotations.SerializedName

data class StatsResponse(
    @SerializedName("data") val data: BusinessStats,
    @SerializedName("message") val message: String? = null,
    @SerializedName("response") val response: Boolean = false,
    @SerializedName("status") val status: Int = 0
)

data class BusinessStats(
    @SerializedName("vehicles") val vehicles: ActiveInactiveStats,
    @SerializedName("orders") val orders: OrdersStats,
    @SerializedName("requisitions") val requisitions: ActiveInactiveStats,
    @SerializedName("tires") val tires: TiresStats
)

data class ActiveInactiveStats(
    @SerializedName("total") val total: Int = 0,
    @SerializedName("active") val active: Int = 0,
    @SerializedName("inactive") val inactive: Int = 0
)

data class OrdersStats(
    @SerializedName("total") val total: Int = 0,
    @SerializedName("by_status") val byStatus: Map<String, OrderStatusCount>? = null
)

data class OrderStatusCount(
    @SerializedName("label") val label: String = "",
    @SerializedName("total") val total: Int = 0
)

data class TiresStats(
    @SerializedName("installed") val installed: Int = 0,
    @SerializedName("spares") val spares: Int = 0,
    @SerializedName("total") val total: Int = 0
)

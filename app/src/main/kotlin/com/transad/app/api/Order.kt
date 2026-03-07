package com.transad.app.api

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

data class OrdersResponse(
    @SerializedName("data") val data: List<Order>,
    @SerializedName("message") val message: String? = null,
    @SerializedName("response") val response: Boolean = false,
    @SerializedName("status") val status: Int = 0
)

data class Order(
    @SerializedName("id") val id: Int,
    @SerializedName("user_id") val userId: Int,
    @SerializedName("business_id") val businessId: Int,
    @SerializedName("requisition_id") val requisitionId: Int,
    @SerializedName("reference") val reference: String,
    @SerializedName("status") val status: Boolean,
    @SerializedName("type_order") val typeOrder: String,
    @SerializedName("document") val document: JsonElement? = null,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String,
    @SerializedName("order_products") val orderProducts: List<Any>? = null
) {
    /** Obtiene las observaciones del document (puede ser objeto o array vacío). */
    fun getObservations(): String {
        if (document == null || !document.isJsonObject) return ""
        val obj = document.asJsonObject
        if (!obj.has("observations")) return ""
        val obs = obj.get("observations")
        return if (obs.isJsonNull) "" else obs.asString
    }
}

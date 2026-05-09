package com.transad.app.api

import android.os.Parcelable
import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

data class OrdersResponse(
    @SerializedName("data") val data: List<Order>,
    @SerializedName("message") val message: String? = null,
    @SerializedName("response") val response: Boolean = false,
    @SerializedName("status") val status: Int = 0
)

data class ProductsResponse(
    @SerializedName("data") val data: List<Product>,
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
    @SerializedName("order_products") val orderProducts: List<OrderProduct>? = null
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

@Parcelize
data class OrderProduct(
    @SerializedName("id") val id: Int,
    @SerializedName("order_id") val orderId: Int,
    @SerializedName("requisition_product_id") val requisitionProductId: Int,
    @SerializedName("productable_type") val productableType: String? = null,
    @SerializedName("productable_id") val productableId: Int,
    @SerializedName("scanned_quantity") val scannedQuantity: Int = 0,
    @SerializedName("product_quantity") val productQuantity: Int,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null,
    @SerializedName("productable") val productable: Product? = null,
    @SerializedName("status") val orderLineStatus: String? = null
) : Parcelable

/** Datos capturados en el modal tras leer un RFID (solo uso en app). */
data class RfidScanPayload(
    val rfidCode: String,
    val tireCode: String,
    val position: String,
    val observation: String? = null,
    val tireDepth: String? = null,
    val tireThickness: String? = null
)

/** `POST api/product-entities` */
data class ProductEntitiesRequest(
    @SerializedName("items") val items: List<ProductEntityItem>
)

data class ProductEntityItem(
    @SerializedName("product_id") val productId: Int,
    @SerializedName("quantity") val quantity: Int = 1,
    @SerializedName("order_product_id") val orderProductId: Int,
    @SerializedName("user_id") val userId: Int,
    @SerializedName("code") val code: String,
    @SerializedName("type") val type: String,
    @SerializedName("additional_information") val additionalInformation: ProductEntityAdditionalInformation? = null
)

data class ProductEntityAdditionalInformation(
    @SerializedName("description") val description: String? = null,
    @SerializedName("position") val position: String,
    @SerializedName("observation") val observation: String? = null
)

data class ProductEntitiesResponse(
    @SerializedName("message") val message: String? = null,
    @SerializedName("success") val success: Boolean? = null
)

@Parcelize
data class Product(
    @SerializedName("id") val id: Int,
    @SerializedName("code") val code: String? = null,
    @SerializedName("reference") val reference: String? = null,
    @SerializedName("id_erp") val idErp: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("brand_name") val brandName: String? = null,
    @SerializedName("condition_name") val conditionName: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("status") val status: Boolean = true,
    @SerializedName("stock_quantity") val stockQuantity: Int = 0,
    @SerializedName("cost") val cost: String? = null
) : Parcelable

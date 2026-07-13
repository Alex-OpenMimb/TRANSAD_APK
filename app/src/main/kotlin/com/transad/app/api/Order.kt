package com.transad.app.api

import android.os.Parcelable
import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

data class OrdersResponse(
    @SerializedName("data") val data: List<OrderListItem>,
    @SerializedName("message") val message: String? = null,
    @SerializedName("response") val response: Boolean = false,
    @SerializedName("status") val status: Int = 0
)

data class OrderResponse(
    @SerializedName("data") val data: Order,
    @SerializedName("message") val message: String? = null,
    @SerializedName("response") val response: Boolean = false,
    @SerializedName("status") val status: Int = 0
)

data class CostCentersResponse(
    @SerializedName("data") val data: List<CostCenter>,
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

@Parcelize
data class CostCenter(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("license_plate") val licensePlate: String? = null,
    @SerializedName("reference") val reference: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("status") val status: Boolean = true
) : Parcelable

/** Orden resumida para el listado (`GET api/orders`). No incluye `order_products`. */
data class OrderListItem(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("user_id") val userId: Int = 0,
    @SerializedName("cost_center_id") val costCenterId: Int? = null,
    @SerializedName("reference") val reference: String = "",
    @SerializedName("status") val status: Boolean = true,
    @SerializedName("type_order") val typeOrder: String = "",
    @SerializedName("document") val document: JsonElement? = null,
    @SerializedName("created_at") val createdAt: String = "",
    @SerializedName("updated_at") val updatedAt: String = "",
    @SerializedName("cost_center") val costCenter: CostCenter? = null
) {
    fun licensePlateFromCostCenter(): String =
        costCenter?.licensePlate?.trim()?.takeIf { it.isNotEmpty() }?.uppercase() ?: ""

    fun getObservations(): String {
        if (document == null || !document.isJsonObject) return ""
        val obj = document.asJsonObject
        if (!obj.has("observations")) return ""
        val obs = obj.get("observations")
        return if (obs.isJsonNull) "" else obs.asString
    }
}

data class Order(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("user_id") val userId: Int = 0,
    @SerializedName("business_id") val businessId: Int = 0,
    @SerializedName("requisition_id") val requisitionId: Int = 0,
    @SerializedName("cost_center_id") val costCenterId: Int? = null,
    @SerializedName("reference") val reference: String = "",
    @SerializedName("status") val status: Boolean = true,
    @SerializedName("type_order") val typeOrder: String = "",
    @SerializedName("document") val document: JsonElement? = null,
    @SerializedName("created_at") val createdAt: String = "",
    @SerializedName("updated_at") val updatedAt: String = "",
    @SerializedName("cost_center") val costCenter: CostCenter? = null,
    @SerializedName("order_products") val orderProducts: List<OrderProduct>? = null
) {
    /** Placa del centro de costo asociado a la orden, si viene en la API. */
    fun licensePlateFromCostCenter(): String =
        costCenter?.licensePlate?.trim()?.takeIf { it.isNotEmpty() }?.uppercase() ?: ""

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
    @SerializedName("id") val id: Int = 0,
    @SerializedName("order_id") val orderId: Int = 0,
    @SerializedName("requisition_product_id") val requisitionProductId: Int = 0,
    @SerializedName("productable_type") val productableType: String? = null,
    @SerializedName("productable_id") val productableId: Int = 0,
    @SerializedName("scanned_quantity") val scannedQuantity: Int = 0,
    @SerializedName("product_quantity") val productQuantity: Int = 0,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null,
    @SerializedName("productable") val productable: Product? = null,
    @SerializedName("status") val orderLineStatus: String? = null,
    /** `etiqueta` | `llanta` — prioridad sobre inferencia por nombre del producto. */
    @SerializedName("type") val lineType: String? = null
) : Parcelable

/** Datos capturados en el modal tras leer un RFID (solo uso en app). */
data class RfidScanPayload(
    val rfidCode: String,
    val tireCode: String,
    val licensePlate: String,
    val position: Int,
    val observation: String? = null,
    /** Producto (product_type_id=1, Llantas) elegido cuando la línea es Banda de reencauche. */
    val parentProductId: Int? = null
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
    @SerializedName("license_plate") val licensePlate: String,
    @SerializedName("position") val position: Int,
    /** Obligatorio en `type: etiqueta`: id de la línea llanta emparejada en el mismo envío. */
    @SerializedName("paired_order_product_id") val pairedOrderProductId: Int? = null,
    @SerializedName("additional_information") val additionalInformation: ProductEntityAdditionalInformation? = null,
    /** Solo aplica si `product_id` es Banda de reencauche y `code` no existe todavía; el backend lo ignora en Llanta/Etiqueta. */
    @SerializedName("parent_id") val parentId: Int? = null
)

data class ProductEntityAdditionalInformation(
    @SerializedName("observacion") val observacion: String
)

data class ProductEntitiesResponse(
    @SerializedName("message") val message: String? = null,
    @SerializedName("success") val success: Boolean? = null
)

@Parcelize
data class ProductTypeInfo(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("name") val name: String? = null
) : Parcelable

@Parcelize
data class Product(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("code") val code: String? = null,
    @SerializedName("reference") val reference: String? = null,
    @SerializedName("id_erp") val idErp: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("brand_name") val brandName: String? = null,
    @SerializedName("condition_name") val conditionName: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("status") val status: Boolean = true,
    @SerializedName("stock_quantity") val stockQuantity: Int = 0,
    @SerializedName("cost") val cost: String? = null,
    @SerializedName("product_type_id") val productTypeId: Int = 0,
    @SerializedName("configuration_name") val configurationName: String? = null,
    @SerializedName("configuration_id") val configurationId: Int = 0,
    @IgnoredOnParcel val productTypeInfo: ProductTypeInfo? = null,
    /** Nombre plano del tipo cuando la API no envía el objeto anidado. */
    @SerializedName("product_type") val productType: String? = null,
    @IgnoredOnParcel val attributes: List<ProductAttribute> = emptyList()
) : Parcelable {
    fun typeName(): String =
        productTypeInfo?.name?.trim().orEmpty().ifBlank { productType?.trim().orEmpty() }
}

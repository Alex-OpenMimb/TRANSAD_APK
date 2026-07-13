package com.transad.app

import com.transad.app.api.OrderProduct
import com.transad.app.api.Product
import com.transad.app.api.ProductEntityAdditionalInformation
import com.transad.app.api.ProductEntityItem
import com.transad.app.api.RfidScanPayload
import java.util.ArrayDeque
import java.util.Locale

/** Clasificación de línea de orden: etiqueta RFID o llanta. */
enum class ProductLineKind {
    ETIQUETA,
    LLANTA,
    UNKNOWN
}

/** Usa [OrderProduct.lineType] de la API; si no viene, infiere por nombre del [Product]. */
fun OrderProduct.lineKind(): ProductLineKind {
    val fromApi = lineType?.trim()?.lowercase(Locale.getDefault())
    if (!fromApi.isNullOrBlank()) {
        when {
            fromApi == ProductEntityTypes.ETIQUETA || fromApi.contains("etiqueta") ->
                return ProductLineKind.ETIQUETA
            fromApi == ProductEntityTypes.LLANTA || fromApi.contains("llanta") ->
                return ProductLineKind.LLANTA
        }
    }
    return productable.inferLineKindFromName()
}

/** Valor de `type` para `POST api/product-entities` (prioriza [OrderProduct.lineType] de la API). */
fun OrderProduct.entityTypeForApi(): String {
    val fromApi = lineType?.trim()?.lowercase(Locale.getDefault())
    if (fromApi == ProductEntityTypes.ETIQUETA || fromApi == ProductEntityTypes.LLANTA) {
        return fromApi
    }
    return when (lineKind()) {
        ProductLineKind.ETIQUETA -> ProductEntityTypes.ETIQUETA
        ProductLineKind.LLANTA -> ProductEntityTypes.LLANTA
        ProductLineKind.UNKNOWN -> ProductEntityTypes.LLANTA
    }
}

private fun Product?.inferLineKindFromName(): ProductLineKind {
    val n = this?.name?.lowercase(Locale.getDefault()).orEmpty()
    if (n.contains("etiqueta") || n.contains("uhf") || n.contains("tire tag") ||
        n.contains("chipset") || (n.contains("tag") && n.contains("tire"))
    ) {
        return ProductLineKind.ETIQUETA
    }
    if (n.contains("llanta")) return ProductLineKind.LLANTA
    return ProductLineKind.UNKNOWN
}

fun OrderProduct.isLinePending(): Boolean {
    if (orderLineStatus.isNullOrBlank()) return scannedQuantity < productQuantity
    if (!orderLineStatus.equals("pendiente", ignoreCase = true)) return false
    return scannedQuantity < productQuantity
}

/** `product_type_id` de "Banda de reencauche" en el catálogo de productos. */
const val RETREAD_BAND_PRODUCT_TYPE_ID = 3

/** true si esta línea de llanta corresponde a un producto Banda de reencauche (requiere `parent_id`). */
fun OrderProduct?.isRetreadBandLine(): Boolean = this?.productable?.productTypeId == RETREAD_BAND_PRODUCT_TYPE_ID

/**
 * Líneas de llanta pendientes en el mismo orden en que [buildProductEntityItemsForScans] las va
 * asignando (una por cada scan, en orden de captura). Permite al diálogo de captura saber, antes
 * de enviar el lote, con qué línea de llanta va a emparejarse un scan concreto.
 */
fun pendingTireLines(orderProducts: List<OrderProduct>): List<OrderProduct> =
    orderProducts.filter { it.lineKind() == ProductLineKind.LLANTA && it.isLinePending() }.sortedBy { it.id }

private fun RfidScanPayload.buildLlantaAdditionalInfo(): ProductEntityAdditionalInformation? {
    val text = observation?.trim().orEmpty()
    if (text.isEmpty()) return null
    return ProductEntityAdditionalInformation(observacion = text)
}

private fun RfidScanPayload.toEntityItem(
    productId: Int,
    orderProductId: Int,
    userId: Int,
    code: String,
    type: String,
    pairedOrderProductId: Int? = null,
    includeAdditionalInfo: Boolean = false,
    parentId: Int? = null
): ProductEntityItem = ProductEntityItem(
    productId = productId,
    quantity = 1,
    orderProductId = orderProductId,
    userId = userId,
    code = code,
    type = type,
    licensePlate = licensePlate.trim().uppercase(),
    position = position,
    pairedOrderProductId = pairedOrderProductId,
    additionalInformation = if (includeAdditionalInfo) buildLlantaAdditionalInfo() else null,
    parentId = parentId
)

/**
 * Por cada lectura RFID se generan **dos** ítems (etiqueta + llanta) con la misma placa y posición.
 * El `code` del body es el que capturó el usuario (EPC / código de llanta); no se valida contra el catálogo.
 * Se asigna la siguiente línea pendiente de etiqueta y la siguiente de llanta en la orden.
 */
fun buildProductEntityItemsForScans(
    scans: List<RfidScanPayload>,
    orderProducts: List<OrderProduct>,
    userId: Int
): Result<List<ProductEntityItem>> {
    if (userId <= 0) {
        return Result.failure(IllegalStateException("INVALID_USER_ID"))
    }
    val tagQueue = ArrayDeque(
        orderProducts
            .filter { it.lineKind() == ProductLineKind.ETIQUETA && it.isLinePending() }
            .sortedBy { it.id }
    )
    val tireQueue = ArrayDeque(pendingTireLines(orderProducts))

    if (tagQueue.isEmpty()) {
        return Result.failure(IllegalStateException("NO_TAG_LINE"))
    }
    if (tireQueue.isEmpty()) {
        return Result.failure(IllegalStateException("NO_TIRE_LINE"))
    }

    val out = mutableListOf<ProductEntityItem>()
    for (scan in scans) {
        if (scan.licensePlate.isBlank()) {
            return Result.failure(IllegalStateException("NO_LICENSE_PLATE"))
        }
        val tagLine = tagQueue.pollFirst()
            ?: return Result.failure(IllegalStateException("NO_TAG_LEFT"))
        val tireLine = tireQueue.pollFirst()
            ?: return Result.failure(IllegalStateException("NO_TIRE_LEFT"))
        val tagProductId = tagLine.productable?.id ?: tagLine.productableId
        val tireProductId = tireLine.productable?.id ?: tireLine.productableId

        out.add(
            scan.toEntityItem(
                productId = tagProductId,
                orderProductId = tagLine.id,
                userId = userId,
                code = scan.rfidCode,
                type = tagLine.entityTypeForApi(),
                pairedOrderProductId = tireLine.id,
                includeAdditionalInfo = false
            )
        )
        out.add(
            scan.toEntityItem(
                productId = tireProductId,
                orderProductId = tireLine.id,
                userId = userId,
                code = scan.tireCode.trim(),
                type = tireLine.entityTypeForApi(),
                pairedOrderProductId = null,
                includeAdditionalInfo = true,
                parentId = if (tireLine.isRetreadBandLine()) scan.parentProductId else null
            )
        )
    }
    return Result.success(out)
}

object ProductEntityTypes {
    const val ETIQUETA = "etiqueta"
    const val LLANTA = "llanta"
}

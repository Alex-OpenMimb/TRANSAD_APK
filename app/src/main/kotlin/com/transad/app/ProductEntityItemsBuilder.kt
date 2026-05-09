package com.transad.app

import com.transad.app.api.OrderProduct
import com.transad.app.api.RfidScanPayload
import com.transad.app.api.Product
import com.transad.app.api.ProductEntityAdditionalInformation
import com.transad.app.api.ProductEntityItem
import java.util.ArrayDeque
import java.util.Locale

/** Clasificación de línea de orden según el nombre del producto (API). */
enum class ProductLineKind {
    ETIQUETA,
    LLANTA,
    UNKNOWN
}

fun Product?.productLineKind(): ProductLineKind {
    val n = this?.name?.lowercase(Locale.getDefault()).orEmpty()
    // Etiqueta RFID: priorizar palabras típicas del catálogo antes que "llanta" en el nombre.
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

fun Product?.matchesUserCode(input: String): Boolean {
    if (this == null) return false
    val t = input.trim()
    if (t.isEmpty()) return false
    return t.equals(reference?.trim(), ignoreCase = true) ||
        t.equals(idErp?.trim(), ignoreCase = true) ||
        t.equals(code?.trim(), ignoreCase = true)
}

private fun RfidScanPayload.buildTireAdditionalInfo(): ProductEntityAdditionalInformation {
    val descParts = buildList {
        tireDepth?.takeIf { it.isNotBlank() }?.let { add("Profundidad: $it") }
        tireThickness?.takeIf { it.isNotBlank() }?.let { add("Grosor: $it") }
    }
    val description = descParts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    return ProductEntityAdditionalInformation(
        description = description,
        position = position,
        observation = observation
    )
}

/**
 * Por cada lectura RFID se generan **dos** ítems: `etiqueta` (código = EPC) y `llanta` (código manual + additional_information).
 * Consume líneas pendientes de la orden en orden de `id` (etiquetas en cola; llantas coincidentes con el código).
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
            .filter { it.productable.productLineKind() == ProductLineKind.ETIQUETA && it.isLinePending() }
            .sortedBy { it.id }
    )
    val tirePool = orderProducts
        .filter { it.productable.productLineKind() == ProductLineKind.LLANTA && it.isLinePending() }
        .sortedBy { it.id }
        .toMutableList()

    if (tagQueue.isEmpty()) {
        return Result.failure(IllegalStateException("NO_TAG_LINE"))
    }
    if (tirePool.isEmpty()) {
        return Result.failure(IllegalStateException("NO_TIRE_LINE"))
    }

    val out = mutableListOf<ProductEntityItem>()
    for (scan in scans) {
        val tagLine = tagQueue.pollFirst()
            ?: return Result.failure(IllegalStateException("NO_TAG_LEFT"))
        val tireIdx = tirePool.indexOfFirst { it.productable.matchesUserCode(scan.tireCode) }
        if (tireIdx < 0) {
            return Result.failure(IllegalStateException("NO_TIRE_MATCH:${scan.tireCode}"))
        }
        val tireLine = tirePool.removeAt(tireIdx)
        val tagProductId = tagLine.productable?.id ?: tagLine.productableId
        val tireProductId = tireLine.productable?.id ?: tireLine.productableId

        out.add(
            ProductEntityItem(
                productId = tagProductId,
                quantity = 1,
                orderProductId = tagLine.id,
                userId = userId,
                code = scan.rfidCode,
                type = ProductEntityTypes.ETIQUETA,
                additionalInformation = null
            )
        )
        out.add(
            ProductEntityItem(
                productId = tireProductId,
                quantity = 1,
                orderProductId = tireLine.id,
                userId = userId,
                code = scan.tireCode.trim(),
                type = ProductEntityTypes.LLANTA,
                additionalInformation = scan.buildTireAdditionalInfo()
            )
        )
    }
    return Result.success(out)
}

object ProductEntityTypes {
    const val ETIQUETA = "etiqueta"
    const val LLANTA = "llanta"
}

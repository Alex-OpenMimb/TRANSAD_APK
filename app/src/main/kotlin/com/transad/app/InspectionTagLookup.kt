package com.transad.app

import com.transad.app.api.CostCenterDetail
import com.transad.app.api.MountedPosition
import com.transad.app.api.MountedTire
import com.transad.app.api.ProductAttribute
import com.transad.app.api.TireInspection

data class InspectionLabelMatch(
    val position: MountedPosition,
    val tire: MountedTire,
    val labelCode: String
)

object InspectionTagLookup {

    fun normalizeLabelCode(code: String): String = code.trim().uppercase()

    fun findLabelInVehicle(detail: CostCenterDetail, labelCode: String): InspectionLabelMatch? {
        val key = normalizeLabelCode(labelCode)
        if (key.isEmpty()) return null
        for (position in detail.mountedPositions) {
            val tire = position.tire ?: continue
            val pairCode = tire.pair?.code?.trim()?.uppercase().orEmpty()
            if (pairCode.isNotEmpty() && pairCode == key) {
                return InspectionLabelMatch(position = position, tire = tire, labelCode = labelCode.trim())
            }
        }
        return null
    }

    fun inspectionAttributes(tire: MountedTire?): List<ProductAttribute> =
        tire?.attributes?.filter { it.isInspectionContext() }.orEmpty()

    fun hasInspectionToday(tire: MountedTire): Boolean =
        tire.inspections.recent.any { InspectionDates.isToday(it.createdAt) }

    /** Busca una inspección de hoy para la etiqueta en cualquier llanta del vehículo. */
    fun findTodayInspectionForLabel(detail: CostCenterDetail, labelCode: String): TireInspection? {
        val key = normalizeLabelCode(labelCode)
        if (key.isEmpty()) return null
        for (position in detail.mountedPositions) {
            val tire = position.tire ?: continue
            tire.inspections.recent.forEach { inspection ->
                val inspectionKey = inspection.labelCode?.trim()?.uppercase().orEmpty()
                if (inspectionKey == key && InspectionDates.isToday(inspection.createdAt)) {
                    return inspection
                }
            }
        }
        return null
    }

    /** Bloquea si la llanta ya fue inspeccionada hoy o si la etiqueta ya tiene inspección hoy. */
    fun findBlockingInspectionToday(
        detail: CostCenterDetail,
        labelCode: String,
        matchedTire: MountedTire?
    ): TireInspection? {
        matchedTire?.inspections?.recent
            ?.firstOrNull { InspectionDates.isToday(it.createdAt) }
            ?.let { return it }
        return findTodayInspectionForLabel(detail, labelCode)
    }
}

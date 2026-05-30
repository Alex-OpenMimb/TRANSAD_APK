package com.transad.app.api

data class CostCenterDetailResponse(
    val data: CostCenterDetail,
    val message: String? = null,
    val response: Boolean = false,
    val status: Int = 0
)

data class CostCenterDetail(
    val id: Int = 0,
    val licensePlate: String? = null,
    val reference: String? = null,
    val description: String? = null,
    val status: Boolean = true,
    val entryDate: String? = null,
    val observations: String? = null,
    val vehicle: CostCenterVehicle? = null,
    val configuration: CostCenterConfiguration? = null,
    val summary: CostCenterPositionSummary? = null,
    val mountedPositions: List<MountedPosition> = emptyList()
)

data class CostCenterVehicle(
    val brand: String? = null,
    val model: String? = null,
    val serial: String? = null,
    val vin: String? = null,
    val cylinder: String? = null,
    val capacity: String? = null,
    val fuelType: String? = null,
    val bodyType: String? = null,
    val engineType: String? = null,
    val kmCount: Int = 0,
    val kmCountDate: String? = null
)

data class CostCenterConfiguration(
    val id: Int = 0,
    val name: String? = null,
    val spareTires: Int = 0
)

data class CostCenterPositionSummary(
    val totalPositions: Int = 0,
    val occupiedPositions: Int = 0,
    val emptyPositions: Int = 0,
    val spareSlots: Int = 0
)

data class MountedPosition(
    val slotId: Int = 0,
    val axisNumber: Int = 0,
    val side: String? = null,
    val position: Int = 0,
    val axisType: String? = null,
    val isSpare: Boolean = false,
    val occupied: Boolean = false,
    val tire: MountedTire? = null
)

data class MountedTire(
    val productEntityId: Int = 0,
    val code: String? = null,
    val lot: String? = null,
    val name: String? = null,
    val reference: String? = null,
    val brand: String? = null,
    val condition: String? = null,
    val productType: String? = null,
    val configuration: String? = null,
    val isUsing: Boolean = false,
    val product: MountedTireProduct? = null,
    val attributes: List<ProductAttribute> = emptyList(),
    val pairedProductEntityId: Int? = null,
    val pair: MountedTirePair? = null,
    val inspections: TireInspectionsSummary = TireInspectionsSummary()
)

data class ProductAttribute(
    val productAttributeId: Int = 0,
    val attributeId: Int = 0,
    val code: String? = null,
    val name: String? = null,
    val type: String? = null,
    val value: String? = null,
    val contexts: List<String> = emptyList()
) {
    fun isInspectionContext(): Boolean = contexts.any { it.equals("inspection", ignoreCase = true) }
}

data class MountedTireProduct(
    val id: Int = 0,
    val name: String? = null,
    val reference: String? = null,
    val idErp: String? = null
)

data class MountedTirePair(
    val productEntityId: Int = 0,
    val code: String? = null,
    val name: String? = null,
    val reference: String? = null,
    val brand: String? = null,
    val condition: String? = null,
    val productType: String? = null,
    val product: MountedTireProduct? = null,
    val attributes: List<ProductAttribute> = emptyList()
) {
    fun isLabelEntity(): Boolean = productType?.trim().equals("Etiqueta", ignoreCase = true)
}

data class TireInspectionsSummary(
    val total: Int = 0,
    val recent: List<TireInspection> = emptyList()
)

data class TireInspection(
    val id: Int = 0,
    val status: String? = null,
    val labelCode: String? = null,
    val observations: String? = null,
    val attributesSnapshot: List<InspectionAttributeSnapshot> = emptyList(),
    val positionSnapshot: InspectionPositionSnapshot? = null,
    val createdAt: String? = null
)

data class InspectionAttributeSnapshot(
    val code: String? = null,
    val name: String? = null,
    val value: String? = null,
    val attributeId: Int = 0
)

data class InspectionPositionSnapshot(
    val side: String? = null,
    val slotId: Int = 0,
    val isSpare: Boolean = false,
    val position: Int = 0,
    val axisType: String? = null,
    val axisNumber: Int = 0
)

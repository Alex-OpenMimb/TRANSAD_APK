package com.transad.app.api

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken

internal fun JsonReader.enableLenient() {
    isLenient = true
}

/** Quita BOM y basura antes del primer `{` o `[`. */
internal fun sanitizeJsonPayload(raw: String): String {
    val trimmed = raw.trim().removePrefix("\uFEFF")
    val startObj = trimmed.indexOf('{')
    val startArr = trimmed.indexOf('[')
    val start = when {
        startObj >= 0 && startArr >= 0 -> minOf(startObj, startArr)
        startObj >= 0 -> startObj
        startArr >= 0 -> startArr
        else -> 0
    }
    return trimmed.substring(start)
}

internal fun JsonReader.readStringValue(): String? {
    return when (peek()) {
        JsonToken.NULL -> {
            nextNull()
            null
        }
        JsonToken.STRING -> nextString()
        JsonToken.NUMBER -> nextString()
        JsonToken.BOOLEAN -> nextBoolean().toString()
        else -> {
            skipValue()
            null
        }
    }
}

internal fun JsonReader.readIntValue(default: Int = 0): Int {
    return when (peek()) {
        JsonToken.NULL -> {
            nextNull()
            default
        }
        JsonToken.NUMBER -> nextDouble().toInt()
        JsonToken.STRING -> nextString().trim().toIntOrNull() ?: default
        JsonToken.BOOLEAN -> if (nextBoolean()) 1 else 0
        else -> {
            skipValue()
            default
        }
    }
}

internal fun JsonReader.readBoolValue(default: Boolean = false): Boolean {
    return when (peek()) {
        JsonToken.NULL -> {
            nextNull()
            default
        }
        JsonToken.BOOLEAN -> nextBoolean()
        JsonToken.NUMBER -> nextDouble() != 0.0
        JsonToken.STRING -> {
            val raw = nextString().trim()
            raw == "1" || raw.equals("true", ignoreCase = true)
        }
        else -> {
            skipValue()
            default
        }
    }
}

internal fun JsonReader.readCostCenter(): CostCenter {
    beginObject()
    var id = 0
    var licensePlate: String? = null
    var reference: String? = null
    var description: String? = null
    var status = true
    while (hasNext()) {
        when (nextName()) {
            "id" -> id = readIntValue()
            "license_plate" -> licensePlate = readStringValue()
            "reference" -> reference = readStringValue()
            "description" -> description = readStringValue()
            "status" -> status = readBoolValue(true)
            else -> skipValue()
        }
    }
    endObject()
    return CostCenter(
        id = id,
        licensePlate = licensePlate,
        reference = reference,
        description = description,
        status = status
    )
}

internal fun JsonReader.readCostCenterList(): List<CostCenter> {
    val items = mutableListOf<CostCenter>()
    beginArray()
    while (hasNext()) {
        items += readCostCenter()
    }
    endArray()
    return items
}

internal fun JsonReader.readOrderListItem(): OrderListItem {
    beginObject()
    var id = 0
    var userId = 0
    var costCenterId: Int? = null
    var reference = ""
    var status = true
    var typeOrder = ""
    var createdAt = ""
    var updatedAt = ""
    var costCenter: CostCenter? = null
    while (hasNext()) {
        when (nextName()) {
            "id" -> id = readIntValue()
            "user_id" -> userId = readIntValue()
            "cost_center_id" -> costCenterId = readIntValue().takeIf { it != 0 }
            "reference" -> reference = readStringValue().orEmpty()
            "status" -> status = readBoolValue(true)
            "type_order" -> typeOrder = readStringValue().orEmpty()
            "document" -> skipValue()
            "created_at" -> createdAt = readStringValue().orEmpty()
            "updated_at" -> updatedAt = readStringValue().orEmpty()
            "cost_center" -> costCenter = if (peek() == JsonToken.NULL) {
                nextNull()
                null
            } else {
                readCostCenter()
            }
            "order_products" -> skipValue()
            else -> skipValue()
        }
    }
    endObject()
    return OrderListItem(
        id = id,
        userId = userId,
        costCenterId = costCenterId,
        reference = reference,
        status = status,
        typeOrder = typeOrder,
        createdAt = createdAt,
        updatedAt = updatedAt,
        costCenter = costCenter
    )
}

internal fun JsonReader.readOrderList(): List<OrderListItem> {
    val items = mutableListOf<OrderListItem>()
    beginArray()
    while (hasNext()) {
        items += readOrderListItem()
    }
    endArray()
    return items
}

internal fun JsonReader.readProductTypeInfo(): ProductTypeInfo {
    beginObject()
    var id = 0
    var name: String? = null
    while (hasNext()) {
        when (nextName()) {
            "id" -> id = readIntValue()
            "name" -> name = readStringValue()
            else -> skipValue()
        }
    }
    endObject()
    return ProductTypeInfo(id = id, name = name)
}

internal fun JsonReader.readProduct(): Product {
    beginObject()
    var id = 0
    var code: String? = null
    var reference: String? = null
    var idErp: String? = null
    var name: String? = null
    var brandName: String? = null
    var conditionName: String? = null
    var description: String? = null
    var status = true
    var stockQuantity = 0
    var cost: String? = null
    var productTypeId = 0
    var configurationName: String? = null
    var configurationId = 0
    var productType: String? = null
    var productTypeInfo: ProductTypeInfo? = null
    var attributes = emptyList<ProductAttribute>()
    while (hasNext()) {
        when (nextName()) {
            "id" -> id = readIntValue()
            "code" -> code = readStringValue()
            "reference" -> reference = readStringValue()
            "id_erp" -> idErp = readStringValue()
            "name" -> name = readStringValue()
            "brand_name" -> brandName = readStringValue()
            "condition_name" -> conditionName = readStringValue()
            "description" -> description = readStringValue()
            "status" -> status = readBoolValue(true)
            "stock_quantity" -> stockQuantity = readIntValue()
            "cost" -> cost = readStringValue()
            "product_type_id" -> productTypeId = readIntValue()
            "configuration_name" -> configurationName = readStringValue()
            "configuration_id" -> configurationId = readIntValue()
            "product_type" -> when (peek()) {
                JsonToken.NULL -> {
                    nextNull()
                    productTypeInfo = null
                }
                JsonToken.BEGIN_OBJECT -> productTypeInfo = readProductTypeInfo()
                else -> productType = readStringValue()
            }
            "attributes" -> attributes = if (peek() == JsonToken.NULL) {
                nextNull()
                emptyList()
            } else {
                readProductAttributeList()
            }
            else -> skipValue()
        }
    }
    endObject()
    return Product(
        id = id,
        code = code,
        reference = reference,
        idErp = idErp,
        name = name,
        brandName = brandName,
        conditionName = conditionName,
        description = description,
        status = status,
        stockQuantity = stockQuantity,
        cost = cost,
        productTypeId = productTypeId,
        configurationName = configurationName,
        configurationId = configurationId,
        productTypeInfo = productTypeInfo,
        productType = productType ?: productTypeInfo?.name,
        attributes = attributes
    )
}

internal fun JsonReader.readProductList(): List<Product> {
    val items = mutableListOf<Product>()
    beginArray()
    while (hasNext()) {
        items += readProduct()
    }
    endArray()
    return items
}

internal fun JsonReader.readOrderProduct(): OrderProduct {
    beginObject()
    var id = 0
    var orderId = 0
    var requisitionProductId = 0
    var productableType: String? = null
    var productableId = 0
    var scannedQuantity = 0
    var productQuantity = 0
    var createdAt: String? = null
    var updatedAt: String? = null
    var productable: Product? = null
    var orderLineStatus: String? = null
    var lineType: String? = null
    while (hasNext()) {
        when (nextName()) {
            "id" -> id = readIntValue()
            "order_id" -> orderId = readIntValue()
            "requisition_product_id" -> requisitionProductId = readIntValue()
            "productable_type" -> productableType = readStringValue()
            "productable_id" -> productableId = readIntValue()
            "scanned_quantity" -> scannedQuantity = readIntValue()
            "product_quantity" -> productQuantity = readIntValue()
            "created_at" -> createdAt = readStringValue()
            "updated_at" -> updatedAt = readStringValue()
            "productable" -> productable = if (peek() == JsonToken.NULL) {
                nextNull()
                null
            } else {
                readProduct()
            }
            "status" -> orderLineStatus = readStringValue()
            "type" -> lineType = readStringValue()
            else -> skipValue()
        }
    }
    endObject()
    return OrderProduct(
        id = id,
        orderId = orderId,
        requisitionProductId = requisitionProductId,
        productableType = productableType,
        productableId = productableId,
        scannedQuantity = scannedQuantity,
        productQuantity = productQuantity,
        createdAt = createdAt,
        updatedAt = updatedAt,
        productable = productable,
        orderLineStatus = orderLineStatus,
        lineType = lineType
    )
}

internal fun JsonReader.readOrderProductList(): List<OrderProduct> {
    val items = mutableListOf<OrderProduct>()
    beginArray()
    while (hasNext()) {
        items += readOrderProduct()
    }
    endArray()
    return items
}

internal fun JsonReader.readOrder(): Order {
    beginObject()
    var id = 0
    var userId = 0
    var businessId = 0
    var requisitionId = 0
    var costCenterId: Int? = null
    var reference = ""
    var status = true
    var typeOrder = ""
    var createdAt = ""
    var updatedAt = ""
    var costCenter: CostCenter? = null
    var orderProducts: List<OrderProduct>? = null
    while (hasNext()) {
        when (nextName()) {
            "id" -> id = readIntValue()
            "user_id" -> userId = readIntValue()
            "business_id" -> businessId = readIntValue()
            "requisition_id" -> requisitionId = readIntValue()
            "cost_center_id" -> costCenterId = readIntValue().takeIf { it != 0 }
            "reference" -> reference = readStringValue().orEmpty()
            "status" -> status = readBoolValue(true)
            "type_order" -> typeOrder = readStringValue().orEmpty()
            "document" -> skipValue()
            "created_at" -> createdAt = readStringValue().orEmpty()
            "updated_at" -> updatedAt = readStringValue().orEmpty()
            "cost_center" -> costCenter = if (peek() == JsonToken.NULL) {
                nextNull()
                null
            } else {
                readCostCenter()
            }
            "order_products" -> orderProducts = if (peek() == JsonToken.NULL) {
                nextNull()
                null
            } else {
                readOrderProductList()
            }
            else -> skipValue()
        }
    }
    endObject()
    return Order(
        id = id,
        userId = userId,
        businessId = businessId,
        requisitionId = requisitionId,
        costCenterId = costCenterId,
        reference = reference,
        status = status,
        typeOrder = typeOrder,
        createdAt = createdAt,
        updatedAt = updatedAt,
        costCenter = costCenter,
        orderProducts = orderProducts
    )
}

internal fun JsonReader.readStringList(): List<String> {
    val items = mutableListOf<String>()
    beginArray()
    while (hasNext()) {
        readStringValue()?.let { items.add(it) }
    }
    endArray()
    return items
}

internal fun JsonReader.readProductAttribute(): ProductAttribute {
    beginObject()
    var productAttributeId = 0
    var attributeId = 0
    var code: String? = null
    var name: String? = null
    var type: String? = null
    var value: String? = null
    var contexts = emptyList<String>()
    while (hasNext()) {
        when (nextName()) {
            "product_attribute_id" -> productAttributeId = readIntValue()
            "attribute_id" -> attributeId = readIntValue()
            "code" -> code = readStringValue()
            "name" -> name = readStringValue()
            "type" -> type = readStringValue()
            "value" -> value = readStringValue()
            "contexts" -> contexts = readStringList()
            else -> skipValue()
        }
    }
    endObject()
    return ProductAttribute(
        productAttributeId = productAttributeId,
        attributeId = attributeId,
        code = code,
        name = name,
        type = type,
        value = value,
        contexts = contexts
    )
}

internal fun JsonReader.readProductAttributeList(): List<ProductAttribute> {
    val items = mutableListOf<ProductAttribute>()
    beginArray()
    while (hasNext()) {
        items += readProductAttribute()
    }
    endArray()
    return items
}

internal fun JsonReader.readInspectionAttributeSnapshot(): InspectionAttributeSnapshot {
    beginObject()
    var code: String? = null
    var name: String? = null
    var value: String? = null
    var attributeId = 0
    while (hasNext()) {
        when (nextName()) {
            "code" -> code = readStringValue()
            "name" -> name = readStringValue()
            "value" -> value = readStringValue()
            "attribute_id" -> attributeId = readIntValue()
            else -> skipValue()
        }
    }
    endObject()
    return InspectionAttributeSnapshot(
        code = code,
        name = name,
        value = value,
        attributeId = attributeId
    )
}

internal fun JsonReader.readInspectionAttributeSnapshotList(): List<InspectionAttributeSnapshot> {
    val items = mutableListOf<InspectionAttributeSnapshot>()
    beginArray()
    while (hasNext()) {
        items += readInspectionAttributeSnapshot()
    }
    endArray()
    return items
}

internal fun JsonReader.readInspectionPositionSnapshot(): InspectionPositionSnapshot {
    beginObject()
    var side: String? = null
    var slotId = 0
    var isSpare = false
    var position = 0
    var axisType: String? = null
    var axisNumber = 0
    while (hasNext()) {
        when (nextName()) {
            "side" -> side = readStringValue()
            "slot_id" -> slotId = readIntValue()
            "is_spare" -> isSpare = readBoolValue()
            "position" -> position = readIntValue()
            "axis_type" -> axisType = readStringValue()
            "axis_number" -> axisNumber = readIntValue()
            else -> skipValue()
        }
    }
    endObject()
    return InspectionPositionSnapshot(
        side = side,
        slotId = slotId,
        isSpare = isSpare,
        position = position,
        axisType = axisType,
        axisNumber = axisNumber
    )
}

internal fun JsonReader.readTireInspection(): TireInspection {
    beginObject()
    var id = 0
    var status: String? = null
    var labelCode: String? = null
    var observations: String? = null
    var attributesSnapshot = emptyList<InspectionAttributeSnapshot>()
    var positionSnapshot: InspectionPositionSnapshot? = null
    var createdAt: String? = null
    while (hasNext()) {
        when (nextName()) {
            "id" -> id = readIntValue()
            "status" -> status = readStringValue()
            "label_code" -> labelCode = readStringValue()
            "observations" -> observations = readStringValue()
            "attributes_snapshot" -> attributesSnapshot = if (peek() == JsonToken.NULL) {
                nextNull()
                emptyList()
            } else {
                readInspectionAttributeSnapshotList()
            }
            "position_snapshot" -> positionSnapshot = if (peek() == JsonToken.NULL) {
                nextNull()
                null
            } else {
                readInspectionPositionSnapshot()
            }
            "created_at" -> createdAt = readStringValue()
            else -> skipValue()
        }
    }
    endObject()
    return TireInspection(
        id = id,
        status = status,
        labelCode = labelCode,
        observations = observations,
        attributesSnapshot = attributesSnapshot,
        positionSnapshot = positionSnapshot,
        createdAt = createdAt
    )
}

internal fun JsonReader.readTireInspectionList(): List<TireInspection> {
    val items = mutableListOf<TireInspection>()
    beginArray()
    while (hasNext()) {
        items += readTireInspection()
    }
    endArray()
    return items
}

internal fun JsonReader.readTireInspectionsSummary(): TireInspectionsSummary {
    beginObject()
    var total = 0
    var recent = emptyList<TireInspection>()
    while (hasNext()) {
        when (nextName()) {
            "total" -> total = readIntValue()
            "recent" -> recent = if (peek() == JsonToken.NULL) {
                nextNull()
                emptyList()
            } else {
                readTireInspectionList()
            }
            else -> skipValue()
        }
    }
    endObject()
    return TireInspectionsSummary(total = total, recent = recent)
}

internal fun JsonReader.readMountedTireProduct(): MountedTireProduct {
    beginObject()
    var id = 0
    var name: String? = null
    var reference: String? = null
    var idErp: String? = null
    while (hasNext()) {
        when (nextName()) {
            "id" -> id = readIntValue()
            "name" -> name = readStringValue()
            "reference" -> reference = readStringValue()
            "id_erp" -> idErp = readStringValue()
            else -> skipValue()
        }
    }
    endObject()
    return MountedTireProduct(id = id, name = name, reference = reference, idErp = idErp)
}

internal fun JsonReader.readMountedTirePair(): MountedTirePair {
    beginObject()
    var productEntityId = 0
    var code: String? = null
    var name: String? = null
    var reference: String? = null
    var brand: String? = null
    var condition: String? = null
    var productType: String? = null
    var product: MountedTireProduct? = null
    var attributes = emptyList<ProductAttribute>()
    while (hasNext()) {
        when (nextName()) {
            "product_entity_id" -> productEntityId = readIntValue()
            "code" -> code = readStringValue()
            "name" -> name = readStringValue()
            "reference" -> reference = readStringValue()
            "brand" -> brand = readStringValue()
            "condition" -> condition = readStringValue()
            "product_type" -> productType = readStringValue()
            "product" -> product = if (peek() == JsonToken.NULL) {
                nextNull()
                null
            } else {
                readMountedTireProduct()
            }
            "attributes" -> attributes = if (peek() == JsonToken.NULL) {
                nextNull()
                emptyList()
            } else {
                readProductAttributeList()
            }
            else -> skipValue()
        }
    }
    endObject()
    return MountedTirePair(
        productEntityId = productEntityId,
        code = code,
        name = name,
        reference = reference,
        brand = brand,
        condition = condition,
        productType = productType,
        product = product,
        attributes = attributes
    )
}

internal fun JsonReader.readMountedTire(): MountedTire {
    beginObject()
    var productEntityId = 0
    var code: String? = null
    var lot: String? = null
    var name: String? = null
    var reference: String? = null
    var brand: String? = null
    var condition: String? = null
    var productType: String? = null
    var configuration: String? = null
    var isUsing = false
    var product: MountedTireProduct? = null
    var pairedProductEntityId: Int? = null
    var pair: MountedTirePair? = null
    var attributes = emptyList<ProductAttribute>()
    var inspections = TireInspectionsSummary()
    while (hasNext()) {
        when (nextName()) {
            "product_entity_id" -> productEntityId = readIntValue()
            "code" -> code = readStringValue()
            "lot" -> lot = readStringValue()
            "name" -> name = readStringValue()
            "reference" -> reference = readStringValue()
            "brand" -> brand = readStringValue()
            "condition" -> condition = readStringValue()
            "product_type" -> productType = readStringValue()
            "configuration" -> configuration = readStringValue()
            "is_using" -> isUsing = readBoolValue()
            "product" -> product = if (peek() == JsonToken.NULL) {
                nextNull()
                null
            } else {
                readMountedTireProduct()
            }
            "paired_product_entity_id" -> pairedProductEntityId = readIntValue().takeIf { it != 0 }
            "pair" -> pair = if (peek() == JsonToken.NULL) {
                nextNull()
                null
            } else {
                readMountedTirePair()
            }
            "attributes" -> attributes = if (peek() == JsonToken.NULL) {
                nextNull()
                emptyList()
            } else {
                readProductAttributeList()
            }
            "inspections" -> inspections = if (peek() == JsonToken.NULL) {
                nextNull()
                TireInspectionsSummary()
            } else {
                readTireInspectionsSummary()
            }
            else -> skipValue()
        }
    }
    endObject()
    return MountedTire(
        productEntityId = productEntityId,
        code = code,
        lot = lot,
        name = name,
        reference = reference,
        brand = brand,
        condition = condition,
        productType = productType,
        configuration = configuration,
        isUsing = isUsing,
        product = product,
        pairedProductEntityId = pairedProductEntityId,
        pair = pair,
        attributes = attributes,
        inspections = inspections
    )
}

internal fun JsonReader.readMountedPosition(): MountedPosition {
    beginObject()
    var slotId = 0
    var axisNumber = 0
    var side: String? = null
    var position = 0
    var axisType: String? = null
    var isSpare = false
    var occupied = false
    var tire: MountedTire? = null
    while (hasNext()) {
        when (nextName()) {
            "slot_id" -> slotId = readIntValue()
            "axis_number" -> axisNumber = readIntValue()
            "side" -> side = readStringValue()
            "position" -> position = readIntValue()
            "axis_type" -> axisType = readStringValue()
            "is_spare" -> isSpare = readBoolValue()
            "occupied" -> occupied = readBoolValue()
            "tire" -> tire = if (peek() == JsonToken.NULL) {
                nextNull()
                null
            } else {
                readMountedTire()
            }
            else -> skipValue()
        }
    }
    endObject()
    return MountedPosition(
        slotId = slotId,
        axisNumber = axisNumber,
        side = side,
        position = position,
        axisType = axisType,
        isSpare = isSpare,
        occupied = occupied,
        tire = tire
    )
}

internal fun JsonReader.readMountedPositionList(): List<MountedPosition> {
    val items = mutableListOf<MountedPosition>()
    beginArray()
    while (hasNext()) {
        items += readMountedPosition()
    }
    endArray()
    return items
}

internal fun JsonReader.readCostCenterVehicle(): CostCenterVehicle {
    beginObject()
    var brand: String? = null
    var model: String? = null
    var serial: String? = null
    var vin: String? = null
    var cylinder: String? = null
    var capacity: String? = null
    var fuelType: String? = null
    var bodyType: String? = null
    var engineType: String? = null
    var kmCount = 0
    var kmCountDate: String? = null
    while (hasNext()) {
        when (nextName()) {
            "brand" -> brand = readStringValue()
            "model" -> model = readStringValue()
            "serial" -> serial = readStringValue()
            "vin" -> vin = readStringValue()
            "cylinder" -> cylinder = readStringValue()
            "capacity" -> capacity = readStringValue()
            "fuel_type" -> fuelType = readStringValue()
            "body_type" -> bodyType = readStringValue()
            "engine_type" -> engineType = readStringValue()
            "km_count" -> kmCount = readIntValue()
            "km_count_date" -> kmCountDate = readStringValue()
            else -> skipValue()
        }
    }
    endObject()
    return CostCenterVehicle(
        brand = brand,
        model = model,
        serial = serial,
        vin = vin,
        cylinder = cylinder,
        capacity = capacity,
        fuelType = fuelType,
        bodyType = bodyType,
        engineType = engineType,
        kmCount = kmCount,
        kmCountDate = kmCountDate
    )
}

internal fun JsonReader.readCostCenterConfiguration(): CostCenterConfiguration {
    beginObject()
    var id = 0
    var name: String? = null
    var spareTires = 0
    while (hasNext()) {
        when (nextName()) {
            "id" -> id = readIntValue()
            "name" -> name = readStringValue()
            "spare_tires" -> spareTires = readIntValue()
            else -> skipValue()
        }
    }
    endObject()
    return CostCenterConfiguration(id = id, name = name, spareTires = spareTires)
}

internal fun JsonReader.readCostCenterPositionSummary(): CostCenterPositionSummary {
    beginObject()
    var totalPositions = 0
    var occupiedPositions = 0
    var emptyPositions = 0
    var spareSlots = 0
    while (hasNext()) {
        when (nextName()) {
            "total_positions" -> totalPositions = readIntValue()
            "occupied_positions" -> occupiedPositions = readIntValue()
            "empty_positions" -> emptyPositions = readIntValue()
            "spare_slots" -> spareSlots = readIntValue()
            else -> skipValue()
        }
    }
    endObject()
    return CostCenterPositionSummary(
        totalPositions = totalPositions,
        occupiedPositions = occupiedPositions,
        emptyPositions = emptyPositions,
        spareSlots = spareSlots
    )
}

internal fun JsonReader.readCostCenterDetail(): CostCenterDetail {
    beginObject()
    var id = 0
    var licensePlate: String? = null
    var reference: String? = null
    var description: String? = null
    var status = true
    var entryDate: String? = null
    var observations: String? = null
    var vehicle: CostCenterVehicle? = null
    var configuration: CostCenterConfiguration? = null
    var summary: CostCenterPositionSummary? = null
    var mountedPositions = emptyList<MountedPosition>()
    while (hasNext()) {
        when (nextName()) {
            "id" -> id = readIntValue()
            "license_plate" -> licensePlate = readStringValue()
            "reference" -> reference = readStringValue()
            "description" -> description = readStringValue()
            "status" -> status = readBoolValue(true)
            "entry_date" -> entryDate = readStringValue()
            "observations" -> observations = readStringValue()
            "vehicle" -> vehicle = if (peek() == JsonToken.NULL) {
                nextNull()
                null
            } else {
                readCostCenterVehicle()
            }
            "configuration" -> configuration = if (peek() == JsonToken.NULL) {
                nextNull()
                null
            } else {
                readCostCenterConfiguration()
            }
            "summary" -> summary = if (peek() == JsonToken.NULL) {
                nextNull()
                null
            } else {
                readCostCenterPositionSummary()
            }
            "mounted_positions" -> mountedPositions = readMountedPositionList()
            else -> skipValue()
        }
    }
    endObject()
    return CostCenterDetail(
        id = id,
        licensePlate = licensePlate,
        reference = reference,
        description = description,
        status = status,
        entryDate = entryDate,
        observations = observations,
        vehicle = vehicle,
        configuration = configuration,
        summary = summary,
        mountedPositions = mountedPositions
    )
}

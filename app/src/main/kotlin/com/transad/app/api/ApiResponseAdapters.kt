package com.transad.app.api

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter

internal class OrdersResponseAdapter : TypeAdapter<OrdersResponse>() {
    override fun write(out: JsonWriter, value: OrdersResponse?) = Unit

    override fun read(reader: JsonReader): OrdersResponse {
        reader.enableLenient()
        var data = emptyList<OrderListItem>()
        var message: String? = null
        var response = false
        var status = 0
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "data" -> data = reader.readOrderList()
                "message" -> message = reader.readStringValue()
                "response" -> response = reader.readBoolValue()
                "status" -> status = reader.readIntValue()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return OrdersResponse(data = data, message = message, response = response, status = status)
    }
}

internal class OrderResponseAdapter : TypeAdapter<OrderResponse>() {
    override fun write(out: JsonWriter, value: OrderResponse?) = Unit

    override fun read(reader: JsonReader): OrderResponse {
        reader.enableLenient()
        var data: Order? = null
        var message: String? = null
        var response = false
        var status = 0
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "data" -> data = reader.readOrder()
                "message" -> message = reader.readStringValue()
                "response" -> response = reader.readBoolValue()
                "status" -> status = reader.readIntValue()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return OrderResponse(
            data = data ?: Order(),
            message = message,
            response = response,
            status = status
        )
    }
}

internal class CostCentersResponseAdapter : TypeAdapter<CostCentersResponse>() {
    override fun write(out: JsonWriter, value: CostCentersResponse?) = Unit

    override fun read(reader: JsonReader): CostCentersResponse {
        reader.enableLenient()
        var data = emptyList<CostCenter>()
        var message: String? = null
        var response = false
        var status = 0
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "data" -> data = reader.readCostCenterList()
                "message" -> message = reader.readStringValue()
                "response" -> response = reader.readBoolValue()
                "status" -> status = reader.readIntValue()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return CostCentersResponse(data = data, message = message, response = response, status = status)
    }
}

internal class ProductsResponseAdapter : TypeAdapter<ProductsResponse>() {
    override fun write(out: JsonWriter, value: ProductsResponse?) = Unit

    override fun read(reader: JsonReader): ProductsResponse {
        reader.enableLenient()
        var data = emptyList<Product>()
        var message: String? = null
        var response = false
        var status = 0
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "data" -> data = reader.readProductList()
                "message" -> message = reader.readStringValue()
                "response" -> response = reader.readBoolValue()
                "status" -> status = reader.readIntValue()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return ProductsResponse(data = data, message = message, response = response, status = status)
    }
}

internal class CostCenterDetailResponseAdapter : TypeAdapter<CostCenterDetailResponse>() {
    override fun write(out: JsonWriter, value: CostCenterDetailResponse?) = Unit

    override fun read(reader: JsonReader): CostCenterDetailResponse {
        reader.enableLenient()
        var data: CostCenterDetail? = null
        var message: String? = null
        var response = false
        var status = 0
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "data" -> data = reader.readCostCenterDetail()
                "message" -> message = reader.readStringValue()
                "response" -> response = reader.readBoolValue()
                "status" -> status = reader.readIntValue()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return CostCenterDetailResponse(
            data = data ?: CostCenterDetail(),
            message = message,
            response = response,
            status = status
        )
    }
}

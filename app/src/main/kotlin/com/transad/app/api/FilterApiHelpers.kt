package com.transad.app.api

import com.google.gson.JsonElement
import com.google.gson.reflect.TypeToken

internal inline fun <reified T> parseFilterJsonList(element: JsonElement?): List<T> {
    if (element == null || element.isJsonNull) return emptyList()
    val gson = ApiGson.instance
    val type = object : TypeToken<List<T>>() {}.type
    return when {
        element.isJsonArray -> gson.fromJson(element, type)
        element.isJsonObject -> {
            val data = element.asJsonObject.get("data") ?: return emptyList()
            gson.fromJson(data, type)
        }
        else -> emptyList()
    }
}

internal fun filterTypeIdByName(types: List<FilterProductType>, namePart: String): Int? =
    types.firstOrNull {
        it.name?.trim()?.lowercase()?.contains(namePart) == true
    }?.id

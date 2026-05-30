package com.transad.app.api

import com.google.gson.annotations.SerializedName

data class ProductsFilterState(
    val reference: String? = null,
    val productTypeId: Int? = null,
    val brandId: Int? = null,
    val conditionId: Int? = null,
    val status: Int? = null,
    val costMin: String? = null,
    val costMax: String? = null
) {
    fun hasPanelFilters(): Boolean =
        !reference.isNullOrBlank() ||
            productTypeId != null ||
            brandId != null ||
            conditionId != null ||
            status != null ||
            !costMin.isNullOrBlank() ||
            !costMax.isNullOrBlank()

    fun hasAnyFilter(): Boolean = hasPanelFilters()
}

data class FilterProductType(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("name") val name: String? = null
)

data class FilterBrand(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("name") val name: String? = null,
    @SerializedName("is_new") val isNew: Boolean? = null
)

data class FilterCondition(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("name") val name: String? = null,
    @SerializedName("code") val code: String? = null,
    @SerializedName("color") val color: String? = null
)

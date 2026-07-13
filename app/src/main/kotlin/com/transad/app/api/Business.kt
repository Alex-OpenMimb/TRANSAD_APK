package com.transad.app.api

import com.google.gson.annotations.SerializedName

data class BusinessesResponse(
    @SerializedName("data") val data: List<Business> = emptyList(),
    @SerializedName("current_business_id") val currentBusinessId: Int? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("response") val response: Boolean = false,
    @SerializedName("status") val status: Int = 0
)

data class Business(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("name") val name: String = "",
    @SerializedName("nit") val nit: String? = null,
    @SerializedName("is_current") val isCurrent: Boolean = false
)

data class SelectBusinessRequest(
    @SerializedName("business_id") val businessId: Int
)

data class SelectBusinessResponse(
    @SerializedName("business_id") val businessId: Int = 0,
    @SerializedName("message") val message: String? = null,
    @SerializedName("response") val response: Boolean = false,
    @SerializedName("status") val status: Int = 0
)

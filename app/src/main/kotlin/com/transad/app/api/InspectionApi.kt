package com.transad.app.api

import com.google.gson.annotations.SerializedName
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

data class InspectionRequest(
    @SerializedName("label_code") val labelCode: String,
    @SerializedName("cost_center_id") val costCenterId: Int,
    @SerializedName("user_id") val userId: Int,
    @SerializedName("attributes") val attributes: Map<String, String>,
    @SerializedName("observations") val observations: String? = null
)

data class InspectionResponse(
    @SerializedName("message") val message: String? = null,
    @SerializedName("response") val response: Boolean? = null,
    @SerializedName("status") val status: Int? = null
)

interface InspectionsApi {
    @POST("api/inspections")
    fun createInspection(@Body body: InspectionRequest): Call<InspectionResponse>
}

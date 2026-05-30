package com.transad.app.api

import com.google.gson.annotations.SerializedName
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

data class LabelingRequest(
    @SerializedName("user_id") val userId: Int,
    @SerializedName("pairs") val pairs: List<LabelingPairRequest>
)

data class LabelingPairRequest(
    @SerializedName("tire") val tire: LabelingTireRequest,
    @SerializedName("label") val label: LabelingLabelRequest
)

data class LabelingTireRequest(
    @SerializedName("product_id") val productId: Int,
    @SerializedName("code") val code: String,
    @SerializedName("attributes") val attributes: Map<String, String>
)

data class LabelingLabelRequest(
    @SerializedName("product_id") val productId: Int,
    @SerializedName("code") val code: String
)

data class LabelingResponse(
    @SerializedName("data") val data: LabelingResponseData? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("response") val response: Boolean? = null,
    @SerializedName("status") val status: Int? = null
)

data class LabelingResponseData(
    @SerializedName("pairs") val pairs: List<LabelingResultPair>? = null,
    @SerializedName("count") val count: Int = 0
)

data class LabelingResultPair(
    @SerializedName("tire") val tire: LabelingResultEntity? = null,
    @SerializedName("label") val label: LabelingResultEntity? = null
)

data class LabelingResultEntity(
    @SerializedName("product_entity_id") val productEntityId: Int = 0,
    @SerializedName("code") val code: String? = null,
    @SerializedName("paired_product_entity_id") val pairedProductEntityId: Int? = null
)

interface LabelingApi {
    @POST("api/labeling")
    fun createLabeling(@Body body: LabelingRequest): Call<LabelingResponse>
}

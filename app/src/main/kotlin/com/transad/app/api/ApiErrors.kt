package com.transad.app.api

import com.google.gson.annotations.SerializedName
import retrofit2.Response

data class ApiErrorResponse(
    @SerializedName("error") val error: String? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("response") val response: Boolean? = null,
    @SerializedName("status") val status: Int? = null
) {
    fun displayMessage(): String? =
        error?.trim()?.takeIf { it.isNotEmpty() }
            ?: message?.trim()?.takeIf { it.isNotEmpty() }
}

object ApiErrors {

    fun parseMessage(errorBody: String?): String? {
        if (errorBody.isNullOrBlank()) return null
        val fromJson = runCatching {
            ApiGson.instance.fromJson(sanitizeJsonPayload(errorBody), ApiErrorResponse::class.java).displayMessage()
        }.getOrNull()
        return fromJson?.takeIf { it.isNotBlank() }
    }

    fun parseMessage(response: Response<*>): String? =
        parseMessage(response.errorBody()?.string())

    fun formatHttpError(response: Response<*>, defaultMessage: String): String {
        val parsed = parseMessage(response)
        if (!parsed.isNullOrBlank()) return parsed
        val code = response.code()
        return if (code > 0) "$defaultMessage\n\n(Código HTTP $code)" else defaultMessage
    }

    fun formatNetworkError(defaultMessage: String, error: Throwable?): String {
        val detail = error?.message?.trim()?.takeIf { it.isNotEmpty() }
        return if (detail != null) "$defaultMessage\n\n$detail" else defaultMessage
    }
}

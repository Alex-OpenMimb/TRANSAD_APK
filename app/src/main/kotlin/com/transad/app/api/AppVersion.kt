package com.transad.app.api

import com.google.gson.annotations.SerializedName
import retrofit2.Call
import retrofit2.http.GET

/** `GET api/app-version/latest` — endpoint público, no requiere sesión. */
data class AppVersionResponse(
    @SerializedName("data") val data: AppVersionInfo? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("response") val response: Boolean = false,
    @SerializedName("status") val status: Int = 0
)

data class AppVersionInfo(
    @SerializedName("version_name") val versionName: String = "",
    @SerializedName("version_code") val versionCode: Int = 0,
    @SerializedName("download_url") val downloadUrl: String = "",
    @SerializedName("changelog") val changelog: String? = null,
    @SerializedName("is_mandatory") val isMandatory: Boolean = false,
    @SerializedName("file_size") val fileSize: Long? = null,
    @SerializedName("created_at") val createdAt: String? = null
)

interface AppVersionApi {
    @GET("api/app-version/latest")
    fun getLatestVersion(): Call<AppVersionResponse>
}

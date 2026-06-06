package com.transad.app.api

import android.util.Log
import com.google.gson.annotations.SerializedName
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.atomic.AtomicLong

data class ApiLogEntry(
    @SerializedName("id") val id: Long = 0L,
    @SerializedName("timestamp") val timestamp: Long = System.currentTimeMillis(),
    @SerializedName("method") val method: String = "",
    @SerializedName("url") val url: String = "",
    @SerializedName("path") val path: String = "",
    @SerializedName("section") val section: String = SECTION_OTHER,
    @SerializedName("status_code") val statusCode: Int? = null,
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("duration_ms") val durationMs: Long = 0L,
    @SerializedName("error_message") val errorMessage: String? = null,
    @SerializedName("request_headers") val requestHeaders: Map<String, String>? = null,
    @SerializedName("request_body") val requestBody: String? = null,
    @SerializedName("response_body") val responseBody: String? = null
) {
    fun formattedTime(): String = ApiLogTimeFormat.formatForList(timestamp)

    fun statusLabel(): String = when {
        errorMessage != null && statusCode == null -> "ERROR"
        statusCode != null -> statusCode.toString()
        else -> "—"
    }

    fun isError(): Boolean = !success || errorMessage != null

    fun formattedRequestHeaders(): String? =
        requestHeaders?.entries?.joinToString("\n") { (key, value) -> "$key: $value" }

    fun hasDetail(): Boolean =
        !url.isBlank() ||
            !requestHeaders.isNullOrEmpty() ||
            !requestBody.isNullOrBlank() ||
            !responseBody.isNullOrBlank() ||
            !errorMessage.isNullOrBlank()

    fun toLogcatLine(): String {
        val status = statusCode?.toString() ?: "FAIL"
        val suffix = errorMessage?.let { " · $it" }.orEmpty()
        return "${method.padEnd(6)} $status ${durationMs}ms $path$suffix"
    }

    /** Escribe request/response completos en logcat (para `adb logcat -s TRANSAD_API`). */
    fun logToLogcat(tag: String) {
        Log.d(tag, "──────────────────────────────────────")
        Log.d(tag, "▶ ${method.padEnd(6)} ${statusLabel()} ${durationMs}ms  $path")
        logChunked(tag, "URL: $url")
        formattedRequestHeaders()?.let { logChunked(tag, "Headers:\n$it") }
        requestBody?.let { logChunked(tag, "Request body:\n$it") }
        when {
            !responseBody.isNullOrBlank() -> logChunked(tag, "Response body:\n$responseBody")
            statusCode != null -> Log.d(tag, "Response body: (vacío, HTTP $statusCode)")
            errorMessage != null -> Log.d(tag, "Response: (sin respuesta · $errorMessage)")
        }
        Log.d(tag, "──────────────────────────────────────")
    }

    private fun logChunked(tag: String, text: String) {
        text.lineSequence().forEach { line ->
            if (line.length <= LOGCAT_CHUNK) {
                Log.d(tag, line)
            } else {
                line.chunked(LOGCAT_CHUNK).forEach { chunk -> Log.d(tag, chunk) }
            }
        }
    }

    companion object {
        private const val LOGCAT_CHUNK = 3500
        const val SECTION_AUTH = "Autenticación"
        const val SECTION_ORDERS = "Órdenes"
        const val SECTION_PRODUCTS = "Productos"
        const val SECTION_ENTITIES = "Entidades"
        const val SECTION_INSPECTION = "Inspección"
        const val SECTION_INSPECTIONS = "Inspecciones"
        const val SECTION_LABELING = "Etiquetado"
        const val SECTION_FILTERS = "Filtros"
        const val SECTION_STATS = "Inicio"
        const val SECTION_OTHER = "General"

        private val idSeq = AtomicLong(1L)

        fun create(
            request: Request,
            response: Response?,
            error: Throwable?,
            durationMs: Long,
            requestBodySnapshot: String? = null,
            responseBodySnapshot: String? = null
        ): ApiLogEntry {
            val url = request.url.toString()
            val path = request.url.encodedPath
            val code = response?.code
            val success = error == null && code != null && code in 200..299
            return ApiLogEntry(
                id = idSeq.getAndIncrement(),
                timestamp = System.currentTimeMillis(),
                method = request.method,
                url = url,
                path = path,
                section = sectionFromPath(path),
                statusCode = code,
                success = success,
                durationMs = durationMs,
                errorMessage = error?.message?.takeIf { it.isNotBlank() },
                requestHeaders = snapshotRequestHeaders(request),
                requestBody = requestBodySnapshot?.takeIf { it.isNotBlank() },
                responseBody = responseBodySnapshot?.takeIf { it.isNotBlank() }
            )
        }

        private fun snapshotRequestHeaders(request: Request): Map<String, String>? {
            if (request.headers.size == 0) return null
            return request.headers.names().associateWith { name ->
                request.header(name).orEmpty()
            }
        }

        fun sectionFromPath(path: String): String {
            val parts = path.trim('/').split('/')
            val apiSegment = when {
                parts.firstOrNull() == "api" && parts.size > 1 -> parts[1]
                parts.isNotEmpty() -> parts[0]
                else -> ""
            }
            return when (apiSegment) {
                "auth" -> SECTION_AUTH
                "orders" -> SECTION_ORDERS
                "product-entities" -> SECTION_ENTITIES
                "products" -> SECTION_PRODUCTS
                "cost-centers" -> SECTION_INSPECTION
                "inspections" -> SECTION_INSPECTIONS
                "labeling" -> SECTION_LABELING
                "filters" -> SECTION_FILTERS
                "stats" -> SECTION_STATS
                else -> SECTION_OTHER
            }
        }

        fun sectionOrder(section: String): Int = when (section) {
            SECTION_AUTH -> 0
            SECTION_STATS -> 1
            SECTION_ORDERS -> 2
            SECTION_PRODUCTS -> 3
            SECTION_FILTERS -> 4
            SECTION_ENTITIES -> 5
            SECTION_INSPECTION -> 6
            SECTION_INSPECTIONS -> 7
            SECTION_LABELING -> 8
            else -> 99
        }
    }
}

data class ApiLogSectionGroup(
    val section: String,
    val entries: List<ApiLogEntry>
) {
    val errorCount: Int = entries.count { it.isError() }
}

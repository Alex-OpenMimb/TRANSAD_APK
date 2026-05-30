package com.transad.app.api

import android.util.Log
import com.transad.app.BuildConfig
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.Buffer
import java.nio.charset.Charset

/** Registra cada llamada HTTP con método, URL, código, duración y sección. */
class ApiTrafficInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        if (!BuildConfig.DEBUG) {
            return chain.proceed(chain.request())
        }

        val (request, requestBodySnapshot) = snapshotRequestBody(chain.request())
        val startNs = System.nanoTime()
        var response: Response? = null
        var error: Throwable? = null
        try {
            response = chain.proceed(request)
            return response
        } catch (t: Throwable) {
            error = t
            throw t
        } finally {
            val durationMs = (System.nanoTime() - startNs) / 1_000_000L
            val entry = ApiLogEntry.create(
                request = request,
                response = response,
                error = error,
                durationMs = durationMs,
                requestBodySnapshot = requestBodySnapshot
            )
            ApiLogFile.appendEntry(entry)
            Log.d(API_LOG_TAG, entry.toLogcatLine())
        }
    }

    /** Lee el body sin consumirlo y devuelve una copia del request lista para enviar. */
    private fun snapshotRequestBody(request: Request): Pair<Request, String?> {
        val body = request.body ?: return request to null
        return try {
            val buffer = Buffer()
            body.writeTo(buffer)
            val contentType = body.contentType()
            val charset = contentType?.charset(Charset.forName("UTF-8")) ?: Charsets.UTF_8
            val text = buffer.readString(charset)
            val newBody = text.toRequestBody(contentType)
            val newRequest = request.newBuilder().method(request.method, newBody).build()
            newRequest to truncateBody(text)
        } catch (_: Exception) {
            request to null
        }
    }

    private fun truncateBody(text: String): String {
        if (text.length <= MAX_BODY_CHARS) return text
        return text.take(MAX_BODY_CHARS) + "\n… (truncado, ${text.length} chars total)"
    }

    companion object {
        private const val API_LOG_TAG = "TRANSAD_API"
        private const val MAX_BODY_CHARS = 8_192
    }
}

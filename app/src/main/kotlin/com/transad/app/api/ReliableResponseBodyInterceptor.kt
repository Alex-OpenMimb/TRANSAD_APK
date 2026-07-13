package com.transad.app.api

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException
import java.io.StringReader

/**
 * Lee el body completo, valida que el JSON esté cerrado y reintenta la petición HTTP
 * si llegó truncado (causa de `End of input` / `Unterminated object`).
 *
 * Con `Content-Length` presente (ver ForceContentLength en el backend), un cuerpo
 * truncado hace que `body.bytes()` lance IOException en vez de devolver bytes
 * incompletos en silencio — por eso esa llamada también debe ir dentro del retry.
 */
internal class ReliableResponseBodyInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var lastError: IOException? = null
        repeat(MAX_ATTEMPTS) { attempt ->
            val response = chain.proceed(request)
            val body = response.body ?: return response
            val contentType = body.contentType()
            val charset = contentType?.charset(Charsets.UTF_8) ?: Charsets.UTF_8

            val bytes = try {
                body.bytes()
            } catch (e: IOException) {
                lastError = e
                response.close()
                if (attempt < MAX_ATTEMPTS - 1) {
                    Thread.sleep(RETRY_DELAY_MS * (attempt + 1))
                }
                return@repeat
            }

            if (isCompleteJson(bytes, charset) || attempt == MAX_ATTEMPTS - 1) {
                return response.newBuilder()
                    .body(bytes.toResponseBody(contentType))
                    .build()
            }
            response.close()
            Thread.sleep(RETRY_DELAY_MS * (attempt + 1))
        }
        throw lastError ?: IOException("Respuesta truncada tras $MAX_ATTEMPTS intentos")
    }

    private fun isCompleteJson(bytes: ByteArray, charset: java.nio.charset.Charset): Boolean {
        if (bytes.isEmpty()) return false
        val text = sanitizeJsonPayload(String(bytes, charset)).trim()
        if (!text.endsWith('}') && !text.endsWith(']')) return false
        return try {
            val reader = JsonReader(StringReader(text)).apply { isLenient = true }
            reader.skipValue()
            reader.peek() == JsonToken.END_DOCUMENT
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        private const val MAX_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 200L
    }
}

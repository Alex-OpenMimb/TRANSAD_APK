package com.transad.app.api

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.TypeAdapter
import com.google.gson.reflect.TypeToken
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Converter
import retrofit2.Retrofit
import java.lang.reflect.Type

/** Parsea el body completo con Gson (más robusto que streaming manual). */
class ApiJsonConverterFactory private constructor(
    private val gson: Gson
) : Converter.Factory() {

    override fun responseBodyConverter(
        type: Type,
        annotations: Array<Annotation>,
        retrofit: Retrofit
    ): Converter<ResponseBody, *>? {
        return Converter { body ->
            body.use { responseBody ->
                val raw = responseBody.string()
                if (raw.isBlank()) {
                    throw JsonSyntaxException("Respuesta vacía del servidor")
                }
                val json = sanitizeJsonPayload(raw)
                @Suppress("UNCHECKED_CAST")
                gson.fromJson<Any>(json, type) as Any?
                    ?: throw JsonSyntaxException("No se pudo interpretar la respuesta del servidor")
            }
        }
    }

    override fun requestBodyConverter(
        type: Type,
        parameterAnnotations: Array<Annotation>,
        methodAnnotations: Array<Annotation>,
        retrofit: Retrofit
    ): Converter<*, RequestBody>? {
        val adapter: TypeAdapter<*> = gson.getAdapter(TypeToken.get(type))
        return Converter<Any, RequestBody> { value ->
            @Suppress("UNCHECKED_CAST")
            val json = (adapter as TypeAdapter<Any>).toJson(value)
            RequestBody.create(JSON, json)
        }
    }

    companion object {
        private val JSON = "application/json; charset=UTF-8".toMediaType()

        fun create(gson: Gson): ApiJsonConverterFactory = ApiJsonConverterFactory(gson)
    }
}

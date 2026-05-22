package com.transad.app.api

import android.util.Log
import com.transad.app.BuildConfig
import okhttp3.logging.HttpLoggingInterceptor

/** Tag para `adb logcat -s TRANSAD_API` (si tienes platform-tools en el PATH). */
const val API_LOG_TAG = "TRANSAD_API"

/**
 * Registra URL, headers, body de petición y respuesta (solo build **debug**).
 * Escribe en Logcat y en [ApiLogFile].
 */
fun createApiLoggingInterceptor(): HttpLoggingInterceptor {
    return HttpLoggingInterceptor { message ->
        if (!BuildConfig.DEBUG) return@HttpLoggingInterceptor
        Log.d(API_LOG_TAG, message)
        ApiLogFile.appendLine(message)
    }.apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }
}

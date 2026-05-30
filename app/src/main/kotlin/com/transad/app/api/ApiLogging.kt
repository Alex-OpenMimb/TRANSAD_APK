package com.transad.app.api

/** Tag para `adb logcat -s TRANSAD_API`. */
const val API_LOG_TAG = "TRANSAD_API"

fun createApiTrafficInterceptor(): ApiTrafficInterceptor = ApiTrafficInterceptor()

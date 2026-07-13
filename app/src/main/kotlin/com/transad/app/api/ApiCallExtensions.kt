package com.transad.app.api

import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

/** Reintenta con [Call.clone] cuando el JSON llega truncado (fallo intermitente de red). */
fun <T> Call<T>.enqueueWithRetry(
    maxAttempts: Int = 3,
    shouldRetry: (Throwable) -> Boolean = { it.isJsonParseError() },
    callback: Callback<T>
) {
    val original = this
    fun attempt(remaining: Int) {
        original.clone().enqueue(object : Callback<T> {
            override fun onResponse(call: Call<T>, response: Response<T>) {
                callback.onResponse(call, response)
            }

            override fun onFailure(call: Call<T>, t: Throwable) {
                if (remaining > 1 && !call.isCanceled && shouldRetry(t)) {
                    attempt(remaining - 1)
                } else {
                    callback.onFailure(call, t)
                }
            }
        })
    }
    attempt(maxAttempts)
}

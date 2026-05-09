package com.transad.app.api

import android.content.Context
import com.transad.app.SessionManager
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    
    private const val BASE_URL = "https://staging.tires.transadsas-group.com/"

    private var appContext: Context? = null

    /**
     * Llamar desde Application.onCreate() para que el interceptor pueda añadir el Bearer token.
     */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private val authInterceptor = Interceptor { chain ->
        val request = chain.request()
        val token = appContext?.let { SessionManager(it).getToken() }
        val newRequest = if (!token.isNullOrBlank()) {
            request.newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            request
        }
        chain.proceed(newRequest)
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttp: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttp)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val authApi: AuthApi get() = retrofit.create(AuthApi::class.java)
    val ordersApi: OrdersApi get() = retrofit.create(OrdersApi::class.java)
    val productsApi: ProductsApi get() = retrofit.create(ProductsApi::class.java)
}

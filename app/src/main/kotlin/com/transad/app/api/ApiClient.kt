package com.transad.app.api

import android.content.Context
import com.transad.app.SessionManager
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    const val BASE_URL = "https://staging.tires.transadsas-group.com/"

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private val apiInterceptor = Interceptor { chain ->
        val token = appContext?.let { SessionManager(it).getToken() }
        val builder = chain.request().newBuilder()
            .addHeader("Accept", "application/json")
        if (!token.isNullOrBlank()) {
            builder.addHeader("Authorization", "Bearer $token")
        }
        chain.proceed(builder.build())
    }

    private val trafficInterceptor by lazy { createApiTrafficInterceptor() }

    private val okHttp: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(apiInterceptor)
            .addInterceptor(trafficInterceptor)
            .build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttp)
            .addConverterFactory(GsonConverterFactory.create(ApiGson.instance))
            .build()
    }

    val authApi: AuthApi get() = retrofit.create(AuthApi::class.java)
    val ordersApi: OrdersApi get() = retrofit.create(OrdersApi::class.java)
    val productsApi: ProductsApi get() = retrofit.create(ProductsApi::class.java)
    val costCentersApi: CostCentersApi get() = retrofit.create(CostCentersApi::class.java)
    val statsApi: StatsApi get() = retrofit.create(StatsApi::class.java)
    val inspectionsApi: InspectionsApi get() = retrofit.create(InspectionsApi::class.java)
    val labelingApi: LabelingApi get() = retrofit.create(LabelingApi::class.java)
    val filtersApi: FiltersApi get() = retrofit.create(FiltersApi::class.java)
}

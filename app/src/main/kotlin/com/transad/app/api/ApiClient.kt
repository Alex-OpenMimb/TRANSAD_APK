package com.transad.app.api

import android.content.Context
import com.transad.app.SessionManager
import okhttp3.ConnectionPool
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

object ApiClient {

    const val BASE_URL = "http://192.168.80.23:8000"

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

    private val reliableBodyInterceptor = ReliableResponseBodyInterceptor()

    private val okHttp: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            // El backend local (`php artisan serve`) siempre cierra la conexión
            // (`Connection: close`). Si OkHttp reutiliza una conexión del pool
            // justo cuando el servidor ya la cerró, la lectura del body falla con
            // "unexpected end of stream". Forzamos una conexión nueva por request.
            .connectionPool(ConnectionPool(0, 1, TimeUnit.NANOSECONDS))
            .addInterceptor(apiInterceptor)
            .addInterceptor(trafficInterceptor)
            .addInterceptor(reliableBodyInterceptor)
            .build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttp)
            .addConverterFactory(ApiJsonConverterFactory.create(ApiGson.instance))
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
    val appVersionApi: AppVersionApi get() = retrofit.create(AppVersionApi::class.java)
}

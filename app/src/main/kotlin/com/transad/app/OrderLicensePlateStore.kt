package com.transad.app

import android.content.Context

/** Placa del vehículo asociada a una orden (persistida por `order_id`). */
class OrderLicensePlateStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun get(orderId: Int): String? = prefs.getString(key(orderId), null)?.takeIf { it.isNotBlank() }

    fun save(orderId: Int, licensePlate: String) {
        prefs.edit().putString(key(orderId), licensePlate.trim().uppercase()).apply()
    }

    private fun key(orderId: Int) = "order_license_$orderId"

    companion object {
        private const val PREFS_NAME = "transad_order_license_plates"
    }
}

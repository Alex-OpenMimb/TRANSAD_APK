package com.transad.app.api

/** Parámetros de consulta para `GET api/orders`. */
data class OrdersFilterState(
    val reference: String? = null,
    val dateFrom: String? = null,
    val dateTo: String? = null,
    val costCenterId: Int? = null,
    val licensePlate: String? = null,
    /** Código de workflow: `open`, `pending`, `closed`. */
    val statusCode: String? = null
) {
    fun hasAnyFilter(): Boolean =
        !reference.isNullOrBlank() ||
            !dateFrom.isNullOrBlank() ||
            !dateTo.isNullOrBlank() ||
            costCenterId != null ||
            !licensePlate.isNullOrBlank() ||
            !statusCode.isNullOrBlank()
}

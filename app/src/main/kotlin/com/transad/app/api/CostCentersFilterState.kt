package com.transad.app.api

/** Parámetros de consulta para `GET api/cost-centers`. */
data class CostCentersFilterState(
    val search: String? = null,
    /** `1` activos, `0` inactivos, `null` todos. */
    val status: Int? = null
) {
    fun hasAnyFilter(): Boolean =
        !search.isNullOrBlank() || status != null
}

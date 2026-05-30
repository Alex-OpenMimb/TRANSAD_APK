package com.transad.app

/** Datos capturados tras leer una etiqueta RFID en el módulo de etiquetado. */
data class LabelingScanPayload(
    val labelCode: String,
    val tireCode: String,
    val tireProductId: Int,
    val attributes: Map<String, String> = emptyMap()
)

data class LabelingScanRow(
    val stableId: Long,
    val scan: LabelingScanPayload
)

package com.transad.app.api

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Fecha/hora de logs API en zona horaria Colombia (America/Bogota). */
object ApiLogTimeFormat {

    private val BOGOTA = TimeZone.getTimeZone("America/Bogota")
    private val LOCALE = Locale("es", "CO")

    private val listFormat = ThreadLocal.withInitial {
        SimpleDateFormat("dd/MM/yyyy HH:mm:ss", LOCALE).apply { timeZone = BOGOTA }
    }

    private val exportFormat = ThreadLocal.withInitial {
        SimpleDateFormat("dd/MM/yyyy HH:mm:ss.SSS", LOCALE).apply { timeZone = BOGOTA }
    }

    fun formatForList(timestamp: Long): String =
        listFormat.get()!!.format(Date(timestamp))

    fun formatForExport(timestamp: Long): String =
        exportFormat.get()!!.format(Date(timestamp))
}

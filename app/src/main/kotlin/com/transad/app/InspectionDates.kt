package com.transad.app

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

object InspectionDates {

    private val zone: ZoneId = ZoneId.systemDefault()

    fun isToday(isoDateTime: String?): Boolean {
        if (isoDateTime.isNullOrBlank()) return false
        val inspectionDate = parseLocalDate(isoDateTime.trim()) ?: return false
        return inspectionDate == LocalDate.now(zone)
    }

    private fun parseLocalDate(raw: String): LocalDate? {
        return try {
            OffsetDateTime.parse(raw).atZoneSameInstant(zone).toLocalDate()
        } catch (_: Exception) {
            try {
                val datePart = raw.substringBefore('T').substringBefore(' ')
                LocalDate.parse(datePart)
            } catch (_: Exception) {
                null
            }
        }
    }
}

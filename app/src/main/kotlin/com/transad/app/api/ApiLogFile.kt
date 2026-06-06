package com.transad.app.api

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.transad.app.BuildConfig
import java.io.File

/**
 * Persiste entradas estructuradas de tráfico HTTP (JSONL, solo debug).
 * Ruta: `Android/data/com.transad.app/files/logs/api.log`
 */
object ApiLogFile {

    private const val MAX_BYTES = 5 * 1024 * 1024
    private const val MAX_ENTRIES = 500

    private var logFile: File? = null
    private val lock = Any()
    private val gson = Gson()

    fun init(context: Context) {
        if (!BuildConfig.DEBUG) return
        val dir = File(context.getExternalFilesDir(null), "logs").apply { mkdirs() }
        logFile = File(dir, "api.log")
    }

    fun appendEntry(entry: ApiLogEntry) {
        if (!BuildConfig.DEBUG) return
        val file = logFile ?: return
        synchronized(lock) {
            try {
                trimIfNeeded(file)
                file.appendText(gson.toJson(entry) + "\n")
            } catch (_: Exception) {
            }
        }
    }

    fun readEntries(): List<ApiLogEntry> {
        val file = logFile ?: return emptyList()
        if (!file.exists()) return emptyList()
        return synchronized(lock) {
            try {
                file.readLines()
                    .mapNotNull { line -> parseLine(line) }
                    .takeLast(MAX_ENTRIES)
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    fun readGroupedSections(): List<ApiLogSectionGroup> {
        val entries = readEntries().sortedByDescending { it.timestamp }
        return entries
            .groupBy { it.section }
            .entries
            .sortedWith(compareBy({ ApiLogEntry.sectionOrder(it.key) }, { it.key }))
            .map { (section, items) ->
                ApiLogSectionGroup(section = section, entries = items.sortedByDescending { it.timestamp })
            }
    }

    fun readAll(): String = formatExport(readEntries())

    fun formatExport(entries: List<ApiLogEntry>): String {
        if (entries.isEmpty()) return "(Sin registros de API)"
        val sorted = entries.sortedByDescending { it.timestamp }
        val grouped = sorted.groupBy { it.section }
            .entries
            .sortedWith(compareBy({ ApiLogEntry.sectionOrder(it.key) }, { it.key }))

        return buildString {
            appendLine("=== TRANSAD API Log ===")
            appendLine("Exportado: ${ApiLogTimeFormat.formatForExport(System.currentTimeMillis())} (Colombia)")
            appendLine("Total: ${sorted.size} llamadas · Errores: ${sorted.count { it.isError() }}")
            appendLine()

            grouped.forEach { (section, items) ->
                appendLine("── $section (${items.size}) ──")
                items.sortedByDescending { it.timestamp }.forEach { entry ->
                    appendLine(entry.toExportLine())
                }
                appendLine()
            }
        }
    }

    fun clear() {
        val file = logFile ?: return
        synchronized(lock) {
            try {
                file.writeText("")
            } catch (_: Exception) {
            }
        }
    }

    fun absolutePath(): String = logFile?.absolutePath ?: "(no disponible)"

    fun stats(entries: List<ApiLogEntry>): ApiLogStats {
        if (entries.isEmpty()) return ApiLogStats()
        val errors = entries.count { it.isError() }
        val avgMs = entries.map { it.durationMs }.average().toLong()
        val last = entries.maxByOrNull { it.timestamp }
        return ApiLogStats(
            total = entries.size,
            errors = errors,
            avgDurationMs = avgMs,
            lastTimestamp = last?.timestamp
        )
    }

    private fun parseLine(line: String): ApiLogEntry? {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("===") || trimmed.startsWith("…")) return null
        return try {
            gson.fromJson(trimmed, ApiLogEntry::class.java)
        } catch (_: JsonSyntaxException) {
            null
        }
    }

    private fun ApiLogEntry.toExportLine(): String {
        val time = ApiLogTimeFormat.formatForExport(timestamp)
        val status = statusLabel()
        val err = errorMessage?.let { " · $it" }.orEmpty()
        return buildString {
            append("$time  ${method.padEnd(6)} $status  ${durationMs}ms  $path$err\n")
            append("           URL: $url\n")
            formattedRequestHeaders()?.let { headers ->
                append("           Headers:\n")
                headers.lines().forEach { line -> append("             $line\n") }
            }
            if (!requestBody.isNullOrBlank()) {
                append("           Body enviado:\n")
                requestBody.lines().forEach { line -> append("             $line\n") }
            }
            if (!responseBody.isNullOrBlank()) {
                append("           Respuesta:\n")
                responseBody.lines().forEach { line -> append("             $line\n") }
            }
            if (!errorMessage.isNullOrBlank()) {
                append("           Error: $errorMessage\n")
            }
        }.trimEnd()
    }

    private fun trimIfNeeded(file: File) {
        if (file.length() <= MAX_BYTES) return
        val lines = file.readLines().filter { it.trim().isNotEmpty() }
        val keep = lines.takeLast(MAX_ENTRIES / 2)
        file.writeText(keep.joinToString("\n", postfix = "\n"))
    }
}

data class ApiLogStats(
    val total: Int = 0,
    val errors: Int = 0,
    val avgDurationMs: Long = 0L,
    val lastTimestamp: Long? = null
)

package com.transad.app.api

import android.content.Context
import com.transad.app.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Escribe cada línea del log HTTP en un archivo del dispositivo (solo debug).
 * Ruta típica: `Android/data/com.transad.app/files/logs/api.log`
 */
object ApiLogFile {

    private const val MAX_BYTES = 2 * 1024 * 1024 // 2 MB, luego se trunca

    private var logFile: File? = null
    private val lock = Any()
    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    fun init(context: Context) {
        if (!BuildConfig.DEBUG) return
        val dir = File(context.getExternalFilesDir(null), "logs").apply { mkdirs() }
        logFile = File(dir, "api.log")
        appendLine("=== TRANSAD API log iniciado ${timestampFormat.format(Date())} ===")
    }

    fun appendLine(message: String) {
        if (!BuildConfig.DEBUG) return
        val file = logFile ?: return
        synchronized(lock) {
            try {
                trimIfNeeded(file)
                file.appendText("${timestampFormat.format(Date())}  $message\n")
            } catch (_: Exception) {
                // Ignorar fallos de escritura en disco
            }
        }
    }

    fun readAll(): String {
        val file = logFile ?: return ""
        return try {
            if (file.exists()) file.readText() else "(Archivo de log vacío o aún no creado)"
        } catch (e: Exception) {
            "Error al leer log: ${e.message}"
        }
    }

    fun clear() {
        val file = logFile ?: return
        synchronized(lock) {
            try {
                file.writeText("")
                appendLine("=== Log limpiado ${timestampFormat.format(Date())} ===")
            } catch (_: Exception) {
            }
        }
    }

    fun absolutePath(): String = logFile?.absolutePath ?: "(no disponible)"

    private fun trimIfNeeded(file: File) {
        if (file.length() <= MAX_BYTES) return
        val keep = file.readText().takeLast(MAX_BYTES / 2)
        file.writeText("… (log truncado)\n$keep")
    }
}

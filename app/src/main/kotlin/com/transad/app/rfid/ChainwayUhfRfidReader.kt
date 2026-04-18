package com.transad.app.rfid

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.lang.reflect.Method

/**
 * Lector UHF RFID para **Chainway C72** (Android 11/13) usando el DeviceAPI ([com.rscja.deviceapi])
 * vía reflexión. En C72 con firmware reciente se usa [RFIDWithUHFUART]; el AAR suele llamarse
 * `DeviceAPI_ver*_release.aar` y va en `app/libs/`.
 *
 * Flujo recomendado por Chainway: init → startInventoryTag → readTagFromBuffer en bucle → stopInventory → free.
 */
class ChainwayUhfRfidReader(private val context: Context) {

    private var readerClass: Class<*>? = null
    private var readerInstance: Any? = null
    private var readThread: Thread? = null

    @Volatile
    private var inventoryRunning = false

    private val mainHandler = Handler(Looper.getMainLooper())

    val isDeviceApiPresent: Boolean
        get() = resolveReaderClass() != null

    private fun resolveReaderClass(): Class<*>? {
        if (readerClass != null) return readerClass
        readerClass = READER_CLASS_NAMES.firstNotNullOfOrNull { fqcn ->
            try {
                Class.forName(fqcn)
            } catch (_: ClassNotFoundException) {
                null
            }
        }
        return readerClass
    }

    /**
     * Abre el módulo UHF. Debe llamarse en hilo principal antes de [startContinuousInventory].
     */
    fun init(): Boolean {
        val clazz = resolveReaderClass() ?: return false
        return try {
            val getInstance = clazz.getMethod("getInstance")
            readerInstance = getInstance.invoke(null)
            val init = clazz.getMethod("init", Context::class.java)
            init.invoke(readerInstance, context) as Boolean
        } catch (e: Exception) {
            Log.e(TAG, "init() falló", e)
            readerInstance = null
            false
        }
    }

    /**
     * Inicia inventario continuo; [onEpc] recibe el EPC en hexadecimal (como devuelve el SDK).
     */
    fun startContinuousInventory(onEpc: (String) -> Unit): Boolean {
        val inst = readerInstance ?: return false
        val clazz = readerClass ?: return false
        return try {
            if (!invokeStartInventoryTag(inst, clazz)) {
                Log.e(TAG, "startInventoryTag() devolvió false")
                return false
            }
            inventoryRunning = true
            val readTagFromBuffer: Method = clazz.getMethod("readTagFromBuffer")
            val tagInfoClass = Class.forName("com.rscja.deviceapi.entity.UHFTAGInfo")
            val getEpc: Method = tagInfoClass.getMethod("getEPC")

            readThread = Thread({
                while (inventoryRunning) {
                    try {
                        val res = readTagFromBuffer.invoke(inst) ?: run {
                            Thread.sleep(POLL_MS_IDLE)
                            continue
                        }
                        val epc = (getEpc.invoke(res) as? String)?.trim().orEmpty()
                        if (epc.isNotEmpty()) {
                            mainHandler.post { onEpc(epc) }
                        }
                    } catch (_: InterruptedException) {
                        break
                    } catch (e: Exception) {
                        if (inventoryRunning) Log.w(TAG, "lectura buffer", e)
                        Thread.sleep(POLL_MS_ERROR)
                    }
                }
            }, "transad-uhf-inventory").also { it.start() }
            true
        } catch (e: Exception) {
            Log.e(TAG, "startContinuousInventory falló", e)
            false
        }
    }

    private fun invokeStartInventoryTag(inst: Any, clazz: Class<*>): Boolean {
        return try {
            val m0 = clazz.methods.find { it.name == "startInventoryTag" && it.parameterCount == 0 }
            if (m0 != null) {
                m0.invoke(inst) as Boolean
            } else {
                val m2 = clazz.getMethod(
                    "startInventoryTag",
                    Byte::class.javaPrimitiveType,
                    Byte::class.javaPrimitiveType
                )
                m2.invoke(inst, 0.toByte(), 0.toByte()) as Boolean
            }
        } catch (e: NoSuchMethodException) {
            Log.e(TAG, "No se encontró startInventoryTag compatible", e)
            false
        }
    }

    fun stopInventory() {
        inventoryRunning = false
        readThread?.interrupt()
        try {
            readThread?.join(JOIN_MS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        readThread = null
        val inst = readerInstance ?: return
        try {
            readerClass?.getMethod("stopInventory")?.invoke(inst)
        } catch (e: Exception) {
            Log.w(TAG, "stopInventory", e)
        }
    }

    fun free() {
        stopInventory()
        val inst = readerInstance
        readerInstance = null
        if (inst != null) {
            try {
                readerClass?.getMethod("free")?.invoke(inst)
            } catch (e: Exception) {
                Log.w(TAG, "free", e)
            }
        }
    }

    companion object {
        private const val TAG = "ChainwayUhfRfid"

        /** Orden: UART (Android reciente) y variantes habituales. */
        private val READER_CLASS_NAMES = listOf(
            "com.rscja.deviceapi.RFIDWithUHFUART",
            "com.rscja.deviceapi.RFIDWithUHFA4",
            "com.rscja.deviceapi.RFIDWithUHF"
        )

        private const val POLL_MS_IDLE = 12L
        private const val POLL_MS_ERROR = 50L
        private const val JOIN_MS = 800L
    }
}

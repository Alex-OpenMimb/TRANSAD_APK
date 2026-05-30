package com.transad.app

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.transad.app.api.ApiClient
import com.transad.app.api.OrderProduct
import com.transad.app.api.OrderResponse
import com.transad.app.api.ProductEntitiesRequest
import com.transad.app.api.ProductEntitiesResponse
import com.transad.app.api.RfidScanPayload
import com.transad.app.databinding.ActivityOrderDetailBinding
import com.transad.app.databinding.BottomSheetRfidReadBinding
import com.transad.app.databinding.DialogRfidScanDetailBinding
import com.transad.app.rfid.ChainwayUhfRfidReader
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicLong

class OrderDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOrderDetailBinding
    private lateinit var adapter: OrderProductsAdapter

    private var orderId: Int = 0
    private var orderUserId: Int = 0
    private var orderProductsList: List<OrderProduct> = emptyList()
    /** Placa vigente para esta orden en la sesión actual (también persistida por orden). */
    private var orderLicensePlate: String = ""
    private lateinit var licensePlateStore: OrderLicensePlateStore
    private val rfidScanRows = mutableListOf<RfidScanRow>()
    private val nextScanRowId = AtomicLong(1L)

    private val rfidBeepHandler = Handler(Looper.getMainLooper())
    private var rfidReadToneGenerator: ToneGenerator? = null

    /** Pitido corto al detectar un chip nuevo (solo lectura por hardware UHF). */
    private fun playRfidReadBeep() {
        try {
            rfidReadToneGenerator?.release()
            val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85)
            rfidReadToneGenerator = tg
            tg.startTone(ToneGenerator.TONE_PROP_ACK, 140)
            rfidBeepHandler.postDelayed({
                rfidReadToneGenerator?.release()
                rfidReadToneGenerator = null
            }, 220)
        } catch (_: Exception) {
            rfidReadToneGenerator = null
        }
    }

    override fun onDestroy() {
        rfidBeepHandler.removeCallbacksAndMessages(null)
        rfidReadToneGenerator?.release()
        rfidReadToneGenerator = null
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        binding = ActivityOrderDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        orderId = intent.getIntExtra(EXTRA_ORDER_ID, 0)
        orderUserId = intent.getIntExtra(EXTRA_ORDER_USER_ID, 0)
        val orderReference = intent.getStringExtra(EXTRA_ORDER_REFERENCE) ?: ""
        title = orderReference

        licensePlateStore = OrderLicensePlateStore(this)
        resolveOrderLicensePlate(intent.getStringExtra(EXTRA_ORDER_LICENSE_PLATE))

        adapter = OrderProductsAdapter()
        binding.recyclerOrderProducts.layoutManager = LinearLayoutManager(this)
        binding.recyclerOrderProducts.adapter = adapter

        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        binding.recyclerOrderProducts.visibility = View.GONE
        binding.tvEmptyProducts.visibility = View.GONE
        binding.fabReadRfid.visibility = View.GONE
        binding.progressProducts.visibility = View.VISIBLE

        refreshOrderFromApi { success ->
            binding.progressProducts.visibility = View.GONE
            if (!success) {
                binding.tvEmptyProducts.visibility = View.VISIBLE
            }
        }
    }

    private fun refreshOrderFromApi(onComplete: (Boolean) -> Unit) {
        if (orderId <= 0) {
            onComplete(false)
            return
        }
        ApiClient.ordersApi.getOrder(orderId).enqueue(object : Callback<OrderResponse> {
            override fun onResponse(call: Call<OrderResponse>, response: Response<OrderResponse>) {
                if (!response.isSuccessful) {
                    ApiErrorUi.showHttpError(
                        this@OrderDetailActivity,
                        getString(R.string.order_detail_load_error),
                        response
                    )
                    onComplete(false)
                    return
                }
                val order = response.body()?.data ?: run {
                    onComplete(false)
                    return
                }
                orderUserId = order.userId
                resolveOrderLicensePlate(order.licensePlateFromCostCenter())
                orderProductsList = order.orderProducts ?: emptyList()
                supportActionBar?.title = order.reference
                if (orderProductsList.isEmpty()) {
                    binding.recyclerOrderProducts.visibility = View.GONE
                    binding.tvEmptyProducts.visibility = View.VISIBLE
                    binding.fabReadRfid.visibility = View.GONE
                } else {
                    binding.tvEmptyProducts.visibility = View.GONE
                    binding.recyclerOrderProducts.visibility = View.VISIBLE
                    binding.fabReadRfid.visibility = View.VISIBLE
                    adapter.submitList(orderProductsList)
                    binding.fabReadRfid.setOnClickListener { ensureOrderLicensePlateThen { showRfidReadBottomSheet() } }
                }
                onComplete(true)
            }

            override fun onFailure(call: Call<OrderResponse>, t: Throwable) {
                ApiErrorUi.showNetworkError(
                    this@OrderDetailActivity,
                    getString(R.string.order_detail_load_error),
                    t
                )
                onComplete(false)
            }
        })
    }

    /**
     * Prioriza placa del centro de costo (API); si no hay, usa la guardada localmente.
     */
    private fun resolveOrderLicensePlate(apiPlate: String?) {
        val fromApi = apiPlate?.trim()?.takeIf { it.isNotEmpty() }?.uppercase()
        if (fromApi != null) {
            orderLicensePlate = fromApi
            licensePlateStore.save(orderId, fromApi)
            return
        }
        if (orderLicensePlate.isBlank()) {
            orderLicensePlate = licensePlateStore.get(orderId).orEmpty()
        }
    }

    /** Pide la placa solo si la orden no trae centro de costo con placa ni hay una guardada. */
    private fun ensureOrderLicensePlateThen(onReady: () -> Unit) {
        if (orderLicensePlate.isNotBlank()) {
            onReady()
            return
        }
        val density = resources.displayMetrics.density
        val pad = (24 * density).toInt()
        val input = android.widget.EditText(this).apply {
            setPadding(pad, pad / 2, pad, pad / 2)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            hint = getString(R.string.rfid_hint_license_plate)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.rfid_license_plate_dialog_title)
            .setMessage(R.string.rfid_license_plate_dialog_message)
            .setView(input)
            .setCancelable(false)
            .setPositiveButton(R.string.rfid_detail_save) { _, _ ->
                val plate = input.text?.toString()?.trim().orEmpty()
                if (plate.isEmpty()) {
                    Toast.makeText(this, R.string.rfid_validation_required_license_plate, Toast.LENGTH_LONG).show()
                    ensureOrderLicensePlateThen(onReady)
                } else {
                    orderLicensePlate = plate.uppercase()
                    licensePlateStore.save(orderId, orderLicensePlate)
                    onReady()
                }
            }
            .setNegativeButton(R.string.rfid_detail_cancel, null)
            .show()
    }

    private fun showRfidScanDetailDialog(
        epc: String,
        licensePlate: String,
        editExisting: RfidScanPayload? = null,
        onLicensePlateChanged: (String) -> Unit,
        onFinished: (RfidScanPayload?) -> Unit
    ) {
        val dialogBinding = DialogRfidScanDetailBinding.inflate(layoutInflater)
        dialogBinding.tvRfidRead.text = epc
        dialogBinding.etLicensePlate.setText(editExisting?.licensePlate ?: licensePlate)
        editExisting?.let { existing ->
            dialogBinding.etTireCode.setText(existing.tireCode)
            dialogBinding.etPosition.setText(existing.position.toString())
            dialogBinding.etObservation.setText(existing.observation.orEmpty())
        }

        val dlg = MaterialAlertDialogBuilder(this)
            .setTitle(if (editExisting != null) R.string.rfid_detail_edit_title else R.string.rfid_detail_title)
            .setView(dialogBinding.root)
            .setCancelable(false)
            .create()

        dlg.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        fun clearFieldErrors() {
            dialogBinding.tilLicensePlate.error = null
            dialogBinding.tilTireCode.error = null
            dialogBinding.tilPosition.error = null
        }

        dialogBinding.btnDetailSave.setOnClickListener {
            clearFieldErrors()
            val plate = dialogBinding.etLicensePlate.text?.toString()?.trim().orEmpty()
            val tire = dialogBinding.etTireCode.text?.toString()?.trim().orEmpty()
            val posText = dialogBinding.etPosition.text?.toString()?.trim().orEmpty()
            var ok = true
            if (plate.isEmpty()) {
                dialogBinding.tilLicensePlate.error = getString(R.string.rfid_validation_required_license_plate)
                ok = false
            }
            if (tire.isEmpty()) {
                dialogBinding.tilTireCode.error = getString(R.string.rfid_validation_required_tire)
                ok = false
            }
            val position = posText.toIntOrNull()
            if (posText.isEmpty() || position == null) {
                dialogBinding.tilPosition.error = getString(R.string.rfid_validation_position_number)
                ok = false
            }
            if (!ok) return@setOnClickListener

            val normalizedPlate = plate.uppercase()
            onLicensePlateChanged(normalizedPlate)

            val payload = RfidScanPayload(
                rfidCode = epc,
                tireCode = tire,
                licensePlate = normalizedPlate,
                position = position!!,
                observation = dialogBinding.etObservation.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            )
            dlg.dismiss()
            onFinished(payload)
        }

        dialogBinding.btnDetailCancel.setOnClickListener {
            dlg.dismiss()
            onFinished(null)
        }

        dlg.show()
    }

    private fun showRfidReadBottomSheet() {
        val dialog = BottomSheetDialog(this)
        val sheetBinding = BottomSheetRfidReadBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)

        val scannedEpcs = rfidScanRows.map { normalizeEpc(it.scan.rfidCode) }.toMutableSet()
        val pendingQueue = ArrayDeque<String>()
        var hardwareReleased = false
        var readerInitedOk = false
        var sessionActive = true
        var detailDialogOpen = false
        /** Pausa breve tras guardar para no releer el mismo chip al instante. */
        var ignoreReadsUntilMs = 0L
        var lastDuplicateToastAt = 0L

        val hardwareReader = ChainwayUhfRfidReader(this)
        lateinit var rfidSession: RfidScanSession
        lateinit var tagsAdapter: RfidTagsAdapter

        fun releaseHardware() {
            if (hardwareReleased) return
            hardwareReleased = true
            hardwareReader.free()
        }

        fun pauseReader() {
            if (!hardwareReleased) hardwareReader.stopInventory()
        }

        fun refreshTagsList() {
            tagsAdapter.submitList(rfidScanRows.toList())
            sheetBinding.tvRfidCount.text = getString(R.string.rfid_sheet_tags_read, rfidScanRows.size)
            sheetBinding.btnSendRfid.visibility = if (rfidScanRows.isNotEmpty()) View.VISIBLE else View.GONE
        }

        fun notifyDuplicateChip() {
            val now = System.currentTimeMillis()
            if (now - lastDuplicateToastAt > 2500L) {
                lastDuplicateToastAt = now
                Toast.makeText(this, R.string.rfid_already_scanned, Toast.LENGTH_SHORT).show()
            }
        }

        fun saveOrUpdateScan(payload: RfidScanPayload, editRowId: Long?) {
            val key = normalizeEpc(payload.rfidCode)
            if (editRowId != null) {
                val idx = rfidScanRows.indexOfFirst { it.stableId == editRowId }
                if (idx >= 0) {
                    rfidScanRows[idx] = rfidScanRows[idx].copy(scan = payload)
                }
            } else {
                val existingIdx = rfidScanRows.indexOfFirst { normalizeEpc(it.scan.rfidCode) == key }
                if (existingIdx >= 0) {
                    rfidScanRows[existingIdx] = rfidScanRows[existingIdx].copy(scan = payload)
                } else {
                    rfidScanRows.add(RfidScanRow(stableId = nextScanRowId.getAndIncrement(), scan = payload))
                }
            }
            scannedEpcs.add(key)
            pendingQueue.removeAll { normalizeEpc(it) == key }
            ignoreReadsUntilMs = System.currentTimeMillis() + SCAN_COOLDOWN_MS
            refreshTagsList()
        }

        fun openScanDialog(epc: String, editRow: RfidScanRow?) {
            pauseReader()
            detailDialogOpen = true
            showRfidScanDetailDialog(
                epc = epc,
                licensePlate = orderLicensePlate,
                editExisting = editRow?.scan,
                onLicensePlateChanged = { plate ->
                    orderLicensePlate = plate
                    licensePlateStore.save(orderId, plate)
                }
            ) { payload ->
                detailDialogOpen = false
                if (sessionActive && payload != null) {
                    saveOrUpdateScan(payload, editRow?.stableId)
                }
                if (sessionActive) rfidSession.tryConsumeQueue()
            }
        }

        tagsAdapter = RfidTagsAdapter { row ->
            openScanDialog(row.scan.rfidCode, row)
        }
        sheetBinding.recyclerRfidTags.layoutManager = LinearLayoutManager(this)
        sheetBinding.recyclerRfidTags.adapter = tagsAdapter

        rfidSession = object : RfidScanSession {
            override fun tryConsumeQueue() {
                if (detailDialogOpen || !sessionActive) return
                while (pendingQueue.isNotEmpty()) {
                    val next = pendingQueue.first()
                    if (scannedEpcs.contains(normalizeEpc(next))) {
                        pendingQueue.removeFirst()
                        continue
                    }
                    break
                }
                val epc = pendingQueue.pollFirst() ?: run {
                    resumeReader()
                    return
                }
                if (scannedEpcs.contains(normalizeEpc(epc))) {
                    tryConsumeQueue()
                    return
                }
                openScanDialog(epc, editRow = null)
            }

            override fun resumeReader() {
                if (hardwareReleased || !sessionActive || !readerInitedOk || detailDialogOpen) return
                val started = hardwareReader.startContinuousInventory { epc ->
                    runOnUiThread { onHardwareEpc(epc) }
                }
                if (!started) {
                    readerInitedOk = false
                    releaseHardware()
                    sheetBinding.tvRfidStatus.text = getString(R.string.rfid_hw_init_failed)
                    Toast.makeText(this@OrderDetailActivity, R.string.rfid_hw_init_failed, Toast.LENGTH_LONG).show()
                }
            }

            private fun onHardwareEpc(epc: String) {
                if (epc.isBlank() || !sessionActive || detailDialogOpen) return
                if (System.currentTimeMillis() < ignoreReadsUntilMs) return
                val key = normalizeEpc(epc)
                if (scannedEpcs.contains(key)) {
                    notifyDuplicateChip()
                    return
                }
                if (pendingQueue.any { normalizeEpc(it) == key }) return
                pauseReader()
                pendingQueue.addLast(epc)
                playRfidReadBeep()
                tryConsumeQueue()
            }

            override fun enqueueEpc(epc: String) {
                if (epc.isBlank() || !sessionActive) return
                val key = normalizeEpc(epc)
                if (scannedEpcs.contains(key)) {
                    notifyDuplicateChip()
                    return
                }
                if (pendingQueue.any { normalizeEpc(it) == key }) return
                pendingQueue.addLast(epc)
                tryConsumeQueue()
            }
        }

        dialog.setOnDismissListener {
            sessionActive = false
            pendingQueue.clear()
            releaseHardware()
        }

        if (hardwareReader.isDeviceApiPresent) {
            readerInitedOk = hardwareReader.init()
            if (readerInitedOk) {
                rfidSession.resumeReader()
                if (readerInitedOk) {
                    sheetBinding.tvRfidStatus.text = getString(R.string.rfid_hw_reading)
                }
            } else {
                releaseHardware()
                sheetBinding.tvRfidStatus.text = getString(R.string.rfid_hw_init_failed)
                Toast.makeText(this, R.string.rfid_hw_init_failed, Toast.LENGTH_LONG).show()
            }
        } else {
            sheetBinding.tvRfidStatus.text = getString(R.string.rfid_hw_missing_sdk)
            Toast.makeText(this, R.string.rfid_hw_missing_sdk, Toast.LENGTH_LONG).show()
        }

        sheetBinding.btnAddTag.setOnClickListener {
            val tag = sheetBinding.etRfidTag.text?.toString()?.trim()
            if (!tag.isNullOrEmpty()) {
                sheetBinding.etRfidTag.text?.clear()
                rfidSession.enqueueEpc(tag)
            }
        }

        sheetBinding.btnSendRfid.setOnClickListener {
            if (rfidScanRows.isEmpty()) return@setOnClickListener
            if (orderUserId <= 0) {
                Toast.makeText(this, R.string.product_entity_error_user, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            pauseReader()
            sheetBinding.btnSendRfid.isEnabled = false
            val itemsResult = buildProductEntityItemsForScans(
                scans = rfidScanRows.map { it.scan },
                orderProducts = orderProductsList,
                userId = orderUserId
            )
            itemsResult.fold(
                onSuccess = { items ->
                    ApiClient.ordersApi.createProductEntities(ProductEntitiesRequest(items))
                        .enqueue(object : Callback<ProductEntitiesResponse> {
                            override fun onResponse(
                                call: Call<ProductEntitiesResponse>,
                                response: Response<ProductEntitiesResponse>
                            ) {
                                if (response.isSuccessful) {
                                    refreshOrderFromApi { refreshed ->
                                        sheetBinding.btnSendRfid.isEnabled = true
                                        Toast.makeText(this@OrderDetailActivity, R.string.rfid_send_ok, Toast.LENGTH_SHORT).show()
                                        if (!refreshed) {
                                            ApiErrorUi.show(
                                                this@OrderDetailActivity,
                                                getString(R.string.order_refresh_error)
                                            )
                                        }
                                        rfidScanRows.clear()
                                        refreshTagsList()
                                        dialog.dismiss()
                                    }
                                } else {
                                    sheetBinding.btnSendRfid.isEnabled = true
                                    ApiErrorUi.showHttpError(
                                        this@OrderDetailActivity,
                                        getString(R.string.rfid_send_error),
                                        response
                                    )
                                }
                            }

                            override fun onFailure(call: Call<ProductEntitiesResponse>, t: Throwable) {
                                sheetBinding.btnSendRfid.isEnabled = true
                                ApiErrorUi.showNetworkError(
                                    this@OrderDetailActivity,
                                    getString(R.string.rfid_send_error),
                                    t
                                )
                            }
                        })
                },
                onFailure = { e ->
                    sheetBinding.btnSendRfid.isEnabled = true
                    val msg = when (e.message) {
                        "NO_TAG_LINE" -> getString(R.string.product_entity_error_no_tag)
                        "NO_TIRE_LINE" -> getString(R.string.product_entity_error_no_tire)
                        "NO_TAG_LEFT" -> getString(R.string.product_entity_error_no_tag_left)
                        "NO_TIRE_LEFT" -> getString(R.string.product_entity_error_no_tire_left)
                        "INVALID_USER_ID" -> getString(R.string.product_entity_error_user)
                        "NO_LICENSE_PLATE" -> getString(R.string.rfid_validation_required_license_plate)
                        else -> e.message?.ifEmpty { null } ?: getString(R.string.rfid_send_error)
                    }
                    ApiErrorUi.show(this@OrderDetailActivity, msg)
                }
            )
        }

        sheetBinding.btnCloseRfid.setOnClickListener {
            sessionActive = false
            pendingQueue.clear()
            releaseHardware()
            dialog.dismiss()
        }

        refreshTagsList()
        dialog.show()
    }

    companion object {
        const val EXTRA_ORDER_ID = "order_id"
        const val EXTRA_ORDER_USER_ID = "order_user_id"
        const val EXTRA_ORDER_REFERENCE = "order_reference"
        const val EXTRA_ORDER_LICENSE_PLATE = "order_license_plate"
        const val EXTRA_ORDER_PRODUCTS = "order_products"

        private const val SCAN_COOLDOWN_MS = 1500L

        private fun normalizeEpc(epc: String) = epc.trim().uppercase()
    }

    private interface RfidScanSession {
        fun tryConsumeQueue()
        fun resumeReader()
        fun enqueueEpc(epc: String)
    }
}

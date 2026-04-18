package com.transad.app

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.transad.app.api.ApiClient
import com.transad.app.api.OrderProduct
import com.transad.app.api.RfidScanPayload
import com.transad.app.api.RfidScansRequest
import com.transad.app.api.RfidScansResponse
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
    private val rfidScanRows = mutableListOf<RfidScanRow>()
    private val nextScanRowId = AtomicLong(1L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOrderDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        orderId = intent.getIntExtra(EXTRA_ORDER_ID, 0)
        val orderReference = intent.getStringExtra(EXTRA_ORDER_REFERENCE) ?: ""
        title = orderReference

        val products = intent.getParcelableArrayListExtra<OrderProduct>(EXTRA_ORDER_PRODUCTS) ?: arrayListOf()

        adapter = OrderProductsAdapter()
        binding.recyclerOrderProducts.layoutManager = LinearLayoutManager(this)
        binding.recyclerOrderProducts.adapter = adapter

        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        if (products.isEmpty()) {
            binding.recyclerOrderProducts.visibility = View.GONE
            binding.tvEmptyProducts.visibility = View.VISIBLE
            binding.fabReadRfid.visibility = View.GONE
        } else {
            binding.tvEmptyProducts.visibility = View.GONE
            binding.recyclerOrderProducts.visibility = View.VISIBLE
            binding.fabReadRfid.visibility = View.VISIBLE
            adapter.submitList(products)

            binding.fabReadRfid.setOnClickListener { showRfidReadBottomSheet() }
        }
    }

    private fun showRfidScanDetailDialog(epc: String, onFinished: (RfidScanPayload?) -> Unit) {
        val dialogBinding = DialogRfidScanDetailBinding.inflate(layoutInflater)
        dialogBinding.tvRfidRead.text = epc

        val dlg = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.rfid_detail_title)
            .setView(dialogBinding.root)
            .setCancelable(false)
            .create()

        dlg.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        fun clearFieldErrors() {
            dialogBinding.tilTireCode.error = null
            dialogBinding.tilPosition.error = null
        }

        dialogBinding.btnDetailSave.setOnClickListener {
            clearFieldErrors()
            val tire = dialogBinding.etTireCode.text?.toString()?.trim().orEmpty()
            val pos = dialogBinding.etPosition.text?.toString()?.trim().orEmpty()
            var ok = true
            if (tire.isEmpty()) {
                dialogBinding.tilTireCode.error = getString(R.string.rfid_validation_required_tire)
                ok = false
            }
            if (pos.isEmpty()) {
                dialogBinding.tilPosition.error = getString(R.string.rfid_validation_required_position)
                ok = false
            }
            if (!ok) return@setOnClickListener

            val payload = RfidScanPayload(
                rfidCode = epc,
                tireCode = tire,
                position = pos,
                observation = dialogBinding.etObservation.text?.toString()?.trim()?.takeIf { it.isNotEmpty() },
                tireDepth = dialogBinding.etTireDepth.text?.toString()?.trim()?.takeIf { it.isNotEmpty() },
                tireThickness = dialogBinding.etTireThickness.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
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

        val tagsAdapter = RfidTagsAdapter()
        sheetBinding.recyclerRfidTags.layoutManager = LinearLayoutManager(this)
        sheetBinding.recyclerRfidTags.adapter = tagsAdapter

        val hardwareReader = ChainwayUhfRfidReader(this)
        var hardwareReleased = false
        var readerInitedOk = false
        /** True mientras la sesión de lectura sigue viva (antes de [BottomSheetDialog.show] el sheet aún no está "showing"). */
        var sessionActive = true
        var detailDialogOpen = false

        val pendingQueue = ArrayDeque<String>()

        fun releaseHardware() {
            if (hardwareReleased) return
            hardwareReleased = true
            hardwareReader.free()
        }

        fun refreshTagsList() {
            tagsAdapter.submitList(rfidScanRows.toList())
            sheetBinding.tvRfidCount.text = getString(R.string.rfid_sheet_tags_read, rfidScanRows.size)
            sheetBinding.btnSendRfid.visibility = if (rfidScanRows.isNotEmpty()) View.VISIBLE else View.GONE
        }

        val rfidSession = object {
            fun pauseReader() {
                if (!hardwareReleased) hardwareReader.stopInventory()
            }

            fun tryConsumeQueue() {
                if (detailDialogOpen || !sessionActive) return
                val epc = pendingQueue.pollFirst() ?: run {
                    resumeReader()
                    return
                }
                pauseReader()
                detailDialogOpen = true
                showRfidScanDetailDialog(epc) { payload ->
                    detailDialogOpen = false
                    if (sessionActive) {
                        if (payload != null) {
                            rfidScanRows.add(RfidScanRow(stableId = nextScanRowId.getAndIncrement(), scan = payload))
                        }
                        refreshTagsList()
                        tryConsumeQueue()
                    }
                }
            }

            fun resumeReader() {
                if (hardwareReleased || !sessionActive || !readerInitedOk) return
                val started = hardwareReader.startContinuousInventory { epc ->
                    runOnUiThread {
                        if (epc.isBlank() || !sessionActive) return@runOnUiThread
                        if (pendingQueue.contains(epc)) return@runOnUiThread
                        pendingQueue.addLast(epc)
                        tryConsumeQueue()
                    }
                }
                if (!started) {
                    readerInitedOk = false
                    releaseHardware()
                    sheetBinding.tvRfidStatus.text = getString(R.string.rfid_hw_init_failed)
                    Toast.makeText(this@OrderDetailActivity, R.string.rfid_hw_init_failed, Toast.LENGTH_LONG).show()
                }
            }

            fun enqueueEpc(epc: String) {
                if (epc.isBlank() || !sessionActive) return
                if (pendingQueue.contains(epc)) return
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
            sheetBinding.btnSendRfid.isEnabled = false
            val body = RfidScansRequest(scans = rfidScanRows.map { it.scan })
            ApiClient.ordersApi.submitScans(orderId, body)
                .enqueue(object : Callback<RfidScansResponse> {
                    override fun onResponse(
                        call: Call<RfidScansResponse>,
                        response: Response<RfidScansResponse>
                    ) {
                        sheetBinding.btnSendRfid.isEnabled = true
                        if (response.isSuccessful) {
                            Toast.makeText(this@OrderDetailActivity, R.string.rfid_send_ok, Toast.LENGTH_SHORT).show()
                            rfidScanRows.clear()
                            refreshTagsList()
                            dialog.dismiss()
                        } else {
                            Toast.makeText(this@OrderDetailActivity, R.string.rfid_send_error, Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onFailure(call: Call<RfidScansResponse>, t: Throwable) {
                        sheetBinding.btnSendRfid.isEnabled = true
                        Toast.makeText(
                            this@OrderDetailActivity,
                            getString(R.string.rfid_send_error) + " " + t.message,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                })
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
        const val EXTRA_ORDER_REFERENCE = "order_reference"
        const val EXTRA_ORDER_PRODUCTS = "order_products"
    }
}

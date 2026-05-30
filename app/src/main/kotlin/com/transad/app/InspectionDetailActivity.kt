package com.transad.app

import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Menu
import android.view.MenuItem
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.transad.app.api.ApiClient
import com.transad.app.api.CostCenter
import com.transad.app.api.CostCenterDetail
import com.transad.app.api.CostCenterDetailResponse
import com.transad.app.api.InspectionRequest
import com.transad.app.api.InspectionResponse
import com.transad.app.api.MeResponse
import com.transad.app.api.ProductAttribute
import com.transad.app.databinding.ActivityInspectionDetailBinding
import com.transad.app.databinding.BottomSheetInspectionScanBinding
import com.transad.app.databinding.DialogInspectionRegisterBinding
import com.transad.app.databinding.ItemHomeStatCardBinding
import com.transad.app.rfid.ChainwayUhfRfidReader
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class InspectionDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityInspectionDetailBinding
    private lateinit var positionsAdapter: MountedPositionsAdapter
    private var costCenterId: Int = 0
    private var currentDetail: CostCenterDetail? = null

    private val rfidBeepHandler = Handler(Looper.getMainLooper())
    private var rfidReadToneGenerator: ToneGenerator? = null
    private var scanBottomSheet: BottomSheetDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        binding = ActivityInspectionDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        costCenterId = intent.getIntExtra(EXTRA_COST_CENTER_ID, 0)
        if (costCenterId <= 0) {
            readCostCenterExtra(intent)?.id?.let { costCenterId = it }
        }
        if (costCenterId <= 0) {
            finish()
            return
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        readCostCenterExtra(intent)?.licensePlate?.trim()?.takeIf { it.isNotEmpty() }?.let {
            title = it.uppercase()
        }

        positionsAdapter = MountedPositionsAdapter()
        binding.recyclerMountedPositions.layoutManager = LinearLayoutManager(this)
        binding.recyclerMountedPositions.adapter = positionsAdapter

        binding.fabScanInspection.setOnClickListener { showInspectionScanBottomSheet() }

        loadDetail()
    }

    override fun onDestroy() {
        scanBottomSheet?.dismiss()
        scanBottomSheet = null
        rfidReadToneGenerator?.release()
        rfidReadToneGenerator = null
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_inspection_detail, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_refresh_inspection_detail) {
            loadDetail(showRefreshAck = true)
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun loadDetail(showRefreshAck: Boolean = false) {
        binding.progressDetail.isVisible = true
        binding.scrollDetailContent.isVisible = false
        binding.tvEmptyDetail.isVisible = false

        ApiClient.costCentersApi.getCostCenter(costCenterId).enqueue(object : Callback<CostCenterDetailResponse> {
            override fun onResponse(call: Call<CostCenterDetailResponse>, response: Response<CostCenterDetailResponse>) {
                binding.progressDetail.isVisible = false
                if (response.isSuccessful) {
                    val detail = response.body()?.data ?: run {
                        currentDetail = null
                        showLoadError()
                        return
                    }
                    currentDetail = detail
                    bindDetail(detail)
                    binding.scrollDetailContent.isVisible = true
                    if (showRefreshAck) {
                        Toast.makeText(
                            this@InspectionDetailActivity,
                            R.string.inspection_detail_refreshed,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    currentDetail = null
                    showLoadError()
                    ApiErrorUi.showHttpError(
                        this@InspectionDetailActivity,
                        getString(R.string.inspection_detail_error),
                        response
                    )
                }
            }

            override fun onFailure(call: Call<CostCenterDetailResponse>, t: Throwable) {
                binding.progressDetail.isVisible = false
                currentDetail = null
                showLoadError()
                ApiErrorUi.showNetworkError(
                    this@InspectionDetailActivity,
                    getString(R.string.inspection_detail_error),
                    t
                )
            }
        })
    }

    private fun showLoadError() {
        binding.scrollDetailContent.isVisible = false
        binding.tvEmptyDetail.isVisible = true
    }

    private fun bindDetail(detail: CostCenterDetail) {
        val plate = detail.licensePlate?.trim().orEmpty().ifBlank { "—" }
        title = plate
        binding.tvLicensePlate.text = plate
        binding.tvReference.text = getString(
            R.string.inspection_detail_reference_label,
            detail.reference?.trim().orEmpty().ifBlank { "—" }
        )
        binding.tvDescription.text = detail.description?.trim().orEmpty().ifBlank { "—" }

        binding.chipStatus.text = if (detail.status) {
            getString(R.string.order_status_active)
        } else {
            getString(R.string.order_status_inactive)
        }
        binding.chipStatus.setChipBackgroundColorResource(
            if (detail.status) R.color.status_active else R.color.status_inactive
        )

        detail.entryDate?.trim()?.takeIf { it.isNotEmpty() }?.let {
            binding.tvEntryDate.isVisible = true
            binding.tvEntryDate.text = getString(R.string.inspection_detail_entry_date, it)
        } ?: run { binding.tvEntryDate.isVisible = false }

        detail.observations?.trim()?.takeIf { it.isNotEmpty() }?.let {
            binding.tvObservations.isVisible = true
            binding.tvObservations.text = getString(R.string.inspection_detail_observations, it)
        } ?: run { binding.tvObservations.isVisible = false }

        bindVehicle(detail)
        bindConfiguration(detail)
        bindSummary(detail.summary)

        val positions = detail.mountedPositions.sortedBy { it.position }
        positionsAdapter.submitList(positions)
    }

    private fun bindVehicle(detail: CostCenterDetail) {
        val vehicle = detail.vehicle
        if (vehicle == null) {
            binding.cardVehicle.isVisible = false
            return
        }
        binding.cardVehicle.isVisible = true
        val lines = buildList {
            vehicle.brand?.trim()?.takeIf { it.isNotEmpty() }?.let {
                add(getString(R.string.inspection_detail_vehicle_brand, it))
            }
            vehicle.model?.trim()?.takeIf { it.isNotEmpty() }?.let {
                add(getString(R.string.inspection_detail_vehicle_model, it))
            }
            vehicle.serial?.trim()?.takeIf { it.isNotEmpty() }?.let {
                add(getString(R.string.inspection_detail_vehicle_serial, it))
            }
            vehicle.vin?.trim()?.takeIf { it.isNotEmpty() }?.let {
                add(getString(R.string.inspection_detail_vehicle_vin, it))
            }
            vehicle.capacity?.trim()?.takeIf { it.isNotEmpty() }?.let {
                add(getString(R.string.inspection_detail_vehicle_capacity, it))
            }
            vehicle.bodyType?.trim()?.takeIf { it.isNotEmpty() }?.let {
                add(getString(R.string.inspection_detail_vehicle_body, it))
            }
            if (vehicle.kmCount > 0) {
                add(getString(R.string.inspection_detail_vehicle_km, vehicle.kmCount))
            }
        }
        binding.tvVehicleInfo.text = lines.joinToString("\n").ifBlank { "—" }
    }

    private fun bindConfiguration(detail: CostCenterDetail) {
        val config = detail.configuration
        if (config == null) {
            binding.cardConfiguration.isVisible = false
            return
        }
        binding.cardConfiguration.isVisible = true
        val lines = buildList {
            config.name?.trim()?.takeIf { it.isNotEmpty() }?.let {
                add(getString(R.string.inspection_detail_config_name, it))
            }
            add(getString(R.string.inspection_detail_config_spare, config.spareTires))
        }
        binding.tvConfigurationInfo.text = lines.joinToString("\n")
    }

    private fun bindSummary(summary: com.transad.app.api.CostCenterPositionSummary?) {
        if (summary == null) {
            binding.statTotal.root.isVisible = false
            binding.statOccupied.root.isVisible = false
            binding.statEmpty.root.isVisible = false
            binding.statSpare.root.isVisible = false
            return
        }
        bindStatTile(binding.statTotal, getString(R.string.inspection_detail_stat_total), summary.totalPositions)
        bindStatTile(binding.statOccupied, getString(R.string.inspection_detail_stat_occupied), summary.occupiedPositions)
        bindStatTile(binding.statEmpty, getString(R.string.inspection_detail_stat_empty), summary.emptyPositions)
        bindStatTile(binding.statSpare, getString(R.string.inspection_detail_stat_spare), summary.spareSlots)
    }

    private fun bindStatTile(tile: ItemHomeStatCardBinding, title: String, value: Int) {
        tile.root.isVisible = true
        tile.tvStatTitle.text = title
        tile.tvStatValue.text = value.toString()
        tile.tvStatDetail.isVisible = false
    }

    private fun showInspectionScanBottomSheet() {
        val detail = currentDetail
        if (detail == null) {
            Toast.makeText(this, R.string.inspection_scan_detail_required, Toast.LENGTH_LONG).show()
            return
        }

        val dialog = BottomSheetDialog(this)
        val sheetBinding = BottomSheetInspectionScanBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)
        scanBottomSheet = dialog

        var hardwareReleased = false
        var readerInitedOk = false
        var sessionActive = true
        var processingLabel = false
        var ignoreReadsUntilMs = 0L

        val hardwareReader = ChainwayUhfRfidReader(this)

        fun releaseHardware() {
            if (hardwareReleased) return
            hardwareReleased = true
            hardwareReader.free()
        }

        fun pauseReader() {
            if (!hardwareReleased) hardwareReader.stopInventory()
        }

        fun processLabelCode(labelCode: String) {
            if (labelCode.isBlank() || processingLabel) return
            processingLabel = true
            pauseReader()
            sheetBinding.tvLastLabel.text = labelCode.trim()
            dialog.dismiss()
            handleScannedLabel(detail, labelCode.trim()) {
                processingLabel = false
            }
        }

        fun onHardwareEpc(epc: String) {
            if (epc.isBlank() || !sessionActive || processingLabel) return
            if (System.currentTimeMillis() < ignoreReadsUntilMs) return
            playRfidReadBeep()
            ignoreReadsUntilMs = System.currentTimeMillis() + SCAN_COOLDOWN_MS
            processLabelCode(epc)
        }

        fun resumeReader() {
            if (hardwareReleased || !sessionActive || !readerInitedOk || processingLabel) return
            val started = hardwareReader.startContinuousInventory { epc ->
                runOnUiThread { onHardwareEpc(epc) }
            }
            if (!started) {
                readerInitedOk = false
                releaseHardware()
                sheetBinding.tvScanStatus.text = getString(R.string.rfid_hw_init_failed)
            }
        }

        dialog.setOnDismissListener {
            sessionActive = false
            releaseHardware()
            scanBottomSheet = null
        }

        if (hardwareReader.isDeviceApiPresent) {
            readerInitedOk = hardwareReader.init()
            if (readerInitedOk) {
                resumeReader()
                sheetBinding.tvScanStatus.text = getString(R.string.rfid_hw_reading)
            } else {
                releaseHardware()
                sheetBinding.tvScanStatus.text = getString(R.string.rfid_hw_init_failed)
                Toast.makeText(this, R.string.rfid_hw_init_failed, Toast.LENGTH_LONG).show()
            }
        } else {
            sheetBinding.tvScanStatus.text = getString(R.string.rfid_hw_missing_sdk)
            Toast.makeText(this, R.string.rfid_hw_missing_sdk, Toast.LENGTH_LONG).show()
        }

        sheetBinding.btnAddManualLabel.setOnClickListener {
            val code = sheetBinding.etManualLabel.text?.toString()?.trim().orEmpty()
            if (code.isNotEmpty()) {
                sheetBinding.etManualLabel.text?.clear()
                processLabelCode(code)
            }
        }

        sheetBinding.btnCloseScan.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    private fun handleScannedLabel(detail: CostCenterDetail, labelCode: String, onFinished: () -> Unit) {
        val match = InspectionTagLookup.findLabelInVehicle(detail, labelCode)
        val blockingInspection = InspectionTagLookup.findBlockingInspectionToday(
            detail = detail,
            labelCode = labelCode,
            matchedTire = match?.tire
        )
        if (blockingInspection != null) {
            showAlreadyInspectedTodayDialog(labelCode, onFinished)
            return
        }

        if (match != null) {
            showInspectionRegisterDialog(
                labelCode = labelCode,
                match = match,
                attributes = InspectionTagLookup.inspectionAttributes(match.tire),
                onFinished = onFinished
            )
        } else {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.inspection_label_not_found_title)
                .setMessage(getString(R.string.inspection_label_not_found_message, labelCode))
                .setPositiveButton(R.string.inspection_label_not_found_register) { _, _ ->
                    showInspectionRegisterDialog(
                        labelCode = labelCode,
                        match = null,
                        attributes = emptyList(),
                        onFinished = onFinished
                    )
                }
                .setNegativeButton(R.string.rfid_detail_cancel) { _, _ -> onFinished() }
                .setOnCancelListener { onFinished() }
                .show()
        }
    }

    private fun showAlreadyInspectedTodayDialog(labelCode: String, onFinished: () -> Unit) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.inspection_already_today_title)
            .setMessage(getString(R.string.inspection_already_today_message, labelCode))
            .setPositiveButton(android.R.string.ok) { _, _ -> onFinished() }
            .setOnCancelListener { onFinished() }
            .show()
    }

    private fun showInspectionRegisterDialog(
        labelCode: String,
        match: InspectionLabelMatch?,
        attributes: List<ProductAttribute>,
        onFinished: () -> Unit
    ) {
        val dialogBinding = DialogInspectionRegisterBinding.inflate(layoutInflater)
        val dlg = MaterialAlertDialogBuilder(this)
            .setView(dialogBinding.root)
            .setCancelable(true)
            .create()

        dlg.setOnCancelListener { onFinished() }

        if (match != null) {
            dialogBinding.tvRegisterTitle.setText(R.string.inspection_label_found_title)
            val side = match.position.side?.trim().orEmpty().ifBlank { "—" }
            val tireName = match.tire.name?.trim().orEmpty().ifBlank { "—" }
            dialogBinding.tvRegisterMessage.text = getString(
                R.string.inspection_label_found_message,
                match.position.position,
                match.position.axisNumber,
                side,
                tireName
            )
            dialogBinding.tvRegisterMessage.isVisible = true
        } else {
            dialogBinding.tvRegisterTitle.setText(R.string.inspection_register_unknown_title)
            dialogBinding.tvRegisterMessage.text = getString(R.string.inspection_register_unknown_message)
            dialogBinding.tvRegisterMessage.isVisible = true
        }

        dialogBinding.tvLabelCode.text = labelCode

        val attributeFields = mutableListOf<Pair<ProductAttribute, TextInputEditText>>()
        if (attributes.isNotEmpty()) {
            dialogBinding.tvAttributesHeader.isVisible = true
            attributes.forEach { attr ->
                val inputLayout = TextInputLayout(
                    this,
                    null,
                    com.google.android.material.R.style.Widget_MaterialComponents_TextInputLayout_OutlinedBox
                ).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dpToPx(8) }
                    hint = attr.name?.trim().orEmpty().ifBlank { getString(R.string.inspection_register_attributes) }
                }
                val editText = TextInputEditText(inputLayout.context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    inputType = when (attr.type?.lowercase()) {
                        "number" -> InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                        else -> InputType.TYPE_CLASS_TEXT
                    }
                    attr.value?.trim()?.takeIf { it.isNotEmpty() }?.let { setText(it) }
                }
                inputLayout.addView(editText)
                dialogBinding.containerAttributes.addView(inputLayout)
                attributeFields += attr to editText
            }
        } else {
            dialogBinding.tvAttributesHeader.isVisible = false
        }

        dialogBinding.btnRegisterCancel.setOnClickListener {
            dlg.dismiss()
            onFinished()
        }

        dialogBinding.btnRegisterSave.setOnClickListener {
            val attributeMap = linkedMapOf<String, String>()
            for ((attr, editText) in attributeFields) {
                val value = editText.text?.toString()?.trim().orEmpty()
                if (value.isEmpty()) {
                    val label = attr.name?.trim().orEmpty().ifBlank { "?" }
                    Toast.makeText(
                        this,
                        getString(R.string.inspection_register_attribute_required, label),
                        Toast.LENGTH_LONG
                    ).show()
                    return@setOnClickListener
                }
                attributeMap[attr.attributeId.toString()] = value
            }

            val observations = dialogBinding.etObservations.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            dialogBinding.btnRegisterSave.isEnabled = false
            dialogBinding.btnRegisterCancel.isEnabled = false

            submitInspection(
                labelCode = labelCode,
                attributes = attributeMap,
                observations = observations,
                onComplete = { success ->
                    dialogBinding.btnRegisterSave.isEnabled = true
                    dialogBinding.btnRegisterCancel.isEnabled = true
                    if (success) {
                        dlg.dismiss()
                        onFinished()
                    }
                }
            )
        }

        dlg.show()
    }

    private fun submitInspection(
        labelCode: String,
        attributes: Map<String, String>,
        observations: String?,
        onComplete: (Boolean) -> Unit
    ) {
        val session = SessionManager(this)
        val cachedUserId = session.getUserId()
        if (cachedUserId > 0) {
            postInspection(cachedUserId, labelCode, attributes, observations, onComplete)
            return
        }

        ApiClient.authApi.getMe().enqueue(object : Callback<MeResponse> {
            override fun onResponse(call: Call<MeResponse>, response: Response<MeResponse>) {
                val userId = response.body()?.resolveUser()?.id ?: 0
                if (response.isSuccessful && userId > 0) {
                    session.saveUserId(userId)
                    postInspection(userId, labelCode, attributes, observations, onComplete)
                } else {
                    Toast.makeText(
                        this@InspectionDetailActivity,
                        R.string.product_entity_error_user,
                        Toast.LENGTH_LONG
                    ).show()
                    onComplete(false)
                }
            }

            override fun onFailure(call: Call<MeResponse>, t: Throwable) {
                ApiErrorUi.showNetworkError(
                    this@InspectionDetailActivity,
                    getString(R.string.inspection_register_error),
                    t
                )
                onComplete(false)
            }
        })
    }

    private fun postInspection(
        userId: Int,
        labelCode: String,
        attributes: Map<String, String>,
        observations: String?,
        onComplete: (Boolean) -> Unit
    ) {
        val request = InspectionRequest(
            labelCode = labelCode,
            costCenterId = costCenterId,
            userId = userId,
            attributes = attributes,
            observations = observations
        )

        ApiClient.inspectionsApi.createInspection(request).enqueue(object : Callback<InspectionResponse> {
            override fun onResponse(call: Call<InspectionResponse>, response: Response<InspectionResponse>) {
                if (response.isSuccessful) {
                    Toast.makeText(
                        this@InspectionDetailActivity,
                        R.string.inspection_register_success,
                        Toast.LENGTH_SHORT
                    ).show()
                    onComplete(true)
                } else {
                    ApiErrorUi.showHttpError(
                        this@InspectionDetailActivity,
                        getString(R.string.inspection_register_error),
                        response
                    )
                    onComplete(false)
                }
            }

            override fun onFailure(call: Call<InspectionResponse>, t: Throwable) {
                ApiErrorUi.showNetworkError(
                    this@InspectionDetailActivity,
                    getString(R.string.inspection_register_error),
                    t
                )
                onComplete(false)
            }
        })
    }

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
        }
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_COST_CENTER_ID = "cost_center_id"
        const val EXTRA_COST_CENTER = "cost_center"

        private const val SCAN_COOLDOWN_MS = 1500L

        private fun readCostCenterExtra(intent: Intent): CostCenter? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(EXTRA_COST_CENTER, CostCenter::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(EXTRA_COST_CENTER)
            }
    }
}

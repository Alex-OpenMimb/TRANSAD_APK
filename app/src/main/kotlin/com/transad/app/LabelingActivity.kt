package com.transad.app

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.View
import android.view.WindowManager
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
import com.transad.app.api.LabelingLabelRequest
import com.transad.app.api.LabelingPairRequest
import com.transad.app.api.LabelingRequest
import com.transad.app.api.LabelingResponse
import com.transad.app.api.LabelingTireRequest
import com.transad.app.api.MeResponse
import com.transad.app.api.Product
import com.transad.app.api.ProductAttribute
import com.transad.app.api.ProductsResponse
import android.view.inputmethod.EditorInfo
import com.transad.app.api.FilterProductType
import com.transad.app.api.ProductsFilterState
import com.transad.app.api.filterTypeIdByName
import com.transad.app.api.parseFilterJsonList
import com.transad.app.databinding.ActivityLabelingBinding
import com.transad.app.databinding.BottomSheetRfidReadBinding
import com.transad.app.databinding.DialogLabelingDetailBinding
import com.transad.app.rfid.ChainwayUhfRfidReader
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicLong

class LabelingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLabelingBinding
    private lateinit var scansAdapter: LabelingScansAdapter
    private lateinit var productPickerAdapter: LabelingProductPickerAdapter

    private var allProducts: List<Product> = emptyList()
    private var productTypes: List<FilterProductType> = emptyList()
    private var tireTypeId: Int? = null
    private var labelTypeId: Int? = null
    private var panelFilters = ProductsFilterState()
    private var selectedTire: Product? = null
    private var selectedLabel: Product? = null
    private var labelingSelection: LabelingSelection? = null
    private var pickerTab: ProductPickerTab = ProductPickerTab.TIRE

    private val scanRows = mutableListOf<LabelingScanRow>()
    private val nextScanRowId = AtomicLong(1L)

    private val rfidBeepHandler = Handler(Looper.getMainLooper())
    private var rfidReadToneGenerator: ToneGenerator? = null

    private enum class ProductPickerTab { TIRE, LABEL }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        binding = ActivityLabelingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        productPickerAdapter = LabelingProductPickerAdapter { product ->
            when (pickerTab) {
                ProductPickerTab.TIRE -> selectedTire = product
                ProductPickerTab.LABEL -> selectedLabel = product
            }
            updateSelectionSummary()
            refreshProductList()
        }
        binding.recyclerProducts.layoutManager = LinearLayoutManager(this)
        binding.recyclerProducts.adapter = productPickerAdapter

        scansAdapter = LabelingScansAdapter { row ->
            showLabelingDetailDialog(row.scan.labelCode, editExisting = row)
        }
        binding.recyclerLabelingScans.layoutManager = LinearLayoutManager(this)
        binding.recyclerLabelingScans.adapter = scansAdapter

        binding.toggleProductType.check(binding.btnTabTire.id)
        binding.toggleProductType.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            pickerTab = if (checkedId == binding.btnTabTire.id) ProductPickerTab.TIRE else ProductPickerTab.LABEL
            loadProducts()
        }

        binding.etSearchProduct.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                loadProducts()
                true
            } else {
                false
            }
        }
        binding.tilSearchProduct.setEndIconOnClickListener { loadProducts() }

        binding.btnProductFilter.setOnClickListener { openFilterSheet() }
        binding.chipProductFilters.setOnClickListener { openFilterSheet() }
        binding.btnClearProductFilters.setOnClickListener { clearAllFilters() }

        binding.btnContinueLabeling.setOnClickListener { startLabelingSession() }
        binding.btnChangeProducts.setOnClickListener { returnToProductSelection() }
        binding.btnStartScan.setOnClickListener { showRfidReadBottomSheet() }
        binding.fabReadLabel.setOnClickListener { showRfidReadBottomSheet() }

        loadFilterOptionsAndProducts()
    }

    override fun onDestroy() {
        rfidBeepHandler.removeCallbacksAndMessages(null)
        rfidReadToneGenerator?.release()
        rfidReadToneGenerator = null
        super.onDestroy()
    }

    private fun loadFilterOptionsAndProducts() {
        binding.progressLabeling.isVisible = true
        binding.layoutProductSelection.isVisible = false
        binding.layoutLabelingSession.isVisible = false
        binding.tvLoadError.isVisible = false

        ApiClient.filtersApi.getProductTypes().enqueue(object : Callback<com.google.gson.JsonElement> {
            override fun onResponse(
                call: Call<com.google.gson.JsonElement>,
                response: Response<com.google.gson.JsonElement>
            ) {
                productTypes = if (response.isSuccessful) parseFilterJsonList(response.body()) else emptyList()
                tireTypeId = filterTypeIdByName(productTypes, "llanta")
                labelTypeId = filterTypeIdByName(productTypes, "etiqueta")
                loadProducts(showFullScreenProgress = false)
            }

            override fun onFailure(call: Call<com.google.gson.JsonElement>, t: Throwable) {
                loadProducts(showFullScreenProgress = true)
            }
        })
    }

    private fun effectiveProductTypeId(): Int? =
        panelFilters.productTypeId ?: when (pickerTab) {
            ProductPickerTab.TIRE -> tireTypeId
            ProductPickerTab.LABEL -> labelTypeId
        }

    private fun currentSearch(): String? =
        binding.etSearchProduct.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }

    private fun loadProducts(showFullScreenProgress: Boolean = false) {
        if (showFullScreenProgress) {
            binding.progressLabeling.isVisible = true
            binding.layoutProductSelection.isVisible = false
        } else {
            binding.recyclerProducts.isVisible = false
            binding.tvEmptyProducts.isVisible = false
        }
        binding.tvLoadError.isVisible = false
        updateFilterIndicators()

        ApiClient.productsApi.getProducts(
            search = currentSearch(),
            reference = panelFilters.reference,
            productTypeId = effectiveProductTypeId(),
            brandId = panelFilters.brandId,
            conditionId = panelFilters.conditionId,
            status = panelFilters.status,
            costMin = panelFilters.costMin,
            costMax = panelFilters.costMax
        ).enqueue(object : Callback<ProductsResponse> {
            override fun onResponse(call: Call<ProductsResponse>, response: Response<ProductsResponse>) {
                binding.progressLabeling.isVisible = false
                if (!response.isSuccessful) {
                    if (binding.layoutProductSelection.isVisible || showFullScreenProgress) {
                        binding.tvLoadError.isVisible = true
                    }
                    ApiErrorUi.showHttpError(this@LabelingActivity, getString(R.string.labeling_load_error), response)
                    return
                }
                allProducts = response.body()?.data.orEmpty()
                binding.layoutProductSelection.isVisible = true
                refreshProductList()
                updateSelectionSummary()
            }

            override fun onFailure(call: Call<ProductsResponse>, t: Throwable) {
                binding.progressLabeling.isVisible = false
                binding.tvLoadError.isVisible = true
                ApiErrorUi.showNetworkError(this@LabelingActivity, getString(R.string.labeling_load_error), t)
            }
        })
    }

    private fun openFilterSheet() {
        ProductsFilterBottomSheet(
            activity = this,
            initial = panelFilters,
            preloadedProductTypes = productTypes,
            onApply = { filters ->
                panelFilters = filters
                loadProducts()
            },
            onClear = { clearAllFilters() }
        ).show()
    }

    private fun clearAllFilters() {
        panelFilters = ProductsFilterState()
        binding.etSearchProduct.text?.clear()
        loadProducts()
    }

    private fun updateFilterIndicators() {
        binding.chipProductFilters.isVisible = panelFilters.hasPanelFilters()
        binding.btnClearProductFilters.isVisible = panelFilters.hasAnyFilter() || !currentSearch().isNullOrBlank()
    }

    private fun refreshProductList() {
        val selectedId = when (pickerTab) {
            ProductPickerTab.TIRE -> selectedTire?.id
            ProductPickerTab.LABEL -> selectedLabel?.id
        }
        productPickerAdapter.selectedProductId = selectedId
        productPickerAdapter.submitList(allProducts)
        val empty = allProducts.isEmpty()
        binding.recyclerProducts.isVisible = !empty
        binding.tvEmptyProducts.isVisible = empty
        binding.tvEmptyProducts.text = if (panelFilters.hasAnyFilter() || !currentSearch().isNullOrBlank()) {
            getString(R.string.labeling_products_empty_filtered)
        } else {
            getString(R.string.labeling_products_empty)
        }
    }

    private fun updateSelectionSummary() {
        selectedTire?.let { tire ->
            binding.tvSelectedTire.text = getString(
                R.string.labeling_selected_tire,
                tire.displayReference(),
                tire.displayTitle()
            )
        } ?: run {
            binding.tvSelectedTire.text = getString(R.string.labeling_selected_tire_empty)
        }

        selectedLabel?.let { label ->
            binding.tvSelectedLabel.text = getString(
                R.string.labeling_selected_label,
                label.displayReference(),
                label.displayTitle()
            )
        } ?: run {
            binding.tvSelectedLabel.text = getString(R.string.labeling_selected_label_empty)
        }

        binding.btnContinueLabeling.isEnabled = selectedTire != null && selectedLabel != null
    }

    private fun startLabelingSession() {
        val tire = selectedTire ?: run {
            Toast.makeText(this, R.string.labeling_select_tire_required, Toast.LENGTH_LONG).show()
            return
        }
        val label = selectedLabel ?: run {
            Toast.makeText(this, R.string.labeling_select_label_required, Toast.LENGTH_LONG).show()
            return
        }
        labelingSelection = LabelingSelection(tireProduct = tire, labelProduct = label)

        binding.layoutProductSelection.isVisible = false
        binding.layoutLabelingSession.isVisible = true
        binding.tvSessionTire.text = getString(
            R.string.labeling_session_tire,
            tire.displayReference(),
            tire.displayTitle()
        )
        binding.tvSessionLabel.text = getString(
            R.string.labeling_session_label,
            label.displayReference(),
            label.displayTitle()
        )
        refreshScansList()
    }

    private fun returnToProductSelection() {
        if (scanRows.isNotEmpty()) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.labeling_change_products)
                .setMessage(R.string.labeling_change_products_confirm)
                .setPositiveButton(R.string.labeling_change_products) { _, _ ->
                    scanRows.clear()
                    labelingSelection = null
                    binding.layoutLabelingSession.isVisible = false
                    binding.layoutProductSelection.isVisible = true
                    refreshScansList()
                    refreshProductList()
                }
                .setNegativeButton(R.string.rfid_detail_cancel, null)
                .show()
        } else {
            labelingSelection = null
            binding.layoutLabelingSession.isVisible = false
            binding.layoutProductSelection.isVisible = true
        }
    }

    private fun refreshScansList() {
        scansAdapter.submitList(scanRows.toList())
        val hasRows = scanRows.isNotEmpty()
        binding.recyclerLabelingScans.isVisible = hasRows
        binding.layoutStartAction.isVisible = !hasRows
        binding.fabReadLabel.isVisible = labelingSelection != null && hasRows
    }

    private fun showLabelingDetailDialog(
        labelCode: String,
        editExisting: LabelingScanRow? = null,
        onFinished: (LabelingScanPayload?) -> Unit = {}
    ) {
        val selection = labelingSelection ?: run {
            onFinished(null)
            return
        }
        showLabelingDetailDialogWithSelection(selection, labelCode, editExisting, onFinished)
    }

    private fun showLabelingDetailDialogWithSelection(
        selection: LabelingSelection,
        labelCode: String,
        editExisting: LabelingScanRow? = null,
        onFinished: (LabelingScanPayload?) -> Unit = {}
    ) {
        val tireProduct = selection.tireProduct
        val dialogBinding = DialogLabelingDetailBinding.inflate(layoutInflater)
        dialogBinding.tvLabelRead.text = labelCode
        dialogBinding.tvSelectedTireProduct.text = getString(
            R.string.labeling_detail_tire_product_line,
            tireProduct.displayReference(),
            tireProduct.displayTitle()
        )
        editExisting?.scan?.let { existing ->
            dialogBinding.etTireCode.setText(existing.tireCode)
        }

        val attributeFields = mutableListOf<Pair<ProductAttribute, TextInputEditText>>()

        fun rebuildAttributeFields(existingAttributes: Map<String, String>) {
            attributeFields.clear()
            dialogBinding.containerAttributes.removeAllViews()
            val attrs = tireProduct.labelingAttributes()
            dialogBinding.tvAttributesHeader.isVisible = attrs.isNotEmpty()
            attrs.forEach { attr ->
                val inputLayout = TextInputLayout(
                    this,
                    null,
                    com.google.android.material.R.style.Widget_MaterialComponents_TextInputLayout_OutlinedBox
                ).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dpToPx(8) }
                    hint = attr.name?.trim().orEmpty().ifBlank { getString(R.string.labeling_detail_attributes) }
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
                    val existing = existingAttributes[attr.attributeId.toString()]
                        ?: attr.value?.trim()?.takeIf { it.isNotEmpty() }
                    existing?.let { setText(it) }
                }
                inputLayout.addView(editText)
                dialogBinding.containerAttributes.addView(inputLayout)
                attributeFields += attr to editText
            }
        }

        rebuildAttributeFields(editExisting?.scan?.attributes.orEmpty())

        val dlg = MaterialAlertDialogBuilder(this)
            .setTitle(
                if (editExisting != null) R.string.labeling_detail_edit_title
                else R.string.labeling_detail_title
            )
            .setView(dialogBinding.root)
            .setCancelable(false)
            .create()

        dlg.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        dialogBinding.btnDetailSave.setOnClickListener {
            dialogBinding.tilTireCode.error = null
            val tireCode = dialogBinding.etTireCode.text?.toString()?.trim().orEmpty()
            if (tireCode.isEmpty()) {
                dialogBinding.tilTireCode.error = getString(R.string.rfid_validation_required_tire)
                return@setOnClickListener
            }

            val attributeMap = linkedMapOf<String, String>()
            for ((attr, editText) in attributeFields) {
                val value = editText.text?.toString()?.trim().orEmpty()
                if (value.isEmpty()) {
                    val label = attr.name?.trim().orEmpty().ifBlank { "?" }
                    Toast.makeText(
                        this,
                        getString(R.string.labeling_attribute_required, label),
                        Toast.LENGTH_LONG
                    ).show()
                    return@setOnClickListener
                }
                attributeMap[attr.attributeId.toString()] = value
            }

            val payload = LabelingScanPayload(
                labelCode = labelCode.trim(),
                tireCode = tireCode,
                tireProductId = tireProduct.id,
                attributes = attributeMap
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
        val selection = labelingSelection ?: return

        val dialog = BottomSheetDialog(this)
        val sheetBinding = BottomSheetRfidReadBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)
        sheetBinding.btnSendRfid.text = getString(R.string.labeling_send)

        val scannedEpcs = scanRows.map { normalizeEpc(it.scan.labelCode) }.toMutableSet()
        val pendingQueue = ArrayDeque<String>()
        var hardwareReleased = false
        var readerInitedOk = false
        var sessionActive = true
        var detailDialogOpen = false
        var ignoreReadsUntilMs = 0L
        var lastDuplicateToastAt = 0L

        val hardwareReader = ChainwayUhfRfidReader(this)
        lateinit var rfidSession: RfidScanSession
        lateinit var tagsAdapter: LabelingScansAdapter

        fun releaseHardware() {
            if (hardwareReleased) return
            hardwareReleased = true
            hardwareReader.free()
        }

        fun pauseReader() {
            if (!hardwareReleased) hardwareReader.stopInventory()
        }

        fun refreshTagsList() {
            tagsAdapter.submitList(scanRows.toList())
            sheetBinding.tvRfidCount.text = getString(R.string.rfid_sheet_tags_read, scanRows.size)
            sheetBinding.btnSendRfid.visibility = if (scanRows.isNotEmpty()) View.VISIBLE else View.GONE
        }

        fun notifyDuplicateChip() {
            val now = System.currentTimeMillis()
            if (now - lastDuplicateToastAt > 2500L) {
                lastDuplicateToastAt = now
                Toast.makeText(this, R.string.rfid_already_scanned, Toast.LENGTH_SHORT).show()
            }
        }

        fun saveOrUpdateScan(payload: LabelingScanPayload, editRowId: Long?) {
            val key = normalizeEpc(payload.labelCode)
            if (editRowId != null) {
                val idx = scanRows.indexOfFirst { it.stableId == editRowId }
                if (idx >= 0) scanRows[idx] = scanRows[idx].copy(scan = payload)
            } else {
                val existingIdx = scanRows.indexOfFirst { normalizeEpc(it.scan.labelCode) == key }
                if (existingIdx >= 0) {
                    scanRows[existingIdx] = scanRows[existingIdx].copy(scan = payload)
                } else {
                    scanRows.add(LabelingScanRow(stableId = nextScanRowId.getAndIncrement(), scan = payload))
                }
            }
            scannedEpcs.add(key)
            pendingQueue.removeAll { normalizeEpc(it) == key }
            ignoreReadsUntilMs = System.currentTimeMillis() + SCAN_COOLDOWN_MS
            refreshTagsList()
            refreshScansList()
        }

        fun openScanDialog(epc: String, editRow: LabelingScanRow?) {
            pauseReader()
            detailDialogOpen = true
            showLabelingDetailDialog(
                labelCode = epc,
                editExisting = editRow
            ) { payload ->
                detailDialogOpen = false
                if (sessionActive && payload != null) {
                    saveOrUpdateScan(payload, editRow?.stableId)
                }
                if (sessionActive) rfidSession.tryConsumeQueue()
            }
        }

        tagsAdapter = LabelingScansAdapter { row -> openScanDialog(row.scan.labelCode, row) }
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
                sheetBinding.tvRfidStatus.text = getString(R.string.rfid_hw_reading)
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
            if (scanRows.isEmpty()) return@setOnClickListener
            pauseReader()
            sheetBinding.btnSendRfid.isEnabled = false
            submitLabeling(selection, scanRows.map { it.scan }) { success ->
                sheetBinding.btnSendRfid.isEnabled = true
                if (success) {
                    Toast.makeText(this, R.string.labeling_send_ok, Toast.LENGTH_SHORT).show()
                    scanRows.clear()
                    refreshTagsList()
                    refreshScansList()
                    dialog.dismiss()
                }
            }
        }

        sheetBinding.btnCloseRfid.setOnClickListener { dialog.dismiss() }

        refreshTagsList()
        dialog.show()
    }

    private fun submitLabeling(
        selection: LabelingSelection,
        scans: List<LabelingScanPayload>,
        onComplete: (Boolean) -> Unit
    ) {
        val session = SessionManager(this)
        val cachedUserId = session.getUserId()
        if (cachedUserId > 0) {
            postLabeling(cachedUserId, selection, scans, onComplete)
            return
        }

        ApiClient.authApi.getMe().enqueue(object : Callback<MeResponse> {
            override fun onResponse(call: Call<MeResponse>, response: Response<MeResponse>) {
                val userId = response.body()?.resolveUser()?.id ?: 0
                if (response.isSuccessful && userId > 0) {
                    session.saveUserId(userId)
                    postLabeling(userId, selection, scans, onComplete)
                } else {
                    Toast.makeText(this@LabelingActivity, R.string.product_entity_error_user, Toast.LENGTH_LONG).show()
                    onComplete(false)
                }
            }

            override fun onFailure(call: Call<MeResponse>, t: Throwable) {
                ApiErrorUi.showNetworkError(this@LabelingActivity, getString(R.string.labeling_send_error), t)
                onComplete(false)
            }
        })
    }

    private fun postLabeling(
        userId: Int,
        selection: LabelingSelection,
        scans: List<LabelingScanPayload>,
        onComplete: (Boolean) -> Unit
    ) {
        val pairs = scans.map { scan ->
            LabelingPairRequest(
                tire = LabelingTireRequest(
                    productId = selection.tireProduct.id,
                    code = scan.tireCode,
                    attributes = scan.attributes
                ),
                label = LabelingLabelRequest(
                    productId = selection.labelProduct.id,
                    code = scan.labelCode
                )
            )
        }

        ApiClient.labelingApi.createLabeling(LabelingRequest(userId = userId, pairs = pairs))
            .enqueue(object : Callback<LabelingResponse> {
                override fun onResponse(call: Call<LabelingResponse>, response: Response<LabelingResponse>) {
                    if (response.isSuccessful) {
                        onComplete(true)
                    } else {
                        ApiErrorUi.showHttpError(
                            this@LabelingActivity,
                            getString(R.string.labeling_send_error),
                            response
                        )
                        onComplete(false)
                    }
                }

                override fun onFailure(call: Call<LabelingResponse>, t: Throwable) {
                    ApiErrorUi.showNetworkError(
                        this@LabelingActivity,
                        getString(R.string.labeling_send_error),
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

    private interface RfidScanSession {
        fun tryConsumeQueue()
        fun resumeReader()
        fun enqueueEpc(epc: String)
    }

    companion object {
        private const val SCAN_COOLDOWN_MS = 1500L

        private fun normalizeEpc(epc: String) = epc.trim().uppercase()
    }
}

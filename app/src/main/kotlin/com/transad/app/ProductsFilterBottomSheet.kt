package com.transad.app

import android.view.LayoutInflater
import android.widget.ArrayAdapter
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.transad.app.api.ApiClient
import com.transad.app.api.FilterBrand
import com.transad.app.api.FilterCondition
import com.transad.app.api.FilterProductType
import com.transad.app.api.ProductsFilterState
import com.transad.app.api.parseFilterJsonList
import com.transad.app.databinding.BottomSheetProductsFilterBinding
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ProductsFilterBottomSheet(
    private val activity: LabelingActivity,
    private val initial: ProductsFilterState,
    private val preloadedProductTypes: List<FilterProductType> = emptyList(),
    private val onApply: (ProductsFilterState) -> Unit,
    private val onClear: () -> Unit
) {
    fun show() {
        if (activity.isFinishing || activity.isDestroyed) return

        val dialog = BottomSheetDialog(activity)
        val sheetBinding = BottomSheetProductsFilterBinding.inflate(LayoutInflater.from(activity))
        dialog.setContentView(sheetBinding.root)

        sheetBinding.etFilterReference.setText(initial.reference.orEmpty())
        sheetBinding.etFilterCostMin.setText(initial.costMin.orEmpty())
        sheetBinding.etFilterCostMax.setText(initial.costMax.orEmpty())

        when (initial.status) {
            1 -> sheetBinding.radioStatusActive.isChecked = true
            0 -> sheetBinding.radioStatusInactive.isChecked = true
            else -> sheetBinding.radioStatusAll.isChecked = true
        }

        var productTypes = preloadedProductTypes
        var brands = emptyList<FilterBrand>()
        var conditions = emptyList<FilterCondition>()

        var selectedTypeId: Int? = initial.productTypeId
        var selectedBrandId: Int? = initial.brandId
        var selectedConditionId: Int? = initial.conditionId

        fun showForm() {
            sheetBinding.progressFilterOptions.isVisible = false
            sheetBinding.tilFilterReference.isVisible = true
            sheetBinding.tilFilterProductType.isVisible = true
            sheetBinding.tilFilterBrand.isVisible = true
            sheetBinding.tilFilterCondition.isVisible = true
            sheetBinding.tvFilterStatusLabel.isVisible = true
            sheetBinding.radioStatus.isVisible = true
            sheetBinding.layoutCostRange.isVisible = true
        }

        fun bindDropdown(
            act: MaterialAutoCompleteTextView,
            labels: List<String>,
            ids: List<Int?>,
            selectedId: Int?,
            onSelected: (Int?) -> Unit
        ) {
            if (labels.isEmpty()) return
            val adapter = ArrayAdapter(activity, android.R.layout.simple_list_item_1, labels)
            act.setAdapter(adapter)
            val index = ids.indexOfFirst { it == selectedId }.let { if (it >= 0) it else 0 }
            act.setText(labels[index], false)
            act.setOnItemClickListener { _, _, position, _ ->
                onSelected(ids.getOrNull(position))
            }
        }

        fun bindAllDropdowns() {
            val typeLabels = listOf(activity.getString(R.string.products_filter_all)) +
                productTypes.map { it.name?.trim().orEmpty().ifBlank { "Tipo ${it.id}" } }
            val typeIds = listOf<Int?>(null) + productTypes.map { it.id }
            bindDropdown(sheetBinding.actFilterProductType, typeLabels, typeIds, selectedTypeId) {
                selectedTypeId = it
            }

            val brandLabels = listOf(activity.getString(R.string.products_filter_all)) +
                brands.map { it.name?.trim().orEmpty().ifBlank { "Marca ${it.id}" } }
            val brandIds = listOf<Int?>(null) + brands.map { it.id }
            bindDropdown(sheetBinding.actFilterBrand, brandLabels, brandIds, selectedBrandId) {
                selectedBrandId = it
            }

            val conditionLabels = listOf(activity.getString(R.string.products_filter_all)) +
                conditions.map { it.name?.trim().orEmpty().ifBlank { "Condición ${it.id}" } }
            val conditionIds = listOf<Int?>(null) + conditions.map { it.id }
            bindDropdown(sheetBinding.actFilterCondition, conditionLabels, conditionIds, selectedConditionId) {
                selectedConditionId = it
            }
        }

        var typesLoaded = productTypes.isNotEmpty()
        var brandsLoaded = false
        var conditionsLoaded = false

        fun tryShowForm() {
            if (!typesLoaded || !brandsLoaded || !conditionsLoaded) return
            activity.runOnUiThread {
                if (activity.isFinishing || activity.isDestroyed || !dialog.isShowing) return@runOnUiThread
                bindAllDropdowns()
                showForm()
            }
        }

        fun safeParseList(body: com.google.gson.JsonElement?): List<FilterProductType> =
            runCatching { parseFilterJsonList<FilterProductType>(body) }.getOrDefault(emptyList())

        fun safeParseBrands(body: com.google.gson.JsonElement?): List<FilterBrand> =
            runCatching { parseFilterJsonList<FilterBrand>(body) }.getOrDefault(emptyList())

        fun safeParseConditions(body: com.google.gson.JsonElement?): List<FilterCondition> =
            runCatching { parseFilterJsonList<FilterCondition>(body) }.getOrDefault(emptyList())

        if (typesLoaded) {
            // Ya cargados en LabelingActivity
        } else {
            ApiClient.filtersApi.getProductTypes().enqueue(object : Callback<com.google.gson.JsonElement> {
                override fun onResponse(
                    call: Call<com.google.gson.JsonElement>,
                    response: Response<com.google.gson.JsonElement>
                ) {
                    productTypes = if (response.isSuccessful) safeParseList(response.body()) else emptyList()
                    typesLoaded = true
                    tryShowForm()
                }

                override fun onFailure(call: Call<com.google.gson.JsonElement>, t: Throwable) {
                    typesLoaded = true
                    tryShowForm()
                }
            })
        }

        ApiClient.filtersApi.getBrands().enqueue(object : Callback<com.google.gson.JsonElement> {
            override fun onResponse(
                call: Call<com.google.gson.JsonElement>,
                response: Response<com.google.gson.JsonElement>
            ) {
                brands = if (response.isSuccessful) safeParseBrands(response.body()) else emptyList()
                brandsLoaded = true
                tryShowForm()
            }

            override fun onFailure(call: Call<com.google.gson.JsonElement>, t: Throwable) {
                brandsLoaded = true
                tryShowForm()
            }
        })

        ApiClient.filtersApi.getConditions().enqueue(object : Callback<com.google.gson.JsonElement> {
            override fun onResponse(
                call: Call<com.google.gson.JsonElement>,
                response: Response<com.google.gson.JsonElement>
            ) {
                conditions = if (response.isSuccessful) safeParseConditions(response.body()) else emptyList()
                conditionsLoaded = true
                tryShowForm()
            }

            override fun onFailure(call: Call<com.google.gson.JsonElement>, t: Throwable) {
                conditionsLoaded = true
                tryShowForm()
            }
        })

        sheetBinding.btnApplyFilters.setOnClickListener {
            val status = when (sheetBinding.radioStatus.checkedRadioButtonId) {
                R.id.radioStatusActive -> 1
                R.id.radioStatusInactive -> 0
                else -> null
            }
            onApply(
                ProductsFilterState(
                    reference = sheetBinding.etFilterReference.text?.toString()?.trim()?.takeIf { it.isNotEmpty() },
                    productTypeId = selectedTypeId,
                    brandId = selectedBrandId,
                    conditionId = selectedConditionId,
                    status = status,
                    costMin = sheetBinding.etFilterCostMin.text?.toString()?.trim()?.takeIf { it.isNotEmpty() },
                    costMax = sheetBinding.etFilterCostMax.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                )
            )
            dialog.dismiss()
        }

        sheetBinding.btnClearFilters.setOnClickListener {
            onClear()
            dialog.dismiss()
        }

        dialog.show()

        if (typesLoaded) {
            tryShowForm()
        }
    }
}

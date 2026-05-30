package com.transad.app

import android.view.LayoutInflater
import android.widget.ArrayAdapter
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetDialog
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
    private val onApply: (ProductsFilterState) -> Unit,
    private val onClear: () -> Unit
) {
    fun show() {
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

        var productTypes = emptyList<FilterProductType>()
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
            act: android.widget.AutoCompleteTextView,
            labels: List<String>,
            ids: List<Int?>,
            selectedId: Int?,
            onSelected: (Int?) -> Unit
        ) {
            val adapter = ArrayAdapter(activity, android.R.layout.simple_dropdown_item_1line, labels)
            act.setAdapter(adapter)
            val index = ids.indexOfFirst { it == selectedId }.coerceAtLeast(0)
            act.setText(labels.getOrElse(index) { labels.first() }, false)
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

        var typesLoaded = false
        var brandsLoaded = false
        var conditionsLoaded = false

        fun tryShowForm() {
            if (typesLoaded && brandsLoaded && conditionsLoaded) {
                bindAllDropdowns()
                showForm()
            }
        }

        ApiClient.filtersApi.getProductTypes().enqueue(object : Callback<com.google.gson.JsonElement> {
            override fun onResponse(
                call: Call<com.google.gson.JsonElement>,
                response: Response<com.google.gson.JsonElement>
            ) {
                productTypes = if (response.isSuccessful) parseFilterJsonList(response.body()) else emptyList()
                typesLoaded = true
                tryShowForm()
            }

            override fun onFailure(call: Call<com.google.gson.JsonElement>, t: Throwable) {
                typesLoaded = true
                tryShowForm()
            }
        })

        ApiClient.filtersApi.getBrands().enqueue(object : Callback<com.google.gson.JsonElement> {
            override fun onResponse(
                call: Call<com.google.gson.JsonElement>,
                response: Response<com.google.gson.JsonElement>
            ) {
                brands = if (response.isSuccessful) parseFilterJsonList(response.body()) else emptyList()
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
                conditions = if (response.isSuccessful) parseFilterJsonList(response.body()) else emptyList()
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
    }
}

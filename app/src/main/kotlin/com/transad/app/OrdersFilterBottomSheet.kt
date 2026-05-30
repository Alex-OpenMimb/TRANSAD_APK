package com.transad.app

import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.datepicker.MaterialDatePicker
import com.transad.app.api.ApiClient
import com.transad.app.api.ApiErrors
import com.transad.app.api.CostCenter
import com.transad.app.api.CostCentersResponse
import com.transad.app.api.OrdersFilterState
import com.transad.app.databinding.BottomSheetOrdersFilterBinding
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class OrdersFilterBottomSheet(
    private val activity: AppCompatActivity,
    private val initial: OrdersFilterState,
    private val onApply: (OrdersFilterState) -> Unit,
    private val onClear: () -> Unit
) {
    private data class CostCenterOption(val id: Int?, val label: String)

    private val apiDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun show() {
        val dialog = BottomSheetDialog(activity)
        val sheetBinding = BottomSheetOrdersFilterBinding.inflate(LayoutInflater.from(activity))
        dialog.setContentView(sheetBinding.root)

        sheetBinding.etFilterDateFrom.setText(initial.dateFrom.orEmpty())
        sheetBinding.etFilterDateTo.setText(initial.dateTo.orEmpty())
        sheetBinding.etFilterLicensePlate.setText(initial.licensePlate.orEmpty())

        val openDatePicker = { current: String?, onPicked: (String) -> Unit ->
            val builder = MaterialDatePicker.Builder.datePicker()
            if (!current.isNullOrBlank()) {
                runCatching {
                    val parsed = apiDateFormat.parse(current)
                    if (parsed != null) builder.setSelection(parsed.time)
                }
            }
            val picker = builder.build()
            picker.addOnPositiveButtonClickListener { millis ->
                val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                cal.timeInMillis = millis
                onPicked(apiDateFormat.format(cal.time))
            }
            picker.show(activity.supportFragmentManager, "orders_date_picker")
        }

        sheetBinding.etFilterDateFrom.setOnClickListener {
            openDatePicker(sheetBinding.etFilterDateFrom.text?.toString()) {
                sheetBinding.etFilterDateFrom.setText(it)
            }
        }
        sheetBinding.tilFilterDateFrom.setEndIconOnClickListener { sheetBinding.etFilterDateFrom.performClick() }

        sheetBinding.etFilterDateTo.setOnClickListener {
            openDatePicker(sheetBinding.etFilterDateTo.text?.toString()) {
                sheetBinding.etFilterDateTo.setText(it)
            }
        }
        sheetBinding.tilFilterDateTo.setEndIconOnClickListener { sheetBinding.etFilterDateTo.performClick() }

        var costCenterOptions = listOf(
            CostCenterOption(null, activity.getString(R.string.orders_filter_cost_center_all))
        )
        var selectedCostCenterId: Int? = initial.costCenterId

        fun bindSpinner() {
            val labels = costCenterOptions.map { it.label }
            val adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, labels)
            sheetBinding.spinnerCostCenter.adapter = adapter
            val index = costCenterOptions.indexOfFirst { it.id == selectedCostCenterId }.coerceAtLeast(0)
            sheetBinding.spinnerCostCenter.setSelection(index)
        }

        sheetBinding.spinnerCostCenter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedCostCenterId = costCenterOptions.getOrNull(position)?.id
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        bindSpinner()
        sheetBinding.progressCostCenters.visibility = View.VISIBLE
        sheetBinding.spinnerCostCenter.isEnabled = false

        ApiClient.costCentersApi.getCostCenters().enqueue(object : Callback<CostCentersResponse> {
            override fun onResponse(call: Call<CostCentersResponse>, response: Response<CostCentersResponse>) {
                sheetBinding.progressCostCenters.visibility = View.GONE
                sheetBinding.spinnerCostCenter.isEnabled = true
                if (response.isSuccessful) {
                    val centers = response.body()?.data.orEmpty()
                    costCenterOptions = listOf(
                        CostCenterOption(null, activity.getString(R.string.orders_filter_cost_center_all))
                    ) + centers.map { it.toSpinnerOption(activity) }
                    bindSpinner()
                } else {
                    ApiErrorUi.showHttpError(
                        activity,
                        activity.getString(R.string.orders_cost_centers_error),
                        response
                    )
                }
            }

            override fun onFailure(call: Call<CostCentersResponse>, t: Throwable) {
                sheetBinding.progressCostCenters.visibility = View.GONE
                sheetBinding.spinnerCostCenter.isEnabled = true
                ApiErrorUi.showNetworkError(
                    activity,
                    activity.getString(R.string.orders_cost_centers_error),
                    t
                )
            }
        })

        sheetBinding.btnApplyFilters.setOnClickListener {
            onApply(
                OrdersFilterState(
                    reference = initial.reference,
                    dateFrom = sheetBinding.etFilterDateFrom.text?.toString()?.trim()?.takeIf { it.isNotEmpty() },
                    dateTo = sheetBinding.etFilterDateTo.text?.toString()?.trim()?.takeIf { it.isNotEmpty() },
                    costCenterId = selectedCostCenterId,
                    licensePlate = sheetBinding.etFilterLicensePlate.text?.toString()?.trim()
                        ?.takeIf { it.isNotEmpty() }?.uppercase()
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

    private fun CostCenter.toSpinnerOption(activity: AppCompatActivity): CostCenterOption {
        val plate = licensePlate?.trim().orEmpty().ifBlank { "—" }
        val ref = reference?.trim().orEmpty().ifBlank { "—" }
        val label = activity.getString(R.string.orders_filter_cost_center_item, plate, ref)
        return CostCenterOption(id, label)
    }
}

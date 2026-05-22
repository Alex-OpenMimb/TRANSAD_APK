package com.transad.app

import android.view.LayoutInflater
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.transad.app.databinding.BottomSheetCostCentersFilterBinding

class CostCentersFilterBottomSheet(
    private val activity: InspectionActivity,
    private val initialStatus: Int?,
    private val onApply: (Int?) -> Unit,
    private val onClear: () -> Unit
) {
    fun show() {
        val dialog = BottomSheetDialog(activity)
        val sheetBinding = BottomSheetCostCentersFilterBinding.inflate(LayoutInflater.from(activity))
        dialog.setContentView(sheetBinding.root)

        when (initialStatus) {
            1 -> sheetBinding.radioStatusActive.isChecked = true
            0 -> sheetBinding.radioStatusInactive.isChecked = true
            else -> sheetBinding.radioStatusAll.isChecked = true
        }

        sheetBinding.btnApplyFilters.setOnClickListener {
            val status = when (sheetBinding.radioStatus.checkedRadioButtonId) {
                R.id.radioStatusActive -> 1
                R.id.radioStatusInactive -> 0
                else -> null
            }
            onApply(status)
            dialog.dismiss()
        }

        sheetBinding.btnClearFilters.setOnClickListener {
            onClear()
            dialog.dismiss()
        }

        dialog.show()
    }
}

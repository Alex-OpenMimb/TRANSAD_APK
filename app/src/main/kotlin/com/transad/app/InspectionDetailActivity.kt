package com.transad.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.transad.app.api.CostCenter
import com.transad.app.databinding.ActivityInspectionDetailBinding

class InspectionDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityInspectionDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        binding = ActivityInspectionDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val costCenter = readCostCenterExtra(intent)
        if (costCenter == null) {
            finish()
            return
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val plate = costCenter.licensePlate?.trim().orEmpty().ifBlank { "—" }
        title = plate

        binding.tvLicensePlate.text = plate
        binding.tvReference.text = costCenter.reference?.trim().orEmpty().ifBlank { "—" }
        binding.tvDescription.text = costCenter.description?.trim().orEmpty().ifBlank { "—" }
        binding.chipStatus.text = if (costCenter.status) {
            getString(R.string.order_status_active)
        } else {
            getString(R.string.order_status_inactive)
        }
        binding.chipStatus.setChipBackgroundColorResource(
            if (costCenter.status) R.color.status_active else R.color.status_inactive
        )

        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    companion object {
        const val EXTRA_COST_CENTER = "cost_center"

        private fun readCostCenterExtra(intent: Intent): CostCenter? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(EXTRA_COST_CENTER, CostCenter::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(EXTRA_COST_CENTER)
            }
    }
}

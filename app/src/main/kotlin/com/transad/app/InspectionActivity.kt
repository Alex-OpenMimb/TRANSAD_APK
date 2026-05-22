package com.transad.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.transad.app.api.ApiClient
import com.transad.app.api.CostCentersFilterState
import com.transad.app.api.CostCentersResponse
import com.transad.app.databinding.ActivityInspectionBinding
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class InspectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityInspectionBinding
    private lateinit var adapter: CostCentersAdapter

    /** Filtro de estado del panel; la búsqueda va en la barra superior. */
    private var statusFilter: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        binding = ActivityInspectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        adapter = CostCentersAdapter { costCenter ->
            startActivity(
                Intent(this, InspectionDetailActivity::class.java).apply {
                    putExtra(InspectionDetailActivity.EXTRA_COST_CENTER, costCenter)
                }
            )
        }
        binding.recyclerCostCenters.layoutManager = LinearLayoutManager(this)
        binding.recyclerCostCenters.adapter = adapter

        setupSearchAndFilters()
        loadCostCenters()
    }

    private fun setupSearchAndFilters() {
        binding.tilSearchCostCenter.setEndIconOnClickListener { performSearch() }
        binding.etSearchCostCenter.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                true
            } else {
                false
            }
        }
        binding.btnInspectionFilter.setOnClickListener { openFilterSheet() }
        binding.chipInspectionFilters.setOnClickListener { openFilterSheet() }
        binding.btnClearInspectionFilters.setOnClickListener { clearAllFilters() }
    }

    private fun performSearch() {
        loadCostCenters()
    }

    private fun currentFilters(): CostCentersFilterState {
        val search = binding.etSearchCostCenter.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        return CostCentersFilterState(search = search, status = statusFilter)
    }

    private fun openFilterSheet() {
        CostCentersFilterBottomSheet(
            activity = this,
            initialStatus = statusFilter,
            onApply = { status ->
                statusFilter = status
                updateFilterIndicators()
                loadCostCenters()
            },
            onClear = {
                statusFilter = null
                binding.etSearchCostCenter.text?.clear()
                updateFilterIndicators()
                loadCostCenters()
            }
        ).show()
    }

    private fun clearAllFilters() {
        statusFilter = null
        binding.etSearchCostCenter.text?.clear()
        updateFilterIndicators()
        loadCostCenters()
    }

    private fun updateFilterIndicators() {
        val filters = currentFilters()
        binding.chipInspectionFilters.visibility = if (statusFilter != null) View.VISIBLE else View.GONE
        binding.btnClearInspectionFilters.visibility = if (filters.hasAnyFilter()) View.VISIBLE else View.GONE
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun loadCostCenters() {
        val filters = currentFilters()
        updateFilterIndicators()

        binding.recyclerCostCenters.visibility = View.GONE
        binding.tvEmptyInspection.visibility = View.GONE
        binding.tvEmptyInspection.text = if (filters.hasAnyFilter()) {
            getString(R.string.inspection_empty_filtered)
        } else {
            getString(R.string.inspection_empty)
        }
        binding.progressInspection.visibility = View.VISIBLE

        ApiClient.costCentersApi.getCostCenters(
            search = filters.search,
            status = filters.status
        ).enqueue(object : Callback<CostCentersResponse> {
            override fun onResponse(call: Call<CostCentersResponse>, response: Response<CostCentersResponse>) {
                binding.progressInspection.visibility = View.GONE
                if (response.isSuccessful) {
                    val list = response.body()?.data ?: emptyList()
                    adapter.submitList(list)
                    binding.recyclerCostCenters.visibility = View.VISIBLE
                    if (list.isEmpty()) {
                        binding.tvEmptyInspection.visibility = View.VISIBLE
                    }
                } else {
                    binding.tvEmptyInspection.visibility = View.VISIBLE
                    binding.tvEmptyInspection.text = getString(R.string.inspection_error)
                    Toast.makeText(this@InspectionActivity, R.string.inspection_error, Toast.LENGTH_LONG).show()
                }
            }

            override fun onFailure(call: Call<CostCentersResponse>, t: Throwable) {
                binding.progressInspection.visibility = View.GONE
                binding.tvEmptyInspection.visibility = View.VISIBLE
                binding.tvEmptyInspection.text = getString(R.string.inspection_error)
                Toast.makeText(
                    this@InspectionActivity,
                    getString(R.string.inspection_error) + " " + t.message,
                    Toast.LENGTH_LONG
                ).show()
            }
        })
    }
}

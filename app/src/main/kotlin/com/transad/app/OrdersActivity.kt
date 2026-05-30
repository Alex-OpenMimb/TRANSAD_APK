package com.transad.app

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.transad.app.api.ApiClient
import com.transad.app.api.ApiErrors
import com.transad.app.api.OrdersFilterState
import com.transad.app.api.OrdersResponse
import com.transad.app.databinding.ActivityOrdersBinding
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class OrdersActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOrdersBinding
    private lateinit var adapter: OrdersAdapter

    private var panelFilters = OrdersFilterState()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        binding = ActivityOrdersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (!ensureValidSession()) return

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        adapter = OrdersAdapter { order ->
            startActivity(
                Intent(this, OrderDetailActivity::class.java).apply {
                    putExtra(OrderDetailActivity.EXTRA_ORDER_ID, order.id)
                    putExtra(OrderDetailActivity.EXTRA_ORDER_USER_ID, order.userId)
                    putExtra(OrderDetailActivity.EXTRA_ORDER_REFERENCE, order.reference)
                    putExtra(OrderDetailActivity.EXTRA_ORDER_LICENSE_PLATE, order.licensePlateFromCostCenter())
                }
            )
        }
        binding.recyclerOrders.layoutManager = LinearLayoutManager(this)
        binding.recyclerOrders.adapter = adapter

        setupOrdersSearchAndFilters()
        loadOrders()
    }

    private fun ensureValidSession(): Boolean {
        val session = SessionManager(this)
        if (!session.isLoggedIn() || !session.isSessionForBaseUrl(ApiClient.BASE_URL)) {
            session.logout()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return false
        }
        return true
    }

    private fun setupOrdersSearchAndFilters() {
        binding.tilSearchReference.setEndIconOnClickListener { loadOrders() }
        binding.etSearchReference.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                loadOrders()
                true
            } else {
                false
            }
        }
        binding.btnOrdersFilter.setOnClickListener { openOrdersFilterSheet() }
        binding.chipActiveFilters.setOnClickListener { openOrdersFilterSheet() }
        binding.btnClearSearch.setOnClickListener { clearAllOrdersFilters() }
    }

    private fun openOrdersFilterSheet() {
        OrdersFilterBottomSheet(
            activity = this,
            initial = currentFilters(),
            onApply = { applied ->
                panelFilters = applied.copy(reference = null)
                binding.etSearchReference.setText(applied.reference.orEmpty())
                updateFilterIndicators()
                loadOrders()
            },
            onClear = {
                panelFilters = OrdersFilterState()
                binding.etSearchReference.text?.clear()
                updateFilterIndicators()
                loadOrders()
            }
        ).show()
    }

    private fun clearAllOrdersFilters() {
        panelFilters = OrdersFilterState()
        binding.etSearchReference.text?.clear()
        updateFilterIndicators()
        loadOrders()
    }

    private fun currentFilters(): OrdersFilterState {
        val reference = binding.etSearchReference.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        return panelFilters.copy(reference = reference)
    }

    private fun updateFilterIndicators() {
        val active = currentFilters().hasAnyFilter()
        binding.chipActiveFilters.visibility = if (panelFilters.hasAnyFilter()) View.VISIBLE else View.GONE
        binding.btnClearSearch.visibility = if (active) View.VISIBLE else View.GONE
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_refresh_orders) {
            loadOrders(showRefreshAck = true)
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun loadOrders(showRefreshAck: Boolean = false) {
        val filters = currentFilters()
        updateFilterIndicators()

        binding.recyclerOrders.visibility = View.GONE
        binding.tvEmpty.visibility = View.GONE
        binding.tvEmpty.text = if (filters.hasAnyFilter()) {
            getString(R.string.orders_empty_filtered)
        } else {
            getString(R.string.orders_empty)
        }
        binding.progressOrders.visibility = View.VISIBLE

        ApiClient.ordersApi.getOrders(
            reference = filters.reference,
            dateFrom = filters.dateFrom,
            dateTo = filters.dateTo,
            costCenterId = filters.costCenterId,
            licensePlate = filters.licensePlate
        ).enqueue(object : Callback<OrdersResponse> {
            override fun onResponse(call: Call<OrdersResponse>, response: Response<OrdersResponse>) {
                binding.progressOrders.visibility = View.GONE
                if (response.isSuccessful) {
                    val list = response.body()?.data ?: emptyList()
                    adapter.submitList(list)
                    binding.recyclerOrders.visibility = View.VISIBLE
                    if (list.isEmpty()) binding.tvEmpty.visibility = View.VISIBLE
                    if (showRefreshAck) {
                        Toast.makeText(this@OrdersActivity, R.string.orders_refreshed, Toast.LENGTH_SHORT).show()
                    }
                } else {
                    val errorText = ApiErrors.formatHttpError(response, getString(R.string.orders_error))
                    binding.tvEmpty.visibility = View.VISIBLE
                    binding.tvEmpty.text = errorText
                    ApiErrorUi.showHttpError(this@OrdersActivity, getString(R.string.orders_error), response)
                }
            }

            override fun onFailure(call: Call<OrdersResponse>, t: Throwable) {
                binding.progressOrders.visibility = View.GONE
                binding.tvEmpty.visibility = View.VISIBLE
                binding.tvEmpty.text = getString(R.string.orders_error)
                ApiErrorUi.showNetworkError(this@OrdersActivity, getString(R.string.orders_error), t)
            }
        })
    }
}

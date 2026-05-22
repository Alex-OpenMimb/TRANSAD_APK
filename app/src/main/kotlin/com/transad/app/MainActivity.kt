package com.transad.app

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.transad.app.api.ApiClient
import com.transad.app.api.OrdersFilterState
import com.transad.app.api.OrdersResponse
import com.transad.app.databinding.ActivityMainBinding
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import okhttp3.ResponseBody

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: OrdersAdapter

    /** Filtros del panel (fechas, placa, centro de costo). La referencia va en la barra de búsqueda. */
    private var panelFilters = OrdersFilterState()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (!SessionManager(this).isLoggedIn()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val toggle = ActionBarDrawerToggle(
            this,
            binding.drawerLayout,
            binding.toolbar,
            R.string.menu_inicio,
            R.string.menu_ordenes
        )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        binding.navView.menu.findItem(R.id.nav_api_logs)?.isVisible = BuildConfig.DEBUG

        binding.navView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_inicio, R.id.nav_ordenes -> {
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                    if (item.itemId == R.id.nav_ordenes) loadOrders()
                }
                R.id.nav_inventario -> {
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                    startActivity(Intent(this, InventoryActivity::class.java))
                }
                R.id.nav_inspeccion -> {
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                    startActivity(Intent(this, InspectionActivity::class.java))
                }
                R.id.nav_perfil -> {
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                    Toast.makeText(this, "Próximamente", Toast.LENGTH_SHORT).show()
                }
                R.id.nav_api_logs -> {
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                    startActivity(Intent(this, ApiLogsActivity::class.java))
                }
                R.id.nav_logout -> {
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                    cerrarSesion()
                }
            }
            true
        }

        adapter = OrdersAdapter { order ->
            val intent = Intent(this, OrderDetailActivity::class.java).apply {
                putExtra(OrderDetailActivity.EXTRA_ORDER_ID, order.id)
                putExtra(OrderDetailActivity.EXTRA_ORDER_USER_ID, order.userId)
                putExtra(OrderDetailActivity.EXTRA_ORDER_REFERENCE, order.reference)
                putExtra(OrderDetailActivity.EXTRA_ORDER_LICENSE_PLATE, order.licensePlateFromCostCenter())
                putParcelableArrayListExtra(OrderDetailActivity.EXTRA_ORDER_PRODUCTS, ArrayList(order.orderProducts ?: emptyList()))
            }
            startActivity(intent)
        }
        binding.recyclerOrders.layoutManager = LinearLayoutManager(this)
        binding.recyclerOrders.adapter = adapter

        setupOrdersSearchAndFilters()
        loadOrders()
    }

    private fun setupOrdersSearchAndFilters() {
        binding.tilSearchReference.setEndIconOnClickListener { performOrdersSearch() }
        binding.etSearchReference.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performOrdersSearch()
                true
            } else {
                false
            }
        }
        binding.btnOrdersFilter.setOnClickListener { openOrdersFilterSheet() }
        binding.chipActiveFilters.setOnClickListener { openOrdersFilterSheet() }
        binding.btnClearSearch.setOnClickListener { clearAllOrdersFilters() }
    }

    private fun performOrdersSearch() {
        loadOrders()
    }

    private fun currentFilters(): OrdersFilterState {
        val reference = binding.etSearchReference.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        return panelFilters.copy(reference = reference)
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

    private fun cerrarSesion() {
        ApiClient.authApi.logout().enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                irALogin()
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                irALogin()
            }
        })
    }

    private fun irALogin() {
        SessionManager(this).logout()
        startActivity(Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
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
            override fun onResponse(
                call: Call<OrdersResponse>,
                response: Response<OrdersResponse>
            ) {
                binding.progressOrders.visibility = View.GONE
                if (response.isSuccessful) {
                    val list = response.body()?.data ?: emptyList()
                    adapter.submitList(list)
                    binding.recyclerOrders.visibility = View.VISIBLE
                    if (list.isEmpty()) {
                        binding.tvEmpty.visibility = View.VISIBLE
                    }
                    if (showRefreshAck) {
                        Toast.makeText(this@MainActivity, R.string.orders_refreshed, Toast.LENGTH_SHORT).show()
                    }
                } else {
                    binding.tvEmpty.visibility = View.VISIBLE
                    binding.tvEmpty.text = getString(R.string.orders_error)
                    Toast.makeText(this@MainActivity, R.string.orders_error, Toast.LENGTH_LONG).show()
                }
            }

            override fun onFailure(call: Call<OrdersResponse>, t: Throwable) {
                binding.progressOrders.visibility = View.GONE
                binding.tvEmpty.visibility = View.VISIBLE
                binding.tvEmpty.text = getString(R.string.orders_error)
                Toast.makeText(this@MainActivity, getString(R.string.orders_error) + " " + t.message, Toast.LENGTH_LONG).show()
            }
        })
    }
}

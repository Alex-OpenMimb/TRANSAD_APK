package com.transad.app

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.transad.app.api.ApiClient
import com.transad.app.api.ApiErrors
import com.transad.app.api.ProductsResponse
import com.transad.app.databinding.ActivityInventoryBinding
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class InventoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityInventoryBinding
    private lateinit var adapter: InventoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        binding = ActivityInventoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        adapter = InventoryAdapter()
        binding.recyclerInventory.layoutManager = LinearLayoutManager(this)
        binding.recyclerInventory.adapter = adapter

        loadInventory()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_inventory, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_refresh_inventory) {
            loadInventory(showRefreshAck = true)
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun loadInventory(showRefreshAck: Boolean = false) {
        binding.recyclerInventory.visibility = View.GONE
        binding.tvEmptyInventory.visibility = View.GONE
        binding.tvEmptyInventory.text = getString(R.string.inventory_empty)
        binding.progressInventory.visibility = View.VISIBLE

        ApiClient.productsApi.getProducts().enqueue(object : Callback<ProductsResponse> {
            override fun onResponse(call: Call<ProductsResponse>, response: Response<ProductsResponse>) {
                binding.progressInventory.visibility = View.GONE
                if (response.isSuccessful) {
                    val products = response.body()?.data ?: emptyList()
                    adapter.submitList(products)
                    binding.recyclerInventory.visibility = View.VISIBLE
                    if (products.isEmpty()) {
                        binding.tvEmptyInventory.visibility = View.VISIBLE
                    }
                    if (showRefreshAck) {
                        Toast.makeText(
                            this@InventoryActivity,
                            R.string.inventory_refreshed,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    val errorText = ApiErrors.formatHttpError(response, getString(R.string.inventory_error))
                    binding.tvEmptyInventory.visibility = View.VISIBLE
                    binding.tvEmptyInventory.text = errorText
                    ApiErrorUi.showHttpError(this@InventoryActivity, getString(R.string.inventory_error), response)
                }
            }

            override fun onFailure(call: Call<ProductsResponse>, t: Throwable) {
                binding.progressInventory.visibility = View.GONE
                binding.tvEmptyInventory.visibility = View.VISIBLE
                binding.tvEmptyInventory.text = getString(R.string.inventory_error)
                ApiErrorUi.showNetworkError(this@InventoryActivity, getString(R.string.inventory_error), t)
            }
        })
    }
}

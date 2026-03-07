package com.transad.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.transad.app.api.ApiClient
import com.transad.app.api.OrdersResponse
import com.transad.app.databinding.ActivityMainBinding
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import okhttp3.ResponseBody

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: OrdersAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

        binding.navView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_inicio, R.id.nav_ordenes -> {
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                    if (item.itemId == R.id.nav_ordenes) loadOrders()
                }
                R.id.nav_perfil -> {
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                    Toast.makeText(this, "Próximamente", Toast.LENGTH_SHORT).show()
                }
                R.id.nav_logout -> {
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                    cerrarSesion()
                }
            }
            true
        }

        adapter = OrdersAdapter()
        binding.recyclerOrders.layoutManager = LinearLayoutManager(this)
        binding.recyclerOrders.adapter = adapter

        loadOrders()
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

    private fun loadOrders() {
        binding.recyclerOrders.visibility = View.GONE
        binding.tvEmpty.visibility = View.GONE
        binding.progressOrders.visibility = View.VISIBLE

        ApiClient.ordersApi.getOrders().enqueue(object : Callback<OrdersResponse> {
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

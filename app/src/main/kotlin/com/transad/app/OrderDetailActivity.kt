package com.transad.app

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.transad.app.api.ApiClient
import com.transad.app.api.OrderProduct
import com.transad.app.api.RfidScansRequest
import com.transad.app.api.RfidScansResponse
import com.transad.app.databinding.ActivityOrderDetailBinding
import com.transad.app.databinding.BottomSheetRfidReadBinding
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class OrderDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOrderDetailBinding
    private lateinit var adapter: OrderProductsAdapter

    private var orderId: Int = 0
    private val rfidTagsRead = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOrderDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        orderId = intent.getIntExtra(EXTRA_ORDER_ID, 0)
        val orderReference = intent.getStringExtra(EXTRA_ORDER_REFERENCE) ?: ""
        title = orderReference

        val products = intent.getParcelableArrayListExtra<OrderProduct>(EXTRA_ORDER_PRODUCTS) ?: arrayListOf()

        adapter = OrderProductsAdapter()
        binding.recyclerOrderProducts.layoutManager = LinearLayoutManager(this)
        binding.recyclerOrderProducts.adapter = adapter

        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        if (products.isEmpty()) {
            binding.recyclerOrderProducts.visibility = View.GONE
            binding.tvEmptyProducts.visibility = View.VISIBLE
            binding.fabReadRfid.visibility = View.GONE
        } else {
            binding.tvEmptyProducts.visibility = View.GONE
            binding.recyclerOrderProducts.visibility = View.VISIBLE
            binding.fabReadRfid.visibility = View.VISIBLE
            adapter.submitList(products)

            binding.fabReadRfid.setOnClickListener { showRfidReadBottomSheet() }
        }
    }

    private fun showRfidReadBottomSheet() {
        val dialog = BottomSheetDialog(this)
        val sheetBinding = BottomSheetRfidReadBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)

        val tagsAdapter = RfidTagsAdapter()
        sheetBinding.recyclerRfidTags.layoutManager = LinearLayoutManager(this)
        sheetBinding.recyclerRfidTags.adapter = tagsAdapter

        fun refreshTagsList() {
            tagsAdapter.submitList(rfidTagsRead.toList())
            sheetBinding.tvRfidCount.text = getString(R.string.rfid_sheet_tags_read, rfidTagsRead.size)
            sheetBinding.btnSendRfid.visibility = if (rfidTagsRead.isNotEmpty()) View.VISIBLE else View.GONE
        }

        sheetBinding.btnAddTag.setOnClickListener {
            val tag = sheetBinding.etRfidTag.text?.toString()?.trim()
            if (!tag.isNullOrEmpty()) {
                rfidTagsRead.add(tag)
                sheetBinding.etRfidTag.text?.clear()
                refreshTagsList()
            }
        }

        sheetBinding.btnSendRfid.setOnClickListener {
            if (rfidTagsRead.isEmpty()) return@setOnClickListener
            sheetBinding.btnSendRfid.isEnabled = false
            ApiClient.ordersApi.submitScans(orderId, RfidScansRequest(tags = rfidTagsRead.toList()))
                .enqueue(object : Callback<RfidScansResponse> {
                    override fun onResponse(
                        call: Call<RfidScansResponse>,
                        response: Response<RfidScansResponse>
                    ) {
                        sheetBinding.btnSendRfid.isEnabled = true
                        if (response.isSuccessful) {
                            Toast.makeText(this@OrderDetailActivity, R.string.rfid_send_ok, Toast.LENGTH_SHORT).show()
                            rfidTagsRead.clear()
                            refreshTagsList()
                            dialog.dismiss()
                        } else {
                            Toast.makeText(this@OrderDetailActivity, R.string.rfid_send_error, Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onFailure(call: Call<RfidScansResponse>, t: Throwable) {
                        sheetBinding.btnSendRfid.isEnabled = true
                        Toast.makeText(this@OrderDetailActivity, getString(R.string.rfid_send_error) + " " + t.message, Toast.LENGTH_SHORT).show()
                    }
                })
        }

        sheetBinding.btnCloseRfid.setOnClickListener { dialog.dismiss() }

        refreshTagsList()
        dialog.show()
    }

    companion object {
        const val EXTRA_ORDER_ID = "order_id"
        const val EXTRA_ORDER_REFERENCE = "order_reference"
        const val EXTRA_ORDER_PRODUCTS = "order_products"
    }
}

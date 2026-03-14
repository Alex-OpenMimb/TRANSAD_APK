package com.transad.app

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.transad.app.api.OrderProduct
import com.transad.app.databinding.ItemOrderProductBinding

class OrderProductsAdapter : ListAdapter<OrderProduct, OrderProductsAdapter.ProductViewHolder>(OrderProductDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val binding = ItemOrderProductBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ProductViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ProductViewHolder(private val binding: ItemOrderProductBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: OrderProduct) {
            val product = item.productable
            binding.tvProductName.text = product?.name?.ifBlank { "—" } ?: "—"
            val ref = product?.reference?.takeIf { it.isNotBlank() } ?: product?.idErp ?: "—"
            binding.tvProductReference.text = binding.root.context.getString(R.string.order_product_reference, ref)
            binding.tvQuantity.text = binding.root.context.getString(R.string.order_product_quantity, item.productQuantity)
            binding.tvScanned.text = binding.root.context.getString(R.string.order_product_scanned, item.scannedQuantity)
        }
    }

    private class OrderProductDiffCallback : DiffUtil.ItemCallback<OrderProduct>() {
        override fun areItemsTheSame(old: OrderProduct, new: OrderProduct) = old.id == new.id
        override fun areContentsTheSame(old: OrderProduct, new: OrderProduct) = old == new
    }
}

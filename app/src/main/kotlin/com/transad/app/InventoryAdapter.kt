package com.transad.app

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.transad.app.api.Product
import com.transad.app.databinding.ItemInventoryBinding

class InventoryAdapter : ListAdapter<Product, InventoryAdapter.InventoryViewHolder>(InventoryDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InventoryViewHolder {
        val binding = ItemInventoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return InventoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: InventoryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class InventoryViewHolder(private val binding: ItemInventoryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(product: Product) {
            binding.tvReference.text = product.reference?.ifBlank { "—" } ?: "—"
            binding.tvName.text = product.name?.ifBlank { "Sin nombre" } ?: "Sin nombre"
            binding.tvStock.text = binding.root.context.getString(R.string.inventory_stock, product.stockQuantity)
            binding.tvCost.text = binding.root.context.getString(R.string.inventory_cost, product.cost ?: "0")
        }
    }

    private class InventoryDiffCallback : DiffUtil.ItemCallback<Product>() {
        override fun areItemsTheSame(old: Product, new: Product) = old.id == new.id
        override fun areContentsTheSame(old: Product, new: Product) = old == new
    }
}

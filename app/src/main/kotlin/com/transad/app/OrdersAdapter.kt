package com.transad.app

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.transad.app.api.Order
import com.transad.app.databinding.ItemOrderBinding

class OrdersAdapter(
    private val onOrderClick: (Order) -> Unit
) : ListAdapter<Order, OrdersAdapter.OrderViewHolder>(OrderDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderViewHolder {
        val binding = ItemOrderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return OrderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: OrderViewHolder, position: Int) {
        holder.bind(getItem(position), onOrderClick)
    }

    class OrderViewHolder(private val binding: ItemOrderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(order: Order, onOrderClick: (Order) -> Unit) {
            binding.root.setOnClickListener { onOrderClick(order) }
            binding.tvOrderReference.text = order.reference
            binding.tvOrderType.text = order.typeOrder
            binding.tvOrderObservations.text = order.getObservations().ifBlank { "—" }
            binding.tvOrderDate.text = formatDate(order.createdAt)
            binding.chipStatus.text = if (order.status) binding.root.context.getString(R.string.order_status_active)
                else binding.root.context.getString(R.string.order_status_inactive)
            binding.chipStatus.setChipBackgroundColorResource(
                if (order.status) R.color.status_active else R.color.status_inactive
            )
        }

        private fun formatDate(createdAt: String): String {
            if (createdAt.length < 16) return createdAt
            val datePart = createdAt.take(10).split("-").reversed().joinToString("/")
            val timePart = createdAt.take(16).drop(11)
            return "$datePart $timePart"
        }
    }

    private class OrderDiffCallback : DiffUtil.ItemCallback<Order>() {
        override fun areItemsTheSame(old: Order, new: Order) = old.id == new.id
        override fun areContentsTheSame(old: Order, new: Order) = old == new
    }
}

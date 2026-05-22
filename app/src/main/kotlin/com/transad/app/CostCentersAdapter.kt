package com.transad.app

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.transad.app.api.CostCenter
import com.transad.app.databinding.ItemCostCenterBinding

class CostCentersAdapter(
    private val onCostCenterClick: (CostCenter) -> Unit
) : ListAdapter<CostCenter, CostCentersAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCostCenterBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), onCostCenterClick)
    }

    class ViewHolder(private val binding: ItemCostCenterBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: CostCenter, onCostCenterClick: (CostCenter) -> Unit) {
            val ctx = binding.root.context
            binding.root.setOnClickListener { onCostCenterClick(item) }
            binding.tvLicensePlate.text = item.licensePlate?.trim().orEmpty().ifBlank { "—" }
            binding.tvReference.text = ctx.getString(
                R.string.inspection_reference,
                item.reference?.trim().orEmpty().ifBlank { "—" }
            )
            binding.tvDescription.text = item.description?.trim().orEmpty().ifBlank { "—" }
            binding.chipStatus.text = if (item.status) {
                ctx.getString(R.string.order_status_active)
            } else {
                ctx.getString(R.string.order_status_inactive)
            }
            binding.chipStatus.setChipBackgroundColorResource(
                if (item.status) R.color.status_active else R.color.status_inactive
            )
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<CostCenter>() {
        override fun areItemsTheSame(oldItem: CostCenter, newItem: CostCenter) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: CostCenter, newItem: CostCenter) = oldItem == newItem
    }
}

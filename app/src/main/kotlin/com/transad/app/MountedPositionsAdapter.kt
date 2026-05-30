package com.transad.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.transad.app.api.MountedPosition
import com.transad.app.databinding.ItemMountedPositionBinding

class MountedPositionsAdapter : ListAdapter<MountedPosition, MountedPositionsAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMountedPositionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(private val binding: ItemMountedPositionBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: MountedPosition) {
            val ctx = binding.root.context
            binding.tvPositionTitle.text = ctx.getString(R.string.inspection_detail_position_title, item.position)
            binding.chipOccupied.text = if (item.occupied) {
                ctx.getString(R.string.inspection_detail_occupied)
            } else {
                ctx.getString(R.string.inspection_detail_empty)
            }
            binding.chipOccupied.setChipBackgroundColorResource(
                if (item.occupied) R.color.status_active else R.color.status_inactive
            )

            val spareLabel = if (item.isSpare) " · ${ctx.getString(R.string.inspection_detail_spare)}" else ""
            binding.tvPositionMeta.text = ctx.getString(
                R.string.inspection_detail_position_meta,
                item.axisNumber,
                item.side.orEmpty(),
                item.axisType.orEmpty()
            ) + spareLabel

            if (!item.occupied || item.tire == null) {
                binding.layoutTireContent.isVisible = false
                binding.tvEmptySlot.isVisible = true
                return
            }

            binding.tvEmptySlot.isVisible = false
            binding.layoutTireContent.isVisible = true

            val tire = item.tire
            binding.tvTireCode.text = ctx.getString(
                R.string.inspection_detail_field_code,
                tire.code.orEmpty().ifBlank { "—" }
            )
            binding.tvTireReference.text = ctx.getString(
                R.string.inspection_detail_field_reference,
                tire.reference.orEmpty().ifBlank { "—" }
            )
            binding.tvTireBrand.text = ctx.getString(
                R.string.inspection_detail_field_brand,
                tire.brand.orEmpty().ifBlank { "—" }
            )
            binding.tvTireName.text = tire.name.orEmpty().ifBlank { "—" }
            binding.tvTireCondition.text = ctx.getString(
                R.string.inspection_detail_field_condition,
                tire.condition.orEmpty().ifBlank { "—" }
            )

            val tag = tire.pair
            if (tag != null) {
                binding.tvNoTag.isVisible = false
                binding.tvTagReference.isVisible = true
                binding.tvTagName.isVisible = true
                binding.tvTagCode.isVisible = !tag.isLabelEntity()
                if (!tag.isLabelEntity()) {
                    binding.tvTagCode.text = ctx.getString(
                        R.string.inspection_detail_field_code,
                        tag.code.orEmpty().ifBlank { "—" }
                    )
                }
                binding.tvTagReference.text = ctx.getString(
                    R.string.inspection_detail_field_reference,
                    tag.reference.orEmpty().ifBlank { "—" }
                )
                binding.tvTagName.text = tag.name.orEmpty().ifBlank { "—" }
            } else {
                binding.tvTagCode.isVisible = false
                binding.tvTagReference.isVisible = false
                binding.tvTagName.isVisible = false
                binding.tvNoTag.isVisible = true
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<MountedPosition>() {
        override fun areItemsTheSame(oldItem: MountedPosition, newItem: MountedPosition) =
            oldItem.slotId == newItem.slotId

        override fun areContentsTheSame(oldItem: MountedPosition, newItem: MountedPosition) =
            oldItem == newItem
    }
}

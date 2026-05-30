package com.transad.app

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.transad.app.databinding.ItemLabelingScanBinding

class LabelingScansAdapter(
    private val onEditRow: (LabelingScanRow) -> Unit
) : ListAdapter<LabelingScanRow, LabelingScansAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemLabelingScanBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding, onEditRow)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemLabelingScanBinding,
        private val onEditRow: (LabelingScanRow) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        private var boundRow: LabelingScanRow? = null

        init {
            binding.root.setOnClickListener { boundRow?.let(onEditRow) }
        }

        fun bind(row: LabelingScanRow) {
            boundRow = row
            val item = row.scan
            val ctx = binding.root.context
            binding.tvLabelCode.text = item.labelCode
            binding.tvTireMeta.text = ctx.getString(
                R.string.labeling_item_meta,
                item.tireCode,
                item.attributes.size
            )
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<LabelingScanRow>() {
        override fun areItemsTheSame(old: LabelingScanRow, new: LabelingScanRow) =
            old.stableId == new.stableId

        override fun areContentsTheSame(old: LabelingScanRow, new: LabelingScanRow) =
            old.scan == new.scan
    }
}

package com.transad.app

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.transad.app.api.RfidScanPayload
import com.transad.app.databinding.ItemRfidTagBinding

/** Fila estable para el RecyclerView. */
data class RfidScanRow(
    val stableId: Long,
    val scan: RfidScanPayload
)

class RfidTagsAdapter(
    private val onEditRow: (RfidScanRow) -> Unit
) : ListAdapter<RfidScanRow, RfidTagsAdapter.TagViewHolder>(TagDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TagViewHolder {
        val binding = ItemRfidTagBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TagViewHolder(binding, onEditRow)
    }

    override fun onBindViewHolder(holder: TagViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class TagViewHolder(
        private val binding: ItemRfidTagBinding,
        private val onEditRow: (RfidScanRow) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        private var boundRow: RfidScanRow? = null

        init {
            binding.root.setOnClickListener {
                boundRow?.let(onEditRow)
            }
        }

        fun bind(row: RfidScanRow) {
            boundRow = row
            val item = row.scan
            val ctx = binding.root.context
            binding.tvTagId.text = item.rfidCode
            val extras = buildList {
                item.observation?.takeIf { it.isNotBlank() }?.let {
                    add(ctx.getString(R.string.rfid_item_obs_prefix, it))
                }
            }
            val base = ctx.getString(
                R.string.rfid_item_line2,
                item.licensePlate,
                item.tireCode,
                item.position
            )
            binding.tvTagMeta.text =
                if (extras.isEmpty()) base else base + "\n" + extras.joinToString(" · ")
        }
    }

    private class TagDiffCallback : DiffUtil.ItemCallback<RfidScanRow>() {
        override fun areItemsTheSame(old: RfidScanRow, new: RfidScanRow) = old.stableId == new.stableId

        override fun areContentsTheSame(old: RfidScanRow, new: RfidScanRow) = old.scan == new.scan
    }
}

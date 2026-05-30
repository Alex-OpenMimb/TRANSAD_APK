package com.transad.app

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.transad.app.api.ApiLogEntry
import com.transad.app.databinding.ItemApiLogEntryBinding
import com.transad.app.databinding.ItemApiLogSectionBinding

sealed class ApiLogListItem {
    data class SectionHeader(
        val section: String,
        val count: Int,
        val errorCount: Int
    ) : ApiLogListItem()

    data class Entry(val entry: ApiLogEntry, var expanded: Boolean = false) : ApiLogListItem()
}

class ApiLogsAdapter : ListAdapter<ApiLogListItem, RecyclerView.ViewHolder>(DiffCallback()) {

    var onEntryClick: ((ApiLogEntry) -> Unit)? = null

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is ApiLogListItem.SectionHeader -> VIEW_SECTION
        is ApiLogListItem.Entry -> VIEW_ENTRY
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_SECTION -> SectionViewHolder(
                ItemApiLogSectionBinding.inflate(inflater, parent, false)
            )
            else -> EntryViewHolder(
                ItemApiLogEntryBinding.inflate(inflater, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is ApiLogListItem.SectionHeader -> (holder as SectionViewHolder).bind(item)
            is ApiLogListItem.Entry -> (holder as EntryViewHolder).bind(item)
        }
    }

    inner class SectionViewHolder(
        private val binding: ItemApiLogSectionBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ApiLogListItem.SectionHeader) {
            val ctx = binding.root.context
            binding.tvSectionTitle.text = item.section
            binding.chipSectionCount.text = item.count.toString()
            binding.chipSectionErrors.isVisible = item.errorCount > 0
            if (item.errorCount > 0) {
                binding.chipSectionErrors.text = ctx.resources.getQuantityString(
                    R.plurals.api_logs_error_count,
                    item.errorCount,
                    item.errorCount
                )
            }
        }
    }

    inner class EntryViewHolder(
        private val binding: ItemApiLogEntryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ApiLogListItem.Entry) {
            val entry = item.entry
            val ctx = binding.root.context

            binding.chipMethod.text = entry.method
            binding.tvPath.text = entry.path.ifBlank { entry.url }
            binding.chipStatus.text = entry.statusLabel()
            binding.tvTime.text = entry.formattedTime()
            binding.tvDuration.text = ctx.getString(R.string.api_logs_duration_ms, entry.durationMs)

            val statusColor = when {
                entry.isError() -> R.color.status_inactive
                entry.statusCode != null && entry.statusCode in 200..299 -> R.color.status_active
                entry.statusCode != null -> R.color.status_inactive
                else -> R.color.transad_primary
            }
            binding.chipStatus.setChipBackgroundColorResource(statusColor)

            binding.tvError.isVisible = !entry.errorMessage.isNullOrBlank()
            binding.tvError.text = entry.errorMessage

            val showBody = entry.isError() && !entry.requestBody.isNullOrBlank()
            binding.tvRequestBodyLabel.isVisible = showBody
            binding.tvRequestBody.isVisible = showBody
            binding.tvRequestBody.text = entry.requestBody

            binding.tvUrl.isVisible = item.expanded
            binding.tvUrl.text = entry.url

            binding.root.setOnClickListener {
                onEntryClick?.invoke(entry)
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<ApiLogListItem>() {
        override fun areItemsTheSame(old: ApiLogListItem, new: ApiLogListItem): Boolean {
            return when {
                old is ApiLogListItem.SectionHeader && new is ApiLogListItem.SectionHeader ->
                    old.section == new.section
                old is ApiLogListItem.Entry && new is ApiLogListItem.Entry ->
                    old.entry.id == new.entry.id
                else -> false
            }
        }

        override fun areContentsTheSame(old: ApiLogListItem, new: ApiLogListItem): Boolean = old == new
    }

    companion object {
        private const val VIEW_SECTION = 0
        private const val VIEW_ENTRY = 1
    }
}

package com.transad.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.transad.app.api.ApiLogFile
import com.transad.app.databinding.ActivityApiLogsBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Pantalla de desarrollo: tráfico HTTP agrupado por sección. */
class ApiLogsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityApiLogsBinding
    private lateinit var adapter: ApiLogsAdapter

    private var allGroups: List<com.transad.app.api.ApiLogSectionGroup> = emptyList()
    private var selectedSection: String? = null
    private val expandedEntryIds = mutableSetOf<Long>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        binding = ActivityApiLogsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        adapter = ApiLogsAdapter()
        adapter.onEntryClick = { entry -> toggleExpanded(entry) }
        binding.recyclerApiLogs.layoutManager = LinearLayoutManager(this)
        binding.recyclerApiLogs.adapter = adapter

        binding.tvLogPath.text = getString(R.string.api_logs_path, ApiLogFile.absolutePath())
        binding.btnRefreshLogs.setOnClickListener { refreshLogs() }
        binding.btnClearLogs.setOnClickListener {
            ApiLogFile.clear()
            expandedEntryIds.clear()
            refreshLogs()
        }
        binding.btnShareLogs.setOnClickListener { shareLogs() }

        refreshLogs()
    }

    override fun onResume() {
        super.onResume()
        refreshLogs()
    }

    private fun refreshLogs() {
        val entries = ApiLogFile.readEntries()
        allGroups = ApiLogFile.readGroupedSections()
        bindStats(entries)
        bindSectionChips()
        bindList()
    }

    private fun bindStats(entries: List<com.transad.app.api.ApiLogEntry>) {
        val stats = ApiLogFile.stats(entries)
        binding.tvStatTotal.text = getString(R.string.api_logs_stat_total, stats.total)
        binding.tvStatErrors.text = getString(R.string.api_logs_stat_errors, stats.errors)
        binding.tvStatAvg.text = getString(R.string.api_logs_stat_avg, stats.avgDurationMs)
        binding.tvStatLast.text = stats.lastTimestamp?.let { ts ->
            getString(
                R.string.api_logs_stat_last,
                SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(ts))
            )
        } ?: getString(R.string.api_logs_stat_last, "—")
    }

    private fun bindSectionChips() {
        binding.chipGroupSections.removeAllViews()

        addSectionChip(getString(R.string.api_logs_filter_all), null, selectedSection == null)

        allGroups.forEach { group ->
            addSectionChip(group.section, group.section, selectedSection == group.section)
        }

        binding.chipGroupSections.setOnCheckedStateChangeListener { group, _ ->
            val checkedId = group.checkedChipId
            if (checkedId == View.NO_ID) return@setOnCheckedStateChangeListener
            val chip = group.findViewById<Chip>(checkedId) ?: return@setOnCheckedStateChangeListener
            selectedSection = chip.tag as? String
            bindList()
        }
    }

    private fun addSectionChip(label: String, sectionTag: String?, checked: Boolean) {
        val chip = Chip(this).apply {
            text = label
            tag = sectionTag
            isCheckable = true
            isChecked = checked
        }
        binding.chipGroupSections.addView(chip)
    }

    private fun bindList() {
        val groups = if (selectedSection == null) {
            allGroups
        } else {
            allGroups.filter { it.section == selectedSection }
        }

        val items = mutableListOf<ApiLogListItem>()
        groups.forEach { group ->
            if (selectedSection == null) {
                items += ApiLogListItem.SectionHeader(
                    section = group.section,
                    count = group.entries.size,
                    errorCount = group.errorCount
                )
            }
            group.entries.forEach { entry ->
                items += ApiLogListItem.Entry(
                    entry = entry,
                    expanded = expandedEntryIds.contains(entry.id)
                )
            }
        }

        adapter.submitList(items)
        val empty = items.isEmpty()
        binding.recyclerApiLogs.isVisible = !empty
        binding.tvEmptyLogs.isVisible = empty
    }

    private fun toggleExpanded(entry: com.transad.app.api.ApiLogEntry) {
        if (expandedEntryIds.contains(entry.id)) {
            expandedEntryIds.remove(entry.id)
        } else {
            expandedEntryIds.add(entry.id)
        }
        bindList()
    }

    private fun shareLogs() {
        val entries = if (selectedSection == null) {
            ApiLogFile.readEntries()
        } else {
            ApiLogFile.readEntries().filter { it.section == selectedSection }
        }
        val text = ApiLogFile.formatExport(entries)
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, getString(R.string.api_logs_title))
                    putExtra(Intent.EXTRA_TEXT, text)
                },
                getString(R.string.api_logs_share)
            )
        )
    }
}

package com.transad.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.transad.app.api.ApiLogFile
import com.transad.app.databinding.ActivityApiLogsBinding

/** Pantalla de desarrollo: ver el archivo de logs de llamadas HTTP. */
class ApiLogsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityApiLogsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        binding = ActivityApiLogsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        binding.tvLogPath.text = getString(R.string.api_logs_path, ApiLogFile.absolutePath())

        binding.btnRefreshLogs.setOnClickListener { refreshLogContent() }
        binding.btnClearLogs.setOnClickListener {
            ApiLogFile.clear()
            refreshLogContent()
        }
        binding.btnShareLogs.setOnClickListener {
            val text = ApiLogFile.readAll()
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

        refreshLogContent()
    }

    private fun refreshLogContent() {
        binding.tvLogContent.text = ApiLogFile.readAll()
    }
}

package com.transad.app

import android.app.Activity
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.transad.app.api.AppVersionInfo

/**
 * Descarga el .apk de una actualización con [DownloadManager] (maneja red, reintentos y la
 * notificación de progreso por sí solo) y, al terminar, lanza el instalador del sistema con el
 * content:// URI que el propio DownloadManager expone — no requiere FileProvider propio.
 */
class ApkUpdateInstaller(private val activity: Activity) {

    private var pendingDownloadId: Long = -1L
    private var receiver: BroadcastReceiver? = null

    fun startDownload(version: AppVersionInfo) {
        val downloadManager = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(version.downloadUrl))
            .setTitle(activity.getString(R.string.app_update_notification_title))
            .setDescription(version.versionName)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(activity, Environment.DIRECTORY_DOWNLOADS, "transad-update.apk")
            .setMimeType("application/vnd.android.package-archive")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        pendingDownloadId = downloadManager.enqueue(request)
        registerReceiver(downloadManager)
    }

    private fun registerReceiver(downloadManager: DownloadManager) {
        val br = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (id != pendingDownloadId) return
                unregister()
                installDownloadedApk(downloadManager, id)
            }
        }
        receiver = br
        ContextCompat.registerReceiver(
            activity,
            br,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    private fun installDownloadedApk(downloadManager: DownloadManager, downloadId: Long) {
        val uri = try {
            downloadManager.getUriForDownloadedFile(downloadId)
        } catch (_: Exception) {
            null
        }
        if (uri == null) {
            Toast.makeText(activity, R.string.app_update_download_error, Toast.LENGTH_LONG).show()
            return
        }
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        activity.startActivity(installIntent)
    }

    fun unregister() {
        receiver?.let {
            try {
                activity.unregisterReceiver(it)
            } catch (_: Exception) {
            }
            receiver = null
        }
    }
}

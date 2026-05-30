package com.transad.app

import android.content.Context
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.transad.app.api.ApiErrors
import retrofit2.Response

/**
 * Muestra errores de API en un diálogo con scroll (texto completo, visible hasta que el usuario cierre).
 */
object ApiErrorUi {

    fun show(context: Context, message: CharSequence, title: String? = null) {
        val activity = context as? AppCompatActivity ?: return
        if (activity.isFinishing || activity.isDestroyed) return

        activity.runOnUiThread {
            if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread

            val view = activity.layoutInflater.inflate(R.layout.dialog_api_error, null)
            view.findViewById<TextView>(R.id.tvApiErrorMessage).text = message

            MaterialAlertDialogBuilder(activity)
                .setTitle(title ?: activity.getString(R.string.api_error_title))
                .setView(view)
                .setPositiveButton(R.string.api_error_close, null)
                .setCancelable(true)
                .show()
        }
    }

    fun showHttpError(context: Context, defaultMessage: String, response: Response<*>, title: String? = null) {
        show(context, ApiErrors.formatHttpError(response, defaultMessage), title)
    }

    fun showNetworkError(context: Context, defaultMessage: String, error: Throwable?, title: String? = null) {
        show(context, ApiErrors.formatNetworkError(defaultMessage, error), title)
    }
}

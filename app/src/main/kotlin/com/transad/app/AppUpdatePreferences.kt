package com.transad.app

import android.content.Context

/** Recuerda qué `version_code` descartó el usuario con "Después" (no aplica a actualizaciones obligatorias). */
class AppUpdatePreferences(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isDismissed(versionCode: Int): Boolean = prefs.getInt(KEY_DISMISSED_VERSION_CODE, 0) >= versionCode

    fun dismiss(versionCode: Int) {
        prefs.edit().putInt(KEY_DISMISSED_VERSION_CODE, versionCode).apply()
    }

    companion object {
        private const val PREFS_NAME = "transad_app_update"
        private const val KEY_DISMISSED_VERSION_CODE = "dismissed_version_code"
    }
}

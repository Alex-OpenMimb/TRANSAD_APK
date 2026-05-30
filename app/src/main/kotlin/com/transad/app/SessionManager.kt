package com.transad.app

import android.content.Context
import android.content.SharedPreferences

/**
 * Guarda y lee el token de sesión (access_token) para usarlo en las peticiones autenticadas.
 */
class SessionManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveSession(accessToken: String, expiresIn: Long, apiBaseUrl: String, username: String) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putLong(KEY_EXPIRES_AT, if (expiresIn > 0) System.currentTimeMillis() + (expiresIn * 1000) else 0L)
            .putString(KEY_API_BASE_URL, normalizeBaseUrl(apiBaseUrl))
            .putString(KEY_USERNAME, username.trim())
            .apply()
    }

    fun saveUserId(userId: Int) {
        if (userId > 0) {
            prefs.edit().putInt(KEY_USER_ID, userId).apply()
        }
    }

    fun getUserId(): Int = prefs.getInt(KEY_USER_ID, 0)

    fun getUsername(): String = prefs.getString(KEY_USERNAME, null).orEmpty()

    fun updateUsername(username: String) {
        prefs.edit().putString(KEY_USERNAME, username.trim()).apply()
    }

    fun getToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)

    fun isLoggedIn(): Boolean = !getToken().isNullOrBlank()

    /** Token guardado para otro servidor (p. ej. LAN vs staging). */
    fun isSessionForBaseUrl(currentBaseUrl: String): Boolean {
        val saved = prefs.getString(KEY_API_BASE_URL, null) ?: return false
        return saved == normalizeBaseUrl(currentBaseUrl)
    }

    /**
     * Opcional: devuelve true si el token podría estar expirado según expires_in.
     * La API puede seguir rechazando con 401 aunque no hayamos llegado al tiempo.
     */
    fun isTokenExpired(): Boolean {
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)
        if (expiresAt == 0L) return false
        return System.currentTimeMillis() >= expiresAt
    }

    fun logout() {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_EXPIRES_AT)
            .remove(KEY_API_BASE_URL)
            .remove(KEY_USERNAME)
            .remove(KEY_USER_ID)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "transad_session"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_EXPIRES_AT = "expires_at"
        private const val KEY_API_BASE_URL = "api_base_url"
        private const val KEY_USERNAME = "username"
        private const val KEY_USER_ID = "user_id"

        private fun normalizeBaseUrl(url: String) = url.trimEnd('/') + "/"
    }
}

package com.transad.app

import android.content.Context
import android.content.SharedPreferences

/**
 * Guarda y lee el token de sesión (access_token) para usarlo en las peticiones autenticadas.
 */
class SessionManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveToken(accessToken: String, expiresIn: Long = 0) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putLong(KEY_EXPIRES_AT, if (expiresIn > 0) System.currentTimeMillis() + (expiresIn * 1000) else 0L)
            .apply()
    }

    fun getToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)

    fun isLoggedIn(): Boolean = !getToken().isNullOrBlank()

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
        prefs.edit().remove(KEY_ACCESS_TOKEN).remove(KEY_EXPIRES_AT).apply()
    }

    companion object {
        private const val PREFS_NAME = "transad_session"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_EXPIRES_AT = "expires_at"
    }
}

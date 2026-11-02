package com.example.helloworld.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.helloworld.BuildConfig

object SettingsRepository {
    private const val PREFS_NAME = "yinhe_settings"
    private const val KEY_SERVER_API_BASE = "server_api_base"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun defaultBaseUrl(): String = BuildConfig.SERVER_API_BASE

    fun baseUrl(): String {
        val saved = if (::prefs.isInitialized) {
            prefs.getString(KEY_SERVER_API_BASE, null)
        } else {
            null
        }
        return saved?.takeIf { it.isNotBlank() } ?: BuildConfig.SERVER_API_BASE
    }

    fun origin(): String {
        val base = baseUrl().trim().removeSuffix("/")
        val withSlash = if (base.endsWith("/api", ignoreCase = true)) {
            base.removeSuffix("/api")
        } else {
            base
        }
        return "$withSlash/"
    }

    fun saveBaseUrl(url: String) {
        val normalized = url.trim().let { if (it.endsWith("/")) it else "$it/" }
        prefs.edit().putString(KEY_SERVER_API_BASE, normalized).apply()
    }

    fun resetBaseUrl() {
        prefs.edit().remove(KEY_SERVER_API_BASE).apply()
    }
}

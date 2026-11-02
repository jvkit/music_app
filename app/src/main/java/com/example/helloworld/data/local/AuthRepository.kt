package com.example.helloworld.data.local

import android.content.Context
import android.content.SharedPreferences

object AuthRepository {
    private const val PREFS_NAME = "yinhe_auth"
    private const val KEY_TOKEN = "token"
    private const val KEY_USERNAME = "username"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun token(): String? =
        if (::prefs.isInitialized) prefs.getString(KEY_TOKEN, null) else null

    fun username(): String? =
        if (::prefs.isInitialized) prefs.getString(KEY_USERNAME, null) else null

    fun save(token: String, username: String) {
        prefs.edit().putString(KEY_TOKEN, token).putString(KEY_USERNAME, username).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_TOKEN).remove(KEY_USERNAME).apply()
    }
}

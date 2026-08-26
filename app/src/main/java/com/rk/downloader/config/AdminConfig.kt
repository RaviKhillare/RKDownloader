package com.rk.downloader.config

import android.content.Context

object AdminConfig {
    private const val PREFS_NAME = "rk_admin_prefs"
    private const val KEY_SERVER_URL = "server_url"
    
    // Default fallback address
    private const val DEFAULT_URL = "http://10.31.60.251/rk_tracker"

    fun getServerUrl(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_SERVER_URL, DEFAULT_URL) ?: DEFAULT_URL
    }

    fun setServerUrl(context: Context, url: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SERVER_URL, url.trim().trimEnd('/')).apply()
    }

    const val ADMIN_SECRET_KEY = "admin123"
}
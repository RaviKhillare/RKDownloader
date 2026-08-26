package com.rk.downloader.config

import android.content.Context

object AdminConfig {
    // Cloud Supabase Connection Details
    const val SUPABASE_URL = "https://qwdvujdgkdzzmcxfdcub.supabase.co"
    const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InF3ZHZ1amRna2R6em1jeGZkY3ViIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODc3NDI5MDcsImV4cCI6MjEwMzMxODkwN30.xZCaxY7TxoC0YV-i_8-4EAd4lBqH2hlVVLkh3-HAtcI"
    const val ADMIN_SECRET_KEY = "sb_secret_KzrCFBV30m1jkEk0I3sMAg_JkunNnHK"

    private const val PREFS_NAME = "rk_admin_prefs"
    private const val KEY_EXTRACTOR_URL = "extractor_url"
    
    // Default public Cobalt mirror (V10 compatible)
    private const val DEFAULT_EXTRACTOR_URL = "https://cobalt.api.red.gd"

    fun getExtractorUrl(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_EXTRACTOR_URL, DEFAULT_EXTRACTOR_URL) ?: DEFAULT_EXTRACTOR_URL
    }

    fun setExtractorUrl(context: Context, url: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_EXTRACTOR_URL, url.trim().trimEnd('/')).apply()
    }
}
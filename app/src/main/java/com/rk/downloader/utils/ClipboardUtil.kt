package com.rk.downloader.utils

import android.content.ClipboardManager
import android.content.Context
import android.util.Patterns

object ClipboardUtil {
    /**
     * Reads clipboard text and returns it if it's a valid URL.
     */
    fun getCopiedUrl(context: Context): String? {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (clipboard != null && clipboard.hasPrimaryClip()) {
            val clipData = clipboard.primaryClip
            if (clipData != null && clipData.itemCount > 0) {
                val item = clipData.getItemAt(0)
                val text = item.text?.toString()?.trim() ?: ""
                if (text.isNotEmpty() && isValidUrl(text)) {
                    return text
                }
            }
        }
        return null
    }

    private fun isValidUrl(url: String): Boolean {
        return Patterns.WEB_URL.matcher(url).matches() && 
               (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true))
    }

    /**
     * Checks if the URL is from a supported social media domain.
     */
    fun isSocialMediaUrl(url: String): Boolean {
        val lowerUrl = url.lowercase()
        return lowerUrl.contains("instagram.com") ||
               lowerUrl.contains("facebook.com") ||
               lowerUrl.contains("fb.watch") ||
               lowerUrl.contains("tiktok.com") ||
               lowerUrl.contains("twitter.com") ||
               lowerUrl.contains("x.com") ||
               lowerUrl.contains("youtube.com") ||
               lowerUrl.contains("youtu.be")
    }
}

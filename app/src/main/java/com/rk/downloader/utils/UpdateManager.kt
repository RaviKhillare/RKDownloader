package com.rk.downloader.utils

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class UpdateInfo(
    val latestVersionName: String,
    val latestVersionCode: Long,
    val updateUrl: String,
    val releaseNotes: String,
    val isForceUpdate: Boolean
)

object UpdateManager {
    private const val TAG = "UpdateManager"
    
    // Remote JSON location on GitHub repository
    private const val UPDATE_JSON_URL = "https://raw.githubusercontent.com/RaviKhillare/RKDownloader/main/update.json"
    
    private val client = OkHttpClient()

    /**
     * Connects to GitHub, parses update.json, and checks if a newer version exists.
     */
    suspend fun checkForUpdates(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val packageManager = context.packageManager
            val packageInfo = packageManager.getPackageInfo(context.packageName, 0)
            val currentVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }

            val request = Request.Builder()
                .url(UPDATE_JSON_URL)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val jsonStr = response.body?.string() ?: return@withContext null
                
                val jsonObj = JSONObject(jsonStr)
                val latestVersionCode = jsonObj.optLong("latestVersionCode", 0)
                
                if (latestVersionCode > currentVersionCode) {
                    return@withContext UpdateInfo(
                        latestVersionName = jsonObj.optString("latestVersionName", "1.0"),
                        latestVersionCode = latestVersionCode,
                        updateUrl = jsonObj.optString("updateUrl", "https://github.com/RaviKhillare/RKDownloader/releases"),
                        releaseNotes = jsonObj.optString("releaseNotes", ""),
                        isForceUpdate = jsonObj.optBoolean("isForceUpdate", false)
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Update check request failed: ${e.message}")
        }
        return@withContext null
    }
}

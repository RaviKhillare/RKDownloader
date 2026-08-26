package com.rk.downloader.utils

import android.content.Context
import android.os.Build
import android.util.Log
import com.rk.downloader.config.AdminConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID

object TrackerManager {
    private const val TAG = "TrackerManager"
    private const val PREFS_NAME = "rk_tracker_prefs"
    private const val KEY_DEVICE_UUID = "device_uuid"
    private const val KEY_INSTALL_TIME = "install_time"
    
    private val client = OkHttpClient()

    fun getDeviceUuid(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var uuid = prefs.getString(KEY_DEVICE_UUID, null)
        if (uuid == null) {
            uuid = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_UUID, uuid).apply()
        }
        return uuid
    }

    private fun getInstallTime(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var installTime = prefs.getLong(KEY_INSTALL_TIME, 0L)
        if (installTime == 0L) {
            installTime = System.currentTimeMillis()
            prefs.edit().putLong(KEY_INSTALL_TIME, installTime).apply()
        }
        return installTime
    }

    suspend fun registerDevice(context: Context) = withContext(Dispatchers.IO) {
        val trackerUrl = AdminConfig.getServerUrl(context)
        if (trackerUrl.isEmpty()) {
            return@withContext
        }

        try {
            val uuid = getDeviceUuid(context)
            val installTime = getInstallTime(context)
            val currentTime = System.currentTimeMillis()
            val model = Build.MODEL ?: "Unknown Device"
            val osVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

            val postData = JSONObject().apply {
                put("uuid", uuid)
                put("model", model)
                put("os", osVersion)
                put("install_time", installTime)
                put("last_active", currentTime)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = postData.toString().toRequestBody(mediaType)
            val request = Request.Builder()
                .url("$trackerUrl/track.php")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Log.d(TAG, "Device registered/pinged server successfully.")
                } else {
                    Log.e(TAG, "Failed to ping server. Code: ${response.code}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Tracking failed: ${e.message}", e)
        }
    }
}
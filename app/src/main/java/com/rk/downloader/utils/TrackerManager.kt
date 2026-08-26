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
    private const val KEY_FIRST_RUN = "first_run_logged"
    
    private val client = OkHttpClient()

    /**
     * Generates or fetches the unique UUID of this device.
     */
    fun getDeviceUuid(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var uuid = prefs.getString(KEY_DEVICE_UUID, null)
        if (uuid == null) {
            uuid = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_UUID, uuid).apply()
        }
        return uuid
    }

    /**
     * Registers and logs application start to Firebase database endpoint using REST API.
     */
    suspend fun registerDevice(context: Context) = withContext(Dispatchers.IO) {
        val databaseUrl = AdminConfig.FIREBASE_DATABASE_URL
        if (databaseUrl.isEmpty() || databaseUrl.contains("default-rtdb")) {
            Log.d(TAG, "Firebase URL is not configured. Skipping analytics ping.")
            return@withContext
        }

        try {
            val uuid = getDeviceUuid(context)
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val isFirstRun = !prefs.getBoolean(KEY_FIRST_RUN, false)

            val model = Build.MODEL ?: "Unknown Device"
            val osVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
            val currentTime = System.currentTimeMillis()

            // Prepare device database JSON payload
            val deviceData = JSONObject().apply {
                put("model", model)
                put("os", osVersion)
                put("lastActive", currentTime)
                if (isFirstRun) {
                    put("installTime", currentTime)
                }
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = deviceData.toString().toRequestBody(mediaType)
            val url = "$databaseUrl/devices/$uuid.json"

            val request = Request.Builder()
                .url(url)
                .put(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Log.d(TAG, "Device details synchronized with database.")
                    if (isFirstRun) {
                        prefs.edit().putBoolean(KEY_FIRST_RUN, true).apply()
                    }
                } else {
                    Log.e(TAG, "Database sync failed. Code: ${response.code}")
                }
                Unit
            }

            // Also record a launch event log
            val launchData = JSONObject().apply {
                put("timestamp", currentTime)
                put("model", model)
            }
            val launchUrl = "$databaseUrl/launches/$uuid.json"
            val launchRequest = Request.Builder()
                .url(launchUrl)
                .put(launchData.toString().toRequestBody(mediaType))
                .build()
            
            client.newCall(launchRequest).execute().close()

        } catch (e: Exception) {
            Log.e(TAG, "Device tracking operation failed: ${e.message}", e)
        }
    }
}

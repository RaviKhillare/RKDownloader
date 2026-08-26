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
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object TrackerManager {
    private const val TAG = "TrackerManager"
    private const val PREFS_NAME = "rk_tracker_prefs"
    private const val KEY_DEVICE_UUID = "device_uuid"
    
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

    suspend fun registerDevice(context: Context) = withContext(Dispatchers.IO) {
        val supabaseUrl = AdminConfig.SUPABASE_URL
        val anonKey = AdminConfig.SUPABASE_ANON_KEY
        if (supabaseUrl.isEmpty() || supabaseUrl.contains("yourproject") || anonKey.isEmpty()) {
            Log.w(TAG, "Supabase details not fully configured in AdminConfig.kt.")
            return@withContext
        }

        try {
            val uuid = getDeviceUuid(context)
            val model = Build.MODEL ?: "Unknown Device"
            val osVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
            val currentTime = System.currentTimeMillis()

            val mediaType = "application/json; charset=utf-8".toMediaType()

            // 1. Check if the device is already registered in Supabase
            val checkUrl = "$supabaseUrl/rest/v1/devices?uuid=eq.$uuid"
            val checkRequest = Request.Builder()
                .url(checkUrl)
                .header("apikey", anonKey)
                .header("Authorization", "Bearer $anonKey")
                .build()

            var exists = false
            client.newCall(checkRequest).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: "[]"
                    val jsonArray = JSONArray(body)
                    exists = jsonArray.length() > 0
                }
            }

            if (exists) {
                // 2. Device exists: Perform PATCH request to update last_active timestamp
                val patchData = JSONObject().apply {
                    put("last_active", currentTime)
                }
                val patchUrl = "$supabaseUrl/rest/v1/devices?uuid=eq.$uuid"
                val patchRequest = Request.Builder()
                    .url(patchUrl)
                    .header("apikey", anonKey)
                    .header("Authorization", "Bearer $anonKey")
                    .header("Content-Type", "application/json")
                    .patch(patchData.toString().toRequestBody(mediaType))
                    .build()

                client.newCall(patchRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        Log.d(TAG, "Device activity synchronized with Supabase.")
                    } else {
                        Log.e(TAG, "Failed to patch device. Code: ${response.code}")
                    }
                }
            } else {
                // 3. New Device: Perform POST request to insert new installation record
                val postData = JSONObject().apply {
                    put("uuid", uuid)
                    put("model", model)
                    put("os", osVersion)
                    put("install_time", currentTime)
                    put("last_active", currentTime)
                }
                val postUrl = "$supabaseUrl/rest/v1/devices"
                val postRequest = Request.Builder()
                    .url(postUrl)
                    .header("apikey", anonKey)
                    .header("Authorization", "Bearer $anonKey")
                    .header("Content-Type", "application/json")
                    .post(postData.toString().toRequestBody(mediaType))
                    .build()

                client.newCall(postRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        Log.d(TAG, "New device registered in Supabase cloud.")
                    } else {
                        Log.e(TAG, "Failed to register device. Code: ${response.code}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Supabase tracking operation failed: ${e.message}", e)
        }
    }
}
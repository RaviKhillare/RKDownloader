package com.rk.downloader.utils

import android.content.Context
import android.util.Log
import com.rk.downloader.config.AdminConfig
import com.rk.downloader.data.DownloadOption
import com.rk.downloader.data.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object VideoExtractor {
    private const val TAG = "VideoExtractor"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Queries the local XAMPP extract.php API to extract direct media streams using yt-dlp.
     */
    suspend fun extractVideo(context: Context, url: String): VideoInfo? = withContext(Dispatchers.IO) {
        val trackerUrl = AdminConfig.getServerUrl(context)
        if (trackerUrl.isEmpty()) {
            Log.e(TAG, "Local XAMPP server URL is not configured.")
            return@withContext null
        }

        try {
            val encodedUrl = URLEncoder.encode(url, "UTF-8")
            val requestUrl = "$trackerUrl/extract.php?url=$encodedUrl"

            val request = Request.Builder()
                .url(requestUrl)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: return@use null
                    val jsonObj = JSONObject(bodyStr)
                    val status = jsonObj.optString("status")
                    
                    if (status == "success") {
                        val title = jsonObj.optString("title", "Social Media Video")
                        val source = jsonObj.optString("sourceUrl", url)
                        val optionsArray = jsonObj.optJSONArray("options") ?: return@use null
                        
                        val options = mutableListOf<DownloadOption>()
                        for (i in 0 until optionsArray.length()) {
                            val optObj = optionsArray.getJSONObject(i)
                            val downloadUrl = optObj.optString("downloadUrl", "")
                            if (downloadUrl.isNotEmpty()) {
                                options.add(
                                    DownloadOption(
                                        quality = optObj.optString("quality", "Standard Quality"),
                                        format = optObj.optString("format", "MP4"),
                                        downloadUrl = downloadUrl
                                    )
                                )
                            }
                        }
                        
                        if (options.isNotEmpty()) {
                            return@withContext VideoInfo(title = title, sourceUrl = source, options = options)
                        }
                    } else {
                        val message = jsonObj.optString("message", "Extraction failed")
                        Log.e(TAG, "Extraction failed on local server: $message")
                    }
                } else {
                    Log.e(TAG, "HTTP error from XAMPP extraction: ${response.code}")
                }
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "XAMPP local extraction query failed: ${e.message}", e)
        }
        return@withContext null
    }
}

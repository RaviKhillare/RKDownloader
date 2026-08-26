package com.rk.downloader.utils

import android.content.Context
import android.util.Log
import com.rk.downloader.config.AdminConfig
import com.rk.downloader.data.DownloadOption
import com.rk.downloader.data.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object VideoExtractor {
    private const val TAG = "VideoExtractor"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun extractVideo(context: Context, url: String): VideoInfo? = withContext(Dispatchers.IO) {
        val extractorUrl = AdminConfig.getExtractorUrl(context)
        if (extractorUrl.isEmpty()) {
            Log.e(TAG, "Cobalt Extractor URL is not configured.")
            return@withContext null
        }

        // Try direct POST to root endpoint (Cobalt v10 syntax)
        val result = tryExtractor(extractorUrl, url)
        if (result != null) return@withContext result

        // Fallback to POST /api/json (Cobalt v7 syntax)
        val fallbackUrl = if (extractorUrl.endsWith("/")) "${extractorUrl}api/json" else "$extractorUrl/api/json"
        return@withContext tryExtractor(fallbackUrl, url)
    }

    private fun tryExtractor(apiUrl: String, videoUrl: String): VideoInfo? {
        try {
            val postData = JSONObject().apply {
                put("url", videoUrl)
                put("videoQuality", "720")
                put("downloadMode", "auto")
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = postData.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(apiUrl)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                // Sending a standard Chrome User-Agent prevents Cloudflare blocks on public instances
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: return null
                    val jsonObj = JSONObject(bodyStr)
                    val status = jsonObj.optString("status")

                    // 1. Single file stream output (Cobalt v10 / v7)
                    if (status == "stream" || status == "redirect") {
                        val downloadUrl = jsonObj.optString("url")
                        val filename = jsonObj.optString("filename", "Social Video")
                        if (downloadUrl.isNotEmpty()) {
                            val options = listOf(
                                DownloadOption(
                                    quality = "Video MP4 (Auto)",
                                    format = "MP4",
                                    downloadUrl = downloadUrl
                                )
                            )
                            return VideoInfo(title = filename, sourceUrl = videoUrl, options = options)
                        }
                    } 
                    // 2. Picker array output (Combined and separate streams)
                    else if (status == "picker") {
                        val pickerArray = jsonObj.optJSONArray("picker")
                        val options = mutableListOf<DownloadOption>()
                        if (pickerArray != null) {
                            for (i in 0 until pickerArray.length()) {
                                val item = pickerArray.getJSONObject(i)
                                val downloadUrl = item.optString("url")
                                if (downloadUrl.isNotEmpty()) {
                                    val type = item.optString("type", "video")
                                    val quality = item.optString("quality", "Auto")
                                    options.add(
                                        DownloadOption(
                                            quality = if (type == "audio") "Audio Only (MP3)" else "Video MP4 ($quality)",
                                            format = if (type == "audio") "MP3" else "MP4",
                                            downloadUrl = downloadUrl
                                        )
                                    )
                                }
                            }
                        }
                        if (options.isNotEmpty()) {
                            val title = jsonObj.optString("title", "Social Media Video")
                            return VideoInfo(title = title, sourceUrl = videoUrl, options = options)
                        }
                    }
                    // 3. Fallback for raw direct responses
                    else if (jsonObj.has("url")) {
                        val downloadUrl = jsonObj.optString("url")
                        if (downloadUrl.isNotEmpty()) {
                            val options = listOf(
                                DownloadOption(
                                    quality = "Video MP4 (Auto)",
                                    format = "MP4",
                                    downloadUrl = downloadUrl
                                )
                            )
                            return VideoInfo(title = "Social Media Video", sourceUrl = videoUrl, options = options)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed extraction query for $apiUrl: ${e.message}")
        }
        return null
    }
}

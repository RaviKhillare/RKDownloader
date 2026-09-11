package com.rk.downloader.utils

import android.content.Context
import android.util.Log
import com.rk.downloader.config.AdminConfig
import com.rk.downloader.data.ExtractionResult
import com.rk.downloader.data.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Service interface for media stream extraction implementations.
 */
interface ExtractorService {
    suspend fun extract(context: Context, url: String, platform: SupportedPlatform): ExtractionResult?
}

/**
 * Primary Layer: Free Public REST API (Cobalt API mirrors with user-configurable endpoints).
 */
class PrimaryRestExtractor : ExtractorService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    override suspend fun extract(context: Context, url: String, platform: SupportedPlatform): ExtractionResult? = withContext(Dispatchers.IO) {
        val configuredUrl = AdminConfig.getExtractorUrl(context)
        
        // Build an ordered list of viable public Cobalt REST API endpoints
        val endpoints = mutableListOf<String>()
        if (configuredUrl.isNotEmpty()) {
            endpoints.add(configuredUrl)
            val fallbackV7 = if (configuredUrl.endsWith("/")) "${configuredUrl}api/json" else "$configuredUrl/api/json"
            endpoints.add(fallbackV7)
        }
        endpoints.add("https://cobalt.api.red.gd")
        endpoints.add("https://api.cobalt.tools")

        for (endpoint in endpoints) {
            try {
                val postData = JSONObject().apply {
                    put("url", url)
                    put("videoQuality", "720")
                    put("downloadMode", "auto")
                }

                val mediaType = "application/json; charset=utf-8".toMediaType()
                val requestBody = postData.toString().toRequestBody(mediaType)

                val request = Request.Builder()
                    .url(endpoint)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    // Desktop Chrome User-Agent bypasses Cloudflare anti-bot checks on public mirrors
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    .post(requestBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyStr = response.body?.string() ?: return@use
                        val jsonObj = JSONObject(bodyStr)
                        val status = jsonObj.optString("status")

                        // 1. Single stream or redirect output
                        if (status == "stream" || status == "redirect") {
                            val downloadUrl = jsonObj.optString("url")
                            val filename = jsonObj.optString("filename", "${platform.name.lowercase()}_video.mp4")
                            if (downloadUrl.isNotEmpty()) {
                                return@withContext ExtractionResult(
                                    streamUrl = downloadUrl,
                                    title = filename.substringBeforeLast(".").ifEmpty { "${platform.displayName} Video" },
                                    quality = "720p HD",
                                    format = "MP4",
                                    filename = filename
                                )
                            }
                        }
                        // 2. Picker array output (formats list)
                        else if (status == "picker") {
                            val pickerArray = jsonObj.optJSONArray("picker")
                            if (pickerArray != null && pickerArray.length() > 0) {
                                val item = pickerArray.getJSONObject(0)
                                val downloadUrl = item.optString("url")
                                if (downloadUrl.isNotEmpty()) {
                                    val type = item.optString("type", "video")
                                    val quality = item.optString("quality", "HD")
                                    return@withContext ExtractionResult(
                                        streamUrl = downloadUrl,
                                        title = "${platform.displayName} Video",
                                        quality = quality,
                                        format = if (type == "audio") "MP3" else "MP4",
                                        filename = "${platform.name.lowercase()}_${System.currentTimeMillis()}.${if (type == "audio") "mp3" else "mp4"}"
                                    )
                                }
                            }
                        }
                        // 3. Fallback direct url property
                        else if (jsonObj.has("url")) {
                            val downloadUrl = jsonObj.optString("url")
                            if (downloadUrl.isNotEmpty()) {
                                return@withContext ExtractionResult(
                                    streamUrl = downloadUrl,
                                    title = "${platform.displayName} Video",
                                    quality = "HD",
                                    format = "MP4",
                                    filename = "${platform.name.lowercase()}_${System.currentTimeMillis()}.mp4"
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d("PrimaryRestExtractor", "Endpoint $endpoint failed: ${e.message}")
            }
        }
        return@withContext null
    }
}

/**
 * Secondary Layer: Fallback headless WebResolver DOM/stream sniffer.
 */
class FallbackWebExtractor : ExtractorService {
    override suspend fun extract(context: Context, url: String, platform: SupportedPlatform): ExtractionResult? {
        return WebResolver.resolve(context, url, platform)
    }
}

/**
 * Unified Extractor maintaining a chain-of-responsibility:
 * Primary (REST API) -> Secondary (Headless WebResolver).
 */
object VideoExtractor {
    private const val TAG = "VideoExtractor"

    private val primaryService: ExtractorService = PrimaryRestExtractor()
    private val fallbackService: ExtractorService = FallbackWebExtractor()

    /**
     * Executes the unified extraction pipeline:
     * 1. Detect platform using regex.
     * 2. Attempt Primary REST API extraction.
     * 3. Fallback to headless WebResolver if REST API fails or is rate-limited.
     * 4. Returns standardized ExtractionResult or null.
     */
    suspend fun extract(context: Context, url: String): ExtractionResult? = withContext(Dispatchers.IO) {
        val platform = SupportedPlatform.detect(url)
        Log.d(TAG, "Starting extraction pipeline for platform: ${platform.displayName}, URL: $url")

        // 1. Primary Layer: REST API
        try {
            val restResult = primaryService.extract(context, url, platform)
            if (restResult != null && restResult.streamUrl.isNotEmpty()) {
                Log.d(TAG, "Primary REST API extraction succeeded.")
                return@withContext restResult
            }
        } catch (e: Exception) {
            Log.w(TAG, "Primary REST API error: ${e.message}")
        }

        // 2. Secondary Layer: Headless WebResolver
        Log.d(TAG, "Primary REST API failed or rate-limited. Initiating Secondary WebResolver...")
        try {
            val webResult = fallbackService.extract(context, url, platform)
            if (webResult != null && webResult.streamUrl.isNotEmpty()) {
                Log.d(TAG, "Secondary WebResolver extraction succeeded.")
                return@withContext webResult
            }
        } catch (e: Exception) {
            Log.w(TAG, "Secondary WebResolver error: ${e.message}")
        }

        Log.w(TAG, "All automated extraction layers failed for: $url")
        return@withContext null
    }

    /**
     * Backward-compatible bridge returning legacy VideoInfo for UI components.
     */
    suspend fun extractVideo(context: Context, url: String): VideoInfo? {
        val result = extract(context, url) ?: return null
        return result.toVideoInfo(url)
    }
}

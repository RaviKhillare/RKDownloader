package com.rk.downloader.utils

import android.util.Log
import com.rk.downloader.data.DownloadOption
import com.rk.downloader.data.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object VideoExtractor {
    private const val TAG = "VideoExtractor"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    /**
     * Extracts video information and download URLs from a social media post URL.
     */
    suspend fun extractVideo(url: String): VideoInfo? = withContext(Dispatchers.IO) {
        try {
            val sanitizedUrl = cleanUrl(url)
            
            // Handle Facebook specifically
            if (sanitizedUrl.contains("facebook.com") || sanitizedUrl.contains("fb.watch") || sanitizedUrl.contains("fb.com")) {
                return@withContext extractFacebookVideo(sanitizedUrl)
            }
            
            // Handle TikTok specifically
            if (sanitizedUrl.contains("tiktok.com")) {
                return@withContext extractTikTokVideoDirect(sanitizedUrl)
            }

            // General OpenGraph crawler (highly effective for Instagram, Twitter/X, and other sites)
            val request = Request.Builder()
                .url(sanitizedUrl)
                .header("User-Agent", USER_AGENT)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val html = response.body?.string() ?: return@withContext null
                val doc = Jsoup.parse(html)
                
                val title = doc.select("meta[property=og:title]").attr("content")
                    .ifEmpty { doc.title() }
                    .ifEmpty { "Social Media Video" }

                val videoUrls = mutableListOf<String>()

                // Grab OpenGraph video tags
                val ogVideo = doc.select("meta[property=og:video]").attr("content")
                if (ogVideo.isNotEmpty()) videoUrls.add(ogVideo)

                val ogVideoSecure = doc.select("meta[property=og:video:secure_url]").attr("content")
                if (ogVideoSecure.isNotEmpty()) videoUrls.add(ogVideoSecure)

                // Grab video source tags
                doc.select("video").forEach { videoTag ->
                    val src = videoTag.attr("src")
                    if (src.isNotEmpty()) videoUrls.add(src)
                    
                    videoTag.select("source").forEach { sourceTag ->
                        val sourceSrc = sourceTag.attr("src")
                        if (sourceSrc.isNotEmpty()) videoUrls.add(sourceSrc)
                    }
                }

                // Check script tags for direct video URLs in JSON objects
                if (videoUrls.isEmpty()) {
                    val pattern = Pattern.compile("\"video_url\":\"(.*?)\"")
                    val matcher = pattern.matcher(html)
                    if (matcher.find()) {
                        val videoUrl = matcher.group(1)?.let { decodeUnicode(it) }
                        if (videoUrl != null && !videoUrls.contains(videoUrl)) {
                            videoUrls.add(videoUrl)
                        }
                    }
                }

                // Clean and format captured URLs
                val distinctUrls = videoUrls
                    .map { decodeUnicode(it).replace("&amp;", "&").trim() }
                    .filter { it.startsWith("http") }
                    .distinct()

                if (distinctUrls.isEmpty()) {
                    return@withContext null
                }

                val options = mutableListOf<DownloadOption>()
                distinctUrls.forEachIndexed { index, videoUrl ->
                    val qName = if (index == 0) "HD Video" else "SD Video"
                    options.add(DownloadOption(quality = qName, format = "MP4", downloadUrl = videoUrl))
                    options.add(DownloadOption(quality = "Audio Only", format = "MP3", downloadUrl = videoUrl))
                }

                return@withContext VideoInfo(title = title, sourceUrl = sanitizedUrl, options = options)
            }
        } catch (e: Exception) {
            Log.e(TAG, "General video extraction failed for $url: ${e.message}", e)
            return@withContext null
        }
    }

    private fun cleanUrl(url: String): String {
        var clean = url.trim()
        if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
            clean = "https://$clean"
        }
        return clean
    }

    private fun extractFacebookVideo(url: String): VideoInfo? {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val html = response.body?.string() ?: return null
                val doc = Jsoup.parse(html)
                
                val title = doc.select("meta[property=og:title]").attr("content")
                    .ifEmpty { "Facebook Video" }

                val options = mutableListOf<DownloadOption>()

                // Look for hd_src and sd_src links inside script tags or variables
                val hdPattern = Pattern.compile("hd_src\":\"(.*?)\"")
                val sdPattern = Pattern.compile("sd_src\":\"(.*?)\"")
                val nativePattern = Pattern.compile("browser_native_url\":\"(.*?)\"")

                val hdMatcher = hdPattern.matcher(html)
                val sdMatcher = sdPattern.matcher(html)
                val nativeMatcher = nativePattern.matcher(html)

                var hdUrl: String? = null
                var sdUrl: String? = null

                if (hdMatcher.find()) {
                    hdUrl = hdMatcher.group(1)?.let { decodeUnicode(it) }
                }
                if (sdMatcher.find()) {
                    sdUrl = sdMatcher.group(1)?.let { decodeUnicode(it) }
                }
                if ((hdUrl == null || sdUrl == null) && nativeMatcher.find()) {
                    val matched = nativeMatcher.group(1)?.let { decodeUnicode(it) }
                    if (hdUrl == null) hdUrl = matched else if (sdUrl == null) sdUrl = matched
                }

                // Fallback to og:video if HD/SD not found in JSON
                if (hdUrl == null && sdUrl == null) {
                    val ogVideo = doc.select("meta[property=og:video]").attr("content")
                    if (ogVideo.isNotEmpty()) {
                        sdUrl = ogVideo
                    }
                }

                hdUrl?.let {
                    options.add(DownloadOption(quality = "HD Quality (720p)", format = "MP4", downloadUrl = it))
                }
                sdUrl?.let {
                    options.add(DownloadOption(quality = "SD Quality (360p)", format = "MP4", downloadUrl = it))
                }

                if (options.isEmpty()) return null

                // Add MP3 options for whichever qualities were found
                val audioUrl = hdUrl ?: sdUrl
                audioUrl?.let {
                    options.add(DownloadOption(quality = "Audio Only (128kbps)", format = "MP3", downloadUrl = it))
                }

                return VideoInfo(title = title, sourceUrl = url, options = options)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Facebook extraction failed", e)
        }
        return null
    }

    private fun extractTikTokVideoDirect(url: String): VideoInfo? {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val html = response.body?.string() ?: return null
                
                val playAddrPattern = Pattern.compile("\"playAddr\":\"(.*?)\"")
                val downloadAddrPattern = Pattern.compile("\"downloadAddr\":\"(.*?)\"")

                val playMatcher = playAddrPattern.matcher(html)
                val downloadMatcher = downloadAddrPattern.matcher(html)

                var videoUrl: String? = null
                if (playMatcher.find()) {
                    videoUrl = playMatcher.group(1)?.let { decodeUnicode(it) }?.replace("\\u0026", "&")
                } else if (downloadMatcher.find()) {
                    videoUrl = downloadMatcher.group(1)?.let { decodeUnicode(it) }?.replace("\\u0026", "&")
                }

                if (videoUrl != null) {
                    val options = listOf(
                        DownloadOption(quality = "HD Quality (No Watermark)", format = "MP4", downloadUrl = videoUrl),
                        DownloadOption(quality = "Audio Only", format = "MP3", downloadUrl = videoUrl)
                    )
                    return VideoInfo(title = "TikTok Video", sourceUrl = url, options = options)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "TikTok direct extraction failed", e)
        }
        return null
    }

    private fun decodeUnicode(unicodeStr: String): String {
        return try {
            val regex = Pattern.compile("\\\\u([0-9a-fA-F]{4})")
            val matcher = regex.matcher(unicodeStr)
            val sb = StringBuffer()
            while (matcher.find()) {
                val cp = matcher.group(1)!!.toInt(16)
                matcher.appendReplacement(sb, cp.toChar().toString())
            }
            matcher.appendTail(sb)
            sb.toString().replace("\\/", "/")
        } catch (e: Exception) {
            unicodeStr
        }
    }
}

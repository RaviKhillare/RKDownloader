package com.rk.downloader.utils

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.rk.downloader.data.ExtractionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.URLEncoder
import kotlin.coroutines.resume

/**
 * Headless, silent WebView scraper/resolver that intercepts media streams and DOM elements
 * from known third-party web portals when direct REST APIs fail.
 */
object WebResolver {
    private const val TAG = "WebResolver"
    private const val TIMEOUT_MS = 15000L

    /**
     * Resolves direct media stream using a headless WebView with DOM/network interception.
     */
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun resolve(context: Context, videoUrl: String, platform: SupportedPlatform): ExtractionResult? = withContext(Dispatchers.Main) {
        return@withContext withTimeoutOrNull(TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                Handler(Looper.getMainLooper()).post {
                    var webView: WebView? = null
                    var isCompleted = false

                    fun cleanup() {
                        try {
                            webView?.stopLoading()
                            webView?.webViewClient = object : WebViewClient() {}
                            webView?.destroy()
                            webView = null
                        } catch (e: Exception) {
                            Log.e(TAG, "Error during WebResolver cleanup: ${e.message}")
                        }
                    }

                    continuation.invokeOnCancellation {
                        Handler(Looper.getMainLooper()).post { cleanup() }
                    }

                    try {
                        webView = WebView(context.applicationContext).apply {
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = true
                                mediaPlaybackRequiresUserGesture = false
                                setSupportMultipleWindows(false)
                                javaScriptCanOpenWindowsAutomatically = false
                                userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                            }

                            webViewClient = object : WebViewClient() {
                                override fun shouldInterceptRequest(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): WebResourceResponse? {
                                    val reqUrl = request?.url?.toString() ?: return super.shouldInterceptRequest(view, request)
                                    val lower = reqUrl.lowercase()

                                    // Sniff for media streams or generated direct download links
                                    if (isStreamUrl(lower)) {
                                        synchronized(this@WebResolver) {
                                            if (!isCompleted && continuation.isActive) {
                                                isCompleted = true
                                                val ext = if (lower.contains(".mp3") || lower.contains("audio")) "MP3" else "MP4"
                                                val result = ExtractionResult(
                                                    streamUrl = reqUrl,
                                                    title = "${platform.displayName} Video",
                                                    quality = "HD (Sniffed)",
                                                    format = ext,
                                                    filename = "${platform.name.lowercase()}_${System.currentTimeMillis()}.$ext"
                                                )
                                                Handler(Looper.getMainLooper()).post {
                                                    cleanup()
                                                    continuation.resume(result)
                                                }
                                            }
                                        }
                                    }
                                    return super.shouldInterceptRequest(view, request)
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    // Inject script to sniff and extract direct media download links from the portal DOM
                                    val jsCode = """
                                        (function() {
                                            var links = document.querySelectorAll('a[href*=".mp4"], a[href*="videoplayback"], a[download], a.download-btn, a.btn-download');
                                            for (var i = 0; i < links.length; i++) {
                                                var href = links[i].getAttribute('href');
                                                if (href && (href.indexOf('http') === 0) && (href.indexOf('.mp4') !== -1 || href.indexOf('download') !== -1)) {
                                                    return href;
                                                }
                                            }
                                            return '';
                                        })();
                                    """.trimIndent()

                                    view?.evaluateJavascript(jsCode) { result ->
                                        val clean = result?.replace("\"", "")?.trim() ?: ""
                                        if (clean.isNotEmpty() && clean.startsWith("http") && !isCompleted && continuation.isActive) {
                                            synchronized(this@WebResolver) {
                                                if (!isCompleted && continuation.isActive) {
                                                    isCompleted = true
                                                    val res = ExtractionResult(
                                                        streamUrl = clean,
                                                        title = "${platform.displayName} Video",
                                                        quality = "HD (Web)",
                                                        format = "MP4",
                                                        filename = "${platform.name.lowercase()}_${System.currentTimeMillis()}.mp4"
                                                    )
                                                    cleanup()
                                                    continuation.resume(res)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Choose the target extraction portal based on detected platform
                            val encoded = try { URLEncoder.encode(videoUrl.trim(), "UTF-8") } catch (e: Exception) { videoUrl }
                            val targetPortal = when (platform) {
                                SupportedPlatform.YOUTUBE -> "https://en.savefrom.net/?url=$encoded"
                                SupportedPlatform.INSTAGRAM, SupportedPlatform.FACEBOOK, SupportedPlatform.THREADS -> "https://snapsave.app/"
                                SupportedPlatform.TIKTOK -> "https://ssstik.io/"
                                else -> "https://en.savefrom.net/?url=$encoded"
                            }

                            loadUrl(targetPortal)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start headless WebView resolver", e)
                        cleanup()
                        if (continuation.isActive) continuation.resume(null)
                    }
                }
            }
        }
    }

    private fun isStreamUrl(lower: String): Boolean {
        // Exclude web assets, fonts, analytics, ads
        if (lower.contains("google-analytics") || lower.contains("googlesyndication") ||
            lower.contains("doubleclick") || lower.contains(".js") || lower.contains(".css") ||
            lower.contains(".jpg") || lower.contains(".png") || lower.contains(".svg") ||
            lower.contains(".woff") || lower.contains(".ico")
        ) {
            return false
        }

        return lower.contains(".mp4") ||
               lower.contains(".m3u8") ||
               (lower.contains("googlevideo.com") && lower.contains("videoplayback")) ||
               (lower.contains("fbcdn.net") && lower.contains("/v/")) ||
               (lower.contains("cdninstagram.com") && lower.contains("/v/")) ||
               (lower.contains("tiktokcdn.com") && lower.contains("video"))
    }
}

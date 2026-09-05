package com.rk.downloader.ui.screens

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.rk.downloader.R
import com.rk.downloader.ads.AdManager
import com.rk.downloader.data.DownloadOption
import com.rk.downloader.data.VideoInfo
import com.rk.downloader.ui.components.BannerAdView
import com.rk.downloader.ui.components.VideoInfoBottomSheet
import com.rk.downloader.utils.DownloadManagerHelper
import java.net.URLEncoder

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
    modifier: Modifier = Modifier,
    initialUrl: String = "https://www.google.com"
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val focusManager = LocalFocusManager.current
    
    var webView: WebView? by remember { mutableStateOf(null) }
    var currentUrl by remember { mutableStateOf(initialUrl) }
    var searchInput by remember { mutableStateOf("") }
    var pageTitle by remember { mutableStateOf("Downloader Web") }
    
    var loadingProgress by remember { mutableIntStateOf(0) }
    var isPageLoading by remember { mutableStateOf(false) }
    
    // Intercepted video stream details
    var detectedVideoUrl by remember { mutableStateOf<String?>(null) }
    var showBottomSheet by remember { mutableStateOf(false) }

    // Intercept Android back button to navigate back in web history
    BackHandler(enabled = webView?.canGoBack() == true) {
        webView?.goBack()
    }

    fun loadWebAddress(input: String) {
        var query = input.trim()
        if (query.isEmpty()) return
        
        if (!query.contains(".") || query.contains(" ")) {
            try {
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                query = "https://www.google.com/search?q=$encodedQuery"
            } catch (e: Exception) {
                query = "https://www.google.com/search?q=$query"
            }
        } else if (!query.startsWith("http://") && !query.startsWith("https://")) {
            query = "https://$query"
        }
        
        webView?.loadUrl(query)
        focusManager.clearFocus()
    }

    // Load initial URL if changed externally
    LaunchedEffect(initialUrl) {
        if (initialUrl.isNotEmpty() && initialUrl != "https://www.google.com") {
            webView?.loadUrl(initialUrl)
        }
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Navigation controls and address bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = { webView?.goBack() },
                    enabled = webView?.canGoBack() == true
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }

                IconButton(
                    onClick = { webView?.goForward() },
                    enabled = webView?.canGoForward() == true
                ) {
                    Icon(Icons.Default.ArrowForward, contentDescription = "Forward")
                }

                OutlinedTextField(
                    value = searchInput,
                    onValueChange = { searchInput = it },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    placeholder = { Text(stringResource(R.string.browser_search_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        loadWebAddress(searchInput)
                    }),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    trailingIcon = {
                        if (searchInput.isNotEmpty()) {
                            IconButton(onClick = { searchInput = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    }
                )

                IconButton(onClick = { loadWebAddress(searchInput) }) {
                    Icon(Icons.Default.Search, contentDescription = "Go")
                }

                IconButton(onClick = { webView?.reload() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reload")
                }
            }

            // Quick Third-Party Provider Switcher Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AssistChip(
                    onClick = {
                        val encoded = try { URLEncoder.encode(currentUrl, "UTF-8") } catch (e: Exception) { "" }
                        if (currentUrl.contains("http") && !currentUrl.contains("savefrom")) {
                            loadWebAddress("https://en.savefrom.net/?url=$encoded")
                        } else {
                            loadWebAddress("https://en.savefrom.net/")
                        }
                    },
                    label = { Text("SaveFrom.net", fontWeight = FontWeight.Bold) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )

                AssistChip(
                    onClick = {
                        loadWebAddress("https://snapsave.app/")
                    },
                    label = { Text("SnapSave (FB/Insta)") }
                )

                AssistChip(
                    onClick = {
                        if (currentUrl.contains("youtube.com") || currentUrl.contains("youtu.be")) {
                            loadWebAddress(currentUrl.replace("youtube.com", "ssyoutube.com"))
                        } else {
                            loadWebAddress("https://ssyoutube.com/")
                        }
                    },
                    label = { Text("SSYouTube") }
                )

                AssistChip(
                    onClick = {
                        loadWebAddress("https://www.y2mate.com/")
                    },
                    label = { Text("Y2Mate") }
                )

                AssistChip(
                    onClick = {
                        loadWebAddress("https://www.google.com")
                    },
                    label = { Text("Google") }
                )
            }

            // Loader Progress Bar
            if (isPageLoading && loadingProgress < 100) {
                LinearProgressIndicator(
                    progress = loadingProgress / 100f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Main WebView area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            mediaPlaybackRequiresUserGesture = false
                            // Prevent popup windows from hijacking or freezing webview
                            setSupportMultipleWindows(false)
                            javaScriptCanOpenWindowsAutomatically = false
                            allowFileAccess = true
                            userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                        }

                        // Native DownloadListener: Intercepts all file download requests from SaveFrom.net, SnapSave, etc.
                        setDownloadListener { downloadUrl, userAgent, contentDisposition, mimetype, contentLength ->
                            val guessedName = URLUtil.guessFileName(downloadUrl, contentDisposition, mimetype)
                            val sanitizedTitle = guessedName.substringBeforeLast(".").ifEmpty { "Video_${System.currentTimeMillis()}" }
                            val ext = if (guessedName.endsWith(".mp3", true) || mimetype?.contains("audio") == true) "mp3" else "mp4"

                            if (activity != null) {
                                AdManager.showInterstitialAd(activity) {
                                    DownloadManagerHelper.startDownload(
                                        context = context,
                                        url = downloadUrl,
                                        title = sanitizedTitle,
                                        quality = "Direct",
                                        format = ext.uppercase()
                                    )
                                }
                            } else {
                                DownloadManagerHelper.startDownload(
                                    context = context,
                                    url = downloadUrl,
                                    title = sanitizedTitle,
                                    quality = "Direct",
                                    format = ext.uppercase()
                                )
                            }
                            Toast.makeText(context, "डाऊनलोड सुरू झाले आहे...", Toast.LENGTH_SHORT).show()
                        }
                        
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                isPageLoading = true
                                detectedVideoUrl = null
                                if (url != null) {
                                    currentUrl = url
                                    searchInput = url
                                }
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                isPageLoading = false
                                if (view != null) {
                                    pageTitle = view.title ?: "Web Page"
                                }
                            }

                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val reqUrl = request?.url?.toString() ?: return false
                                val lower = reqUrl.lowercase()

                                // Directly download if URL points to an actual media stream/file
                                if (lower.endsWith(".mp4") || lower.endsWith(".mp3") || lower.endsWith(".m4a") ||
                                    (lower.contains("googlevideo.com") && lower.contains("videoplayback")) ||
                                    (lower.contains("download") && (lower.contains(".mp4") || lower.contains("mime=video")))
                                ) {
                                    val guessedName = URLUtil.guessFileName(reqUrl, null, null)
                                    val sanitizedTitle = guessedName.substringBeforeLast(".").ifEmpty { "Video_${System.currentTimeMillis()}" }
                                    val ext = if (lower.endsWith(".mp3")) "mp3" else "mp4"
                                    DownloadManagerHelper.startDownload(
                                        context = context,
                                        url = reqUrl,
                                        title = sanitizedTitle,
                                        quality = "Direct",
                                        format = ext.uppercase()
                                    )
                                    Toast.makeText(context, "डाऊनलोड सुरू झाले आहे...", Toast.LENGTH_SHORT).show()
                                    return true
                                }

                                // Block non-HTTP popup/intent links that can crash or redirect away
                                if (!reqUrl.startsWith("http://") && !reqUrl.startsWith("https://")) {
                                    return true
                                }

                                return false
                            }

                            // Intercept network queries to scan for media streams
                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): WebResourceResponse? {
                                if (request != null) {
                                    val reqUrl = request.url.toString()
                                    val lowerUrl = reqUrl.lowercase()
                                    
                                    // Match media URLs
                                    if (lowerUrl.contains(".mp4") || 
                                        lowerUrl.contains(".m3u8") ||
                                        (lowerUrl.contains("fbcdn.net") && lowerUrl.contains("/v/")) ||
                                        (lowerUrl.contains("instagram.com") && lowerUrl.contains("/video/")) ||
                                        (lowerUrl.contains("tiktok.com") && lowerUrl.contains("mime=video"))
                                    ) {
                                        detectedVideoUrl = reqUrl
                                    }
                                }
                                return super.shouldInterceptRequest(view, request)
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                loadingProgress = newProgress
                            }
                        }
                        
                        loadUrl(initialUrl)
                        webView = this
                    }
                },
                update = {
                    webView = it
                }
            )

            // Animated download FAB that appears when a direct video stream link is intercepted
            androidx.compose.animation.AnimatedVisibility(
                visible = detectedVideoUrl != null,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                FloatingActionButton(
                    onClick = { showBottomSheet = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowDownward,
                        contentDescription = "Download Video",
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }

        // Banner Ad on the browser tab
        BannerAdView()
    }

    // Quality chooser for intercepted WebView URLs
    if (showBottomSheet && detectedVideoUrl != null) {
        val streamUrl = detectedVideoUrl!!
        val parsedVideoInfo = VideoInfo(
            title = pageTitle.ifEmpty { "Browser Downloaded Video" },
            sourceUrl = currentUrl,
            options = listOf(
                DownloadOption(quality = "HD Video Stream", format = "MP4", downloadUrl = streamUrl),
                DownloadOption(quality = "Audio Only", format = "MP3", downloadUrl = streamUrl)
            )
        )

        VideoInfoBottomSheet(
            videoInfo = parsedVideoInfo,
            onDismissRequest = { showBottomSheet = false },
            onDownloadSelected = { option ->
                if (activity != null) {
                    AdManager.showInterstitialAd(activity) {
                        DownloadManagerHelper.startDownload(
                            context = context,
                            url = option.downloadUrl,
                            title = parsedVideoInfo.title,
                            quality = option.quality,
                            format = option.format
                        )
                        detectedVideoUrl = null
                    }
                } else {
                    DownloadManagerHelper.startDownload(
                        context = context,
                        url = option.downloadUrl,
                        title = parsedVideoInfo.title,
                        quality = option.quality,
                        format = option.format
                    )
                    detectedVideoUrl = null
                }
            }
        )
    }
}

package com.rk.downloader.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.rk.downloader.R
import com.rk.downloader.ads.AdManager
import com.rk.downloader.data.VideoInfo
import com.rk.downloader.ui.components.BannerAdView
import com.rk.downloader.ui.components.VideoInfoBottomSheet
import com.rk.downloader.utils.ClipboardUtil
import com.rk.downloader.utils.DownloadManagerHelper
import com.rk.downloader.utils.SupportedPlatform
import com.rk.downloader.utils.VideoExtractor
import kotlinx.coroutines.launch
import java.net.URLEncoder

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    initialUrl: String = "",
    onNavigateToBrowser: (String) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    var urlInput by remember { mutableStateOf(initialUrl) }
    var isExtracting by remember { mutableStateOf(false) }
    var extractedVideoInfo by remember { mutableStateOf<VideoInfo?>(null) }
    
    var showClipboardDialog by remember { mutableStateOf(false) }
    var detectedClipboardUrl by remember { mutableStateOf("") }

    var showFallbackDialog by remember { mutableStateOf(false) }
    var failedUrl by remember { mutableStateOf("") }
    var detectedPlatform by remember { mutableStateOf(SupportedPlatform.GENERIC) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val clipboardUrl = ClipboardUtil.getCopiedUrl(context)
                if (clipboardUrl != null && ClipboardUtil.isSocialMediaUrl(clipboardUrl) && clipboardUrl != urlInput) {
                    detectedClipboardUrl = clipboardUrl
                    showClipboardDialog = true
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    fun getSaveFromUrl(url: String): String {
        return try {
            "https://en.savefrom.net/?url=" + URLEncoder.encode(url.trim(), "UTF-8")
        } catch (e: Exception) {
            "https://en.savefrom.net/"
        }
    }

    fun parseUrl(url: String) {
        val cleanUrl = url.trim()
        if (cleanUrl.isEmpty()) {
            Toast.makeText(context, context.getString(R.string.toast_invalid_url), Toast.LENGTH_SHORT).show()
            return
        }

        isExtracting = true
        val platform = SupportedPlatform.detect(cleanUrl)
        detectedPlatform = platform

        scope.launch {
            val videoInfo = VideoExtractor.extractVideo(context, cleanUrl)
            isExtracting = false
            if (videoInfo != null) {
                extractedVideoInfo = videoInfo
            } else {
                // If both automated layers (REST API & headless WebResolver) fail, offer manual browser portal
                failedUrl = cleanUrl
                showFallbackDialog = true
            }
        }
    }

    LaunchedEffect(initialUrl) {
        if (initialUrl.isNotEmpty()) {
            urlInput = initialUrl
            parseUrl(initialUrl)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Primary URL input
            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                label = { Text(stringResource(R.string.enter_url_hint)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                trailingIcon = {
                    if (urlInput.isNotEmpty()) {
                        IconButton(onClick = { urlInput = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Primary Download Buttons Row (Dual Mode)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val clipText = ClipboardUtil.getCopiedUrl(context)
                        if (clipText != null) {
                            urlInput = clipText
                        } else {
                            Toast.makeText(context, context.getString(R.string.toast_clipboard_empty), Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text(stringResource(R.string.btn_paste))
                }

                Button(
                    onClick = { parseUrl(urlInput) },
                    modifier = Modifier.weight(1.4f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isExtracting) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.btn_download))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // One-click Direct SaveFrom.net Web Action Button
            FilledTonalButton(
                onClick = {
                    val clean = urlInput.trim()
                    if (clean.isEmpty()) {
                        Toast.makeText(context, "कृपया प्रथम व्हिडिओ लिंक टाका.", Toast.LENGTH_SHORT).show()
                    } else {
                        onNavigateToBrowser(getSaveFromUrl(clean))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("SaveFrom.net द्वारे डाऊनलोड करा", fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Material 3 Quick-Launch Platform Chips / Portal Buttons
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "सपोर्टेड प्लॅटफॉर्म्स (Quick Launch Portals)",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Row 1: Top Social Media
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SuggestionChip(
                            onClick = { onNavigateToBrowser("https://www.youtube.com") },
                            label = { Text("YouTube") }
                        )
                        SuggestionChip(
                            onClick = { onNavigateToBrowser("https://www.instagram.com") },
                            label = { Text("Instagram") }
                        )
                        SuggestionChip(
                            onClick = { onNavigateToBrowser("https://www.facebook.com") },
                            label = { Text("Facebook") }
                        )
                        SuggestionChip(
                            onClick = { onNavigateToBrowser("https://www.tiktok.com") },
                            label = { Text("TikTok") }
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Row 2: Extended Platforms (Twitter, Pinterest, Threads, Dailymotion)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SuggestionChip(
                            onClick = { onNavigateToBrowser("https://x.com") },
                            label = { Text("Twitter / X") }
                        )
                        SuggestionChip(
                            onClick = { onNavigateToBrowser("https://www.pinterest.com") },
                            label = { Text("Pinterest") }
                        )
                        SuggestionChip(
                            onClick = { onNavigateToBrowser("https://www.threads.net") },
                            label = { Text("Threads") }
                        )
                        SuggestionChip(
                            onClick = { onNavigateToBrowser("https://www.dailymotion.com") },
                            label = { Text("Dailymotion") }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "• कोणत्याही प्लॅटफॉर्मची लिंक पेस्ट करून 'Download' दाबा किंवा थेट ब्राउझरमध्ये पाहण्यासाठी वरील बटण दाबा.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        BannerAdView(modifier = Modifier.padding(top = 16.dp))
    }

    // Modal Bottom Sheet displaying parsed video format options
    extractedVideoInfo?.let { videoInfo ->
        VideoInfoBottomSheet(
            videoInfo = videoInfo,
            onDismissRequest = { extractedVideoInfo = null },
            onDownloadSelected = { option ->
                if (activity != null) {
                    AdManager.showInterstitialAd(activity) {
                        DownloadManagerHelper.startDownload(
                            context = context,
                            url = option.downloadUrl,
                            title = videoInfo.title,
                            quality = option.quality,
                            format = option.format
                        )
                    }
                } else {
                    DownloadManagerHelper.startDownload(
                        context = context,
                        url = option.downloadUrl,
                        title = videoInfo.title,
                        quality = option.quality,
                        format = option.format
                    )
                }
            }
        )
    }

    // Tertiary Layer Fallback Dialog
    if (showFallbackDialog) {
        AlertDialog(
            onDismissRequest = { showFallbackDialog = false },
            title = { Text("थेट डाऊनलोड उपलब्ध नाही") },
            text = {
                Text("या व्हिडिओसाठी थेट API उपलब्ध नाही. हा व्हिडिओ SaveFrom.net किंवा SnapSave वेब पोर्टलद्वारे सहज डाऊनलोड करता येईल. ब्राउझरमध्ये उघडायचे का?")
            },
            confirmButton = {
                Button(onClick = {
                    showFallbackDialog = false
                    onNavigateToBrowser(getSaveFromUrl(failedUrl))
                }) {
                    Text("SaveFrom.net ने उघडा")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = {
                        showFallbackDialog = false
                        onNavigateToBrowser("https://snapsave.app/")
                    }) {
                        Text("SnapSave")
                    }
                    TextButton(onClick = { showFallbackDialog = false }) {
                        Text("रद्द करा")
                    }
                }
            }
        )
    }

    // Automatic Clipboard Detection Dialog
    if (showClipboardDialog) {
        AlertDialog(
            onDismissRequest = { showClipboardDialog = false },
            title = { Text(stringResource(R.string.clipboard_detect_title)) },
            text = { Text(stringResource(R.string.clipboard_detect_msg)) },
            confirmButton = {
                Button(onClick = {
                    showClipboardDialog = false
                    urlInput = detectedClipboardUrl
                    parseUrl(detectedClipboardUrl)
                }) {
                    Text(stringResource(R.string.btn_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClipboardDialog = false }) {
                    Text(stringResource(R.string.btn_no))
                }
            }
        )
    }
}

package com.rk.downloader.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Language
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

    fun openWithSaveFrom(url: String) {
        val cleanUrl = url.trim()
        if (cleanUrl.isEmpty()) {
            Toast.makeText(context, "कृपया प्रथम व्हिडिओ लिंक टाका.", Toast.LENGTH_SHORT).show()
            return
        }
        onNavigateToBrowser(getSaveFromUrl(cleanUrl))
    }

    fun parseUrl(url: String) {
        val cleanUrl = url.trim()
        if (cleanUrl.isEmpty()) {
            Toast.makeText(context, context.getString(R.string.toast_invalid_url), Toast.LENGTH_SHORT).show()
            return
        }

        isExtracting = true
        scope.launch {
            val videoInfo = VideoExtractor.extractVideo(context, cleanUrl)
            isExtracting = false
            if (videoInfo != null) {
                extractedVideoInfo = videoInfo
            } else {
                // When direct API fails, prompt user to download seamlessly via SaveFrom.net
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
                .weight(1f),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 20.dp)
            )

            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                label = { Text(stringResource(R.string.enter_url_hint)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Primary Download Buttons Row
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
                    modifier = Modifier.weight(1.3f),
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

            // SaveFrom.net Dedicated Direct Action Button
            Button(
                onClick = { openWithSaveFrom(urlInput) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("SaveFrom.net द्वारे डाऊनलोड करा", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Quick Third-Party Downloader Portals Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "थर्ड-पार्टी डाऊनलोड पोर्टल्स (Third-Party Services)",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AssistChip(
                            onClick = { openWithSaveFrom(urlInput) },
                            label = { Text("SaveFrom.net") }
                        )

                        AssistChip(
                            onClick = { onNavigateToBrowser("https://snapsave.app/") },
                            label = { Text("SnapSave (FB/Insta)") }
                        )

                        AssistChip(
                            onClick = {
                                val clean = urlInput.trim()
                                if (clean.contains("youtube.com") || clean.contains("youtu.be")) {
                                    onNavigateToBrowser(clean.replace("youtube.com", "ssyoutube.com"))
                                } else {
                                    onNavigateToBrowser("https://ssyoutube.com/")
                                }
                            },
                            label = { Text("SSYouTube") }
                        )

                        AssistChip(
                            onClick = { onNavigateToBrowser("https://www.y2mate.com/") },
                            label = { Text("Y2Mate") }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• YouTube, Instagram, Facebook, TikTok, Twitter चे कोणतेही व्हिडिओ डाऊनलोड करता येतात.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        BannerAdView(modifier = Modifier.padding(top = 16.dp))
    }

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

    // Direct Extraction Fallback Dialog
    if (showFallbackDialog) {
        AlertDialog(
            onDismissRequest = { showFallbackDialog = false },
            title = { Text("थेट डाऊनलोड उपलब्ध नाही") },
            text = {
                Text("या व्हिडिओसाठी थेट API उपलब्ध नाही. हा व्हिडिओ SaveFrom.net किंवा SnapSave द्वारे सहज डाऊनलोड करता येईल. SaveFrom.net उघडायचे का?")
            },
            confirmButton = {
                Button(onClick = {
                    showFallbackDialog = false
                    openWithSaveFrom(failedUrl)
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

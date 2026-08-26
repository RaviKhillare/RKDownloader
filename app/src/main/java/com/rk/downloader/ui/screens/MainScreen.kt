package com.rk.downloader.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
                Toast.makeText(context, "व्हिडिओ लिंक तपासा किंवा ब्राउझर टॅब वापरून डाऊनलोड करा.", Toast.LENGTH_LONG).show()
                onNavigateToBrowser(cleanUrl)
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
                modifier = Modifier.padding(bottom = 24.dp)
            )

            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                label = { Text(stringResource(R.string.enter_url_hint)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

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
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isExtracting) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(stringResource(R.string.btn_download))
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.help_title),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• Instagram (Posts, Reels, Stories)\n• Facebook (Videos, Watch)\n• TikTok (No watermark)\n• Twitter / X (Video posts)\n• YouTube (Videos/Shorts)\n• Direct MP4/Web Links",
                        style = MaterialTheme.typography.bodyMedium
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

import os
import subprocess
import sys

print("=======================================================")
print("      RKDownloader - Project Update & Build Script")
print("=======================================================")
print("")

# 1. Define file paths
base_path = "app/src/main/java/com/rk/downloader"
files_to_update = {}

# Code content for AdminConfig.kt
files_to_update[f"{base_path}/config/AdminConfig.kt"] = r"""package com.rk.downloader.config

import android.content.Context

object AdminConfig {
    private const val PREFS_NAME = "rk_admin_prefs"
    private const val KEY_SERVER_URL = "server_url"
    
    // Default fallback address
    private const val DEFAULT_URL = "http://10.31.60.251/rk_tracker"

    fun getServerUrl(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_SERVER_URL, DEFAULT_URL) ?: DEFAULT_URL
    }

    fun setServerUrl(context: Context, url: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SERVER_URL, url.trim().trimEnd('/')).apply()
    }

    const val ADMIN_SECRET_KEY = "admin123"
}
"""

# Code content for TrackerManager.kt
files_to_update[f"{base_path}/utils/TrackerManager.kt"] = r"""package com.rk.downloader.utils

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
import org.json.JSONObject
import java.util.UUID

object TrackerManager {
    private const val TAG = "TrackerManager"
    private const val PREFS_NAME = "rk_tracker_prefs"
    private const val KEY_DEVICE_UUID = "device_uuid"
    private const val KEY_INSTALL_TIME = "install_time"
    
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

    private fun getInstallTime(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var installTime = prefs.getLong(KEY_INSTALL_TIME, 0L)
        if (installTime == 0L) {
            installTime = System.currentTimeMillis()
            prefs.edit().putLong(KEY_INSTALL_TIME, installTime).apply()
        }
        return installTime
    }

    suspend fun registerDevice(context: Context) = withContext(Dispatchers.IO) {
        val trackerUrl = AdminConfig.getServerUrl(context)
        if (trackerUrl.isEmpty()) {
            return@withContext
        }

        try {
            val uuid = getDeviceUuid(context)
            val installTime = getInstallTime(context)
            val currentTime = System.currentTimeMillis()
            val model = Build.MODEL ?: "Unknown Device"
            val osVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

            val postData = JSONObject().apply {
                put("uuid", uuid)
                put("model", model)
                put("os", osVersion)
                put("install_time", installTime)
                put("last_active", currentTime)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = postData.toString().toRequestBody(mediaType)
            val request = Request.Builder()
                .url("$trackerUrl/track.php")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Log.d(TAG, "Device registered/pinged server successfully.")
                } else {
                    Log.e(TAG, "Failed to ping server. Code: ${response.code}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Tracking failed: ${e.message}", e)
        }
    }
}
"""

# Code content for VideoExtractor.kt
files_to_update[f"{base_path}/utils/VideoExtractor.kt"] = r"""package com.rk.downloader.utils

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
"""

# Code content for MainScreen.kt
files_to_update[f"{base_path}/ui/screens/MainScreen.kt"] = r"""package com.rk.downloader.ui.screens

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
"""

# Code content for DownloadManagerHelper.kt
files_to_update[f"{base_path}/utils/DownloadManagerHelper.kt"] = r"""package com.rk.downloader.utils

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.widget.Toast
import com.rk.downloader.R
import com.rk.downloader.data.DownloadedVideo
import java.io.File

object DownloadManagerHelper {
    private const val DOWNLOAD_SUBFOLDER = "RKDownloader"

    fun startDownload(context: Context, url: String, title: String, quality: String, format: String): Long {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        
        val sanitizedTitle = title.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        val extension = format.lowercase()
        val qName = quality.replace(Regex("[^a-zA-Z0-9]"), "_")
        val fileName = "${if (sanitizedTitle.length > 50) sanitizedTitle.substring(0, 50) else sanitizedTitle}_$qName.$extension"

        val uri = Uri.parse(url)
        val request = DownloadManager.Request(uri).apply {
            setTitle(fileName)
            setDescription(context.getString(R.string.app_name))
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "$DOWNLOAD_SUBFOLDER/$fileName")
            
            val mimeType = if (extension == "mp3") "audio/mpeg" else "video/mp4"
            setMimeType(mimeType)
            
            @Suppress("DEPRECATION")
            allowScanningByMediaScanner()
        }

        Toast.makeText(context, context.getString(R.string.toast_download_started), Toast.LENGTH_SHORT).show()
        return downloadManager.enqueue(request)
    }

    fun getDownloadedFiles(context: Context): List<DownloadedVideo> {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterByStatus(DownloadManager.STATUS_SUCCESSFUL)
        val cursor = downloadManager.query(query)
        val list = mutableListOf<DownloadedVideo>()
        
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                val idColumn = cursor.getColumnIndex(DownloadManager.COLUMN_ID)
                val titleColumn = cursor.getColumnIndex(DownloadManager.COLUMN_TITLE)
                val filenameColumn = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_FILENAME)
                val localUriColumn = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                val sizeColumn = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                val dateColumn = cursor.getColumnIndex(DownloadManager.COLUMN_LAST_MODIFIED_TIMESTAMP)
                
                do {
                    val id = cursor.getLong(idColumn)
                    val title = cursor.getString(titleColumn) ?: "Video"
                    val size = cursor.getLong(sizeColumn)
                    val date = cursor.getLong(dateColumn)
                    
                    var filePath = ""
                    var exists = false
                    
                    if (filenameColumn != -1) {
                        val path = cursor.getString(filenameColumn)
                        if (!path.isNullOrEmpty()) {
                            filePath = path
                            exists = File(path).exists()
                        }
                    }
                    
                    if (!exists && localUriColumn != -1) {
                        val localUriStr = cursor.getString(localUriColumn)
                        if (!localUriStr.isNullOrEmpty()) {
                            val uri = Uri.parse(localUriStr)
                            if (uri.scheme == "file") {
                                val path = uri.path
                                if (!path.isNullOrEmpty()) {
                                    filePath = path
                                    exists = File(path).exists()
                                }
                            } else if (uri.scheme == "content") {
                                exists = try {
                                    context.contentResolver.openAssetFileDescriptor(uri, "r")?.use {
                                        it.length > 0
                                    } ?: false
                                } catch (e: Exception) {
                                    true
                                }
                                filePath = localUriStr
                            }
                        }
                    }
                    
                    if (exists && filePath.isNotEmpty()) {
                        list.add(DownloadedVideo(id, title, filePath, size, date))
                    }
                } while (cursor.moveToNext())
            }
            cursor.close()
        }
        return list.sortedByDescending { it.dateAdded }
    }

    fun deleteFile(context: Context, video: DownloadedVideo): Boolean {
        return try {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.remove(video.id)
            
            if (video.filePath.startsWith("content://")) {
                context.contentResolver.delete(Uri.parse(video.filePath), null, null)
            } else {
                val file = File(video.filePath)
                if (file.exists()) file.delete()
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}
"""

# Code content for DownloadsScreen.kt
files_to_update[f"{base_path}/ui/screens/DownloadsScreen.kt"] = r"""package com.rk.downloader.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.rk.downloader.R
import com.rk.downloader.data.DownloadedVideo
import com.rk.downloader.ui.components.BannerAdView
import com.rk.downloader.utils.DownloadManagerHelper
import java.io.File

@Composable
fun DownloadsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var downloadedFiles by remember { mutableStateOf<List<DownloadedVideo>>(emptyList()) }
    var fileToDelete by remember { mutableStateOf<DownloadedVideo?>(null) }

    fun loadFiles() {
        downloadedFiles = DownloadManagerHelper.getDownloadedFiles(context)
    }

    LaunchedEffect(Unit) {
        loadFiles()
    }

    fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format("%.2f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }

    fun playFile(video: DownloadedVideo) {
        try {
            val contentUri = if (video.filePath.startsWith("content://")) {
                Uri.parse(video.filePath)
            } else {
                val file = File(video.filePath)
                FileProvider.getUriForFile(context, "com.rk.downloader.fileprovider", file)
            }
            
            val mimeType = if (video.name.lowercase().endsWith(".mp3")) "audio/*" else "video/*"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.btn_play)))
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot play file: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun shareFile(video: DownloadedVideo) {
        try {
            val contentUri = if (video.filePath.startsWith("content://")) {
                Uri.parse(video.filePath)
            } else {
                val file = File(video.filePath)
                FileProvider.getUriForFile(context, "com.rk.downloader.fileprovider", file)
            }
            
            val mimeType = if (video.name.lowercase().endsWith(".mp3")) "audio/*" else "video/*"
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.btn_share)))
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot share file: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun deleteFile(video: DownloadedVideo) {
        val success = DownloadManagerHelper.deleteFile(context, video)
        if (success) {
            Toast.makeText(context, context.getString(R.string.toast_deleted), Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, context.getString(R.string.toast_delete_failed), Toast.LENGTH_SHORT).show()
        }
        loadFiles()
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
        ) {
            Text(
                text = stringResource(R.string.tab_downloads),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (downloadedFiles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.no_downloads),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(downloadedFiles) { video ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = video.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = formatSize(video.sizeBytes),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(onClick = { playFile(video) }) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Play",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    IconButton(onClick = { shareFile(video) }) {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = "Share",
                                            tint = MaterialTheme.colorScheme.secondary
                                        )
                                    }

                                    IconButton(onClick = { fileToDelete = video }) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        BannerAdView(modifier = Modifier.padding(top = 16.dp))
    }

    fileToDelete?.let { video ->
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            title = { Text(stringResource(R.string.delete_confirm_title)) },
            text = { Text(stringResource(R.string.delete_confirm_msg)) },
            confirmButton = {
                Button(
                    onClick = {
                        deleteFile(video)
                        fileToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.btn_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { fileToDelete = null }) {
                    Text(stringResource(R.string.btn_no))
                }
            }
        )
    }
}
"""

# Code content for AdminScreen.kt
files_to_update[f"{base_path}/ui/screens/AdminScreen.kt"] = r"""package com.rk.downloader.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.rk.downloader.config.AdminConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.*

data class DeviceRecord(
    val uuid: String,
    val model: String,
    val os: String,
    val installTime: Long,
    val lastActive: Long
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    
    var totalDevices by remember { mutableIntStateOf(0) }
    var activeDevices7Days by remember { mutableIntStateOf(0) }
    var deviceList by remember { mutableStateOf<List<DeviceRecord>>(emptyList()) }

    fun fetchStats() {
        isLoading = true
        errorMsg = null
        scope.launch {
            try {
                val stats = withContext(Dispatchers.IO) {
                    val trackerUrl = AdminConfig.getServerUrl(context)
                    if (trackerUrl.isEmpty()) {
                        throw Exception("Local XAMPP tracker URL is not configured.")
                    }

                    val request = Request.Builder()
                        .url("$trackerUrl/admin_api.php")
                        .build()

                    val client = OkHttpClient()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) throw Exception("Local server connection failed. Code ${response.code}")
                        val bodyStr = response.body?.string() ?: "[]"
                        
                        val records = mutableListOf<DeviceRecord>()
                        val jsonArray = JSONArray(bodyStr)
                        
                        for (i in 0 until jsonArray.length()) {
                            val deviceObj = jsonArray.getJSONObject(i)
                            records.add(
                                DeviceRecord(
                                    uuid = deviceObj.optString("uuid", ""),
                                    model = deviceObj.optString("model", "Unknown Device"),
                                    os = deviceObj.optString("os", "Unknown OS"),
                                    installTime = deviceObj.optLong("install_time", 0L),
                                    lastActive = deviceObj.optLong("last_active", 0L)
                                )
                            )
                        }
                        records
                    }
                }

                val currentTime = System.currentTimeMillis()
                val sevenDaysAgo = currentTime - (7L * 24L * 60L * 60L * 1000L)

                deviceList = stats.sortedByDescending { it.lastActive }
                totalDevices = stats.size
                activeDevices7Days = stats.count { it.lastActive > sevenDaysAgo }
                isLoading = false
            } catch (e: Exception) {
                errorMsg = e.message ?: "Failed to retrieve statistics"
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        fetchStats()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Admin Dashboard (XAMPP)") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (errorMsg != null) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = errorMsg!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    Button(onClick = { fetchStats() }) {
                        Text("Retry")
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Card(
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Total Installs", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = totalDevices.toString(),
                                    style = MaterialTheme.typography.headlineLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Card(
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Active (7 Days)", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = activeDevices7Days.toString(),
                                    style = MaterialTheme.typography.headlineLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "Device Distribution (${deviceList.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    HorizontalDivider()

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(top = 8.dp)
                    ) {
                        items(deviceList) { record ->
                            val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                            val dateStr = if (record.lastActive > 0) sdf.format(Date(record.lastActive)) else "Never"

                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = record.model,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = record.os,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Last Active: $dateStr",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
"""

# Code content for SettingsScreen.kt
files_to_update[f"{base_path}/ui/screens/SettingsScreen.kt"] = r"""package com.rk.downloader.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import com.rk.downloader.R
import com.rk.downloader.config.AdminConfig
import com.rk.downloader.ui.components.BannerAdView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onNavigateToAdmin: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    
    // Tap-detection variables for admin entry
    var versionTapCount by remember { mutableStateOf(0) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var passwordInput by remember { mutableStateOf("") }
    var isPasswordError by remember { mutableStateOf(false) }

    // Dynamic Server URL Configuration
    var serverUrl by remember { mutableStateOf(AdminConfig.getServerUrl(context)) }
    var isEditingServerUrl by remember { mutableStateOf(false) }

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
                .verticalScroll(scrollState)
        ) {
            Text(
                text = stringResource(R.string.tab_settings),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Server URL Configuration Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Server Configuration",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (isEditingServerUrl) {
                        OutlinedTextField(
                            value = serverUrl,
                            onValueChange = { serverUrl = it },
                            label = { Text("Server URL") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    AdminConfig.setServerUrl(context, serverUrl)
                                    isEditingServerUrl = false
                                    Toast.makeText(context, "Server URL updated!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Save")
                            }
                            TextButton(
                                onClick = {
                                    serverUrl = AdminConfig.getServerUrl(context)
                                    isEditingServerUrl = false
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Cancel")
                            }
                        }
                    } else {
                        Text(
                            text = "Current Server: ${AdminConfig.getServerUrl(context)}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { isEditingServerUrl = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Edit Server Address")
                        }
                    }
                }
            }

            // Monetization Section
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Row(modifier = Modifier.padding(16.dp)) {
                    Icon(
                        imageVector = Icons.Default.MonetizationOn,
                        contentDescription = "Monetization",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.admob_title),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.admob_desc),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // Help & Instructions Section
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Row(modifier = Modifier.padding(16.dp)) {
                    Icon(
                        imageVector = Icons.Default.QuestionMark,
                        contentDescription = "Help",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.help_title),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.help_desc),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // Disclaimer Section
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Row(modifier = Modifier.padding(16.dp)) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Disclaimer",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.disclaimer_title),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.disclaimer_desc),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // Version info (Acts as the hidden Admin Panel gateway)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null // Disables ripple effect to keep it fully hidden
                    ) {
                        versionTapCount++
                        if (versionTapCount >= 5) {
                            versionTapCount = 0
                            showPasswordDialog = true
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.app_version),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Show banner ad at the bottom of the screen
        BannerAdView(modifier = Modifier.padding(top = 16.dp))
    }

    // Password Prompt for Admin Screen
    if (showPasswordDialog) {
        AlertDialog(
            onDismissRequest = {
                showPasswordDialog = false
                passwordInput = ""
                isPasswordError = false
            },
            title = { Text("Admin Authorisation") },
            text = {
                Column {
                    Text("Please enter your admin secret key to open the metrics dashboard.")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = {
                            passwordInput = it
                            isPasswordError = false
                        },
                        label = { Text("Admin Secret Key") },
                        visualTransformation = PasswordVisualTransformation(),
                        isError = isPasswordError,
                        singleLine = true
                    )
                    if (isPasswordError) {
                        Text(
                            text = "Incorrect credentials. Try again.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (passwordInput == AdminConfig.ADMIN_SECRET_KEY) {
                        showPasswordDialog = false
                        passwordInput = ""
                        onNavigateToAdmin()
                    } else {
                        isPasswordError = true
                    }
                }) {
                    Text("Unlock")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPasswordDialog = false
                    passwordInput = ""
                    isPasswordError = false
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}
"""

# 2. Process and write all files
print("[1/3] Writing updated project source files...")
for file_path, content in files_to_update.items():
    dir_name = os.path.dirname(file_path)
    if not os.path.exists(dir_name):
        os.makedirs(dir_name)
    
    with open(file_path, "w", encoding="utf-8") as f:
        f.write(content)
    print(f" -> Updated: {file_path}")

print("")
print("[2/3] Project files updated successfully!")
print("Compiling APK using Android Studio components...")
print("")

# 3. Compile Project using Gradle wrapper Bat file
env = os.environ.copy()
env["JAVA_HOME"] = "C:\\\\Program Files\\\\Android\\\\Android Studio\\\\jbr"

try:
    process = subprocess.Popen(
        [".\\\\gradlew.bat", "assembleDebug"],
        env=env,
        shell=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        bufsize=1
    )
    
    for line in iter(process.stdout.readline, ""):
        print(line, end="")
        
    process.stdout.close()
    return_code = process.wait()
    
    print("")
    if return_code == 0:
        print("=======================================================")
        print("[SUCCESS] Build Successful! APK generated.")
        print("APK Path: app/build/outputs/apk/debug/app-debug.apk")
        print("=======================================================")
    else:
        print("=======================================================")
        print(f"[ERROR] Build failed with return code {return_code}")
        print("=======================================================")
        
except Exception as e:
    print(f"[ERROR] Failed to run gradlew build: {e}")


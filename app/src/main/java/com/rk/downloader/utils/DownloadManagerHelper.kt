package com.rk.downloader.utils

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import com.rk.downloader.R
import com.rk.downloader.data.DownloadedVideo
import java.io.File

object DownloadManagerHelper {
    private const val DOWNLOAD_SUBFOLDER = "RKDownloader"

    /**
     * Enqueues a file to Android's system DownloadManager.
     */
    fun startDownload(context: Context, url: String, title: String, quality: String, format: String): Long {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        
        // Clean title for a valid filename
        val sanitizedTitle = title.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        val extension = format.lowercase()
        // Example filename: video_title_HD_720p.mp4
        val qName = quality.replace(Regex("[^a-zA-Z0-9]"), "_")
        val fileName = "${if (sanitizedTitle.length > 50) sanitizedTitle.substring(0, 50) else sanitizedTitle}_$qName.$extension"

        val uri = Uri.parse(url)
        val request = DownloadManager.Request(uri).apply {
            setTitle(fileName)
            setDescription(context.getString(R.string.app_name))
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            
            // Create app specific download subfolder in public Downloads directory
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "$DOWNLOAD_SUBFOLDER/$fileName")
            
            val mimeType = if (extension == "mp3") "audio/mpeg" else "video/mp4"
            setMimeType(mimeType)
            
            // Enable scanning by MediaScanner so it immediately shows up in Gallery/Photos
            @Suppress("DEPRECATION")
            allowScanningByMediaScanner()
        }

        Toast.makeText(context, context.getString(R.string.toast_download_started), Toast.LENGTH_SHORT).show()
        return downloadManager.enqueue(request)
    }

    /**
     * Scans the RKDownloader subfolder and retrieves all downloaded files.
     */
    fun getDownloadedFiles(): List<DownloadedVideo> {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val appFolder = File(downloadsDir, DOWNLOAD_SUBFOLDER)
        
        if (!appFolder.exists() || !appFolder.isDirectory) {
            return emptyList()
        }

        val files = appFolder.listFiles { file ->
            file.isFile && (file.extension.lowercase() == "mp4" || file.extension.lowercase() == "mp3")
        } ?: return emptyList()

        return files.mapIndexed { index, file ->
            DownloadedVideo(
                id = index.toLong(),
                name = file.name,
                filePath = file.absolutePath,
                sizeBytes = file.length(),
                dateAdded = file.lastModified()
            )
        }.sortedByDescending { it.dateAdded }
    }

    /**
     * Deletes a downloaded file from storage.
     */
    fun deleteFile(video: DownloadedVideo): Boolean {
        val file = File(video.filePath)
        return if (file.exists()) {
            file.delete()
        } else {
            false
        }
    }
}

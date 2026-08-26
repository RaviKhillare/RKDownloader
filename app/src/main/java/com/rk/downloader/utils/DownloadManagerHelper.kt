package com.rk.downloader.utils

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

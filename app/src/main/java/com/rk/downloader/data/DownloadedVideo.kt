package com.rk.downloader.data

import java.io.File

data class DownloadedVideo(
    val id: Long, // Download Manager ID
    val name: String,
    val filePath: String,
    val sizeBytes: Long,
    val dateAdded: Long
) {
    val file: File
        get() = File(filePath)
    
    val exists: Boolean
        get() = file.exists()
}

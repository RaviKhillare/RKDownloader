package com.rk.downloader.data

data class VideoInfo(
    val title: String,
    val sourceUrl: String,
    val options: List<DownloadOption>
)

data class DownloadOption(
    val quality: String,  // e.g., "HD (720p)", "SD (360p)", "Audio Only (128kbps)"
    val format: String,   // e.g., "MP4", "MP3"
    val downloadUrl: String,
    val sizeBytes: Long = 0L
)

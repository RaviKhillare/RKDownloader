package com.rk.downloader.data

/**
 * Standardized media extraction result representing a resolved direct downloadable stream.
 */
data class ExtractionResult(
    val streamUrl: String,
    val title: String,
    val quality: String = "HD",
    val thumbnail: String? = null,
    val filename: String = "",
    val format: String = "MP4"
) {
    /**
     * Converts to legacy VideoInfo model for seamless integration with VideoInfoBottomSheet
     */
    fun toVideoInfo(sourceUrl: String): VideoInfo {
        val safeTitle = title.ifEmpty { "Social Media Video" }
        return VideoInfo(
            title = safeTitle,
            sourceUrl = sourceUrl,
            options = listOf(
                DownloadOption(
                    quality = quality,
                    format = format,
                    downloadUrl = streamUrl
                )
            )
        )
    }
}

package com.rk.downloader.utils

/**
 * Enumeration of supported media platforms with clean regex-based detection.
 */
enum class SupportedPlatform(
    val displayName: String,
    val defaultPortalUrl: String
) {
    YOUTUBE("YouTube", "https://en.savefrom.net/"),
    INSTAGRAM("Instagram", "https://snapsave.app/"),
    FACEBOOK("Facebook", "https://snapsave.app/"),
    TIKTOK("TikTok", "https://ssstik.io/"),
    TWITTER("Twitter/X", "https://en.savefrom.net/"),
    PINTEREST("Pinterest", "https://en.savefrom.net/"),
    THREADS("Threads", "https://snapsave.app/"),
    DAILYMOTION("Dailymotion", "https://en.savefrom.net/"),
    GENERIC("Web Video", "https://en.savefrom.net/");

    companion object {
        fun detect(url: String): SupportedPlatform {
            val lower = url.lowercase().trim()
            return when {
                Regex("https?://(www\\.)?(youtube\\.com|youtu\\.be)/.+").containsMatchIn(lower) -> YOUTUBE
                Regex("https?://(www\\.)?instagram\\.com/.+").containsMatchIn(lower) -> INSTAGRAM
                Regex("https?://(www\\.|m\\.)?(facebook\\.com|fb\\.watch)/.+").containsMatchIn(lower) -> FACEBOOK
                Regex("https?://(www\\.|vm\\.|vt\\.)?tiktok\\.com/.+").containsMatchIn(lower) -> TIKTOK
                Regex("https?://(www\\.)?(twitter\\.com|x\\.com)/.+").containsMatchIn(lower) -> TWITTER
                Regex("https?://(www\\.|[a-z]{2}\\.)?(pinterest\\.[a-z.]+|pin\\.it)/.+").containsMatchIn(lower) -> PINTEREST
                Regex("https?://(www\\.)?threads\\.net/.+").containsMatchIn(lower) -> THREADS
                Regex("https?://(www\\.)?(dailymotion\\.com|dai\\.ly)/.+").containsMatchIn(lower) -> DAILYMOTION
                else -> GENERIC
            }
        }
    }
}

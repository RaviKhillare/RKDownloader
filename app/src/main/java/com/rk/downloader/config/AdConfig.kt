package com.rk.downloader.config

object AdConfig {
    // Set to false when ready to publish to Play Store with production Ad Units
    const val IS_TEST_MODE = true

    // AdMob App ID: ca-app-pub-3940256099942544~3347511713 (Configured in AndroidManifest.xml)
    
    // Banner Ad Unit IDs
    private const val TEST_BANNER_ID = "ca-app-pub-3940256099942544/6300978111"
    private const val PROD_BANNER_ID = "YOUR_PRODUCTION_BANNER_AD_UNIT_ID" // Swap this for Play Store

    // Interstitial Ad Unit IDs
    private const val TEST_INTERSTITIAL_ID = "ca-app-pub-3940256099942544/1033173712"
    private const val PROD_INTERSTITIAL_ID = "YOUR_PRODUCTION_INTERSTITIAL_AD_UNIT_ID" // Swap this for Play Store

    val bannerAdUnitId: String
        get() = if (IS_TEST_MODE) TEST_BANNER_ID else PROD_BANNER_ID

    val interstitialAdUnitId: String
        get() = if (IS_TEST_MODE) TEST_INTERSTITIAL_ID else PROD_INTERSTITIAL_ID
}

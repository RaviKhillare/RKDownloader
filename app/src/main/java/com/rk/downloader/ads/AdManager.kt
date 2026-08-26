package com.rk.downloader.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.rk.downloader.config.AdConfig

object AdManager {
    private const val TAG = "AdManager"
    private var mInterstitialAd: InterstitialAd? = null
    private var isAdLoading = false
    private var lastAdShowTime = 0L
    private const val AD_COOLDOWN_MS = 30000L // 30 seconds cooldown between interstitial ads

    fun loadInterstitialAd(context: Context) {
        if (mInterstitialAd != null || isAdLoading) return

        isAdLoading = true
        val adRequest = AdRequest.Builder().build()
        val adUnitId = AdConfig.interstitialAdUnitId

        InterstitialAd.load(
            context,
            adUnitId,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.e(TAG, "Ad failed to load: ${adError.message}")
                    mInterstitialAd = null
                    isAdLoading = false
                }

                override fun onAdLoaded(interstitialAd: InterstitialAd) {
                    Log.d(TAG, "Ad was loaded.")
                    mInterstitialAd = interstitialAd
                    isAdLoading = false
                }
            }
        )
    }

    fun showInterstitialAd(activity: Activity, onAdClosed: () -> Unit) {
        val currentTime = System.currentTimeMillis()
        
        // Respect the cooldown so users are not bombarded
        if (currentTime - lastAdShowTime < AD_COOLDOWN_MS) {
            onAdClosed()
            return
        }

        val interstitial = mInterstitialAd
        if (interstitial != null) {
            interstitial.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    Log.d(TAG, "Ad dismissed fullscreen content.")
                    mInterstitialAd = null
                    lastAdShowTime = System.currentTimeMillis()
                    // Preload the next ad
                    loadInterstitialAd(activity)
                    onAdClosed()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    Log.e(TAG, "Ad failed to show: ${adError.message}")
                    mInterstitialAd = null
                    loadInterstitialAd(activity)
                    onAdClosed()
                }

                override fun onAdShowedFullScreenContent() {
                    Log.d(TAG, "Ad showed fullscreen content.")
                }
            }
            interstitial.show(activity)
        } else {
            Log.d(TAG, "The interstitial ad wasn't ready yet.")
            loadInterstitialAd(activity)
            onAdClosed()
        }
    }
}

package com.rk.downloader

import android.app.Application
import com.google.android.gms.ads.MobileAds

class DownloaderApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize the Google Mobile Ads SDK
        MobileAds.initialize(this) {}
    }
}

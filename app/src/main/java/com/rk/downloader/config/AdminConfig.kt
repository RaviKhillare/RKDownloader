package com.rk.downloader.config

import java.util.Locale

object AdminConfig {
    // TODO: Paste your Firebase Realtime Database URL here (e.g., "https://yourproject-rtdb.firebaseio.com")
    // Keep it empty or default for testing. Do not add a trailing slash.
    const val FIREBASE_DATABASE_URL = "https://rkdownloader-default-rtdb.firebaseio.com"

    // Secret key to access the hidden Admin Panel inside the app (Settings -> click Version 5 times)
    const val ADMIN_SECRET_KEY = "admin123"
}

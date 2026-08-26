package com.rk.downloader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.rk.downloader.ads.AdManager
import com.rk.downloader.ui.screens.AdminScreen
import com.rk.downloader.ui.screens.BrowserScreen
import com.rk.downloader.ui.screens.DownloadsScreen
import com.rk.downloader.ui.screens.MainScreen
import com.rk.downloader.ui.screens.SettingsScreen
import com.rk.downloader.ui.theme.RKDownloaderTheme
import com.rk.downloader.utils.TrackerManager
import com.rk.downloader.utils.UpdateInfo
import com.rk.downloader.utils.UpdateManager
import kotlinx.coroutines.launch

enum class DownloaderTab {
    DOWNLOADER, BROWSER, DOWNLOADS, SETTINGS
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Preload the first interstitial ad so it's ready when the user starts a download
        AdManager.loadInterstitialAd(this)

        // Check and prompt the user for storage permission depending on API level
        checkAndRequestPermissions()

        // Sync device tracking details on startup
        lifecycleScope.launch {
            TrackerManager.registerDevice(this@MainActivity)
        }

        setContent {
            RKDownloaderTheme {
                var currentTab by remember { mutableStateOf(DownloaderTab.DOWNLOADER) }
                var passedBrowserUrl by remember { mutableStateOf("https://www.google.com") }
                
                // Overlay state to toggle hidden Admin panel screen
                var isAdminActive by remember { mutableStateOf(false) }
                
                // GitHub Update dialog state
                var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
                
                val context = LocalContext.current

                // Check for updates on startup
                LaunchedEffect(Unit) {
                    val info = UpdateManager.checkForUpdates(context)
                    if (info != null) {
                        updateInfo = info
                    }
                }

                // If admin overlay is activated, present the admin dashboard directly
                if (isAdminActive) {
                    AdminScreen(onBack = { isAdminActive = false })
                } else {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        bottomBar = {
                            NavigationBar {
                                NavigationBarItem(
                                    selected = currentTab == DownloaderTab.DOWNLOADER,
                                    onClick = { currentTab = DownloaderTab.DOWNLOADER },
                                    label = { Text(stringResource(R.string.tab_downloader)) },
                                    icon = { Icon(Icons.Default.Download, contentDescription = "Downloader") }
                                )
                                NavigationBarItem(
                                    selected = currentTab == DownloaderTab.BROWSER,
                                    onClick = { currentTab = DownloaderTab.BROWSER },
                                    label = { Text(stringResource(R.string.tab_browser)) },
                                    icon = { Icon(Icons.Default.Language, contentDescription = "Browser") }
                                )
                                NavigationBarItem(
                                    selected = currentTab == DownloaderTab.DOWNLOADS,
                                    onClick = { currentTab = DownloaderTab.DOWNLOADS },
                                    label = { Text(stringResource(R.string.tab_downloads)) },
                                    icon = { Icon(Icons.Default.Folder, contentDescription = "Downloads") }
                                )
                                NavigationBarItem(
                                    selected = currentTab == DownloaderTab.SETTINGS,
                                    onClick = { currentTab = DownloaderTab.SETTINGS },
                                    label = { Text(stringResource(R.string.tab_settings)) },
                                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") }
                                )
                            }
                        }
                    ) { innerPadding ->
                        when (currentTab) {
                            DownloaderTab.DOWNLOADER -> MainScreen(
                                modifier = Modifier.padding(innerPadding),
                                onNavigateToBrowser = { url ->
                                    passedBrowserUrl = url
                                    currentTab = DownloaderTab.BROWSER
                                }
                            )
                            DownloaderTab.BROWSER -> BrowserScreen(
                                modifier = Modifier.padding(innerPadding),
                                initialUrl = passedBrowserUrl
                            )
                            DownloaderTab.DOWNLOADS -> DownloadsScreen(
                                modifier = Modifier.padding(innerPadding)
                            )
                            DownloaderTab.SETTINGS -> SettingsScreen(
                                modifier = Modifier.padding(innerPadding),
                                onNavigateToAdmin = { isAdminActive = true }
                            )
                        }
                    }
                }

                // Show Update Dialog if update metadata is received
                updateInfo?.let { info ->
                    AlertDialog(
                        onDismissRequest = {
                            if (!info.isForceUpdate) {
                                updateInfo = null
                            }
                        },
                        title = { Text("ॲप अपडेट उपलब्ध आहे (Update Available)") },
                        text = {
                            Column {
                                Text(
                                    text = "Version ${info.latestVersionName} is now available.",
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(text = info.releaseNotes)
                            }
                        },
                        confirmButton = {
                            Button(onClick = {
                                try {
                                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(info.updateUrl))
                                    context.startActivity(browserIntent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Cannot open download link", Toast.LENGTH_SHORT).show()
                                }
                                if (!info.isForceUpdate) {
                                    updateInfo = null
                                }
                            }) {
                                Text("अपडेट करा (Update)")
                            }
                        },
                        dismissButton = {
                            if (!info.isForceUpdate) {
                                TextButton(onClick = { updateInfo = null }) {
                                    Text("नंतर (Later)")
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()
        
        // Write permission is required up to API 29 (Android 10)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        
        // Read permission depends on Android version
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val ungranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (ungranted.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, ungranted.toTypedArray(), 101)
        }
    }
}

# RKDownloader - Complete App Specification & Master AI Prompt

> **Instructions for Gemini / AI**: 
> You are an expert Android developer specializing in Kotlin and modern Jetpack Compose. 
> Below is the complete architecture, source code breakdown, dependencies, and configuration for the **RKDownloader** Android application. 
> Use this context whenever the developer asks to add new features, refactor code, fix bugs, or optimize the application.

---

## 1. Application Overview
* **App Name**: RKDownloader
* **Package Name**: `com.rk.downloader`
* **Application ID**: `com.rk.downloader`
* **Primary Function**: A modern, high-speed, all-in-one social media video and audio downloader for Android. It supports downloading media from **YouTube (Shorts & Videos), Instagram (Reels, Posts, Stories), Facebook (Watch & Videos), TikTok (Watermark-free), Twitter / X**, and direct web video links.
* **Key Architecture Model**: 100% Serverless. Uses direct cloud APIs (Cobalt mirrors) + integrated third-party web portals (SaveFrom.net, SnapSave, SSYouTube, Y2Mate) with native WebView download interception + Cloud Supabase PostgreSQL database for analytics.

---

## 2. Technical Specifications & Environment

| Parameter | Specification / Value |
| :--- | :--- |
| **Language** | Kotlin 1.9.24 |
| **UI Framework** | Jetpack Compose (Material 3) |
| **Compile SDK** | Android 15 (API 35) |
| **Target SDK** | Android 15 (API 35) |
| **Min SDK** | Android 7.0 (API 24) |
| **JVM Target** | Java 21 (`JavaVersion.VERSION_21`) |
| **Android Gradle Plugin** | 8.13.2 |
| **Networking** | OkHttp 4.12.0, JSoup 1.18.1 |
| **Monetization** | Google Play Services Ads (AdMob 23.3.0) |
| **Database / Analytics** | Cloud Supabase (PostgREST REST API) |
| **Storage Destination** | Public `Downloads/RKDownloader/` directory |
| **File Sharing / Playback** | Android `FileProvider` (`com.rk.downloader.fileprovider`) |

---

## 3. Project File Structure & Core Components

```
RKDownloader/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml                  # Permissions, AdMob ID, FileProvider configuration
│   │   ├── res/xml/
│   │   │   ├── file_paths.xml                  # FileProvider download directories definition
│   │   │   └── network_security_config.xml     # Cleartext traffic and domain configurations
│   │   └── java/com/rk/downloader/
│   │       ├── DownloaderApp.kt                # Application class initializing Mobile Ads (AdMob)
│   │       ├── MainActivity.kt                 # Main entry activity, tab navigation, permission handler
│   │       ├── ads/
│   │       │   └── AdManager.kt                # AdMob Banner & Interstitial ad loader/controllers
│   │       ├── config/
│   │       │   └── AdminConfig.kt              # Supabase URL, Anon Key, Admin Password, Extractor URL
│   │       ├── data/
│   │       │   ├── DownloadOption.kt           # Data model for quality/format stream options
│   │       │   ├── DownloadedVideo.kt          # Data model for completed download items
│   │       │   └── VideoInfo.kt                # Data model holding parsed video metadata
│   │       ├── ui/
│   │       │   ├── components/
│   │       │   │   ├── BannerAdView.kt         # Jetpack Compose AdMob Banner ad wrapper
│   │       │   │   └── VideoInfoBottomSheet.kt # Bottom sheet displaying quality options (MP4/MP3)
│   │       │   ├── screens/
│   │       │   │   ├── MainScreen.kt           # URL input, auto clipboard sniffer, third-party portals
│   │       │   │   ├── BrowserScreen.kt        # In-app browser with native DownloadListener & switcher
│   │       │   │   ├── DownloadsScreen.kt      # Download history, video player, share intent, delete
│   │       │   │   ├── SettingsScreen.kt       # Settings, dynamic extractor URL editor, hidden admin entry
│   │       │   │   └── AdminScreen.kt          # Live Supabase metrics dashboard (active devices, installs)
│   │       │   └── theme/                      # Compose theme, colors, typography
│   │       └── utils/
│   │           ├── ClipboardUtil.kt            # Detects social media URLs from system clipboard
│   │           ├── DownloadManagerHelper.kt    # Android DownloadManager queue, query, delete engine
│   │           ├── TrackerManager.kt           # UUID device registration and heartbeat to Supabase
│   │           ├── UpdateManager.kt            # Remote GitHub version checking (update.json)
│   │           └── VideoExtractor.kt           # Multi-engine cloud stream extractor (Cobalt v10/v7)
├── update_project.py                           # Standalone Python script to regenerate and compile app
└── APP_SPECIFICATION.md                        # Master specification documentation
```

---

## 4. Key Workflows & Implementation Details

### A. Video Extraction & Download Pipeline
1. **Tier 1: Fast Direct Extraction (`VideoExtractor.kt`)**:
   * Sends POST requests to configurable Cobalt API mirrors (`https://cobalt.api.red.gd` by default).
   * Incorporates a Chrome desktop `User-Agent` header to bypass Cloudflare anti-bot checks.
   * Handles both Cobalt v10 (`stream`, `redirect`, `picker`) and older Cobalt v7 formats.
   * If successful, displays `VideoInfoBottomSheet` allowing users to select video quality (720p, 1080p, MP4) or Audio (MP3).

2. **Tier 2: Third-Party Web Integration (`MainScreen.kt` & `BrowserScreen.kt`)**:
   * If fast direct extraction fails or if the user prefers, a dedicated button: **"SaveFrom.net द्वारे डाऊनलोड करा"** is available.
   * Quick-switch chips allow instant redirection to:
     * **SaveFrom.net**: `https://en.savefrom.net/?url=[ENCODED_URL]`
     * **SnapSave**: `https://snapsave.app/` (Specialized for Instagram Reels & Facebook Watch)
     * **SSYouTube**: `https://ssyoutube.com/` (Specialized for YouTube)
     * **Y2Mate**: `https://www.y2mate.com/`

3. **Tier 3: In-App Browser Download Interception (`BrowserScreen.kt`)**:
   * Uses an integrated Android `WebView`.
   * **Native `setDownloadListener`**: Whenever a user clicks any "Download" button on SaveFrom.net, SnapSave, or any other website, the URL, mimetype, and filename are intercepted.
   * **`shouldOverrideUrlLoading`**: Scans clicked links for direct `.mp4`, `.mp3`, or `googlevideo.com` media streams.
   * **Ad & Popup Protection**: `setSupportMultipleWindows(false)` and blocked non-HTTP popup intents prevent third-party spam ads from hijacking the browser window.
   * **Handoff to DownloadManager**: Downloads are processed in the background via Android's native `DownloadManagerHelper` directly to `Downloads/RKDownloader/`.

### B. Remote Analytics & Cloud Admin Dashboard (`Supabase`)
* **Endpoint**: `https://qwdvujdgkdzzmcxfdcub.supabase.co/rest/v1/devices`
* **Table Schema (`devices`)**:
  * `uuid` (Text / Primary Key): Unique device installation identifier.
  * `model` (Text): Device hardware model (e.g. `Pixel 7`, `Samsung SM-G998B`).
  * `os` (Text): Android OS version and API level.
  * `install_time` (BigInt / Milliseconds): First launch timestamp.
  * `last_active` (BigInt / Milliseconds): Latest app launch heartbeat timestamp.
* **App Synchronization (`TrackerManager.kt`)**:
  * On every app launch, checks if the UUID exists in Supabase.
  * If existing: sends `PATCH` updating `last_active`.
  * If new: sends `POST` creating the device record.
* **In-App Admin Screen (`AdminScreen.kt`)**:
  * Gateway: User goes to **Settings** -> taps the **App Version** label 5 consecutive times -> enters `ADMIN_SECRET_KEY` (`sb_secret_KzrCFBV30m1jkEk0I3sMAg_JkunNnHK`).
  * Queries `select=*` from Supabase and renders:
    * Total unique installs counter.
    * 7-day active user count.
    * Real-time device distribution list with device models, OS versions, and last-active dates.

### C. In-App Updates Mechanism (`UpdateManager.kt`)
* Checks remote JSON at: `https://raw.githubusercontent.com/RaviKhillare/RKDownloader/main/update.json`.
* Compares `latestVersionCode` with local `packageInfo.versionCode`.
* If a new version exists, prompts the user with release notes and a direct download/update link.

### D. Monetization (`AdManager.kt`)
* **Banner Ads**: Embedded at the bottom of all 4 tabs (`MainScreen`, `BrowserScreen`, `DownloadsScreen`, `SettingsScreen`).
* **Interstitial Ads**: Pre-cached on app launch and automatically displayed when the user initiates a download.

---

## 5. Ready-to-Use Prompts for Future Updates

Whenever you want Gemini to build a new feature, copy and use one of the templates below:

### Prompt 1: Adding a New Feature
```
I have an Android video downloader app named "RKDownloader" built with Jetpack Compose, Kotlin, and Material 3.
Current specifications:
- Architecture: Serverless (Cobalt API + SaveFrom.net WebView DownloadListener + Supabase analytics).
- Storage: Downloads/RKDownloader/ using Android DownloadManagerHelper.
- SDK: Min SDK 24, Target SDK 35, Java 21.

Feature request:
[Describe the feature you want, e.g., "Add dark/light theme toggle in settings", "Add a video preview player before downloading", "Add a history search bar in DownloadsScreen"]

Please provide the exact Kotlin code changes and explain which file to update.
```

### Prompt 2: Adding a New Video Platform / Extractor
```
In my Android app "RKDownloader", I want to add support for downloading from [Platform Name, e.g., Pinterest / Threads / Dailymotion].
In VideoExtractor.kt, show me how to handle or extract direct MP4 links for this platform, or how to add a dedicated third-party chip in MainScreen.kt and BrowserScreen.kt.
```

### Prompt 3: UI Redesign or Component Enhancement
```
In "RKDownloader" (Jetpack Compose, Material 3), I want to redesign [MainScreen / DownloadsScreen / SettingsScreen].
Current file: [paste or mention file name]
Desired UI changes: [describe layout, cards, animations, or colors].
Ensure all existing functionalities (clipboard detection, DownloadManagerHelper, AdMob banners) remain fully intact.
```

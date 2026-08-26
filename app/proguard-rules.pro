# Add project specific ProGuard rules here.
# You can control the set of keep rules in this file.

# For JSoup
-keep class org.jsoup.** { *; }

# For OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# For Google Mobile Ads SDK (AdMob)
-keep class com.google.android.gms.ads.** { *; }
-keep class com.google.ads.** { *; }

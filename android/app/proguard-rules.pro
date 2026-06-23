# Keep everything in our own app — we're not obfuscating, just trimming dead
# code pulled in by AndroidX/Material/Kotlin stdlib/OkHttp.
-keep class com.dy.tiktokmode.** { *; }

# WebView JS bridges (annotation-driven entry points).
-keepattributes JavascriptInterface
-keepattributes *Annotation*
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# OkHttp / Okio platform-specific classes we don't actually link to.
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn org.bouncycastle.**

# Material BottomSheetDialog reflectively references coordinator behaviors.
-keep class com.google.android.material.bottomsheet.** { *; }

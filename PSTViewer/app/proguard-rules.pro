# Keep all PST library classes
-keep class com.pff.** { *; }
-dontwarn com.pff.**

# Keep the app's own classes
-keep class com.pstviewer.** { *; }

# WebView
-keepclassmembers class * extends android.webkit.WebViewClient {
    public *;
}

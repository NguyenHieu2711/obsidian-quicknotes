# Giữ nguyên JavascriptInterface cho Android WebView trên bản Release R8
-keepclassmembers class com.nguyenhieu.quicknotes.MainActivity$AndroidWebBridge {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes JavascriptInterface
-keep class com.nguyenhieu.quicknotes.** { *; }

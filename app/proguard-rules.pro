# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
# --- FIX LỖI R8 CHO GOOGLE API CLIENT & APACHE HTTP ---
# Bỏ qua cảnh báo thiếu class javax.naming (dùng cho LDAP, không có trên Android)
-dontwarn javax.naming.**
-dontwarn javax.naming.directory.**
-dontwarn javax.naming.ldap.**

# Bỏ qua cảnh báo thiếu class org.ietf.jgss (GSS-API, không có trên Android)
-dontwarn org.ietf.jgss.**

# Bỏ qua các cảnh báo liên quan đến Apache HTTP Legacy
-dontwarn org.apache.http.**
-dontwarn android.net.http.SslError
-dontwarn android.webkit.WebViewClient

# Giữ lại các class cần thiết của Google API Client để tránh lỗi Runtime
-keep class com.google.api.client.** { *; }
-keep class com.google.api.services.drive.** { *; }
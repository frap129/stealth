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

-keepclassmembers class * extends java.lang.Enum {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keep class org.bouncycastle.jsse.provider.ImportX509TrustManager_7 {
    <init>(javax.net.ssl.X509ExtendedTrustManager);
}

-keep class org.bouncycastle.jsse.provider.ExportX509TrustManager_7 {
    <init>(org.bouncycastle.jsse.BCX509ExtendedTrustManager);
}

-keep class org.bouncycastle.jsse.provider.ImportSSLSession_7 {
    <init>(javax.net.ssl.ExtendedSSLSession);
}

-keep class org.bouncycastle.jsse.provider.ImportSSLSession_8 {
    <init>(javax.net.ssl.ExtendedSSLSession);
}

-keep class org.bouncycastle.jsse.provider.ExportSSLSession_7 {
    <init>(org.bouncycastle.jsse.BCExtendedSSLSession);
}

-keep class org.bouncycastle.jsse.provider.ExportSSLSession_8 {
    <init>(org.bouncycastle.jsse.BCExtendedSSLSession);
}

-dontwarn org.conscrypt.Conscrypt$Version
-dontwarn org.conscrypt.Conscrypt
-dontwarn org.conscrypt.ConscryptHostnameVerifier
-dontwarn org.openjsse.javax.net.ssl.SSLParameters
-dontwarn org.openjsse.javax.net.ssl.SSLSocket
-dontwarn org.openjsse.net.ssl.OpenJSSE
-dontwarn org.slf4j.impl.StaticLoggerBinder

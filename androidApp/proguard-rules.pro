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
# ---------------------------------------------------------------------------
# 1.3.0 release shrinking
# ---------------------------------------------------------------------------
-keepattributes *Annotation*,InnerClasses,EnclosingMethod,Signature,Exceptions,SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Rhino evaluates user JS through reflection; every runtime class must survive.
-keep class org.mozilla.javascript.** { *; }
-dontwarn org.mozilla.javascript.**
-keep class com.kyant.backdrop.catalog.ScriptEngine { *; }

# Enum names are read back from SharedPreferences via valueOf().
-keepclassmembers enum com.kyant.backdrop.catalog.appstore.music.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Optional OkHttp / Rhino platform classes that are absent on Android.
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn org.slf4j.**

# AndroidX / Media3 / Compose ship their own consumer rules; keep the entry point.
-keep class com.kyant.backdrop.catalog.MainActivity { *; }


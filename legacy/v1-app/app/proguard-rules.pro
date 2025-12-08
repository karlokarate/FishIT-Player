-keep class androidx.room.** { *; }
-keep class kotlinx.serialization.** { *; }
-dontwarn org.xmlpull.v1.**
-dontwarn org.jetbrains.annotations.**


# Logs in Release entfernen (CPU sparen) - nur Debug/Verbose, Warnings behalten für Crashlytics
-assumenosideeffects class android.util.Log {
  public static *** d(...);
  public static *** v(...);
}

# ===== Firebase & Google Services =====
# Keep Firebase classes
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# Crashlytics - keep line numbers for readable stack traces
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception

# Keep custom exception classes for better crash reports
-keep class com.chris.m3usuite.**Exception { *; }
-keep class com.chris.m3usuite.**Error { *; }

# ObjectBox Entities und JNI
-keep class io.objectbox.** { *; }
-keep @io.objectbox.annotation.Entity class * { *; }

# Media3: Warnungen dämpfen, Shrink weiter zulassen
-dontwarn androidx.media3.**

# OkHttp/Okio
-dontwarn okhttp3.**
-dontwarn okio.**

# Coroutines Debug ausschließen (nur debugImplementation zulassen)
-dontwarn kotlinx.coroutines.debug.**

# TDLib (tdl-coroutines-android) - Keep DTO classes and native bindings
-keep class dev.g000sha256.tdl.dto.** { *; }
-keep class dev.g000sha256.tdl.TdlClient { *; }
-keep class dev.g000sha256.tdl.** { *; }
-keepclassmembers class dev.g000sha256.tdl.** {
    native <methods>;
}

# LeakCanary - Only for debug builds, but add rules to be safe
-dontwarn com.squareup.leakcanary.**

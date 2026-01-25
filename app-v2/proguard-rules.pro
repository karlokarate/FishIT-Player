# ==============================================================================
# FishIT-Player v2 - R8/ProGuard Configuration
# Best Practice Configuration for maximum shrinking with stability
# ==============================================================================

# ==============================================================================
# GENERAL OPTIMIZATION SETTINGS
# ==============================================================================

# R8 Full Mode Compatibility (AGP 8.7.x + Kotlin 2.1.0)
# Enable aggressive optimizations while maintaining stability
-optimizationpasses 5
-allowaccessmodification
-dontpreverify
-repackageclasses ''

# Keep source file and line number information for better stack traces
-keepattributes SourceFile,LineNumberTable

# Hide original source file name in stack traces (optional privacy)
-renamesourcefileattribute SourceFile

# Keep annotation information (required for Hilt, Room, etc.)
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# Remove debug/verbose logs in release builds (CPU optimization)
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}

# ==============================================================================
# KOTLIN
# ==============================================================================

# Kotlin metadata for reflection
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-dontnote kotlin.**

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.debug.**
-dontwarn kotlinx.coroutines.flow.**FlowKt**

# Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
}
-keepclasseswithmembers class **$$serializer {
    *** INSTANCE;
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1>$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}

# ==============================================================================
# JETPACK COMPOSE
# ==============================================================================

# Keep Compose runtime
-keep class androidx.compose.runtime.** { *; }

# Keep @Composable annotated functions (critical for UI)
-keep @androidx.compose.runtime.Composable class * { *; }
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

# Keep Compose Material components
-keep class androidx.compose.material3.** { *; }
-keep class androidx.compose.material.icons.** { *; }

# Keep Compose Navigation
-keep class androidx.navigation.** { *; }
-keep class * extends androidx.navigation.Navigator { *; }

# ==============================================================================
# HILT / DAGGER DEPENDENCY INJECTION
# ==============================================================================

# Keep Hilt-generated components
-keep class * extends dagger.hilt.android.internal.managers.ComponentSupplier { *; }
-keep class * extends dagger.hilt.internal.GeneratedComponent { *; }
-keep class * extends dagger.hilt.android.internal.lifecycle.DefaultViewModelFactories$** { *; }

# Keep @Inject annotated classes
-keepclassmembers,allowobfuscation class * {
    @javax.inject.Inject <init>(...);
    @javax.inject.Inject <fields>;
}

# Keep @HiltViewModel annotated classes
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }

# Keep Hilt entry points
-keep @dagger.hilt.android.EntryPoint class * { *; }
-keep @dagger.hilt.InstallIn class * { *; }

# Keep Dagger modules
-keep @dagger.Module class * { *; }
-keep @dagger.hilt.InstallIn class * { *; }

# ==============================================================================
# OBJECTBOX DATABASE
# ==============================================================================

# Keep ObjectBox entities and their properties
-keep class io.objectbox.** { *; }
-keep @io.objectbox.annotation.Entity class * { *; }
-keepclassmembers @io.objectbox.annotation.Entity class * {
    <fields>;
    <init>(...);
}

# Keep ObjectBox generated classes
-keep class **_
-keep class **_$** { *; }

# Keep ObjectBox converters
-keep class * implements io.objectbox.converter.PropertyConverter { *; }

# Keep ObjectBox cursor classes
-keep class * extends io.objectbox.Cursor { *; }

# Keep ObjectBox model files
-keepclassmembers class * {
    @io.objectbox.annotation.* <fields>;
}

# ==============================================================================
# FIREBASE & GOOGLE SERVICES
# ==============================================================================

# Firebase Core
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# Firebase Analytics
-keep class com.google.android.gms.measurement.** { *; }

# Firebase Crashlytics - keep exception classes for readable reports
-keep public class * extends java.lang.Exception
-keep class com.fishit.player.**Exception { *; }
-keep class com.fishit.player.**Error { *; }

# Google Play Services
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# ==============================================================================
# TDLIB / TELEGRAM
# ==============================================================================

# TDLib DTO classes (critical for Telegram functionality)
-keep class dev.g000sha256.tdl.dto.** { *; }
-keep class dev.g000sha256.tdl.TdlClient { *; }
-keep class dev.g000sha256.tdl.** { *; }

# TDLib native bindings (JNI)
-keepclassmembers class dev.g000sha256.tdl.** {
    native <methods>;
}

# org.drinkless.td (fallback TDLib)
-keep class org.drinkless.td.** { *; }
-keepclassmembers class org.drinkless.td.** {
    native <methods>;
}

# ==============================================================================
# MEDIA3 / EXOPLAYER
# ==============================================================================

# Media3 core
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# ExoPlayer extensions
-keep class com.google.android.exoplayer2.** { *; }
-dontwarn com.google.android.exoplayer2.**

# FFmpeg extension
-keep class androidx.media3.decoder.ffmpeg.** { *; }
-keepclassmembers class androidx.media3.decoder.ffmpeg.** {
    native <methods>;
}

# ==============================================================================
# NETWORKING
# ==============================================================================

# OkHttp
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# Retrofit (if used in future)
-keep class retrofit2.** { *; }
-keepclassmembers class retrofit2.** { *; }
-dontwarn retrofit2.**

# ==============================================================================
# IMAGE LOADING (COIL)
# ==============================================================================

-keep class coil.** { *; }
-keep class coil3.** { *; }
-dontwarn coil.**
-dontwarn coil3.**

# ==============================================================================
# FISHIT-PLAYER v2 CORE
# ==============================================================================

# Keep RawMediaMetadata and related model classes
-keep class com.fishit.player.core.model.** { *; }
-keep class com.fishit.player.core.persistence.entity.** { *; }

# Keep pipeline DTOs
-keep class com.fishit.player.pipeline.**.dto.** { *; }

# Keep player model
-keep class com.fishit.player.core.player.model.** { *; }

# Keep playback context
-keep class com.fishit.player.playback.domain.** { *; }

# ==============================================================================
# ANDROID ARCHITECTURE COMPONENTS
# ==============================================================================

# ViewModel
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keep class * extends androidx.lifecycle.AndroidViewModel { *; }

# LiveData observers
-keepclassmembers class * {
    void observe*(...);
}

# SavedStateHandle
-keepclassmembers class * implements androidx.savedstate.SavedStateRegistry$SavedStateProvider {
    *;
}

# ==============================================================================
# REFLECTION & MISC
# ==============================================================================

# Keep enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep Parcelables
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Keep Serializable classes
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Keep R classes
-keepclassmembers class **.R$* {
    public static <fields>;
}

# ==============================================================================
# SUPPRESS WARNINGS
# ==============================================================================

# Common warnings that can be safely ignored
-dontwarn org.xmlpull.v1.**
-dontwarn org.jetbrains.annotations.**
-dontwarn javax.annotation.**
-dontwarn sun.misc.Unsafe
-dontwarn java.lang.invoke.**
-dontwarn org.codehaus.mojo.**

# LeakCanary (debug only)
-dontwarn com.squareup.leakcanary.**

# ==============================================================================
# R8 FULL MODE (AGGRESSIVE OPTIMIZATION)
# ==============================================================================
# Enabled in gradle.properties: android.enableR8.fullMode=true
# Compatible with: AGP 8.7.3, Kotlin 2.1.0

# R8 full mode specific optimizations
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-verbose

# Keep debugging information for crashes
-keepattributes SourceFile,LineNumberTable

# Optimization options for R8 full mode
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses

# ==============================================================================
# KOTLIN 2.1.0 + R8 COMPATIBILITY
# ==============================================================================

# Critical for Kotlin 2.1.0 + R8 Full Mode
-keep class kotlin.coroutines.Continuation
-keep class kotlin.coroutines.jvm.internal.** { *; }

# Kotlin metadata (required for reflection in Hilt/Dagger)
-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.** { *; }
-dontwarn kotlin.reflect.**

# Kotlin annotation retention
-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations,RuntimeInvisibleParameterAnnotations

# ==============================================================================

# Enable R8 full mode for maximum optimization
# This is controlled in gradle.properties with:
# android.enableR8.fullMode=true


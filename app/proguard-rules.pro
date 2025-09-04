-keep class androidx.room.** { *; }
-keep class kotlinx.serialization.** { *; }
-dontwarn org.xmlpull.v1.**
-dontwarn org.jetbrains.annotations.**

# TDLib (Telegram) – keep JNI-bound classes if present (support both package variants)
-keep class org.drinkless.td.libcore.telegram.** { *; }
-keep class org.drinkless.tdlib.** { *; }

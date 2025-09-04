# Keep TDLib classes if referenced; shipped via app proguard too
-keep class org.drinkless.td.libcore.telegram.** { *; }
# Also keep the TGX package layout
-keep class org.drinkless.tdlib.** { *; }

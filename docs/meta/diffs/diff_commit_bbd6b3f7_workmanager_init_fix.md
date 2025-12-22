# Commit: bbd6b3f7

**Message:** fix(manifest): WorkManager auto-init removal for release builds

**Author:** karlokarate
**Date:** Mon Dec 22 11:31:27 2025 +0000

## Summary

Fix WorkManager auto-initialization removal in AndroidManifest.xml to work properly in release builds. The previous configuration was incomplete and caused "WorkManager is already initialized" crashes in release APKs.

## Changed Files

| File | Change | Description |
|------|--------|-------------|
| app-v2/src/main/AndroidManifest.xml | MOD | Complete meta-data removal configuration |

## Problem

The previous manifest configuration:

```xml
<meta-data
    android:name="androidx.work.WorkManagerInitializer"
    tools:node="remove" />
```

Was not working in release builds because the `tools:node="remove"` directive requires an **exact match** of all attributes from the library's contribution.

## Solution

Added the missing `android:value="androidx.startup"` attribute to ensure complete match:

```xml
<meta-data
    android:name="androidx.work.WorkManagerInitializer"
    android:value="androidx.startup"
    tools:node="remove" />
```

Also added:

- `android:exported="false"` to the provider (required for Android 12+)
- Improved documentation comments referencing the guardrail contract

---

## Full Diff

```diff
diff --git a/app-v2/src/main/AndroidManifest.xml b/app-v2/src/main/AndroidManifest.xml
index 487a1708..02b9c4cc 100644
--- a/app-v2/src/main/AndroidManifest.xml
+++ b/app-v2/src/main/AndroidManifest.xml
@@ -12,7 +12,7 @@
         android:roundIcon="@android:drawable/sym_def_app_icon"
         android:supportsRtl="true"
         android:theme="@style/Theme.FishITPlayerV2">
-        
+
         <activity
             android:name=".MainActivity"
             android:exported="true"
@@ -25,19 +25,28 @@
         </activity>
 
         <!-- 
-            Remove WorkManager auto-initialization via AndroidX Startup.
+            MANDATORY: Remove WorkManager auto-initialization via AndroidX Startup.
             We use on-demand initialization via Configuration.Provider in FishItV2Application.
-            See: https://developer.android.com/topic/libraries/architecture/workmanager/advanced/custom-configuration
+            
+            The tools:node="remove" on the meta-data completely removes the WorkManagerInitializer
+            from the merged manifest, preventing conflicts with our custom Configuration.Provider.
+            
+            See:
+            https://developer.android.com/topic/libraries/architecture/workmanager/advanced/custom-configuration
+            Contract: WORKMANAGER_INITIALIZATION_GUARDRAIL.md
         -->
         <provider
             android:name="androidx.startup.InitializationProvider"
             android:authorities="com.fishit.player.v2.androidx-startup"
+            android:exported="false"
             tools:node="merge">
+            <!-- Remove WorkManager auto-init - we use Configuration.Provider -->
             <meta-data
                 android:name="androidx.work.WorkManagerInitializer"
+                android:value="androidx.startup"
                 tools:node="remove" />
         </provider>
-        
+
     </application>
 
-</manifest>
+</manifest>
\ No newline at end of file
```

## Verification

After this fix, run:

```bash
./scripts/build/check_no_workmanager_initializer.sh
```

Expected output: No WorkManagerInitializer entries in merged manifest.

## Related Documents

- [WORKMANAGER_INITIALIZATION_GUARDRAIL.md](../../../docs/WORKMANAGER_INITIALIZATION_GUARDRAIL.md)
- [Android WorkManager Custom Configuration](https://developer.android.com/topic/libraries/architecture/workmanager/advanced/custom-configuration)

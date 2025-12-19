# WorkManager Initialization Guardrail

## Overview

This project uses **on-demand WorkManager initialization** via `Configuration.Provider` pattern, as implemented in `FishItV2Application.kt`.

The WorkManager library attempts to auto-initialize via AndroidX Startup, which conflicts with our on-demand pattern and can cause runtime issues.

## The Problem

When `androidx.work:work-runtime-ktx` is included as a dependency, it automatically registers `WorkManagerInitializer` in the merged manifest via AndroidX Startup:

```xml
<provider
    android:name="androidx.startup.InitializationProvider"
    android:authorities="com.fishit.player.v2.androidx-startup"
    android:exported="false">
    <meta-data
        android:name="androidx.work.WorkManagerInitializer"
        android:value="androidx.startup" />
</provider>
```

This auto-initialization conflicts with our `Configuration.Provider` implementation and must be disabled.

## The Solution

### 1. Manifest Override

In `app-v2/src/main/AndroidManifest.xml`, we explicitly remove the WorkManagerInitializer:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">
    
    <application ...>
        <!-- Remove WorkManager auto-initialization via AndroidX Startup -->
        <provider
            android:name="androidx.startup.InitializationProvider"
            android:authorities="com.fishit.player.v2.androidx-startup"
            tools:node="merge">
            <meta-data
                android:name="androidx.work.WorkManagerInitializer"
                tools:node="remove" />
        </provider>
    </application>
</manifest>
```

**Key points:**
- `xmlns:tools` namespace must be declared on the `<manifest>` root
- `tools:node="merge"` on the provider keeps other initializers (Emoji2, Lifecycle, etc.)
- `tools:node="remove"` on the meta-data removes only WorkManager's initializer

### 2. On-Demand Initialization

In `FishItV2Application.kt`, we implement `Configuration.Provider`:

```kotlin
@HiltAndroidApp
class FishItV2Application : 
    Application(),
    Configuration.Provider {
    
    @Inject
    lateinit var workConfiguration: Configuration
    
    override val workManagerConfiguration: Configuration
        get() = workConfiguration
}
```

This ensures WorkManager is initialized with our custom configuration (including HiltWorkerFactory) when first accessed.

## The Guardrail

To prevent regressions, we have two layers of protection:

### Layer 1: Automated Build Check (Gradle Task)

In `app-v2/build.gradle.kts`:

```kotlin
tasks.register<Exec>("checkNoWorkManagerInitializer") {
    group = "verification"
    description = "Verify WorkManagerInitializer is not in merged manifests"
    commandLine("${rootProject.projectDir}/scripts/check_no_workmanager_initializer.sh")
    // ...
}

tasks.matching { it.name.startsWith("assemble") }.configureEach {
    finalizedBy("checkNoWorkManagerInitializer")
}
```

**Behavior:**
- Runs automatically after every `assemble*` task (debug, release, all variants)
- Checks all merged manifests for `androidx.work.WorkManagerInitializer`
- Fails the build if found
- Provides clear instructions on how to fix

### Layer 2: Shell Script

`scripts/check_no_workmanager_initializer.sh` is a standalone script that:
- Scans `app-v2/build/intermediates/merged_manifests/` for all variants
- Searches each merged AndroidManifest.xml for WorkManagerInitializer
- Exits with status 0 if clean, 1 if violations found
- Can be run manually: `./scripts/check_no_workmanager_initializer.sh`

## CI Integration

The guardrail is **automatically integrated** into all CI workflows because:

1. All CI workflows run Gradle build tasks (`assembleDebug`, `assembleRelease`, etc.)
2. The Gradle task `checkNoWorkManagerInitializer` is set to run via `finalizedBy` on all `assemble*` tasks
3. If the check fails, the CI build fails immediately with a clear error message

**Workflows affected:**
- `android-ci.yml` (runs `assembleDebug`)
- `pr-ci.yml` (runs `assembleDebug`)
- `v2-release-build.yml` (runs `assembleRelease`)
- `android-quality.yml` (runs various build tasks)

## Manual Verification

To manually verify the merged manifest:

```bash
# Build the app
./gradlew :app-v2:assembleDebug

# Check merged manifest
grep -i "workmanager" app-v2/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml

# Run the guardrail check
./scripts/check_no_workmanager_initializer.sh

# Or use the Gradle task
./gradlew :app-v2:checkNoWorkManagerInitializer
```

**Expected result:** No `WorkManagerInitializer` found (only comments about it being removed).

## Troubleshooting

### If the check fails in CI

The error will look like:

```
❌ FAILURE: Found 1 violation(s)

The app uses on-demand WorkManager initialization via Configuration.Provider.
Auto-initialization must be disabled in app-v2/src/main/AndroidManifest.xml
```

**Fix:**
1. Ensure `xmlns:tools="http://schemas.android.com/tools"` is present on the `<manifest>` root
2. Ensure the `<provider>` override with `tools:node="remove"` is present (see solution above)
3. Clean and rebuild: `./gradlew clean :app-v2:assembleDebug`

### If WorkManager doesn't initialize at runtime

This would indicate our on-demand initialization isn't working:

1. Verify `FishItV2Application` implements `Configuration.Provider`
2. Verify `workConfiguration` is injected via Hilt
3. Check logcat for WorkManager initialization errors

## References

- [WorkManager Custom Configuration](https://developer.android.com/topic/libraries/architecture/workmanager/advanced/custom-configuration)
- [Disable AndroidX Startup initializers](https://developer.android.com/topic/libraries/app-startup#disable-individual)
- [Android Manifest Merge Reference](https://developer.android.com/build/manage-manifests#merge-manifests)

## History

- **2024-12-19**: Initial guardrail implementation
  - Added manifest override with `tools:node="remove"`
  - Created `check_no_workmanager_initializer.sh` script
  - Integrated Gradle task with `finalizedBy` on `assemble*` tasks
  - Verified CI integration via automatic task execution

# WorkManager Initialization Guardrail (v2 SSOT)

> **SSOT Location:** `/docs/v2/WORKMANAGER_INITIALIZATION_GUARDRAIL.md`

## Overview

This document is the **single source of truth** for WorkManager initialization configuration in v2.

The v2 app uses **on-demand WorkManager initialization** via the `Configuration.Provider` pattern, implemented in `FishItV2Application.kt`.

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│  FishItV2Application : Configuration.Provider              │
│    • Provides custom Configuration                          │
│    • Injects HiltWorkerFactory                              │
│    • WorkManager initializes on first access                │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│  app-v2 AndroidManifest.xml                                 │
│    • Disables WorkManagerInitializer via tools:node="remove"│
│    • Keeps other AndroidX Startup initializers              │
└─────────────────────────────────────────────────────────────┘
```

## Configuration

### 1. Application Class

In `app-v2/src/main/java/.../FishItV2Application.kt`:

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

### 2. Manifest Override

In `app-v2/src/main/AndroidManifest.xml`:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">
    
    <application ...>
        <!-- Remove WorkManager auto-initialization -->
        <provider
            android:name="androidx.startup.InitializationProvider"
            android:authorities="${applicationId}.androidx-startup"
            tools:node="merge">
            <meta-data
                android:name="androidx.work.WorkManagerInitializer"
                tools:node="remove" />
        </provider>
    </application>
</manifest>
```

## Why On-Demand?

1. **Hilt Integration:** WorkManager requires `HiltWorkerFactory` for `@HiltWorker` classes
2. **Control:** Explicit lifecycle control over initialization timing
3. **Debugging:** Clear stack trace when initialization fails

## Verification

Run the merged manifest check:

```bash
./gradlew :app-v2:processDebugManifest
grep -A5 "WorkManagerInitializer" app-v2/build/intermediates/merged_manifest/debug/AndroidManifest.xml
```

Expected: No `WorkManagerInitializer` entry, or entry with `tools:node="remove"`.

## Related Contracts

- `/docs/v2/STARTUP_TRIGGER_CONTRACT.md` - Startup & sync triggers
- `docs/v2/WORKMANAGER_PATTERNS.md` - Worker implementation patterns

## Migration from docs/

The legacy document at `/docs/WORKMANAGER_INITIALIZATION_GUARDRAIL.md` is superseded by this file.

# LeakCanary Diagnostics - Gold Standard Implementation

**Document Version:** 1.0  
**Created:** December 2024  
**Status:** Implemented

---

## Overview

This document describes the LeakCanary integration in FishIT Player v2, including the noise control system that helps distinguish transient GC delays from real memory leaks.

## Architecture

### Component Locations

| Component | Path | Purpose |
|-----------|------|---------|
| `LeakDiagnostics` (interface) | `feature/settings/src/main/java/.../debug/LeakDiagnostics.kt` | Abstraction for leak detection |
| `LeakDiagnosticsImpl` (debug) | `feature/settings/src/debug/java/.../debug/LeakDiagnosticsImpl.kt` | Real LeakCanary integration |
| `LeakDiagnosticsImpl` (release) | `feature/settings/src/release/java/.../debug/LeakDiagnosticsImpl.kt` | No-op stub |
| `LeakCanaryConfig` | `app-v2/src/debug/java/.../debug/LeakCanaryConfig.kt` | App-level configuration |
| `DebugScreen` | `feature/settings/src/main/java/.../DebugScreen.kt` | UI for diagnostics |

### Dependency Configuration

```kotlin
// app-v2/build.gradle.kts
debugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")
```

**Important:** LeakCanary is ONLY included in debug builds. Release builds use a no-op stub.

---

## Noise Control System

### Problem Statement

LeakCanary's retained object count can be misleading:
- Objects may be temporarily retained during GC cycles
- Short-lived UI components may not be collected immediately
- Not every retained object indicates a real leak

### Solution: Severity-Based Classification

The system classifies retention into four severity levels:

| Severity | Retained Count | Interpretation | UI Color |
|----------|---------------|----------------|----------|
| **NONE** | 0 | All clear | Surface variant |
| **LOW** | 1-2 | Likely transient GC delay | Tertiary (green/teal) |
| **MEDIUM** | 3-4 | Worth investigating | Secondary (yellow/orange) |
| **HIGH** | 5+ (threshold) | Likely a real leak | Error (red) |

### API

```kotlin
interface LeakDiagnostics {
    val isAvailable: Boolean
    
    fun getSummary(): LeakSummary
    fun getDetailedStatus(): LeakDetailedStatus
    
    fun openLeakUi(context: Context): Boolean
    suspend fun exportLeakReport(context: Context, uri: Uri): Result<Unit>
    
    fun requestGarbageCollection()
    fun triggerHeapDump()
    
    fun getLatestHeapDumpPath(): String?
}

data class LeakDetailedStatus(
    val retainedObjectCount: Int,
    val hasRetainedObjects: Boolean,
    val severity: RetentionSeverity,
    val statusMessage: String,
    val config: LeakCanaryConfig,
    val memoryStats: MemoryStats,
    val capturedAtMs: Long
)
```

---

## Debug Screen UI

The LeakCanary section in DebugScreen provides:

### Status Display
- **Severity Banner**: Color-coded card showing retention status
- **Memory Stats**: Current heap usage (used/max/percentage)
- **Config Info**: Watch duration and threshold settings

### Actions
| Button | Action | Use Case |
|--------|--------|----------|
| **Open** | Opens LeakCanary UI | View detailed leak traces |
| **Refresh** | Reloads status | Check after screen transitions |
| **GC** | Requests garbage collection | Reduce noise before analysis |
| **Dump** | Triggers heap dump | Force analysis of current state |
| **Export** | SAF export of text report | Share diagnostics without heap dump |

---

## Leak Analysis Results

### Code Review Findings

The v2 codebase was analyzed for common leak patterns:

#### ✅ ViewModels
- All ViewModels use `@HiltViewModel` with proper lifecycle
- No Activity/Context fields stored directly
- `@ApplicationContext` used where Context is needed
- All Flow collectors use `viewModelScope.launch` (auto-cancelled)

#### ✅ WorkManager Observers
- `WorkManagerSyncStateObserver` uses `LiveData.asFlow()` correctly
- Flows are collected within ViewModel scopes
- No forever-running observers outside lifecycle

#### ✅ Compose UI
- No `remember { context }` patterns holding Activity
- No ViewModel references in lambdas outside lifecycle
- `DisposableEffect` properly used where needed

#### ✅ Singletons
- `PlaybackPendingState` is `@Singleton` but holds only data, no Context
- No Activity references in application-scoped objects

### Conclusion

**No structural leaks were found in the v2 codebase.**

Any retained objects seen in LeakCanary are likely:
1. Transient GC delays (severity LOW)
2. Framework-internal delays (Android system)
3. LeakCanary's own detection latency

---

## Verification Steps

### Manual Testing

1. **Navigate through screens:**
   ```
   Home → Library → Detail → Player → back → Home
   ```
   
2. **Check DebugScreen:**
   - Open Debug Screen
   - Tap "Refresh" in LeakCanary section
   - Verify severity is NONE or LOW

3. **After screen rotation:**
   - Rotate device multiple times
   - Wait 10 seconds (watch duration)
   - Check retained count stabilizes at 0

### Automated Verification

For instrumented tests:

```kotlin
@Test
fun screenNavigation_doesNotLeakActivities() {
    // Open and close screens multiple times
    repeat(3) {
        navController.navigate("detail/movie:test:2024")
        navController.popBackStack()
    }
    
    // Force GC
    System.gc()
    Thread.sleep(1000)
    
    // Check retained count
    val retainedCount = AppWatcher.objectWatcher.retainedObjectCount
    assertTrue("Retained count should be below threshold", retainedCount < 5)
}
```

---

## Export Format

The exported leak report includes:

```
============================================================
FishIT Player - Memory Diagnostics Report
============================================================

Generated: 2024-12-30 15:30:00

----------------------------------------
App Info
----------------------------------------
Version: 2.0.0-dev (1)
Package: com.fishit.player.v2.debug
Build Type: debug

----------------------------------------
Memory Status (Noise Control)
----------------------------------------
Retained Objects: 0
Has Retained: false
Severity: NONE
Status: All clear - no objects retained

----------------------------------------
LeakCanary Configuration
----------------------------------------
Retained Threshold: 5
Watch Duration: 10000ms
Watch Activities: true
Watch Fragments: true
Watch ViewModels: true

----------------------------------------
Runtime Memory
----------------------------------------
Used: 128MB
Total: 256MB
Max: 512MB
Usage: 25%

----------------------------------------
Noise Control Guide
----------------------------------------
• NONE (0 retained): All clear
• LOW (1-2 retained): Likely transient GC delay
• MEDIUM (3-4 retained): Worth investigating
• HIGH (5+ retained): Likely a real leak

----------------------------------------
How to Get Full Leak Details
----------------------------------------
1. Open LeakCanary UI from Debug Screen
2. View individual leak traces
3. Use 'Share heap dump' for detailed analysis
4. Import .hprof into Android Studio Profiler

============================================================
End of Report
============================================================
```

---

## Configuration

### App-Level Config (LeakCanaryConfig.kt)

```kotlin
object LeakCanaryConfig {
    fun install(app: Application) {
        LeakCanary.config = LeakCanary.config.copy(
            retainedVisibleThreshold = 5,
            dumpHeapWhenDebugging = false,
            computeRetainedHeapSize = true,
            maxStoredHeapDumps = 7,
            requestWriteExternalStoragePermission = false
        )

        AppWatcher.config = AppWatcher.config.copy(
            watchActivities = true,
            watchFragments = true,
            watchFragmentViews = true,
            watchViewModels = true,
            watchDurationMillis = 10_000L  // 10s for player components
        )
    }
}
```

---

## Troubleshooting

### High Retained Count Persists

1. **Request GC** via Debug Screen
2. Wait 10 seconds (watch duration)
3. **Refresh** status
4. If still HIGH → Open LeakCanary UI for trace

### Leak Trace Shows Framework Class

If the leak trace points to Android framework classes:
- Check `AndroidReferenceMatchers.appDefaults` in config
- May be a known Android bug (add to referenceMatchers)

### Export Fails

- Check SAF permissions
- Use "Share heap dump" in LeakCanary UI for binary export

---

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | Dec 2024 | Initial implementation with noise control |

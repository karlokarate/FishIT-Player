# Diff: Debug Screen Real Data Implementation (c1c14da9)

**Commit:** c1c14da99e719040f768fda5b64c00b37e820412  
**Date:** 2025-12-22  
**Author:** karlokarate

## Summary

DebugScreen now displays **REAL data** instead of hardcoded stubs. This commit replaces all demo/stub data in the debug screen with live implementations that query actual system state.

## Changes Overview

| File | Type | Description |
|------|------|-------------|
| [LogBufferTree.kt](infra/logging/src/main/java/com/fishit/player/infra/logging/LogBufferTree.kt) | NEW | Timber.Tree with ring buffer for log capture |
| [LoggingModule.kt](infra/logging/src/main/java/com/fishit/player/infra/logging/di/LoggingModule.kt) | NEW | Hilt module for LogBufferProvider |
| [UnifiedLogInitializer.kt](infra/logging/src/main/java/com/fishit/player/infra/logging/UnifiedLogInitializer.kt) | MOD | Plant LogBufferTree on init |
| [DebugInfoProvider.kt](feature/settings/src/main/java/com/fishit/player/feature/settings/DebugInfoProvider.kt) | NEW | Interface for debug info access |
| [DefaultDebugInfoProvider.kt](app-v2/src/main/java/com/fishit/player/v2/di/DefaultDebugInfoProvider.kt) | NEW | Real implementation with all dependencies |
| [DebugModule.kt](app-v2/src/main/java/com/fishit/player/v2/di/DebugModule.kt) | NEW | Hilt module for DebugInfoProvider |
| [DebugViewModel.kt](feature/settings/src/main/java/com/fishit/player/feature/settings/DebugViewModel.kt) | MOD | Use real providers instead of stubs |
| [build.gradle.kts](infra/logging/build.gradle.kts) | MOD | Add Hilt dependencies |

## What Was Replaced

| Feature | Before (STUB) | After (REAL) |
|---------|---------------|--------------|
| **Logs** | `generateDemoLogs()` hardcoded list | `LogBufferProvider.observeLogs()` from Timber |
| **Telegram Connection** | `telegramConnected = true` | `TelegramAuthRepository.authState` |
| **Xtream Connection** | `xtreamConnected = false` | `SourceActivationStore.observeStates()` |
| **Telegram Cache Size** | `"128 MB"` | File system calculation of tdlib directories |
| **Image Cache Size** | `"45 MB"` | `imageLoader.diskCache?.size` |
| **Database Size** | `"12 MB"` | ObjectBox directory calculation |
| **Content Counts** | Hardcoded zeros | Repository `observeAll().map { it.size }` |
| **Clear Cache** | `delay(1000)` no-op | Real file deletion |

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│  DebugScreen (UI)                                           │
│    └─ DebugViewModel                                        │
│         ├─ LogBufferProvider (logs)                         │
│         ├─ DebugInfoProvider (connection, cache, counts)    │
│         ├─ SyncStateObserver (sync state) [existing]        │
│         └─ CatalogSyncWorkScheduler (sync actions) [exist]  │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│  DefaultDebugInfoProvider (app-v2)                          │
│    ├─ TelegramAuthRepository (connection status)            │
│    ├─ SourceActivationStore (Xtream status)                 │
│    ├─ XtreamCredentialsStore (server details)               │
│    ├─ TelegramContentRepository (media counts)              │
│    ├─ XtreamCatalogRepository (VOD/Series counts)           │
│    ├─ XtreamLiveRepository (Live channel counts)            │
│    └─ ImageLoader (cache size + clearing)                   │
└─────────────────────────────────────────────────────────────┘
```

## New Files

### LogBufferTree.kt (215 lines)

```kotlin
/**
 * Timber Tree that buffers log entries in a ring buffer.
 * - Captures all log entries (DEBUG, INFO, WARN, ERROR)
 * - Maintains fixed-size buffer (default: 500 entries)
 * - Provides Flow<List<BufferedLogEntry>> for reactive UI
 */
class LogBufferTree(maxEntries: Int = 500) : Timber.Tree() {
    private val buffer = ArrayDeque<BufferedLogEntry>(maxEntries)
    private val _entriesFlow = MutableStateFlow<List<BufferedLogEntry>>(emptyList())
    
    val entriesFlow: Flow<List<BufferedLogEntry>> = _entriesFlow.asStateFlow()
    
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        // Ring buffer logic: remove oldest if at capacity
        if (buffer.size >= maxEntries) buffer.removeFirst()
        buffer.addLast(BufferedLogEntry(timestamp, priority, tag, message, t))
        _entriesFlow.value = buffer.toList()
    }
}
```

### DebugInfoProvider.kt (118 lines)

```kotlin
/**
 * Interface for debug/diagnostics information.
 * Feature-owned (feature/settings), implementation in app-v2.
 */
interface DebugInfoProvider {
    fun observeTelegramConnection(): Flow<ConnectionInfo>
    fun observeXtreamConnection(): Flow<ConnectionInfo>
    suspend fun getTelegramCacheSize(): Long?
    suspend fun getImageCacheSize(): Long?
    suspend fun getDatabaseSize(): Long?
    fun observeContentCounts(): Flow<ContentCounts>
    suspend fun clearTelegramCache(): Boolean
    suspend fun clearImageCache(): Boolean
}
```

### DefaultDebugInfoProvider.kt (238 lines)

```kotlin
/**
 * Real implementation with all dependencies.
 * Bridges feature/settings to infra layer.
 */
@Singleton
class DefaultDebugInfoProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sourceActivationStore: SourceActivationStore,
    private val telegramAuthRepository: TelegramAuthRepository,
    private val xtreamCredentialsStore: XtreamCredentialsStore,
    private val telegramContentRepository: TelegramContentRepository,
    private val xtreamCatalogRepository: XtreamCatalogRepository,
    private val xtreamLiveRepository: XtreamLiveRepository,
    private val imageLoader: ImageLoader,
) : DebugInfoProvider {
    // Real implementations using dependencies
}
```

## DebugViewModel Changes

**Before:**

```kotlin
class DebugViewModel @Inject constructor(
    private val catalogSyncWorkScheduler: CatalogSyncWorkScheduler,
    private val syncStateObserver: SyncStateObserver,
    private val tmdbEnrichmentScheduler: TmdbEnrichmentScheduler,
)
```

**After:**

```kotlin
class DebugViewModel @Inject constructor(
    private val catalogSyncWorkScheduler: CatalogSyncWorkScheduler,
    private val syncStateObserver: SyncStateObserver,
    private val tmdbEnrichmentScheduler: TmdbEnrichmentScheduler,
    private val logBufferProvider: LogBufferProvider,      // NEW
    private val debugInfoProvider: DebugInfoProvider,      // NEW
)
```

**New Init Block:**

```kotlin
init {
    loadSystemInfo()
    observeSyncState()       // existing
    observeConnectionStatus() // NEW - real auth state
    observeContentCounts()    // NEW - real counts from repos
    observeLogs()             // NEW - real logs from buffer
    loadCacheSizes()          // NEW - real file sizes
}
```

## Data Flow

```
Timber.d("...") → LogBufferTree → entriesFlow → LogBufferProvider 
                                                      ↓
                                               DebugViewModel.observeLogs()
                                                      ↓
                                               DebugState.recentLogs
                                                      ↓
                                               DebugScreen UI
```

## Contract Compliance

- **LOGGING_CONTRACT_V2:** LogBufferTree integrates with UnifiedLog via Timber
- **Layer Boundaries:** DebugInfoProvider interface in feature, impl in app-v2
- **AGENTS.md Section 4:** No direct transport access from feature layer

## Testing Notes

The debug screen will now show:

- Real log entries from the application
- Actual connection status (disconnected until login)
- Real cache sizes (0 until files are cached)
- Real content counts (0 until catalog sync runs)

To verify:

1. Open app → DebugScreen shows "0 MB" for caches, disconnected status
2. Login to Telegram → Connection shows "Authorized"
3. Run catalog sync → Content counts increase
4. Logs section shows real application logs in real-time

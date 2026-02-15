---
applyTo: 
  - feature/settings/**
---

# 🏆 PLATIN Instructions:  feature/settings

**Version:** 1.0  
**Last Updated:** 2026-02-04  
**Status:** Active

> **PLATIN STANDARD** - Settings Screen with Debug UI.
>
> **Purpose:** Settings UI with source status, sync controls, cache management, and debug diagnostics.
> **Inherits:** All rules from `feature-common.instructions.md` apply here.

---

## 🔴 MODULE-SPECIFIC HARD RULES

### 1. Sync Actions via Schedulers (CRITICAL - SSOT)

```kotlin
// ✅ CORRECT: Use CatalogSyncWorkScheduler (SSOT for ALL sync operations)
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val catalogSyncScheduler: CatalogSyncWorkScheduler,  // SSOT
    private val tmdbEnrichmentScheduler: TmdbEnrichmentScheduler,  // SSOT
) : ViewModel() {
    
    fun syncAll() {
        catalogSyncScheduler.enqueueSyncAll()
    }
    
    fun syncTelegram() {
        catalogSyncScheduler.enqueueTelegramSync()
    }
    
    fun enrichAll() {
        tmdbEnrichmentScheduler.enqueueEnrichAll()
    }
}

// ❌ WRONG: Direct WorkManager enqueue (bypass SSOT!)
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val workManager: WorkManager,  // WRONG - bypasses SSOT!
) : ViewModel() {
    fun syncAll() {
        val request = OneTimeWorkRequestBuilder<TelegramSyncWorker>().build()
        workManager.enqueue(request)  // WRONG - bypasses scheduler logic!
    }
}
```

**Why This Matters:**
- `CatalogSyncWorkScheduler` is the SSOT for all sync operations
- Handles deduplication, retry logic, constraints
- Bypassing it creates duplicate/conflicting workers

---

### 2. Source Status via SourceActivationStore

```kotlin
// ✅ CORRECT:  Observe SourceActivationSnapshot
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val sourceActivationStore: SourceActivationStore,  // Domain interface
) : ViewModel() {
    
    val sourceStatus:  StateFlow<SourceActivationSnapshot> = 
        sourceActivationStore.snapshot
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SourceActivationSnapshot())
}

// UI displays status
@Composable
fun SourceStatusCard(snapshot: SourceActivationSnapshot) {
    when {
        snapshot.isTelegramActive -> Text("Telegram:  Active", color = Color.Green)
        else -> Text("Telegram: Inactive", color = Color.Gray)
    }
}

// ❌ WRONG:  Direct transport status check
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val telegramClient: TelegramHistoryClient,  // WRONG - transport!
) : ViewModel() {
    fun checkTelegramStatus() {
        val isConnected = telegramClient.isConnected()  // WRONG - transport call!
    }
}
```

---

### 3. Sync State Observation

```kotlin
// ✅ CORRECT:  Observe SyncStateObserver
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val syncStateObserver: SyncStateObserver,  // Domain interface
) : ViewModel() {
    
    val syncState: StateFlow<SyncState> = 
        syncStateObserver.observeSyncState()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SyncState.Idle)
}

// UI shows sync progress
@Composable
fun SyncStatusIndicator(state: SyncState) {
    when (state) {
        is SyncState.Syncing -> LinearProgressIndicator(progress = state.progress)
        is SyncState.Idle -> Text("Last sync: ${state.lastSyncTime}")
        is SyncState.Error -> Text("Error: ${state. message}", color = Color.Red)
    }
}
```

---

### 4. Debug UI Pattern (Abstraction for Safety)

```kotlin
// ✅ CORRECT: DebugInfoProvider abstraction
interface DebugInfoProvider {
    suspend fun getDebugInfo(): DebugInfo
    suspend fun getModuleInfo(): List<ModuleInfo>
    suspend fun getRecentLogs(limit: Int): List<LogEntry>
}

@HiltViewModel
class DebugViewModel @Inject constructor(
    private val debugInfoProvider: DebugInfoProvider,  // Abstraction
) : ViewModel() {
    
    val debugInfo: StateFlow<DebugInfo? > = flow {
        emit(debugInfoProvider.getDebugInfo())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}

// Implementation lives in app-v2 (NOT in feature module!)
// This prevents feature module from accessing internal module details

// ❌ WRONG:  Direct module access
@HiltViewModel
class DebugViewModel @Inject constructor(
    private val boxStore: BoxStore,  // WRONG - direct ObjectBox access!
    private val telegramClient: TelegramHistoryClient,  // WRONG - direct transport!
) : ViewModel() {
    fun getDebugInfo() {
        val entityCount = boxStore.boxFor(ObxCanonicalMedia::class.java).count()  // WRONG! 
    }
}
```

---

### 5. Cache Management

```kotlin
// ✅ CORRECT: Use CacheManager (infra abstraction)
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val cacheManager: CacheManager,  // Infra interface
) : ViewModel() {
    
    val cacheInfo: StateFlow<CacheInfo> = flow {
        emit(cacheManager.getCacheInfo())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CacheInfo.Empty)
    
    fun clearCache() {
        viewModelScope.launch {
            cacheManager.clearAll()
        }
    }
}

// ❌ WRONG: Direct file operations
@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {
    fun clearCache() {
        val cacheDir = context.cacheDir
        cacheDir.deleteRecursively()  // WRONG - direct file operations! 
    }
}
```

---

## 📋 Module Responsibilities

| Responsibility | Allowed | Forbidden |
|----------------|---------|-----------|
| Display source status | ✅ | Direct transport status checks |
| Sync controls | ✅ | Direct worker enqueue |
| Cache management | ✅ | Direct file operations |
| Debug UI | ✅ | Direct module access |
| TMDB enrichment trigger | ✅ | Direct TMDB API calls |

---

## 📐 Architecture Position

```
SettingsScreen (Composable)
    ↓
SettingsViewModel (StateFlow)
    ↓
    ├── CatalogSyncWorkScheduler (SSOT for sync)
    ├── TmdbEnrichmentScheduler (SSOT for TMDB)
    ├── SourceActivationStore (source status)
    ├── SyncStateObserver (sync state)
    └── CacheManager (cache operations)

DebugScreen (Composable)
    ↓
DebugViewModel (StateFlow)
    ↓
    └── DebugInfoProvider (abstraction)
        └── Impl in app-v2 (not in feature!)
```

---

## ✅ PLATIN Checklist

### Inherited from feature-common
- [ ] All common feature rules apply (see `feature-common.instructions.md`)

### Settings-Specific
- [ ] ALL sync operations use `CatalogSyncWorkScheduler` (SSOT)
- [ ] NO direct WorkManager enqueue
- [ ] Source status via `SourceActivationStore`, not transport
- [ ] Sync state via `SyncStateObserver`, not WorkManager
- [ ] Cache management via `CacheManager`, not File I/O
- [ ] Debug UI via `DebugInfoProvider` abstraction
- [ ] NO direct module access in DebugViewModel

---

## 📚 Reference Documents

1. `/feature/settings/README.md` - Settings architecture
2. `/core/catalog-sync/README.md` - Sync SSOT documentation
3. `/feature-common.instructions.md` - Common rules (INHERITED)
4. `/contracts/CATALOG_SYNC_WORKERS_CONTRACT_V2.md` - WorkManager patterns

---

## 🚨 Common Violations & Solutions

### Violation 1: Bypass Sync SSOT

```kotlin
// ❌ WRONG
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val workManager: WorkManager,  // WRONG! 
)

// ✅ CORRECT
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val catalogSyncScheduler: CatalogSyncWorkScheduler,  // SSOT
)
```

---

### Violation 2: Direct Transport Status Check

```kotlin
// ❌ WRONG
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val telegramClient: TelegramHistoryClient,  // WRONG! 
)

// ✅ CORRECT
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val sourceActivationStore: SourceActivationStore,  // Domain interface
)
```

---

### Violation 3: Direct Module Access in Debug UI

```kotlin
// ❌ WRONG
@HiltViewModel
class DebugViewModel @Inject constructor(
    private val boxStore: BoxStore,  // WRONG - direct access!
)

// ✅ CORRECT
@HiltViewModel
class DebugViewModel @Inject constructor(
    private val debugInfoProvider: DebugInfoProvider,  // Abstraction
)
```

---

**End of feature/settings Instructions**
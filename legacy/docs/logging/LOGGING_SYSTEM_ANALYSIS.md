> LEGACY (V1) – historical document  
> Not valid for v2. For current architecture see `V2_PORTAL.md` and `docs/v2/**`.

# Logging System Analysis

> **Created:** 2025-12-01  
> **Purpose:** Document the logging architecture, Telegram enable state lifecycle, crash logging gaps, and propose unified logging design.  
> **Status:** ANALYSIS + DESIGN ONLY (No code changes)

---

## Table of Contents

1. [PART 1: Telegram Enable/Ready State Persistence](#part-1-telegram-enableready-state-persistence)
2. [PART 2: Global Logging Architecture](#part-2-global-logging-architecture)
3. [PART 3: Crash Logging Design](#part-3-crash-logging-design)
4. [PART 4: Telegram Content → UI Rows Wiring](#part-4-telegram-content--ui-rows-wiring)
5. [PART 5: JSON Export for Telegram Parsing](#part-5-json-export-for-telegram-parsing)
6. [PART 6: Unified Logging Target Design](#part-6-unified-logging-target-design)
7. [PART 7: Pre-Release Logging Boosters (New)](#part-7-pre-release-logging-boosters-new)

---

## PART 1: Telegram Enable/Ready State Persistence

### 1.1 Current State Storage

#### SettingsStore (DataStore Preferences)

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `TG_ENABLED` | Boolean | `false` | Master toggle for Telegram feature |
| `TG_API_ID` | Int | `0` | API ID for TDLib |
| `TG_API_HASH` | String | `""` | API Hash for TDLib |
| `TG_PHONE_NUMBER` | String | `""` | Phone number for auto-login |
| `TG_SELECTED_CHATS_CSV` | String | `""` | Selected chats for content sync |

**Key Finding:** `TG_ENABLED` **IS persisted** in DataStore. The issue is NOT that enabled state isn't saved.

#### T_TelegramServiceClient (Singleton)

| Field | Type | Persistence | Description |
|-------|------|-------------|-------------|
| `isStarted` | AtomicBoolean | **In-memory only** | Whether service has been initialized |
| `_authState` | StateFlow | **In-memory only** | Current auth state (Idle/Ready/etc.) |
| `_connectionState` | StateFlow | **In-memory only** | Connection state |
| `client` | TdlClient | **In-memory only** | The actual TDLib client instance |

**Key Finding:** The singleton service client state is **NOT preserved across process restarts**. When app restarts, `isStarted = false` and `_authState = Idle`, even if TDLib's local database still contains valid session.

### 1.2 Auth State Flow

```
┌─────────────────────────────────────────────────────────────────────┐
│                    TelegramSettingsViewModel                         │
├─────────────────────────────────────────────────────────────────────┤
│  onToggleEnabled(true)                                              │
│       │                                                              │
│       ▼                                                              │
│  store.setTgEnabled(true)  ◄─── DataStore persists this             │
│       │                                                              │
│       ▼                                                              │
│  serviceClient.ensureStarted(app, store)                            │
│       │                                                              │
│       ▼                                                              │
│  serviceClient.login()  ◄─── Triggers TDLib auth flow               │
│       │                                                              │
│       ▼                                                              │
│  T_TelegramSession.login()                                          │
│       │                                                              │
│       ▼                                                              │
│  TDLib checks local DB for valid session                            │
│       │                                                              │
│       ├── If session valid: AuthorizationStateReady                 │
│       └── If session invalid: AuthorizationStateWaitPhoneNumber     │
└─────────────────────────────────────────────────────────────────────┘
```

### 1.3 The Root Cause of Toggle OFF/ON Requirement

**Problem:** When the app restarts:

1. `SettingsStore.tgEnabled` flows `true` (persisted in DataStore) ✅
2. `TelegramSettingsViewModel` observes this in `init {}` block ✅
3. BUT `TelegramSettingsViewModel` does **NOT** call `serviceClient.ensureStarted()` on init
4. The `serviceClient.authState` remains `Idle` because no one started the engine
5. User toggles OFF → `serviceClient.shutdown()` is called (does nothing since not started)
6. User toggles ON → `onToggleEnabled(true)` calls `ensureStarted()` + `login()` → engine starts

**The key gap:** `TelegramSettingsViewModel.init {}` only sets up flow collectors but does NOT auto-start the engine when `enabled == true` is already persisted.

### 1.4 Current Lifecycle Diagram

```
                          APP STARTUP
                              │
                              ▼
                    ┌─────────────────────┐
                    │ App.onCreate()      │
                    │   • T_TelegramServiceClient │
                    │     .getInstance(this)      │
                    │   • NO ensureStarted()!     │
                    └─────────────────────┘
                              │
                              ▼
                    ┌─────────────────────┐
                    │ Settings Screen opens │
                    │   • TelegramSettingsVM │
                    │     created            │
                    │   • observes tgEnabled │
                    │     (already true!)    │
                    │   • observes authState │
                    │     (remains Idle!)    │
                    │   • NO auto-start!     │
                    └─────────────────────┘
                              │
                              ▼
                    ┌─────────────────────┐
                    │ User sees:           │
                    │   Enabled: ON        │
                    │   Status: DISCONNECTED │
                    │                       │
                    │ (must toggle OFF/ON) │
                    └─────────────────────┘
```

### 1.5 Proposed Fix Design

#### Option A: Auto-Start in TelegramSettingsViewModel

```kotlin
// TelegramSettingsViewModel.init {}
init {
    viewModelScope.launch {
        // Existing collectors...
        
        // NEW: Auto-start if enabled is persisted as true
        val enabled = store.tgEnabled.first()
        if (enabled) {
            try {
                serviceClient.ensureStarted(app, store)
                serviceClient.login() // Let TDLib determine if session is valid
            } catch (e: Exception) {
                // Log but don't fail - UI will show auth needed
            }
        }
    }
}
```

#### Option B: Auto-Start in App.onCreate()

```kotlin
// App.onCreate()
applicationScope.launch {
    val store = SettingsStore(this@App)
    val enabled = store.tgEnabled.first()
    if (enabled) {
        val serviceClient = T_TelegramServiceClient.getInstance(this@App)
        try {
            serviceClient.ensureStarted(this@App, store)
            serviceClient.login()
        } catch (e: Exception) {
            TelegramLogRepository.warn("App", "Auto-start Telegram failed: ${e.message}")
        }
    }
}
```

#### Option C: Lazy Auto-Start on First Use (Recommended)

Add to `TelegramSettingsViewModel`:

```kotlin
private fun ensureStartedIfEnabled() {
    viewModelScope.launch {
        val enabled = store.tgEnabled.first()
        val hasApiCreds = store.tgApiId.first() != 0 && store.tgApiHash.first().isNotBlank()
        
        if (enabled && hasApiCreds && !_state.value.isConnecting) {
            try {
                serviceClient.ensureStarted(app, store)
                serviceClient.login()
            } catch (e: Exception) {
                TelegramLogRepository.warn("TelegramSettingsVM", "Auto-start failed: ${e.message}")
            }
        }
    }
}

init {
    viewModelScope.launch {
        // ... existing collectors
        
        // Auto-start after initial state load
        ensureStartedIfEnabled()
    }
}
```

### 1.6 What Must Change for Proper Persistence

| Component | Current Behavior | Required Change |
|-----------|------------------|-----------------|
| `TelegramSettingsViewModel.init` | Only observes, doesn't auto-start | Add `ensureStartedIfEnabled()` call |
| `T_TelegramServiceClient.ensureStarted()` | Already idempotent | No change needed |
| `T_TelegramSession.login()` | Already checks TDLib DB for valid session | No change needed |
| `SettingsStore.tgEnabled` | Already persisted | No change needed |

---

## PART 2: Global Logging Architecture

### 2.1 Logging Backends Overview

| Backend | Location | Storage | Capacity | Persistence |
|---------|----------|---------|----------|-------------|
| **AppLog** | `core/logging/AppLog.kt` | In-memory ring buffer | 500 entries | Lost on restart |
| **TelegramLogRepository** | `telegram/logging/TelegramLogRepository.kt` | In-memory ring buffer | 500 entries | Lost on restart |
| **DiagnosticsLogger** | `diagnostics/DiagnosticsLogger.kt` | In-memory queue | 1000 events | Lost on restart |

### 2.2 Data Flow Between Backends

```
                    ┌───────────────────┐
                    │   TelegramLogRepository  │
                    │   (Telegram-specific)    │
                    └───────────┬───────────┘
                                │ forwards to
                                ▼
                    ┌───────────────────┐
                    │   DiagnosticsLogger    │
                    │   (category: "telegram")│
                    └───────────┬───────────┘
                                │ forwards to
                                ▼
                    ┌───────────────────┐
                    │      AppLog          │
                    │   (unified sink)     │
                    └───────────────────┘
                                │
                                ▼
                    ┌───────────────────┐
                    │   Android Logcat     │
                    │   (tag: FishIT-*)    │
                    └───────────────────┘
```

### 2.3 Two Log Viewers Comparison

| Feature | App Log Viewer | Telegram Log Viewer |
|---------|----------------|---------------------|
| **Location** | `logs/ui/LogViewerScreen.kt` | `telegram/ui/TelegramLogScreen.kt` |
| **ViewModel** | `LogViewerViewModel.kt` | `TelegramLogViewModel.kt` |
| **Data Source** | `AppLog.history` + `AppLog.events` | `TelegramLogRepository.entries` |
| **Entry Type** | `AppLog.Entry` | `TgLogEntry` |
| **UI Style** | Simple monospace text | Rich cards with level colors |
| **Filtering** | Category only | Level + Source |
| **Statistics** | No | Yes (count by level) |
| **Export** | File to cache dir | Share intent with text |
| **Copy Full Log** | Yes (via SelectionContainer) | Via share |
| **Accessibility** | Always available | Only when Telegram enabled |
| **Navigation** | `log_viewer` route | `telegram_log` route |

### 2.4 App Log Viewer Implementation

```kotlin
// LogViewerViewModel.kt
class LogViewerViewModel : AndroidViewModel {
    init {
        viewModelScope.launch {
            // Seed with current history
            entries.addAll(AppLog.history.value.map(::toLogEntry))
            
            // Append live events
            AppLog.events.collect { e ->
                entries.add(toLogEntry(e))
                // Trim to maxEntries (1000)
            }
        }
    }
}
```

**Key Characteristics:**
- Reads from `AppLog.history` (StateFlow) and `AppLog.events` (SharedFlow)
- Converts `AppLog.Entry` → `LogEntry` (simpler display format)
- Simple monospace text rendering
- Export writes to `cacheDir/applog_*.txt`

### 2.5 Telegram Log Viewer Implementation

```kotlin
// TelegramLogViewModel.kt
class TelegramLogViewModel : ViewModel() {
    init {
        TelegramLogRepository.entries
            .onEach { entries ->
                _state.value = _state.value.copy(
                    entries = entries,
                    availableSources = TelegramLogRepository.getAllSources(),
                )
            }.launchIn(viewModelScope)
    }
}
```

**Key Characteristics:**
- Reads directly from `TelegramLogRepository.entries` (StateFlow)
- Rich UI with level-colored cards
- Filter by level (VERBOSE/DEBUG/INFO/WARN/ERROR)
- Filter by source (component name)
- Statistics card showing counts by level
- Auto-scroll to latest entry
- Export via Android share intent

### 2.6 Known Conflicts and Issues

#### Issue 1: Telegram Log Viewer Only Accessible When Telegram Enabled

**Problem:** The Telegram Log Viewer is only shown in Settings when `tgEnabled == true`. If Telegram fails to initialize or crashes, logs are invisible.

**Impact:** Can't debug Telegram issues when Telegram isn't working.

#### Issue 2: Potential Crash on Chat Selection Changes

**Problem:** When chat selection changes, `TelegramSyncWorker` triggers which:
1. Writes many log entries rapidly
2. Updates `TelegramLogRepository.entries` StateFlow
3. If `TelegramLogScreen` is open, recomposition happens frequently
4. Could cause index out of bounds if LazyColumn keys change during iteration

**Root Cause Analysis:**
- `TelegramLogRepository` uses `ReentrantReadWriteLock` for thread safety ✅
- But rapid emissions to `_entries` StateFlow can cause UI racing
- LazyColumn key is `"${it.timestamp}_${it.hashCode()}"` which should be stable

**Likely crash scenario:** Not from TelegramLogRepository itself, but from UI recomposition while list is changing size rapidly.

#### Issue 3: No Log Persistence Across App Restarts

**Problem:** Both `AppLog` and `TelegramLogRepository` use in-memory ring buffers. All logs are lost when the app process dies.

**Impact:** Can't diagnose issues that caused a crash or happened before restart.

### 2.7 Proposal: Unified Logging Design

#### Single Unified Backend

```kotlin
// Proposed: UnifiedLogRepository.kt
object UnifiedLogRepository {
    private const val MAX_ENTRIES = 1000
    private const val CRASH_PERSISTENCE_FILE = "last_crash.log"
    
    data class LogEntry(
        val timestamp: Long,
        val level: Level,
        val category: String,  // "app", "telegram", "player", "xtream", "crash"
        val source: String,    // Component name
        val message: String,
        val details: Map<String, String>? = null,
    )
    
    enum class Level { VERBOSE, DEBUG, INFO, WARN, ERROR, CRASH }
    
    // Ring buffer for runtime logs
    private val ringBuffer = ArrayDeque<LogEntry>(MAX_ENTRIES)
    
    // StateFlow for UI consumption
    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()
    
    // SharedFlow for live events (overlays)
    private val _events = MutableSharedFlow<LogEntry>(extraBufferCapacity = 10)
    val events: SharedFlow<LogEntry> = _events.asSharedFlow()
    
    fun log(
        level: Level,
        category: String,
        source: String,
        message: String,
        details: Map<String, String>? = null,
    ) {
        val entry = LogEntry(System.currentTimeMillis(), level, category, source, message, details)
        
        // Thread-safe add to buffer
        synchronized(ringBuffer) {
            if (ringBuffer.size >= MAX_ENTRIES) ringBuffer.removeFirst()
            ringBuffer.addLast(entry)
            _entries.value = ringBuffer.toList()
        }
        
        _events.tryEmit(entry)
        
        // Forward to logcat
        val tag = "FishIT-$category-$source"
        when (level) {
            Level.VERBOSE -> Log.v(tag, message)
            Level.DEBUG -> Log.d(tag, message)
            Level.INFO -> Log.i(tag, message)
            Level.WARN -> Log.w(tag, message)
            Level.ERROR, Level.CRASH -> Log.e(tag, message)
        }
    }
}
```

#### Single Primary Viewer (Telegram-Style UI)

Enhance `TelegramLogScreen` to become `UnifiedLogScreen`:

1. Read from `UnifiedLogRepository.entries` instead of `TelegramLogRepository.entries`
2. Add category filter (alongside level/source filters)
3. Keep rich card UI with level colors
4. Keep statistics overview
5. Add "Copy Full Log" button
6. Add "Export to File" button
7. **Make accessible from Settings regardless of Telegram enabled state**

#### Migration Path

1. `AppLog.log()` → forward to `UnifiedLogRepository.log(category = "app", ...)`
2. `TelegramLogRepository.log()` → forward to `UnifiedLogRepository.log(category = "telegram", ...)`
3. `DiagnosticsLogger.logEvent()` → forward to `UnifiedLogRepository.log(category = "diagnostics", ...)`
4. Deprecate `LogViewerScreen`, replace with `UnifiedLogScreen`
5. Keep `TelegramLogRepository` facade for backward compatibility (delegates to UnifiedLogRepository)

---

## PART 3: Crash Logging Design

### 3.1 Current Crash Handling Analysis

**App.kt analysis:**
- No custom `UncaughtExceptionHandler` is installed
- No crash file writing mechanism
- No Crashlytics or similar integration

**Impact:** If app crashes, no in-app way to see what caused it on next startup.

### 3.2 Proposed Crash Handler Design

#### Global UncaughtExceptionHandler

```kotlin
// Proposed: CrashHandler.kt
object CrashHandler {
    private const val CRASH_FILE = "last_crash.json"
    
    @Serializable
    data class CrashReport(
        val timestamp: Long,
        val threadName: String,
        val exceptionType: String,
        val message: String?,
        val stackTrace: String,
        val lastLogs: List<LogEntrySummary>,
    )
    
    @Serializable
    data class LogEntrySummary(
        val timestamp: Long,
        val level: String,
        val category: String,
        val message: String,
    )
    
    fun install(context: Context) {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                // Write crash to file BEFORE app dies
                writeCrashToFile(context, thread, throwable)
                
                // Also log to UnifiedLogRepository (may not persist but good for debugging)
                UnifiedLogRepository.log(
                    level = Level.CRASH,
                    category = "crash",
                    source = "CrashHandler",
                    message = "UNCAUGHT EXCEPTION: ${throwable::class.simpleName}: ${throwable.message}",
                    details = mapOf("thread" to thread.name),
                )
            } catch (e: Exception) {
                // Crash handler must not crash
                Log.e("CrashHandler", "Failed to write crash report", e)
            } finally {
                // Chain to default handler (shows system crash dialog)
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }
    
    private fun writeCrashToFile(context: Context, thread: Thread, throwable: Throwable) {
        val lastLogs = UnifiedLogRepository.entries.value.takeLast(50).map {
            LogEntrySummary(it.timestamp, it.level.name, it.category, it.message)
        }
        
        val report = CrashReport(
            timestamp = System.currentTimeMillis(),
            threadName = thread.name,
            exceptionType = throwable::class.qualifiedName ?: "Unknown",
            message = throwable.message,
            stackTrace = throwable.stackTraceToString(),
            lastLogs = lastLogs,
        )
        
        val file = File(context.filesDir, CRASH_FILE)
        file.writeText(Json.encodeToString(report))
    }
    
    /**
     * Read last crash report if exists.
     * Returns null if no crash occurred or report was already consumed.
     */
    fun readLastCrash(context: Context): CrashReport? {
        val file = File(context.filesDir, CRASH_FILE)
        if (!file.exists()) return null
        
        return try {
            Json.decodeFromString<CrashReport>(file.readText())
        } catch (e: Exception) {
            Log.e("CrashHandler", "Failed to read crash report", e)
            null
        }
    }
    
    /**
     * Mark crash as consumed (delete file).
     */
    fun clearLastCrash(context: Context) {
        File(context.filesDir, CRASH_FILE).delete()
    }
}
```

#### Installation in App.kt

```kotlin
// App.onCreate()
override fun onCreate() {
    super.onCreate()
    
    // Install crash handler FIRST
    CrashHandler.install(this)
    
    // ... rest of initialization
}
```

### 3.3 Unified Log Viewer: Last Crash Display

On startup, the unified log viewer should:

1. Check `CrashHandler.readLastCrash(context)`
2. If crash exists:
   - Show a prominent card at the top: "App crashed on [date]"
   - Show exception type and message
   - "View Stack Trace" button expands full trace
   - "Share Crash Report" button exports JSON
   - "Dismiss" button clears the crash report
3. Pre-populate log viewer with `lastLogs` from crash report

```kotlin
// UnifiedLogViewModel.kt
class UnifiedLogViewModel(application: Application) : AndroidViewModel(application) {
    private val _lastCrash = MutableStateFlow<CrashReport?>(null)
    val lastCrash: StateFlow<CrashReport?> = _lastCrash.asStateFlow()
    
    init {
        _lastCrash.value = CrashHandler.readLastCrash(application)
    }
    
    fun dismissCrash() {
        CrashHandler.clearLastCrash(getApplication())
        _lastCrash.value = null
    }
}
```

### 3.4 Design Constraints

| Constraint | How Addressed |
|------------|---------------|
| No recursion | Crash handler wrapped in try/catch, uses File I/O not logging |
| Minimal overhead | Only activates on actual crash |
| Graceful degradation | If crash handler fails, chains to default handler |
| Persistence | Uses app's filesDir, survives restart |
| Privacy | Stack traces may contain sensitive paths, user controls sharing |

---

## PART 4: Telegram Content → UI Rows Wiring

### 4.1 Current Status

Per `TELEGRAM_UI_WIRING_ANALYSIS.md`, the UI is now connected to `ObxTelegramItem` (new pipeline):

```
TelegramIngestionCoordinator
        │
        ▼
ObxTelegramItem (new table)
        │
        │ NOW CONNECTED ✅
        ▼
StartViewModel.observeVodItemsByChat()
        │
        ▼
FishTelegramItemRow (in StartScreen)
```

### 4.2 Why Telegram Rows May Still Not Appear

Even with wiring fixed, rows may not appear due to:

1. **tgEnabled == false** (check DataStore)
2. **tgSelectedVodChatsCsv == ""** (no chats selected for VOD)
3. **ObxTelegramItem table empty** (ingestion never ran or failed)
4. **Auth not ready** (TDLib can't resolve chat titles)

### 4.3 Data Flow with Logging Hooks

```
TelegramSyncWorker
        │
        │ LOG: "TelegramSyncWorker starting for mode=..."
        ▼
TelegramIngestionCoordinator.startBackfill()
        │
        │ LOG: "Starting backfill for chat $chatId"
        ▼
TelegramHistoryScanner → ExportMessage
        │
        │ LOG: "Processing batch: X messages -> Y blocks"
        ▼
TelegramBlockGrouper → MessageBlock
        │
        │ LOG: "Built X items from Y blocks"
        ▼
TelegramItemBuilder → TelegramItem
        │
        │ LOG: "Persisted X items for chat $chatId"
        ▼
TelegramContentRepository.upsertItems()
        │
        │ LOG: "telegram-ui: TelegramIngestionCoordinator: Persisted X TelegramItems"
        ▼
ObxTelegramItem (new table)
        │
        │ >>> GAP: No logging between storage and UI <<<
        │
        ▼
TelegramContentRepository.observeVodItemsByChat()
        │
        │ LOG: "telegram-ui: UI summaries: X chats, totalVod=Y"
        ▼
StartViewModel.observeTelegramContent()
        │
        │ LOG: "telegram-ui: StartVM received X Telegram chat summaries"
        ▼
StartScreen composable
        │
        │ LOG: "telegram-ui: Rendering Telegram rows with X chats"
        ▼
FishTelegramItemRow
```

### 4.4 Proposed Logging Hooks to Trace Row Construction

#### In TelegramContentRepository.observeVodChatSummaries()

```kotlin
fun observeVodChatSummaries(): Flow<List<TelegramChatSummary>> =
    store.tgSelectedVodChatsCsv
        .map { csv ->
            val chatIds = parseChatIdsCsv(csv)
            Log.d("telegram-ui", "observeVodChatSummaries: selectedChatIds=$chatIds")
            
            if (chatIds.isEmpty()) {
                Log.d("telegram-ui", "observeVodChatSummaries: No chats selected, returning empty")
                emptyList()
            } else {
                buildVodChatSummaries(chatIds)
            }
        }
```

#### In TelegramContentRepository.buildVodChatSummaries()

```kotlin
private suspend fun buildVodChatSummaries(chatIds: List<Long>): List<TelegramChatSummary> =
    withContext(Dispatchers.IO) {
        val summaries = mutableListOf<TelegramChatSummary>()
        
        for (chatId in chatIds) {
            val allItems = itemBox.query {
                equal(ObxTelegramItem_.chatId, chatId)
            }.find()
            
            Log.d("telegram-ui", "buildVodChatSummaries: chat=$chatId, totalItems=${allItems.size}")
            
            val vodItems = allItems.map { it.toDomain() }.filter { it.isVodType() }
            
            Log.d("telegram-ui", "buildVodChatSummaries: chat=$chatId, vodItems=${vodItems.size}")
            
            if (vodItems.isEmpty()) {
                Log.d("telegram-ui", "buildVodChatSummaries: chat=$chatId has no VOD items, skipping")
                continue
            }
            
            val chatTitle = try {
                resolveChatTitle(chatId)
            } catch (e: Exception) {
                Log.w("telegram-ui", "buildVodChatSummaries: Failed to resolve title for chat=$chatId", e)
                "Chat $chatId"
            }
            
            summaries.add(TelegramChatSummary(chatId, chatTitle, vodItems.size, vodItems.firstOrNull()?.posterRef))
        }
        
        Log.d("telegram-ui", "buildVodChatSummaries: Final summaries=${summaries.size}, totalVod=${summaries.sumOf { it.vodCount }}")
        summaries
    }
```

#### In StartViewModel.observeTelegramContent()

```kotlin
private fun observeTelegramContent() = viewModelScope.launch {
    launch {
        tgRepo.observeVodChatSummaries().collectLatest { summaries ->
            Log.d("telegram-ui", "StartVM: Received ${summaries.size} chat summaries")
            summaries.forEach { s ->
                Log.d("telegram-ui", "  Chat ${s.chatId} '${s.chatTitle}': ${s.vodCount} items")
            }
            _telegramChatSummaries.value = summaries
        }
    }
    
    launch {
        tgRepo.observeVodItemsByChat().collectLatest { chatMap ->
            Log.d("telegram-ui", "StartVM: Received ${chatMap.size} chats from observeVodItemsByChat")
            chatMap.forEach { (chatId, items) ->
                Log.d("telegram-ui", "  Chat $chatId: ${items.size} items")
            }
            // ... rest of processing
        }
    }
}
```

#### In StartScreen composable

```kotlin
// Inside LazyColumn
if (tgEnabled && telegramVodByChat.isNotEmpty()) {
    Log.d("telegram-ui", "StartScreen: Rendering ${telegramVodByChat.size} Telegram rows")
    
    telegramVodByChat.forEach { (chatId, chatData) ->
        val (chatTitle, items) = chatData
        Log.d("telegram-ui", "StartScreen: Row for chat=$chatId '$chatTitle' with ${items.size} items")
        
        if (items.isNotEmpty()) {
            item(key = "start_telegram_row:$chatId") {
                FishTelegramItemRow(...)
            }
        } else {
            Log.d("telegram-ui", "StartScreen: Skipping empty row for chat=$chatId")
        }
    }
} else {
    Log.d("telegram-ui", "StartScreen: NOT rendering Telegram rows (enabled=$tgEnabled, chatCount=${telegramVodByChat.size})")
}
```

### 4.5 Expected Log Output After Fix

**Scenario: 2 chats selected, 45 items total**

```
telegram-ui: observeVodChatSummaries: selectedChatIds=[-1001234567890, -1009876543210]
telegram-ui: buildVodChatSummaries: chat=-1001234567890, totalItems=30
telegram-ui: buildVodChatSummaries: chat=-1001234567890, vodItems=25
telegram-ui: buildVodChatSummaries: chat=-1009876543210, totalItems=25
telegram-ui: buildVodChatSummaries: chat=-1009876543210, vodItems=20
telegram-ui: buildVodChatSummaries: Final summaries=2, totalVod=45
telegram-ui: StartVM: Received 2 chat summaries
telegram-ui:   Chat -1001234567890 'Movie Channel': 25 items
telegram-ui:   Chat -1009876543210 'Series Channel': 20 items
telegram-ui: StartVM: Received 2 chats from observeVodItemsByChat
telegram-ui:   Chat -1001234567890: 25 items
telegram-ui:   Chat -1009876543210: 20 items
telegram-ui: StartScreen: Rendering 2 Telegram rows
telegram-ui: StartScreen: Row for chat=-1001234567890 'Movie Channel' with 25 items
telegram-ui: StartScreen: Row for chat=-1009876543210 'Series Channel' with 20 items
```

**Scenario: Problem case (no rows)**

```
telegram-ui: observeVodChatSummaries: selectedChatIds=[]
telegram-ui: observeVodChatSummaries: No chats selected, returning empty
telegram-ui: StartVM: Received 0 chat summaries
telegram-ui: StartVM: Received 0 chats from observeVodItemsByChat
telegram-ui: StartScreen: NOT rendering Telegram rows (enabled=true, chatCount=0)
```

---

## PART 5: JSON Export for Telegram Parsing

### 5.1 Export Requirements

| Field | Description |
|-------|-------------|
| **rawTdlib** | List of TDLib messages / ExportMessages as received from TDLib |
| **parsedItems** | List of TelegramItems / ObxTelegramItems stored in ObjectBox |

### 5.2 Where Raw TDLib DTOs Are Accessible

1. **TelegramHistoryScanner.scan()** - receives `Message` objects from TDLib
2. **ExportMessageFactory.create()** - converts `Message` → `ExportMessage`
3. **TelegramIngestionCoordinator.processBatch()** - has access to `List<ExportMessage>` before parsing

**Problem:** Raw DTOs are transient - they're processed and discarded. Not stored anywhere.

### 5.3 Proposed Diagnostics Facility

#### Step 1: Add Diagnostics Capture Flag

```kotlin
// TelegramIngestionCoordinator.kt
class TelegramIngestionCoordinator(...) {
    // Diagnostics: Capture raw messages for last batch (per chat)
    // Only populated when diagnostics mode is active
    private val _diagnosticsCapture = MutableStateFlow<Map<Long, DiagnosticsSnapshot>>(emptyMap())
    val diagnosticsCapture: StateFlow<Map<Long, DiagnosticsSnapshot>> = _diagnosticsCapture.asStateFlow()
    
    data class DiagnosticsSnapshot(
        val chatId: Long,
        val capturedAt: Long,
        val rawMessages: List<ExportMessageSummary>,
        val parsedItems: List<TelegramItemSummary>,
    )
    
    @Serializable
    data class ExportMessageSummary(
        val messageId: Long,
        val type: String,  // "video", "document", "photo", "text"
        val caption: String?,
        val fileName: String?,
        val dateUnix: Int,
    )
    
    @Serializable
    data class TelegramItemSummary(
        val anchorMessageId: Long,
        val type: String,  // TelegramItemType
        val title: String?,
        val year: Int?,
        val videoFileId: Int?,
    )
    
    var diagnosticsMode: Boolean = false
}
```

#### Step 2: Capture During Processing

```kotlin
// TelegramIngestionCoordinator.processBatch()
private suspend fun processBatch(
    messages: List<ExportMessage>,
    chatId: Long,
    chatTitle: String?,
): Int {
    if (messages.isEmpty()) return 0
    
    // Diagnostics capture (if enabled)
    val rawSummaries = if (diagnosticsMode) {
        messages.map { msg ->
            ExportMessageSummary(
                messageId = msg.id,
                type = msg.type.name,
                caption = msg.caption,
                fileName = msg.fileName,
                dateUnix = msg.dateUnix,
            )
        }
    } else emptyList()
    
    // ... existing grouping/building logic ...
    
    val items = mutableListOf<TelegramItem>()
    // ... build items ...
    
    // Diagnostics capture (if enabled)
    if (diagnosticsMode && items.isNotEmpty()) {
        val itemSummaries = items.map { item ->
            TelegramItemSummary(
                anchorMessageId = item.anchorMessageId,
                type = item.type.name,
                title = item.metadata.title,
                year = item.metadata.year,
                videoFileId = item.videoRef?.remoteId,
            )
        }
        
        val snapshot = DiagnosticsSnapshot(
            chatId = chatId,
            capturedAt = System.currentTimeMillis(),
            rawMessages = rawSummaries,
            parsedItems = itemSummaries,
        )
        
        val current = _diagnosticsCapture.value.toMutableMap()
        current[chatId] = snapshot
        _diagnosticsCapture.value = current
    }
    
    // ... persist items ...
    return items.size
}
```

#### Step 3: Export Function

```kotlin
// TelegramIngestionCoordinator.kt
fun exportDiagnosticsJson(): String {
    val snapshots = _diagnosticsCapture.value.values.toList()
    return Json { prettyPrint = true }.encodeToString(snapshots)
}

suspend fun captureCurrentStateForExport(context: Context): String {
    val contentRepo = TelegramContentRepository(context, SettingsStore(context))
    
    // Get all items grouped by chat
    val allItems = withContext(Dispatchers.IO) {
        contentRepo.observeAllItems().first()
    }
    
    val itemsByChat = allItems.groupBy { it.chatId }
    
    @Serializable
    data class ChatExport(
        val chatId: Long,
        val itemCount: Int,
        val items: List<TelegramItemSummary>,
    )
    
    val exports = itemsByChat.map { (chatId, items) ->
        ChatExport(
            chatId = chatId,
            itemCount = items.size,
            items = items.map { item ->
                TelegramItemSummary(
                    anchorMessageId = item.anchorMessageId,
                    type = item.type.name,
                    title = item.metadata.title,
                    year = item.metadata.year,
                    videoFileId = item.videoRef?.remoteId,
                )
            },
        )
    }
    
    return Json { prettyPrint = true }.encodeToString(exports)
}
```

#### Step 4: Integration with Unified Log Viewer

Add "Export Telegram Parsing Snapshot" action to unified log viewer:

```kotlin
// UnifiedLogScreen.kt
var showTelegramExportDialog by remember { mutableStateOf(false) }

// In actions row
IconButton(onClick = { showTelegramExportDialog = true }) {
    Icon(Icons.Default.DataObject, "Export Telegram Data")
}

// Dialog
if (showTelegramExportDialog) {
    AlertDialog(
        onDismissRequest = { showTelegramExportDialog = false },
        title = { Text("Export Telegram Parsing Snapshot") },
        text = {
            Column {
                Text("This will export a JSON snapshot of:")
                Text("• All parsed Telegram items in ObxTelegramItem")
                Text("• Grouped by chat ID")
                Text("")
                Text("Note: Raw TDLib messages are only available if diagnostics mode was enabled during ingestion.")
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    val json = TelegramIngestionCoordinator(...).captureCurrentStateForExport(context)
                    
                    // Write to cache and share
                    val file = File(context.cacheDir, "telegram_snapshot_${System.currentTimeMillis()}.json")
                    file.writeText(json)
                    
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/json"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share Telegram Snapshot"))
                }
                showTelegramExportDialog = false
            }) {
                Text("Export")
            }
        },
        dismissButton = {
            TextButton(onClick = { showTelegramExportDialog = false }) {
                Text("Cancel")
            }
        },
    )
}
```

### 5.4 Design Constraints

| Constraint | How Addressed |
|------------|---------------|
| No heavy permanent storage | Raw DTOs only captured when diagnostics mode enabled |
| Manual diagnostic action | Export requires explicit user action |
| JSON format | Easy to read and share |
| Per-chat grouping | Export organized by chatId |

---

## PART 6: Unified Logging Target Design

### 6.1 Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         UNIFIED LOGGING SYSTEM                               │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐        │
│  │ Telegram    │  │ Xtream      │  │ Player      │  │ General     │        │
│  │ Components  │  │ Components  │  │ Components  │  │ App Code    │        │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘        │
│         │                │                │                │               │
│         └────────────────┴────────────────┴────────────────┘               │
│                                    │                                        │
│                                    ▼                                        │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                     UnifiedLogRepository                             │   │
│  │  • In-memory ring buffer (1000 entries)                             │   │
│  │  • StateFlow for UI (entries)                                       │   │
│  │  • SharedFlow for live events (overlays)                            │   │
│  │  • Categories: app, telegram, xtream, player, diagnostics, crash    │   │
│  │  • Levels: VERBOSE, DEBUG, INFO, WARN, ERROR, CRASH                 │   │
│  │  • Thread-safe with ReentrantReadWriteLock                          │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                    │                                        │
│              ┌─────────────────────┼─────────────────────┐                 │
│              │                     │                     │                  │
│              ▼                     ▼                     ▼                  │
│  ┌───────────────┐    ┌───────────────────┐    ┌───────────────────┐      │
│  │ Android       │    │ UnifiedLogScreen   │    │ CrashHandler       │      │
│  │ Logcat        │    │ (Telegram-style UI)│    │ (file persistence) │      │
│  │ (FishIT-*)    │    │                     │    │                     │      │
│  └───────────────┘    └───────────────────┘    └───────────────────┘      │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 6.2 Unified Log Entry Schema

```kotlin
@Serializable
data class UnifiedLogEntry(
    val timestamp: Long,
    val level: Level,
    val category: String,      // "app", "telegram", "xtream", "player", "crash"
    val source: String,        // Component/class name
    val message: String,
    val details: Map<String, String>? = null,
) {
    enum class Level { VERBOSE, DEBUG, INFO, WARN, ERROR, CRASH }
    
    fun formattedTime(): String {
        val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}
```

### 6.3 Unified Log Viewer Features

| Feature | Description |
|---------|-------------|
| **Filter by Level** | VERBOSE/DEBUG/INFO/WARN/ERROR/CRASH |
| **Filter by Category** | app/telegram/xtream/player/diagnostics/crash |
| **Filter by Source** | Component/class name |
| **Statistics** | Count by level, filtered count |
| **Last Crash** | Prominent card showing last crash (if any) |
| **Copy Full Log** | Copy all (or filtered) entries to clipboard |
| **Export to File** | Save to JSON or text file |
| **Share** | Android share intent |
| **Telegram JSON Export** | Export parsed Telegram items snapshot |
| **Live Updates** | Auto-scroll to latest entry |
| **Always Accessible** | Not gated by Telegram enabled state |

### 6.4 Navigation & Accessibility

| Scenario | Access Path |
|----------|-------------|
| Normal operation | Settings → Tools → Unified Log Viewer |
| Telegram issues | Same path (not blocked by tgEnabled) |
| After crash | Unified Log Viewer shows crash card automatically |
| Debug builds | Direct access via debug menu (if added) |

### 6.5 Migration Path

1. **Phase 1: Create UnifiedLogRepository**
   - New singleton object
   - Same interface as TelegramLogRepository but with `category` field
   
2. **Phase 2: Update Existing Loggers**
   - `AppLog.log()` → delegates to `UnifiedLogRepository.log(category="app")`
   - `TelegramLogRepository.log()` → delegates to `UnifiedLogRepository.log(category="telegram")`
   - `DiagnosticsLogger.logEvent()` → delegates to `UnifiedLogRepository.log(category="diagnostics")`

3. **Phase 3: Create UnifiedLogScreen**
   - Based on TelegramLogScreen UI
   - Add category filter
   - Add crash card
   - Add copy/export buttons
   - Remove Telegram-enabled gate

4. **Phase 4: Install CrashHandler**
   - Add to `App.onCreate()` as first operation
   - Test crash persistence

5. **Phase 5: Deprecate Old Viewers**
   - Mark `LogViewerScreen` as deprecated
   - Update navigation to use `UnifiedLogScreen`
   - Keep old route for backward compatibility

### 6.6 Summary

| Goal | Design |
|------|--------|
| Single unified backend | `UnifiedLogRepository` with categories |
| Single primary viewer | `UnifiedLogScreen` (Telegram-style UI) |
| Independent of Telegram | Viewer accessible regardless of tgEnabled |
| Safe during crashes | `CrashHandler` writes to file before dying |
| Crash visibility | Crash card at top of viewer on next startup |
| Telegram parsing debug | JSON export of `ObxTelegramItem` per chat |
| Row construction visibility | Logging hooks in repository/ViewModel/Screen |

---

## PART 7: Pre-Release Logging Boosters (New)

> **Scope:** Prerelease builds only; remove or gate off for release. Per user request, secrets may be logged verbatim here to debug setup/auth issues.

### 7.1 Correlated Tracing Across Telegram Pipeline
- Introduce `traceId`/`spanId` for TDLib update → ingestion → ObjectBox write → ViewModel map → Compose render. Stamp IDs into every log and include per-stage `latencyMs` to reconstruct end-to-end timelines per chat/message.

### 7.2 Structured JSON Logs
- Emit JSON alongside text (fields: `traceId`, `tgChatId`, `tgMessageId`, `tgFileId`, `obxId`, `stage`, `latencyMs`, `result`, `errorCode`, `thread`). Enables machine parsing, regression diffing, and timeline reconstruction without manual scraping.

### 7.3 Adaptive Sampling and Burst Protection
- Add dynamic sampling/coalescing: aggregate bursts into counters (e.g., "ingestion batch: 120 msgs in 320ms", "UI recompositions skipped: 18") and throttle per-source when queues grow, so telemetry stays visible without causing UI jank/ANRs during floods.

### 7.4 Explicit Secret Logging (Prerelease Only)
- Provide an opt-in flag (e.g., `BuildConfig.PRERELEASE_LOG_SECRETS=true`) that disables redaction and logs exact secrets (API ID/HASH, proxy creds, UA, phone) for troubleshooting. Guard with build-type checks and ensure the flag is removed/false for release builds; surface a banner in the log viewer when raw secrets are emitted.

### 7.5 Rolling Persistence + Diagnostics Bundle
- Keep a rolling set of persisted log files (last N sessions) and add a "Collect diagnostics bundle" action that zips filtered logs, last crash report, and Telegram parsing snapshot. Attach the recent rolling window to crash reports so pre-crash context survives process death.

---

## Appendix A: File Locations

| Component | File Path |
|-----------|-----------|
| AppLog | `app/src/main/java/com/chris/m3usuite/core/logging/AppLog.kt` |
| TelegramLogRepository | `app/src/main/java/com/chris/m3usuite/telegram/logging/TelegramLogRepository.kt` |
| DiagnosticsLogger | `app/src/main/java/com/chris/m3usuite/diagnostics/DiagnosticsLogger.kt` |
| LogViewerScreen | `app/src/main/java/com/chris/m3usuite/logs/ui/LogViewerScreen.kt` |
| LogViewerViewModel | `app/src/main/java/com/chris/m3usuite/logs/LogViewerViewModel.kt` |
| TelegramLogScreen | `app/src/main/java/com/chris/m3usuite/telegram/ui/TelegramLogScreen.kt` |
| TelegramLogViewModel | `app/src/main/java/com/chris/m3usuite/telegram/ui/TelegramLogViewModel.kt` |
| TelegramSettingsViewModel | `app/src/main/java/com/chris/m3usuite/telegram/ui/TelegramSettingsViewModel.kt` |
| T_TelegramServiceClient | `app/src/main/java/com/chris/m3usuite/telegram/core/T_TelegramServiceClient.kt` |
| T_TelegramSession | `app/src/main/java/com/chris/m3usuite/telegram/core/T_TelegramSession.kt` |
| TelegramContentRepository | `app/src/main/java/com/chris/m3usuite/data/repo/TelegramContentRepository.kt` |
| TelegramIngestionCoordinator | `app/src/main/java/com/chris/m3usuite/telegram/ingestion/TelegramIngestionCoordinator.kt` |
| StartViewModel | `app/src/main/java/com/chris/m3usuite/ui/home/StartViewModel.kt` |
| StartScreen | `app/src/main/java/com/chris/m3usuite/ui/home/StartScreen.kt` |
| App | `app/src/main/java/com/chris/m3usuite/App.kt` |
| SettingsStore | `app/src/main/java/com/chris/m3usuite/prefs/SettingsStore.kt` |

## Appendix B: Related Documentation

- `docs/TELEGRAM_UI_WIRING_ANALYSIS.md` - UI wiring analysis and fix status
- `docs/LOG_VIEWER.md` - Current App Log Viewer feature documentation
- `docs/TELEGRAM_SIP_PLAYER_INTEGRATION.md` - Telegram player integration
- `docs/TELEGRAM_PARSER_CONTRACT.md` - Telegram parser pipeline contract
- `.github/tdlibAgent.md` - TDLib integration authoritative reference

---

*End of Analysis Document*

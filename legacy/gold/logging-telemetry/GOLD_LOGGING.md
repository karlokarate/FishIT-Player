# Gold: Logging & Telemetry Patterns

## Overview
This document captures the proven patterns from v1's logging and telemetry infrastructure that should be preserved in v2.

## Source Files
- `UnifiedLog.kt` (711 lines) - Unified logging system with ring buffer
- `DiagnosticsLogger.kt` (270 lines) - Structured event logging
- `UnifiedLogViewerScreen.kt` (685 lines) - In-app log viewer UI
- `Telemetry.kt` - Performance and usage metrics
- `PerformanceMonitor.kt` - Operation timing utilities

---

## 1. Unified Logging System

### Key Pattern: Single Logging Entry Point
**v1 Implementation:** `UnifiedLog`

```kotlin
/**
 * GOLD: Unified Logging Facade
 * 
 * Why this works:
 * - Single source of truth for all app logs
 * - Replaces Android Log, Timber, and custom loggers
 * - Consistent API across entire codebase
 * - Centralized control over output destinations
 */
object UnifiedLog {
    enum class Level {
        VERBOSE, DEBUG, INFO, WARN, ERROR
    }
    
    // Simple API
    fun d(source: String, message: String, throwable: Throwable? = null)
    fun i(source: String, message: String, throwable: Throwable? = null)
    fun w(source: String, message: String, throwable: Throwable? = null)
    fun e(source: String, message: String, throwable: Throwable? = null)
    
    // Usage:
    UnifiedLog.d("TelegramClient", "Fetching chat history")
    UnifiedLog.e("XtreamClient", "Failed to fetch VOD list", exception)
}
```

**Why preserve:** Prevents logging inconsistencies, enables global log control.

### Ring Buffer Pattern
**Pattern:** Fixed-size in-memory log buffer

```kotlin
/**
 * GOLD: Ring Buffer for Log Entries
 * 
 * Why this works:
 * - 1000 entry circular buffer
 * - Recent logs always available for debugging
 * - No unbounded memory growth
 * - Fast access for UI log viewer
 * - Thread-safe via ReentrantReadWriteLock
 */
private const val MAX_ENTRIES = 1000
private val entries = mutableListOf<Entry>()
private val lock = ReentrantReadWriteLock()

fun addEntry(entry: Entry) {
    lock.write {
        if (entries.size >= MAX_ENTRIES) {
            entries.removeAt(0)  // Remove oldest
        }
        entries.add(entry)
    }
    // Emit to flows for UI
    _entriesFlow.value = entries.toList()
}

fun getEntries(): List<Entry> {
    return lock.read {
        entries.toList()
    }
}
```

**Why preserve:** Essential for in-app log viewer without OOM risk.

### Persistent Filter Settings
**Pattern:** Save log filters in DataStore

```kotlin
/**
 * GOLD: Persistent Log Filters
 * 
 * Why this works:
 * - User can filter by log level and source
 * - Filters persist across app restarts
 * - Stored in DataStore Preferences
 * - Reactive updates via Flow
 */
private val Context.logPrefsDataStore by preferencesDataStore(name = "unified_log_prefs")

private val KEY_ENABLED_LEVELS = stringSetPreferencesKey("enabled_levels")
private val KEY_ENABLED_SOURCES = stringSetPreferencesKey("enabled_sources")

suspend fun setEnabledLevels(levels: Set<Level>) {
    context.logPrefsDataStore.edit { prefs ->
        prefs[KEY_ENABLED_LEVELS] = levels.map { it.name }.toSet()
    }
}

val enabledLevelsFlow: Flow<Set<Level>> = context.logPrefsDataStore.data
    .map { prefs ->
        prefs[KEY_ENABLED_LEVELS]?.mapNotNull { Level.valueOf(it) }?.toSet()
            ?: Level.entries.toSet()
    }
```

**Why preserve:** Better developer experience for debugging.

---

## 2. Source Category System

### Key Pattern: Predefined Log Sources
**v1 Implementation:** `SourceCategory` enum

```kotlin
/**
 * GOLD: Source Categories for Filtering
 * 
 * Why this works:
 * - Logical grouping of related log sources
 * - Easy to filter "all Telegram logs" or "all UI logs"
 * - Auto-categorization based on source string
 * - User-friendly filter UI
 */
enum class SourceCategory(val displayName: String, val sources: Set<String>) {
    PLAYBACK("Playback", setOf(
        "playback", "player", "exo", 
        "PlaybackSession", "PlaybackLauncher"
    )),
    TELEGRAM_DOWNLOAD("TG Download", setOf(
        "T_TelegramFileDownloader", "TelegramDataSource", 
        "TdlibRandomAccessSource"
    )),
    TELEGRAM_AUTH("TG Auth", setOf(
        "T_TelegramSession", "T_TelegramServiceClient", 
        "TgAuthOrchestrator"
    )),
    TELEGRAM_SYNC("TG Sync", setOf(
        "TelegramSyncWorker", "T_ChatBrowser", 
        "TelegramSeriesIndexer"
    )),
    THUMBNAILS("Thumbnails", setOf(
        "TelegramThumbPrefetcher", "coil", "ImageLoader"
    )),
    UI_FOCUS("UI/Focus", setOf(
        "ui", "focus", "navigation", "GlobalDebug"
    )),
    NETWORK("Network", setOf(
        "xtream", "epg", "network", "XtreamClient", "OkHttp"
    )),
    DIAGNOSTICS("Diagnostics", setOf(
        "diagnostics", "crash", "CrashHandler", "Telemetry"
    )),
    APP("App", setOf(
        "App", "Firebase", "WorkManager"
    )),
    OTHER("Other", emptySet())
    
    companion object {
        fun forSource(source: String): SourceCategory {
            return entries.firstOrNull { cat ->
                cat.sources.any { source.contains(it, ignoreCase = true) }
            } ?: OTHER
        }
    }
}
```

**Why preserve:** Makes log filtering intuitive and powerful.

---

## 3. Optional File Export

### Key Pattern: Session Log File
**v1 Implementation:** File buffer in UnifiedLog

```kotlin
/**
 * GOLD: Optional File Buffer
 * 
 * Why this works:
 * - Disabled by default (no disk usage)
 * - User can enable to capture full session
 * - Useful for bug reports
 * - Auto-rotates on app restart
 * - Exports as single file for sharing
 */
private const val FILE_BUFFER_NAME = "unified_log_session.txt"
private var fileBufferEnabled = false
private var fileWriter: PrintWriter? = null

fun enableFileBuffer(context: Context) {
    val logFile = File(context.cacheDir, FILE_BUFFER_NAME)
    fileWriter = PrintWriter(logFile.bufferedWriter(), true)
    fileBufferEnabled = true
}

fun exportLogFile(context: Context): File? {
    if (!fileBufferEnabled) return null
    return File(context.cacheDir, FILE_BUFFER_NAME).takeIf { it.exists() }
}

private fun writeToFile(entry: Entry) {
    if (!fileBufferEnabled || fileWriter == null) return
    try {
        fileWriter?.println(formatEntry(entry))
    } catch (e: Exception) {
        // Silently fail file writes
    }
}
```

**Why preserve:** Essential for remote debugging of user issues.

---

## 4. In-App Log Viewer

### Key Pattern: Real-Time Log UI
**v1 Implementation:** `UnifiedLogViewerScreen`

```kotlin
/**
 * GOLD: In-App Log Viewer
 * 
 * Why this works:
 * - View logs without USB debugging
 * - Real-time updates via StateFlow
 * - Filter by level and source category
 * - Search with text query
 * - Export logs to file
 * - TV-friendly with DPAD navigation
 */
@Composable
fun UnifiedLogViewerScreen(
    viewModel: UnifiedLogViewModel = hiltViewModel()
) {
    val entries by viewModel.filteredEntries.collectAsState()
    val enabledLevels by viewModel.enabledLevels.collectAsState()
    val enabledSources by viewModel.enabledSources.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    
    Column {
        // Filter chips
        FlowRow {
            // Level filters
            UnifiedLog.Level.entries.forEach { level ->
                FilterChip(
                    selected = level in enabledLevels,
                    onClick = { viewModel.toggleLevel(level) },
                    label = { Text(level.name) }
                )
            }
            
            // Source category filters
            SourceCategory.entries.forEach { category ->
                FilterChip(
                    selected = category in enabledSources,
                    onClick = { viewModel.toggleSource(category) },
                    label = { Text(category.displayName) }
                )
            }
        }
        
        // Search bar
        TextField(
            value = searchQuery,
            onValueChange = { viewModel.setSearchQuery(it) },
            placeholder = { Text("Search logs...") }
        )
        
        // Log entries (virtualized)
        LazyColumn {
            items(entries) { entry ->
                LogEntryRow(entry)
            }
        }
        
        // Export button
        Button(onClick = { viewModel.exportLogs() }) {
            Text("Export Logs")
        }
    }
}
```

**Why preserve:** Dramatically improves debugging on real devices.

---

## 5. Structured Diagnostics Events

### Key Pattern: JSON-Serializable Events
**v1 Implementation:** `DiagnosticsLogger`

```kotlin
/**
 * GOLD: Structured Diagnostics Events
 * 
 * Why this works:
 * - Events are JSON-serializable for analysis
 * - Consistent schema across app
 * - Includes context (screen, component)
 * - Async logging to avoid blocking UI
 * - No sensitive data leakage
 */
@Serializable
data class DiagnosticEvent(
    val timestamp: Long,
    val eventId: Long,
    val category: String,       // "xtream", "telegram", "playback"
    val event: String,           // "load_live_list", "playback_start"
    val level: String,           // "INFO", "WARN", "ERROR"
    val screen: String? = null,  // "HomeScreen", "LiveScreen"
    val component: String? = null,  // "FishRow", "MediaActionBar"
    val metadata: Map<String, String> = emptyMap(),  // NO sensitive data
    val buildInfo: String? = null
)

// Usage:
DiagnosticsLogger.logEvent(
    category = "xtream",
    event = "load_live_list",
    metadata = mapOf(
        "count" to "150",
        "duration_ms" to "234",
        "cached" to "true"
    )
)

DiagnosticsLogger.logEvent(
    category = "playback",
    event = "playback_error",
    level = LogLevel.ERROR,
    screen = "DetailScreen",
    metadata = mapOf(
        "error_code" to "SOURCE_ERROR",
        "media_type" to "VOD"
    )
)
```

**Why preserve:** Enables analytics and performance monitoring.

### Async Event Processing
**Pattern:** Non-blocking event logging

```kotlin
/**
 * GOLD: Async Diagnostics Processing
 * 
 * Why this works:
 * - Logging never blocks UI thread
 * - Events buffered in Channel
 * - Background coroutine processes events
 * - Bounded buffer to prevent OOM
 */
private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
private val logChannel = Channel<DiagnosticEvent>(Channel.BUFFERED)

init {
    // Background processor
    scope.launch {
        for (event in logChannel) {
            processEvent(event)
        }
    }
}

fun logEvent(...) {
    if (!isEnabled) return
    
    val event = DiagnosticEvent(...)
    
    // Non-blocking send
    logChannel.trySend(event)
}

private suspend fun processEvent(event: DiagnosticEvent) {
    // Write to UnifiedLog
    UnifiedLog.d("Diagnostics", json.encodeToString(event))
    
    // Could also:
    // - Write to analytics service
    // - Store in database
    // - Export to file
}
```

**Why preserve:** Prevents performance impact from logging.

---

## 6. Performance Monitoring

### Key Pattern: Operation Timing
**v1 Implementation:** `PerformanceMonitor` (inferred)

```kotlin
/**
 * GOLD: Performance Timing Helper
 * 
 * Why this works:
 * - Measure operation duration
 * - Log slow operations automatically
 * - Coroutine-friendly
 * - Minimal boilerplate
 */
object PerformanceMonitor {
    suspend fun <T> measure(
        operation: String,
        threshold: Long = 100L,  // Log if > 100ms
        block: suspend () -> T
    ): T {
        val start = System.currentTimeMillis()
        return try {
            block()
        } finally {
            val duration = System.currentTimeMillis() - start
            if (duration > threshold) {
                DiagnosticsLogger.logEvent(
                    category = "performance",
                    event = "slow_operation",
                    level = LogLevel.WARN,
                    metadata = mapOf(
                        "operation" to operation,
                        "duration_ms" to duration.toString()
                    )
                )
            }
        }
    }
}

// Usage:
val channels = PerformanceMonitor.measure("load_live_channels") {
    xtreamClient.getLiveStreams()
}
```

**Why preserve:** Identifies performance bottlenecks easily.

---

## 7. Crashlytics Integration

### Key Pattern: Error Reporting
**v1 Implementation:** Firebase Crashlytics in UnifiedLog

```kotlin
/**
 * GOLD: Crashlytics Integration
 * 
 * Why this works:
 * - ERROR level logs sent to Crashlytics
 * - Provides context for crash reports
 * - Automatic breadcrumb trail
 * - User-friendly error aggregation
 */
fun e(source: String, message: String, throwable: Throwable? = null) {
    // Android logcat
    Log.e(source, message, throwable)
    
    // In-memory buffer
    addEntry(Entry(level = Level.ERROR, source = source, message = message, throwable = throwable))
    
    // Crashlytics
    try {
        FirebaseCrashlytics.getInstance().apply {
            log("[$source] $message")
            throwable?.let { recordException(it) }
        }
    } catch (e: Exception) {
        // Silently fail if Crashlytics not initialized
    }
}
```

**Why preserve:** Production error monitoring without manual integration.

---

## 8. Log Level Colors

### Key Pattern: Visual Log Levels
**v1 Implementation:** Color-coded levels in viewer

```kotlin
/**
 * GOLD: Color-Coded Log Levels
 * 
 * Why this works:
 * - Easier to scan logs visually
 * - Color codes are standard (red=error, yellow=warn, etc.)
 * - Works in both light and dark themes
 */
enum class Level {
    VERBOSE,
    DEBUG,
    INFO,
    WARN,
    ERROR
    
    val color: Long
        get() = when (this) {
            VERBOSE -> 0xFF9E9E9E // Gray
            DEBUG -> 0xFF4CAF50   // Green
            INFO -> 0xFF2196F3    // Blue
            WARN -> 0xFFFF9800    // Orange
            ERROR -> 0xFFF44336   // Red
        }
}

// In UI:
@Composable
fun LogEntryRow(entry: Entry) {
    Row {
        // Level indicator
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(Color(entry.level.color), CircleShape)
        )
        Text(entry.message)
    }
}
```

**Why preserve:** Improves log viewer usability.

---

## 9. v2 Porting Guidance

### What to Port

1. **UnifiedLog Facade** → Already exists in `infra/logging/UnifiedLog.kt`
   - ✅ Already ported!
   - Verify ring buffer implementation
   - Verify StateFlow emission

2. **SourceCategory System** → Add to `infra/logging/LogCategories.kt`
   - Port category definitions
   - Update for v2 module structure
   - Add new categories for v2 features

3. **DiagnosticsLogger** → Add to `infra/telemetry/DiagnosticsLogger.kt`
   - Port structured event model
   - Port async processing
   - Keep JSON serialization

4. **Log Viewer UI** → Add to `feature/settings/ui/LogViewerScreen.kt`
   - Port filter UI
   - Port search functionality
   - Add export feature
   - TV-optimize with DPAD

5. **Performance Monitor** → Add to `infra/telemetry/PerformanceMonitor.kt`
   - Port timing utilities
   - Integrate with DiagnosticsLogger
   - Add coroutine support

### What to Change

1. **Package Names**
   - v1: `com.chris.m3usuite.core.logging`
   - v2: `com.fishit.player.infra.logging`
   - Reason: Module structure

2. **Detekt Rules**
   - v1: No enforcement
   - v2: Forbid direct android.util.Log usage
   - Reason: Contract compliance (LOGGING_CONTRACT_V2.md)

3. **FirebaseCrashlytics**
   - v1: Direct Firebase dependency
   - v2: Abstract behind interface
   - Reason: Testability, flexibility

4. **DataStore Location**
   - v1: In logging module
   - v2: In settings module
   - Reason: Module boundaries

### Implementation Phases

**Phase 0: Validate Existing** (FIRST)
- [ ] Verify infra/logging/UnifiedLog.kt matches v1 API
- [ ] Check for ring buffer implementation
- [ ] Verify StateFlow emission for UI
- [ ] Add missing methods if needed

**Phase 1: Categories & Filtering**
- [ ] Port SourceCategory system
- [ ] Add DataStore for filter persistence
- [ ] Add Flow-based filter API
- [ ] Update categories for v2 modules

**Phase 2: Structured Events**
- [ ] Port DiagnosticsLogger
- [ ] Add JSON serialization
- [ ] Add async processing
- [ ] Integrate with UnifiedLog

**Phase 3: UI Viewer**
- [ ] Port log viewer screen
- [ ] Add filter chips
- [ ] Add search bar
- [ ] Add export functionality
- [ ] TV-optimize

**Phase 4: Performance**
- [ ] Port PerformanceMonitor
- [ ] Add timing utilities
- [ ] Integrate with telemetry
- [ ] Add slow operation detection

---

## Key Principles

1. **Single Entry Point** - One logging API for entire app
2. **Ring Buffer** - Bounded memory for log entries
3. **Reactive Filters** - Flows for UI consumption
4. **Structured Events** - JSON for analysis
5. **Async Processing** - Never block UI thread
6. **In-App Viewer** - Debug without USB
7. **No Sensitive Data** - Never log tokens, passwords, etc.

---

## References

- v1 Source: `/legacy/v1-app/app/src/main/java/com/chris/m3usuite/core/logging/`
- v2 Target: `/infra/logging/` (already exists!)
- v2 Contract: `/docs/v2/LOGGING_CONTRACT_V2.md`
- Detekt Rules: `/detekt-config.yml` (ForbiddenImport for android.util.Log)

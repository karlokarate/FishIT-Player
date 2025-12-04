package com.chris.m3usuite.core.logging

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Unified logging system for the entire application.
 * Replaces both AppLog and UnifiedLog.
 *
 * Features:
 * - Single source of truth for all app logs
 * - 1000 entry in-memory ring buffer
 * - Optional file buffer for full session export
 * - Persistent filter settings via DataStore
 * - Predefined source categories for easy filtering
 * - Firebase Crashlytics integration
 * - Thread-safe access
 */
object UnifiedLog {
    private const val TAG = "UnifiedLog"
    private const val MAX_ENTRIES = 1000
    private const val FILE_BUFFER_NAME = "unified_log_session.txt"

    // DataStore for persistent filter settings
    private val Context.logPrefsDataStore by preferencesDataStore(name = "unified_log_prefs")

    // DataStore keys
    private val KEY_ENABLED_LEVELS = stringSetPreferencesKey("enabled_levels")
    private val KEY_ENABLED_SOURCES = stringSetPreferencesKey("enabled_sources")
    private val KEY_FILE_BUFFER_ENABLED = booleanPreferencesKey("file_buffer_enabled")

    /** Log levels */
    enum class Level {
        VERBOSE,
        DEBUG,
        INFO,
        WARN,
        ERROR,
        ;

        val color: Long
            get() =
                when (this) {
                    VERBOSE -> 0xFF9E9E9E // Gray
                    DEBUG -> 0xFF4CAF50 // Green
                    INFO -> 0xFF2196F3 // Blue
                    WARN -> 0xFFFF9800 // Orange
                    ERROR -> 0xFFF44336 // Red
                }
    }

    /** Predefined source categories for filtering */
    enum class SourceCategory(
        val displayName: String,
        val sources: Set<String>,
    ) {
        PLAYBACK("Playback", setOf("playback", "player", "exo", "PlaybackSession", "PlaybackLauncher")),
        TELEGRAM_DOWNLOAD("TG Download", setOf("T_TelegramFileDownloader", "TelegramDataSource", "TdlibRandomAccessSource")),
        TELEGRAM_AUTH("TG Auth", setOf("T_TelegramSession", "T_TelegramServiceClient", "TgAuthOrchestrator")),
        TELEGRAM_SYNC("TG Sync", setOf("TelegramSyncWorker", "T_ChatBrowser", "TelegramSeriesIndexer")),
        THUMBNAILS("Thumbnails", setOf("TelegramThumbPrefetcher", "coil", "ImageLoader")),
        UI_FOCUS("UI/Focus", setOf("ui", "focus", "navigation", "GlobalDebug")),
        NETWORK("Network", setOf("xtream", "epg", "network", "XtreamClient", "OkHttp")),
        DIAGNOSTICS("Diagnostics", setOf("diagnostics", "crash", "CrashHandler", "Telemetry")),
        APP("App", setOf("App", "Firebase", "WorkManager")),
        OTHER("Other", emptySet()),
        ;

        companion object {
            fun forSource(source: String): SourceCategory =
                entries.firstOrNull { cat ->
                    cat.sources.any { source.contains(it, ignoreCase = true) }
                } ?: OTHER
        }
    }

    /** Log entry data class */
    data class Entry(
        val id: Long,
        val timestamp: Long = System.currentTimeMillis(),
        val level: Level,
        val source: String,
        val message: String,
        val details: Map<String, String>? = null,
        val category: SourceCategory = SourceCategory.forSource(source),
    ) {
        fun formattedTime(): String {
            val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }

        fun formattedDateTime(): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }

        fun formattedDetails(): String? {
            if (details.isNullOrEmpty()) return null
            return details.entries.joinToString(", ") { "${it.key}=${it.value}" }
        }

        fun toLogLine(): String =
            buildString {
                append("[${formattedTime()}] [${level.name}] [$source] $message")
                formattedDetails()?.let { append(" | $it") }
            }

        fun toExportLine(): String =
            buildString {
                append("[${formattedDateTime()}] [${level.name}] [$source]")
                appendLine()
                append("  $message")
                formattedDetails()?.let {
                    appendLine()
                    append("  Details: $it")
                }
            }
    }

    /** Filter state for UI */
    data class FilterState(
        val enabledLevels: Set<Level> = Level.entries.toSet(),
        val enabledCategories: Set<SourceCategory> = SourceCategory.entries.toSet(),
        val searchQuery: String = "",
    )

    /** Statistics for UI */
    data class Statistics(
        val total: Int = 0,
        val verbose: Int = 0,
        val debug: Int = 0,
        val info: Int = 0,
        val warn: Int = 0,
        val error: Int = 0,
        val filtered: Int = 0,
    )

    // Thread-safe state
    private val lock = ReentrantReadWriteLock()
    private val ringBuffer = ArrayDeque<Entry>(MAX_ENTRIES)
    private val idCounter = AtomicLong(0)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // StateFlows
    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    private val _events = MutableSharedFlow<Entry>(extraBufferCapacity = 64)
    val events: SharedFlow<Entry> = _events.asSharedFlow()

    private val _filterState = MutableStateFlow(FilterState())
    val filterState: StateFlow<FilterState> = _filterState.asStateFlow()

    // File buffer
    private var fileBufferEnabled = false
    private var fileBufferWriter: PrintWriter? = null
    private var appContext: Context? = null

    /** Initialize with context for DataStore and file buffer */
    fun init(context: Context) {
        appContext = context.applicationContext

        // Load persistent filter settings
        scope.launch {
            try {
                val prefs = context.logPrefsDataStore.data.first()

                val levels =
                    prefs[KEY_ENABLED_LEVELS]
                        ?.mapNotNull { name ->
                            runCatching { Level.valueOf(name) }.getOrNull()
                        }?.toSet() ?: Level.entries.toSet()

                val categories =
                    prefs[KEY_ENABLED_SOURCES]
                        ?.mapNotNull { name ->
                            runCatching { SourceCategory.valueOf(name) }.getOrNull()
                        }?.toSet() ?: SourceCategory.entries.toSet()

                fileBufferEnabled = prefs[KEY_FILE_BUFFER_ENABLED] ?: false

                _filterState.value =
                    FilterState(
                        enabledLevels = levels,
                        enabledCategories = categories,
                    )

                if (fileBufferEnabled) {
                    enableFileBuffer()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load filter settings", e)
            }
        }
    }

    /** Enable file buffer for full session logging */
    fun enableFileBuffer() {
        val ctx = appContext ?: return
        try {
            val file = File(ctx.cacheDir, FILE_BUFFER_NAME)
            fileBufferWriter = PrintWriter(file.bufferedWriter())
            fileBufferEnabled = true

            // Write header
            fileBufferWriter?.println("=== FishIT Player Log Session ===")
            fileBufferWriter?.println("Started: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
            fileBufferWriter?.println()
            fileBufferWriter?.flush()

            Log.i(TAG, "File buffer enabled: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enable file buffer", e)
        }
    }

    /** Disable file buffer */
    fun disableFileBuffer() {
        try {
            fileBufferWriter?.close()
        } catch (e: Exception) {
            // Ignore
        }
        fileBufferWriter = null
        fileBufferEnabled = false
    }

    /** Log a message */
    fun log(
        level: Level,
        source: String,
        message: String,
        details: Map<String, String>? = null,
    ) {
        val entry =
            Entry(
                id = idCounter.incrementAndGet(),
                level = level,
                source = source,
                message = message,
                details = details,
            )

        // Add to ring buffer
        lock.write {
            if (ringBuffer.size >= MAX_ENTRIES) {
                ringBuffer.removeFirst()
            }
            ringBuffer.addLast(entry)
            _entries.value = ringBuffer.toList()
        }

        // Emit event
        scope.launch {
            _events.emit(entry)
        }

        // Write to logcat
        val tag = "FishIT/$source"
        val fullMessage =
            buildString {
                append(message)
                details?.let { d ->
                    if (d.isNotEmpty()) {
                        append(" | ")
                        append(d.entries.joinToString(", ") { "${it.key}=${it.value}" })
                    }
                }
            }
        when (level) {
            Level.VERBOSE -> Log.v(tag, fullMessage)
            Level.DEBUG -> Log.d(tag, fullMessage)
            Level.INFO -> Log.i(tag, fullMessage)
            Level.WARN -> Log.w(tag, fullMessage)
            Level.ERROR -> Log.e(tag, fullMessage)
        }

        // Write to file buffer if enabled
        fileBufferWriter?.let { writer ->
            try {
                writer.println(entry.toLogLine())
                writer.flush()
            } catch (e: Exception) {
                // Ignore file write errors
            }
        }

        // Forward errors to Crashlytics
        if (level == Level.ERROR || level == Level.WARN) {
            try {
                FirebaseCrashlytics.getInstance().log(entry.toLogLine())
            } catch (e: Exception) {
                // Crashlytics not available
            }
        }
    }

    // Convenience methods
    fun verbose(
        source: String,
        message: String,
        details: Map<String, String>? = null,
    ) = log(Level.VERBOSE, source, message, details)

    fun debug(
        source: String,
        message: String,
        details: Map<String, String>? = null,
    ) = log(Level.DEBUG, source, message, details)

    fun info(
        source: String,
        message: String,
        details: Map<String, String>? = null,
    ) = log(Level.INFO, source, message, details)

    fun warn(
        source: String,
        message: String,
        details: Map<String, String>? = null,
    ) = log(Level.WARN, source, message, details)

    fun error(
        source: String,
        message: String,
        details: Map<String, String>? = null,
    ) = log(Level.ERROR, source, message, details)

    fun error(
        source: String,
        message: String,
        exception: Throwable,
        details: Map<String, String>? = null,
    ) {
        val errorDetails =
            buildMap {
                details?.forEach { (k, v) -> put(k, v) }
                put("error_type", exception::class.java.simpleName)
                put("error_message", exception.message?.take(200) ?: "unknown")
            }
        log(Level.ERROR, source, message, errorDetails)

        // Record exception to Crashlytics
        try {
            FirebaseCrashlytics.getInstance().recordException(exception)
        } catch (e: Exception) {
            // Crashlytics not available
        }
    }

    /**
     * Log file download progress (Telegram-specific convenience method).
     * Maps to DEBUG level with structured details for tracking download status.
     */
    fun logFileDownload(
        fileId: Int,
        progress: Int,
        total: Int,
        status: String,
    ) {
        debug(
            source = "TelegramDownload",
            message = "File download: $status",
            details =
                mapOf(
                    "fileId" to fileId.toString(),
                    "progress" to progress.toString(),
                    "total" to total.toString(),
                    "status" to status,
                    "percent" to if (total > 0) "${(progress * 100 / total)}%" else "0%",
                ),
        )
    }

    /** Get filtered entries */
    fun getFilteredEntries(filter: FilterState = _filterState.value): List<Entry> =
        lock.read {
            ringBuffer.filter { entry ->
                entry.level in filter.enabledLevels &&
                    entry.category in filter.enabledCategories &&
                    (
                        filter.searchQuery.isEmpty() ||
                            entry.message.contains(filter.searchQuery, ignoreCase = true) ||
                            entry.source.contains(filter.searchQuery, ignoreCase = true)
                    )
            }
        }

    /** Get statistics */
    fun getStatistics(filter: FilterState = _filterState.value): Statistics {
        val all = lock.read { ringBuffer.toList() }
        val filtered = getFilteredEntries(filter)
        return Statistics(
            total = all.size,
            verbose = all.count { it.level == Level.VERBOSE },
            debug = all.count { it.level == Level.DEBUG },
            info = all.count { it.level == Level.INFO },
            warn = all.count { it.level == Level.WARN },
            error = all.count { it.level == Level.ERROR },
            filtered = filtered.size,
        )
    }

    /** Get all unique sources */
    fun getAllSources(): List<String> =
        lock.read {
            ringBuffer.map { it.source }.distinct().sorted()
        }

    /** Update filter state */
    fun setFilter(filter: FilterState) {
        _filterState.value = filter
        persistFilters()
    }

    /** Filter by level (toggle) */
    fun toggleLevel(level: Level) {
        val current = _filterState.value
        val newLevels =
            if (level in current.enabledLevels) {
                current.enabledLevels - level
            } else {
                current.enabledLevels + level
            }
        _filterState.value = current.copy(enabledLevels = newLevels)
        persistFilters()
    }

    /** Filter by category (toggle) */
    fun toggleCategory(category: SourceCategory) {
        val current = _filterState.value
        val newCategories =
            if (category in current.enabledCategories) {
                current.enabledCategories - category
            } else {
                current.enabledCategories + category
            }
        _filterState.value = current.copy(enabledCategories = newCategories)
        persistFilters()
    }

    /** Set search query */
    fun setSearchQuery(query: String) {
        _filterState.value = _filterState.value.copy(searchQuery = query)
    }

    /** Filter to show only specific level (for clickable statistics) */
    fun filterToLevel(level: Level) {
        _filterState.value =
            _filterState.value.copy(
                enabledLevels = setOf(level),
                enabledCategories = SourceCategory.entries.toSet(),
            )
        persistFilters()
    }

    /** Reset filters to default */
    fun resetFilters() {
        _filterState.value = FilterState()
        persistFilters()
    }

    /** Persist filter settings to DataStore */
    private fun persistFilters() {
        val ctx = appContext ?: return
        scope.launch {
            try {
                ctx.logPrefsDataStore.edit { prefs ->
                    prefs[KEY_ENABLED_LEVELS] =
                        _filterState.value.enabledLevels
                            .map { it.name }
                            .toSet()
                    prefs[KEY_ENABLED_SOURCES] =
                        _filterState.value.enabledCategories
                            .map { it.name }
                            .toSet()
                    prefs[KEY_FILE_BUFFER_ENABLED] = fileBufferEnabled
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist filter settings", e)
            }
        }
    }

    /** Clear all logs */
    fun clear() {
        lock.write {
            ringBuffer.clear()
            _entries.value = emptyList()
        }
    }

    /** Export logs as text (filtered) */
    fun exportAsText(filter: FilterState = _filterState.value): String {
        val filtered = getFilteredEntries(filter)
        val stats = getStatistics(filter)
        val exportTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        return buildString {
            appendLine("=== FishIT Player Log Export ===")
            appendLine("Export Time: $exportTime")
            appendLine("Total Entries: ${stats.total}")
            appendLine("Showing: ${stats.filtered} (filtered)")
            appendLine()
            appendLine("Statistics:")
            appendLine("  Verbose: ${stats.verbose}")
            appendLine("  Debug: ${stats.debug}")
            appendLine("  Info: ${stats.info}")
            appendLine("  Warn: ${stats.warn}")
            appendLine("  Error: ${stats.error}")
            appendLine()
            appendLine("Active Filters:")
            appendLine("  Levels: ${filter.enabledLevels.joinToString(", ") { it.name }}")
            appendLine("  Categories: ${filter.enabledCategories.joinToString(", ") { it.displayName }}")
            if (filter.searchQuery.isNotEmpty()) {
                appendLine("  Search: \"${filter.searchQuery}\"")
            }
            appendLine()
            appendLine("=== Log Entries ===")
            appendLine()

            filtered.forEach { entry ->
                appendLine(entry.toExportLine())
                appendLine()
            }
        }
    }

    /** Export full session from file buffer */
    fun exportFullSession(): String? {
        val ctx = appContext ?: return null
        val file = File(ctx.cacheDir, FILE_BUFFER_NAME)
        return if (file.exists()) {
            try {
                file.readText()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read file buffer", e)
                null
            }
        } else {
            null
        }
    }

    /** Get file buffer path for sharing */
    fun getFileBufferPath(): File? {
        val ctx = appContext ?: return null
        val file = File(ctx.cacheDir, FILE_BUFFER_NAME)
        return if (file.exists()) file else null
    }

    /** Check if file buffer is enabled */
    fun isFileBufferEnabled(): Boolean = fileBufferEnabled

    /** Save export to file and return path */
    fun saveExportToFile(filter: FilterState = _filterState.value): File? {
        val ctx = appContext ?: return null
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "fishit_log_$timestamp.txt"
        val file = File(ctx.cacheDir, fileName)

        return try {
            file.writeText(exportAsText(filter))
            file
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save export", e)
            null
        }
    }

    // ===== Backward Compatibility with AppLog =====
    // These methods ensure existing code continues to work

    /** Legacy UnifiedLog.Level mapping */
    @Deprecated("Use UnifiedLog.Level directly", ReplaceWith("UnifiedLog.Level"))
    object LegacyLevel {
        val VERBOSE = Level.VERBOSE
        val DEBUG = Level.DEBUG
        val INFO = Level.INFO
        val WARN = Level.WARN
        val ERROR = Level.ERROR
    }

    /** Legacy log method for backward compatibility */
    fun log(
        category: String,
        level: Level,
        message: String,
        extras: Map<String, String>? = null,
        @Suppress("UNUSED_PARAMETER") bypassMaster: Boolean = false,
    ) {
        log(level, category, message, extras)
    }

    /** Legacy history StateFlow - use entries instead */
    val history: StateFlow<List<Entry>>
        get() = entries
}

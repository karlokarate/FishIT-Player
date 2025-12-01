package com.chris.m3usuite.core.logging

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Central logging utility for the application.
 * Provides structured logging with categories and levels.
 *
 * Used by:
 * - Player components
 * - Work handlers
 * - UI components
 * - Network handlers
 */
object AppLog {
    /** Log levels matching standard Android log levels */
    enum class Level {
        VERBOSE,
        DEBUG,
        INFO,
        WARN,
        ERROR,
    }

    /** Log entry data class */
    data class Entry(
        val timestamp: Long = System.currentTimeMillis(),
        val level: Level,
        val category: String,
        val message: String,
        val extras: Map<String, String> = emptyMap(),
    )

    /** Whether logging is enabled globally */
    @Volatile
    var enabled: Boolean = true

    /** Categories that are enabled for logging */
    @Volatile
    private var enabledCategories: Set<String>? = null

    /** Maximum number of entries to keep in history */
    private const val MAX_HISTORY_SIZE = 500

    /**
     * Set the master enabled switch for logging.
     */
    fun setMasterEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    /**
     * Set which categories are enabled for logging.
     * Pass null to enable all categories.
     */
    fun setCategoriesEnabled(categories: Set<String>?) {
        this.enabledCategories = categories
    }

    /**
     * Check if a category is enabled for logging.
     */
    private fun isCategoryEnabled(category: String): Boolean {
        val cats = enabledCategories
        return cats == null || cats.isEmpty() || cats.contains(category)
    }

    /** Scope for emitting log events */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** In-memory ring buffer of log entries */
    private val _history = MutableStateFlow<List<Entry>>(emptyList())
    val history: StateFlow<List<Entry>> = _history.asStateFlow()

    /** Live event stream for new log entries */
    private val _events = MutableSharedFlow<Entry>(extraBufferCapacity = 64)
    val events: SharedFlow<Entry> = _events.asSharedFlow()

    /**
     * Log a message with the specified category and level.
     *
     * @param category Log category (e.g., "player", "network", "ui")
     * @param level Log level
     * @param message Log message
     * @param extras Optional key-value pairs for additional context
     * @param bypassMaster If true, logs even if global master switch is off (reserved for critical errors)
     */
    fun log(
        category: String,
        level: Level,
        message: String,
        extras: Map<String, String>? = null,
        bypassMaster: Boolean = false,
    ) {
        if (!enabled && !bypassMaster) return
        if (!bypassMaster && !isCategoryEnabled(category)) return

        val tag = "FishIT/$category"
        val fullMessage =
            buildString {
                append(message)
                if (!extras.isNullOrEmpty()) {
                    append(" | ")
                    append(extras.entries.joinToString(", ") { "${it.key}=${it.value}" })
                }
            }

        when (level) {
            Level.VERBOSE -> Log.v(tag, fullMessage)
            Level.DEBUG -> Log.d(tag, fullMessage)
            Level.INFO -> Log.i(tag, fullMessage)
            Level.WARN -> Log.w(tag, fullMessage)
            Level.ERROR -> Log.e(tag, fullMessage)
        }

        // Add to history and emit event
        val entry =
            Entry(
                level = level,
                category = category,
                message = message,
                extras = extras ?: emptyMap(),
            )
        addEntry(entry)
    }

    /**
     * Add entry to history and emit event.
     */
    private fun addEntry(entry: Entry) {
        _history.update { current ->
            val newList = current + entry
            if (newList.size > MAX_HISTORY_SIZE) {
                newList.drop(newList.size - MAX_HISTORY_SIZE)
            } else {
                newList
            }
        }
        scope.launch {
            _events.emit(entry)
        }
    }

    /**
     * Log an error with exception.
     *
     * @param category Log category
     * @param message Log message
     * @param exception Exception to log
     */
    fun error(
        category: String,
        message: String,
        exception: Throwable,
    ) {
        if (!enabled) return

        val tag = "FishIT/$category"
        Log.e(tag, message, exception)

        // Add to history with exception info
        val entry =
            Entry(
                level = Level.ERROR,
                category = category,
                message = "$message: ${exception.message}",
                extras = mapOf("exception" to exception::class.simpleName.orEmpty()),
            )
        addEntry(entry)
    }

    /**
     * Convenience method for debug logging.
     */
    fun debug(
        category: String,
        message: String,
        extras: Map<String, String>? = null,
    ) {
        log(category, Level.DEBUG, message, extras)
    }

    /**
     * Convenience method for info logging.
     */
    fun info(
        category: String,
        message: String,
        extras: Map<String, String>? = null,
    ) {
        log(category, Level.INFO, message, extras)
    }

    /**
     * Convenience method for warning logging.
     */
    fun warn(
        category: String,
        message: String,
        extras: Map<String, String>? = null,
    ) {
        log(category, Level.WARN, message, extras)
    }

    /**
     * Clear all log history.
     */
    fun clear() {
        _history.value = emptyList()
    }
}

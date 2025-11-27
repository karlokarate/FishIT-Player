package com.chris.m3usuite.core.logging

import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Centralized logging utility for the application.
 *
 * Provides structured logging with categories and levels.
 * Supports master enable/disable, category filtering, and history for LogViewer.
 */
object AppLog {
    enum class Level {
        VERBOSE,
        DEBUG,
        INFO,
        WARN,
        ERROR,
    }

    /**
     * A log entry with all metadata.
     */
    data class Entry(
        val timestamp: Long,
        val category: String,
        val level: Level,
        val message: String,
        val extras: Map<String, String> = emptyMap(),
    )

    private const val MAX_HISTORY_SIZE = 500

    private val masterEnabled = AtomicBoolean(false)
    private var enabledCategories: Set<String> = emptySet()

    private val _history = MutableStateFlow<List<Entry>>(emptyList())
    private val _events = MutableSharedFlow<Entry>(extraBufferCapacity = 64)

    /** Read-only history of log entries for LogViewer. */
    val history: StateFlow<List<Entry>> = _history.asStateFlow()

    /** Live stream of log events for LogViewer. */
    val events: SharedFlow<Entry> = _events.asSharedFlow()

    /**
     * Enable or disable the master logging switch.
     * When disabled, all logs (except bypassMaster) are suppressed.
     */
    fun setMasterEnabled(enabled: Boolean) {
        masterEnabled.set(enabled)
    }

    /**
     * Set the enabled categories for filtering.
     * An empty set means all categories are enabled.
     */
    fun setCategoriesEnabled(categories: Set<String>) {
        enabledCategories = categories
    }

    /**
     * Log a message with the specified category and level.
     *
     * @param category The log category (e.g., "player", "telegram", "xtream")
     * @param level The log level
     * @param message The log message
     * @param extras Optional metadata to include in the log
     * @param bypassMaster If true, logs even when master is disabled (for diagnostics)
     */
    fun log(
        category: String,
        level: Level = Level.DEBUG,
        message: String,
        extras: Map<String, String> = emptyMap(),
        bypassMaster: Boolean = false,
    ) {
        // Check master switch (unless bypassed)
        if (!bypassMaster && !masterEnabled.get()) return

        // Check category filter (empty set = all enabled)
        if (enabledCategories.isNotEmpty() && category !in enabledCategories) return

        val tag = "FishIT-$category"
        val fullMessage =
            if (extras.isEmpty()) {
                message
            } else {
                "$message | ${extras.entries.joinToString(", ") { "${it.key}=${it.value}" }}"
            }

        // Log to Logcat
        when (level) {
            Level.VERBOSE -> Log.v(tag, fullMessage)
            Level.DEBUG -> Log.d(tag, fullMessage)
            Level.INFO -> Log.i(tag, fullMessage)
            Level.WARN -> Log.w(tag, fullMessage)
            Level.ERROR -> Log.e(tag, fullMessage)
        }

        // Record to history and emit event for LogViewer
        val entry = Entry(
            timestamp = System.currentTimeMillis(),
            category = category,
            level = level,
            message = message,
            extras = extras,
        )

        // Update history (thread-safe via StateFlow)
        val current = _history.value.toMutableList()
        current.add(entry)
        if (current.size > MAX_HISTORY_SIZE) {
            current.removeAt(0)
        }
        _history.value = current.toList()

        // Emit live event
        _events.tryEmit(entry)
    }

    /**
     * Log a message with the specified category (shorthand for DEBUG level).
     *
     * @param category The log category
     * @param message The log message
     */
    fun log(
        category: String,
        message: String,
    ) {
        log(category, Level.DEBUG, message)
    }
}

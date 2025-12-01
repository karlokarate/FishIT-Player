package com.chris.m3usuite.core.logging

import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Central in-memory log repository for the entire application.
 *
 * Features:
 * - In-memory ring buffer (500 entries max)
 * - Thread-safe access
 * - Master enable toggle for production/debug modes
 * - Category-based filtering
 * - Dual flow architecture:
 *   - StateFlow<List<Entry>> for log history (UI viewing via LogViewerScreen)
 *   - SharedFlow<Entry> for live events (overlays, telemetry sinks)
 * - Forwards to Android Logcat with FishIT- prefixed tags
 *
 * Usage:
 * ```
 * AppLog.log(
 *     category = "player",
 *     level = AppLog.Level.INFO,
 *     message = "Playback started",
 *     extras = mapOf("mediaId" to "123")
 * )
 * ```
 */
object AppLog {
    private const val MAX_ENTRIES = 500
    private const val TAG_PREFIX = "FishIT"

    enum class Level {
        VERBOSE,
        DEBUG,
        INFO,
        WARN,
        ERROR,
    }

    data class Entry(
        val timestamp: Long,
        val category: String,
        val level: Level,
        val message: String,
        val extras: Map<String, String> = emptyMap(),
    )

    // Configuration
    @Volatile
    private var masterEnabled: Boolean = false

    @Volatile
    private var categoriesEnabled: Set<String> = emptySet()

    // Thread-safe ring buffer
    private val lock = ReentrantReadWriteLock()
    private val ringBuffer = ArrayDeque<Entry>(MAX_ENTRIES)

    // StateFlow for log history (consumed by LogViewerScreen)
    private val _history = MutableStateFlow<List<Entry>>(emptyList())
    val history: StateFlow<List<Entry>> = _history.asStateFlow()

    // SharedFlow for live events (consumed by telemetry sinks, overlays)
    private val _events =
        MutableSharedFlow<Entry>(
            replay = 0,
            extraBufferCapacity = 10,
        )
    val events: SharedFlow<Entry> = _events.asSharedFlow()

    /**
     * Enable or disable the master log switch.
     * When disabled, no logs are written to the ring buffer (but still go to Logcat).
     */
    fun setMasterEnabled(enabled: Boolean) {
        masterEnabled = enabled
    }

    /**
     * Set the categories that are enabled for logging.
     * An empty set means all categories are enabled.
     */
    fun setCategoriesEnabled(categories: Set<String>) {
        categoriesEnabled = categories
    }

    /**
     * Log a message.
     *
     * @param category Category identifier (e.g., "player", "telegram", "epg")
     * @param level Log level
     * @param message Human-readable message
     * @param extras Optional key-value metadata
     * @param bypassMaster If true, log even when master is disabled (for telemetry)
     */
    fun log(
        category: String,
        level: Level,
        message: String,
        extras: Map<String, String> = emptyMap(),
        bypassMaster: Boolean = false,
    ) {
        // Forward to Android Logcat regardless of master switch
        val tag = "$TAG_PREFIX-$category"
        val extrasLine =
            if (extras.isNotEmpty()) {
                " | " + extras.entries.joinToString(", ") { "${it.key}=${it.value}" }
            } else {
                ""
            }
        val fullMessage = "$message$extrasLine"

        when (level) {
            Level.VERBOSE -> Log.v(tag, fullMessage)
            Level.DEBUG -> Log.d(tag, fullMessage)
            Level.INFO -> Log.i(tag, fullMessage)
            Level.WARN -> Log.w(tag, fullMessage)
            Level.ERROR -> Log.e(tag, fullMessage)
        }

        // Check if we should write to ring buffer
        if (!bypassMaster && !masterEnabled) return

        // Check category filter (empty means all enabled)
        if (categoriesEnabled.isNotEmpty() && category !in categoriesEnabled) return

        val entry =
            Entry(
                timestamp = System.currentTimeMillis(),
                category = category,
                level = level,
                message = message,
                extras = extras,
            )

        // Add to ring buffer (thread-safe)
        lock.write {
            if (ringBuffer.size >= MAX_ENTRIES) {
                ringBuffer.removeFirst()
            }
            ringBuffer.addLast(entry)

            // Update StateFlow with immutable list
            _history.value = ringBuffer.toList()
        }

        // Emit event for live consumers (non-blocking)
        _events.tryEmit(entry)
    }

    /**
     * Convenience: Log with just category and message (defaults to DEBUG level).
     */
    fun log(
        category: String,
        message: String,
    ) {
        log(category, Level.DEBUG, message)
    }

    /**
     * Get filtered entries by category.
     */
    fun getEntriesByCategory(category: String): List<Entry> =
        lock.read {
            ringBuffer.filter { it.category == category }
        }

    /**
     * Get filtered entries by level.
     */
    fun getEntriesByLevel(level: Level): List<Entry> =
        lock.read {
            ringBuffer.filter { it.level == level }
        }

    /**
     * Clear all log entries.
     */
    fun clear() {
        lock.write {
            ringBuffer.clear()
            _history.value = emptyList()
        }
    }

    /**
     * Get all unique categories in the log.
     */
    fun getAllCategories(): List<String> =
        lock.read {
            ringBuffer.map { it.category }.distinct().sorted()
        }

    /**
     * Export logs as text for sharing.
     */
    fun exportAsText(): String {
        val entries =
            lock.read {
                ringBuffer.toList()
            }
        return buildString {
            appendLine("=== App Log Export ===")
            appendLine("Total Entries: ${entries.size}")
            appendLine()

            entries.forEach { entry ->
                val extras =
                    if (entry.extras.isNotEmpty()) {
                        " | " + entry.extras.entries.joinToString(", ") { "${it.key}=${it.value}" }
                    } else {
                        ""
                    }
                appendLine("[${entry.level}] ${entry.category}: ${entry.message}$extras")
            }
        }
    }
}

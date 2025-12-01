package com.chris.m3usuite.core.logging

import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Unified logging repository for the entire application.
 *
 * This is the single sink for all log events across the app.
 * It consolidates logs from:
 * - App (general application logs)
 * - Telegram (TDLib operations, sync, streaming)
 * - Xtream (API calls, parsing)
 * - Player (Media3, playback events)
 * - Diagnostics (performance, telemetry)
 * - Crash (uncaught exceptions)
 *
 * Features:
 * - In-memory ring buffer (1000 entries max)
 * - Thread-safe access with ReentrantReadWriteLock
 * - Category-based filtering
 * - Level-based filtering
 * - Dual flow architecture:
 *   - StateFlow<List<UnifiedLogEntry>> for UI consumption
 *   - SharedFlow<UnifiedLogEntry> for live events (overlays)
 * - Forwards to Android Logcat with FishIT- prefixed tags
 *
 * Usage:
 * ```
 * UnifiedLogRepository.log(
 *     level = Level.INFO,
 *     category = "telegram",
 *     source = "T_TelegramServiceClient",
 *     message = "TDLib client initialized",
 *     details = mapOf("version" to "5.0.0")
 * )
 * ```
 */
object UnifiedLogRepository {
    private const val MAX_ENTRIES = 1000
    private const val TAG_PREFIX = "FishIT"

    /**
     * Log level enum with CRASH as highest severity.
     */
    enum class Level {
        VERBOSE,
        DEBUG,
        INFO,
        WARN,
        ERROR,
        CRASH,
    }

    /**
     * Standard log categories.
     */
    object Category {
        const val APP = "app"
        const val TELEGRAM = "telegram"
        const val XTREAM = "xtream"
        const val PLAYER = "player"
        const val DIAGNOSTICS = "diagnostics"
        const val CRASH = "crash"
    }

    /**
     * Unified log entry that captures all relevant context.
     */
    @Serializable
    data class UnifiedLogEntry(
        val timestamp: Long,
        val level: Level,
        val category: String,
        val source: String,
        val message: String,
        val details: Map<String, String>? = null,
    ) {
        /**
         * Format timestamp for display.
         */
        fun formattedTime(): String {
            val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }

        /**
         * Format details as a readable string.
         */
        fun formattedDetails(): String? {
            if (details.isNullOrEmpty()) return null
            return details.entries.joinToString(", ") { "${it.key}=${it.value}" }
        }
    }

    // Thread-safe ring buffer
    private val lock = ReentrantReadWriteLock()
    private val ringBuffer = ArrayDeque<UnifiedLogEntry>(MAX_ENTRIES)

    // StateFlow for log history (consumed by UnifiedLogScreen)
    private val _entries = MutableStateFlow<List<UnifiedLogEntry>>(emptyList())
    val entries: StateFlow<List<UnifiedLogEntry>> = _entries.asStateFlow()

    // SharedFlow for live events (consumed by overlays, debug UIs)
    private val _events =
        MutableSharedFlow<UnifiedLogEntry>(
            replay = 0,
            extraBufferCapacity = 10,
        )
    val events: SharedFlow<UnifiedLogEntry> = _events.asSharedFlow()

    /**
     * Log a unified entry.
     *
     * @param level Log level
     * @param category Category (app, telegram, xtream, player, diagnostics, crash)
     * @param source Source component (e.g., class name)
     * @param message Human-readable message
     * @param details Optional key-value details for context
     */
    fun log(
        level: Level,
        category: String,
        source: String,
        message: String,
        details: Map<String, String>? = null,
    ) {
        val entry =
            UnifiedLogEntry(
                timestamp = System.currentTimeMillis(),
                level = level,
                category = category,
                source = source,
                message = message,
                details = details,
            )

        // Add to ring buffer (thread-safe)
        lock.write {
            if (ringBuffer.size >= MAX_ENTRIES) {
                ringBuffer.removeFirst()
            }
            ringBuffer.addLast(entry)

            // Update StateFlow with immutable list
            _entries.value = ringBuffer.toList()
        }

        // Emit event for live consumers (non-blocking)
        _events.tryEmit(entry)

        // Forward to Android Logcat
        val tag = "$TAG_PREFIX-$category-$source"
        val detailsLine =
            if (!details.isNullOrEmpty()) {
                " | " + details.entries.joinToString(", ") { "${it.key}=${it.value}" }
            } else {
                ""
            }
        val fullMessage = "$message$detailsLine"

        when (level) {
            Level.VERBOSE -> Log.v(tag, fullMessage)
            Level.DEBUG -> Log.d(tag, fullMessage)
            Level.INFO -> Log.i(tag, fullMessage)
            Level.WARN -> Log.w(tag, fullMessage)
            Level.ERROR, Level.CRASH -> Log.e(tag, fullMessage)
        }
    }

    // Convenience methods for each level

    fun verbose(
        category: String,
        source: String,
        message: String,
        details: Map<String, String>? = null,
    ) {
        log(Level.VERBOSE, category, source, message, details)
    }

    fun debug(
        category: String,
        source: String,
        message: String,
        details: Map<String, String>? = null,
    ) {
        log(Level.DEBUG, category, source, message, details)
    }

    fun info(
        category: String,
        source: String,
        message: String,
        details: Map<String, String>? = null,
    ) {
        log(Level.INFO, category, source, message, details)
    }

    fun warn(
        category: String,
        source: String,
        message: String,
        details: Map<String, String>? = null,
    ) {
        log(Level.WARN, category, source, message, details)
    }

    fun error(
        category: String,
        source: String,
        message: String,
        details: Map<String, String>? = null,
    ) {
        log(Level.ERROR, category, source, message, details)
    }

    fun crash(
        source: String,
        message: String,
        details: Map<String, String>? = null,
    ) {
        log(Level.CRASH, Category.CRASH, source, message, details)
    }

    /**
     * Get filtered entries by level.
     */
    fun getEntriesByLevel(level: Level): List<UnifiedLogEntry> =
        lock.read {
            ringBuffer.filter { it.level == level }
        }

    /**
     * Get filtered entries by category.
     */
    fun getEntriesByCategory(category: String): List<UnifiedLogEntry> =
        lock.read {
            ringBuffer.filter { it.category == category }
        }

    /**
     * Get filtered entries by source.
     */
    fun getEntriesBySource(source: String): List<UnifiedLogEntry> =
        lock.read {
            ringBuffer.filter { it.source == source }
        }

    /**
     * Get entries with combined filters.
     */
    fun getEntries(
        level: Level? = null,
        category: String? = null,
        source: String? = null,
    ): List<UnifiedLogEntry> =
        lock.read {
            ringBuffer.filter { entry ->
                (level == null || entry.level == level) &&
                    (category == null || entry.category == category) &&
                    (source == null || entry.source == source)
            }
        }

    /**
     * Clear all log entries.
     */
    fun clear() {
        lock.write {
            ringBuffer.clear()
            _entries.value = emptyList()
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
     * Get all unique sources in the log.
     */
    fun getAllSources(): List<String> =
        lock.read {
            ringBuffer.map { it.source }.distinct().sorted()
        }

    /**
     * Count entries by level.
     */
    fun countByLevel(): Map<Level, Int> =
        lock.read {
            Level.entries.associateWith { level ->
                ringBuffer.count { it.level == level }
            }
        }

    /**
     * Export logs as text for sharing.
     */
    fun exportAsText(
        level: Level? = null,
        category: String? = null,
    ): String {
        val filtered = getEntries(level = level, category = category)
        val exportTimeFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        return buildString {
            appendLine("=== Unified Log Export ===")
            appendLine("Total Entries: ${filtered.size}")
            appendLine("Export Time: ${exportTimeFormatter.format(Date())}")
            level?.let { appendLine("Filter Level: $it") }
            category?.let { appendLine("Filter Category: $it") }
            appendLine()

            filtered.forEach { entry ->
                appendLine("[${entry.formattedTime()}] [${entry.level}] [${entry.category}] [${entry.source}]")
                appendLine("  ${entry.message}")
                entry.formattedDetails()?.let { details ->
                    appendLine("  Details: $details")
                }
                appendLine()
            }
        }
    }
}

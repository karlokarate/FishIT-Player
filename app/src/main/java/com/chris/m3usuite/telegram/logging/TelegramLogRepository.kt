package com.chris.m3usuite.telegram.logging

import com.chris.m3usuite.diagnostics.DiagnosticsLogger
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Log entry for Telegram operations.
 */
data class TgLogEntry(
    val timestamp: Long,
    val level: LogLevel,
    val source: String,
    val message: String,
    val details: Map<String, String>? = null,
) {
    enum class LogLevel {
        VERBOSE,
        DEBUG,
        INFO,
        WARN,
        ERROR,
    }

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

/**
 * Comprehensive logging repository for all Telegram operations.
 *
 * Features:
 * - In-memory ringbuffer (500 entries max)
 * - Thread-safe access
 * - Dual flow architecture:
 *   - StateFlow<List<TgLogEntry>> for log history (UI viewing)
 *   - SharedFlow<TgLogEntry> for live events (overlays, notifications)
 * - Integration with DiagnosticsLogger for logcat/file output
 * - Level-based filtering
 * - Source-based filtering
 *
 * Usage:
 * ```
 * TelegramLogRepository.log(
 *     level = TgLogEntry.LogLevel.INFO,
 *     source = "T_TelegramServiceClient",
 *     message = "TDLib client initialized",
 *     details = mapOf("version" to "5.0.0")
 * )
 * ```
 */
object TelegramLogRepository {
    private const val MAX_ENTRIES = 500

    // Thread-safe ringbuffer for log entries
    private val lock = ReentrantReadWriteLock()
    private val ringBuffer = ArrayDeque<TgLogEntry>(MAX_ENTRIES)

    // StateFlow for log history (consumed by UI)
    private val _entries = MutableStateFlow<List<TgLogEntry>>(emptyList())
    val entries: StateFlow<List<TgLogEntry>> = _entries.asStateFlow()

    // SharedFlow for live events (consumed by overlays)
    private val _events =
        MutableSharedFlow<TgLogEntry>(
            replay = 0,
            extraBufferCapacity = 10,
        )
    val events: SharedFlow<TgLogEntry> = _events.asSharedFlow()

    /**
     * Log a Telegram event.
     *
     * @param level Log level
     * @param source Source component (e.g., "T_TelegramServiceClient", "T_ChatBrowser")
     * @param message Human-readable message
     * @param details Optional key-value details for context
     */
    fun log(
        level: TgLogEntry.LogLevel,
        source: String,
        message: String,
        details: Map<String, String>? = null,
    ) {
        val entry =
            TgLogEntry(
                timestamp = System.currentTimeMillis(),
                level = level,
                source = source,
                message = message,
                details = details,
            )

        // Add to ringbuffer (thread-safe)
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

        // Forward to DiagnosticsLogger for logcat/file
        val diagnosticsLevel =
            when (level) {
                TgLogEntry.LogLevel.VERBOSE -> DiagnosticsLogger.LogLevel.VERBOSE
                TgLogEntry.LogLevel.DEBUG -> DiagnosticsLogger.LogLevel.DEBUG
                TgLogEntry.LogLevel.INFO -> DiagnosticsLogger.LogLevel.INFO
                TgLogEntry.LogLevel.WARN -> DiagnosticsLogger.LogLevel.WARN
                TgLogEntry.LogLevel.ERROR -> DiagnosticsLogger.LogLevel.ERROR
            }

        val metadata =
            buildMap {
                put("source", source)
                details?.forEach { (k, v) -> put(k, v) }
            }

        DiagnosticsLogger.logEvent(
            category = "telegram",
            event = message,
            level = diagnosticsLevel,
            metadata = metadata,
        )
    }

    /**
     * Convenience: Log INFO level.
     */
    fun info(
        source: String,
        message: String,
        details: Map<String, String>? = null,
    ) {
        log(TgLogEntry.LogLevel.INFO, source, message, details)
    }

    /**
     * Convenience: Log WARN level.
     */
    fun warn(
        source: String,
        message: String,
        details: Map<String, String>? = null,
    ) {
        log(TgLogEntry.LogLevel.WARN, source, message, details)
    }

    /**
     * Convenience: Log ERROR level.
     */
    fun error(
        source: String,
        message: String,
        details: Map<String, String>? = null,
    ) {
        log(TgLogEntry.LogLevel.ERROR, source, message, details)
    }

    /**
     * Convenience: Log ERROR level with exception.
     */
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
        log(TgLogEntry.LogLevel.ERROR, source, message, errorDetails)
    }

    /**
     * Convenience: Log DEBUG level.
     */
    fun debug(
        source: String,
        message: String,
        details: Map<String, String>? = null,
    ) {
        log(TgLogEntry.LogLevel.DEBUG, source, message, details)
    }

    /**
     * Get filtered entries by level.
     */
    fun getEntriesByLevel(level: TgLogEntry.LogLevel): List<TgLogEntry> =
        lock.read {
            ringBuffer.filter { it.level == level }
        }

    /**
     * Get filtered entries by source.
     */
    fun getEntriesBySource(source: String): List<TgLogEntry> =
        lock.read {
            ringBuffer.filter { it.source == source }
        }

    /**
     * Get filtered entries by level and source.
     */
    fun getEntries(
        level: TgLogEntry.LogLevel? = null,
        source: String? = null,
    ): List<TgLogEntry> =
        lock.read {
            ringBuffer.filter { entry ->
                (level == null || entry.level == level) &&
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
     * Get all unique sources in the log.
     */
    fun getAllSources(): List<String> =
        lock.read {
            ringBuffer.map { it.source }.distinct().sorted()
        }

    /**
     * Export logs as text for sharing.
     */
    fun exportAsText(
        level: TgLogEntry.LogLevel? = null,
        source: String? = null,
    ): String {
        val filteredEntries = getEntries(level, source)
        val exportTimeFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return buildString {
            appendLine("=== Telegram Log Export ===")
            appendLine("Total Entries: ${filteredEntries.size}")
            appendLine("Export Time: ${exportTimeFormatter.format(Date())}")
            appendLine()

            filteredEntries.forEach { entry ->
                appendLine("[${entry.formattedTime()}] [${entry.level}] [${entry.source}]")
                appendLine("  ${entry.message}")
                entry.formattedDetails()?.let { details ->
                    appendLine("  Details: $details")
                }
                appendLine()
            }
        }
    }

    /**
     * Log sync progress from TelegramSyncWorker.
     */
    fun logSyncEvent(
        phase: String,
        progress: Int? = null,
        total: Int? = null,
        details: Map<String, String>? = null,
    ) {
        val syncDetails =
            buildMap {
                put("phase", phase)
                progress?.let { put("progress", it.toString()) }
                total?.let { put("total", it.toString()) }
                details?.forEach { (k, v) -> put(k, v) }
            }

        info(
            source = "TelegramSyncWorker",
            message = "Sync: $phase",
            details = syncDetails,
        )
    }

    /**
     * Log auth state change from T_TelegramSession.
     */
    fun logAuthStateChange(
        from: String,
        to: String,
        details: Map<String, String>? = null,
    ) {
        info(
            source = "T_TelegramSession",
            message = "Auth state: $from -> $to",
            details = details,
        )
    }

    /**
     * Log file download progress from T_TelegramFileDownloader.
     */
    fun logFileDownload(
        fileId: Int,
        progress: Int,
        total: Int,
        status: String,
    ) {
        debug(
            source = "T_TelegramFileDownloader",
            message = "File download: $status",
            details =
                mapOf(
                    "file_id" to fileId.toString(),
                    "progress" to progress.toString(),
                    "total" to total.toString(),
                    "percentage" to "${(progress * 100 / total.coerceAtLeast(1))}%",
                ),
        )
    }

    /**
     * Log chat browsing activity from T_ChatBrowser.
     */
    fun logChatActivity(
        chatId: Long,
        action: String,
        details: Map<String, String>? = null,
    ) {
        debug(
            source = "T_ChatBrowser",
            message = "Chat $chatId: $action",
            details = details,
        )
    }

    /**
     * Log streaming activity from TelegramDataSource.
     */
    fun logStreamingActivity(
        fileId: Int,
        action: String,
        details: Map<String, String>? = null,
    ) {
        debug(
            source = "TelegramDataSource",
            message = "Stream file $fileId: $action",
            details = details,
        )
    }
}

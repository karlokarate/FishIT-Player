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
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Log entry for Telegram operations.
 */
data class TgLogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val level: LogLevel,
    val source: String,
    val message: String,
    val details: String? = null,
    val throwable: Throwable? = null,
) {
    fun formattedTimestamp(): String {
        val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}

/**
 * Log levels for Telegram operations.
 */
enum class LogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR,
}

/**
 * Repository for Telegram logging.
 * Maintains an in-memory ringbuffer of log entries and provides flows for UI consumption.
 *
 * Features:
 * - In-memory ringbuffer (configurable size, default 500 entries)
 * - StateFlow of all entries for UI display
 * - SharedFlow of new events for real-time notifications
 * - Integration with DiagnosticsLogger for logcat/file output
 */
class TelegramLogRepository(
    private val maxEntries: Int = 500,
) {
    // Thread-safe ringbuffer for log entries
    private val entries = ConcurrentLinkedQueue<TgLogEntry>()

    // StateFlow for all entries
    private val _entriesFlow = MutableStateFlow<List<TgLogEntry>>(emptyList())
    val entriesFlow: StateFlow<List<TgLogEntry>> = _entriesFlow.asStateFlow()

    // SharedFlow for new events (for real-time notifications)
    private val _eventsFlow = MutableSharedFlow<TgLogEntry>(replay = 0, extraBufferCapacity = 10)
    val eventsFlow: SharedFlow<TgLogEntry> = _eventsFlow.asSharedFlow()

    /**
     * Log a message.
     */
    fun log(
        level: LogLevel,
        source: String,
        message: String,
        details: String? = null,
        throwable: Throwable? = null,
    ) {
        val entry = TgLogEntry(
            timestamp = System.currentTimeMillis(),
            level = level,
            source = source,
            message = message,
            details = details,
            throwable = throwable,
        )

        // Add to ringbuffer
        entries.add(entry)
        while (entries.size > maxEntries) {
            entries.poll()
        }

        // Update flows
        _entriesFlow.value = entries.toList()
        _eventsFlow.tryEmit(entry)

        // Also log to DiagnosticsLogger for logcat/file
        val logMessage = "$message${details?.let { " | $it" } ?: ""}"
        val diagnosticsLevel = when (level) {
            LogLevel.DEBUG -> DiagnosticsLogger.LogLevel.DEBUG
            LogLevel.INFO -> DiagnosticsLogger.LogLevel.INFO
            LogLevel.WARN -> DiagnosticsLogger.LogLevel.WARN
            LogLevel.ERROR -> DiagnosticsLogger.LogLevel.ERROR
        }
        
        if (throwable != null) {
            DiagnosticsLogger.logError(
                category = "telegram",
                event = source,
                throwable = throwable,
                metadata = mapOf("message" to logMessage)
            )
        } else {
            DiagnosticsLogger.logEvent(
                category = "telegram",
                event = source,
                level = diagnosticsLevel,
                metadata = mapOf("message" to logMessage)
            )
        }
    }

    /**
     * Convenience methods for different log levels.
     */
    fun debug(source: String, message: String, details: String? = null) {
        log(LogLevel.DEBUG, source, message, details)
    }

    fun info(source: String, message: String, details: String? = null) {
        log(LogLevel.INFO, source, message, details)
    }

    fun warn(source: String, message: String, details: String? = null, throwable: Throwable? = null) {
        log(LogLevel.WARN, source, message, details, throwable)
    }

    fun error(source: String, message: String, details: String? = null, throwable: Throwable? = null) {
        log(LogLevel.ERROR, source, message, details, throwable)
    }

    /**
     * Clear all log entries.
     */
    fun clear() {
        entries.clear()
        _entriesFlow.value = emptyList()
    }

    /**
     * Get filtered entries by level and/or source.
     */
    fun getFiltered(
        minLevel: LogLevel? = null,
        source: String? = null,
    ): List<TgLogEntry> {
        return entries.toList().filter { entry ->
            val levelMatch = minLevel == null || entry.level.ordinal >= minLevel.ordinal
            val sourceMatch = source == null || entry.source.contains(source, ignoreCase = true)
            levelMatch && sourceMatch
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: TelegramLogRepository? = null

        fun getInstance(): TelegramLogRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: TelegramLogRepository().also {
                    INSTANCE = it
                }
            }
    }
}

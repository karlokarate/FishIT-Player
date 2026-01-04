package com.fishit.player.infra.logging

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.util.concurrent.locks.ReentrantReadWriteLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Data-only representation of a throwable for log buffer storage.
 *
 * **Contract (LOGGING_CONTRACT_V2):**
 * - No real Throwable references may be stored in the log buffer
 * - Only the type name and redacted message are retained
 * - This ensures no sensitive data persists via exception messages or stack traces
 *
 * @property type Simple class name of the original throwable (e.g., "IOException")
 * @property message Redacted error message (secrets replaced with ***)
 */
data class RedactedThrowableInfo(
    val type: String?,
    val message: String?,
) {
    override fun toString(): String = "[$type] $message"
}

/**
 * A single buffered log entry.
 *
 * @property timestamp Unix timestamp in milliseconds
 * @property priority Android Log priority (Log.DEBUG, Log.INFO, etc.)
 * @property tag Log tag
 * @property message Log message
 * @property throwableInfo Optional redacted throwable info (no real Throwable retained)
 */
data class BufferedLogEntry(
    val timestamp: Long,
    val priority: Int,
    val tag: String?,
    val message: String,
    val throwableInfo: RedactedThrowableInfo? = null,
) {
    /**
     * Format timestamp as HH:mm:ss.SSS
     */
    fun formattedTime(): String {
        val sdf = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US)
        return sdf.format(java.util.Date(timestamp))
    }

    /**
     * Get priority as string (DEBUG, INFO, WARN, ERROR, etc.)
     */
    fun priorityString(): String =
        when (priority) {
            Log.VERBOSE -> "VERBOSE"
            Log.DEBUG -> "DEBUG"
            Log.INFO -> "INFO"
            Log.WARN -> "WARN"
            Log.ERROR -> "ERROR"
            Log.ASSERT -> "ASSERT"
            else -> "UNKNOWN"
        }
}

/**
 * Timber Tree that buffers log entries in a ring buffer.
 *
 * This tree captures all log entries and stores them in memory for later retrieval.
 * It maintains a fixed-size ring buffer to prevent unbounded memory growth.
 *
 * **Usage:**
 * 1. Plant this tree via [UnifiedLogInitializer.init]
 * 2. Inject [LogBufferProvider] wherever logs need to be displayed
 *
 * **Architecture:**
 * - This tree is planted alongside Timber.DebugTree
 * - It captures logs from all sources (including UnifiedLog facade)
 * - Provides a Flow for reactive UI updates
 *
 * @param maxEntries Maximum number of log entries to retain (default: 500)
 */
class LogBufferTree(
    private val maxEntries: Int = DEFAULT_BUFFER_SIZE,
) : Timber.Tree() {
    private val lock = ReentrantReadWriteLock()
    private val buffer = ArrayDeque<BufferedLogEntry>(maxEntries)
    private val _entriesFlow = MutableStateFlow<List<BufferedLogEntry>>(emptyList())

    /**
     * Flow of buffered log entries.
     * Emits a new list whenever a log entry is added.
     */
    val entriesFlow: Flow<List<BufferedLogEntry>> = _entriesFlow.asStateFlow()

    /**
     * Get current buffered entries (snapshot).
     */
    fun getEntries(): List<BufferedLogEntry> =
        lock.read {
            buffer.toList()
        }

    /**
     * Clear all buffered entries.
     */
    fun clear() {
        lock.write {
            buffer.clear()
            _entriesFlow.value = emptyList()
        }
    }

    /**
     * Get entry count.
     */
    fun size(): Int = lock.read { buffer.size }

    override fun log(
        priority: Int,
        tag: String?,
        message: String,
        t: Throwable?,
    ) {
        // MANDATORY: Redact sensitive information before buffering
        // Contract: No secrets may persist in memory (LOGGING_CONTRACT_V2)
        // Contract: No real Throwable references may be stored (prevents memory leaks & secret retention)
        val redactedMessage = LogRedactor.redact(message)
        val redactedThrowableInfo =
            t?.let { original ->
                RedactedThrowableInfo(
                    type = original::class.simpleName,
                    message = LogRedactor.redact(original.message ?: ""),
                )
            }

        val entry =
            BufferedLogEntry(
                timestamp = System.currentTimeMillis(),
                priority = priority,
                tag = tag,
                message = redactedMessage,
                throwableInfo = redactedThrowableInfo,
            )

        lock.write {
            // Ring buffer: remove oldest if at capacity
            if (buffer.size >= maxEntries) {
                buffer.removeFirst()
            }
            buffer.addLast(entry)
            _entriesFlow.value = buffer.toList()
        }
    }

    companion object {
        /**
         * Default buffer size - 500 entries is a good balance between
         * memory usage and useful log history.
         */
        const val DEFAULT_BUFFER_SIZE = 500

        /**
         * Singleton instance for global access.
         * Initialized by [UnifiedLogInitializer].
         */
        @Volatile
        private var instance: LogBufferTree? = null

        /**
         * Get or create the singleton instance.
         *
         * @param maxEntries Buffer size (only used if creating new instance)
         * @return The singleton LogBufferTree
         */
        @JvmStatic
        fun getInstance(maxEntries: Int = DEFAULT_BUFFER_SIZE): LogBufferTree =
            instance ?: synchronized(this) {
                instance ?: LogBufferTree(maxEntries).also { instance = it }
            }

        /**
         * Get singleton instance or null if not initialized.
         */
        @JvmStatic
        fun getInstanceOrNull(): LogBufferTree? = instance
    }
}

/**
 * Provider interface for accessing buffered logs.
 *
 * This is the DI-friendly interface for components that need to read logs.
 * Implementations are provided via Hilt.
 *
 * **Contract:**
 * - Returns logs in chronological order (oldest first)
 * - Provides reactive Flow for live updates
 * - Supports clearing logs
 */
interface LogBufferProvider {
    /**
     * Observe buffered logs as a Flow.
     *
     * @param limit Maximum number of entries to return (most recent)
     * @return Flow of log entries
     */
    fun observeLogs(limit: Int = 100): Flow<List<BufferedLogEntry>>

    /**
     * Get current logs (snapshot).
     *
     * @param limit Maximum number of entries to return (most recent)
     * @return List of log entries
     */
    fun getLogs(limit: Int = 100): List<BufferedLogEntry>

    /**
     * Clear all buffered logs.
     */
    fun clearLogs()

    /**
     * Get total number of buffered entries.
     */
    fun getLogCount(): Int
}

/**
 * Default implementation of [LogBufferProvider] using [LogBufferTree].
 */
@Singleton
class DefaultLogBufferProvider
    @Inject
    constructor() : LogBufferProvider {
        private val tree: LogBufferTree
            get() = LogBufferTree.getInstance()

        override fun observeLogs(limit: Int): Flow<List<BufferedLogEntry>> =
            tree.entriesFlow.map { entries ->
                entries.takeLast(limit)
            }

        override fun getLogs(limit: Int): List<BufferedLogEntry> = tree.getEntries().takeLast(limit)

        override fun clearLogs() {
            tree.clear()
        }

        override fun getLogCount(): Int = tree.size()
    }

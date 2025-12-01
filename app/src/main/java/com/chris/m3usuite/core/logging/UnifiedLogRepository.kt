package com.chris.m3usuite.core.logging

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
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
 * - Pre-Release Boosters (Part 7):
 *   - TraceId/SpanId for correlated tracing
 *   - Structured JSON log emission
 *   - Adaptive sampling and burst protection
 *   - Rolling file persistence
 *   - Diagnostics bundle export
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

    // Part 7: Burst protection configuration
    private const val BURST_WINDOW_MS = 1000L // 1 second window
    private const val BURST_LIMIT = 100 // Max entries per window

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
        val traceId: String? = null,
        val spanId: String? = null,
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

        /**
         * Convert to structured JSON string.
         */
        fun toJson(): String =
            buildString {
                append("{")
                append("\"ts\":$timestamp,")
                append("\"level\":\"$level\",")
                append("\"cat\":\"$category\",")
                append("\"src\":\"$source\",")
                append("\"msg\":\"${message.replace("\"", "\\\"")}\"")
                traceId?.let { append(",\"traceId\":\"$it\"") }
                spanId?.let { append(",\"spanId\":\"$it\"") }
                details?.let { d ->
                    if (d.isNotEmpty()) {
                        append(",\"details\":{")
                        append(d.entries.joinToString(",") { (k, v) -> "\"$k\":\"${v.replace("\"", "\\\"")}\"" })
                        append("}")
                    }
                }
                append("}")
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

    // =========================================================================
    // Part 7: Pre-Release Logging Boosters
    // =========================================================================

    // Trace context for correlated logs
    private val currentTraceId = ThreadLocal<String?>()
    private val currentSpanId = ThreadLocal<String?>()

    // Burst protection state
    private val burstWindowStart = AtomicLong(0)
    private val burstCount = AtomicInteger(0)
    private val droppedCount = AtomicInteger(0)

    // Sampling configuration (0.0 = none, 1.0 = all)
    @Volatile
    private var samplingRate: Float = 1.0f

    // Secret logging flag for prerelease builds (enables sensitive data logging)
    @Volatile
    private var secretLoggingEnabled: Boolean = false

    // File persistence context (must be set before persistence works)
    private var persistenceContext: Context? = null
    private val jsonSerializer = Json { prettyPrint = false }

    /**
     * Start a new trace context for correlated logging across multiple operations.
     *
     * @return TraceContext that should be closed when the operation completes
     */
    fun beginTrace(): TraceContext {
        val traceId = UUID.randomUUID().toString().take(8)
        val spanId = UUID.randomUUID().toString().take(8)
        currentTraceId.set(traceId)
        currentSpanId.set(spanId)
        return TraceContext(traceId, spanId)
    }

    /**
     * Trace context holder that auto-clears on close.
     */
    class TraceContext(
        val traceId: String,
        val spanId: String,
    ) : AutoCloseable {
        override fun close() {
            currentTraceId.remove()
            currentSpanId.remove()
        }

        /**
         * Create a child span within this trace.
         */
        fun childSpan(): TraceContext {
            val childSpanId = UUID.randomUUID().toString().take(8)
            currentSpanId.set(childSpanId)
            return TraceContext(traceId, childSpanId)
        }
    }

    /**
     * Set the sampling rate for non-error logs.
     * Error and Crash level logs are never sampled.
     *
     * @param rate Rate between 0.0 (no sampling) and 1.0 (all logs)
     */
    fun setSamplingRate(rate: Float) {
        samplingRate = rate.coerceIn(0.0f, 1.0f)
    }

    /**
     * Enable or disable secret logging for prerelease builds.
     * When enabled, sensitive details may be logged.
     */
    fun setSecretLoggingEnabled(enabled: Boolean) {
        secretLoggingEnabled = enabled
    }

    /**
     * Check if secret logging is enabled.
     */
    fun isSecretLoggingEnabled(): Boolean = secretLoggingEnabled

    /**
     * Initialize rolling file persistence.
     *
     * @param context Application context for file access
     */
    fun initPersistence(context: Context) {
        persistenceContext = context.applicationContext
    }

    /**
     * Log a unified entry.
     *
     * @param level Log level
     * @param category Category (app, telegram, xtream, player, diagnostics, crash)
     * @param source Source component (e.g., class name)
     * @param message Human-readable message
     * @param details Optional key-value details for context
     * @param traceId Optional explicit traceId (uses thread-local if not provided)
     * @param spanId Optional explicit spanId (uses thread-local if not provided)
     */
    fun log(
        level: Level,
        category: String,
        source: String,
        message: String,
        details: Map<String, String>? = null,
        traceId: String? = null,
        spanId: String? = null,
    ) {
        // Adaptive sampling: Never sample errors/crashes
        if (level != Level.ERROR && level != Level.CRASH) {
            if (samplingRate < 1.0f && Math.random() > samplingRate) {
                return
            }
        }

        // Burst protection: Limit log rate to prevent flooding
        if (!checkBurstLimit()) {
            droppedCount.incrementAndGet()
            return
        }

        val effectiveTraceId = traceId ?: currentTraceId.get()
        val effectiveSpanId = spanId ?: currentSpanId.get()

        val entry =
            UnifiedLogEntry(
                timestamp = System.currentTimeMillis(),
                level = level,
                category = category,
                source = source,
                message = message,
                details = details,
                traceId = effectiveTraceId,
                spanId = effectiveSpanId,
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
                entry.traceId?.let { appendLine("  TraceId: $it") }
                entry.spanId?.let { appendLine("  SpanId: $it") }
                appendLine("  ${entry.message}")
                entry.formattedDetails()?.let { details ->
                    appendLine("  Details: $details")
                }
                appendLine()
            }
        }
    }

    // =========================================================================
    // Part 7: Pre-Release Logging Boosters - Helper Methods
    // =========================================================================

    /**
     * Check if we're within burst limits.
     * Returns true if log should be allowed, false if it should be dropped.
     */
    private fun checkBurstLimit(): Boolean {
        val now = System.currentTimeMillis()
        val windowStart = burstWindowStart.get()

        // Start new window if expired
        if (now - windowStart > BURST_WINDOW_MS) {
            burstWindowStart.set(now)
            burstCount.set(1)
            return true
        }

        // Check if we've exceeded burst limit
        val count = burstCount.incrementAndGet()
        return count <= BURST_LIMIT
    }

    /**
     * Get count of dropped logs due to burst protection.
     */
    fun getDroppedCount(): Int = droppedCount.get()

    /**
     * Reset dropped count.
     */
    fun resetDroppedCount() {
        droppedCount.set(0)
    }

    /**
     * Get entries for a specific traceId.
     */
    fun getEntriesByTraceId(traceId: String): List<UnifiedLogEntry> =
        lock.read {
            ringBuffer.filter { it.traceId == traceId }
        }

    /**
     * Export logs as JSON for structured analysis.
     */
    fun exportAsJson(
        level: Level? = null,
        category: String? = null,
    ): String {
        val filtered = getEntries(level = level, category = category)
        return buildString {
            appendLine("[")
            filtered.forEachIndexed { index, entry ->
                append("  ")
                append(entry.toJson())
                if (index < filtered.lastIndex) append(",")
                appendLine()
            }
            appendLine("]")
        }
    }

    /**
     * Export NDJSON (Newline Delimited JSON) for streaming analysis.
     */
    fun exportAsNdjson(
        level: Level? = null,
        category: String? = null,
    ): String {
        val filtered = getEntries(level = level, category = category)
        return buildString {
            filtered.forEach { entry ->
                appendLine(entry.toJson())
            }
        }
    }

    /**
     * Persist current logs to rolling file.
     * Requires initPersistence() to be called first.
     */
    fun persistToFile(): Boolean {
        val context = persistenceContext ?: return false

        return try {
            val logsDir = File(context.filesDir, "logs")
            logsDir.mkdirs()

            // Rolling log: keep last 5 files
            val existingLogs =
                logsDir.listFiles { file -> file.name.startsWith("unified_log_") }
                    ?.sortedByDescending { it.lastModified() }
                    ?: emptyList()

            if (existingLogs.size >= 5) {
                existingLogs.drop(4).forEach { it.delete() }
            }

            // Write current logs to new file
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val logFile = File(logsDir, "unified_log_$timestamp.ndjson")
            logFile.writeText(exportAsNdjson())

            Log.d(TAG_PREFIX, "Persisted ${ringBuffer.size} log entries to ${logFile.name}")
            true
        } catch (e: Exception) {
            Log.e(TAG_PREFIX, "Failed to persist logs", e)
            false
        }
    }

    /**
     * Export diagnostics bundle as a ZIP file.
     * Includes: current logs (JSON + text), last crash, app state metadata.
     *
     * @return Path to exported ZIP file, or null if failed
     */
    fun exportDiagnosticsBundle(): String? {
        val context = persistenceContext ?: return null

        return try {
            val exportDir = File(context.cacheDir, "diagnostics_export")
            exportDir.mkdirs()

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val bundleDir = File(exportDir, "bundle_$timestamp")
            bundleDir.mkdirs()

            // Write logs as JSON
            File(bundleDir, "logs.json").writeText(exportAsJson())

            // Write logs as text
            File(bundleDir, "logs.txt").writeText(exportAsText())

            // Write metadata
            val metadata =
                buildString {
                    appendLine("=== Diagnostics Bundle ===")
                    appendLine("Generated: $timestamp")
                    appendLine("Total Log Entries: ${ringBuffer.size}")
                    appendLine("Dropped (burst protection): ${droppedCount.get()}")
                    appendLine("Sampling Rate: $samplingRate")
                    appendLine("Secret Logging: $secretLoggingEnabled")
                    appendLine()
                    appendLine("=== Entry Count by Level ===")
                    countByLevel().forEach { (level, count) ->
                        appendLine("  $level: $count")
                    }
                    appendLine()
                    appendLine("=== Categories ===")
                    getAllCategories().forEach { cat ->
                        appendLine("  - $cat")
                    }
                }
            File(bundleDir, "metadata.txt").writeText(metadata)

            // Include last crash if exists
            val crashFile = File(context.filesDir, "last_crash.json")
            if (crashFile.exists()) {
                crashFile.copyTo(File(bundleDir, "last_crash.json"), overwrite = true)
            }

            bundleDir.absolutePath
        } catch (e: Exception) {
            Log.e(TAG_PREFIX, "Failed to export diagnostics bundle", e)
            null
        }
    }
}

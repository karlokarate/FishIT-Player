package com.chris.m3usuite.diagnostics

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong

/**
 * Structured diagnostics logger for performance monitoring and debugging.
 *
 * Features:
 * - JSON-serializable events for easy analysis
 * - Async logging to avoid blocking main thread
 * - Configurable log levels
 * - Screen/component context tracking
 * - No sensitive data logging (no tokens, credentials, or private content)
 *
 * Usage:
 *   DiagnosticsLogger.logEvent(
 *     category = "xtream",
 *     event = "load_live_list",
 *     metadata = mapOf("count" to "150", "duration_ms" to "234")
 *   )
 */
object DiagnosticsLogger {
    private const val TAG = "DiagnosticsLogger"
    private const val MAX_QUEUE_SIZE = 1000

    // Configuration
    var isEnabled: Boolean = true
    var logLevel: LogLevel = LogLevel.INFO
    var enableConsoleOutput: Boolean = true

    enum class LogLevel {
        VERBOSE,
        DEBUG,
        INFO,
        WARN,
        ERROR,
    }

    @Serializable
    data class DiagnosticEvent(
        val timestamp: Long,
        val eventId: Long,
        val category: String,
        val event: String,
        val level: String,
        val screen: String? = null,
        val component: String? = null,
        val metadata: Map<String, String> = emptyMap(),
        val buildInfo: String? = null,
    )

    private val eventIdGenerator = AtomicLong(0)
    private val eventQueue = ConcurrentLinkedQueue<DiagnosticEvent>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val logChannel = Channel<DiagnosticEvent>(Channel.BUFFERED)

    private val json =
        Json {
            prettyPrint = false
            ignoreUnknownKeys = true
        }

    init {
        // Background coroutine to process log events
        scope.launch {
            for (event in logChannel) {
                processEvent(event)
            }
        }
    }

    /**
     * Log a diagnostic event.
     *
     * @param category Event category (e.g., "xtream", "telegram", "media3", "compose_tv")
     * @param event Event name (e.g., "load_live_list", "playback_start", "focus_change")
     * @param level Log level
     * @param screen Current screen identifier
     * @param component Component/feature identifier
     * @param metadata Additional context (DO NOT include sensitive data)
     */
    fun logEvent(
        category: String,
        event: String,
        level: LogLevel = LogLevel.INFO,
        screen: String? = null,
        component: String? = null,
        metadata: Map<String, String> = emptyMap(),
    ) {
        if (!isEnabled || level.ordinal < logLevel.ordinal) return

        // Sanitize metadata to ensure no sensitive data
        val sanitizedMetadata =
            metadata.filterKeys { key ->
                !key.contains("token", ignoreCase = true) &&
                    !key.contains("password", ignoreCase = true) &&
                    !key.contains("secret", ignoreCase = true) &&
                    !key.contains("auth", ignoreCase = true)
            }

        val diagnosticEvent =
            DiagnosticEvent(
                timestamp = System.currentTimeMillis(),
                eventId = eventIdGenerator.incrementAndGet(),
                category = category,
                event = event,
                level = level.name,
                screen = screen,
                component = component,
                metadata = sanitizedMetadata,
                buildInfo = getBuildInfo(),
            )

        // Async processing
        scope.launch {
            logChannel.send(diagnosticEvent)
        }
    }

    /**
     * Log an error with exception details.
     */
    fun logError(
        category: String,
        event: String,
        throwable: Throwable,
        screen: String? = null,
        component: String? = null,
        metadata: Map<String, String> = emptyMap(),
    ) {
        val errorMetadata =
            metadata.toMutableMap().apply {
                put("error_type", throwable::class.java.simpleName)
                put("error_message", throwable.message?.take(200) ?: "unknown")
            }

        logEvent(
            category = category,
            event = event,
            level = LogLevel.ERROR,
            screen = screen,
            component = component,
            metadata = errorMetadata,
        )
    }

    private fun processEvent(event: DiagnosticEvent) {
        // Add to queue with size limit
        if (eventQueue.size >= MAX_QUEUE_SIZE) {
            eventQueue.poll() // Remove oldest event
        }
        eventQueue.offer(event)

        // Console output
        if (enableConsoleOutput) {
            val jsonString = json.encodeToString(event)
            when (event.level) {
                "VERBOSE" -> Log.v(TAG, jsonString)
                "DEBUG" -> Log.d(TAG, jsonString)
                "INFO" -> Log.i(TAG, jsonString)
                "WARN" -> Log.w(TAG, jsonString)
                "ERROR" -> Log.e(TAG, jsonString)
            }
        }

        // TODO: Add optional integrations
        // - Firebase Performance Monitoring
        // - Sentry Performance
        // - Custom backend endpoint
    }

    /**
     * Get recent events for debugging/export.
     */
    fun getRecentEvents(limit: Int = 100): List<DiagnosticEvent> = eventQueue.toList().takeLast(limit)

    /**
     * Export events as JSON string.
     */
    fun exportEventsAsJson(limit: Int = 100): String {
        val events = getRecentEvents(limit)
        return json.encodeToString(events)
    }

    /**
     * Clear event history.
     */
    fun clearEvents() {
        eventQueue.clear()
    }

    private fun getBuildInfo(): String? {
        // Return build variant/version info if available
        // Avoid including BuildConfig directly to prevent circular dependencies
        return try {
            "debug" // Can be enhanced with actual build info
        } catch (e: Exception) {
            null
        }
    }

    // Convenience methods for common categories

    object Xtream {
        fun logLoadStart(
            type: String,
            screen: String? = null,
        ) {
            logEvent(
                category = "xtream",
                event = "load_start",
                screen = screen,
                metadata = mapOf("type" to type),
            )
        }

        fun logLoadComplete(
            type: String,
            count: Int,
            durationMs: Long,
            screen: String? = null,
        ) {
            logEvent(
                category = "xtream",
                event = "load_complete",
                screen = screen,
                metadata =
                    mapOf(
                        "type" to type,
                        "count" to count.toString(),
                        "duration_ms" to durationMs.toString(),
                    ),
            )
        }

        fun logLoadError(
            type: String,
            error: String,
            screen: String? = null,
        ) {
            logEvent(
                category = "xtream",
                event = "load_error",
                level = LogLevel.ERROR,
                screen = screen,
                metadata = mapOf("type" to type, "error" to error),
            )
        }
    }

    object Telegram {
        fun logUpdateReceived(updateType: String) {
            logEvent(
                category = "telegram",
                event = "update_received",
                metadata = mapOf("update_type" to updateType),
            )
        }

        fun logMediaResolve(
            messageId: String,
            durationMs: Long,
        ) {
            logEvent(
                category = "telegram",
                event = "media_resolve",
                metadata =
                    mapOf(
                        "message_id" to messageId,
                        "duration_ms" to durationMs.toString(),
                    ),
            )
        }
    }

    object Media3 {
        fun logPlaybackStart(
            screen: String?,
            mediaType: String,
        ) {
            logEvent(
                category = "media3",
                event = "playback_start",
                screen = screen,
                metadata = mapOf("media_type" to mediaType),
            )
        }

        fun logSeekOperation(
            screen: String?,
            fromMs: Long,
            toMs: Long,
        ) {
            logEvent(
                category = "media3",
                event = "seek_operation",
                screen = screen,
                metadata =
                    mapOf(
                        "from_ms" to fromMs.toString(),
                        "to_ms" to toMs.toString(),
                        "delta_ms" to (toMs - fromMs).toString(),
                    ),
            )
        }

        fun logBufferEvent(
            screen: String?,
            bufferMs: Long,
            isBuffering: Boolean,
        ) {
            logEvent(
                category = "media3",
                event = "buffer_event",
                screen = screen,
                metadata =
                    mapOf(
                        "buffer_ms" to bufferMs.toString(),
                        "is_buffering" to isBuffering.toString(),
                    ),
            )
        }

        fun logPlaybackError(
            screen: String?,
            error: String,
        ) {
            logEvent(
                category = "media3",
                event = "playback_error",
                level = LogLevel.ERROR,
                screen = screen,
                metadata = mapOf("error" to error),
            )
        }
    }

    object ComposeTV {
        fun logScreenLoad(
            screen: String,
            durationMs: Long,
        ) {
            logEvent(
                category = "compose_tv",
                event = "screen_load",
                screen = screen,
                metadata = mapOf("duration_ms" to durationMs.toString()),
            )
        }

        fun logFocusChange(
            screen: String,
            from: String?,
            to: String?,
        ) {
            logEvent(
                category = "compose_tv",
                event = "focus_change",
                screen = screen,
                metadata =
                    buildMap {
                        from?.let { put("from", it) }
                        to?.let { put("to", it) }
                    },
            )
        }

        fun logKeyEvent(
            screen: String,
            keyCode: String,
            action: String,
        ) {
            logEvent(
                category = "compose_tv",
                event = "key_event",
                level = LogLevel.DEBUG,
                screen = screen,
                metadata =
                    mapOf(
                        "key_code" to keyCode,
                        "action" to action,
                    ),
            )
        }
    }
}

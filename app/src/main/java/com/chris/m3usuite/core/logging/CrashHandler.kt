package com.chris.m3usuite.core.logging

import android.content.Context
import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Global crash handler that writes crash reports to persistent storage
 * AND forwards to Firebase Crashlytics.
 *
 * Features:
 * - Captures uncaught exceptions before process death
 * - Reports to Firebase Crashlytics for cloud analysis
 * - Writes crash report with stack trace and last N log entries locally
 * - Crash report survives app restart for post-crash analysis
 * - Chains to default handler for standard system crash dialog
 *
 * Installation (in App.onCreate() AFTER Firebase init):
 * ```
 * CrashHandler.install(this)
 * ```
 */
object CrashHandler {
    private const val TAG = "CrashHandler"
    private const val CRASH_FILE = "last_crash.json"
    private const val LAST_LOGS_COUNT = 50

    private val json =
        Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        }

    @Serializable
    data class CrashReport(
        val timestamp: Long,
        val threadName: String,
        val exceptionType: String,
        val message: String?,
        val stackTrace: String,
        val lastLogs: List<LogEntrySummary>,
    )

    @Serializable
    data class LogEntrySummary(
        val timestamp: Long,
        val level: String,
        val category: String,
        val message: String,
    )

    /**
     * Install the global uncaught exception handler.
     * Should be called as early as possible in App.onCreate(), AFTER Firebase init.
     */
    fun install(context: Context) {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                // Report to Firebase Crashlytics FIRST (most reliable)
                reportToCrashlytics(thread, throwable)

                // Write crash to file BEFORE app dies (local backup)
                writeCrashToFile(context, thread, throwable)

                // Log to AppLog (may not persist but good for debugging if we survive)
                try {
                    AppLog.log(
                        category = "crash",
                        level = AppLog.Level.ERROR,
                        message = "UNCAUGHT EXCEPTION: ${throwable::class.simpleName}: ${throwable.message}",
                        extras = mapOf("thread" to thread.name),
                    )
                } catch (e: Exception) {
                    // AppLog may fail, don't let it prevent crash handling
                }
            } catch (e: Exception) {
                // Crash handler must not crash - log to system and continue
                Log.e(TAG, "Failed to write crash report", e)
            } finally {
                // Chain to default handler (shows system crash dialog or restarts)
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }

        Log.i(TAG, "CrashHandler installed (with Crashlytics integration)")
    }

    /**
     * Report crash to Firebase Crashlytics with additional context.
     */
    private fun reportToCrashlytics(thread: Thread, throwable: Throwable) {
        try {
            val crashlytics = FirebaseCrashlytics.getInstance()
            
            // Add context about the crash
            crashlytics.setCustomKey("crash_thread", thread.name)
            crashlytics.setCustomKey("crash_time", System.currentTimeMillis())
            
            // Log last few app logs to Crashlytics for context
            try {
                val recentLogs = AppLog.history.value.takeLast(10)
                recentLogs.forEachIndexed { index, entry ->
                    crashlytics.log("[$index] ${entry.category}: ${entry.message}")
                }
            } catch (e: Exception) {
                crashlytics.log("Failed to attach app logs: ${e.message}")
            }
            
            // Record the exception
            crashlytics.recordException(throwable)
            
            Log.d(TAG, "Crash reported to Crashlytics")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to report to Crashlytics", e)
        }
    }

    /**
     * Write crash information to a JSON file.
     */
    private fun writeCrashToFile(
        context: Context,
        thread: Thread,
        throwable: Throwable,
    ) {
        val lastLogs =
            try {
                AppLog.history.value.takeLast(LAST_LOGS_COUNT).map {
                    LogEntrySummary(
                        timestamp = it.timestamp,
                        level = it.level.name,
                        category = it.category,
                        message = it.message,
                    )
                }
            } catch (e: Exception) {
                emptyList()
            }

        val report =
            CrashReport(
                timestamp = System.currentTimeMillis(),
                threadName = thread.name,
                exceptionType = throwable::class.qualifiedName ?: "Unknown",
                message = throwable.message,
                stackTrace = throwable.stackTraceToString(),
                lastLogs = lastLogs,
            )

        val file = File(context.filesDir, CRASH_FILE)
        file.writeText(json.encodeToString(report))

        Log.i(TAG, "Crash report written to ${file.absolutePath}")
    }

    /**
     * Read last crash report if exists.
     * Returns null if no crash occurred or report was already consumed.
     */
    fun readLastCrash(context: Context): CrashReport? {
        val file = File(context.filesDir, CRASH_FILE)
        if (!file.exists()) return null

        return try {
            json.decodeFromString<CrashReport>(file.readText())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read crash report", e)
            null
        }
    }

    /**
     * Mark crash as consumed (delete file).
     * Call this after user has reviewed/dismissed the crash report.
     */
    fun clearLastCrash(context: Context) {
        val file = File(context.filesDir, CRASH_FILE)
        if (file.exists()) {
            file.delete()
            Log.i(TAG, "Crash report cleared")
        }
    }

    /**
     * Check if there is a pending crash report.
     */
    fun hasPendingCrash(context: Context): Boolean {
        val file = File(context.filesDir, CRASH_FILE)
        return file.exists()
    }

    /**
     * Export crash report as shareable text.
     */
    fun exportCrashAsText(report: CrashReport): String =
        buildString {
            appendLine("=== FishIT-Player Crash Report ===")
            appendLine()
            appendLine(
                "Timestamp: ${java.text.SimpleDateFormat(
                    "yyyy-MM-dd HH:mm:ss.SSS",
                    java.util.Locale.getDefault(),
                ).format(java.util.Date(report.timestamp))}",
            )
            appendLine("Thread: ${report.threadName}")
            appendLine("Exception: ${report.exceptionType}")
            appendLine("Message: ${report.message ?: "N/A"}")
            appendLine()
            appendLine("=== Stack Trace ===")
            appendLine(report.stackTrace)
            appendLine()
            appendLine("=== Last ${report.lastLogs.size} Log Entries ===")
            report.lastLogs.forEach { log ->
                val time =
                    java.text
                        .SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault())
                        .format(java.util.Date(log.timestamp))
                appendLine("[$time] [${log.level}] ${log.category}: ${log.message}")
            }
        }
}

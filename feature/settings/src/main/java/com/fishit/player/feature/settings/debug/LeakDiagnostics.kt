package com.fishit.player.feature.settings.debug

import android.content.Context
import android.net.Uri

/**
 * Abstraction for memory leak diagnostics (LeakCanary integration).
 *
 * Debug builds provide a real implementation using LeakCanary APIs.
 * Release builds provide a no-op implementation to avoid shipping LeakCanary.
 *
 * Usage:
 * - Check [isAvailable] before showing LeakCanary-specific UI
 * - Use [openLeakUi] to launch the LeakCanary activity
 * - Use [exportLeakReport] to write a text report via SAF
 * - Use [getSummary] to display compact leak info in the debug screen
 */
interface LeakDiagnostics {

    /**
     * Whether LeakCanary is available in this build.
     * Returns false for release builds.
     */
    val isAvailable: Boolean

    /**
     * Open the LeakCanary UI activity.
     *
     * @param context Android context for starting the activity
     * @return true if the activity was started, false otherwise
     */
    fun openLeakUi(context: Context): Boolean

    /**
     * Export a text-based leak report to the given URI.
     *
     * The report includes:
     * - App version and build info
     * - Device model and SDK version
     * - Leak count and signatures (if accessible)
     *
     * @param context Android context for content resolver access
     * @param uri SAF destination URI from CreateDocument
     * @return Result indicating success or failure with error message
     */
    suspend fun exportLeakReport(context: Context, uri: Uri): Result<Unit>

    /**
     * Get a compact summary of the current leak status.
     */
    fun getSummary(): LeakSummary

    /**
     * Get the path to the latest heap dump file, if available.
     * Returns null if no heap dump exists or if the API doesn't expose the path.
     */
    fun getLatestHeapDumpPath(): String?
}

/**
 * Summary of leak detection status for UI display.
 */
data class LeakSummary(
    /**
     * Number of detected leaks (retained objects / leak traces).
     * May be 0 if no leaks detected or if unavailable.
     */
    val leakCount: Int,

    /**
     * Uptime timestamp (ms) of the most recent leak, if available.
     */
    val lastLeakUptimeMs: Long?,

    /**
     * Additional note (e.g., "LeakCanary not available in this build").
     */
    val note: String? = null,
)

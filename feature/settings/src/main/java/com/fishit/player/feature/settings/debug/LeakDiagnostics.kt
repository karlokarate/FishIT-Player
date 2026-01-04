package com.fishit.player.feature.settings.debug

import android.content.Context
import android.net.Uri

/**
 * Abstraction for memory leak diagnostics (LeakCanary integration).
 *
 * Debug builds provide a real implementation using LeakCanary APIs.
 * Release builds provide a no-op implementation to avoid shipping LeakCanary.
 *
 * **Gold Standard Features:**
 * - Noise Control: Distinguish transient GC delays from real leaks
 * - Status Tracking: Monitor retained object stability over time
 * - Threshold-based Warnings: Configurable alert thresholds
 * - Export: SAF-based report export (text, not heap dumps)
 *
 * Usage:
 * - Check [isAvailable] before showing LeakCanary-specific UI
 * - Use [openLeakUi] to launch the LeakCanary activity
 * - Use [exportLeakReport] to write a text report via SAF
 * - Use [getSummary] to display compact leak info in the debug screen
 * - Use [getDetailedStatus] for full diagnostics info
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
     * - LeakCanary configuration
     * - Memory statistics
     *
     * @param context Android context for content resolver access
     * @param uri SAF destination URI from CreateDocument
     * @return Result indicating success or failure with error message
     */
    suspend fun exportLeakReport(
        context: Context,
        uri: Uri,
    ): Result<Unit>

    /**
     * Get a compact summary of the current leak status.
     */
    fun getSummary(): LeakSummary

    /**
     * Get detailed diagnostics status including noise control information.
     */
    fun getDetailedStatus(): LeakDetailedStatus

    /**
     * Get the path to the latest heap dump file, if available.
     * Returns null if no heap dump exists or if the API doesn't expose the path.
     */
    fun getLatestHeapDumpPath(): String?

    /**
     * Request a manual GC and wait briefly for objects to be collected.
     * Use this to reduce noise before checking retained count.
     * Does NOT block; any GC triggered is async.
     */
    fun requestGarbageCollection()

    /**
     * Force a heap dump and analysis now.
     * Use sparingly - this freezes the app for several seconds.
     */
    fun triggerHeapDump()
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

/**
 * Detailed leak diagnostics status with noise control.
 *
 * Helps distinguish between:
 * - **Transient retention**: Objects temporarily retained during GC delays
 * - **Persistent leaks**: Objects that remain retained over time
 */
data class LeakDetailedStatus(
    /**
     * Current number of retained objects (not yet analyzed).
     */
    val retainedObjectCount: Int,
    /**
     * Whether any objects are currently retained.
     */
    val hasRetainedObjects: Boolean,
    /**
     * Retention severity level for UI display.
     */
    val severity: RetentionSeverity,
    /**
     * Human-readable status message.
     */
    val statusMessage: String,
    /**
     * LeakCanary configuration info for diagnostics.
     */
    val config: LeakCanaryConfig,
    /**
     * Runtime memory statistics.
     */
    val memoryStats: MemoryStats,
    /**
     * Timestamp when this status was captured (System.currentTimeMillis).
     */
    val capturedAtMs: Long,
)

/**
 * Retention severity levels for UI display.
 */
enum class RetentionSeverity {
    /** No objects retained - all clear */
    NONE,

    /** Few objects retained briefly - likely transient GC delay */
    LOW,

    /** Multiple objects retained - may be a leak, investigate */
    MEDIUM,

    /** Many objects retained or persisting - likely a real leak */
    HIGH,
}

/**
 * LeakCanary configuration snapshot for diagnostics.
 */
data class LeakCanaryConfig(
    val retainedVisibleThreshold: Int,
    val computeRetainedHeapSize: Boolean,
    val maxStoredHeapDumps: Int,
    val watchDurationMillis: Long,
    val watchActivities: Boolean,
    val watchFragments: Boolean,
    val watchViewModels: Boolean,
)

/**
 * Runtime memory statistics.
 */
data class MemoryStats(
    val usedMemoryMb: Long,
    val totalMemoryMb: Long,
    val maxMemoryMb: Long,
    val freeMemoryMb: Long,
) {
    val usagePercentage: Int
        get() = if (maxMemoryMb > 0) ((usedMemoryMb * 100) / maxMemoryMb).toInt() else 0
}

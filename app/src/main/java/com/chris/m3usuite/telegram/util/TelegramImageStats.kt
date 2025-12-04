package com.chris.m3usuite.telegram.util

import com.chris.m3usuite.telegram.domain.TelegramItem
import com.chris.m3usuite.core.logging.UnifiedLog
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Phase T2: Debug utility for logging Telegram image reference statistics.
 *
 * Logs once per app run the count of items with:
 * - Non-null posterRef
 * - Non-null backdropRef
 *
 * This helps diagnose image availability issues in the Telegram pipeline.
 *
 * Usage in TelegramLibraryViewModel:
 * ```kotlin
 * allItems.collect { items ->
 *     TelegramImageStats.logStatsOnce(items, source = "TelegramLibraryViewModel")
 * }
 * ```
 */
object TelegramImageStats {
    private const val TAG = "TelegramImageStats"

    // Flag to ensure we only log once per app run
    private val hasLoggedOnce = AtomicBoolean(false)

    /**
     * Log image statistics once per app run.
     *
     * Thread-safe: Uses AtomicBoolean to ensure single logging.
     *
     * @param items List of TelegramItems to analyze
     * @param source Calling source for logging context
     */
    fun logStatsOnce(
        items: List<TelegramItem>,
        source: String = TAG,
    ) {
        if (!hasLoggedOnce.compareAndSet(false, true)) {
            // Already logged this session
            return
        }

        logStats(items, source)
    }

    /**
     * Force log image statistics (bypasses once-per-run guard).
     *
     * @param items List of TelegramItems to analyze
     * @param source Calling source for logging context
     */
    fun logStats(
        items: List<TelegramItem>,
        source: String = TAG,
    ) {
        if (items.isEmpty()) {
            UnifiedLog.info(
                source = source,
                message = "Telegram image stats: No items to analyze",
            )
            return
        }

        val totalItems = items.size
        val withPosterRef = items.count { it.posterRef != null }
        val withBackdropRef = items.count { it.backdropRef != null }
        val withBothRefs = items.count { it.posterRef != null && it.backdropRef != null }
        val withNoRefs = items.count { it.posterRef == null && it.backdropRef == null }

        val posterPercentage = (withPosterRef * 100) / totalItems
        val backdropPercentage = (withBackdropRef * 100) / totalItems

        UnifiedLog.info(
            source = source,
            message = "Telegram image stats (Phase T2)",
            details =
                mapOf(
                    "totalItems" to totalItems.toString(),
                    "withPosterRef" to "$withPosterRef ($posterPercentage%)",
                    "withBackdropRef" to "$withBackdropRef ($backdropPercentage%)",
                    "withBothRefs" to withBothRefs.toString(),
                    "withNoRefs" to withNoRefs.toString(),
                ),
        )
    }

    /**
     * Reset the logging flag (for testing purposes).
     */
    fun resetLoggingFlag() {
        hasLoggedOnce.set(false)
    }
}

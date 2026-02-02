package com.fishit.player.infra.logging

import android.util.Log
import timber.log.Timber

/**
 * Initializer for the unified logging system.
 *
 * This should be called exactly once from the Application.onCreate() method.
 * Sets up Timber as the internal logging backend with appropriate trees for
 * debug and release builds.
 *
 * **Performance Note:**
 * In release builds, logging is completely disabled by default to maximize
 * performance. No log operations, string allocations, or backend calls occur.
 * Only critical errors (crashes) are reported.
 *
 * **Future Integration Points:**
 * - Crashlytics/Sentry integration will be added to ProductionReportingTree
 * - Potential migration to Kermit for Kotlin Multiplatform support
 *
 * @see UnifiedLog for the public logging API
 */
object UnifiedLogInitializer {
    /**
     * Initialize the logging system.
     *
     * Must be called from Application.onCreate() before any logging occurs.
     *
     * **Release Build Behavior:**
     * - Logging is globally disabled via [UnifiedLog.setEnabled]
     * - No Timber trees are planted (no overhead)
     * - Only crashes are reported via [UnifiedLog.setMinLevel] = ERROR
     *
     * **Debug Build Behavior:**
     * - Full logging enabled to logcat via Timber.DebugTree
     * - LogBufferTree enabled for DebugScreen in-memory log viewing
     *
     * @param isDebug true for debug builds (enables verbose console logging),
     *                false for release builds (completely disables logging)
     * @param enableLogBuffer true to enable in-memory log buffering for DebugScreen
     *                        (default: same as isDebug)
     */
    fun init(
        isDebug: Boolean,
        enableLogBuffer: Boolean = isDebug,
    ) {
        // Clear any existing trees (useful for tests)
        Timber.uprootAll()

        if (isDebug) {
            // Debug builds: enable full logging
            UnifiedLog.setEnabled(true)
            UnifiedLog.setMinLevel(UnifiedLog.Level.VERBOSE)

            // Use Timber's DebugTree for full logcat output
            Timber.plant(Timber.DebugTree())

            // Plant LogBufferTree for in-memory log access (used by DebugScreen)
            if (enableLogBuffer) {
                Timber.plant(LogBufferTree.getInstance())
            }
        } else {
            // Release builds: COMPLETELY DISABLE logging for maximum performance
            // No string allocations, no lambda evaluations, no backend calls
            UnifiedLog.setEnabled(false)

            // Set minLevel to ERROR as a safety fallback (only crashes if re-enabled)
            UnifiedLog.setMinLevel(UnifiedLog.Level.ERROR)

            // DO NOT plant any Timber trees in release - no logging backend needed
            // This eliminates all logging overhead at runtime

            // Note: If crash reporting is needed later, a minimal ProductionReportingTree
            // can be planted AND UnifiedLog.setEnabled(true) must be called.
            // But for now, we prioritize performance over logging.
        }
    }

    /**
     * Production logging tree that reports errors to crash reporting services.
     *
     * Currently forwards WARN and ERROR logs to Android's Log system.
     * Future versions will integrate with Crashlytics/Sentry/Bugsnag.
     *
     * NOTE: This tree is NOT planted by default in release builds.
     * It can be used for Crashlytics/Sentry integration when needed.
     *
     * TODO(logging): Integrate with Firebase Crashlytics for error reporting
     * TODO(logging): Add breadcrumb logging for debugging production crashes
     * TODO(logging): Consider adding custom log level mapping for reporting services
     */
    private class ProductionReportingTree : Timber.Tree() {
        override fun log(
            priority: Int,
            tag: String?,
            message: String,
            t: Throwable?,
        ) {
            // Only log WARN and ERROR in production to reduce overhead
            if (priority < Log.WARN) {
                return
            }

            // Forward to Android Log
            if (t != null) {
                Log.println(priority, tag ?: "FishIT", "$message\n${Log.getStackTraceString(t)}")
            } else {
                Log.println(priority, tag ?: "FishIT", message)
            }

            // TODO(crashlytics): Integrate with Crashlytics
            // Example:
            // if (priority == Log.ERROR) {
            //     FirebaseCrashlytics.getInstance().apply {
            //         log(message)
            //         t?.let { recordException(it) }
            //     }
            // }

            // TODO(sentry): Add Sentry integration as an alternative
            // Example:
            // if (priority >= Log.WARN) {
            //     Sentry.captureMessage(message, when(priority) {
            //         Log.WARN -> SentryLevel.WARNING
            //         Log.ERROR -> SentryLevel.ERROR
            //         else -> SentryLevel.INFO
            //     })
            // }
        }
    }
}

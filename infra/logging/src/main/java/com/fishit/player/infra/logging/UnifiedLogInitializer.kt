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
     * @param isDebug true for debug builds (enables verbose console logging),
     *                false for release builds (enables production reporting)
     * @param enableLogBuffer true to enable in-memory log buffering for DebugScreen
     *                        (default: same as isDebug)
     */
    fun init(isDebug: Boolean, enableLogBuffer: Boolean = isDebug) {
        // Clear any existing trees (useful for tests)
        Timber.uprootAll()

        if (isDebug) {
            // Debug builds: use Timber's DebugTree for full logcat output
            Timber.plant(Timber.DebugTree())
        } else {
            // Release builds: use production tree for error reporting
            Timber.plant(ProductionReportingTree())
        }

        // Plant LogBufferTree for in-memory log access (used by DebugScreen)
        if (enableLogBuffer) {
            Timber.plant(LogBufferTree.getInstance())
        }
    }

    /**
     * Production logging tree that reports errors to crash reporting services.
     *
     * Currently forwards WARN and ERROR logs to Android's Log system.
     * Future versions will integrate with Crashlytics/Sentry/Bugsnag.
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

package com.fishit.player.infra.logging

import timber.log.Timber

/**
 * Unified logging facade for the entire application.
 *
 * This is a **stable facade** that hides the internal logging backend (currently Timber)
 * and provides a consistent API for all modules. The backend can be swapped without
 * affecting call sites.
 *
 * **Design Principles:**
 * - All logging must go through this facade; direct android.util.Log usage is forbidden
 * - All logging must go through this facade; direct Timber usage is forbidden outside this module
 * - The facade API is stable and independent of the backend implementation
 * - Call sites should never depend on backend-specific types (Timber, android.util.Log, etc.)
 *
 * **Current Backend:** Timber 5.0.1
 * **Future Options:** Kermit (for Kotlin Multiplatform), custom implementations
 *
 * **Initialization:**
 * Must be initialized via [UnifiedLogInitializer.init] in Application.onCreate()
 *
 * **v1 Component Mapping:**
 * - Adapted from v1 `com.chris.m3usuite.core.logging.UnifiedLog`
 * - Simplified for v2 (no ring buffer or DataStore integration yet)
 * - Uses Timber as backend instead of direct android.util.Log
 *
 * @see UnifiedLogInitializer for initialization
 */
object UnifiedLog {
    /**
     * Log level enum for filtering.
     *
     * Levels are ordered from most verbose to least verbose.
     */
    enum class Level {
        VERBOSE,
        DEBUG,
        INFO,
        WARN,
        ERROR,
    }

    /**
     * Current minimum log level.
     *
     * Only logs at or above this level will be emitted.
     * Default is DEBUG to balance verbosity and performance.
     */
    @Volatile
    private var minLevel: Level = Level.DEBUG

    /**
     * Set the minimum log level.
     *
     * Logs below this level will be silently dropped.
     * Useful for controlling verbosity in different build types or runtime scenarios.
     *
     * @param level the minimum level to log
     */
    fun setMinLevel(level: Level) {
        minLevel = level
    }

    /**
     * Log a verbose message.
     *
     * Use for detailed diagnostic information that is rarely needed.
     *
     * @param tag source identifier (e.g., class name or component name)
     * @param message log message
     * @param throwable optional exception to log
     */
    fun v(
        tag: String,
        message: String,
        throwable: Throwable? = null,
    ) {
        log(Level.VERBOSE, tag, message, throwable)
    }

    /**
     * Log a debug message.
     *
     * Use for diagnostic information useful during development.
     *
     * @param tag source identifier (e.g., class name or component name)
     * @param message log message
     * @param throwable optional exception to log
     */
    fun d(
        tag: String,
        message: String,
        throwable: Throwable? = null,
    ) {
        log(Level.DEBUG, tag, message, throwable)
    }

    /**
     * Log an info message.
     *
     * Use for general informational messages about app flow.
     *
     * @param tag source identifier (e.g., class name or component name)
     * @param message log message
     * @param throwable optional exception to log
     */
    fun i(
        tag: String,
        message: String,
        throwable: Throwable? = null,
    ) {
        log(Level.INFO, tag, message, throwable)
    }

    /**
     * Log a warning message.
     *
     * Use for recoverable errors or unexpected situations.
     *
     * @param tag source identifier (e.g., class name or component name)
     * @param message log message
     * @param throwable optional exception to log
     */
    fun w(
        tag: String,
        message: String,
        throwable: Throwable? = null,
    ) {
        log(Level.WARN, tag, message, throwable)
    }

    /**
     * Log an error message.
     *
     * Use for serious errors that require attention.
     *
     * @param tag source identifier (e.g., class name or component name)
     * @param message log message
     * @param throwable optional exception to log
     */
    fun e(
        tag: String,
        message: String,
        throwable: Throwable? = null,
    ) {
        log(Level.ERROR, tag, message, throwable)
    }

    // Legacy method names for backward compatibility with v1 code

    /**
     * @deprecated Use [d] instead for consistency with standard logging conventions
     */
    @Deprecated("Use d() instead", ReplaceWith("d(tag, message)"))
    fun debug(
        tag: String,
        message: String,
    ) = d(tag, message)

    /**
     * @deprecated Use [i] instead for consistency with standard logging conventions
     */
    @Deprecated("Use i() instead", ReplaceWith("i(tag, message)"))
    fun info(
        tag: String,
        message: String,
    ) = i(tag, message)

    /**
     * @deprecated Use [w] instead for consistency with standard logging conventions
     */
    @Deprecated("Use w() instead", ReplaceWith("w(tag, message, throwable)"))
    fun warn(
        tag: String,
        message: String,
        throwable: Throwable? = null,
    ) = w(tag, message, throwable)

    /**
     * @deprecated Use [e] instead for consistency with standard logging conventions
     */
    @Deprecated("Use e() instead", ReplaceWith("e(tag, message, throwable)"))
    fun error(
        tag: String,
        message: String,
        throwable: Throwable? = null,
    ) = e(tag, message, throwable)

    /**
     * @deprecated Use [v] instead for consistency with standard logging conventions
     */
    @Deprecated("Use v() instead", ReplaceWith("v(tag, message)"))
    fun verbose(
        tag: String,
        message: String,
    ) = v(tag, message)

    /**
     * Internal logging implementation.
     *
     * Checks minLevel and forwards to Timber backend.
     * This is the only place where Timber is accessed directly.
     */
    private fun log(
        level: Level,
        tag: String,
        message: String,
        throwable: Throwable?,
    ) {
        // Filter by minimum level
        if (level.ordinal < minLevel.ordinal) return

        // Forward to Timber backend with appropriate priority
        when (level) {
            Level.VERBOSE -> {
                if (throwable != null) {
                    Timber.tag(tag).v(throwable, message)
                } else {
                    Timber.tag(tag).v(message)
                }
            }
            Level.DEBUG -> {
                if (throwable != null) {
                    Timber.tag(tag).d(throwable, message)
                } else {
                    Timber.tag(tag).d(message)
                }
            }
            Level.INFO -> {
                if (throwable != null) {
                    Timber.tag(tag).i(throwable, message)
                } else {
                    Timber.tag(tag).i(message)
                }
            }
            Level.WARN -> {
                if (throwable != null) {
                    Timber.tag(tag).w(throwable, message)
                } else {
                    Timber.tag(tag).w(message)
                }
            }
            Level.ERROR -> {
                if (throwable != null) {
                    Timber.tag(tag).e(throwable, message)
                } else {
                    Timber.tag(tag).e(message)
                }
            }
        }
    }
}

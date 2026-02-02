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
 * **Primary API (Lazy, Lambda-based):**
 * The recommended API uses inline lambdas to defer message construction until after the log
 * level check. This is especially important in hot paths (player, transport, pipelines).
 *
 * ```kotlin
 * UnifiedLog.d(TAG) { "loading item $id took ${measureMs()} ms" }
 * UnifiedLog.e(TAG, throwable) { "error message" }
 * ```
 *
 * **Convenience API (String-based):**
 * For constant messages or non-critical paths, string-based overloads are available:
 *
 * ```kotlin
 * UnifiedLog.d(TAG, "constant message")
 * ```
 *
 * **Current Backend:** Timber 5.0.1
 * **Future Options:** Kermit (for Kotlin Multiplatform), custom implementations
 *
 * **Initialization:**
 * Must be initialized via [UnifiedLogInitializer.init] in Application.onCreate()
 *
 * **v1 Component Mapping:**
 * - Adapted from the v1 UnifiedLog implementation
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
     * Global enable flag for logging.
     *
     * When false, ALL logging operations are short-circuited immediately
     * for maximum performance in release builds. No string allocation,
     * no lambda evaluation, no backend calls.
     *
     * Default is true (logging enabled).
     * In release builds, this should be set to false via [setEnabled].
     */
    @Volatile
    @PublishedApi
    internal var enabled: Boolean = true
        private set

    /**
     * Current minimum log level.
     *
     * Only logs at or above this level will be emitted.
     * Default is DEBUG to balance verbosity and performance.
     *
     * Note: If [enabled] is false, no logs are processed regardless of level.
     */
    @Volatile
    private var minLevel: Level = Level.DEBUG

    /**
     * Enables or disables all logging operations globally.
     *
     * When disabled, ALL logging calls return immediately without
     * any string allocation, lambda evaluation, or backend calls.
     * This provides maximum performance for release builds.
     *
     * @param isEnabled true to enable logging, false to completely disable
     */
    fun setEnabled(isEnabled: Boolean) {
        enabled = isEnabled
    }

    /**
     * Returns true if logging is globally enabled.
     */
    fun isEnabled(): Boolean = enabled

    /**
     * Set the minimum log level.
     *
     * Logs below this level will be silently dropped.
     * Useful for controlling verbosity in different build types or runtime scenarios.
     *
     * Note: If [enabled] is false, no logs are processed regardless of level.
     *
     * @param level the minimum level to log
     */
    fun setMinLevel(level: Level) {
        minLevel = level
    }

    /**
     * Check if a specific log level is currently enabled.
     *
     * Returns false if logging is globally disabled OR if the level
     * is below the minimum configured level.
     *
     * Useful for expensive log preparations that should be skipped entirely.
     *
     * @param level the level to check
     * @return true if logs at this level would be emitted
     */
    @PublishedApi
    internal fun isEnabled(level: Level): Boolean = enabled && level.ordinal >= minLevel.ordinal

    // ========== PRIMARY API: Lambda-based (Lazy) ==========

    /**
     * Log a verbose message (lazy).
     *
     * Use for detailed diagnostic information that is rarely needed.
     * The message lambda is only evaluated if VERBOSE logging is enabled.
     *
     * @param tag source identifier (e.g., class name or component name)
     * @param throwable optional exception to log
     * @param message lazy message provider
     */
    inline fun v(
        tag: String,
        throwable: Throwable? = null,
        message: () -> String,
    ) {
        if (!isEnabled(Level.VERBOSE)) return
        log(Level.VERBOSE, tag, message(), throwable)
    }

    /**
     * Log a debug message (lazy).
     *
     * Use for diagnostic information useful during development.
     * The message lambda is only evaluated if DEBUG logging is enabled.
     *
     * @param tag source identifier (e.g., class name or component name)
     * @param throwable optional exception to log
     * @param message lazy message provider
     */
    inline fun d(
        tag: String,
        throwable: Throwable? = null,
        message: () -> String,
    ) {
        if (!isEnabled(Level.DEBUG)) return
        log(Level.DEBUG, tag, message(), throwable)
    }

    /**
     * Log an info message (lazy).
     *
     * Use for general informational messages about app flow.
     * The message lambda is only evaluated if INFO logging is enabled.
     *
     * @param tag source identifier (e.g., class name or component name)
     * @param throwable optional exception to log
     * @param message lazy message provider
     */
    inline fun i(
        tag: String,
        throwable: Throwable? = null,
        message: () -> String,
    ) {
        if (!isEnabled(Level.INFO)) return
        log(Level.INFO, tag, message(), throwable)
    }

    /**
     * Log a warning message (lazy).
     *
     * Use for recoverable errors or unexpected situations.
     * The message lambda is only evaluated if WARN logging is enabled.
     *
     * @param tag source identifier (e.g., class name or component name)
     * @param throwable optional exception to log
     * @param message lazy message provider
     */
    inline fun w(
        tag: String,
        throwable: Throwable? = null,
        message: () -> String,
    ) {
        if (!isEnabled(Level.WARN)) return
        log(Level.WARN, tag, message(), throwable)
    }

    /**
     * Log an error message (lazy).
     *
     * Use for serious errors that require attention.
     * The message lambda is only evaluated if ERROR logging is enabled.
     *
     * @param tag source identifier (e.g., class name or component name)
     * @param throwable optional exception to log
     * @param message lazy message provider
     */
    inline fun e(
        tag: String,
        throwable: Throwable? = null,
        message: () -> String,
    ) {
        if (!isEnabled(Level.ERROR)) return
        log(Level.ERROR, tag, message(), throwable)
    }

    // ========== CONVENIENCE API: String-based ==========

    /**
     * Log a verbose message (convenience).
     *
     * For constant messages or non-critical paths. For string interpolation
     * or expensive computations, prefer the lambda-based overload.
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
     * Log a debug message (convenience).
     *
     * For constant messages or non-critical paths. For string interpolation
     * or expensive computations, prefer the lambda-based overload.
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
     * Log an info message (convenience).
     *
     * For constant messages or non-critical paths. For string interpolation
     * or expensive computations, prefer the lambda-based overload.
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
     * Log a warning message (convenience).
     *
     * For constant messages or non-critical paths. For string interpolation
     * or expensive computations, prefer the lambda-based overload.
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
     * Log an error message (convenience).
     *
     * For constant messages or non-critical paths. For string interpolation
     * or expensive computations, prefer the lambda-based overload.
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

    // ========== DEPRECATED LEGACY API ==========

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
     * Checks global enabled flag and minLevel, then forwards to Timber backend.
     * This is the only place where Timber is accessed directly.
     *
     * Note: @PublishedApi is required for inline functions to call this method.
     */
    @PublishedApi
    internal fun log(
        level: Level,
        tag: String,
        message: String,
        throwable: Throwable?,
    ) {
        // Fast path: skip all processing if logging is globally disabled
        if (!enabled) return

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

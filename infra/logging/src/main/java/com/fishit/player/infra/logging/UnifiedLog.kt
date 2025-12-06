package com.fishit.player.infra.logging

import android.util.Log

/**
 * Unified logging abstraction for v2 architecture.
 * 
 * Simple wrapper around Android Log for Phase 2.
 * Future phases will integrate with Firebase Crashlytics and other logging backends.
 * 
 * **v1 Component Mapping:**
 * - Adapted from v1 `com.chris.m3usuite.core.logging.UnifiedLog`
 * - Simplified for v2 (no Firebase integration yet)
 */
object UnifiedLog {
    
    /**
     * Log level enum for filtering
     */
    enum class Level {
        VERBOSE, DEBUG, INFO, WARN, ERROR
    }
    
    /**
     * Current minimum log level
     */
    var minLevel: Level = Level.DEBUG
    
    /**
     * Log a debug message
     */
    fun debug(tag: String, message: String) {
        if (minLevel.ordinal <= Level.DEBUG.ordinal) {
            Log.d(tag, message)
        }
    }
    
    /**
     * Log an info message
     */
    fun info(tag: String, message: String) {
        if (minLevel.ordinal <= Level.INFO.ordinal) {
            Log.i(tag, message)
        }
    }
    
    /**
     * Log a warning message
     */
    fun warn(tag: String, message: String, throwable: Throwable? = null) {
        if (minLevel.ordinal <= Level.WARN.ordinal) {
            if (throwable != null) {
                Log.w(tag, message, throwable)
            } else {
                Log.w(tag, message)
            }
        }
    }
    
    /**
     * Log an error message
     */
    fun error(tag: String, message: String, throwable: Throwable? = null) {
        if (minLevel.ordinal <= Level.ERROR.ordinal) {
            if (throwable != null) {
                Log.e(tag, message, throwable)
            } else {
                Log.e(tag, message)
            }
        }
    }
    
    /**
     * Log a verbose message
     */
    fun verbose(tag: String, message: String) {
        if (minLevel.ordinal <= Level.VERBOSE.ordinal) {
            Log.v(tag, message)
        }
    }
}

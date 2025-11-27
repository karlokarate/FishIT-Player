package com.chris.m3usuite.core.logging

import android.util.Log

/**
 * Centralized logging utility for the application.
 *
 * Provides structured logging with categories and levels.
 */
object AppLog {
    enum class Level {
        VERBOSE,
        DEBUG,
        INFO,
        WARN,
        ERROR,
    }

    /**
     * Log a message with the specified category and level.
     *
     * @param category The log category (e.g., "player", "telegram", "xtream")
     * @param level The log level
     * @param message The log message
     * @param extras Optional metadata to include in the log
     */
    fun log(
        category: String,
        level: Level = Level.DEBUG,
        message: String,
        extras: Map<String, String> = emptyMap(),
    ) {
        val tag = "FishIT-$category"
        val fullMessage =
            if (extras.isEmpty()) {
                message
            } else {
                "$message | ${extras.entries.joinToString(", ") { "${it.key}=${it.value}" }}"
            }
        when (level) {
            Level.VERBOSE -> Log.v(tag, fullMessage)
            Level.DEBUG -> Log.d(tag, fullMessage)
            Level.INFO -> Log.i(tag, fullMessage)
            Level.WARN -> Log.w(tag, fullMessage)
            Level.ERROR -> Log.e(tag, fullMessage)
        }
    }

    /**
     * Log a message with the specified category (shorthand for DEBUG level).
     *
     * @param category The log category
     * @param message The log message
     */
    fun log(
        category: String,
        message: String,
    ) {
        log(category, Level.DEBUG, message)
    }
}

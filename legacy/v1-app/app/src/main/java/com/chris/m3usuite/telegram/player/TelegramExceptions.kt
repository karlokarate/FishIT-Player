package com.chris.m3usuite.telegram.player

import java.io.IOException

/**
 * Exception thrown when Telegram engine is not yet ready for operations.
 *
 * This is a specific exception type that can be caught and handled differently
 * from generic IOExceptions. It indicates that the Telegram service client
 * is either not started or not authenticated yet.
 *
 * Player error handlers can map this to a user-friendly message like:
 * "Telegram is not ready yet. Please wait a moment or re-open the Telegram settings."
 */
class TelegramUnavailableException(
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)

/**
 * Exception thrown when ensureFileReady() times out waiting for download.
 *
 * Phase 3: Runtime-driven timeout exception for streaming operations.
 * Includes detailed context about the file, mode, and download state at timeout.
 */
class TelegramFileReadTimeoutException(
    message: String,
    val fileId: Int,
    val remoteId: String? = null,
    val mode: String,
    val requiredPrefix: Long,
    val downloadedPrefix: Long,
    cause: Throwable? = null,
) : IOException(message, cause)

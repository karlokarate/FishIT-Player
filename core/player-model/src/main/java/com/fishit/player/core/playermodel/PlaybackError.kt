package com.fishit.player.core.playermodel

import com.fishit.player.core.model.SourceType

/**
 * Represents a playback error with categorization.
 *
 * @property type The category of error
 * @property message Human-readable error message
 * @property code Optional error code from underlying player
 * @property sourceType The source that produced the error (if known)
 * @property isRetryable Whether the error might be resolved by retrying
 */
data class PlaybackError(
    val type: ErrorType,
    val message: String,
    val code: Int? = null,
    val sourceType: SourceType? = null,
    val isRetryable: Boolean = false,
) {
    /**
     * Categories of playback errors.
     */
    enum class ErrorType {
        /** Network connectivity issues */
        NETWORK,

        /** Decoder/codec issues */
        DECODER,

        /** Source not found or unavailable */
        SOURCE_NOT_FOUND,

        /** Permission denied (DRM, geo-blocking, etc.) */
        PERMISSION,

        /** Timeout waiting for data */
        TIMEOUT,

        /** Source-specific error (Telegram auth, Xtream auth, etc.) */
        SOURCE_SPECIFIC,

        /** Unknown or uncategorized error */
        UNKNOWN,
    }

    companion object {
        fun network(message: String, isRetryable: Boolean = true) = PlaybackError(
            type = ErrorType.NETWORK,
            message = message,
            isRetryable = isRetryable,
        )

        fun decoder(message: String, code: Int? = null) = PlaybackError(
            type = ErrorType.DECODER,
            message = message,
            code = code,
            isRetryable = false,
        )

        fun sourceNotFound(message: String, sourceType: SourceType? = null) = PlaybackError(
            type = ErrorType.SOURCE_NOT_FOUND,
            message = message,
            sourceType = sourceType,
            isRetryable = false,
        )

        fun timeout(message: String) = PlaybackError(
            type = ErrorType.TIMEOUT,
            message = message,
            isRetryable = true,
        )

        fun unknown(message: String, code: Int? = null) = PlaybackError(
            type = ErrorType.UNKNOWN,
            message = message,
            code = code,
            isRetryable = false,
        )
    }
}

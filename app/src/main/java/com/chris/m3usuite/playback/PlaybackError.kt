package com.chris.m3usuite.playback

import androidx.media3.common.PlaybackException

/**
 * Structured error model for playback errors.
 *
 * ════════════════════════════════════════════════════════════════════════════════
 * PHASE 8 – Task 6: Playback Error Handling
 * ════════════════════════════════════════════════════════════════════════════════
 *
 * This sealed class provides typed error information for:
 * - Network connectivity issues
 * - HTTP errors (4xx, 5xx responses)
 * - Media source/format errors
 * - Decoder/rendering errors
 * - Unknown/unexpected errors
 *
 * **Key Principles:**
 * - Structured error types for UI differentiation
 * - Rich metadata for logging (AppLog integration)
 * - Kids-friendly generic messages
 * - No Telegram/Parser-specific logic
 *
 * **Contract Reference:**
 * - INTERNAL_PLAYER_PHASE8_CHECKLIST.md Group 8
 * - LOG_VIEWER.md (AppLog is the single source of truth)
 */
sealed class PlaybackError {
    /**
     * Network connectivity error (no internet, timeout, DNS failure).
     * @param code Optional error code from the underlying exception
     * @param message Optional error message
     */
    data class Network(
        val code: Int? = null,
        val message: String? = null,
    ) : PlaybackError()

    /**
     * HTTP response error (4xx, 5xx status codes).
     * @param code HTTP status code
     * @param url The URL that failed (may be null for privacy)
     */
    data class Http(
        val code: Int,
        val url: String? = null,
    ) : PlaybackError()

    /**
     * Media source error (unsupported format, corrupted file, invalid manifest).
     * @param message Error description
     */
    data class Source(
        val message: String? = null,
    ) : PlaybackError()

    /**
     * Decoder/rendering error (codec not available, hardware decoder failure).
     * @param message Error description
     */
    data class Decoder(
        val message: String? = null,
    ) : PlaybackError()

    /**
     * Unknown or unexpected error.
     * @param throwable The original exception, if available
     */
    data class Unknown(
        val throwable: Throwable? = null,
    ) : PlaybackError()

    // ══════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ══════════════════════════════════════════════════════════════════

    /**
     * Get a short summary suitable for logs and error messages.
     */
    fun toShortSummary(): String =
        when (this) {
            is Network -> "Network error${code?.let { " ($it)" } ?: ""}"
            is Http -> "HTTP $code"
            is Source -> "Source error: ${message ?: "unknown"}"
            is Decoder -> "Decoder error: ${message ?: "unknown"}"
            is Unknown -> "Unknown error: ${throwable?.message ?: "unknown"}"
        }

    /**
     * Get a user-friendly message for non-technical users.
     * Suitable for Kids Mode where generic messages are preferred.
     */
    fun toUserFriendlyMessage(): String =
        when (this) {
            is Network -> "Cannot connect to server. Please check your internet connection."
            is Http ->
                when (code) {
                    401, 403 -> "Access denied. Please check your credentials."
                    404 -> "Content not found. It may have been removed."
                    in 500..599 -> "Server error. Please try again later."
                    else -> "Connection error. Please try again."
                }
            is Source -> "This video format is not supported."
            is Decoder -> "Cannot play this video on your device."
            is Unknown -> "Cannot play this video right now."
        }

    /**
     * Get a generic message suitable for Kids Mode.
     * Never includes technical details.
     */
    fun toKidsFriendlyMessage(): String = "Cannot play this video right now."

    /**
     * Get the error type name for logging.
     */
    val typeName: String
        get() =
            when (this) {
                is Network -> "Network"
                is Http -> "Http"
                is Source -> "Source"
                is Decoder -> "Decoder"
                is Unknown -> "Unknown"
            }

    /**
     * Get HTTP or network error code as string, or null.
     */
    val httpOrNetworkCodeAsString: String?
        get() =
            when (this) {
                is Network -> code?.toString()
                is Http -> code.toString()
                else -> null
            }

    /**
     * Get URL if available (for Http errors).
     */
    val urlOrNull: String?
        get() = (this as? Http)?.url

    companion object {
        /**
         * Convert a PlaybackException to a structured PlaybackError.
         *
         * This method maps ExoPlayer error types to our structured model.
         */
        fun fromPlaybackException(exception: PlaybackException): PlaybackError {
            // Extract HTTP status code if available
            val httpCode = extractHttpStatusCode(exception)
            if (httpCode != null) {
                return Http(
                    code = httpCode,
                    url = extractUrl(exception),
                )
            }

            // Map by error code category
            return when (exception.errorCode) {
                // Network errors
                in
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED..PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
                -> {
                    Network(
                        code = exception.errorCode,
                        message = exception.message,
                    )
                }

                // HTTP errors (when no status code extracted)
                PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> {
                    Http(
                        code = 0, // Unknown status
                        url = extractUrl(exception),
                    )
                }

                // Source/format errors
                in
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED..PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
                PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
                PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
                -> {
                    Source(message = exception.message)
                }

                // Decoder errors
                in
                PlaybackException.ERROR_CODE_DECODER_INIT_FAILED..PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
                in
                PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED..PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED,
                -> {
                    Decoder(message = exception.message)
                }

                // Unknown/other errors
                else -> Unknown(throwable = exception)
            }
        }

        /**
         * Extract HTTP status code from the exception cause chain.
         */
        private fun extractHttpStatusCode(exception: PlaybackException): Int? {
            var cause: Throwable? = exception
            while (cause != null) {
                // Check for HttpDataSource.InvalidResponseCodeException
                val className = cause::class.java.name
                if (className.contains("InvalidResponseCodeException")) {
                    // Use reflection to get responseCode field
                    runCatching {
                        val field = cause::class.java.getDeclaredField("responseCode")
                        field.isAccessible = true
                        val code = field.getInt(cause)
                        if (code in 100..599) {
                            return code
                        }
                    }
                }
                cause = cause.cause
            }
            return null
        }

        /**
         * Extract URL from the exception cause chain.
         */
        private fun extractUrl(exception: PlaybackException): String? {
            var cause: Throwable? = exception
            while (cause != null) {
                val className = cause::class.java.name
                if (className.contains("InvalidResponseCodeException") ||
                    className.contains("HttpDataSourceException")
                ) {
                    // Try to get dataSpec.uri
                    runCatching {
                        val dataSpecField = cause::class.java.getDeclaredField("dataSpec")
                        dataSpecField.isAccessible = true
                        val dataSpec = dataSpecField.get(cause)
                        if (dataSpec != null) {
                            val uriField = dataSpec::class.java.getDeclaredField("uri")
                            uriField.isAccessible = true
                            return uriField.get(dataSpec)?.toString()
                        }
                    }
                }
                cause = cause.cause
            }
            return null
        }
    }
}

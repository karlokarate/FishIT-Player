package com.fishit.player.playback.telegram

import android.net.Uri
import com.fishit.player.core.model.PlaybackHintKeys
import java.net.URLEncoder

/**
 * SSOT contract for Telegram playback URI construction and validation.
 *
 * This object defines the authoritative format for tg:// URIs used in Telegram playback:
 * - Single place for building URIs (factory uses this)
 * - Single place for parsing URIs (DataSource uses this)
 * - Validation rules to prevent invalid/unresolvable URIs
 *
 * **URI Format:**
 * ```
 * tg://file/<fileId>?chatId=<chatId>&messageId=<messageId>&remoteId=<remoteId>&mimeType=<mimeType>
 * ```
 *
 * **Resolution Contract:**
 * The URI MUST contain enough information for TelegramFileDataSource to resolve the file:
 * 1. MUST have chatId AND messageId (for fallback message fetch)
 * 2. MUST have either:
 *    - fileId > 0 (fast path - same TDLib session)
 *    - OR remoteId != null/blank (stable cross-session identifier)
 * 3. Without both chatId/messageId AND (fileId>0 OR remoteId), URI is INVALID
 *
 * @see TelegramPlaybackSourceFactoryImpl uses this to build URIs
 * @see TelegramFileDataSource uses this to parse URIs
 */
object TelegramPlaybackUriContract {
    const val SCHEME = "tg"
    const val HOST = "file"

    // Query parameter keys (stable for serialization)
    const val PARAM_CHAT_ID = "chatId"
    const val PARAM_MESSAGE_ID = "messageId"
    const val PARAM_REMOTE_ID = "remoteId"
    const val PARAM_MIME_TYPE = "mimeType"
    const val PARAM_DURATION_MS = "durationMs"
    const val PARAM_SIZE_BYTES = "sizeBytes"

    /**
     * Parsed URI components.
     *
     * This data class represents the result of parsing a tg:// URI.
     * All fields nullable except what was parsed successfully.
     */
    data class ParsedUri(
        val fileId: Int?,
        val chatId: Long?,
        val messageId: Long?,
        val remoteId: String?,
        val mimeType: String?,
        val durationMs: Long?,
        val sizeBytes: Long?,
    ) {
        /** Returns true if URI has minimal required fields for resolution. */
        val isResolvable: Boolean
            get() = hasMessageLocator && hasFileLocator

        /** Has chat+message identity for fallback resolution. */
        val hasMessageLocator: Boolean
            get() = chatId != null && messageId != null

        /** Has direct file locator (fileId > 0 OR remoteId). */
        val hasFileLocator: Boolean
            get() = (fileId != null && fileId > 0) || !remoteId.isNullOrBlank()
    }

    /**
     * Validation result with error details.
     */
    sealed class ValidationResult {
        object Valid : ValidationResult()

        data class Invalid(
            val reason: String,
        ) : ValidationResult()
    }

    /**
     * Builds a tg:// URI from components.
     *
     * @param fileId TDLib local file ID (0 if unknown/cross-session)
     * @param chatId Chat containing the media (REQUIRED)
     * @param messageId Message ID within the chat (REQUIRED)
     * @param remoteId Stable remote file ID (REQUIRED if fileId <= 0)
     * @param mimeType Optional MIME type hint
     * @param durationMs Optional duration in milliseconds
     * @param sizeBytes Optional file size in bytes
     * @return Valid tg:// URI string
     * @throws IllegalArgumentException if required fields are missing
     */
    fun buildUri(
        fileId: Int = 0,
        chatId: Long,
        messageId: Long,
        remoteId: String? = null,
        mimeType: String? = null,
        durationMs: Long? = null,
        sizeBytes: Long? = null,
    ): String {
        // Validate required fields
        require(chatId != 0L) { "chatId is required and cannot be 0" }
        require(messageId != 0L) { "messageId is required and cannot be 0" }
        require(fileId > 0 || !remoteId.isNullOrBlank()) {
            "Either fileId > 0 or remoteId must be provided for resolution"
        }

        val builder = StringBuilder("$SCHEME://$HOST/$fileId")
        val params = mutableListOf<String>()

        params.add("$PARAM_CHAT_ID=$chatId")
        params.add("$PARAM_MESSAGE_ID=$messageId")

        remoteId?.takeIf { it.isNotBlank() }?.let {
            params.add("$PARAM_REMOTE_ID=${encodeParam(it)}")
        }
        mimeType?.takeIf { it.isNotBlank() }?.let {
            params.add("$PARAM_MIME_TYPE=${encodeParam(it)}")
        }
        durationMs?.let {
            params.add("$PARAM_DURATION_MS=$it")
        }
        sizeBytes?.let {
            params.add("$PARAM_SIZE_BYTES=$it")
        }

        if (params.isNotEmpty()) {
            builder.append("?")
            builder.append(params.joinToString("&"))
        }

        return builder.toString()
    }

    /**
     * Builds a tg:// URI from PlaybackContext extras (playbackHints).
     *
     * This is the primary builder for use by TelegramPlaybackSourceFactoryImpl.
     *
     * @param extras Map containing PlaybackHintKeys.Telegram.* values
     * @param fileId Optional explicit fileId (from legacy sourceKey parsing)
     * @return Valid tg:// URI string
     * @throws IllegalArgumentException if required fields are missing
     */
    fun buildUriFromExtras(
        extras: Map<String, String>,
        fileId: Int = 0,
    ): String {
        val chatId =
            (extras[PlaybackHintKeys.Telegram.CHAT_ID] ?: extras["chatId"])
                ?.toLongOrNull()
                ?: throw IllegalArgumentException("Missing chatId in extras")

        val messageId =
            (extras[PlaybackHintKeys.Telegram.MESSAGE_ID] ?: extras["messageId"])
                ?.toLongOrNull()
                ?: throw IllegalArgumentException("Missing messageId in extras")

        val remoteId = extras[PlaybackHintKeys.Telegram.REMOTE_ID] ?: extras["remoteId"]
        val explicitFileId =
            (extras[PlaybackHintKeys.Telegram.FILE_ID] ?: extras["fileId"])
                ?.toIntOrNull()
                ?: fileId
        val mimeType = extras[PlaybackHintKeys.Telegram.MIME_TYPE] ?: extras["mimeType"]

        // Validate resolution capability
        if (explicitFileId <= 0 && remoteId.isNullOrBlank()) {
            throw IllegalArgumentException(
                "Neither fileId > 0 nor remoteId available - cannot resolve Telegram file. " +
                    "chatId=$chatId, messageId=$messageId",
            )
        }

        return buildUri(
            fileId = explicitFileId,
            chatId = chatId,
            messageId = messageId,
            remoteId = remoteId,
            mimeType = mimeType,
        )
    }

    /**
     * Parses a tg:// URI into its components.
     *
     * @param uriString The URI string to parse
     * @return ParsedUri with extracted components (null for missing/invalid values)
     */
    fun parseUri(uriString: String): ParsedUri? {
        val uri =
            try {
                Uri.parse(uriString)
            } catch (e: Exception) {
                return null
            }

        if (uri.scheme != SCHEME || uri.host != HOST) {
            return null
        }

        val pathSegments = uri.pathSegments
        val fileId = pathSegments.getOrNull(0)?.toIntOrNull()

        return ParsedUri(
            fileId = fileId,
            chatId = uri.getQueryParameter(PARAM_CHAT_ID)?.toLongOrNull(),
            messageId = uri.getQueryParameter(PARAM_MESSAGE_ID)?.toLongOrNull(),
            remoteId = uri.getQueryParameter(PARAM_REMOTE_ID),
            mimeType = uri.getQueryParameter(PARAM_MIME_TYPE),
            durationMs = uri.getQueryParameter(PARAM_DURATION_MS)?.toLongOrNull(),
            sizeBytes = uri.getQueryParameter(PARAM_SIZE_BYTES)?.toLongOrNull(),
        )
    }

    /**
     * Validates a tg:// URI string.
     *
     * @param uriString The URI to validate
     * @return ValidationResult.Valid or ValidationResult.Invalid with reason
     */
    fun validate(uriString: String): ValidationResult {
        val parsed =
            parseUri(uriString)
                ?: return ValidationResult.Invalid("Failed to parse URI: $uriString")

        if (parsed.chatId == null || parsed.chatId == 0L) {
            return ValidationResult.Invalid("Missing or invalid chatId")
        }

        if (parsed.messageId == null || parsed.messageId == 0L) {
            return ValidationResult.Invalid("Missing or invalid messageId")
        }

        if (!parsed.hasFileLocator) {
            return ValidationResult.Invalid(
                "No file locator: fileId=${parsed.fileId}, remoteId=${parsed.remoteId}",
            )
        }

        return ValidationResult.Valid
    }

    /**
     * Checks if a URI string looks like a Telegram playback URI.
     */
    fun isTelegramUri(uriString: String?): Boolean = uriString?.startsWith("$SCHEME://$HOST/") == true

    private fun encodeParam(value: String): String =
        try {
            URLEncoder.encode(value, "UTF-8")
        } catch (e: Exception) {
            value
        }
}

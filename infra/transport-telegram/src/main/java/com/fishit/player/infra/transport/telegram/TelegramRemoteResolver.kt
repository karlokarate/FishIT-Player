package com.fishit.player.infra.transport.telegram

/**
 * Resolves Telegram remote identifiers (chatId + messageId) to current TDLib file references.
 *
 * This service implements the **RemoteId-First Architecture** defined in
 * `contracts/TELEGRAM_ID_ARCHITECTURE_CONTRACT.md`.
 *
 * ## Core Principle
 *
 * RemoteId (chatId + messageId) is the **SSOT** for Telegram media. TDLib file.id is treated as
 * ephemeral session detail that may change across:
 * - TDLib cache eviction
 * - User logout/login
 * - Message content updates
 * - App reinstalls
 *
 * ## Resolution Pattern
 *
 * ```kotlin
 * // Step 1: Retrieve message from TDLib
 * val resolved = resolver.resolveMedia(TelegramRemoteId(chatId, messageId))
 *
 * // Step 2: Use current fileIds for operations
 * telegramFileClient.startDownload(resolved.mediaFileId, priority = 32)
 *
 * // Step 3: On error, resolve again (fileId may have changed)
 * val freshResolved = resolver.resolveMedia(remoteId)
 * ```
 *
 * ## Architecture Boundaries
 *
 * - **Transport Layer:** Implements this interface, provides TDLib message fetching
 * - **Playback Layer:** Consumes resolved fileIds for streaming
 * - **Imaging Layer:** Consumes resolved thumbFileIds for thumbnails
 * - **Pipeline Layer:** Does NOT use this (only transport and above)
 *
 * @see contracts/TELEGRAM_ID_ARCHITECTURE_CONTRACT.md
 * @see TelegramFileClient for file download operations
 */
interface TelegramRemoteResolver {

    /**
     * Resolves a remote identifier to current TDLib file references.
     *
     * This method:
     * 1. Fetches the message via `client.getMessage(chatId, messageId)`
     * 2. Extracts video/document content
     * 3. Selects best available thumbnail
     * 4. Returns current fileIds and metadata
     *
     * **Error Handling:**
     * - Returns null if message not found (deleted, inaccessible)
     * - Returns null if message has no media content
     * - Logs errors via UnifiedLog
     *
     * **Performance:**
     * - Fast: Single TDLib API call
     * - Cacheable: Results can be cached with TTL
     * - Idempotent: Safe to call repeatedly
     *
     * @param remoteId The remote identifier (chatId + messageId)
     * @return Resolved media with current fileIds, or null if not resolvable
     */
    suspend fun resolveMedia(remoteId: TelegramRemoteId): ResolvedTelegramMedia?
}

/**
 * Remote identifier for Telegram media (SSOT).
 *
 * This is the stable identifier that persists in the database and flows through the system.
 * TDLib fileIds are resolved on-demand from this identifier.
 *
 * @property chatId Telegram chat ID containing the message
 * @property messageId Message ID within the chat
 */
data class TelegramRemoteId(
    val chatId: Long,
    val messageId: Long
) {
    /**
     * Encodes as a stable string for use in sourceKey or URIs.
     *
     * Format: "msg:<chatId>:<messageId>"
     */
    fun toSourceKey(): String = "msg:$chatId:$messageId"

    companion object {
        /**
         * Parses a sourceKey into a TelegramRemoteId.
         *
         * Supports format: "msg:<chatId>:<messageId>"
         *
         * @param sourceKey The encoded source key
         * @return Parsed remote ID, or null if invalid format
         */
        fun fromSourceKey(sourceKey: String): TelegramRemoteId? {
            if (!sourceKey.startsWith("msg:")) return null
            val parts = sourceKey.split(":")
            if (parts.size < 3) return null

            val chatId = parts[1].toLongOrNull() ?: return null
            val messageId = parts[2].toLongOrNull() ?: return null

            return TelegramRemoteId(chatId, messageId)
        }
    }
}

/**
 * Resolved Telegram media with current TDLib file references.
 *
 * Contains all information needed for playback and imaging:
 * - Current media fileId (video/document)
 * - Current thumbnail fileId (if available)
 * - Metadata (MIME type, duration, size, dimensions)
 * - Local path (if already downloaded)
 *
 * ## Important: FileIds are Ephemeral
 *
 * These fileIds are valid NOW but may become stale. Always resolve from remoteId when:
 * - Starting a new playback session
 * - After TDLib errors (file not found)
 * - After app restart
 * - After cache eviction
 *
 * @property mediaFileId Current TDLib file ID for the video/document
 * @property thumbFileId Current TDLib file ID for best available thumbnail (null if no thumb)
 * @property mimeType MIME type (e.g., "video/mp4", "video/x-matroska")
 * @property durationSecs Duration in seconds (null if unknown)
 * @property sizeBytes File size in bytes (0 if unknown)
 * @property width Video width in pixels (0 if unknown)
 * @property height Video height in pixels (0 if unknown)
 * @property supportsStreaming Whether TDLib indicates streaming support
 * @property mediaLocalPath Local path if media file already downloaded (null otherwise)
 * @property thumbLocalPath Local path if thumbnail already downloaded (null otherwise)
 * @property minithumbnailBytes Inline JPEG bytes for instant placeholder (~40px, null if unavailable)
 */
data class ResolvedTelegramMedia(
    val mediaFileId: Int,
    val thumbFileId: Int? = null,
    val mimeType: String? = null,
    val durationSecs: Int? = null,
    val sizeBytes: Long = 0,
    val width: Int = 0,
    val height: Int = 0,
    val supportsStreaming: Boolean = false,
    val mediaLocalPath: String? = null,
    val thumbLocalPath: String? = null,
    val minithumbnailBytes: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ResolvedTelegramMedia) return false
        return mediaFileId == other.mediaFileId &&
            thumbFileId == other.thumbFileId &&
            mimeType == other.mimeType &&
            durationSecs == other.durationSecs &&
            sizeBytes == other.sizeBytes &&
            width == other.width &&
            height == other.height &&
            supportsStreaming == other.supportsStreaming &&
            mediaLocalPath == other.mediaLocalPath &&
            thumbLocalPath == other.thumbLocalPath &&
            minithumbnailBytes.contentEquals(other.minithumbnailBytes)
    }

    override fun hashCode(): Int {
        var result = mediaFileId
        result = 31 * result + (thumbFileId ?: 0)
        result = 31 * result + (mimeType?.hashCode() ?: 0)
        result = 31 * result + (durationSecs ?: 0)
        result = 31 * result + sizeBytes.hashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + supportsStreaming.hashCode()
        result = 31 * result + (mediaLocalPath?.hashCode() ?: 0)
        result = 31 * result + (thumbLocalPath?.hashCode() ?: 0)
        result = 31 * result + (minithumbnailBytes?.contentHashCode() ?: 0)
        return result
    }
}

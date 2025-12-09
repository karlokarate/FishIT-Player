package com.fishit.player.infra.transport.telegram

import kotlinx.coroutines.flow.Flow

/**
 * Telegram Transport Client Interface (v2 Architecture)
 *
 * Low-level TDLib operations abstracted for reuse across layers.
 * This interface exposes ONLY transport-level operations - no media-specific logic.
 *
 * **Module Boundary:**
 * - Transport layer provides raw TDLib access (auth, messages, files)
 * - Pipeline layer consumes transport and produces RawMediaMetadata
 * - Data layer consumes pipeline events and persists to DB
 *
 * **Design Principles:**
 * - Returns raw TDLib wrapper types (TgMessage, TgFile, TgChat)
 * - No knowledge of RawMediaMetadata or pipeline models
 * - Flow-based reactive APIs for state and updates
 * - Manages connection and authorization internally
 *
 * **NOT included:**
 * - Media classification/parsing (belongs in pipeline)
 * - Normalization (belongs in :core:metadata-normalizer)
 * - Persistence (belongs in :infra:data-telegram)
 * - Playback (belongs in :playback:telegram)
 */
interface TelegramTransportClient {

    /** Current authorization state. Emits updates when auth state changes. */
    val authState: Flow<TelegramAuthState>

    /** Current connection state. Emits updates when connection state changes. */
    val connectionState: Flow<TelegramConnectionState>

    /**
     * Ensure the client is authorized and ready to use.
     *
     * If not authorized, this will initiate the auth flow. Callers should observe [authState]
     * to handle interactive auth steps.
     *
     * @throws TelegramAuthException if authorization fails
     */
    suspend fun ensureAuthorized()

    /**
     * Check if currently authorized without initiating auth flow.
     *
     * @return true if authorized and ready
     */
    suspend fun isAuthorized(): Boolean

    /**
     * Get list of available chats.
     *
     * @param limit Maximum number of chats to return
     * @return List of chat info
     */
    suspend fun getChats(limit: Int = 100): List<TgChat>

    /**
     * Fetch messages from a specific chat.
     *
     * @param chatId Telegram chat ID
     * @param limit Maximum number of messages to fetch
     * @param offsetMessageId Starting message ID for pagination (0 for most recent)
     * @return List of messages from the chat
     */
    suspend fun fetchMessages(
        chatId: Long,
        limit: Int = 100,
        offsetMessageId: Long = 0
    ): List<TgMessage>

    /**
     * Resolve file location for a Telegram file.
     *
     * @param fileId TDLib file ID
     * @return File location details
     * @throws TelegramFileException if file cannot be resolved
     */
    suspend fun resolveFile(fileId: Int): TgFile

    /**
     * Resolve file by remote ID (cross-session stable identifier).
     *
     * @param remoteId Remote file ID string
     * @return Resolved file info
     * @throws TelegramFileException if file cannot be resolved
     */
    suspend fun resolveFileByRemoteId(remoteId: String): TgFile

    /**
     * Request file download/preparation.
     *
     * @param fileId TDLib file ID
     * @param priority Download priority hint (1-32, higher = more important)
     * @return Updated file location
     */
    suspend fun requestFileDownload(fileId: Int, priority: Int = 16): TgFile

    /** Close the client and release resources. */
    suspend fun close()
}

// ============================================================================
// Transport-Level Types (Wrapper around TDLib DTOs)
// ============================================================================

/**
 * Wrapper for TDLib Chat.
 * Exposes only fields needed by transport consumers.
 */
data class TgChat(
    val id: Long,
    val title: String,
    val type: TgChatType,
    val photoSmallFileId: Int?,
    val photoBigFileId: Int?,
    val memberCount: Int?
)

/**
 * Simplified chat type enum.
 */
enum class TgChatType {
    PRIVATE,
    BASIC_GROUP,
    SUPERGROUP,
    CHANNEL,
    SECRET,
    UNKNOWN
}

/**
 * Wrapper for TDLib Message.
 * Contains content details needed for media classification.
 */
data class TgMessage(
    val id: Long,
    val chatId: Long,
    val senderId: Long,
    val date: Int,
    val content: TgContent,
    val replyToMessageId: Long?
)

/**
 * Sealed hierarchy for message content types.
 * Maps to TDLib MessageContent types relevant for media.
 */
sealed class TgContent {
    /** Video message content */
    data class Video(
        val fileId: Int,
        val remoteId: String,
        val uniqueId: String,
        val duration: Int,
        val width: Int,
        val height: Int,
        val fileName: String?,
        val mimeType: String?,
        val fileSize: Long,
        val caption: String?,
        val thumbnail: TgThumbnail?
    ) : TgContent()

    /** Document (file) message content */
    data class Document(
        val fileId: Int,
        val remoteId: String,
        val uniqueId: String,
        val fileName: String?,
        val mimeType: String?,
        val fileSize: Long,
        val caption: String?,
        val thumbnail: TgThumbnail?
    ) : TgContent()

    /** Audio message content */
    data class Audio(
        val fileId: Int,
        val remoteId: String,
        val uniqueId: String,
        val duration: Int,
        val title: String?,
        val performer: String?,
        val fileName: String?,
        val mimeType: String?,
        val fileSize: Long,
        val caption: String?,
        val albumCoverThumbnail: TgThumbnail?
    ) : TgContent()

    /** Photo message content */
    data class Photo(
        val sizes: List<TgPhotoSize>,
        val caption: String?
    ) : TgContent()

    /** Animation (GIF) message content */
    data class Animation(
        val fileId: Int,
        val remoteId: String,
        val uniqueId: String,
        val duration: Int,
        val width: Int,
        val height: Int,
        val fileName: String?,
        val mimeType: String?,
        val fileSize: Long,
        val caption: String?,
        val thumbnail: TgThumbnail?
    ) : TgContent()

    /** Video note (round video) message content */
    data class VideoNote(
        val fileId: Int,
        val remoteId: String,
        val uniqueId: String,
        val duration: Int,
        val length: Int,
        val fileSize: Long,
        val thumbnail: TgThumbnail?
    ) : TgContent()

    /** Voice note message content */
    data class VoiceNote(
        val fileId: Int,
        val remoteId: String,
        val uniqueId: String,
        val duration: Int,
        val mimeType: String?,
        val fileSize: Long,
        val caption: String?
    ) : TgContent()

    /** Text message (not media but included for completeness) */
    data class Text(
        val text: String
    ) : TgContent()

    /** Unsupported or unknown content type */
    data class Unsupported(
        val typeName: String
    ) : TgContent()
}

/**
 * Photo size information.
 */
data class TgPhotoSize(
    val type: String,
    val fileId: Int,
    val remoteId: String,
    val uniqueId: String,
    val width: Int,
    val height: Int,
    val fileSize: Long
)

/**
 * Thumbnail information.
 */
data class TgThumbnail(
    val fileId: Int,
    val remoteId: String,
    val uniqueId: String,
    val width: Int,
    val height: Int,
    val fileSize: Long
)

/**
 * File location and download state.
 */
data class TgFile(
    val id: Int,
    val remoteId: String,
    val uniqueId: String,
    val size: Long,
    val expectedSize: Long,
    val localPath: String?,
    val downloadedPrefixSize: Long,
    val isDownloadingActive: Boolean,
    val isDownloadingCompleted: Boolean
)

// ============================================================================
// State Types
// ============================================================================

/** Telegram authorization state. */
sealed class TelegramAuthState {
    data object Idle : TelegramAuthState()
    data object Connecting : TelegramAuthState()
    data object WaitingForPhone : TelegramAuthState()
    data object WaitingForCode : TelegramAuthState()
    data object WaitingForPassword : TelegramAuthState()
    data object Ready : TelegramAuthState()
    data class Error(val message: String) : TelegramAuthState()
}

/** Telegram connection state. */
sealed class TelegramConnectionState {
    data object Disconnected : TelegramConnectionState()
    data object Connecting : TelegramConnectionState()
    data object Connected : TelegramConnectionState()
    data class Error(val message: String) : TelegramConnectionState()
}

// ============================================================================
// Exceptions
// ============================================================================

/** Exception thrown during Telegram authentication. */
class TelegramAuthException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Exception thrown during Telegram file operations. */
class TelegramFileException(message: String, cause: Throwable? = null) : Exception(message, cause)

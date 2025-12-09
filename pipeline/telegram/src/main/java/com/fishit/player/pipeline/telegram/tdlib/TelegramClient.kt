package com.fishit.player.pipeline.telegram.tdlib

import com.fishit.player.pipeline.telegram.model.TelegramMediaItem
import kotlinx.coroutines.flow.Flow

/**
 * v2 Telegram Client Interface
 *
 * Abstracts TDLib client operations for Telegram media access. Focuses on media-related operations
 * (chat history, message/media resolution, file locations).
 *
 * **Naming Convention:**
 * - `TelegramClient` = Interface (analog to `XtreamApiClient`)
 * - `DefaultTelegramClient` = Implementation (analog to `DefaultXtreamApiClient`)
 *
 * **v1 Component Mapping:**
 * - Replaces direct TdlClient usage from v1
 * - Wraps functionality from v1 `T_TelegramServiceClient`, `T_TelegramSession`, `T_ChatBrowser`
 * - Provides pipeline-appropriate API surface (no UI, no player logic)
 *
 * **Design Principles:**
 * - Media-focused API (no auth UI, no player logic)
 * - Uses g00sha tdlib-coroutines as underlying client
 * - Flow-based reactive APIs for state and updates
 * - Manages connection and authorization internally
 *
 * **NOT included:**
 * - ExoPlayer/DataSource logic (belongs in `:player:internal`)
 * - Caching or resume logic (belongs in domain/persistence layers)
 * - UI components (belong in `:feature:telegram-media`)
 */
interface TelegramClient {
    /** Current authorization state. Emits updates when auth state changes. */
    val authState: Flow<TelegramAuthState>

    /** Current connection state. Emits updates when connection state changes. */
    val connectionState: Flow<TelegramConnectionState>

    /**
     * Ensure the client is authorized and ready to use.
     *
     * If not authorized, this will initiate the auth flow. Callers should observe [authState] to
     * handle interactive auth steps.
     *
     * @throws TelegramAuthException if authorization fails
     */
    suspend fun ensureAuthorized()

    /**
     * Fetch media messages from a specific chat.
     *
     * @param chatId Telegram chat ID
     * @param limit Maximum number of messages to fetch
     * @param offsetMessageId Starting message ID for pagination (0 for most recent)
     * @return List of media items from the chat
     */
    suspend fun fetchMediaMessages(
        chatId: Long,
        limit: Int = 100,
        offsetMessageId: Long = 0,
    ): List<TelegramMediaItem>

    /**
     * Fetch all media messages from selected chats.
     *
     * @param chatIds List of chat IDs to fetch from
     * @param limit Maximum messages per chat
     * @return List of all media items across chats
     */
    suspend fun fetchAllMediaMessages(
        chatIds: List<Long>,
        limit: Int = 100,
    ): List<TelegramMediaItem>

    /**
     * Resolve file location for a Telegram file.
     *
     * This returns the TDLib file information needed for streaming/downloading.
     *
     * @param fileId TDLib file ID
     * @return File location details
     * @throws TelegramFileException if file cannot be resolved
     */
    suspend fun resolveFileLocation(fileId: Int): TelegramFileLocation

    /**
     * Resolve file by remote ID (cross-session stable identifier).
     *
     * @param remoteId Remote file ID
     * @return Resolved file ID for current session
     * @throws TelegramFileException if file cannot be resolved
     */
    suspend fun resolveFileByRemoteId(remoteId: String): Int

    /**
     * Get list of available chats.
     *
     * @param limit Maximum number of chats to return
     * @return List of chat summaries
     */
    suspend fun getChats(limit: Int = 100): List<TelegramChatInfo>

    /**
     * Get raw message history from a chat.
     *
     * This is a low-level API that returns raw TDLib Message DTOs for catalog scanning.
     * Unlike fetchMediaMessages, this does NOT filter or map messages.
     *
     * **TDLib Pagination:**
     * - fromMessageId=0: start from latest message
     * - fromMessageId=X: continue from message X (going backwards in time)
     *
     * @param chatId Chat ID to fetch messages from
     * @param fromMessageId Starting message ID (0 for latest)
     * @param limit Maximum number of messages to fetch (max 100)
     * @return List of raw TDLib messages
     */
    suspend fun getMessagesPage(
        chatId: Long,
        fromMessageId: Long = 0,
        limit: Int = 100,
    ): List<dev.g000sha256.tdl.dto.Message>

    /**
     * Request file download/preparation (metadata only).
     *
     * **ARCHITECTURE NOTE:** This method only REQUESTS the download and returns file metadata.
     * Actual streaming/buffering logic belongs in `:player:internal` DataSource. The pipeline does
     * NOT implement streaming, windowing, or chunk logic.
     *
     * @param fileId TDLib file ID
     * @param priority Download priority hint (1-32, higher = more important)
     * @return File location metadata (localPath may be null if not yet downloaded)
     */
    suspend fun requestFileDownload(
        fileId: Int,
        priority: Int = 16,
    ): TelegramFileLocation

    /** Close the client and release resources. */
    suspend fun close()
}

/** Telegram authorization state. */
sealed class TelegramAuthState {
    object Idle : TelegramAuthState()

    object Connecting : TelegramAuthState()

    object WaitingForPhone : TelegramAuthState()

    object WaitingForCode : TelegramAuthState()

    object WaitingForPassword : TelegramAuthState()

    object Ready : TelegramAuthState()

    data class Error(
        val message: String,
    ) : TelegramAuthState()
}

/** Telegram connection state. */
sealed class TelegramConnectionState {
    object Disconnected : TelegramConnectionState()

    object Connecting : TelegramConnectionState()

    object Connected : TelegramConnectionState()

    data class Error(
        val message: String,
    ) : TelegramConnectionState()
}

/**
 * Telegram file location details.
 *
 * Provides information needed for streaming/downloading via TDLib.
 */
data class TelegramFileLocation(
    val fileId: Int,
    val remoteId: String,
    val uniqueId: String,
    val localPath: String?,
    val size: Long,
    val downloadedSize: Long,
    val isDownloadingActive: Boolean,
    val isDownloadingCompleted: Boolean,
)

/** Telegram chat information. */
data class TelegramChatInfo(
    val chatId: Long,
    val title: String,
    val type: String,
    val photoPath: String?,
)

/** Exception thrown during Telegram authentication. */
class TelegramAuthException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/** Exception thrown during Telegram file operations. */
class TelegramFileException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

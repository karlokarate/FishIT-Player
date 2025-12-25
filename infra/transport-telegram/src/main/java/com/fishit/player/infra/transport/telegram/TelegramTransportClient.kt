package com.fishit.player.infra.transport.telegram

import com.fishit.player.infra.transport.telegram.api.TdlibAuthState
import com.fishit.player.infra.transport.telegram.api.TelegramAuthException
import com.fishit.player.infra.transport.telegram.api.TelegramConnectionState
import com.fishit.player.infra.transport.telegram.api.TelegramFileException
import com.fishit.player.infra.transport.telegram.api.TgChat
import com.fishit.player.infra.transport.telegram.api.TgFile
import com.fishit.player.infra.transport.telegram.api.TgMessage
import kotlinx.coroutines.flow.Flow

/**
 * Telegram Transport Client Interface (v2 Architecture)
 *
 * Low-level TDLib operations abstracted for reuse across layers. This interface exposes ONLY
 * transport-level operations - no media-specific logic.
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
        val authState: Flow<TdlibAuthState>

        /** Current connection state. Emits updates when connection state changes. */
        val connectionState: Flow<TelegramConnectionState>

        /**
         * Stream of incoming messages (media-only) for live ingestion/warm-up. Implementations
         * should emit only playable media messages.
         */
        val mediaUpdates: Flow<TgMessage>

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

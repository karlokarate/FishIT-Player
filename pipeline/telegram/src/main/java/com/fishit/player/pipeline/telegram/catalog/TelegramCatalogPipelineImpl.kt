package com.fishit.player.pipeline.telegram.catalog

import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.pipeline.telegram.tdlib.TelegramAuthState
import com.fishit.player.pipeline.telegram.tdlib.TelegramChatInfo
import com.fishit.player.pipeline.telegram.tdlib.TelegramClient
import com.fishit.player.pipeline.telegram.tdlib.TelegramConnectionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * Default implementation of TelegramCatalogPipeline.
 *
 * **Architecture:**
 * - Stateless: no DB writes, no cache, no downloads
 * - Side-effect-free: only reads TDLib state and emits events
 * - Uses UnifiedLog for logging per LOGGING_CONTRACT_V2
 *
 * **Flow:**
 * 1. Check auth/connection state (emit error if not ready)
 * 2. Resolve chats to scan (filter by config.includedChatIds)
 * 3. For each chat, create TelegramMessageCursor
 * 4. Iterate messages, classify, map to RawMediaMetadata
 * 5. Emit ItemDiscovered for relevant media
 * 6. Emit progress events periodically
 * 7. Emit ScanCompleted at end
 *
 * @param client TelegramClient for TDLib access
 * @param mapper Message → RawMediaMetadata mapper
 */
class TelegramCatalogPipelineImpl
    @Inject
    constructor(
        private val client: TelegramClient,
        private val mapper: TelegramCatalogMessageMapper,
    ) : TelegramCatalogPipeline {
        companion object {
            private const val TAG = "TelegramCatalogPipeline"
            private const val PROGRESS_UPDATE_INTERVAL = 50 // messages
            private const val DEFAULT_CHAT_LIMIT = 200
        }

        override fun scanCatalog(config: TelegramCatalogConfig): Flow<TelegramCatalogEvent> =
            flow {
                UnifiedLog.i(TAG, "Starting catalog scan with config: $config")

                try {
                    // ========== 1. Check Auth/Connection State ==========
                    val authState = client.authState.first()
                    val connectionState = client.connectionState.first()

                    if (authState !is TelegramAuthState.Ready) {
                        UnifiedLog.w(TAG, "Not authenticated: $authState")
                        emit(TelegramCatalogEvent.ScanError("unauthenticated_or_not_ready_auth_state"))
                        return@flow
                    }

                    if (connectionState !is TelegramConnectionState.Connected) {
                        UnifiedLog.w(TAG, "Not connected: $connectionState")
                        emit(TelegramCatalogEvent.ScanError("offline_or_not_connected"))
                        return@flow
                    }

                    UnifiedLog.d(TAG, "Auth and connection OK, resolving chats...")

                    // ========== 2. Resolve Chats ==========
                    val allChats = client.getChats(limit = DEFAULT_CHAT_LIMIT)
                    val chatsToScan =
                        if (config.includedChatIds.isNotEmpty()) {
                            allChats.filter { it.chatId in config.includedChatIds }
                        } else {
                            allChats
                        }

                    if (chatsToScan.isEmpty()) {
                        UnifiedLog.w(TAG, "No chats to scan")
                        emit(TelegramCatalogEvent.ScanCompleted(scannedChats = 0, scannedMessages = 0))
                        return@flow
                    }

                    UnifiedLog.i(TAG, "Scanning ${chatsToScan.size} chats")
                    emit(TelegramCatalogEvent.ScanningInitial(chatCount = chatsToScan.size))

                    // ========== 3. Scan Each Chat ==========
                    var scannedChats = 0
                    var scannedMessages = 0L

                    for (chat in chatsToScan) {
                        UnifiedLog.d(TAG, "Scanning chat ${chat.chatId} (${chat.title})")

                        try {
                            val chatMessages =
                                scanChat(chat, config) { event ->
                                    emit(event)
                                }
                            scannedMessages += chatMessages

                            scannedChats++

                            // Emit progress
                            emit(
                                TelegramCatalogEvent.ScanProgress(
                                    scannedChats = scannedChats,
                                    totalChats = chatsToScan.size,
                                    scannedMessages = scannedMessages,
                                ),
                            )
                        } catch (e: Exception) {
                            UnifiedLog.w(TAG, "Failed to scan chat ${chat.chatId}: ${e.message}", e)
                            // Continue with next chat (non-fatal error)
                        }
                    }

                    // ========== 4. Scan Completed ==========
                    UnifiedLog.i(TAG, "Scan completed: $scannedChats chats, $scannedMessages messages")
                    emit(
                        TelegramCatalogEvent.ScanCompleted(
                            scannedChats = scannedChats,
                            scannedMessages = scannedMessages,
                        ),
                    )
                } catch (e: Exception) {
                    UnifiedLog.e(TAG, "Catalog scan failed", e)
                    emit(TelegramCatalogEvent.ScanError("scan_failed", e))
                }
            }

        /**
         * Scan a single chat for media messages.
         *
         * Uses TelegramMessageCursor to traverse history, classifies messages,
         * and emits ItemDiscovered events for relevant media.
         *
         * @param chat Chat to scan
         * @param config Scan configuration
         * @return Number of messages scanned
         */
        private suspend fun scanChat(
            chat: TelegramChatInfo,
            config: TelegramCatalogConfig,
            emitEvent: suspend (TelegramCatalogEvent) -> Unit,
        ): Long {
            val cursor =
                TelegramMessageCursor(
                    client = client,
                    chatId = chat.chatId,
                    maxMessages = config.maxMessagesPerChat,
                    minMessageTimestampMs = config.minMessageTimestampMs,
                    pageSize = 100,
                )

            var messageCount = 0L
            var lastProgressUpdate = 0L

            while (cursor.hasNext()) {
                val messages = cursor.nextBatch()
                if (messages.isEmpty()) break

                for (message in messages) {
                    messageCount++

                    // Classify media kind
                    val mediaKind = mapper.classifyMediaKind(message)

                    // Check if this media kind is included in config
                    val shouldInclude =
                        when (mediaKind) {
                            MediaKind.Image -> config.includeImages
                            MediaKind.Video -> config.includeVideo
                            MediaKind.Audio -> config.includeAudio
                            MediaKind.Document -> config.includeDocuments
                            MediaKind.Unknown -> false
                        }

                    if (!shouldInclude) continue

                    // Map to RawMediaMetadata
                    val rawMetadata = mapper.toRawMediaMetadata(message, chat, mediaKind)
                    if (rawMetadata != null) {
                        val item =
                            TelegramCatalogItem(
                                raw = rawMetadata,
                                chatId = chat.chatId,
                                messageId = message.id,
                                chatTitle = chat.title,
                            )
                        emitEvent(TelegramCatalogEvent.ItemDiscovered(item))
                    }

                    // Emit progress update periodically
                    if (messageCount - lastProgressUpdate >= PROGRESS_UPDATE_INTERVAL) {
                        lastProgressUpdate = messageCount
                        UnifiedLog.d(TAG, "Chat ${chat.chatId}: scanned $messageCount messages")
                    }
                }
            }

            UnifiedLog.d(TAG, "Chat ${chat.chatId}: completed with $messageCount messages")
            return messageCount
        }
    }

package com.fishit.player.pipeline.telegram.catalog

import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.pipeline.telegram.tdlib.TelegramAuthState
import com.fishit.player.pipeline.telegram.tdlib.TelegramClient
import com.fishit.player.pipeline.telegram.tdlib.TelegramConnectionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * Implementation of [TelegramCatalogPipeline].
 *
 * Scans Telegram chats for media and emits catalog events following the stateless, side-effect-free
 * catalog pipeline contract.
 *
 * **Design Principles:**
 * - Stateless: No persistence, no downloads, no caching
 * - Flow-based: Emits events as items are discovered
 * - Error-resilient: Logs errors but continues scanning where possible
 * - Auth-check: Validates auth/connection before scanning
 *
 * **Architecture Boundaries:**
 * - NO TDLib cache management
 * - NO media downloads (thumbnails or files)
 * - NO writes to any DB or index
 * - NO interaction with ExoPlayer or UI
 * - NO title normalization (delegated to :core:metadata-normalizer)
 *
 * @property telegramClient TDLib client for message history access.
 * @property messageMapper Mapper for converting TDLib messages to RawMediaMetadata.
 */
class TelegramCatalogPipelineImpl
    @Inject
    constructor(
        private val telegramClient: TelegramClient,
        private val messageMapper: TelegramCatalogMessageMapper,
    ) : TelegramCatalogPipeline {
        companion object {
            private const val TAG = "TelegramCatalogPipeline"
            private const val PROGRESS_EMIT_INTERVAL = 50 // Emit progress every 50 messages
        }

        override fun scanCatalog(config: TelegramCatalogConfig): Flow<TelegramCatalogEvent> =
            flow {
                UnifiedLog.i(TAG, "Starting catalog scan with config: $config")

                // ========== 1. Check Auth State ==========
                val authState = telegramClient.authState.firstOrNull()
                if (authState !is TelegramAuthState.Ready) {
                    UnifiedLog.w(TAG, "Auth state not ready: $authState")
                    emit(
                        TelegramCatalogEvent.ScanError(
                            reason = "unauthenticated_or_not_ready_auth_state",
                            throwable = null,
                        ),
                    )
                    return@flow
                }

                // ========== 2. Check Connection State ==========
                val connectionState = telegramClient.connectionState.firstOrNull()
                if (connectionState !is TelegramConnectionState.Connected) {
                    UnifiedLog.w(TAG, "Connection state not connected: $connectionState")
                    emit(
                        TelegramCatalogEvent.ScanError(
                            reason = "offline_or_not_connected",
                            throwable = null,
                        ),
                    )
                    return@flow
                }

                // ========== 3. Resolve Chats to Scan ==========
                val chatsToScan =
                    try {
                        resolveChatsToScan(config)
                    } catch (e: Exception) {
                        UnifiedLog.e(TAG, "Failed to resolve chats: ${e.message}", e)
                        emit(
                            TelegramCatalogEvent.ScanError(
                                reason = "failed_to_resolve_chats",
                                throwable = e,
                            ),
                        )
                        return@flow
                    }

                if (chatsToScan.isEmpty()) {
                    UnifiedLog.w(TAG, "No chats to scan")
                    emit(
                        TelegramCatalogEvent.ScanCompleted(
                            scannedChats = 0,
                            scannedMessages = 0,
                        ),
                    )
                    return@flow
                }

                UnifiedLog.i(TAG, "Resolved ${chatsToScan.size} chats to scan")

                // ========== 4. Emit ScanningInitial ==========
                emit(
                    TelegramCatalogEvent.ScanningInitial(
                        chatCount = chatsToScan.size,
                        estimatedTotalMessages = null, // No estimate available
                    ),
                )

                // ========== 5. Scan Each Chat ==========
                var scannedChats = 0
                var totalScannedMessages = 0L
                var progressCounter = 0

                for (chatInfo in chatsToScan) {
                    try {
                        UnifiedLog.d(TAG, "Scanning chat: ${chatInfo.title} (${chatInfo.chatId})")

                        val cursor =
                            TelegramMessageCursor(
                                telegramClient = telegramClient,
                                chatId = chatInfo.chatId,
                                maxMessagesPerChat = config.maxMessagesPerChat,
                                minMessageTimestampMs = config.minMessageTimestampMs,
                            )

                        while (cursor.hasNext()) {
                            val messages = cursor.nextBatch()

                            for (message in messages) {
                                // Classify and filter by media kind
                                val mediaKind = messageMapper.classifyMediaKind(message)
                                if (!shouldIncludeMediaKind(mediaKind, config)) {
                                    continue
                                }

                                // Map to RawMediaMetadata
                                val rawMetadata =
                                    messageMapper.toRawMediaMetadata(
                                        message = message,
                                        chatId = chatInfo.chatId,
                                        chatTitle = chatInfo.title,
                                        mediaKind = mediaKind,
                                    )

                                if (rawMetadata != null) {
                                    val catalogItem =
                                        TelegramCatalogItem(
                                            raw = rawMetadata,
                                            chatId = chatInfo.chatId,
                                            messageId = message.id,
                                            chatTitle = chatInfo.title,
                                        )
                                    emit(TelegramCatalogEvent.ItemDiscovered(catalogItem))
                                }
                            }

                            // Update progress counters
                            totalScannedMessages += messages.size
                            progressCounter += messages.size

                            // Emit progress periodically
                            if (progressCounter >= PROGRESS_EMIT_INTERVAL) {
                                emit(
                                    TelegramCatalogEvent.ScanProgress(
                                        scannedChats = scannedChats,
                                        totalChats = chatsToScan.size,
                                        scannedMessages = totalScannedMessages,
                                        totalMessagesEstimate = null,
                                    ),
                                )
                                progressCounter = 0
                            }
                        }

                        scannedChats++
                        UnifiedLog.d(TAG, "Completed scanning chat ${chatInfo.chatId}: ${cursor.getScannedCount()} messages")
                    } catch (e: Exception) {
                        UnifiedLog.e(TAG, "Error scanning chat ${chatInfo.chatId}: ${e.message}", e)
                        // Continue to next chat
                    }
                }

                // ========== 6. Emit Final Progress and Completion ==========
                if (progressCounter > 0) {
                    emit(
                        TelegramCatalogEvent.ScanProgress(
                            scannedChats = scannedChats,
                            totalChats = chatsToScan.size,
                            scannedMessages = totalScannedMessages,
                            totalMessagesEstimate = null,
                        ),
                    )
                }

                emit(
                    TelegramCatalogEvent.ScanCompleted(
                        scannedChats = scannedChats,
                        scannedMessages = totalScannedMessages,
                    ),
                )

                UnifiedLog.i(TAG, "Catalog scan completed: $scannedChats chats, $totalScannedMessages messages")
            }

        // ========== Private Helpers ==========

        /**
         * Resolve which chats to scan based on config.
         *
         * If config.includedChatIds is non-empty, use those.
         * Otherwise, fetch available chats from TDLib and filter to relevant types.
         */
        private suspend fun resolveChatsToScan(config: TelegramCatalogConfig): List<ChatToScan> =
            if (config.includedChatIds.isNotEmpty()) {
                // Use explicit chat IDs from config
                UnifiedLog.d(TAG, "Using explicit chat IDs: ${config.includedChatIds.size} chats")
                config.includedChatIds
                    .map { chatId ->
                        // Fetch chat info for each ID
                        try {
                            val chats = telegramClient.getChats(limit = 1000)
                            val chatInfo = chats.find { it.chatId == chatId }
                            if (chatInfo != null) {
                                ChatToScan(chatInfo.chatId, chatInfo.title)
                            } else {
                                UnifiedLog.w(TAG, "Chat $chatId not found in available chats")
                                null
                            }
                        } catch (e: Exception) {
                            UnifiedLog.w(TAG, "Failed to fetch chat $chatId: ${e.message}")
                            null
                        }
                    }.filterNotNull()
            } else {
                // Use default chat selection (user, supergroups, channels)
                UnifiedLog.d(TAG, "Using default chat selection")
                val allChats = telegramClient.getChats(limit = 1000)
                allChats
                    .filter { isRelevantChatType(it.type) }
                    .map { ChatToScan(it.chatId, it.title) }
            }

        /**
         * Check if a chat type is relevant for media scanning.
         *
         * Includes: user chats, supergroups, channels.
         * Excludes: secret chats (limited API access), basic groups (deprecated).
         */
        private fun isRelevantChatType(type: String): Boolean =
            when (type.lowercase()) {
                "private", "user" -> true
                "supergroup" -> true
                "channel" -> true
                "basicgroup" -> false // Basic groups are deprecated
                "secret" -> false // Secret chats have limited API access
                else -> false
            }

        /**
         * Check if a media kind should be included based on config.
         */
        private fun shouldIncludeMediaKind(
            kind: MediaKind,
            config: TelegramCatalogConfig,
        ): Boolean =
            when (kind) {
                MediaKind.Image -> config.includeImages
                MediaKind.Video -> config.includeVideo
                MediaKind.Audio -> config.includeAudio
                MediaKind.Document -> config.includeDocuments
                MediaKind.Unknown -> false // Skip unknown media
            }

        /**
         * Simple holder for chat info during scanning.
         */
        private data class ChatToScan(
            val chatId: Long,
            val title: String,
        )
    }

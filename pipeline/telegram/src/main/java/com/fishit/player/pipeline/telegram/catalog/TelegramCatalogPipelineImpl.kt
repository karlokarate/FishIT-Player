package com.fishit.player.pipeline.telegram.catalog

import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.pipeline.telegram.model.toRawMediaMetadata
import com.fishit.player.pipeline.telegram.tdlib.TelegramAuthState
import com.fishit.player.pipeline.telegram.tdlib.TelegramClient
import com.fishit.player.pipeline.telegram.tdlib.TelegramConnectionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import javax.inject.Inject

/**
 * Default implementation of [TelegramCatalogPipeline].
 *
 * Stateless producer that:
 * - Reads from TelegramClient (auth state, chats, messages)
 * - Converts TelegramMediaItem to RawMediaMetadata via toRawMediaMetadata()
 * - Emits TelegramCatalogEvent via cold Flow
 * - Performs no DB writes, caching, or UI work
 *
 * **Architecture Integration:**
 * - Uses existing TelegramClient interface (not raw TDLib)
 * - Uses TelegramMediaItem.toRawMediaMetadata() for conversion
 * - Emits standardized catalog events for CatalogSync consumption
 *
 * **Error Handling:**
 * - Pre-flight checks for auth and connection state
 * - Graceful handling of per-chat failures (logs warning, continues)
 * - Cancellation-aware (respects coroutine scope)
 *
 * @param client TelegramClient for chat/message access
 */
class TelegramCatalogPipelineImpl @Inject constructor(
    private val client: TelegramClient,
) : TelegramCatalogPipeline {

    override fun scanCatalog(
        config: TelegramCatalogConfig,
    ): Flow<TelegramCatalogEvent> = channelFlow {
        val startTime = System.currentTimeMillis()

        try {
            // Pre-flight: Check auth state
            val auth = client.authState.first()
            if (auth !is TelegramAuthState.Ready) {
                UnifiedLog.w(TAG, "Cannot scan: auth state is $auth")
                trySend(
                    TelegramCatalogEvent.ScanError(
                        reason = "unauthenticated",
                        message = "Telegram is not authenticated. Current state: $auth",
                    ),
                )
                return@channelFlow
            }

            // Pre-flight: Check connection state
            val conn = client.connectionState.first()
            if (conn !is TelegramConnectionState.Connected) {
                UnifiedLog.w(TAG, "Cannot scan: connection state is $conn")
                trySend(
                    TelegramCatalogEvent.ScanError(
                        reason = "not_connected",
                        message = "Telegram is not connected. Current state: $conn",
                    ),
                )
                return@channelFlow
            }

            // Get chats to scan
            val allChats = client.getChats(limit = 200)
            val chatsToScan = if (config.chatIds != null) {
                allChats.filter { it.chatId in config.chatIds }
            } else {
                allChats
            }

            val totalChats = chatsToScan.size
            UnifiedLog.i(TAG, "Starting catalog scan for $totalChats chats")

            trySend(
                TelegramCatalogEvent.ScanStarted(
                    chatCount = totalChats,
                    estimatedTotalMessages = null, // TDLib doesn't provide this efficiently
                ),
            )

            var scannedChats = 0
            var scannedMessages = 0L
            var discoveredItems = 0L

            for (chat in chatsToScan) {
                if (!isActive) {
                    UnifiedLog.i(TAG, "Scan cancelled at chat $scannedChats/$totalChats")
                    trySend(
                        TelegramCatalogEvent.ScanCancelled(
                            scannedChats = scannedChats,
                            scannedMessages = scannedMessages,
                        ),
                    )
                    return@channelFlow
                }

                UnifiedLog.d(TAG, "Scanning chat '${chat.title}' (id=${chat.chatId})")

                try {
                    val cursor = TelegramMessageCursor(
                        client = client,
                        chat = chat,
                        maxMessages = config.maxMessagesPerChat,
                        minMessageTimestampMs = config.minMessageTimestampMs,
                        pageSize = config.pageSize,
                    )

                    while (isActive && cursor.hasNext()) {
                        val batch = cursor.nextBatch()
                        if (batch.isEmpty()) break

                        for (mediaItem in batch) {
                            scannedMessages++

                            // Convert to RawMediaMetadata
                            val rawMetadata = mediaItem.toRawMediaMetadata()

                            val catalogItem = TelegramCatalogItem(
                                raw = rawMetadata,
                                chatId = chat.chatId,
                                messageId = mediaItem.messageId,
                                chatTitle = chat.title,
                            )

                            trySend(TelegramCatalogEvent.ItemDiscovered(catalogItem))
                            discoveredItems++

                            // Log progress periodically
                            if (scannedMessages % PROGRESS_LOG_INTERVAL == 0L) {
                                UnifiedLog.d(
                                    TAG,
                                    "Progress: $scannedMessages messages, $discoveredItems items",
                                )
                            }
                        }

                        // Emit progress event
                        trySend(
                            TelegramCatalogEvent.ScanProgress(
                                scannedChats = scannedChats,
                                totalChats = totalChats,
                                scannedMessages = scannedMessages,
                                discoveredItems = discoveredItems,
                            ),
                        )
                    }

                    scannedChats++
                } catch (ce: CancellationException) {
                    throw ce // Re-throw cancellation
                } catch (e: Exception) {
                    // Log error but continue with next chat
                    UnifiedLog.w(TAG, "Error scanning chat ${chat.chatId}: ${e.message}")
                }
            }

            val durationMs = System.currentTimeMillis() - startTime
            UnifiedLog.i(
                TAG,
                "Scan completed: $scannedChats chats, $scannedMessages messages, " +
                    "$discoveredItems items in ${durationMs}ms",
            )

            trySend(
                TelegramCatalogEvent.ScanCompleted(
                    scannedChats = scannedChats,
                    scannedMessages = scannedMessages,
                    discoveredItems = discoveredItems,
                    durationMs = durationMs,
                ),
            )
        } catch (ce: CancellationException) {
            UnifiedLog.i(TAG, "Scan cancelled by coroutine cancellation")
            throw ce
        } catch (t: Throwable) {
            UnifiedLog.e(TAG, "Catalog scan failed", t)
            trySend(
                TelegramCatalogEvent.ScanError(
                    reason = "unexpected_error",
                    message = t.message ?: "Unknown error",
                    throwable = t,
                ),
            )
        }
    }

    companion object {
        private const val TAG = "TelegramCatalogPipeline"
        private const val PROGRESS_LOG_INTERVAL = 1000L
    }
}

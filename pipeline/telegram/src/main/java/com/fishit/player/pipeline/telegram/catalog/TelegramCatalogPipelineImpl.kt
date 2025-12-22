package com.fishit.player.pipeline.telegram.catalog

import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.infra.transport.telegram.api.TdlibAuthState
import com.fishit.player.infra.transport.telegram.api.TelegramConnectionState
import com.fishit.player.pipeline.telegram.adapter.TelegramChatInfo
import com.fishit.player.pipeline.telegram.adapter.TelegramMediaUpdate
import com.fishit.player.pipeline.telegram.adapter.TelegramPipelineAdapter
import com.fishit.player.pipeline.telegram.model.toRawMediaMetadata
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Default implementation of [TelegramCatalogPipeline].
 *
 * Stateless producer that:
 * - Reads from TelegramPipelineAdapter (auth state, chats, messages)
 * - Converts TelegramMediaItem to RawMediaMetadata via toRawMediaMetadata()
 * - Emits TelegramCatalogEvent via cold Flow
 * - Performs no DB writes, caching, or UI work
 *
 * **Architecture Integration:**
 * - Uses TelegramPipelineAdapter (wraps TelegramTransportClient)
 * - Uses TelegramMediaItem.toRawMediaMetadata() for conversion
 * - Emits standardized catalog events for CatalogSync consumption
 *
 * **Error Handling:**
 * - Pre-flight checks for auth and connection state
 * - Graceful handling of per-chat failures (logs warning, continues)
 * - Cancellation-aware (respects coroutine scope)
 *
 * @param adapter TelegramPipelineAdapter for chat/message access
 */
class TelegramCatalogPipelineImpl
@Inject
constructor(
        private val adapter: TelegramPipelineAdapter,
) : TelegramCatalogPipeline {

    override fun scanCatalog(
            config: TelegramCatalogConfig,
    ): Flow<TelegramCatalogEvent> = channelFlow {
        val startTime = System.currentTimeMillis()

        try {
            // Pre-flight: Check auth state
            UnifiedLog.i(TAG) { "scanCatalog called - checking auth state..." }
            val auth = adapter.authState.first()
            UnifiedLog.i(TAG) { "Auth state: $auth (isReady=${auth is TdlibAuthState.Ready})" }
            
            if (auth !is TdlibAuthState.Ready) {
                UnifiedLog.w(TAG) { "BLOCKER: Cannot scan - auth state is $auth (expected TdlibAuthState.Ready)" }
                trySend(
                        TelegramCatalogEvent.ScanError(
                                reason = "unauthenticated",
                                message = "Telegram is not authenticated. Current state: $auth",
                        ),
                )
                return@channelFlow
            }

            // Pre-flight: Check connection state
            UnifiedLog.i(TAG) { "Auth OK - checking connection state..." }
            val conn = adapter.connectionState.first()
            UnifiedLog.i(TAG) { "Connection state: $conn (isConnected=${conn is TelegramConnectionState.Connected})" }
            
            if (conn !is TelegramConnectionState.Connected) {
                UnifiedLog.w(TAG) { "BLOCKER: Cannot scan - connection state is $conn (expected TelegramConnectionState.Connected)" }
                trySend(
                        TelegramCatalogEvent.ScanError(
                                reason = "not_connected",
                                message = "Telegram is not connected. Current state: $conn",
                        ),
                )
                return@channelFlow
            }
            
            UnifiedLog.i(TAG) { "Pre-flight checks passed - starting catalog scan" }

            // Get chats to scan
            val allChats = adapter.getChats(limit = 200)
            val chatsToScan =
                    if (config.chatIds != null) {
                        allChats.filter { it.chatId in config.chatIds }
                    } else {
                        allChats
                    }

            val totalChats = chatsToScan.size
            UnifiedLog.i(TAG) { "Starting catalog scan for $totalChats chats" }

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
                    UnifiedLog.i(TAG) { "Scan cancelled at chat $scannedChats/$totalChats" }
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
                    val cursor =
                            TelegramMessageCursor(
                                    adapter = adapter,
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

                            val catalogItem =
                                    TelegramCatalogItem(
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
        private const val TAG_LIVE = "TelegramLiveUpdates"
        private const val PROGRESS_LOG_INTERVAL = 1000L
        private const val LIVE_METRICS_LOG_INTERVAL = 100L
    }

    override fun liveMediaUpdates(
            config: TelegramLiveUpdatesConfig,
    ): Flow<TelegramCatalogEvent> = channelFlow {
        val liveStartTime = System.currentTimeMillis()
        UnifiedLog.i(TAG_LIVE, "Starting live media updates stream")

        val auth = adapter.authState.first()
        if (auth !is TdlibAuthState.Ready) {
            UnifiedLog.w(TAG_LIVE, "Pre-flight failed: auth_state=$auth")
            trySend(
                    TelegramCatalogEvent.ScanError(
                            reason = "unauthenticated",
                            message = "Telegram is not authenticated. Current state: $auth",
                    ),
            )
            return@channelFlow
        }

        val conn = adapter.connectionState.first()
        if (conn !is TelegramConnectionState.Connected) {
            UnifiedLog.w(TAG_LIVE, "Pre-flight failed: connection_state=$conn")
            trySend(
                    TelegramCatalogEvent.ScanError(
                            reason = "not_connected",
                            message = "Telegram is not connected. Current state: $conn",
                    ),
            )
            return@channelFlow
        }

        UnifiedLog.d(TAG_LIVE, "Pre-flight passed: auth=Ready, connection=Connected")

        // Metrics counters
        var liveMessagesReceived = 0L
        var liveMessagesEmitted = 0L
        var liveMessagesSuppressed = 0L
        var warmUpTriggered = 0L
        var warmUpItemsEmitted = 0L

        val chatCache = mutableMapOf<Long, TelegramChatInfo>()
        val initialChats =
                runCatching { adapter.getChats(limit = config.chatLookupLimit) }
                        .getOrDefault(emptyList())
        initialChats.forEach { chatCache[it.chatId] = it }
        UnifiedLog.i(TAG_LIVE, "Initial chat cache populated: ${initialChats.size} chats")

        val warmUpInProgress = mutableSetOf<Long>()
        val classifier = TelegramChatMediaClassifier()

        // Pre-seed classifier with initial chat samples
        val seedStartTime = System.currentTimeMillis()
        var seedSuccessCount = 0
        var seedFailCount = 0
        initialChats.forEach { chat ->
            runCatching {
                val sample =
                        adapter.fetchMessages(
                                chatId = chat.chatId,
                                limit = TelegramChatMediaClassifier.SAMPLE_SIZE,
                        )
                classifier.recordSample(chat.chatId, sample)
                seedSuccessCount++
            }
                    .onFailure { error ->
                        seedFailCount++
                        UnifiedLog.w(
                                TAG_LIVE,
                                "Classifier seed failed: chat_id=${chat.chatId}, error=${error.message}",
                        )
                    }
        }
        val seedDurationMs = System.currentTimeMillis() - seedStartTime
        UnifiedLog.i(
                TAG_LIVE,
                "Classifier seeding complete: success=$seedSuccessCount, failed=$seedFailCount, " +
                        "duration_ms=$seedDurationMs",
        )

        suspend fun emitDiscovered(update: TelegramMediaUpdate, chatTitle: String?) {
            val media = update.mediaItem
            val catalogItem =
                    TelegramCatalogItem(
                            raw = media.toRawMediaMetadata(),
                            chatId = media.chatId,
                            messageId = media.messageId,
                            chatTitle = chatTitle,
                    )
            send(TelegramCatalogEvent.ItemDiscovered(catalogItem))
            liveMessagesEmitted++
        }

        suspend fun warmUpIngest(chatId: Long) {
            val ingestStartTime = System.currentTimeMillis()
            var ingestItemCount = 0L

            val chatInfo =
                    chatCache[chatId]
                            ?: adapter.getChats(limit = config.chatLookupLimit)
                                    .firstOrNull { it.chatId == chatId }
                                    ?.also { chatCache[chatId] = it }
                                    ?: run {
                                UnifiedLog.w(TAG_LIVE, "Warm-up aborted: chat_id=$chatId not found")
                                return
                            }

            val cursor =
                    TelegramMessageCursor(
                            adapter = adapter,
                            chat = chatInfo,
                            maxMessages = config.warmUpIngestMessages,
                            minMessageTimestampMs = config.minMessageTimestampMs,
                            pageSize = config.pageSize,
                    )

            while (isActive && cursor.hasNext()) {
                val batch = cursor.nextBatch()
                for (mediaItem in batch) {
                    val catalogItem =
                            TelegramCatalogItem(
                                    raw = mediaItem.toRawMediaMetadata(),
                                    chatId = chatInfo.chatId,
                                    messageId = mediaItem.messageId,
                                    chatTitle = chatInfo.title,
                            )
                    send(TelegramCatalogEvent.ItemDiscovered(catalogItem))
                    ingestItemCount++
                    warmUpItemsEmitted++
                }
            }

            val ingestDurationMs = System.currentTimeMillis() - ingestStartTime
            UnifiedLog.d(
                    TAG_LIVE,
                    "Warm-up ingest complete: chat_id=$chatId, items=$ingestItemCount, " +
                            "duration_ms=$ingestDurationMs",
            )
        }

        classifier.onChatWarmUp = { chatId, newClass ->
            launch {
                if (!warmUpInProgress.add(chatId)) return@launch
                warmUpTriggered++
                try {
                    UnifiedLog.i(
                            TAG_LIVE,
                            "Warm-up triggered: chat_id=$chatId, classification=$newClass",
                    )
                    warmUpIngest(chatId)
                } catch (ce: CancellationException) {
                    throw ce
                } catch (t: Throwable) {
                    UnifiedLog.w(
                            TAG_LIVE,
                            "Warm-up failed: chat_id=$chatId, error=${t.message}",
                    )
                } finally {
                    warmUpInProgress.remove(chatId)
                }
            }
        }

        UnifiedLog.i(
                TAG_LIVE,
                "Live updates stream ready, init_time_ms=${System.currentTimeMillis() - liveStartTime}",
        )

        adapter.mediaUpdates.collect { update ->
            liveMessagesReceived++
            classifier.recordMessage(update.message)

            if (classifier.isSuppressed(update.message.chatId)) {
                liveMessagesSuppressed++
                return@collect
            }

            val chatTitle = chatCache[update.mediaItem.chatId]?.title
            emitDiscovered(update, chatTitle)

            // Log metrics periodically
            if (liveMessagesReceived % LIVE_METRICS_LOG_INTERVAL == 0L) {
                UnifiedLog.d(
                        TAG_LIVE,
                        "Live metrics: received=$liveMessagesReceived, emitted=$liveMessagesEmitted, " +
                                "suppressed=$liveMessagesSuppressed, warmups=$warmUpTriggered, " +
                                "warmup_items=$warmUpItemsEmitted",
                )
            }
        }
    }
}

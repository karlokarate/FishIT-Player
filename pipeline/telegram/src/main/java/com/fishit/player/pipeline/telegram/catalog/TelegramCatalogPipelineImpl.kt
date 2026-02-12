package com.fishit.player.pipeline.telegram.catalog

import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.infra.transport.telegram.api.TransportAuthState
import com.fishit.player.infra.transport.telegram.api.TelegramConnectionState
import com.fishit.player.pipeline.telegram.adapter.TelegramChatInfo
import com.fishit.player.pipeline.telegram.adapter.TelegramMediaUpdate
import com.fishit.player.pipeline.telegram.adapter.TelegramPipelineAdapter
import com.fishit.player.pipeline.telegram.model.toRawMediaMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

/**
 * Default implementation of [TelegramCatalogPipeline].
 *
 * **PLATINUM:** Parallel Chat Scanning
 * - Scans multiple chats concurrently (controlled by chatParallelism)
 * - Emits items immediately via channelFlow (no accumulation)
 * - Supports checkpoint resume via excludeChatIds
 * - ~3x faster than sequential scanning for typical accounts
 *
 * Stateless producer that:
 * - Reads from TelegramPipelineAdapter (auth state, chats, messages)
 * - Converts TelegramMediaItem to RawMediaMetadata via toRawMediaMetadata()
 * - Emits TelegramCatalogEvent via cold Flow
 * - Performs no DB writes, caching, or UI work
 *
 * **Architecture Integration:**
 * - Uses TelegramPipelineAdapter (wraps typed TelegramClient interfaces)
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
        override fun scanCatalog(config: TelegramCatalogConfig): Flow<TelegramCatalogEvent> =
            channelFlow {
                val startTime = System.currentTimeMillis()

                try {
                    // Pre-flight: Check auth state
                    UnifiedLog.i(TAG) { "scanCatalog called - checking auth state..." }
                    val auth = adapter.authState.first()
                    UnifiedLog.i(TAG) { "Auth state: $auth (isReady=${auth is TransportAuthState.Ready})" }

                    if (auth !is TransportAuthState.Ready) {
                        UnifiedLog.w(TAG) { "BLOCKER: Cannot scan - auth state is $auth (expected TransportAuthState.Ready)" }
                        send(
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
                        UnifiedLog.w(
                            TAG,
                        ) { "BLOCKER: Cannot scan - connection state is $conn (expected TelegramConnectionState.Connected)" }
                        send(
                            TelegramCatalogEvent.ScanError(
                                reason = "not_connected",
                                message = "Telegram is not connected. Current state: $conn",
                            ),
                        )
                        return@channelFlow
                    }

                    UnifiedLog.i(TAG) { "Pre-flight checks passed - starting PLATINUM parallel catalog scan" }

                    // Get chats to scan (limit=0 means all chats)
                    val allChats = adapter.getChats(limit = 0)

                    // Apply chatIds filter
                    val filteredChats =
                        if (config.chatIds != null) {
                            allChats.filter { it.chatId in config.chatIds }
                        } else {
                            allChats
                        }

                    // PLATINUM: Apply excludeChatIds filter for checkpoint resume
                    val chatsToScan =
                        if (config.excludeChatIds.isNotEmpty()) {
                            filteredChats.filter { it.chatId !in config.excludeChatIds }
                        } else {
                            filteredChats
                        }

                    val totalChats = filteredChats.size
                    val skippedChats = filteredChats.size - chatsToScan.size

                    UnifiedLog.i(TAG) {
                        "PLATINUM scan: $totalChats total chats, $skippedChats skipped, " +
                            "${chatsToScan.size} to process (parallelism=${config.chatParallelism})"
                    }

                    send(
                        TelegramCatalogEvent.ScanStarted(
                            chatCount = totalChats,
                            estimatedTotalMessages = null, // Telegram API doesn't provide this efficiently
                        ),
                    )

                    if (chatsToScan.isEmpty()) {
                        UnifiedLog.d(TAG) { "No chats to scan (all excluded or filtered)" }
                        send(
                            TelegramCatalogEvent.ScanCompleted(
                                scannedChats = 0,
                                scannedMessages = 0,
                                discoveredItems = 0,
                                durationMs = System.currentTimeMillis() - startTime,
                                newHighWaterMarks = emptyMap(),
                            ),
                        )
                        return@channelFlow
                    }

                    // Atomic counters for thread-safe updates
                    val scannedChats = AtomicInteger(0)
                    val scannedMessages = AtomicLong(0)
                    val discoveredItems = AtomicLong(0)

                    // Track high-water marks for checkpoint updates (synchronized)
                    val newHighWaterMarks = java.util.concurrent.ConcurrentHashMap<Long, Long>()
                    val isIncremental = config.isIncremental

                    if (isIncremental) {
                        UnifiedLog.i(TAG) { "Incremental scan: Using ${config.highWaterMarks?.size ?: 0} high-water marks" }
                    } else {
                        UnifiedLog.i(TAG) { "Full scan: No high-water marks (initial sync)" }
                    }

                    // ================================================================
                    // PLATINUM: Parallel Chat Scanning
                    // Scan multiple chats concurrently with semaphore limiting
                    // ================================================================
                    val semaphore = Semaphore(config.chatParallelism)

                    supervisorScope {
                        chatsToScan
                            .map { chat ->
                                async {
                                    semaphore.withPermit {
                                        if (!isActive) return@withPermit

                                        scanSingleChat(
                                            chat = chat,
                                            config = config,
                                            scannedMessages = scannedMessages,
                                            discoveredItems = discoveredItems,
                                            scannedChats = scannedChats,
                                            totalChats = chatsToScan.size,
                                            newHighWaterMarks = newHighWaterMarks,
                                            isIncremental = isIncremental,
                                        )
                                    }
                                }
                            }.awaitAll()
                    }

                    // Check if cancelled during parallel scan
                    if (!isActive) {
                        UnifiedLog.i(TAG) { "Scan cancelled during parallel processing" }
                        send(
                            TelegramCatalogEvent.ScanCancelled(
                                scannedChats = scannedChats.get(),
                                scannedMessages = scannedMessages.get(),
                                partialHighWaterMarks = newHighWaterMarks.toMap(),
                            ),
                        )
                        return@channelFlow
                    }

                    val durationMs = System.currentTimeMillis() - startTime
                    UnifiedLog.i(
                        TAG,
                        "PLATINUM scan completed: ${scannedChats.get()} chats, ${scannedMessages.get()} messages, " +
                            "${discoveredItems.get()} items in ${durationMs}ms (incremental=$isIncremental, parallel=${config.chatParallelism})",
                    )

                    send(
                        TelegramCatalogEvent.ScanCompleted(
                            scannedChats = scannedChats.get(),
                            scannedMessages = scannedMessages.get(),
                            discoveredItems = discoveredItems.get(),
                            durationMs = durationMs,
                            newHighWaterMarks = newHighWaterMarks.toMap(),
                        ),
                    )
                } catch (ce: CancellationException) {
                    UnifiedLog.i(TAG, "Scan cancelled by coroutine cancellation")
                    throw ce
                } catch (t: Throwable) {
                    UnifiedLog.e(TAG, "Catalog scan failed", t)
                    send(
                        TelegramCatalogEvent.ScanError(
                            reason = "unexpected_error",
                            message = t.message ?: "Unknown error",
                            throwable = t,
                        ),
                    )
                }
            }

        /**
         * PLATINUM: Scan a single chat and emit results.
         *
         * Called concurrently from multiple coroutines during parallel scanning.
         * Emits ChatScanComplete/ChatScanFailed events for checkpoint tracking.
         */
        private suspend fun kotlinx.coroutines.channels.SendChannel<TelegramCatalogEvent>.scanSingleChat(
            chat: TelegramChatInfo,
            config: TelegramCatalogConfig,
            scannedMessages: AtomicLong,
            discoveredItems: AtomicLong,
            scannedChats: AtomicInteger,
            totalChats: Int,
            newHighWaterMarks: java.util.concurrent.ConcurrentHashMap<Long, Long>,
            isIncremental: Boolean,
        ) {
            val chatHighWaterMark = config.getHighWaterMark(chat.chatId)
            UnifiedLog.d(TAG, "Scanning chat '${chat.title}' (id=${chat.chatId}, hwm=${chatHighWaterMark ?: "none"})")

            var chatItemCount = 0L
            var chatHighestSeen = 0L
            var batchCount = 0

            try {
                val cursor =
                    TelegramMessageCursor(
                        adapter = adapter,
                        chat = chat,
                        maxMessages = config.maxMessagesPerChat,
                        minMessageTimestampMs = config.minMessageTimestampMs,
                        pageSize = config.pageSize,
                        stopAtMessageId = chatHighWaterMark,
                    )

                while (cursor.hasNext()) {
                    val batch = cursor.nextBatch()
                    if (batch.isEmpty()) break
                    batchCount++

                    for (mediaItem in batch) {
                        // Check cancellation in inner loop for responsiveness
                        if (!kotlinx.coroutines.currentCoroutineContext().isActive) {
                            UnifiedLog.d(TAG, "Chat ${chat.chatId} scan cancelled mid-batch")
                            return
                        }

                        chatItemCount++
                        val totalItems = scannedMessages.incrementAndGet()

                        // Convert to RawMediaMetadata
                        val rawMetadata = mediaItem.toRawMediaMetadata()

                        val catalogItem =
                            TelegramCatalogItem(
                                raw = rawMetadata,
                                chatId = chat.chatId,
                                messageId = mediaItem.messageId,
                                chatTitle = chat.title,
                            )

                        send(TelegramCatalogEvent.ItemDiscovered(catalogItem))
                        val totalDiscovered = discoveredItems.incrementAndGet()

                        // Log progress periodically
                        if (totalItems % PROGRESS_LOG_INTERVAL == 0L) {
                            UnifiedLog.d(TAG, "Progress: $totalItems scanned, $totalDiscovered discovered")
                        }
                    }

                    // Debounce progress events: emit every 3rd batch to reduce channel flooding
                    if (batchCount % 3 == 0) {
                        send(
                            TelegramCatalogEvent.ScanProgress(
                                scannedChats = scannedChats.get(),
                                totalChats = totalChats,
                                scannedMessages = scannedMessages.get(),
                                discoveredItems = discoveredItems.get(),
                            ),
                        )
                    }
                }

                // Update high-water mark for this chat
                chatHighestSeen = cursor.highestSeenMessageId()
                if (chatHighestSeen > 0) {
                    val existingHwm = chatHighWaterMark ?: 0L
                    if (chatHighestSeen > existingHwm) {
                        newHighWaterMarks[chat.chatId] = chatHighestSeen
                    } else if (existingHwm > 0) {
                        newHighWaterMarks[chat.chatId] = existingHwm
                    }
                } else if (chatHighWaterMark != null) {
                    newHighWaterMarks[chat.chatId] = chatHighWaterMark
                }

                if (cursor.reachedHighWaterMark()) {
                    UnifiedLog.d(TAG, "Chat ${chat.chatId}: Incremental sync complete (reached HWM)")
                }

                scannedChats.incrementAndGet()

                // PLATINUM: Emit ChatScanComplete for checkpoint tracking
                // Note: chatItemCount represents media items (cursor already filters for media)
                send(
                    TelegramCatalogEvent.ChatScanComplete(
                        chatId = chat.chatId,
                        messageCount = cursor.scannedCount(), // Actual messages scanned (before filtering)
                        itemCount = chatItemCount, // Media items discovered
                        newHighWaterMark = if (chatHighestSeen > 0) chatHighestSeen else chatHighWaterMark,
                    ),
                )

                UnifiedLog.v(TAG) { "Chat ${chat.chatId} (${chat.title}) complete: ${cursor.scannedCount()} scanned, $chatItemCount items" }
            } catch (ce: CancellationException) {
                throw ce // Re-throw cancellation
            } catch (e: Exception) {
                // PLATINUM: Emit ChatScanFailed - this chat won't be in processedChatIds
                UnifiedLog.w(TAG, "Error scanning chat ${chat.chatId}: ${e.message}")
                send(
                    TelegramCatalogEvent.ChatScanFailed(
                        chatId = chat.chatId,
                        reason = e.message ?: "Unknown error",
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

        override fun liveMediaUpdates(config: TelegramLiveUpdatesConfig): Flow<TelegramCatalogEvent> =
            channelFlow {
                val liveStartTime = System.currentTimeMillis()
                UnifiedLog.i(TAG_LIVE, "Starting live media updates stream")

                val auth = adapter.authState.first()
                if (auth !is TransportAuthState.Ready) {
                    UnifiedLog.w(TAG_LIVE, "Pre-flight failed: auth_state=$auth")
                    send(
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
                    send(
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
                    }.onFailure { error ->
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

                suspend fun emitDiscovered(
                    update: TelegramMediaUpdate,
                    chatTitle: String?,
                ) {
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
                            ?: adapter
                                .getChats(limit = config.chatLookupLimit)
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

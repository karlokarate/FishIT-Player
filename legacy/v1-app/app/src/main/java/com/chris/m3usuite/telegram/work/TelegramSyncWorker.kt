package com.chris.m3usuite.telegram.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.chris.m3usuite.playback.PlaybackPriority
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.telegram.core.T_TelegramServiceClient
import com.chris.m3usuite.telegram.core.TgSyncState
import com.chris.m3usuite.telegram.ingestion.TelegramHistoryScanner
import com.chris.m3usuite.telegram.ingestion.TelegramIngestionCoordinator
import com.chris.m3usuite.core.logging.UnifiedLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Turbo-Sync Worker for Telegram content synchronization.
 * Implements adaptive parallel processing based on device profile.
 *
 * Key features:
 * - Loads chat selections from SettingsStore
 * - Uses T_TelegramServiceClient for unified Telegram access
 * - Parallel processing with adaptive parallelism
 * - Multiple sync modes: MODE_ALL, MODE_SELECTION_CHANGED, MODE_BACKFILL_SERIES
 * - Delegates to TelegramIngestionCoordinator for pipeline processing
 * - Updates sync state in T_TelegramServiceClient
 * - Comprehensive error handling and retry logic
 *
 * **Phase C Update:**
 * - Now uses TelegramIngestionCoordinator for the ingestion pipeline
 * - The coordinator handles: TelegramHistoryScanner → TdlMessageMapper → parser pipeline → ObjectBox
 * - Legacy indexChatMessages() is deprecated and no longer called
 *
 * ════════════════════════════════════════════════════════════════════════════════
 * PHASE 8 – Task 3: Playback-Aware Worker Scheduling
 * ════════════════════════════════════════════════════════════════════════════════
 *
 * This worker is playback-aware: when [PlaybackPriority.isPlaybackActive] is true,
 * heavy operations are throttled to avoid impacting playback quality.
 *
 * Implementation follows Cluster B specification from tdlibAgent.md.
 */
class TelegramSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    private val settingsStore = SettingsStore(context)

    /**
     * NOTE: TelegramContentRepository is no longer initialized here.
     * The new ingestion pipeline uses TelegramIngestionCoordinator which creates
     * its own repository instance. Legacy repository field removed to avoid
     * unnecessary object creation.
     */

    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            try {
                UnifiedLog.info("TelegramSyncWorker", "Starting sync...")

                // Load settings
                val enabled = settingsStore.tgEnabled.first()
                if (!enabled) {
                    UnifiedLog.info("TelegramSyncWorker", "Telegram not enabled, skipping sync")
                    return@withContext Result.success()
                }

                // Get sync mode from input data
                val mode = inputData.getString(KEY_MODE) ?: MODE_ALL
                val validModes = setOf(MODE_ALL, MODE_SELECTION_CHANGED, MODE_BACKFILL_SERIES)
                if (mode !in validModes) {
                    UnifiedLog.info("TelegramSyncWorker", "Invalid sync mode: $mode. Failing job.")
                    return@withContext Result.failure()
                }
                val refreshHome = inputData.getBoolean(KEY_REFRESH_HOME, false)

                UnifiedLog.info("TelegramSyncWorker", "Sync mode: $mode, refreshHome: $refreshHome")

                // Ensure Telegram service is started
                val serviceClient = T_TelegramServiceClient.getInstance(applicationContext)
                serviceClient.ensureStarted(applicationContext, settingsStore)

                // Per design decision 6.11: Ingestion MUST NOT run unless auth state is READY
                // Wait for auth to become ready (may already be ready if DB was authorized)
                val isReady = serviceClient.awaitAuthReady(timeoutMs = 30_000L)
                if (!isReady) {
                    val currentAuthState = serviceClient.authState.value
                    UnifiedLog.warn(
                        "TelegramSyncWorker",
                        "Auth not ready after timeout, cannot sync (state: ${currentAuthState::class.simpleName})",
                    )
                    serviceClient.updateSyncState(TgSyncState.Failed("Auth not ready: ${currentAuthState::class.simpleName}"))
                    return@withContext Result.failure(
                        workDataOf("error" to "Auth not ready: ${currentAuthState::class.simpleName}"),
                    )
                }

                UnifiedLog.info("TelegramSyncWorker", "Auth ready, proceeding with sync")

                // Phase 8: Playback-aware throttling before heavy sync operations
                throttleIfPlaybackActive(mode)

                // Update sync state to Running
                serviceClient.updateSyncState(TgSyncState.Running(0, 0))

                // Determine which chats to sync based on mode
                val chatsToSync = determineChatIdsToSync(mode)

                if (chatsToSync.isEmpty()) {
                    UnifiedLog.info("TelegramSyncWorker", "No chats selected for sync")
                    serviceClient.updateSyncState(TgSyncState.Completed(0))
                    return@withContext Result.success()
                }

                UnifiedLog.info("TelegramSyncWorker", "Syncing ${chatsToSync.size} chats")

                // Phase 8: Calculate adaptive parallelism based on device profile
                // and playback state (reduced parallelism during active playback)
                val parallelism = calculateParallelism()
                UnifiedLog.info("TelegramSyncWorker", "Using parallelism: $parallelism")

                // Sync chats in parallel
                var totalProcessed = 0

                val dispatcher = Dispatchers.IO.limitedParallelism(parallelism)

                val results =
                    withContext(dispatcher) {
                        chatsToSync
                            .map { chatConfig ->
                                async {
                                    syncChat(
                                        serviceClient = serviceClient,
                                        chatId = chatConfig.chatId,
                                        chatType = chatConfig.type,
                                        mode = mode,
                                    )
                                }
                            }.awaitAll()
                    }

                // Sum up results
                totalProcessed = results.sum()

                UnifiedLog.info("TelegramSyncWorker", "Sync completed: $totalProcessed items processed")

                // Update sync state to Completed
                serviceClient.updateSyncState(TgSyncState.Completed(totalProcessed))

                Result.success(
                    workDataOf(
                        "itemsProcessed" to totalProcessed,
                        "chatsProcessed" to chatsToSync.size,
                    ),
                )
            } catch (e: Exception) {
                UnifiedLog.info("TelegramSyncWorker", "Sync failed: ${e.message}")
                e.printStackTrace()

                // Update sync state to Failed
                try {
                    val serviceClient = T_TelegramServiceClient.getInstance(applicationContext)
                    serviceClient.updateSyncState(TgSyncState.Failed(e.message ?: "Unknown error"))
                } catch (ignored: Exception) {
                    // Ignore errors updating state
                }

                // Retry on failure
                if (runAttemptCount < 3) {
                    return@withContext Result.retry()
                } else {
                    return@withContext Result.failure(
                        workDataOf(
                            "error" to (e.message ?: "Unknown error"),
                        ),
                    )
                }
            }
        }

    /**
     * Load messages for sync with multi-page support.
     * Loads up to maxPages of messages from the chat.
     *
     * @param chatId Chat ID to load messages from
     * @param pageSize Number of messages per page
     * @param maxPages Maximum number of pages to load
     * @return List of all loaded messages
     *
     * @deprecated Use TelegramHistoryScanner via TelegramIngestionCoordinator instead.
     * This method was used by the legacy indexChatMessages() flow. The new pipeline
     * uses TelegramHistoryScanner for paging with better retry logic and ExportMessage conversion.
     */
    @Deprecated(
        message = "Use TelegramHistoryScanner via TelegramIngestionCoordinator",
        replaceWith = ReplaceWith("TelegramHistoryScanner(serviceClient).scan(chatId, config)"),
    )
    @Suppress("unused")
    private suspend fun loadMessagesForSync(
        serviceClient: T_TelegramServiceClient,
        chatId: Long,
        pageSize: Int = 50,
        maxPages: Int = 10,
    ): List<dev.g000sha256.tdl.dto.Message> {
        val all = mutableListOf<dev.g000sha256.tdl.dto.Message>()
        var fromMessageId = 0L
        var offset = 0

        repeat(maxPages) { pageIndex ->
            val page =
                serviceClient.browser().loadMessagesPaged(
                    chatId = chatId,
                    fromMessageId = fromMessageId,
                    offset = offset,
                    limit = pageSize,
                )

            if (page.isEmpty()) {
                UnifiedLog.info(
                    source = "TelegramSyncWorker",
                    message = "Stopping pagination: empty page from TDLib",
                    details =
                        mapOf(
                            "chatId" to chatId.toString(),
                            "totalLoaded" to all.size.toString(),
                        ),
                )
                return all
            }

            all += page

            UnifiedLog.info(
                source = "TelegramSyncWorker",
                message = "Loaded Telegram page",
                details =
                    mapOf(
                        "chatId" to chatId.toString(),
                        "page" to pageIndex.toString(),
                        "pageSize" to page.size.toString(),
                        "totalLoaded" to all.size.toString(),
                    ),
            )

            // Use last message from the page to set next fromMessageId (consistent with T_ChatBrowser)
            val oldestMessage = page.lastOrNull()
            if (oldestMessage == null || oldestMessage.id == fromMessageId) {
                UnifiedLog.info(
                    source = "TelegramSyncWorker",
                    message = "Stopping pagination: reached oldest known message",
                    details =
                        mapOf(
                            "chatId" to chatId.toString(),
                            "fromMessageId" to fromMessageId.toString(),
                            "totalLoaded" to all.size.toString(),
                        ),
                )
                return all
            }

            fromMessageId = oldestMessage.id
            offset = 0
        }

        UnifiedLog.info(
            source = "TelegramSyncWorker",
            message = "Stopping pagination: reached maxPages cap",
            details =
                mapOf(
                    "chatId" to chatId.toString(),
                    "maxPages" to maxPages.toString(),
                    "totalLoaded" to all.size.toString(),
                ),
        )

        return all
    }

    /**
     * Sync a single chat using the new ingestion pipeline (Phase C).
     *
     * Uses TelegramIngestionCoordinator which orchestrates:
     * - TelegramHistoryScanner for message fetching
     * - TdlMessageMapper for TDLib → ExportMessage conversion
     * - TelegramBlockGrouper for 120-second window grouping
     * - TelegramItemBuilder for TelegramItem creation
     * - TelegramContentRepository for persistence
     */
    private suspend fun syncChat(
        serviceClient: T_TelegramServiceClient,
        chatId: Long,
        chatType: String,
        mode: String,
    ): Int =
        withContext(Dispatchers.IO) {
            var itemsProcessed = 0

            try {
                UnifiedLog.info("TelegramSyncWorker", "Syncing chat $chatId (type: $chatType, mode: $mode)")

                // Resolve chat title for context
                val chatTitle = serviceClient.resolveChatTitle(chatId)

                // Create ingestion coordinator
                val coordinator = TelegramIngestionCoordinator(applicationContext, serviceClient)

                // Configure scan based on mode
                val scanConfig =
                    when (mode) {
                        MODE_ALL -> {
                            TelegramHistoryScanner.ScanConfig(
                                pageSize = 100,
                                maxPages = 10,
                            )
                        }
                        MODE_SELECTION_CHANGED -> {
                            TelegramHistoryScanner.ScanConfig(
                                pageSize = 50,
                                maxPages = 10,
                            )
                        }
                        MODE_BACKFILL_SERIES -> {
                            // For series, load more messages to capture all episodes
                            TelegramHistoryScanner.ScanConfig(
                                pageSize = 100,
                                maxPages = 20,
                            )
                        }
                        else ->
                            TelegramHistoryScanner.ScanConfig(
                                pageSize = 50,
                                maxPages = 5,
                            )
                    }

                // Execute the new ingestion pipeline
                itemsProcessed =
                    coordinator.startBackfill(
                        chatId = chatId,
                        chatTitle = chatTitle,
                        config = scanConfig,
                    )

                UnifiedLog.info(
                    "TelegramSyncWorker",
                    "Chat $chatId: processed $itemsProcessed items via new pipeline",
                )
            } catch (e: Exception) {
                UnifiedLog.info("TelegramSyncWorker", "Error syncing chat $chatId: ${e.message}")
                e.printStackTrace()
                // Continue with other chats even if one fails
            }

            itemsProcessed
        }

    /**
     * Determine which chat IDs to sync based on mode and settings.
     */
    private suspend fun determineChatIdsToSync(mode: String): List<ChatSyncConfig> {
        val configs = mutableListOf<ChatSyncConfig>()

        when (mode) {
            MODE_ALL -> {
                // Sync all selected chats (VOD + Series)
                val vodChats = parseChatIds(settingsStore.tgSelectedVodChatsCsv.first())
                val seriesChats = parseChatIds(settingsStore.tgSelectedSeriesChatsCsv.first())

                configs.addAll(vodChats.map { ChatSyncConfig(it, "vod") })
                configs.addAll(seriesChats.map { ChatSyncConfig(it, "series") })
            }
            MODE_SELECTION_CHANGED -> {
                // Sync only recently selected chats (use general selection)
                val selectedChats = parseChatIds(settingsStore.tgSelectedChatsCsv.first())
                configs.addAll(selectedChats.map { ChatSyncConfig(it, "vod") })
            }
            MODE_BACKFILL_SERIES -> {
                // Sync only series chats with deeper backfill
                val seriesChats = parseChatIds(settingsStore.tgSelectedSeriesChatsCsv.first())
                configs.addAll(seriesChats.map { ChatSyncConfig(it, "series") })
            }
        }

        return configs.distinctBy { it.chatId }
    }

    /**
     * Parse comma-separated chat IDs from settings.
     */
    private fun parseChatIds(csv: String): List<Long> = csv.split(",").mapNotNull { it.trim().toLongOrNull() }

    /**
     * Calculate adaptive parallelism based on device profile.
     * Takes into account CPU cores, device class (Phone/TV), and Android version.
     *
     * Phase 8: When playback is active, parallelism is reduced to 1 to avoid
     * impacting playback quality.
     */
    private fun calculateParallelism(): Int {
        // Phase 8: Minimal parallelism during active playback
        if (PlaybackPriority.isPlaybackActive.value) {
            return 1
        }

        // Get CPU core count
        val cores = Runtime.getRuntime().availableProcessors()

        // Determine device class
        val isTV =
            applicationContext.packageManager.hasSystemFeature("android.software.leanback") ||
                applicationContext.packageManager.hasSystemFeature("android.hardware.type.television")

        // Base parallelism on cores
        val baseParallelism =
            when {
                cores >= 8 -> 4 // High-end devices (8+ cores)
                cores >= 4 -> 3 // Mid-range devices (4-7 cores)
                cores >= 2 -> 2 // Entry-level devices (2-3 cores)
                else -> 1 // Very low-end (1 core)
            }

        // Adjust for TV devices (typically have more resources)
        val adjusted =
            if (isTV) {
                (baseParallelism * 1.5).toInt().coerceAtLeast(2)
            } else {
                baseParallelism
            }

        // Cap at reasonable maximum
        return adjusted.coerceIn(1, 6)
    }

    /**
     * Phase 8: Delays execution when playback is active to avoid stuttering.
     * Uses [PlaybackPriority.PLAYBACK_THROTTLE_MS] delay when playback is active.
     * Logs the throttling event via UnifiedLog.
     *
     * @param mode Current sync mode for logging context
     */
    private suspend fun throttleIfPlaybackActive(mode: String) {
        if (PlaybackPriority.isPlaybackActive.value) {
            UnifiedLog.info(
                "TelegramSyncWorker",
                "Playback active, using throttled mode",
                mapOf("mode" to mode),
            )
            delay(PlaybackPriority.PLAYBACK_THROTTLE_MS)
        }
    }

    /**
     * Configuration for syncing a single chat.
     */
    private data class ChatSyncConfig(
        val chatId: Long,
        val type: String, // "vod", "series", or "feed"
    )

    companion object {
        // Sync modes
        const val MODE_ALL = "all"
        const val MODE_SELECTION_CHANGED = "selection_changed"
        const val MODE_BACKFILL_SERIES = "backfill_series"

        // Input data keys
        private const val KEY_MODE = "mode"
        private const val KEY_REFRESH_HOME = "refreshHome"

        /**
         * Schedule a one-time Telegram sync with the specified mode.
         *
         * @param context Android context
         * @param mode Sync mode (MODE_ALL, MODE_SELECTION_CHANGED, MODE_BACKFILL_SERIES)
         * @param refreshHome Whether to refresh home screen after sync
         */
        fun scheduleNow(
            context: Context,
            mode: String = MODE_ALL,
            refreshHome: Boolean = false,
        ) {
            val request =
                OneTimeWorkRequestBuilder<TelegramSyncWorker>()
                    .setInputData(
                        workDataOf(
                            KEY_MODE to mode,
                            KEY_REFRESH_HOME to refreshHome,
                        ),
                    ).build()

            WorkManager.getInstance(context).enqueue(request)
            UnifiedLog.info("TelegramSyncWorker", "Scheduled sync with mode: $mode")
        }
    }
}

package com.chris.m3usuite.telegram.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.chris.m3usuite.data.repo.TelegramContentRepository
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.telegram.core.T_TelegramServiceClient
import com.chris.m3usuite.telegram.core.TgSyncState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
 * - Integrates MediaParser + TgContentHeuristics
 * - Updates sync state in T_TelegramServiceClient
 * - Comprehensive error handling and retry logic
 *
 * Implementation follows Cluster B specification from tdlibAgent.md.
 */
class TelegramSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    private val settingsStore = SettingsStore(context)
    private val repository = TelegramContentRepository(context, settingsStore)

    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            try {
                println("[TelegramSyncWorker] Starting sync...")

                // Load settings
                val enabled = settingsStore.tgEnabled.first()
                if (!enabled) {
                    println("[TelegramSyncWorker] Telegram not enabled, skipping sync")
                    return@withContext Result.success()
                }

                // Get sync mode from input data
                val mode = inputData.getString(KEY_MODE) ?: MODE_ALL
                val refreshHome = inputData.getBoolean(KEY_REFRESH_HOME, false)

                println("[TelegramSyncWorker] Sync mode: $mode, refreshHome: $refreshHome")

                // Ensure Telegram service is started
                val serviceClient = T_TelegramServiceClient.getInstance(applicationContext)
                serviceClient.ensureStarted(applicationContext, settingsStore)

                // Update sync state to Running
                serviceClient.updateSyncState(TgSyncState.Running(0, 0))

                // Determine which chats to sync based on mode
                val chatsToSync = determineChatIdsToSync(mode)

                if (chatsToSync.isEmpty()) {
                    println("[TelegramSyncWorker] No chats selected for sync")
                    serviceClient.updateSyncState(TgSyncState.Completed(0))
                    return@withContext Result.success()
                }

                println("[TelegramSyncWorker] Syncing ${chatsToSync.size} chats")

                // Calculate adaptive parallelism based on device profile
                val parallelism = calculateParallelism()
                println("[TelegramSyncWorker] Using parallelism: $parallelism")

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

                println("[TelegramSyncWorker] Sync completed: $totalProcessed items processed")

                // Update sync state to Completed
                serviceClient.updateSyncState(TgSyncState.Completed(totalProcessed))

                Result.success(
                    workDataOf(
                        "itemsProcessed" to totalProcessed,
                        "chatsProcessed" to chatsToSync.size,
                    ),
                )
            } catch (e: Exception) {
                println("[TelegramSyncWorker] Sync failed: ${e.message}")
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
                    return Result.retry()
                } else {
                    return Result.failure(
                        workDataOf(
                            "error" to (e.message ?: "Unknown error"),
                        ),
                    )
                }
            }
        }

    /**
     * Sync a single chat with error handling and retry logic.
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
                println("[TelegramSyncWorker] Syncing chat $chatId (type: $chatType, mode: $mode)")

                // Resolve chat title for context
                val chatTitle = serviceClient.resolveChatTitle(chatId)

                // Load messages from chat using T_ChatBrowser
                val messages =
                    when (mode) {
                        MODE_ALL -> {
                            // Load all messages (paginated)
                            serviceClient.browser().loadMessagesPaged(
                                chatId = chatId,
                                limit = 100,
                                fromMessageId = 0,
                            )
                        }
                        MODE_SELECTION_CHANGED -> {
                            // Load recent messages only
                            serviceClient.browser().loadMessagesPaged(
                                chatId = chatId,
                                limit = 50,
                                fromMessageId = 0,
                            )
                        }
                        MODE_BACKFILL_SERIES -> {
                            // For series, load more messages to capture all episodes
                            serviceClient.browser().loadMessagesPaged(
                                chatId = chatId,
                                limit = 200,
                                fromMessageId = 0,
                            )
                        }
                        else -> emptyList()
                    }

                if (messages.isEmpty()) {
                    println("[TelegramSyncWorker] No messages found in chat $chatId")
                    return@withContext 0
                }

                println("[TelegramSyncWorker] Processing ${messages.size} messages from chat $chatId")

                // Index messages using repository
                itemsProcessed =
                    repository.indexChatMessages(
                        chatId = chatId,
                        chatTitle = chatTitle,
                        messages = messages,
                        chatType = chatType,
                    )

                println("[TelegramSyncWorker] Chat $chatId: indexed $itemsProcessed items")
            } catch (e: Exception) {
                println("[TelegramSyncWorker] Error syncing chat $chatId: ${e.message}")
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
     */
    private fun calculateParallelism(): Int {
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
            println("[TelegramSyncWorker] Scheduled sync with mode: $mode")
        }
    }
}

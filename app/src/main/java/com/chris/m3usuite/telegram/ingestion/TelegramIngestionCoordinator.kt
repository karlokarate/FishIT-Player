package com.chris.m3usuite.telegram.ingestion

import android.content.Context
import com.chris.m3usuite.data.repo.TelegramContentRepository
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.telegram.core.T_TelegramServiceClient
import com.chris.m3usuite.telegram.domain.ChatScanState
import com.chris.m3usuite.telegram.domain.ScanStatus
import com.chris.m3usuite.telegram.domain.TelegramItem
import com.chris.m3usuite.telegram.logging.TelegramLogRepository
import com.chris.m3usuite.telegram.parser.ExportMessage
import com.chris.m3usuite.telegram.parser.TelegramBlockGrouper
import com.chris.m3usuite.telegram.parser.TelegramItemBuilder
import com.chris.m3usuite.telegram.repository.TelegramSyncStateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Orchestrates TDLib history scanning and parser pipeline for Telegram ingestion.
 *
 * Per TELEGRAM_PARSER_CONTRACT.md Phase C.3:
 * - Orchestrate history scans and state persistence for multiple chats
 * - Use TelegramHistoryScanner to fetch ExportMessage batches
 * - Use TelegramBlockGrouper and TelegramItemBuilder to turn them into TelegramItems
 * - Use TelegramContentRepository.upsertItems to persist them
 * - Use TelegramSyncStateRepository to persist and update ChatScanState
 *
 * Behavior:
 * - For each chatId:
 *   - Read current ChatScanState from TelegramSyncStateRepository
 *   - Start or resume scanning from lastScannedMessageId
 *   - After each batch: Group → build items → upsert into content repo
 *   - Update ChatScanState (lastScannedMessageId, hasMoreHistory, status)
 *
 * Auth constraint:
 * - Never runs without auth READY (assume TelegramSyncWorker handles gating)
 * - Errors update ChatScanState.status = ERROR with lastError set
 */
class TelegramIngestionCoordinator(
    private val context: Context,
    private val serviceClient: T_TelegramServiceClient,
) {
    companion object {
        private const val TAG = "TelegramIngestionCoordinator"
    }

    private val settingsStore = SettingsStore(context)
    private val contentRepository = TelegramContentRepository(context, settingsStore)
    private val syncStateRepository = TelegramSyncStateRepository(context)
    private val historyScanner = TelegramHistoryScanner(serviceClient)

    // Internal state tracking for active scans
    private val _scanStates = MutableStateFlow<List<ChatScanState>>(emptyList())

    /**
     * Observable scan states for all chats being processed.
     */
    val scanStates: StateFlow<List<ChatScanState>> = _scanStates.asStateFlow()

    /**
     * Observe scan states from persistent storage.
     */
    fun observeScanStates(): Flow<List<ChatScanState>> = syncStateRepository.observeScanStates()

    /**
     * Start or resume a backfill scan for a specific chat.
     *
     * @param chatId Chat ID to scan
     * @param chatTitle Chat title for metadata extraction
     * @param config Optional scan configuration
     * @return Number of items persisted
     */
    suspend fun startBackfill(
        chatId: Long,
        chatTitle: String? = null,
        config: TelegramHistoryScanner.ScanConfig = TelegramHistoryScanner.ScanConfig(),
    ): Int {
        TelegramLogRepository.info(TAG, "Starting backfill for chat $chatId")

        // Get or create scan state
        val existingState = syncStateRepository.getScanState(chatId)
        val initialState =
            existingState ?: ChatScanState(
                chatId = chatId,
                lastScannedMessageId = 0,
                hasMoreHistory = true,
                status = ScanStatus.IDLE,
            )

        // Update state to SCANNING
        val scanningState =
            initialState.copy(
                status = ScanStatus.SCANNING,
                updatedAt = System.currentTimeMillis(),
            )
        syncStateRepository.updateScanState(scanningState)
        updateInMemoryState(scanningState)

        return try {
            // Resolve chat title if not provided
            val resolvedTitle = chatTitle ?: serviceClient.resolveChatTitle(chatId)

            // Determine starting point
            val startFromMessageId =
                if (existingState != null && existingState.lastScannedMessageId > 0) {
                    existingState.lastScannedMessageId
                } else {
                    0L
                }

            val scanConfig = config.copy(fromMessageId = startFromMessageId)

            // Execute scan with batch processing
            val scanResult =
                historyScanner.scan(chatId, scanConfig) { batch, pageIndex ->
                    processBatch(batch, chatId, resolvedTitle)
                }

            // Update final state
            val completedState =
                scanningState.copy(
                    lastScannedMessageId = scanResult.oldestMessageId,
                    hasMoreHistory = scanResult.hasMoreHistory,
                    status = ScanStatus.IDLE,
                    lastError = null,
                    updatedAt = System.currentTimeMillis(),
                )
            syncStateRepository.updateScanState(completedState)
            updateInMemoryState(completedState)

            TelegramLogRepository.info(
                TAG,
                "Backfill complete for chat $chatId: ${scanResult.convertedCount} items",
            )

            scanResult.convertedCount
        } catch (e: Exception) {
            TelegramLogRepository.error(
                TAG,
                "Backfill failed for chat $chatId: ${e.message}",
            )

            // Update state to ERROR
            val errorState =
                scanningState.copy(
                    status = ScanStatus.ERROR,
                    lastError = e.message ?: "Unknown error",
                    updatedAt = System.currentTimeMillis(),
                )
            syncStateRepository.updateScanState(errorState)
            updateInMemoryState(errorState)

            0
        }
    }

    /**
     * Resume a backfill scan that was previously interrupted.
     * This is an alias for startBackfill with resume=true behavior.
     *
     * @param chatId Chat ID to resume scanning
     * @param chatTitle Optional chat title (will be resolved if not provided)
     * @return Number of items persisted
     */
    suspend fun resumeBackfill(
        chatId: Long,
        chatTitle: String? = null,
    ): Int {
        // Check if there's existing state with more history
        val existingState = syncStateRepository.getScanState(chatId)
        if (existingState != null && !existingState.hasMoreHistory) {
            TelegramLogRepository.info(TAG, "Chat $chatId has no more history to scan")
            return 0
        }

        return startBackfill(chatId, chatTitle)
    }

    /**
     * Pause a backfill scan in progress.
     * Currently just marks the state as IDLE; actual cancellation would require
     * coroutine scope management at the worker level.
     *
     * @param chatId Chat ID to pause
     */
    suspend fun pauseBackfill(chatId: Long) {
        val existingState = syncStateRepository.getScanState(chatId) ?: return

        if (existingState.status == ScanStatus.SCANNING) {
            val pausedState =
                existingState.copy(
                    status = ScanStatus.IDLE,
                    updatedAt = System.currentTimeMillis(),
                )
            syncStateRepository.updateScanState(pausedState)
            updateInMemoryState(pausedState)

            TelegramLogRepository.info(TAG, "Paused backfill for chat $chatId")
        }
    }

    /**
     * Get the current scan state for a chat.
     *
     * @param chatId Chat ID to query
     * @return ChatScanState or null if not found
     */
    suspend fun getScanState(chatId: Long): ChatScanState? = syncStateRepository.getScanState(chatId)

    /**
     * Clear scan state for a chat (useful for forcing a fresh full scan).
     *
     * @param chatId Chat ID to clear state for
     */
    suspend fun clearScanState(chatId: Long) {
        syncStateRepository.clearScanState(chatId)
        updateInMemoryState(null, chatId)
        TelegramLogRepository.info(TAG, "Cleared scan state for chat $chatId")
    }

    /**
     * Process a batch of ExportMessages through the parser pipeline.
     *
     * Pipeline:
     * 1. TelegramBlockGrouper.group() - Group messages by 120-second windows
     * 2. TelegramItemBuilder.build() - Convert blocks to TelegramItems
     * 3. TelegramContentRepository.upsertItems() - Persist to ObjectBox
     *
     * @param messages List of ExportMessages to process
     * @param chatId Chat ID for context
     * @param chatTitle Chat title for metadata extraction
     * @return Number of items persisted
     */
    private suspend fun processBatch(
        messages: List<ExportMessage>,
        chatId: Long,
        chatTitle: String?,
    ): Int {
        if (messages.isEmpty()) return 0

        // Step 1: Group messages into blocks
        val blocks = TelegramBlockGrouper.group(messages)

        TelegramLogRepository.debug(
            TAG,
            "Processing batch: ${messages.size} messages -> ${blocks.size} blocks",
        )

        // Step 2: Build TelegramItems from blocks
        val items = mutableListOf<TelegramItem>()
        for (block in blocks) {
            val item = TelegramItemBuilder.build(block, chatTitle)
            if (item != null) {
                items.add(item)
            }
        }

        TelegramLogRepository.debug(
            TAG,
            "Built ${items.size} items from ${blocks.size} blocks",
        )

        // Step 3: Persist items
        if (items.isNotEmpty()) {
            contentRepository.upsertItems(items)
            TelegramLogRepository.debug(TAG, "Persisted ${items.size} items for chat $chatId")
            // DEBUG: Log persistence target for UI wiring diagnostics
            android.util.Log.d(
                "telegram-ui",
                "TelegramIngestionCoordinator: Persisted ${items.size} TelegramItems to ObxTelegramItem (new table)",
            )
        }

        return items.size
    }

    /**
     * Update in-memory scan state for reactive UI.
     */
    private fun updateInMemoryState(
        state: ChatScanState?,
        chatIdToRemove: Long? = null,
    ) {
        val currentStates = _scanStates.value.toMutableList()

        if (chatIdToRemove != null) {
            currentStates.removeAll { it.chatId == chatIdToRemove }
        }

        if (state != null) {
            val existingIndex = currentStates.indexOfFirst { it.chatId == state.chatId }
            if (existingIndex >= 0) {
                currentStates[existingIndex] = state
            } else {
                currentStates.add(state)
            }
        }

        _scanStates.value = currentStates
    }
}

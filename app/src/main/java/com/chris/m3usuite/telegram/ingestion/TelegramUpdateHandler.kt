package com.chris.m3usuite.telegram.ingestion

import android.content.Context
import com.chris.m3usuite.data.repo.TelegramContentRepository
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.telegram.core.T_TelegramServiceClient
import com.chris.m3usuite.telegram.core.TgActivityEvent
import com.chris.m3usuite.telegram.domain.TelegramItem
import com.chris.m3usuite.telegram.logging.TelegramLogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Handles hot-path updates for real-time Telegram message processing.
 *
 * Per TELEGRAM_PARSER_CONTRACT.md Phase C.5 (optional):
 * - Listen for new/edited/deleted messages (online path)
 * - Map to ExportMessage and pass through mini pipeline
 * - Update affected TelegramItem in ObjectBox
 *
 * This is a stub implementation for Phase C. Full implementation
 * can be expanded in Phase D when UI integration is complete.
 *
 * Note: This handler processes individual messages incrementally,
 * unlike the batch processing in TelegramIngestionCoordinator.
 * For single messages, we create a block with just that message.
 */
class TelegramUpdateHandler(
    private val context: Context,
    private val serviceClient: T_TelegramServiceClient,
) {
    companion object {
        private const val TAG = "TelegramUpdateHandler"
    }

    private val settingsStore = SettingsStore(context)
    private val contentRepository = TelegramContentRepository(context, settingsStore)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var isStarted = false

    /**
     * Start listening for real-time updates.
     * This should be called after auth is READY.
     */
    fun start() {
        if (isStarted) {
            TelegramLogRepository.debug(TAG, "Already started")
            return
        }

        TelegramLogRepository.info(TAG, "Starting update handler")
        isStarted = true

        // Subscribe to activity events from service client
        serviceClient.activityEvents
            .onEach { event ->
                handleActivityEvent(event)
            }.launchIn(scope)
    }

    /**
     * Stop listening for updates.
     */
    fun stop() {
        if (!isStarted) return

        TelegramLogRepository.info(TAG, "Stopping update handler")
        isStarted = false
        // Note: scope cancellation would need to be handled if we want true stop
    }

    /**
     * Handle an activity event from the service client.
     */
    private suspend fun handleActivityEvent(event: TgActivityEvent) {
        when (event) {
            is TgActivityEvent.NewMessage -> {
                TelegramLogRepository.debug(
                    TAG,
                    "Received new message event: chat=${event.chatId}, message=${event.messageId}",
                )
                // In a full implementation, we would:
                // 1. Fetch the message from TDLib
                // 2. Convert to ExportMessage
                // 3. Find related messages in the same time window
                // 4. Build TelegramItem
                // 5. Upsert to repository
                //
                // For now, this is a stub that logs the event.
                // Full implementation deferred to Phase D.
            }
            is TgActivityEvent.NewDownload,
            is TgActivityEvent.DownloadComplete,
            -> {
                // These events are handled by the player/download layer
                // No action needed here
            }
            is TgActivityEvent.ParseComplete -> {
                TelegramLogRepository.debug(
                    TAG,
                    "Parse complete: chat=${event.chatId}, items=${event.itemsFound}",
                )
            }
        }
    }

    /**
     * Process a single new message through the pipeline.
     * This is the entry point for real-time message processing.
     *
     * @param chatId Chat ID
     * @param messageId Message ID
     * @param chatTitle Chat title for metadata
     * @return The created TelegramItem, or null if processing failed
     */
    suspend fun processNewMessage(
        chatId: Long,
        messageId: Long,
        chatTitle: String?,
    ): TelegramItem? {
        TelegramLogRepository.debug(TAG, "Processing new message: chat=$chatId, message=$messageId")

        // This is a stub implementation.
        // Full implementation would:
        // 1. Fetch message from TDLib via browser
        // 2. Look for adjacent messages in time window
        // 3. Build block and item
        // 4. Persist

        // For now, return null to indicate not implemented
        TelegramLogRepository.debug(TAG, "Full message processing not yet implemented")
        return null
    }

    /**
     * Delete a TelegramItem when its source message is deleted.
     *
     * @param chatId Chat ID
     * @param anchorMessageId The anchor message ID of the item to delete
     */
    suspend fun handleMessageDeleted(
        chatId: Long,
        anchorMessageId: Long,
    ) {
        TelegramLogRepository.debug(TAG, "Handling message deletion: chat=$chatId, anchor=$anchorMessageId")

        contentRepository.deleteItem(chatId, anchorMessageId)

        TelegramLogRepository.info(TAG, "Deleted item for message $anchorMessageId in chat $chatId")
    }
}

package com.chris.m3usuite.telegram.ingestion

import android.content.Context
import com.chris.m3usuite.data.repo.TelegramContentRepository
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.telegram.core.T_TelegramServiceClient
import com.chris.m3usuite.telegram.core.TgActivityEvent
import com.chris.m3usuite.telegram.domain.MessageBlock
import com.chris.m3usuite.telegram.domain.TelegramItem
import com.chris.m3usuite.telegram.logging.TelegramLogRepository
import com.chris.m3usuite.telegram.parser.ExportMessageFactory
import com.chris.m3usuite.telegram.parser.TelegramItemBuilder
import dev.g000sha256.tdl.dto.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Handles hot-path updates for real-time Telegram message processing.
 *
 * Per TELEGRAM_PARSER_CONTRACT.md Section 7.3:
 * - Listen for new/edited/deleted messages (online path)
 * - Map to ExportMessage using ExportMessageFactory (SAME logic as batch ingestion)
 * - Run through TelegramItemBuilder pipeline
 * - Update affected TelegramItem in ObjectBox via TelegramContentRepository
 *
 * Phase T3: Full implementation for live UpdateHandler → Library refresh.
 *
 * Key Design:
 * - Uses SAME parser logic as TelegramIngestionCoordinator (no forks)
 * - ExportMessageFactory.fromTdlMessage() for consistent message conversion
 * - TelegramItemBuilder.build() for item creation
 * - TelegramContentRepository.upsertItems() for persistence
 *
 * Phase 8 Contract Compliance:
 * - No lifecycle manipulation (no onPause/onResume hooks)
 * - No player control
 * - Only library/listing data is updated
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

        TelegramLogRepository.info(TAG, "Starting update handler (Phase T3)")
        isStarted = true

        // Subscribe to activity events from service client for logging
        serviceClient.activityEvents
            .onEach { event ->
                handleActivityEvent(event)
            }.launchIn(scope)

        // Phase T3: Subscribe to full message updates via T_ChatBrowser
        // This provides the complete TDLib Message for pipeline processing
        scope.launch {
            try {
                serviceClient
                    .browser()
                    .observeAllNewMessages()
                    .collect { message ->
                        processNewTdlMessage(message)
                    }
            } catch (e: Exception) {
                TelegramLogRepository.error(
                    TAG,
                    "Error in message updates flow",
                    exception = e,
                )
            }
        }
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
     * This is used for logging and coordination, not for data processing.
     */
    private suspend fun handleActivityEvent(event: TgActivityEvent) {
        when (event) {
            is TgActivityEvent.NewMessage -> {
                // Logging only - actual processing happens via observeAllNewMessages()
                TelegramLogRepository.debug(
                    TAG,
                    "Activity event: new message",
                    details =
                        mapOf(
                            "chatId" to event.chatId.toString(),
                            "messageId" to event.messageId.toString(),
                        ),
                )
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
     * Process a new TDLib message through the full parser pipeline.
     *
     * Phase T3: Uses SAME parser logic as batch ingestion:
     * 1. ExportMessageFactory.fromTdlMessage() - Convert TDLib Message → ExportMessage
     * 2. Create single-message MessageBlock
     * 3. TelegramItemBuilder.build() - Build TelegramItem
     * 4. TelegramContentRepository.upsertItems() - Persist to ObjectBox
     *
     * @param message TDLib Message from newMessageUpdates flow
     */
    private suspend fun processNewTdlMessage(message: Message) {
        val chatId = message.chatId
        val messageId = message.id

        TelegramLogRepository.debug(
            TAG,
            "Processing new TDLib message",
            details =
                mapOf(
                    "chatId" to chatId.toString(),
                    "messageId" to messageId.toString(),
                    "contentType" to message.content::class.simpleName.toString(),
                ),
        )

        // Step 1: Convert TDLib Message → ExportMessage using SAME factory as batch ingestion
        val exportMessage = ExportMessageFactory.fromTdlMessage(message)
        if (exportMessage == null) {
            TelegramLogRepository.debug(
                TAG,
                "Message skipped: ExportMessageFactory returned null",
                details =
                    mapOf(
                        "chatId" to chatId.toString(),
                        "messageId" to messageId.toString(),
                        "contentType" to message.content::class.simpleName.toString(),
                    ),
            )
            return
        }

        // Step 2: Resolve chat title for metadata extraction
        val chatTitle = resolveChatTitle(chatId)

        // Step 3: Create single-message MessageBlock
        // Note: In full implementation, we might group with adjacent messages
        // For real-time updates, we process single messages to avoid latency
        val block =
            MessageBlock(
                chatId = chatId,
                messages = listOf(exportMessage),
            )

        // Step 4: Build TelegramItem using SAME builder as batch ingestion
        val item = TelegramItemBuilder.build(block, chatTitle)
        if (item == null) {
            TelegramLogRepository.debug(
                TAG,
                "Message skipped: TelegramItemBuilder returned null",
                details =
                    mapOf(
                        "chatId" to chatId.toString(),
                        "messageId" to messageId.toString(),
                    ),
            )
            return
        }

        // Step 5: Persist to ObjectBox via TelegramContentRepository
        contentRepository.upsertItems(listOf(item))

        TelegramLogRepository.info(
            TAG,
            "Live update: TelegramItem created/updated",
            details =
                mapOf(
                    "chatId" to chatId.toString(),
                    "anchorMessageId" to item.anchorMessageId.toString(),
                    "type" to item.type.name,
                    "title" to (item.metadata.title ?: "untitled"),
                ),
        )

        // Emit activity event for UI notification
        serviceClient.emitActivityEvent(
            TgActivityEvent.ParseComplete(
                chatId = chatId,
                itemsFound = 1,
            ),
        )
    }

    /**
     * Resolve chat title from TDLib via browser.
     */
    private suspend fun resolveChatTitle(chatId: Long): String? =
        try {
            serviceClient.resolveChatTitle(chatId).takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            TelegramLogRepository.debug(
                TAG,
                "Failed to resolve chat title",
                details = mapOf("chatId" to chatId.toString(), "error" to (e.message ?: "unknown")),
            )
            null
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

        // Fetch the message from TDLib via browser
        val messages = serviceClient.browser().loadMessagesPaged(chatId, messageId, offset = 0, limit = 1)
        val message = messages.firstOrNull { it.id == messageId }

        if (message == null) {
            TelegramLogRepository.warn(
                TAG,
                "Message not found",
                details = mapOf("chatId" to chatId.toString(), "messageId" to messageId.toString()),
            )
            return null
        }

        // Convert to ExportMessage
        val exportMessage = ExportMessageFactory.fromTdlMessage(message) ?: return null

        // Build item
        val block = MessageBlock(chatId = chatId, messages = listOf(exportMessage))
        val item = TelegramItemBuilder.build(block, chatTitle) ?: return null

        // Persist
        contentRepository.upsertItems(listOf(item))

        TelegramLogRepository.info(
            TAG,
            "Processed new message into TelegramItem",
            details =
                mapOf(
                    "chatId" to chatId.toString(),
                    "anchorMessageId" to item.anchorMessageId.toString(),
                    "type" to item.type.name,
                ),
        )

        return item
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

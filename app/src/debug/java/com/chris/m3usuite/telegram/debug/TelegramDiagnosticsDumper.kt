package com.chris.m3usuite.telegram.debug

import android.content.Context
import android.util.Log
import com.chris.m3usuite.data.obx.ObxStore
import com.chris.m3usuite.data.obx.ObxTelegramItem
import com.chris.m3usuite.data.obx.ObxTelegramItem_
import com.chris.m3usuite.data.obx.ObxTelegramMessage
import com.chris.m3usuite.data.obx.ObxTelegramMessage_
import com.chris.m3usuite.telegram.domain.TelegramItemType
import com.chris.m3usuite.telegram.domain.toDomain
import com.chris.m3usuite.telegram.logging.TelegramLogRepository
import io.objectbox.kotlin.boxFor
import io.objectbox.kotlin.query

/**
 * Debug-only helper for dumping TelegramItem data from ObjectBox.
 *
 * This tool queries both the new ObxTelegramItem table (new parser pipeline)
 * and the legacy ObxTelegramMessage table (old pipeline / UI reads from this).
 *
 * IMPORTANT FINDING:
 * The UI (LibraryScreen) reads from ObxTelegramMessage (legacy), but
 * TelegramIngestionCoordinator/TelegramItemBuilder write to ObxTelegramItem (new).
 * This means items from the new pipeline won't show in the UI!
 *
 * Usage (in debug builds only):
 * ```kotlin
 * val dumper = TelegramDiagnosticsDumper(context)
 * dumper.dumpItemsForChat(chatId = -1001589507635L)
 * dumper.dumpAllProblematicChats()
 * dumper.dumpItemTypeSummary()
 * dumper.dumpLegacyMessages(chatId) // Check what the UI actually sees
 * ```
 *
 * NOT FOR PRODUCTION USE - purely a diagnostic tool.
 */
class TelegramDiagnosticsDumper(
    private val context: Context,
) {
    companion object {
        private const val TAG = "TgDiagnosticsDump"

        /** Maximum characters to display for truncated remote IDs */
        private const val REMOTE_ID_PREVIEW_LENGTH = 30
    }

    private val obxStore by lazy { ObxStore.get(context) }
    private val itemBox by lazy { obxStore.boxFor<ObxTelegramItem>() }
    private val legacyMessageBox by lazy { obxStore.boxFor<ObxTelegramMessage>() }

    /**
     * Dump all TelegramItems for a specific chat.
     *
     * @param chatId Chat ID to query
     */
    fun dumpItemsForChat(chatId: Long) {
        logHeader("DUMP ITEMS FOR CHAT", chatId)

        val items =
            itemBox
                .query {
                    equal(ObxTelegramItem_.chatId, chatId)
                    orderDesc(ObxTelegramItem_.createdAtUtc)
                }.find()

        log("Total items in ObjectBox for chat $chatId: ${items.size}")
        log("")

        if (items.isEmpty()) {
            log("WARNING: No items found for this chat!")
            log("Possible causes:")
            log("  - Ingestion never ran for this chat")
            log("  - TDLib returned no messages")
            log("  - All messages were filtered out by TelegramItemBuilder")
            log("  - Parser pipeline produced null items for all blocks")
        } else {
            // Group by type
            val byType = items.groupBy { it.itemType }
            log("Items by type:")
            byType.forEach { (type, typeItems) ->
                log("  $type: ${typeItems.size}")
            }
            log("")

            // Log each item
            items.forEachIndexed { index, obxItem ->
                logItemDetails(index, obxItem)
            }
        }

        logFooter()
    }

    /**
     * Dump items for all problematic chats.
     */
    fun dumpAllProblematicChats() {
        logHeader("DUMP ALL PROBLEMATIC CHATS", 0L)

        TelegramIngestionDebugHelper.PROBLEMATIC_CHAT_IDS.forEach { chatId ->
            log("----------------------------------------")
            dumpItemsForChat(chatId)
            log("")
        }

        logFooter()
    }

    /**
     * Dump a summary of all item types in the database.
     */
    fun dumpItemTypeSummary() {
        logHeader("ITEM TYPE SUMMARY (ALL CHATS)", 0L)

        val allItems = itemBox.all

        log("Total TelegramItems in database: ${allItems.size}")
        log("")

        // Group by type
        val byType = allItems.groupBy { it.itemType }
        log("Distribution by type:")
        byType.forEach { (type, items) ->
            log("  $type: ${items.size}")
        }
        log("")

        // Group by chat
        val byChat = allItems.groupBy { it.chatId }
        log("Distribution by chat:")
        byChat.forEach { (chatId, items) ->
            val types = items.groupBy { obxItem -> obxItem.itemType }.mapValues { entry -> entry.value.size }
            log("  Chat $chatId: ${items.size} items $types")
        }
        log("")

        // Check for items with missing posterRef
        val withPoster = allItems.count { !it.posterRemoteId.isNullOrBlank() }
        val withBackdrop = allItems.count { !it.backdropRemoteId.isNullOrBlank() }
        val withVideoRef = allItems.count { !it.videoRemoteId.isNullOrBlank() }
        val withDocRef = allItems.count { !it.documentRemoteId.isNullOrBlank() }

        log("Media reference coverage:")
        log("  With posterRef: $withPoster / ${allItems.size}")
        log("  With backdropRef: $withBackdrop / ${allItems.size}")
        log("  With videoRef: $withVideoRef / ${allItems.size}")
        log("  With documentRef: $withDocRef / ${allItems.size}")

        logFooter()
    }

    /**
     * Dump items that would be visible to the UI (excluding certain types).
     */
    fun dumpUiVisibleItems() {
        logHeader("UI-VISIBLE ITEMS ANALYSIS", 0L)

        val allItems = itemBox.all.map { it.toDomain() }

        // UI typically shows MOVIE, SERIES_EPISODE, CLIP
        // May exclude POSTER_ONLY, RAR_ITEM, AUDIOBOOK
        val uiVisibleTypes =
            setOf(
                TelegramItemType.MOVIE,
                TelegramItemType.SERIES_EPISODE,
                TelegramItemType.CLIP,
            )

        val visibleItems = allItems.filter { it.type in uiVisibleTypes }
        val hiddenItems = allItems.filter { it.type !in uiVisibleTypes }

        log("Total items: ${allItems.size}")
        log("UI-visible items: ${visibleItems.size}")
        log("Hidden items: ${hiddenItems.size}")
        log("")

        log("UI-visible breakdown:")
        visibleItems.groupBy { it.type }.forEach { (type, items) ->
            log("  $type: ${items.size}")
        }
        log("")

        log("Hidden items breakdown:")
        hiddenItems.groupBy { it.type }.forEach { (type, items) ->
            log("  $type: ${items.size}")
        }
        log("")

        // Check problematic chats specifically
        log("Problematic chats visibility:")
        TelegramIngestionDebugHelper.PROBLEMATIC_CHAT_IDS.forEach { chatId ->
            val chatItems = allItems.filter { it.chatId == chatId }
            val chatVisible = chatItems.filter { it.type in uiVisibleTypes }
            log("  Chat $chatId: ${chatVisible.size} visible / ${chatItems.size} total")
            if (chatItems.isNotEmpty()) {
                chatItems.groupBy { it.type }.forEach { (type, items) ->
                    log("    $type: ${items.size}")
                }
            }
        }

        logFooter()
    }

    /**
     * Log detailed information about a single ObxTelegramItem.
     */
    private fun logItemDetails(
        index: Int,
        item: ObxTelegramItem,
    ) {
        log("--- Item #$index ---")
        log("  ObxId: ${item.id}")
        log("  chatId: ${item.chatId}")
        log("  anchorMessageId: ${item.anchorMessageId}")
        log("  itemType: ${item.itemType}")
        log("  createdAtUtc: ${item.createdAtUtc}")
        log("")
        log("  Metadata:")
        log("    title: ${item.title}")
        log("    originalTitle: ${item.originalTitle}")
        log("    year: ${item.year}")
        log("    lengthMinutes: ${item.lengthMinutes}")
        log("    fsk: ${item.fsk}")
        log("    isAdult: ${item.isAdult}")
        log("    genres: ${item.genresJson}")
        log("")
        log("  References:")
        log("    videoRef: ${formatRemoteIdRef(item.videoRemoteId)}")
        log("    documentRef: ${formatRemoteIdRef(item.documentRemoteId)}")
        log("    posterRef: ${formatRemoteIdRef(item.posterRemoteId)}")
        log("    backdropRef: ${formatRemoteIdRef(item.backdropRemoteId)}")
        log("    textMessageId: ${item.textMessageId}")
        log("    photoMessageId: ${item.photoMessageId}")
        log("")
    }

    /**
     * Format a remote ID reference for logging.
     */
    private fun formatRemoteIdRef(remoteId: String?): String =
        if (remoteId.isNullOrBlank()) {
            "null"
        } else {
            "present (remoteId=${remoteId.take(REMOTE_ID_PREVIEW_LENGTH)}...)"
        }

    // =========================================================================
    // Legacy ObxTelegramMessage Diagnostics (what the UI actually reads!)
    // =========================================================================

    /**
     * Dump legacy ObxTelegramMessage entries for a specific chat.
     * This is what the UI (LibraryScreen) actually reads via getTelegramVodByChat().
     *
     * CRITICAL: If this is empty but dumpItemsForChat() shows items,
     * it means the new parser pipeline is writing to ObxTelegramItem but
     * the UI is reading from ObxTelegramMessage (legacy).
     *
     * @param chatId Chat ID to query
     */
    fun dumpLegacyMessages(chatId: Long) {
        logHeader("DUMP LEGACY MESSAGES (UI source)", chatId)

        val messages =
            legacyMessageBox
                .query {
                    equal(ObxTelegramMessage_.chatId, chatId)
                    orderDesc(ObxTelegramMessage_.date)
                }.find()

        log("Total LEGACY messages in ObxTelegramMessage for chat $chatId: ${messages.size}")
        log("")

        if (messages.isEmpty()) {
            log("WARNING: No LEGACY messages found for this chat!")
            log("This explains why UI shows nothing - UI reads from ObxTelegramMessage.")
            log("")
            log("The new parser pipeline writes to ObxTelegramItem (separate table).")
            log("Run dumpItemsForChat($chatId) to check new pipeline data.")
        } else {
            log("Messages found (showing first 10):")
            messages.take(10).forEachIndexed { index, msg ->
                log("--- Legacy Message #$index ---")
                log("  messageId: ${msg.messageId}")
                log("  fileId: ${msg.fileId}")
                log("  caption: ${msg.caption?.take(50)}")
                log("  title: ${msg.title}")
                log("  isSeries: ${msg.isSeries}")
                log("  seriesName: ${msg.seriesName}")
                log("  durationSecs: ${msg.durationSecs}")
                log("")
            }
        }

        logFooter()
    }

    /**
     * Compare both tables for a chat to diagnose pipeline mismatches.
     */
    fun compareNewVsLegacy(chatId: Long) {
        logHeader("COMPARE NEW vs LEGACY TABLES", chatId)

        val newItems =
            itemBox
                .query {
                    equal(ObxTelegramItem_.chatId, chatId)
                }.find()

        val legacyMessages =
            legacyMessageBox
                .query {
                    equal(ObxTelegramMessage_.chatId, chatId)
                }.find()

        log("Chat $chatId:")
        log("  NEW    (ObxTelegramItem):    ${newItems.size} items")
        log("  LEGACY (ObxTelegramMessage): ${legacyMessages.size} messages")
        log("")

        if (newItems.isNotEmpty() && legacyMessages.isEmpty()) {
            log("⚠️  DIAGNOSIS: Pipeline/UI Mismatch!")
            log("   New parser wrote ${newItems.size} items to ObxTelegramItem,")
            log("   but UI reads from ObxTelegramMessage (which is empty).")
            log("")
            log("   SOLUTION: Either:")
            log("   1. Update UI to read from ObxTelegramItem (new pipeline)")
            log("   2. Or use legacy indexChatMessages() for this chat")
        } else if (newItems.isEmpty() && legacyMessages.isEmpty()) {
            log("⚠️  DIAGNOSIS: Both tables empty!")
            log("   No ingestion has run for this chat, or messages were filtered out.")
        } else if (newItems.isEmpty() && legacyMessages.isNotEmpty()) {
            log("✓ Using LEGACY pipeline only (no new parser items)")
            log("  UI should show content from ObxTelegramMessage.")
        } else {
            log("✓ Both pipelines have data")
            log("  Check if UI is connected to correct data source.")
        }

        logFooter()
    }

    /**
     * Full diagnostic for all problematic chats comparing both tables.
     */
    fun diagnosePipelineMismatch() {
        logHeader("FULL PIPELINE MISMATCH DIAGNOSIS", 0L)

        val allNewItems = itemBox.all
        val allLegacyMsgs = legacyMessageBox.all

        log("GLOBAL COUNTS:")
        log("  NEW    (ObxTelegramItem):    ${allNewItems.size} total items")
        log("  LEGACY (ObxTelegramMessage): ${allLegacyMsgs.size} total messages")
        log("")

        log("PER-CHAT COMPARISON (problematic chats):")
        TelegramIngestionDebugHelper.PROBLEMATIC_CHAT_IDS.forEach { chatId ->
            val newCount = allNewItems.count { it.chatId == chatId }
            val legacyCount = allLegacyMsgs.count { it.chatId == chatId }
            val status =
                when {
                    newCount > 0 && legacyCount == 0 -> "⚠️ MISMATCH"
                    newCount == 0 && legacyCount == 0 -> "❌ EMPTY"
                    newCount == 0 && legacyCount > 0 -> "📱 LEGACY ONLY"
                    else -> "✓ BOTH"
                }
            log("  Chat $chatId: NEW=$newCount, LEGACY=$legacyCount  $status")
        }

        logFooter()
    }

    private fun logHeader(
        title: String,
        chatId: Long,
    ) {
        log("")
        log("╔════════════════════════════════════════════════════════════════╗")
        log("║ $title")
        if (chatId != 0L) {
            log("║ Chat ID: $chatId")
        }
        log("╚════════════════════════════════════════════════════════════════╝")
        log("")
    }

    private fun logFooter() {
        log("")
        log("════════════════════════════════════════════════════════════════")
        log("")
    }

    private fun log(message: String) {
        Log.d(TAG, message)
        TelegramLogRepository.debug(TAG, message)
    }
}

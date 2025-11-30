package com.chris.m3usuite.telegram.debug

import android.content.Context
import android.util.Log
import com.chris.m3usuite.data.obx.ObxStore
import com.chris.m3usuite.data.obx.ObxTelegramItem
import com.chris.m3usuite.data.obx.ObxTelegramItem_
import com.chris.m3usuite.telegram.domain.TelegramItemType
import com.chris.m3usuite.telegram.domain.toDomain
import com.chris.m3usuite.telegram.logging.TelegramLogRepository
import io.objectbox.kotlin.boxFor
import io.objectbox.kotlin.query

/**
 * Debug-only helper for dumping TelegramItem data from ObjectBox.
 *
 * This tool queries the ObxTelegramItem table and logs detailed information
 * about what has been persisted for specific chats.
 *
 * Usage (in debug builds only):
 * ```kotlin
 * val dumper = TelegramDiagnosticsDumper(context)
 * dumper.dumpItemsForChat(chatId = -1001589507635L)
 * dumper.dumpAllProblematicChats()
 * dumper.dumpItemTypeSummary()
 * ```
 *
 * NOT FOR PRODUCTION USE - purely a diagnostic tool.
 */
class TelegramDiagnosticsDumper(
    private val context: Context,
) {
    companion object {
        private const val TAG = "TgDiagnosticsDump"
    }

    private val obxStore by lazy { ObxStore.get(context) }
    private val itemBox by lazy { obxStore.boxFor<ObxTelegramItem>() }

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
        log("    videoRef: ${if (item.videoRemoteId.isNullOrBlank()) "null" else "present (remoteId=${item.videoRemoteId?.take(30)}...)"}")
        log(
            "    documentRef: ${if (item.documentRemoteId.isNullOrBlank()) {
                "null"
            } else {
                "present (remoteId=${item.documentRemoteId?.take(
                    30,
                )}...)"
            }}",
        )
        log(
            "    posterRef: ${if (item.posterRemoteId.isNullOrBlank()) {
                "null"
            } else {
                "present (remoteId=${item.posterRemoteId?.take(
                    30,
                )}...)"
            }}",
        )
        log(
            "    backdropRef: ${if (item.backdropRemoteId.isNullOrBlank()) {
                "null"
            } else {
                "present (remoteId=${item.backdropRemoteId?.take(
                    30,
                )}...)"
            }}",
        )
        log("    textMessageId: ${item.textMessageId}")
        log("    photoMessageId: ${item.photoMessageId}")
        log("")
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

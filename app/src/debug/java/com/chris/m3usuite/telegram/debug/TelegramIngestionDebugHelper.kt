package com.chris.m3usuite.telegram.debug

import android.content.Context
import android.util.Log
import com.chris.m3usuite.telegram.core.T_TelegramServiceClient
import com.chris.m3usuite.telegram.logging.TelegramLogRepository
import dev.g000sha256.tdl.dto.Message
import dev.g000sha256.tdl.dto.MessageAudio
import dev.g000sha256.tdl.dto.MessageDocument
import dev.g000sha256.tdl.dto.MessagePhoto
import dev.g000sha256.tdl.dto.MessageText
import dev.g000sha256.tdl.dto.MessageVideo

/**
 * Debug-only helper for diagnosing Telegram ingestion issues.
 *
 * This tool performs a single getChatHistory call and logs detailed TDLib DTOs
 * to understand what TDLib is actually returning for specific chats.
 *
 * Usage (in debug builds only):
 * ```kotlin
 * val helper = TelegramIngestionDebugHelper(context)
 * helper.diagnoseChatHistory(chatId = -1001589507635L)
 * ```
 *
 * NOT FOR PRODUCTION USE - purely a diagnostic tool.
 */
class TelegramIngestionDebugHelper(
    private val context: Context,
) {
    companion object {
        private const val TAG = "TgIngestionDebug"

        /**
         * Chat IDs that are known to be problematic from logs.
         * These will get extra verbose logging.
         */
        val PROBLEMATIC_CHAT_IDS =
            setOf(
                -1001589507635L,
                -1001650042012L,
                -1001452246125L,
                -1001309881692L,
                -1001545742878L,
            )
    }

    private val serviceClient: T_TelegramServiceClient by lazy {
        T_TelegramServiceClient.getInstance(context)
    }

    /**
     * Diagnose a chat's history by fetching messages and logging detailed DTO info.
     *
     * @param chatId Chat ID to diagnose
     * @param fromMessageId Starting message ID (0 for most recent)
     * @param limit Number of messages to fetch (default 50)
     * @param onlyLocal Whether to use only locally cached messages
     */
    suspend fun diagnoseChatHistory(
        chatId: Long,
        fromMessageId: Long = 0L,
        limit: Int = 50,
        onlyLocal: Boolean = false,
    ) {
        logHeader("DIAGNOSE CHAT HISTORY", chatId)

        try {
            val browser = serviceClient.browser()

            // Get chat info first
            val chat = browser.getChat(chatId, useCache = false)
            if (chat != null) {
                log("Chat Info:")
                log("  Title: ${chat.title}")
                log("  Type: ${chat.type::class.simpleName}")
                log("  Last Message ID: ${chat.lastMessage?.id}")
                log("  Unread Count: ${chat.unreadCount}")
            } else {
                log("WARNING: Could not retrieve chat info for $chatId")
            }

            // Fetch messages
            log("")
            log("Fetching messages: fromMessageId=$fromMessageId, offset=0, limit=$limit, onlyLocal=$onlyLocal")

            val messages =
                browser.loadMessagesPaged(
                    chatId = chatId,
                    fromMessageId = fromMessageId,
                    offset = 0,
                    limit = limit,
                )

            log("")
            log("=== RESULTS ===")
            log("Total messages returned: ${messages.size}")
            log("")

            if (messages.isEmpty()) {
                log("WARNING: TDLib returned 0 messages!")
                log("Possible causes:")
                log("  - Chat not joined or permissions issue")
                log("  - onlyLocal=true but no cached messages")
                log("  - API rate limit or timeout")
                log("  - Chat is empty")
            } else {
                // Group by message type
                val byType = messages.groupBy { it.content::class.simpleName }
                log("Messages by type:")
                byType.forEach { (type, msgs) ->
                    log("  $type: ${msgs.size}")
                }
                log("")

                // Log each message in detail
                messages.forEachIndexed { index, message ->
                    logMessageDetails(index, message)
                }
            }

            logFooter()
        } catch (e: Exception) {
            log("ERROR: ${e::class.simpleName}: ${e.message}")
            e.printStackTrace()
            logFooter()
        }
    }

    /**
     * Diagnose all problematic chats at once.
     */
    suspend fun diagnoseAllProblematicChats() {
        logHeader("DIAGNOSE ALL PROBLEMATIC CHATS", 0L)

        PROBLEMATIC_CHAT_IDS.forEach { chatId ->
            log("----------------------------------------")
            diagnoseChatHistory(chatId)
            log("")
        }

        logFooter()
    }

    /**
     * Log detailed information about a single message.
     */
    private fun logMessageDetails(
        index: Int,
        message: Message,
    ) {
        log("--- Message #$index ---")
        log("  ID: ${message.id}")
        log("  Date: ${message.date}")
        log("  Type: ${message.content::class.simpleName}")

        when (val content = message.content) {
            is MessageVideo -> {
                val video = content.video
                val videoFile = video.video
                log("  [VIDEO]")
                log("    fileName: ${video.fileName}")
                log("    mimeType: ${video.mimeType}")
                log("    duration: ${video.duration}s")
                log("    dimensions: ${video.width}x${video.height}")
                log("    supportsStreaming: ${video.supportsStreaming}")
                log("    fileId: ${videoFile?.id}")
                log("    fileSize: ${videoFile?.size}")
                log("    remoteId: ${videoFile?.remote?.id?.take(50)}...")
                log("    uniqueId: ${videoFile?.remote?.uniqueId}")
                log("    localPath: ${videoFile?.local?.path}")
                log("    isDownloaded: ${videoFile?.local?.isDownloadingCompleted}")
                log("    caption: ${content.caption?.text?.take(100)}")
                video.thumbnail?.let { thumb ->
                    log("    thumbnail:")
                    log("      dimensions: ${thumb.width}x${thumb.height}")
                    log("      fileId: ${thumb.file?.id}")
                }
            }
            is MessagePhoto -> {
                val photo = content.photo
                log("  [PHOTO]")
                log("    sizes: ${photo.sizes.size}")
                photo.sizes.forEachIndexed { sizeIdx, size ->
                    log("    size[$sizeIdx]: ${size.type} ${size.width}x${size.height}")
                    log("      fileId: ${size.photo?.id}")
                    log("      remoteId: ${size.photo?.remote?.id?.take(50)}...")
                    log("      uniqueId: ${size.photo?.remote?.uniqueId}")
                }
                log("    caption: ${content.caption?.text?.take(100)}")
            }
            is MessageText -> {
                log("  [TEXT]")
                log("    text: ${content.text.text.take(200)}...")
                log("    entities: ${content.text.entities.size}")
            }
            is MessageDocument -> {
                val doc = content.document
                val docFile = doc.document
                log("  [DOCUMENT]")
                log("    fileName: ${doc.fileName}")
                log("    mimeType: ${doc.mimeType}")
                log("    fileId: ${docFile?.id}")
                log("    fileSize: ${docFile?.size}")
                log("    remoteId: ${docFile?.remote?.id?.take(50)}...")
                log("    uniqueId: ${docFile?.remote?.uniqueId}")
                log("    caption: ${content.caption?.text?.take(100)}")
            }
            is MessageAudio -> {
                val audio = content.audio
                val audioFile = audio.audio
                log("  [AUDIO]")
                log("    title: ${audio.title}")
                log("    performer: ${audio.performer}")
                log("    fileName: ${audio.fileName}")
                log("    duration: ${audio.duration}s")
                log("    mimeType: ${audio.mimeType}")
                log("    fileId: ${audioFile?.id}")
                log("    fileSize: ${audioFile?.size}")
                log("    remoteId: ${audioFile?.remote?.id?.take(50)}...")
                log("    uniqueId: ${audioFile?.remote?.uniqueId}")
                log("    caption: ${content.caption?.text?.take(100)}")
            }
            else -> {
                log("  [OTHER: ${content::class.simpleName}]")
            }
        }
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

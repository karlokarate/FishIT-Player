package com.fishit.player.pipeline.telegram.debug

import com.fishit.player.pipeline.telegram.adapter.TelegramPipelineAdapter
import com.fishit.player.pipeline.telegram.catalog.ChatMediaClass
import com.fishit.player.pipeline.telegram.catalog.TelegramChatMediaClassifier
import com.fishit.player.pipeline.telegram.model.toRawMediaMetadata

/**
 * Default implementation of [TelegramDebugService].
 *
 * Uses [TelegramPipelineAdapter] for data access and
 * [TelegramChatMediaClassifier] for Hot/Warm/Cold classification.
 *
 * @param adapter Pipeline adapter for Telegram transport
 * @param classifier Chat media classifier instance
 * @param sessionDir Path to TDLib session directory (for status reporting)
 */
class TelegramDebugServiceImpl(
    private val adapter: TelegramPipelineAdapter,
    private val classifier: TelegramChatMediaClassifier = TelegramChatMediaClassifier(),
    private val sessionDir: String = "",
) : TelegramDebugService {
    override suspend fun getStatus(): TelegramStatus {
        val chats = adapter.getChats(limit = 200)

        // Classify all chats
        var hotCount = 0
        var warmCount = 0
        var coldCount = 0

        for (chat in chats) {
            // Sample messages for classification
            val sample =
                try {
                    adapter.fetchMessages(chatId = chat.chatId, limit = TelegramChatMediaClassifier.SAMPLE_SIZE)
                } catch (e: Exception) {
                    emptyList()
                }

            classifier.recordSample(chat.chatId, sample)
            val profile = classifier.getProfile(chat.chatId)
            val classification = classifier.classify(profile)

            when (classification) {
                ChatMediaClass.MEDIA_HOT -> hotCount++
                ChatMediaClass.MEDIA_WARM -> warmCount++
                ChatMediaClass.MEDIA_COLD -> coldCount++
            }
        }

        return TelegramStatus(
            isAuthenticated = true, // We got chats, so we're authenticated
            sessionDir = sessionDir,
            chatCount = chats.size,
            hotChats = hotCount,
            warmChats = warmCount,
            coldChats = coldCount,
        )
    }

    override suspend fun listChats(
        filter: ChatFilter,
        limit: Int,
    ): List<TelegramChatSummary> {
        val chats = adapter.getChats(limit = 200)
        val result = mutableListOf<TelegramChatSummary>()

        for (chat in chats) {
            if (result.size >= limit) break

            // Sample and classify
            val sample =
                try {
                    adapter.fetchMessages(chatId = chat.chatId, limit = TelegramChatMediaClassifier.SAMPLE_SIZE)
                } catch (e: Exception) {
                    emptyList()
                }

            classifier.recordSample(chat.chatId, sample)
            val profile = classifier.getProfile(chat.chatId)
            val classification = classifier.classify(profile)

            // Apply filter
            val matchesFilter =
                when (filter) {
                    ChatFilter.ALL -> true
                    ChatFilter.HOT -> classification == ChatMediaClass.MEDIA_HOT
                    ChatFilter.WARM -> classification == ChatMediaClass.MEDIA_WARM
                    ChatFilter.COLD -> classification == ChatMediaClass.MEDIA_COLD
                }

            if (matchesFilter) {
                result.add(
                    TelegramChatSummary(
                        chatId = chat.chatId,
                        title = chat.title,
                        mediaClass = classification.name.removePrefix("MEDIA_"),
                        mediaCountEstimate = profile.mediaMessagesSampled,
                    ),
                )
            }
        }

        return result
    }

    override suspend fun sampleMedia(
        chatId: Long,
        limit: Int,
    ): List<TelegramMediaSummary> {
        // Use fetchMediaMessages which returns TelegramMediaItem directly
        val mediaItems = adapter.fetchMediaMessages(chatId = chatId, limit = limit)
        val result = mutableListOf<TelegramMediaSummary>()

        for (mediaItem in mediaItems) {
            // Convert to RawMediaMetadata
            val rawMeta = mediaItem.toRawMediaMetadata()

            result.add(
                TelegramMediaSummary(
                    messageId = mediaItem.messageId,
                    timestampMillis = mediaItem.date ?: 0L,
                    mimeType = mediaItem.mimeType,
                    sizeBytes = mediaItem.sizeBytes,
                    normalizedTitle = rawMeta.originalTitle,
                    normalizedMediaType = rawMeta.mediaType,
                ),
            )
        }

        return result
    }
}

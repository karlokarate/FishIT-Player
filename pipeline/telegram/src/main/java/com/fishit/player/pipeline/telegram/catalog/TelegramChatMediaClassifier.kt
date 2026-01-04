package com.fishit.player.pipeline.telegram.catalog

import com.fishit.player.infra.transport.telegram.api.TgContent
import com.fishit.player.infra.transport.telegram.api.TgMessage
import java.util.concurrent.ConcurrentHashMap

/**
 * Classifies Telegram chats by media density (Hot/Warm/Cold).
 *
 * **Purpose:**
 * - Avoid wasting resources on text-only chats
 * - Prioritize media-rich channels for ingestion
 * - Dynamically reclassify chats as activity changes
 *
 * **Classification Thresholds:**
 * - MEDIA_HOT: ≥20 media messages OR ≥30% media ratio
 * - MEDIA_WARM: ≥3 media messages AND ≥5% media ratio
 * - MEDIA_COLD: Below WARM thresholds
 *
 * **Ingestion Strategy by Class:** | Class | Initial Scan | Live Updates | Full History |
 * |-------|-------------|--------------|--------------| | HOT | Yes | Real-time | Yes | | WARM |
 * Recent only | Periodic | On-demand | | COLD | No | Monitor only | No |
 */
class TelegramChatMediaClassifier {
    /** Optional callback fired when a previously COLD chat warms up. */
    var onChatWarmUp: ((chatId: Long, newClass: ChatMediaClass) -> Unit)? = null

    companion object {
        /** Minimum media messages to qualify as HOT (fast path). */
        private const val HOT_MEDIA_COUNT_THRESHOLD = 20

        /** Minimum media ratio to qualify as HOT. */
        private const val HOT_MEDIA_RATIO_THRESHOLD = 0.30

        /** Minimum media messages to qualify as WARM. */
        private const val WARM_MEDIA_COUNT_THRESHOLD = 3

        /** Minimum media ratio to qualify as WARM. */
        private const val WARM_MEDIA_RATIO_THRESHOLD = 0.05

        /** Sample size for initial chat classification. */
        const val SAMPLE_SIZE = 200
    }

    /** Profiles indexed by chat ID. Thread-safe for concurrent updates. */
    private val profiles = ConcurrentHashMap<Long, TelegramChatMediaProfile>()

    /** Chats currently suppressed (COLD classification). */
    private val suppressedChats = ConcurrentHashMap.newKeySet<Long>()

    /** Get or create a profile for a chat. */
    fun getProfile(chatId: Long): TelegramChatMediaProfile = profiles.computeIfAbsent(chatId) { TelegramChatMediaProfile(chatId) }

    /**
     * Classify a chat based on its profile.
     *
     * @param profile Chat's media profile
     * @return Classification (HOT, WARM, or COLD)
     */
    fun classify(profile: TelegramChatMediaProfile): ChatMediaClass =
        when {
            // HOT: High media density
            profile.mediaMessagesSampled >= HOT_MEDIA_COUNT_THRESHOLD ||
                profile.mediaRatio >= HOT_MEDIA_RATIO_THRESHOLD -> ChatMediaClass.MEDIA_HOT

            // WARM: Moderate media presence
            profile.mediaMessagesSampled >= WARM_MEDIA_COUNT_THRESHOLD &&
                profile.mediaRatio >= WARM_MEDIA_RATIO_THRESHOLD -> ChatMediaClass.MEDIA_WARM

            // COLD: Low or no media
            else -> ChatMediaClass.MEDIA_COLD
        }

    /**
     * Record a message and update classification.
     *
     * @param message Transport-level message
     * @return Updated classification
     */
    fun recordMessage(message: TgMessage): ChatMediaClass {
        val profile = getProfile(message.chatId)
        val previousClass = classify(profile)

        val isMedia = message.content?.isPlayableMedia() == true
        val timestampMillis = message.date * 1000L

        profile.recordMessage(isMedia, timestampMillis)

        val newClass = classify(profile)

        // If chat was suppressed but is now WARM or HOT, unsuppress and trigger ingestion
        if (newClass != ChatMediaClass.MEDIA_COLD && suppressedChats.contains(message.chatId)) {
            suppressedChats.remove(message.chatId)
            onChatWarmUp?.invoke(message.chatId, newClass)
        } else if (previousClass == ChatMediaClass.MEDIA_COLD && newClass != previousClass) {
            onChatWarmUp?.invoke(message.chatId, newClass)
        }

        return newClass
    }

    /**
     * Record multiple messages (batch update).
     *
     * @param chatId Chat being sampled
     * @param messages Messages from the chat
     * @return Classification after sampling
     */
    fun recordSample(
        chatId: Long,
        messages: List<TgMessage>,
    ): ChatMediaClass {
        val profile = getProfile(chatId)
        val previousClass = classify(profile)

        messages.forEach { msg ->
            val isMedia = msg.content?.isPlayableMedia() == true
            val timestampMillis = msg.date * 1000L
            profile.recordMessage(isMedia, timestampMillis)
        }

        val classification = classify(profile)

        // Suppress COLD chats from initial ingestion
        if (classification == ChatMediaClass.MEDIA_COLD) {
            suppressedChats.add(chatId)
        }

        // If a COLD chat warms up during sampling, trigger ingestion
        if (previousClass == ChatMediaClass.MEDIA_COLD && classification != ChatMediaClass.MEDIA_COLD) {
            onChatWarmUp?.invoke(chatId, classification)
        }

        return classification
    }

    /** Check if a chat is suppressed (COLD and not yet warmed up). */
    fun isSuppressed(chatId: Long): Boolean = suppressedChats.contains(chatId)

    /** Get all non-suppressed chats with their classifications. */
    fun getActiveChats(): Map<Long, ChatMediaClass> =
        profiles.filter { !suppressedChats.contains(it.key) }.mapValues {
            classify(it.value)
        }

    /** Clear all profiles (for testing or reset). */
    fun clear() {
        profiles.clear()
        suppressedChats.clear()
    }
}

/**
 * Check if content is playable media.
 *
 * Playable media types:
 * - Video, Audio, VoiceNote, VideoNote, Animation
 * - Photo (for galleries)
 * - Document with video/audio MIME type
 */
private fun TgContent.isPlayableMedia(): Boolean {
    return when (this) {
        is TgContent.Video -> true
        is TgContent.Audio -> true
        is TgContent.VoiceNote -> true
        is TgContent.VideoNote -> true
        is TgContent.Animation -> true
        is TgContent.Photo -> true
        is TgContent.Document -> {
            // Check MIME type for media documents
            val mime = mimeType?.lowercase() ?: return false
            mime.startsWith("video/") || mime.startsWith("audio/")
        }
        is TgContent.Text -> false
        is TgContent.Unknown -> false
    }
}

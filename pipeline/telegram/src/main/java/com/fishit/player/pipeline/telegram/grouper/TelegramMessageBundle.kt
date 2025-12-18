package com.fishit.player.pipeline.telegram.grouper

import com.fishit.player.infra.transport.telegram.api.TgMessage
import com.fishit.player.pipeline.telegram.model.TelegramBundleType

/**
 * Represents a group of Telegram messages bundled together.
 *
 * Per TELEGRAM_STRUCTURED_BUNDLES_CONTRACT.md Section 1.1:
 * - A Structured Bundle is 2..N messages with identical timestamp
 * - Must pass Cohesion Gate (R1b) and contain at least one VIDEO
 *
 * The bundle organizes messages by type for efficient processing:
 * - [videoMessages] contains all VIDEO messages (1..N for multi-video bundles)
 * - [textMessage] contains the TEXT message with structured metadata (optional)
 * - [photoMessage] contains the PHOTO message for poster (optional)
 *
 * Per Contract R7 (Lossless Emission):
 * - Each VIDEO in the bundle becomes one RawMediaMetadata
 * - All VIDEOs share the same structured metadata from textMessage
 *
 * @property timestamp Unix epoch timestamp shared by all messages in bundle
 * @property messages All messages in the bundle (sorted by messageId)
 * @property bundleType Classification of this bundle (FULL_3ER, COMPACT_2ER, SINGLE)
 * @property videoMessages All VIDEO messages in the bundle (never empty for valid bundles)
 * @property textMessage TEXT message with structured metadata (null for SINGLE/some COMPACT_2ER)
 * @property photoMessage PHOTO message for poster image (null for SINGLE/some COMPACT_2ER)
 * @property discriminator Cohesion discriminator used for grouping (albumId or proximity hash)
 */
data class TelegramMessageBundle(
    val timestamp: Long,
    val messages: List<TgMessage>,
    val bundleType: TelegramBundleType,
    val videoMessages: List<TgMessage>,
    val textMessage: TgMessage?,
    val photoMessage: TgMessage?,
    val discriminator: String? = null,
) {
    /**
     * Number of VIDEO messages in this bundle.
     *
     * Per Contract R7 (Lossless Emission): Each VIDEO becomes one RawMediaMetadata.
     */
    val videoCount: Int get() = videoMessages.size
    
    /**
     * Whether this bundle has structured metadata from a TEXT message.
     */
    val hasStructuredMetadata: Boolean get() = textMessage != null
    
    /**
     * Whether this bundle has a poster image from a PHOTO message.
     */
    val hasPoster: Boolean get() = photoMessage != null
    
    /**
     * Whether this is a complete 3-cluster bundle (PHOTO + TEXT + VIDEO).
     */
    val isComplete: Boolean get() = bundleType == TelegramBundleType.FULL_3ER
    
    /**
     * ChatId from the first message (all messages share the same chatId).
     */
    val chatId: Long get() = messages.firstOrNull()?.chatId ?: 0L
    
    /**
     * MessageId range for logging/debugging.
     */
    val messageIdRange: LongRange
        get() {
            val ids = messages.map { it.messageId }
            return (ids.minOrNull() ?: 0L)..(ids.maxOrNull() ?: 0L)
        }
    
    companion object {
        /**
         * Creates a SINGLE bundle from a standalone message.
         */
        fun single(message: TgMessage): TelegramMessageBundle = TelegramMessageBundle(
            timestamp = message.date,
            messages = listOf(message),
            bundleType = TelegramBundleType.SINGLE,
            videoMessages = listOf(message),
            textMessage = null,
            photoMessage = null,
        )
    }
}

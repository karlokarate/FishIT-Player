package com.fishit.player.pipeline.telegram.catalog

/**
 * Profile tracking media density for a Telegram chat.
 *
 * Used by [TelegramChatMediaClassifier] to determine if a chat is worth scanning for media content.
 *
 * @property chatId Telegram chat ID
 * @property totalMessagesSampled Total messages examined in sample
 * @property mediaMessagesSampled Messages containing playable media
 * @property lastMediaAtMillis Timestamp of most recent media message
 */
data class TelegramChatMediaProfile(
        val chatId: Long,
        var totalMessagesSampled: Int = 0,
        var mediaMessagesSampled: Int = 0,
        var lastMediaAtMillis: Long? = null,
) {
    /** Ratio of media messages to total messages (0.0 to 1.0). */
    val mediaRatio: Double
        get() =
                if (totalMessagesSampled == 0) 0.0
                else {
                    mediaMessagesSampled.toDouble() / totalMessagesSampled.toDouble()
                }

    /**
     * Update profile after examining a message.
     *
     * @param isMedia Whether the message contains playable media
     * @param timestampMillis Message timestamp in milliseconds
     */
    fun recordMessage(isMedia: Boolean, timestampMillis: Long?) {
        totalMessagesSampled++
        if (isMedia) {
            mediaMessagesSampled++
            timestampMillis?.let {
                if (lastMediaAtMillis == null || it > lastMediaAtMillis!!) {
                    lastMediaAtMillis = it
                }
            }
        }
    }
}

/**
 * Classification of a chat's media density.
 *
 * Determines the ingestion strategy:
 * - MEDIA_HOT: Full catalog scan, real-time updates
 * - MEDIA_WARM: Partial scan, periodic updates
 * - MEDIA_COLD: Skip initial scan, monitor for changes
 */
enum class ChatMediaClass {
    /**
     * High-density media chat (movie channels, media groups).
     * - ≥20 media messages sampled OR ≥30% media ratio
     * - Full catalog ingestion
     * - Real-time update monitoring
     */
    MEDIA_HOT,

    /**
     * Medium-density chat (mixed content groups).
     * - ≥3 media messages AND ≥5% media ratio
     * - Partial scan (recent media only)
     * - Periodic update checks
     */
    MEDIA_WARM,

    /**
     * Low-density chat (text-only or minimal media).
     * - Does not meet HOT or WARM criteria
     * - Skip initial catalog scan
     * - Monitor for activity changes
     */
    MEDIA_COLD,
}

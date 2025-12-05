package com.fishit.player.core.model

/**
 * Represents a stored resume position for content.
 *
 * @property contentId Unique identifier for the content.
 * @property type Type of the content.
 * @property positionMs Resume position in milliseconds.
 * @property durationMs Total duration in milliseconds.
 * @property updatedAt Timestamp of last update.
 * @property profileId Profile that created this resume point.
 */
data class ResumePoint(
    val contentId: String,
    val type: PlaybackType,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long = System.currentTimeMillis(),
    val profileId: Long? = null,
) {
    /**
     * Progress as a fraction (0.0 to 1.0).
     */
    val progress: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs) else 0f

    /**
     * Whether the content is considered finished (>95% watched).
     */
    val isFinished: Boolean
        get() = progress >= 0.95f
}

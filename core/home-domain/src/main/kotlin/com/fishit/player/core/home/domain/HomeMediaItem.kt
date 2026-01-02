package com.fishit.player.core.home.domain

import com.fishit.player.core.model.ImageRef
import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.SourceType

/**
 * Domain model for Home screen content items.
 *
 * Feature-facing model that decouples UI from persistence/pipeline concerns.
 */
data class HomeMediaItem(
    val id: String,
    val title: String,
    val poster: ImageRef? = null,
    val placeholderThumbnail: ImageRef? = null,
    val backdrop: ImageRef? = null,
    val mediaType: MediaType,
    val sourceType: SourceType,
    val sourceTypes: List<SourceType> = listOf(sourceType),
    val resumePosition: Long = 0L,
    val duration: Long = 0L,
    val isNew: Boolean = false,
    val year: Int? = null,
    val rating: Float? = null,
    val genres: String? = null,
    val navigationId: String,
    val navigationSource: SourceType,
) {
    val resumeFraction: Float?
        get() =
            if (duration > 0 && resumePosition > 0) {
                (resumePosition.toFloat() / duration).coerceIn(0f, 1f)
            } else {
                null
            }

    val hasMultipleSources: Boolean
        get() = sourceTypes.distinct().size > 1
}

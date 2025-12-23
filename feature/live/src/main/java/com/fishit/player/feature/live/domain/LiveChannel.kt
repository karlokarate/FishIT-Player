package com.fishit.player.feature.live.domain

import com.fishit.player.core.model.ImageRef
import com.fishit.player.core.model.SourceType

/**
 * Domain model for a live TV channel in the Live feature.
 *
 * This is a UI-ready model consumed by LiveScreen and LiveViewModel.
 * Mapped from RawMediaMetadata by LiveContentRepositoryAdapter.
 *
 * **Architecture:**
 * - Feature layer defines this domain model
 * - Data layer maps RawMediaMetadata → LiveChannel
 * - UI layer displays this model directly
 */
data class LiveChannel(
    /** Unique identifier (sourceId from RawMediaMetadata) */
    val id: String,

    /** Channel name */
    val name: String,

    /** Channel number/position */
    val channelNumber: Int?,

    /** Channel logo/icon */
    val logo: ImageRef?,

    /** Category ID for filtering */
    val categoryId: String?,

    /** Category name for display */
    val categoryName: String?,

    /** Current program title (from EPG if available) */
    val currentProgram: String?,

    /** Current program description */
    val currentProgramDescription: String?,

    /** Program start time (epoch millis) */
    val programStart: Long?,

    /** Program end time (epoch millis) */
    val programEnd: Long?,

    /** Source type (XTREAM, etc.) */
    val sourceType: SourceType,

    /** Whether this channel is a favorite */
    val isFavorite: Boolean = false,

    /** Last watched timestamp for recent channels */
    val lastWatched: Long? = null
)

/**
 * Category for grouping live channels.
 */
data class LiveCategory(
    val id: String,
    val name: String,
    val channelCount: Int = 0
)

package com.fishit.player.core.live.domain

import com.fishit.player.core.model.ImageRef
import com.fishit.player.core.model.SourceType

/**
 * Domain model for a live TV channel.
 */
data class LiveChannel(
    val id: String,
    val name: String,
    val channelNumber: Int?,
    val logo: ImageRef?,
    val categoryId: String?,
    val categoryName: String?,
    val currentProgram: String?,
    val currentProgramDescription: String?,
    val programStart: Long?,
    val programEnd: Long?,
    val sourceType: SourceType,
    val isFavorite: Boolean = false,
    val lastWatched: Long? = null,
    // === EPG/Catchup Support ===
    /** EPG channel ID for program guide integration */
    val epgChannelId: String? = null,
    /** TV archive/catchup flag: true if catchup is available */
    val hasCatchup: Boolean = false,
    /** TV archive duration in days (catchup window) */
    val catchupDays: Int = 0,
    /** Adult content flag for parental controls */
    val isAdult: Boolean = false,
)

data class LiveCategory(
    val id: String,
    val name: String,
    val channelCount: Int = 0,
)

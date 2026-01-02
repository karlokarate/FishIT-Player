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
)

data class LiveCategory(
    val id: String,
    val name: String,
    val channelCount: Int = 0,
)

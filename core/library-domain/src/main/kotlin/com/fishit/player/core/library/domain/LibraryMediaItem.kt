package com.fishit.player.core.library.domain

import com.fishit.player.core.model.ImageRef
import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.SourceType

/**
 * Domain model for Library screen content items.
 */
data class LibraryMediaItem(
    val id: String,
    val title: String,
    val poster: ImageRef? = null,
    val backdrop: ImageRef? = null,
    val mediaType: MediaType,
    val sourceType: SourceType,
    val year: Int? = null,
    val rating: Float? = null,
    val categoryId: String? = null,
    val categoryName: String? = null,
    val genres: List<String> = emptyList(),
    val description: String? = null,
    val navigationId: String,
    val navigationSource: SourceType,
)

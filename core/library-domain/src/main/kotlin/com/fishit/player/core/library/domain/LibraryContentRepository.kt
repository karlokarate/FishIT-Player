package com.fishit.player.core.library.domain

import kotlinx.coroutines.flow.Flow

// Repository interface for Library screen content.
//
// Architecture:
// - Contract lives in core modules
// - Implementations live in infra modules
interface LibraryContentRepository {

    fun observeVod(categoryId: String? = null): Flow<List<LibraryMediaItem>>

    fun observeSeries(categoryId: String? = null): Flow<List<LibraryMediaItem>>

    fun observeVodCategories(): Flow<List<LibraryCategory>>

    fun observeSeriesCategories(): Flow<List<LibraryCategory>>

    suspend fun search(query: String, limit: Int = 50): List<LibraryMediaItem>
}

data class LibraryCategory(
    val id: String,
    val name: String,
    val itemCount: Int = 0,
)

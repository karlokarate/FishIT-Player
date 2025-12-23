package com.fishit.player.feature.library.domain

import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for Library screen content.
 *
 * Provides VOD and Series content for the Library browser.
 *
 * **Architecture:**
 * - Feature layer defines this interface
 * - Implementation lives in infra/data-* layer
 * - Returns domain models (LibraryMediaItem), not RawMediaMetadata
 *
 * **Content Sources:**
 * - Xtream VOD (movies)
 * - Xtream Series (TV shows)
 */
interface LibraryContentRepository {

    /**
     * Observe all VOD items (movies).
     *
     * @param categoryId Optional category filter
     * @return Flow of VOD items for Library display
     */
    fun observeVod(categoryId: String? = null): Flow<List<LibraryMediaItem>>

    /**
     * Observe all series items.
     *
     * @param categoryId Optional category filter
     * @return Flow of series items for Library display
     */
    fun observeSeries(categoryId: String? = null): Flow<List<LibraryMediaItem>>

    /**
     * Get available VOD categories.
     *
     * @return Flow of category pairs (id, name)
     */
    fun observeVodCategories(): Flow<List<LibraryCategory>>

    /**
     * Get available series categories.
     *
     * @return Flow of category pairs (id, name)
     */
    fun observeSeriesCategories(): Flow<List<LibraryCategory>>

    /**
     * Search library by title.
     *
     * @param query Search query
     * @param limit Maximum results
     * @return Matching media items
     */
    suspend fun search(query: String, limit: Int = 50): List<LibraryMediaItem>
}

/**
 * Simple category model for filtering.
 */
data class LibraryCategory(
    val id: String,
    val name: String,
    val itemCount: Int = 0
)

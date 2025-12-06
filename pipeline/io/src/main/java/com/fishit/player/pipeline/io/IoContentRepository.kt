package com.fishit.player.pipeline.io

import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for IO content discovery and management.
 */
interface IoContentRepository {
    /**
     * Discovers all available media items.
     */
    fun discoverAll(mimeTypeFilter: String? = null): Flow<List<IoMediaItem>>

    /**
     * Lists media items in a specific directory or source.
     */
    fun listItems(source: IoSource, recursive: Boolean = false): Flow<List<IoMediaItem>>

    /**
     * Searches for media items by title or filename.
     */
    fun search(query: String): Flow<List<IoMediaItem>>

    /**
     * Gets a single item by its ID.
     */
    suspend fun getItemById(id: String): IoMediaItem?

    /**
     * Refreshes the content catalog.
     */
    suspend fun refresh()
}

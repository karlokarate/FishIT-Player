package com.fishit.player.pipeline.io

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Stub implementation of IoContentRepository.
 *
 * This implementation provides deterministic fake data for testing
 * and early development. No real filesystem or SAF access is performed.
 *
 * **Purpose:**
 * - Validate interface contracts
 * - Enable early integration testing
 * - Provide predictable behavior for unit tests
 *
 * **Future:**
 * A real implementation will be added in later phases with actual
 * filesystem scanning, SAF integration, and network share support.
 */
class StubIoContentRepository : IoContentRepository {
    private val fakeSources =
        listOf(
            IoMediaItem.fake(
                id = "stub-item-1",
                path = "/storage/emulated/0/Movies/sample-1.mp4",
                title = "Sample Video 1",
            ),
            IoMediaItem.fake(
                id = "stub-item-2",
                path = "/storage/emulated/0/Movies/sample-2.mp4",
                title = "Sample Video 2",
            ),
        )

    override fun discoverAll(mimeTypeFilter: String?): Flow<List<IoMediaItem>> {
        // Stub: Return fake items for testing purposes
        return flowOf(fakeSources)
    }

    override fun listItems(
        source: IoSource,
        recursive: Boolean,
    ): Flow<List<IoMediaItem>> {
        // Stub: Return empty list (no real directory browsing)
        return flowOf(emptyList())
    }

    override fun search(query: String): Flow<List<IoMediaItem>> {
        // Stub: Simple case-insensitive search on fake items
        val results =
            fakeSources.filter {
                it.title.contains(query, ignoreCase = true) ||
                    it.fileName.contains(query, ignoreCase = true)
            }
        return flowOf(results)
    }

    override suspend fun getItemById(id: String): IoMediaItem? {
        // Stub: Simple lookup in fake items
        return fakeSources.find { it.id == id }
    }

    override suspend fun refresh() {
        // Stub: No-op (no real scanning to refresh)
    }
}

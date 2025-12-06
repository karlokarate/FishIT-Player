package com.fishit.player.pipeline.audiobook

/**
 * Stub implementation of AudiobookRepository for Phase 2.
 *
 * This implementation returns deterministic empty results to establish
 * the repository contract without actual file I/O or persistence.
 *
 * Future phases (4+) will replace this with implementations that:
 * - Scan local directories for audiobook files
 * - Parse RAR/ZIP archives for audiobooks
 * - Extract metadata and chapter information
 * - Cache audiobook data for performance
 */
class StubAudiobookRepository : AudiobookRepository {
    override suspend fun getAllAudiobooks(): List<AudiobookItem> {
        // Stub returns empty list
        // Future: Return actual audiobooks from local storage/database
        return emptyList()
    }

    override suspend fun getAudiobookById(id: String): AudiobookItem? {
        // Stub returns null
        // Future: Query audiobook from database/cache by ID
        return null
    }

    override suspend fun getChaptersForAudiobook(audiobookId: String): List<AudiobookChapter> {
        // Stub returns empty list
        // Future: Load chapters from metadata or parse from archive
        return emptyList()
    }

    override suspend fun searchAudiobooks(query: String): List<AudiobookItem> {
        // Stub returns empty list
        // Future: Implement full-text search across title/author/metadata
        return emptyList()
    }
}

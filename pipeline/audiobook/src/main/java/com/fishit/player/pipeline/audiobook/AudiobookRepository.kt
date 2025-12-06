package com.fishit.player.pipeline.audiobook

/**
 * Repository interface for managing audiobook content.
 *
 * This interface defines the contract for loading and managing audiobooks.
 * Phase 2 provides stub implementations. Future phases (4+) will implement
 * full RAR/ZIP scanning, chapter parsing, and metadata extraction.
 *
 * Key features for future phases:
 * - Scan local directories for audiobook archives (RAR/ZIP)
 * - Extract and parse embedded chapter markers
 * - Cache audiobook metadata for quick access
 * - Support bookmarks and reading positions
 */
interface AudiobookRepository {
    /**
     * Get all available audiobooks.
     *
     * @return List of all audiobooks. Stub implementation returns empty list.
     */
    suspend fun getAllAudiobooks(): List<AudiobookItem>

    /**
     * Get a specific audiobook by ID.
     *
     * @param id Audiobook unique identifier
     * @return Audiobook if found, null otherwise. Stub returns null.
     */
    suspend fun getAudiobookById(id: String): AudiobookItem?

    /**
     * Get chapters for a specific audiobook.
     *
     * @param audiobookId Audiobook unique identifier
     * @return List of chapters. Stub returns empty list.
     */
    suspend fun getChaptersForAudiobook(audiobookId: String): List<AudiobookChapter>

    /**
     * Search audiobooks by title or author.
     *
     * @param query Search query string
     * @return List of matching audiobooks. Stub returns empty list.
     */
    suspend fun searchAudiobooks(query: String): List<AudiobookItem>
}

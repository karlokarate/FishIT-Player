package com.fishit.player.core.model.repository

/**
 * Repository interface for managing content metadata.
 * 
 * This is a placeholder for Phase 2. Full implementation will come
 * when pipeline integration is complete.
 */
interface ContentRepository {
    
    /**
     * Get content title by content ID.
     * Returns null if content not found.
     */
    suspend fun getContentTitle(contentId: String): String?
    
    /**
     * Get content poster URL by content ID.
     * Returns null if content not found or has no poster.
     */
    suspend fun getContentPosterUrl(contentId: String): String?
}

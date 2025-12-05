package com.fishit.player.core.persistence.repository

import com.fishit.player.core.model.repository.ContentRepository
import io.objectbox.BoxStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ObjectBox-backed implementation of [ContentRepository].
 *
 * This is a minimal placeholder for Phase 2.
 * Full implementation will be added when pipeline integration is complete.
 */
@Singleton
class ObxContentRepository
    @Inject
    constructor(
        private val boxStore: BoxStore,
    ) : ContentRepository {
        override suspend fun getContentTitle(contentId: String): String? =
            withContext(Dispatchers.IO) {
                // Placeholder implementation
                // Will be expanded when pipelines are integrated
                null
            }

        override suspend fun getContentPosterUrl(contentId: String): String? =
            withContext(Dispatchers.IO) {
                // Placeholder implementation
                // Will be expanded when pipelines are integrated
                null
            }
    }

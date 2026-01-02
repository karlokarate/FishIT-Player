package com.fishit.player.core.persistence.cache

import com.fishit.player.infra.logging.UnifiedLog
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cache invalidation orchestrator for sync operations.
 *
 * **Phase 2 Scope:**
 * - Invalidates HomeContentCache after catalog sync completion
 * - Called by sync workers (Xtream, Telegram, IO) on success
 *
 * **Contract:**
 * - Call invalidateAll() after any catalog sync that modifies media
 * - Safe to call from Worker threads (suspend function)
 * - Logs all invalidation operations
 *
 * **Layer Compliance:**
 * - Lives in core/persistence (infrastructure)
 * - Used by app-v2/work sync workers
 * - No UI or feature dependencies
 */
@Singleton
class HomeCacheInvalidator @Inject constructor(private val homeContentCache: HomeContentCache) {
    companion object {
        private const val TAG = "HomeCacheInvalidator"
    }

    /**
     * Invalidate all Home content cache entries.
     *
     * **Use Cases:**
     * - After Xtream catalog scan completes
     * - After Telegram history scan completes
     * - After IO scan completes
     * - After manual "Refresh" action
     *
     * **Thread Safety:**
     * - Safe to call from Worker threads
     * - Safe to call from multiple workers (idempotent)
     */
    suspend fun invalidateAllAfterSync(source: String, syncRunId: String) {
        UnifiedLog.i(TAG) { "INVALIDATE_ALL source=$source sync_run_id=$syncRunId" }

        homeContentCache.invalidateAll()

        UnifiedLog.d(TAG) { "Cache invalidated: Home UI will refresh from DB on next query" }
    }

    /**
     * Invalidate specific cache key (optional fine-grained control).
     *
     * **Use Case:** If sync only affected movies, invalidate only Movies cache. Currently not used
     * (Phase 2 uses full invalidation).
     */
    suspend fun invalidateKey(key: CacheKey, source: String, syncRunId: String) {
        UnifiedLog.i(TAG) { "INVALIDATE_KEY key=${key.name} source=$source sync_run_id=$syncRunId" }

        homeContentCache.invalidate(key)
    }
}

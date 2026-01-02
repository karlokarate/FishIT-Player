package com.fishit.player.core.persistence.cache.impl

import com.fishit.player.core.persistence.cache.CacheKey
import com.fishit.player.core.persistence.cache.CachedSection
import com.fishit.player.core.persistence.cache.HomeContentCache
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * L1 in-memory cache implementation (Phase 2).
 *
 * **Characteristics:**
 * - Fast: ConcurrentHashMap access (<1ms)
 * - Volatile: Lost on app kill/restart
 * - TTL-based: Entries expire via CachedSection.isExpired()
 *
 * **Thread Safety:**
 * - ConcurrentHashMap for lock-free reads/writes
 * - MutableSharedFlow for invalidation events (coroutine-safe)
 * - No synchronized blocks needed
 *
 * **Layer Compliance:**
 * - No UI, Domain, Pipeline dependencies
 * - Pure infra/caching concern
 * - HomeMediaItem is allowed (domain DTO)
 */
@Singleton
class InMemoryHomeCache @Inject constructor() : HomeContentCache {

    private val cache = ConcurrentHashMap<CacheKey, CachedSection<*>>()
    private val _invalidations =
            MutableSharedFlow<CacheKey>(
                    extraBufferCapacity = 10 // Buffer for burst invalidations
            )

    /**
     * Get cached section if present and not expired.
     *
     * **Contract:**
     * - Returns null if key missing OR expired
     * - Expired entries are NOT auto-removed (lazy cleanup)
     * - Thread-safe (ConcurrentHashMap read)
     * - Non-blocking synchronous operation
     */
    override fun get(key: CacheKey): CachedSection<*>? {
        return cache[key]?.takeUnless { it.isExpired() }
    }

    /**
     * Store section with TTL.
     *
     * **Contract:**
     * - Overwrites existing entry (no merge)
     * - Timestamp is set by CachedSection constructor
     * - Thread-safe (ConcurrentHashMap write)
     * - Non-blocking synchronous operation
     */
    override fun <T> put(key: CacheKey, section: CachedSection<T>) {
        cache[key] = section
    }

    /**
     * Invalidate specific section and emit event.
     *
     * **Contract:**
     * - Removes from cache immediately
     * - Emits invalidation event for reactive listeners
     * - Safe to call even if key not present
     */
    override suspend fun invalidate(key: CacheKey) {
        cache.remove(key)
        _invalidations.emit(key)
    }

    /**
     * Invalidate all sections (e.g., after catalog sync).
     *
     * **Contract:**
     * - Clears entire cache atomically
     * - Emits invalidation event for each key type
     * - Used by sync workers to force refresh
     */
    override suspend fun invalidateAll() {
        cache.clear()

        // Emit invalidation for all known keys
        listOf(
                        CacheKey.ContinueWatching,
                        CacheKey.RecentlyAdded,
                        CacheKey.Movies,
                        CacheKey.Series,
                        CacheKey.Clips,
                        CacheKey.LiveTV
                )
                .forEach { key -> _invalidations.emit(key) }
    }

    /**
     * Observe invalidation events for reactive cache updates.
     *
     * **Use Case:**
     * - Repository can re-query after invalidation
     * - UI can show loading state during refresh
     */
    override fun observeInvalidations(): Flow<CacheKey> = _invalidations.asSharedFlow()
}

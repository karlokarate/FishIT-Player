package com.fishit.player.core.persistence.cache

import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Multi-layer cache for Home screen content.
 *
 * **Architecture:**
 * - L1 (Memory): Fast, short TTL (5s), volatile
 * - L3 (ObjectBox): Source of truth, always fresh
 *
 * **Layer Compliance:**
 * - Lives in core/persistence (shared infrastructure)
 * - Generic interface (no domain dependencies)
 * - Actual items type is determined by usage context
 *
 * **Phase 2 Scope:**
 * - Memory-only cache (no disk layer)
 * - TTL-based expiration
 * - Sync-triggered invalidation
 */
interface HomeContentCache {
    /**
     * Get cached section with TTL check.
     * Returns null if not cached or expired.
     *
     * **Thread Safety:** Safe to call from any thread (ConcurrentHashMap read)
     * **Non-blocking:** Synchronous operation, no suspend needed
     */
    fun get(key: CacheKey): CachedSection<*>?

    /**
     * Store section with TTL.
     *
     * **Thread Safety:** Safe to call from any thread (ConcurrentHashMap write)
     * **Non-blocking:** Synchronous operation, no suspend needed
     */
    fun <T> put(
        key: CacheKey,
        section: CachedSection<T>,
    )

    /**
     * Invalidate specific section.
     *
     * **Suspend:** Emits invalidation event via SharedFlow
     */
    suspend fun invalidate(key: CacheKey)

    /**
     * Invalidate all sections (e.g., after catalog sync).
     *
     * **Suspend:** Emits invalidation events via SharedFlow
     */
    suspend fun invalidateAll()

    /** Observe cache invalidation events (for reactive updates). */
    fun observeInvalidations(): Flow<CacheKey>
}

/**
 * Cache key for Home sections.
 *
 * **Contract:**
 * - Each key maps to ONE HomeContentRepository method
 * - Keys are stable across app restarts
 * - Keys are used for invalidation targeting
 */
sealed class CacheKey(
    val name: String,
) {
    data object ContinueWatching : CacheKey("continue_watching")

    data object RecentlyAdded : CacheKey("recently_added")

    data object Movies : CacheKey("movies")

    data object Series : CacheKey("series")

    data object Clips : CacheKey("clips")

    data object LiveTV : CacheKey("live_tv")
}

/**
 * Cached section with timestamp and TTL.
 *
 * **Contract:**
 * - Immutable data class (thread-safe reads)
 * - TTL check via isExpired() (monotonic time)
 * - Default TTL: 5 minutes (balance freshness vs performance)
 * - Generic type T for items (domain-agnostic)
 */
data class CachedSection<T>(
    val items: List<T>,
    val timestamp: Long = System.currentTimeMillis(),
    val ttl: Duration = 300.seconds, // 5 minutes default
) {
    /**
     * Check if cache entry is expired based on TTL.
     *
     * **Thread Safety:** Safe to call from any thread (pure computation)
     */
    fun isExpired(): Boolean = System.currentTimeMillis() - timestamp > ttl.inWholeMilliseconds
}

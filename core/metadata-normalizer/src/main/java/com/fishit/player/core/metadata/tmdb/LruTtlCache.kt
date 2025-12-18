package com.fishit.player.core.metadata.tmdb

import java.util.concurrent.ConcurrentHashMap

/**
 * Bounded in-memory LRU cache with TTL.
 *
 * Per TMDB_ENRICHMENT_CONTRACT.md Section 6.1:
 * - Thread-safe
 * - Bounded by max entries (evicts oldest)
 * - TTL-based expiration
 *
 * @param maxEntries Maximum number of entries before eviction
 * @param ttlMillis Time-to-live in milliseconds
 */
class LruTtlCache<K, V>(
    private val maxEntries: Int,
    private val ttlMillis: Long,
) {
    private data class CacheEntry<V>(
        val value: V,
        val timestamp: Long,
    )

    private val cache = ConcurrentHashMap<K, CacheEntry<V>>()
    private val accessOrder = ConcurrentHashMap<K, Long>()
    private var accessCounter = 0L

    /**
     * Get value from cache if present and not expired.
     */
    fun get(key: K): V? {
        val entry = cache[key] ?: return null

        // Check TTL expiration
        val now = System.currentTimeMillis()
        if (now - entry.timestamp > ttlMillis) {
            cache.remove(key)
            accessOrder.remove(key)
            return null
        }

        // Update access order
        synchronized(this) {
            accessOrder[key] = ++accessCounter
        }

        return entry.value
    }

    /**
     * Put value into cache.
     *
     * Evicts oldest entry if maxEntries exceeded.
     */
    fun put(
        key: K,
        value: V,
    ) {
        val now = System.currentTimeMillis()

        synchronized(this) {
            // Evict oldest if at capacity and key is new
            if (cache.size >= maxEntries && !cache.containsKey(key)) {
                val oldestKey = accessOrder.entries.minByOrNull { it.value }?.key
                if (oldestKey != null) {
                    cache.remove(oldestKey)
                    accessOrder.remove(oldestKey)
                }
            }

            cache[key] = CacheEntry(value, now)
            accessOrder[key] = ++accessCounter
        }
    }

    /**
     * Clear all entries from cache.
     */
    fun clear() {
        cache.clear()
        accessOrder.clear()
    }

    /**
     * Get current cache size.
     */
    val size: Int get() = cache.size
}

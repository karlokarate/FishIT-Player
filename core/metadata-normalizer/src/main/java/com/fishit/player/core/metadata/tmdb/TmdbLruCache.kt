package com.fishit.player.core.metadata.tmdb

import java.util.concurrent.ConcurrentHashMap

/**
 * Simple LRU cache with TTL for TMDB responses.
 *
 * Per TMDB_ENRICHMENT_CONTRACT.md T-13, T-14:
 * - Bounded max size (256 entries default) for FireTV safety
 * - TTL-based expiration
 * - Thread-safe via ConcurrentHashMap
 *
 * @param maxSize Maximum number of entries (default: 256)
 * @param ttlMs Time-to-live in milliseconds
 */
class TmdbLruCache<K : Any, V : Any>(
    private val maxSize: Int = 256,
    private val ttlMs: Long,
) {
    private data class Entry<V>(
        val value: V,
        val expiresAt: Long,
    )

    private val cache = ConcurrentHashMap<K, Entry<V>>()
    private val accessOrder = LinkedHashMap<K, Long>(maxSize, 0.75f, true)

    /**
     * Get value from cache if present and not expired.
     */
    fun get(key: K): V? {
        val entry = cache[key] ?: return null

        // Check TTL
        if (System.currentTimeMillis() > entry.expiresAt) {
            cache.remove(key)
            synchronized(accessOrder) {
                accessOrder.remove(key)
            }
            return null
        }

        // Update access order
        synchronized(accessOrder) {
            accessOrder[key] = System.currentTimeMillis()
        }

        return entry.value
    }

    /**
     * Put value into cache with TTL.
     */
    fun put(key: K, value: V) {
        val now = System.currentTimeMillis()
        val expiresAt = now + ttlMs

        // Evict oldest if at capacity
        synchronized(accessOrder) {
            while (accessOrder.size >= maxSize) {
                val oldest = accessOrder.keys.firstOrNull() ?: break
                accessOrder.remove(oldest)
                cache.remove(oldest)
            }
            accessOrder[key] = now
        }

        cache[key] = Entry(value, expiresAt)
    }

    /**
     * Remove value from cache.
     */
    fun remove(key: K) {
        cache.remove(key)
        synchronized(accessOrder) {
            accessOrder.remove(key)
        }
    }

    /**
     * Clear all entries.
     */
    fun clear() {
        cache.clear()
        synchronized(accessOrder) {
            accessOrder.clear()
        }
    }

    /**
     * Get current cache size.
     */
    val size: Int
        get() = cache.size

    /**
     * Check if cache contains key (and not expired).
     */
    fun contains(key: K): Boolean = get(key) != null

    companion object {
        /** 7 days in milliseconds */
        const val TTL_7_DAYS_MS = 7L * 24 * 60 * 60 * 1000

        /** 24 hours in milliseconds */
        const val TTL_24_HOURS_MS = 24L * 60 * 60 * 1000

        /** Default max size (FireTV-safe) */
        const val DEFAULT_MAX_SIZE = 256
    }
}

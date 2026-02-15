package com.fishit.player.infra.transport.xtream

/**
 * Interface for caching resolved Xtream ports.
 *
 * **Status:** Stub interface for Sprint 5.
 * Implementation deferred to Sprint 6 (caching layer).
 */
interface XtreamPortStore {
    /**
     * Get cached port for a given host.
     *
     * @param host Server hostname
     * @return Cached port or null if not found/expired
     */
    fun get(host: String): Int?

    /**
     * Store resolved port in cache.
     *
     * @param host Server hostname
     * @param port Resolved port
     */
    fun put(
        host: String,
        port: Int,
    )

    /**
     * Clear all cached ports.
     */
    fun clear()
}

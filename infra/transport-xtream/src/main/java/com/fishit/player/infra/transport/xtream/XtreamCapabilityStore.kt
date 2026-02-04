package com.fishit.player.infra.transport.xtream

import com.fishit.player.infra.transport.xtream.model.XtreamCapabilities

/**
 * Interface for caching Xtream API capabilities.
 * 
 * **Status:** Stub interface for Sprint 5.
 * Implementation deferred to Sprint 6 (caching layer).
 */
interface XtreamCapabilityStore {
    /**
     * Get cached capabilities for a given cache key.
     * 
     * @param key Cache key (typically: "scheme://username@host:port")
     * @return Cached capabilities or null if not found/expired
     */
    fun get(key: String): XtreamCapabilities?
    
    /**
     * Store capabilities in cache.
     * 
     * @param capabilities Capabilities to cache
     */
    fun put(capabilities: XtreamCapabilities)
    
    /**
     * Clear all cached capabilities.
     */
    fun clear()
}

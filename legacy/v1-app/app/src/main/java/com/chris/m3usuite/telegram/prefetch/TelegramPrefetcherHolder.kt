package com.chris.m3usuite.telegram.prefetch

/**
 * Simple holder for the global TelegramThumbPrefetcher instance.
 * Allows other components to clear the prefetch cache when needed.
 */
object TelegramPrefetcherHolder {
    private var prefetcher: TelegramThumbPrefetcher? = null

    fun set(instance: TelegramThumbPrefetcher) {
        prefetcher = instance
    }

    fun clear() {
        prefetcher?.clearCache()
    }
}

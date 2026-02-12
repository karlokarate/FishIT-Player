package com.fishit.player.infra.cache

/**
 * Centralized cache management interface.
 *
 * **Contract:**
 * - All cache size calculations run on IO dispatcher (no main-thread IO)
 * - All cache clearing operations run on IO dispatcher
 * - All operations log via UnifiedLog (no secrets in log messages)
 * - This is the ONLY place where file-system cache operations should occur
 *
 * **Architecture:**
 * - Interface defined in infra/cache
 * - Implementation (DefaultCacheManager) also in infra/cache
 * - Consumers (DebugInfoProvider, Settings) inject via Hilt
 *
 * **Thread Safety:**
 * - All methods are suspend functions that internally use Dispatchers.IO
 * - Callers may invoke from any dispatcher
 */
interface CacheManager {
    /**
     * Get the size of Telegram/Telegram API cache in bytes.
     *
     * Includes:
     * - Telegram API database directory (telegram/)
     * - Telegram API files directory (telegram-files/)
     *
     * @return Size in bytes, or 0 if unable to calculate
     */
    suspend fun getTelegramCacheSizeBytes(): Long

    /**
     * Get the size of the image cache (Coil) in bytes.
     *
     * Includes:
     * - Disk cache size
     *
     * @return Size in bytes, or 0 if unable to calculate
     */
    suspend fun getImageCacheSizeBytes(): Long

    /**
     * Get the size of the database (ObjectBox) in bytes.
     *
     * @return Size in bytes, or 0 if unable to calculate
     */
    suspend fun getDatabaseSizeBytes(): Long

    /**
     * Clear the Telegram/Telegram API file cache.
     *
     * **Note:** This clears ONLY the files cache (downloaded media),
     * NOT the database. This preserves chat history while reclaiming space.
     *
     * @return true if successful, false on error
     */
    suspend fun clearTelegramCache(): Boolean

    /**
     * Clear the image cache (Coil disk + memory).
     *
     * @return true if successful, false on error
     */
    suspend fun clearImageCache(): Boolean
}

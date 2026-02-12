package com.fishit.player.feature.settings

import kotlinx.coroutines.flow.Flow

/**
 * Provider for debug/diagnostics information.
 *
 * This interface abstracts all external dependencies needed by DebugViewModel
 * to display real system information instead of stubs.
 *
 * **Components Provided:**
 * - Connection status (Telegram, Xtream)
 * - Cache sizes (Telegram API, Image, Database)
 * - Content counts (media items per source)
 *
 * **Architecture:**
 * - Interface lives in feature/settings (owned by DebugViewModel)
 * - Implementation lives in app-v2 (has access to all infra modules)
 * - Provided via Hilt @Binds
 */
interface DebugInfoProvider {
    // =========================================================================
    // Connection Status
    // =========================================================================

    /**
     * Observe Telegram connection status.
     *
     * @return Flow of connection info (connected, user name)
     */
    fun observeTelegramConnection(): Flow<ConnectionInfo>

    /**
     * Observe Xtream connection status.
     *
     * @return Flow of connection info (connected, server URL)
     */
    fun observeXtreamConnection(): Flow<ConnectionInfo>

    // =========================================================================
    // Cache Sizes
    // =========================================================================

    /**
     * Calculate Telegram API cache size.
     *
     * Scans the Telegram API files directory for total storage usage.
     *
     * @return Size in bytes, or null if unavailable
     */
    suspend fun getTelegramCacheSize(): Long?

    /**
     * Calculate image cache size.
     *
     * Returns Coil disk cache size if configured.
     *
     * @return Size in bytes, or null if unavailable
     */
    suspend fun getImageCacheSize(): Long?

    /**
     * Calculate database size.
     *
     * Returns ObjectBox database file size.
     *
     * @return Size in bytes, or null if unavailable
     */
    suspend fun getDatabaseSize(): Long?

    // =========================================================================
    // Content Counts
    // =========================================================================

    /**
     * Observe content counts from all sources.
     *
     * @return Flow of content statistics
     */
    fun observeContentCounts(): Flow<ContentCounts>

    // =========================================================================
    // API Credential Status
    // =========================================================================

    /**
     * Get Telegram API credential configuration status.
     *
     * **Important distinction:**
     * - Credentials CONFIGURED = TG_API_ID and TG_API_HASH are set (BuildConfig)
     * - Credentials MISSING = App was built without Telegram API credentials
     * - Authorization state is separate (user may be logged in or not)
     *
     * @return Status of Telegram API credentials
     */
    fun getTelegramCredentialStatus(): TelegramCredentialStatus

    /**
     * Get TMDB API key configuration status.
     *
     * @return true if TMDB_API_KEY is configured in BuildConfig
     */
    fun isTmdbApiKeyConfigured(): Boolean

    // =========================================================================
    // Cache Actions
    // =========================================================================

    /**
     * Clear Telegram/Telegram API cache.
     *
     * Deletes downloaded files but preserves database.
     *
     * @return true if successful
     */
    suspend fun clearTelegramCache(): Boolean

    /**
     * Clear image cache.
     *
     * Clears Coil disk cache.
     *
     * @return true if successful
     */
    suspend fun clearImageCache(): Boolean
}

/**
 * Connection status info.
 */
data class ConnectionInfo(
    val isConnected: Boolean,
    val details: String? = null,
)

/**
 * Telegram API credential status.
 *
 * **Note:** This is separate from authorization state!
 * - Credentials = TG_API_ID/TG_API_HASH from BuildConfig
 * - Authorization = User logged in via Telegram API
 */
data class TelegramCredentialStatus(
    /** Whether TG_API_ID and TG_API_HASH are configured (non-empty) */
    val isConfigured: Boolean,
    /** Human-readable status message */
    val statusMessage: String,
)

/**
 * Content counts from all sources.
 */
data class ContentCounts(
    val telegramMediaCount: Int = 0,
    val xtreamVodCount: Int = 0,
    val xtreamSeriesCount: Int = 0,
    val xtreamLiveCount: Int = 0,
)

/**
 * Format bytes to human-readable string (e.g., "128 MB").
 */
fun Long.formatAsSize(): String {
    if (this < 1024) return "$this B"
    val kb = this / 1024.0
    if (kb < 1024) return String.format("%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format("%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format("%.2f GB", gb)
}

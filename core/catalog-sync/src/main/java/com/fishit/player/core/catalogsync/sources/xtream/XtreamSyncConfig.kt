// Module: core/catalog-sync/sources/xtream
// Unified Xtream sync configuration implementing BaseSyncConfig

package com.fishit.player.core.catalogsync.sources.xtream

import com.fishit.player.core.model.sync.BaseSyncConfig
import com.fishit.player.core.model.sync.DeviceProfile

/**
 * Unified configuration for Xtream catalog synchronization.
 *
 * Replaces 6 scattered sync methods with ONE configurable entry point:
 *
 * **Old (6 methods):**
 * - `syncXtreamCatalog()` - VOD only
 * - `syncXtreamSeries()` - Series only
 * - `syncXtreamLive()` - Live only
 * - `syncXtreamBuffered()` - All with Channel buffer
 * - `syncXtreamEpisodes()` - Episodes only
 * - `syncXtreamIncremental()` - Incremental
 *
 * **New (1 method):**
 * - `XtreamSyncService.sync(config)` - Everything configurable
 *
 * **TiviMate Pattern:** Like TiviMate's unified playlist sync,
 * a single well-designed config handles all scenarios.
 *
 * @property accountKey Unique identifier for the Xtream source account
 * @property deviceProfile Device profile for adaptive buffer/consumer tuning
 * @property forceFullSync Skip incremental checks, do full refresh
 * @property syncVod Include VOD movies in sync
 * @property syncSeries Include series containers in sync
 * @property syncLive Include live channels in sync
 * @property syncEpisodes Include individual episode info (expensive)
 * @property vodCategoryIds Specific VOD categories to sync (empty = all)
 * @property seriesCategoryIds Specific series categories to sync (empty = all)
 * @property liveCategoryIds Specific live categories to sync (empty = all)
 * @property enableCheckpoints Enable checkpoint persistence for resumable sync
 * @property enableTelemetry Enable performance metrics collection
 * @property bufferSize Override buffer size (null = auto from profile)
 * @property consumerCount Override consumer count (null = auto from profile)
 */
data class XtreamSyncConfig(
    // === Required ===
    override val accountKey: String,
    // === Device Optimization ===
    override val deviceProfile: DeviceProfile = DeviceProfile.AUTO,
    override val forceFullSync: Boolean = false,
    // === Content Selection (TiviMate-style) ===
    val syncVod: Boolean = true,
    val syncSeries: Boolean = true,
    val syncLive: Boolean = true,
    val syncEpisodes: Boolean = false, // Expensive, off by default
    // === Category Filtering (Premium feature) ===
    val vodCategoryIds: Set<String> = emptySet(),
    val seriesCategoryIds: Set<String> = emptySet(),
    val liveCategoryIds: Set<String> = emptySet(),
    // === Advanced Options ===
    val enableCheckpoints: Boolean = true,
    val enableTelemetry: Boolean = true,
    val bufferSize: Int? = null, // null = auto from DeviceProfile
    val consumerCount: Int? = null, // null = auto from DeviceProfile
    // === Episode Options ===
    val episodeParallelism: Int = 3,
    val excludeSeriesIds: Set<Int> = emptySet(), // For resumable episode sync
) : BaseSyncConfig {
    companion object {
        /**
         * Full catalog sync - everything enabled.
         *
         * Use when: First sync, user manual refresh, weekly resync
         */
        fun fullSync(accountKey: String): XtreamSyncConfig =
            XtreamSyncConfig(
                accountKey = accountKey,
                forceFullSync = true,
                syncVod = true,
                syncSeries = true,
                syncLive = true,
                syncEpisodes = false, // Still expensive by default
            )

        /**
         * Incremental sync - changes only.
         *
         * Use when: Background periodic sync, app startup
         */
        fun incremental(accountKey: String): XtreamSyncConfig =
            XtreamSyncConfig(
                accountKey = accountKey,
                forceFullSync = false,
                syncVod = true,
                syncSeries = true,
                syncLive = true,
                syncEpisodes = false,
            )

        /**
         * Live-only quick sync.
         *
         * Use when: User navigating to Live TV screen
         */
        fun liveOnly(accountKey: String): XtreamSyncConfig =
            XtreamSyncConfig(
                accountKey = accountKey,
                syncVod = false,
                syncSeries = false,
                syncLive = true,
                syncEpisodes = false,
            )

        /**
         * VOD-only sync with optional categories.
         *
         * Use when: User browsing Movies screen
         */
        fun vodOnly(
            accountKey: String,
            categoryIds: Set<String> = emptySet(),
        ): XtreamSyncConfig =
            XtreamSyncConfig(
                accountKey = accountKey,
                syncVod = true,
                syncSeries = false,
                syncLive = false,
                syncEpisodes = false,
                vodCategoryIds = categoryIds,
            )

        /**
         * Series-only sync with episodes.
         *
         * Use when: User browsing Series screen with details needed
         */
        fun seriesWithEpisodes(
            accountKey: String,
            categoryIds: Set<String> = emptySet(),
        ): XtreamSyncConfig =
            XtreamSyncConfig(
                accountKey = accountKey,
                syncVod = false,
                syncSeries = true,
                syncLive = false,
                syncEpisodes = true,
                seriesCategoryIds = categoryIds,
            )

        /**
         * Category-filtered sync.
         *
         * Use when: User has configured category whitelist in settings
         */
        fun withCategories(
            accountKey: String,
            vodIds: Set<String> = emptySet(),
            seriesIds: Set<String> = emptySet(),
            liveIds: Set<String> = emptySet(),
        ): XtreamSyncConfig =
            XtreamSyncConfig(
                accountKey = accountKey,
                syncVod = vodIds.isNotEmpty() || vodIds.isEmpty(),
                syncSeries = seriesIds.isNotEmpty() || seriesIds.isEmpty(),
                syncLive = liveIds.isNotEmpty() || liveIds.isEmpty(),
                vodCategoryIds = vodIds,
                seriesCategoryIds = seriesIds,
                liveCategoryIds = liveIds,
            )

        /**
         * Minimal memory config for constrained devices.
         *
         * Use when: FireTV Stick 4K, low-RAM devices
         */
        fun lowMemory(accountKey: String): XtreamSyncConfig =
            XtreamSyncConfig(
                accountKey = accountKey,
                deviceProfile = DeviceProfile.FIRETV_STICK,
                syncVod = true,
                syncSeries = true,
                syncLive = true,
                syncEpisodes = false,
                bufferSize = 300,
                consumerCount = 1,
            )
    }

    /**
     * Check if any content type is enabled for sync.
     */
    val hasContentToSync: Boolean
        get() = syncVod || syncSeries || syncLive

    /**
     * Check if category filtering is active for any content type.
     */
    val hasCategoryFilters: Boolean
        get() =
            vodCategoryIds.isNotEmpty() ||
                seriesCategoryIds.isNotEmpty() ||
                liveCategoryIds.isNotEmpty()

    /**
     * Get effective buffer size (from config or profile).
     */
    fun getEffectiveBufferSize(profile: DeviceProfile): Int = bufferSize ?: profile.bufferCapacity

    /**
     * Get effective consumer count (from config or profile).
     */
    fun getEffectiveConsumerCount(profile: DeviceProfile): Int = consumerCount ?: profile.consumerCount
}

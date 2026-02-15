/*
 * Copyright (C) 2024-2026 FishIT-Player
 * SPDX-License-Identifier: Apache-2.0
 *
 * Module: core/model
 * Layer: 1 (Foundation - Zero Dependencies)
 *
 * This file is part of the unified sync architecture.
 * See: contracts/CATALOG_SYNC_WORKERS_CONTRACT_V2.md
 */
package com.fishit.player.core.model.sync

/**
 * Base interface for all sync configurations across pipelines.
 *
 * This interface defines the common properties required by ALL sync operations,
 * regardless of source (Xtream, Telegram, IO, Audiobook). Source-specific
 * configurations extend this interface with additional properties.
 *
 * ## Design Principle: TiviMate Pattern
 * TiviMate uses a single `syncPlaylist(config)` method with configuration objects.
 * We adopt this pattern: ONE sync method per source, configured via typed config objects.
 *
 * ## Implementations
 * - `XtreamSyncConfig` - Xtream IPTV sources (core/catalog-sync/sources/xtream)
 * - `TelegramSyncConfig` - Telegram channels (core/catalog-sync/sources/telegram)
 * - `LocalSyncConfig` - Local file system (future)
 *
 * ## Usage
 * ```kotlin
 * // Xtream-specific configuration
 * val xtreamConfig = XtreamSyncConfig(
 *     accountKey = "user@iptv.server.com",
 *     deviceProfile = DeviceProfile.AUTO,
 *     forceFullSync = false,
 *     syncLive = true,
 *     syncVod = true,
 *     syncSeries = true,
 * )
 * xtreamSyncService.sync(xtreamConfig)
 *
 * // Telegram-specific configuration
 * val telegramConfig = TelegramSyncConfig(
 *     accountKey = "telegram:+1234567890",
 *     chatIds = setOf(-100123456789L),
 * )
 * telegramSyncService.sync(telegramConfig)
 * ```
 *
 * @since v2 Unified Sync Architecture
 */
interface BaseSyncConfig {
    /**
     * Unique identifier for the account/source being synced.
     *
     * Used for:
     * - Checkpoint storage (incremental sync)
     * - Progress tracking
     * - Multi-account isolation
     *
     * Format varies by source:
     * - Xtream: `"username@host:port"` or connection key
     * - Telegram: `"telegram:phoneNumber"` or `"telegram:userId"`
     * - Local: `"local:path"`
     */
    val accountKey: String

    /**
     * Device profile for adaptive sync parameters.
     *
     * When set to [DeviceProfile.AUTO], the sync service will detect
     * the device type at runtime and select appropriate buffer sizes
     * and consumer counts.
     *
     * Explicitly setting a profile is useful for testing or when
     * auto-detection produces suboptimal results.
     */
    val deviceProfile: DeviceProfile

    /**
     * Force a full sync, ignoring any stored checkpoints.
     *
     * When `true`:
     * - All items are re-fetched from source
     * - Existing checkpoints are cleared
     * - No incremental/delta optimization
     *
     * Use cases:
     * - User explicitly requests "Refresh All"
     * - Source reports major catalog change
     * - Debugging sync issues
     */
    val forceFullSync: Boolean
}

/**
 * Extension to check if this config allows incremental sync.
 */
val BaseSyncConfig.supportsIncremental: Boolean
    get() = !forceFullSync && accountKey.isNotBlank()

/**
 * Extension to generate a checkpoint key prefix for this config.
 */
fun BaseSyncConfig.checkpointKeyPrefix(): String = accountKey.replace(":", "_").replace("@", "_")

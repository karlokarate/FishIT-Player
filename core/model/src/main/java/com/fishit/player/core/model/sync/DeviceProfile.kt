/*
 * Copyright (C) 2024-2026 FishIT-Player
 * SPDX-License-Identifier: Apache-2.0
 *
 * Module: core/model
 * Layer: 1 (Foundation - Zero Dependencies)
 *
 * This file is part of the unified sync architecture.
 * See: docs/v2/XTREAM_SYNC_REFACTORING_PLAN.md
 */
package com.fishit.player.core.model.sync

/**
 * Device profile for adaptive sync configuration.
 *
 * Each profile defines recommended buffer sizes and consumer counts
 * optimized for the device's memory and processing capabilities.
 * The actual detection happens in `core/sync-common` via [DeviceProfileDetector].
 *
 * ## TiviMate Pattern
 * Similar to TiviMate's adaptive sync, we auto-tune parameters based on device class
 * rather than exposing complex settings to users.
 *
 * ## Usage
 * ```kotlin
 * val config = XtreamSyncConfig(
 *     accountKey = "user@server.com",
 *     deviceProfile = DeviceProfile.AUTO, // Recommended
 * )
 * // DeviceProfileDetector resolves AUTO → actual profile at runtime
 * ```
 *
 * @since v2 Unified Sync Architecture
 */
enum class DeviceProfile(
    /**
     * Recommended channel buffer capacity.
     * Higher values = more memory but smoother processing.
     */
    val bufferCapacity: Int,

    /**
     * Number of concurrent consumer coroutines.
     * Higher values = faster processing but more CPU/memory pressure.
     */
    val consumerCount: Int,

    /**
     * Maximum items to batch for DB writes.
     * Tuned for ObjectBox performance on each device class.
     */
    val dbBatchSize: Int,

    /**
     * Memory budget in MB for sync operation.
     * Used for adaptive throttling when approaching limit.
     */
    val memoryBudgetMb: Int,
) {
    /**
     * Auto-detect device profile at runtime.
     * Resolved by [DeviceProfileDetector] in `core/sync-common`.
     *
     * Placeholder values are overridden during detection.
     */
    AUTO(
        bufferCapacity = 0,
        consumerCount = 0,
        dbBatchSize = 0,
        memoryBudgetMb = 0,
    ),

    /**
     * High-end phones with ≥6GB RAM (Pixel 7+, Galaxy S21+, etc.)
     */
    PHONE_HIGH_RAM(
        bufferCapacity = 1000,
        consumerCount = 3,
        dbBatchSize = 100,
        memoryBudgetMb = 256,
    ),

    /**
     * Budget phones with 3-4GB RAM.
     */
    PHONE_LOW_RAM(
        bufferCapacity = 500,
        consumerCount = 2,
        dbBatchSize = 50,
        memoryBudgetMb = 128,
    ),

    /**
     * Fire TV Stick (all generations) - constrained memory.
     */
    FIRETV_STICK(
        bufferCapacity = 500,
        consumerCount = 2,
        dbBatchSize = 35,
        memoryBudgetMb = 96,
    ),

    /**
     * Fire TV Cube - better than Stick but not Shield-level.
     */
    FIRETV_CUBE(
        bufferCapacity = 700,
        consumerCount = 2,
        dbBatchSize = 50,
        memoryBudgetMb = 160,
    ),

    /**
     * NVIDIA Shield TV - high-performance Android TV.
     */
    SHIELD_TV(
        bufferCapacity = 2000,
        consumerCount = 4,
        dbBatchSize = 150,
        memoryBudgetMb = 512,
    ),

    /**
     * Google TV / Chromecast with Google TV - mid-range.
     */
    CHROMECAST_GTV(
        bufferCapacity = 700,
        consumerCount = 2,
        dbBatchSize = 50,
        memoryBudgetMb = 128,
    ),

    /**
     * Android TV boxes (generic, assume mid-range).
     */
    ANDROID_TV_GENERIC(
        bufferCapacity = 800,
        consumerCount = 2,
        dbBatchSize = 75,
        memoryBudgetMb = 192,
    ),

    /**
     * Tablets (assume decent memory).
     */
    TABLET(
        bufferCapacity = 1200,
        consumerCount = 3,
        dbBatchSize = 100,
        memoryBudgetMb = 256,
    ),
    ;

    /**
     * Returns the effective values, resolving AUTO to the provided detected profile.
     */
    fun resolveOrDefault(detectedProfile: DeviceProfile = PHONE_HIGH_RAM): DeviceProfile {
        return if (this == AUTO) detectedProfile else this
    }

    companion object {
        /**
         * Fallback profile when detection fails or is unavailable.
         */
        val DEFAULT: DeviceProfile = PHONE_HIGH_RAM

        /**
         * Most constrained profile - use for memory-safe operations.
         */
        val MOST_CONSTRAINED: DeviceProfile = FIRETV_STICK
    }
}

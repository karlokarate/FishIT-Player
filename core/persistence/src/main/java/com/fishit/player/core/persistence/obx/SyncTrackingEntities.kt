package com.fishit.player.core.persistence.obx

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import io.objectbox.annotation.Unique

/**
 * Sync Tracking Entities for Incremental Sync System
 *
 * These entities support the 4-tier incremental sync architecture:
 * - Tier 1: ETag/304 caching (stored in [NX_SyncCheckpoint])
 * - Tier 2: Item count quick-check (stored in [NX_SyncCheckpoint])
 * - Tier 3: Timestamp filtering (stored in [NX_SyncCheckpoint])
 * - Tier 4: Fingerprint comparison (stored in [NX_ItemFingerprint])
 *
 * **Design Document:** docs/v2/INCREMENTAL_SYNC_DESIGN.md
 *
 * @since 2026-01 (Issue #621 - OBX PLATIN Refactor)
 */

// =============================================================================
// NX_SyncCheckpoint - Per-Account, Per-ContentType Sync State
// =============================================================================

/**
 * Tracks sync state for each content type per account.
 *
 * One checkpoint exists for each combination of:
 * - sourceType (xtream, telegram)
 * - accountId (Xtream account key)
 * - contentType (vod, series, live, clips)
 *
 * **Key format:** `<sourceType>:<accountId>:<contentType>`
 *
 * Used for:
 * - Timestamp filtering (lastSyncCompleteMs)
 * - ETag caching (etag, lastModified)
 * - Item count quick-check (itemCount)
 * - Sync statistics (newItemCount, updatedItemCount, deletedItemCount)
 *
 * @property checkpointKey Unique key for this checkpoint
 * @property sourceType Source type: "xtream", "telegram"
 * @property accountId Account identifier (Xtream account key)
 * @property contentType Content type: "vod", "series", "live", "clips"
 * @property lastSyncStartMs Timestamp when last sync started
 * @property lastSyncCompleteMs Timestamp when last sync completed successfully
 * @property lastSyncDurationMs Duration of last sync in milliseconds
 * @property etag HTTP ETag header from last response (if server supports)
 * @property lastModified HTTP Last-Modified header from last response
 * @property itemCount Total item count from last sync
 * @property newItemCount New items discovered in last sync
 * @property updatedItemCount Items updated (fingerprint changed) in last sync
 * @property deletedItemCount Items deleted (not seen) in last sync
 * @property wasIncrementalSync True if last sync was incremental
 * @property forcedFullSync True if last sync was forced full refresh
 * @property syncGeneration Incremented each sync for deletion detection
 * @property lastError Last error message (if sync failed)
 * @property consecutiveFailures Number of consecutive sync failures
 */
@Entity
data class NX_SyncCheckpoint(
    @Id var id: Long = 0,
    // === Key ===
    /** Unique checkpoint key: `<sourceType>:<accountId>:<contentType>` */
    @Unique
    @Index
    val checkpointKey: String = "",
    // === Source Identification ===
    /** Source type: "xtream" or "telegram" */
    @Index
    val sourceType: String = "",
    /** Account identifier (e.g., Xtream account key) */
    @Index
    val accountId: String = "",
    /** Content type: "vod", "series", "live", "clips" */
    @Index
    val contentType: String = "",
    // === Sync Timing ===
    /** When the last sync started (epoch ms) */
    var lastSyncStartMs: Long = 0,
    /** When the last sync completed successfully (epoch ms) */
    var lastSyncCompleteMs: Long = 0,
    /** Duration of last sync in milliseconds */
    var lastSyncDurationMs: Long = 0,
    // === HTTP Caching (Tier 1) ===
    /** ETag header from last response (null if not supported) */
    var etag: String? = null,
    /** Last-Modified header from last response */
    var lastModified: String? = null,
    // === Item Tracking (Tier 2 & 3) ===
    /** Total item count from last sync */
    var itemCount: Int = 0,
    /** New items discovered in last sync */
    var newItemCount: Int = 0,
    /** Items with changed fingerprint in last sync */
    var updatedItemCount: Int = 0,
    /** Items not seen in last sync (potential deletions) */
    var deletedItemCount: Int = 0,
    // === Sync Mode ===
    /** True if last sync was incremental (not full) */
    var wasIncrementalSync: Boolean = false,
    /** True if last sync was explicitly forced full */
    var forcedFullSync: Boolean = false,
    /** Sync generation counter for deletion detection */
    var syncGeneration: Long = 0,
    // === Error Tracking ===
    /** Last error message (null if last sync succeeded) */
    var lastError: String? = null,
    /** Number of consecutive sync failures */
    var consecutiveFailures: Int = 0,
) {
    companion object {
        /** Content type constant for VOD/Movies */
        const val CONTENT_TYPE_VOD = "vod"

        /** Content type constant for Series */
        const val CONTENT_TYPE_SERIES = "series"

        /** Content type constant for Live TV */
        const val CONTENT_TYPE_LIVE = "live"

        /** Content type constant for Clips */
        const val CONTENT_TYPE_CLIPS = "clips"

        /** Source type constant for Xtream */
        const val SOURCE_TYPE_XTREAM = "xtream"

        /** Source type constant for Telegram */
        const val SOURCE_TYPE_TELEGRAM = "telegram"

        /**
         * Build checkpoint key from components.
         */
        fun buildKey(
            sourceType: String,
            accountId: String,
            contentType: String,
        ): String = "$sourceType:$accountId:$contentType"
    }
}

// =============================================================================
// NX_ItemFingerprint - Per-Item Change Detection
// =============================================================================

/**
 * Stores fingerprint hashes for incremental sync change detection.
 *
 * Each item from a catalog source gets a fingerprint entry. On subsequent syncs,
 * we compare the new fingerprint with the stored one to detect changes.
 *
 * **Key format:** `<sourceType>:<accountId>:<contentType>:<itemId>`
 *
 * **Fingerprint calculation:**
 * - VOD: hash(streamId, name, added, categoryId, containerExtension, streamIcon)
 * - Series: hash(seriesId, name, lastModified, categoryId, cover, episodeCount)
 * - Live: hash(streamId, name, categoryId, streamIcon, epgChannelId)
 *
 * @property sourceKey Unique key for this item fingerprint
 * @property fingerprint Hash of key fields (computed by pipeline)
 * @property lastSeenMs Timestamp when item was last seen in sync
 * @property syncGeneration Sync generation when item was last seen
 * @property sourceType Source type for queries: "xtream", "telegram"
 * @property accountId Account ID for queries
 * @property contentType Content type for queries: "vod", "series", "live"
 */
@Entity
data class NX_ItemFingerprint(
    @Id var id: Long = 0,
    // === Key ===
    /** Unique item key: `<sourceType>:<accountId>:<contentType>:<itemId>` */
    @Unique
    @Index
    val sourceKey: String = "",
    // === Fingerprint ===
    /** Hash of key fields for change detection */
    var fingerprint: Int = 0,
    // === Tracking ===
    /** When this item was last seen in a sync (epoch ms) */
    var lastSeenMs: Long = 0,
    /** Sync generation when this item was last seen */
    var syncGeneration: Long = 0,
    // === Query Fields (denormalized for performance) ===
    /** Source type for bulk queries */
    @Index
    val sourceType: String = "",
    /** Account ID for bulk queries */
    @Index
    val accountId: String = "",
    /** Content type for bulk queries */
    @Index
    val contentType: String = "",
) {
    companion object {
        /**
         * Build source key from components.
         */
        fun buildKey(
            sourceType: String,
            accountId: String,
            contentType: String,
            itemId: String,
        ): String = "$sourceType:$accountId:$contentType:$itemId"
    }
}

package com.fishit.player.core.catalogsync

import com.fishit.player.core.persistence.obx.NX_SyncCheckpoint
import com.fishit.player.core.persistence.repository.FingerprintRepository
import com.fishit.player.core.persistence.repository.SyncCheckpointRepository
import com.fishit.player.infra.logging.UnifiedLog
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decides which sync strategy to use based on available data.
 *
 * Implements the 4-tier incremental sync decision tree:
 *
 * ```
 * Tier 1: ETag/304     → 100% skip if unchanged
 * Tier 2: Count check  → Fast gate if count differs
 * Tier 3: Timestamp    → Process only new items (added > lastSync)
 * Tier 4: Fingerprint  → Compare hashes for changed items
 * ```
 *
 * **Design:** docs/v2/INCREMENTAL_SYNC_DESIGN.md
 */
@Singleton
class IncrementalSyncDecider
    @Inject
    constructor(
        private val checkpointRepository: SyncCheckpointRepository,
        private val fingerprintRepository: FingerprintRepository,
    ) {
        companion object {
            private const val TAG = "IncrementalSyncDecider"

            /**
             * Minimum time between syncs before considering incremental.
             * Syncs within this window will always be full syncs.
             */
            private const val MIN_SYNC_INTERVAL_MS = 5 * 60 * 1000L // 5 minutes

            /**
             * Maximum time before forcing a full sync.
             * Even if incremental would work, refresh everything weekly.
             */
            private const val MAX_INCREMENTAL_AGE_MS = 7 * 24 * 60 * 60 * 1000L // 7 days
        }

        /**
         * Determine the best sync strategy for the given content type.
         *
         * @param sourceType Source type (e.g., "xtream")
         * @param accountId Account identifier
         * @param contentType Content type (e.g., "vod", "series", "live")
         * @param currentCount Current item count from API (null if not available)
         * @param currentEtag ETag from current response (null if not supported)
         * @param forceFullSync User explicitly requested full refresh
         * @return Recommended sync strategy
         */
        suspend fun decideSyncStrategy(
            sourceType: String,
            accountId: String,
            contentType: String,
            currentCount: Int? = null,
            currentEtag: String? = null,
            forceFullSync: Boolean = false,
        ): SyncStrategy {
            // Tier 0: User forced full sync
            if (forceFullSync) {
                UnifiedLog.i(TAG, "[$contentType] User forced full sync")
                return SyncStrategy.FullSync(reason = "User requested full refresh")
            }

            // Get checkpoint for this content type
            val checkpoint = checkpointRepository.getCheckpoint(sourceType, accountId, contentType)

            // First sync ever? Must be full.
            if (checkpoint == null || checkpoint.lastSyncCompleteMs == 0L) {
                UnifiedLog.i(TAG, "[$contentType] First sync - full required")
                return SyncStrategy.FullSync(reason = "First sync")
            }

            val lastSyncMs = checkpoint.lastSyncCompleteMs
            val nowMs = System.currentTimeMillis()
            val sinceSyncMs = nowMs - lastSyncMs

            // Too recent? Don't sync at all.
            if (sinceSyncMs < MIN_SYNC_INTERVAL_MS) {
                UnifiedLog.d(
                    TAG,
                    "[$contentType] Sync too recent (${sinceSyncMs}ms < ${MIN_SYNC_INTERVAL_MS}ms)",
                )
                return SyncStrategy.SkipSync(
                    reason = "Synced ${sinceSyncMs / 1000}s ago",
                    lastSyncMs = lastSyncMs,
                )
            }

            // Too old? Force full sync.
            if (sinceSyncMs > MAX_INCREMENTAL_AGE_MS) {
                UnifiedLog.i(TAG, "[$contentType] Last sync too old - full required")
                return SyncStrategy.FullSync(
                    reason = "Last sync ${sinceSyncMs / (24 * 60 * 60 * 1000)}d ago",
                )
            }

            // Tier 1: ETag check (if available)
            if (currentEtag != null && checkpoint.etag != null) {
                if (currentEtag == checkpoint.etag) {
                    UnifiedLog.i(TAG, "[$contentType] ETag match - skip sync (304)")
                    return SyncStrategy.SkipSync(
                        reason = "ETag unchanged",
                        lastSyncMs = lastSyncMs,
                    )
                } else {
                    UnifiedLog.d(TAG, "[$contentType] ETag changed - proceeding with sync")
                }
            }

            // Tier 2: Count quick-check
            if (currentCount != null) {
                val lastCount = checkpoint.itemCount
                if (currentCount != lastCount) {
                    val delta = currentCount - lastCount
                    UnifiedLog.i(
                        TAG,
                        "[$contentType] Item count changed ($lastCount → $currentCount, delta=$delta)",
                    )
                    // Count changed - but we can still use timestamp filtering
                }
            }

            // Tier 3: Timestamp filtering available
            UnifiedLog.i(
                TAG,
                "[$contentType] Using incremental sync (lastSync=${lastSyncMs}, " +
                    "generation=${checkpoint.syncGeneration})",
            )

            return SyncStrategy.IncrementalSync(
                lastSyncMs = lastSyncMs,
                lastItemCount = checkpoint.itemCount,
                syncGeneration = checkpoint.syncGeneration + 1,
                etag = checkpoint.etag,
            )
        }

        /**
         * Get fingerprint map for a content type.
         *
         * Used for Tier 4 comparison during processing.
         *
         * @return Map of itemId → fingerprint hash
         */
        suspend fun getFingerprints(
            sourceType: String,
            accountId: String,
            contentType: String,
        ): Map<String, Int> {
            return fingerprintRepository.getFingerprintsAsMap(sourceType, accountId, contentType)
        }

        /**
         * Analyze sync results for metrics.
         */
        fun analyzeSyncResults(
            totalItems: Int,
            newItems: Int,
            updatedItems: Int,
            unchangedItems: Int,
            deletedItems: Int,
            durationMs: Long,
        ): SyncAnalysis {
            val processedItems = newItems + updatedItems
            val skippedItems = unchangedItems
            val savingsPercent = if (totalItems > 0) {
                (skippedItems.toDouble() / totalItems * 100).toInt()
            } else 0

            return SyncAnalysis(
                totalItems = totalItems,
                newItems = newItems,
                updatedItems = updatedItems,
                unchangedItems = unchangedItems,
                deletedItems = deletedItems,
                processedItems = processedItems,
                skippedItems = skippedItems,
                savingsPercent = savingsPercent,
                durationMs = durationMs,
            )
        }
    }

/**
 * Recommended sync strategy from [IncrementalSyncDecider].
 */
sealed class SyncStrategy {
    /**
     * Skip sync entirely - content is up-to-date.
     */
    data class SkipSync(
        val reason: String,
        val lastSyncMs: Long,
    ) : SyncStrategy()

    /**
     * Perform full sync - download and process all items.
     */
    data class FullSync(
        val reason: String,
    ) : SyncStrategy()

    /**
     * Perform incremental sync - only process new/changed items.
     */
    data class IncrementalSync(
        val lastSyncMs: Long,
        val lastItemCount: Int,
        val syncGeneration: Long,
        val etag: String?,
    ) : SyncStrategy()
}

/**
 * Analysis of sync results.
 */
data class SyncAnalysis(
    val totalItems: Int,
    val newItems: Int,
    val updatedItems: Int,
    val unchangedItems: Int,
    val deletedItems: Int,
    val processedItems: Int,
    val skippedItems: Int,
    val savingsPercent: Int,
    val durationMs: Long,
) {
    override fun toString(): String =
        "SyncAnalysis(total=$totalItems, new=$newItems, updated=$updatedItems, " +
            "unchanged=$unchangedItems, deleted=$deletedItems, " +
            "savings=$savingsPercent%, duration=${durationMs}ms)"
}

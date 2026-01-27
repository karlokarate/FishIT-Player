package com.fishit.player.core.persistence.repository

import com.fishit.player.core.persistence.obx.NX_SyncCheckpoint
import com.fishit.player.core.persistence.obx.NX_SyncCheckpoint_
import com.fishit.player.infra.logging.UnifiedLog
import io.objectbox.BoxStore
import io.objectbox.kotlin.boxFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for sync checkpoint persistence.
 *
 * Manages [NX_SyncCheckpoint] entities for incremental sync tracking.
 * Each checkpoint tracks the state of a specific content type for a specific account.
 *
 * **Key format:** `<sourceType>:<accountId>:<contentType>`
 *
 * **Design:** docs/v2/INCREMENTAL_SYNC_DESIGN.md
 */
@Singleton
class SyncCheckpointRepository
    @Inject
    constructor(
        private val boxStore: BoxStore,
    ) {
        private val box by lazy { boxStore.boxFor<NX_SyncCheckpoint>() }

        companion object {
            private const val TAG = "SyncCheckpointRepo"
        }

        // =========================================================================
        // Read Operations
        // =========================================================================

        /**
         * Get checkpoint for a specific content type.
         *
         * @param sourceType Source type (e.g., "xtream")
         * @param accountId Account identifier
         * @param contentType Content type (e.g., "vod", "series", "live")
         * @return Checkpoint or null if not found
         */
        suspend fun getCheckpoint(
            sourceType: String,
            accountId: String,
            contentType: String,
        ): NX_SyncCheckpoint? = withContext(Dispatchers.IO) {
            val key = NX_SyncCheckpoint.buildKey(sourceType, accountId, contentType)
            box.query(NX_SyncCheckpoint_.checkpointKey.equal(key))
                .build()
                .use { it.findFirst() }
        }

        /**
         * Get all checkpoints for an account.
         *
         * @param sourceType Source type
         * @param accountId Account identifier
         * @return List of checkpoints for this account
         */
        suspend fun getCheckpointsForAccount(
            sourceType: String,
            accountId: String,
        ): List<NX_SyncCheckpoint> = withContext(Dispatchers.IO) {
            box.query(
                NX_SyncCheckpoint_.sourceType.equal(sourceType)
                    .and(NX_SyncCheckpoint_.accountId.equal(accountId)),
            ).build().use { it.find() }
        }

        /**
         * Get last sync timestamp for a content type.
         *
         * @return Epoch ms of last successful sync, or 0 if never synced
         */
        suspend fun getLastSyncTimestamp(
            sourceType: String,
            accountId: String,
            contentType: String,
        ): Long = withContext(Dispatchers.IO) {
            getCheckpoint(sourceType, accountId, contentType)?.lastSyncCompleteMs ?: 0L
        }

        /**
         * Get stored ETag for a content type.
         *
         * @return ETag string or null if not available
         */
        suspend fun getEtag(
            sourceType: String,
            accountId: String,
            contentType: String,
        ): String? = withContext(Dispatchers.IO) {
            getCheckpoint(sourceType, accountId, contentType)?.etag
        }

        /**
         * Get current sync generation for deletion detection.
         *
         * @return Current sync generation, or 0 if never synced
         */
        suspend fun getSyncGeneration(
            sourceType: String,
            accountId: String,
            contentType: String,
        ): Long = withContext(Dispatchers.IO) {
            getCheckpoint(sourceType, accountId, contentType)?.syncGeneration ?: 0L
        }

        // =========================================================================
        // Write Operations
        // =========================================================================

        /**
         * Record sync start.
         *
         * Creates checkpoint if not exists, updates lastSyncStartMs.
         */
        suspend fun recordSyncStart(
            sourceType: String,
            accountId: String,
            contentType: String,
        ): NX_SyncCheckpoint = withContext(Dispatchers.IO) {
            val key = NX_SyncCheckpoint.buildKey(sourceType, accountId, contentType)
            val existing = box.query(NX_SyncCheckpoint_.checkpointKey.equal(key))
                .build()
                .use { it.findFirst() }

            val checkpoint = existing?.copy(
                lastSyncStartMs = System.currentTimeMillis(),
            ) ?: NX_SyncCheckpoint(
                checkpointKey = key,
                sourceType = sourceType,
                accountId = accountId,
                contentType = contentType,
                lastSyncStartMs = System.currentTimeMillis(),
            )

            box.put(checkpoint)
            UnifiedLog.d(TAG, "Sync started: $key")
            checkpoint
        }

        /**
         * Record successful sync completion.
         *
         * Updates timestamps, counts, and increments sync generation.
         */
        suspend fun recordSyncComplete(
            sourceType: String,
            accountId: String,
            contentType: String,
            itemCount: Int,
            newItemCount: Int,
            updatedItemCount: Int,
            deletedItemCount: Int,
            etag: String? = null,
            lastModified: String? = null,
            wasIncremental: Boolean = false,
        ): NX_SyncCheckpoint = withContext(Dispatchers.IO) {
            val key = NX_SyncCheckpoint.buildKey(sourceType, accountId, contentType)
            val existing = box.query(NX_SyncCheckpoint_.checkpointKey.equal(key))
                .build()
                .use { it.findFirst() }

            val now = System.currentTimeMillis()
            val startMs = existing?.lastSyncStartMs ?: now

            val checkpoint = (existing ?: NX_SyncCheckpoint(
                checkpointKey = key,
                sourceType = sourceType,
                accountId = accountId,
                contentType = contentType,
            )).copy(
                lastSyncCompleteMs = now,
                lastSyncDurationMs = now - startMs,
                itemCount = itemCount,
                newItemCount = newItemCount,
                updatedItemCount = updatedItemCount,
                deletedItemCount = deletedItemCount,
                etag = etag,
                lastModified = lastModified,
                wasIncrementalSync = wasIncremental,
                syncGeneration = (existing?.syncGeneration ?: 0L) + 1,
                lastError = null,
                consecutiveFailures = 0,
            )

            box.put(checkpoint)
            UnifiedLog.i(
                TAG,
                "Sync complete: $key | items=$itemCount, new=$newItemCount, " +
                    "updated=$updatedItemCount, deleted=$deletedItemCount, " +
                    "duration=${checkpoint.lastSyncDurationMs}ms, " +
                    "incremental=$wasIncremental",
            )
            checkpoint
        }

        /**
         * Record sync failure.
         *
         * Updates error info and increments failure counter.
         */
        suspend fun recordSyncFailure(
            sourceType: String,
            accountId: String,
            contentType: String,
            error: String,
        ): NX_SyncCheckpoint = withContext(Dispatchers.IO) {
            val key = NX_SyncCheckpoint.buildKey(sourceType, accountId, contentType)
            val existing = box.query(NX_SyncCheckpoint_.checkpointKey.equal(key))
                .build()
                .use { it.findFirst() }

            val checkpoint = (existing ?: NX_SyncCheckpoint(
                checkpointKey = key,
                sourceType = sourceType,
                accountId = accountId,
                contentType = contentType,
            )).copy(
                lastError = error.take(500), // Truncate long errors
                consecutiveFailures = (existing?.consecutiveFailures ?: 0) + 1,
            )

            box.put(checkpoint)
            UnifiedLog.w(
                TAG,
                "Sync failed: $key | error=$error, failures=${checkpoint.consecutiveFailures}",
            )
            checkpoint
        }

        /**
         * Force full sync on next run by clearing ETag and timestamp.
         */
        suspend fun forceFullSync(
            sourceType: String,
            accountId: String,
            contentType: String,
        ) = withContext(Dispatchers.IO) {
            val key = NX_SyncCheckpoint.buildKey(sourceType, accountId, contentType)
            val existing = box.query(NX_SyncCheckpoint_.checkpointKey.equal(key))
                .build()
                .use { it.findFirst() }

            existing?.let {
                box.put(
                    it.copy(
                        etag = null,
                        lastModified = null,
                        lastSyncCompleteMs = 0,
                        forcedFullSync = true,
                    ),
                )
                UnifiedLog.i(TAG, "Forced full sync: $key")
            }
        }

        /**
         * Delete all checkpoints for an account.
         *
         * Called when account is removed.
         */
        suspend fun deleteCheckpointsForAccount(
            sourceType: String,
            accountId: String,
        ) = withContext(Dispatchers.IO) {
            val deleted = box.query(
                NX_SyncCheckpoint_.sourceType.equal(sourceType)
                    .and(NX_SyncCheckpoint_.accountId.equal(accountId)),
            ).build().use {
                val count = it.count()
                it.remove()
                count
            }
            UnifiedLog.i(TAG, "Deleted $deleted checkpoints for $sourceType:$accountId")
        }
    }

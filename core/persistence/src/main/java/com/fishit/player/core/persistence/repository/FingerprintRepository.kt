package com.fishit.player.core.persistence.repository

import com.fishit.player.core.persistence.obx.NX_ItemFingerprint
import com.fishit.player.core.persistence.obx.NX_ItemFingerprint_
import com.fishit.player.infra.logging.UnifiedLog
import io.objectbox.BoxStore
import io.objectbox.kotlin.boxFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for item fingerprint persistence.
 *
 * Manages [NX_ItemFingerprint] entities for incremental sync change detection.
 * Each fingerprint tracks the hash of an item's key fields for comparison.
 *
 * **Key format:** `<sourceType>:<accountId>:<contentType>:<itemId>`
 *
 * **Bulk Operations:**
 * - [getFingerprints] - Load all fingerprints for a content type (into memory map)
 * - [putFingerprints] - Bulk upsert fingerprints
 * - [markStaleItems] - Find items not seen in current sync (deletions)
 *
 * **Design:** contracts/CATALOG_SYNC_WORKERS_CONTRACT_V2.md
 */
@Singleton
class FingerprintRepository
    @Inject
    constructor(
        private val boxStore: BoxStore,
    ) {
        private val box by lazy { boxStore.boxFor<NX_ItemFingerprint>() }

        companion object {
            private const val TAG = "FingerprintRepo"
        }

        // =========================================================================
        // Read Operations
        // =========================================================================

        /**
         * Get fingerprint for a single item.
         *
         * @param sourceType Source type (e.g., "xtream")
         * @param accountId Account identifier
         * @param contentType Content type (e.g., "vod")
         * @param itemId Item identifier (e.g., stream_id)
         * @return Fingerprint or null if not found
         */
        suspend fun getFingerprint(
            sourceType: String,
            accountId: String,
            contentType: String,
            itemId: String,
        ): NX_ItemFingerprint? =
            withContext(Dispatchers.IO) {
                val key = NX_ItemFingerprint.buildKey(sourceType, accountId, contentType, itemId)
                box
                    .query(NX_ItemFingerprint_.sourceKey.equal(key))
                    .build()
                    .use { it.findFirst() }
            }

        /**
         * Get all fingerprints for a content type as a Map.
         *
         * **Performance:** Loads all fingerprints into memory for O(1) lookup.
         * Use for content types with <100K items. For larger catalogs,
         * consider streaming comparison.
         *
         * @return Map of itemId → fingerprint hash
         */
        suspend fun getFingerprintsAsMap(
            sourceType: String,
            accountId: String,
            contentType: String,
        ): Map<String, Int> =
            withContext(Dispatchers.IO) {
                val prefix = "$sourceType:$accountId:$contentType:"

                box
                    .query(
                        NX_ItemFingerprint_.sourceType
                            .equal(sourceType)
                            .and(NX_ItemFingerprint_.accountId.equal(accountId))
                            .and(NX_ItemFingerprint_.contentType.equal(contentType)),
                    ).build()
                    .use { query ->
                        query.find().associate { fp ->
                            // Extract itemId from sourceKey (last segment after :)
                            val itemId = fp.sourceKey.removePrefix(prefix)
                            itemId to fp.fingerprint
                        }
                    }.also {
                        UnifiedLog.d(TAG, "Loaded ${it.size} fingerprints for $sourceType:$accountId:$contentType")
                    }
            }

        /**
         * Check if fingerprint has changed.
         *
         * @return true if item is new or changed, false if unchanged
         */
        suspend fun hasChanged(
            sourceType: String,
            accountId: String,
            contentType: String,
            itemId: String,
            newFingerprint: Int,
        ): Boolean =
            withContext(Dispatchers.IO) {
                val existing = getFingerprint(sourceType, accountId, contentType, itemId)
                existing?.fingerprint != newFingerprint
            }

        // =========================================================================
        // Write Operations
        // =========================================================================

        /**
         * Store or update a single fingerprint.
         *
         * @param syncGeneration Current sync generation (for deletion detection)
         */
        suspend fun putFingerprint(
            sourceType: String,
            accountId: String,
            contentType: String,
            itemId: String,
            fingerprint: Int,
            syncGeneration: Long,
        ) = withContext(Dispatchers.IO) {
            val key = NX_ItemFingerprint.buildKey(sourceType, accountId, contentType, itemId)
            val now = System.currentTimeMillis()

            val existing =
                box
                    .query(NX_ItemFingerprint_.sourceKey.equal(key))
                    .build()
                    .use { it.findFirst() }

            val entity =
                existing?.copy(
                    fingerprint = fingerprint,
                    lastSeenMs = now,
                    syncGeneration = syncGeneration,
                ) ?: NX_ItemFingerprint(
                    sourceKey = key,
                    fingerprint = fingerprint,
                    lastSeenMs = now,
                    syncGeneration = syncGeneration,
                    sourceType = sourceType,
                    accountId = accountId,
                    contentType = contentType,
                )

            box.put(entity)
        }

        /**
         * Bulk store/update fingerprints.
         *
         * **Performance:** Uses bulk put for efficiency.
         *
         * @param fingerprints Map of itemId → fingerprint hash
         * @param syncGeneration Current sync generation
         * @return Number of items stored
         */
        suspend fun putFingerprints(
            sourceType: String,
            accountId: String,
            contentType: String,
            fingerprints: Map<String, Int>,
            syncGeneration: Long,
        ): Int =
            withContext(Dispatchers.IO) {
                if (fingerprints.isEmpty()) return@withContext 0

                val now = System.currentTimeMillis()

                // Load existing fingerprints for this content type
                val existingMap =
                    box
                        .query(
                            NX_ItemFingerprint_.sourceType
                                .equal(sourceType)
                                .and(NX_ItemFingerprint_.accountId.equal(accountId))
                                .and(NX_ItemFingerprint_.contentType.equal(contentType)),
                        ).build()
                        .use { query ->
                            query.find().associateBy { it.sourceKey }
                        }

                val entities =
                    fingerprints.map { (itemId, fp) ->
                        val key = NX_ItemFingerprint.buildKey(sourceType, accountId, contentType, itemId)
                        val existing = existingMap[key]

                        existing?.copy(
                            fingerprint = fp,
                            lastSeenMs = now,
                            syncGeneration = syncGeneration,
                        ) ?: NX_ItemFingerprint(
                            sourceKey = key,
                            fingerprint = fp,
                            lastSeenMs = now,
                            syncGeneration = syncGeneration,
                            sourceType = sourceType,
                            accountId = accountId,
                            contentType = contentType,
                        )
                    }

                box.put(entities)
                UnifiedLog.d(
                    TAG,
                    "Stored ${entities.size} fingerprints for $sourceType:$accountId:$contentType",
                )
                entities.size
            }

        /**
         * Find items that were not seen in the current sync.
         *
         * Items with syncGeneration < currentGeneration are considered stale
         * (potentially deleted from the provider).
         *
         * @param currentGeneration The current sync generation
         * @return List of item IDs that are stale
         */
        suspend fun findStaleItems(
            sourceType: String,
            accountId: String,
            contentType: String,
            currentGeneration: Long,
        ): List<String> =
            withContext(Dispatchers.IO) {
                val prefix = "$sourceType:$accountId:$contentType:"

                box
                    .query(
                        NX_ItemFingerprint_.sourceType
                            .equal(sourceType)
                            .and(NX_ItemFingerprint_.accountId.equal(accountId))
                            .and(NX_ItemFingerprint_.contentType.equal(contentType))
                            .and(NX_ItemFingerprint_.syncGeneration.less(currentGeneration)),
                    ).build()
                    .use { query ->
                        query.find().map { fp ->
                            fp.sourceKey.removePrefix(prefix)
                        }
                    }.also {
                        if (it.isNotEmpty()) {
                            UnifiedLog.d(
                                TAG,
                                "Found ${it.size} stale items for $sourceType:$accountId:$contentType",
                            )
                        }
                    }
            }

        /**
         * Delete fingerprints for stale items.
         *
         * Called after confirming items are truly deleted (not just temporarily missing).
         *
         * @param itemIds List of item IDs to delete
         * @return Number of fingerprints deleted
         */
        suspend fun deleteFingerprints(
            sourceType: String,
            accountId: String,
            contentType: String,
            itemIds: List<String>,
        ): Int =
            withContext(Dispatchers.IO) {
                if (itemIds.isEmpty()) return@withContext 0

                var deleted = 0
                for (itemId in itemIds) {
                    val key = NX_ItemFingerprint.buildKey(sourceType, accountId, contentType, itemId)
                    deleted +=
                        box
                            .query(NX_ItemFingerprint_.sourceKey.equal(key))
                            .build()
                            .use { it.remove().toInt() }
                }

                UnifiedLog.d(
                    TAG,
                    "Deleted $deleted fingerprints for $sourceType:$accountId:$contentType",
                )
                deleted
            }

        /**
         * Delete all fingerprints for an account.
         *
         * Called when account is removed.
         */
        suspend fun deleteAllForAccount(
            sourceType: String,
            accountId: String,
        ) = withContext(Dispatchers.IO) {
            val deleted =
                box
                    .query(
                        NX_ItemFingerprint_.sourceType
                            .equal(sourceType)
                            .and(NX_ItemFingerprint_.accountId.equal(accountId)),
                    ).build()
                    .use {
                        val count = it.count()
                        it.remove()
                        count
                    }
            UnifiedLog.i(TAG, "Deleted $deleted fingerprints for $sourceType:$accountId")
        }

        /**
         * Prune old fingerprints.
         *
         * Removes fingerprints older than the specified threshold.
         * Called periodically to prevent unbounded growth.
         *
         * @param maxAgeMs Maximum age in milliseconds (default: 30 days)
         * @return Number of fingerprints pruned
         */
        suspend fun pruneOldFingerprints(
            maxAgeMs: Long = 30L * 24 * 60 * 60 * 1000, // 30 days
        ): Int =
            withContext(Dispatchers.IO) {
                val threshold = System.currentTimeMillis() - maxAgeMs

                val deleted =
                    box
                        .query(NX_ItemFingerprint_.lastSeenMs.less(threshold))
                        .build()
                        .use { it.remove() }

                if (deleted > 0) {
                    UnifiedLog.i(TAG, "Pruned $deleted old fingerprints")
                }
                deleted.toInt()
            }
    }

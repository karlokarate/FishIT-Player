package com.fishit.player.infra.data.nx.repository

import com.fishit.player.core.model.repository.NxEpgRepository
import com.fishit.player.core.model.repository.NxEpgRepository.EpgEntry
import com.fishit.player.core.model.repository.NxEpgRepository.NowNext
import com.fishit.player.core.persistence.ObjectBoxFlow.asFlow
import com.fishit.player.core.persistence.obx.NX_EpgEntry
import com.fishit.player.core.persistence.obx.NX_EpgEntry_
import io.objectbox.Box
import io.objectbox.BoxStore
import io.objectbox.query.QueryBuilder.StringOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ObjectBox implementation of [NxEpgRepository].
 *
 * Manages EPG (Electronic Program Guide) data for live TV channels.
 *
 * ## Key Design Decisions
 *
 * - Uses `epgEntryKey = "<channelWorkKey>:<startMs>"` for unique identification
 * - Supports batch operations for efficient EPG sync
 * - Auto-generates lowercase titleLower for search
 * - Links to NX_Work via channelWorkKey (not direct relation to avoid circular deps)
 */
@Singleton
class NxEpgRepositoryImpl
    @Inject
    constructor(
        boxStore: BoxStore,
    ) : NxEpgRepository {
        private val box: Box<NX_EpgEntry> = boxStore.boxFor(NX_EpgEntry::class.java)

        // ──────────────────────────────────────────────────────────────────────────
        // Reads
        // ──────────────────────────────────────────────────────────────────────────

        override suspend fun getNowNext(channelWorkKey: String): NowNext =
            withContext(Dispatchers.IO) {
                val nowMs = System.currentTimeMillis()

                // Find current program (startMs <= now < endMs)
                val now =
                    box
                        .query(NX_EpgEntry_.channelWorkKey.equal(channelWorkKey, StringOrder.CASE_SENSITIVE))
                        .and()
                        .lessOrEqual(NX_EpgEntry_.startMs, nowMs)
                        .and()
                        .greater(NX_EpgEntry_.endMs, nowMs)
                        .build()
                        .findFirst()
                        ?.toDomain()

                // Find next program (startMs > now, first one)
                val next =
                    box
                        .query(NX_EpgEntry_.channelWorkKey.equal(channelWorkKey, StringOrder.CASE_SENSITIVE))
                        .and()
                        .greater(NX_EpgEntry_.startMs, nowMs)
                        .order(NX_EpgEntry_.startMs)
                        .build()
                        .findFirst()
                        ?.toDomain()

                NowNext(channelWorkKey, now, next)
            }

        override fun observeNowNext(channelWorkKey: String): Flow<NowNext> =
            box
                .query(NX_EpgEntry_.channelWorkKey.equal(channelWorkKey, StringOrder.CASE_SENSITIVE))
                .build()
                .asFlow()
                .map { _ -> getNowNext(channelWorkKey) }

        override suspend fun getNowNextBatch(channelWorkKeys: List<String>): Map<String, NowNext> =
            withContext(Dispatchers.IO) {
                channelWorkKeys.associateWith { getNowNext(it) }
            }

        override fun observeNowNextBatch(channelWorkKeys: List<String>): Flow<Map<String, NowNext>> {
            // Observe all EPG entries and filter client-side
            return box
                .query()
                .build()
                .asFlow()
                .map { _ -> getNowNextBatch(channelWorkKeys) }
        }

        override suspend fun getSchedule(
            channelWorkKey: String,
            fromMs: Long,
            toMs: Long,
        ): List<EpgEntry> =
            withContext(Dispatchers.IO) {
                box
                    .query(NX_EpgEntry_.channelWorkKey.equal(channelWorkKey, StringOrder.CASE_SENSITIVE))
                    .and()
                    .greaterOrEqual(NX_EpgEntry_.endMs, fromMs)
                    .and()
                    .lessOrEqual(NX_EpgEntry_.startMs, toMs)
                    .order(NX_EpgEntry_.startMs)
                    .build()
                    .find()
                    .map { it.toDomain() }
            }

        override fun observeSchedule(
            channelWorkKey: String,
            fromMs: Long,
            toMs: Long,
        ): Flow<List<EpgEntry>> =
            box
                .query(NX_EpgEntry_.channelWorkKey.equal(channelWorkKey, StringOrder.CASE_SENSITIVE))
                .and()
                .greaterOrEqual(NX_EpgEntry_.endMs, fromMs)
                .and()
                .lessOrEqual(NX_EpgEntry_.startMs, toMs)
                .order(NX_EpgEntry_.startMs)
                .build()
                .asFlow()
                .map { entries -> entries.map { it.toDomain() } }

        override suspend fun searchByTitle(
            query: String,
            fromMs: Long,
            limit: Int,
        ): List<EpgEntry> =
            withContext(Dispatchers.IO) {
                val queryLower = query.lowercase()
                box
                    .query(NX_EpgEntry_.titleLower.contains(queryLower, StringOrder.CASE_INSENSITIVE))
                    .and()
                    .greaterOrEqual(NX_EpgEntry_.startMs, fromMs)
                    .order(NX_EpgEntry_.startMs)
                    .build()
                    .find(0, limit.toLong())
                    .map { it.toDomain() }
            }

        // ──────────────────────────────────────────────────────────────────────────
        // Writes
        // ──────────────────────────────────────────────────────────────────────────

        override suspend fun upsert(entry: EpgEntry): EpgEntry =
            withContext(Dispatchers.IO) {
                val nowMs = System.currentTimeMillis()
                val epgEntryKey = generateEpgEntryKey(entry.channelWorkKey, entry.startMs)

                // Find existing
                val existing =
                    box
                        .query(NX_EpgEntry_.epgEntryKey.equal(epgEntryKey, StringOrder.CASE_SENSITIVE))
                        .build()
                        .findFirst()

                val entity =
                    NX_EpgEntry(
                        id = existing?.id ?: 0,
                        epgEntryKey = epgEntryKey,
                        channelWorkKey = entry.channelWorkKey,
                        epgChannelId = entry.epgChannelId,
                        title = entry.title,
                        titleLower = entry.title.lowercase(),
                        startMs = entry.startMs,
                        endMs = entry.endMs,
                        description = entry.description,
                        category = entry.category,
                        iconUrl = entry.iconUrl,
                        createdAt = existing?.createdAt ?: nowMs,
                        updatedAt = nowMs,
                    )

                box.put(entity)
                entity.toDomain()
            }

        override suspend fun upsertBatch(entries: List<EpgEntry>): Int =
            withContext(Dispatchers.IO) {
                if (entries.isEmpty()) return@withContext 0

                val nowMs = System.currentTimeMillis()

                // Build map of existing entries by key for efficient lookup
                val keys = entries.map { generateEpgEntryKey(it.channelWorkKey, it.startMs) }
                val existingMap =
                    box
                        .query()
                        .`in`(NX_EpgEntry_.epgEntryKey, keys.toTypedArray(), StringOrder.CASE_SENSITIVE)
                        .build()
                        .find()
                        .associateBy { it.epgEntryKey }

                val entities =
                    entries.map { entry ->
                        val epgEntryKey = generateEpgEntryKey(entry.channelWorkKey, entry.startMs)
                        val existing = existingMap[epgEntryKey]

                        NX_EpgEntry(
                            id = existing?.id ?: 0,
                            epgEntryKey = epgEntryKey,
                            channelWorkKey = entry.channelWorkKey,
                            epgChannelId = entry.epgChannelId,
                            title = entry.title,
                            titleLower = entry.title.lowercase(),
                            startMs = entry.startMs,
                            endMs = entry.endMs,
                            description = entry.description,
                            category = entry.category,
                            iconUrl = entry.iconUrl,
                            createdAt = existing?.createdAt ?: nowMs,
                            updatedAt = nowMs,
                        )
                    }

                box.put(entities)
                entities.size
            }

        override suspend fun replaceForChannel(
            channelWorkKey: String,
            entries: List<EpgEntry>,
        ): Int =
            withContext(Dispatchers.IO) {
                // Delete existing entries for this channel
                deleteForChannel(channelWorkKey)

                // Insert new entries
                upsertBatch(entries)
            }

        override suspend fun deleteOlderThan(beforeMs: Long): Int =
            withContext(Dispatchers.IO) {
                val toDelete =
                    box
                        .query(NX_EpgEntry_.endMs.less(beforeMs))
                        .build()
                        .find()

                box.remove(toDelete)
                toDelete.size
            }

        override suspend fun deleteForChannel(channelWorkKey: String): Int =
            withContext(Dispatchers.IO) {
                val toDelete =
                    box
                        .query(NX_EpgEntry_.channelWorkKey.equal(channelWorkKey, StringOrder.CASE_SENSITIVE))
                        .build()
                        .find()

                box.remove(toDelete)
                toDelete.size
            }

        // ──────────────────────────────────────────────────────────────────────────
        // Maintenance
        // ──────────────────────────────────────────────────────────────────────────

        override suspend fun count(): Long =
            withContext(Dispatchers.IO) {
                box.count()
            }

        override suspend fun countForChannel(channelWorkKey: String): Long =
            withContext(Dispatchers.IO) {
                box
                    .query(NX_EpgEntry_.channelWorkKey.equal(channelWorkKey, StringOrder.CASE_SENSITIVE))
                    .build()
                    .count()
            }

        override suspend fun pruneExpired(maxAgeMs: Long): Int =
            withContext(Dispatchers.IO) {
                val cutoffMs = System.currentTimeMillis() - maxAgeMs
                deleteOlderThan(cutoffMs)
            }

        // ──────────────────────────────────────────────────────────────────────────
        // Internal Helpers
        // ──────────────────────────────────────────────────────────────────────────

        private fun generateEpgEntryKey(
            channelWorkKey: String,
            startMs: Long,
        ): String = "$channelWorkKey:$startMs"

        private fun NX_EpgEntry.toDomain(): EpgEntry =
            EpgEntry(
                epgEntryKey = epgEntryKey,
                channelWorkKey = channelWorkKey,
                epgChannelId = epgChannelId,
                title = title,
                startMs = startMs,
                endMs = endMs,
                description = description,
                category = category,
                iconUrl = iconUrl,
                createdAtMs = createdAt,
                updatedAtMs = updatedAt,
            )
    }

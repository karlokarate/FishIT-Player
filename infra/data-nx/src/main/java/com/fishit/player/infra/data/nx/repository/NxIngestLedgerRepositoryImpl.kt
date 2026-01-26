package com.fishit.player.infra.data.nx.repository

import com.fishit.player.core.model.repository.NxIngestLedgerRepository
import com.fishit.player.core.model.repository.NxIngestLedgerRepository.LedgerEntry
import com.fishit.player.core.model.repository.NxIngestLedgerRepository.LedgerState
import com.fishit.player.core.persistence.obx.NX_IngestLedger
import com.fishit.player.core.persistence.obx.NX_IngestLedger_
import com.fishit.player.infra.data.nx.mapper.toDomain
import com.fishit.player.infra.data.nx.mapper.toEntity
import io.objectbox.Box
import io.objectbox.BoxStore
import com.fishit.player.core.persistence.ObjectBoxFlow.asFlow
import io.objectbox.query.QueryBuilder.StringOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ObjectBox implementation of [NxIngestLedgerRepository].
 *
 * Audit trail for all ingest decisions - enforces "no silent drops" invariant.
 */
@Singleton
class NxIngestLedgerRepositoryImpl @Inject constructor(
    boxStore: BoxStore,
) : NxIngestLedgerRepository {

    private val box: Box<NX_IngestLedger> = boxStore.boxFor(NX_IngestLedger::class.java)

    // ──────────────────────────────────────────────────────────────────────────
    // Reads
    // ──────────────────────────────────────────────────────────────────────────

    override suspend fun get(ledgerKey: String): LedgerEntry? = withContext(Dispatchers.IO) {
        box.query(NX_IngestLedger_.sourceKey.equal(ledgerKey, StringOrder.CASE_SENSITIVE))
            .build()
            .findFirst()
            ?.toDomain()
    }

    override suspend fun shouldSkip(ledgerKey: String, nowMs: Long): Boolean = withContext(Dispatchers.IO) {
        val existing = box.query(NX_IngestLedger_.sourceKey.equal(ledgerKey, StringOrder.CASE_SENSITIVE))
            .build()
            .findFirst()
            ?: return@withContext false

        // Skip if already rejected or skipped and no TTL or TTL not expired
        val entry = existing.toDomain()
        when (entry.state) {
            LedgerState.REJECTED, LedgerState.SKIPPED -> {
                val ttl = entry.ttlUntilMs
                ttl == null || ttl > nowMs
            }
            LedgerState.ACCEPTED -> false
        }
    }

    override fun observeRecentRejected(limit: Int): Flow<List<LedgerEntry>> =
        box.query()
            .notEqual(NX_IngestLedger_.decision, "ACCEPTED", StringOrder.CASE_SENSITIVE)
            .orderDesc(NX_IngestLedger_.processedAt)
            .build()
            .asFlow()
            .map { list ->
                list.take(limit).map { it.toDomain() }
            }

    // ──────────────────────────────────────────────────────────────────────────
    // Writes
    // ──────────────────────────────────────────────────────────────────────────

    override suspend fun upsert(entry: LedgerEntry): LedgerEntry = withContext(Dispatchers.IO) {
        val existing = box.query(NX_IngestLedger_.sourceKey.equal(entry.ledgerKey, StringOrder.CASE_SENSITIVE))
            .build()
            .findFirst()

        val entity = if (existing != null) {
            existing.apply {
                decision = when (entry.state) {
                    LedgerState.ACCEPTED -> "ACCEPTED"
                    LedgerState.REJECTED -> "REJECTED"
                    LedgerState.SKIPPED -> "SKIPPED"
                }
                reasonCode = entry.reason.name
                resultWorkKey = entry.workKey
                processedAt = System.currentTimeMillis()
            }
        } else {
            entry.toEntity()
        }

        box.put(entity)
        entity.toDomain()
    }

    override suspend fun upsertBatch(entries: List<LedgerEntry>): List<LedgerEntry> = withContext(Dispatchers.IO) {
        val existingMap = entries.mapNotNull { entry ->
            box.query(NX_IngestLedger_.sourceKey.equal(entry.ledgerKey, StringOrder.CASE_SENSITIVE))
                .build()
                .findFirst()?.let { entry.ledgerKey to it }
        }.toMap()

        entries.map { entry ->
            val existing = existingMap[entry.ledgerKey]
            if (existing != null) {
                existing.apply {
                    decision = when (entry.state) {
                        LedgerState.ACCEPTED -> "ACCEPTED"
                        LedgerState.REJECTED -> "REJECTED"
                        LedgerState.SKIPPED -> "SKIPPED"
                    }
                    reasonCode = entry.reason.name
                    resultWorkKey = entry.workKey
                    processedAt = System.currentTimeMillis()
                }
            } else {
                entry.toEntity()
            }
        }.also { entities ->
            box.put(entities)
        }.map { it.toDomain() }
    }
}

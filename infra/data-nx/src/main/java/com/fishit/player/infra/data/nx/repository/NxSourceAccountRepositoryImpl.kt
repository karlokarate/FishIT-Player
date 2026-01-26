package com.fishit.player.infra.data.nx.repository

import com.fishit.player.core.model.repository.NxSourceAccountRepository
import com.fishit.player.core.model.repository.NxSourceAccountRepository.SourceAccount
import com.fishit.player.core.persistence.obx.NX_SourceAccount
import com.fishit.player.core.persistence.obx.NX_SourceAccount_
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
 * ObjectBox implementation of [NxSourceAccountRepository].
 *
 * Manages source account metadata (Telegram, Xtream, etc.).
 * Credentials are NOT stored here - only labels and status.
 */
@Singleton
class NxSourceAccountRepositoryImpl @Inject constructor(
    boxStore: BoxStore,
) : NxSourceAccountRepository {

    private val box: Box<NX_SourceAccount> = boxStore.boxFor(NX_SourceAccount::class.java)

    // ──────────────────────────────────────────────────────────────────────────
    // Reads
    // ──────────────────────────────────────────────────────────────────────────

    override fun observeAll(): Flow<List<SourceAccount>> =
        box.query()
            .order(NX_SourceAccount_.createdAt)
            .build()
            .asFlow()
            .map { list -> list.map { it.toDomain() } }

    override suspend fun get(accountKey: String): SourceAccount? = withContext(Dispatchers.IO) {
        box.query(NX_SourceAccount_.accountKey.equal(accountKey, StringOrder.CASE_SENSITIVE))
            .build()
            .findFirst()
            ?.toDomain()
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Writes
    // ──────────────────────────────────────────────────────────────────────────

    override suspend fun upsert(account: SourceAccount): SourceAccount = withContext(Dispatchers.IO) {
        val existing = box.query(NX_SourceAccount_.accountKey.equal(account.accountKey, StringOrder.CASE_SENSITIVE))
            .build()
            .findFirst()

        val entity = if (existing != null) {
            existing.apply {
                sourceType = account.sourceType.name
                displayName = account.label
                isActive = account.status == NxSourceAccountRepository.AccountStatus.ACTIVE
                syncStatus = when (account.status) {
                    NxSourceAccountRepository.AccountStatus.ACTIVE -> "OK"
                    NxSourceAccountRepository.AccountStatus.ERROR -> "ERROR"
                    NxSourceAccountRepository.AccountStatus.DISABLED -> "DISABLED"
                }
                syncError = account.lastErrorMessage
                updatedAt = System.currentTimeMillis()
            }
        } else {
            account.toEntity()
        }

        box.put(entity)
        entity.toDomain()
    }

    override suspend fun delete(accountKey: String): Boolean = withContext(Dispatchers.IO) {
        val deleted = box.query(NX_SourceAccount_.accountKey.equal(accountKey, StringOrder.CASE_SENSITIVE))
            .build()
            .remove()
        deleted > 0
    }
}

/**
 * TEMP IMPLEMENTATION NOTES (REMOVE AFTER IMPLEMENTATION)
 * -------------------------------------------------------
 * - DOMAIN interface only.
 * - Stores metadata only (label/status). Secrets live in Encrypted DataStore (NOT here).
 * - Implementation maps to NX_SourceAccount entity in infra/data-nx.
 */
package com.fishit.player.core.model.repository

import kotlinx.coroutines.flow.Flow

interface NxSourceAccountRepository {

    enum class AccountStatus { ACTIVE, ERROR, DISABLED }

    data class SourceAccount(
        val accountKey: String,
        val sourceType: NxWorkSourceRefRepository.SourceType,
        val label: String,
        val status: AccountStatus = AccountStatus.ACTIVE,
        val lastErrorCode: String? = null,
        val lastErrorMessage: String? = null,
        val createdAtMs: Long = 0L,
        val updatedAtMs: Long = 0L,
    )

    fun observeAll(): Flow<List<SourceAccount>>

    suspend fun get(accountKey: String): SourceAccount?

    suspend fun upsert(account: SourceAccount): SourceAccount

    suspend fun delete(accountKey: String): Boolean
}


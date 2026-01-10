/**
 * TEMP IMPLEMENTATION NOTES (REMOVE AFTER IMPLEMENTATION)
 * -------------------------------------------------------
 * - DOMAIN interface only.
 * - Redirects preserve user state when works are merged/deduped.
 * - Implementation maps to NX_WorkRedirect entity in infra/data-nx.
 */
package com.fishit.player.core.model.repository

import kotlinx.coroutines.flow.Flow

interface NxWorkRedirectRepository {

    data class Redirect(
        val obsoleteWorkKey: String,
        val targetWorkKey: String,
        val createdAtMs: Long = 0L,
    )

    suspend fun resolve(workKey: String): String

    suspend fun upsertRedirect(redirect: Redirect): Redirect

    suspend fun delete(obsoleteWorkKey: String): Boolean

    fun observeAll(): Flow<List<Redirect>>
}


/**
 * TEMP IMPLEMENTATION NOTES (REMOVE AFTER IMPLEMENTATION)
 * -------------------------------------------------------
 * - DOMAIN interface only.
 * - Authority refs attach external IDs (TMDB/IMDB/TVDB/...) to a Work.
 * - Promotion to primary identity happens only after validation (resolver logic).
 * - Implementation maps to NX_WorkAuthorityRef entity in infra/data-nx.
 */
package com.fishit.player.core.model.repository

import kotlinx.coroutines.flow.Flow

interface NxWorkAuthorityRepository {
    enum class AuthorityType { TMDB, IMDB, TVDB, MUSICBRAINZ, UNKNOWN }

    enum class Namespace { MOVIE, TV, EPISODE, UNKNOWN }

    enum class Status { CONFIRMED, PROBABLE, REJECTED }

    data class AuthorityRef(
        val authorityKey: String, // e.g. "tmdb:movie:550"
        val workKey: String,
        val authorityType: AuthorityType,
        val namespace: Namespace,
        val authorityId: String,
        val status: Status,
        val confidenceScore: Float = 0f,
        val matchedAtMs: Long = 0L,
        val matchedBy: String = "AUTO", // AUTO|MANUAL
        val evidenceSummary: String? = null,
    )

    suspend fun findWorkKeyByAuthorityKey(authorityKey: String): String?

    fun observeForWork(workKey: String): Flow<List<AuthorityRef>>

    suspend fun upsert(ref: AuthorityRef): AuthorityRef

    suspend fun delete(authorityKey: String): Boolean
}

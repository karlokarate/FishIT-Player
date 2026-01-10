/**
 * TEMP IMPLEMENTATION NOTES (REMOVE AFTER IMPLEMENTATION)
 * -------------------------------------------------------
 * - DOMAIN interface only: must not reference ObjectBox entities or BoxStore.
 * - MVP focuses on SERIES_EPISODE navigation.
 * - Keep complex graph operations out of MVP. Add to NxWorkRelationDiagnostics if needed.
 * - Remove this block after infra/data-nx implementation + integration tests are green.
 */
package com.fishit.player.core.model.repository

import kotlinx.coroutines.flow.Flow

/**
 * MVP repository for work relationships (series ↔ episode etc.).
 */
interface NxWorkRelationRepository {

    enum class RelationType {
        SERIES_EPISODE,
        RELATED,
        UNKNOWN,
    }

    data class Relation(
        val parentWorkKey: String,
        val childWorkKey: String,
        val relationType: RelationType,
        val orderIndex: Int? = null,
        val seasonNumber: Int? = null,
        val episodeNumber: Int? = null,
        val createdAtMs: Long = 0L,
        val updatedAtMs: Long = 0L,
    )

    // ──────────────────────────────────────────────────────────────────────
    // Reads (UI-critical)
    // ──────────────────────────────────────────────────────────────────────

    suspend fun findChildren(
        parentWorkKey: String,
        relationType: RelationType? = null,
    ): List<Relation>

    fun observeChildren(
        parentWorkKey: String,
        relationType: RelationType? = null,
    ): Flow<List<Relation>>

    suspend fun findEpisodesForSeries(seriesWorkKey: String): List<Relation>

    fun observeEpisodesForSeries(seriesWorkKey: String): Flow<List<Relation>>

    // ──────────────────────────────────────────────────────────────────────
    // Writes (MVP)
    // ──────────────────────────────────────────────────────────────────────

    suspend fun upsert(relation: Relation): Relation

    suspend fun delete(
        parentWorkKey: String,
        childWorkKey: String,
        relationType: RelationType,
    ): Boolean

    suspend fun linkSeriesEpisode(
        seriesWorkKey: String,
        episodeWorkKey: String,
        season: Int,
        episode: Int,
        orderIndex: Int? = null,
    ): Relation
}
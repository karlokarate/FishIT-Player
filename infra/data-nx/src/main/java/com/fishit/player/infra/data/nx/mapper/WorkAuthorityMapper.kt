package com.fishit.player.infra.data.nx.mapper

import com.fishit.player.core.model.repository.NxWorkAuthorityRepository
import com.fishit.player.core.persistence.obx.NX_Work

/**
 * Mapper helpers for NxWorkAuthorityRepository.AuthorityRef domain model.
 *
 * Note: There's no dedicated NX_WorkAuthorityRef entity in the current schema.
 * Authority refs are stored directly on NX_Work (authorityKey, tmdbId, imdbId, tvdbId).
 * This mapper provides parsing/unparsing for the authorityKey format.
 */

/**
 * Parses an authorityKey string (e.g., "tmdb:movie:550") into components.
 * Returns null if format is invalid.
 */
internal fun parseAuthorityKey(authorityKey: String): AuthorityKeyParts? {
    val parts = authorityKey.split(":")
    if (parts.size < 3) return null

    val authorityType =
        when (parts[0].lowercase()) {
            "tmdb" -> NxWorkAuthorityRepository.AuthorityType.TMDB
            "imdb" -> NxWorkAuthorityRepository.AuthorityType.IMDB
            "tvdb" -> NxWorkAuthorityRepository.AuthorityType.TVDB
            "musicbrainz" -> NxWorkAuthorityRepository.AuthorityType.MUSICBRAINZ
            else -> NxWorkAuthorityRepository.AuthorityType.UNKNOWN
        }

    val namespace =
        when (parts[1].lowercase()) {
            "movie" -> NxWorkAuthorityRepository.Namespace.MOVIE
            "tv" -> NxWorkAuthorityRepository.Namespace.TV
            "episode" -> NxWorkAuthorityRepository.Namespace.EPISODE
            else -> NxWorkAuthorityRepository.Namespace.UNKNOWN
        }

    val authorityId = parts.drop(2).joinToString(":")

    return AuthorityKeyParts(authorityType, namespace, authorityId)
}

internal data class AuthorityKeyParts(
    val authorityType: NxWorkAuthorityRepository.AuthorityType,
    val namespace: NxWorkAuthorityRepository.Namespace,
    val authorityId: String,
)

/**
 * Builds an authorityKey string from components.
 */
internal fun buildAuthorityKey(
    authorityType: NxWorkAuthorityRepository.AuthorityType,
    namespace: NxWorkAuthorityRepository.Namespace,
    authorityId: String,
): String = "${authorityType.name.lowercase()}:${namespace.name.lowercase()}:$authorityId"

/**
 * Extracts AuthorityRef from NX_Work if authorityKey is set.
 */
internal fun NX_Work.toAuthorityRef(): NxWorkAuthorityRepository.AuthorityRef? {
    val key = authorityKey ?: return null
    val parts = parseAuthorityKey(key) ?: return null

    return NxWorkAuthorityRepository.AuthorityRef(
        authorityKey = key,
        workKey = workKey,
        authorityType = parts.authorityType,
        namespace = parts.namespace,
        authorityId = parts.authorityId,
        status = NxWorkAuthorityRepository.Status.CONFIRMED,
        confidenceScore = 1.0f,
        matchedAtMs = updatedAt,
        matchedBy = NxWorkAuthorityRepository.MatchSource.AUTO,
        evidenceSummary = null,
    )
}

package com.fishit.player.core.persistence

import com.fishit.player.core.model.ids.TmdbId

fun String.toTmdbIdOrNull(): TmdbId? {
    val trimmed = trim()

    // Supported formats:
    // - "tmdb:movie:123"
    // - "tmdb:tv:456"
    // - legacy: "tmdb:789"
    // - raw numeric: "789"
    val idString =
            when {
                TMDB_TYPED_PATTERN.matches(trimmed) -> trimmed.substringAfterLast(':')
                TMDB_LEGACY_PATTERN.matches(trimmed) -> trimmed.substringAfter(':')
                trimmed.matches(Regex("^\\d+$")) -> trimmed
                else -> return null
            }

    return idString.toIntOrNull()?.let(::TmdbId)
}

private val TMDB_TYPED_PATTERN = Regex("^tmdb:(movie|tv):(\\d+)$")
private val TMDB_LEGACY_PATTERN = Regex("^tmdb:(\\d+)$")

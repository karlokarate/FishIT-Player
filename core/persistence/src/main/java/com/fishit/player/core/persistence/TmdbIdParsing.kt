package com.fishit.player.core.persistence

import com.fishit.player.core.model.ids.TmdbId

fun String.toTmdbIdOrNull(): TmdbId? {
    val numericPart =
            when {
                startsWith("tmdb:") -> substringAfter("tmdb:")
                matches(Regex("^\\d+$")) -> this
                else -> return null
            }

    return numericPart.toIntOrNull()?.let(::TmdbId)
}

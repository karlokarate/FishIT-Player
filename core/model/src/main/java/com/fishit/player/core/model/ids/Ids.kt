package com.fishit.player.core.model.ids

@JvmInline
value class CanonicalId(val value: String) {
    override fun toString(): String = value
}

@JvmInline
value class RemoteId(val value: String) {
    override fun toString(): String = value
}

@JvmInline
value class PipelineItemId(val value: String) {
    override fun toString(): String = value
}

@JvmInline
value class TmdbId(val value: Int) {
    override fun toString(): String = value.toString()
}

fun String.asCanonicalId(): CanonicalId = CanonicalId(this)

fun String.asRemoteId(): RemoteId = RemoteId(this)

fun String.asPipelineItemId(): PipelineItemId = PipelineItemId(this)

fun Int.asTmdbId(): TmdbId = TmdbId(this)

fun String.toTmdbIdOrNull(): TmdbId? {
    val numericPart =
            when {
                startsWith("tmdb:") -> substringAfter("tmdb:")
                matches(Regex("^\\d+$")) -> this
                else -> return null
            }

    return numericPart.toIntOrNull()?.let(::TmdbId)
}

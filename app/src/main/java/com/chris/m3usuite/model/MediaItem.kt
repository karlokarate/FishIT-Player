package com.chris.m3usuite.model

data class MediaItem(
    val id: Long = 0L,
    val type: String = "", // live|vod|series
    val streamId: Int? = null,
    val name: String = "",
    val sortTitle: String = "",
    val categoryId: String? = null,
    val categoryName: String? = null,
    val logo: String? = null,
    val poster: String? = null,
    val backdrop: String? = null,
    val epgChannelId: String? = null,
    val year: Int? = null,
    val rating: Double? = null,
    val durationSecs: Int? = null,
    val plot: String? = null,
    val url: String? = null,
    val extraJson: String? = null,
    val source: String? = null, // e.g., TG
    val tgChatId: Long? = null,
    val tgMessageId: Long? = null,
    val tgFileId: Int? = null,
    // Optional detail fields (UI/Playback)
    val images: List<String> = emptyList(),
    val imdbId: String? = null,
    val tmdbId: String? = null,
    val trailer: String? = null,
    val director: String? = null,
    val cast: String? = null,
    val country: String? = null,
    val releaseDate: String? = null,
    val genre: String? = null,
    // For VOD direct play URL container preference
    val containerExt: String? = null,
)

/**
 * Returns true when the media entry has at least one artwork reference that we can render.
 */
fun MediaItem.hasArtwork(): Boolean {
    if (!poster.isNullOrBlank()) return true
    if (!logo.isNullOrBlank()) return true
    if (!backdrop.isNullOrBlank()) return true
    if (images.any { !it.isNullOrBlank() }) return true
    return false
}

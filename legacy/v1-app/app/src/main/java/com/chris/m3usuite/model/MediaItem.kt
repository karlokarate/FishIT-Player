package com.chris.m3usuite.model

import com.chris.m3usuite.core.util.isAdultCategory
import com.chris.m3usuite.core.util.isAdultProvider

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
    val providerKey: String? = null,
    val genreKey: String? = null,
    // Telegram thumbnail support (Requirement 3)
    val posterId: Int? = null,
    val localPosterPath: String? = null,
    // Zero-copy playback paths (Requirement 6)
    val localVideoPath: String? = null,
    val localPhotoPath: String? = null,
    val localDocumentPath: String? = null,
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

fun MediaItem.isAdultCategory(): Boolean {
    if (isAdultCategory(categoryId, categoryName)) return true
    if (isAdultProvider(providerKey)) return true
    if (isAdultProvider(genreKey)) return true
    return false
}

/**
 * Extract player artwork as ByteArray for Media3 metadata injection (Requirement 7).
 * Attempts to load from localPosterPath first, falls back to poster URL.
 * Returns null if no artwork is available.
 */
fun MediaItem.playerArtwork(): ByteArray? {
    // Try local poster path first (for Telegram content)
    localPosterPath?.let { path ->
        return try {
            java.io
                .File(path)
                .takeIf { it.exists() && it.canRead() }
                ?.readBytes()
        } catch (e: Exception) {
            null
        }
    }

    // TODO: For network posters, this would require async loading
    // For now, return null for non-local posters
    return null
}

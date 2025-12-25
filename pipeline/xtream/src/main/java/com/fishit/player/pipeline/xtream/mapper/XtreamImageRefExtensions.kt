package com.fishit.player.pipeline.xtream.mapper

import com.fishit.player.core.model.ImageRef
import com.fishit.player.infra.transport.xtream.XtreamHttpHeaders
import com.fishit.player.pipeline.xtream.model.XtreamChannel
import com.fishit.player.pipeline.xtream.model.XtreamEpisode
import com.fishit.player.pipeline.xtream.model.XtreamSeriesItem
import com.fishit.player.pipeline.xtream.model.XtreamVodItem

/**
 * Extensions for extracting ImageRef from Xtream pipeline models.
 *
 * **Contract (IMAGING_SYSTEM.md):**
 * - Pipelines produce ImageRef (not raw URLs)
 * - UI consumes ImageRef via GlobalImageLoader
 * - NO Coil dependency in pipeline modules
 *
 * **Xtream Image URL Patterns:**
 * - VOD: `streamIcon` (poster URL from panel)
 * - Series: `cover` (series poster URL)
 * - Episode: `thumbnail` (episode still)
 * - Channel: `streamIcon` (channel logo)
 *
 * All URLs are HTTP/HTTPS and may require panel auth headers.
 */

// region VOD Extensions
// =============================================================================

/**
 * Extract poster ImageRef from VOD item.
 *
 * @param authHeaders Optional headers for panel authentication
 * @return ImageRef.Http or null if no poster URL available
 */
fun XtreamVodItem.toPosterImageRef(authHeaders: Map<String, String> = emptyMap()): ImageRef? {
    val url = streamIcon?.takeIf { it.isNotBlank() && it.isValidImageUrl() } ?: return null
    return ImageRef.Http(
            url = url,
            headers = XtreamHttpHeaders.withDefaults(authHeaders),
            preferredWidth = 300, // Poster width hint
            preferredHeight = 450, // Poster height hint (2:3 aspect)
    )
}

// =============================================================================
// Series Extensions
// =============================================================================

/**
 * Extract poster ImageRef from series item.
 *
 * @param authHeaders Optional headers for panel authentication
 * @return ImageRef.Http or null if no cover URL available
 */
fun XtreamSeriesItem.toPosterImageRef(authHeaders: Map<String, String> = emptyMap()): ImageRef? {
    val url = cover?.takeIf { it.isNotBlank() && it.isValidImageUrl() } ?: return null
    return ImageRef.Http(
            url = url,
            headers = XtreamHttpHeaders.withDefaults(authHeaders),
            preferredWidth = 300,
            preferredHeight = 450,
    )
}

// =============================================================================
// Episode Extensions
// =============================================================================

/**
 * Extract thumbnail ImageRef from episode.
 *
 * @param authHeaders Optional headers for panel authentication
 * @return ImageRef.Http or null if no thumbnail URL available
 */
fun XtreamEpisode.toThumbnailImageRef(authHeaders: Map<String, String> = emptyMap()): ImageRef? {
    val url = thumbnail?.takeIf { it.isNotBlank() && it.isValidImageUrl() } ?: return null
    return ImageRef.Http(
            url = url,
            headers = XtreamHttpHeaders.withDefaults(authHeaders),
            preferredWidth = 320, // Thumbnail width hint
            preferredHeight = 180, // Thumbnail height hint (16:9 aspect)
    )
}

// =============================================================================
// Channel Extensions
// =============================================================================

/**
 * Extract logo ImageRef from live channel.
 *
 * @param authHeaders Optional headers for panel authentication
 * @return ImageRef.Http or null if no logo URL available
 */
fun XtreamChannel.toLogoImageRef(authHeaders: Map<String, String> = emptyMap()): ImageRef? {
    val url = streamIcon?.takeIf { it.isNotBlank() && it.isValidImageUrl() } ?: return null
    return ImageRef.Http(
            url = url,
            headers = XtreamHttpHeaders.withDefaults(authHeaders),
            preferredWidth = 120, // Logo width hint
            preferredHeight = 120, // Logo height hint (square)
    )
}

// =============================================================================
// Validation Helpers
// =============================================================================

/**
 * Validates if a string is a valid image URL.
 *
 * Checks for:
 * - HTTP/HTTPS protocol
 * - Not a placeholder URL
 * - Contains an image extension or is from known image hosting
 */
private fun String.isValidImageUrl(): Boolean {
    if (isBlank()) return false
    val lower = lowercase()

    // Must be HTTP/HTTPS
    if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false

    // Filter out placeholder images
    if (lower.contains("placeholder") ||
                    lower.contains("no-image") ||
                    lower.contains("noimage") ||
                    lower.contains("default.") ||
                    lower.contains("/null") ||
                    lower.endsWith("/null")
    )
            return false

    // Valid if has image extension OR is from known CDN
    val hasImageExtension = IMAGE_EXTENSIONS.any { lower.endsWith(it) || lower.contains("$it?") }
    val isKnownImageHost = KNOWN_IMAGE_HOSTS.any { lower.contains(it) }

    return hasImageExtension || isKnownImageHost
}

private val IMAGE_EXTENSIONS =
        listOf(".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp", ".svg", ".avif", ".heic")

private val KNOWN_IMAGE_HOSTS =
        listOf(
                "image.tmdb.org",
                "images.unsplash.com",
                "i.imgur.com",
                "cloudfront.net",
                "akamaized.net",
                "/images/",
                "/img/",
                "/poster/",
                "/cover/",
                "/thumb/",
                "/logo/",
        )

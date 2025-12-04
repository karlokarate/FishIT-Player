package com.chris.m3usuite.telegram.image

/**
 * Key for Telegram thumbnail caching with Coil 3.
 *
 * This key is used by [TelegramThumbFetcher] to load thumbnails from TDLib
 * and cache them in Coil's disk and memory caches.
 *
 * **Cache Key Stability:**
 * - Uses remoteId (stable across TDLib sessions) instead of fileId (unstable)
 * - fileId is only used internally by the fetcher to download from TDLib
 * - Cache key format: "tg_thumb_{kind}_{sizeBucket}_{remoteId}"
 *
 * **Usage Example:**
 * ```kotlin
 * AsyncImage(
 *     model = ImageRequest.Builder(context)
 *         .data(
 *             TelegramThumbKey(
 *                 remoteId = message.remoteId,
 *                 kind = ThumbKind.CHAT_MESSAGE,
 *                 sizeBucket = 256
 *             )
 *         )
 *         .build(),
 *     imageLoader = AppImageLoader.get(context)
 * )
 * ```
 *
 * @property remoteId Stable remote file identifier from TDLib (remote.id or remote.unique_id)
 * @property kind Thumbnail kind (determines resolution and use case)
 * @property sizeBucket Target size bucket (128, 256, 512, or 0 for default)
 */
data class TelegramThumbKey(
    val remoteId: String,
    val kind: ThumbKind,
    val sizeBucket: Int = 0,
) {
    /**
     * Generate stable cache key for Coil.
     * Format: "tg_thumb_{kind}_{sizeBucket}_{remoteId}"
     */
    fun toCacheKey(): String = "tg_thumb_${kind.name}_${sizeBucket}_$remoteId"
}

/**
 * Thumbnail kind for different use cases.
 *
 * Each kind may have different resolution requirements and download strategies.
 *
 * - **POSTER**: High-resolution poster images for movie/series detail screens
 * - **CHAT_MESSAGE**: Chat message thumbnails (typically lower resolution)
 * - **PREVIEW**: Preview images for lists and grids
 */
enum class ThumbKind {
    /** High-resolution poster for detail screens */
    POSTER,

    /** Chat message thumbnail (lower resolution) */
    CHAT_MESSAGE,

    /** Preview image for lists and grids */
    PREVIEW,
}

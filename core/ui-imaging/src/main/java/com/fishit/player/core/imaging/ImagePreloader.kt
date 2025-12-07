package com.fishit.player.core.imaging

import android.content.Context
import coil3.ImageLoader
import coil3.request.ImageRequest
import com.fishit.player.core.model.ImageRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Preload utility for batch image loading.
 *
 * **Purpose:**
 * - Preload images in background for faster display
 * - Respects cache policies and avoids redundant fetches
 * - Limits concurrent preloads to avoid overwhelming the system
 *
 * **Usage:**
 * ```kotlin
 * val preloader = ImagePreloader(imageLoader)
 *
 * // Preload first 8 posters from a list
 * preloader.preload(
 *     context = context,
 *     imageRefs = movies.mapNotNull { it.poster }.take(8),
 * )
 * ```
 *
 * Ported from v1 AppImageLoader.preload() with ImageRef support.
 */
class ImagePreloader(
        private val imageLoader: ImageLoader,
) {

    /** Default preload configuration. */
    object Config {
        /** Maximum images to preload in a single batch */
        const val DEFAULT_LIMIT = 8

        /** Whether to use crossfade during preload (off for perf) */
        const val PRELOAD_CROSSFADE = false
    }

    /**
     * Preload a collection of [ImageRef]s.
     *
     * Runs on IO dispatcher and limits to [limit] images to avoid overwhelming the system during
     * startup or navigation.
     *
     * @param context Application context
     * @param imageRefs Collection of ImageRefs to preload
     * @param limit Maximum number to preload (default 8)
     */
    suspend fun preload(
            context: Context,
            imageRefs: Collection<ImageRef?>,
            limit: Int = Config.DEFAULT_LIMIT,
    ) {
        if (imageRefs.isEmpty()) return

        val candidates = imageRefs.asSequence().filterNotNull().distinct().take(limit).toList()

        if (candidates.isEmpty()) return

        withContext(Dispatchers.IO) {
            for (ref in candidates) {
                runCatching {
                    val request = ImageRequest.Builder(context).data(ref).build()
                    imageLoader.execute(request)
                }
            }
        }
    }

    /**
     * Preload images for a list of items with a selector.
     *
     * @param context Application context
     * @param items Collection of items
     * @param selector Function to extract ImageRef from each item
     * @param limit Maximum number to preload
     */
    suspend fun <T> preloadFrom(
            context: Context,
            items: Collection<T>,
            selector: (T) -> ImageRef?,
            limit: Int = Config.DEFAULT_LIMIT,
    ) {
        preload(context, items.map(selector), limit)
    }

    /**
     * Preload multiple ImageRef fields from items.
     *
     * Useful when items have both poster and backdrop that should be preloaded.
     *
     * @param context Application context
     * @param items Collection of items
     * @param selectors Functions to extract ImageRefs from each item
     * @param limit Maximum total images to preload
     */
    suspend fun <T> preloadMultipleFrom(
            context: Context,
            items: Collection<T>,
            vararg selectors: (T) -> ImageRef?,
            limit: Int = Config.DEFAULT_LIMIT,
    ) {
        val refs = items.flatMap { item -> selectors.mapNotNull { selector -> selector(item) } }
        preload(context, refs, limit)
    }
}

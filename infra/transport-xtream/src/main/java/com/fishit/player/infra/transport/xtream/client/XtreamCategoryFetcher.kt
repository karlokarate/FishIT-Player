package com.fishit.player.infra.transport.xtream.client

import com.fishit.player.core.model.Layer
import com.fishit.player.core.model.PipelineComponent
import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.infra.transport.xtream.XtreamCategory
import com.fishit.player.infra.transport.xtream.XtreamUrlBuilder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject

/**
 * XtreamCategoryFetcher - Handles category operations.
 *
 * Extracted from DefaultXtreamApiClient to reduce cyclomatic complexity.
 * Responsibilities:
 * - Fetch live/VOD/series categories
 * - Handle VOD alias resolution (vod/movie/movies)
 *
 * CC Target: ≤ 6 per function
 *
 * @responsibility Fetch live/VOD/series categories from Xtream API
 * @responsibility Resolve VOD path aliases (movie/vod/movies)
 */
@PipelineComponent(
    layer = Layer.TRANSPORT,
    sourceType = "Xtream",
    genericPattern = "{Source}CategoryFetcher",
)
class XtreamCategoryFetcher
    @Inject
    constructor(
        private val okHttpClient: OkHttpClient,
        private val json: Json,
        private val urlBuilder: XtreamUrlBuilder,
        private val io: CoroutineDispatcher = Dispatchers.IO,
    ) {
        companion object {
            private const val TAG = "XtreamCategoryFetcher"
            private val VOD_ALIAS_CANDIDATES = listOf("movie", "vod", "movies") // "movie" first - most common
        }

        /**
         * Get live stream categories.
         * CC: 2
         */
        suspend fun getLiveCategories(): List<XtreamCategory> = fetchCategories("get_live_categories")

        /**
         * Get VOD categories with alias resolution.
         * CC: 4 (alias loop)
         */
        suspend fun getVodCategories(currentVodKind: String): Pair<List<XtreamCategory>, String> {
            // Try aliases in order
            val candidates = listOf(currentVodKind) + VOD_ALIAS_CANDIDATES.filter { it != currentVodKind }
            for (alias in candidates) {
                val result = fetchCategories("get_${alias}_categories")
                if (result.isNotEmpty()) {
                    return Pair(result, alias)
                }
            }
            return Pair(emptyList(), currentVodKind)
        }

        /**
         * Get series categories.
         * CC: 2
         */
        suspend fun getSeriesCategories(): List<XtreamCategory> = fetchCategories("get_series_categories")

        /**
         * Fetch categories for a given action.
         * CC: 4 (parsing)
         */
        private suspend fun fetchCategories(action: String): List<XtreamCategory> =
            withContext(io) {
                val url = urlBuilder.playerApiUrl(action)
                val body = fetchRaw(url) ?: return@withContext emptyList()
                parseCategories(body, action)
            }

        /**
         * Parse category JSON array.
         * CC: 3
         */
        private fun parseCategories(
            body: String,
            action: String,
        ): List<XtreamCategory> =
            try {
                val jsonElement = json.parseToJsonElement(body)
                jsonElement.jsonArray.mapNotNull { element ->
                    val obj = element.jsonObject
                    XtreamCategory(
                        categoryId = obj["category_id"]?.jsonPrimitive?.content,
                        categoryName = obj["category_name"]?.jsonPrimitive?.content,
                        parentId = obj["parent_id"]?.jsonPrimitive?.intOrNull,
                    )
                }
            } catch (e: Exception) {
                UnifiedLog.w(TAG, e) { "Failed to parse categories for $action" }
                emptyList()
            }

        /**
         * Internal helper to fetch HTTP response using OkHttp directly.
         *
         * @param url The URL to fetch
         * @return Response body as string, or null if request failed
         */
        private suspend fun fetchRaw(url: String): String? =
            try {
                val request = Request.Builder().url(url).build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) null else response.body?.string()
                }
            } catch (_: IOException) {
                null
            }
    }

package com.fishit.player.infra.transport.xtream.client

import android.os.SystemClock
import com.fishit.player.infra.http.HttpClient
import com.fishit.player.infra.http.RequestConfig
import com.fishit.player.infra.http.CacheConfig
import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.infra.transport.xtream.XtreamContentType
import com.fishit.player.infra.transport.xtream.XtreamEpgProgramme
import com.fishit.player.infra.transport.xtream.XtreamEpgResponse
import com.fishit.player.infra.transport.xtream.XtreamLiveStream
import com.fishit.player.infra.transport.xtream.XtreamSearchResults
import com.fishit.player.infra.transport.xtream.XtreamSeriesInfo
import com.fishit.player.infra.transport.xtream.XtreamSeriesStream
import com.fishit.player.infra.transport.xtream.XtreamUrlBuilder
import com.fishit.player.infra.transport.xtream.XtreamVodInfo
import com.fishit.player.infra.transport.xtream.XtreamVodStream
import com.fishit.player.infra.transport.xtream.mapper.LiveStreamMapper
import com.fishit.player.infra.transport.xtream.mapper.SeriesStreamMapper
import com.fishit.player.infra.transport.xtream.mapper.VodStreamMapper
import com.fishit.player.infra.transport.xtream.strategy.CategoryFallbackStrategy
import com.fishit.player.infra.transport.xtream.streaming.JsonObjectReader
import com.fishit.player.infra.transport.xtream.streaming.StreamingJsonParser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import javax.inject.Inject

/**
 * XtreamStreamFetcher - Handles stream fetching operations.
 *
 * Extracted from DefaultXtreamApiClient to reduce cyclomatic complexity.
 * Responsibilities:
 * - Fetch VOD/Live/Series streams
 * - Streaming batch operations (memory-efficient)
 * - Detail endpoint fetching (getVodInfo, getSeriesInfo)
 * - Count operations
 *
 * CC Target: ≤ 10 per function
 */
class XtreamStreamFetcher @Inject constructor(
    private val httpClient: HttpClient,
    private val json: Json,
    private val urlBuilder: XtreamUrlBuilder,
    private val categoryFallbackStrategy: CategoryFallbackStrategy,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    companion object {
        private const val TAG = "XtreamStreamFetcher"
        private val VOD_ALIAS_CANDIDATES = listOf("movie", "vod", "movies") // "movie" first - most common
        private const val DEFAULT_VOD_KIND = "movie" // Most Xtream servers use /movie/ path
    }

    /**
     * Data class for stream response.
     */
    data class StreamResponse(
        val isSuccessful: Boolean,
        val inputStream: java.io.InputStream?,
    ) : AutoCloseable {
        override fun close() {
            inputStream?.close()
        }
    }

    // =========================================================================
    // List Endpoints (returns full list with client-side pagination)
    // =========================================================================

    /**
     * Get VOD streams with client-side pagination.
     * Uses alias fallback (vod → movie → movies) for panel compatibility.
     * CC: 4 (delegation + pagination)
     */
    suspend fun getVodStreams(
        categoryId: String? = null,
        limit: Int = 500,
        offset: Int = 0,
    ): List<XtreamVodStream> =
        withContext(io) {
            val all = fetchStreamsWithCategoryFallbackStreaming(
                action = "get_${DEFAULT_VOD_KIND}_streams",
                categoryId = categoryId,
                streamingMapper = VodStreamMapper::fromStreaming,
                fallbackMapper = VodStreamMapper::fromJsonObject,
            )
            all.drop(offset).take(limit)
        }

    /**
     * Get live streams with client-side pagination.
     * CC: 4 (delegation + pagination)
     */
    suspend fun getLiveStreams(
        categoryId: String? = null,
        limit: Int = 500,
        offset: Int = 0,
    ): List<XtreamLiveStream> =
        withContext(io) {
            val all = fetchStreamsWithCategoryFallbackStreaming(
                action = "get_live_streams",
                categoryId = categoryId,
                streamingMapper = LiveStreamMapper::fromStreaming,
                fallbackMapper = LiveStreamMapper::fromJsonObject,
            )
            all.drop(offset).take(limit)
        }

    /**
     * Get series with client-side pagination.
     * CC: 4 (delegation + pagination)
     */
    suspend fun getSeries(
        categoryId: String? = null,
        limit: Int = 500,
        offset: Int = 0,
    ): List<XtreamSeriesStream> =
        withContext(io) {
            val all = fetchStreamsWithCategoryFallbackStreaming(
                action = "get_series",
                categoryId = categoryId,
                streamingMapper = SeriesStreamMapper::fromStreaming,
                fallbackMapper = SeriesStreamMapper::fromJsonObject,
            )
            all.drop(offset).take(limit)
        }

    // =========================================================================
    // Batch Streaming Methods (Memory-Efficient)
    // =========================================================================

    /**
     * Stream VOD in batches with constant memory usage.
     * Uses injected urlBuilder and alias fallback for panel compatibility.
     * CC: 4 (batch processing)
     */
    suspend fun streamVodInBatches(
        batchSize: Int = 500,
        categoryId: String? = null,
        onBatch: suspend (List<XtreamVodStream>) -> Unit,
    ): Int =
        withContext(io) {
            streamContentInBatches(
                action = "get_${DEFAULT_VOD_KIND}_streams",
                categoryId = categoryId,
                batchSize = batchSize,
                aliases = VOD_ALIAS_CANDIDATES,
                mapper = VodStreamMapper::fromStreaming,
                onBatch = onBatch,
            )
        }

    /**
     * Stream series in batches with constant memory usage.
     * CC: 4
     */
    suspend fun streamSeriesInBatches(
        batchSize: Int = 500,
        categoryId: String? = null,
        onBatch: suspend (List<XtreamSeriesStream>) -> Unit,
    ): Int =
        withContext(io) {
            streamContentInBatches(
                action = "get_series",
                categoryId = categoryId,
                batchSize = batchSize,
                aliases = listOf("series"),
                mapper = SeriesStreamMapper::fromStreaming,
                onBatch = onBatch,
            )
        }

    /**
     * Stream live in batches with constant memory usage.
     * CC: 4
     */
    suspend fun streamLiveInBatches(
        batchSize: Int = 500,
        categoryId: String? = null,
        onBatch: suspend (List<XtreamLiveStream>) -> Unit,
    ): Int =
        withContext(io) {
            streamContentInBatches(
                action = "get_live_streams",
                categoryId = categoryId,
                batchSize = batchSize,
                aliases = listOf("live"),
                mapper = LiveStreamMapper::fromStreaming,
                onBatch = onBatch,
            )
        }

    // =========================================================================
    // Count Methods
    // =========================================================================

    /**
     * Count VOD streams efficiently using streaming internals.
     * CC: 2
     */
    suspend fun countVodStreams(
        categoryId: String? = null,
    ): Int =
        runCatching {
            streamVodInBatches(
                batchSize = 1000,
                categoryId = categoryId,
                onBatch = { /* Discard items, just count */ },
            )
        }.getOrElse {
            UnifiedLog.w(TAG) { "countVodStreams failed: ${it.message}" }
            -1
        }

    /**
     * Count series efficiently using streaming internals.
     * CC: 2
     */
    suspend fun countSeries(
        categoryId: String? = null,
    ): Int =
        runCatching {
            streamSeriesInBatches(
                batchSize = 1000,
                categoryId = categoryId,
                onBatch = { /* Discard items, just count */ },
            )
        }.getOrElse {
            UnifiedLog.w(TAG) { "countSeries failed: ${it.message}" }
            -1
        }

    /**
     * Count live streams efficiently using streaming internals.
     * CC: 2
     */
    suspend fun countLiveStreams(
        categoryId: String? = null,
    ): Int =
        runCatching {
            streamLiveInBatches(
                batchSize = 1000,
                categoryId = categoryId,
                onBatch = { /* Discard items, just count */ },
            )
        }.getOrElse {
            UnifiedLog.w(TAG) { "countLiveStreams failed: ${it.message}" }
            -1
        }

    // =========================================================================
    // Detail Endpoints
    // =========================================================================

    /**
     * Get VOD info for a specific item.
     * CC: 4 (parsing and error handling)
     *
     * @param vodId The VOD stream ID
     * @param vodKind The kind/alias to use for API call (movie, vod, movies) - from PlaybackHints
     */
    suspend fun getVodInfo(
        vodId: Int,
        vodKind: String? = null,
    ): XtreamVodInfo? =
        withContext(io) {
            val kind = vodKind ?: "vod" // Default fallback
            val action = "get_${kind}_info"
            val paramKey = "${kind}_id"
            val url = urlBuilder.playerApiUrl(action, mapOf(paramKey to vodId.toString()))

            UnifiedLog.d(TAG) { "getVodInfo: Using action=$action with $paramKey=$vodId (kind=$kind)" }

            val body = runCatching { fetchRaw(url) }.getOrNull()
            val trimmedBody = body?.trim { it.isWhitespace() || it == '\uFEFF' }

            if (trimmedBody.isNullOrEmpty() || !trimmedBody.startsWith("{")) {
                UnifiedLog.w(TAG) { "getVodInfo: empty or invalid response vodId=$vodId action=$action" }
                return@withContext null
            }

            runCatching { json.decodeFromString<XtreamVodInfo>(trimmedBody) }
                .onFailure { e -> UnifiedLog.w(TAG, e) { "getVodInfo: JSON parse failed vodId=$vodId action=$action" } }
                .getOrNull()
        }

    /**
     * Get series info including episodes.
     * CC: 4
     */
    suspend fun getSeriesInfo(
        seriesId: Int,
    ): XtreamSeriesInfo? =
        withContext(io) {
            val url = urlBuilder.playerApiUrl("get_series_info", mapOf("series_id" to seriesId.toString()))
            val body = runCatching { fetchRaw(url) }.getOrNull()
            val trimmedBody = body?.trim { it.isWhitespace() || it == '\uFEFF' }

            if (trimmedBody.isNullOrEmpty() || !trimmedBody.startsWith("{")) {
                UnifiedLog.w(TAG) { "getSeriesInfo: empty or invalid response seriesId=$seriesId" }
                return@withContext null
            }

            runCatching { json.decodeFromString<XtreamSeriesInfo>(trimmedBody) }
                .onFailure { e -> UnifiedLog.w(TAG, e) { "getSeriesInfo: JSON parse failed seriesId=$seriesId" } }
                .getOrNull()
        }

    // =========================================================================
    // Private Helpers
    // =========================================================================

    /**
     * Streaming version of fetchStreamsWithCategoryFallback.
     * CC: ~8 (category fallback + streaming + fallback)
     */
    private suspend fun <T> fetchStreamsWithCategoryFallbackStreaming(
        action: String,
        categoryId: String?,
        streamingMapper: (JsonObjectReader) -> T?,
        fallbackMapper: (JsonObject) -> T,
    ): List<T> {
        // If specific category requested, fetch only that category (no fallback)
        if (categoryId != null) {
            val url = urlBuilder.playerApiUrl(action, mapOf("category_id" to categoryId))
            return fetchAndParseStreaming(url, action, streamingMapper, fallbackMapper)
        }

        // No specific category: use fallback strategy (* → 0 → null)
        return categoryFallbackStrategy.fetchWithFallback(categoryId) { catId ->
            val url =
                if (catId == null) {
                    urlBuilder.playerApiUrl(action)
                } else {
                    urlBuilder.playerApiUrl(action, mapOf("category_id" to catId))
                }
            runCatching {
                fetchAndParseStreaming(url, action, streamingMapper, fallbackMapper)
            }.getOrElse { emptyList() }
        }
    }

    /**
     * Fetch and parse JSON array using streaming (O(1) memory).
     * CC: ~6 (streaming + fallback)
     */
    private suspend fun <T> fetchAndParseStreaming(
        url: String,
        actionHint: String,
        streamingMapper: (JsonObjectReader) -> T?,
        fallbackMapper: (JsonObject) -> T,
    ): List<T> {
        return fetchRawAsStream(url).use { streamResp ->
            if (!streamResp.isSuccessful) {
                return@use emptyList()
            }

            try {
                val inputStream = streamResp.inputStream!!
                val startTime = SystemClock.elapsedRealtime()

                val items = StreamingJsonParser.parseArrayAsSequence(inputStream, streamingMapper).toList()

                val elapsed = SystemClock.elapsedRealtime() - startTime
                UnifiedLog.d(TAG) {
                    "StreamingParse($actionHint): Parsed ${items.size} items in ${elapsed}ms (streaming)"
                }
                items
            } catch (e: Exception) {
                UnifiedLog.w(TAG) {
                    "StreamingParse($actionHint): Streaming failed, falling back to DOM: ${e.message}"
                }
                // Fallback to DOM parsing
                val body = fetchRaw(url) ?: return@use emptyList()
                parseJsonArray(body, fallbackMapper, actionHint)
            }
        }
    }

    /**
     * Parse JSON array (fallback when streaming fails).
     * CC: 3
     */
    private fun <T> parseJsonArray(
        body: String,
        mapper: (JsonObject) -> T,
        actionHint: String,
    ): List<T> {
        return try {
            val jsonElement = json.parseToJsonElement(body)
            jsonElement.jsonArray.mapNotNull { element ->
                runCatching { mapper(element.jsonObject) }.getOrNull()
            }
        } catch (e: Exception) {
            UnifiedLog.w(TAG, e) { "parseJsonArray failed for $actionHint" }
            emptyList()
        }
    }

    /**
     * Generic streaming batch method with category fallback.
     * Uses injected urlBuilder and tries alias fallback for panel compatibility.
     * CC: ~9 (alias retry, category fallback)
     */
    private suspend fun <T> streamContentInBatches(
        action: String,
        categoryId: String?,
        batchSize: Int,
        aliases: List<String>,
        mapper: (JsonObjectReader) -> T?,
        onBatch: suspend (List<T>) -> Unit,
    ): Int {
        // Helper to try all aliases for a given categoryId
        suspend fun tryAllAliases(catId: String?): Int {
            for (alias in aliases) {
                val actualAction = if (alias == aliases.first()) action else "get_${alias}_streams"
                val url =
                    if (catId == null) {
                        urlBuilder.playerApiUrl(actualAction)
                    } else {
                        urlBuilder.playerApiUrl(actualAction, mapOf("category_id" to catId))
                    }
                val count =
                    runCatching {
                        streamFromUrl(url, batchSize, mapper, onBatch)
                    }.onFailure { error ->
                        UnifiedLog.w(TAG) {
                            "streamContentInBatches($action): streamFromUrl FAILED for alias=$alias catId=$catId | ${error.javaClass.simpleName}: ${error.message}"
                        }
                    }.getOrElse { 0 }
                if (count > 0) {
                    return count
                }
            }
            return 0
        }

        // Specific category requested
        if (categoryId != null) {
            return tryAllAliases(categoryId)
        }

        // Use strategy for fallback: * → 0 → null
        return categoryFallbackStrategy.fetchScalarWithFallback(
            categoryId = null,
            isValidResult = { it != null && it > 0 },
        ) { catId ->
            tryAllAliases(catId)
        } ?: 0
    }

    /**
     * Stream JSON array from URL in batches.
     * CC: ~7 (batch streaming + stats)
     */
    private suspend fun <T> streamFromUrl(
        url: String,
        batchSize: Int,
        mapper: (JsonObjectReader) -> T?,
        onBatch: suspend (List<T>) -> Unit,
    ): Int {
        UnifiedLog.d(TAG) {
            "streamFromUrl: CALLED for ${redactUrl(url)}"
        }

        return fetchRawAsStream(url).use { streamResp ->
            if (!streamResp.isSuccessful || streamResp.inputStream == null) {
                UnifiedLog.w(TAG) {
                    "streamFromUrl: Request failed for ${redactUrl(url)} | isSuccessful=${streamResp.isSuccessful} hasInputStream=${streamResp.inputStream != null}"
                }
                return@use 0
            }

            val startTime = SystemClock.elapsedRealtime()
            val inputStream = streamResp.inputStream

            UnifiedLog.d(TAG) {
                "streamFromUrl: Starting StreamingJsonParser.streamInBatches() for ${redactUrl(url)}"
            }

            val stats = StreamingJsonParser.streamInBatches(
                input = inputStream,
                batchSize = batchSize,
                mapper = mapper,
                onBatch = onBatch,
            )

            val elapsed = SystemClock.elapsedRealtime() - startTime
            UnifiedLog.d(TAG) {
                "StreamBatch: ${stats.totalCount} items in ${stats.batchCount} batches (${elapsed}ms) | errors=${stats.errorCount}"
            }

            if (stats.totalCount == 0) {
                UnifiedLog.w(TAG) {
                    "streamFromUrl: ZERO items parsed from ${redactUrl(url)} | batchCount=${stats.batchCount} errors=${stats.errorCount}"
                }
            }

            stats.totalCount
        }
    }

    private fun redactUrl(url: String): String {
        return url.replace(Regex("username=[^&]+"), "username=***")
            .replace(Regex("password=[^&]+"), "password=***")
    }

    /**
     * Internal helper to fetch HTTP response using the generic HttpClient.
     *
     * @param url The URL to fetch
     * @return Response body as string, or null if request failed
     */
    private suspend fun fetchRaw(url: String): String? {
        val result = httpClient.fetch(url, RequestConfig(cache = CacheConfig.DEFAULT))
        return result.getOrNull()
    }

    /**
     * Internal helper to fetch HTTP response as stream.
     *
     * @param url The URL to fetch
     * @return StreamResponse with input stream
     */
    private suspend fun fetchRawAsStream(url: String): StreamResponse {
        val result = httpClient.fetchStream(url, RequestConfig(cache = CacheConfig.DISABLED))
        return result.fold(
            onSuccess = { inputStream ->
                StreamResponse(isSuccessful = true, inputStream = inputStream)
            },
            onFailure = {
                StreamResponse(isSuccessful = false, inputStream = null)
            }
        )
    }

    // =========================================================================
    // EPG Methods
    // =========================================================================

    /**
     * Get short EPG for a stream.
     * CC: 3
     */
    suspend fun getShortEpg(streamId: Int, limit: Int): List<XtreamEpgProgramme> =
        withContext(io) {
            val url = urlBuilder.playerApiUrl(
                "get_short_epg",
                mapOf("stream_id" to streamId.toString(), "limit" to limit.toString()),
            )
            fetchAndParseEpg(url)
        }

    /**
     * Get full EPG for a stream.
     * CC: 3
     */
    suspend fun getFullEpg(streamId: Int): List<XtreamEpgProgramme> =
        withContext(io) {
            val url = urlBuilder.playerApiUrl(
                "get_simple_data_table",
                mapOf("stream_id" to streamId.toString()),
            )
            fetchAndParseEpg(url)
        }

    /**
     * Prefetch EPG for multiple streams (fire-and-forget).
     * CC: 2
     */
    suspend fun prefetchEpg(streamIds: List<Int>, perStreamLimit: Int) {
        // Fire and forget - just start the requests
        streamIds.forEach { streamId ->
            runCatching { getShortEpg(streamId, perStreamLimit) }
        }
    }

    private suspend fun fetchAndParseEpg(url: String): List<XtreamEpgProgramme> {
        val body = fetchRaw(url) ?: return emptyList()
        return runCatching {
            json.decodeFromString<XtreamEpgResponse>(body).epgListings ?: emptyList()
        }.getOrElse { emptyList() }
    }

    // =========================================================================
    // Search Methods
    // =========================================================================

    /**
     * Search across content types (client-side filtering).
     * CC: 5
     */
    suspend fun search(
        query: String,
        types: Set<XtreamContentType>,
        limit: Int,
    ): XtreamSearchResults = withContext(io) {
        val lowerQuery = query.lowercase()

        val live = if (XtreamContentType.LIVE in types) {
            getLiveStreams(null, Int.MAX_VALUE, 0)
                .filter { it.name?.lowercase()?.contains(lowerQuery) == true }
                .take(limit)
        } else emptyList()

        val vod = if (XtreamContentType.VOD in types) {
            getVodStreams(null, Int.MAX_VALUE, 0)
                .filter { it.name?.lowercase()?.contains(lowerQuery) == true }
                .take(limit)
        } else emptyList()

        val series = if (XtreamContentType.SERIES in types) {
            getSeries(null, Int.MAX_VALUE, 0)
                .filter { it.name?.lowercase()?.contains(lowerQuery) == true }
                .take(limit)
        } else emptyList()

        XtreamSearchResults(live, vod, series)
    }
}

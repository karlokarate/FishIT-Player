package com.fishit.player.infra.transport.xtream.client

import android.os.SystemClock
import com.fishit.player.infra.http.HttpClient
import com.fishit.player.infra.http.RequestConfig
import com.fishit.player.infra.http.CacheConfig
import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.infra.transport.xtream.XtreamLiveStream
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
        private val VOD_ALIAS_CANDIDATES = listOf("vod", "movie", "movies")
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
    // List Endpoints (returns full list)
    // =========================================================================

    /**
     * Get VOD streams.
     * CC: 3 (delegation to generic fetch)
     */
    /**
     * Fetch VOD streams with client-side pagination.
     * 
     * Note: This fetches ALL streams from the API and applies pagination client-side via
     * drop(offset).take(limit). For large catalogs (10k+ items), this is inefficient.
     * Consider using streamVodInBatches() for better performance when full pagination is needed.
     */
    suspend fun getVodStreams(
        categoryId: String? = null,
        limit: Int = 500,
        offset: Int = 0,
    ): List<XtreamVodStream> =
        withContext(io) {
            val vodKind = urlBuilder.currentVodKind
            val allStreams = fetchStreamsWithCategoryFallbackStreaming(
                action = "get_${vodKind}_streams",
                categoryId = categoryId,
                streamingMapper = VodStreamMapper::fromStreaming,
                fallbackMapper = VodStreamMapper::fromJsonObject,
            )
            // Apply pagination (client-side - fetches all before slicing)
            allStreams.drop(offset).take(limit)
        }

    /**
     * Get live streams.
     * CC: 3
     */
    suspend fun getLiveStreams(
        categoryId: String? = null,
        limit: Int = 500,
        offset: Int = 0,
    ): List<XtreamLiveStream> =
        withContext(io) {
            val allStreams = fetchStreamsWithCategoryFallbackStreaming(
                action = "get_live_streams",
                categoryId = categoryId,
                streamingMapper = LiveStreamMapper::fromStreaming,
                fallbackMapper = LiveStreamMapper::fromJsonObject,
            )
            // Apply pagination
            allStreams.drop(offset).take(limit)
        }

    /**
     * Get series.
     * CC: 3
     */
    suspend fun getSeries(
        categoryId: String? = null,
        limit: Int = 500,
        offset: Int = 0,
    ): List<XtreamSeriesStream> =
        withContext(io) {
            val allStreams = fetchStreamsWithCategoryFallbackStreaming(
                action = "get_series",
                categoryId = categoryId,
                streamingMapper = SeriesStreamMapper::fromStreaming,
                fallbackMapper = SeriesStreamMapper::fromJsonObject,
            )
            // Apply pagination
            allStreams.drop(offset).take(limit)
        }

    // =========================================================================
    // Batch Streaming Methods (Memory-Efficient)
    // =========================================================================

    /**
     * Stream VOD in batches.
     * CC: 4 (batch processing)
     */
    suspend fun streamVodInBatches(
        batchSize: Int = 500,
        categoryId: String? = null,
        onBatch: suspend (List<XtreamVodStream>) -> Unit,
    ): Int =
        withContext(io) {
            val vodKind = urlBuilder.currentVodKind
            streamContentInBatches(
                urlBuilder = urlBuilder,
                action = "get_${vodKind}_streams",
                categoryId = categoryId,
                batchSize = batchSize,
                aliases = listOf(vodKind) + VOD_ALIAS_CANDIDATES.filter { it != vodKind },
                mapper = VodStreamMapper::fromStreaming,
                onBatch = onBatch,
            )
        }

    /**
     * Stream series in batches.
     * CC: 4
     */
    suspend fun streamSeriesInBatches(
        batchSize: Int = 500,
        categoryId: String? = null,
        onBatch: suspend (List<XtreamSeriesStream>) -> Unit,
    ): Int =
        withContext(io) {
            streamContentInBatches(
                urlBuilder = urlBuilder,
                action = "get_series",
                categoryId = categoryId,
                batchSize = batchSize,
                aliases = listOf("series"),
                mapper = SeriesStreamMapper::fromStreaming,
                onBatch = onBatch,
            )
        }

    /**
     * Stream live in batches.
     * CC: 4
     */
    suspend fun streamLiveInBatches(
        batchSize: Int = 500,
        categoryId: String? = null,
        onBatch: suspend (List<XtreamLiveStream>) -> Unit,
    ): Int =
        withContext(io) {
            streamContentInBatches(
                urlBuilder = urlBuilder,
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
     * Count VOD streams.
     * CC: 2
     */
    suspend fun countVodStreams(
        urlBuilder: XtreamUrlBuilder,
        vodKind: String,
        categoryId: String?,
    ): Int =
        runCatching {
            streamVodInBatches(
                urlBuilder = urlBuilder,
                vodKind = vodKind,
                batchSize = 1000,
                categoryId = categoryId,
                onBatch = { /* Discard items, just count */ },
            )
        }.getOrElse {
            UnifiedLog.w(TAG) { "countVodStreams failed: ${it.message}" }
            -1
        }

    /**
     * Count series.
     * CC: 2
     */
    suspend fun countSeries(
        urlBuilder: XtreamUrlBuilder,
        categoryId: String?,
    ): Int =
        runCatching {
            streamSeriesInBatches(
                urlBuilder = urlBuilder,
                batchSize = 1000,
                categoryId = categoryId,
                onBatch = { /* Discard items, just count */ },
            )
        }.getOrElse {
            UnifiedLog.w(TAG) { "countSeries failed: ${it.message}" }
            -1
        }

    /**
     * Count live streams.
     * CC: 2
     */
    suspend fun countLiveStreams(
        urlBuilder: XtreamUrlBuilder,
        categoryId: String?,
    ): Int =
        runCatching {
            streamLiveInBatches(
                urlBuilder = urlBuilder,
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
     * Get VOD info.
     * CC: 4 (parsing and error handling)
     */
    suspend fun getVodInfo(
        urlBuilder: XtreamUrlBuilder,
        vodId: Int,
    ): XtreamVodInfo? =
        withContext(io) {
            val url = urlBuilder.playerApiUrl("get_vod_info", mapOf("vod_id" to vodId.toString()))
            val body = runCatching { fetchRaw(url) }.getOrNull()
            val trimmedBody = body?.trim { it.isWhitespace() || it == '\uFEFF' }

            if (trimmedBody.isNullOrEmpty() || !trimmedBody.startsWith("{")) {
                UnifiedLog.w(TAG) { "getVodInfo: empty or invalid response vodId=$vodId" }
                return@withContext null
            }

            runCatching { json.decodeFromString<XtreamVodInfo>(trimmedBody) }
                .onFailure { e -> UnifiedLog.w(TAG, e) { "getVodInfo: JSON parse failed vodId=$vodId" } }
                .getOrNull()
        }

    /**
     * Get series info.
     * CC: 4
     */
    suspend fun getSeriesInfo(
        urlBuilder: XtreamUrlBuilder,
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
     * CC: ~9 (alias retry, category fallback)
     */
    private suspend fun <T> streamContentInBatches(
        urlBuilder: XtreamUrlBuilder,
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
     * Get short EPG for a stream.
     * CC: 2
     */
    suspend fun getShortEpg(
        streamId: Int,
        limit: Int = 20,
    ): List<XtreamEpgProgramme> =
        withContext(io) {
            val url = urlBuilder.playerApiUrl(
                "get_short_epg",
                mapOf("stream_id" to streamId.toString(), "limit" to limit.toString())
            )
            val body = runCatching { fetchRaw(url) }.getOrNull()
            if (body.isNullOrEmpty()) return@withContext emptyList()
            
            runCatching { 
                json.decodeFromString<Map<String, List<XtreamEpgProgramme>>>(body)
                    .values.flatten().take(limit)
            }.getOrElse { emptyList() }
        }

    /**
     * Get full EPG for a stream.
     * CC: 2
     */
    suspend fun getFullEpg(streamId: Int): List<XtreamEpgProgramme> =
        withContext(io) {
            val url = urlBuilder.playerApiUrl("get_simple_data_table", mapOf("stream_id" to streamId.toString()))
            val body = runCatching { fetchRaw(url) }.getOrNull()
            if (body.isNullOrEmpty()) return@withContext emptyList()
            
            runCatching {
                json.decodeFromString<Map<String, List<XtreamEpgProgramme>>>(body)
                    .values.flatten()
            }.getOrElse { emptyList() }
        }

    /**
     * Prefetch EPG data for multiple streams concurrently.
     * Launches parallel fetch operations and awaits all completions before returning.
     */
    suspend fun prefetchEpg(
        streamIds: List<Int>,
        perStreamLimit: Int = 10,
    ) {
        withContext(io) {
            streamIds.map { streamId ->
                async {
                    runCatching { getShortEpg(streamId, perStreamLimit) }
                }
            }.awaitAll()
        }
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
}

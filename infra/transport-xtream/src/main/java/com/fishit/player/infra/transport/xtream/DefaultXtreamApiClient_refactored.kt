package com.fishit.player.infra.transport.xtream

import android.os.SystemClock
import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.infra.transport.xtream.client.XtreamCategoryFetcher
import com.fishit.player.infra.transport.xtream.client.XtreamConnectionManager
import com.fishit.player.infra.transport.xtream.client.XtreamStreamFetcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

/**
 * DefaultXtreamApiClient – Production-Ready Xtream Codes API Client (REFACTORED)
 *
 * **ARCHITECTURE IMPROVEMENT (Target: ~800 lines, CC ≤ 10):**
 * - Delegates connection lifecycle to [XtreamConnectionManager]
 * - Delegates category operations to [XtreamCategoryFetcher]
 * - Delegates stream operations to [XtreamStreamFetcher]
 * - Keeps shared infrastructure: rate limiting, caching, URL building, EPG
 * - Reduces file from 2312 lines (CC ~52) to ~800 lines (CC ≤ 10)
 *
 * @param http OkHttpClient with Premium Contract settings
 * @param json JSON parser
 * @param parallelism Device-aware parallelism from DI
 * @param connectionManager Handles initialization, ping, close
 * @param categoryFetcher Handles category fetching
 * @param streamFetcher Handles stream fetching and batch operations
 * @param io Coroutine dispatcher for IO operations
 */
class DefaultXtreamApiClient(
    private val http: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val parallelism: XtreamParallelism,
    private val connectionManager: XtreamConnectionManager,
    private val categoryFetcher: XtreamCategoryFetcher,
    private val streamFetcher: XtreamStreamFetcher,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : XtreamApiClient {

    // =========================================================================
    // State (Delegated to ConnectionManager)
    // =========================================================================

    override val authState: StateFlow<XtreamAuthState> = connectionManager.authState
    override val connectionState: StateFlow<XtreamConnectionState> = connectionManager.connectionState
    override val capabilities: XtreamCapabilities? get() = connectionManager.capabilities

    /**
     * OkHttpClient with extended timeouts for streaming large JSON arrays.
     */
    private val streamingHttp: OkHttpClient by lazy {
        http.newBuilder()
            .readTimeout(XtreamTransportConfig.STREAMING_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(XtreamTransportConfig.STREAMING_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    // Rate limiting (shared across all instances for same host)
    private companion object {
        private const val TAG = "XtreamApiClient"
        private val rateMutex = Mutex()
        private val lastCallByHost = mutableMapOf<String, Long>()

        // Response cache
        private val cacheLock = Mutex()
        private val cache =
            object : LinkedHashMap<String, CacheEntry>(512, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheEntry>?): Boolean =
                    size > 512
            }

        private fun redactUrl(url: String): String =
            try {
                val httpUrl = url.toHttpUrlOrNull()
                if (httpUrl != null) {
                    "${httpUrl.host}${httpUrl.encodedPath}"
                } else {
                    "<invalid-url>"
                }
            } catch (_: Exception) {
                "<invalid-url>"
            }
    }

    private data class CacheEntry(
        val at: Long,
        val body: String,
    )

    /**
     * Semaphore for EPG parallel requests.
     */
    private val epgSemaphore = Semaphore(parallelism.value)

    // =========================================================================
    // Lifecycle (Delegated to ConnectionManager)
    // =========================================================================

    override suspend fun initialize(
        config: XtreamApiConfig,
        forceDiscovery: Boolean,
    ): Result<XtreamCapabilities> = connectionManager.initialize(config, forceDiscovery)

    override suspend fun ping(): Boolean = connectionManager.ping()

    override fun close() = connectionManager.close()

    // =========================================================================
    // Server & User Info
    // =========================================================================

    override suspend fun getServerInfo(): Result<XtreamServerInfo> =
        withContext(io) {
            try {
                val url = buildPlayerApiUrl(action = null)
                val safeUrl = redactUrl(url)
                UnifiedLog.d(TAG) { "getServerInfo: Fetching from $safeUrl" }

                val body = fetchRaw(url, isEpg = false)
                    ?: return@withContext Result.failure(
                        Exception("player_api.php returned non-JSON response")
                    )

                val parsed = runCatching { json.decodeFromString<XtreamServerInfo>(body) }
                    .getOrElse { parseError ->
                        return@withContext Result.failure(
                            Exception("Failed to parse server info JSON", parseError)
                        )
                    }

                UnifiedLog.d(TAG) { "Server info retrieved: ${parsed.serverInfo?.url ?: "unknown"}" }
                Result.success(parsed)
            } catch (e: Exception) {
                UnifiedLog.e(TAG, e) { "getServerInfo failed" }
                Result.failure(e)
            }
        }

    override suspend fun getPanelInfo(): String? =
        withContext(io) {
            val cfg = connectionManager.config ?: return@withContext null
            val builder = connectionManager.urlBuilder ?: XtreamUrlBuilder(
                cfg,
                connectionManager.resolvedPort,
                connectionManager.vodKind
            )

            try {
                val url = builder.panelApiUrl()
                UnifiedLog.d(TAG) { "getPanelInfo: Fetching from panel_api.php" }
                fetchRaw(url, isEpg = false)
            } catch (e: Exception) {
                UnifiedLog.w(TAG, e) { "getPanelInfo: panel_api.php not available or failed" }
                null
            }
        }

    override suspend fun getUserInfo(): Result<XtreamUserInfo> =
        withContext(io) {
            getServerInfo().mapCatching { serverInfo ->
                serverInfo.userInfo?.let { XtreamUserInfo.fromRaw(it) }
                    ?: throw Exception("No user info in response")
            }
        }

    // =========================================================================
    // Categories (Delegated to CategoryFetcher)
    // =========================================================================

    override suspend fun getLiveCategories(): List<XtreamCategory> =
        categoryFetcher.getLiveCategories()

    override suspend fun getVodCategories(): List<XtreamCategory> {
        val (categories, newVodKind) = categoryFetcher.getVodCategories(connectionManager.vodKind)
        connectionManager.vodKind = newVodKind
        return categories
    }

    override suspend fun getSeriesCategories(): List<XtreamCategory> =
        categoryFetcher.getSeriesCategories()

    // =========================================================================
    // Content Lists (Delegated to StreamFetcher)
    // =========================================================================

    override suspend fun getLiveStreams(
        categoryId: String?,
        limit: Int,
        offset: Int,
    ): List<XtreamLiveStream> =
        withContext(io) {
            val all = streamFetcher.getLiveStreams(categoryId)
            sliceList(all, offset, limit)
        }

    override suspend fun getVodStreams(
        categoryId: String?,
        limit: Int,
        offset: Int,
    ): List<XtreamVodStream> =
        withContext(io) {
            val all = streamFetcher.getVodStreams(categoryId)
            sliceList(all, offset, limit)
        }

    override suspend fun getSeries(
        categoryId: String?,
        limit: Int,
        offset: Int,
    ): List<XtreamSeriesStream> =
        withContext(io) {
            val all = streamFetcher.getSeries(categoryId)
            sliceList(all, offset, limit)
        }

    // =========================================================================
    // Streaming Batch Methods (Delegated to StreamFetcher)
    // =========================================================================

    override suspend fun streamVodInBatches(
        batchSize: Int,
        categoryId: String?,
        onBatch: suspend (List<XtreamVodStream>) -> Unit,
    ): Int = streamFetcher.streamVodInBatches(batchSize, categoryId, onBatch)

    override suspend fun streamSeriesInBatches(
        batchSize: Int,
        categoryId: String?,
        onBatch: suspend (List<XtreamSeriesStream>) -> Unit,
    ): Int = streamFetcher.streamSeriesInBatches(batchSize, categoryId, onBatch)

    override suspend fun streamLiveInBatches(
        batchSize: Int,
        categoryId: String?,
        onBatch: suspend (List<XtreamLiveStream>) -> Unit,
    ): Int = streamFetcher.streamLiveInBatches(batchSize, categoryId, onBatch)

    // =========================================================================
    // Count Methods (Delegated to StreamFetcher)
    // =========================================================================

    override suspend fun countVodStreams(categoryId: String?): Int =
        streamFetcher.countVodStreams(categoryId)

    override suspend fun countSeries(categoryId: String?): Int =
        streamFetcher.countSeries(categoryId)

    override suspend fun countLiveStreams(categoryId: String?): Int =
        streamFetcher.countLiveStreams(categoryId)

    // =========================================================================
    // Detail Endpoints (Delegated to StreamFetcher)
    // =========================================================================

    override suspend fun getVodInfo(vodId: Int): XtreamVodInfo? =
        streamFetcher.getVodInfo(vodId)

    override suspend fun getSeriesInfo(seriesId: Int): XtreamSeriesInfo? =
        streamFetcher.getSeriesInfo(seriesId)

    // =========================================================================
    // EPG
    // =========================================================================

    override suspend fun getShortEpg(
        streamId: Int,
        limit: Int,
    ): List<XtreamEpgProgramme> =
        withContext(io) {
            val url = buildPlayerApiUrl(
                "get_short_epg",
                mapOf(
                    "stream_id" to streamId.toString(),
                    "limit" to limit.toString(),
                ),
            )
            val body = fetchRaw(url, isEpg = true) ?: return@withContext emptyList()

            val root = runCatching { json.parseToJsonElement(body) }.getOrNull()
                ?: return@withContext emptyList()

            val listings = when {
                root is JsonArray -> root
                root is JsonObject && root.containsKey("epg_listings") ->
                    root["epg_listings"]?.jsonArray
                else -> null
            } ?: return@withContext emptyList()

            listings.mapNotNull { el ->
                val obj = el.jsonObjectOrNull() ?: return@mapNotNull null
                XtreamEpgProgramme(
                    id = obj.stringOrNull("id"),
                    epgId = obj.stringOrNull("epg_id"),
                    title = obj.stringOrNull("title"),
                    lang = obj.stringOrNull("lang"),
                    start = obj.stringOrNull("start"),
                    startTimestamp = obj.longOrNull("start_timestamp"),
                    end = obj.stringOrNull("end"),
                    endTimestamp = obj.longOrNull("end_timestamp"),
                    stopTimestamp = obj.longOrNull("stop_timestamp"),
                    description = obj.stringOrNull("description"),
                    channelId = obj.stringOrNull("channel_id"),
                    hasArchive = obj.intOrNull("has_archive"),
                )
            }
        }

    override suspend fun getFullEpg(streamId: Int): List<XtreamEpgProgramme> =
        withContext(io) {
            if (capabilities?.extras?.supportsSimpleDataTable == true) {
                val url = buildPlayerApiUrl(
                    "get_simple_data_table",
                    mapOf("stream_id" to streamId.toString()),
                )
                val body = fetchRaw(url, isEpg = true)
                if (!body.isNullOrEmpty()) {
                    return@withContext getShortEpg(streamId, 200)
                }
            }
            getShortEpg(streamId, 200)
        }

    override suspend fun prefetchEpg(
        streamIds: List<Int>,
        perStreamLimit: Int,
    ) = withContext(io) {
        streamIds.distinct().forEach { id ->
            epgSemaphore.withPermit { runCatching { getShortEpg(id, perStreamLimit) } }
        }
    }

    // =========================================================================
    // Playback URLs
    // =========================================================================

    override fun buildLiveUrl(
        streamId: Int,
        extension: String?,
    ): String {
        val cfg = connectionManager.config ?: return ""
        val ext = normalizeExtension(
            extension ?: cfg.liveExtPrefs.firstOrNull() ?: "m3u8",
            isLive = true,
        )
        return buildPlayUrl("live", streamId, ext)
    }

    override fun buildVodUrl(
        vodId: Int,
        containerExtension: String?,
    ): String {
        val cfg = connectionManager.config ?: return ""
        val ext = sanitizeExtension(containerExtension ?: cfg.vodExtPrefs.firstOrNull() ?: "m3u8")
        val playbackKind = resolveVodPlaybackKind(connectionManager.vodKind)
        return buildPlayUrl(playbackKind, vodId, ext)
    }

    override fun buildSeriesEpisodeUrl(
        seriesId: Int,
        seasonNumber: Int,
        episodeNumber: Int,
        episodeId: Int?,
        containerExtension: String?,
    ): String {
        val cfg = connectionManager.config ?: return ""
        val ext = if (!containerExtension.isNullOrBlank()) {
            sanitizeSeriesExtension(containerExtension)
        } else {
            cfg.seriesExtPrefs.firstOrNull()?.let { sanitizeSeriesExtension(it) } ?: "mkv"
        }

        val actualId = episodeId ?: (seriesId * 10000 + seasonNumber * 100 + episodeNumber)
        return buildPlayUrl("series", actualId, ext)
    }

    private fun buildPlayUrl(
        kind: String,
        id: Int,
        ext: String,
    ): String {
        val cfg = connectionManager.config ?: return ""
        val builder = connectionManager.urlBuilder ?: XtreamUrlBuilder(
            cfg,
            connectionManager.resolvedPort,
            connectionManager.vodKind
        )
        return builder.playUrl(kind, id, ext)
    }

    private fun resolveVodPlaybackKind(vodKind: String): String =
        when (vodKind) {
            "movie", "movies" -> "movie"
            else -> "vod"
        }

    // =========================================================================
    // Internal: Shared Infrastructure
    // =========================================================================

    /**
     * Build player_api.php URL.
     */
    private fun buildPlayerApiUrl(
        action: String?,
        params: Map<String, String>? = null,
    ): String {
        val cfg = connectionManager.config ?: return ""
        val builder = connectionManager.urlBuilder ?: XtreamUrlBuilder(
            cfg,
            connectionManager.resolvedPort,
            connectionManager.vodKind
        )
        return builder.playerApiUrl(action, params)
    }

    /**
     * Fetch raw HTTP response with rate limiting and caching.
     */
    private suspend fun fetchRaw(
        url: String,
        isEpg: Boolean,
    ): String? {
        // Check cache
        readCache(url, isEpg)?.let { return it }

        // Rate limit
        takeRateSlot(connectionManager.config?.host ?: "")

        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("Accept-Encoding", "gzip")
            .header("User-Agent", "FishIT-Player/2.x (Android)")
            .get()
            .build()

        val safeUrl = redactUrl(url)

        return try {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    UnifiedLog.i(TAG) { "HTTP ${response.code} for $safeUrl" }
                    return null
                }

                val bodyBytes = response.body?.bytes() ?: return null
                if (bodyBytes.isEmpty()) {
                    UnifiedLog.i(TAG) { "Empty body for $safeUrl" }
                    return null
                }

                // Handle gzip
                val body = if (bodyBytes.size >= 2 &&
                    (bodyBytes[0].toInt() and 0xFF) == 0x1F &&
                    (bodyBytes[1].toInt() and 0xFF) == 0x8B
                ) {
                    try {
                        GZIPInputStream(bodyBytes.inputStream()).use { gzipStream ->
                            gzipStream.bufferedReader().use { it.readText() }
                        }
                    } catch (e: Exception) {
                        String(bodyBytes, Charsets.UTF_8)
                    }
                } else {
                    String(bodyBytes, Charsets.UTF_8)
                }

                // JSON validation
                val trimmed = body.trimStart()
                if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
                    UnifiedLog.w(TAG) { "Non-JSON response for $safeUrl" }
                    return null
                }

                writeCache(url, body)
                body
            }
        } catch (e: Exception) {
            UnifiedLog.i(TAG) { "Network error for $safeUrl: ${e.message}" }
            null
        }
    }

    /**
     * Fetch raw HTTP response as InputStream for streaming JSON parsing.
     */
    private suspend fun fetchRawAsStream(url: String): StreamingResponse {
        takeRateSlot(connectionManager.config?.host ?: "")

        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("Accept-Encoding", "gzip")
            .header("User-Agent", "FishIT-Player/2.x (Android)")
            .get()
            .build()

        val safeUrl = redactUrl(url)

        return try {
            val response = streamingHttp.newCall(request).execute()

            if (!response.isSuccessful) {
                UnifiedLog.i(TAG) { "StreamingFetch: HTTP ${response.code} for $safeUrl" }
                response.close()
                return StreamingResponse(null, null)
            }

            val responseBody = response.body
            if (responseBody == null) {
                UnifiedLog.w(TAG) { "StreamingFetch: Empty body for $safeUrl" }
                response.close()
                return StreamingResponse(null, null)
            }

            var inputStream: InputStream = responseBody.byteStream()

            // Check for GZIP manually
            val pushback = java.io.PushbackInputStream(inputStream, 2)
            val b1 = pushback.read()
            val b2 = pushback.read()

            if (b1 == 0x1F && b2 == 0x8B) {
                pushback.unread(b2)
                pushback.unread(b1)
                inputStream = GZIPInputStream(pushback)
            } else if (b1 != -1) {
                if (b2 != -1) pushback.unread(b2)
                pushback.unread(b1)
                inputStream = pushback
            }

            StreamingResponse(inputStream, response)
        } catch (e: Exception) {
            UnifiedLog.i(TAG) { "StreamingFetch: Failed $safeUrl - ${e.javaClass.simpleName}" }
            StreamingResponse(null, null)
        }
    }

    data class StreamingResponse(
        val inputStream: InputStream?,
        private val response: Response?,
    ) : AutoCloseable {
        override fun close() {
            runCatching { inputStream?.close() }
            runCatching { response?.close() }
        }

        val isSuccessful: Boolean get() = inputStream != null
    }

    private suspend fun takeRateSlot(host: String) {
        rateMutex.withLock {
            val now = SystemClock.elapsedRealtime()
            val lastCall = lastCallByHost[host] ?: 0L
            val delta = now - lastCall
            if (delta in 0 until XtreamTransportConfig.MIN_INTERVAL_MS) {
                delay(XtreamTransportConfig.MIN_INTERVAL_MS - delta)
            }
            lastCallByHost[host] = SystemClock.elapsedRealtime()
        }
    }

    private suspend fun readCache(url: String, isEpg: Boolean): String? {
        val ttl = if (isEpg) {
            XtreamTransportConfig.EPG_CACHE_TTL_MS
        } else {
            XtreamTransportConfig.CACHE_TTL_MS
        }
        return cacheLock.withLock {
            val entry = cache[url] ?: return@withLock null
            if ((SystemClock.elapsedRealtime() - entry.at) <= ttl) entry.body else null
        }
    }

    private suspend fun writeCache(url: String, body: String) {
        cacheLock.withLock { cache[url] = CacheEntry(SystemClock.elapsedRealtime(), body) }
    }

    // =========================================================================
    // Internal: JSON Utilities
    // =========================================================================

    private fun JsonElement.jsonObjectOrNull(): JsonObject? = runCatching { jsonObject }.getOrNull()

    private fun JsonObject.stringOrNull(key: String): String? {
        val element = this[key] ?: return null
        return when {
            element is JsonPrimitive -> element.contentOrNull
            element is JsonArray && element.isNotEmpty() -> {
                element.firstOrNull()?.jsonPrimitive?.contentOrNull
            }
            else -> null
        }
    }

    private fun JsonObject.intOrNull(key: String): Int? {
        val element = this[key] ?: return null
        return if (element is JsonPrimitive) element.intOrNull else null
    }

    private fun JsonObject.longOrNull(key: String): Long? {
        val element = this[key] ?: return null
        return if (element is JsonPrimitive) element.longOrNull else null
    }

    private fun JsonObject.doubleOrNull(key: String): Double? {
        val element = this[key] ?: return null
        return if (element is JsonPrimitive) element.doubleOrNull else null
    }

    // =========================================================================
    // Internal: Utilities
    // =========================================================================

    private fun <T> sliceList(list: List<T>, offset: Int, limit: Int): List<T> {
        val from = offset.coerceAtLeast(0)
        val to = (offset + limit).coerceAtMost(list.size)
        return if (from < to) list.subList(from, to) else emptyList()
    }

    private fun normalizeExtension(ext: String, isLive: Boolean): String {
        val lower = ext.lowercase().trim()
        return when {
            isLive && lower == "hls" -> "m3u8"
            isLive && lower in listOf("m3u8", "ts") -> lower
            !isLive -> sanitizeExtension(lower)
            else -> "m3u8"
        }
    }

    private fun sanitizeExtension(ext: String?): String {
        val lower = ext?.lowercase()?.trim().orEmpty()
        val validFormats = setOf("m3u8", "ts", "mkv", "mp4", "avi", "mov", "wmv", "flv", "webm")
        return if (lower in validFormats) lower else "m3u8"
    }

    private fun sanitizeSeriesExtension(ext: String): String {
        val lower = ext.lowercase().trim()
        val validSeriesFormats = setOf("mkv", "mp4", "avi", "mov", "wmv", "flv", "webm")

        if (lower in validSeriesFormats) {
            return lower
        }

        if (lower in setOf("m3u8", "ts")) {
            throw IllegalArgumentException(
                "Invalid extension for series episode: '$ext'. " +
                    "Series episodes require container formats (mp4, mkv, avi, etc.), not streaming formats (m3u8, ts)."
            )
        }

        throw IllegalArgumentException(
            "Invalid extension for series episode: '$ext'. " +
                "Valid formats: mp4, mkv, avi, mov, wmv, flv, webm"
        )
    }
}

package com.fishit.player.infra.transport.xtream

import android.os.SystemClock
import com.fishit.player.infra.logging.UnifiedLog
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * DefaultXtreamApiClient – Production-Ready Xtream Codes API Client
 *
 * Vollständige Implementation des [XtreamApiClient] Interface mit:
 * - Per-Host Rate-Limiting (120ms Min-Intervall)
 * - In-Memory Response Cache (60s TTL, 15s für EPG)
 * - Automatische Alias-Resolution (vod/movie/movies)
 * - Flexible ID-Feld-Erkennung (stream_id/vod_id/movie_id/id)
 * - Port-Auto-Discovery (optional)
 * - Credential-Redaktion im Logging
 *
 * Basiert auf v1 XtreamClient.kt mit Verbesserungen aus:
 * - tellytv/telly (Panel-Kompatibilität)
 * - Real-World Testing mit verschiedenen Panels
 *
 * @param http OkHttpClient with Premium Contract settings (timeouts, headers, dispatcher)
 * @param json JSON parser
 * @param parallelism Device-aware parallelism from DI (SSOT for Semaphores)
 * @param io Coroutine dispatcher for IO operations
 * @param capabilityStore Optional cache for capabilities
 * @param portStore Optional cache for resolved ports
 */
class DefaultXtreamApiClient(
        private val http: OkHttpClient,
        private val json: Json = Json { ignoreUnknownKeys = true },
        private val parallelism: XtreamParallelism,
        private val io: CoroutineDispatcher = Dispatchers.IO,
        private val capabilityStore: XtreamCapabilityStore? = null,
        private val portStore: XtreamPortStore? = null,
) : XtreamApiClient {
    // =========================================================================
    // State
    // =========================================================================

    private val _authState = MutableStateFlow<XtreamAuthState>(XtreamAuthState.Unknown)
    override val authState: StateFlow<XtreamAuthState> = _authState.asStateFlow()

    private val _connectionState =
            MutableStateFlow<XtreamConnectionState>(XtreamConnectionState.Disconnected)
    override val connectionState: StateFlow<XtreamConnectionState> = _connectionState.asStateFlow()

    private var _capabilities: XtreamCapabilities? = null
    override val capabilities: XtreamCapabilities?
        get() = _capabilities

    private var config: XtreamApiConfig? = null
    private var resolvedPort: Int = 80
    private var vodKind: String = "vod"
    private var urlBuilder: XtreamUrlBuilder? = null

    // Rate limiting (shared across all instances for same host)
    private companion object {
        private const val TAG = "XtreamApiClient"
        private const val DEFAULT_LIMIT = 100 // Classic pagination cutoff for many Xtream panels
        private val rateMutex = Mutex()
        private val lastCallByHost = mutableMapOf<String, Long>()

        // Response cache
        private val cacheLock = Mutex()
        private val cache =
                object : LinkedHashMap<String, CacheEntry>(512, 0.75f, true) {
                    override fun removeEldestEntry(
                            eldest: MutableMap.MutableEntry<String, CacheEntry>?
                    ): Boolean = size > 512
                }

        // VOD alias candidates in preference order
        private val VOD_ALIAS_CANDIDATES = listOf("vod", "movie", "movies")

        // ID field candidates in preference order
        private val VOD_ID_FIELDS = listOf("vod_id", "movie_id", "id", "stream_id")
        private val LIVE_ID_FIELDS = listOf("stream_id", "id")
        private val SERIES_ID_FIELDS = listOf("series_id", "id")

        /**
         * Redact URL for safe logging: returns "host/path" only. No query parameters (which contain
         * credentials) are logged.
         */
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
     *
     * Premium Contract Section 5: Use device-class parallelism from DI (SSOT). This semaphore
     * provides coroutine-level throttling consistent with OkHttp Dispatcher limits.
     */
    private val epgSemaphore = Semaphore(parallelism.value)

    // =========================================================================
    // Lifecycle
    // =========================================================================

    override suspend fun initialize(
            config: XtreamApiConfig,
            forceDiscovery: Boolean,
    ): Result<XtreamCapabilities> =
            withContext(io) {
                UnifiedLog.d(TAG) {
                    "Initializing client for ${config.host} (forceDiscovery=$forceDiscovery)"
                }
                this@DefaultXtreamApiClient.config = config
                _connectionState.value = XtreamConnectionState.Connecting
                _authState.value = XtreamAuthState.Pending

                try {
                    // 1. Resolve port if not specified
                    resolvedPort = config.port ?: resolvePort(config)
                    UnifiedLog.d(TAG) { "Resolved port: $resolvedPort" }

                    // 2. Check capability cache
                    val cacheKey = buildCacheKey(config, resolvedPort)
                    if (!forceDiscovery) {
                        capabilityStore?.get(cacheKey)?.let { cached ->
                            _capabilities = cached
                            vodKind = cached.resolvedAliases.vodKind ?: "vod"
                            return@withContext validateAndComplete(config, cached)
                        }
                    }

                    // 3. Discover capabilities
                    val startTime = SystemClock.elapsedRealtime()
                    val caps = discoverCapabilities(config, resolvedPort, cacheKey)
                    val latency = SystemClock.elapsedRealtime() - startTime

                    _capabilities = caps
                    vodKind = caps.resolvedAliases.vodKind ?: "vod"
                    capabilityStore?.put(caps)

                    // 4. Validate credentials
                    validateAndComplete(config, caps, latency)
                } catch (e: Exception) {
                    UnifiedLog.e(TAG, e) { "Initialize failed for ${config.host}" }
                    val error = mapException(e)
                    _connectionState.value = XtreamConnectionState.Error(error, retryable = true)
                    _authState.value = XtreamAuthState.Failed(error)
                    Result.failure(e)
                }
            }

    @Suppress("UNUSED_PARAMETER")
    private suspend fun validateAndComplete(
            config: XtreamApiConfig,
            caps: XtreamCapabilities,
            latency: Long = 0,
    ): Result<XtreamCapabilities> {
        val result = getServerInfo()
        return result.fold(
                onSuccess = { serverInfo ->
                    val userInfo = serverInfo.userInfo?.let { XtreamUserInfo.fromRaw(it) }
                    if (userInfo != null) {
                        when (userInfo.status) {
                            XtreamUserInfo.UserStatus.ACTIVE -> {
                                _authState.value = XtreamAuthState.Authenticated(userInfo)
                                _connectionState.value =
                                        XtreamConnectionState.Connected(caps.baseUrl, latency)
                                Result.success(caps)
                            }
                            XtreamUserInfo.UserStatus.EXPIRED -> {
                                _authState.value = XtreamAuthState.Expired(userInfo.expDateEpoch)
                                Result.failure(Exception("Account expired"))
                            }
                            else -> {
                                _authState.value =
                                        XtreamAuthState.Failed(XtreamError.InvalidCredentials)
                                Result.failure(Exception("Account not active: ${userInfo.status}"))
                            }
                        }
                    } else {
                        // No user info but server responded - assume OK
                        UnifiedLog.d(TAG) {
                            "validateAndComplete: No user info in response, assuming OK"
                        }
                        _connectionState.value =
                                XtreamConnectionState.Connected(caps.baseUrl, latency)
                        Result.success(caps)
                    }
                },
                onFailure = { error ->
                    UnifiedLog.w(TAG, error) {
                        "validateAndComplete: getServerInfo failed, trying fallback validation"
                    }
                    // Fallback: Try a simple action-based endpoint to validate connectivity
                    val fallbackResult = tryFallbackValidation()
                    if (fallbackResult) {
                        UnifiedLog.d(TAG) { "validateAndComplete: Fallback validation succeeded" }
                        _connectionState.value =
                                XtreamConnectionState.Connected(caps.baseUrl, latency)
                        _authState.value = XtreamAuthState.Unknown
                        Result.success(caps)
                    } else {
                        UnifiedLog.e(TAG) { "validateAndComplete: Fallback validation failed" }
                        _authState.value = XtreamAuthState.Failed(XtreamError.InvalidCredentials)
                        Result.failure(error)
                    }
                },
        )
    }

    /**
     * Fallback validation when getServerInfo() fails. Some servers don't support player_api.php
     * without action parameter. Try multiple action-based endpoints to validate connectivity.
     *
     * This ensures lenient validation - as long as ANY endpoint returns valid JSON, we accept the
     * server configuration.
     */
    private suspend fun tryFallbackValidation(): Boolean =
            withContext(io) {
                // Try multiple endpoints in order of likelihood
                val fallbackActions =
                        listOf(
                                "get_live_categories",
                                "get_vod_categories",
                                "get_series_categories",
                                "get_live_streams",
                        )

                for (action in fallbackActions) {
                    try {
                        UnifiedLog.d(TAG) { "tryFallbackValidation: Trying $action" }
                        val url = buildPlayerApiUrl(action)
                        val body = fetchRaw(url, isEpg = false)

                        if (body != null && body.isNotEmpty()) {
                            // Try to parse as JSON to verify it's a valid response
                            val parsed = runCatching { json.parseToJsonElement(body) }.getOrNull()
                            if (parsed != null) {
                                UnifiedLog.d(TAG) {
                                    "tryFallbackValidation: Success with $action - received valid JSON response"
                                }
                                return@withContext true
                            }
                        }
                    } catch (e: Exception) {
                        // Continue to next action
                        UnifiedLog.d(TAG) {
                            "tryFallbackValidation: $action failed, trying next..."
                        }
                    }
                }

                UnifiedLog.w(TAG) { "tryFallbackValidation: All fallback endpoints failed" }
                false
            }

    override suspend fun ping(): Boolean =
            withContext(io) {
                if (config == null) return@withContext false
                try {
                    val url = buildPlayerApiUrl("get_live_categories")
                    val body = fetchRaw(url, isEpg = false)
                    // Trim whitespace and BOM (U+FEFF) before checking JSON start
                    val trimmed = body?.trim { it.isWhitespace() || it == '\uFEFF' }
                    !trimmed.isNullOrEmpty() && (trimmed.startsWith("[") || trimmed.startsWith("{"))
                } catch (_: Exception) {
                    false
                }
            }

    override fun close() {
        _connectionState.value = XtreamConnectionState.Disconnected
        _authState.value = XtreamAuthState.Unknown
        _capabilities = null
        config = null
        urlBuilder = null
    }

    // =========================================================================
    // Server & User Info (Premium Contract Section 2 X-10)
    // =========================================================================

    override suspend fun getServerInfo(): Result<XtreamServerInfo> =
            withContext(io) {
                try {
                    val url = buildPlayerApiUrl(action = null) // No action = server info
                    val safeUrl = redactUrl(url)
                    UnifiedLog.d(TAG) { "getServerInfo: Fetching from $safeUrl" }

                    val body =
                            fetchRaw(url, isEpg = false)
                                    ?: run {
                                        // Non-JSON response from player_api.php endpoint
                                        // This will trigger fallback validation with action-based
                                        // endpoints
                                        UnifiedLog.d(TAG) {
                                            "XtreamConnect: player_api.php returned non-JSON response, will try fallback validation (endpoint=player_api.php, action=null)"
                                        }
                                        return@withContext Result.failure(
                                                Exception(
                                                        "player_api.php returned non-JSON response. Will try fallback validation.",
                                                ),
                                        )
                                    }

                    UnifiedLog.d(TAG) { "getServerInfo: Received ${body.length} bytes, parsing..." }

                    // Try to parse the JSON response
                    // If parsing fails, treat it as empty response (lenient mode)
                    val parsed =
                            runCatching { json.decodeFromString<XtreamServerInfo>(body) }
                                    .getOrElse { parseError ->
                                        UnifiedLog.w(TAG, parseError) {
                                            "getServerInfo: JSON parsing failed, treating as empty response"
                                        }
                                        // Return empty server info - fallback validation will be
                                        // triggered
                                        return@withContext Result.failure(
                                                Exception(
                                                        "Failed to parse server info JSON. Will try fallback validation.",
                                                        parseError,
                                                ),
                                        )
                                    }

                    UnifiedLog.d(TAG) {
                        "Server info retrieved: ${parsed.serverInfo?.url ?: "unknown"}"
                    }
                    Result.success(parsed)
                } catch (e: Exception) {
                    UnifiedLog.e(TAG, e) { "getServerInfo failed" }
                    Result.failure(e)
                }
            }

    /**
     * Fetch panel info via panel_api.php (optional diagnostics endpoint).
     *
     * Per Premium Contract Section 2 (X-10) and Section 8:
     * - Used for optional diagnostics and capability detection
     * - May not be available on all panels
     *
     * @return Raw JSON response body or null if not available/supported
     */
    override suspend fun getPanelInfo(): String? =
            withContext(io) {
                val cfg = config ?: return@withContext null
                val builder = urlBuilder ?: XtreamUrlBuilder(cfg, resolvedPort, vodKind)

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
    // Categories
    // =========================================================================

    override suspend fun getLiveCategories(): List<XtreamCategory> =
            fetchCategories("get_live_categories")

    override suspend fun getVodCategories(): List<XtreamCategory> {
        // Try aliases in order
        for (alias in listOf(vodKind) + VOD_ALIAS_CANDIDATES.filter { it != vodKind }) {
            val result = fetchCategories("get_${alias}_categories")
            if (result.isNotEmpty()) {
                vodKind = alias
                return result
            }
        }
        return emptyList()
    }

    override suspend fun getSeriesCategories(): List<XtreamCategory> =
            fetchCategories("get_series_categories")

    private suspend fun fetchCategories(action: String): List<XtreamCategory> =
            withContext(io) {
                val url = buildPlayerApiUrl(action)
                val body = fetchRaw(url, isEpg = false) ?: return@withContext emptyList()
                parseJsonArray(
                        body,
                        { obj ->
                            XtreamCategory(
                                    categoryId = obj.stringOrNull("category_id"),
                                    categoryName = obj.stringOrNull("category_name"),
                                    parentId = obj.intOrNull("parent_id"),
                            )
                        },
                        action,
                )
            }

    // =========================================================================
    // Content Lists
    // =========================================================================

    override suspend fun getLiveStreams(
            categoryId: String?,
            limit: Int,
            offset: Int,
    ): List<XtreamLiveStream> =
            withContext(io) {
                val all =
                        fetchStreamsWithCategoryFallback("get_live_streams", categoryId) { obj ->
                            XtreamLiveStream(
                                    num = obj.intOrNull("num"),
                                    name = obj.stringOrNull("name"),
                                    streamId = obj.intOrNull("stream_id"),
                                    id = obj.intOrNull("id"),
                                    streamIcon = obj.stringOrNull("stream_icon"),
                                    logo = obj.stringOrNull("logo"),
                                    epgChannelId = obj.stringOrNull("epg_channel_id"),
                                    tvArchive = obj.intOrNull("tv_archive"),
                                    tvArchiveDuration = obj.intOrNull("tv_archive_duration"),
                                    categoryId = obj.stringOrNull("category_id"),
                                    added = obj.stringOrNull("added"),
                                    isAdult = obj.stringOrNull("is_adult"),
                            )
                        }
                sliceList(all, offset, limit)
            }

    override suspend fun getVodStreams(
            categoryId: String?,
            limit: Int,
            offset: Int,
    ): List<XtreamVodStream> =
            withContext(io) {
                // Try aliases in order
                for (alias in listOf(vodKind) + VOD_ALIAS_CANDIDATES.filter { it != vodKind }) {
                    val all =
                            fetchStreamsWithCategoryFallback("get_${alias}_streams", categoryId) {
                                    obj ->
                                XtreamVodStream(
                                        num = obj.intOrNull("num"),
                                        name = obj.stringOrNull("name"),
                                        vodId = obj.intOrNull("vod_id"),
                                        movieId = obj.intOrNull("movie_id"),
                                        streamId = obj.intOrNull("stream_id"),
                                        id = obj.intOrNull("id"),
                                        streamIcon = obj.stringOrNull("stream_icon"),
                                        posterPath = obj.stringOrNull("poster_path"),
                                        cover = obj.stringOrNull("cover"),
                                        logo = obj.stringOrNull("logo"),
                                        categoryId = obj.stringOrNull("category_id"),
                                        containerExtension =
                                                obj.stringOrNull("container_extension"),
                                        added = obj.stringOrNull("added"),
                                        rating = obj.stringOrNull("rating"),
                                        rating5Based = obj.doubleOrNull("rating_5based"),
                                        isAdult = obj.stringOrNull("is_adult"),
                                        year = obj.stringOrNull("year"),
                                        genre = obj.stringOrNull("genre"),
                                        plot = obj.stringOrNull("plot"),
                                        duration = obj.stringOrNull("duration"),
                                )
                            }
                    if (all.isNotEmpty()) {
                        vodKind = alias
                        return@withContext sliceList(all, offset, limit)
                    }
                }
                emptyList()
            }

    override suspend fun getSeries(
            categoryId: String?,
            limit: Int,
            offset: Int,
    ): List<XtreamSeriesStream> =
            withContext(io) {
                val all =
                        fetchStreamsWithCategoryFallback("get_series", categoryId) { obj ->
                            XtreamSeriesStream(
                                    num = obj.intOrNull("num"),
                                    name = obj.stringOrNull("name"),
                                    seriesId = obj.intOrNull("series_id"),
                                    id = obj.intOrNull("id"),
                                    cover = obj.stringOrNull("cover"),
                                    posterPath = obj.stringOrNull("poster_path"),
                                    logo = obj.stringOrNull("logo"),
                                    backdropPath = obj.stringOrNull("backdrop_path"),
                                    categoryId = obj.stringOrNull("category_id"),
                                    added = obj.stringOrNull("added"),
                                    rating = obj.stringOrNull("rating"),
                                    rating5Based = obj.doubleOrNull("rating_5based"),
                                    isAdult = obj.stringOrNull("is_adult"),
                                    year = obj.stringOrNull("year"),
                                    genre = obj.stringOrNull("genre"),
                                    plot = obj.stringOrNull("plot"),
                                    cast = obj.stringOrNull("cast"),
                                    episodeRunTime = obj.stringOrNull("episode_run_time"),
                                    lastModified = obj.stringOrNull("last_modified"),
                            )
                        }
                sliceList(all, offset, limit)
            }

    /**
     * Fetch streams with intelligent category_id fallback.
     *
     * Some Xtream panels behave oddly depending on whether `category_id` is present. In practice we
     * have seen:
     * - `category_id=*` returning the FULL list on most panels (preferred)
     * - `category_id=0` working on some panels
     * - no `category_id` returning a truncated/limited list on many panels
     *
     * Strategy (ported from v1 XtreamClient.sliceArray):
     * - If a concrete [categoryId] is provided → request that category only.
     * - If no category is provided:
     * 1. Try `category_id=*` first (works on most panels, returns ALL items)
     * 2. If empty → try `category_id=0`
     * 3. If still empty → try without `category_id` as last resort
     *
     * This order is CRITICAL: many panels truncate results when no category_id is provided, but
     * return the full catalog with `category_id=*`.
     */
    private suspend fun <T> fetchStreamsWithCategoryFallback(
            action: String,
            categoryId: String?,
            mapper: (JsonObject) -> T,
    ): List<T> {
        if (categoryId != null) {
            // Specific category requested
            val url = buildPlayerApiUrl(action, mapOf("category_id" to categoryId))
            val body = fetchRaw(url, isEpg = false) ?: return emptyList()
            return parseJsonArray(body, mapper, action)
        }

        suspend fun fetchAndParse(params: Map<String, String>?): List<T> {
            val url =
                    if (params == null) {
                        buildPlayerApiUrl(action)
                    } else {
                        buildPlayerApiUrl(action, params)
                    }
            val body = runCatching { fetchRaw(url, isEpg = false) }.getOrNull()
            if (body.isNullOrEmpty()) return emptyList()
            return parseJsonArray(body, mapper, action)
        }

        // 1) Try category_id=* first (works on most panels, returns ALL items)
        val star = fetchAndParse(mapOf("category_id" to "*"))
        if (star.isNotEmpty()) {
            UnifiedLog.d(TAG) {
                "fetchStreamsWithCategoryFallback($action): got ${star.size} items with category_id=*"
            }
            return star
        }

        // 2) If empty → try category_id=0
        val zero = fetchAndParse(mapOf("category_id" to "0"))
        if (zero.isNotEmpty()) {
            UnifiedLog.d(TAG) {
                "fetchStreamsWithCategoryFallback($action): got ${zero.size} items with category_id=0"
            }
            return zero
        }

        // 3) As last resort → try without category_id
        val plain = fetchAndParse(null)
        UnifiedLog.d(TAG) {
            "fetchStreamsWithCategoryFallback($action): got ${plain.size} items without category_id (fallback)"
        }
        return plain
    }

    // =========================================================================
    // Detail Endpoints
    // =========================================================================

    override suspend fun getVodInfo(vodId: Int): XtreamVodInfo? =
            withContext(io) {
                // Try different alias + ID field combinations
                for (alias in listOf(vodKind) + VOD_ALIAS_CANDIDATES.filter { it != vodKind }) {
                    for (idField in VOD_ID_FIELDS) {
                        val url =
                                buildPlayerApiUrl(
                                        "get_${alias}_info",
                                        mapOf(idField to vodId.toString()),
                                )
                        val body = runCatching { fetchRaw(url, isEpg = false) }.getOrNull()
                        // Trim whitespace and BOM (U+FEFF) that may be added by gzip decompression
                        // or proxies
                        // Handle both orders: whitespace+BOM or BOM+whitespace
                        val trimmedBody = body?.trim { it.isWhitespace() || it == '\uFEFF' }
                        if (!trimmedBody.isNullOrEmpty() && trimmedBody.startsWith("{")) {
                            val parsed =
                                    runCatching {
                                                json.decodeFromString<XtreamVodInfo>(trimmedBody)
                                            }
                                            .getOrNull()
                            if (parsed != null && (parsed.info != null || parsed.movieData != null)
                            ) {
                                vodKind = alias
                                return@withContext parsed
                            }
                        }
                    }
                }
                null
            }

    override suspend fun getSeriesInfo(seriesId: Int): XtreamSeriesInfo? =
            withContext(io) {
                val url =
                        buildPlayerApiUrl(
                                "get_series_info",
                                mapOf("series_id" to seriesId.toString()),
                        )
                val body = fetchRaw(url, isEpg = false) ?: return@withContext null
                // Trim whitespace and BOM (U+FEFF) for consistency with getVodInfo()
                val trimmedBody = body.trim { it.isWhitespace() || it == '\uFEFF' }
                runCatching { json.decodeFromString<XtreamSeriesInfo>(trimmedBody) }.getOrNull()
            }

    // =========================================================================
    // EPG
    // =========================================================================

    override suspend fun getShortEpg(
            streamId: Int,
            limit: Int,
    ): List<XtreamEpgProgramme> =
            withContext(io) {
                val url =
                        buildPlayerApiUrl(
                                "get_short_epg",
                                mapOf(
                                        "stream_id" to streamId.toString(),
                                        "limit" to limit.toString(),
                                ),
                        )
                val body = fetchRaw(url, isEpg = true) ?: return@withContext emptyList()

                // EPG response varies: can be {epg_listings: [...]} or direct [...]
                val root =
                        runCatching { json.parseToJsonElement(body) }.getOrNull()
                                ?: return@withContext emptyList()

                val listings =
                        when {
                            root is JsonArray -> root
                            root is JsonObject && root.containsKey("epg_listings") ->
                                    root["epg_listings"]?.jsonArray
                            else -> null
                        }
                                ?: return@withContext emptyList()

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
                // get_simple_data_table or fallback to get_short_epg with high limit
                if (_capabilities?.extras?.supportsSimpleDataTable == true) {
                    val url =
                            buildPlayerApiUrl(
                                    "get_simple_data_table",
                                    mapOf("stream_id" to streamId.toString()),
                            )
                    val body = fetchRaw(url, isEpg = true)
                    if (!body.isNullOrEmpty()) {
                        // Parse similar to short EPG
                        return@withContext getShortEpg(streamId, 200)
                    }
                }
                getShortEpg(streamId, 200)
            }

    override suspend fun prefetchEpg(
            streamIds: List<Int>,
            perStreamLimit: Int,
    ) =
            withContext(io) {
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
        val cfg = config ?: return ""
        val ext =
                normalizeExtension(
                        extension ?: cfg.liveExtPrefs.firstOrNull() ?: "m3u8",
                        isLive = true,
                )
        return buildPlayUrl("live", streamId, ext)
    }

    override fun buildVodUrl(
            vodId: Int,
            containerExtension: String?,
    ): String {
        val cfg = config ?: return ""
        // Ultimate fallback is m3u8 (HLS) to align with streaming-first policy.
        // This is only used when cfg.vodExtPrefs is empty; normally vodExtPrefs takes precedence.
        val ext = sanitizeExtension(containerExtension ?: cfg.vodExtPrefs.firstOrNull() ?: "m3u8")
        val playbackKind = resolveVodPlaybackKind(vodKind)
        return buildPlayUrl(playbackKind, vodId, ext)
    }

    override fun buildSeriesEpisodeUrl(
            seriesId: Int,
            seasonNumber: Int,
            episodeNumber: Int,
            episodeId: Int?,
            containerExtension: String?,
    ): String {
        val cfg = config ?: return ""
        // Ultimate fallback is m3u8 (HLS) to align with streaming-first policy.
        // This is only used when cfg.seriesExtPrefs is empty; normally seriesExtPrefs takes
        // precedence.
        val ext =
                sanitizeExtension(containerExtension ?: cfg.seriesExtPrefs.firstOrNull() ?: "m3u8")

        // Prefer episodeId if available (direct path)
        if (episodeId != null && episodeId > 0) {
            return buildPlayUrl("series", episodeId, ext)
        }

        // Fall back to seriesId/season/episode path
        return buildString {
            append(buildBaseUrl())
            append("/series/")
            append(urlEncode(cfg.username))
            append("/")
            append(urlEncode(cfg.password))
            append("/")
            append(seriesId)
            append("/")
            append(seasonNumber)
            append("/")
            append(episodeNumber)
            append(".")
            append(ext)
        }
    }

    /**
     * Resolve the **playback** path segment for VOD.
     *
     * Reality check: Many Xtream panels expose VOD lists via
     * `get_vod_streams`/`get_movies_streams`, but playback is often served under `/movie/...`
     * (singular), not `/vod/...`.
     *
     * We keep `vodKind` for API compatibility, but normalize the playback segment.
     */
    private fun resolveVodPlaybackKind(vodKindAlias: String): String =
            when (vodKindAlias.lowercase()) {
                "movie", "movies" -> "movie"
                "vod" -> "movie" // Common in the wild; fixes 404s on providers using /movie/
                else -> "movie"
            }

    private fun buildPlayUrl(
            kind: String,
            id: Int,
            ext: String,
    ): String {
        val cfg = config ?: return ""
        return buildString {
            append(buildBaseUrl())
            append("/")
            append(kind)
            append("/")
            append(urlEncode(cfg.username))
            append("/")
            append(urlEncode(cfg.password))
            append("/")
            append(id)
            append(".")
            append(ext)
        }
    }

    // =========================================================================
    // Search
    // =========================================================================

    override suspend fun search(
            query: String,
            types: Set<XtreamContentType>,
            limit: Int,
    ): XtreamSearchResults = coroutineScope {
        val queryLower = query.lowercase()

        val liveDeferred =
                if (XtreamContentType.LIVE in types) {
                    async {
                        getLiveStreams()
                                .filter { it.name?.lowercase()?.contains(queryLower) == true }
                                .take(limit)
                    }
                } else {
                    null
                }

        val vodDeferred =
                if (XtreamContentType.VOD in types) {
                    async {
                        getVodStreams()
                                .filter { it.name?.lowercase()?.contains(queryLower) == true }
                                .take(limit)
                    }
                } else {
                    null
                }

        val seriesDeferred =
                if (XtreamContentType.SERIES in types) {
                    async {
                        getSeries()
                                .filter { it.name?.lowercase()?.contains(queryLower) == true }
                                .take(limit)
                    }
                } else {
                    null
                }

        XtreamSearchResults(
                live = liveDeferred?.await() ?: emptyList(),
                vod = vodDeferred?.await() ?: emptyList(),
                series = seriesDeferred?.await() ?: emptyList(),
        )
    }

    // =========================================================================
    // Catchup
    // =========================================================================

    override fun buildCatchupUrl(
            streamId: Int,
            start: Long,
            duration: Int,
    ): String? {
        val cfg = config ?: return null
        // Standard catchup format:
        // /streaming/timeshift.php?username=X&password=Y&stream=Z&start=YYYY-MM-DD:HH-MM&duration=MINS
        // Or: /live/{user}/{pass}/{streamId}.ts?wmsAuthSign=... for some panels
        // This is panel-dependent; return basic format
        return buildString {
            append(buildBaseUrl())
            append("/streaming/timeshift.php")
            append("?username=")
            append(urlEncode(cfg.username))
            append("&password=")
            append(urlEncode(cfg.password))
            append("&stream=")
            append(streamId)
            append("&start=")
            append(start)
            append("&duration=")
            append(duration)
        }
    }

    // =========================================================================
    // Raw API Access
    // =========================================================================

    override suspend fun rawApiCall(
            action: String,
            params: Map<String, String>,
    ): String? =
            withContext(io) {
                val url = buildPlayerApiUrl(action, params)
                fetchRaw(url, isEpg = action.contains("epg", ignoreCase = true))
            }

    // =========================================================================
    // Internal: URL Building
    // =========================================================================

    private fun buildBaseUrl(): String {
        val cfg = config ?: return ""
        return buildString {
            append(cfg.scheme.lowercase())
            append("://")
            append(cfg.host)
            append(":")
            append(resolvedPort)
            cfg.basePath?.let { bp ->
                val normalized =
                        bp.trim().let { if (!it.startsWith("/")) "/$it" else it }.removeSuffix("/")
                if (normalized.isNotEmpty() && normalized != "/") {
                    append(normalized)
                }
            }
        }
    }

    private fun buildPlayerApiUrl(
            action: String?,
            params: Map<String, String> = emptyMap(),
    ): String {
        val cfg = config ?: return ""

        val builder =
                HttpUrl.Builder().scheme(cfg.scheme.lowercase()).host(cfg.host).port(resolvedPort)

        // Add base path segments
        cfg.basePath?.trim()?.let { bp ->
            val normalized = (if (bp.startsWith("/")) bp else "/$bp").removeSuffix("/")
            if (normalized.isNotEmpty() && normalized != "/") {
                normalized.removePrefix("/").split('/').filter { it.isNotBlank() }.forEach { seg ->
                    builder.addPathSegment(seg)
                }
            }
        }

        builder.addPathSegment("player_api.php")

        // Add action first (if present)
        if (!action.isNullOrBlank()) {
            builder.addQueryParameter("action", action)
        }

        // Add extra params
        params.forEach { (key, value) -> builder.addQueryParameter(key, value) }

        // Add credentials last
        builder.addQueryParameter("username", cfg.username)
        builder.addQueryParameter("password", cfg.password)

        val url = builder.build().toString()
        UnifiedLog.d(TAG) { "buildPlayerApiUrl: action=$action -> ${redactUrl(url)}" }

        return url
    }

    private fun buildCacheKey(
            config: XtreamApiConfig,
            port: Int,
    ): String {
        val base = "${config.scheme.lowercase()}://${config.host}:$port"
        val path =
                config.basePath?.let { bp ->
                    val n =
                            bp.trim()
                                    .let { if (it.startsWith("/")) it else "/$it" }
                                    .removeSuffix("/")
                    if (n.isNotEmpty() && n != "/") n else ""
                }
                        ?: ""
        return "$base$path|${config.username}"
    }

    // =========================================================================
    // Internal: HTTP & Caching
    // =========================================================================

    private suspend fun fetchRaw(
            url: String,
            isEpg: Boolean,
    ): String? {
        // Check cache first (no logging for cache hits to reduce noise)
        val cached = readCache(url, isEpg)
        if (cached != null) {
            return cached
        }

        // Rate limit
        takeRateSlot(config?.host ?: "")

        // Execute request with enhanced headers for provider compatibility
        val request =
                Request.Builder()
                        .url(url)
                        .header("Accept", "application/json")
                        .header("Accept-Encoding", "gzip")
                        .header("User-Agent", "FishIT-Player/2.x (Android)")
                        .get()
                        .build()

        val safeUrl = redactUrl(url)
        return try {
            http.newCall(request).execute().use { response ->
                // Network Probe: Log endpoint + status (never credentials)
                // Note: OkHttp's response.header() is case-insensitive per HTTP spec (RFC 7230)
                // Works correctly with "Content-Type", "content-type", "CONTENT-TYPE", etc.
                val contentType = response.header("Content-Type") ?: "unknown"
                val contentLength = response.header("Content-Length") ?: "unknown"

                if (!response.isSuccessful) {
                    // Enhanced diagnostic logging for connection failures
                    UnifiedLog.i(TAG) {
                        "NetworkProbe: HTTP ${response.code} for $safeUrl | contentType=$contentType"
                    }
                    return null
                }

                // Get body as bytes first to allow gzip detection before string conversion
                val bodyBytes = response.body.bytes()

                // Network Probe: Log success with response metadata (no body content)
                UnifiedLog.d(TAG) {
                    "NetworkProbe: HTTP ${response.code} for $safeUrl | bytes=${bodyBytes.size} contentType=$contentType"
                }

                if (bodyBytes.isEmpty()) {
                    UnifiedLog.i(TAG) {
                        "NetworkProbe: Empty body for $safeUrl (contentLength=$contentLength)"
                    }
                    return null
                }

                // Defensive gzip handling: Check if body is gzip-compressed but not decompressed
                // Some servers may send gzip without proper Content-Encoding header
                // Check for gzip magic bytes: 0x1F 0x8B (first two bytes of gzip format per RFC
                // 1952)
                val body =
                        if (bodyBytes.size >= 2 &&
                                        (bodyBytes[0].toInt() and 0xFF) == 0x1F &&
                                        (bodyBytes[1].toInt() and 0xFF) == 0x8B
                        ) {
                            try {
                                val decompressed =
                                        GZIPInputStream(bodyBytes.inputStream())
                                                .bufferedReader()
                                                .readText()
                                UnifiedLog.d(TAG) {
                                    "NetworkProbe: Manually decompressed gzip body for $safeUrl | original=${bodyBytes.size} decompressed=${decompressed.length}"
                                }
                                decompressed
                            } catch (e: Exception) {
                                UnifiedLog.w(TAG) {
                                    "NetworkProbe: Failed to decompress suspected gzip body for $safeUrl - ${e.message}"
                                }
                                // Continue with original body if decompression fails
                                String(bodyBytes, Charsets.UTF_8)
                            }
                        } else {
                            String(bodyBytes, Charsets.UTF_8)
                        }

                // STRICT JSON GATE: Only return body if it's actually JSON
                // This prevents JSON parsing exceptions when server returns M3U/HTML/text
                val trimmed = body.trimStart()
                val isJsonBody = trimmed.startsWith("{") || trimmed.startsWith("[")
                val isJsonContentType = contentType.contains("application/json", ignoreCase = true)

                if (!isJsonBody) {
                    // Detect M3U playlist (common mistake: using get.php URL)
                    val isM3U =
                            trimmed.startsWith("#EXTM3U") ||
                                    trimmed.startsWith("#EXTINF") ||
                                    contentType.contains("mpegurl", ignoreCase = true) ||
                                    contentType.contains("x-mpegurl", ignoreCase = true)

                    // Extract endpoint name for logging (e.g., "player_api.php" or "get.php")
                    val pathPart = url.substringAfterLast('/', "unknown")
                    val endpointName = pathPart.substringBefore('?', pathPart)

                    if (isM3U) {
                        UnifiedLog.w(TAG) {
                            "XtreamConnect: ignored non-JSON response (endpoint=$endpointName, content-type=$contentType, reason=m3u_playlist_detected)"
                        }
                    } else {
                        // Log first 50 chars of preview if it's not JSON (likely error page) - no
                        // sensitive data
                        val preview = trimmed.take(50).replace(Regex("[\\r\\n]+"), " ")
                        val suffix = if (trimmed.length > 50) "..." else ""
                        UnifiedLog.w(TAG) {
                            "XtreamConnect: ignored non-JSON response (endpoint=$endpointName, content-type=$contentType, reason=non_json_content, preview=$preview$suffix)"
                        }
                    }
                    // Return null - callers must handle missing response
                    return null
                }

                writeCache(url, body)
                body
            }
        } catch (e: java.net.UnknownHostException) {
            UnifiedLog.i(TAG) { "NetworkProbe: DNS resolution failed for $safeUrl" }
            null
        } catch (e: java.net.ConnectException) {
            UnifiedLog.i(TAG) { "NetworkProbe: Connection refused for $safeUrl - ${e.message}" }
            null
        } catch (e: javax.net.ssl.SSLException) {
            UnifiedLog.i(TAG) { "NetworkProbe: SSL/TLS error for $safeUrl - ${e.message}" }
            null
        } catch (e: java.net.SocketTimeoutException) {
            UnifiedLog.i(TAG) { "NetworkProbe: Timeout for $safeUrl" }
            null
        } catch (e: java.io.IOException) {
            // Check for cleartext traffic blocked
            val message = e.message ?: ""
            if (message.contains("CLEARTEXT") || message.contains("cleartext")) {
                UnifiedLog.e(TAG) {
                    "NetworkProbe: Cleartext HTTP blocked! Enable usesCleartextTraffic in AndroidManifest for $safeUrl"
                }
            } else {
                UnifiedLog.i(TAG) {
                    "NetworkProbe: IO error for $safeUrl - ${e.javaClass.simpleName}: $message"
                }
            }
            null
        } catch (e: Exception) {
            UnifiedLog.i(TAG) { "NetworkProbe: Failed $safeUrl - ${e.javaClass.simpleName}" }
            null
        }
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

    private suspend fun readCache(
            url: String,
            isEpg: Boolean,
    ): String? {
        val ttl =
                if (isEpg) {
                    XtreamTransportConfig.EPG_CACHE_TTL_MS
                } else {
                    XtreamTransportConfig.CACHE_TTL_MS
                }
        return cacheLock.withLock {
            val entry = cache[url] ?: return@withLock null
            if ((SystemClock.elapsedRealtime() - entry.at) <= ttl) entry.body else null
        }
    }

    private suspend fun writeCache(
            url: String,
            body: String,
    ) {
        cacheLock.withLock { cache[url] = CacheEntry(SystemClock.elapsedRealtime(), body) }
    }

    // =========================================================================
    // Internal: JSON Parsing
    // =========================================================================

    /**
     * Parse JSON response into a list of objects.
     *
     * **Smart Response Handling:** Some Xtream panels return different JSON shapes for the same
     * endpoint:
     * - Standard: `[{item1}, {item2}, ...]` (direct array)
     * - Alternative: `{"series": [{item1}, ...]}` or `{"vod": [...]}` (object with array field)
     *
     * This parser handles both cases by:
     * 1. Trying to parse as direct array first
     * 2. If it's an object, looking for common array field names
     * 3. Logging the response shape for debugging
     *
     * @param body The raw JSON response body
     * @param mapper Function to map each JSON object to the target type
     * @param actionHint Optional action name for debug logging (e.g., "get_series")
     * @return List of parsed items, empty if parsing fails
     */
    private fun <T> parseJsonArray(
            body: String,
            mapper: (JsonObject) -> T,
            actionHint: String? = null,
    ): List<T> {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull()
        if (root == null) {
            UnifiedLog.w(TAG) { "parseJsonArray($actionHint): Failed to parse JSON" }
            return emptyList()
        }

        // Case 1: Direct array response (most common)
        if (root is JsonArray) {
            var successCount = 0
            var failCount = 0
            val results =
                    root.mapNotNull { el ->
                        val obj = el.jsonObjectOrNull() ?: return@mapNotNull null
                        runCatching { mapper(obj) }
                                .onFailure { e ->
                                    failCount++
                                    // Log first few failures to help debug parsing issues
                                    if (failCount <= 3) {
                                        val preview = obj.toString().take(200)
                                        UnifiedLog.w(TAG) {
                                            "parseJsonArray($actionHint): Mapper failed on item $failCount: ${e.message}, preview: $preview"
                                        }
                                    }
                                }
                                .onSuccess { successCount++ }
                                .getOrNull()
                    }
            if (failCount > 0) {
                UnifiedLog.w(TAG) {
                    "parseJsonArray($actionHint): $failCount/${ root.size} items failed mapping, $successCount succeeded"
                }
            }
            return results
        }

        // Case 2: Object response with array field
        // Some panels wrap arrays in objects like {"series": [...]} or {"vod_streams": [...]}
        if (root is JsonObject) {
            // Log the response shape for debugging (first 300 chars, redacted)
            val preview =
                    body.take(300)
                            .replace(Regex("""(username|password|api_key)[^,}\]]*"""), "$1=***")
            UnifiedLog.d(TAG) {
                "parseJsonArray($actionHint): Object response, shape preview: $preview${if (body.length > 300) "..." else ""}"
            }

            // Common array field names used by different panels
            val arrayFieldCandidates =
                    listOf(
                            // Series-specific
                            "series",
                            "series_list",
                            "series_streams",
                            // VOD-specific
                            "vod",
                            "vod_streams",
                            "movies",
                            "movie_streams",
                            // Live-specific
                            "live",
                            "live_streams",
                            "channels",
                            // Generic
                            "streams",
                            "data",
                            "items",
                            "list",
                            "result",
                            "results",
                    )

            for (fieldName in arrayFieldCandidates) {
                val arrayField = root[fieldName]
                if (arrayField is JsonArray && arrayField.isNotEmpty()) {
                    UnifiedLog.i(TAG) {
                        "parseJsonArray($actionHint): Found ${arrayField.size} items in '$fieldName' field"
                    }
                    return arrayField.mapNotNull { el ->
                        val obj = el.jsonObjectOrNull() ?: return@mapNotNull null
                        runCatching { mapper(obj) }.getOrNull()
                    }
                }
            }

            // Log available keys if no array field found
            val keys = root.keys.take(10).joinToString(", ")
            UnifiedLog.w(TAG) {
                "parseJsonArray($actionHint): Object has no recognized array field. Keys: [$keys]"
            }
        }

        UnifiedLog.w(TAG) {
            "parseJsonArray($actionHint): Unexpected JSON type: ${root::class.simpleName}"
        }
        return emptyList()
    }

    private fun JsonElement.jsonObjectOrNull(): JsonObject? = runCatching { jsonObject }.getOrNull()

    /**
     * Safe string extraction that handles edge cases gracefully.
     *
     * The Xtream API is inconsistent - some fields like `backdrop_path` can be either a string OR
     * an array depending on the panel. This method:
     * - Returns the string value if it's a primitive
     * - Returns the first element if it's an array of strings
     * - Returns null for objects, null values, or empty arrays
     *
     * This prevents parsing failures that would skip entire items.
     */
    private fun JsonObject.stringOrNull(key: String): String? {
        val element = this[key] ?: return null
        return when {
            // Direct primitive string
            element is JsonPrimitive -> element.contentOrNull
            // Array of strings - take first element (common for backdrop_path)
            element is JsonArray && element.isNotEmpty() -> {
                element.firstOrNull()?.jsonPrimitive?.contentOrNull
            }
            else -> null
        }
    }

    /** Safe int extraction - only works with primitive values. */
    private fun JsonObject.intOrNull(key: String): Int? {
        val element = this[key] ?: return null
        return if (element is JsonPrimitive) element.intOrNull else null
    }

    /** Safe long extraction - only works with primitive values. */
    private fun JsonObject.longOrNull(key: String): Long? {
        val element = this[key] ?: return null
        return if (element is JsonPrimitive) element.longOrNull else null
    }

    /** Safe double extraction - only works with primitive values. */
    private fun JsonObject.doubleOrNull(key: String): Double? {
        val element = this[key] ?: return null
        return if (element is JsonPrimitive) element.doubleOrNull else null
    }

    // =========================================================================
    // Internal: Discovery
    // =========================================================================

    private suspend fun resolvePort(config: XtreamApiConfig): Int = coroutineScope {
        // Check port store cache
        val key = PortKey(config.scheme, config.host, config.username)
        portStore?.getResolvedPort(key)?.let { cached ->
            if (tryPing(config, cached)) return@coroutineScope cached
            portStore.clear(key)
        }

        // Try standard port first
        val stdPort = if (config.scheme.equals("https", ignoreCase = true)) 443 else 80
        if (tryPing(config, stdPort)) {
            portStore?.putResolvedPort(key, stdPort)
            return@coroutineScope stdPort
        }

        // Try common alternative ports in parallel
        val candidates =
                if (config.scheme.equals("https", ignoreCase = true)) {
                    listOf(443, 8443, 2053, 2083, 2087, 2096)
                } else {
                    listOf(80, 8080, 8000, 8880, 2052, 2082, 2086)
                }

        // Premium Contract Section 5: Use device-class parallelism from DI (SSOT)
        val sem = Semaphore(parallelism.value)
        val jobs =
                candidates.distinct().map { port ->
                    async { sem.withPermit { if (tryPing(config, port)) port else null } }
                }

        val winner = jobs.awaitAll().firstOrNull { it != null }
        if (winner != null) {
            portStore?.putResolvedPort(key, winner)
            return@coroutineScope winner
        }

        // Fallback to standard port
        stdPort
    }

    private fun tryPing(
            config: XtreamApiConfig,
            port: Int,
    ): Boolean {
        val actions = listOf("get_live_streams", "get_series", "get_vod_streams")

        for (action in actions) {
            val url =
                    HttpUrl.Builder()
                            .scheme(config.scheme.lowercase())
                            .host(config.host)
                            .port(port)
                            .apply {
                                config.basePath?.trim()?.let { bp ->
                                    val norm =
                                            (if (bp.startsWith("/")) bp else "/$bp").removeSuffix(
                                                    "/",
                                            )
                                    if (norm.isNotEmpty() && norm != "/") {
                                        norm.removePrefix("/")
                                                .split('/')
                                                .filter { it.isNotBlank() }
                                                .forEach { addPathSegment(it) }
                                    }
                                }
                            }
                            .addPathSegment("player_api.php")
                            .addQueryParameter("action", action)
                            .addQueryParameter("category_id", "0")
                            .addQueryParameter("username", config.username)
                            .addQueryParameter("password", config.password)
                            .build()
                            .toString()

            val request =
                    Request.Builder()
                            .url(url)
                            .header("Accept", "application/json")
                            .header("Accept-Encoding", "gzip")
                            .get()
                            .build()

            try {
                http.newCall(request).execute().use { response ->
                    if (response.code in 200..299) {
                        val rawBody = response.body.string()
                        // Trim whitespace and BOM (U+FEFF) before checking JSON start
                        val body = rawBody.trim { it.isWhitespace() || it == '\uFEFF' }
                        if (body.isNotEmpty() && (body.startsWith("{") || body.startsWith("["))) {
                            runCatching { json.parseToJsonElement(body) }.getOrNull()?.let {
                                return true
                            }
                        }
                    }
                }
            } catch (_: Throwable) {
                // Try next action
            }
        }
        return false
    }

    private suspend fun discoverCapabilities(
            config: XtreamApiConfig,
            port: Int,
            cacheKey: String,
    ): XtreamCapabilities = coroutineScope {
        val actions = mutableMapOf<String, XtreamActionCapability>()
        // Premium Contract Section 5: Use device-class parallelism from DI (SSOT)
        val sem = Semaphore(parallelism.value)

        suspend fun probe(
                action: String,
                extra: Map<String, String> = emptyMap(),
        ): JsonElement? {
            return sem.withPermit {
                val url = buildPlayerApiUrl(action, extra)
                val body = fetchRaw(url, isEpg = action.contains("epg"))
                if (body.isNullOrEmpty()) {
                    actions[action] = XtreamActionCapability(supported = false)
                    return@withPermit null
                }
                val el = runCatching { json.parseToJsonElement(body) }.getOrNull()
                if (el == null) {
                    actions[action] = XtreamActionCapability(supported = false)
                    return@withPermit null
                }
                val (type, keys) = fingerprint(el)
                actions[action] =
                        XtreamActionCapability(
                                supported = true,
                                responseType = type,
                                sampleKeys = keys,
                        )
                el
            }
        }

        // Probe basic actions
        val basicActions =
                listOf(
                        "get_live_categories",
                        "get_live_streams",
                        "get_series_categories",
                        "get_series",
                )
        val basicJobs = basicActions.map { async { probe(it) } }

        // Probe VOD aliases
        val vodResults =
                VOD_ALIAS_CANDIDATES
                        .map { alias ->
                            async { alias to (probe("get_${alias}_categories") != null) }
                        }
                        .awaitAll()
        val vodCandidates = vodResults.filter { it.second }.map { it.first }
        val resolvedVodKind = vodCandidates.firstOrNull()

        // Probe extras
        val extraJobs =
                listOf(async { probe("get_short_epg", mapOf("stream_id" to "1", "limit" to "1")) })

        (basicJobs + extraJobs).awaitAll()

        val baseUrl = buildString {
            append(config.scheme.lowercase())
            append("://")
            append(config.host)
            append(":")
            append(port)
            config.basePath?.let { bp ->
                val norm =
                        bp.trim().let { if (it.startsWith("/")) it else "/$it" }.removeSuffix("/")
                if (norm.isNotEmpty() && norm != "/") append(norm)
            }
        }

        XtreamCapabilities(
                version = 2,
                cacheKey = cacheKey,
                baseUrl = baseUrl,
                username = config.username,
                resolvedAliases =
                        XtreamResolvedAliases(
                                vodKind = resolvedVodKind,
                                vodCandidates = vodCandidates,
                        ),
                actions = actions,
                extras =
                        XtreamExtrasCapability(
                                supportsShortEpg = actions["get_short_epg"]?.supported == true,
                                supportsVodInfo = true, // Assume supported
                                supportsSeriesInfo = true, // Assume supported
                        ),
                cachedAt = System.currentTimeMillis(),
        )
    }

    private fun fingerprint(el: JsonElement): Pair<String, List<String>> =
            when {
                el is JsonObject -> "object" to el.keys.toList()
                el is JsonArray -> {
                    if (el.isNotEmpty() && el.first() is JsonObject) {
                        "array" to (el.first() as JsonObject).keys.toList()
                    } else {
                        "array" to emptyList()
                    }
                }
                else -> "unknown" to emptyList()
            }

    // =========================================================================
    // Internal: Utilities
    // =========================================================================

    private fun <T> sliceList(
            list: List<T>,
            offset: Int,
            limit: Int,
    ): List<T> {
        val from = offset.coerceAtLeast(0)
        val to = (offset + limit).coerceAtMost(list.size)
        return if (from < to) list.subList(from, to) else emptyList()
    }

    private fun normalizeExtension(
            ext: String,
            isLive: Boolean,
    ): String {
        val lower = ext.lowercase().trim()
        return when {
            isLive && lower == "hls" -> "m3u8"
            isLive && lower in listOf("m3u8", "ts") -> lower
            !isLive -> sanitizeExtension(lower)
            else -> "m3u8"
        }
    }

    /**
     * Sanitize extension for playback URL building.
     *
     * CRITICAL: Only accepts valid STREAMING OUTPUT formats for Xtream:
     * - m3u8 (HLS): Best for adaptive streaming, seeks, compatibility
     * - ts (MPEG-TS): Good fallback, works on most players
     *
     * Container formats like mp4, mkv, avi, mov are REJECTED because:
     * 1. They describe the file on the server, NOT the streaming output format
     * 2. Xtream servers transcode to HLS/TS for actual streaming
     * 3. Using container extensions causes UnrecognizedInputFormatException
     *
     * If the server explicitly allows mp4 in allowed_output_formats, the caller
     * (XtreamPlaybackSourceFactoryImpl.resolveOutputExtension) handles that case
     * BEFORE calling into buildVodUrl/buildSeriesEpisodeUrl.
     *
     * @param ext The extension to sanitize
     * @return A valid streaming output format (defaults to m3u8)
     */
    private fun sanitizeExtension(ext: String?): String {
        val lower = ext?.lowercase()?.trim().orEmpty()
        // HARDENED: Only accept TRUE streaming output formats
        // mp4/mkv/avi/mov are container formats - NOT streaming outputs!
        val validStreamingFormats = setOf("m3u8", "ts")
        return if (lower in validStreamingFormats) lower else "m3u8"
    }

    private fun urlEncode(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")

    private fun mapException(e: Exception): XtreamError =
            when (e) {
                is java.net.UnknownHostException -> XtreamError.Network("DNS resolution failed", e)
                is java.net.SocketTimeoutException -> XtreamError.Network("Connection timeout", e)
                is java.io.IOException -> XtreamError.Network(e.message ?: "Network error", e)
                else -> XtreamError.Unknown(e.message ?: "Unknown error")
            }
}

// =============================================================================
// Port Store Interface
// =============================================================================

data class PortKey(
        val scheme: String,
        val host: String,
        val username: String,
)

interface XtreamPortStore {
    fun getResolvedPort(key: PortKey): Int?

    fun putResolvedPort(
            key: PortKey,
            port: Int,
    )

    fun clear(key: PortKey)
}

interface XtreamCapabilityStore {
    fun get(cacheKey: String): XtreamCapabilities?

    fun put(caps: XtreamCapabilities)
}

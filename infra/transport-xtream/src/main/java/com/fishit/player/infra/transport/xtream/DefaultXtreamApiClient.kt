package com.fishit.player.infra.transport.xtream

import android.os.SystemClock
import com.fishit.player.infra.logging.UnifiedLog
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
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl
import okhttp3.MediaType
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
 */
class DefaultXtreamApiClient(
    private val http: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
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

    // Rate limiting (shared across all instances for same host)
    private companion object {
        private const val TAG = "XtreamApiClient"
        private val rateMutex = Mutex()
        private val lastCallByHost = mutableMapOf<String, Long>()
        private const val MIN_INTERVAL_MS = 120L

        // Response cache
        private val cacheLock = Mutex()
        private val cache =
            object : LinkedHashMap<String, CacheEntry>(512, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheEntry>?): Boolean = size > 512
            }
        private const val CACHE_TTL_MS = 60_000L
        private const val EPG_CACHE_TTL_MS = 15_000L

        // VOD alias candidates in preference order
        private val VOD_ALIAS_CANDIDATES = listOf("vod", "movie", "movies")

        // ID field candidates in preference order
        private val VOD_ID_FIELDS = listOf("vod_id", "movie_id", "id", "stream_id")
        private val LIVE_ID_FIELDS = listOf("stream_id", "id")
        private val SERIES_ID_FIELDS = listOf("series_id", "id")
    }

    private data class CacheEntry(
        val at: Long,
        val body: String,
    )

    // EPG parallel limit
    private val epgSemaphore = Semaphore(4)

    // =========================================================================
    // Lifecycle
    // =========================================================================

    override suspend fun initialize(
        config: XtreamApiConfig,
        forceDiscovery: Boolean,
    ): Result<XtreamCapabilities> =
        withContext(io) {
            UnifiedLog.d(
                TAG,
                "Initializing client for ${config.host} (forceDiscovery=$forceDiscovery)",
            )
            this@DefaultXtreamApiClient.config = config
            _connectionState.value = XtreamConnectionState.Connecting
            _authState.value = XtreamAuthState.Pending

            try {
                // 1. Resolve port if not specified
                resolvedPort = config.port ?: resolvePort(config)
                UnifiedLog.d(TAG, "Resolved port: $resolvedPort")

                // 1b. Script parity auth check (player_api.php without action)
                runScriptParityAuthCheck()

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
                UnifiedLog.e(TAG, "Initialize failed for ${config.host}", e)
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
                    UnifiedLog.d(TAG, "validateAndComplete: No user info in response, assuming OK")
                    _connectionState.value =
                        XtreamConnectionState.Connected(caps.baseUrl, latency)
                    Result.success(caps)
                }
            },
            onFailure = { error ->
                UnifiedLog.w(TAG, "validateAndComplete: getServerInfo failed, trying fallback validation", error)
                // Fallback: Try a simple action-based endpoint to validate connectivity
                val fallbackResult = tryFallbackValidation()
                if (fallbackResult) {
                    UnifiedLog.d(TAG, "validateAndComplete: Fallback validation succeeded")
                    _connectionState.value = XtreamConnectionState.Connected(caps.baseUrl, latency)
                    _authState.value = XtreamAuthState.Unknown
                    Result.success(caps)
                } else {
                    UnifiedLog.e(TAG, "validateAndComplete: Fallback validation failed")
                    _authState.value = XtreamAuthState.Failed(XtreamError.InvalidCredentials)
                    Result.failure(error)
                }
            },
        )
    }

    /**
     * Fallback validation when getServerInfo() fails.
     * Some servers don't support player_api.php without action parameter.
     * Try a simple action-based endpoint instead.
     */
    private suspend fun tryFallbackValidation(): Boolean =
        withContext(io) {
            try {
                UnifiedLog.d(TAG, "tryFallbackValidation: Trying get_live_categories")
                val url = buildPlayerApiUrl("get_live_categories")
                val body = fetchRaw(url, isEpg = false)
                
                if (body != null && body.isNotEmpty()) {
                    // Try to parse as JSON to verify it's a valid response
                    val parsed = runCatching { json.parseToJsonElement(body) }.getOrNull()
                    if (parsed != null) {
                        UnifiedLog.d(TAG, "tryFallbackValidation: Success - received valid JSON response")
                        true
                    } else {
                        UnifiedLog.w(TAG, "tryFallbackValidation: Response is not valid JSON")
                        false
                    }
                } else {
                    UnifiedLog.w(TAG, "tryFallbackValidation: Empty or null response")
                    false
                }
            } catch (e: Exception) {
                UnifiedLog.e(TAG, "tryFallbackValidation: Exception", e)
                false
            }
        }

    private suspend fun runScriptParityAuthCheck() =
        withContext(io) {
            val preflightUrl = buildPlayerApiUrl(action = null)
            UnifiedLog.d(TAG) { "runScriptParityAuthCheck: preflight ${preflightUrl.replace(Regex("(password|username)=[^&]*"), "$1=***")}" }
            fetchRaw(preflightUrl, isEpg = false)

            val serverInfoUrl = buildPlayerApiUrl(action = "get_server_info")
            runCatching { fetchRaw(serverInfoUrl, isEpg = false) }
                .recoverCatching { error ->
                    UnifiedLog.w(TAG, "runScriptParityAuthCheck: get_server_info failed, fallback to get_live_categories", error)
                    val fallbackUrl = buildPlayerApiUrl(action = "get_live_categories")
                    fetchRaw(fallbackUrl, isEpg = false)
                }
                .getOrThrow()
        }


    override suspend fun ping(): Boolean =
        withContext(io) {
            if (config == null) return@withContext false
            try {
                val url = buildPlayerApiUrl("get_live_categories")
                val body = fetchRaw(url, isEpg = false)
                body != null && (body.startsWith("[") || body.startsWith("{"))
            } catch (_: Exception) {
                false
            }
        }

    override fun close() {
        _connectionState.value = XtreamConnectionState.Disconnected
        _authState.value = XtreamAuthState.Unknown
        _capabilities = null
        config = null
    }

    // =========================================================================
    // Server & User Info
    // =========================================================================

    override suspend fun getServerInfo(): Result<XtreamServerInfo> =
        withContext(io) {
            try {
                val url = buildPlayerApiUrl(action = "get_server_info")
                UnifiedLog.d(TAG, "getServerInfo: Fetching from URL: ${url.replace(Regex("(password|username)=([^&]*)"), "$1=***")}")

                val body = fetchRaw(url, isEpg = false)
                    ?: fetchRaw(buildPlayerApiUrl(action = null), isEpg = false)
                    ?: run {
                        UnifiedLog.e(TAG, "getServerInfo: Empty response from server")
                        return@withContext Result.failure(
                            Exception("Empty response from server. Check URL, credentials, and network connection."),
                        )
                    }

                UnifiedLog.d(TAG, "getServerInfo: Received ${body.length} bytes, parsing...")
                val parsed = json.decodeFromString<XtreamServerInfo>(body)
                UnifiedLog.d(
                    TAG,
                    "Server info retrieved: ${parsed.serverInfo?.url ?: "unknown"}",
                )
                Result.success(parsed)
            } catch (e: Exception) {
                UnifiedLog.e(TAG, "getServerInfo failed", e)
                Result.failure(e)
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

    override suspend fun getLiveCategories(): List<XtreamCategory> = fetchCategories("get_live_categories")

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

    override suspend fun getSeriesCategories(): List<XtreamCategory> = fetchCategories("get_series_categories")

    private suspend fun fetchCategories(action: String): List<XtreamCategory> =
        withContext(io) {
            val url = buildPlayerApiUrl(action)
            val body = fetchRaw(url, isEpg = false) ?: return@withContext emptyList()
            parseJsonArray(body) { obj ->
                XtreamCategory(
                    categoryId = obj.stringOrNull("category_id"),
                    categoryName = obj.stringOrNull("category_name"),
                    parentId = obj.intOrNull("parent_id"),
                )
            }
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
                    fetchStreamsWithCategoryFallback("get_${alias}_streams", categoryId) { obj ->
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
     * Fetch streams with intelligent category_id fallback. Order: category_id=* → category_id=0 →
     * no category_id
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
            return parseJsonArray(body, mapper)
        }

        // No category = try fallback sequence
        // 1. Try category_id=*
        val starUrl = buildPlayerApiUrl(action, mapOf("category_id" to "*"))
        val starBody = runCatching { fetchRaw(starUrl, isEpg = false) }.getOrNull()
        if (!starBody.isNullOrEmpty()) {
            val result = parseJsonArray(starBody, mapper)
            if (result.isNotEmpty()) return result
        }

        // 2. Try category_id=0
        val zeroUrl = buildPlayerApiUrl(action, mapOf("category_id" to "0"))
        val zeroBody = runCatching { fetchRaw(zeroUrl, isEpg = false) }.getOrNull()
        if (!zeroBody.isNullOrEmpty()) {
            val result = parseJsonArray(zeroBody, mapper)
            if (result.isNotEmpty()) return result
        }

        // 3. Try without category_id
        val plainUrl = buildPlayerApiUrl(action)
        val plainBody = fetchRaw(plainUrl, isEpg = false) ?: return emptyList()
        return parseJsonArray(plainBody, mapper)
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
                    if (!body.isNullOrEmpty() && body.startsWith("{")) {
                        val parsed =
                            runCatching { json.decodeFromString<XtreamVodInfo>(body) }
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
            runCatching { json.decodeFromString<XtreamSeriesInfo>(body) }.getOrNull()
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
        val ext = sanitizeExtension(containerExtension ?: cfg.vodExtPrefs.firstOrNull() ?: "mp4")
        return buildPlayUrl(vodKind, vodId, ext)
    }

    override fun buildSeriesEpisodeUrl(
        seriesId: Int,
        seasonNumber: Int,
        episodeNumber: Int,
        episodeId: Int?,
        containerExtension: String?,
    ): String {
        val cfg = config ?: return ""
        val ext = sanitizeExtension(containerExtension ?: cfg.seriesExtPrefs.firstOrNull() ?: "mp4")

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
    ): XtreamSearchResults =
        coroutineScope {
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
            HttpUrl
                .Builder()
                .scheme(cfg.scheme.lowercase())
                .host(cfg.host)
                .port(resolvedPort)

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
        val redactedUrl = url.replace(Regex("(password|username)=[^&]*"), "$1=***")
        UnifiedLog.d(TAG, "buildPlayerApiUrl: Built URL: $redactedUrl")
        
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
                    bp
                        .trim()
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
        val redactedUrl = url.replace(Regex("(password|username)=[^&]*"), "$1=***")
        UnifiedLog.d(TAG, "fetchRaw: Fetching URL: $redactedUrl, isEpg=$isEpg")

        // Check cache
        val cached = readCache(url, isEpg)
        if (cached != null) {
            UnifiedLog.d(TAG, "fetchRaw: Cache hit for $redactedUrl, returning ${cached.length} bytes")
            return cached
        }

        // Rate limit
        takeRateSlot(config?.host ?: "")

        // Execute request
        val request =
            Request
                .Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("Accept-Encoding", "gzip")
                .get()
                .build()

        return try {
            UnifiedLog.d(TAG, "fetchRaw: Executing HTTP request to $redactedUrl")
            http.newCall(request).execute().use { response ->
                val contentType = response.body?.contentType()
                val body = response.body?.string().orEmpty()
                UnifiedLog.d(TAG, "fetchRaw: Received response code ${response.code} for $redactedUrl")
                UnifiedLog.d(TAG, "fetchRaw: Received ${body.length} bytes from $redactedUrl")

                if (!response.isSuccessful) {
                    throw XtreamHttpException(
                        XtreamError.Http(
                            code = response.code,
                            message = response.message,
                        ),
                    )
                }

                if (body.isEmpty()) {
                    throw XtreamHttpException(XtreamError.ParseError("Empty response"))
                }

                if (isHtmlResponse(contentType, body)) {
                    val error =
                        if (response.code == 403 || response.code == 520 || response.code == 503) {
                            XtreamError.CdnBlocked(response.code, "Received HTML challenge page")
                        } else {
                            XtreamError.UnexpectedHtml("Unexpected HTML response", response.code)
                        }
                    throw XtreamHttpException(error)
                }

                writeCache(url, body)
                body
            }
        } catch (e: XtreamHttpException) {
            UnifiedLog.e(TAG, "fetchRaw: HTTP error while fetching $redactedUrl", e)
            throw e
        } catch (e: Exception) {
            UnifiedLog.e(TAG, "fetchRaw: Exception while fetching $redactedUrl", e)
            throw e
        }
    }

    private fun isHtmlResponse(contentType: MediaType?, body: String): Boolean {
        val looksLikeHtml = body.trimStart().startsWith("<", ignoreCase = true)
        val isHtmlType = contentType?.subtype?.contains("html", ignoreCase = true) == true
        return isHtmlType || looksLikeHtml
    }

    private suspend fun takeRateSlot(host: String) {
        rateMutex.withLock {
            val now = SystemClock.elapsedRealtime()
            val lastCall = lastCallByHost[host] ?: 0L
            val delta = now - lastCall
            if (delta in 0 until MIN_INTERVAL_MS) {
                delay(MIN_INTERVAL_MS - delta)
            }
            lastCallByHost[host] = SystemClock.elapsedRealtime()
        }
    }

    private suspend fun readCache(
        url: String,
        isEpg: Boolean,
    ): String? {
        val ttl = if (isEpg) EPG_CACHE_TTL_MS else CACHE_TTL_MS
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

    private fun <T> parseJsonArray(
        body: String,
        mapper: (JsonObject) -> T,
    ): List<T> {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() ?: return emptyList()
        if (root !is JsonArray) return emptyList()
        return root.mapNotNull { el ->
            val obj = el.jsonObjectOrNull() ?: return@mapNotNull null
            runCatching { mapper(obj) }.getOrNull()
        }
    }

    private fun JsonElement.jsonObjectOrNull(): JsonObject? = runCatching { jsonObject }.getOrNull()

    private fun JsonObject.stringOrNull(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

    private fun JsonObject.intOrNull(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull

    private fun JsonObject.longOrNull(key: String): Long? = this[key]?.jsonPrimitive?.longOrNull

    private fun JsonObject.doubleOrNull(key: String): Double? = this[key]?.jsonPrimitive?.doubleOrNull

    // =========================================================================
    // Internal: Discovery
    // =========================================================================

    private suspend fun resolvePort(config: XtreamApiConfig): Int =
        coroutineScope {
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

            val sem = Semaphore(4)
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
                HttpUrl
                    .Builder()
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
                                norm
                                    .removePrefix("/")
                                    .split('/')
                                    .filter { it.isNotBlank() }
                                    .forEach { addPathSegment(it) }
                            }
                        }
                    }.addPathSegment("player_api.php")
                    .addQueryParameter("action", action)
                    .addQueryParameter("category_id", "0")
                    .addQueryParameter("username", config.username)
                    .addQueryParameter("password", config.password)
                    .build()
                    .toString()

            val request =
                Request
                    .Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .header("Accept-Encoding", "gzip")
                    .get()
                    .build()

            try {
                http.newCall(request).execute().use { response ->
                    if (response.code in 200..299) {
                        val body = response.body.string().trim()
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
    ): XtreamCapabilities =
        coroutineScope {
            val actions = mutableMapOf<String, XtreamActionCapability>()
            val sem = Semaphore(4)

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
                    }.awaitAll()
            val vodCandidates = vodResults.filter { it.second }.map { it.first }
            val resolvedVodKind = vodCandidates.firstOrNull()

            // Probe extras
            val extraJobs =
                listOf(async { probe("get_short_epg", mapOf("stream_id" to "1", "limit" to "1")) })

            (basicJobs + extraJobs).awaitAll()

            val baseUrl =
                buildString {
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

    private fun sanitizeExtension(ext: String?): String {
        val lower = ext?.lowercase()?.trim().orEmpty()
        val valid = Regex("^[a-z0-9]{2,5}$")
        return if (valid.matches(lower)) lower else "mp4"
    }

    private fun urlEncode(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")

    private class XtreamHttpException(val error: XtreamError) : Exception(error.toString())

    private fun mapException(e: Exception): XtreamError =
        when (e) {
            is XtreamHttpException -> e.error
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

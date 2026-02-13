package com.fishit.player.infra.transport.xtream.client

import android.os.SystemClock
import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.infra.transport.xtream.XtreamApiConfig
import com.fishit.player.infra.transport.xtream.XtreamAuthState
import com.fishit.player.infra.transport.xtream.XtreamCapabilities
import com.fishit.player.infra.transport.xtream.XtreamCapabilityStore
import com.fishit.player.infra.transport.xtream.XtreamConnectionState
import com.fishit.player.infra.transport.xtream.XtreamDiscovery
import com.fishit.player.infra.transport.xtream.XtreamError
import com.fishit.player.infra.transport.xtream.XtreamPortStore
import com.fishit.player.infra.transport.xtream.XtreamServerInfo
import com.fishit.player.infra.transport.xtream.XtreamUrlBuilder
import com.fishit.player.infra.transport.xtream.XtreamUserInfo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject

/**
 * XtreamConnectionManager - Handles connection lifecycle (init, ping, close).
 *
 * Extracted from DefaultXtreamApiClient to reduce cyclomatic complexity.
 * Responsibilities:
 * - Port resolution and discovery
 * - Capability detection and caching
 * - Authentication state management
 * - Connection validation
 *
 * CC Target: ≤ 10 per function
 */
class XtreamConnectionManager @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val urlBuilder: XtreamUrlBuilder,
    private val discovery: XtreamDiscovery,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val capabilityStore: XtreamCapabilityStore? = null,
    private val portStore: XtreamPortStore? = null,
) {
    private val _authState = MutableStateFlow<XtreamAuthState>(XtreamAuthState.Unknown)
    val authState: StateFlow<XtreamAuthState> = _authState.asStateFlow()

    private val _connectionState = MutableStateFlow<XtreamConnectionState>(XtreamConnectionState.Disconnected)
    val connectionState: StateFlow<XtreamConnectionState> = _connectionState.asStateFlow()

    private var _capabilities: XtreamCapabilities? = null
    val capabilities: XtreamCapabilities?
        get() = _capabilities

    var config: XtreamApiConfig? = null
        private set
    var resolvedPort: Int = 80
        private set
    var vodKind: String = "movie" // Default to "movie" - most Xtream servers use /movie/ path
        internal set

    companion object {
        private const val TAG = "XtreamConnectionMgr"
    }

    /**
     * Initialize connection to Xtream server.
     * CC: ~8 (port resolution, cache check, discovery, validation)
     */
    suspend fun initialize(
        config: XtreamApiConfig,
        forceDiscovery: Boolean,
    ): Result<XtreamCapabilities> = withContext(io) {
        UnifiedLog.d(TAG) { "Initializing for ${config.host} (forceDiscovery=$forceDiscovery)" }
        this@XtreamConnectionManager.config = config
        _connectionState.value = XtreamConnectionState.Connecting
        _authState.value = XtreamAuthState.Pending

        try {
            // 1. Resolve port
            resolvedPort = config.port ?: resolvePort(config)
            UnifiedLog.d(TAG) { "Resolved port: $resolvedPort" }

            // 2. Configure urlBuilder EARLY (before cache check or capability discovery)
            urlBuilder.configure(config, resolvedPort)

            // 3. Check cache
            val cacheKey = buildCacheKey(config, resolvedPort)
            if (!forceDiscovery) {
                capabilityStore?.get(cacheKey)?.let { cached ->
                    _capabilities = cached
                    vodKind = cached.resolvedAliases.vodKind ?: "movie"
                    urlBuilder.updateVodKind(vodKind)
                    return@withContext validateAndComplete(config, cached)
                }
            }

            // 4. Discover capabilities
            val startTime = SystemClock.elapsedRealtime()
            val caps = discoverCapabilities(config, resolvedPort, cacheKey)
            val latency = SystemClock.elapsedRealtime() - startTime

            _capabilities = caps
            vodKind = caps.resolvedAliases.vodKind ?: "movie"
            urlBuilder.updateVodKind(vodKind)
            capabilityStore?.put(caps)

            // 5. Validate credentials
            validateAndComplete(config, caps, latency)
        } catch (e: Exception) {
            UnifiedLog.e(TAG, e) { "Initialize failed for ${config.host}" }
            val error = XtreamError.Network(e.message ?: "Unknown error", e)
            _connectionState.value = XtreamConnectionState.Error(error, retryable = true)
            _authState.value = XtreamAuthState.Failed(error)
            Result.failure(e)
        }
    }

    /**
     * Ping the server to check connectivity.
     * CC: 3
     */
    suspend fun ping(): Boolean = withContext(io) {
        if (config == null) return@withContext false
        try {
            val url = buildPlayerApiUrl("get_live_categories")
            val body = fetchRaw(url, false)
            val trimmed = body?.trim { it.isWhitespace() || it == '\uFEFF' }
            !trimmed.isNullOrEmpty() && (trimmed.startsWith("[") || trimmed.startsWith("{"))
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Close connection and reset state.
     * Note: urlBuilder instance persists (singleton), but state is reset on next initialize()
     * CC: 1
     */
    fun close() {
        _connectionState.value = XtreamConnectionState.Disconnected
        _authState.value = XtreamAuthState.Unknown
        _capabilities = null
        config = null
        // urlBuilder is an injected singleton; its instance persists, but state is (re)configured on initialize()
    }

    /**
     * Validate credentials and complete initialization.
     * CC: ~7 (status checks, fallback logic)
     */
    @Suppress("UNUSED_PARAMETER")
    private suspend fun validateAndComplete(
        config: XtreamApiConfig,
        caps: XtreamCapabilities,
        latency: Long = 0,
    ): Result<XtreamCapabilities> {
        val result = getServerInfo()
        return result.fold(
            onSuccess = { serverInfo ->
                processServerInfo(serverInfo, caps, latency)
            },
            onFailure = { error ->
                UnifiedLog.w(TAG, error) { "validateAndComplete: getServerInfo failed, trying fallback" }
                handleValidationFallback(caps, latency, error)
            }
        )
    }

    /**
     * Process server info response and update auth state.
     * CC: ~5 (user status checks)
     */
    private fun processServerInfo(
        serverInfo: XtreamServerInfo,
        caps: XtreamCapabilities,
        latency: Long,
    ): Result<XtreamCapabilities> {
        val userInfo = serverInfo.userInfo?.let { XtreamUserInfo.fromRaw(it) }
        return if (userInfo != null) {
            when (userInfo.status) {
                XtreamUserInfo.UserStatus.ACTIVE -> {
                    _authState.value = XtreamAuthState.Authenticated(userInfo)
                    _connectionState.value = XtreamConnectionState.Connected(caps.baseUrl, latency)
                    Result.success(caps)
                }
                XtreamUserInfo.UserStatus.EXPIRED -> {
                    _authState.value = XtreamAuthState.Expired(userInfo.expDateEpoch)
                    Result.failure(Exception("Account expired"))
                }
                else -> {
                    _authState.value = XtreamAuthState.Failed(XtreamError.InvalidCredentials)
                    Result.failure(Exception("Account not active: ${userInfo.status}"))
                }
            }
        } else {
            // No user info but server responded - assume OK
            UnifiedLog.d(TAG) { "validateAndComplete: No user info, assuming OK" }
            _connectionState.value = XtreamConnectionState.Connected(caps.baseUrl, latency)
            Result.success(caps)
        }
    }

    /**
     * Handle validation fallback when getServerInfo fails.
     * CC: ~4
     */
    private suspend fun handleValidationFallback(
        caps: XtreamCapabilities,
        latency: Long,
        error: Throwable,
    ): Result<XtreamCapabilities> {
        val fallbackResult = tryFallbackValidation()
        return if (fallbackResult) {
            UnifiedLog.d(TAG) { "validateAndComplete: Fallback validation succeeded" }
            _connectionState.value = XtreamConnectionState.Connected(caps.baseUrl, latency)
            _authState.value = XtreamAuthState.Unknown
            Result.success(caps)
        } else {
            UnifiedLog.e(TAG) { "validateAndComplete: Fallback validation failed" }
            _authState.value = XtreamAuthState.Failed(XtreamError.InvalidCredentials)
            Result.failure(error)
        }
    }

    /**
     * Try fallback validation when getServerInfo fails.
     * CC: ~5 (action loop with try-catch)
     */
    private suspend fun tryFallbackValidation(): Boolean = withContext(io) {
        val fallbackActions = listOf(
            "get_live_categories",
            "get_vod_categories",
            "get_series_categories",
            "get_live_streams",
        )

        for (action in fallbackActions) {
            if (tryValidationAction(action)) {
                return@withContext true
            }
        }

        UnifiedLog.w(TAG) { "tryFallbackValidation: All endpoints failed" }
        false
    }

    /**
     * Try a single validation action.
     * CC: ~4
     */
    private suspend fun tryValidationAction(action: String): Boolean {
        return try {
            UnifiedLog.d(TAG) { "tryFallbackValidation: Trying $action" }
            val url = buildPlayerApiUrl(action)
            val body = fetchRaw(url, false)

            if (body != null && body.isNotEmpty()) {
                val parsed = runCatching { json.parseToJsonElement(body) }.getOrNull()
                if (parsed != null) {
                    UnifiedLog.d(TAG) { "tryFallbackValidation: Success with $action" }
                    return true
                }
            }
            false
        } catch (e: Exception) {
            UnifiedLog.d(TAG) { "tryFallbackValidation: $action failed, trying next..." }
            false
        }
    }

    /**
     * Get server info from player_api.php.
     * CC: ~6 (parsing and error handling)
     */
    suspend fun getServerInfo(): Result<XtreamServerInfo> = withContext(io) {
        try {
            val url = buildPlayerApiUrl(action = null)
            UnifiedLog.d(TAG) { "getServerInfo: Fetching..." }

            val body = fetchRaw(url, false)
                ?: return@withContext Result.failure(
                    Exception("player_api.php returned non-JSON response")
                )

            UnifiedLog.d(TAG) { "getServerInfo: Received ${body.length} bytes, parsing..." }

            val parsed = runCatching { json.decodeFromString<XtreamServerInfo>(body) }
                .getOrElse { parseError ->
                    UnifiedLog.w(TAG, parseError) { "getServerInfo: JSON parsing failed" }
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

    /**
     * Get panel info (server URL/name).
     * CC: 2
     */
    suspend fun getPanelInfo(): String? =
        getServerInfo().getOrNull()?.serverInfo?.url

    /**
     * Get normalized user info.
     * CC: 3
     */
    suspend fun getUserInfo(): Result<XtreamUserInfo> =
        getServerInfo().mapCatching { serverInfo ->
            val raw = serverInfo.userInfo
                ?: throw Exception("No user info in server response")
            XtreamUserInfo.fromRaw(raw)
        }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private suspend fun resolvePort(config: XtreamApiConfig): Int {
        // Use host as cache key for port resolution
        val cacheKey = config.host
        
        portStore?.get(cacheKey)?.let { cached ->
            UnifiedLog.d(TAG) { "Using cached port: $cached" }
            return cached
        }

        val discovered = discovery.resolvePort(config)
        if (discovered != config.port) {
            portStore?.put(cacheKey, discovered)
        }

        return discovered
    }

    private suspend fun discoverCapabilities(
        config: XtreamApiConfig,
        port: Int,
        cacheKey: String,
    ): XtreamCapabilities {
        // urlBuilder already configured in initialize()
        return discovery.discoverCapabilities(
            config = config,
            port = port,
            forceRefresh = false,
        )
    }

    private fun buildCacheKey(config: XtreamApiConfig, port: Int): String {
        return "${config.host}:$port:${config.username}"
    }

    private fun buildPlayerApiUrl(action: String?): String {
        val cfg = config ?: throw IllegalStateException("Client not initialized")
        // urlBuilder is injected and configured in initialize()
        return urlBuilder.playerApiUrl(action)
    }

    /**
     * Internal helper to fetch HTTP response using OkHttp directly.
     *
     * @param url The URL to fetch
     * @param isEpg Whether this is an EPG request (currently unused, reserved for future caching)
     * @return Response body as string, or null if request failed
     */
    @Suppress("UNUSED_PARAMETER")
    private suspend fun fetchRaw(url: String, isEpg: Boolean): String? {
        return try {
            val request = Request.Builder().url(url).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) null else response.body?.string()
            }
        } catch (_: IOException) {
            null
        }
    }

    // =========================================================================
    // URL Building (Delegating to XtreamUrlBuilder)
    // =========================================================================

    /**
     * Build live stream playback URL.
     */
    fun buildLiveUrl(streamId: Int, extension: String? = null, liveKind: String? = null): String =
        urlBuilder.liveUrl(streamId, extension, liveKind)

    /**
     * Build VOD playback URL.
     */
    fun buildVodUrl(vodId: Int, containerExtension: String?, vodKind: String? = null): String =
        urlBuilder.vodUrl(vodId, containerExtension, vodKind)

    /**
     * Build series episode playback URL.
     */
    fun buildSeriesEpisodeUrl(
        seriesId: Int,
        seasonNumber: Int,
        episodeNumber: Int,
        episodeId: Int? = null,
        containerExtension: String? = null,
        seriesKind: String? = null,
    ): String = urlBuilder.seriesEpisodeUrl(seriesId, seasonNumber, episodeNumber, episodeId, containerExtension, seriesKind)

    /**
     * Build catchup/timeshift URL.
     */
    fun buildCatchupUrl(streamId: Int, start: Long, duration: Int): String? =
        urlBuilder.catchupUrl(streamId, start, duration)

    /**
     * Raw API call using player_api.php.
     */
    suspend fun rawApiCall(action: String, params: Map<String, String>): String? =
        withContext(io) {
            val url = urlBuilder.playerApiUrl(action, params)
            fetchRaw(url, isEpg = false)
        }
}

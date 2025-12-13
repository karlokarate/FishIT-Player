package com.fishit.player.infra.transport.xtream

import kotlinx.coroutines.flow.StateFlow

/**
 * XtreamApiClient – Universeller Xtream Codes API Client
 *
 * Dieser Client ist für maximale Kompatibilität mit verschiedenen Xtream-Panel-Varianten ausgelegt:
 * - Xtream-UI (Original)
 * - XUI.ONE (Modernisierte Variante)
 * - StalkerPortal-kompatible Panels
 * - Diverse Reseller-Panels (KönigTV, Area51, Iris, etc.)
 *
 * **Design-Prinzipien:**
 * 1. **Alias-Aware:** VOD-Endpunkte können `vod`, `movie`, oder `movies` heißen
 * 2. **Field-Flexible:** ID-Felder variieren (`stream_id`, `vod_id`, `movie_id`, `id`)
 * 3. **Port-Resilient:** Automatische Port-Discovery für verschiedene Setups
 * 4. **Rate-Limited:** Per-Host Throttling zum Schutz vor Panel-Sperren
 * 5. **Cached:** Intelligentes Caching mit konfigurierbarem TTL
 * 6. **Flow-basiert:** Reactive State für Auth/Connection
 *
 * **Panel-Kompatibilitäts-Matrix:** | Feature | Xtream-UI | XUI.ONE | StalkerPortal | Reseller |
 * |---------------------|-----------|---------|---------------|----------| | get_live_streams | ✅ |
 * ✅ | ✅ | ✅ | | get_vod_streams | ✅ | ✅ | ⚠️ alias | ✅ | | get_series | ✅ | ✅ | ✅ | ✅ | |
 * get_short_epg | ✅ | ✅ | ⚠️ | ⚠️ | | category_id=* | ✅ | ⚠️ | ❌ | ⚠️ | | category_id=0 | ⚠️ | ✅ |
 * ✅ | ✅ |
 *
 * **v1 Component Mapping:**
 * - `XtreamClient` → Core API methods
 * - `XtreamConfig` → URL factory (via XtreamApiConfig)
 * - `XtreamCapabilities` → Discovery & caching
 * - `CapabilityDiscoverer` → Port resolver & feature detection
 *
 * @see XtreamApiConfig for URL building configuration
 * @see XtreamConnectionState for connection lifecycle
 * @see XtreamAuthState for authentication state
 */
interface XtreamApiClient {
    // =========================================================================
    // State & Lifecycle
    // =========================================================================

    /** Current authentication state. Emits updates when credentials are verified or invalidated. */
    val authState: StateFlow<XtreamAuthState>

    /** Current connection state. Tracks API availability and network status. */
    val connectionState: StateFlow<XtreamConnectionState>

    /**
     * Resolved API capabilities after discovery. Null until [initialize] completes successfully.
     */
    val capabilities: XtreamCapabilities?

    /**
     * Initialize the client with credentials and perform capability discovery.
     *
     * This method:
     * 1. Resolves the correct port (if not specified)
     * 2. Discovers supported API endpoints and aliases
     * 3. Validates credentials via server info call
     * 4. Caches capabilities for future sessions
     *
     * @param config Initial configuration with credentials
     * @param forceDiscovery If true, bypasses capability cache
     * @return Result with resolved capabilities or error
     */
    suspend fun initialize(
        config: XtreamApiConfig,
        forceDiscovery: Boolean = false,
    ): Result<XtreamCapabilities>

    /**
     * Test current connection and credentials. Does not modify state; use for health checks.
     *
     * @return True if API is reachable and credentials are valid
     */
    suspend fun ping(): Boolean

    /** Close the client and release resources. Safe to call multiple times. */
    fun close()

    // =========================================================================
    // Server & User Info
    // =========================================================================

    /**
     * Fetch server information (no action parameter). Most panels return this with just
     * username/password.
     *
     * Response includes:
     * - user_info: Account details (status, exp_date, max_connections, etc.)
     * - server_info: Panel details (timezone, timestamp, etc.)
     *
     * @return Server info or error
     */
    suspend fun getServerInfo(): Result<XtreamServerInfo>

    /**
     * Fetch detailed user account information. Same as getServerInfo but with explicit parsing.
     *
     * @return User info or error
     */
    suspend fun getUserInfo(): Result<XtreamUserInfo>

    // =========================================================================
    // Categories
    // =========================================================================

    /**
     * Fetch live TV categories. Action: get_live_categories
     *
     * @return List of categories or empty on error
     */
    suspend fun getLiveCategories(): List<XtreamCategory>

    /**
     * Fetch VOD/movie categories. Action: get_vod_categories (with alias fallback: movie, movies)
     *
     * @return List of categories or empty on error
     */
    suspend fun getVodCategories(): List<XtreamCategory>

    /**
     * Fetch series categories. Action: get_series_categories
     *
     * @return List of categories or empty on error
     */
    suspend fun getSeriesCategories(): List<XtreamCategory>

    // =========================================================================
    // Content Lists (Paginated Client-Side)
    // =========================================================================

    /**
     * Fetch live TV streams. Action: get_live_streams
     *
     * Panel compatibility:
     * - category_id=* : Xtream-UI, some XUI.ONE
     * - category_id=0 : Most panels
     * - No category_id: Fallback for strict panels
     *
     * @param categoryId Optional category filter (null = all)
     * @param limit Max items to return (client-side slicing)
     * @param offset Pagination offset (client-side)
     * @return List of live streams
     */
    suspend fun getLiveStreams(
        categoryId: String? = null,
        limit: Int = 500,
        offset: Int = 0,
    ): List<XtreamLiveStream>

    /**
     * Fetch VOD/movie streams. Action: get_vod_streams (with alias fallback)
     *
     * Alias resolution order: discovered vodKind → vod → movie → movies
     *
     * @param categoryId Optional category filter
     * @param limit Max items to return
     * @param offset Pagination offset
     * @return List of VOD items
     */
    suspend fun getVodStreams(
        categoryId: String? = null,
        limit: Int = 500,
        offset: Int = 0,
    ): List<XtreamVodStream>

    /**
     * Fetch series list. Action: get_series
     *
     * @param categoryId Optional category filter
     * @param limit Max items to return
     * @param offset Pagination offset
     * @return List of series items
     */
    suspend fun getSeries(
        categoryId: String? = null,
        limit: Int = 500,
        offset: Int = 0,
    ): List<XtreamSeriesStream>

    // =========================================================================
    // Detail Endpoints
    // =========================================================================

    /**
     * Fetch detailed VOD information. Action: get_vod_info (with alias fallback)
     *
     * ID field resolution: vod_id → movie_id → id → stream_id
     *
     * @param vodId The VOD item ID
     * @return Detailed VOD info or null if not found
     */
    suspend fun getVodInfo(vodId: Int): XtreamVodInfo?

    /**
     * Fetch detailed series information including episodes. Action: get_series_info
     *
     * @param seriesId The series ID
     * @return Detailed series info with seasons/episodes, or null
     */
    suspend fun getSeriesInfo(seriesId: Int): XtreamSeriesInfo?

    // =========================================================================
    // EPG (Electronic Program Guide)
    // =========================================================================

    /**
     * Fetch short EPG for a specific stream. Action: get_short_epg
     *
     * @param streamId The live stream ID
     * @param limit Max programmes to return
     * @return List of EPG entries
     */
    suspend fun getShortEpg(
        streamId: Int,
        limit: Int = 20,
    ): List<XtreamEpgProgramme>

    /**
     * Fetch full EPG for a date range. Action: get_simple_data_table (if supported)
     *
     * @param streamId The live stream ID
     * @return List of EPG entries for the day
     */
    suspend fun getFullEpg(streamId: Int): List<XtreamEpgProgramme>

    /**
     * Prefetch EPG for multiple streams (fire-and-forget). Useful for warming cache before UI
     * render.
     *
     * @param streamIds List of stream IDs
     * @param perStreamLimit Limit per stream
     */
    suspend fun prefetchEpg(
        streamIds: List<Int>,
        perStreamLimit: Int = 10,
    )

    // =========================================================================
    // Playback URLs
    // =========================================================================

    /**
     * Build live stream playback URL.
     *
     * URL format: {baseUrl}/{liveKind}/{username}/{password}/{streamId}.{ext}
     *
     * @param streamId The stream ID
     * @param extension Optional extension override (default: from config prefs)
     * @return Playback URL string
     */
    fun buildLiveUrl(
        streamId: Int,
        extension: String? = null,
    ): String

    /**
     * Build VOD playback URL.
     *
     * URL format: {baseUrl}/{vodKind}/{username}/{password}/{vodId}.{ext}
     *
     * @param vodId The VOD item ID
     * @param containerExtension Container from VOD info (mp4, mkv, etc.)
     * @return Playback URL string
     */
    fun buildVodUrl(
        vodId: Int,
        containerExtension: String?,
    ): String

    /**
     * Build series episode playback URL.
     *
     * Two URL formats supported:
     * 1. Episode ID: {baseUrl}/series/{username}/{password}/{episodeId}.{ext}
     * 2. Path-based: {baseUrl}/series/{username}/{password}/{seriesId}/{season}/{episode}.{ext}
     *
     * @param seriesId The series ID
     * @param seasonNumber Season number
     * @param episodeNumber Episode number
     * @param episodeId Optional direct episode ID (preferred if available)
     * @param containerExtension Container from episode info
     * @return Playback URL string
     */
    fun buildSeriesEpisodeUrl(
        seriesId: Int,
        seasonNumber: Int,
        episodeNumber: Int,
        episodeId: Int? = null,
        containerExtension: String? = null,
    ): String

    // =========================================================================
    // Search (Client-Side)
    // =========================================================================

    /**
     * Search across all content types. Note: Most Xtream panels don't have server-side search; this
     * performs client-side filtering after fetching lists.
     *
     * @param query Search query
     * @param types Content types to search (default: all)
     * @param limit Max results
     * @return Combined search results
     */
    suspend fun search(
        query: String,
        types: Set<XtreamContentType> = XtreamContentType.entries.toSet(),
        limit: Int = 50,
    ): XtreamSearchResults

    // =========================================================================
    // Catchup / Timeshift (if supported)
    // =========================================================================

    /**
     * Build catchup/timeshift URL for archived content. Only works if stream has tv_archive
     * enabled.
     *
     * @param streamId The stream ID
     * @param start Start timestamp (epoch seconds)
     * @param duration Duration in minutes
     * @return Catchup URL or null if not supported
     */
    fun buildCatchupUrl(
        streamId: Int,
        start: Long,
        duration: Int,
    ): String?

    // =========================================================================
    // Raw API Access (for extensibility)
    // =========================================================================

    /**
     * Execute raw API call with custom action. For advanced use cases not covered by typed methods.
     *
     * @param action The API action name
     * @param params Additional query parameters
     * @return Raw JSON response string or null on error
     */
    suspend fun rawApiCall(
        action: String,
        params: Map<String, String> = emptyMap(),
    ): String?
}

// =============================================================================
// State Types
// =============================================================================

/** Authentication state for Xtream API. */
sealed interface XtreamAuthState {
    /** Initial state before any auth attempt */
    data object Unknown : XtreamAuthState

    /** Credentials provided but not yet validated */
    data object Pending : XtreamAuthState

    /** Successfully authenticated */
    data class Authenticated(
        val userInfo: XtreamUserInfo,
    ) : XtreamAuthState

    /** Authentication failed */
    data class Failed(
        val error: XtreamError,
    ) : XtreamAuthState

    /** Account expired or disabled */
    data class Expired(
        val expDate: Long?,
    ) : XtreamAuthState
}

/** Connection state for API availability. */
sealed interface XtreamConnectionState {
    /** Not yet connected */
    data object Disconnected : XtreamConnectionState

    /** Connection in progress (discovery/init) */
    data object Connecting : XtreamConnectionState

    /** Connected and ready */
    data class Connected(
        val baseUrl: String,
        val latencyMs: Long,
    ) : XtreamConnectionState

    /** Connection error */
    data class Error(
        val error: XtreamError,
        val retryable: Boolean,
    ) : XtreamConnectionState
}

/** Error types for Xtream API operations. */
sealed interface XtreamError {
    /** Network error (timeout, DNS, etc.) */
    data class Network(
        val message: String,
        val cause: Throwable? = null,
    ) : XtreamError

    /** HTTP error (4xx, 5xx) */
    data class Http(
        val code: Int,
        val message: String,
    ) : XtreamError

    /** Invalid credentials (401/403 or user_info.status != "Active") */
    data object InvalidCredentials : XtreamError

    /** Account expired */
    data class AccountExpired(
        val expDate: Long?,
    ) : XtreamError

    /** JSON parsing error */
    data class ParseError(
        val message: String,
    ) : XtreamError

    /** API action not supported by panel */
    data class Unsupported(
        val action: String,
    ) : XtreamError

    /** Rate limited by panel */
    data class RateLimited(
        val retryAfterMs: Long?,
    ) : XtreamError

    /** Unknown error */
    data class Unknown(
        val message: String,
    ) : XtreamError
}

/** Content type enumeration. */
enum class XtreamContentType {
    LIVE,
    VOD,
    SERIES,
}

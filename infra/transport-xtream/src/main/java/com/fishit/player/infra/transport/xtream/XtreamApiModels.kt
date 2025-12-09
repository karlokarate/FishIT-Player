package com.fishit.player.infra.transport.xtream

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// =============================================================================
// XtreamApiClient Models
//
// Umfassende DTOs für maximale Panel-Kompatibilität.
// Basiert auf v1 XtreamModels.kt + Erkenntnisse aus:
// - tellytv/telly (Go)
// - Diverse Xtream-Panel-Dokumentationen
// - Real-World Panel-Varianten (Xtream-UI, XUI.ONE, StalkerPortal)
// =============================================================================

// =============================================================================
// Configuration
// =============================================================================

/**
 * API Configuration for Xtream client initialization.
 *
 * @param scheme HTTP scheme (http/https)
 * @param host Panel hostname
 * @param port Panel port (null = auto-discover)
 * @param username Account username
 * @param password Account password
 * @param basePath Optional reverse proxy path (e.g., "/xtream")
 * @param liveExtPrefs Preferred live stream extensions (order matters)
 * @param vodExtPrefs Preferred VOD extensions
 * @param seriesExtPrefs Preferred series extensions
 */
data class XtreamApiConfig(
        val scheme: String = "http",
        val host: String,
        val port: Int? = null,
        val username: String,
        val password: String,
        val basePath: String? = null,
        val liveExtPrefs: List<String> = listOf("m3u8", "ts"),
        val vodExtPrefs: List<String> = listOf("mp4", "mkv", "avi"),
        val seriesExtPrefs: List<String> = listOf("mp4", "mkv", "avi"),
) {
    init {
        require(
                scheme.equals("http", ignoreCase = true) ||
                        scheme.equals("https", ignoreCase = true)
        ) { "scheme must be 'http' or 'https'" }
        require(host.isNotBlank()) { "host must not be blank" }
        require(username.isNotBlank()) { "username must not be blank" }
        require(password.isNotBlank()) { "password must not be blank" }
    }

    /**
     * Create config from M3U get.php URL.
     *
     * Example: http://host:8080/get.php?username=user&password=pass&output=ts
     */
    companion object {
        fun fromM3uUrl(url: String): XtreamApiConfig? {
            val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return null
            val params =
                    uri.query?.split("&")?.associate {
                        it.split("=", limit = 2).let { p -> p[0] to (p.getOrNull(1) ?: "") }
                    }
                            ?: emptyMap()

            val username = params["username"] ?: return null
            val password = params["password"] ?: return null
            val host = uri.host ?: return null
            val output = params["output"]?.lowercase()
            val port = if (uri.port > 0) uri.port else null

            return XtreamApiConfig(
                    scheme = uri.scheme ?: "http",
                    host = host,
                    port = port,
                    username = username,
                    password = password,
                    liveExtPrefs =
                            when (output) {
                                "ts" -> listOf("ts", "m3u8")
                                "m3u8", "hls" -> listOf("m3u8", "ts")
                                else -> listOf("m3u8", "ts")
                            },
            )
        }
    }
}

// =============================================================================
// Capabilities (Discovery Result)
// =============================================================================

/**
 * Discovered capabilities of an Xtream panel. Cached per provider+account to avoid repeated
 * discovery.
 */
@Serializable
data class XtreamCapabilities(
        /** Version of capability schema */
        val version: Int = 2,

        /** Cache key: baseUrl|username */
        val cacheKey: String,

        /** Resolved base URL including port */
        val baseUrl: String,

        /** Account username */
        val username: String,

        /** Resolved API aliases */
        val resolvedAliases: XtreamResolvedAliases = XtreamResolvedAliases(),

        /** Supported actions with their capabilities */
        val actions: Map<String, XtreamActionCapability> = emptyMap(),

        /** Field mapping hints per content type */
        val schemaHints: Map<String, XtreamSchemaHint> = emptyMap(),

        /** Extra feature support */
        val extras: XtreamExtrasCapability = XtreamExtrasCapability(),

        /** Cache timestamp */
        val cachedAt: Long = 0L,
)

/** Resolved API path aliases. Different panels use different names for VOD endpoints. */
@Serializable
data class XtreamResolvedAliases(
        /** Resolved VOD kind: "vod" | "movie" | "movies" */
        val vodKind: String? = null,

        /** All VOD aliases that worked during discovery */
        val vodCandidates: List<String> = emptyList(),
)

/** Capability details for a single API action. */
@Serializable
data class XtreamActionCapability(
        /** Whether this action is supported */
        val supported: Boolean = false,

        /** Response type: "array" | "object" | "unknown" */
        val responseType: String? = null,

        /** Sample keys from first response object */
        val sampleKeys: List<String> = emptyList(),

        /** Detected ID field name (stream_id, vod_id, etc.) */
        val idField: String? = null,

        /** Detected name field */
        val nameField: String? = null,

        /** Detected logo/icon field */
        val logoField: String? = null,
)

/** Schema hints for field mapping. */
@Serializable
data class XtreamSchemaHint(
        /** Field mappings: normalized -> rawKey */
        val fieldMappings: Map<String, String> = emptyMap(),
)

/** Extra feature capabilities. */
@Serializable
data class XtreamExtrasCapability(
        /** Supports get_short_epg action */
        val supportsShortEpg: Boolean = false,

        /** Supports get_simple_data_table action */
        val supportsSimpleDataTable: Boolean = false,

        /** Supports catchup/timeshift */
        val supportsCatchup: Boolean = false,

        /** Supports VOD info endpoint */
        val supportsVodInfo: Boolean = false,

        /** Supports series info endpoint */
        val supportsSeriesInfo: Boolean = false,
)

// =============================================================================
// Server & User Info
// =============================================================================

/** Server information from base API call. */
@Serializable
data class XtreamServerInfo(
        @SerialName("user_info") val userInfo: XtreamUserInfoRaw? = null,
        @SerialName("server_info") val serverInfo: XtreamServerInfoRaw? = null,
)

/** Raw user info from API response. */
@Serializable
data class XtreamUserInfoRaw(
        val username: String? = null,
        val password: String? = null,
        val status: String? = null,
        @SerialName("exp_date") val expDate: String? = null,
        @SerialName("is_trial") val isTrial: String? = null,
        @SerialName("active_cons") val activeCons: String? = null,
        @SerialName("created_at") val createdAt: String? = null,
        @SerialName("max_connections") val maxConnections: String? = null,
        @SerialName("allowed_output_formats") val allowedOutputFormats: List<String>? = null,
        val message: String? = null,
        val auth: Int? = null,
)

/** Raw server info from API response. */
@Serializable
data class XtreamServerInfoRaw(
        val url: String? = null,
        val port: String? = null,
        @SerialName("https_port") val httpsPort: String? = null,
        @SerialName("server_protocol") val serverProtocol: String? = null,
        @SerialName("rtmp_port") val rtmpPort: String? = null,
        val timezone: String? = null,
        @SerialName("timestamp_now") val timestampNow: Long? = null,
        @SerialName("time_now") val timeNow: String? = null,
        val process: Boolean? = null,
)

/** Normalized user info for app consumption. */
data class XtreamUserInfo(
        val username: String,
        val status: UserStatus,
        val expDateEpoch: Long?,
        val maxConnections: Int,
        val activeConnections: Int,
        val isTrial: Boolean,
        val allowedFormats: List<String>,
        val createdAt: Long?,
        val message: String?,
) {
    enum class UserStatus {
        ACTIVE,
        EXPIRED,
        BANNED,
        DISABLED,
        UNKNOWN,
    }

    companion object {
        fun fromRaw(raw: XtreamUserInfoRaw): XtreamUserInfo {
            val status =
                    when (raw.status?.lowercase()) {
                        "active" -> UserStatus.ACTIVE
                        "expired" -> UserStatus.EXPIRED
                        "banned" -> UserStatus.BANNED
                        "disabled" -> UserStatus.DISABLED
                        else -> UserStatus.UNKNOWN
                    }

            return XtreamUserInfo(
                    username = raw.username.orEmpty(),
                    status = status,
                    expDateEpoch = raw.expDate?.toLongOrNull(),
                    maxConnections = raw.maxConnections?.toIntOrNull() ?: 1,
                    activeConnections = raw.activeCons?.toIntOrNull() ?: 0,
                    isTrial = raw.isTrial == "1" || raw.isTrial?.lowercase() == "true",
                    allowedFormats = raw.allowedOutputFormats ?: listOf("m3u8", "ts"),
                    createdAt = raw.createdAt?.toLongOrNull(),
                    message = raw.message,
            )
        }
    }
}

// =============================================================================
// Categories
// =============================================================================

/** Category from API. Used for Live, VOD, and Series categories. */
@Serializable
data class XtreamCategory(
        @SerialName("category_id") val categoryId: String? = null,
        @SerialName("category_name") val categoryName: String? = null,
        @SerialName("parent_id") val parentId: Int? = null,
) {
    /** Safe ID accessor */
    val id: String
        get() = categoryId.orEmpty()

    /** Safe name accessor */
    val name: String
        get() = categoryName.orEmpty()
}

// =============================================================================
// Content Streams
// =============================================================================

/** Live TV stream from get_live_streams. */
@Serializable
data class XtreamLiveStream(
        val num: Int? = null,
        val name: String? = null,
        @SerialName("stream_id") val streamId: Int? = null,

        // Alternative ID field names (panel-specific)
        val id: Int? = null,
        @SerialName("stream_icon") val streamIcon: String? = null,
        val logo: String? = null,
        @SerialName("epg_channel_id") val epgChannelId: String? = null,
        @SerialName("tv_archive") val tvArchive: Int? = null,
        @SerialName("tv_archive_duration") val tvArchiveDuration: Int? = null,
        @SerialName("category_id") val categoryId: String? = null,
        @SerialName("category_ids") val categoryIds: List<Int>? = null,
        @SerialName("custom_sid") val customSid: String? = null,
        val added: String? = null,
        @SerialName("is_adult") val isAdult: String? = null,
        @SerialName("direct_source") val directSource: String? = null,
) {
    /** Resolved stream ID (handles different panel field names) */
    val resolvedId: Int
        get() = streamId ?: id ?: 0

    /** Resolved icon URL */
    val resolvedIcon: String?
        get() = streamIcon ?: logo

    /** Whether catchup is available */
    val hasCatchup: Boolean
        get() = (tvArchive ?: 0) > 0
}

/** VOD/Movie stream from get_vod_streams. */
@Serializable
data class XtreamVodStream(
        val num: Int? = null,
        val name: String? = null,
        @SerialName("vod_id") val vodId: Int? = null,
        @SerialName("movie_id") val movieId: Int? = null,
        @SerialName("stream_id") val streamId: Int? = null,
        val id: Int? = null,
        @SerialName("stream_icon") val streamIcon: String? = null,
        @SerialName("poster_path") val posterPath: String? = null,
        val cover: String? = null,
        val logo: String? = null,
        @SerialName("category_id") val categoryId: String? = null,
        @SerialName("category_ids") val categoryIds: List<Int>? = null,
        @SerialName("container_extension") val containerExtension: String? = null,
        val added: String? = null,
        val rating: String? = null,
        @SerialName("rating_5based") val rating5Based: Double? = null,
        @SerialName("is_adult") val isAdult: String? = null,
        @SerialName("direct_source") val directSource: String? = null,

        // Quick info fields (some panels include these in list)
        val year: String? = null,
        val genre: String? = null,
        val plot: String? = null,
        val duration: String? = null,
) {
    /** Resolved VOD ID (handles different panel field names) */
    val resolvedId: Int
        get() = vodId ?: movieId ?: streamId ?: id ?: 0

    /** Resolved poster URL */
    val resolvedPoster: String?
        get() = streamIcon ?: posterPath ?: cover ?: logo
}

/** Series stream from get_series. */
@Serializable
data class XtreamSeriesStream(
        val num: Int? = null,
        val name: String? = null,
        @SerialName("series_id") val seriesId: Int? = null,
        val id: Int? = null,
        val cover: String? = null,
        @SerialName("poster_path") val posterPath: String? = null,
        val logo: String? = null,
        @SerialName("backdrop_path") val backdropPath: String? = null,
        @SerialName("category_id") val categoryId: String? = null,
        @SerialName("category_ids") val categoryIds: List<Int>? = null,
        val added: String? = null,
        val rating: String? = null,
        @SerialName("rating_5based") val rating5Based: Double? = null,
        @SerialName("is_adult") val isAdult: String? = null,

        // Quick info fields
        val year: String? = null,
        val genre: String? = null,
        val plot: String? = null,
        val cast: String? = null,
        @SerialName("episode_run_time") val episodeRunTime: String? = null,
        @SerialName("last_modified") val lastModified: String? = null,
) {
    /** Resolved series ID */
    val resolvedId: Int
        get() = seriesId ?: id ?: 0

    /** Resolved cover URL */
    val resolvedCover: String?
        get() = cover ?: posterPath ?: logo
}

// =============================================================================
// Detail Endpoints
// =============================================================================

/** Full VOD info from get_vod_info. */
@Serializable
data class XtreamVodInfo(
        val info: XtreamVodInfoBlock? = null,
        @SerialName("movie_data") val movieData: XtreamMovieData? = null,
)

/** VOD info block with metadata. */
@Serializable
data class XtreamVodInfoBlock(
        val name: String? = null,
        @SerialName("o_name") val originalName: String? = null,
        val year: String? = null,
        val rating: String? = null,
        @SerialName("rating_5based") val rating5Based: Double? = null,
        val plot: String? = null,
        val description: String? = null,
        val overview: String? = null,
        val genre: String? = null,
        val genres: String? = null,
        val director: String? = null,
        val cast: String? = null,
        val actors: String? = null,
        val country: String? = null,
        @SerialName("releasedate") val releaseDate: String? = null,
        @SerialName("release_date") val releaseDateAlt: String? = null,
        @SerialName("imdb_id") val imdbId: String? = null,
        @SerialName("tmdb_id") val tmdbId: String? = null,
        @SerialName("tmdb_url") val tmdbUrl: String? = null,
        @SerialName("movie_image") val movieImage: String? = null,
        @SerialName("poster_path") val posterPath: String? = null,
        val cover: String? = null,
        @SerialName("cover_big") val coverBig: String? = null,
        @SerialName("backdrop_path") val backdropPath: List<String>? = null,
        @SerialName("youtube_trailer") val youtubeTrailer: String? = null,
        val trailer: String? = null,
        @SerialName("trailer_url") val trailerUrl: String? = null,
        val youtube: String? = null,
        @SerialName("yt_trailer") val ytTrailer: String? = null,
        val duration: String? = null,
        @SerialName("duration_secs") val durationSecs: Int? = null,
        val audio: String? = null,
        val video: String? = null,
        val bitrate: String? = null,
        @SerialName("mpaa_rating") val mpaaRating: String? = null,
        val age: String? = null,
)

/** Movie data block with stream info. */
@Serializable
data class XtreamMovieData(
        @SerialName("stream_id") val streamId: Int? = null,
        @SerialName("container_extension") val containerExtension: String? = null,
        val name: String? = null,
        val added: String? = null,
        @SerialName("category_id") val categoryId: String? = null,
        @SerialName("direct_source") val directSource: String? = null,
)

/** Full series info from get_series_info. */
@Serializable
data class XtreamSeriesInfo(
        val info: XtreamSeriesInfoBlock? = null,
        val seasons: List<XtreamSeasonInfo>? = null,
        val episodes: Map<String, List<XtreamEpisodeInfo>>? = null,
)

/** Series info block with metadata. */
@Serializable
data class XtreamSeriesInfoBlock(
        val name: String? = null,
        val year: String? = null,
        val rating: String? = null,
        @SerialName("rating_5based") val rating5Based: Double? = null,
        val plot: String? = null,
        val description: String? = null,
        val overview: String? = null,
        val genre: String? = null,
        val genres: String? = null,
        val director: String? = null,
        val cast: String? = null,
        val actors: String? = null,
        val country: String? = null,
        @SerialName("releasedate") val releaseDate: String? = null,
        @SerialName("imdb_id") val imdbId: String? = null,
        @SerialName("tmdb_id") val tmdbId: String? = null,
        @SerialName("poster_path") val posterPath: String? = null,
        val cover: String? = null,
        @SerialName("backdrop_path") val backdropPath: List<String>? = null,
        @SerialName("youtube_trailer") val youtubeTrailer: String? = null,
        val trailer: String? = null,
        @SerialName("episode_run_time") val episodeRunTime: String? = null,
)

/** Season info. */
@Serializable
data class XtreamSeasonInfo(
        @SerialName("season_number") val seasonNumber: Int? = null,
        val name: String? = null,
        val overview: String? = null,
        @SerialName("air_date") val airDate: String? = null,
        @SerialName("episode_count") val episodeCount: Int? = null,
        val cover: String? = null,
        @SerialName("cover_big") val coverBig: String? = null,
)

/** Episode info. */
@Serializable
data class XtreamEpisodeInfo(
        val id: Int? = null,
        // KönigTV and many panels return episode_id instead of id
        @SerialName("episode_id") val episodeId: Int? = null,
        @SerialName("episode_num") val episodeNum: Int? = null,
        val title: String? = null,
        @SerialName("container_extension") val containerExtension: String? = null,
        val info: XtreamEpisodeInfoBlock? = null,
        @SerialName("custom_sid") val customSid: String? = null,
        val added: String? = null,
        @SerialName("direct_source") val directSource: String? = null,
) {
    /** Resolved episode ID for playback URLs. Prefers episode_id (KönigTV/XUI) over id. */
    val resolvedEpisodeId: Int?
        get() = episodeId ?: id
}

/** Episode info block. */
@Serializable
data class XtreamEpisodeInfoBlock(
        val name: String? = null,
        val plot: String? = null,
        val rating: String? = null,
        val duration: String? = null,
        @SerialName("duration_secs") val durationSecs: Int? = null,
        @SerialName("releasedate") val releaseDate: String? = null,
        @SerialName("air_date") val airDate: String? = null,
        @SerialName("movie_image") val movieImage: String? = null,
        @SerialName("poster_path") val posterPath: String? = null,
        val cover: String? = null,
        val thumbnail: String? = null,
        val img: String? = null,
        @SerialName("still_path") val stillPath: String? = null,
        val video: XtreamVideoInfo? = null,
        val audio: XtreamAudioInfo? = null,
        val bitrate: Int? = null,
)

/** Video stream info. */
@Serializable
data class XtreamVideoInfo(
        val codec: String? = null,
        val width: Int? = null,
        val height: Int? = null,
        @SerialName("aspect_ratio") val aspectRatio: String? = null,
)

/** Audio stream info. */
@Serializable
data class XtreamAudioInfo(
        val codec: String? = null,
        val channels: Int? = null,
        val language: String? = null,
)

// =============================================================================
// EPG (Electronic Program Guide)
// =============================================================================

/** EPG programme from get_short_epg. */
@Serializable
data class XtreamEpgProgramme(
        val id: String? = null,
        @SerialName("epg_id") val epgId: String? = null,
        val title: String? = null,
        val lang: String? = null,
        val start: String? = null, // Epoch seconds as string
        @SerialName("start_timestamp") val startTimestamp: Long? = null,
        val end: String? = null, // Epoch seconds as string
        @SerialName("end_timestamp") val endTimestamp: Long? = null,
        @SerialName("stop_timestamp") val stopTimestamp: Long? = null,
        val description: String? = null,
        @SerialName("channel_id") val channelId: String? = null,
        @SerialName("has_archive") val hasArchive: Int? = null,
) {
    /** Resolved start timestamp */
    val startEpoch: Long?
        get() = startTimestamp ?: start?.toLongOrNull()

    /** Resolved end timestamp */
    val endEpoch: Long?
        get() = endTimestamp ?: stopTimestamp ?: end?.toLongOrNull()
}

// =============================================================================
// Search Results
// =============================================================================

/** Combined search results. */
data class XtreamSearchResults(
        val live: List<XtreamLiveStream> = emptyList(),
        val vod: List<XtreamVodStream> = emptyList(),
        val series: List<XtreamSeriesStream> = emptyList(),
) {
    val totalCount: Int
        get() = live.size + vod.size + series.size
    val isEmpty: Boolean
        get() = totalCount == 0
}

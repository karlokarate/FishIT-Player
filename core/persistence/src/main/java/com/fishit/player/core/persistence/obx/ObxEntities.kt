package com.fishit.player.core.persistence.obx

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import io.objectbox.annotation.Unique

/**
 * ObjectBox entities for FishIT Player v2.
 * Ported from v1 with v2 package structure.
 */

@Entity
data class ObxCategory(
    @Id var id: Long = 0,
    @Index var kind: String = "", // live|vod|series
    @Index var categoryId: String = "",
    var categoryName: String? = null,
)

@Entity
data class ObxLive(
    @Id var id: Long = 0,
    @Unique @Index var streamId: Int = 0,
    @Index var nameLower: String = "",
    @Index var sortTitleLower: String = "",
    var name: String = "",
    var logo: String? = null,
    var epgChannelId: String? = null,
    var tvArchive: Int? = null,
    @Index var categoryId: String? = null,
    @Index var providerKey: String? = null,
    @Index var genreKey: String? = null,
)

@Entity
data class ObxVod(
    @Id var id: Long = 0,
    @Unique @Index var vodId: Int = 0,
    @Index var nameLower: String = "",
    @Index var sortTitleLower: String = "",
    var name: String = "",
    var poster: String? = null,
    var imagesJson: String? = null,
    @Index var year: Int? = null,
    @Index var yearKey: Int? = null,
    var rating: Double? = null,
    var plot: String? = null,
    var genre: String? = null,
    var director: String? = null,
    var cast: String? = null,
    var country: String? = null,
    var releaseDate: String? = null,
    var imdbId: String? = null,
    var tmdbId: String? = null,
    var trailer: String? = null,
    var containerExt: String? = null,
    var durationSecs: Int? = null,
    @Index var categoryId: String? = null,
    @Index var providerKey: String? = null,
    @Index var genreKey: String? = null,
    @Index var importedAt: Long? = null,
    @Index var updatedAt: Long? = null,
)

@Entity
data class ObxSeries(
    @Id var id: Long = 0,
    @Unique @Index var seriesId: Int = 0,
    @Index var nameLower: String = "",
    @Index var sortTitleLower: String = "",
    var name: String = "",
    var imagesJson: String? = null,
    @Index var year: Int? = null,
    @Index var yearKey: Int? = null,
    var rating: Double? = null,
    var plot: String? = null,
    var genre: String? = null,
    var director: String? = null,
    var cast: String? = null,
    var imdbId: String? = null,
    var tmdbId: String? = null,
    var trailer: String? = null,
    var country: String? = null,
    var releaseDate: String? = null,
    @Index var categoryId: String? = null,
    @Index var providerKey: String? = null,
    @Index var genreKey: String? = null,
    @Index var importedAt: Long? = null,
    @Index var updatedAt: Long? = null,
)

@Entity
data class ObxEpisode(
    @Id var id: Long = 0,
    @Index var seriesId: Int = 0,
    @Index var season: Int = 0,
    var episodeNum: Int = 0,
    @Index var episodeId: Int = 0,
    var title: String? = null,
    var durationSecs: Int? = null,
    var rating: Double? = null,
    var plot: String? = null,
    var airDate: String? = null,
    var playExt: String? = null,
    var imageUrl: String? = null,
    // Telegram bridging for direct playback via tg://
    @Index var tgChatId: Long? = null,
    @Index var tgMessageId: Long? = null,
    @Index var tgFileId: Int? = null,
    // Enriched media meta for UI/filtering
    var mimeType: String? = null,
    var width: Int? = null,
    var height: Int? = null,
    @Index var sizeBytes: Long? = null,
    var supportsStreaming: Boolean? = null,
    @Index var language: String? = null,
)

@Entity
data class ObxEpgNowNext(
    @Id var id: Long = 0,
    @Index var streamId: Int? = null,
    @Index var channelId: String? = null,
    var nowTitle: String? = null,
    var nowStartMs: Long? = null,
    var nowEndMs: Long? = null,
    var nextTitle: String? = null,
    var nextStartMs: Long? = null,
    var nextEndMs: Long? = null,
    @Index var updatedAt: Long = 0,
)

@Entity
data class ObxProfile(
    @Id var id: Long = 0,
    var name: String = "",
    @Index var type: String = "adult", // adult | kid | guest
    var avatarPath: String? = null,
    var createdAt: Long = 0,
    var updatedAt: Long = 0,
)

@Entity
data class ObxProfilePermissions(
    @Id var id: Long = 0,
    @Index var profileId: Long = 0,
    var canOpenSettings: Boolean = true,
    var canChangeSources: Boolean = true,
    var canUseExternalPlayer: Boolean = true,
    var canEditFavorites: Boolean = true,
    var canSearch: Boolean = true,
    var canSeeResume: Boolean = true,
    var canEditWhitelist: Boolean = true,
)

@Entity
data class ObxKidContentAllow(
    @Id var id: Long = 0,
    @Index var kidProfileId: Long = 0,
    @Index var contentType: String = "vod", // live|vod|series
    @Index var contentId: Long = 0,
)

@Entity
data class ObxKidCategoryAllow(
    @Id var id: Long = 0,
    @Index var kidProfileId: Long = 0,
    @Index var contentType: String = "vod",
    @Index var categoryId: String = "",
)

@Entity
data class ObxKidContentBlock(
    @Id var id: Long = 0,
    @Index var kidProfileId: Long = 0,
    @Index var contentType: String = "vod",
    @Index var contentId: Long = 0,
)

@Entity
data class ObxScreenTimeEntry(
    @Id var id: Long = 0,
    @Index var kidProfileId: Long = 0,
    @Index var dayYyyymmdd: String = "",
    var usedMinutes: Int = 0,
    var limitMinutes: Int = 0,
)

/**
 * ObjectBox entity for Telegram media items.
 *
 * ## remoteId-First Architecture (v2)
 *
 * This entity follows the **remoteId-first design** defined in
 * `contracts/TELEGRAM_ID_ARCHITECTURE_CONTRACT.md`.
 *
 * ### Persisted IDs (stable across sessions):
 * - `chatId` – for chat lookups and history API
 * - `messageId` – for message reload and pagination
 * - `remoteId` – for video/media file (resolved via `getRemoteFile()` at runtime)
 * - `thumbRemoteId` – for thumbnail file (resolved via `getRemoteFile()` at runtime)
 * - `posterRemoteId` – for poster image (resolved via `getRemoteFile()` at runtime)
 *
 * ### NOT Persisted (volatile/redundant):
 * - `fileId` – session-local integer, becomes stale after TDLib cache changes
 * - `uniqueId` – no TDLib API to resolve back to file
 *
 * ### Runtime Resolution:
 * ```kotlin
 * val fileId = telegramClient.getRemoteFile(remoteId)?.id
 * telegramClient.downloadFile(fileId, priority)
 * ```
 *
 * @see contracts/TELEGRAM_ID_ARCHITECTURE_CONTRACT.md
 */
@Entity
data class ObxTelegramMessage(
    @Id var id: Long = 0,
    
    // === Stable Identifiers (ALWAYS persist) ===
    @Index var chatId: Long = 0,
    @Index var messageId: Long = 0,
    
    // === File References (remoteId only - resolve fileId at runtime) ===
    /** Stable remote ID for video/media file. Use getRemoteFile(remoteId) to get fileId. */
    @Index var remoteId: String? = null,
    
    /** Stable remote ID for thumbnail. Use getRemoteFile(thumbRemoteId) to get fileId. */
    var thumbRemoteId: String? = null,
    
    /** Stable remote ID for poster image. Use getRemoteFile(posterRemoteId) to get fileId. */
    var posterRemoteId: String? = null,
    
    // === Media Properties ===
    var supportsStreaming: Boolean? = null,
    var caption: String? = null,
    @Index var captionLower: String? = null,
    var date: Long? = null,
    var fileName: String? = null,
    
    // === Enriched Metadata (from TDLib) ===
    var durationSecs: Int? = null,
    var mimeType: String? = null,
    @Index var sizeBytes: Long? = null,
    var width: Int? = null,
    var height: Int? = null,
    @Index var language: String? = null,
    
    // === Movie Metadata (from structured 3-message pattern) ===
    var title: String? = null,
    var year: Int? = null,
    var genres: String? = null, // Comma-separated
    var fsk: Int? = null,
    var description: String? = null,
    
    // === Series Metadata (for episode grouping) ===
    @Index var isSeries: Boolean = false,
    @Index var seriesName: String? = null,
    var seriesNameNormalized: String? = null, // For grouping
    var seasonNumber: Int? = null,
    var episodeNumber: Int? = null,
    var episodeTitle: String? = null,
    
    // === External IDs (for canonical unification) ===
    /** TMDB ID for cross-pipeline canonical identity (e.g., "550" for Fight Club) */
    @Index var tmdbId: String? = null,
    /** IMDB ID (e.g., "tt0137523") */
    @Index var imdbId: String? = null,
)

// --- Aggregated index tables (persisted once per import; no full scans required) ---

@Entity
data class ObxIndexProvider(
    @Id var id: Long = 0,
    @Index var kind: String = "", // live|vod|series
    @Index var key: String = "",
    var count: Long = 0,
)

@Entity
data class ObxIndexYear(
    @Id var id: Long = 0,
    @Index var kind: String = "",
    @Index var key: Int = 0,
    var count: Long = 0,
)

@Entity
data class ObxIndexGenre(
    @Id var id: Long = 0,
    @Index var kind: String = "",
    @Index var key: String = "",
    var count: Long = 0,
)

@Entity
data class ObxIndexLang(
    @Id var id: Long = 0,
    @Index var kind: String = "",
    @Index var key: String = "",
    var count: Long = 0,
)

@Entity
data class ObxIndexQuality(
    @Id var id: Long = 0,
    @Index var kind: String = "",
    @Index var key: String = "",
    var count: Long = 0,
)

// =========================================================================
// Season/Episode Index Entities (Part B: Platinum Episode Handling)
// =========================================================================

/**
 * Lightweight season index for fast series detail display.
 *
 * **Purpose:**
 * - Store minimal season metadata per series
 * - Enable instant season list display without fetching full episode data
 * - Track episode count per season for UI hints
 *
 * **TTL Policy:**
 * - seasonIndex TTL = 7 days
 * - Invalidated on credential change via [lastUpdatedMs]
 *
 * **Usage:**
 * - Created when user opens a series detail (lazy load)
 * - Persisted for quick subsequent access
 * - UI can show "Season 1 (12 episodes)" immediately
 */
@Entity
data class ObxSeasonIndex(
    @Id var id: Long = 0,
    
    /** Parent series ID (Xtream series_id) */
    @Index var seriesId: Int = 0,
    
    /** Season number (1, 2, 3...) */
    @Index var seasonNumber: Int = 0,
    
    /** Number of episodes in this season (optional, for UI hints) */
    var episodeCount: Int? = null,
    
    /** Season name/title (optional) */
    var name: String? = null,
    
    /** Cover image for this season (optional) */
    var coverUrl: String? = null,
    
    /** Air date of first episode (optional) */
    var airDate: String? = null,
    
    /** Last update timestamp for TTL check */
    @Index var lastUpdatedMs: Long = System.currentTimeMillis(),
)

/**
 * Lightweight episode index for paged episode lists.
 *
 * **Purpose:**
 * - Store minimal episode metadata for series browsing
 * - Enable paged episode loading (pageSize ~30)
 * - Store playback hints for deterministic playback
 *
 * **Key Design:**
 * - `sourceKey` is the stable identifier (pipelineIdTag + episodeId)
 * - `playbackHintsJson` stores serialized playback hints (stream_id, containerExtension)
 * - Enables `EnsureEpisodePlaybackReadyUseCase` to check if enrichment is needed
 *
 * **TTL Policy:**
 * - episodeIndex TTL = 7 days
 * - playbackHints TTL = 30 days (critical for playback)
 * - Invalidated on credential change
 *
 * **Playback Flow:**
 * 1. User taps episode
 * 2. `EnsureEpisodePlaybackReadyUseCase` checks `playbackHintsJson`
 * 3. If missing/expired → fetch from API, persist, then play
 * 4. If present → play immediately
 */
@Entity
data class ObxEpisodeIndex(
    @Id var id: Long = 0,
    
    /** Parent series ID (Xtream series_id) */
    @Index var seriesId: Int = 0,
    
    /** Season number */
    @Index var seasonNumber: Int = 0,
    
    /** Episode number within season */
    @Index var episodeNumber: Int = 0,
    
    /**
     * Stable source key for lookups.
     * Format: "xtream:episode:{seriesId}:{seasonNum}:{episodeNum}"
     * Used by EnsureEpisodePlaybackReadyUseCase
     */
    @Index var sourceKey: String = "",
    
    /**
     * Xtream episode ID (stream_id from API).
     * Critical for playback URL construction.
     */
    @Index var episodeId: Int? = null,
    
    /** Episode title */
    var title: String? = null,
    
    /** Thumbnail URL */
    var thumbUrl: String? = null,
    
    /** Duration in seconds */
    var durationSecs: Int? = null,
    
    /** Plot/description (brief, for list display) */
    var plotBrief: String? = null,
    
    /** Rating (optional) */
    var rating: Double? = null,
    
    /** Air date (optional) */
    var airDate: String? = null,
    
    /**
     * JSON-serialized playback hints.
     * Contains keys like "stream_id", "container_extension", etc.
     * Enables playback without additional API calls.
     *
     * Example: {"stream_id":"12345","container_extension":"mkv"}
     */
    var playbackHintsJson: String? = null,
    
    /** Last update timestamp for episode index TTL (7 days) */
    @Index var lastUpdatedMs: Long = System.currentTimeMillis(),
    
    /** Last update timestamp for playback hints TTL (30 days) */
    @Index var playbackHintsUpdatedMs: Long = 0,
) {
    companion object {
        /** Episode index TTL: 7 days in milliseconds */
        const val INDEX_TTL_MS = 7 * 24 * 60 * 60 * 1000L
        
        /** Playback hints TTL: 30 days in milliseconds */
        const val PLAYBACK_HINTS_TTL_MS = 30 * 24 * 60 * 60 * 1000L
    }
    
    /** Check if episode index is stale (older than 7 days) */
    val isIndexStale: Boolean
        get() = System.currentTimeMillis() - lastUpdatedMs > INDEX_TTL_MS
    
    /** Check if playback hints are stale (older than 30 days) */
    val arePlaybackHintsStale: Boolean
        get() = playbackHintsUpdatedMs == 0L || 
                System.currentTimeMillis() - playbackHintsUpdatedMs > PLAYBACK_HINTS_TTL_MS
    
    /** Check if episode is ready for playback (has valid hints) */
    val isPlaybackReady: Boolean
        get() = !playbackHintsJson.isNullOrEmpty() && !arePlaybackHintsStale
}

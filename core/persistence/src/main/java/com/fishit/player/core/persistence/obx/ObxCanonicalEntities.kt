package com.fishit.player.core.persistence.obx

import com.fishit.player.core.model.ImageRef
import com.fishit.player.core.persistence.obx.converter.ImageRefConverter
import io.objectbox.annotation.Backlink
import io.objectbox.annotation.Convert
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import io.objectbox.annotation.Unique
import io.objectbox.relation.ToMany

/**
 * ObjectBox entity for canonical media identity.
 *
 * Represents a unique media work (movie or episode) that may have multiple sources across different
 * pipelines (Telegram, Xtream, IO, etc.).
 *
 * Per MEDIA_NORMALIZATION_CONTRACT.md:
 * - Each media work is stored once with a stable canonical key
 * - Multiple sources are linked via ObxMediaSourceRef
 * - Resume positions are tied to the canonical identity, not individual sources
 *
 * @property canonicalKey Stable identity key (e.g., "tmdb:12345" or "movie:inception:2010")
 * @property kind MOVIE or EPISODE
 * @property canonicalTitle Normalized title (cleaned, deterministic)
 * @property year Release year
 * @property season Season number (episodes only)
 * @property episode Episode number (episodes only)
 * @property tmdbId TMDB ID if available (primary identity)
 * @property imdbId IMDB ID if available
 * @property tvdbId TVDB ID if available
 * @property poster Best available poster ImageRef
 * @property backdrop Best available backdrop ImageRef
 * @property thumbnail Thumbnail ImageRef for list views
 * @property plot Description/plot text
 * @property rating Rating (0-10 scale)
 * @property durationMinutes Runtime in minutes
 * @property genres Comma-separated genre list
 * @property director Director name(s)
 * @property cast Comma-separated cast list
 * @property releaseDate Full release date string (YYYY-MM-DD)
 * @property createdAt First indexed timestamp
 * @property updatedAt Last update timestamp
 */
@Entity
data class ObxCanonicalMedia(
        @Id var id: Long = 0,
        /** Unique canonical identity key (e.g., "tmdb:550" or "movie:fight-club:1999") */
        @Unique @Index var canonicalKey: String = "",
        /** Media kind: "movie" or "episode" */
        @Index var kind: String = "movie",
        /** Normalized/cleaned title */
        @Index var canonicalTitle: String = "",
        /** Lowercase title for case-insensitive search */
        @Index var canonicalTitleLower: String = "",
        /** Release year */
        @Index var year: Int? = null,
        /** Season number (episodes only) */
        var season: Int? = null,
        /** Episode number (episodes only) */
        var episode: Int? = null,
        // === External IDs (for cross-referencing) ===
        /** TMDB ID - primary identity when available */
        @Index var tmdbId: String? = null,
        /** IMDB ID (e.g., "tt0137523") */
        @Index var imdbId: String? = null,
        /** TVDB ID (for series) */
        @Index var tvdbId: String? = null,
        // === Display metadata (aggregated from best source) ===
        /**
         * Best available poster ImageRef.
         * Stored as JSON via [ImageRefConverter] to preserve type information
         * (Http, TelegramThumb, LocalFile, InlineBytes).
         */
        @Convert(converter = ImageRefConverter::class, dbType = String::class)
        var poster: ImageRef? = null,
        /**
         * Best available backdrop ImageRef.
         * Stored as JSON via [ImageRefConverter].
         */
        @Convert(converter = ImageRefConverter::class, dbType = String::class)
        var backdrop: ImageRef? = null,
        /**
         * Thumbnail ImageRef for list views.
         * Stored as JSON via [ImageRefConverter].
         */
        @Convert(converter = ImageRefConverter::class, dbType = String::class)
        var thumbnail: ImageRef? = null,
        /** Plot/description text */
        var plot: String? = null,
        /** Rating (0-10 scale) */
        var rating: Double? = null,
        /** Runtime in minutes */
        var durationMinutes: Int? = null,
        /** Comma-separated genres */
        var genres: String? = null,
        /** Director name(s) */
        var director: String? = null,
        /** Comma-separated cast */
        var cast: String? = null,
        /** Release date (YYYY-MM-DD) */
        var releaseDate: String? = null,
        // === Timestamps ===
        @Index var createdAt: Long = System.currentTimeMillis(),
        @Index var updatedAt: Long = System.currentTimeMillis(),
) {
    /** Backlink to all source references */
    @Backlink(to = "canonicalMedia") lateinit var sources: ToMany<ObxMediaSourceRef>
}

/**
 * ObjectBox entity for a media source reference.
 *
 * Links a pipeline source to a canonical media work. Enables cross-pipeline unification: same movie
 * from Telegram, Xtream, and local files all link to one ObxCanonicalMedia.
 *
 * IMPORTANT: Each source represents a DIFFERENT FILE of the SAME media work:
 * - Different file sizes (different encoding, quality levels)
 * - Different durations (different cuts, frame rates, credits handling)
 * - Different formats (container, codecs, language tracks)
 * - Different quality (resolution, HDR, audio codec)
 *
 * The media is the SAME WORK (Fight Club, Breaking Bad S05E16), but each source is DIFFERENT.
 *
 * @property sourceType Pipeline identifier (TELEGRAM, XTREAM, IO, etc.)
 * @property sourceId Stable unique identifier within pipeline
 * @property sourceLabel Human-readable label for UI
 * @property qualityJson JSON-encoded MediaQuality (resolution, codec, HDR)
 * @property languagesJson JSON-encoded LanguageInfo
 * @property formatJson JSON-encoded MediaFormat
 * @property sizeBytes File size in bytes (source-specific!)
 * @property durationMs Duration in milliseconds (source-specific! crucial for cross-source resume)
 * @property priority Ordering priority for source selection
 * @property isAvailable Whether source is currently reachable
 * @property lastVerifiedAt Last time source was verified as available
 * @property addedAt When this source was linked
 */
@Entity
data class ObxMediaSourceRef(
        @Id var id: Long = 0,
        /** Pipeline type: TELEGRAM, XTREAM, IO, AUDIOBOOK, PLEX, OTHER */
        @Index var sourceType: String = "",
        /** Unique identifier within pipeline (e.g., "telegram:123:456", "xtream:vod:789") */
        @Unique @Index var sourceId: String = "",
        /** Human-readable label (e.g., "Telegram: Movie Group", "Xtream: Provider A") */
        var sourceLabel: String = "",
        // === Quality & Format (JSON for flexibility) ===
        /** JSON: {"resolution":1080,"codec":"HEVC","hdr":"HDR10"} */
        var qualityJson: String? = null,
        /** JSON: {"audioLanguages":["de","en"],"primaryAudio":"de","isDubbed":false} */
        var languagesJson: String? = null,
        /** JSON: {"container":"mkv","audioCodec":"dts","audioChannels":"5.1"} */
        var formatJson: String? = null,
        /** File size in bytes (differs per source!) */
        @Index var sizeBytes: Long? = null,
        /**
         * Duration in milliseconds (differs per source!). CRITICAL for cross-source resume
         * calculation. Different sources of the same movie can have ±5 minute differences.
         */
        var durationMs: Long? = null,
        /** Priority for selection (higher = preferred) */
        var priority: Int = 0,
        // === Availability tracking ===
        /** Whether source is currently reachable/valid */
        @Index var isAvailable: Boolean = true,
        /** Last verification timestamp */
        var lastVerifiedAt: Long? = null,
        // === Playback-specific data ===
        /** Direct playback URL/URI if applicable */
        var playbackUri: String? = null,
        /** Original poster URL from this source */
        var posterUrl: String? = null,
        // === Timestamps ===
        @Index var addedAt: Long = System.currentTimeMillis(),
) {
    /** Relation to canonical media */
    lateinit var canonicalMedia: io.objectbox.relation.ToOne<ObxCanonicalMedia>
}

/**
 * ObjectBox entity for canonical resume position.
 *
 * Stores resume position tied to canonical identity, not individual sources. When user resumes, the
 * **percentage** position applies regardless of which source is used.
 *
 * IMPORTANT: Different sources (Telegram, Xtream, IO) may have different file lengths, encodings,
 * and runtimes for the SAME movie. The canonical resume uses percentage-based positioning to enable
 * cross-source resuming.
 *
 * @property canonicalKey The canonical media key (e.g., "tmdb:550")
 * @property profileId User profile ID for multi-profile support
 * @property positionPercent Playback position as percentage (0.0 - 1.0) - PRIMARY for cross-source
 * @property positionMs Position in milliseconds from LAST source played (for same-source resume)
 * @property durationMs Duration in milliseconds from LAST source played
 * @property lastSourceType Last pipeline used for playback
 * @property lastSourceId Last source ID used for playback
 * @property lastSourceDurationMs Duration of the last source in milliseconds (for conversion)
 * @property watchedCount Number of times this media was watched to completion
 * @property isCompleted Whether the media was watched to completion (>90%)
 * @property updatedAt Last update timestamp
 */
@Entity
data class ObxCanonicalResumeMark(
        @Id var id: Long = 0,
        /** Canonical media key (matches ObxCanonicalMedia.canonicalKey) */
        @Index var canonicalKey: String = "",
        /** Profile ID for multi-profile support */
        @Index var profileId: Long = 0,
        /**
         * Position as percentage (0.0 - 1.0). PRIMARY resume indicator for cross-source playback.
         * When switching sources, this percentage is applied to the new source's duration.
         */
        var positionPercent: Float = 0f,
        /** Playback position in milliseconds from LAST source played */
        var positionMs: Long = 0,
        /** Total duration in milliseconds from LAST source played */
        var durationMs: Long = 0,
        /** Last pipeline used (TELEGRAM, XTREAM, IO, etc.) */
        var lastSourceType: String? = null,
        /** Last source ID used for playback */
        var lastSourceId: String? = null,
        /** Duration of the last source in milliseconds (for accurate cross-source conversion) */
        var lastSourceDurationMs: Long? = null,
        /** Number of complete watches */
        var watchedCount: Int = 0,
        /** Whether watched to completion (>90%) */
        @Index var isCompleted: Boolean = false,
        /** Last update timestamp */
        @Index var updatedAt: Long = System.currentTimeMillis(),
) {
    /**
     * Calculate resume position for a different source with a different duration.
     *
     * Example: User watched 1:30:00 of a 2:00:00 Xtream version (75%). New Telegram version is
     * 2:05:00 long. Resume position = 75% of 2:05:00 = 1:33:45.
     *
     * @param targetDurationMs Duration of the new source in milliseconds
     * @return Position in milliseconds to seek to in the new source
     */
    fun calculatePositionForSource(targetDurationMs: Long): Long {
        return (positionPercent * targetDurationMs).toLong()
    }

    /**
     * Check if the resume is for the same source (exact match). When same source, use positionMs
     * directly for frame-accurate resume. When different source, use percentage-based positioning.
     */
    fun isSameSource(sourceId: String): Boolean = lastSourceId == sourceId
}

/** Extension to generate stable canonical key from components. */
object CanonicalKeyGenerator {
    private const val TMDB_PREFIX = "tmdb:"
    private const val MOVIE_PREFIX = "movie:"
    private const val EPISODE_PREFIX = "episode:"

    /** Generate canonical key from TMDB ID (preferred). */
    fun fromTmdbId(tmdbId: com.fishit.player.core.model.ids.TmdbId): String = "$TMDB_PREFIX${tmdbId.value}"

    /** Generate canonical key for a movie without TMDB ID. Uses normalized title + year. */
    fun forMovie(canonicalTitle: String, year: Int?): String {
        val normalized = normalizeForKey(canonicalTitle)
        return if (year != null) {
            "$MOVIE_PREFIX$normalized:$year"
        } else {
            "$MOVIE_PREFIX$normalized"
        }
    }

    /**
     * Generate canonical key for an episode without TMDB ID. Uses normalized series title + season
     * + episode.
     */
    fun forEpisode(seriesTitle: String, season: Int, episode: Int): String {
        val normalized = normalizeForKey(seriesTitle)
        return "$EPISODE_PREFIX$normalized:S${season.toString().padStart(2, '0')}E${episode.toString().padStart(2, '0')}"
    }

    /**
     * Normalize title for use in canonical key.
     * - Lowercase
     * - Replace spaces with hyphens
     * - Remove special characters
     * - Collapse multiple hyphens
     */
    private fun normalizeForKey(title: String): String =
            title.lowercase()
                    .replace(Regex("[^a-z0-9\\s-]"), "")
                    .replace(Regex("\\s+"), "-")
                    .replace(Regex("-+"), "-")
                    .trim('-')

    /** Parse canonical key to determine kind. */
    fun kindFromKey(key: String): String =
            when {
                key.startsWith(TMDB_PREFIX) -> "movie" // May be refined by TMDB lookup
                key.startsWith(MOVIE_PREFIX) -> "movie"
                key.startsWith(EPISODE_PREFIX) -> "episode"
                else -> "movie"
            }

    /** Check if key is TMDB-based (high confidence). */
    fun isTmdbBased(key: String): Boolean = key.startsWith(TMDB_PREFIX)
}

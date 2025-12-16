package com.fishit.player.core.scenenameparser.api

/**
 * Scene name parser interface for normalizing release names.
 *
 * Parses scene-style release names (movies + series episodes) to extract:
 * - Title, year, season, episode
 * - Technical metadata (resolution, source, codecs, language, release group)
 * - TMDB IDs/URLs (string extraction only, no network lookups)
 *
 * Design:
 * - Fast, deterministic, pure string parsing
 * - Returns Unparsed instead of throwing on failure
 * - Source hint biases token cleanup (Telegram vs Xtream)
 */
interface SceneNameParser {
    /**
     * Parse a scene release name.
     *
     * @param input Scene name input with raw string and source hint
     * @return Parsed result with structured data, or Unparsed with reason
     */
    fun parse(input: SceneNameInput): SceneNameParseResult
}

/**
 * Input for scene name parsing.
 *
 * @property raw Raw release name string
 * @property sourceHint Source type hint for cleanup heuristics
 */
data class SceneNameInput(
    val raw: String,
    val sourceHint: SourceHint,
)

/**
 * Source hint for parser cleanup heuristics.
 *
 * TELEGRAM: Extra cleanup for emojis, "@channel", "t.me/...", bracket tags
 * XTREAM: Minimal cleanup, preserve structure
 */
enum class SourceHint {
    TELEGRAM,
    XTREAM,
}

/**
 * Result of scene name parsing.
 */
sealed class SceneNameParseResult {
    /**
     * Successfully parsed release name.
     */
    data class Parsed(val value: ParsedReleaseName) : SceneNameParseResult()

    /**
     * Unable to parse (malformed, insufficient data, etc.).
     */
    data class Unparsed(val reason: String) : SceneNameParseResult()
}

/**
 * Structured release name after parsing.
 *
 * @property title Cleaned media title
 * @property year Release year (movie) or air date year (episode)
 * @property season Season number (series only)
 * @property episode Episode number (series only)
 * @property episodeTitle Episode title if present
 * @property resolution Video resolution ("2160p", "1080p", "720p", "480p")
 * @property source Release source ("WEB-DL", "WEBRip", "BluRay", "HDTV")
 * @property videoCodec Video codec ("x264", "x265", "H.264", "HEVC")
 * @property audioCodec Audio codec ("AAC", "DDP5.1", "DTS", "AC3", "Atmos", "TrueHD")
 * @property language Language tag ("GERMAN", "MULTI", "ENGLISH", "DL", "KOREAN", "DUAL")
 * @property releaseGroup Release group name (e.g., "-GROUP")
 * @property tmdbId TMDB ID extracted from URL/tag
 * @property tmdbType TMDB type (MOVIE/TV) if derivable from URL
 * @property tmdbUrl Full TMDB URL if present
 */
data class ParsedReleaseName(
    val title: String,
    val year: Int? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val episodeTitle: String? = null,

    // Technical metadata
    val resolution: String? = null,
    val source: String? = null,
    val videoCodec: String? = null,
    val audioCodec: String? = null,
    val language: String? = null,
    val releaseGroup: String? = null,

    // TMDB extraction (string parsing only, no network)
    val tmdbId: Int? = null,
    val tmdbType: TmdbType? = null,
    val tmdbUrl: String? = null,
)

/**
 * TMDB content type.
 */
enum class TmdbType {
    MOVIE,
    TV,
}

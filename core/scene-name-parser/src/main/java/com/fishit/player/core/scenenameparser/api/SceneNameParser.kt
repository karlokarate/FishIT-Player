package com.fishit.player.core.scenenameparser.api

/**
 * Parser for "scene-style" release names.
 *
 * This parser extracts structured metadata from media file names and captions
 * commonly found in Telegram and Xtream sources.
 *
 * **Design Principles:**
 * - Deterministic: Same input always produces same output
 * - Fast: Linear time complexity for most inputs
 * - Pure: No side effects, no I/O, no network calls
 *
 * **Layer Boundary:**
 * Only :core:metadata-normalizer may use this parser.
 * Pipelines MUST NOT call this directly.
 */
interface SceneNameParser {
    /**
     * Parse a scene release name into structured metadata.
     *
     * @param input The raw input with source hint
     * @return Either Parsed result or Unparsed with reason
     */
    fun parse(input: SceneNameInput): SceneNameParseResult
}

/**
 * Input for scene name parsing.
 *
 * @property raw The raw file name or caption to parse
 * @property sourceHint Hint about the source (affects preprocessing)
 */
data class SceneNameInput(
    val raw: String,
    val sourceHint: SourceHint,
)

/**
 * Source hint for parser preprocessing.
 *
 * - TELEGRAM: Expects Telegram-specific noise (emojis, channel tags, etc.)
 * - XTREAM: Expects cleaner input from Xtream API
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
    data class Parsed(
        val value: ParsedReleaseName,
    ) : SceneNameParseResult()

    /**
     * Failed to parse with a reason.
     * Not an exception - just means the input doesn't match known patterns.
     */
    data class Unparsed(
        val reason: String,
    ) : SceneNameParseResult()
}

/**
 * Structured metadata extracted from a scene release name.
 *
 * All fields except title are optional as they may not be present in the input.
 *
 * @property title Extracted title (cleaned but not fully normalized)
 * @property year Release year for movies, or air year for series
 * @property season Season number (series only)
 * @property episode Episode number (series only)
 * @property episodeTitle Episode title if present
 * @property resolution Video resolution (e.g., "2160p", "1080p", "720p")
 * @property source Source type (e.g., "WEB-DL", "BluRay", "HDTV")
 * @property videoCodec Video codec (e.g., "x264", "x265", "HEVC", "H.264")
 * @property audioCodec Audio codec (e.g., "AAC", "DDP5.1", "DTS")
 * @property language Language tag (e.g., "GERMAN", "MULTI", "DL", "ENGLISH")
 * @property releaseGroup Release group name (typically after final "-")
 * @property tmdbId TMDB ID if found in raw string
 * @property tmdbType TMDB type (MOVIE/TV) if derivable from URL
 * @property tmdbUrl Full TMDB URL if present
 */
data class ParsedReleaseName(
    val title: String,
    val year: Int? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val episodeTitle: String? = null,
    val resolution: String? = null,
    val source: String? = null,
    val videoCodec: String? = null,
    val audioCodec: String? = null,
    val language: String? = null,
    val releaseGroup: String? = null,
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

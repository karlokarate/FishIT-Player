package com.fishit.player.core.metadata.parser

/**
 * Interface for parsing media filenames.
 *
 * Implementations should be deterministic: same input → same output.
 * Must not perform network calls or access external services.
 *
 * Typical usage:
 * ```kotlin
 * val parser = Re2jSceneNameParser()
 * val parsed = parser.parse("Movie.Title.2020.1080p.WEB-DL.x264-GROUP.mp4")
 * // parsed.title = "Movie Title"
 * // parsed.year = 2020
 * // parsed.quality.resolution = "1080p"
 * ```
 */
interface SceneNameParser {
    /**
     * Parse a filename into structured metadata.
     *
     * @param filename The filename to parse (with or without extension)
     * @return Parsed scene information with title, year, quality, etc.
     */
    fun parse(filename: String): ParsedSceneInfo
}

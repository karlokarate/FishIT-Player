/*
 * Copyright 2024 FishIT-Player
 * SPDX-License-Identifier: Apache-2.0
 *
 * Season and Episode extraction rules.
 * NO Kotlin Regex - uses RE2J patterns and token-based logic.
 */
package com.fishit.player.core.metadata.parser.rules

import com.google.re2j.Pattern as Re2Pattern

/**
 * Season/Episode extraction result.
 */
data class SeasonEpisodeResult(
    val season: Int? = null,
    val episode: Int? = null,
    val tokenIndex: Int = -1, // Where S/E marker was found
    val isAbsoluteEpisode: Boolean = false, // True if episode without season (anime style)
)

/**
 * Season and Episode extraction rules.
 *
 * Supports formats:
 * - Standard: S01E02, S01 E02, S1E2
 * - X-Format: 1x02, 01x02
 * - German: Folge 02
 * - Absolute episode: E02 (anime)
 * - Season-only: S01, Season 1
 * - Episode-only: EP02, Episode 02
 */
object SeasonEpisodeRules {
    // RE2J patterns for extraction
    // Note: These allow separators between S and E
    private val sxePattern = Re2Pattern.compile("(?i)S(\\d{1,2})[._ ]?E(\\d{1,4})")
    private val xFormatPattern = Re2Pattern.compile("(?i)(\\d{1,2})x(\\d{2,4})")
    private val folgePattern = Re2Pattern.compile("(?i)Folge[._ ]?(\\d{1,4})")
    private val seasonOnlyPattern = Re2Pattern.compile("(?i)(?:^|[._ ])S(\\d{1,2})(?:[._ ]|$)")
    private val episodeOnlyPattern = Re2Pattern.compile("(?i)(?:^|[._ ])E(\\d{1,4})(?:[._ ]|$)")

    // Classification patterns (cheap checks)
    private val sxeClassifyPattern = Re2Pattern.compile("(?i)S\\d{1,2}[._ ]?E\\d{1,4}")
    private val xFormatClassifyPattern = Re2Pattern.compile("(?i)\\d{1,2}x\\d{2,4}")
    private val folgeClassifyPattern = Re2Pattern.compile("(?i)Folge[._ ]?\\d{1,4}")
    private val episodeClassifyPattern = Re2Pattern.compile("(?i)(?:^|[._ ])E\\d{1,4}(?:[._ ]|$)")

    /**
     * Check if input has series markers (SxxEyy, 1x02, Folge, etc.).
     * This is a CHEAP operation - used for classification.
     */
    fun hasSeriesMarkers(input: String): Boolean =
        sxeClassifyPattern.matcher(input).find() ||
            xFormatClassifyPattern.matcher(input).find() ||
            folgeClassifyPattern.matcher(input).find() ||
            episodeClassifyPattern.matcher(input).find()

    /**
     * Extract season and episode from input.
     *
     * @param input Pre-cleaned input string
     * @return SeasonEpisodeResult with extracted values
     */
    fun extract(input: String): SeasonEpisodeResult {
        // Try SxxEyy format first (most common)
        val sxeMatcher = sxePattern.matcher(input)
        if (sxeMatcher.find()) {
            return SeasonEpisodeResult(
                season = sxeMatcher.group(1)?.toIntOrNull(),
                episode = sxeMatcher.group(2)?.toIntOrNull(),
                tokenIndex = input.substring(0, sxeMatcher.start()).count { it == ' ' || it == '.' || it == '_' },
            )
        }

        // Try 1x02 format
        val xMatcher = xFormatPattern.matcher(input)
        if (xMatcher.find()) {
            return SeasonEpisodeResult(
                season = xMatcher.group(1)?.toIntOrNull(),
                episode = xMatcher.group(2)?.toIntOrNull(),
                tokenIndex = input.substring(0, xMatcher.start()).count { it == ' ' || it == '.' || it == '_' },
            )
        }

        // Try Folge format (German)
        val folgeMatcher = folgePattern.matcher(input)
        if (folgeMatcher.find()) {
            return SeasonEpisodeResult(
                season = 1, // Folge implies season 1
                episode = folgeMatcher.group(1)?.toIntOrNull(),
                tokenIndex = input.substring(0, folgeMatcher.start()).count { it == ' ' || it == '.' || it == '_' },
            )
        }

        // Try episode-only format (anime absolute episode)
        val epMatcher = episodeOnlyPattern.matcher(input)
        if (epMatcher.find()) {
            return SeasonEpisodeResult(
                season = null,
                episode = epMatcher.group(1)?.toIntOrNull(),
                tokenIndex = input.substring(0, epMatcher.start()).count { it == ' ' || it == '.' || it == '_' },
                isAbsoluteEpisode = true,
            )
        }

        // Try season-only format
        val seasonMatcher = seasonOnlyPattern.matcher(input)
        if (seasonMatcher.find()) {
            return SeasonEpisodeResult(
                season = seasonMatcher.group(1)?.toIntOrNull(),
                episode = null,
                tokenIndex = input.substring(0, seasonMatcher.start()).count { it == ' ' || it == '.' || it == '_' },
            )
        }

        return SeasonEpisodeResult()
    }

    /**
     * Find the position where season/episode marker starts.
     * Used to determine where title ends.
     *
     * @return Start position or -1 if not found
     */
    fun findMarkerPosition(input: String): Int {
        // Try patterns in order of preference
        val patterns = listOf(sxePattern, xFormatPattern, folgePattern, episodeOnlyPattern)

        for (pattern in patterns) {
            val matcher = pattern.matcher(input)
            if (matcher.find()) {
                return matcher.start()
            }
        }

        return -1
    }
}

/*
 * Copyright 2024 FishIT-Player
 * SPDX-License-Identifier: Apache-2.0
 *
 * Two-Stage Scene Name Parser - RE2J Only Version
 *
 * Architecture:
 * 1. CLASSIFY FIRST - Determine if Series, Movie, or Unknown (cheap)
 * 2. PARSE SECOND - Run only the relevant parse path via rule packs
 *
 * Rules:
 * - For UNKNOWN (non-Xtream): classify first, then parse
 * - For Xtream: mediaType comes from endpoint, only do title cleaning
 * - Never infer mediaType from parsing for Xtream sources
 *
 * CRITICAL: NO Kotlin Regex (kotlin.text.Regex) allowed in this file.
 * Uses RE2J patterns + token-based rule packs for O(n) guaranteed safety.
 */
package com.fishit.player.core.metadata.parser

import com.fishit.player.core.metadata.parser.rules.AudioCodecRules
import com.fishit.player.core.metadata.parser.rules.EditionRules
import com.fishit.player.core.metadata.parser.rules.GroupRules
import com.fishit.player.core.metadata.parser.rules.ResolutionRules
import com.fishit.player.core.metadata.parser.rules.SceneTokenizer
import com.fishit.player.core.metadata.parser.rules.SeasonEpisodeRules
import com.fishit.player.core.metadata.parser.rules.SourceRules
import com.fishit.player.core.metadata.parser.rules.TechBoundaryDetector
import com.fishit.player.core.metadata.parser.rules.TitleSimplifierRules
import com.fishit.player.core.metadata.parser.rules.Token
import com.fishit.player.core.metadata.parser.rules.VideoCodecRules
import com.fishit.player.core.metadata.parser.rules.YearRules
import com.fishit.player.core.metadata.parser.rules.collapseWhitespace
import com.fishit.player.core.metadata.parser.rules.convertSeparatorsPreservingHyphens
import com.fishit.player.core.metadata.parser.rules.trimTrailingSeparators

/**
 * Content candidate type determined by classification step.
 */
enum class ContentCandidate {
    SERIES, // SxxEyy, 1x02, Folge detected
    MOVIE, // Valid year token detected
    UNKNOWN, // Neither series markers nor year found
}

/**
 * RE2J-based scene name parser with two-stage processing:
 * 1. Classify (series vs movie vs unknown)
 * 2. Parse (only relevant patterns via rule packs)
 *
 * All patterns are RE2J-safe with O(n) guaranteed time.
 * NO Kotlin Regex (kotlin.text.Regex) is used.
 */
class Re2jSceneNameParser : SceneNameParser {

    // =========================================================================
    // MAIN PARSE METHOD
    // =========================================================================

    override fun parse(filename: String): ParsedSceneInfo {
        if (filename.isBlank()) {
            return ParsedSceneInfo(title = "")
        }

        // Step 1: Pre-clean input (removes extension, provider prefix, etc.)
        val precleaned = TitleSimplifierRules.preclean(filename)

        // Step 2: CLASSIFY (cheap) - determine content type
        val candidate = classify(precleaned)

        // Step 3: PARSE (targeted) - only run relevant patterns
        return when (candidate) {
            ContentCandidate.SERIES -> parseAsSeries(precleaned)
            ContentCandidate.MOVIE -> parseAsMovie(precleaned)
            ContentCandidate.UNKNOWN -> parseAsUnknown(precleaned)
        }
    }

    // =========================================================================
    // STAGE 1: CLASSIFICATION (cheap, determines parse path)
    // =========================================================================

    /**
     * Classify input as Series, Movie, or Unknown.
     * This is a CHEAP operation - just check for markers, don't extract.
     */
    private fun classify(input: String): ContentCandidate {
        // Check for series markers FIRST (most specific)
        if (SeasonEpisodeRules.hasSeriesMarkers(input)) {
            return ContentCandidate.SERIES
        }

        // Check for valid year (not timestamp)
        if (YearRules.hasValidYear(input)) {
            return ContentCandidate.MOVIE
        }

        return ContentCandidate.UNKNOWN
    }

    // =========================================================================
    // STAGE 2: TARGETED PARSING
    // =========================================================================

    /**
     * Parse as Series: extract season, episode, title.
     */
    private fun parseAsSeries(input: String): ParsedSceneInfo {
        // Tokenize for rule pack processing
        val tokens = SceneTokenizer.tokenize(input)

        // Extract season/episode
        val seResult = SeasonEpisodeRules.extract(input)

        // Find where S/E marker is to extract title
        val markerPos = SeasonEpisodeRules.findMarkerPosition(input)

        // Extract title (everything before S/E marker or tech boundary)
        val titleEndPos = if (markerPos > 0) {
            markerPos
        } else {
            val techIdx = TechBoundaryDetector.findTechBoundary(tokens)
            if (techIdx < tokens.size) tokens[techIdx].startIndex else input.length
        }

        val rawTitle = if (titleEndPos > 0) input.substring(0, titleEndPos) else input
        val cleanedTitle = cleanTitle(rawTitle, input)

        // Extract quality info using rule packs
        val quality = extractQuality(tokens, input)

        // Extract edition info using rule packs
        val editionResult = EditionRules.detect(tokens)
        val edition = convertEditionResult(editionResult)

        return ParsedSceneInfo(
            title = cleanedTitle,
            year = null, // Series don't have years in the same way
            isEpisode = true,
            season = seResult.season,
            episode = seResult.episode,
            quality = quality,
            edition = edition,
        )
    }

    /**
     * Parse as Movie: extract year, title.
     */
    private fun parseAsMovie(input: String): ParsedSceneInfo {
        // Tokenize for rule pack processing
        val tokens = SceneTokenizer.tokenize(input)

        // Find tech boundary first
        val techBoundaryIdx = TechBoundaryDetector.findTechBoundary(tokens)
        val techBoundaryPos = if (techBoundaryIdx < tokens.size) {
            tokens[techBoundaryIdx].startIndex
        } else {
            input.length
        }

        // Extract year (prefer last valid year before tech boundary)
        val yearResult = YearRules.extract(input, techBoundaryPos)

        // Title is everything before year (or before tech boundary if no year)
        val titleEndPos = when {
            yearResult.position > 0 -> yearResult.position
            techBoundaryPos > 0 && techBoundaryPos < input.length -> techBoundaryPos
            else -> input.length
        }

        val rawTitle = input.substring(0, titleEndPos)
        val cleanedTitle = cleanTitle(rawTitle, input)

        // Extract quality info using rule packs
        val quality = extractQuality(tokens, input)

        // Extract edition info using rule packs
        val editionResult = EditionRules.detect(tokens)
        val edition = convertEditionResult(editionResult)

        return ParsedSceneInfo(
            title = cleanedTitle,
            year = yearResult.year,
            isEpisode = false,
            season = null,
            episode = null,
            quality = quality,
            edition = edition,
        )
    }

    /**
     * Parse as Unknown: only clean title, extract tech info.
     * Do NOT guess mediaType.
     */
    private fun parseAsUnknown(input: String): ParsedSceneInfo {
        // Tokenize for rule pack processing
        val tokens = SceneTokenizer.tokenize(input)

        // Find tech boundary
        val techBoundaryIdx = TechBoundaryDetector.findTechBoundary(tokens)
        val techBoundaryPos = if (techBoundaryIdx < tokens.size) {
            tokens[techBoundaryIdx].startIndex
        } else {
            input.length
        }

        val rawTitle = if (techBoundaryPos > 0) input.substring(0, techBoundaryPos) else input
        val cleanedTitle = cleanTitle(rawTitle, input)

        // Extract quality info (for display purposes)
        val quality = extractQuality(tokens, input)

        return ParsedSceneInfo(
            title = cleanedTitle,
            year = null,
            isEpisode = false,
            season = null,
            episode = null,
            quality = quality,
            edition = EditionInfo(),
        )
    }

    // =========================================================================
    // HELPER METHODS (No Kotlin Regex - uses linear code)
    // =========================================================================

    /**
     * Clean a title using linear algorithms (no Kotlin Regex).
     *
     * Steps:
     * 1. Remove anime subgroup prefix [SubGroup]
     * 2. Convert separators (dots/underscores to spaces, preserve hyphens)
     * 3. Collapse whitespace
     * 4. Trim trailing separators
     */
    private fun cleanTitle(rawTitle: String, fullInput: String): String {
        var title = rawTitle.trim()

        // Remove anime subgroup prefix
        title = TitleSimplifierRules.removeAnimeSubgroup(title)

        // Remove release group suffix (e.g., "-GROUP")
        title = removeGroupSuffix(title)

        // Convert separators (preserve hyphens between letters)
        title = convertSeparatorsPreservingHyphens(title)

        // Collapse multiple spaces
        title = collapseWhitespace(title)

        // Trim trailing separators
        title = trimTrailingSeparators(title)

        return title.ifBlank { "Unknown" }
    }

    /**
     * Remove release group suffix (e.g., "-GROUP" at end).
     * Linear scan, no regex.
     */
    private fun removeGroupSuffix(input: String): String {
        // Look for pattern: separator followed by hyphen and alphanumeric group
        // e.g., "Movie.Title.-GROUP" or "Movie Title -GROUP"
        val lastHyphen = input.lastIndexOf('-')
        if (lastHyphen < 0 || lastHyphen >= input.length - 2) return input

        // Check if there's a separator before the hyphen
        if (lastHyphen > 0) {
            val charBefore = input[lastHyphen - 1]
            if (charBefore == '.' || charBefore == '_' || charBefore == ' ') {
                // Check if suffix is valid group (2-12 alphanumeric chars)
                val suffix = input.substring(lastHyphen + 1)
                if (suffix.length in 2..12 && suffix.all { it.isLetterOrDigit() }) {
                    // Verify it's not a technical tag
                    val tokens = SceneTokenizer.tokenize(suffix)
                    if (tokens.isNotEmpty() && !TechBoundaryDetector.isTechToken(tokens[0])) {
                        return input.substring(0, lastHyphen - 1)
                    }
                }
            }
        }

        return input
    }

    /**
     * Extract quality information using rule packs.
     */
    private fun extractQuality(tokens: List<Token>, rawInput: String): QualityInfo {
        val resResult = ResolutionRules.detect(tokens)
        val srcResult = SourceRules.detect(tokens)
        val codecResult = VideoCodecRules.detect(tokens)
        val audio = AudioCodecRules.detect(tokens)
        val groupResult = GroupRules.detect(tokens, rawInput)

        return QualityInfo(
            resolution = resResult.resolution,
            source = srcResult.source,
            codec = codecResult.codec,
            audio = audio,
            hdr = null, // HDR is in EditionRules
            group = groupResult.group,
        )
    }

    /**
     * Convert EditionResult to EditionInfo.
     */
    private fun convertEditionResult(result: com.fishit.player.core.metadata.parser.rules.EditionResult): EditionInfo {
        return EditionInfo(
            extended = result.extended,
            directors = result.directors,
            unrated = result.unrated,
            theatrical = result.theatrical,
            threeD = result.threeD,
            imax = result.imax,
            remastered = result.remastered,
            proper = result.proper,
            repack = result.repack,
        )
    }
}

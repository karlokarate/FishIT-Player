/*
 * Copyright 2024 FishIT-Player
 * SPDX-License-Identifier: Apache-2.0
 *
 * Xtream-specific format rules.
 *
 * Handles provider-specific naming patterns:
 * - Pipe-separated: "Title | Year | Rating | Quality" (and provider variants)
 * - Country-prefix: "CC | Title | Year | Rating"
 * - Live channel cleanup (Unicode decorators)
 *
 * Based on real Xtream API data analysis:
 * - 21.2% of VOD uses pipe-separated format
 * - 55.7% uses parentheses format "Title (Year)"
 * - Live streams contain Unicode block characters (▃ ▅ ▆ █)
 *
 * The pipe-format parser uses position-independent segment classification
 * to handle varying provider formats (not all providers use the same order).
 *
 * NO Kotlin Regex - uses RE2J patterns + linear algorithms.
 */
package com.fishit.player.core.metadata.parser.rules

import com.google.re2j.Pattern as Re2Pattern

/** Result from Xtream pipe-format parsing. */
data class XtreamPipeResult(
    val title: String,
    val year: Int?,
    val rating: Double?,
    val quality: String?,
    val matched: Boolean,
)

/**
 * Xtream-specific format rules for provider naming patterns.
 *
 * These patterns are NOT scene-release patterns but provider-specific formatting used by Xtream
 * Codes panels.
 */
object XtreamFormatRules {
    // =========================================================================
    // PIPE-SEPARATED FORMAT
    // =========================================================================

    /**
     * Valid year range for pipe-format detection and parsing.
     *
     * Narrowed to 1960–2030 to avoid misclassifying movie titles that ARE year numbers:
     * - "1917" (2019 war film)     → falls inside range, but protected by first-segment rule
     * - "1923" (TV series)         → falls inside range, but protected by first-segment rule
     * - "2046" (2004 Wong Kar-wai) → falls OUTSIDE range → treated as title ✅
     * - "1776" (1972 musical)      → falls OUTSIDE range → treated as title ✅
     * - "2001" (Kubrick)           → falls inside range, but protected by first-segment rule
     *
     * Combined with the first-segment-is-always-title rule, this gives robust handling.
     */
    private val YEAR_RANGE = 1960..2030

    /**
     * Detect if input uses Xtream pipe-separated format.
     *
     * Handles multiple provider variants:
     * - Standard:       "Title | Year | Rating"
     * - With quality:   "Title | Year | 4K |"
     * - Country prefix: "NL | Title | Year | Rating"
     * - Year-as-title:  "1992 | 2024 | 6.6"   (first segment is title, not year)
     *
     * The key heuristic: at least one pipe segment at position ≥ 1 must be a valid year.
     * The first segment is ALWAYS considered title text (never year) to handle titles
     * that are year numbers (e.g. "1917", "1923", "2001").
     */
    fun isPipeFormat(input: String): Boolean {
        if (!input.contains('|')) return false

        val parts = input.split('|')
        if (parts.size < 2) return false

        // Check segments 1..min(3, size) for a valid year.
        // Segment 0 is always title — never checked as year.
        val limit = minOf(4, parts.size)
        for (i in 1 until limit) {
            val seg = parts[i].trim()
            if (seg.length == 4 && seg.all { it.isDigit() }) {
                val y = seg.toIntOrNull()
                if (y != null && y in YEAR_RANGE) return true
            }
        }
        return false
    }

    /**
     * Parse Xtream pipe-separated format.
     *
     * Uses a **position-aware + content-classified** approach:
     * - Segment 0 is ALWAYS treated as title (even if it looks like a year, e.g. "1992")
     * - Segments 1+ are classified by content: year, rating, quality tag, or extra title text
     *
     * This handles all known provider variants:
     *
     * - "Title | Year | Rating"             (standard)
     * - "Title | Year | Rating | Quality"   (with quality tag)
     * - "Title | Year | Quality |"          (quality instead of rating)
     * - "CC | Title | Year | Rating"        (country-code prefix, CC becomes part of title)
     * - "Title | Rating | Year"             (reversed order)
     * - "1992 | 2024 | 6.6"                (year-as-title: "1992" is the movie title)
     * - "2012 | 2009 | 5.8"                (year-as-title: "2012" is the movie title)
     *
     * Year range: [YEAR_RANGE] to avoid misclassifying far-future/far-past titles (e.g. "2046").
     * Rating range: (0.0, 10.0] to avoid misclassifying other numeric fields.
     *
     * @return XtreamPipeResult with extracted fields
     */
    fun parsePipeFormat(input: String): XtreamPipeResult {
        if (!input.contains('|')) {
            return XtreamPipeResult(
                title = input,
                year = null,
                rating = null,
                quality = null,
                matched = false,
            )
        }

        val parts = input.split('|').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) {
            return XtreamPipeResult(title = input, year = null, rating = null, quality = null, matched = false)
        }

        // Segment 0 is ALWAYS title — even "1992", "2012", "2001" are movie titles
        val titleParts = mutableListOf(parts[0])

        // Classify segments 1+ by content
        var year: Int? = null
        var rating: Double? = null
        var quality: String? = null

        for (i in 1 until parts.size) {
            val part = parts[i]
            val upper = part.uppercase()

            when {
                // Quality tag (4K, HEVC, LOWQ, BACKUP, etc.)
                upper in QUALITY_TAGS -> {
                    if (quality == null) quality = upper
                }
                // Year: exactly 4 digits in YEAR_RANGE (take first match)
                year == null && part.length == 4 && part.all { it.isDigit() } -> {
                    val y = part.toIntOrNull()
                    if (y != null && y in YEAR_RANGE) {
                        year = y
                    } else {
                        titleParts.add(part)
                    }
                }
                // Rating: decimal number in (0.0, 10.0] (take first match)
                rating == null && part.contains('.') &&
                    part.toDoubleOrNull()?.let { it > 0.0 && it <= 10.0 } == true -> {
                    rating = part.toDoubleOrNull()
                }
                // Everything else is extra title text (or unrecognized tag)
                else -> titleParts.add(part)
            }
        }

        // Title: first segment + any unclassified segments joined by " | "
        val title = if (titleParts.size == 1) {
            titleParts[0]
        } else {
            titleParts.joinToString(" | ")
        }

        return XtreamPipeResult(
            title = title,
            year = year,
            rating = rating,
            quality = quality,
            matched = year != null,
        )
    }

    // Known quality tags in Xtream pipe format
    private val QUALITY_TAGS =
        setOf(
            "4K",
            "UHD",
            "2160P",
            "FHD",
            "1080P",
            "HD",
            "720P",
            "SD",
            "480P",
            "LOWQ",
            "LOW",
            "HEVC",
            "H265",
            "H264",
            "HDR",
            "SDR",
            "DV",
            "BACKUP",
        )

    // =========================================================================
    // LIVE CHANNEL NAME CLEANUP
    // =========================================================================

    // Unicode block characters used as decorators in live channel names
    private val unicodeDecoratorsPattern = Re2Pattern.compile("[▃▅▆█▇▄▂░▒▓■□●○◆◇★☆⬛⬜]+")

    // Common prefixes for live channels (country codes)
    private val liveChannelPrefixPattern = Re2Pattern.compile("^([A-Z]{2}):?\\s*")

    /**
     * Clean live channel name by removing decorators.
     *
     * Examples:
     * - "▃ ▅ ▆ █ DE HEVC █ ▆ ▅ ▃" → "DE HEVC"
     * - "DE: RTL HD" → "DE: RTL HD" (no change needed)
     */
    fun cleanLiveChannelName(input: String): String {
        // Remove Unicode block decorators
        var result = unicodeDecoratorsPattern.matcher(input).replaceAll(" ")

        // Collapse multiple spaces
        result = collapseWhitespace(result)

        return result.trim()
    }

    /**
     * Extract country code prefix from live channel name.
     *
     * @return Pair of (countryCode, remainingName) or (null, originalName)
     */
    fun extractLiveChannelCountry(input: String): Pair<String?, String> {
        val matcher = liveChannelPrefixPattern.matcher(input)
        return if (matcher.find()) {
            val country = matcher.group(1)
            val remaining = input.substring(matcher.end()).trim()
            country to remaining
        } else {
            null to input
        }
    }

    // =========================================================================
    // PARENTHESES FORMAT (most common - 55.7%)
    // =========================================================================

    // Pattern: "Title (Year)" - parentheses at end
    private val parenYearPattern = Re2Pattern.compile("^(.+?)\\s*\\((\\d{4})\\)\\s*$")

    /**
     * Detect if input uses parentheses year format.
     *
     * Pattern: "Title (Year)" Examples:
     * - "Evil Dead Rise (2023)"
     * - "Asterix & Obelix im Reich der Mitte (2023)"
     */
    fun isParenthesesFormat(input: String): Boolean = parenYearPattern.matcher(input).matches()

    /**
     * Parse parentheses year format.
     *
     * @return Pair of (title, year) or (input, null) if no match
     */
    fun parseParenthesesFormat(input: String): Pair<String, Int?> {
        val matcher = parenYearPattern.matcher(input)
        return if (matcher.matches()) {
            val title = matcher.group(1).trim()
            val year = matcher.group(2).toIntOrNull()
            title to year
        } else {
            input to null
        }
    }
}

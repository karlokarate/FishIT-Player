/*
 * Copyright 2024 FishIT-Player
 * SPDX-License-Identifier: Apache-2.0
 *
 * Xtream-specific format rules.
 *
 * Handles provider-specific naming patterns:
 * - Pipe-separated: "Title | Year | Rating | Quality" (and provider variants)
 * - Reversed order: "Title | Rating | Year" (some providers swap rating/year)
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
     * - "2046" (2004 Wong Kar-wai) → falls OUTSIDE range → treated as title ✅
     * - "1776" (1972 musical)      → falls OUTSIDE range → treated as title ✅
     * - "1917", "1923", "2001"     → fall inside range, but protected by title-segment rule
     *
     * Combined with the title-segment rule (see [parsePipeFormat]), this gives robust handling.
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

        // limit = 5 covers all known formats (prefix + title + year + rating + tag)
        val parts = input.split('|', limit = 5)
        if (parts.size < 2) return false

        // Check segments 1+ for a valid year.
        // Segment 0 is always title (or prefix) — never checked as year for detection.
        for (i in 1 until parts.size) {
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
     * Uses a **prefix-aware + content-classified** approach:
     * - Detects country-code prefix: if segment 0 is a 2–3 char uppercase alpha string
     *   (e.g. "NL", "DE", "UK"), it is stripped and segment 1 becomes the title.
     *   Verified from real data: 2,013 of 43,537 items use "NL | Title | Year | Rating".
     * - The title segment is ALWAYS the first non-prefix segment (even "1992" or "2012")
     * - Remaining segments are classified by content: year, rating, quality tag, or extra title
     *
     * This handles all known provider variants:
     *
     * - "Title | Year | Rating"             (standard — 7,031 items)
     * - "Title | Year | Rating | Quality"   (with quality tag — 200 items)
     * - "NL | Title | Year | Rating"        (country-code prefix — 2,013 items)
     * - "Title | Rating | Year"             (reversed order — rare)
     * - "1992 | 2024 | 6.6"                (year-as-title: "1992" is the movie title)
     *
     * Year range: [YEAR_RANGE] to avoid misclassifying far-future/far-past titles.
     * Rating: requires decimal point (e.g. "7.4") in range (0.0, 10.0].
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

        // Detect country-code prefix: 2–3 char uppercase alpha string at segment 0.
        // Real data: "NL | Title | Year | Rating" — 2,013 items with NL prefix.
        val hasPrefix = parts.size >= 3 && isCountryPrefix(parts[0])
        val titleIndex = if (hasPrefix) 1 else 0

        // Title segment is ALWAYS at titleIndex — even "1992", "2012", "2001" are movie titles
        val titleParts = mutableListOf(parts[titleIndex])

        // Classify segments after title by content
        var year: Int? = null
        var rating: Double? = null
        var quality: String? = null

        for (i in (titleIndex + 1) until parts.size) {
            val part = parts[i]
            val upper = part.uppercase()

            when {
                // Quality tag (4K, HEVC, LOWQ, +18, UNTERTITEL, IMAX, etc.)
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
                // Rating: decimal number in (0.0, 10.0] (take first match).
                // Requires '.' to distinguish from integer tags or counters.
                // Verified: no real Xtream data uses integer-only ratings in pipe format.
                rating == null && part.contains('.') -> {
                    val parsed = part.toDoubleOrNull()
                    if (parsed != null && parsed > 0.0 && parsed <= 10.0) {
                        rating = parsed
                    } else {
                        titleParts.add(part)
                    }
                }
                // Everything else is extra title text (or unrecognized tag)
                else -> titleParts.add(part)
            }
        }

        // Title: title segment + any unclassified segments joined by " | "
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

    /**
     * Detect if a segment is a country-code prefix (e.g. "NL", "DE", "UK").
     *
     * A prefix is a 2–3 character uppercase alphabetic string.
     * Real data shows only "NL" (2,013 items), but the rule is generic for other providers.
     */
    private fun isCountryPrefix(segment: String): Boolean =
        segment.length in 2..3 && segment.all { it.isUpperCase() && it.isLetter() }

    // Known quality/tag markers in Xtream pipe format (from real data analysis)
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
            "IMAX",
            "+18",
            "UNTERTITEL",
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

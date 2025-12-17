/*
 * Copyright 2024 FishIT-Player
 * SPDX-License-Identifier: Apache-2.0
 *
 * Xtream-specific format rules.
 *
 * Handles provider-specific naming patterns:
 * - Pipe-separated: "Title | Year | Rating | Quality"
 * - Live channel cleanup (Unicode decorators)
 *
 * Based on real Xtream API data analysis:
 * - 21.2% of VOD uses pipe-separated format
 * - 55.7% uses parentheses format "Title (Year)"
 * - Live streams contain Unicode block characters (▃ ▅ ▆ █)
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
     * Detect if input uses Xtream pipe-separated format.
     *
     * Pattern: "Title | Year | ..." or "Title | Year |" Examples:
     * - "Sisu: Road to Revenge | 2025 | 7.4"
     * - "John Wick: Kapitel 4 | 2023 | 4K |"
     * - "Silent Night | 2025 | 5.3 | LOWQ"
     */
    fun isPipeFormat(input: String): Boolean {
        // Must have at least one pipe
        if (!input.contains('|')) return false

        // Must have year after first pipe
        val parts = input.split('|')
        if (parts.size < 2) return false

        // Second part should be a valid year (1900-2099)
        val yearPart = parts[1].trim()
        return yearPart.length == 4 &&
                yearPart.all { it.isDigit() } &&
                yearPart.toIntOrNull()?.let { it in 1900..2099 } == true
    }

    /**
     * Parse Xtream pipe-separated format.
     *
     * Format: "Title | Year | Rating | Quality"
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

        val parts = input.split('|').map { it.trim() }

        // Part 0: Title
        val title = parts.getOrNull(0)?.trim() ?: input

        // Part 1: Year
        val yearStr = parts.getOrNull(1)?.trim()
        val year = yearStr?.toIntOrNull()?.takeIf { it in 1900..2099 }

        // Part 2: Rating (decimal number) or Quality tag
        val part2 = parts.getOrNull(2)?.trim()?.uppercase()
        val rating: Double?
        var quality: String? = null

        if (part2 != null) {
            // Check if it's a quality tag
            if (part2 in QUALITY_TAGS) {
                rating = null
                quality = part2
            } else {
                // Try to parse as rating
                rating = parts.getOrNull(2)?.trim()?.toDoubleOrNull()
            }
        } else {
            rating = null
        }

        // Part 3+: Quality tags (4K, LOWQ, HD, HEVC, etc.)
        if (quality == null && parts.size > 3) {
            for (i in 3 until parts.size) {
                val tag = parts[i].trim().uppercase()
                if (tag in QUALITY_TAGS) {
                    quality = tag
                    break
                }
            }
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
    fun isParenthesesFormat(input: String): Boolean {
        return parenYearPattern.matcher(input).matches()
    }

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

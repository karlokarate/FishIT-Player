/*
 * Copyright 2024 FishIT-Player
 * SPDX-License-Identifier: Apache-2.0
 *
 * Year extraction rules.
 * NO Kotlin Regex - uses RE2J patterns and linear validation.
 */
package com.fishit.player.core.metadata.parser.rules

import com.google.re2j.Pattern as Re2Pattern

/**
 * Year extraction result.
 */
data class YearResult(
    val year: Int? = null,
    val position: Int = -1, // Start position of year in input
)

/**
 * Year extraction rules.
 *
 * Extracts movie years (1900-2099) while avoiding:
 * - Timestamps (20231205)
 * - Resolutions (1080, 720)
 * - Version numbers
 */
object YearRules {

    // Year pattern with word boundaries
    private val yearPattern = Re2Pattern.compile("(?:^|[._ \\-\\(\\[])((19|20)\\d{2})(?:[._ \\-\\)\\]]|$)")

    // Timestamp pattern (8 digits: YYYYMMDD)
    private val timestampPattern = Re2Pattern.compile("(?:19|20)\\d{6}")

    // Valid year range
    private const val MIN_YEAR = 1900
    private const val MAX_YEAR = 2099

    /**
     * Check if input has a valid year.
     * This is a CHEAP classification operation.
     */
    fun hasValidYear(input: String): Boolean {
        // First check if there's a timestamp pattern - if so, skip
        if (timestampPattern.matcher(input).find()) {
            return false
        }

        val matcher = yearPattern.matcher(input)
        while (matcher.find()) {
            val yearStr = extractDigitsFromMatch(matcher.group())
            if (yearStr.length == 4) {
                val year = yearStr.toIntOrNull()
                if (year != null && year in MIN_YEAR..MAX_YEAR) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Extract year from input.
     * Prefers the LAST valid year before technical tags.
     *
     * @param input Pre-cleaned input string
     * @param techBoundary Position where tech tags start (or input.length)
     * @return YearResult with extracted year
     */
    fun extract(input: String, techBoundary: Int = input.length): YearResult {
        // Skip if timestamp found
        if (timestampPattern.matcher(input).find()) {
            return YearResult()
        }

        var lastYear: Int? = null
        var lastPosition = -1

        val matcher = yearPattern.matcher(input)
        while (matcher.find()) {
            val pos = matcher.start()

            // Only consider years before tech boundary
            if (pos < techBoundary) {
                val yearStr = matcher.group(1)
                val year = yearStr?.toIntOrNull()

                if (year != null && year in MIN_YEAR..MAX_YEAR) {
                    // Validate it's not a resolution
                    if (!isResolutionLike(year)) {
                        lastYear = year
                        lastPosition = pos
                    }
                }
            }
        }

        return YearResult(year = lastYear, position = lastPosition)
    }

    /**
     * Find position of year in input.
     * Used to determine where title ends.
     */
    fun findYearPosition(input: String): Int {
        val result = extract(input)
        return result.position
    }

    /**
     * Check if a number could be a resolution (not a year).
     */
    private fun isResolutionLike(value: Int): Boolean {
        // Common resolution-like numbers
        return value in setOf(1080, 720, 480, 576, 2160, 4320)
    }

    /**
     * Extract only digits from a match string.
     * Linear time, no regex.
     */
    private fun extractDigitsFromMatch(match: String): String {
        val sb = StringBuilder(4)
        for (c in match) {
            if (c.isDigit()) {
                sb.append(c)
            }
        }
        return sb.toString()
    }
}

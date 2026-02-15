package com.fishit.player.core.model.util

/**
 * SSOT for year validation and extraction.
 *
 * Centralizes year-related logic previously duplicated across pipeline mappers:
 * - Validation: filter "0", "N/A", empty; range-check 1900–2100
 * - Extraction from date strings (e.g., "2014-09-21" → 2014)
 * - Extraction from titles (for normalizer, NOT pipeline — per pipeline contract)
 *
 * **Pipeline Usage:** Only [validate] and [fromDate] should be called from pipelines.
 * Title-based extraction is normalizer territory per MEDIA_NORMALIZATION_CONTRACT.
 */
object YearParser {
    private val VALID_RANGE = 1900..2100

    /**
     * Validate a year string from API, filtering common invalid values.
     *
     * Filters: empty/blank, "0", "N/A", out-of-range.
     *
     * @param yearStr Raw year string from API
     * @return Validated year as Int, or null if invalid
     */
    fun validate(yearStr: String?): Int? =
        yearStr
            ?.takeIf { it.isNotBlank() && it != "0" && it != "N/A" }
            ?.toIntOrNull()
            ?.takeIf { it in VALID_RANGE }

    /**
     * Extract year from an ISO-style date string.
     *
     * Examples:
     * - "2014-09-21" → 2014
     * - "2023" → 2023
     * - "abc" → null
     *
     * @param dateStr Date string (first 4 chars extracted)
     * @return Year as Int, or null if not extractable
     */
    fun fromDate(dateStr: String?): Int? = dateStr?.take(4)?.toIntOrNull()?.takeIf { it in VALID_RANGE }

    // =========================================================================
    // Title-based extraction — for :core:metadata-normalizer use ONLY.
    // Per pipeline.instructions.md: extractYearFromTitle is FORBIDDEN in pipeline.
    // =========================================================================

    /**
     * Extract year from pipe-delimited VOD title.
     *
     * Many Xtream providers format VOD titles as: "Title | Year | Rating"
     * Examples:
     * - "Ella McCay | 2025 | 5.2" → 2025
     * - "The Killer | 2024 | 6.4 |" → 2024
     *
     * **Not for pipeline use** — normalizer territory.
     *
     * @param title The VOD title to parse
     * @return Extracted year or null
     */
    fun extractFromVodTitle(title: String): Int? {
        val parts = title.split("|").map { it.trim() }
        if (parts.size >= 2) {
            val potentialYear = parts[1].toIntOrNull()
            if (potentialYear != null && potentialYear in VALID_RANGE) {
                return potentialYear
            }
        }
        return extractFromSeriesTitle(title)
    }

    /**
     * Extract year from series-style title.
     *
     * Patterns handled:
     * - "Show Name (2023)" → 2023
     * - "Show Name [2023]" → 2023
     * - "Show Name 2023" → 2023
     *
     * **Not for pipeline use** — normalizer territory.
     *
     * @param title The series title to parse
     * @return Extracted year or null
     */
    fun extractFromSeriesTitle(title: String): Int? {
        // Pattern 1: Year in parentheses: "Show Name (2023)"
        val parenPattern = """\((\d{4})\)""".toRegex()
        parenPattern.findAll(title).lastOrNull()?.let { match ->
            val year = match.groupValues[1].toInt()
            if (year in VALID_RANGE) return year
        }

        // Pattern 2: Year in brackets: "Show Name [2023]"
        val bracketPattern = """\[(\d{4})\]""".toRegex()
        bracketPattern.findAll(title).lastOrNull()?.let { match ->
            val year = match.groupValues[1].toInt()
            if (year in VALID_RANGE) return year
        }

        // Pattern 3: Standalone year at end: "Show Name 2023"
        val standalone = """\b(\d{4})$""".toRegex()
        standalone.find(title)?.let { match ->
            val year = match.groupValues[1].toInt()
            if (year in VALID_RANGE) return year
        }

        return null
    }
}

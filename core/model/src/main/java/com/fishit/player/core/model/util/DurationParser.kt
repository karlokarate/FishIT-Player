package com.fishit.player.core.model.util

/**
 * SSOT for duration parsing and time-unit conversion.
 *
 * Centralizes all duration string parsing previously scattered across pipeline mappers.
 * Used by Xtream pipeline (and potentially others) to convert API-provided duration strings
 * to milliseconds.
 *
 * Handles various Xtream API duration formats:
 * - "01:30:00" → 5_400_000 (HH:MM:SS)
 * - "90:00"    → 5_400_000 (MM:SS)
 * - "90"       → 5_400_000 (minutes as number)
 * - "90 min"   → 5_400_000 (with unit suffix)
 * - null/empty → null
 */
object DurationParser {
    /**
     * Parse a duration string to milliseconds.
     *
     * @param duration Raw duration string from API (various formats)
     * @return Duration in milliseconds, or null if unparseable
     */
    fun parseToMs(duration: String?): Long? {
        if (duration.isNullOrBlank()) return null
        val cleaned = duration.trim().lowercase()

        // Try HH:MM:SS or MM:SS format
        val parts = cleaned.split(":")
        if (parts.size >= 2) {
            return try {
                when (parts.size) {
                    3 -> { // HH:MM:SS
                        val hours = parts[0].toInt()
                        val minutes = parts[1].toInt()
                        val seconds = parts[2].toInt()
                        (hours * 3600L + minutes * 60L + seconds) * 1000L
                    }
                    2 -> { // MM:SS
                        val minutes = parts[0].toInt()
                        val seconds = parts[1].toInt()
                        (minutes * 60L + seconds) * 1000L
                    }
                    else -> null
                }
            } catch (_: NumberFormatException) {
                null
            }
        }

        // Try plain number (minutes) or "90 min" format
        val numberMatch = Regex("^(\\d+)").find(cleaned)
        if (numberMatch != null) {
            val minutes = numberMatch.groupValues[1].toLongOrNull() ?: return null
            return minutes * 60L * 1000L
        }

        return null
    }

    /** Convert seconds to milliseconds. */
    fun secondsToMs(seconds: Long): Long = seconds * 1000L

    /** Convert minutes to milliseconds. */
    fun minutesToMs(minutes: Int): Long = minutes * 60L * 1000L

    /**
     * Resolve duration from multiple API sources.
     *
     * Xtream episodes provide both `durationSecs` (numeric, more accurate) and
     * `duration` (string, various formats). Prefer the numeric value.
     *
     * @param durationSecs Duration in seconds from API (preferred)
     * @param durationStr Duration string from API (fallback)
     * @return Duration in milliseconds, or null if neither is available
     */
    fun resolve(
        durationSecs: Long?,
        durationStr: String?,
    ): Long? =
        durationSecs?.takeIf { it > 0 }?.let { secondsToMs(it) }
            ?: parseToMs(durationStr)
}

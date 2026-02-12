package com.fishit.player.core.model.util

/**
 * SSOT for Unix epoch timestamp conversion.
 *
 * Xtream API returns timestamps as Unix epoch SECONDS (string or numeric).
 * All internal timestamps use milliseconds. This utility centralizes the
 * conversion, replacing scattered `* 1000L` expressions across pipeline files.
 */
object EpochConverter {

    /**
     * Convert Unix epoch seconds (as String) to milliseconds.
     *
     * @param epochSecondsStr Epoch seconds as string from API (e.g., "1706745600")
     * @return Epoch milliseconds, or null if unparseable
     */
    fun secondsToMs(epochSecondsStr: String?): Long? =
        epochSecondsStr?.toLongOrNull()?.let { it * 1000L }

    /**
     * Convert Unix epoch seconds (Long) to milliseconds.
     *
     * @param epochSeconds Epoch seconds as numeric value
     * @return Epoch milliseconds
     */
    fun secondsToMs(epochSeconds: Long): Long = epochSeconds * 1000L
}

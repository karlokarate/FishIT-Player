package com.fishit.player.core.epg.model

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * NormalizedEpgEvent – Canonical EPG programme representation.
 *
 * Per EPG_SYSTEM_CONTRACT_V1.md (EPG-31, EPG-32):
 * - All fields are normalized (Base64 decoded, UTC timestamps)
 * - `epgKey` is a stable hash for idempotent upsert
 * - `dayBucketUtc` enables bucketed storage (EPG-50)
 *
 * This is the domain model consumed by UI and stored in persistence.
 * Raw EPG DTOs (from transport) are converted to this format by the normalizer.
 *
 * @param channelId Stable canonical channel identifier
 * @param startUtc Programme start time (UTC)
 * @param endUtc Programme end time (UTC)
 * @param title Programme title (Base64 decoded)
 * @param description Programme description (Base64 decoded, optional)
 * @param language Language code (normalized, lowercase, optional)
 * @param hasCatchup Whether catchup/archive is available
 * @param isNowPlaying Whether this is the current programme (snapshot)
 * @param source EPG data source
 * @param sourceEventId Raw event ID from source (not stable)
 * @param sourceEpgId Raw EPG ID from source (not stable)
 * @param epgKey Stable hash key for idempotent upsert
 */
data class NormalizedEpgEvent(
    val channelId: CanonicalChannelId,
    val startUtc: Instant,
    val endUtc: Instant,
    val title: String,
    val description: String? = null,
    val language: String? = null,
    val hasCatchup: Boolean = false,
    val isNowPlaying: Boolean = false,
    val source: EpgSource = EpgSource.XTREAM,
    val sourceEventId: String? = null,
    val sourceEpgId: String? = null,
    val epgKey: String,
) {
    init {
        require(endUtc > startUtc) {
            "endUtc ($endUtc) must be > startUtc ($startUtc)"
        }
        require(title.isNotBlank()) { "title must not be blank" }
        require(epgKey.isNotBlank()) { "epgKey must not be blank" }
    }

    /**
     * Day bucket for storage partitioning (UTC 00:00 boundary).
     * Per EPG_SYSTEM_CONTRACT_V1.md (EPG-50).
     */
    val dayBucketUtc: String
        get() = DAY_FORMATTER.format(startUtc)

    /**
     * Duration in minutes.
     */
    val durationMinutes: Int
        get() = ((endUtc.epochSecond - startUtc.epochSecond) / 60).toInt()

    /**
     * Check if event is currently active at given instant.
     */
    fun isActiveAt(instant: Instant): Boolean {
        return instant >= startUtc && instant < endUtc
    }

    /**
     * Check if event overlaps with a time range.
     */
    fun overlaps(rangeStart: Instant, rangeEnd: Instant): Boolean {
        return startUtc < rangeEnd && endUtc > rangeStart
    }

    companion object {
        private val DAY_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd")
            .withZone(ZoneOffset.UTC)
    }
}

package com.fishit.player.core.epg.model

import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * EpgTimeRange – Time window for EPG queries.
 *
 * Provides convenient factory methods for common EPG query patterns.
 */
data class EpgTimeRange(
    val from: Instant,
    val to: Instant,
) {
    init {
        require(to > from) { "to ($to) must be > from ($from)" }
    }

    /**
     * Duration of this range in hours.
     */
    val durationHours: Long
        get() = ChronoUnit.HOURS.between(from, to)

    /**
     * Check if an instant falls within this range.
     */
    operator fun contains(instant: Instant): Boolean {
        return instant >= from && instant < to
    }

    companion object {
        /**
         * Create range for "now + next N hours".
         */
        fun nowPlusHours(hours: Long): EpgTimeRange {
            val now = Instant.now()
            return EpgTimeRange(
                from = now,
                to = now.plus(hours, ChronoUnit.HOURS),
            )
        }

        /**
         * Create range for today (UTC).
         */
        fun today(): EpgTimeRange {
            val now = Instant.now()
            val startOfDay = now.truncatedTo(ChronoUnit.DAYS)
            val endOfDay = startOfDay.plus(1, ChronoUnit.DAYS)
            return EpgTimeRange(from = startOfDay, to = endOfDay)
        }

        /**
         * Create range for N days starting from today (UTC).
         */
        fun nextDays(days: Int): EpgTimeRange {
            val now = Instant.now()
            val startOfDay = now.truncatedTo(ChronoUnit.DAYS)
            val end = startOfDay.plus(days.toLong(), ChronoUnit.DAYS)
            return EpgTimeRange(from = startOfDay, to = end)
        }

        /**
         * Create range centered around now (±hours).
         */
        fun aroundNow(hoursBack: Long, hoursForward: Long): EpgTimeRange {
            val now = Instant.now()
            return EpgTimeRange(
                from = now.minus(hoursBack, ChronoUnit.HOURS),
                to = now.plus(hoursForward, ChronoUnit.HOURS),
            )
        }
    }
}

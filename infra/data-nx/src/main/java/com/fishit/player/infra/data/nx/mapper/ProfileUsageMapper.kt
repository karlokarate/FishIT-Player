package com.fishit.player.infra.data.nx.mapper

import com.fishit.player.core.model.repository.NxProfileUsageRepository
import com.fishit.player.core.persistence.obx.NX_ProfileUsage

/**
 * Mapper between NX_ProfileUsage entity and NxProfileUsageRepository.UsageDay domain model.
 *
 * Note: Domain model uses profileKey (String) and epochDay (Int),
 * Entity uses profileId (Long) and date (String YYYY-MM-DD).
 */

internal fun NX_ProfileUsage.toDomain(profileKey: String): NxProfileUsageRepository.UsageDay =
    NxProfileUsageRepository.UsageDay(
        profileKey = profileKey,
        epochDay = dateToEpochDay(date),
        watchedMs = watchTimeMs,
        sessionsCount = itemsWatched, // Approximate mapping
        lastUpdatedAtMs = lastActivityAt,
    )

internal fun NxProfileUsageRepository.UsageDay.toEntity(profileId: Long): NX_ProfileUsage =
    NX_ProfileUsage(
        profileId = profileId,
        date = epochDayToDate(epochDay),
        watchTimeMs = watchedMs,
        itemsWatched = sessionsCount,
        lastActivityAt = if (lastUpdatedAtMs > 0) lastUpdatedAtMs else System.currentTimeMillis(),
    )

/**
 * Converts epoch day (days since 1970-01-01) to YYYY-MM-DD string.
 */
private fun epochDayToDate(epochDay: Int): String {
    val ms = epochDay.toLong() * 24 * 60 * 60 * 1000
    val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
    cal.timeInMillis = ms
    return String.format(
        "%04d-%02d-%02d",
        cal.get(java.util.Calendar.YEAR),
        cal.get(java.util.Calendar.MONTH) + 1,
        cal.get(java.util.Calendar.DAY_OF_MONTH)
    )
}

/**
 * Converts YYYY-MM-DD string to epoch day.
 */
private fun dateToEpochDay(date: String): Int {
    val parts = date.split("-")
    if (parts.size != 3) return 0
    val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
    cal.clear()
    cal.set(parts[0].toIntOrNull() ?: 1970, (parts[1].toIntOrNull() ?: 1) - 1, parts[2].toIntOrNull() ?: 1)
    return (cal.timeInMillis / (24 * 60 * 60 * 1000)).toInt()
}

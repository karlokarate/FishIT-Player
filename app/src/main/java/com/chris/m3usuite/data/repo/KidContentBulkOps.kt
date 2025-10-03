package com.chris.m3usuite.data.repo

import kotlin.math.abs

/**
 * Bulk helpers for KidContentRepository using encoded media IDs.
 *
 * Encoded Media IDs follow the OBX ID bridge convention:
 *  - live   = 1_000_000_000_000L + streamId
 *  - vod    = 2_000_000_000_000L + vodId
 *  - series = 3_000_000_000_000L + seriesId
 *
 * These helpers decode encoded IDs and invoke repository allow/disallow methods in batches.
 * They use reflective fallbacks so they work regardless of the concrete KidContentRepository
 * method overloads (allow(kind,id) vs allowVod/allowSeries/allowLive).
 */
object KidContentBulkOps {
    const val LIVE_BASE: Long = 1_000_000_000_000L
    const val VOD_BASE: Long = 2_000_000_000_000L
    const val SERIES_BASE: Long = 3_000_000_000_000L

    enum class Kind { LIVE, VOD, SERIES }

    data class Decoded(val kind: Kind, val id: Long)

    fun decode(encodedId: Long): Decoded {
        return when {
            encodedId >= SERIES_BASE -> Decoded(Kind.SERIES, encodedId - SERIES_BASE)
            encodedId >= VOD_BASE -> Decoded(Kind.VOD, encodedId - VOD_BASE)
            encodedId >= LIVE_BASE -> Decoded(Kind.LIVE, encodedId - LIVE_BASE)
            else -> {
                // Fallback: infer by magnitude; prefer positive IDs
                // Treat unknowns as VOD to avoid Live favorites interference; override as needed.
                Decoded(Kind.VOD, abs(encodedId))
            }
        }
    }
}

/**
 * Allow a collection of encoded media IDs for one profile.
 *
 * Splits the set into typed batches (live/vod/series) and calls the best-matching KidContentRepository
 * methods using reflection fallbacks:
 *  - prefer allowVod/allowSeries/allowLive if found
 *  - else prefer allow(profileId: Long, kind: String, id: Long)
 */
fun KidContentRepository.allowManyEncoded(profileId: Long, encodedIds: Collection<Long>) {
    if (encodedIds.isEmpty()) return
    val live = ArrayList<Long>()
    val vod = ArrayList<Long>()
    val series = ArrayList<Long>()
    for (e in encodedIds) {
        val d = KidContentBulkOps.decode(e)
        when (d.kind) {
            KidContentBulkOps.Kind.LIVE -> live += d.id
            KidContentBulkOps.Kind.VOD -> vod += d.id
            KidContentBulkOps.Kind.SERIES -> series += d.id
        }
    }
    // Try type-specific bulk methods first
    val clazz = this::class.java
    val allowVodBulk = clazz.methods.firstOrNull { it.name == "allowManyVod" && it.parameterTypes.size == 2 }
    val allowSeriesBulk = clazz.methods.firstOrNull { it.name == "allowManySeries" && it.parameterTypes.size == 2 }
    val allowLiveBulk = clazz.methods.firstOrNull { it.name == "allowManyLive" && it.parameterTypes.size == 2 }
    val allowGeneric = clazz.methods.firstOrNull { m ->
        m.name == "allow" && m.parameterTypes.size == 3 &&
            (m.parameterTypes[1] == String::class.java || m.parameterTypes[1] == java.lang.String::class.java)
    }
    // Invoke bulk if available
    if (vod.isNotEmpty()) {
        if (allowVodBulk != null) {
            allowVodBulk.invoke(this, profileId, vod)
        } else {
            // Fallback to per-item generic
            if (allowGeneric != null) {
                vod.forEach { allowGeneric.invoke(this, profileId, "vod", it) }
            } else {
                // Try type-specific per-item methods
                val allowVod = clazz.methods.firstOrNull { it.name == "allowVod" && it.parameterTypes.size == 2 }
                if (allowVod != null) {
                    vod.forEach { allowVod.invoke(this, profileId, it) }
                }
            }
        }
    }
    if (series.isNotEmpty()) {
        if (allowSeriesBulk != null) {
            allowSeriesBulk.invoke(this, profileId, series)
        } else {
            if (allowGeneric != null) {
                series.forEach { allowGeneric.invoke(this, profileId, "series", it) }
            } else {
                val allowSeries = clazz.methods.firstOrNull { it.name == "allowSeries" && it.parameterTypes.size == 2 }
                if (allowSeries != null) {
                    series.forEach { allowSeries.invoke(this, profileId, it) }
                }
            }
        }
    }
    if (live.isNotEmpty()) {
        if (allowLiveBulk != null) {
            allowLiveBulk.invoke(this, profileId, live)
        } else {
            if (allowGeneric != null) {
                live.forEach { allowGeneric.invoke(this, profileId, "live", it) }
            } else {
                val allowLive = clazz.methods.firstOrNull { it.name == "allowLive" && it.parameterTypes.size == 2 }
                if (allowLive != null) {
                    live.forEach { allowLive.invoke(this, profileId, it) }
                }
            }
        }
    }
}

/**
 * Disallow a collection of encoded media IDs for one profile.
 *
 * Mirrors [allowManyEncoded] with disallow fallbacks.
 */
fun KidContentRepository.disallowManyEncoded(profileId: Long, encodedIds: Collection<Long>) {
    if (encodedIds.isEmpty()) return
    val live = ArrayList<Long>()
    val vod = ArrayList<Long>()
    val series = ArrayList<Long>()
    for (e in encodedIds) {
        val d = KidContentBulkOps.decode(e)
        when (d.kind) {
            KidContentBulkOps.Kind.LIVE -> live += d.id
            KidContentBulkOps.Kind.VOD -> vod += d.id
            KidContentBulkOps.Kind.SERIES -> series += d.id
        }
    }
    val clazz = this::class.java
    val disallowVodBulk = clazz.methods.firstOrNull { it.name == "disallowManyVod" && it.parameterTypes.size == 2 }
    val disallowSeriesBulk = clazz.methods.firstOrNull { it.name == "disallowManySeries" && it.parameterTypes.size == 2 }
    val disallowLiveBulk = clazz.methods.firstOrNull { it.name == "disallowManyLive" && it.parameterTypes.size == 2 }
    val disallowGeneric = clazz.methods.firstOrNull { m ->
        m.name == "disallow" && m.parameterTypes.size == 3 &&
            (m.parameterTypes[1] == String::class.java || m.parameterTypes[1] == java.lang.String::class.java)
    }
    if (vod.isNotEmpty()) {
        if (disallowVodBulk != null) {
            disallowVodBulk.invoke(this, profileId, vod)
        } else if (disallowGeneric != null) {
            vod.forEach { disallowGeneric.invoke(this, profileId, "vod", it) }
        } else {
            val disallowVod = clazz.methods.firstOrNull { it.name == "disallowVod" && it.parameterTypes.size == 2 }
            disallowVod?.let { m -> vod.forEach { m.invoke(this, profileId, it) } }
        }
    }
    if (series.isNotEmpty()) {
        if (disallowSeriesBulk != null) {
            disallowSeriesBulk.invoke(this, profileId, series)
        } else if (disallowGeneric != null) {
            series.forEach { disallowGeneric.invoke(this, profileId, "series", it) }
        } else {
            val disallowSeries = clazz.methods.firstOrNull { it.name == "disallowSeries" && it.parameterTypes.size == 2 }
            disallowSeries?.let { m -> series.forEach { m.invoke(this, profileId, it) } }
        }
    }
    if (live.isNotEmpty()) {

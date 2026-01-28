package com.fishit.player.pipeline.xtream.debug

import com.fishit.player.core.model.RawMediaMetadata
import com.fishit.player.infra.logging.UnifiedLog
import java.util.concurrent.atomic.AtomicInteger

/**
 * XTC (Xtream-Chain) Logging Helper
 *
 * Provides sample-based logging for Xtream pipeline to track:
 * - DTO → RawMetadata mapping
 * - Field population (title, year, plot, cast, director, poster)
 * - Normalization results
 * - DB entity writes
 * - Playback URL generation
 *
 * **Strategy:**
 * - Log first item of each type (VOD/Series/Episode/Live)
 * - Then sample every 50th item to avoid log flooding
 * - Focus on commonly problematic fields (plot, cast, images)
 *
 * **Usage:**
 * ```kotlin
 * XtcLogger.logDtoToRaw("VOD", vodItem.id, vodItem.name, raw)
 * ```
 */
object XtcLogger {
    private const val TAG = "XTC" // Xtream-Chain
    private const val SAMPLE_INTERVAL = 50

    private val vodCounter = AtomicInteger(0)
    private val seriesCounter = AtomicInteger(0)
    private val episodeCounter = AtomicInteger(0)
    private val liveCounter = AtomicInteger(0)

    /**
     * Log DTO → RawMetadata mapping with field validation.
     *
     * Tracks which fields are populated vs empty to spot pipeline gaps.
     */
    fun logDtoToRaw(
        type: String,
        sourceId: String,
        originalTitle: String?,
        raw: RawMediaMetadata
    ) {
        val count = when (type) {
            "VOD" -> vodCounter.incrementAndGet()
            "SERIES" -> seriesCounter.incrementAndGet()
            "EPISODE" -> episodeCounter.incrementAndGet()
            "LIVE" -> liveCounter.incrementAndGet()
            else -> return
        }

        // Sample: first item + every 50th
        if (count == 1 || count % SAMPLE_INTERVAL == 0) {
            val fields = buildString {
                append("[$type] DTO→Raw #$count | ")
                append("id=$sourceId | ")
                append("title=\"${originalTitle?.take(40)}\" | ")
                append("sourceType=${raw.sourceType} | ")  // ADD: Track sourceType for playback debugging
                append("Fields: ")

                val populated = mutableListOf<String>()
                val missing = mutableListOf<String>()

                if (raw.year != null) populated.add("year=${raw.year}") else missing.add("year")
                if (raw.plot?.isNotBlank() == true) populated.add("plot(${raw.plot!!.length}c)") else missing.add("plot")
                if (raw.cast?.isNotBlank() == true) populated.add("cast") else missing.add("cast")
                if (raw.director?.isNotBlank() == true) populated.add("director") else missing.add("director")
                if (raw.poster != null) populated.add("poster") else missing.add("poster")
                if (raw.backdrop != null) populated.add("backdrop") else missing.add("backdrop")
                if (raw.durationMs != null) populated.add("duration=${raw.durationMs}ms") else missing.add("duration")
                if (raw.externalIds.tmdb != null) populated.add("tmdb=${raw.externalIds.tmdb!!.id}") else missing.add("tmdb")

                append("✓[${populated.joinToString(", ")}] ")
                if (missing.isNotEmpty()) {
                    append("✗[${missing.joinToString(", ")}]")
                }
            }

            UnifiedLog.d(TAG, fields)
        }
    }

    /**
     * Log normalized metadata result.
     *
     * Tracks normalization transformations (title cleaning, year extraction, etc).
     */
    fun logNormalized(
        type: String,
        rawTitle: String?,
        normalizedTitle: String?,
        year: Int?,
        adult: Boolean,
        mediaType: String
    ) {
        val count = when (type) {
            "VOD" -> vodCounter.get()
            "SERIES" -> seriesCounter.get()
            "EPISODE" -> episodeCounter.get()
            "LIVE" -> liveCounter.get()
            else -> return
        }

        // Sample: first item + every 50th
        if (count == 1 || count % SAMPLE_INTERVAL == 0) {
            val titleChange = if (rawTitle != normalizedTitle) {
                " (cleaned: \"${rawTitle?.take(30)}\" → \"${normalizedTitle?.take(30)}\")"
            } else ""

            UnifiedLog.d(TAG) {
                "[$type] Normalized #$count | type=$mediaType | year=$year | adult=$adult$titleChange"
            }
        }
    }

    /**
     * Log NX entity write.
     *
     * Tracks DB writes to NX_Work, NX_WorkSourceRef, NX_WorkVariant.
     */
    fun logNxWrite(
        type: String,
        workKey: String,
        sourceKey: String,
        hasVariant: Boolean,
        fieldsPopulated: Int,
        totalFields: Int
    ) {
        val count = when (type) {
            "VOD" -> vodCounter.get()
            "SERIES" -> seriesCounter.get()
            "EPISODE" -> episodeCounter.get()
            "LIVE" -> liveCounter.get()
            else -> return
        }

        // Sample: first item + every 50th
        if (count == 1 || count % SAMPLE_INTERVAL == 0) {
            UnifiedLog.d(TAG) {
                "[$type] NX Write #$count | workKey=$workKey | sourceKey=$sourceKey | " +
                "variant=$hasVariant | fields=$fieldsPopulated/$totalFields"
            }
        }
    }

    /**
     * Log playback URL generation.
     *
     * Tracks final playback URL construction from hints.
     */
    fun logPlaybackUrl(
        type: String,
        sourceId: String,
        url: String,
        hints: Map<String, String>
    ) {
        // Only log first few playback URLs to avoid flooding
        if (vodCounter.get() + seriesCounter.get() + episodeCounter.get() + liveCounter.get() < 5) {
            val safeUrl = url.substringBefore("?") // Hide credentials
            UnifiedLog.d(TAG) {
                "[$type] Playback URL | id=$sourceId | url=$safeUrl | hints=${hints.keys.joinToString()}"
            }
        }
    }

    /**
     * Log pipeline phase completion.
     */
    fun logPhaseComplete(phase: String, count: Int, durationMs: Long) {
        UnifiedLog.i(TAG) {
            "Phase complete: $phase | items=$count | duration=${durationMs}ms | " +
            "rate=${if (durationMs > 0) count * 1000 / durationMs else 0} items/sec"
        }
    }

    /**
     * Reset counters (for new sync run).
     */
    fun reset() {
        vodCounter.set(0)
        seriesCounter.set(0)
        episodeCounter.set(0)
        liveCounter.set(0)
    }
}

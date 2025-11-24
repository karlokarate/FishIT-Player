package com.chris.m3usuite.player.internal.domain

import android.content.Context
import com.chris.m3usuite.data.repo.ResumeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * Abstraktion für Resume-Handling, damit der Player-Screen
 * nicht mehr direkt mit ResumeRepository reden muss.
 */
interface ResumeManager {
    /**
     * Try to load a resume position in milliseconds for the given context.
     *
     * Returns null if:
     * - no resume exists
     * - or the saved position is too small (<=10s) to be useful.
     */
    suspend fun loadResumePositionMs(context: PlaybackContext): Long?

    /**
     * Periodic tick (~3s): either save the current progress or clear
     * the resume mark if we are near the end (<10s remaining).
     */
    suspend fun handlePeriodicTick(
        context: PlaybackContext,
        positionMs: Long,
        durationMs: Long,
    )

    /**
     * Called when the player reports STATE_ENDED.
     * Clears the resume mark for VOD/Series.
     */
    suspend fun handleEnded(context: PlaybackContext)
}

/**
 * Default implementation that mimics the legacy InternalPlayerScreen logic,
 * but lives in a dedicated domain service.
 */
class DefaultResumeManager(
    context: Context,
) : ResumeManager {

    private val repo = ResumeRepository(context)

    override suspend fun loadResumePositionMs(context: PlaybackContext): Long? =
        withContext(Dispatchers.IO) {
            when (context.type) {
                PlaybackType.VOD -> {
                    val id = context.mediaId ?: return@withContext null
                    val posSecs = repo.getVodResume(id) ?: return@withContext null
                    if (posSecs > 10) posSecs.toLong() * 1000L else null
                }

                PlaybackType.SERIES -> {
                    val seriesId = context.seriesId
                    val season = context.season
                    val episodeNum = context.episodeNumber
                    if (seriesId == null || season == null || episodeNum == null) {
                        return@withContext null
                    }
                    val posSecs =
                        repo.getSeriesResume(
                            seriesId = seriesId,
                            season = season,
                            episodeNum = episodeNum,
                        ) ?: return@withContext null

                    if (posSecs > 10) posSecs.toLong() * 1000L else null
                }

                PlaybackType.LIVE -> null
            }
        }

    override suspend fun handlePeriodicTick(
        context: PlaybackContext,
        positionMs: Long,
        durationMs: Long,
    ) {
        if (context.type == PlaybackType.LIVE) return
        if (durationMs <= 0L) return

        val remaining = durationMs - positionMs
        val posSecs = max(0L, positionMs / 1000L).toInt()

        when (context.type) {
            PlaybackType.VOD -> {
                val mediaId = context.mediaId ?: return
                withContext(Dispatchers.IO) {
                    if (durationMs > 0 && remaining in 0L..9999L) {
                        // near end -> clear
                        repo.clearVod(mediaId)
                    } else {
                        repo.setVodResume(mediaId, posSecs)
                    }
                }
            }

            PlaybackType.SERIES -> {
                val seriesId = context.seriesId
                val season = context.season
                val episodeNum = context.episodeNumber
                if (seriesId == null || season == null || episodeNum == null) return

                withContext(Dispatchers.IO) {
                    if (durationMs > 0 && remaining in 0L..9999L) {
                        // near end -> clear
                        repo.clearSeriesResume(seriesId, season, episodeNum)
                    } else {
                        repo.setSeriesResume(seriesId, season, episodeNum, posSecs)
                    }
                }
            }

            PlaybackType.LIVE -> Unit
        }
    }

    override suspend fun handleEnded(context: PlaybackContext) {
        when (context.type) {
            PlaybackType.VOD -> {
                val mediaId = context.mediaId ?: return
                withContext(Dispatchers.IO) {
                    repo.clearVod(mediaId)
                }
            }

            PlaybackType.SERIES -> {
                val seriesId = context.seriesId
                val season = context.season
                val episodeNum = context.episodeNumber
                if (seriesId == null || season == null || episodeNum == null) return

                withContext(Dispatchers.IO) {
                    repo.clearSeriesResume(seriesId, season, episodeNum)
                }
            }

            PlaybackType.LIVE -> Unit
        }
    }
}
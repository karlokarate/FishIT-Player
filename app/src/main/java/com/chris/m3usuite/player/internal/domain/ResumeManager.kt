package com.chris.m3usuite.player.internal.domain

import android.content.Context
import com.chris.m3usuite.data.obx.ObxEpisode
import com.chris.m3usuite.data.obx.ObxEpisode_
import com.chris.m3usuite.data.obx.ObxStore
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
 *
 * ════════════════════════════════════════════════════════════════════════════
 * Phase 2 Behavioral Parity Notes (vs. legacy InternalPlayerScreen)
 * ════════════════════════════════════════════════════════════════════════════
 *
 * RESUME LOAD (loadResumePositionMs):
 * ─────────────────────────────────────────────────────────────────────────────
 * Legacy Reference: InternalPlayerScreen L572-608
 *
 * TODO(Phase 2 Parity): >10s Rule
 *   - Legacy (L601): if (p > 10) { exoPlayer.seekTo(p.toLong() * 1000L) }
 *   - This implementation: if (posSecs > 10) posSecs.toLong() * 1000L else null
 *   - ✓ Matches: Only restores position if saved value is greater than 10 seconds
 *   - Purpose: Avoid jarring short seeks that are less useful than starting fresh
 *
 * TODO(Phase 2 Parity): Multi-ID Mapping (mediaId vs seriesId+season+episodeNum)
 *   - VOD Legacy (L581-584):
 *       resumeRepo.recentVod(1).firstOrNull { it.mediaId == mediaId }?.positionSecs
 *   - VOD Modular: repo.getVodResume(id)
 *   - SERIES Legacy (L585-596):
 *       resumeRepo.recentEpisodes(50).firstOrNull {
 *           it.seriesId == seriesId && it.season == season && it.episodeNum == episodeNum
 *       }?.positionSecs
 *   - SERIES Modular: repo.getSeriesResume(seriesId, season, episodeNum)
 *   - Note: Legacy uses list-based lookups, modular uses direct key lookups.
 *     Functionally equivalent but different query patterns.
 *
 * TODO(Phase 2 Parity): episodeId Fallback
 *   - Legacy (L575): (episodeId != null || (seriesId != null && season != null && episodeNum != null))
 *   - This implementation only supports composite key (seriesId+season+episodeNum)
 *   - episodeId-only fallback is NOT implemented in modular version
 *   - Verify: All call sites should provide composite key, not just episodeId
 *
 * PERIODIC TICK (handlePeriodicTick):
 * ─────────────────────────────────────────────────────────────────────────────
 * Legacy Reference: InternalPlayerScreen L692-722
 *
 * TODO(Phase 2 Parity): <10s Near-End Clear
 *   - Legacy (L701-710):
 *       val remaining = if (dur > 0) dur - pos else Long.MAX_VALUE
 *       if (dur > 0 && remaining in 0..9999) { clearVod/clearSeriesResume }
 *   - This implementation: if (remaining in 0L..9999L) { clear }
 *   - ✓ Matches: Clears resume when remaining playback is less than 10 seconds
 *   - Purpose: Treat "almost finished" as "finished" for resume purposes
 *
 * TODO(Phase 2 Parity): Periodic Tick Timing Contract (3s)
 *   - Legacy (L747): delay(3000) in while(isActive) loop
 *   - This function should be called every ~3 seconds by the session layer
 *   - Note: This function does not enforce timing; caller is responsible
 *   - Future: Consider adding timing validation or debouncing
 *
 * TODO(Phase 2 Parity): Duration Guard
 *   - Legacy (L698-701): val dur = exoPlayer.duration; val remaining = if (dur > 0) dur - pos else Long.MAX_VALUE
 *   - This implementation: if (durationMs <= 0L) return
 *   - ✓ Matches: Skips save/clear when duration is unknown or invalid
 *   - Purpose: Avoid clearing resume on streams with unknown duration
 *
 * PLAYBACK ENDED (handleEnded):
 * ─────────────────────────────────────────────────────────────────────────────
 * Legacy Reference: InternalPlayerScreen L798-806
 *
 * TODO(Phase 2 Parity): STATE_ENDED Clear
 *   - Legacy (L794-810):
 *       onPlaybackStateChanged(playbackState == Player.STATE_ENDED)
 *       scope.launch(Dispatchers.IO) { clearVod/clearSeriesResume }
 *   - This implementation: Uses Dispatchers.IO in handleEnded
 *   - ✓ Matches: Clears resume unconditionally on playback end
 *   - No remaining time check needed (player already at end)
 *
 * TODO(Phase 2 Parity): VOD vs Series Behavior
 *   - VOD: clearVod(mediaId)
 *   - SERIES: clearSeriesResume(seriesId, season, episodeNum)
 *   - LIVE: No action (type check returns early)
 *   - ✓ Matches legacy behavior exactly
 *
 * LIFECYCLE SAVE (ON_DESTROY) - NOT IN THIS CLASS:
 * ─────────────────────────────────────────────────────────────────────────────
 * Legacy Reference: InternalPlayerScreen L636-664
 *
 * TODO(Phase 8): ON_DESTROY save/clear should be handled by InternalPlayerLifecycle
 *   - Legacy uses DisposableEffect + LifecycleEventObserver
 *   - Uses runBlocking for synchronous final save (not ideal)
 *   - Modular: Should use suspending lifecycle callbacks in Phase 8
 */
class DefaultResumeManager(
    context: Context,
) : ResumeManager {
    private val appContext = context
    private val repo = ResumeRepository(context)
    private val episodeCompositeCache =
        object : LinkedHashMap<Int, SeriesComposite>(32, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, SeriesComposite>?): Boolean = size > 128
        }

    // TODO(Phase 2 Parity): Legacy uses ResumeRepository.recentVod() and recentEpisodes()
    // for lookups. This implementation uses getVodResume/getSeriesResume directly.
    // Verify functional equivalence.
    override suspend fun loadResumePositionMs(context: PlaybackContext): Long? =
        withContext(Dispatchers.IO) {
            when (context.type) {
                PlaybackType.VOD -> {
                    val id = context.mediaId ?: return@withContext null
                    val posSecs = repo.getVodResume(id) ?: return@withContext null
                    val posMs = posSecs.toLong() * 1000L
                    if (posSecs > 10 && posMs <= MAX_RESUME_MS) posMs else null
                }

                PlaybackType.SERIES -> {
                    val composite =
                        resolveSeriesComposite(
                            episodeId = context.episodeId,
                            seriesId = context.seriesId,
                            season = context.season,
                            episodeNum = context.episodeNumber,
                        ) ?: return@withContext null

                    val posSecs =
                        repo.getSeriesResume(
                            seriesId = composite.seriesId,
                            season = composite.season,
                            episodeNum = composite.episodeNum,
                        ) ?: return@withContext null

                    val posMs = posSecs.toLong() * 1000L
                    if (posSecs > 10 && posMs <= MAX_RESUME_MS) posMs else null
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
                val composite =
                    resolveSeriesComposite(
                        episodeId = context.episodeId,
                        seriesId = context.seriesId,
                        season = context.season,
                        episodeNum = context.episodeNumber,
                    ) ?: return

                withContext(Dispatchers.IO) {
                    if (durationMs > 0 && remaining in 0L..9999L) {
                        // near end -> clear
                        repo.clearSeriesResume(composite.seriesId, composite.season, composite.episodeNum)
                    } else {
                        repo.setSeriesResume(composite.seriesId, composite.season, composite.episodeNum, posSecs)
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

    private data class SeriesComposite(
        val seriesId: Int,
        val season: Int,
        val episodeNum: Int,
    )

    companion object {
        // Guard against implausible resume positions (>24h)
        private const val MAX_RESUME_MS = 24 * 60 * 60 * 1000L
    }

    /**
     * Resolve series identifiers from either composite keys or legacy episodeId fallback.
     */
    private fun resolveSeriesComposite(
        episodeId: Int?,
        seriesId: Int?,
        season: Int?,
        episodeNum: Int?,
    ): SeriesComposite? {
        if (seriesId != null && season != null && episodeNum != null) {
            return SeriesComposite(seriesId, season, episodeNum)
        }
        if (episodeId == null) return null

        synchronized(episodeCompositeCache) {
            episodeCompositeCache[episodeId]?.let { return it }
        }

        return runCatching {
            val box = ObxStore.get(appContext).boxFor(ObxEpisode::class.java)
            val query =
                box
                    .query(ObxEpisode_.episodeId.equal(episodeId.toLong()))
                    .build()
            query.use { q ->
                val row = q.findFirst() ?: return@use null
                val sId = row.seriesId
                val sSeason = row.season
                val sEpisode = row.episodeNum
                SeriesComposite(sId, sSeason, sEpisode)
            }
        }.onSuccess { composite ->
            if (composite != null) {
                synchronized(episodeCompositeCache) {
                    episodeCompositeCache[episodeId] = composite
                }
            }
        }.getOrNull()
    }
}

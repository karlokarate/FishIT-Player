package com.chris.m3usuite.player.internal.session

import com.chris.m3usuite.player.internal.domain.KidsGateState
import com.chris.m3usuite.player.internal.domain.KidsPlaybackGate
import com.chris.m3usuite.player.internal.domain.PlaybackContext
import com.chris.m3usuite.player.internal.domain.PlaybackType
import com.chris.m3usuite.player.internal.domain.ResumeManager

/**
 * Phase 2 integration hooks.
 *
 * These functions are used to mirror existing legacy behavior in a modular way.
 * They must NOT change runtime behavior while the legacy InternalPlayerScreen
 * remains active.
 *
 * ════════════════════════════════════════════════════════════════════════════
 * IMPORTANT: This file is NOT wired into the runtime player flow.
 * InternalPlayerEntry still delegates to the legacy InternalPlayerScreen.
 * All SIP modules remain REFERENCE-ONLY and are NOT executed at runtime.
 * ════════════════════════════════════════════════════════════════════════════
 *
 * ┌──────────────────────────────────────────────────────────────────────────┐
 * │                    LEGACY BEHAVIOR MAPPING (Complete)                    │
 * └──────────────────────────────────────────────────────────────────────────┘
 *
 * RESUME HANDLING (InternalPlayerScreen.kt):
 * ├─ L572-608 (Resume Load): LaunchedEffect that loads initial resume position
 * │   - VOD: resumeRepo.recentVod(1).firstOrNull { it.mediaId == mediaId }?.positionSecs
 * │   - SERIES: resumeRepo.recentEpisodes(50).firstOrNull { seriesId, season, episodeNum match }
 * │   - LIVE: No resume (skipped)
 * │   - Threshold: Only seek if posSecs > 10
 * │   - Timing: Only if startPositionMs == null (no explicit start position provided)
 * │
 * ├─ L692-722 (Resume Tick): LaunchedEffect with ~3s delay loop
 * │   - Condition: (type == "vod" && mediaId != null) || (type == "series" && seriesId/season/episodeNum != null)
 * │   - Near-end clear: remaining in 0..9999ms → clearVod() / clearSeriesResume()
 * │   - Normal save: setVodResume(mediaId, posSecs) / setSeriesResume(seriesId, season, episodeNum, posSecs)
 * │   - posSecs = (pos / 1000L).toInt().coerceAtLeast(0)
 * │
 * ├─ L636-664 (Resume on ON_DESTROY): Lifecycle observer
 * │   - Same logic as periodic tick (near-end clear vs save)
 * │   - Uses runBlocking + Dispatchers.IO (synchronous final save)
 * │   - Future: Handled by InternalPlayerLifecycle (Phase 8)
 * │
 * └─ L798-806 (Resume on STATE_ENDED): Player listener
 *     - Condition: (type == "vod" && mediaId != null) || (type == "series" && seriesId/season/episodeNum != null)
 *     - Action: clearVod(mediaId) / clearSeriesResume(seriesId, season, episodeNum)
 *     - Timing: Immediately on playback end state
 *
 * KIDS/SCREEN-TIME GATE (InternalPlayerScreen.kt):
 * ├─ L547-569 (KidsGate Start): LaunchedEffect(Unit)
 * │   - Profile detection: store.currentProfileId.first() → obxStore.boxFor(ObxProfile).get(id)
 * │   - Kid check: prof?.type == "kid"
 * │   - Remaining time: screenTimeRepo.remainingMinutes(kidId)
 * │   - Block condition: remain <= 0 → kidBlocked = true
 * │   - playWhenReady: Only set true if !kidBlocked
 * │
 * ├─ L725-744 (KidsGate Tick): Inside ~3s delay loop
 * │   - Condition: kidActive && exoPlayer.playWhenReady && exoPlayer.isPlaying
 * │   - Accumulation: tickAccum += 3 (seconds)
 * │   - Tick trigger: tickAccum >= 60
 * │   - Action: screenTimeRepo.tickUsageIfPlaying(kidId, tickAccum); tickAccum = 0
 * │   - Block check: remainingMinutes(kidId) <= 0 → playWhenReady = false; kidBlocked = true
 * │   - Reset: tickAccum = 0 when not playing or not kid profile
 * │
 * └─ L2282-2290 (KidsGate UI): AlertDialog shown when kidBlocked == true
 *     - Title: "Bildschirmzeit abgelaufen"
 *     - Action: Navigate back / close player
 */
object Phase2Integration {

    /**
     * Load the initial resume position for a playback session.
     *
     * ┌──────────────────────────────────────────────────────────────────────┐
     * │ Mirrors legacy behavior at InternalPlayerScreen L572-608             │
     * └──────────────────────────────────────────────────────────────────────┘
     *
     * LEGACY PARAMETER SHAPE (legacy uses explicit parameters):
     *   type: String ("vod" | "series" | "live")
     *   mediaId: String? (for VOD)
     *   seriesId: String?, season: Int?, episodeNum: Int? (for SERIES)
     *   startPositionMs: Long? (explicit start position, bypasses resume)
     *
     * MODULAR PARAMETER SHAPE (uses PlaybackContext):
     *   playbackContext.type: PlaybackType (VOD | SERIES | LIVE)
     *   playbackContext.mediaId: String? (for VOD)
     *   playbackContext.seriesId: String?, season: Int?, episodeNumber: Int? (for SERIES)
     *   Note: startPositionMs is handled by caller before invoking this function
     *
     * BEHAVIORAL PARITY:
     *   - VOD: resumeRepo.recentVod(1).firstOrNull { it.mediaId == mediaId }?.positionSecs
     *          → Modular: resumeManager.loadResumePositionMs() queries ResumeRepository
     *   - SERIES: resumeRepo.recentEpisodes(50).firstOrNull { matches seriesId/season/episodeNum }
     *          → Modular: resumeManager maps to getSeriesResume(seriesId, season, episodeNum)
     *   - LIVE: No resume (returns null in both)
     *
     * THRESHOLD RULES (L601):
     *   - Only returns position if posSecs > 10
     *   - Positions ≤10s are rejected (returns null)
     *   - Return value is in MILLISECONDS (posSecs * 1000L)
     *
     * TIMING CONTRACT:
     *   - Called once during player setup, inside LaunchedEffect
     *   - Only evaluated if startPositionMs == null (no explicit start position)
     *   - Uses Dispatchers.IO for repository access
     *
     * @param playbackContext The current playback context with type and IDs
     * @param resumeManager Resume manager instance (DefaultResumeManager)
     * @return Resume position in milliseconds, or null if:
     *         - No resume record exists
     *         - Saved position is ≤10 seconds
     *         - PlaybackType is LIVE
     */
    suspend fun loadInitialResumePosition(
        playbackContext: PlaybackContext,
        resumeManager: ResumeManager,
    ): Long? {
        // TODO(Phase 2): This function mirrors legacy resume loading behavior.
        // It should only be called from experimental code paths until
        // the modular session replaces the legacy InternalPlayerScreen.
        //
        // Legacy ID mapping (L574-575):
        //   VOD: (type == "vod" && mediaId != null)
        //   SERIES: (type == "series" && (episodeId != null || (seriesId != null && season != null && episodeNum != null)))
        //
        // Note: Legacy uses episodeId OR composite (seriesId+season+episodeNum) key.
        // Modular uses composite key exclusively via PlaybackContext.
        return resumeManager.loadResumePositionMs(playbackContext)
    }

    /**
     * Handle periodic tick for resume saving and kids gate updates.
     *
     * ┌──────────────────────────────────────────────────────────────────────┐
     * │ Mirrors legacy behavior at InternalPlayerScreen L692-722, L725-744   │
     * └──────────────────────────────────────────────────────────────────────┘
     *
     * LEGACY PARAMETER SHAPE (L693):
     *   url: String, type: String, mediaId: String?, episodeId: String?, exoPlayer: ExoPlayer
     *   (implicit) seriesId: String?, season: Int?, episodeNum: Int?
     *   (implicit) kidActive: Boolean, kidIdState: Long?, screenTimeRepo: ScreenTimeRepository
     *
     * MODULAR PARAMETER SHAPE:
     *   playbackContext: PlaybackContext (contains type, mediaId, seriesId, season, episodeNumber)
     *   positionMs: Long (from exoPlayer.currentPosition)
     *   durationMs: Long (from exoPlayer.duration)
     *   resumeManager: ResumeManager
     *   kidsGate: KidsPlaybackGate
     *   currentKidsState: KidsGateState? (tracks kidActive, kidBlocked, kidProfileId)
     *   tickAccumSecs: Int (caller-maintained accumulator for 60s threshold)
     *
     * RESUME TICK BEHAVIORAL PARITY (L692-722):
     *   - Tick interval: ~3 seconds (delay(3000) at L747)
     *   - Condition: (type == "vod" && mediaId != null) || (type == "series" && seriesId/season/episodeNum != null)
     *   - Duration check: dur = exoPlayer.duration, pos = exoPlayer.currentPosition
     *   - Remaining calculation: remaining = if (dur > 0) dur - pos else Long.MAX_VALUE
     *   - Near-end clear rule: (dur > 0 && remaining in 0..9999) → clear resume
     *   - Normal save: posSecs = (pos / 1000L).toInt().coerceAtLeast(0) → save
     *   - LIVE content: Skipped (type != "live" check at L711)
     *
     * KIDS TICK BEHAVIORAL PARITY (L725-744):
     *   - Condition: kidActive && exoPlayer.playWhenReady && exoPlayer.isPlaying
     *   - Accumulation: tickAccum += 3 (every 3-second loop iteration)
     *   - Trigger threshold: tickAccum >= 60 (every 60 seconds)
     *   - Action sequence:
     *     1. screenTimeRepo.tickUsageIfPlaying(kidId, tickAccum)
     *     2. tickAccum = 0 (reset)
     *     3. remain = screenTimeRepo.remainingMinutes(kidId)
     *     4. if remain <= 0: exoPlayer.playWhenReady = false; kidBlocked = true
     *   - Reset on pause: tickAccum = 0 when !playWhenReady || !isPlaying || !kidActive
     *
     * SIDE EFFECTS (Legacy L735-736):
     *   - On block: exoPlayer.playWhenReady = false (pause)
     *   - On block: kidBlocked = true (UI state)
     *   - Modular: Caller should check returned KidsGateState.kidBlocked and pause player
     *
     * TIMING CONTRACT:
     *   - Called every ~3 seconds from a LaunchedEffect loop
     *   - Resume saves happen on every tick for VOD/SERIES
     *   - Kids ticks only fire when tickAccumSecs >= 60
     *   - Caller must maintain tickAccumSecs and reset when function indicates
     *
     * @param playbackContext The current playback context with type and IDs
     * @param positionMs Current playback position in milliseconds (exoPlayer.currentPosition)
     * @param durationMs Total media duration in milliseconds (exoPlayer.duration)
     * @param resumeManager Resume manager instance (DefaultResumeManager)
     * @param kidsGate Kids playback gate instance (DefaultKidsPlaybackGate)
     * @param currentKidsState Current kids gate state (from previous evaluateStart or tick)
     * @param tickAccumSecs Accumulated seconds since last kids tick (caller maintains, add 3 per call)
     * @return Updated KidsGateState if kid profile is active, original state otherwise
     *         - Caller should check kidBlocked and pause player if true
     *         - Caller should reset tickAccumSecs to 0 when this returns with tickAccumSecs >= 60
     */
    suspend fun onPlaybackTick(
        playbackContext: PlaybackContext,
        positionMs: Long,
        durationMs: Long,
        resumeManager: ResumeManager,
        kidsGate: KidsPlaybackGate,
        currentKidsState: KidsGateState?,
        tickAccumSecs: Int,
    ): KidsGateState? {
        // TODO(Phase 2): Mirror legacy periodic tick:
        // - Save resume position periodically (VOD/SERIES)
        // - Apply kids/screentime gating every ~60s
        // This should be wired into the modular session, not the legacy screen.
        //
        // Legacy accumulation pattern (L727-728):
        //   tickAccum += 3  // Inside 3s loop
        //   if (tickAccum >= 60) { ... tickAccum = 0 }
        //
        // Modular pattern:
        //   Caller passes tickAccumSecs, this function returns new state.
        //   Caller should: tickAccumSecs += 3, then reset to 0 after tick fires.

        // Resume handling (every tick for VOD/Series)
        if (playbackContext.type != PlaybackType.LIVE) {
            resumeManager.handlePeriodicTick(playbackContext, positionMs, durationMs)
        }

        // Kids gate handling (every 60s when kid profile active)
        val kidsState = currentKidsState
        if (kidsState != null && kidsState.kidActive && tickAccumSecs >= 60) {
            return kidsGate.onPlaybackTick(kidsState, tickAccumSecs)
        }

        return kidsState
    }

    /**
     * Handle playback ended event.
     *
     * ┌──────────────────────────────────────────────────────────────────────┐
     * │ Mirrors legacy behavior at InternalPlayerScreen L798-806             │
     * └──────────────────────────────────────────────────────────────────────┘
     *
     * LEGACY PARAMETER SHAPE (implicit in Player.Listener):
     *   type: String ("vod" | "series" | "live")
     *   mediaId: String? (for VOD)
     *   seriesId: String?, season: Int?, episodeNum: Int? (for SERIES)
     *   resumeRepo: ResumeRepository
     *
     * MODULAR PARAMETER SHAPE:
     *   playbackContext: PlaybackContext (contains type and all IDs)
     *   resumeManager: ResumeManager
     *
     * BEHAVIORAL PARITY (L793-810):
     *   - Trigger: onPlaybackStateChanged(playbackState == Player.STATE_ENDED)
     *   - Condition: (type == "vod" && mediaId != null) || (type == "series" && seriesId/season/episodeNum != null)
     *   - VOD action: resumeRepo.clearVod(mediaId!!)
     *   - SERIES action: resumeRepo.clearSeriesResume(seriesId!!, season!!, episodeNum!!)
     *   - LIVE: No action (implicit, condition fails)
     *   - Threading: scope.launch(Dispatchers.IO) { ... }
     *
     * NEAR-END BEHAVIOR:
     *   - STATE_ENDED is triggered when playback reaches end naturally
     *   - At this point, remaining time is effectively 0
     *   - Resume is cleared unconditionally (no remaining time check needed)
     *
     * AUTOPLAY SERIES (L811-866 - NOT in scope):
     *   - Legacy has autoplay next episode logic after clearing resume
     *   - This is handled separately in the modular architecture
     *   - This function only handles resume clearing
     *
     * @param playbackContext The current playback context with type and IDs
     * @param resumeManager Resume manager instance (DefaultResumeManager)
     */
    suspend fun onPlaybackEnded(
        playbackContext: PlaybackContext,
        resumeManager: ResumeManager,
    ) {
        // TODO(Phase 2): Mirror legacy STATE_ENDED handling.
        // Clear resume marker when playback completes.
        //
        // Legacy uses scope.launch(Dispatchers.IO) (L798).
        // Modular ResumeManager.handleEnded() handles threading internally.
        resumeManager.handleEnded(playbackContext)
    }

    /**
     * Evaluate kids gate state before starting playback.
     *
     * ┌──────────────────────────────────────────────────────────────────────┐
     * │ Mirrors legacy behavior at InternalPlayerScreen L547-569             │
     * └──────────────────────────────────────────────────────────────────────┘
     *
     * LEGACY PARAMETER SHAPE (implicit via remember/LaunchedEffect):
     *   store: SettingsStore (for currentProfileId)
     *   obxStore: ObxStore (for ObxProfile lookup)
     *   screenTimeRepo: ScreenTimeRepository (for remainingMinutes)
     *   exoPlayer: ExoPlayer (for playWhenReady control)
     *
     * MODULAR PARAMETER SHAPE:
     *   kidsGate: KidsPlaybackGate (encapsulates all dependencies)
     *   Returns: KidsGateState (caller uses to control playWhenReady)
     *
     * BEHAVIORAL PARITY (L547-569):
     *   - Profile detection (L554): val id = store.currentProfileId.first()
     *   - Skip if no profile (L555): if (id > 0) { ... }
     *   - Profile lookup (L556): obxStore.boxFor(ObxProfile::class.java).get(id)
     *   - Kid type check (L557): kidActive = prof?.type == "kid"
     *   - Kid ID assignment (L558): kidIdState = if (kidActive) id else null
     *   - Remaining time check (L560-562):
     *       if (kidActive) {
     *           val remain = screenTimeRepo.remainingMinutes(kidIdState!!)
     *           kidBlocked = remain <= 0
     *       }
     *   - Play control (L564-566): if (!kidBlocked) { exoPlayer.playWhenReady = true }
     *
     * PROFILE TYPE VALUES:
     *   - "kid" → kidActive = true, check screen time
     *   - "adult" or other → kidActive = false, no screen time gate
     *   - null profile → kidActive = false
     *
     * BLOCK CONDITION:
     *   - remainingMinutes(profileId) <= 0 → kidBlocked = true
     *   - Remaining time is in MINUTES (not seconds)
     *   - Daily quota is configured per kid profile
     *
     * SIDE EFFECTS (Legacy L564-566):
     *   - if (!kidBlocked): exoPlayer.playWhenReady = true
     *   - if (kidBlocked): playWhenReady NOT set (implicit pause)
     *   - Modular: Caller should check kidBlocked and set playWhenReady accordingly
     *
     * UI FEEDBACK (Legacy L2282-2290):
     *   - When kidBlocked == true, show AlertDialog
     *   - Title: "Bildschirmzeit abgelaufen" (Screen time expired)
     *   - Modular: InternalPlayerUiState.kidBlocked drives UI overlay
     *
     * EXCEPTION HANDLING (L567-569):
     *   - On any error: exoPlayer.playWhenReady = true (fail-open)
     *   - Modular: DefaultKidsPlaybackGate returns safe defaults on error
     *
     * @param kidsGate Kids playback gate instance (DefaultKidsPlaybackGate)
     * @return KidsGateState with:
     *         - kidActive: Boolean (true if kid profile)
     *         - kidBlocked: Boolean (true if screen time exhausted)
     *         - kidProfileId: Long? (profile ID if kid, null otherwise)
     *         Caller should:
     *         - Set playWhenReady = !kidBlocked
     *         - Show block overlay if kidBlocked == true
     */
    suspend fun evaluateKidsGateOnStart(
        kidsGate: KidsPlaybackGate,
    ): KidsGateState {
        // TODO(Phase 2): Mirror legacy kid gate start evaluation.
        // This determines if playback should be blocked before it begins.
        //
        // Legacy uses try/catch with fail-open (L567-569).
        // Modular KidsPlaybackGate.evaluateStart() handles exceptions internally.
        return kidsGate.evaluateStart()
    }
}

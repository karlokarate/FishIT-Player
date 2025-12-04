package com.chris.m3usuite.player.internal.domain

import android.content.Context
import com.chris.m3usuite.core.logging.UnifiedLog
import com.chris.m3usuite.data.obx.ObxProfile
import com.chris.m3usuite.data.obx.ObxStore
import com.chris.m3usuite.data.repo.ScreenTimeRepository
import com.chris.m3usuite.prefs.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Immutable snapshot of the kid gate state.
 */
data class KidsGateState(
    val kidActive: Boolean,
    val kidBlocked: Boolean,
    val kidProfileId: Long?,
    val remainingMinutes: Int? = null,
)

/**
 * Abstraktion für Kid-Profile + Screen-Time-Gate.
 *
 * Diese Schicht kennt keine UI und keinen Player, sondern nur:
 * - aktuelles Profil
 * - ob es ein Kid-Profil ist
 * - ob Screen-Time schon aufgebraucht wurde
 */
interface KidsPlaybackGate {
    /**
     * Evaluate the kid/screentime situation before starting playback.
     *
     * This is called once when the player is created and prepared.
     */
    suspend fun evaluateStart(): KidsGateState

    /**
     * Called periodically (z.B. alle 60 Sekunden), während gespielt wird.
     *
     * Returns an updated KidsGateState.
     */
    suspend fun onPlaybackTick(
        current: KidsGateState,
        deltaSecs: Int,
    ): KidsGateState
}

/**
 * Default implementation that mimics the legacy InternalPlayerScreen behaviour:
 *
 * - Uses SettingsStore.currentProfileId to find the active profile.
 * - Loads ObxProfile to decide if this is a kid profile.
 * - Uses ScreenTimeRepository.remainingMinutes / tickUsageIfPlaying.
 *
 * ════════════════════════════════════════════════════════════════════════════
 * Phase 2 Behavioral Parity Notes (vs. legacy InternalPlayerScreen)
 * ════════════════════════════════════════════════════════════════════════════
 *
 * EVALUATE START (evaluateStart):
 * ─────────────────────────────────────────────────────────────────────────────
 * Legacy Reference: InternalPlayerScreen L547-569
 *
 * TODO(Phase 2 Parity): Profile Detection
 *   - Legacy (L554): val id = store.currentProfileId.first()
 *   - Legacy (L555): if (id > 0) { ... }
 *   - This implementation: settings.currentProfileId.first()
 *   - ✓ Matches: Skips processing if profileId <= 0 (no profile selected)
 *   - Default behavior: kidActive = false, kidBlocked = false when no profile
 *
 * TODO(Phase 2 Parity): Kid Profile Type Check
 *   - Legacy (L556-557):
 *       val prof = withContext(Dispatchers.IO) { obxStore.boxFor(ObxProfile).get(id) }
 *       kidActive = prof?.type == "kid"
 *   - This implementation: profile?.type == "kid"
 *   - ✓ Matches: Only type == "kid" activates kid mode
 *   - Other types ("adult", null, etc.) → kidActive = false
 *
 * TODO(Phase 2 Parity): Daily Quota Check
 *   - Legacy (L560-562):
 *       val remain = screenTimeRepo.remainingMinutes(kidIdState!!)
 *       kidBlocked = remain <= 0
 *   - This implementation: screenTimeRepo.remainingMinutes(profileId); kidBlocked = remaining <= 0
 *   - ✓ Matches: Block when remaining minutes is zero or negative
 *   - Note: remainingMinutes() returns MINUTES, not seconds
 *   - Daily quota is configured per-profile in ScreenTimeRepository
 *
 * TODO(Phase 2 Parity): Fail-Open Exception Handling
 *   - Legacy (L567-569): try/catch wraps entire block; on error: exoPlayer.playWhenReady = true
 *   - This implementation: runCatching { box.get(profileId) }.getOrNull()
 *   - ✓ Matches: Exceptions result in kidActive = false (allow playback)
 *   - Purpose: Don't block playback due to ObjectBox errors
 *
 * PLAYBACK TICK (onPlaybackTick):
 * ─────────────────────────────────────────────────────────────────────────────
 * Legacy Reference: InternalPlayerScreen L725-744
 *
 * TODO(Phase 2 Parity): 60-Second Accumulation
 *   - Legacy (L727-728):
 *       tickAccum += 3  // Every 3-second loop iteration
 *       if (tickAccum >= 60) { ... }
 *   - This implementation receives deltaSecs directly
 *   - Caller (Phase2Integration.onPlaybackTick) is responsible for accumulation
 *   - ScreenTimeRepository.tickUsageIfPlaying receives accumulated seconds
 *
 * TODO(Phase 2 Parity): Tick Condition
 *   - Legacy (L726): if (kidActive && exoPlayer.playWhenReady && exoPlayer.isPlaying)
 *   - This implementation: if (!current.kidActive) return current
 *   - Note: Caller should only invoke onPlaybackTick when player is actively playing
 *   - Legacy tracks playWhenReady + isPlaying; modular delegates to caller
 *
 * TODO(Phase 2 Parity): Block Transitions
 *   - Legacy (L733-737):
 *       val remain = screenTimeRepo.remainingMinutes(kidId)
 *       if (remain <= 0) {
 *           exoPlayer.playWhenReady = false
 *           kidBlocked = true
 *       }
 *   - This implementation: current.copy(kidBlocked = remaining <= 0)
 *   - Caller should check kidBlocked and pause player:
 *       if (newState.kidBlocked) player.playWhenReady = false
 *
 * TODO(Phase 2 Parity): Pause/Play Event Interactions
 *   - Legacy (L742-743): tickAccum = 0 when not playing
 *   - This prevents accumulation during pause
 *   - Modular: Caller should not invoke onPlaybackTick during pause
 *   - If user pauses manually, accumulation should stop
 *   - If blocked, player is paused and accumulation naturally stops
 *
 * TODO(Phase 2 Parity): Tick Reset
 *   - Legacy (L732): tickAccum = 0 (after tickUsageIfPlaying)
 *   - Legacy (L739, 743): tickAccum = 0 (on no kidId, or not playing)
 *   - Modular: Caller (Phase2Integration) resets tickAccumSecs after tick fires
 *
 * UI FEEDBACK:
 * ─────────────────────────────────────────────────────────────────────────────
 * Legacy Reference: InternalPlayerScreen L2282-2290
 *
 * TODO(Phase 2 UI): AlertDialog when blocked
 *   - Legacy shows AlertDialog with title "Bildschirmzeit abgelaufen"
 *   - Modular: InternalPlayerUiState.kidBlocked drives UI overlay
 *   - Overlay should display blocking message and exit option
 */
class DefaultKidsPlaybackGate(
    private val context: Context,
    private val settings: SettingsStore,
) : KidsPlaybackGate {
    private val screenTimeRepo = ScreenTimeRepository(context)
    private val store get() = ObxStore.get(context)

    override suspend fun evaluateStart(): KidsGateState =
        withContext(Dispatchers.IO) {
            try {
                val profileId = settings.currentProfileId.first()
                if (profileId <= 0L) {
                    return@withContext KidsGateState(
                        kidActive = false,
                        kidBlocked = false,
                        kidProfileId = null,
                        remainingMinutes = null,
                    )
                }

                val box = store.boxFor(ObxProfile::class.java)
                val profile = runCatching { box.get(profileId) }.getOrNull()

                val kidActive = profile?.type == "kid"
                if (!kidActive) {
                    return@withContext KidsGateState(
                        kidActive = false,
                        kidBlocked = false,
                        kidProfileId = null,
                        remainingMinutes = null,
                    )
                }

                val remaining = screenTimeRepo.remainingMinutes(profileId)
                KidsGateState(
                    kidActive = true,
                    kidBlocked = remaining <= 0,
                    kidProfileId = profileId,
                    remainingMinutes = remaining,
                )
            } catch (t: Throwable) {
                UnifiedLog.log(
                    level = UnifiedLog.Level.WARN,
                    source = "player",
                    message = "KidsPlaybackGate evaluateStart fail-open",
                    details = mapOf("error" to (t.message ?: t.javaClass.simpleName)),
                )
                KidsGateState(
                    kidActive = false,
                    kidBlocked = false,
                    kidProfileId = null,
                    remainingMinutes = null,
                )
            }
        }

    override suspend fun onPlaybackTick(
        current: KidsGateState,
        deltaSecs: Int,
    ): KidsGateState {
        if (!current.kidActive) return current
        val kidId = current.kidProfileId ?: return current
        if (deltaSecs <= 0) return current

        return withContext(Dispatchers.IO) {
            runCatching {
                // Legacy: tickUsageIfPlaying() arbeitet in Minuten-Schritten
                screenTimeRepo.tickUsageIfPlaying(kidId, deltaSecs)
                val remaining = screenTimeRepo.remainingMinutes(kidId)
                current.copy(
                    kidBlocked = remaining <= 0,
                    remainingMinutes = remaining,
                )
            }.getOrElse { t ->
                UnifiedLog.log(
                    level = UnifiedLog.Level.WARN,
                    source = "player",
                    message = "KidsPlaybackGate onPlaybackTick fail-open",
                    details = mapOf("error" to (t.message ?: t.javaClass.simpleName)),
                )
                current
            }
        }
    }
}

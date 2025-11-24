package com.chris.m3usuite.player.internal.domain

import android.content.Context
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
 */
class DefaultKidsPlaybackGate(
    private val context: Context,
    private val settings: SettingsStore,
) : KidsPlaybackGate {

    private val screenTimeRepo = ScreenTimeRepository(context)
    private val store get() = ObxStore.get(context)

    override suspend fun evaluateStart(): KidsGateState =
        withContext(Dispatchers.IO) {
            val profileId = settings.currentProfileId.first()
            if (profileId <= 0L) {
                return@withContext KidsGateState(
                    kidActive = false,
                    kidBlocked = false,
                    kidProfileId = null,
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
                )
            }

            val remaining = screenTimeRepo.remainingMinutes(profileId)
            KidsGateState(
                kidActive = true,
                kidBlocked = remaining <= 0,
                kidProfileId = profileId,
            )
        }

    override suspend fun onPlaybackTick(
        current: KidsGateState,
        deltaSecs: Int,
    ): KidsGateState {
        if (!current.kidActive) return current
        val kidId = current.kidProfileId ?: return current
        if (deltaSecs <= 0) return current

        return withContext(Dispatchers.IO) {
            // Legacy: tickUsageIfPlaying() arbeitet in Minuten-Schritten
            screenTimeRepo.tickUsageIfPlaying(kidId, deltaSecs)
            val remaining = screenTimeRepo.remainingMinutes(kidId)
            current.copy(kidBlocked = remaining <= 0)
        }
    }
}
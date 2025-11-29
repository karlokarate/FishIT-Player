package com.chris.m3usuite.playback

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Composable that observes Activity/Fragment lifecycle and wires it to PlaybackSession.
 *
 * ════════════════════════════════════════════════════════════════════════════════
 * PHASE 8 – PlaybackLifecycleController
 * ════════════════════════════════════════════════════════════════════════════════
 *
 * This composable should be placed high in the composition tree (e.g., in MainActivity
 * or HomeChromeScaffold) to ensure lifecycle events are properly forwarded to PlaybackSession.
 *
 * **Lifecycle Behavior:**
 * - ON_RESUME: Calls PlaybackSession.onAppForeground() for warm resume
 * - ON_PAUSE: Calls PlaybackSession.onAppBackground()
 * - ON_STOP: No action (app may continue audio in background)
 * - ON_DESTROY: No action (PlaybackSession release is handled separately)
 *
 * **Warm Resume Contract (Phase 8):**
 * When the app returns to foreground:
 * - DO NOT recreate ExoPlayer
 * - Re-bind UI surfaces (PlayerSurface, MiniPlayerOverlay) to existing session
 * - Restore lifecycle state to PLAYING or PAUSED based on current player state
 *
 * **Contract Reference:**
 * - INTERNAL_PLAYER_PHASE8_PERFORMANCE_LIFECYCLE_CONTRACT.md Section 4.3
 */
@Composable
fun PlaybackLifecycleController() {
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    // App returned to foreground - warm resume
                    PlaybackSession.onAppForeground()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    // App going to background
                    PlaybackSession.onAppBackground()
                }
                // ON_STOP: Keep player alive for potential background audio
                // ON_DESTROY: Player release is handled by app-level logic, not lifecycle
                else -> { /* No action for other events */ }
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}

/**
 * Non-composable helper for lifecycle events when Compose is not available.
 *
 * This object can be called directly from Activity lifecycle methods
 * when the lifecycle observer pattern is not suitable.
 */
object PlaybackLifecycleHelper {
    /**
     * Call from Activity.onResume() to handle warm resume.
     */
    fun onActivityResumed() {
        PlaybackSession.onAppForeground()
    }

    /**
     * Call from Activity.onPause() to handle backgrounding.
     */
    fun onActivityPaused() {
        PlaybackSession.onAppBackground()
    }

    /**
     * Call from Activity.onDestroy() when the activity is being fully destroyed
     * and no playback should continue.
     *
     * **Note:** Only call this when:
     * - App is closing completely
     * - No MiniPlayer or PiP should continue
     * - User has explicitly stopped playback
     *
     * Do NOT call this on configuration changes or navigation.
     */
    fun onActivityDestroyed(shouldReleasePlayer: Boolean) {
        if (shouldReleasePlayer) {
            PlaybackSession.release()
        }
    }

    /**
     * Check if PlaybackSession is in a state that allows warm resume.
     */
    fun canWarmResume(): Boolean = PlaybackSession.canResume
}

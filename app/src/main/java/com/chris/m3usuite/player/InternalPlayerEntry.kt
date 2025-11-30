package com.chris.m3usuite.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.chris.m3usuite.core.logging.AppLog
import com.chris.m3usuite.core.playback.rememberPlayerController
import com.chris.m3usuite.player.internal.domain.PlaybackContext
import com.chris.m3usuite.player.internal.session.rememberInternalPlayerSession
import com.chris.m3usuite.player.internal.state.InternalPlayerController
import com.chris.m3usuite.player.internal.state.InternalPlayerUiState
import com.chris.m3usuite.player.internal.system.InternalPlayerSystemUi
import com.chris.m3usuite.player.internal.ui.InternalPlayerContent
import com.chris.m3usuite.player.miniplayer.DefaultMiniPlayerManager
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.ui.focus.FocusKit
import com.chris.m3usuite.ui.util.ImageHeaders

/**
 * Phase 9 entry point for the internal player.
 *
 * ════════════════════════════════════════════════════════════════════════════════════════════════════
 * PHASE 9 – TASK 1: SIP-ONLY ACTIVATION (Legacy Disabled)
 * ════════════════════════════════════════════════════════════════════════════════════════════════════
 *
 * This entry point now **always** routes to the SIP (Simplified Internal Player) architecture
 * for both DEBUG and RELEASE builds. The legacy InternalPlayerScreen is no longer used by
 * this entry point but remains in the codebase for historical reference.
 *
 * **SIP Architecture Components:**
 * - `rememberInternalPlayerSession`: Manages ExoPlayer via PlaybackSession singleton
 * - `InternalPlayerContent`: Renders player UI with controls, overlays, and gestures
 * - `InternalPlayerSystemUi`: Handles back navigation, screen-on, and fullscreen
 * - `InternalPlayerController`: Callbacks for UI interaction
 *
 * **Key Benefits over Legacy:**
 * - Single ExoPlayer instance via PlaybackSession (MiniPlayer continuity)
 * - Modular architecture (subtitles, live TV, trickplay as separate modules)
 * - Hot/Cold state split for better Compose performance
 * - Full test coverage from Phases 4-8
 *
 * **Legacy Status:**
 * - `InternalPlayerScreen` remains in codebase (not deleted)
 * - Legacy is NOT instantiated by this entry point anymore
 * - Future Phase 9 Task 2 will remove legacy completely
 *
 * @param url The media URL to play
 * @param startMs Optional start position in milliseconds
 * @param mimeType Optional explicit MIME type
 * @param headers HTTP headers for the stream
 * @param mediaItem Pre-resolved MediaItem for Telegram content
 * @param playbackContext Typed playback context (VOD/SERIES/LIVE + metadata)
 * @param onExit Callback when exiting the player
 */
@androidx.media3.common.util.UnstableApi
@Composable
fun InternalPlayerEntry(
    url: String,
    startMs: Long?,
    mimeType: String?,
    headers: Map<String, String> = emptyMap(),
    mediaItem: com.chris.m3usuite.model.MediaItem?,
    playbackContext: PlaybackContext,
    onExit: () -> Unit,
) {
    // ════════════════════════════════════════════════════════════════════════════════
    // Phase 9 Task 1 Step 3: Log SIP path activation
    // ════════════════════════════════════════════════════════════════════════════════
    LaunchedEffect(Unit) {
        AppLog.log(
            category = "PLAYER_ROUTE",
            level = AppLog.Level.DEBUG,
            message = "Using SIP player path (legacy disabled)",
            extras = mapOf("source" to "InternalPlayerEntry"),
        )
    }

    // ════════════════════════════════════════════════════════════════════════════════
    // Phase 9: SIP Player Implementation
    // ════════════════════════════════════════════════════════════════════════════════
    val ctx = LocalContext.current
    val isTv = remember(ctx) { FocusKit.isTvDevice(ctx) }
    val settings = remember(ctx) { SettingsStore(ctx) }

    // Convert headers map to ImageHeaders
    val networkHeaders =
        remember(headers) {
            val ua = headers["User-Agent"] ?: headers["user-agent"] ?: ""
            val ref = headers["Referer"] ?: headers["referer"] ?: ""
            val extras = headers.filterKeys { it.lowercase() !in setOf("user-agent", "referer") }
            ImageHeaders(ua = ua, referer = ref, extras = extras)
        }

    val controller = rememberPlayerController()

    // UI state managed by the SIP session
    var uiState by remember { mutableStateOf(InternalPlayerUiState(playbackType = playbackContext.type)) }

    // Create the SIP session and get the ExoPlayer instance
    val player =
        rememberInternalPlayerSession(
            context = ctx,
            url = url,
            startMs = startMs,
            mimeType = mimeType,
            preparedMediaItem = mediaItem,
            playbackContext = playbackContext,
            settings = settings,
            networkHeaders = networkHeaders,
            controller = controller,
            onStateChanged = { newState -> uiState = newState },
            onError = { /* Handled via PlaybackSession.playbackError in InternalPlayerContent */ },
        )

    // Create the controller with all callbacks
    val playerController =
        remember(player, uiState) {
            createSipController(
                player = player,
                currentState = uiState,
                onStateUpdate = { uiState = it },
                onExit = onExit,
            )
        }

    // System UI (back handler, screen-on, fullscreen)
    InternalPlayerSystemUi(
        isPlaying = uiState.isPlaying,
        onClose = onExit,
    )

    // Main player content with black background
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black),
    ) {
        InternalPlayerContent(
            player = player,
            state = uiState,
            controller = playerController,
            isTv = isTv,
        )
    }
}

/**
 * Creates an InternalPlayerController with all SIP callbacks wired up.
 *
 * This replaces the complex callback setup that was embedded in the legacy InternalPlayerScreen.
 * Each callback maps to the appropriate session/player operation.
 */
@androidx.media3.common.util.UnstableApi
private fun createSipController(
    player: androidx.media3.exoplayer.ExoPlayer?,
    currentState: InternalPlayerUiState,
    onStateUpdate: (InternalPlayerUiState) -> Unit,
    onExit: () -> Unit,
): InternalPlayerController =
    InternalPlayerController(
        onPlayPause = {
            player?.let {
                if (it.isPlaying) it.pause() else it.play()
            }
        },
        onSeekTo = { positionMs ->
            player?.seekTo(positionMs.coerceAtLeast(0L))
        },
        onSeekBy = { deltaMs ->
            player?.let {
                val target = (it.currentPosition + deltaMs).coerceAtLeast(0L)
                it.seekTo(target)
            }
        },
        onChangeSpeed = { speed ->
            player?.playbackParameters = androidx.media3.common.PlaybackParameters(speed)
            onStateUpdate(currentState.copy(playbackSpeed = speed))
        },
        onToggleLoop = {
            player?.let {
                val looping = it.repeatMode != androidx.media3.common.Player.REPEAT_MODE_ONE
                it.repeatMode =
                    if (looping) {
                        androidx.media3.common.Player.REPEAT_MODE_ONE
                    } else {
                        androidx.media3.common.Player.REPEAT_MODE_OFF
                    }
                onStateUpdate(currentState.copy(isLooping = looping))
            }
        },
        onEnterPip = {
            // Phase 7: MiniPlayer is handled via onEnterMiniPlayer callback
        },
        onToggleSettingsDialog = {
            onStateUpdate(currentState.copy(showSettingsDialog = !currentState.showSettingsDialog))
        },
        onToggleTracksDialog = {
            onStateUpdate(currentState.copy(showTracksDialog = !currentState.showTracksDialog))
        },
        onToggleSpeedDialog = {
            onStateUpdate(currentState.copy(showSpeedDialog = !currentState.showSpeedDialog))
        },
        onToggleSleepTimerDialog = {
            onStateUpdate(currentState.copy(showSleepTimerDialog = !currentState.showSleepTimerDialog))
        },
        onToggleDebugInfo = {
            onStateUpdate(currentState.copy(showDebugInfo = !currentState.showDebugInfo))
        },
        onCycleAspectRatio = {
            val nextMode = currentState.aspectRatioMode.next()
            onStateUpdate(currentState.copy(aspectRatioMode = nextMode))
        },
        onJumpLiveChannel = { /* Handled by LivePlaybackController in session */ },
        onToggleCcMenu = {
            onStateUpdate(currentState.copy(showCcMenuDialog = !currentState.showCcMenuDialog))
        },
        onSelectSubtitleTrack = { /* Handled by SubtitleSelectionPolicy in session */ },
        onUpdateSubtitleStyle = { /* Handled by SubtitleStyleManager in session */ },
        onApplySubtitlePreset = { /* Handled by SubtitleStyleManager in session */ },
        onStartTrickplay = { direction ->
            val speed = if (direction > 0) 2f else -2f
            onStateUpdate(currentState.copy(trickplayActive = true, trickplaySpeed = speed))
        },
        onStopTrickplay = { applyPosition ->
            onStateUpdate(currentState.copy(trickplayActive = false, trickplaySpeed = 1f))
        },
        onCycleTrickplaySpeed = {
            if (currentState.trickplayActive) {
                val speeds = listOf(2f, 3f, 5f)
                val absSpeed = kotlin.math.abs(currentState.trickplaySpeed)
                val currentIndex = speeds.indexOfFirst { it == absSpeed }
                // If current speed is not in the list, default to first speed
                val nextIndex = if (currentIndex == -1) 0 else (currentIndex + 1) % speeds.size
                val sign = if (currentState.trickplaySpeed > 0) 1f else -1f
                onStateUpdate(currentState.copy(trickplaySpeed = speeds[nextIndex] * sign))
            }
        },
        onStepSeek = { deltaMs ->
            player?.let {
                val target = (it.currentPosition + deltaMs).coerceAtLeast(0L)
                it.seekTo(target)
            }
        },
        onToggleControlsVisibility = {
            onStateUpdate(
                currentState.copy(
                    controlsVisible = !currentState.controlsVisible,
                    controlsTick = currentState.controlsTick + 1,
                ),
            )
        },
        onUserInteraction = {
            onStateUpdate(
                currentState.copy(
                    controlsVisible = true,
                    controlsTick = currentState.controlsTick + 1,
                ),
            )
        },
        onHideControls = {
            onStateUpdate(currentState.copy(controlsVisible = false))
        },
        onEnterMiniPlayer = {
            // Phase 7: Enter MiniPlayer mode
            // The fromRoute parameter should be the current player route for return navigation
            DefaultMiniPlayerManager.enterMiniPlayer(fromRoute = "player")
            onExit()
        },
    )

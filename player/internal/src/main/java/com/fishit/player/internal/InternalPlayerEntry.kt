package com.fishit.player.internal

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.ui.PlayerView
import com.fishit.player.core.model.PlaybackContext
import com.fishit.player.internal.session.InternalPlayerSession
import com.fishit.player.internal.source.DefaultPlaybackSourceResolver
import com.fishit.player.internal.ui.InternalPlayerControls
import com.fishit.player.internal.ui.PlayerSurface
import com.fishit.player.playback.domain.KidsPlaybackGate
import com.fishit.player.playback.domain.ResumeManager
import kotlinx.coroutines.delay

/**
 * Public entry point for the internal player.
 *
 * This Composable sets up and manages the entire player experience:
 * - Creates the player session
 * - Renders the video surface
 * - Shows controls overlay
 * - Handles lifecycle
 *
 * @param playbackContext The context defining what to play.
 * @param resumeManager Manager for resume positions.
 * @param kidsPlaybackGate Gate for kids screen time.
 * @param onBack Callback when user wants to exit the player.
 * @param modifier Modifier for the player container.
 */
@Composable
fun InternalPlayerEntry(
    playbackContext: PlaybackContext,
    resumeManager: ResumeManager,
    kidsPlaybackGate: KidsPlaybackGate,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Create the session
    val session = remember(playbackContext) {
        InternalPlayerSession(
            context = context,
            sourceResolver = DefaultPlaybackSourceResolver(),
            resumeManager = resumeManager,
            kidsPlaybackGate = kidsPlaybackGate
        )
    }

    // Create PlayerView
    val playerView = remember {
        PlayerView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            useController = false
        }
    }

    val state by session.state.collectAsState()

    // Initialize playback
    LaunchedEffect(playbackContext) {
        session.initialize(playbackContext)
    }

    // Auto-hide controls
    LaunchedEffect(state.areControlsVisible, state.isPlaying) {
        if (state.areControlsVisible && state.isPlaying) {
            delay(4000)
            session.setControlsVisible(false)
        }
    }

    // Lifecycle handling
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    // Save resume position when backgrounding
                    kotlinx.coroutines.runBlocking {
                        session.saveResumePosition()
                    }
                }
                Lifecycle.Event.ON_DESTROY -> {
                    session.destroy()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            session.destroy()
        }
    }

    // UI
    androidx.compose.foundation.layout.Box(
        modifier = modifier.fillMaxSize()
    ) {
        PlayerSurface(
            state = state,
            playerViewProvider = { playerView },
            modifier = Modifier.fillMaxSize()
        )

        InternalPlayerControls(
            state = state,
            onTogglePlayPause = { session.togglePlayPause() },
            onSeekForward = { session.seekForward() },
            onSeekBackward = { session.seekBackward() },
            onSeekTo = { session.seekTo(it) },
            onToggleMute = { session.toggleMute() },
            onTapSurface = { session.toggleControls() },
            modifier = Modifier.fillMaxSize()
        )
    }
}

package com.fishit.player.player.ui.internal

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
import com.fishit.player.internal.session.InternalPlayerSession
import com.fishit.player.internal.ui.InternalPlayerControls
import com.fishit.player.internal.ui.PlayerSurface
import kotlinx.coroutines.delay

/**
 * Internal player surface renderer.
 *
 * This composable is internal to player:ui and bridges between
 * PlayerEntryPoint abstraction and actual player UI rendering.
 *
 * @param session The active internal player session
 * @param onExit Callback when user wants to exit
 * @param modifier Modifier for the container
 */
@Composable
internal fun InternalPlayerSurface(
    session: InternalPlayerSession,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

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

    LaunchedEffect(state.areControlsVisible, state.isPlaying) {
        if (state.areControlsVisible && state.isPlaying) {
            delay(4000)
            session.setControlsVisible(false)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    kotlinx.coroutines.runBlocking { session.saveResumePosition() }
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
        }
    }

    androidx.compose.foundation.layout.Box(modifier = modifier.fillMaxSize()) {
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

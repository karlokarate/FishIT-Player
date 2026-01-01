package com.fishit.player.internal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import com.fishit.player.core.playermodel.PlaybackState
import com.fishit.player.internal.R
import com.fishit.player.internal.state.InternalPlayerState

/**
 * The video playback surface using Media3 PlayerView.
 *
 * Displays the video content and handles aspect ratio.
 */
@Composable
fun PlayerSurface(
    state: InternalPlayerState,
    playerViewProvider: () -> PlayerView?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        // Video surface
        AndroidView(
            factory = { ctx ->
                playerViewProvider()?.apply {
                    useController = false // We use custom controls
                } ?: PlayerView(ctx).apply {
                    useController = false
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Buffering overlay
        if (state.playbackState == PlaybackState.BUFFERING) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        // Error overlay
        if (state.playbackState == PlaybackState.ERROR) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = state.error?.message ?: stringResource(R.string.player_error_generic),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

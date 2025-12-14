package com.fishit.player.player.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.fishit.player.core.playermodel.PlaybackContext

/**
 * Public Player UI API for FishIT Player v2.
 *
 * This is the stable public entry point for rendering the player UI.
 * App modules (app-v2, features) MUST use this screen instead of InternalPlayerEntry.
 *
 * Architecture:
 * - Lives in player:ui (public API module)
 * - Uses PlayerEntryPoint abstraction from playback:domain
 * - All engine wiring (resolver, resume, kids gate, codec) encapsulated in PlayerEntryPoint
 * - app-v2 has zero imports from player:internal
 *
 * @param context Source-agnostic playback descriptor
 * @param onExit Callback when user wants to exit the player
 * @param modifier Modifier for the player container
 */
@Composable
fun PlayerScreen(
    context: PlaybackContext,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerUiViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    // Start playback on first composition or context change
    LaunchedEffect(context.canonicalId) {
        viewModel.startPlayback(context)
    }

    when {
        state.isLoading -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        state.error != null -> {
            LaunchedEffect(state.error) {
                onExit()
            }
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = state.error ?: "Unable to start playback")
            }
        }

        state.isReady -> {
            // Render player UI via PlayerEntryPoint
            // All engine dependencies (resolver, resume, kids, codec) are
            // encapsulated in the PlayerEntryPoint implementation
            viewModel.playerEntryPoint.RenderPlayerUi(
                onExit = onExit,
                modifier = modifier
            )
        }
    }
}

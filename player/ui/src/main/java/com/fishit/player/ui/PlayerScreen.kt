package com.fishit.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fishit.player.core.playermodel.PlaybackContext

/**
 * Public entry point for the player UI.
 *
 * This composable provides a clean, decoupled interface for playback:
 * - Receives only a [PlaybackContext] (source-agnostic)
 * - Uses [PlayerEntryPoint] internally to start playback
 * - No direct dependencies on engine wiring (resolver, resume, kids, codec)
 * - Uses @HiltViewModel for dependency injection (no EntryPoints)
 *
 * **Architecture:**
 * - UI Layer (player:ui) - This composable
 * - Domain Layer (playback:domain) - PlayerEntryPoint interface
 * - Internal Layer (player:internal) - PlayerEntryPoint implementation
 *
 * @param context Source-agnostic playback descriptor
 * @param onExit Callback when user exits the player
 * @param modifier Modifier for the player container
 */
@Composable
fun PlayerScreen(
    context: PlaybackContext,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: PlayerUiViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()

    // Start playback only when canonicalId actually changes
    androidx.compose.runtime.LaunchedEffect(context.canonicalId) {
        // Use a remembered variable to avoid unnecessary restarts
        val lastStartedId = androidx.compose.runtime.remember { mutableStateOf<String?>(null) }
        if (lastStartedId.value != context.canonicalId) {
            viewModel.start(context)
            lastStartedId.value = context.canonicalId
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when (val currentState = state) {
            is PlayerUiState.Idle -> {
                // Initial state - should transition to Loading quickly
            }

            is PlayerUiState.Loading -> {
                LoadingView()
            }

            is PlayerUiState.Playing -> {
                PlayingView(context = context)
            }

            is PlayerUiState.Error -> {
                ErrorView(
                    message = currentState.message,
                    onRetry = { viewModel.retry() }
                )
            }
        }

        // Back button (always visible in top-left)
        IconButton(
            onClick = onExit,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Back",
                tint = Color.White
            )
        }
    }
}

@Composable
private fun LoadingView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Color.White)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Starting playback...",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White
            )
        }
    }
}

@Composable
private fun PlayingView(context: PlaybackContext) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Placeholder for video surface
        // The actual surface will be integrated in Phase 2 via clean interface split
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "🎬 Playing",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = context.title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = context.canonicalId,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun ErrorView(
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "❌ Playback Error",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}

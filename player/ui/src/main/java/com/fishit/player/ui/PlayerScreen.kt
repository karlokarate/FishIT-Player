package com.fishit.player.ui

import android.view.ViewGroup
import android.widget.FrameLayout
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.fishit.player.core.playermodel.AudioSelectionState
import com.fishit.player.core.playermodel.PlaybackContext
import com.fishit.player.core.playermodel.SubtitleSelectionState
import com.fishit.player.internal.state.InternalPlayerState
import com.fishit.player.internal.ui.InternalPlayerControls
import com.fishit.player.ui.sheets.AudioTrackSheet
import com.fishit.player.ui.sheets.SpeedSelectionSheet
import com.fishit.player.ui.sheets.SubtitleTrackSheet

/**
 * Public entry point for the player UI.
 *
 * This composable provides a clean, decoupled interface for playback:
 * - Receives only a [PlaybackContext] (source-agnostic)
 * - Uses [PlayerEntryPoint] internally to start playback
 * - Displays actual video via Media3 [PlayerView]
 * - Supports both Xtream (HTTP) and Telegram (TDLib streaming) playback
 *
 * **Architecture:**
 * - UI Layer (player:ui) - This composable
 * - Domain Layer (playback:domain) - PlayerEntryPoint interface
 * - Internal Layer (player:internal) - PlayerEntryPoint implementation + ExoPlayer
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

    // Track the last started ID to avoid unnecessary restarts
    val lastStartedId = remember { mutableStateOf<String?>(null) }

    // Start playback only when canonicalId actually changes
    LaunchedEffect(context.canonicalId) {
        if (lastStartedId.value != context.canonicalId) {
            viewModel.start(context)
            lastStartedId.value = context.canonicalId
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Black),
    ) {
        when (val currentState = state) {
            is PlayerUiState.Idle -> {
                // Initial state - should transition to Loading quickly
            }

            is PlayerUiState.Loading -> {
                LoadingView()
            }

            is PlayerUiState.Playing -> {
                PlayingView(
                    viewModel = viewModel,
                    context = context,
                )
            }

            is PlayerUiState.Error -> {
                ErrorView(
                    message = currentState.message,
                    onRetry = { viewModel.retry() },
                )
            }
        }

        // Back button (always visible in top-left)
        IconButton(
            onClick = onExit,
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp),
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
            )
        }
    }
}

@Composable
private fun LoadingView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Color.White)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Starting playback...",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
            )
        }
    }
}

/**
 * Playing view with actual video surface via Media3 PlayerView.
 *
 * **Integration:**
 * - Gets ExoPlayer instance from ViewModel
 * - Attaches to PlayerView via AndroidView (useController = false)
 * - Shows custom InternalPlayerControls overlay for consistent UX
 *
 * **Custom Controls Features:**
 * - Play/Pause, Seek ±10s
 * - Progress slider with time display
 * - Mute toggle
 * - Live indicator for streams
 * - Animated visibility (tap to show/hide)
 */
@Composable
private fun PlayingView(
    viewModel: PlayerUiViewModel,
    context: PlaybackContext,
) {
    val player: Player? = viewModel.getPlayer()

    // Observe internal player state for custom controls
    val sessionStateFlow = viewModel.getSessionState()
    val sessionState: InternalPlayerState =
        sessionStateFlow
            ?.collectAsStateWithLifecycle(initialValue = InternalPlayerState.INITIAL)
            ?.value
            ?: InternalPlayerState.INITIAL

    // Observe audio/subtitle state for track selection
    val audioStateFlow = viewModel.getAudioState()
    val audioState: AudioSelectionState =
        audioStateFlow
            ?.collectAsStateWithLifecycle(initialValue = AudioSelectionState())
            ?.value
            ?: AudioSelectionState()

    val subtitleStateFlow = viewModel.getSubtitleState()
    val subtitleState: SubtitleSelectionState =
        subtitleStateFlow
            ?.collectAsStateWithLifecycle(initialValue = SubtitleSelectionState())
            ?.value
            ?: SubtitleSelectionState()

    // Sheet visibility state
    var showAudioSheet by remember { mutableStateOf(false) }
    var showSubtitleSheet by remember { mutableStateOf(false) }
    var showSpeedSheet by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (player != null) {
            // Video surface via Media3 PlayerView (no built-in controls)
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        layoutParams =
                            FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                        // Disable Media3 built-in controls - we use custom controls
                        useController = false
                        setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    }
                },
                update = { playerView ->
                    playerView.player = player
                },
                modifier = Modifier.fillMaxSize(),
            )

            // Custom controls overlay
            InternalPlayerControls(
                state = sessionState,
                onTogglePlayPause = viewModel::togglePlayPause,
                onSeekForward = viewModel::seekForward,
                onSeekBackward = viewModel::seekBackward,
                onSeekTo = viewModel::seekTo,
                onToggleMute = viewModel::toggleMute,
                onTapSurface = viewModel::toggleControls,
                onHideControls = viewModel::hideControls,
                // Track selection callbacks (Phase 6/7 wiring)
                onAudioTrackClick = { showAudioSheet = true },
                onSubtitleClick = { showSubtitleSheet = true },
                onSpeedClick = { showSpeedSheet = true },
                // State indicators
                hasSubtitles = subtitleState.selectedTrack != null,
                hasMultipleAudioTracks = audioState.availableTracks.size > 1,
            )

            // Audio Track Selection Sheet
            if (showAudioSheet) {
                AudioTrackSheet(
                    audioState = audioState,
                    onSelectTrack = { trackId ->
                        viewModel.selectAudioTrack(trackId)
                        showAudioSheet = false
                    },
                    onDismiss = { showAudioSheet = false },
                )
            }

            // Subtitle Selection Sheet
            if (showSubtitleSheet) {
                SubtitleTrackSheet(
                    subtitleState = subtitleState,
                    onSelectTrack = { trackId ->
                        viewModel.selectSubtitleTrack(trackId)
                        showSubtitleSheet = false
                    },
                    onDisableSubtitles = {
                        viewModel.disableSubtitles()
                        showSubtitleSheet = false
                    },
                    onDismiss = { showSubtitleSheet = false },
                )
            }

            // Speed Selection Sheet
            if (showSpeedSheet) {
                SpeedSelectionSheet(
                    currentSpeed = sessionState.playbackSpeed,
                    onSelectSpeed = { speed ->
                        viewModel.setPlaybackSpeed(speed)
                        showSpeedSheet = false
                    },
                    onDismiss = { showSpeedSheet = false },
                )
            }

            // Cleanup when composable is disposed
            DisposableEffect(player) {
                onDispose {
                    // Player cleanup is handled by ViewModel/Session
                }
            }
        } else {
            // Fallback when player is not yet available
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp),
                ) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Preparing video...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = context.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorView(
    message: String,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Text(
                text = "❌ Playback Error",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f),
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}

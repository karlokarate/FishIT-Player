package com.fishit.player.feature.home.debug

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fishit.player.core.playermodel.PlaybackContext
import com.fishit.player.core.playermodel.SourceType
import com.fishit.player.ui.PlayerScreen

/**
 * Debug screen for Phase 1 playback testing.
 *
 * This screen provides a simple way to test the player
 * without needing Telegram or Xtream sources configured.
 *
 * **Test Flow:**
 * 1. Click "Play Test Stream" button
 * 2. Player loads Big Buck Bunny (or any available test stream)
 * 3. Test player controls (play/pause, seek, mute)
 *
 * **Architecture:**
 * - Uses PlayerScreen from player:ui (clean API)
 * - No direct dependencies on player:internal
 * - PlayerScreen handles all playback wiring internally
 */
@Composable
fun DebugPlaybackScreen(
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showPlayer by remember { mutableStateOf(false) }

    if (showPlayer) {
        // Show the actual player
        val testContext = remember {
            PlaybackContext(
                canonicalId = "debug-test-stream",
                title = "Big Buck Bunny (Test)",
                sourceType = SourceType.HTTP,
                uri = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
            )
        }

        PlayerScreen(
            context = testContext,
            onExit = {
                showPlayer = false
                onBack()
            },
            modifier = modifier.fillMaxSize()
        )
    } else {
        // Show the menu to start playback
        DebugPlaybackMenu(
            onPlayTestStream = { showPlayer = true },
            onBack = onBack,
            modifier = modifier
        )
    }
}

/**
 * Menu screen with options to start debug playback.
 */
@Composable
private fun DebugPlaybackMenu(
    onPlayTestStream: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "Debug Playback",
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Test the player with a sample video stream.",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(onClick = onPlayTestStream) {
                Text("▶ Play Test Stream (Big Buck Bunny)")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = onBack) {
                Text("← Back")
            }
        }
    }
}

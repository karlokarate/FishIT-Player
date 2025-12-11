package com.fishit.player.feature.home.debug

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.fishit.player.playback.domain.KidsPlaybackGate
import com.fishit.player.playback.domain.ResumeManager

/**
 * Debug screen for Phase 1 playback testing.
 *
 * NOTE: This is a placeholder. The actual debug playback screen
 * requires NextlibCodecConfigurator which needs to be injected via Hilt.
 * For now, we show a simple placeholder.
 */
@Composable
fun DebugPlaybackScreen(
    resumeManager: ResumeManager,
    kidsPlaybackGate: KidsPlaybackGate,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("Debug Playback Screen - Placeholder")
    }
}

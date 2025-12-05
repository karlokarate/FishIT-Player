package com.fishit.player.feature.home.debug

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.fishit.player.core.model.PlaybackContext
import com.fishit.player.core.model.PlaybackType
import com.fishit.player.internal.InternalPlayerEntry
import com.fishit.player.playback.domain.KidsPlaybackGate
import com.fishit.player.playback.domain.ResumeManager

/**
 * Debug screen for Phase 1 playback testing.
 *
 * This screen:
 * - Creates a test PlaybackContext
 * - Launches InternalPlayerEntry
 * - Plays a test HTTP stream (Big Buck Bunny)
 */
@Composable
fun DebugPlaybackScreen(
    resumeManager: ResumeManager,
    kidsPlaybackGate: KidsPlaybackGate,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Create a simple test context
    val testContext = PlaybackContext(
        type = PlaybackType.VOD,
        uri = "", // Empty - will use hardcoded test URL
        title = "Big Buck Bunny",
        subtitle = "Test Video - Phase 1",
        contentId = "test_video_001"
    )

    Box(modifier = modifier.fillMaxSize()) {
        InternalPlayerEntry(
            playbackContext = testContext,
            resumeManager = resumeManager,
            kidsPlaybackGate = kidsPlaybackGate,
            onBack = onBack,
            modifier = Modifier.fillMaxSize()
        )
    }
}

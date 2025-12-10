package com.fishit.player.feature.home.debug

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.fishit.player.core.playermodel.PlaybackContext
import com.fishit.player.core.playermodel.SourceType
import com.fishit.player.internal.InternalPlayerEntry
import com.fishit.player.internal.source.PlaybackSourceResolver
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
    sourceResolver: PlaybackSourceResolver,
    resumeManager: ResumeManager,
    kidsPlaybackGate: KidsPlaybackGate,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Create a simple test context using HTTP source type
    val testContext = PlaybackContext.testVod(
        url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
        title = "Big Buck Bunny"
    )

    Box(modifier = modifier.fillMaxSize()) {
        InternalPlayerEntry(
            playbackContext = testContext,
            sourceResolver = sourceResolver,
            resumeManager = resumeManager,
            kidsPlaybackGate = kidsPlaybackGate,
            onBack = onBack,
            modifier = Modifier.fillMaxSize()
        )
    }
}

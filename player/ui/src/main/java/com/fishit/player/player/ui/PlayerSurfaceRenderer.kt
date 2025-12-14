package com.fishit.player.player.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.fishit.player.core.playermodel.PlaybackContext
import com.fishit.player.internal.InternalPlayerEntry
import com.fishit.player.internal.source.PlaybackSourceResolver
import com.fishit.player.nextlib.NextlibCodecConfigurator
import com.fishit.player.playback.domain.KidsPlaybackGate
import com.fishit.player.playback.domain.ResumeManager
import javax.inject.Inject

/**
 * Renders the actual player surface.
 *
 * This is an internal implementation detail of player:ui.
 * Dependencies are injected via Hilt so app-v2 doesn't need to pass them.
 */
@Composable
internal fun PlayerSurfaceRenderer(
    context: PlaybackContext,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    dependencies: PlayerDependencies = rememberPlayerDependencies()
) {
    InternalPlayerEntry(
        playbackContext = context,
        sourceResolver = dependencies.sourceResolver,
        resumeManager = dependencies.resumeManager,
        kidsPlaybackGate = dependencies.kidsPlaybackGate,
        codecConfigurator = dependencies.codecConfigurator,
        onBack = onExit,
        modifier = modifier
    )
}

/**
 * Container for player dependencies injected via Hilt.
 */
data class PlayerDependencies(
    val sourceResolver: PlaybackSourceResolver,
    val resumeManager: ResumeManager,
    val kidsPlaybackGate: KidsPlaybackGate,
    val codecConfigurator: NextlibCodecConfigurator
)

/**
 * Provides player dependencies via Hilt injection.
 */
@Composable
fun rememberPlayerDependencies(): PlayerDependencies {
    return PlayerDependencies(
        sourceResolver = androidx.compose.ui.platform.LocalContext.current.let { context ->
            // Get from Hilt entry point
            dagger.hilt.android.EntryPointAccessors.fromApplication(
                context.applicationContext,
                PlayerDependenciesEntryPoint::class.java
            ).sourceResolver()
        },
        resumeManager = androidx.compose.ui.platform.LocalContext.current.let { context ->
            dagger.hilt.android.EntryPointAccessors.fromApplication(
                context.applicationContext,
                PlayerDependenciesEntryPoint::class.java
            ).resumeManager()
        },
        kidsPlaybackGate = androidx.compose.ui.platform.LocalContext.current.let { context ->
            dagger.hilt.android.EntryPointAccessors.fromApplication(
                context.applicationContext,
                PlayerDependenciesEntryPoint::class.java
            ).kidsPlaybackGate()
        },
        codecConfigurator = androidx.compose.ui.platform.LocalContext.current.let { context ->
            dagger.hilt.android.EntryPointAccessors.fromApplication(
                context.applicationContext,
                PlayerDependenciesEntryPoint::class.java
            ).codecConfigurator()
        }
    )
}

/**
 * Hilt entry point for accessing player dependencies.
 */
@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface PlayerDependenciesEntryPoint {
    fun sourceResolver(): PlaybackSourceResolver
    fun resumeManager(): ResumeManager
    fun kidsPlaybackGate(): KidsPlaybackGate
    fun codecConfigurator(): NextlibCodecConfigurator
}

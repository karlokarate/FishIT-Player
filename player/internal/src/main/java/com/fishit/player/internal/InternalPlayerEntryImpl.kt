package com.fishit.player.internal

import android.content.Context
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
import com.fishit.player.core.playermodel.PlaybackContext
import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.internal.session.InternalPlayerSession
import com.fishit.player.internal.source.PlaybackSourceResolver
import com.fishit.player.internal.ui.InternalPlayerControls
import com.fishit.player.internal.ui.PlayerSurface
import com.fishit.player.nextlib.NextlibCodecConfigurator
import com.fishit.player.playback.domain.KidsPlaybackGate
import com.fishit.player.playback.domain.PlayerEntryPoint
import com.fishit.player.playback.domain.ResumeManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [PlayerEntryPoint] that delegates to [InternalPlayerSession].
 *
 * This adapter provides a clean abstraction layer so feature modules can initiate
 * playback without depending on concrete player internals.
 *
 * **Architecture:**
 * - Implements playback/domain interface (PlayerEntryPoint)
 * - Wraps player/internal implementation (InternalPlayerSession)
 * - Feature modules depend ONLY on PlayerEntryPoint, NOT on this class
 * - All engine wiring (resolver, resume, kids gate, codec) encapsulated here
 *
 * **Thread Safety:**
 * - Uses mutex to ensure only one playback session at a time
 * - Stops previous session before starting new one
 *
 * @param context Android application context
 * @param sourceResolver Resolver for playback sources
 * @param resumeManager Manager for resume positions
 * @param kidsPlaybackGate Gate for kids screen time
 * @param codecConfigurator Configurator for FFmpeg codecs
 */
@Singleton
class InternalPlayerEntryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sourceResolver: PlaybackSourceResolver,
    private val resumeManager: ResumeManager,
    private val kidsPlaybackGate: KidsPlaybackGate,
    private val codecConfigurator: NextlibCodecConfigurator,
) : PlayerEntryPoint {

    private val mutex = Mutex()
    private var currentSession: InternalPlayerSession? = null

    override suspend fun start(context: PlaybackContext) = mutex.withLock {
        UnifiedLog.d(TAG) { "Starting playback: ${context.canonicalId}" }

        // Stop any existing session
        currentSession?.let { session ->
            UnifiedLog.d(TAG) { "Stopping previous session" }
            session.destroy()
        }

        // Create new session
        val session = InternalPlayerSession(
            context = this.context,
            sourceResolver = sourceResolver,
            resumeManager = resumeManager,
            kidsPlaybackGate = kidsPlaybackGate,
            codecConfigurator = codecConfigurator,
        )

        currentSession = session

        // Initialize playback
        session.initialize(context)

        UnifiedLog.d(TAG) { "Playback started: ${context.canonicalId}" }
    }

    override suspend fun stop() = mutex.withLock {
        UnifiedLog.d(TAG) { "Stopping playback" }
        currentSession?.destroy()
        currentSession = null
    }

    @Composable
    override fun RenderPlayerUi(
        onExit: () -> Unit,
        modifier: Modifier
    ) {
        val session = currentSession
        if (session == null) {
            UnifiedLog.w(TAG) { "RenderPlayerUi called but no active session" }
            return
        }

        val lifecycleOwner = LocalLifecycleOwner.current
        val context = LocalContext.current

        // Create PlayerView
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

        // Auto-hide controls
        LaunchedEffect(state.areControlsVisible, state.isPlaying) {
            if (state.areControlsVisible && state.isPlaying) {
                delay(4000)
                session.setControlsVisible(false)
            }
        }

        // Lifecycle handling
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_PAUSE -> {
                        // Save resume position when backgrounding
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

        // UI
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

    companion object {
        private const val TAG = "InternalPlayerEntryImpl"
    }
}

package com.fishit.player.internal

import android.content.Context
import com.fishit.player.core.playermodel.PlaybackContext
import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.internal.session.InternalPlayerSession
import com.fishit.player.internal.source.PlaybackSourceResolver
import com.fishit.player.nextlib.NextlibCodecConfigurator
import com.fishit.player.playback.domain.KidsPlaybackGate
import com.fishit.player.playback.domain.PlayerEntryPoint
import com.fishit.player.playback.domain.ResumeManager
import dagger.hilt.android.qualifiers.ApplicationContext
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
    
    /**
     * Get the current active player session.
     * Used internally by player:ui to render the player surface.
     * Should only be accessed after [start] has completed successfully.
     */
    fun getCurrentSession(): InternalPlayerSession? = currentSession

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

    companion object {
        private const val TAG = "InternalPlayerEntryImpl"
    }
}

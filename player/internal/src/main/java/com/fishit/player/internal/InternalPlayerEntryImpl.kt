package com.fishit.player.internal

import android.content.Context
import androidx.media3.datasource.DataSource
import com.fishit.player.core.playermodel.PlaybackContext
import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.internal.session.InternalPlayerSession
import com.fishit.player.internal.source.PlaybackSourceResolver
import com.fishit.player.nextlib.NextlibCodecConfigurator
import com.fishit.player.playback.domain.DataSourceType
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
 *
 * **Thread Safety:**
 * - Uses mutex to ensure only one playback session at a time
 * - Stops previous session before starting new one
 *
 * **DataSource Wiring:**
 * - Injects Map<DataSourceType, DataSource.Factory> from PlayerDataSourceModule
 * - Telegram uses TelegramFileDataSourceFactory (zero-copy TDLib streaming)
 * - Xtream uses DefaultDataSource.Factory (standard HTTP)
 *
 * @param context Android application context
 * @param sourceResolver Resolver for playback sources
 * @param resumeManager Manager for resume positions
 * @param kidsPlaybackGate Gate for kids screen time
 * @param codecConfigurator Configurator for FFmpeg codecs
 * @param dataSourceFactories Map of source-type-specific DataSource factories
 */
@Singleton
class InternalPlayerEntryImpl
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val sourceResolver: PlaybackSourceResolver,
        private val resumeManager: ResumeManager,
        private val kidsPlaybackGate: KidsPlaybackGate,
        private val codecConfigurator: NextlibCodecConfigurator,
        private val dataSourceFactories: Map<DataSourceType, @JvmSuppressWildcards DataSource.Factory>,
    ) : PlayerEntryPoint {
        private val mutex = Mutex()
        private var currentSession: InternalPlayerSession? = null

        override suspend fun start(context: PlaybackContext) =
            mutex.withLock {
                UnifiedLog.d(TAG) { "Starting playback: ${context.canonicalId}" }

                // Stop any existing session
                currentSession?.let { session ->
                    UnifiedLog.d(TAG) { "Stopping previous session" }
                    session.destroy()
                }

                // Create new session with DataSource factories for source-specific streaming
                val session =
                    InternalPlayerSession(
                        context = this.context,
                        sourceResolver = sourceResolver,
                        resumeManager = resumeManager,
                        kidsPlaybackGate = kidsPlaybackGate,
                        codecConfigurator = codecConfigurator,
                        dataSourceFactories = dataSourceFactories,
                    )

                currentSession = session

                // Initialize playback
                session.initialize(context)

                UnifiedLog.d(TAG) { "Playback started: ${context.canonicalId}" }
            }

        override suspend fun stop() =
            mutex.withLock {
                UnifiedLog.d(TAG) { "Stopping playback" }
                currentSession?.destroy()
                currentSession = null
            }

        /**
         * Returns the current active session, if any.
         *
         * UI components can use this to attach the ExoPlayer to a PlayerView.
         */
        fun getCurrentSession(): InternalPlayerSession? = currentSession

        companion object {
            private const val TAG = "InternalPlayerEntryImpl"
        }
    }

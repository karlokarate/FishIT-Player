package com.fishit.player.v2

import com.fishit.player.core.catalogsync.CatalogSyncWorkScheduler
import com.fishit.player.core.feature.auth.TelegramAuthRepository
import com.fishit.player.core.feature.auth.TelegramAuthState
import com.fishit.player.feature.onboarding.domain.XtreamAuthRepository
import com.fishit.player.feature.onboarding.domain.XtreamAuthState
import com.fishit.player.feature.onboarding.domain.XtreamConnectionState
import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.v2.di.AppScopeModule
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Bootstraps catalog synchronization once per app session after authentication succeeds.
 *
 * Responsibilities:
 * - Observe auth/connection state from Telegram and Xtream repositories (domain layer)
 * - Trigger catalog sync when authentication is ready
 * - Does NOT handle session initialization (that's XtreamSessionBootstrap's job)
 */
@Singleton
class CatalogSyncBootstrap
    @Inject
    constructor(
        private val catalogSyncWorkScheduler: CatalogSyncWorkScheduler,
        private val telegramAuthRepository: TelegramAuthRepository,
        private val xtreamAuthRepository: XtreamAuthRepository,
        @Named(AppScopeModule.APP_LIFECYCLE_SCOPE)
        private val appScope: CoroutineScope,
    ) {
        private val hasStarted = AtomicBoolean(false)
        private val hasTriggered = AtomicBoolean(false)

        fun start() {
            if (!hasStarted.compareAndSet(false, true)) {
                UnifiedLog.d(TAG) { "Catalog sync bootstrap already started, ignoring" }
                return
            }

            UnifiedLog.i(TAG) { "Catalog sync bootstrap collection started" }

            appScope.launch {
                try {
                    val readinessSnapshot =
                        combine(
                            telegramAuthRepository.authState.map { state ->
                                state.toTelegramReadiness()
                            },
                            combine(
                                xtreamAuthRepository.connectionState,
                                xtreamAuthRepository.authState,
                            ) { connection, auth ->
                                XtreamReadiness(connectionState = connection, authState = auth)
                            },
                        ) { telegramReadiness, xtreamReadiness ->
                            val snapshot = AuthReadinessSnapshot(telegramReadiness, xtreamReadiness)
                            UnifiedLog.d(TAG) {
                                "Auth state update: telegram=${telegramReadiness.state} (ready=${telegramReadiness.isReady}), " +
                                    "xtreamConn=${xtreamReadiness.connectionState} " +
                                    "xtreamAuth=${xtreamReadiness.authState} (ready=${xtreamReadiness.isReady})"
                            }
                            snapshot
                        }
                            .distinctUntilChanged()
                            .onEach { snapshot ->
                                UnifiedLog.d(TAG) { "Checking auth state: hasAuth=${snapshot.hasReadySource}" }
                            }
                            .first { snapshot -> snapshot.hasReadySource }

                    triggerSync(
                        telegramReady = readinessSnapshot.telegram.isReady,
                        xtreamConnected = readinessSnapshot.xtream.isReady,
                    )
                } catch (cancellation: CancellationException) {
                    UnifiedLog.d(TAG) { "Catalog sync bootstrap cancelled" }
                    throw cancellation
                } catch (t: Throwable) {
                    UnifiedLog.e(TAG, t) { "Catalog sync bootstrap failed to start" }
                }
            }
        }

/**
     * Contract T-2: Mandatory delay gate before sync.
     * This prevents sync from racing with other startup tasks.
     */
    private suspend fun triggerSync(
        telegramReady: Boolean,
        xtreamConnected: Boolean,
    ) {
        if (!hasTriggered.compareAndSet(false, true)) return

        // Contract T-2: Delay gate - wait before triggering sync
        delay(SYNC_DELAY_MS)

        // Contract T-3: Only sync if at least one source is ready
        if (!telegramReady && !xtreamConnected) {
            UnifiedLog.i(TAG) { "No sources ready, skipping catalog sync" }
            return
        }

            UnifiedLog.i(TAG) { "Catalog sync bootstrap triggered; telegram=$telegramReady xtream=$xtreamConnected" }
            catalogSyncWorkScheduler.enqueueAutoSync()
        }

        private data class AuthReadinessSnapshot(
            val telegram: TelegramReadiness,
            val xtream: XtreamReadiness,
        ) {
            val hasReadySource: Boolean
                get() = telegram.isReady || xtream.isReady
        }

        private data class TelegramReadiness(
            val state: TelegramAuthState,
            val isReady: Boolean,
        )

        private data class XtreamReadiness(
            val connectionState: XtreamConnectionState,
            val authState: XtreamAuthState,
        ) {
            val isReady: Boolean
                get() =
                    connectionState is XtreamConnectionState.Connected &&
                        authState is XtreamAuthState.Authenticated
        }

        private fun TelegramAuthState.toTelegramReadiness(): TelegramReadiness =
            TelegramReadiness(state = this, isReady = this is TelegramAuthState.Connected)

        private companion object {
            private const val TAG = "CatalogSyncBootstrap"

            /**
             * Contract T-2: Delay gate in milliseconds.
             * Prevents sync from racing with other startup operations.
             */
            private const val SYNC_DELAY_MS = 5_000L
        }
    }

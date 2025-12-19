package com.fishit.player.v2

import com.fishit.player.core.catalogsync.CatalogSyncWorkScheduler
import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.infra.transport.telegram.TelegramAuthClient
import com.fishit.player.infra.transport.telegram.api.TdlibAuthState
import com.fishit.player.infra.transport.xtream.XtreamApiClient
import com.fishit.player.infra.transport.xtream.XtreamConnectionState
import com.fishit.player.v2.di.AppScopeModule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Bootstraps catalog synchronization once per app session after authentication succeeds.
 *
 * Responsibilities:
 * - Observe auth/connection state from Telegram and Xtream clients
 * - Trigger catalog sync when authentication is ready
 * - Does NOT handle session initialization (that's XtreamSessionBootstrap's job)
 */
    @Singleton
    class CatalogSyncBootstrap
        @Inject
        constructor(
            private val catalogSyncWorkScheduler: CatalogSyncWorkScheduler,
            private val telegramAuthClient: TelegramAuthClient,
            private val xtreamApiClient: XtreamApiClient,
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
                    val (telegramReady, xtreamConnected) =
                        combine(
                            telegramAuthClient.authState
                                .map { it is TdlibAuthState.Ready }
                                .onStart {
                                    val isAuthorized =
                                        runCatching { telegramAuthClient.isAuthorized() }
                                            .onFailure { error ->
                                                UnifiedLog.w(TAG) { "Failed to check Telegram auth: ${error.message}" }
                                            }
                                            .getOrDefault(false)
                                    emit(isAuthorized)
                                },
                            xtreamApiClient.connectionState
                                .map { it is XtreamConnectionState.Connected }
                                .onStart {
                                    emit(xtreamApiClient.connectionState.value is XtreamConnectionState.Connected)
                                },
                        ) { telegramAuthenticated, xtreamAuthenticated ->
                            UnifiedLog.d(TAG) { "Auth state update: telegram=$telegramAuthenticated, xtream=$xtreamAuthenticated" }
                            telegramAuthenticated to xtreamAuthenticated
                        }.distinctUntilChanged()
                            .first { (telegramAuthenticated, xtreamAuthenticated) ->
                                val hasAuth = telegramAuthenticated || xtreamAuthenticated
                                UnifiedLog.d(TAG) { "Checking auth state: hasAuth=$hasAuth" }
                                hasAuth
                            }

                    triggerSync(telegramReady, xtreamConnected)
                } catch (cancellation: CancellationException) {
                    UnifiedLog.d(TAG) { "Catalog sync bootstrap cancelled" }
                    throw cancellation
                } catch (t: Throwable) {
                    UnifiedLog.e(TAG, t) { "Catalog sync bootstrap failed to start" }
                }
            }
        }

        private fun triggerSync(
            telegramReady: Boolean,
            xtreamConnected: Boolean,
        ) {
            if (!hasTriggered.compareAndSet(false, true)) return

            UnifiedLog.i(TAG) { "Catalog sync bootstrap triggered; telegram=$telegramReady xtream=$xtreamConnected" }
            catalogSyncWorkScheduler.enqueueAutoSync()
        }

        private companion object {
            private const val TAG = "CatalogSyncBootstrap"
        }
    }

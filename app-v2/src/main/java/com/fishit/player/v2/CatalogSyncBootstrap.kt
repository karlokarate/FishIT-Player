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
 * **SSOT Compliance:** Uses `CatalogSyncWorkScheduler` as the single entry point for sync.
 * Per CATALOG_SYNC_WORKERS_CONTRACT_V2.md, no direct calls to `CatalogSyncService.sync()`.
 *
 * Responsibilities:
 * - Observe auth/connection state from Telegram and Xtream clients
 * - Trigger catalog sync via scheduler when authentication is ready
 * - Does NOT handle session initialization (that's XtreamSessionBootstrap's job)
 */
@Singleton
class CatalogSyncBootstrap
    @Inject
    constructor(
        private val catalogSyncScheduler: CatalogSyncWorkScheduler,
        private val telegramAuthClient: TelegramAuthClient,
        private val xtreamApiClient: XtreamApiClient,
        @Named(AppScopeModule.APP_LIFECYCLE_SCOPE)
        private val appScope: CoroutineScope,
    ) {
        private val hasStarted = AtomicBoolean(false)
        private val hasTriggered = AtomicBoolean(false)

        fun start() {
            if (!hasStarted.compareAndSet(false, true)) return

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
                                            .getOrDefault(false)
                                    emit(isAuthorized)
                                },
                            xtreamApiClient.connectionState
                                .map { it is XtreamConnectionState.Connected }
                                .onStart {
                                    emit(xtreamApiClient.connectionState.value is XtreamConnectionState.Connected)
                                },
                        ) { telegramAuthenticated, xtreamAuthenticated ->
                            telegramAuthenticated to xtreamAuthenticated
                        }.distinctUntilChanged()
                            .first { (telegramAuthenticated, xtreamAuthenticated) ->
                                telegramAuthenticated || xtreamAuthenticated
                            }

                    triggerSync(telegramReady, xtreamConnected)
                } catch (cancellation: CancellationException) {
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

            // Trigger auto sync via SSOT scheduler (W-6: unique work "catalog_sync_global")
            // The scheduler will determine which sources to sync based on active sources
            if (telegramReady || xtreamConnected) {
                catalogSyncScheduler.enqueueAutoSync()
            }
        }

        private companion object {
            private const val TAG = "CatalogSyncBootstrap"
        }
    }

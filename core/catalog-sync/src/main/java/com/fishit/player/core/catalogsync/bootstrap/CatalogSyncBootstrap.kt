package com.fishit.player.core.catalogsync.bootstrap

import com.fishit.player.core.appstartup.bootstrap.CatalogSyncStarter
import com.fishit.player.core.catalogsync.CatalogSyncService
import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.infra.transport.telegram.TelegramAuthClient
import com.fishit.player.infra.transport.telegram.api.TelegramAuthState
import com.fishit.player.infra.transport.xtream.XtreamApiClient
import com.fishit.player.infra.transport.xtream.XtreamConnectionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
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
 * **Purpose:**
 * - Observe auth/connection state from Telegram and Xtream clients
 * - Trigger catalog sync when authentication is ready
 * - Does NOT handle session initialization (that's XtreamSessionBootstrap's job)
 *
 * **Architecture (Phase B2):**
 * - Migrated from app-v2 to core/catalog-sync
 * - Orchestrates sync triggers based on transport state
 * - Implements CatalogSyncStarter interface from core:app-startup
 */
@Singleton
class CatalogSyncBootstrap
    @Inject
    constructor(
        private val catalogSyncService: CatalogSyncService,
        private val telegramAuthClient: TelegramAuthClient,
        private val xtreamApiClient: XtreamApiClient,
        @Named("AppLifecycleScope")
        private val appScope: CoroutineScope,
    ) : CatalogSyncStarter {
        private val hasStarted = AtomicBoolean(false)
        private val hasTriggered = AtomicBoolean(false)

        override fun start() {
            if (!hasStarted.compareAndSet(false, true)) return

            UnifiedLog.i(TAG) { "Catalog sync bootstrap collection started" }

            appScope.launch {
                try {
                    val (telegramReady, xtreamConnected) =
                        combine(
                            telegramAuthClient.authState
                                .map { it is TelegramAuthState.Ready }
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

            if (telegramReady) {
                appScope.launch {
                    catalogSyncService
                        .syncTelegram()
                        .catch { error ->
                            UnifiedLog.e(TAG, error) { "Telegram sync failed to start" }
                        }.collect()
                }
            }

            if (xtreamConnected) {
                appScope.launch {
                    catalogSyncService
                        .syncXtream()
                        .catch { error ->
                            UnifiedLog.e(TAG, error) { "Xtream sync failed to start" }
                        }.collect()
                }
            }
        }

        private companion object {
            private const val TAG = "CatalogSyncBootstrap"
        }
    }

package com.fishit.player.v2

import com.fishit.player.core.catalogsync.CatalogSyncService
import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.infra.transport.telegram.TelegramAuthClient
import com.fishit.player.infra.transport.telegram.api.TelegramAuthState
import com.fishit.player.infra.transport.xtream.XtreamApiClient
import com.fishit.player.infra.transport.xtream.XtreamConnectionState
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

/**
 * Bootstraps catalog synchronization once per app session after authentication succeeds.
 */
class CatalogSyncBootstrap @Inject constructor(
    private val catalogSyncService: CatalogSyncService,
    private val telegramAuthClient: TelegramAuthClient,
    private val xtreamApiClient: XtreamApiClient,
) {

    private val hasStarted = AtomicBoolean(false)
    private val hasTriggered = AtomicBoolean(false)

    fun start(scope: CoroutineScope) {
        if (!hasStarted.compareAndSet(false, true)) return

        scope.launch {
            try {
                val (telegramReady, xtreamConnected) = combine(
                    telegramAuthClient.authState
                        .map { it is TelegramAuthState.Ready }
                        .onStart {
                            val isAuthorized = runCatching { telegramAuthClient.isAuthorized() }
                                .getOrDefault(false)
                            emit(isAuthorized)
                        },
                    xtreamApiClient.connectionState
                        .map { it is XtreamConnectionState.Connected }
                        .onStart {
                            emit(xtreamApiClient.connectionState.value is XtreamConnectionState.Connected)
                        }
                ) { telegramAuthenticated, xtreamAuthenticated ->
                    telegramAuthenticated to xtreamAuthenticated
                }
                    .distinctUntilChanged()
                    .first { (telegramAuthenticated, xtreamAuthenticated) ->
                        (telegramAuthenticated || xtreamAuthenticated) && !hasTriggered.get()
                    }

                triggerSync(scope, telegramReady, xtreamConnected)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (t: Throwable) {
                UnifiedLog.e(TAG, t) { "Catalog sync bootstrap failed to start" }
            }
        }
    }

    private fun triggerSync(
        scope: CoroutineScope,
        telegramReady: Boolean,
        xtreamConnected: Boolean,
    ) {
        if (!hasTriggered.compareAndSet(false, true)) return

        UnifiedLog.i(TAG) { "Catalog sync bootstrap triggered; telegram=$telegramReady xtream=$xtreamConnected" }

        if (telegramReady) {
            scope.launch {
                catalogSyncService.syncTelegram()
                    .catch { error ->
                        UnifiedLog.e(TAG, error) { "Telegram sync failed to start" }
                    }
                    .collect()
            }
        }

        if (xtreamConnected) {
            scope.launch {
                catalogSyncService.syncXtream()
                    .catch { error ->
                        UnifiedLog.e(TAG, error) { "Xtream sync failed to start" }
                    }
                    .collect()
            }
        }
    }

    private companion object {
        private const val TAG = "CatalogSyncBootstrap"
    }
}

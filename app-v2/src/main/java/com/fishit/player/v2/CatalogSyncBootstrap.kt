package com.fishit.player.v2

import com.fishit.player.core.catalogsync.CatalogSyncService
import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.infra.transport.telegram.TelegramAuthClient
import com.fishit.player.infra.transport.telegram.api.TelegramAuthState
import com.fishit.player.infra.transport.xtream.XtreamApiClient
import com.fishit.player.infra.transport.xtream.XtreamConnectionState
import com.fishit.player.infra.transport.xtream.XtreamCredentialsStore
import com.fishit.player.v2.di.AppScopeModule
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
@Singleton
class CatalogSyncBootstrap @Inject constructor(
    private val catalogSyncService: CatalogSyncService,
    private val telegramAuthClient: TelegramAuthClient,
    private val xtreamApiClient: XtreamApiClient,
    private val xtreamCredentialsStore: XtreamCredentialsStore,
    @Named(AppScopeModule.APP_LIFECYCLE_SCOPE)
    private val appScope: CoroutineScope,
) {

    private val hasStarted = AtomicBoolean(false)
    private val hasTriggered = AtomicBoolean(false)
    private val hasAutoInitXtream = AtomicBoolean(false)

    fun start() {
        if (!hasStarted.compareAndSet(false, true)) return

        UnifiedLog.i(TAG) { "Catalog sync bootstrap collection started" }

        // Try to auto-initialize Xtream from stored credentials
        autoInitializeXtream()

        appScope.launch {
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

    private fun autoInitializeXtream() {
        if (!hasAutoInitXtream.compareAndSet(false, true)) return

        appScope.launch(Dispatchers.IO) {
            try {
                val storedConfig = xtreamCredentialsStore.read()
                if (storedConfig != null) {
                    UnifiedLog.i(TAG) {
                        "Auto-initializing Xtream from stored config: scheme=${storedConfig.scheme}, " +
                                "host=${storedConfig.host}, port=${storedConfig.port}"
                    }
                    val result = xtreamApiClient.initialize(storedConfig.toApiConfig())
                    if (result.isSuccess) {
                        UnifiedLog.i(TAG) { "Xtream auto-initialization succeeded" }
                    } else {
                        val error = result.exceptionOrNull()
                        UnifiedLog.w(TAG, error) {
                            "Xtream auto-initialization failed (credentials may be stale)"
                        }
                    }
                } else {
                    UnifiedLog.d(TAG) { "No stored Xtream credentials found" }
                }
            } catch (t: Throwable) {
                UnifiedLog.e(TAG, t) { "Xtream auto-initialization error" }
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
                catalogSyncService.syncTelegram()
                    .catch { error ->
                        UnifiedLog.e(TAG, error) { "Telegram sync failed to start" }
                    }
                    .collect()
            }
        }

        if (xtreamConnected) {
            appScope.launch {
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

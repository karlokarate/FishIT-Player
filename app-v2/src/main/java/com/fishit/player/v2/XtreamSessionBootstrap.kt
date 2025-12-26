package com.fishit.player.v2

import com.fishit.player.core.sourceactivation.SourceActivationStore
import com.fishit.player.core.sourceactivation.SourceErrorReason
import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.infra.transport.xtream.XtreamApiClient
import com.fishit.player.infra.transport.xtream.XtreamCredentialsStore
import com.fishit.player.v2.di.AppScopeModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Bootstraps Xtream session initialization from stored credentials on app start.
 *
 * Responsibilities:
 * - Read stored Xtream credentials on app start
 * - Auto-initialize XtreamApiClient if credentials exist
 * - Update SourceActivationStore on success/failure
 * - Does NOT trigger catalog sync (that's CatalogSyncBootstrap's job)
 * - Runs once per app process
 */
@Singleton
class XtreamSessionBootstrap
    @Inject
    constructor(
        private val xtreamApiClient: XtreamApiClient,
        private val xtreamCredentialsStore: XtreamCredentialsStore,
        private val sourceActivationStore: SourceActivationStore,
        @Named(AppScopeModule.APP_LIFECYCLE_SCOPE)
        private val appScope: CoroutineScope,
    ) {
        private val hasAutoInitialized = AtomicBoolean(false)

        fun start() {
            if (!hasAutoInitialized.compareAndSet(false, true)) return

            appScope.launch(Dispatchers.IO) {
                try {
                    val storedConfig = xtreamCredentialsStore.read()
                    if (storedConfig != null) {
                        UnifiedLog.i(TAG) {
                            "Auto-initializing Xtream session from stored config: " +
                                "scheme=${storedConfig.scheme}, host=${storedConfig.host}, port=${storedConfig.port}"
                        }
                        val result = xtreamApiClient.initialize(storedConfig.toApiConfig())
                        if (result.isSuccess) {
                            UnifiedLog.i(TAG) { "Xtream session auto-initialization succeeded" }
                            sourceActivationStore.setXtreamActive()
                        } else {
                            val error = result.exceptionOrNull()
                            UnifiedLog.w(TAG, error) {
                                "Xtream session auto-initialization failed (credentials may be stale)"
                            }
                            sourceActivationStore.setXtreamInactive(SourceErrorReason.INVALID_CREDENTIALS)
                        }
                    } else {
                        UnifiedLog.d(TAG) { "No stored Xtream credentials found" }
                        // No credentials = inactive (not an error)
                        sourceActivationStore.setXtreamInactive()
                    }
                } catch (t: Throwable) {
                    UnifiedLog.e(TAG, t) { "Xtream session auto-initialization error" }
                    sourceActivationStore.setXtreamInactive(SourceErrorReason.TRANSPORT_ERROR)
                }
            }
        }

        private companion object {
            private const val TAG = "XtreamSessionBootstrap"
        }
    }

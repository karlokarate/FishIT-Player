package com.fishit.player.infra.transport.xtream.bootstrap

import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.infra.transport.xtream.XtreamApiClient
import com.fishit.player.infra.transport.xtream.XtreamCredentialsStore
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
 * **Purpose:**
 * - Read stored Xtream credentials on app start
 * - Auto-initialize XtreamApiClient if credentials exist
 * - Does NOT trigger catalog sync (that's CatalogSyncBootstrap's job)
 * - Runs once per app process
 *
 * **Architecture (Phase B2):**
 * - Migrated from app-v2 to infra/transport-xtream
 * - Transport layer owns session initialization
 * - Exposes XtreamBootstrapper interface to app layer
 */
@Singleton
class XtreamSessionBootstrap
    @Inject
    constructor(
        private val xtreamApiClient: XtreamApiClient,
        private val xtreamCredentialsStore: XtreamCredentialsStore,
        @Named("AppLifecycleScope")
        private val appScope: CoroutineScope,
    ) : XtreamBootstrapper {
        private val hasAutoInitialized = AtomicBoolean(false)

        override fun start() {
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
                        } else {
                            val error = result.exceptionOrNull()
                            UnifiedLog.w(TAG, error) {
                                "Xtream session auto-initialization failed (credentials may be stale)"
                            }
                        }
                    } else {
                        UnifiedLog.d(TAG) { "No stored Xtream credentials found" }
                    }
                } catch (t: Throwable) {
                    UnifiedLog.e(TAG, t) { "Xtream session auto-initialization error" }
                }
            }
        }

        private companion object {
            private const val TAG = "XtreamSessionBootstrap"
        }
    }

/**
 * Safe interface for triggering Xtream session bootstrap.
 *
 * Exposed to app-v2 layer for startup integration without exposing
 * transport implementation details.
 */
interface XtreamBootstrapper {
    /**
     * Start the Xtream session bootstrap process.
     *
     * Idempotent - safe to call multiple times.
     */
    fun start()
}

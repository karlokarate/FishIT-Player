package com.chris.m3usuite.core.xtream

import android.content.Context
import android.net.Uri
import com.chris.m3usuite.core.http.HttpClientFactory
import com.chris.m3usuite.core.logging.AppLog
import com.chris.m3usuite.data.repo.XtreamObxRepository
import com.chris.m3usuite.prefs.Keys
import com.chris.m3usuite.prefs.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Coordinates initial Xtream seeding so all heads are available immediately.
 *
 * - Runs port discovery when needed (mirrors IBO provisioning flow).
 * - Imports full head lists once (`importHeadsOnly`) without pruning/details.
 * - Writes diagnostics/last-import timestamps into SettingsStore.
 */
object XtreamSeeder {
    private val mutex = Mutex()
    private const val TAG = "XtreamSeeder"

    /**
     * Ensures that ObjectBox contains at least the head lists for live/vod/series.
     * Returns null when no seeding was required.
     */
    suspend fun ensureSeeded(
        context: Context,
        store: SettingsStore,
        reason: String? = null,
        force: Boolean = false,
        forceDiscovery: Boolean = false,
    ): Result<Triple<Int, Int, Int>>? =
        mutex.withLock {
            // Gatekeeping: Xtream creds required and API must be enabled.
            if (!store.m3uWorkersEnabled.first()) return@withLock null
            if (!store.hasXtream()) return@withLock null

            val host = store.xtHost.first()
            val user = store.xtUser.first()
            val pass = store.xtPass.first()
            val port = store.xtPort.first()
            val portVerified = store.xtPortVerified.first()
            val scheme = if (port == 443) "https" else "http"

            val repo = XtreamObxRepository(context, store)
            val shouldSeed =
                withContext(Dispatchers.IO) {
                    val hasContent = runCatching { repo.hasAnyContent() }.getOrDefault(false)
                    !hasContent || force
                }
            if (!shouldSeed) return@withLock null

            XtreamImportCoordinator.runSeeding {
                withContext(Dispatchers.IO) {
                    // Discovery mirrors the IBO flow: resolve correct port/aliases once.
                    val needsDiscovery = forceDiscovery || !portVerified || port <= 0
                    if (needsDiscovery) {
                        try {
                            val http = HttpClientFactory.create(context, store)
                            val capStore = ProviderCapabilityStore(context)
                            val portStore = EndpointPortStore(context)
                            val discoverer = CapabilityDiscoverer(http, capStore, portStore)
                            val caps =
                                discoverer.discoverAuto(
                                    scheme = scheme,
                                    host = host,
                                    username = user,
                                    password = pass,
                                    basePath = null,
                                    forceRefresh = true,
                                )
                            val baseUri = Uri.parse(caps.baseUrl)
                            val resolvedScheme = (baseUri.scheme ?: scheme).lowercase()
                            val resolvedHost = baseUri.host ?: host
                            val resolvedPort = baseUri.port.takeIf { it > 0 } ?: port
                            store.setXtHost(resolvedHost)
                            store.setXtPort(resolvedPort)
                            store.setXtPortVerified(true)
                            if (store.epgUrl.first().isBlank()) {
                                val epgUrl = "$resolvedScheme://$resolvedHost:$resolvedPort/xmltv.php?username=$user&password=$pass"
                                store.set(Keys.EPG_URL, epgUrl)
                            }
                        } catch (e: Throwable) {
                            AppLog.log(
                                category = "xtream",
                                level = AppLog.Level.WARN,
                                message = "Discovery failed (${reason.orEmpty()}): ${e.message}",
                            )
                        }
                    }

                    // Ultra-fast first paint: seed evenly per category (200 per category)
                    val result = repo.seedListsQuick(limitPerKind = 0, forceRefreshDiscovery = forceDiscovery, perCategoryLimit = 200)
                    if (result.isSuccess) {
                        val (live, vod, series) = result.getOrNull() ?: Triple(0, 0, 0)
                        try {
                            store.setLastSeedCounts(live, vod, series)
                        } catch (_: Throwable) {
                        }
                        try {
                            store.setLastImportAtMs(System.currentTimeMillis())
                        } catch (_: Throwable) {
                        }
                        try {
                            store.setXtPortVerified(true)
                        } catch (_: Throwable) {
                        }
                    } else {
                        result.exceptionOrNull()?.let { e ->
                            AppLog.log(
                                category = "xtream",
                                level = AppLog.Level.WARN,
                                message = "Head import failed (${reason.orEmpty()}): ${e.message}",
                            )
                        }
                    }
                    // Immediately complete VOD/Series header lists synchronously (heads-only delta; Live skipped for performance)
                    runCatching { XtreamObxRepository(context, store).importDelta(deleteOrphans = false, includeLive = false) }
                    // Then queue a background one-shot for VOD/Series (heads-only)
                    XtreamImportCoordinator.enqueueWork {
                        runCatching {
                            com.chris.m3usuite.work.XtreamDeltaImportWorker.triggerOnce(
                                context,
                                includeLive = false,
                                vodLimit = 0,
                                seriesLimit = 0,
                            )
                        }
                    }
                    // And queue a delayed Live heads-only fill so Live comes later without impacting first paint
                    XtreamImportCoordinator.enqueueWork {
                        runCatching {
                            com.chris.m3usuite.work.XtreamDeltaImportWorker
                                .triggerOnceDelayedLive(context, delayMinutes = 5)
                        }
                    }
                    result
                }
            }
        }
}

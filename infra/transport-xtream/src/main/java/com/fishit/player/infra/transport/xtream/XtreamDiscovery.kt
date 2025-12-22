package com.fishit.player.infra.transport.xtream

import android.os.SystemClock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * XtreamDiscovery – Port Resolution & Capability Discovery
 *
 * Automatische Erkennung von:
 * - Korrektem Port (HTTP: 80, 8080, 8000; HTTPS: 443, 8443)
 * - VOD-Alias (vod/movie/movies)
 * - Verfügbaren API-Actions (EPG, info endpoints, etc.)
 * - Panel-Typ-Hinweise (Xtream-UI, XUI.ONE, etc.)
 *
 * Basiert auf v1 XtreamCapabilities.kt mit verbessertem Probing.
 */
class XtreamDiscovery(
    private val http: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    // =========================================================================
    // Cache
    // =========================================================================

    companion object {
        private val cacheMutex = Mutex()
        private val portCache = mutableMapOf<String, PortCacheEntry>()
        private val capCache = mutableMapOf<String, CapabilityCacheEntry>()

        // Port candidates by scheme
        private val HTTP_PORTS = listOf(80, 8080, 8000, 8880, 2052, 2082, 2086)
        private val HTTPS_PORTS = listOf(443, 8443, 2053, 2083, 2087, 2096)

        // VOD alias candidates
        private val VOD_ALIASES = listOf("vod", "movie", "movies")

        // Probe actions
        private val PROBE_ACTIONS = listOf("get_live_streams", "get_series", "get_vod_streams")
    }

    private data class PortCacheEntry(
        val port: Int,
        val at: Long,
    )

    private data class CapabilityCacheEntry(
        val caps: XtreamCapabilities,
        val at: Long,
    )

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Resolve the correct port for the given config.
     *
     * @param config API config (without port).
     * @param forceRefresh Skip cache and probe again.
     * @return Resolved port or default (80/443).
     */
    suspend fun resolvePort(
        config: XtreamApiConfig,
        forceRefresh: Boolean = false,
    ): Int =
        withContext(io) {
            val key = portCacheKey(config)

            // Check cache
            if (!forceRefresh) {
                cacheMutex.withLock {
                    portCache[key]?.let { entry ->
                        if (SystemClock.elapsedRealtime() - entry.at < XtreamTransportConfig.PORT_CACHE_TTL_MS) {
                            return@withContext entry.port
                        }
                    }
                }
            }

            // Resolve
            val resolved = probePort(config)

            // Cache result
            cacheMutex.withLock {
                portCache[key] = PortCacheEntry(resolved, SystemClock.elapsedRealtime())
            }

            resolved
        }

    /**
     * Discover capabilities for the given config.
     *
     * @param config API config.
     * @param port Resolved port.
     * @param forceRefresh Skip cache and probe again.
     * @return Discovered capabilities.
     */
    suspend fun discoverCapabilities(
        config: XtreamApiConfig,
        port: Int,
        forceRefresh: Boolean = false,
    ): XtreamCapabilities =
        withContext(io) {
            val key = capCacheKey(config, port)

            // Check cache
            if (!forceRefresh) {
                cacheMutex.withLock {
                    capCache[key]?.let { entry ->
                        if (SystemClock.elapsedRealtime() - entry.at < XtreamTransportConfig.CAPABILITY_CACHE_TTL_MS) {
                            return@withContext entry.caps
                        }
                    }
                }
            }

            // Discover
            val caps = probeCapabilities(config, port, key)

            // Cache result
            cacheMutex.withLock {
                capCache[key] = CapabilityCacheEntry(caps, SystemClock.elapsedRealtime())
            }

            caps
        }

    /** Full discovery: port + capabilities. */
    suspend fun fullDiscovery(
        config: XtreamApiConfig,
        forceRefresh: Boolean = false,
    ): Pair<Int, XtreamCapabilities> =
        withContext(io) {
            val port = resolvePort(config, forceRefresh)
            val caps = discoverCapabilities(config, port, forceRefresh)
            port to caps
        }

    /** Quick ping check to verify connectivity. */
    suspend fun ping(
        config: XtreamApiConfig,
        port: Int,
    ): Boolean = withContext(io) { tryProbe(config, port, "get_live_categories") }

    /** Clear all caches. */
    suspend fun clearCache() {
        cacheMutex.withLock {
            portCache.clear()
            capCache.clear()
        }
    }

    /** Clear cache for specific config. */
    suspend fun clearCacheFor(config: XtreamApiConfig) {
        val portKey = portCacheKey(config)
        cacheMutex.withLock {
            portCache.remove(portKey)
            // Clear all cap entries for this host/user combo
            capCache.keys.filter { it.startsWith(portKey) }.forEach { capCache.remove(it) }
        }
    }

    // =========================================================================
    // Port Probing
    // =========================================================================

    private suspend fun probePort(config: XtreamApiConfig): Int =
        coroutineScope {
            val isHttps = config.scheme.equals("https", ignoreCase = true)
            val defaultPort = if (isHttps) 443 else 80

            // If port specified in config, validate and return
            config.port?.let { specifiedPort ->
                if (tryProbe(config, specifiedPort)) {
                    return@coroutineScope specifiedPort
                }
                // Specified port failed, continue with discovery
            }

            // Try default port first (most common)
            if (tryProbe(config, defaultPort)) {
                return@coroutineScope defaultPort
            }

            // Parallel probe of alternative ports
            val candidates =
                (if (isHttps) HTTPS_PORTS else HTTP_PORTS).filter { it != defaultPort }.distinct()

            // Premium Contract Section 5: Use centralized parallelism config
            val semaphore = Semaphore(XtreamTransportConfig.PARALLELISM_PHONE_TABLET)
            val jobs =
                candidates.map { port ->
                    async { semaphore.withPermit { if (tryProbe(config, port)) port else null } }
                }

            val results = jobs.awaitAll()
            val winner = results.firstOrNull { it != null }

            winner ?: defaultPort
        }

    private fun tryProbe(
        config: XtreamApiConfig,
        port: Int,
        action: String? = null,
    ): Boolean {
        val actions = if (action != null) listOf(action) else PROBE_ACTIONS

        for (act in actions) {
            val url = buildProbeUrl(config, port, act)
            if (executeProbe(url)) return true
        }

        return false
    }

    private fun buildProbeUrl(
        config: XtreamApiConfig,
        port: Int,
        action: String,
    ): String =
        HttpUrl
            .Builder()
            .scheme(config.scheme.lowercase())
            .host(config.host)
            .port(port)
            .apply {
                config.basePath?.trim()?.let { bp ->
                    val norm = (if (bp.startsWith("/")) bp else "/$bp").removeSuffix("/")
                    if (norm.isNotEmpty() && norm != "/") {
                        norm.removePrefix("/").split('/').filter { it.isNotBlank() }.forEach {
                            addPathSegment(it)
                        }
                    }
                }
            }.addPathSegment("player_api.php")
            .addQueryParameter("action", action)
            .addQueryParameter("category_id", "0")
            .addQueryParameter("username", config.username)
            .addQueryParameter("password", config.password)
            .build()
            .toString()

    private fun executeProbe(url: String): Boolean {
        val request =
            Request
                .Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("Accept-Encoding", "gzip")
                .get()
                .build()

        // Use short timeout for probing
        val probeClient =
            http
                .newBuilder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build()

        return try {
            probeClient.newCall(request).execute().use { response ->
                if (response.code !in 200..299) return false

                val body =
                    response.body
                        ?.string()
                        ?.trim()
                        .orEmpty()
                if (body.isEmpty()) return false

                // Must be valid JSON
                if (!body.startsWith("{") && !body.startsWith("[")) return false

                runCatching { json.parseToJsonElement(body) }.isSuccess
            }
        } catch (_: Throwable) {
            false
        }
    }

    // =========================================================================
    // Capability Probing
    // =========================================================================

    private suspend fun probeCapabilities(
        config: XtreamApiConfig,
        port: Int,
        cacheKey: String,
    ): XtreamCapabilities =
        coroutineScope {
            val actions = mutableMapOf<String, XtreamActionCapability>()
            // Premium Contract Section 5: Use centralized parallelism config
            val semaphore = Semaphore(XtreamTransportConfig.PARALLELISM_PHONE_TABLET)

            suspend fun probe(
                action: String,
                extra: Map<String, String> = emptyMap(),
            ): JsonElement? {
                return semaphore.withPermit {
                    val url = buildCapUrl(config, port, action, extra)
                    val body = fetchBody(url)

                    if (body.isNullOrEmpty()) {
                        actions[action] = XtreamActionCapability(supported = false)
                        return@withPermit null
                    }

                    val element = runCatching { json.parseToJsonElement(body) }.getOrNull()
                    if (element == null) {
                        actions[action] = XtreamActionCapability(supported = false)
                        return@withPermit null
                    }

                    val (type, keys) = fingerprint(element)
                    actions[action] =
                        XtreamActionCapability(
                            supported = true,
                            responseType = type,
                            sampleKeys = keys,
                        )
                    element
                }
            }

            // Probe basic actions
            val basicJobs =
                listOf(
                    async { probe("get_live_categories") },
                    async { probe("get_live_streams", mapOf("category_id" to "*")) },
                    async { probe("get_series_categories") },
                    async { probe("get_series", mapOf("category_id" to "*")) },
                )

            // Probe VOD aliases to find the working one
            val vodResults =
                VOD_ALIASES
                    .map { alias ->
                        async {
                            semaphore.withPermit {
                                alias to (probe("get_${alias}_categories") != null)
                            }
                        }
                    }.awaitAll()

            val vodCandidates = vodResults.filter { it.second }.map { it.first }
            val resolvedVodKind = vodCandidates.firstOrNull()

            // Probe extras
            val extraJobs =
                listOf(
                    async { probe("get_short_epg", mapOf("stream_id" to "1", "limit" to "1")) },
                )

            (basicJobs + extraJobs).awaitAll()

            val baseUrl =
                buildString {
                    append(config.scheme.lowercase())
                    append("://")
                    append(config.host)
                    append(":")
                    append(port)
                    config.basePath?.let { bp ->
                        val norm =
                            bp.trim().let { if (it.startsWith("/")) it else "/$it" }.removeSuffix("/")
                        if (norm.isNotEmpty() && norm != "/") append(norm)
                    }
                }

            XtreamCapabilities(
                version = 2,
                cacheKey = cacheKey,
                baseUrl = baseUrl,
                username = config.username,
                resolvedAliases =
                    XtreamResolvedAliases(
                        vodKind = resolvedVodKind,
                        vodCandidates = vodCandidates,
                    ),
                actions = actions,
                extras =
                    XtreamExtrasCapability(
                        supportsShortEpg = actions["get_short_epg"]?.supported == true,
                        supportsVodInfo = true, // Assume supported
                        supportsSeriesInfo = true, // Assume supported
                    ),
                cachedAt = System.currentTimeMillis(),
            )
        }

    private fun buildCapUrl(
        config: XtreamApiConfig,
        port: Int,
        action: String,
        extra: Map<String, String> = emptyMap(),
    ): String =
        HttpUrl
            .Builder()
            .scheme(config.scheme.lowercase())
            .host(config.host)
            .port(port)
            .apply {
                config.basePath?.trim()?.let { bp ->
                    val norm = (if (bp.startsWith("/")) bp else "/$bp").removeSuffix("/")
                    if (norm.isNotEmpty() && norm != "/") {
                        norm.removePrefix("/").split('/').filter { it.isNotBlank() }.forEach {
                            addPathSegment(it)
                        }
                    }
                }
            }.addPathSegment("player_api.php")
            .addQueryParameter("action", action)
            .apply { extra.forEach { (k, v) -> addQueryParameter(k, v) } }
            .addQueryParameter("username", config.username)
            .addQueryParameter("password", config.password)
            .build()
            .toString()

    private fun fetchBody(url: String): String? {
        val request =
            Request
                .Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("Accept-Encoding", "gzip")
                .get()
                .build()

        return try {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                response.body?.string()?.trim()
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun fingerprint(el: JsonElement): Pair<String, List<String>> =
        when {
            el is JsonObject -> "object" to el.keys.toList()
            el is JsonArray -> {
                if (el.isNotEmpty() && el.firstOrNull()?.let { it is JsonObject } == true) {
                    "array" to (
                        el
                            .first()
                            .jsonObject.keys
                            .toList()
                    )
                } else {
                    "array" to emptyList()
                }
            }
            else -> "unknown" to emptyList()
        }

    // =========================================================================
    // Cache Keys
    // =========================================================================

    private fun portCacheKey(config: XtreamApiConfig): String {
        val base = "${config.scheme.lowercase()}://${config.host}"
        val path =
            config.basePath?.let { bp ->
                val n =
                    bp
                        .trim()
                        .let { if (it.startsWith("/")) it else "/$it" }
                        .removeSuffix("/")
                if (n.isNotEmpty() && n != "/") n else ""
            }
                ?: ""
        return "$base$path|${config.username}"
    }

    private fun capCacheKey(
        config: XtreamApiConfig,
        port: Int,
    ): String = "${portCacheKey(config)}:$port"
}

/** XtreamDiscoveryResult – Result of full discovery. */
data class XtreamDiscoveryResult(
    val port: Int,
    val capabilities: XtreamCapabilities,
    val latencyMs: Long,
)

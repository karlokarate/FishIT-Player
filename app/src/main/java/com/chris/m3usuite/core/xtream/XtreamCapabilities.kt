package com.chris.m3usuite.core.xtream

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import androidx.core.content.edit

/**
 * XtreamCapabilities – EXTENDED (final, turbo + port resolver)
 *
 * Ziel:
 *  • Bei neuen Zugangsdaten schnell & deterministisch ermitteln, welche API-Actions/Aliasse funktionieren,
 *    welche Response-Typen & Key-Shapes vorliegen und welche ID/Name/Logo-Felder verwendet werden.
 *  • Vor der Discovery (falls nötig) den korrekten PORT für scheme|host|username ermitteln (std → Rennen → Cache),
 *    wobei Kandidatenlisten NICHT mehr hart sind, sondern injizierbar (nur definierte Fallbacks sind fix).
 *  • Ergebnis pro Provider+Account persistent cachen (TTL), damit der Client anschließend ohne Raten
 *    exakt die richtigen Endpunkte verwendet.
 *
 * Verwendung:
 *  val store = ProviderCapabilityStore(context)
 *  val portStore = EndpointPortStore(context)
 *  val discoverer = CapabilityDiscoverer(
 *      http = httpClient,
 *      store = store,
 *      portStore = portStore,
 *      // optional alles frei konfigurierbar:
 *      aliasCandidates = listOf("vod","movie","movies"),
 *      portResolverConfig = PortResolverConfig(
 *          httpCandidates = listOf(80, 8080, 8000, 8880, 2052, 2082, 2086, 2095),
 *          httpsCandidates = listOf(443, 8443, 2053, 2083, 2087, 2096),
 *      )
 *  )
 *
 *  // Auto-Discovery inkl. Port-Resolver (basePath optional; keine 'output'-Fixwerte mehr):
 *  val caps  = discoverer.discoverAuto(
 *      scheme="https", host="host.tld", username="U", password="P", basePath=null, forceRefresh=false
 *  )
 *
 *  // Oder wenn Port schon sicher ist:
 *  val cfg = XtreamConfig(scheme="https", host="host.tld", port=8443, username="U", password="P")
 *  val caps2 = discoverer.discover(cfg, forceRefresh=false)
 */

// ==========================================================
// Persistierte Capabilities (Model + Store)
// ==========================================================

@Serializable
data class XtreamCapabilities(
    val version: Int = 2,
    val cacheKey: String,                 // baseUrl|username – eindeutig pro Provider+Account (inkl. basePath!)
    val baseUrl: String,                  // e.g. http://host:8080[/xtream]
    val username: String,
    val resolvedAliases: ResolvedAliases = ResolvedAliases(), // finale Aktionsnamen (z. B. vodKind = "movie")
    val actions: Map<String, ActionCapability> = emptyMap(),  // action -> capability
    val schemaHints: Map<String, SchemaHint> = emptyMap(),    // z.B. Mapping für list/logo Felder
    val extras: ExtrasCapability = ExtrasCapability(),        // short_epg usw.
    val cachedAt: Long = 0L,
)

@Serializable
data class ResolvedAliases(
    val vodKind: String? = null,                      // "vod" | "movie" | "movies" | …
    val vodCandidates: List<String> = emptyList(),    // alle, die funktioniert haben
)

@Serializable
data class ActionCapability(
    val supported: Boolean = false,
    val responseType: String? = null,                 // "array" | "object" | "unknown"
    val sampleKeys: List<String> = emptyList(),
    val idField: String? = null,                      // z. B. stream_id, vod_id, movie_id, series_id, id
    val nameField: String? = null,                    // z. B. name, title
    val logoField: String? = null,                    // z. B. stream_icon/cover/poster_path/logo
)

@Serializable
data class SchemaHint(
    val fieldMappings: Map<String, String> = emptyMap() // normalized -> rawKey (z. B. "logo.vod" -> "poster_path")
)

@Serializable
data class ExtrasCapability(
    val supportsShortEpg: Boolean = false,
    val supportsSimpleDataTable: Boolean = false,
)

class ProviderCapabilityStore(
    context: Context,
    private val ttlMillis: Long = 24L * 60 * 60 * 1000,
    private val modelVersion: Int = 2
) {
    private val prefs = context.getSharedPreferences("xt_caps_v$modelVersion", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun get(cacheKey: String): XtreamCapabilities? {
        val raw = prefs.getString(cacheKey, null) ?: return null
        return try {
            val cap = json.decodeFromString(XtreamCapabilities.serializer(), raw)
            if (cap.version != modelVersion) return null
            if (System.currentTimeMillis() - cap.cachedAt > ttlMillis) return null
            cap
        } catch (_: Throwable) { null }
    }

    fun put(caps: XtreamCapabilities) {
        val str = json.encodeToString(XtreamCapabilities.serializer(), caps.copy(cachedAt = System.currentTimeMillis()))
        prefs.edit { putString(caps.cacheKey, str) }
    }
}

// ==========================================================
// Endpoint Port Store (Port-Resolver Cache) + Config
// ==========================================================

data class EndpointKey(val scheme: String, val host: String, val username: String)

/**
 * Kandidatenlisten sind injizierbar – keine harten Ports im Code nötig.
 * Nur Standardports (80/443) sind als definierte Fallbacks vorgesehen.
 */
data class PortResolverConfig(
    // Prefer 8080; drop 2095 to avoid Cloudflare/WAF traps
    val httpCandidates: List<Int> = listOf(80, 8080, 8000, 8880, 2052, 2082, 2086),
    val httpsCandidates: List<Int> = listOf(443, 8443, 2053, 2083, 2087, 2096),
    val parallelism: Int = 4
)

class EndpointPortStore(context: Context, private val ttlMillis: Long = 7L * 24 * 60 * 60 * 1000) {
    private val prefs = context.getSharedPreferences("xt_port_cache", Context.MODE_PRIVATE)

    fun getResolvedPort(key: EndpointKey): Int? {
        val v = prefs.getString("${key.scheme}://${key.host}|${key.username}", null) ?: return null
        val parts = v.split("|")
        if (parts.size != 2) return null
        val ts = parts[1].toLongOrNull() ?: return null
        if (System.currentTimeMillis() - ts > ttlMillis) return null
        return parts[0].toIntOrNull()
    }

    fun putResolvedPort(key: EndpointKey, port: Int) {
        val value = "$port|${System.currentTimeMillis()}"
        prefs.edit { putString("${key.scheme}://${key.host}|${key.username}", value) }
    }

    fun clear(key: EndpointKey) {
        prefs.edit { remove("${key.scheme}://${key.host}|${key.username}") }
    }
}

// ==========================================================
// Discovery (CapabilityProbe) – schnell & parallel
// ==========================================================

class CapabilityDiscoverer(
    private val http: OkHttpClient,
    private val store: ProviderCapabilityStore,
    private val portStore: EndpointPortStore,
    private val json: Json = Json { ignoreUnknownKeys = true },

    /**
     * Aliasse sind injizierbar; Standardliste ist nur ein definierter Fallback.
     * Reihenfolge ist Präferenzreihenfolge; erste funktionierende gewinnt.
     */
    private val aliasCandidates: List<String> = listOf("vod", "movie", "movies"),

    /**
     * Port-Resolver-Settings (injizierbar); Standardlisten sind definierte Fallbacks.
     */
    private val portResolverConfig: PortResolverConfig = PortResolverConfig(),
) {
    private val basicActions = listOf(
        "get_live_categories",
        "get_live_streams",
        "get_series_categories",
        "get_series",
        "get_series_info",
    )
    private val extraActions = listOf("get_short_epg", "get_simple_data_table")

    /**
     * Komfort-API: Port automatisch ermitteln (falls nötig) und dann Discovery fahren.
     * Keine Fixwerte für 'output' mehr – die neue XtreamConfig verwaltet Präferenzen intern.
     */
    suspend fun discoverAuto(
        scheme: String,
        host: String,
        username: String,
        password: String,
        basePath: String? = null,
        forceRefresh: Boolean = false
    ): XtreamCapabilities {
        val key = EndpointKey(scheme.lowercase(), host, username)
        val port = resolvePortIfNeeded(key, password, basePath)
        val cfg = XtreamConfig(
            scheme = key.scheme,
            host = host,
            port = port,
            username = username,
            password = password,
            // Pfadaliase/VOD-Alias setzt discover() nach dem Rennen per ResolvedAliases
            basePath = basePath
        )
        return discover(cfg, forceRefresh)
    }

    /**
     * Führt die Discovery mit limitiert parallelen Requests durch,
     * ermittelt Aliasse/Shapes/ID-Felder und persistiert das Ergebnis.
     */
    suspend fun discover(cfg: XtreamConfig, forceRefresh: Boolean = false): XtreamCapabilities = coroutineScope {
        // baseRoot inkl. basePath bauen (für eindeutigen CacheKey und baseUrl)
        val baseRoot = buildString {
            append(cfg.portalBase)
            cfg.basePath?.let { bp ->
                val n = bp.trim().let { if (it.startsWith("/")) it else "/$it" }.removeSuffix("/")
                if (n.isNotEmpty() && n != "/") append(n)
            }
        }

        val cacheKey = "$baseRoot|${cfg.username}"
        if (!forceRefresh) store.get(cacheKey)?.let { return@coroutineScope it }

        val actions = mutableMapOf<String, ActionCapability>()
        val schemaHints = mutableMapOf<String, SchemaHint>()
        val sem = Semaphore(4) // begrenze Parallelität (nett zum Provider)

        // --- Helper: eine Action probieren, Keys & Typ fingerprinten ---
        suspend fun probe(action: String, extra: String? = null): JsonElement? = sem.withPermit {
            val url = cfg.playerApi(action, extra)
            val req = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("Accept-Encoding", "gzip")
                .get()
                .build()
            return@withPermit try {
                http.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        actions[action] = ActionCapability(supported = false)
                        return@withPermit null
                    }
                    val body = resp.body?.string() ?: return@withPermit null
                    val el = json.parseToJsonElement(body)
                    val (type, keys) = fingerprint(el)
                    actions[action] = ActionCapability(supported = true, responseType = type, sampleKeys = keys)
                    el
                }
            } catch (_: Throwable) {
                actions[action] = ActionCapability(supported = false)
                null
            }
        }

        // 1) Kernaktionen
        val coreJobs = basicActions.map { act -> async { probe(act) } }

        // 2) Alias-Rennen für VOD Kategorien (erste gültige gewinnt)
        val vodCatJobs = aliasCandidates.map { alias -> async { alias to (probe("get_${alias}_categories") != null) } }
        val vodCatResults = vodCatJobs.awaitAll()
        val vodCandidates = vodCatResults.filter { it.second }.map { it.first }
        val vodKind = vodCandidates.firstOrNull()

        // 3) Streams + Info (beim Siegeralias echte ID ermitteln)
        if (vodKind != null) {
            // Streams laden und sampleKeys merken
            val streamsEl = probe("get_${vodKind}_streams")
            // Info nur dann probieren, wenn wir ein valides Item + idField finden
            var infoProbed = false
            if (streamsEl != null && streamsEl.isJsonArray()) {
                val arr = streamsEl.jsonArray
                if (arr.isNotEmpty() && arr.first().isJsonObject()) {
                    val first = arr.first().jsonObject
                    val idFieldCandidates = listOf("vod_id", "movie_id", "id")
                    val idField = idFieldCandidates.firstOrNull { first.containsKey(it) }
                    val idVal = idField?.let { first[it]?.toString()?.trim('"')?.toIntOrNull() }
                    if (idField != null && idVal != null) {
                        probe("get_${vodKind}_info", "&$idField=$idVal")
                        infoProbed = true
                    }
                }
            }
            if (!infoProbed) {
                // Kein harter Fallback auf "vod_id=1" – Streams-Shape reicht in der Praxis.
            }
        } else {
            // Optional: minimal alle testen – einmalige Kosten, danach Cache
            aliasCandidates.forEach { alias ->
                async { probe("get_${alias}_streams") }.await()
                // Keine Dummy-ID-Probe hier; Panels unterscheiden oft das Paramfeld.
            }
        }

        // 4) Extras
        val extraJobs = extraActions.map { act -> async { probe(act) } }
        (coreJobs + extraJobs).awaitAll()

        // Guardrail: if no core actions responded, invalidate port cache to avoid sticky bad ports
        runCatching {
            val coreSupported = listOf(
                "get_live_categories",
                "get_live_streams",
                "get_series_categories",
                "get_series"
            ).any { k -> actions[k]?.supported == true } || (vodCandidates.isNotEmpty())
            if (!coreSupported) {
                val key = EndpointKey(cfg.scheme.lowercase(), cfg.host, cfg.username)
                portStore.clear(key)
            }
        }

        // 5) Felder id/name/logo für Listen ermitteln
        inferIdNameLogo(actions)

        // 6) Schema-Hints (z. B. Logo pro Kind)
        val hints = mutableMapOf<String, String>()
        hints["logo.live"] = pickFirst(actions["get_live_streams"]?.sampleKeys, listOf("stream_icon", "logo"))
        // Wenn kein vodKind, nutze ersten Kandidaten als Heuristik – ansonsten "vod"
        val vk = vodKind ?: aliasCandidates.firstOrNull().orEmpty()
        hints["logo.vod"] = pickFirst(actions["get_${vk}_streams"]?.sampleKeys, listOf("stream_icon", "poster_path", "cover", "logo"))
        hints["logo.series"] = pickFirst(actions["get_series"]?.sampleKeys, listOf("cover", "poster_path", "logo"))
        schemaHints["list"] = SchemaHint(fieldMappings = hints.filterValues { it.isNotBlank() })

        val caps = XtreamCapabilities(
            version = 2,
            cacheKey = cacheKey,
            baseUrl = baseRoot,
            username = cfg.username,
            resolvedAliases = ResolvedAliases(vodKind = vodKind, vodCandidates = vodCandidates),
            actions = actions,
            schemaHints = schemaHints,
            extras = ExtrasCapability(
                supportsShortEpg = actions["get_short_epg"]?.supported == true,
                supportsSimpleDataTable = actions["get_simple_data_table"]?.supported == true
            ),
            cachedAt = System.currentTimeMillis()
        )
        store.put(caps)
        return@coroutineScope caps
    }

    // ---- Port Resolver ----

    private suspend fun resolvePortIfNeeded(
        key: EndpointKey,
        password: String,
        basePath: String?
    ): Int = coroutineScope {
        // 1) Cache-Hit? Revalidieren mit strenger tryPing; bei Fehler Cache verwerfen
        portStore.getResolvedPort(key)?.let { cached ->
            if (tryPing(key.scheme, key.host, cached, key.username, password, basePath)) {
                return@coroutineScope cached
            } else {
                portStore.clear(key)
            }
        }

        // 2) Erst Standardport probieren (http:80 / https:443) – definierte Fallbacks
        val std = if (key.scheme == "https") 443 else 80
        if (tryPing(key.scheme, key.host, std, key.username, password, basePath)) {
            portStore.putResolvedPort(key, std)
            return@coroutineScope std
        }

        // 3) Rennen gegen injizierte Kandidatenliste (limitiert parallel, nett zum Provider)
        val candidates = if (key.scheme == "https")
            portResolverConfig.httpsCandidates
        else
            portResolverConfig.httpCandidates

        val sem = Semaphore(portResolverConfig.parallelism)
        val jobs = candidates.distinct().map { p ->
            async {
                sem.withPermit {
                    if (tryPing(key.scheme, key.host, p, key.username, password, basePath)) p else null
                }
            }
        }
        val winner = jobs.awaitAll().firstOrNull { it != null }
        if (winner != null) {
            portStore.putResolvedPort(key, winner)
            return@coroutineScope winner
        } else {
            // Fallback: Standardport je Schema zurückgeben, selbst wenn Pings fehlschlugen.
            // Die nachfolgenden Action-Probes validieren die Nutzbarkeit.
            val fallback = if (key.scheme == "https") 443 else 80
            return@coroutineScope fallback
        }
    }

    private fun tryPing(
        scheme: String,
        host: String,
        port: Int,
        username: String,
        password: String,
        basePath: String?
    ): Boolean {
        // Some panels behind WAF return 521 unless an explicit action is provided.
        // Probe a small set of canonical actions with category_id=*, stopping at first valid JSON response.
        val actions = listOf(
            "get_live_streams",
            "get_series",
            "get_vod_streams"
        )

        fun buildUrl(action: String): String {
            val b = okhttp3.HttpUrl.Builder()
                .scheme(scheme.lowercase())
                .host(host)
                .port(port)
            basePath?.trim()?.let { bp ->
                val norm = (if (bp.startsWith("/")) bp else "/$bp").removeSuffix("/")
                if (norm.isNotEmpty() && norm != "/") {
                    norm.removePrefix("/").split('/')
                        .filter { it.isNotBlank() }
                        .forEach { seg -> b.addPathSegment(seg) }
                }
            }
            b.addPathSegment("player_api.php")
            b.addQueryParameter("action", action)
            b.addQueryParameter("category_id", "0")
            b.addQueryParameter("username", username)
            b.addQueryParameter("password", password)
            return b.build().toString()
        }

        for (act in actions) {
            val url = buildUrl(act)
            val req = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("Accept-Encoding", "gzip")
                .get()
                .build()
            try {
                http.newCall(req).execute().use { resp ->
                    if (resp.code in 200..299) {
                        val body = runCatching { resp.body?.string()?.trim().orEmpty() }.getOrDefault("")
                        if (body.isNotEmpty() && (body.startsWith("{") || body.startsWith("["))) {
                            val ok = runCatching { json.parseToJsonElement(body); true }.getOrDefault(false)
                            if (ok) return true
                        }
                    }
                }
            } catch (_: Throwable) {
                // try next action
            }
        }
        return false
    }

    // ---- Utilities ----
    private fun fingerprint(el: JsonElement): Pair<String, List<String>> = when {
        el.isJsonObject() -> "object" to el.jsonObject.keys.toList()
        el.isJsonArray() -> {
            val arr = el.jsonArray
            if (arr.isNotEmpty() && arr.first().isJsonObject()) "array" to arr.first().jsonObject.keys.toList()
            else "array" to emptyList()
        }
        else -> "unknown" to emptyList()
    }

    private fun inferIdNameLogo(actions: MutableMap<String, ActionCapability>) {
        fun enrich(key: String, ids: List<String>, names: List<String>, logos: List<String>) {
            val cap = actions[key] ?: return
            if (!cap.supported) return
            val idField = pickFirst(cap.sampleKeys, ids)
            val nameField = pickFirst(cap.sampleKeys, names)
            val logoField = pickFirst(cap.sampleKeys, logos)
            actions[key] = cap.copy(idField = idField, nameField = nameField, logoField = logoField)
        }
        enrich("get_live_streams", ids = listOf("stream_id", "id"), names = listOf("name", "title"), logos = listOf("stream_icon", "logo"))
        aliasCandidates.forEach { alias ->
            enrich(
                "get_${alias}_streams",
                ids = listOf("vod_id", "movie_id", "id"),
                names = listOf("name", "title"),
                logos = listOf("poster_path", "cover", "stream_icon", "logo")
            )
        }
        enrich("get_series", ids = listOf("series_id", "id"), names = listOf("name", "title"), logos = listOf("cover", "poster_path", "logo"))
    }
}

// ==========================================================
// Json helpers
// ==========================================================
private fun JsonElement.isJsonArray(): Boolean = runCatching { this.jsonArray; true }.getOrDefault(false)
private fun JsonElement.isJsonObject(): Boolean = runCatching { this.jsonObject; true }.getOrDefault(false)
private fun JsonElement.jsonObjectOrNull(): JsonObject? = runCatching { this.jsonObject }.getOrNull()

// ==========================================================
// Small utils
// ==========================================================
private fun pickFirst(keys: List<String>?, candidates: List<String>): String =
    candidates.firstOrNull { c -> keys?.any { it.equals(c, ignoreCase = true) } == true }.orEmpty()

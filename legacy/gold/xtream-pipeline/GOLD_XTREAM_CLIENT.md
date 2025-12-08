# Gold: Xtream Pipeline Patterns

## Overview
This document captures the proven patterns from v1's Xtream Codes API integration that should be preserved in v2.

## Source Files
- `XtreamClient.kt` (903 lines) - API client with rate limiting and caching
- `XtreamConfig.kt` (400 lines) - URL factory and path generation
- `XtreamCapabilities.kt` (630 lines) - Discovery and port resolution
- `XtreamModels.kt` (206 lines) - Raw and normalized models
- `XtreamObxRepository.kt` (2829 lines) - ObjectBox persistence layer

---

## 1. Rate Limiting & Caching

### Key Pattern: Per-Host Rate Limiting
**v1 Implementation:** `XtreamClient`

```kotlin
/**
 * GOLD: Per-Host Rate Limiting
 * 
 * Why this works:
 * - Xtream panels are often resource-constrained
 * - 120ms minimum interval prevents rate limit errors
 * - Uses Mutex for thread-safe timing
 * - Applied globally across all request types
 */
companion object {
    private val rateMutex = Mutex()
    private var lastCallAt = 0L
    private const val MIN_INTERVAL_MS = 120L

    private suspend fun takeRateSlot() {
        rateMutex.withLock {
            val now = SystemClock.elapsedRealtime()
            val delta = now - lastCallAt
            if (delta in 0 until MIN_INTERVAL_MS) {
                delay(MIN_INTERVAL_MS - delta)
            }
            lastCallAt = SystemClock.elapsedRealtime()
        }
    }
}
```

**Why preserve:** Prevents server overload and 429 errors from flaky Xtream panels.

### In-Memory Cache with TTL
**Pattern:** LRU cache with separate TTLs for different data types

```kotlin
/**
 * GOLD: Dual-TTL In-Memory Cache
 * 
 * Why this works:
 * - Category lists change rarely (60s TTL)
 * - EPG data changes frequently (15s TTL)
 * - LRU eviction at 512 entries
 * - Thread-safe via Mutex
 */
private data class CacheEntry(
    val at: Long,
    val body: String,
)

private val cacheLock = Mutex()
private val cache = object : LinkedHashMap<String, CacheEntry>(512, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheEntry>?): Boolean = 
        size > 512
}

private const val CACHE_TTL_MS = 60_000L
private const val EPG_CACHE_TTL_MS = 15_000L

private suspend fun readCache(url: String, isEpg: Boolean): String? {
    val ttl = if (isEpg) EPG_CACHE_TTL_MS else CACHE_TTL_MS
    return cacheLock.withLock {
        val e = cache[url] ?: return@withLock null
        if ((SystemClock.elapsedRealtime() - e.at) <= ttl) e.body else null
    }
}
```

**Why preserve:** Reduces server load and improves UI responsiveness.

---

## 2. URL Generation & Path Handling

### Key Pattern: VOD Alias Rotation
**v1 Implementation:** `XtreamConfig`

```kotlin
/**
 * GOLD: VOD Alias Rotation
 * 
 * Why this works:
 * - Different Xtream panels use different VOD paths
 * - Aliases: "vod", "movie", "movies"
 * - Try each until one works
 * - Cache working alias per provider
 */
private var vodKind: String = "vod"

suspend fun getVodStreams(): List<XtreamVodStream> {
    val aliases = listOf("vod", "movie", "movies")
    for (alias in aliases) {
        try {
            val url = cfg.vodListUrl(alias)
            val result = callApi(url)
            if (result.isNotEmpty()) {
                vodKind = alias  // Cache working alias
                return result
            }
        } catch (e: Exception) {
            // Try next alias
        }
    }
    throw IOException("No working VOD alias found")
}
```

**Why preserve:** Handles inconsistent Xtream panel implementations.

### Stream URL Generation
**Pattern:** Consistent URL building with auth tokens

```kotlin
/**
 * GOLD: Stream URL Generation
 * 
 * Format: http://host:port/{type}/{username}/{password}/{id}.{ext}
 * 
 * Types:
 * - live: /live/{username}/{password}/{streamId}.{ext}
 * - vod: /movie/{username}/{password}/{vodId}.{ext}
 * - series: /series/{username}/{password}/{seriesId}.{ext}
 * 
 * Why this works:
 * - Explicit auth in URL (no separate auth header)
 * - Extension preference order (m3u8 > ts > mp4)
 * - Panel URL format is standardized
 */
fun buildStreamUrl(
    type: PathKind,
    id: Int,
    ext: String
): String {
    val pathType = when (type) {
        PathKind.LIVE -> "live"
        PathKind.VOD -> vodKind  // Use cached alias
        PathKind.SERIES -> "series"
    }
    return "$scheme://$host:$port/$pathType/$username/$password/$id.$ext"
}
```

**Why preserve:** Xtream URL format is quasi-standard across panels.

---

## 3. Discovery & Capabilities

### Key Pattern: Multi-Port Discovery
**v1 Implementation:** `XtreamCapabilities`

```kotlin
/**
 * GOLD: Multi-Port Discovery
 * 
 * Why this works:
 * - Xtream panels run on various ports (80, 8080, 25461, etc.)
 * - Try all common ports in parallel
 * - First successful response wins
 * - Cache working port per provider
 */
suspend fun discoverPort(
    scheme: String,
    host: String,
    username: String,
    password: String
): Int {
    val ports = listOf(80, 8080, 25461, 8000, 443)
    return coroutineScope {
        ports.map { port ->
            async {
                try {
                    testConnection(scheme, host, port, username, password)
                    port
                } catch (e: Exception) {
                    null
                }
            }
        }.awaitAll().filterNotNull().firstOrNull() 
            ?: throw IOException("No working port found")
    }
}
```

**Why preserve:** Handles diverse panel configurations without user input.

### Capability Detection
**Pattern:** Detect panel features via API probing

```kotlin
/**
 * GOLD: Capability Detection
 * 
 * Detected Features:
 * - EPG support (via get_epg endpoint)
 * - Series support (via get_series endpoint)
 * - Catchup support (via timestamp parameter)
 * - XMLTV support (via xmltv.php endpoint)
 * 
 * Why this works:
 * - Not all panels support all features
 * - Probing is cheap (just HEAD requests)
 * - Cache results to avoid repeated probing
 */
data class XtreamCapabilities(
    val hasEpg: Boolean = false,
    val hasSeries: Boolean = false,
    val hasCatchup: Boolean = false,
    val hasXmltv: Boolean = false
)

suspend fun detectCapabilities(config: XtreamConfig): XtreamCapabilities {
    return XtreamCapabilities(
        hasEpg = testEndpoint("${config.baseUrl}/player_api.php?action=get_simple_data_table&stream_id=1"),
        hasSeries = testEndpoint("${config.baseUrl}/player_api.php?action=get_series"),
        hasCatchup = testLiveCatchup(config),
        hasXmltv = testEndpoint("${config.baseUrl}/xmltv.php")
    )
}
```

**Why preserve:** Graceful degradation when panels have limited features.

---

## 4. EPG Integration

### Key Pattern: Parallel EPG Prefetch
**v1 Implementation:** `XtreamClient`

```kotlin
/**
 * GOLD: Parallel EPG Prefetch with Limits
 * 
 * Why this works:
 * - EPG for 100+ channels takes time
 * - Parallelize with Semaphore(4) to limit concurrency
 * - Separate cache TTL for EPG (15s vs 60s)
 * - Fire-and-forget for background prefetch
 */
private val epgSemaphore = Semaphore(4)

suspend fun prefetchEpgForChannels(channelIds: List<Int>) {
    channelIds.forEach { channelId ->
        launch {
            epgSemaphore.withPermit {
                try {
                    getEpgForStream(channelId)
                } catch (e: Exception) {
                    // Log but don't fail
                }
            }
        }
    }
}
```

**Why preserve:** Keeps EPG data fresh without blocking UI.

### EPG Merging Strategy
**Pattern:** Merge EPG data with channel metadata

```kotlin
/**
 * GOLD: EPG Merging
 * 
 * Why this works:
 * - Channel list has basic metadata (name, logo, category)
 * - EPG endpoint has program schedule
 * - Merge by stream_id
 * - Fall back to channel name if no EPG
 */
data class ChannelWithEpg(
    val streamId: Int,
    val name: String,
    val logo: String?,
    val currentProgram: EpgProgram?,
    val nextProgram: EpgProgram?
)

fun mergeEpgWithChannels(
    channels: List<XtreamChannel>,
    epgData: Map<Int, List<EpgProgram>>
): List<ChannelWithEpg> {
    return channels.map { channel ->
        val programs = epgData[channel.streamId] ?: emptyList()
        val now = System.currentTimeMillis() / 1000
        ChannelWithEpg(
            streamId = channel.streamId,
            name = channel.name,
            logo = channel.streamIcon,
            currentProgram = programs.find { it.start <= now && it.end >= now },
            nextProgram = programs.find { it.start > now }
        )
    }
}
```

**Why preserve:** Provides rich live TV experience with program info.

---

## 5. Error Handling & Resilience

### Key Pattern: Graceful Degradation
**v1 Implementation:** Throughout `XtreamClient`

```kotlin
/**
 * GOLD: Graceful Degradation
 * 
 * Strategies:
 * 1. Empty results instead of crashes
 * 2. Log errors but continue processing
 * 3. Fall back to alternative endpoints
 * 4. Cache last-known-good data
 */

// Example 1: Empty list on error
suspend fun getVodStreams(): List<XtreamVodStream> {
    return try {
        callApi(cfg.vodListUrl())
    } catch (e: Exception) {
        UnifiedLog.w("Xtream", "Failed to fetch VOD streams", e)
        emptyList()  // Don't crash, return empty
    }
}

// Example 2: Fallback to alternative endpoint
suspend fun getCategories(): List<XtreamCategory> {
    return try {
        callApi("${cfg.baseUrl}/player_api.php?action=get_live_categories")
    } catch (e: Exception) {
        // Fallback to category_id=* approach
        callApi("${cfg.baseUrl}/player_api.php?action=get_live_streams&category_id=*")
    }
}

// Example 3: Credential redaction in logs
private fun redact(url: String): String =
    url.replace(Regex("(?i)(password)=([^&]*)"), "$1=***")
       .replace(Regex("(?i)(username)=([^&]*)"), "$1=***")
```

**Why preserve:** Flaky Xtream panels are the norm, not the exception.

---

## 6. Category & Pagination

### Key Pattern: Category Fallback
**v1 Implementation:** `XtreamClient`

```kotlin
/**
 * GOLD: Category Fallback Strategy
 * 
 * Why this works:
 * - Some panels support category_id=* (all categories)
 * - Others require category_id=0 (uncategorized)
 * - Try * first, fall back to 0 if empty
 * - Cache working strategy per provider
 */
suspend fun getStreams(categoryId: String? = null): List<XtreamStream> {
    val catId = categoryId ?: "*"
    val url = "${cfg.baseUrl}/player_api.php?action=get_live_streams&category_id=$catId"
    
    val result = callApi(url)
    if (result.isEmpty() && catId == "*") {
        // Fallback to category_id=0
        return callApi(url.replace("category_id=*", "category_id=0"))
    }
    return result
}
```

**Why preserve:** Handles both panel variants without user configuration.

---

## 7. v2 Porting Guidance

### What to Port

1. **Rate Limiting** → Port to `pipeline/xtream/client/XtreamApiClient`
   - Keep 120ms minimum interval
   - Keep per-host Mutex pattern
   - Update logging imports

2. **Caching** → Port to `pipeline/xtream/client/XtreamApiClient`
   - Keep dual-TTL cache
   - Keep LRU eviction
   - Consider moving to dedicated cache module

3. **URL Generation** → Port to `pipeline/xtream/client/XtreamUrlBuilder`
   - Keep VOD alias rotation
   - Keep stream URL format
   - Document all path patterns

4. **Discovery** → Port to `pipeline/xtream/client/XtreamDiscovery`
   - Keep multi-port probing
   - Keep capability detection
   - Cache results in DataStore

5. **EPG** → Port to `pipeline/xtream/repository/XtreamEpgRepository`
   - Keep parallel prefetch
   - Keep merging strategy
   - Add persistence layer

### What to Change

1. **No Normalization in Client**
   - v1: Client includes title cleaning
   - v2: Emit RawMediaMetadata only
   - Reason: MEDIA_NORMALIZATION_CONTRACT.md

2. **Repository Interface**
   - v1: `XtreamObxRepository` is concrete
   - v2: Implement `XtreamCatalogRepository` interface
   - Reason: Module boundaries, testability

3. **Logging**
   - v1: `import com.chris.m3usuite.core.logging.UnifiedLog`
   - v2: `import com.fishit.player.infra.logging.UnifiedLog`
   - Reason: Module structure

4. **No Singleton**
   - v1: Companion object caches
   - v2: Inject cache as dependency
   - Reason: Testability, multiple providers

### Implementation Phases

**Phase 1: Core Client** (NEAR FUTURE)
- [ ] Port rate limiting
- [ ] Port caching
- [ ] Port URL generation
- [ ] Add XtreamApiClient interface

**Phase 2: Discovery**
- [ ] Port multi-port detection
- [ ] Port capability detection
- [ ] Add discovery cache
- [ ] Integrate with settings

**Phase 3: Repository**
- [ ] Port to XtreamCatalogRepository
- [ ] Implement toRawMediaMetadata()
- [ ] Add EPG persistence
- [ ] Add image URL handling

**Phase 4: Testing**
- [ ] Unit tests for URL builder
- [ ] Unit tests for discovery
- [ ] Integration tests with real panels
- [ ] Performance tests for large catalogs

---

## Key Differences from Telegram Pipeline

1. **No Auth Session** - Xtream uses credentials in URLs, no separate auth flow
2. **No File Downloads** - Streams are direct URLs, no local caching
3. **More Error Prone** - Xtream panels vary wildly in reliability
4. **Category System** - Hierarchical categories (Live > Sports > NFL)
5. **EPG Complexity** - Must merge EPG data with channel metadata

---

## References

- v1 Source: `/legacy/v1-app/app/src/main/java/com/chris/m3usuite/core/xtream/`
- v2 Target: `/pipeline/xtream/src/main/java/com/fishit/player/pipeline/xtream/`
- v2 Contract: `/docs/v2/XTREAM_PIPELINE_V2_REUSE_ANALYSIS.md`
- v2 Analysis: `/docs/v2/V1_VS_V2_ANALYSIS_REPORT.md`

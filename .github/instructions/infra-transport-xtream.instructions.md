---
applyTo: 
  - infra/transport-xtream/**
---

# 🏆 PLATIN Instructions: infra/transport-xtream

**Version:** 1.0  
**Last Updated:** 2026-02-04  
**Status:** Active

> **PLATIN STANDARD** - Xtream Codes API Transport Layer (Absolute Perfection).
>
> **Purpose:** Provides HTTP-based access to Xtream Codes panels with maximum compatibility.
> This module DERIVES `@XtreamHttpClient` from `@PlatformHttpClient` (infra/networking) via `.newBuilder()`.
> The platform client provides connection pool, Chucker, User-Agent, and base timeouts.
> This module adds Xtream-specific: Accept:json, Dispatcher parallelism, callTimeout, followSslRedirects=false.
> Upper layers (pipeline, playback) consume typed interfaces - NEVER raw OkHttp types.
>
> **Binding Contracts (Priority Order):**
> 1. `contracts/XTREAM_SCAN_PREMIUM_CONTRACT_V1.md` (v1.0) - Premium Contract (AUTHORITATIVE)
> 2. `contracts/GLOSSARY_v2_naming_and_modules.md` (Section 1.4) - Infrastructure Terms
> 3. `contracts/LOGGING_CONTRACT_V2.md` (v1.1) - Logging rules
>
> **Parent–Child HTTP Client Architecture:**
> ```
> @PlatformHttpClient (infra/networking)        ← generic: pool + Chucker + User-Agent + 30s base timeouts
>   └── @XtreamHttpClient (transport-xtream)    ← +Accept:json, +Dispatcher, +callTimeout=30s, -followSslRedirects
>         ├── streamingClient (lazy .newBuilder) ← +readTimeout=120s, +callTimeout=180s
>         └── playbackClient (lazy .newBuilder)  ← +followSslRedirects=true
> ```

---

## 🔴 ABSOLUTE HARD RULES

### 1. Premium Contract HTTP Configuration (MANDATORY)

```kotlin
// @XtreamHttpClient is derived from @PlatformHttpClient via .newBuilder()
// Base timeouts (connect/read/write 30s), User-Agent, Chucker → inherited from platform

// ✅ CORRECT: Derive from platform, add Xtream-specific settings
@Provides @XtreamHttpClient
fun provideXtreamOkHttpClient(
    @PlatformHttpClient platformClient: OkHttpClient,
    parallelism: XtreamParallelism,
): OkHttpClient = platformClient.newBuilder()
    .callTimeout(30, TimeUnit.SECONDS)          // Premium Contract Section 3
    .followSslRedirects(false)                  // Xtream security
    .addInterceptor { /* Accept: application/json */ }
    .dispatcher(Dispatcher().apply {            // Premium Contract Section 5
        maxRequests = parallelism.value
        maxRequestsPerHost = parallelism.value
    })
    .build()

// ❌ FORBIDDEN: Creating a standalone OkHttpClient (bypasses platform)
val wrongClient = OkHttpClient.Builder()        // WRONG - use platformClient.newBuilder()!
    .connectTimeout(30, TimeUnit.SECONDS)
    .build()
```

### 2. Premium Contract Headers (MANDATORY - Section 4)

```kotlin
// User-Agent → set by @PlatformHttpClient (inherited, no need to add)
// Accept: application/json → set by @XtreamHttpClient interceptor

object XtreamTransportConfig {
    // User-Agent delegates to PlatformHttpConfig (app-wide SSOT)
    const val USER_AGENT = PlatformHttpConfig.USER_AGENT
    const val ACCEPT_JSON = "application/json"
}

// ✅ Xtream-specific Accept header (added in XtreamTransportModule)
.addInterceptor { chain ->
    val request = chain.request()
    if (request.header("Accept") == null) {
        chain.proceed(request.newBuilder().header("Accept", ACCEPT_JSON).build())
    } else {
        chain.proceed(request)
    }
}

// ⚠️ CRITICAL: Two Distinct Header Profiles
object XtreamHttpHeaders {
    // API Headers (JSON requests) - Accept: application/json
    fun defaults(): Map<String, String>
    
    // PLAYBACK Headers (video streams) - Accept: */*, Accept-Encoding: identity
    fun playbackDefaults(): Map<String, String>
}
```

### 3. Device-Aware Parallelism (MANDATORY - Section 5)

```kotlin
// Per Premium Contract Section 5: Concurrency
// Phone/Tablet: parallelism = 12
// FireTV/low-RAM: parallelism = 3
// SSOT in transport via OkHttp Dispatcher + Coroutine Semaphore
// NOT in UI, NOT in pipeline!

data class XtreamParallelism(val value: Int)

// Applied to OkHttp Dispatcher AND Coroutine Semaphore
```

### 4. Layer Boundary Enforcement (Section 0)

```kotlin
// ✅ ALLOWED in Transport
import okhttp3.*
import com.fishit.player.core.model.*

// ❌ FORBIDDEN in Transport
import com.fishit.player.pipeline.*           // Pipeline layer!
import com.fishit.player.core.persistence.*  // Persistence layer!

// ❌ FORBIDDEN: Pipeline DTOs
data class XtreamVodItem(...)    // WRONG - belongs in pipeline!

// ✅ CORRECT: Transport DTOs (raw API response)
data class XtreamVodStream(...)  // Raw API response
```

### 5. Credential Security (Section 1)

```kotlin
// X-1: Credentials SSOT
// - Encrypted at rest (EncryptedSharedPreferences)
// - NEVER logged
// - No plaintext fallback

interface XtreamCredentialsStore {
    suspend fun read(): XtreamStoredConfig?
    suspend fun write(config: XtreamStoredConfig)
    suspend fun clear()
}
```

### 6. Endpoint Coverage (Section 8)

```kotlin
interface XtreamApiClient {
    // X-10: Preflight
    suspend fun getServerInfo(): Result<XtreamServerInfo>
    suspend fun getPanelInfo(): Result<XtreamPanelInfo>
    
    // X-11: Core Catalog
    suspend fun getLiveStreams(categoryId: Int? = null): Result<List<XtreamLiveStream>>
    suspend fun getVodStreams(categoryId: Int? = null): Result<List<XtreamVodStream>>
    suspend fun getSeries(categoryId: Int? = null): Result<List<XtreamSeriesInfo>>
    suspend fun getSeriesInfo(seriesId: Int): Result<XtreamSeriesDetails>
    suspend fun getVodInfo(vodId: Int): Result<XtreamVodDetails>
    
    // Categories (fallback per Section 7)
    suspend fun getLiveCategories(): Result<List<XtreamCategory>>
    suspend fun getVodCategories(): Result<List<XtreamCategory>>
    suspend fun getSeriesCategories(): Result<List<XtreamCategory>>
    
    // X-12: EPG
    suspend fun getShortEpg(streamId: Int, limit: Int = 10): Result<List<XtreamEpgEntry>>
    suspend fun getEpg(streamId: Int): Result<List<XtreamEpgEntry>>
}
```

### 7. Large Payload Safety (Section 6)

```kotlin
// Streaming-friendly parsing (no duplicate buffers)
// Persistence "as-you-go" (CatalogSyncService)
// No payload dumps in logs (counts only)

// ✅ CORRECT
UnifiedLog.i(TAG) { "Fetched VOD streams: count=${streams.size}" }

// ❌ FORBIDDEN
UnifiedLog.d(TAG) { "VOD streams: $streams" }  // Giant payload!
```

### 8. Logging (Section 9)

```kotlin
// UnifiedLog only
// High-level events and endpoint timing
// Credentials NEVER logged
// Redact URLs containing credentials

fun String.redactCredentials(): String {
    return this
        .replace(Regex("password=[^&]+"), "password=***")
        .replace(Regex("username=[^&]+"), "username=***")
}
```

---

## 📋 Module Structure

```
infra/transport-xtream/
├── XtreamApiClient.kt              # Public interface
├── DefaultXtreamApiClient.kt       # Implementation
├── XtreamApiConfig.kt              # Configuration
├── XtreamApiModels.kt              # Response DTOs
├── XtreamUrlBuilder.kt             # URL factory
├── XtreamDiscovery.kt              # Port/capability discovery
├── XtreamCredentialsStore.kt       # Credential interface
├── EncryptedXtreamCredentialsStore.kt  # Secure implementation
├── XtreamHttpHeaders.kt            # Header profiles
├── XtreamTransportConfig.kt        # Premium Contract constants
├── XtreamParallelism.kt            # Device-aware parallelism
└── di/XtreamTransportModule.kt     # Hilt bindings
```

---

## ✅ PLATIN Checklist

### Premium Contract Compliance
- [ ] Timeouts: connect=30s, read=30s, write=30s, call=30s
- [ ] User-Agent: FishIT-Player/2.x (Android)
- [ ] Accept: application/json (API), */* (playback)
- [ ] Accept-Encoding: gzip (API), identity (playback)
- [ ] Parallelism: 10 (phone), 3 (FireTV) via Dispatcher + Semaphore

### Security
- [ ] Credentials encrypted (EncryptedSharedPreferences)
- [ ] No plaintext fallback
- [ ] Credentials never logged
- [ ] URLs redacted in logs

### Layer Boundaries
- [ ] No pipeline imports
- [ ] No persistence imports
- [ ] No pipeline DTOs (XtreamVodItem)

### Logging
- [ ] UnifiedLog only
- [ ] Lambda-based in hot paths
- [ ] Large payloads: counts only

---

## 📚 Reference Documents

1. `/contracts/XTREAM_SCAN_PREMIUM_CONTRACT_V1.md` - AUTHORITATIVE
2. `/contracts/GLOSSARY_v2_naming_and_modules.md` - Section 1.4
3. `/contracts/LOGGING_CONTRACT_V2.md` - v1.1
4. `/infra/transport-xtream/README.md`

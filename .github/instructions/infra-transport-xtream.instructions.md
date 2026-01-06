---
applyTo: 
  - infra/transport-xtream/**
---

# 🏆 PLATIN Instructions: infra/transport-xtream

> **PLATIN STANDARD** - Xtream Codes API Transport Layer (Absolute Perfection).
>
> **Purpose:** Provides HTTP-based access to Xtream Codes panels with maximum compatibility.
> This module OWNS OkHttp configuration, rate limiting, credential storage, and panel discovery.
> Upper layers (pipeline, playback) consume typed interfaces - NEVER raw OkHttp types.
>
> **Binding Contracts (Priority Order):**
> 1. `contracts/XTREAM_SCAN_PREMIUM_CONTRACT_V1.md` (v1.0) - Premium Contract (AUTHORITATIVE)
> 2. `contracts/GLOSSARY_v2_naming_and_modules.md` (Section 1.4) - Infrastructure Terms
> 3. `contracts/LOGGING_CONTRACT_V2.md` (v1.1) - Logging rules
>
> **Core Principle: Premium Contract Compliance**
> ```
> Timeouts: connectTimeout=30s, readTimeout=30s, writeTimeout=30s, callTimeout=30s
> Headers:  User-Agent: FishIT-Player/2.x (Android), Accept: application/json, Accept-Encoding: gzip
> Parallelism: Phone/Tablet=10, FireTV/low-RAM=3 (OkHttp Dispatcher + Semaphore)
> ```

---

## 🔴 ABSOLUTE HARD RULES

### 1. Premium Contract HTTP Configuration (MANDATORY)

```kotlin
// Per Premium Contract Section 3: HTTP Timeouts

// ✅ MANDATORY in OkHttpClient provider
val xtreamHttpClient = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)   // Premium Contract
    .readTimeout(30, TimeUnit.SECONDS)      // Premium Contract
    .writeTimeout(30, TimeUnit.SECONDS)     // Premium Contract
    .callTimeout(30, TimeUnit.SECONDS)      // Premium Contract (MANDATORY)
    .addInterceptor(headerInterceptor)      // Per Section 4
    .dispatcher(xtreamDispatcher)           // Per Section 5
    .build()

// ❌ FORBIDDEN: Non-compliant timeouts
val wrongClient = OkHttpClient.Builder()
    .connectTimeout(60, TimeUnit.SECONDS)   // WRONG - must be 30s!
    .readTimeout(120, TimeUnit.SECONDS)     // WRONG - must be 30s!
    // No callTimeout                        // WRONG - mandatory!
    .build()
```

### 2. Premium Contract Headers (MANDATORY - Section 4)

```kotlin
object XtreamTransportConfig {
    const val USER_AGENT = "FishIT-Player/2.x (Android)"
    const val ACCEPT_JSON = "application/json"
    const val ACCEPT_ENCODING = "gzip"
}

// ✅ MANDATORY: API request interceptor
val headerInterceptor = Interceptor { chain ->
    val request = chain.request().newBuilder()
        .header("User-Agent", XtreamTransportConfig.USER_AGENT)
        .header("Accept", XtreamTransportConfig.ACCEPT_JSON)
        .header("Accept-Encoding", XtreamTransportConfig.ACCEPT_ENCODING)
        .build()
    chain.proceed(request)
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
// Phone/Tablet: parallelism = 10
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

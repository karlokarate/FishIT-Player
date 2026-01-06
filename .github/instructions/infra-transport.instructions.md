---
applyTo: 
  - infra/transport-telegram/**
  - infra/transport-xtream/**
  - infra/transport-io/**
---

# 🏆 PLATIN Instructions:  infra/transport-*

> **PLATIN STANDARD** - External API Integration Layer. 
>
> **Purpose:** Provides abstracted access to external services (TDLib, Xtream API, local files).
> This is the **ONLY** layer allowed to directly interact with external SDKs and network libraries.
> All upper layers consume clean, typed interfaces with source-agnostic DTOs. 

---

## 🔴 ABSOLUTE HARD RULES

### 1. This Layer OWNS External Dependencies

```kotlin
// ✅ ONLY ALLOWED HERE
// Transport-Telegram: 
import org.drinkless.td.TdApi.*               // TDLib (raw)
import dev.g000sha256.tdl.*                    // TDLib coroutines wrapper

// Transport-Xtream:
import okhttp3.*                                // HTTP client
import retrofit2.*                              // REST client (if used)

// Transport-IO:
import android.content.ContentResolver         // Android media
import java.io.File                            // File system

// All other modules use abstracted interfaces from this layer
```

### 2. Export Clean DTOs ONLY - NEVER Leak SDK Types

```kotlin
// ✅ CORRECT:  Clean, source-agnostic DTOs
// Transport-Telegram:
data class TgMessage(...)
data class TgChat(...)
data class TgContent(...)
data class TgFile(...)
data class TgThumbnailRef(...)

// Transport-Xtream: 
data class XtreamVodStream(...)
data class XtreamLiveStream(...)
data class XtreamSeriesInfo(...)
data class XtreamCapabilities(...)

// ❌ FORBIDDEN: Leaking raw SDK types
fun getUpdates(): List<TdApi.Update>           // WRONG - wrap it! 
fun getTelegramMessages(): List<TdApi.Message> // WRONG - use TgMessage!
fun getClient(): TdlClient                      // WRONG - never expose client!
fun getOkHttpClient(): OkHttpClient             // WRONG - internal only!
```

### 3. Typed Interfaces - NOT Monolithic Clients

```kotlin
// ✅ CORRECT: Segregated interfaces (Interface Segregation Principle)
interface TelegramAuthClient { ... }           // Auth operations only
interface TelegramHistoryClient { ... }        // Chat/message operations only
interface TelegramFileClient { ... }           // File download operations only
interface TelegramThumbFetcher { ... }         // Thumbnail loading only
interface TelegramRemoteResolver { ...  }       // remoteId resolution only

interface XtreamApiClient { ... }              // Core API operations

// ❌ FORBIDDEN:  God objects
interface TelegramTransportClient {             // DEPRECATED - too broad
    suspend fun auth()
    suspend fun getChats()
    suspend fun downloadFile()
    suspend fun resolveRemote()
    // ... 50 more methods
}
```

### 4. No Business Logic

```kotlin
// ❌ FORBIDDEN in Transport
fun normalizeTitle(title: String): String              // → core/metadata-normalizer
fun classifyMediaType(item: TgMessage): MediaType      // → pipeline
fun generateGlobalId(... ): String                      // → core/metadata-normalizer
fun extractSeasonEpisode(title: String): Pair<Int, Int>?  // → pipeline
suspend fun searchTmdb(title: String): TmdbRef?        // → core/metadata-normalizer

// ✅ CORRECT: Pure transport/mapping
suspend fun fetchMessage(chatId: Long, messageId: Long): TgMessage
suspend fun downloadFile(fileId: Int): ByteArray
suspend fun getLiveStreams(): List<XtreamLiveStream>
```

### 5. No Persistence or Caching Logic

```kotlin
// ❌ FORBIDDEN
import io.objectbox.*
import com.fishit.player.core.persistence.*
import androidx.room.*

class TelegramMessageCache { ... }             // WRONG - data layer handles this
suspend fun saveToDatabase(message: TgMessage) // WRONG - transport is stateless

// ✅ CORRECT:  Stateless transport
// Data layer (infra/data-*) handles persistence
// Transport only provides fetching primitives
```

---

## 📋 Module Responsibilities

### infra/transport-telegram

**Purpose:** TDLib SDK wrapper providing typed Telegram access. 

| Responsibility | Allowed | Forbidden |
|----------------|---------|-----------|
| TDLib client lifecycle | ✅ | Exposing `TdlClient` to upper layers |
| Auth state machine | ✅ | Login UI (belongs in feature layer) |
| Message/chat fetching | ✅ | Message classification (belongs in pipeline) |
| File downloads | ✅ | MP4 parsing (belongs in playback layer) |
| Thumbnail fetching | ✅ | Image caching (Coil handles this) |
| TDLib logging bridge | ✅ | Log persistence (infra/logging handles this) |

**Public Interfaces:**
- `TelegramAuthClient` - Authentication operations
- `TelegramHistoryClient` - Chat/message browsing
- `TelegramFileClient` - File download primitives
- `TelegramThumbFetcher` - Thumbnail loading for Coil
- `TelegramRemoteResolver` - remoteId → fileId resolution

**Wrapper DTOs:**
- `TgMessage` - Transport-level message
- `TgChat` - Transport-level chat
- `TgContent` - Media content descriptor
- `TgFile` - File download state
- `TgThumbnailRef` - Thumbnail reference (remoteId-first)

**Internal (NOT exported):**
- `DefaultTelegramClient` - Implements all interfaces, owns TDLib state
- `TelegramTdlibClientFactory` - ⚠️ Factory for TdlClient (internal use only)
- `TdlibClientProvider` - ⚠️ **v1 legacy pattern**, must NEVER be exposed to upper layers

---

### infra/transport-xtream

**Purpose:** Xtream Codes API client providing typed REST access.

| Responsibility | Allowed | Forbidden |
|----------------|---------|-----------|
| HTTP API calls | ✅ | Repository access |
| Port auto-discovery | ✅ | Credential storage encryption (belongs in transport) |
| Capability detection | ✅ | Media normalization (belongs in pipeline) |
| URL construction | ✅ | Playback URI logic (belongs in playback layer) |
| Credential redaction | ✅ | Logging implementation (UnifiedLog handles this) |

**Public Interfaces:**
- `XtreamApiClient` - Core API operations
- `XtreamDiscovery` - Port resolution & capability detection
- `XtreamUrlBuilder` - URL construction
- `XtreamCredentialsStore` - Secure credential persistence

**Response DTOs:**
- `XtreamVodStream` - VOD stream descriptor
- `XtreamLiveStream` - Live channel descriptor
- `XtreamSeriesInfo` - Series metadata
- `XtreamEpisodeInfo` - Episode metadata
- `XtreamCapabilities` - Discovered panel capabilities

**Configuration:**
- `XtreamApiConfig` - API configuration (host, port, credentials, preferences)
- `XtreamTransportConfig` - Centralized transport settings (timeouts, headers, parallelism)
- `XtreamParallelism` - Device-aware concurrency limits

---

### infra/transport-io

**Purpose:** Local file system and Android ContentResolver access.

| Responsibility | Allowed | Forbidden |
|----------------|---------|-----------|
| File system scanning | ✅ | Media library UI (belongs in feature layer) |
| ContentResolver access | ✅ | Playlist parsing (belongs in pipeline) |
| Local file metadata | ✅ | FFmpeg probing (belongs in tools layer) |

**Public Interface:**
- `LocalFileClient` - File system operations

**DTOs:**
- `IoMediaFile` - Local file descriptor

---

## ⚠️ Critical Architecture Patterns

### Telegram:  remoteId-First Architecture

**SSOT:  `chatId` + `messageId` (stable across sessions)**

```kotlin
// ✅ CORRECT: remoteId is the stable identifier
data class TgContent(
    val remoteId: String,        // Stable file ID (use for cache key/persistence)
    val fileId: Int,             // Session-specific TDLib ID (internal, ephemeral)
    val chatId: Long,            // Message location
    val messageId: Long,         // Message location
    val mimeType: String?,
    val sizeBytes: Long?,
)

// ⚠️ fileId may change across: 
// - TDLib cache eviction
// - User logout/login
// - Message content updates
// - App reinstalls

// ✅ Resolution pattern for stale fileIds: 
suspend fun resolveStaleFile(remoteId: String): TgFile?  {
    // 1. Parse remoteId to chatId + messageId
    val (chatId, messageId) = parseRemoteId(remoteId)
    
    // 2. Re-fetch message
    val message = telegramHistoryClient.getMessage(chatId, messageId)
    
    // 3. Extract current fileId
    return message?.content?.file
}
```

**Imaging Integration:**

```kotlin
// TelegramThumbFetcher implementation (in transport layer)
class TelegramThumbFetcherImpl(
    private val fileClient: TelegramFileClient,
) : TelegramThumbFetcher {
    override suspend fun fetchThumbnail(thumbRef: TgThumbnailRef): String?  {
        val remoteId = thumbRef.remoteId
        
        // 1. Resolve remoteId → fileId via getRemoteFile(remoteId)
        val file = fileClient.resolveRemoteId(remoteId) ?: return null
        
        // 2. Check if already downloaded
        if (file.isDownloadingCompleted && file.localPath != null) {
            return file.localPath
        }
        
        // 3. Download if needed
        fileClient.startDownload(file.fileId, priority = 16)
        
        // 4. Wait for completion
        return waitForDownloadCompletion(file. fileId)
    }
}
```

---

### Xtream: Premium Contract Compliance

**User-Agent (Section 4):**

```kotlin
// ✅ CORRECT: Centralized in XtreamTransportConfig
object XtreamTransportConfig {
    const val USER_AGENT = "FishIT-Player/2.x (Android)"
    const val ACCEPT_JSON = "application/json"
    const val ACCEPT_ENCODING = "gzip"  // For API calls ONLY
    
    // ⚠️ Playback requires different headers! 
    const val PLAYBACK_ACCEPT = "*/*"
    const val PLAYBACK_ENCODING = "identity"  // NO compression for streams! 
}

// ❌ FORBIDDEN: Hardcoded headers scattered across codebase
request.header("User-Agent", "FishIT-Player/2.x (Android)")  // WRONG - use constant
```

**Port Discovery & Caching (Section 2/8):**

```kotlin
// ✅ CORRECT: XtreamDiscovery with caching
class XtreamDiscovery(
    private val http: OkHttpClient,
    private val parallelism: XtreamParallelism,
    private val portStore: XtreamPortStore?  = null,  // Optional persistent cache
) {
    suspend fun resolvePort(config: XtreamApiConfig): Int {
        // 1. Check persistent cache
        portStore?.getPort(config.host)?. let { return it }
        
        // 2. Probe common ports (80, 8080, 443, 8443, ...)
        val resolvedPort = probeCommonPorts(config)
        
        // 3. Cache result
        portStore?.savePort(config.host, resolvedPort)
        
        return resolvedPort
    }
}
```

**Rate Limiting & Throttling (Section 6):**

```kotlin
// ✅ CORRECT: Per-host rate limiting
class DefaultXtreamApiClient(
    private val http: OkHttpClient,
    private val parallelism: XtreamParallelism,  // Device-aware limits
) {
    private val rateLimiters = mutableMapOf<String, RateLimiter>()
    
    private suspend fun <T> rateLimited(host: String, block: suspend () -> T): T {
        val limiter = rateLimiters. getOrPut(host) {
            RateLimiter(minIntervalMs = 120)  // 120ms min interval
        }
        return limiter.execute(block)
    }
}
```

---

### Xtream: Alias-Aware VOD Resolution

```kotlin
// ✅ CORRECT: Try vod/movie/movies aliases
suspend fun getVodStreams(categoryId: String? ): List<XtreamVodStream> {
    val aliases = listOf("vod", "movie", "movies")
    
    for (alias in aliases) {
        val result = tryFetchVodStreams(alias, categoryId)
        if (result != null) {
            // Cache successful alias for future calls
            vodKind = alias
            return result
        }
    }
    
    return emptyList()
}
```

---

## 📐 Architecture Position

```
External APIs (TDLib, Xtream, Local Files)
              ↓
    infra/transport-* ← YOU ARE HERE
              ↓
         pipeline/*
              ↓
    core/metadata-normalizer
              ↓
         infra/data-*
              ↓
        core/*-domain
              ↓
         feature/*
```

---

## 🔍 Layer Boundary Enforcement

### Upstream Dependencies (ALLOWED)

```kotlin
import com.fishit.player.core. model.*           // Core types (ImageRef, SourceType, etc.)
import com.fishit.player.infra.logging.*        // UnifiedLog
import kotlinx.coroutines.*                      // Coroutines
import kotlinx.serialization.*                   // JSON serialization
```

### Downstream Consumers

```kotlin
// Pipeline consumes transport DTOs
private val telegramTransport:  TelegramHistoryClient

val messages = telegramTransport.fetchMessages(chatId, limit = 100)
val rawMetadata = messages.map { it.toRawMediaMetadata() }  // Pipeline mapping

// Playback consumes transport for streaming
private val telegramFileClient: TelegramFileClient
private val remoteResolver: TelegramRemoteResolver

val resolved = remoteResolver.resolveMedia(remoteId)
telegramFileClient.startDownload(resolved.mediaFileId, priority = 32)
```

### Forbidden Imports (CI-GUARDED)

```kotlin
// ❌ FORBIDDEN
import com.fishit.player. pipeline.*             // Pipeline
import com.fishit.player.core.metadata.*        // Normalizer
import com.fishit. player.core.persistence.*     // Persistence
import com.fishit.player. feature.*              // UI
import com.fishit.player.playback.*             // Playback domain
```

---

## 🔍 Pre-Change Verification

```bash
# 1. No forbidden imports
grep -rn "import.*pipeline\|import.*core\. metadata\|import.*persistence\|import.*feature\|import.*playback" infra/transport-telegram/ infra/transport-xtream/

# 2. No TdlClient leaks (Telegram)
grep -rn "TdlClient\|TdApi\." infra/transport-telegram/ | grep -v "internal/"

# 3. No OkHttpClient leaks (Xtream)
grep -rn "fun.*OkHttpClient\|val.*OkHttpClient" infra/transport-xtream/ | grep -v "internal\|private"

# 4. No business logic (normalization, classification)
grep -rn "normalizeTitle\|classifyMediaType\|generateGlobalId\|extractSeasonEpisode" infra/transport-telegram/ infra/transport-xtream/

# All should return empty! 
```

---

## ✅ PLATIN Checklist

### Common (All Transport Modules)
- [ ] Only this layer imports external SDKs (TDLib, OkHttp, ContentResolver)
- [ ] Exports clean, documented DTOs (no raw SDK types)
- [ ] No business logic or normalization
- [ ] No persistence (ObjectBox, Room)
- [ ] No pipeline imports
- [ ] No UI imports
- [ ] No playback domain imports
- [ ] Uses UnifiedLog for all logging
- [ ] Credentials redacted in logs (UnifiedLog handles this)
- [ ] Stateless design (no in-memory caches for catalog data)

### Transport-Telegram Specific
- [ ] TdlClient never exposed to upper layers
- [ ] TdlibClientProvider internal use only (v1 legacy pattern)
- [ ] remoteId-first for all file references
- [ ] `TgThumbnailRef` uses remoteId (not fileId)
- [ ] TelegramThumbFetcher implemented in transport layer
- [ ] Typed interfaces (Auth, History, File, Thumb, Resolver)
- [ ] DefaultTelegramClient implements all interfaces
- [ ] Bounded error tracking for failed remoteIds (prevents log spam)

### Transport-Xtream Specific
- [ ] Premium User-Agent in all API requests
- [ ] Port auto-discovery with persistent caching
- [ ] VOD alias resolution (vod/movie/movies)
- [ ] Per-host rate limiting (120ms min interval)
- [ ] Device-aware parallelism via XtreamParallelism
- [ ] Credential encryption via EncryptedSharedPreferences
- [ ] Separate headers for API vs Playback (NO gzip on streams!)
- [ ] XtreamApiClient interface (not monolithic god object)

---

## 📚 Reference Documents (Priority Order)

1. **`/docs/v2/MEDIA_NORMALIZATION_CONTRACT.md`** - Transport → Pipeline contract
2. **`/contracts/TELEGRAM_ID_ARCHITECTURE_CONTRACT.md`** - remoteId-first design
3. **`/.github/tdlibAgent.md`** - TDLib integration SSOT
4. **`/contracts/XTREAM_SCAN_PREMIUM_CONTRACT_V1.md`** - Xtream Premium Contract
5. **`/AGENTS. md`** - Section on Player Layer Isolation (transport boundaries)
6. **`/contracts/GLOSSARY_v2_naming_and_modules.md`** - Transport layer definition
7. **`infra/transport-telegram/README.md`** - Module-specific rules
8. **`infra/transport-xtream/README. md`** - Module-specific rules

---

## 🚨 Common Violations & Solutions

### Violation 1: Leaking TDLib Types

```kotlin
// ❌ WRONG
fun getTelegramFile(fileId: Int): TdApi.File  // Leaking TDLib type! 

// ✅ CORRECT
suspend fun getFile(fileId: Int): TgFile      // Wrapped DTO
```

### Violation 2: Business Logic in Transport

```kotlin
// ❌ WRONG (in transport layer)
fun classifyTelegramMessage(message: TgMessage): MediaType {
    if (message.content?. video != null) return MediaType. MOVIE
    // ... complex classification logic
}

// ✅ CORRECT:  Move to pipeline
// pipeline/telegram/classifier/TelegramMediaClassifier.kt
fun TgMessage.toRawMediaMetadata(): RawMediaMetadata {
    // Classification logic HERE
}
```

### Violation 3: Monolithic Client Interface

```kotlin
// ❌ WRONG
interface TelegramTransportClient {
    suspend fun auth()
    suspend fun getChats()
    suspend fun downloadFile()
    // ... 50 methods
}

// ✅ CORRECT:  Segregated interfaces
interface TelegramAuthClient { suspend fun isAuthorized(): Boolean }
interface TelegramHistoryClient { suspend fun getChats(): List<TgChat> }
interface TelegramFileClient { suspend fun startDownload(fileId: Int) }
```

### Violation 4: Exposing OkHttpClient

```kotlin
// ❌ WRONG
class XtreamApiClientImpl(val http: OkHttpClient) : XtreamApiClient {
    // Exposes OkHttpClient as public property! 
}

// ✅ CORRECT
class DefaultXtreamApiClient(private val http: OkHttpClient) : XtreamApiClient {
    // OkHttpClient is private
}
```

---

**End of PLATIN Instructions for infra/transport-***

**Next Steps:** Review `infra/data-*` instructions for repository implementations. 
---
applyTo: 
  - infra/transport-telegram/**
  - infra/transport-xtream/**
  - infra/transport-io/**
---

# 🏆 PLATIN Instructions: infra/transport-* (Common Rules)

> **PLATIN STANDARD** - External API Integration Layer (Common Rules).
>
> **Purpose:** This document defines the COMMON rules for ALL transport modules.
> Transport is the **ONLY** layer allowed to directly interact with external SDKs and network libraries
> **for business/source data access**. 
>
> **Exception:** `core/ui-imaging` (Coil) is explicitly allowed to use OkHttp for image fetching - 
> this is NOT a transport violation as imaging is a separate concern from business data access.
>
> All upper layers consume clean, typed interfaces with source-agnostic DTOs.
>
> **Binding Contracts:**
> - `contracts/GLOSSARY_v2_naming_and_modules.md` (Section 1.4 - Infrastructure Terms)
> - `contracts/LOGGING_CONTRACT_V2.md` (Logging rules)
>
> **Source-Specific Instructions:**
> - Telegram: `.github/instructions/infra-transport-telegram.instructions.md`
> - Xtream: `.github/instructions/infra-transport-xtream.instructions.md`

---

## 🔴 ABSOLUTE HARD RULES (ALL TRANSPORT MODULES)

### 1. This Layer OWNS External Dependencies

```kotlin
// ✅ ONLY ALLOWED in transport layer
// Transport-Telegram:
import org.drinkless.td.TdApi.*               // TDLib SDK
import dev.g000sha256.tdl.*                    // TDLib coroutines wrapper

// Transport-Xtream:
import okhttp3.*                               // HTTP client
import retrofit2.*                             // REST client (if used)

// Transport-IO:
import android.content.ContentResolver         // Android media
import java.io.File                            // File system

// All other modules use abstracted interfaces from this layer
// Pipeline, Playback, Feature → MUST NOT import these SDKs!
```

**Per Glossary Section 1.4:**
> Transport: Network/file access layer. Lives in `infra/transport-*`. 
> Exposes typed interfaces, hides implementation details (TDLib, HTTP clients).

---

### 2. Export Clean DTOs ONLY - NEVER Leak SDK Types

```kotlin
// ✅ CORRECT: Clean, source-agnostic DTOs
// Transport-Telegram:
data class TgMessage(val chatId: Long, val messageId: Long, ...)
data class TgChat(val id: Long, val title: String, ...)
data class TgContent(val remoteId: String, val mimeType: String?, ...)
data class TgFile(val id: Int, val localPath: String?, ...)

// Transport-Xtream:
data class XtreamVodStream(val streamId: Int, val name: String, ...)
data class XtreamLiveStream(val streamId: Int, val name: String, ...)
data class XtreamSeriesInfo(val seriesId: Int, val name: String, ...)

// ❌ FORBIDDEN: Leaking raw SDK types
fun getUpdates(): List<TdApi.Update>           // WRONG - wrap it!
fun getTelegramMessages(): List<TdApi.Message> // WRONG - use TgMessage!
fun getClient(): TdlClient                      // WRONG - never expose client!
fun getOkHttpClient(): OkHttpClient             // WRONG - internal only!
```

---

### 3. Typed Interfaces - NOT Monolithic Clients

```kotlin
// ✅ CORRECT: Segregated interfaces (Interface Segregation Principle)
// Telegram:
interface TelegramAuthClient { ... }           // Auth operations only
interface TelegramHistoryClient { ... }        // Chat/message operations only
interface TelegramFileClient { ... }           // File download operations only
interface TelegramThumbFetcher { ... }         // Thumbnail loading only
interface TelegramRemoteResolver { ... }       // remoteId resolution only

// Xtream:
interface XtreamApiClient { ... }              // Core API operations
interface XtreamDiscovery { ... }              // Port resolution
interface XtreamUrlBuilder { ... }             // URL construction

// ❌ FORBIDDEN: God objects
interface TelegramTransportClient {            // WRONG - too broad
    suspend fun auth()
    suspend fun getChats()
    suspend fun downloadFile()
    suspend fun resolveRemote()
    // ... 50 more methods
}
```

**Per Glossary Section 1.6 (Telegram Transport Interfaces):**
> Typed interfaces: `TelegramAuthClient`, `TelegramHistoryClient`, `TelegramFileClient`, `TelegramThumbFetcher`

---

### 4. No Business Logic

```kotlin
// ❌ FORBIDDEN in Transport
fun normalizeTitle(title: String): String              // → core/metadata-normalizer
fun classifyMediaType(item: TgMessage): MediaType      // → pipeline
fun generateGlobalId(...): String                      // → core/metadata-normalizer
fun extractSeasonEpisode(title: String): Pair<Int?, Int?>?   // → core/metadata-normalizer
suspend fun searchTmdb(title: String): TmdbRef?        // → core/metadata-normalizer

// ✅ CORRECT: Pure transport/mapping operations
suspend fun fetchMessage(chatId: Long, messageId: Long): TgMessage
suspend fun downloadFile(fileId: Int): ByteArray
suspend fun getLiveStreams(): List<XtreamLiveStream>
```

---

### 5. No Persistence or Caching Logic

```kotlin
// ❌ FORBIDDEN
import io.objectbox.*
import com.fishit.player.core.persistence.*
import androidx.room.*

class TelegramMessageCache { ... }             // WRONG - data layer handles this
suspend fun saveToDatabase(message: TgMessage) // WRONG - transport is stateless

// ✅ CORRECT: Stateless transport
// Data layer (infra/data-*) handles persistence
// Transport only provides fetching primitives
```

---

### 6. UnifiedLog for ALL Logging (Per LOGGING_CONTRACT_V2.md)

```kotlin
// ✅ CORRECT: Lambda-based logging (MANDATORY in transport - hot path)
private const val TAG = "TelegramTransport"

UnifiedLog.d(TAG) { "Fetching messages: chatId=$chatId, limit=$limit" }
UnifiedLog.i(TAG) { "Download complete: fileId=$fileId, size=$sizeBytes" }
UnifiedLog.e(TAG, exception) { "Failed to resolve remoteId: $remoteId" }

// ❌ FORBIDDEN: Secrets in logs
UnifiedLog.d(TAG) { "Auth with token=$authToken" }  // WRONG - security issue!
UnifiedLog.d(TAG) { "Password: $password" }          // WRONG - CRITICAL!

// ✅ CORRECT: Redacted logging
UnifiedLog.d(TAG) { "Auth: hasToken=${authToken != null}" }
UnifiedLog.d(TAG) { "Credentials: configured=${credentials.isValid}" }
```

---

## 📋 Module Overview

| Module | Purpose | Binding Contract | Specific Instructions |
|--------|---------|------------------|----------------------|
| `transport-telegram` | TDLib SDK wrapper | `TELEGRAM_ID_ARCHITECTURE_CONTRACT.md` | `infra-transport-telegram.instructions.md` |
| `transport-xtream` | Xtream Codes API client | `XTREAM_SCAN_PREMIUM_CONTRACT_V1.md` | `infra-transport-xtream.instructions.md` |
| `transport-io` | Local file system access | *(none)* | *(basic rules only)* |

---

## 📋 Common Responsibilities (ALL Transport Modules)

| Responsibility | Allowed | Forbidden |
|----------------|---------|-----------|
| External SDK integration | ✅ | Exposing SDK types to upper layers |
| Clean DTO mapping | ✅ | Leaking raw SDK objects |
| Typed interface segregation | ✅ | Monolithic client interfaces |
| Error wrapping | ✅ | Business logic (normalization, classification) |
| Credential handling | ✅ | Logging secrets/passwords |
| Request/response logging | ✅ | Payload dumps in logs |
| Rate limiting primitives | ✅ | Persistence/caching logic |

---

## 📐 Architecture Position

```
External APIs (TDLib, Xtream HTTP, Local Files)
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
import com.fishit.player.core.model.*           // Core types (ImageRef, SourceType, etc.)
import com.fishit.player.infra.logging.*        // UnifiedLog
import kotlinx.coroutines.*                      // Coroutines
import kotlinx.serialization.*                   // JSON serialization
```

### Forbidden Imports (CI-GUARDED)

```kotlin
// ❌ FORBIDDEN in ALL transport modules
import com.fishit.player.pipeline.*             // Pipeline
import com.fishit.player.core.metadata.*        // Normalizer
import com.fishit.player.core.persistence.*     // Persistence
import com.fishit.player.feature.*              // UI
import com.fishit.player.playback.*             // Playback domain
```

---

## 🔍 Pre-Change Verification (ALL Transport Modules)

```bash
# 1. No forbidden imports
grep -rn "import.*pipeline\|import.*core\.metadata\|import.*persistence\|import.*feature\|import.*playback" infra/transport-*/

# 2. No business logic (normalization, classification)
grep -rn "normalizeTitle\|classifyMediaType\|generateGlobalId\|extractSeasonEpisode" infra/transport-*/

# 3. No persistence imports
grep -rn "import.*objectbox\|import.*room\|BoxStore" infra/transport-*/

# 4. No secrets in logs (manual review required)
grep -rn "password\|token\|apiKey\|secret" infra/transport-*/ | grep -i "unifiedlog\|log\."

# All should return empty (except #4 which requires manual review)!
```

---

## ✅ PLATIN Checklist (Common - ALL Transport Modules)

- [ ] Only this layer imports external SDKs (TDLib, OkHttp, ContentResolver)
- [ ] Exports clean, documented DTOs (no raw SDK types)
- [ ] Uses typed, segregated interfaces (not god objects)
- [ ] No business logic or normalization
- [ ] No persistence (ObjectBox, Room)
- [ ] No pipeline imports
- [ ] No UI imports
- [ ] No playback domain imports
- [ ] Uses UnifiedLog for all logging (lambda-based in hot paths)
- [ ] Credentials redacted in logs
- [ ] Stateless design (no in-memory caches for catalog data)

**For source-specific rules, see:**
- `infra-transport-telegram.instructions.md` - remoteId-first, TelegramThumbFetcher, etc.
- `infra-transport-xtream.instructions.md` - Premium Contract, rate limiting, etc.

---

## 📚 Reference Documents (Priority Order)

1. **`/contracts/GLOSSARY_v2_naming_and_modules.md`** - Section 1.4 (Infrastructure Terms)
2. **`/contracts/LOGGING_CONTRACT_V2.md`** - Logging rules (v1.1)
3. **`/contracts/TELEGRAM_ID_ARCHITECTURE_CONTRACT.md`** - remoteId-first design
4. **`/contracts/XTREAM_SCAN_PREMIUM_CONTRACT_V1.md`** - Xtream Premium Contract
5. **`/AGENTS.md`** - Layer boundary enforcement

---

## 🚨 Common Violations & Solutions

### Violation 1: Leaking SDK Types

```kotlin
// ❌ WRONG
fun getTelegramFile(fileId: Int): TdApi.File  // Leaking TDLib type!

// ✅ CORRECT
suspend fun getFile(fileId: Int): TgFile      // Wrapped DTO
```

### Violation 2: Business Logic in Transport

```kotlin
// ❌ WRONG (in transport layer)
fun normalizeTitle(title: String): String {
    return title.replace("[1080p]", "").trim()
}

// ✅ CORRECT
// No normalization in transport! Pass raw data to pipeline
suspend fun fetchMessage(...): TgMessage  // Raw data
```

### Violation 3: Monolithic Client

```kotlin
// ❌ WRONG
interface TelegramClient {
    fun auth()
    fun getChats()
    fun downloadFile()
    fun fetchThumbnail()
    // ... 30 more methods
}

// ✅ CORRECT: Segregated interfaces
interface TelegramAuthClient { fun auth() }
interface TelegramHistoryClient { fun getChats() }
interface TelegramFileClient { fun downloadFile() }
interface TelegramThumbFetcher { fun fetchThumbnail() }
```

### Violation 4: Secrets in Logs

```kotlin
// ❌ WRONG
UnifiedLog.d(TAG) { "Login with password=$password, token=$authToken" }

// ✅ CORRECT
UnifiedLog.d(TAG) { "Login: hasPassword=true, hasToken=${authToken != null}" }
```

---

**End of PLATIN Instructions for infra/transport-* (Common Rules)**

**Next Steps:** Create source-specific instructions:
- `.github/instructions/infra-transport-telegram.instructions.md`
- `.github/instructions/infra-transport-xtream.instructions.md`

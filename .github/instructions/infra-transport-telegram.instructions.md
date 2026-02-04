---
applyTo: 
  - infra/transport-telegram/**
---

# 🏆 PLATIN Instructions: infra/transport-telegram

> **PLATIN STANDARD** - TDLib Transport Layer (Absolute Perfection).
>
> **Purpose:** Provides abstracted, typed access to Telegram via TDLib SDK.
> This module OWNS the TDLib client lifecycle and exports clean DTOs.
> Upper layers (pipeline, playback) consume typed interfaces - NEVER raw TDLib types.
>
> **Binding Contracts (Priority Order):**
> 1. `contracts/TELEGRAM_ID_ARCHITECTURE_CONTRACT.md` (v1.0) - remoteId-first design
> 2. `contracts/GLOSSARY_v2_naming_and_modules.md` (Section 1.6) - Transport interfaces
> 3. `contracts/LOGGING_CONTRACT_V2.md` (v1.1) - Logging rules
> 4. `contracts/TELEGRAM_*.md (binding contracts)` - TDLib integration SSOT (legacy reference)
>
> **Core Principle: remoteId-First Architecture**
> ```
> PERSISTENCE: chatId + messageId + remoteId (stable)
>                        ↓
> RUNTIME: getRemoteFile(remoteId) → TgFile { id: Int }
>                        ↓
>          downloadFile(fileId) → localPath
> ```

---

## 🔴 ABSOLUTE HARD RULES

### 1. TDLib Client Isolation (CRITICAL - READ FIRST)

```kotlin
// ✅ INTERNAL ONLY - Never exposed
import org.drinkless.td.TdApi.*               // TDLib SDK
import dev.g000sha256.tdl.TdlClient           // TDLib coroutines wrapper

// DefaultTelegramClient owns TdlClient - SINGLE INSTANCE per process
internal class DefaultTelegramClient @Inject constructor(
    private val tdlClient: TdlClient,  // INTERNAL - never exposed!
) : TelegramAuthClient, 
    TelegramHistoryClient, 
    TelegramFileClient,
    TelegramRemoteResolver {
    // Implements all typed interfaces
}

// ❌ FORBIDDEN: Exposing TdlClient or TdApi types
interface TelegramTransport {
    fun getClient(): TdlClient                    // WRONG - never expose!
    suspend fun getUpdates(): List<TdApi.Update>  // WRONG - wrap in DTOs!
    suspend fun getMessage(): TdApi.Message       // WRONG - use TgMessage!
}
```

**Per AGENTS.md (CRITICAL):**
> `TdlibClientProvider` is a **v1 legacy pattern** – must NOT be reintroduced in v2.
> Use typed interfaces (`TelegramAuthClient`, etc.) instead.

---

### 2. Typed Interface Segregation (Per Glossary Section 1.6)

```kotlin
// ✅ CORRECT: Segregated interfaces (Interface Segregation Principle)

/**
 * Authentication operations.
 * Handles login, logout, auth state observation.
 */
interface TelegramAuthClient {
    /** Observe authentication state changes. */
    fun observeAuthState(): Flow<TelegramAuthState>
    
    /** Check if currently authenticated. */
    suspend fun isAuthorized(): Boolean
    
    /** Start phone number authentication. */
    suspend fun startPhoneAuth(phoneNumber: String): AuthResult
    
    /** Submit authentication code. */
    suspend fun submitCode(code: String): AuthResult
    
    /** Submit 2FA password. */
    suspend fun submitPassword(password: String): AuthResult
    
    /** Logout and clear session. */
    suspend fun logout()
}

/**
 * Chat and message browsing operations.
 * Returns TgMessage/TgChat wrapper types.
 */
interface TelegramHistoryClient {
    /** Get list of chats. */
    suspend fun getChats(limit: Int = 100): List<TgChat>
    
    /** Get single chat by ID. */
    suspend fun getChat(chatId: Long): TgChat?
    
    /** Get message by ID. */
    suspend fun getMessage(chatId: Long, messageId: Long): TgMessage?
    
    /** Fetch message history (paginated). */
    suspend fun getMessageHistory(
        chatId: Long,
        fromMessageId: Long = 0,
        limit: Int = 100,
    ): List<TgMessage>
    
    /** Observe new messages in chat. */
    fun observeNewMessages(chatId: Long): Flow<TgMessage>
}

/**
 * File download operations.
 * Handles file downloads and progress tracking.
 */
interface TelegramFileClient {
    /** Start file download with priority. */
    suspend fun startDownload(fileId: Int, priority: Int = 1): TgFile
    
    /** Cancel ongoing download. */
    suspend fun cancelDownload(fileId: Int)
    
    /** Get current file state. */
    suspend fun getFile(fileId: Int): TgFile?
    
    /** Observe download progress. */
    fun observeDownloadProgress(fileId: Int): Flow<TgDownloadProgress>
}

/**
 * Remote ID resolution.
 * Resolves stable remoteId to session-local fileId.
 */
interface TelegramRemoteResolver {
    /** 
     * Resolve remoteId to TgFile.
     * This is the PRIMARY resolution method per TELEGRAM_ID_ARCHITECTURE_CONTRACT.
     */
    suspend fun resolveRemoteId(remoteId: String): TgFile?
    
    /**
     * Resolve media from message coordinates.
     * Fallback when remoteId is stale.
     */
    suspend fun resolveFromMessage(chatId: Long, messageId: Long): TgFile?
}

/**
 * Thumbnail fetching for Coil integration.
 * Interface defined here, implementation in transport.
 */
interface TelegramThumbFetcher {
    /** 
     * Fetch thumbnail by remoteId.
     * Returns local file path when downloaded.
     */
    suspend fun fetchThumbnail(remoteId: String): String?
    
    /** Factory for Coil integration. */
    interface Factory {
        fun create(): TelegramThumbFetcher
    }
}
```

---

### 3. remoteId-First Architecture (Per TELEGRAM_ID_ARCHITECTURE_CONTRACT)

```kotlin
// ═══════════════════════════════════════════════════════════════════════════
// CRITICAL: ID STABILITY RULES
// ═══════════════════════════════════════════════════════════════════════════

// Per Contract Section 2:
// | ID Type    | Stability          | Persist? | Purpose                    |
// |------------|-------------------|----------|----------------------------|
// | fileId     | Session-local     | ❌ NO    | TDLib download operations  |
// | remoteId   | Cross-session     | ✅ YES   | Stable file reference      |
// | uniqueId   | Cross-session     | ❌ NO    | No API to resolve back     |
// | chatId     | Stable            | ✅ YES   | Chat identification        |
// | messageId  | Stable            | ✅ YES   | Message identification     |

// ✅ CORRECT: DTOs use remoteId only
data class TgContent(
    val remoteId: String,           // ✅ Stable - PERSIST THIS
    val chatId: Long,               // ✅ Stable - PERSIST THIS
    val messageId: Long,            // ✅ Stable - PERSIST THIS
    val mimeType: String?,
    val sizeBytes: Long?,
    val durationSeconds: Int?,
    val width: Int?,
    val height: Int?,
    // ⚠️ NO fileId here - it's session-local and volatile!
)

data class TgThumbnailRef(
    val remoteId: String,           // ✅ Only stable ID
    val width: Int,
    val height: Int,
    val format: String = "jpeg",
    // ⚠️ NO fileId here!
)

data class TgFile(
    val id: Int,                    // ⚠️ Session-local - DO NOT persist!
    val remoteId: String?,          // ✅ Stable
    val localPath: String?,         // Path when downloaded
    val isDownloadingCompleted: Boolean,
    val downloadedSize: Long,
    val expectedSize: Long,
)

// ❌ FORBIDDEN: Persisting fileId
data class TgContentWRONG(
    val fileId: Int,                // WRONG - volatile!
    val uniqueId: String,           // WRONG - no resolution API!
    // ...
)
```

---

### 4. Runtime Resolution Pattern

```kotlin
// Per Contract Section 3.2: All file operations MUST resolve fileId at runtime

/**
 * Resolution flow:
 * 1. UI requests image/video for item
 * 2. Lookup remoteId from ObjectBox
 * 3. Call getRemoteFile(remoteId) → TgFile { id: Int }
 * 4. Use fileId for downloadFile() or startDownload()
 * 5. TDLib downloads to its cache → localPath
 * 6. Return localPath to caller
 */
class TelegramRemoteResolverImpl @Inject constructor(
    private val tdlClient: TdlClient,
) : TelegramRemoteResolver {
    
    override suspend fun resolveRemoteId(remoteId: String): TgFile? {
        return try {
            val result = tdlClient.execute(TdApi.GetRemoteFile(remoteId, null))
            result?.toTgFile()
        } catch (e: Exception) {
            UnifiedLog.w(TAG) { "Failed to resolve remoteId: ${remoteId.take(20)}..." }
            null
        }
    }
    
    override suspend fun resolveFromMessage(chatId: Long, messageId: Long): TgFile? {
        return try {
            val message = tdlClient.execute(TdApi.GetMessage(chatId, messageId))
            message?.extractVideoFile()?.toTgFile()
        } catch (e: Exception) {
            UnifiedLog.w(TAG) { "Failed to resolve from message: chatId=$chatId, messageId=$messageId" }
            null
        }
    }
    
    companion object {
        private const val TAG = "TelegramResolver"
    }
}
```

---

### 5. TelegramThumbFetcher Implementation

```kotlin
// Per Contract Section 3.5: Fetcher resolves fileId internally

class TelegramThumbFetcherImpl @Inject constructor(
    private val remoteResolver: TelegramRemoteResolver,
    private val fileClient: TelegramFileClient,
) : TelegramThumbFetcher {
    
    private val failedRemoteIds = ConcurrentHashMap<String, Long>()
    
    override suspend fun fetchThumbnail(remoteId: String): String? {
        // Bounded error tracking - skip known-bad remoteIds for 5 minutes
        val lastFailure = failedRemoteIds[remoteId]
        if (lastFailure != null && System.currentTimeMillis() - lastFailure < FAILURE_COOLDOWN_MS) {
            return null
        }
        
        return try {
            // 1. Resolve remoteId → fileId
            val file = remoteResolver.resolveRemoteId(remoteId)
                ?: return null.also { recordFailure(remoteId) }
            
            // 2. Check if already downloaded
            if (file.isDownloadingCompleted && file.localPath != null) {
                return file.localPath
            }
            
            // 3. Start download with high priority (thumbnails are UI-blocking)
            val downloadedFile = fileClient.startDownload(file.id, priority = 16)
            
            // 4. Wait for completion or return path
            if (downloadedFile.isDownloadingCompleted) {
                failedRemoteIds.remove(remoteId)
                return downloadedFile.localPath
            }
            
            // 5. If not immediately complete, observe progress
            return waitForDownload(file.id, timeoutMs = 10_000)
        } catch (e: Exception) {
            UnifiedLog.d(TAG) { "Thumbnail fetch failed: remoteId=${remoteId.take(20)}..." }
            recordFailure(remoteId)
            null
        }
    }
    
    private fun recordFailure(remoteId: String) {
        failedRemoteIds[remoteId] = System.currentTimeMillis()
        // Prevent unbounded growth
        if (failedRemoteIds.size > MAX_FAILURE_ENTRIES) {
            val oldest = failedRemoteIds.entries.minByOrNull { it.value }
            oldest?.let { failedRemoteIds.remove(it.key) }
        }
    }
    
    companion object {
        private const val TAG = "TelegramThumbFetcher"
        private const val FAILURE_COOLDOWN_MS = 5 * 60 * 1000L  // 5 minutes
        private const val MAX_FAILURE_ENTRIES = 1000
    }
}
```

---

### 6. Wrapper DTOs (Complete Set)

```kotlin
// ═══════════════════════════════════════════════════════════════════════════
// TRANSPORT LAYER DTOS - Clean wrappers for TDLib types
// ═══════════════════════════════════════════════════════════════════════════

/** Transport-level chat representation. */
data class TgChat(
    val id: Long,
    val title: String,
    val type: TgChatType,
    val photoRemoteId: String?,
    val memberCount: Int?,
    val lastMessageDate: Long?,
)

enum class TgChatType {
    PRIVATE, GROUP, SUPERGROUP, CHANNEL, SECRET, UNKNOWN
}

/** Transport-level message representation. */
data class TgMessage(
    val id: Long,
    val chatId: Long,
    val date: Long,                          // Unix timestamp in seconds
    val senderId: Long?,
    val content: TgContent?,
    val caption: String?,
    val replyToMessageId: Long?,
    val mediaAlbumId: Long?,                 // For bundle detection
)

/** Media content descriptor. */
data class TgContent(
    val type: TgContentType,
    val remoteId: String,                    // ✅ Stable - use for persistence
    val chatId: Long,                        // ✅ Stable
    val messageId: Long,                     // ✅ Stable
    val mimeType: String?,
    val sizeBytes: Long?,
    val durationSeconds: Int?,
    val width: Int?,
    val height: Int?,
    val thumbnail: TgThumbnailRef?,
    val minithumbnail: ByteArray?,           // Inline blur placeholder
)

enum class TgContentType {
    VIDEO, PHOTO, DOCUMENT, AUDIO, VOICE_NOTE, VIDEO_NOTE, ANIMATION, UNKNOWN
}

/** Thumbnail reference (remoteId-only). */
data class TgThumbnailRef(
    val remoteId: String,                    // ✅ Only stable ID
    val width: Int,
    val height: Int,
    val format: String = "jpeg",
)

/** File state descriptor. */
data class TgFile(
    val id: Int,                             // ⚠️ Session-local - do NOT persist
    val remoteId: String?,                   // ✅ Stable
    val uniqueId: String?,                   // ⚠️ Not useful - no resolution API
    val localPath: String?,
    val isDownloadingCompleted: Boolean,
    val isDownloadingActive: Boolean,
    val downloadedSize: Long,
    val expectedSize: Long,
)

/** Download progress update. */
data class TgDownloadProgress(
    val fileId: Int,
    val downloadedSize: Long,
    val expectedSize: Long,
    val isComplete: Boolean,
)

/** Authentication state. */
sealed class TelegramAuthState {
    object WaitingForPhoneNumber : TelegramAuthState()
    object WaitingForCode : TelegramAuthState()
    object WaitingForPassword : TelegramAuthState()
    object Ready : TelegramAuthState()
    object LoggedOut : TelegramAuthState()
    data class Error(val message: String) : TelegramAuthState()
}

/** Authentication result. */
sealed class AuthResult {
    object Success : AuthResult()
    object NeedCode : AuthResult()
    object NeedPassword : AuthResult()
    data class Error(val message: String) : AuthResult()
}
```

---

### 7. No Business Logic (Strict Boundary)

```kotlin
// ❌ FORBIDDEN in Transport Layer
fun normalizeTitle(title: String): String              // → core/metadata-normalizer
fun classifyMediaType(message: TgMessage): MediaType   // → pipeline
fun extractSeasonEpisode(title: String): Pair<Int, Int>? // → pipeline
fun generateGlobalId(...): String                      // → core/metadata-normalizer
suspend fun searchTmdb(title: String): TmdbRef?        // → core/metadata-normalizer
fun detectBundle(messages: List<TgMessage>): Bundle    // → pipeline

// ❌ FORBIDDEN: Structured bundle processing
fun groupIntoBundle(messages: List<TgMessage>): TelegramBundle  // → pipeline!
// Per TELEGRAM_STRUCTURED_BUNDLES_CONTRACT R11:
// "Structured Bundles are processed entirely in pipeline/telegram"

// ✅ CORRECT: Pure transport operations
suspend fun getMessage(chatId: Long, messageId: Long): TgMessage?
suspend fun getMessageHistory(chatId: Long, limit: Int): List<TgMessage>
suspend fun startDownload(fileId: Int, priority: Int): TgFile
suspend fun resolveRemoteId(remoteId: String): TgFile?
```

---

### 8. No Persistence (Stateless Transport)

```kotlin
// ❌ FORBIDDEN
import io.objectbox.*
import com.fishit.player.core.persistence.*
import androidx.room.*

class TelegramMessageCache { ... }              // WRONG - data layer handles this
suspend fun saveMessage(message: TgMessage)     // WRONG - transport is stateless
suspend fun getCachedChats(): List<TgChat>      // WRONG - use data layer

// ✅ CORRECT: Stateless transport
// infra/data-telegram handles persistence via TelegramContentRepository
// Transport only provides fetching primitives
```

---

## 📋 Module Structure

```
infra/transport-telegram/
├── src/main/java/com/fishit/player/infra/transport/telegram/
│   ├── api/                           # Public interfaces
│   │   ├── TelegramAuthClient.kt
│   │   ├── TelegramHistoryClient.kt
│   │   ├── TelegramFileClient.kt
│   │   ├── TelegramRemoteResolver.kt
│   │   └── TelegramThumbFetcher.kt
│   ├── dto/                           # Wrapper DTOs
│   │   ├── TgMessage.kt
│   │   ├── TgChat.kt
│   │   ├── TgContent.kt
│   │   ├── TgFile.kt
│   │   ├── TgThumbnailRef.kt
│   │   └── TelegramAuthState.kt
│   ├── internal/                      # Internal implementation
│   │   ├── DefaultTelegramClient.kt   # Implements all interfaces
│   │   ├── TdlibClientFactory.kt      # TdlClient lifecycle
│   │   ├── TdApiMappers.kt            # TdApi → DTO mappers
│   │   └── TelegramThumbFetcherImpl.kt
│   └── di/
│       └── TelegramTransportModule.kt  # Hilt bindings
└── build.gradle.kts
```

---

## 📐 Architecture Position

```
TDLib SDK (org.drinkless.td.TdApi.*)
              ↓
    infra/transport-telegram ← YOU ARE HERE
              ↓
         pipeline/telegram (consumes TgMessage, TgContent)
              ↓
         playback/telegram (consumes TelegramFileClient, TelegramRemoteResolver)
              ↓
    core/ui-imaging (consumes TelegramThumbFetcher)
```

**Dependency Rules:**
- `transport-telegram` MAY import: TDLib SDK, `core/model`, `infra/logging`
- `transport-telegram` MUST NOT import: Pipeline, Playback, Feature, Persistence

---

## 🔍 Pre-Change Verification

```bash
# 1. No TdApi types in public interfaces (only in internal/)
grep -rn "TdApi\." infra/transport-telegram/src/main/java/ | grep -v "/internal/"

# 2. No TdlClient exposure
grep -rn "TdlClient" infra/transport-telegram/src/main/java/ | grep -v "internal\|private"

# 3. No fileId in persisted DTOs (only in TgFile which is runtime-only)
grep -rn "val fileId" infra/transport-telegram/src/main/java/*/dto/ | grep -v "TgFile"

# 4. No business logic (normalization, classification, bundle detection)
grep -rn "normalizeTitle\|classifyMediaType\|extractSeasonEpisode\|generateGlobalId\|detectBundle" infra/transport-telegram/

# 5. No persistence imports
grep -rn "objectbox\|room\|BoxStore" infra/transport-telegram/

# 6. No pipeline imports
grep -rn "import.*pipeline" infra/transport-telegram/

# All should return empty!
```

---

## ✅ PLATIN Checklist

### Core Architecture
- [ ] Single `TdlClient` instance per process (in `DefaultTelegramClient`)
- [ ] `TdlClient` and `TdApi.*` types NEVER exposed outside `internal/`
- [ ] `TdlibClientProvider` pattern NOT reintroduced (v1 legacy)
- [ ] All public APIs use typed interfaces (Auth, History, File, Resolver, ThumbFetcher)

### remoteId-First (Per TELEGRAM_ID_ARCHITECTURE_CONTRACT)
- [ ] DTOs use `remoteId` for stable file references
- [ ] `fileId` only in `TgFile` (runtime-only, not persisted)
- [ ] `uniqueId` not used (no resolution API)
- [ ] `chatId` + `messageId` persisted for message reload
- [ ] `resolveRemoteId()` is PRIMARY resolution method
- [ ] `TgThumbnailRef` uses remoteId only (no fileId)

### Interface Segregation (Per Glossary Section 1.6)
- [ ] `TelegramAuthClient` - Auth operations only
- [ ] `TelegramHistoryClient` - Chat/message browsing only
- [ ] `TelegramFileClient` - File download only
- [ ] `TelegramRemoteResolver` - remoteId resolution only
- [ ] `TelegramThumbFetcher` - Thumbnail loading only
- [ ] `DefaultTelegramClient` implements all interfaces

### Layer Boundaries
- [ ] No business logic (normalization, classification, bundle detection)
- [ ] No persistence imports (ObjectBox, Room)
- [ ] No pipeline imports
- [ ] No playback imports
- [ ] No feature imports
- [ ] Stateless design (no catalog caching)

### Logging (Per LOGGING_CONTRACT_V2.md)
- [ ] Uses `UnifiedLog` exclusively (lambda-based in hot paths)
- [ ] No secrets logged (auth tokens, phone numbers redacted)
- [ ] Bounded error tracking (prevent log spam for failed remoteIds)
- [ ] Meaningful TAGs (max 23 chars)

---

## 🚨 Error Handling & Edge Cases (HS-04)

### Error Handling Semantics

**Unified Error Model:** All transport interfaces SHOULD use nullable return types or sealed result types for recoverable errors. Exceptions are reserved for unrecoverable failures.

**Preferred Patterns:**

```kotlin
// ✅ PATTERN 1: Nullable return (simple cases)
interface TelegramHistoryClient {
    suspend fun getMessage(chatId: Long, messageId: Long): TgMessage?  // null = not found/error
}

// ✅ PATTERN 2: Sealed Result type (when error details needed)
sealed class TelegramResult<out T> {
    data class Success<T>(val value: T) : TelegramResult<T>()
    data class Error(val reason: TelegramError) : TelegramResult<Nothing>()
}

enum class TelegramError {
    NOT_AUTHORIZED,
    NETWORK_TIMEOUT,
    NOT_FOUND,
    RATE_LIMITED,
    DATABASE_CORRUPTED,
    UNKNOWN
}

interface TelegramAuthClient {
    suspend fun startPhoneAuth(phoneNumber: String): TelegramResult<Unit>
}

// ✅ PATTERN 3: Exceptions for unrecoverable failures
class TelegramAuthRequiredException(message: String) : Exception(message)
class TelegramDatabaseCorruptedException(message: String, cause: Throwable?) : Exception(message, cause)

// ❌ WRONG: Throwing exceptions for recoverable errors
suspend fun getMessage(...): TgMessage {
    throw TelegramNotFoundException()  // WRONG - use nullable return
}

// ❌ WRONG: Returning null when error details are needed
suspend fun authenticate(...): Boolean?  // WRONG - use Result type to indicate reason
```

**Guidelines:**
- **Nullable returns:** When only success/failure matters (file fetching, message retrieval)
- **Result types:** When error reason affects caller behavior (auth flow, rate limiting)
- **Exceptions:** Only for programmer errors or unrecoverable failures (database corruption, auth required for critical operation)
- **Logging:** Always log errors internally before returning null/Error

### Common Error Scenarios

| Error | Cause | Handling |
|-------|-------|----------|
| **TDLib not initialized** | Client lifecycle issue | Return null, log warning, ensure init in Application.onCreate() |
| **Auth required** | User not logged in | Return null/throw AuthRequiredException, check TelegramAuthClient.isAuthorized() |
| **Network timeout** | Poor connectivity | Retry with exponential backoff, don't block UI |
| **File not found** | Stale fileId or deleted message | Try resolveFromMessage() fallback, use remoteId-first pattern |
| **Rate limit** | Too many requests | Implement request throttling, respect Telegram limits |

### Edge Case Handling Examples

**1. Stale remoteId after session restart:**
```kotlin
// Try remoteId first, fallback to message coordinates
val file = remoteResolver.resolveRemoteId(remoteId)
    ?: remoteResolver.resolveFromMessage(chatId, messageId)
```

**2. TDLib database corruption (HS-05 - Destructive Actions):**

⚠️ **CRITICAL:** Database clearing is a DESTRUCTIVE operation and must ONLY be triggered by explicit user action in Settings, NEVER automatically.

```kotlin
// ❌ WRONG: Automatic database clearing (data loss risk!)
try {
    tdlClient.execute(TdApi.SetDatabaseEncryptionKey())
} catch (e: TdlException) {
    if (e.message?.contains("database") == true) {
        clearTdlibDatabase()  // WRONG - automatic data loss!
        reinitialize()
    }
}

// ✅ CORRECT: Error reporting for user decision
try {
    tdlClient.execute(TdApi.SetDatabaseEncryptionKey())
} catch (e: TdlException) {
    if (e.message?.contains("database") == true) {
        // Report error, let user decide via Settings
        return TelegramResult.Error(TelegramError.DATABASE_CORRUPTED)
    }
}

// ✅ CORRECT: Manual clear via Settings only
class SettingsViewModel {
    fun clearTelegramData() {
        // Require explicit confirmation dialog
        showConfirmDialog(
            title = "Clear Telegram Data?",
            message = "This will delete all local Telegram data. You will need to login again.",
            onConfirm = {
                telegramDatabaseManager.clearDatabase()
            }
        )
    }
}
```

**Destructive Action Guidelines:**
- **NEVER** automatically clear user data (database, cache, credentials)
- **ALWAYS** require explicit user confirmation via Settings UI
- **ALWAYS** show clear warning about data loss consequences
- **PREFER** error reporting over automatic recovery for corruption

**3. Auth state transitions:**
```kotlin
// Always check current auth state before operations
if (!authClient.isAuthorized()) {
    throw AuthRequiredException("User must log in first")
}
```

**4. Thumbnail fetch timeout:**
```kotlin
// Use timeout and bounded retries in TelegramThumbFetcher
withTimeout(10_000) {
    fileClient.startDownload(fileId, priority = 16)
}
```

**5. Graceful exception wrapping:**
```kotlin
// ✅ CORRECT: Wrap TDLib exceptions
suspend fun resolveRemoteId(remoteId: String): TgFile? {
    return try {
        val result = tdlClient.execute(TdApi.GetRemoteFile(remoteId, null))
        result?.toTgFile()
    } catch (e: Exception) {
        UnifiedLog.w(TAG) { "Failed to resolve remoteId: ${remoteId.take(20)}..." }
        null  // Graceful degradation
    }
}
```

---

### Error Handling
- [ ] Bounded failure tracking in `TelegramThumbFetcherImpl`
- [ ] Graceful fallback for stale remoteIds
- [ ] Proper exception wrapping (no raw TDLib exceptions leaked)

---

## 📚 Reference Documents (Priority Order)

1. **`/contracts/TELEGRAM_ID_ARCHITECTURE_CONTRACT.md`** - AUTHORITATIVE remoteId-first design
2. **`/contracts/GLOSSARY_v2_naming_and_modules.md`** - Section 1.6 (Transport interfaces)
3. **`/contracts/LOGGING_CONTRACT_V2.md`** - Logging rules (v1.1)
4. **`/AGENTS.md`** - Player Layer Isolation (TdlibClientProvider forbidden)
5. **`/contracts/TELEGRAM_*.md (binding contracts)`** - TDLib integration SSOT (legacy reference)
6. **`/contracts/TELEGRAM_STRUCTURED_BUNDLES_CONTRACT.md`** - Bundle processing (pipeline responsibility)
7. TDLib documentation (upstream reference)

---

## 🚨 Common Violations & Solutions

### Violation 1: Exposing TdlClient

```kotlin
// ❌ WRONG
interface TelegramTransport {
    fun getClient(): TdlClient  // NEVER expose!
}

// ✅ CORRECT
// TdlClient is internal to DefaultTelegramClient
// Upper layers use typed interfaces only
```

### Violation 2: Persisting fileId

```kotlin
// ❌ WRONG
@Entity
data class ObxTelegramMessage(
    val fileId: Int,      // WRONG - volatile!
    val uniqueId: String, // WRONG - no resolution API!
)

// ✅ CORRECT
@Entity
data class ObxTelegramMessage(
    val remoteId: String,   // ✅ Stable
    val chatId: Long,       // ✅ Stable
    val messageId: Long,    // ✅ Stable
)
```

### Violation 3: Bundle Detection in Transport

```kotlin
// ❌ WRONG (in transport layer)
fun detectBundle(messages: List<TgMessage>): TelegramBundle {
    // Bundle logic...
}

// ✅ CORRECT
// Bundle detection is pipeline responsibility
// Per TELEGRAM_STRUCTURED_BUNDLES_CONTRACT R11:
// "Structured Bundles are processed entirely in pipeline/telegram"
```

### Violation 4: TdApi Types in DTOs

```kotlin
// ❌ WRONG
data class TgMessage(
    val content: TdApi.MessageContent,  // WRONG - raw TDLib type!
)

// ✅ CORRECT
data class TgMessage(
    val content: TgContent?,  // Wrapped DTO
)
```

### Violation 5: Reintroducing TdlibClientProvider

```kotlin
// ❌ WRONG (v1 legacy pattern)
@Singleton
class TdlibClientProvider @Inject constructor() {
    fun getClient(): TdlClient  // FORBIDDEN in v2!
}

// ✅ CORRECT
// Use typed interfaces instead:
// TelegramAuthClient, TelegramHistoryClient, TelegramFileClient, etc.
```

---

**End of PLATIN Instructions for infra/transport-telegram**

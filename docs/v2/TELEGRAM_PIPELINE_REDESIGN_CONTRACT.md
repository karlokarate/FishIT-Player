# Telegram Pipeline Redesign – Platinum Contract

**Version:** 1.0  
**Date:** 2026-02-09  
**Status:** Draft – Architecture Specification  
**Branch:** `architecture/v2-bootstrap`  
**Scope:** Complete redesign of the Telegram pipeline to achieve semantic parity with the Xtream pipeline, leveraging `dev.g000sha256:tdl-coroutines` for native Kotlin coroutine integration.

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Goals & Non-Goals](#2-goals--non-goals)
3. [Current State Analysis](#3-current-state-analysis)
4. [Target Architecture](#4-target-architecture)
5. [TDLib Coroutines Library (`tdl-coroutines`)](#5-tdlib-coroutines-library)
6. [Transport Layer Redesign](#6-transport-layer-redesign)
7. [Pipeline Layer Redesign](#7-pipeline-layer-redesign)
8. [Sync Service Integration](#8-sync-service-integration)
9. [Field Parity Matrix](#9-field-parity-matrix)
10. [Telegram-Specific Strategies](#10-telegram-specific-strategies)
11. [Orchestrator Integration](#11-orchestrator-integration)
12. [Migration Plan](#12-migration-plan)
13. [Risk Matrix](#13-risk-matrix)
14. [Appendix A: API Reference](#appendix-a-tdl-coroutines-api-reference)
15. [Appendix B: Xtream Reference Pipeline](#appendix-b-xtream-reference-pipeline)
16. [Contract References](#contract-references)

---

## 1. Executive Summary

The Telegram pipeline currently exists as a **partially implemented** ingestion system. While the transport layer (`DefaultTelegramClient`), pipeline module (`pipeline/telegram`), and structured bundle detection are functional, the **sync service layer is a stub** — the orchestrator's `syncTelegram()` method is a TODO. Additionally, the Telegram pipeline persists **significantly fewer fields** than the Xtream pipeline, resulting in impoverished NX entities for Telegram-sourced media.

This document specifies the **complete redesign** to achieve:

1. **Semantic parity** — Telegram-sourced `RawMediaMetadata` must populate the same fields as Xtream
2. **Orchestrator integration** — A `TelegramSyncService` following the `XtreamSyncService` pattern
3. **Platinum transport** — Leveraging `tdl-coroutines` v8.0.0 with proper timeout, paging, retry, cache management
4. **Architectural alignment** — Same producer/consumer/buffer pattern as Xtream

### Key Metric

| Metric | Current | Target |
|--------|---------|--------|
| `RawMediaMetadata` fields populated (Telegram) | 17 of 28 | **28 of 28** |
| `RawMediaMetadata` fields populated (Xtream) | 26 of 28 | 28 of 28 |
| `TelegramSyncService` | ❌ Stub | ✅ Full implementation |
| `tdl-coroutines` version | Unknown/older | **8.0.0** (TDLib 1.8.60) |
| Orchestrator integration | TODO | ✅ Parallel with Xtream |

---

## 2. Goals & Non-Goals

### Goals

| # | Goal | Rationale |
|---|------|-----------|
| G-1 | **Field parity**: Telegram `RawMediaMetadata` output populates all fields that Xtream populates | NX entities must be equally rich regardless of source |
| G-2 | **`TelegramSyncService`** following `XtreamSyncService` pattern | Seamless orchestrator integration |
| G-3 | **Update `tdl-coroutines` to v8.0.0** | Latest TDLib 1.8.60, pure Kotlin coroutine API, prebuilt native libs |
| G-4 | **Platinum paging**: Correct `getChatHistory` pagination with cursor tracking | Reliable, complete chat scanning |
| G-5 | **Platinum retry**: Exponential backoff with jitter for TDLib errors (429, FLOOD_WAIT) | Robust sync under rate limits |
| G-6 | **Platinum timeout**: Per-request timeout with structured concurrency | No hanging syncs |
| G-7 | **Platinum cache**: TDLib database flags + application-level dedup | Minimal redundant network calls |
| G-8 | **`Flow<SyncStatus>`** progress reporting matching Xtream pattern | Unified progress UI |

### Non-Goals

| # | Non-Goal | Reason |
|---|----------|--------|
| NG-1 | Implement Telegram Live TV | TDLib has no live channel concept |
| NG-2 | Implement EPG for Telegram | Not applicable |
| NG-3 | Implement Telegram categories | Chats are not categories; use chat-as-source model |
| NG-4 | Replace structured bundle detection | Existing bundle system is correct and tested |
| NG-5 | Modify NX entity schema | Schema already has all required fields |

---

## 3. Current State Analysis

### 3.1 Architecture Overview (Current)

```
┌─────────────────────────────────────────────────────────────────┐
│  CatalogSyncOrchestratorWorker                                  │
│  ├─ syncXtream() → XtreamSyncService.sync() ✅ FULL            │
│  ├─ syncTelegram() → TODO ❌ STUB                               │
│  └─ syncIo() → TODO                                            │
└─────────────────────────────────────────────────────────────────┘
```

### 3.2 Existing Modules

| Module | Path | Status | Description |
|--------|------|--------|-------------|
| Transport | `infra/transport-telegram/` | ✅ Functional | `DefaultTelegramClient` wrapping TDLib via `TdlClient` |
| Pipeline | `pipeline/telegram/` | ⚠️ Partial | 37 files — catalog, grouper, mapper, adapter, model, debug, tests |
| Sync Service | — | ❌ Missing | No `TelegramSyncService` exists |
| Data/Persistence | `infra/data-nx/` | ✅ Ready | NX entity writers already handle all fields |

### 3.3 Existing Transport Interfaces

```kotlin
// infra/transport-telegram/
interface TelegramAuthClient {
    val authState: Flow<TdlibAuthState>
    suspend fun ensureAuthorized()
    suspend fun isAuthorized(): Boolean
    suspend fun sendPhoneNumber(phone: String)
    suspend fun sendCode(code: String)
    suspend fun sendPassword(password: String)
    suspend fun logout()
}

interface TelegramHistoryClient {
    val messageUpdates: Flow<TgMessage>
    suspend fun getChats(): List<TgChat>
    suspend fun getChat(chatId: Long): TgChat?
    suspend fun fetchMessages(chatId: Long, fromMessageId: Long, limit: Int): List<TgMessage>
    suspend fun loadAllMessages(chatId: Long, onBatch: suspend (List<TgMessage>) -> Unit)
    suspend fun searchMessages(chatId: Long, query: String): List<TgMessage>
}

interface TelegramFileClient {
    val fileUpdates: Flow<TgFileUpdate>
    suspend fun startDownload(fileId: Int, priority: Int)
    suspend fun cancelDownload(fileId: Int)
    suspend fun getFile(fileId: Int): TgFile?
    suspend fun resolveRemoteId(remoteId: String): TgFile?
}
```

### 3.4 Existing Pipeline Classes

| Class | Purpose | Status |
|-------|---------|--------|
| `TelegramCatalogPipelineImpl` | Main pipeline — scans chats, produces `TelegramMediaItem` | ⚠️ Needs sync service wrapper |
| `TelegramPipelineAdapter` | Wraps transport for pipeline use | ✅ Done |
| `TelegramMessageBundler` | Groups messages into structured bundles | ✅ Done |
| `TelegramStructuredMetadataExtractor` | Parses TEXT messages for TMDB, rating, genres, etc. | ✅ Done |
| `TelegramBundleToMediaItemMapper` | Converts bundles → `TelegramMediaItem` | ✅ Done |
| `TelegramChatMediaClassifier` | Classifies chat content type (VOD, Series, Mixed) | ✅ Done |
| `TelegramRawMetadataExtensions.kt` | `toRawMediaMetadata()` conversion | ⚠️ **NEEDS FIELD FIXES** |

---

## 4. Target Architecture

### 4.1 Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│  CatalogSyncOrchestratorWorker                                      │
│  ├─ syncXtream() → XtreamSyncService.sync(config)                  │
│  └─ syncTelegram() → TelegramSyncService.sync(config)  ← NEW      │
│         │                                                           │
│         ▼                                                           │
│  ┌─────────────────────────────────────────────────────────┐       │
│  │  DefaultTelegramSyncService                              │       │
│  │  ├─ AUTH PREFLIGHT: ensureAuthorized()                   │       │
│  │  ├─ CHAT DISCOVERY: getChats() → filter media chats     │       │
│  │  ├─ PRODUCER: pipeline.scanCatalog(config)               │       │
│  │  │   ├─ Per chat: paginated getChatHistory()             │       │
│  │  │   ├─ Bundle detection (timestamp grouping)            │       │
│  │  │   ├─ Metadata extraction (structured bundles)         │       │
│  │  │   ├─ toRawMediaMetadata() — FIELD PARITY             │       │
│  │  │   └─ ChannelSyncBuffer.send(item)                     │       │
│  │  ├─ CONSUMER: buffer.consumeBatched(batchSize=50)        │       │
│  │  │   └─ catalogRepository.upsertAll(items)               │       │
│  │  └─ TMDB ENRICHMENT: Post-sync enrichment pass           │       │
│  └─────────────────────────────────────────────────────────┘       │
│         │                                                           │
│         ▼                                                           │
│  ┌─────────────────────────────────────────────────────────┐       │
│  │  DefaultTelegramClient (Transport)                       │       │
│  │  ├─ TdlClient (g000sha tdl-coroutines v8.0.0)           │       │
│  │  ├─ TelegramAuthSession (auth state machine)             │       │
│  │  ├─ TelegramChatBrowser (chat listing, history paging)   │       │
│  │  ├─ TelegramFileDownloadManager (file downloads)         │       │
│  │  └─ TelegramRetryPolicy (backoff, FLOOD_WAIT, timeouts)  │       │
│  └─────────────────────────────────────────────────────────┘       │
└─────────────────────────────────────────────────────────────────────┘
```

### 4.2 Semantic Alignment with Xtream

| Xtream Component | Telegram Equivalent | Notes |
|------------------|---------------------|-------|
| `XtreamSyncService` | `TelegramSyncService` | Same interface pattern |
| `XtreamSyncConfig` | `TelegramSyncConfig` | Auth, chat filters, mode |
| `XtreamCatalogPipeline` | `TelegramCatalogPipeline` | Already exists, needs wrapping |
| `XtreamCatalogEvent` | `TelegramCatalogEvent` | Same event sealed class |
| `ChannelSyncBuffer` | `ChannelSyncBuffer` | **SHARED** — same implementation |
| `SyncCheckpointStore` | `SyncCheckpointStore` | **SHARED** — per-chat cursor tracking |
| `IncrementalSyncDecider` | `TelegramIncrementalDecider` | Telegram-specific: message timestamp cursor |
| `XtreamCatalogRepository` | `TelegramCatalogRepository` | Both write to NX entities |
| `SyncPerfMetrics` | `SyncPerfMetrics` | **SHARED** — same telemetry |

---

## 5. TDLib Coroutines Library

### 5.1 Library Details

| Property | Value |
|----------|-------|
| **Package** | `dev.g000sha256:tdl-coroutines` |
| **Version** | **8.0.0** (2026-01-10) |
| **TDLib** | 1.8.60 (commit `0da5c72f`) |
| **Kotlin** | 2.2.20 |
| **Coroutines** | kotlinx-coroutines-core 1.10.2 |
| **Serialization** | kotlinx-serialization-json 1.9.0 |
| **Platforms** | Android (minSdk 21), JVM, iOS, macOS |
| **License** | Apache 2.0 |
| **Repo** | [github.com/g000sha256/tdl-coroutines](https://github.com/g000sha256/tdl-coroutines) |

### 5.2 Update Recommendation

**YES — update to v8.0.0.** Reasons:

1. **TDLib 1.8.60** — latest upstream fixes, performance improvements, API additions
2. **Prebuilt native libs** — ships `.so` for all Android ABIs (arm64-v8a, armeabi-v7a, x86_64, x86)
3. **Kotlin 2.2.20 + Coroutines 1.10.2** — matches project's Kotlin version trajectory
4. **KMP-ready** — future-proofs for Kotlin Multiplatform if needed
5. **Named arguments** — the library recommends named args to survive TDLib breaking changes

### 5.3 Gradle Integration

```kotlin
// gradle/libs.versions.toml
[versions]
tdl-coroutines = "8.0.0"

[libraries]
tdl-coroutines = { module = "dev.g000sha256:tdl-coroutines", version.ref = "tdl-coroutines" }

// infra/transport-telegram/build.gradle.kts
dependencies {
    implementation(libs.tdl.coroutines)
}
```

### 5.4 Core API Surface

```kotlin
// Creating a client
val client = TdlClient.create()

// All TDLib methods as suspend functions returning TdlResult<T>
val result: TdlResult<Chat> = client.getChat(chatId = 123456L)
when (result) {
    is TdlResult.Success -> result.result  // Chat object
    is TdlResult.Failure -> error("${result.code}: ${result.message}")
}

// Converting to Kotlin Result
val chat = client.getChat(chatId = 123L).toResult().getOrThrow()

// 174 typed update Flows
client.authorizationStateUpdates.collect { update -> /* auth state machine */ }
client.newMessageUpdates.collect { message -> /* new messages */ }
client.fileUpdates.collect { file -> /* download progress */ }

// Connection state
client.connectionStateUpdates.collect { state -> /* Connecting, Ready, etc. */ }

// Closing
client.close()
```

### 5.5 Architecture Internals

```
TdlClient (public API — 954 suspend methods, 174 typed Flows)
  ↓
TdlClientImpl (generated, delegates to TdlRepository)
  ↓
TdlRepository (request-response correlation via requestId + SharedFlow)
  ↓
TdlEngine (dedicated TDL-Sender-Thread + TDL-Receiver-Thread)
  ↓
TdlNative → JsonClient (JNI bridge to libtdjson)
```

**Key design:**
- Two dedicated single-thread dispatchers (sender/receiver)
- Request-response correlation via atomic `requestId` counter + `MutableSharedFlow`
- JSON serialization via kotlinx.serialization (not TDLib Java objects)
- Singleton engine shared across all `TdlClient` instances

### 5.6 What the Library Does NOT Provide

These are **application responsibilities** that FishIT must implement:

| Gap | Solution |
|-----|----------|
| No pagination helpers | `TelegramPaginatedScanner` — cursor-based `getChatHistory()` loop |
| No retry/timeout | `TelegramRetryPolicy` — exponential backoff with jitter, FLOOD_WAIT handling |
| No file download abstraction | Existing `TelegramFileDownloadManager` — enhance with retry |
| No caching layer | TDLib internal DB flags + application-level `SyncCheckpointStore` |
| No rate limiting | `TelegramRateLimiter` — Semaphore-based request throttling |
| No connection state abstraction | Map `ConnectionStateUpdates` → `TelegramConnectionState` |

---

## 6. Transport Layer Redesign

### 6.1 `DefaultTelegramClient` Updates

The existing `DefaultTelegramClient` already wraps `TdlClient`. Changes needed:

```kotlin
// BEFORE: Unknown TdlClient version
private val tdlClient: TdlClient

// AFTER: tdl-coroutines v8.0.0
// Same interface — TdlClient.create() returns the new version
// No structural changes needed, but:
// 1. Update import paths if package changed
// 2. Use TdlResult<T> sealed class properly
// 3. Add named arguments per library recommendation
```

### 6.2 New: `TelegramRetryPolicy`

```kotlin
/**
 * Platinum retry policy for TDLib operations.
 *
 * Handles:
 * - FLOOD_WAIT_X errors → wait X seconds + jitter
 * - Network errors → exponential backoff (1s, 2s, 4s, 8s, max 30s)
 * - TDLib internal errors → 3 retries with backoff
 * - Timeout via withTimeout {} → configurable per operation type
 *
 * Location: infra/transport-telegram/.../internal/TelegramRetryPolicy.kt
 */
class TelegramRetryPolicy(
    private val maxRetries: Int = 3,
    private val initialDelayMs: Long = 1_000L,
    private val maxDelayMs: Long = 30_000L,
    private val timeoutMs: Long = 30_000L,
) {
    suspend fun <T> execute(
        operationName: String,
        block: suspend () -> TdlResult<T>,
    ): TdlResult<T> {
        var lastResult: TdlResult<T>? = null
        var currentDelay = initialDelayMs

        repeat(maxRetries + 1) { attempt ->
            val result = withTimeoutOrNull(timeoutMs) { block() }
                ?: return TdlResult.Failure(code = -1, message = "Timeout after ${timeoutMs}ms")

            when {
                result is TdlResult.Success -> return result
                result is TdlResult.Failure && result.isFloodWait -> {
                    val waitSeconds = result.floodWaitSeconds
                    val jitter = Random.nextLong(500, 2_000)
                    delay(waitSeconds * 1000L + jitter)
                }
                result is TdlResult.Failure && result.isRetryable -> {
                    if (attempt < maxRetries) {
                        val jitter = Random.nextLong(0, currentDelay / 2)
                        delay(currentDelay + jitter)
                        currentDelay = (currentDelay * 2).coerceAtMost(maxDelayMs)
                    }
                }
                else -> return result  // Non-retryable error
            }
            lastResult = result
        }
        return lastResult ?: TdlResult.Failure(code = -1, message = "Max retries exceeded")
    }
}
```

### 6.3 New: `TelegramPaginatedScanner`

```kotlin
/**
 * Cursor-based paginated chat history scanner.
 *
 * Reference: docs/telegram/cli/ implementation of getChatHistory pagination.
 *
 * TDLib pagination rules:
 * - getChatHistory(chatId, fromMessageId=0, offset=0, limit=100) → newest messages
 * - Next page: fromMessageId = lastMessage.id, offset=0
 * - Empty result = end of history
 * - limit max = 100 per request
 *
 * Location: infra/transport-telegram/.../internal/TelegramPaginatedScanner.kt
 */
class TelegramPaginatedScanner(
    private val client: TdlClient,
    private val retryPolicy: TelegramRetryPolicy,
    private val rateLimiter: TelegramRateLimiter,
) {
    /**
     * Scan all messages in a chat, emitting batches via Flow.
     *
     * @param chatId Target chat
     * @param fromMessageId Start cursor (0 = newest)
     * @param onlyLocal If true, only read from TDLib cache (no network)
     * @param maxMessages Stop after this many messages (0 = unlimited)
     */
    fun scanChat(
        chatId: Long,
        fromMessageId: Long = 0L,
        onlyLocal: Boolean = false,
        maxMessages: Int = 0,
    ): Flow<List<TgMessage>> = flow {
        var cursor = fromMessageId
        var totalFetched = 0
        val pageSize = 100

        while (true) {
            // Rate limiting between requests
            rateLimiter.acquire()

            val result = retryPolicy.execute("getChatHistory($chatId, $cursor)") {
                client.getChatHistory(
                    chatId = chatId,
                    fromMessageId = cursor,
                    offset = 0,
                    limit = pageSize,
                    onlyLocal = onlyLocal,
                )
            }

            val messages = when (result) {
                is TdlResult.Success -> result.result.messages?.toList() ?: emptyList()
                is TdlResult.Failure -> {
                    UnifiedLog.w(TAG) { "getChatHistory failed: ${result.message}" }
                    break
                }
            }

            if (messages.isEmpty()) break

            val mapped = messages.mapNotNull { TgMessageMapper.fromTdl(it) }
            emit(mapped)

            totalFetched += messages.size
            cursor = messages.last().id

            if (maxMessages > 0 && totalFetched >= maxMessages) break
        }
    }
}
```

### 6.4 New: `TelegramRateLimiter`

```kotlin
/**
 * Rate limiter for TDLib requests to avoid FLOOD_WAIT.
 *
 * Default: max 20 requests per second with token bucket algorithm.
 * Configurable per device profile (FireTV = conservative, Shield = aggressive).
 */
class TelegramRateLimiter(
    private val requestsPerSecond: Int = 20,
) {
    private val semaphore = Semaphore(requestsPerSecond)
    private val scope = CoroutineScope(SupervisorJob())

    suspend fun acquire() {
        semaphore.acquire()
        scope.launch {
            delay(1000L / requestsPerSecond)
            semaphore.release()
        }
    }
}
```

### 6.5 TDLib Database Configuration

```kotlin
// Optimal TDLib config for sync operations
client.setTdlibParameters(
    useTestDc = false,
    databaseDirectory = context.filesDir.resolve("tdlib").absolutePath,
    filesDirectory = context.cacheDir.resolve("tdlib-files").absolutePath,
    databaseEncryptionKey = byteArrayOf(),
    useFileDatabase = true,          // ✅ Cache file metadata (remoteId → fileId)
    useChatInfoDatabase = true,      // ✅ Cache chat info (avoid re-fetching)
    useMessageDatabase = true,       // ✅ Cache messages (incremental scan)
    useSecretChats = false,          // ❌ Not needed for media
    apiId = BuildConfig.TELEGRAM_API_ID,
    apiHash = BuildConfig.TELEGRAM_API_HASH,
    systemLanguageCode = "en",
    deviceModel = Build.MODEL,
    systemVersion = Build.VERSION.RELEASE,
    applicationVersion = BuildConfig.VERSION_NAME,
)
```

**Cache strategy:**
- `useMessageDatabase = true` enables incremental scans — TDLib won't re-download messages from server if already cached
- `useFileDatabase = true` enables `remoteId → fileId` resolution without network
- `useChatInfoDatabase = true` caches chat metadata, reducing startup time
- Combined with `onlyLocal = true` flag on `getChatHistory()`, this enables fast cache-only rescans

---

## 7. Pipeline Layer Redesign

### 7.1 `toRawMediaMetadata()` — Field Parity Fixes

The current `TelegramRawMetadataExtensions.kt` drops several fields that Xtream populates. Required changes:

```kotlin
// pipeline/telegram/.../model/TelegramRawMetadataExtensions.kt

fun TelegramMediaItem.toRawMediaMetadata(): RawMediaMetadata = RawMediaMetadata(
    // ── EXISTING (keep) ──
    originalTitle = extractRawTitle(),
    mediaType = mapTelegramMediaType(),
    year = structuredYear ?: year,
    season = seasonNumber,
    episode = episodeNumber,
    durationMs = (structuredLengthMinutes?.let { it * 60_000L })
        ?: durationSecs?.let { it * 1_000L },
    externalIds = buildExternalIds(),
    sourceType = SourceType.TELEGRAM,
    sourceLabel = buildSourceLabel(),
    sourceId = "msg:${chatId}:${messageId}",
    pipelineIdTag = PipelineIdTag.TELEGRAM,
    poster = toPosterImageRef(),
    thumbnail = toThumbnailImageRef(),
    placeholderThumbnail = toMinithumbnailImageRef(),
    rating = structuredRating,
    ageRating = structuredFsk,
    plot = description,
    genres = structuredGenres?.joinToString(", ") ?: genres,
    director = structuredDirector,
    addedTimestamp = date?.let { it * 1000L },
    globalId = "",  // Computed by canonical linker

    // ── NEW: Previously missing fields (FIELD PARITY) ──
    backdrop = toBackdropImageRef(),                              // FIX: was always null
    cast = null,                                                   // TDLib doesn't provide; enriched by TMDB
    trailer = null,                                                // TDLib doesn't provide; enriched by TMDB
    releaseDate = structuredYear?.let { "$it-01-01" },            // FIX: was not mapped at all
    lastModifiedTimestamp = date?.let { it * 1000L },             // FIX: was not mapped
    isAdult = false,                                               // TDLib doesn't provide; enriched by normalizer
    categoryId = chatId.toString(),                                // FIX: map chat ID as category

    // ── NEW: Enhanced playbackHints (FIELD PARITY with Xtream) ──
    playbackHints = buildMap {
        put("chatId", chatId.toString())
        put("messageId", messageId.toString())
        remoteId?.let { put("remoteId", it) }
        mimeType?.let { put("mimeType", it) }
        sizeBytes?.let { put("fileSize", it.toString()) }
        fileName?.let { put("fileName", it) }
        // NEW: video metadata in hints (parity with Xtream episodes)
        width?.let { put("videoWidth", it.toString()) }
        height?.let { put("videoHeight", it.toString()) }
        mimeType?.let { mime ->
            // Infer container from mimeType
            val ext = when {
                mime.contains("mp4") -> "mp4"
                mime.contains("x-matroska") || mime.contains("mkv") -> "mkv"
                mime.contains("avi") -> "avi"
                mime.contains("webm") -> "webm"
                else -> null
            }
            ext?.let { put("containerExt", it) }
        }
        // Compute qualityHeight from video height
        height?.let { h ->
            val quality = when {
                h >= 2160 -> "2160"
                h >= 1080 -> "1080"
                h >= 720 -> "720"
                h >= 480 -> "480"
                else -> h.toString()
            }
            put("qualityHeight", quality)
        }
        supportsStreaming?.let { put("supportsStreaming", it.toString()) }
    },
)
```

### 7.2 Backdrop Image Resolution

```kotlin
/**
 * Generate backdrop ImageRef for Telegram items.
 *
 * Strategy (from highest to lowest quality):
 * 1. Largest photo from PHOTO bundle message (if FULL_3ER bundle)
 * 2. Video thumbnail (if available, typically 320×180 or higher)
 * 3. null (TMDB enrichment will supply backdrop later)
 */
private fun TelegramMediaItem.toBackdropImageRef(): ImageRef? {
    // For FULL_3ER bundles: largest photo size serves as backdrop
    val largestPhoto = photoSizes?.maxByOrNull { it.width * it.height }
    if (largestPhoto != null) {
        return ImageRef.remote(
            url = "tg:photo:${largestPhoto.remoteId}",
            width = largestPhoto.width,
            height = largestPhoto.height,
        )
    }
    // Fallback: video thumbnail as backdrop (better than nothing)
    return thumbRemoteId?.let { remoteId ->
        ImageRef.remote(
            url = "tg:thumb:$remoteId",
            width = thumbnailWidth ?: 0,
            height = thumbnailHeight ?: 0,
        )
    }
}
```

### 7.3 New: `TelegramCatalogEvent`

```kotlin
/**
 * Events emitted by the Telegram catalog pipeline.
 * Mirrors XtreamCatalogEvent for semantic consistency.
 */
sealed interface TelegramCatalogEvent {
    /** A media item was discovered and converted to RawMediaMetadata. */
    data class ItemDiscovered(
        val raw: RawMediaMetadata,
        val chatId: Long,
        val messageId: Long,
    ) : TelegramCatalogEvent

    /** A chat scan phase started. */
    data class ChatScanStarted(
        val chatId: Long,
        val chatTitle: String,
        val estimatedMessages: Int?,
    ) : TelegramCatalogEvent

    /** A chat scan phase completed. */
    data class ChatScanCompleted(
        val chatId: Long,
        val itemsFound: Int,
        val messagesScanned: Int,
    ) : TelegramCatalogEvent

    /** Progress update during scanning. */
    data class ScanProgress(
        val chatId: Long,
        val messagesProcessed: Int,
        val itemsFound: Int,
    ) : TelegramCatalogEvent
}
```

---

## 8. Sync Service Integration

### 8.1 `TelegramSyncService` Interface

```kotlin
/**
 * Telegram synchronization service — mirrors XtreamSyncService.
 *
 * Location: core/catalog-sync/sources/telegram/TelegramSyncService.kt
 */
interface TelegramSyncService {

    /**
     * Execute Telegram catalog synchronization.
     *
     * Flow emissions follow the same SyncStatus pattern as XtreamSyncService:
     * Idle → Checking → Starting → Started → InProgress → Completed/Error/Cancelled
     *
     * @param config Sync configuration (chat filters, mode, auth state)
     * @return Flow of sync status updates
     */
    fun sync(config: TelegramSyncConfig): Flow<SyncStatus>

    /** Cancel active sync. Cooperative — saves checkpoint before stopping. */
    fun cancel()

    /** Whether a sync is currently active. */
    val isActive: Boolean
}
```

### 8.2 `TelegramSyncConfig`

```kotlin
/**
 * Configuration for Telegram sync operations.
 * Mirrors XtreamSyncConfig semantics.
 */
data class TelegramSyncConfig(
    /** Sync mode: AUTO, EXPERT_SYNC_NOW, FORCE_RESCAN */
    val mode: CatalogSyncWorkMode,
    /** Optional: Restrict to specific chat IDs. Empty = all media chats. */
    val chatFilter: Set<Long> = emptySet(),
    /** Whether to force full rescan, ignoring checkpoints. */
    val forceFullScan: Boolean = false,
    /** Max messages per chat (0 = unlimited). */
    val maxMessagesPerChat: Int = 0,
    /** Whether to use cached messages only (no network). */
    val cacheOnly: Boolean = false,
    /** Device profile for adaptive tuning. */
    val deviceProfile: DeviceProfile = DeviceProfile.UNKNOWN,
)
```

### 8.3 `DefaultTelegramSyncService`

```kotlin
/**
 * Default implementation of TelegramSyncService.
 *
 * Architecture mirrors DefaultXtreamSyncService:
 * - Auth preflight → Chat discovery → Parallel chat scans → Buffer → Persist
 * - Uses ChannelSyncBuffer for producer/consumer pattern  
 * - Leverages checkpoint store for incremental scans
 * - Reports progress via Flow<SyncStatus>
 *
 * Location: core/catalog-sync/sources/telegram/DefaultTelegramSyncService.kt
 */
@Singleton
class DefaultTelegramSyncService @Inject constructor(
    private val pipeline: TelegramCatalogPipeline,
    private val authClient: TelegramAuthClient,
    private val catalogRepository: TelegramCatalogRepository,
    private val checkpointStore: SyncCheckpointStore,
    private val deviceProfileDetector: DeviceProfileDetector,
    private val syncPerfMetrics: SyncPerfMetrics,
) : TelegramSyncService {

    private var activeJob: Job? = null

    override val isActive: Boolean
        get() = activeJob?.isActive == true

    override fun sync(config: TelegramSyncConfig): Flow<SyncStatus> = flow {
        val startTime = System.currentTimeMillis()
        emit(SyncStatus.Starting(source = SYNC_SOURCE))

        // ── PHASE 1: Auth Preflight ──
        if (!authClient.isAuthorized()) {
            emit(SyncStatus.Error(
                source = SYNC_SOURCE,
                message = "Telegram not authorized",
                canRetry = false,
            ))
            return@flow
        }
        emit(SyncStatus.Started(
            source = SYNC_SOURCE,
            accountKey = "telegram",
            isFullSync = config.forceFullScan,
            estimatedPhases = listOf(SyncPhase.TELEGRAM_CHAT_SCAN),
        ))

        // ── PHASE 2: Incremental Check ──
        val lastCheckpoint = if (!config.forceFullScan) {
            checkpointStore.getCheckpoint("telegram")
        } else null
        val lastScanTimestamp = lastCheckpoint?.lastTimestamp ?: 0L

        // ── PHASE 3: Producer/Consumer Scan ──
        val buffer = ChannelSyncBuffer<RawMediaMetadata>(capacity = 200)
        val itemCount = AtomicInteger(0)

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        activeJob = scope.coroutineContext[Job]

        // Producer: pipeline scans chats
        scope.launch {
            try {
                pipeline.scanCatalog(config, lastScanTimestamp).collect { event ->
                    when (event) {
                        is TelegramCatalogEvent.ItemDiscovered -> {
                            buffer.send(event.raw)
                            itemCount.incrementAndGet()
                        }
                        is TelegramCatalogEvent.ScanProgress -> {
                            // Progress update (emit via channel to main flow)
                        }
                        else -> { /* log */ }
                    }
                }
                buffer.close()
            } catch (e: CancellationException) {
                buffer.close()
                throw e
            }
        }

        // Consumer: batch persist
        buffer.consumeBatched(batchSize = 50) { batch ->
            catalogRepository.upsertAll(batch)
            emit(SyncStatus.InProgress(
                source = SYNC_SOURCE,
                phase = SyncPhase.TELEGRAM_CHAT_SCAN,
                itemsProcessed = itemCount.get(),
            ))
        }

        // ── PHASE 4: Save Checkpoint ──
        val now = System.currentTimeMillis()
        checkpointStore.saveCheckpoint("telegram", SyncCheckpoint(
            lastTimestamp = now / 1000L,
            completedAt = now,
        ))

        val duration = (System.currentTimeMillis() - startTime).milliseconds
        emit(SyncStatus.Completed(
            source = SYNC_SOURCE,
            totalDuration = duration,
            itemCounts = SyncStatus.Completed.ItemCounts(
                total = itemCount.get(),
            ),
            wasIncremental = lastCheckpoint != null,
        ))
    }

    override fun cancel() {
        activeJob?.cancel()
    }

    companion object {
        private const val SYNC_SOURCE = "TELEGRAM"
    }
}
```

---

## 9. Field Parity Matrix

### 9.1 Complete Field Comparison: Current vs Target

| # | `RawMediaMetadata` Field | Xtream VOD | Xtream Series | Xtream Episode | Telegram Current | Telegram Target | Fix Required |
|---|---|---|---|---|---|---|---|
| 1 | `originalTitle` | ✅ `name` | ✅ `name` | ✅ `title` | ✅ fileName/caption | ✅ Same | — |
| 2 | `mediaType` | ✅ MOVIE | ✅ SERIES | ✅ SERIES_EPISODE | ⚠️ often UNKNOWN | ⚠️ Same (normalizer resolves) | — |
| 3 | `year` | ✅ | ✅ | — | ⚠️ bundle-only | ⚠️ Same + TMDB enrichment | — |
| 4 | `season` | — | — | ✅ | ✅ parsed | ✅ Same | — |
| 5 | `episode` | — | — | ✅ | ✅ parsed | ✅ Same | — |
| 6 | `durationMs` | ✅ | ✅ | ✅ | ✅ | ✅ Same | — |
| 7 | `externalIds` | ✅ TMDb | ✅ TMDb | ✅ TMDb | ⚠️ bundle-only | ⚠️ Same + TMDB enrichment | — |
| 8 | `sourceType` | ✅ XTREAM | ✅ | ✅ | ✅ TELEGRAM | ✅ Same | — |
| 9 | `sourceLabel` | ✅ | ✅ | ✅ | ✅ | ✅ Same | — |
| 10 | `sourceId` | ✅ | ✅ | ✅ | ✅ `msg:chatId:msgId` | ✅ Same | — |
| 11 | `pipelineIdTag` | ✅ XTREAM | ✅ | ✅ | ✅ TELEGRAM | ✅ Same | — |
| 12 | `addedTimestamp` | ✅ `added` | ✅ `lastModified` | ✅ `added` | ✅ `date*1000L` | ✅ Same | — |
| 13 | `lastModifiedTimestamp` | ✅ | ✅ | ✅ | ❌ **null** | ✅ `date*1000L` | **FIX** |
| 14 | `poster` | ✅ | ✅ | — | ✅ photo/thumb | ✅ Same | — |
| 15 | `backdrop` | ✅ (detail) | ✅ | — | ❌ **always null** | ✅ photo or thumb fallback | **FIX** |
| 16 | `thumbnail` | — | — | ✅ | ✅ | ✅ Same | — |
| 17 | `placeholderThumbnail` | — | — | — | ✅ minithumbnail | ✅ Same | — |
| 18 | `rating` | ✅ | ✅ | ✅ | ⚠️ bundle-only | ⚠️ Same + TMDB enrichment | — |
| 19 | `ageRating` | — | — | — | ✅ `structuredFsk` | ✅ Same | — |
| 20 | `plot` | ✅ | ✅ | ✅ | ⚠️ `description` (rare) | ⚠️ Same + TMDB enrichment | — |
| 21 | `genres` | ✅ | ✅ | — | ⚠️ bundle-only | ⚠️ Same + TMDB enrichment | — |
| 22 | `director` | ✅ (detail) | ✅ | — | ⚠️ bundle-only | ⚠️ Same + TMDB enrichment | — |
| 23 | `cast` | ✅ (detail) | ✅ | — | ❌ **always null** | ⚠️ null (TMDB enrichment fills) | — |
| 24 | `trailer` | ✅ (detail) | ✅ | — | ❌ **not mapped** | ⚠️ null (TMDB enrichment fills) | — |
| 25 | `releaseDate` | ✅ (detail) | ✅ | ✅ | ❌ **not mapped** | ✅ from `structuredYear` | **FIX** |
| 26 | `isAdult` | ✅ | ✅ | — | ❌ defaults false | ⚠️ false (normalizer detects) | — |
| 27 | `categoryId` | ✅ | ✅ | — | ❌ **not mapped** | ✅ `chatId.toString()` | **FIX** |
| 28 | `playbackHints` | ✅ rich | ✅ rich | ✅ rich | ⚠️ basic (6 keys) | ✅ enhanced (12 keys) | **FIX** |

### 9.2 PlaybackHints Comparison

| Hint Key | Xtream | Telegram Current | Telegram Target |
|----------|--------|------------------|-----------------|
| `chatId` | — | ✅ | ✅ |
| `messageId` | — | ✅ | ✅ |
| `remoteId` | — | ✅ | ✅ |
| `mimeType` | — | ✅ | ✅ |
| `fileSize` | — | ✅ | ✅ |
| `fileName` | — | ✅ | ✅ |
| `contentType` | ✅ | — | — (different model) |
| `vodId` / `seriesId` | ✅ | — | — (different model) |
| `videoWidth` | ✅ | ❌ | ✅ **NEW** |
| `videoHeight` | ✅ | ❌ | ✅ **NEW** |
| `videoCodec` | ✅ | ❌ | ❌ (TDLib doesn't expose) |
| `audioCodec` | ✅ | ❌ | ❌ (TDLib doesn't expose) |
| `audioChannels` | ✅ | ❌ | ❌ (TDLib doesn't expose) |
| `bitrate` | ✅ | ❌ | ❌ (TDLib doesn't expose) |
| `containerExt` | ✅ | ❌ | ✅ **NEW** (inferred from mimeType) |
| `qualityHeight` | ✅ | ❌ | ✅ **NEW** (from video height) |
| `supportsStreaming` | — | ❌ | ✅ **NEW** |

### 9.3 Fields TDLib Cannot Provide (TMDB Enrichment Required)

These fields are structurally unavailable from Telegram messages. The downstream **TMDB enrichment pipeline** fills them after the catalog sync:

| Field | Enrichment Strategy |
|-------|---------------------|
| `cast` | TMDB `/movie/{id}/credits` or `/tv/{id}/credits` |
| `trailer` | TMDB `/movie/{id}/videos` or `/tv/{id}/videos` |
| `videoCodec`, `audioCodec`, `audioChannels`, `bitrate` | Not available from TDLib. Probed at playback time by ExoPlayer. |
| `plot` (when not in bundle) | TMDB `/movie/{id}` or `/tv/{id}` overview field |
| `director` (when not in bundle) | TMDB credits endpoint |
| `genres` (when not in bundle) | TMDB `/movie/{id}` or `/tv/{id}` genre_ids |
| `isAdult` | TMDB `adult` field OR normalizer adult-content detection |
| `rating` (when not in bundle) | TMDB `vote_average` |
| `backdrop` (when no photo bundle) | TMDB `backdrop_path` |

**Key insight:** The Xtream pipeline has the same TMDB enrichment dependency for VOD list items (which lack detail fields like cast, trailer, backdrop). The difference is that Xtream's API provides more metadata in the initial scan, while Telegram relies more heavily on TMDB enrichment. The **end result in NX entities is identical** because both go through the same enrichment pipeline.

---

## 10. Telegram-Specific Strategies

### 10.1 Pagination Strategy

```
Xtream: Single API call per content type → get_vod_streams, get_series, get_live_streams
Telegram: Cursor-based pagination per chat → getChatHistory(fromMessageId, limit=100)
```

**Implementation:**

```kotlin
// Per-chat cursor pagination with checkpoint support
suspend fun scanChat(chatId: Long, checkpoint: Long?) {
    var cursor = checkpoint ?: 0L  // 0 = start from newest

    while (true) {
        val messages = retryPolicy.execute("getChatHistory") {
            client.getChatHistory(
                chatId = chatId,
                fromMessageId = cursor,
                offset = 0,
                limit = 100,
                onlyLocal = false,
            )
        }.toResult().getOrNull()?.messages ?: break

        if (messages.isEmpty()) break

        // Process batch
        val items = processBatch(messages, chatId)
        items.forEach { buffer.send(it) }

        // Advance cursor
        cursor = messages.last().id

        // Save per-chat checkpoint for resumption
        checkpointStore.saveChatCursor(chatId, cursor)
    }
}
```

### 10.2 Retry Strategy

| Error Type | Strategy | Parameters |
|------------|----------|------------|
| `FLOOD_WAIT_X` | Wait X seconds + jitter (500ms–2s) | `FLOOD_WAIT` seconds from error message |
| `429 Too Many Requests` | Exponential backoff | Initial 1s, max 30s, 3 retries |
| `TIMEOUT` (no response) | Retry with increased timeout | Initial 30s, then 60s, max 120s |
| `NETWORK_ERROR` | Exponential backoff + connection check | Initial 2s, max 60s, 5 retries |
| `DATABASE_ERROR` | Single retry after 1s | TDLib internal DB issue |
| `AUTH_ERROR` | **No retry** — emit non-retryable error | Auth state invalid |
| `CHAT_NOT_FOUND` | **Skip chat** — log warning | Chat may be deleted/restricted |

### 10.3 Timeout Strategy

| Operation | Timeout | Rationale |
|-----------|---------|-----------|
| `setTdlibParameters` | 30s | One-time setup |
| `ensureAuthorized` | 60s | May require server round-trip |
| `getChats` | 30s | Small payload |
| `getChatHistory` (per page) | 30s | 100 messages max |
| `getRemoteFile` | 15s | Simple resolution |
| `downloadFile` (thumbnail) | 60s | Small files (~50–200KB) |
| Full chat scan (per chat) | 10 min | Large chats may have thousands of messages |
| Total sync operation | 30 min | Configurable via `max_runtime_ms` |

### 10.4 Cache Management

```
┌─────────────────────────────────────────────────────────────────┐
│                     CACHE HIERARCHY                              │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  LAYER 1: TDLib Message DB (useMessageDatabase=true)            │
│  ├─ Messages cached locally after first fetch                   │
│  ├─ getChatHistory(onlyLocal=true) reads from cache only        │
│  └─ Enables fast incremental rescans                            │
│                                                                  │
│  LAYER 2: TDLib File DB (useFileDatabase=true)                  │
│  ├─ remoteId → fileId resolution cached                         │
│  ├─ File metadata (size, path) cached                           │
│  └─ Avoids redundant getRemoteFile() calls                     │
│                                                                  │
│  LAYER 3: SyncCheckpointStore (application level)               │
│  ├─ Per-chat cursor: last scanned messageId                     │
│  ├─ Global timestamp: last successful sync time                 │
│  └─ Used by incremental sync to skip already-scanned messages   │
│                                                                  │
│  LAYER 4: Fingerprint Dedup (shared with Xtream)                │
│  ├─ Hash of (chatId, messageId) avoids re-processing            │
│  └─ ~90% fewer DB writes on incremental sync                    │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### 10.5 Incremental Sync Strategy

```kotlin
/**
 * Telegram incremental sync decision tree.
 *
 * Unlike Xtream (which uses ETag/304 + item count + fingerprint),
 * Telegram uses message-timestamp cursor:
 *
 * 1. Load checkpoint: lastTimestamp, lastMessageId per chat
 * 2. For each chat:
 *    a. Fetch newest messages (fromMessageId=0, limit=10)
 *    b. If newest message.date <= lastTimestamp → SKIP (no new content)
 *    c. Else: scan from checkpoint cursor until reaching lastTimestamp
 * 3. Only process messages with date > lastTimestamp
 */
class TelegramIncrementalDecider {
    suspend fun shouldScanChat(
        chatId: Long,
        checkpoint: ChatScanCheckpoint?,
        client: TelegramHistoryClient,
    ): IncrementalDecision {
        if (checkpoint == null) return IncrementalDecision.FullScan

        // Quick probe: fetch latest 10 messages
        val recentMessages = client.fetchMessages(chatId, fromMessageId = 0L, limit = 10)
        if (recentMessages.isEmpty()) return IncrementalDecision.Skip("Empty chat")

        val newestDate = recentMessages.maxOf { it.date }
        if (newestDate <= checkpoint.lastTimestamp) {
            return IncrementalDecision.Skip("No new messages since ${checkpoint.lastTimestamp}")
        }

        return IncrementalDecision.Incremental(
            fromMessageId = checkpoint.lastMessageId,
            sinceTimestamp = checkpoint.lastTimestamp,
        )
    }
}

sealed class IncrementalDecision {
    object FullScan : IncrementalDecision()
    data class Skip(val reason: String) : IncrementalDecision()
    data class Incremental(
        val fromMessageId: Long,
        val sinceTimestamp: Long,
    ) : IncrementalDecision()
}
```

---

## 11. Orchestrator Integration

### 11.1 Wire into `CatalogSyncOrchestratorWorker`

```kotlin
// app-v2/.../work/CatalogSyncOrchestratorWorker.kt

// BEFORE:
private suspend fun syncTelegram(input: WorkerInputData, startTimeMs: Long): SyncResult {
    UnifiedLog.i(TAG) { "Telegram sync: TODO - awaiting TelegramSyncService" }
    return SyncResult(source = "TELEGRAM", itemsPersisted = 0)
}

// AFTER:
private suspend fun syncTelegram(input: WorkerInputData, startTimeMs: Long): SyncResult {
    if (!sourceActivationStore.isSourceActive(SourceType.TELEGRAM)) {
        return SyncResult(source = "TELEGRAM", itemsPersisted = 0, skipped = true)
    }

    val config = TelegramSyncConfig(
        mode = input.syncMode,
        forceFullScan = input.syncMode == CatalogSyncWorkMode.EXPERT_FORCE_RESCAN,
        deviceProfile = deviceProfileDetector.detect(),
    )

    var itemsPersisted = 0
    telegramSyncService.sync(config).collect { status ->
        when (status) {
            is SyncStatus.InProgress -> {
                itemsPersisted = status.itemsProcessed
                setProgress(WorkerOutputData.progress(
                    source = "TELEGRAM",
                    phase = status.phase?.name ?: "SCANNING",
                    items = status.itemsProcessed,
                ))
            }
            is SyncStatus.Error -> {
                if (!status.canRetry) {
                    UnifiedLog.e(TAG) { "Telegram sync failed (non-retryable): ${status.message}" }
                }
            }
            is SyncStatus.Completed -> {
                itemsPersisted = status.itemCounts.total
            }
            else -> { /* Starting, Checking, etc. */ }
        }
    }

    val durationMs = System.currentTimeMillis() - startTimeMs
    return SyncResult(
        source = "TELEGRAM",
        itemsPersisted = itemsPersisted,
        durationMs = durationMs,
    )
}
```

### 11.2 DI Module

```kotlin
// core/catalog-sync/sources/telegram/di/TelegramSyncModule.kt

@Module
@InstallIn(SingletonComponent::class)
abstract class TelegramSyncModule {

    @Binds
    @Singleton
    abstract fun bindTelegramSyncService(
        impl: DefaultTelegramSyncService,
    ): TelegramSyncService
}
```

### 11.3 Parallel Execution with Xtream

```kotlin
// In CatalogSyncOrchestratorWorker.doWork():

// Both sources run in parallel (same as today, just no longer a stub)
val results = coroutineScope {
    val xtreamDeferred = async { syncXtream(input, startTime) }
    val telegramDeferred = async { syncTelegram(input, startTime) }

    listOf(
        xtreamDeferred.await(),
        telegramDeferred.await(),
    )
}
```

---

## 12. Migration Plan

### Phase 1: Library Update (Low Risk)

| Task | Files | Effort |
|------|-------|--------|
| Update `tdl-coroutines` to 8.0.0 in `libs.versions.toml` | 1 file | S |
| Update `infra/transport-telegram/build.gradle.kts` dependency | 1 file | S |
| Verify API compatibility (named arguments) | Transport layer files | M |
| Run compile + tests | — | S |

### Phase 2: Transport Enhancements (Medium Risk)

| Task | Files | Effort |
|------|-------|--------|
| Create `TelegramRetryPolicy` | 1 new file | M |
| Create `TelegramRateLimiter` | 1 new file | S |
| Enhance `TelegramPaginatedScanner` / `TelegramChatBrowser` | 1–2 files | M |
| Add TDLib DB configuration (useMessageDatabase=true, etc.) | 1 file | S |
| Update `DefaultTelegramClient` for enhanced retry/timeout | 1 file | M |

### Phase 3: Pipeline Field Parity (Medium Risk)

| Task | Files | Effort |
|------|-------|--------|
| Fix `toRawMediaMetadata()` — add all missing fields | `TelegramRawMetadataExtensions.kt` | M |
| Add `toBackdropImageRef()` | Same file | S |
| Enhance `playbackHints` with video/container metadata | Same file | S |
| Update golden file tests | Test files | M |
| Run pipeline tests | — | S |

### Phase 4: Sync Service (High Impact)

| Task | Files | Effort |
|------|-------|--------|
| Create `TelegramSyncService` interface | 1 new file | S |
| Create `TelegramSyncConfig` | 1 new file | S |
| Create `DefaultTelegramSyncService` | 1 new file | L |
| Create `TelegramIncrementalDecider` | 1 new file | M |
| Create `TelegramCatalogEvent` sealed class | 1 new file | S |
| Wire pipeline to produce `TelegramCatalogEvent` | `TelegramCatalogPipelineImpl.kt` | M |
| Create `TelegramCatalogRepository` (data layer) | 1 new file | M |
| DI module (`TelegramSyncModule`) | 1 new file | S |

### Phase 5: Orchestrator Integration (Medium Risk)

| Task | Files | Effort |
|------|-------|--------|
| Replace `syncTelegram()` stub in orchestrator | `CatalogSyncOrchestratorWorker.kt` | M |
| Add `TelegramSyncService` injection | Same file | S |
| Progress reporting integration | Same file | S |
| Error handling / retry logic | Same file | M |
| End-to-end test: Orchestrator → TelegramSync → Pipeline → NX | Integration test | L |

### Phase 6: Verification

| Task | Verification |
|------|-------------|
| Field parity audit | Compare NX entity fields for identical media from both sources |
| Incremental sync | Verify checkpoint-based resumption works correctly |
| Error recovery | Simulate FLOOD_WAIT, network loss, auth expiry |
| Performance | Benchmark: messages/second, memory usage, battery impact |
| Compilation | `./gradlew compileReleaseKotlin --no-daemon` for all affected modules |

---

## 13. Risk Matrix

| # | Risk | Impact | Likelihood | Mitigation |
|---|------|--------|------------|------------|
| R-1 | `tdl-coroutines` v8.0.0 API breaking changes | High | Medium | Use named arguments; wrap in transport adapter layer |
| R-2 | TDLib FLOOD_WAIT blocking sync for minutes | Medium | High | Proper per-request timeout + backoff + jitter |
| R-3 | Large chats (10K+ messages) causing OOM | High | Medium | Streaming batch processing via ChannelSyncBuffer |
| R-4 | TDLib DB corruption on crash | Medium | Low | Use separate DB directory; handle gracefully |
| R-5 | Structured bundles producing incorrect metadata | Medium | Low | Existing bundle tests + golden file tests |
| R-6 | Fields like videoCodec/audioCodec unavailable from TDLib | Low | Certain | Accept gap — fill at playback time via ExoPlayer probe |
| R-7 | Rate limiting differences between Telegram accounts | Medium | Medium | Conservative default rate limits with per-account tuning |
| R-8 | Low library adoption (3 GitHub stars) | Low | Certain | Library is thin wrapper; easy to replace if abandoned |

---

## Appendix A: `tdl-coroutines` API Reference

### Key Methods Used in FishIT Pipeline

| Method | Return Type | Purpose |
|--------|-------------|---------|
| `TdlClient.create()` | `TdlClient` | Create new client with unique clientId |
| `client.setTdlibParameters(...)` | `TdlResult<Ok>` | Initialize TDLib with API credentials |
| `client.getChats(chatList, limit)` | `TdlResult<Chats>` | List user's chats |
| `client.getChat(chatId)` | `TdlResult<Chat>` | Get chat details |
| `client.getChatHistory(chatId, fromMessageId, offset, limit, onlyLocal)` | `TdlResult<Messages>` | Paginated message fetch |
| `client.getRemoteFile(remoteFileId, fileType)` | `TdlResult<File>` | Resolve remoteId → File |
| `client.downloadFile(fileId, priority, offset, limit, synchronous)` | `TdlResult<File>` | Start/track file download |
| `client.searchMessages(chatList, onlyInChannels, query, ...)` | `TdlResult<FoundMessages>` | Search messages |
| `client.close()` | `TdlResult<Ok>` | Close client, release resources |
| `client.authorizationStateUpdates` | `Flow<UpdateAuthorizationState>` | Auth state machine |
| `client.newMessageUpdates` | `Flow<UpdateNewMessage>` | Real-time new messages |
| `client.fileUpdates` | `Flow<UpdateFile>` | Download progress |
| `client.connectionStateUpdates` | `Flow<UpdateConnectionState>` | Network state |

### TdlResult Pattern

```kotlin
sealed class TdlResult<T> {
    data class Success<T>(val result: T) : TdlResult<T>()
    data class Failure<T>(val code: Int, val message: String) : TdlResult<T>()
}

// Extension: convert to Kotlin Result
fun <T> TdlResult<T>.toResult(): Result<T>

// Common error codes:
// 401 = Unauthorized
// 429 = Too Many Requests
// 420 = FLOOD_WAIT_X
// 400 = Bad Request
// 404 = Not Found
```

---

## Appendix B: Xtream Reference Pipeline

### Data Flow (Reference Implementation)

```
XtreamSyncConfig
  ↓
DefaultXtreamSyncService.sync()
  ├─ IncrementalSyncDecider.decide()
  │   ├─ Tier 1: ETag/304 check
  │   ├─ Tier 2: Item count comparison
  │   ├─ Tier 3: Timestamp filter
  │   └─ Tier 4: Fingerprint comparison
  ├─ PRODUCER: XtreamCatalogPipeline.scanCatalog()
  │   ├─ PhaseScanOrchestrator (parallel phases)
  │   │   ├─ Phase: Live channels → XtreamChannel.toRawMetadata()
  │   │   ├─ Phase: VOD movies → XtreamVodItem.toRawMetadata()
  │   │   └─ Phase: Series → XtreamSeriesItem.toRawMetadata()
  │   └─ Emit XtreamCatalogEvent.ItemDiscovered(raw)
  ├─ ChannelSyncBuffer(capacity=200)
  └─ CONSUMER: consumeBatched(50)
      ├─ XtreamLiveRepository.upsertAll()       → NX_Work (LIVE)
      └─ XtreamCatalogRepository.upsertAll()    → NX_Work (MOVIE, SERIES)
          ↓
      NX_Work + NX_WorkSourceRef + NX_WorkVariant
```

### Key Design Decisions to Mirror

| Decision | Xtream Implementation | Telegram Equivalent |
|----------|----------------------|---------------------|
| Single entry point | `sync(config): Flow<SyncStatus>` | Same |
| Producer/consumer | `ChannelSyncBuffer` | Same (shared class) |
| Batch persistence | `upsertAll(batch: List<RawMediaMetadata>)` | Same pattern |
| Checkpoint resumption | `SyncCheckpointStore` | Same (per-chat cursors) |
| Incremental detection | ETag + count + timestamp + fingerprint | Timestamp cursor + message probe |
| Progress reporting | `SyncStatus` sealed class | Same |
| Parallel phases | `async/awaitAll + Semaphore(3)` | `async` per chat (configurable concurrency) |
| Device profile | `DeviceProfileDetector` | Same (shared) |
| Performance metrics | `SyncPerfMetrics` | Same (shared) |

---

## Contract References

| Contract | Location | Relevance |
|----------|----------|-----------|
| Media Normalization | `contracts/MEDIA_NORMALIZATION_CONTRACT.md` | Pipeline output format, normalization chain |
| Telegram Parser | `contracts/TELEGRAM_PARSER_CONTRACT.md` | Message parsing rules, CLI reference implementation |
| Telegram Structured Bundles | `contracts/TELEGRAM_STRUCTURED_BUNDLES_CONTRACT.md` | Bundle detection, metadata extraction |
| Telegram ID Architecture | `contracts/TELEGRAM_ID_ARCHITECTURE_CONTRACT.md` | remoteId-first design, persistence rules |
| Catalog Sync Workers | `contracts/CATALOG_SYNC_WORKERS_CONTRACT_V2.md` | Orchestrator pattern, error handling, retry limits |
| NX SSOT | `contracts/NX_SSOT_CONTRACT.md` | NX entity schema, UI consumption rules |
| Glossary | `contracts/GLOSSARY_v2_naming_and_modules.md` | Naming conventions, module boundaries |
| Logging | `contracts/LOGGING_CONTRACT_V2.md` | UnifiedLog patterns |

---

> **Next Steps:** This contract must be reviewed and approved before implementation begins.
> Implementation should follow the phased plan in Section 12, with each phase producing a compilable, testable commit.
>
> **Canonical Source:** `docs/v2/TELEGRAM_PIPELINE_REDESIGN_CONTRACT.md`

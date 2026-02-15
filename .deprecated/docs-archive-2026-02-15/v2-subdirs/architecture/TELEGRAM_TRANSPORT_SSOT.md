# Telegram Transport SSOT (Single Source of Truth)

**Status:** ✅ FINAL  
**Date:** 2025-12-25  
**Branch:** `architecture/v2-bootstrap`

---

## 1. Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                    App Layer (app-v2)                           │
│         TelegramTransportModule provides DI bindings            │
└─────────────────────────────────────────────────────────────────┘
                              │
                    ┌─────────┴─────────┐
                    │   TelegramClient  │ (unified interface)
                    │     SINGLETON     │
                    └─────────┬─────────┘
                              │
        ┌─────────────────────┼─────────────────────┐
        │                     │                     │
        ▼                     ▼                     ▼
┌───────────────┐   ┌─────────────────┐   ┌─────────────────┐
│TelegramAuth   │   │TelegramHistory  │   │TelegramFile     │
│Client         │   │Client           │   │Client           │
└───────────────┘   └─────────────────┘   └─────────────────┘
        │                     │                     │
        └─────────────────────┼─────────────────────┘
                              │
                    ┌─────────┴─────────┐
                    │DefaultTelegram    │ (internal implementation)
                    │Client             │
                    └─────────┬─────────┘
                              │
                    ┌─────────┴─────────┐
                    │     TdlClient     │ (g000sha256 TDLib)
                    │  ONE instance     │
                    └───────────────────┘
```

---

## 2. Core Principle

> **One implementation, one TDLib instance, multiple typed interfaces.**

All Telegram transport operations flow through a **single `DefaultTelegramClient` instance** that implements all typed interfaces. This ensures:

- ✅ **Consistent state** across all operations
- ✅ **Single TDLib session** (no resource conflicts)
- ✅ **Predictable lifecycle** (one init, one teardown)
- ✅ **Testable** (mock one interface, all others use same mock)

---

## 3. Typed Interfaces

| Interface | Responsibility | Key Methods |
|-----------|----------------|-------------|
| `TelegramAuthClient` | Authentication, session state | `authState`, `ensureAuthorized()` |
| `TelegramHistoryClient` | Chat listing, message fetching | `getChats()`, `fetchHistory()` |
| `TelegramFileClient` | File downloads, resolution | `downloadFile()`, `resolveRemoteId()` |
| `TelegramThumbFetcher` | Thumbnail loading for Coil | `fetchThumbnail()`, `prefetch()` |

All four interfaces are implemented by `DefaultTelegramClient` (internal class).

---

## 4. DI Binding (SSOT Pattern)

```kotlin
// TelegramTransportModule.kt

@Provides @Singleton
fun provideTelegramClient(...): TelegramClient = DefaultTelegramClient(...)

@Provides @Singleton
fun provideTelegramAuthClient(client: TelegramClient): TelegramAuthClient = client

@Provides @Singleton
fun provideTelegramHistoryClient(client: TelegramClient): TelegramHistoryClient = client

@Provides @Singleton
fun provideTelegramFileClient(client: TelegramClient): TelegramFileClient = client

@Provides @Singleton
fun provideTelegramThumbFetcher(client: TelegramClient): TelegramThumbFetcher = client
```

**Key Point:** All typed interfaces return the **same instance** of `TelegramClient`.

---

## 5. Forbidden Patterns

| ❌ Forbidden | ✅ Correct |
|-------------|-----------|
| Creating additional `TdlClient` instances | Use injected `TelegramClient` |
| Accessing `TdApi.*` types outside transport | Use typed interfaces |
| Creating parallel transport wrappers | Use single `TelegramClient` |
| Importing `internal/DefaultTelegramClient` | Use public interfaces only |
| Calling TDLib directly from pipelines | Use `TelegramPipelineAdapter` |

---

## 6. Consumer Guidelines

### For Pipelines (`pipeline/telegram`)

```kotlin
// ✅ Correct: Use TelegramPipelineAdapter
class TelegramCatalogPipelineImpl @Inject constructor(
    private val adapter: TelegramPipelineAdapter
)

// ❌ Wrong: Direct transport access
class TelegramCatalogPipelineImpl @Inject constructor(
    private val authClient: TelegramAuthClient  // NO!
)
```

### For Playback (`playback/telegram`)

```kotlin
// ✅ Correct: Use TelegramFileClient
class TelegramFileDataSource(
    private val fileClient: TelegramFileClient
)

// ❌ Wrong: Full TelegramClient
class TelegramFileDataSource(
    private val client: TelegramClient  // Too broad
)
```

### For Imaging (`app-v2/di`)

```kotlin
// ✅ Correct: Use TelegramThumbFetcher
class TelegramThumbFetcherImpl(
    private val transportFetcher: TelegramThumbFetcher
)
```

---

## 7. Deprecated Artifacts

| Artifact | Status | Migration |
|----------|--------|-----------|
| `TelegramTransportClient` (interface) | Deprecated | Use typed interfaces |
| `DefaultTelegramTransportClient` | Deleted | N/A |
| `TelegramClientFactory.fromExistingSession()` | Deleted | Use `createUnifiedClient()` |

---

## 8. Testing Strategy

```kotlin
// Unit tests: Mock the typed interface you need
val mockFileClient = mockk<TelegramFileClient>()

// Integration tests: Use TelegramClientFactory
val client = TelegramClientFactory.createUnifiedClient(config)
```

---

## 9. References

- [AGENTS.md Section 4](../../../AGENTS.md) — Player Layer Isolation
- [MEDIA_NORMALIZATION_CONTRACT.md](../../MEDIA_NORMALIZATION_CONTRACT.md) — Pipeline contracts
- [TELEGRAM_ID_ARCHITECTURE_CONTRACT.md](../../../contracts/TELEGRAM_ID_ARCHITECTURE_CONTRACT.md) — ID handling

---

## 10. Change Log

| Date | Change |
|------|--------|
| 2025-12-25 | Initial SSOT finalization |

---

**This document is BINDING.** Any changes require PR review and AGENTS.md update.

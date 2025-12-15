# Imaging System Architecture (v2)

**Status:** ✅ **FROZEN** — Canonical imaging architecture, no alternatives allowed

**Authority:** This document defines the single source of truth for all image handling in FishIT-Player v2.

---

## Overview

The v2 imaging system is built around **`ImageRef`** as the universal image abstraction. All pipelines produce `ImageRef`, all UI consumes `ImageRef`, and all image loading goes through the centralized `GlobalImageLoader`.

**Core Principle:** No module-specific image models, no UI image states, no shadow image types.

---

## Architecture Layers

```
┌─────────────────────────────────────────────────────────────┐
│  UI Layer (feature/*)                                       │
│    - Receives ImageRef from repositories                    │
│    - Passes ImageRef to FishImage composable                │
│    - NO UiImageRef, NO UiThumbState                         │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│  Domain/Data Layer (domain/*, infra/data-*)                 │
│    - Stores ImageRef in entities (via ImageRefConverter)    │
│    - Exposes ImageRef in domain models                      │
│    - NO transformation to UI-specific types                 │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│  Core Imaging (core:ui-imaging)                             │
│    - GlobalImageLoader (Coil 3 singleton)                   │
│    - ImageRefFetcher + ImageRefKeyer                        │
│    - TelegramThumbFetcher interface                         │
│    - FishImage composable (wrapper for AsyncImage)          │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│  Infrastructure (infra/*)                                    │
│    - TelegramThumbFetcher implementation (via TDLib)        │
│    - HTTP fetching (delegated to Coil OkHttp)               │
│    - NO ImageRef transformation                             │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│  Pipeline Layer (pipeline/*)                                │
│    - Produces ImageRef from raw sources                     │
│    - TDLib minithumbnails → ImageRef.InlineBytes            │
│    - Xtream URLs → ImageRef.Http                            │
│    - NO Coil dependencies                                   │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│  Core Model (core:model)                                    │
│    - ImageRef sealed interface (canonical definition)       │
│    - NO module-specific variants                            │
└─────────────────────────────────────────────────────────────┘
```

---

## ImageRef Variants

### 1. `ImageRef.Http`

**Purpose:** Web-accessible images (Xtream, TMDB, any HTTP/HTTPS URL)

**Fields:**
- `url: String` — Full HTTP/HTTPS URL
- `headers: Map<String, String>` — Optional auth headers (e.g., Xtream cookies)
- `preferredWidth/Height: Int?` — Size hints for fetcher

**Fetcher:** Standard Coil `HttpUriFetcher` (uses shared OkHttpClient)

**Example:**
```kotlin
ImageRef.Http(
    url = "https://image.tmdb.org/t/p/w500/abc123.jpg",
    preferredWidth = 500
)
```

### 2. `ImageRef.TelegramThumb`

**Purpose:** Telegram thumbnails (videos, photos, documents)

**Fields:**
- `fileId: Int` — TDLib file ID (current session)
- `uniqueId: String` — Cross-session stable identifier
- `chatId: Long?` — Chat context (for logs/analytics)
- `messageId: Long?` — Message context
- `preferredWidth/Height: Int?` — Size hints

**Fetcher:** `TelegramThumbFetcher` (interface in `core:ui-imaging`, impl in `infra/*`)

**Resolution Flow:**
1. Coil calls `TelegramThumbFetcher.fetch()`
2. Fetcher uses `TelegramTransportClient` to resolve TDLib file
3. Returns local path if downloaded, or triggers download
4. Coil loads from local path

**Example:**
```kotlin
ImageRef.TelegramThumb(
    fileId = 12345,
    uniqueId = "AgADbQADr60xG...",
    chatId = -1001234567890,
    messageId = 42
)
```

### 3. `ImageRef.LocalFile`

**Purpose:** Already-downloaded images, cached files, user uploads

**Fields:**
- `path: String` — Absolute file path
- `preferredWidth/Height: Int?` — Size hints

**Fetcher:** Standard Coil `FileUriFetcher`

**Example:**
```kotlin
ImageRef.LocalFile(
    path = "/data/data/com.fishit.player/cache/telegram/thumb_12345.jpg"
)
```

### 4. `ImageRef.InlineBytes`

**Purpose:** Inline tiny images (TDLib minithumbnails, blur placeholders)

**Fields:**
- `bytes: ByteArray` — Raw image data (typically JPEG)
- `mimeType: String?` — MIME type hint (default "image/jpeg")
- `preferredWidth/Height: Int?` — Size hints

**Fetcher:** `ImageRefFetcher` (decodes bytes in-memory)

**TDLib Minithumbnail Strategy:**
- TDLib provides ~40x40px inline JPEGs in message metadata
- Pipeline converts to `ImageRef.InlineBytes` during parsing
- Coil decodes instantly without network request
- Used as placeholder until full `TelegramThumb` loads

**Example:**
```kotlin
ImageRef.InlineBytes(
    bytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), ...), // JPEG magic + data
    mimeType = "image/jpeg",
    preferredWidth = 40,
    preferredHeight = 40
)
```

---

## Tiered Loading Strategy

For Telegram media, the pipeline produces BOTH a minithumbnail and a full thumbnail:

```kotlin
TelegramMediaItem(
    miniThumb = ImageRef.InlineBytes(bytes = miniBytes),    // ~40x40 inline
    thumbnail = ImageRef.TelegramThumb(fileId = thumbId),   // Full quality
    ...
)
```

UI loading flow:

1. **Instant:** Coil loads `miniThumb` (InlineBytes) synchronously → blur placeholder appears
2. **Background:** Coil fetches `thumbnail` (TelegramThumb) → replaces placeholder when ready
3. **Crossfade:** Smooth transition from miniThumb → full thumbnail (configurable)

This provides instant visual feedback without blocking the UI thread.

---

## Persistence

**ObjectBox Integration:**

`core:persistence` provides `ImageRefConverter` for storing `ImageRef` in ObjectBox entities:

```kotlin
@Entity
data class ObxTelegramMedia(
    @Id var id: Long = 0,
    @Convert(converter = ImageRefConverter::class, dbType = String::class)
    var thumbnail: ImageRef? = null,
    ...
)
```

**Serialization Format:**

ImageRef is serialized as JSON with a `type` discriminator:

```json
{
  "type": "Http",
  "url": "https://example.com/image.jpg",
  "headers": {},
  "preferredWidth": 500,
  "preferredHeight": null
}
```

```json
{
  "type": "InlineBytes",
  "bytes": "base64EncodedData==",
  "mimeType": "image/jpeg",
  "preferredWidth": 40,
  "preferredHeight": 40
}
```

**Why JSON?**
- Type-safe deserialization
- Forward-compatible (new variants won't break old DB)
- Human-readable for debugging

---

## Why No UI Image State?

**Forbidden Patterns:**

```kotlin
// ❌ NEVER introduce these
sealed class UiImageRef { ... }
data class UiThumbState(val loading: Boolean, val url: String?) { ... }
data class UiImage(val imageRef: ImageRef?, val isLoading: Boolean) { ... }
```

**Reasons:**

1. **Coil handles state internally** — `AsyncImagePainter.State` already provides Loading/Success/Error
2. **ImageRef is UI-ready** — No transformation needed
3. **No business logic in images** — Images are pure data, not stateful entities
4. **Simplicity** — One model for all layers = less code, fewer bugs

**Correct Pattern:**

```kotlin
@Composable
fun MediaCard(item: DomainMediaItem) {
    FishImage(
        imageRef = item.poster,  // ImageRef from domain, no transformation
        contentDescription = item.title,
        modifier = Modifier.size(200.dp)
    )
}
```

Coil's `AsyncImage` (wrapped by `FishImage`) handles all state transitions automatically.

---

## Module Boundaries (ENFORCED)

### ✅ Allowed

| Module | Can Import | Can Produce/Consume |
|--------|-----------|---------------------|
| `core:model` | Nothing imaging-specific | Defines `ImageRef` |
| `pipeline/*` | `core:model` | Produces `ImageRef` |
| `infra/data-*` | `core:model`, `core:persistence` | Stores `ImageRef` |
| `domain/*` | `core:model` | Exposes `ImageRef` in domain models |
| `core:ui-imaging` | `core:model`, Coil 3 | Fetchers, `GlobalImageLoader`, `FishImage` |
| `feature/*` (UI) | `core:ui-imaging`, `core:model` | Consumes `ImageRef` via `FishImage` |

### ❌ Forbidden

| Module | Must NOT Import | Reason |
|--------|----------------|--------|
| `pipeline/*` | Coil, `core:ui-imaging` | Pipelines are source-agnostic, no UI deps |
| `infra/transport-*` | `ImageRef`, Coil | Transport provides raw data, not images |
| `feature/*` (UI) | Raw TDLib DTOs, `TdApi.*` | UI must use `ImageRef` only |
| **Any module** | Create `UiImageRef`, `UiThumbState`, etc. | Shadow types forbidden |

**Guardrails:**

`scripts/ci/check-duplicate-contracts.sh` enforces:
- No `Ui*Image*` types
- No `Ui*Thumb*` types
- No `*ThumbState` types
- No duplicate `ImageRef` definitions

---

## GlobalImageLoader Configuration

**Location:** `core:ui-imaging/GlobalImageLoader.kt`

**Features:**

- **Singleton:** One `ImageLoader` instance per app
- **Memory Cache:** 25% of available heap (configurable)
- **Disk Cache:** 100 MB on disk (expandable for production)
- **Crossfade:** 300ms default (disable for better perf on low-end devices)
- **Placeholders:** Support for `ImageRef.InlineBytes` as blur placeholders
- **Error Handling:** Retry policy, fallback drawables

**Dependency Injection:**

```kotlin
// app-v2/di/ImagingModule.kt
@Module
@InstallIn(SingletonComponent::class)
object ImagingModule {
    @Provides
    @Singleton
    fun provideImageLoader(context: Context): ImageLoader {
        return GlobalImageLoader.create(context)
    }

    @Provides
    @Singleton
    fun provideTelegramThumbFetcher(
        transportClient: TelegramTransportClient
    ): TelegramThumbFetcher {
        return TelegramThumbFetcherImpl(transportClient)
    }
}
```

---

## Testing Guidelines

### Unit Tests

**ImageRef creation:**

```kotlin
@Test
fun `pipeline produces ImageRef from TDLib minithumbnail`() {
    val miniBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), ...)
    val imageRef = ImageRef.InlineBytes(
        bytes = miniBytes,
        mimeType = "image/jpeg",
        preferredWidth = 40,
        preferredHeight = 40
    )
    
    assertEquals(40, imageRef.preferredWidth)
    assertContentEquals(miniBytes, imageRef.bytes)
}
```

**Persistence:**

```kotlin
@Test
fun `ImageRefConverter serializes and deserializes TelegramThumb`() {
    val original = ImageRef.TelegramThumb(
        fileId = 12345,
        uniqueId = "AgADbQADr60xG...",
        chatId = -1001234567890
    )
    
    val json = converter.convertToDatabaseValue(original)
    val restored = converter.convertToEntityProperty(json)
    
    assertEquals(original, restored)
}
```

### Integration Tests

**Coil Fetcher:**

```kotlin
@Test
fun `TelegramThumbFetcher resolves file from TDLib`() = runTest {
    val imageRef = ImageRef.TelegramThumb(fileId = 123, uniqueId = "abc")
    val options = Options(context, size = Size(500, 500))
    
    val result = fetcher.fetch(imageRef, options)
    
    assertTrue(result is SourceFetchResult)
    assertTrue(result.source.file().exists())
}
```

---

## Migration from v1

**v1 Pattern (deprecated):**

```kotlin
// Old v1 code
val imageUrl = message.video?.thumbnail?.remote?.id?.let {
    "https://api.telegram.org/file/bot$token/$it"
}

Coil.imageLoader(context).enqueue(
    ImageRequest.Builder(context)
        .data(imageUrl)
        .target(imageView)
        .build()
)
```

**v2 Pattern (correct):**

```kotlin
// New v2 code
val imageRef = message.thumbnail?.let { thumb ->
    ImageRef.TelegramThumb(
        fileId = thumb.id,
        uniqueId = thumb.uniqueId,
        chatId = message.chatId,
        messageId = message.id
    )
}

FishImage(
    imageRef = imageRef,
    contentDescription = "Thumbnail",
    modifier = Modifier.size(200.dp)
)
```

**Key Changes:**

1. No manual URL construction
2. No direct Coil API calls in UI
3. Minithumbnails extracted as `InlineBytes`
4. Context (chatId, messageId) preserved for debugging

---

## Glossary

| Term | Definition |
|------|------------|
| **ImageRef** | Sealed interface in `core:model` representing any image source |
| **Minithumbnail** | TDLib's ~40x40px inline JPEG preview (stored as `InlineBytes`) |
| **TelegramThumb** | Full-quality Telegram thumbnail (fetched via TDLib) |
| **Fetcher** | Coil 3 component that resolves `ImageRef` to image data |
| **GlobalImageLoader** | Singleton `ImageLoader` configured for FishIT-Player |
| **FishImage** | Composable wrapper for Coil's `AsyncImage` with `ImageRef` support |
| **Shadow Image Type** | Forbidden: Any UI-specific image model (e.g., `UiImageRef`) |

---

## Contracts & Invariants

### Hard Rules

1. **Single ImageRef Definition:** Only `core:model/ImageRef.kt` may define `sealed interface ImageRef`
2. **No Shadow Types:** No `UiImageRef`, `UiThumbState`, `DomainImage`, etc.
3. **Pipeline Isolation:** Pipelines must NOT import Coil or `core:ui-imaging`
4. **Transport Isolation:** Transport must NOT import `ImageRef`
5. **UI Simplicity:** UI receives `ImageRef`, passes to `FishImage`, no transformation

### Guardrail Enforcement

`scripts/ci/check-duplicate-contracts.sh` checks:

```bash
# Forbidden patterns:
Ui*Image*
Ui*Thumb*
*ThumbState
sealed interface ImageRef  # (outside core:model)
```

### Violation Examples

```kotlin
// ❌ VIOLATION: Shadow UI type
data class UiThumbnail(val url: String, val loading: Boolean)

// ❌ VIOLATION: Duplicate ImageRef
package com.fishit.player.feature.detail
sealed class ImageRef { ... }

// ❌ VIOLATION: Coil in pipeline
// pipeline/telegram/TelegramMediaParser.kt
import coil3.ImageLoader  // FORBIDDEN

// ❌ VIOLATION: ImageRef in transport
// infra/transport-telegram/TelegramTransportClient.kt
import com.fishit.player.core.model.ImageRef  // FORBIDDEN
```

---

## Future Considerations (NOT Planned)

These features are **explicitly NOT part of v2**:

- ❌ Progressive image loading (Coil handles this)
- ❌ Image caching strategies per source (one global cache)
- ❌ Custom placeholder strategies (use `InlineBytes`)
- ❌ Image transformations in `ImageRef` (Coil modifiers handle this)
- ❌ UI-specific image states (Coil's `AsyncImagePainter.State` is sufficient)

If any of these become necessary, they must be implemented **without introducing shadow types**.

---

## Summary

**The v2 imaging system is:**

- ✅ **Unified:** One `ImageRef` abstraction for all sources
- ✅ **Layered:** Clean separation (pipeline → model → imaging → UI)
- ✅ **Frozen:** No module-specific variants, no shadow types
- ✅ **Tested:** Covered by unit tests and guardrails
- ✅ **Simple:** No UI image state, no transformation layers

**Key Takeaway:**

> **ImageRef is the only image model in v2. Anything else is a violation.**

---

**Last Updated:** 2025-12-15  
**Frozen By:** Architecture freeze task (v2-bootstrap)  
**Authoritative Source:** This document + `core:model/ImageRef.kt` + guardrail scripts

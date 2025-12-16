# Telegram ID Architecture Contract

**Version:** 1.0  
**Status:** Binding  
**Scope:** All modules handling TDLib file references (transport, pipeline, persistence, playback, imaging)

---

## 1. Executive Summary

This contract defines the **canonical ID strategy** for TDLib file handling in FishIT Player v2.

### Core Principle: remoteId-First Design

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        OPTIMIZED ARCHITECTURE                           │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│   PERSISTENCE (ObjectBox)           RUNTIME                             │
│   ──────────────────────           ───────                              │
│                                                                         │
│   chatId (Long)     ─────────►  History-API, Chat-Lookup                │
│   messageId (Long)  ─────────►  Message-Reload, Pagination              │
│   remoteId (String) ─────────►  getRemoteFile(remoteId)                 │
│                                        │                                │
│                                        ▼                                │
│                                   TgFile { id: Int }                    │
│                                        │                                │
│                                        ▼                                │
│                                   downloadFile(fileId)                  │
│                                        │                                │
│                                        ▼                                │
│                                   localPath (TDLib Cache)               │
│                                        │                                │
│                                        ▼                                │
│   Coil Memory Cache  ◄─────────  Decode & Display                       │
│   (NO Disk Cache!)                                                      │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 2. TDLib ID Types Reference

| ID Type | Type | Stability | Purpose | Persist? |
|---------|------|-----------|---------|----------|
| `fileId` | `Int` | **Session-local** | TDLib download/upload operations | ❌ NO |
| `remoteId` | `String` | **Cross-session stable** | Resolves to fileId via `getRemoteFile()` | ✅ YES |
| `uniqueId` | `String` | **Cross-session stable** | ❌ NO API to resolve back to file | ❌ NO |
| `chatId` | `Long` | **Stable** | Identifies chat for history API | ✅ YES |
| `messageId` | `Long` | **Stable** | Identifies message within chat | ✅ YES |

### Why NOT persist `fileId`?

`fileId` is a **session-local integer** assigned by TDLib. It can become stale when:
- TDLib database is cleared
- User logs out and back in
- Cache eviction occurs
- App reinstall

Persisting `fileId` leads to "ghost references" that fail silently.

### Why NOT persist `uniqueId`?

`uniqueId` has **no TDLib API** to resolve it back to a file. While stable, it's useless:
- Cannot call `getFileByUniqueId()` – no such API exists
- Only extractable from an existing file object
- `remoteId` provides the same stability AND can be resolved

---

## 3. Binding Rules

### 3.1. Persistence Layer (ObjectBox)

**MUST persist:**
- `chatId: Long` – for chat lookups and history
- `messageId: Long` – for message reload and pagination
- `remoteId: String` – for all file references (video, thumbnail, poster)

**MUST NOT persist:**
- `fileId: Int` – volatile, session-local
- `uniqueId: String` – redundant, no resolution API

**Entity Example:**
```kotlin
@Entity
data class ObxTelegramMessage(
    @Id var id: Long = 0,
    @Index var chatId: Long = 0,
    @Index var messageId: Long = 0,
    
    // File reference - ONLY remoteId
    @Index var remoteId: String? = null,
    
    // Thumbnail reference - ONLY remoteId
    var thumbRemoteId: String? = null,
    
    // Poster reference - ONLY remoteId  
    var posterRemoteId: String? = null,
    
    // ... other metadata fields (no fileId anywhere)
)
```

### 3.2. Runtime Resolution

All file operations MUST resolve `fileId` at runtime via:

```kotlin
suspend fun resolveFileId(remoteId: String): Int? {
    val file = client.getRemoteFile(remoteId)
    return file?.id
}
```

**Flow:**
1. UI requests image/video for item
2. Lookup `remoteId` from ObjectBox
3. Call `getRemoteFile(remoteId)` → `TgFile { id: Int }`
4. Use `fileId` for `downloadFile()` or `startDownload()`
5. TDLib downloads to its cache → `localPath`
6. Return `localPath` to caller

### 3.3. Image Loading (Coil)

**Cache Key:** Use `remoteId` as the stable cache key.

```kotlin
class ImageRefKeyer : Keyer<ImageRef> {
    override fun key(data: ImageRef, options: Options): String {
        return when (data) {
            is ImageRef.TelegramThumb -> "tg:${data.remoteId}"
            // ... other variants
        }
    }
}
```

**Disk Cache:** DISABLED for Telegram images. TDLib already caches files.

**Memory Cache:** ENABLED. Decode once, display multiple times.

### 3.4. ImageRef.TelegramThumb

**Before (wrong):**
```kotlin
data class TelegramThumb(
    val fileId: Int,      // ❌ volatile
    val uniqueId: String, // ❌ redundant
    // ...
)
```

**After (correct):**
```kotlin
data class TelegramThumb(
    val remoteId: String,           // ✅ stable, resolvable
    val chatId: Long? = null,       // optional context
    val messageId: Long? = null,    // optional context
    // ...
)
```

### 3.5. Transport Layer (TgThumbnailRef)

**Before (wrong):**
```kotlin
data class TgThumbnailRef(
    val fileId: Int,      // ❌ volatile
    val remoteId: String, // ✅ but fileId shouldn't be here
    // ...
)
```

**After (correct):**
```kotlin
data class TgThumbnailRef(
    val remoteId: String, // ✅ only stable ID
    val width: Int,
    val height: Int,
    val format: String = "jpeg"
)
```

Fetcher resolves `fileId` internally via `getRemoteFile(remoteId)`.

---

## 4. Migration Strategy

### 4.1. ObjectBox Schema Change

Since `fileId` and `uniqueId` are removed, existing data becomes invalid.

**Approach:** Full resync on upgrade.

1. Detect schema version change
2. Clear `ObxTelegramMessage` box
3. Trigger full chat rescan
4. New data uses `remoteId` only

### 4.2. ImageRef URI Format Change

**Before:** `tg://thumb/<fileId>/<uniqueId>?chatId=...&messageId=...`

**After:** `tg://thumb/<remoteId>?chatId=...&messageId=...`

### 4.3. Backward Compatibility

No backward compatibility for persisted `fileId`/`uniqueId`. Full resync is required.
This is acceptable because:
- Telegram pipeline was non-functional in v1 anyway
- Clean slate for v2 architecture

---

## 5. Coil Cache Configuration

### 5.1. Memory Cache

```kotlin
ImageLoader.Builder(context)
    .memoryCache {
        MemoryCache.Builder()
            .maxSizePercent(context, 0.25) // 25% of heap
            .build()
    }
```

### 5.2. Disk Cache for Telegram

**DISABLED** – TDLib already maintains its own file cache.

```kotlin
// When loading Telegram images, skip Coil disk cache
ImageLoader.Builder(context)
    .diskCache(null) // For Telegram-only loader
    // OR: Configure per-request via ImageRequest.Builder
```

Alternative: Separate ImageLoader for Telegram with disk cache disabled.

### 5.3. Rationale

| Layer | What it caches | Why |
|-------|----------------|-----|
| TDLib | Downloaded files on disk | Native C++ cache, managed by TDLib |
| Coil Memory | Decoded Bitmaps in RAM | Fast display without re-decode |
| Coil Disk | ❌ DISABLED for Telegram | Would duplicate TDLib's disk cache |

---

## 6. Implementation Checklist

### Phase 1: Contracts & Documentation
- [x] Create this contract (`TELEGRAM_ID_ARCHITECTURE_CONTRACT.md`)
- [ ] Update `TELEGRAM_PARSER_CONTRACT.md` to remove `fileId`/`uniqueId` requirements
- [ ] Update `MEDIA_NORMALIZATION_AND_UNIFICATION.md`

### Phase 2: Core Model
- [ ] Update `ImageRef.TelegramThumb` to use `remoteId` only
- [ ] Update `ImageRefKeyer` to use `remoteId`
- [ ] Update URI parsing in `ImageRef.fromString()`

### Phase 3: Persistence
- [ ] Update `ObxTelegramMessage` to remove `fileId`, `fileUniqueId`, `thumbFileId`
- [ ] Add `thumbRemoteId` field
- [ ] Update converters if needed

### Phase 4: Transport
- [ ] Update `TgThumbnailRef` to use `remoteId` only
- [ ] Update `TelegramThumbFetcherImpl` to always resolve via `getRemoteFile()`
- [ ] Remove `fileId` fallback logic

### Phase 5: Pipeline & Mapping
- [ ] Update `TelegramMediaItem.toRawMediaMetadata()`
- [ ] Update `toImageRef()` extension functions
- [ ] Update playback URI format

### Phase 6: Coil Configuration
- [ ] Configure separate ImageLoader for Telegram (no disk cache)
- [ ] OR: Add request interceptor to skip disk cache for `tg://` URIs

---

## 7. Verification

After implementation, verify:

1. **No `fileId` in ObjectBox:** `grep -r "fileId" core/persistence/` should not find persistence fields
2. **No `uniqueId` in ObjectBox:** Same check
3. **ImageRefKeyer uses remoteId:** Cache key is `tg:<remoteId>`
4. **TelegramThumbFetcher resolves at runtime:** Always calls `getRemoteFile()` first
5. **Coil disk cache disabled:** Telegram images not duplicated on disk

---

## 8. References

- `infra/transport-telegram/TelegramFileClient.kt` – `resolveRemoteId()` API
- `infra/transport-telegram/TelegramThumbFetcher.kt` – Interface definition
- `core/model/ImageRef.kt` – Image reference types
- `core/persistence/obx/ObxEntities.kt` – ObjectBox entities
- TDLib documentation: File ID types and stability guarantees

# Trickplay Integration Design Document

**Version:** 1.0  
**Status:** Design Proposal  
**Scope:** Seek preview / thumbnail scrubbing for Xtream VOD and Telegram VOD content  
**Applies To:** SIP Internal Player (Phase 5+ architecture)

---

## Table of Contents

1. [Overview](#1-overview)
2. [Trickplay Data Requirements](#2-trickplay-data-requirements)
3. [Existing Trickplay Infrastructure](#3-existing-trickplay-infrastructure)
4. [Telegram VOD Trickplay Design](#4-telegram-vod-trickplay-design)
5. [Xtream VOD Trickplay Design](#5-xtream-vod-trickplay-design)
6. [SIP Player Integration Layer](#6-sip-player-integration-layer)
7. [Non-Goals & Constraints](#7-non-goals--constraints)
8. [Implementation Roadmap](#8-implementation-roadmap)
9. [References](#9-references)

---

## 1. Overview

### 1.1 What is Trickplay?

Trickplay (also called "seek preview" or "thumbnail scrubbing") is a media player feature that displays preview images while the user scrubs through the timeline or uses fast-forward/rewind. This provides visual feedback about where in the content the user is navigating to before they commit to the seek position.

**Types of Trickplay:**
- **Static Preview:** A single image (e.g., poster/thumbnail) shown regardless of seek position
- **Position-Based Preview:** Different images shown depending on the seek target position (e.g., thumbnails extracted at regular intervals)

### 1.2 Why LIVE is Excluded

LIVE content (live TV streams, real-time broadcasts) does not support Trickplay because:

1. **No Defined Duration:** LIVE streams have no fixed duration to seek within
2. **No Archive/DVR:** Unless the stream has timeshift/DVR capability, there is no "past" to seek to
3. **Real-Time Nature:** The current position is always "now" - there is no future content to preview

**Contract Rule:**
> `PlaybackType.LIVE` → Trickplay disabled (`supportsTrickplay = false`)

### 1.3 Why Xtream VOD and Telegram VOD Are Eligible

Both Xtream VOD and Telegram VOD are **file-based streams** that:

1. Have a defined duration
2. Support seeking within the content
3. Have associated metadata with poster/cover images
4. Are retrieved via URL (HTTP for Xtream, `tg://` for Telegram)

Both sources can provide at minimum a **static preview image** and potentially support **position-based preview** with additional infrastructure.

### 1.4 Design Goal

The SIP Internal Player should treat Trickplay **uniformly** at the UI/Session level for **any VOD source**:

- The player only sees "VOD with TrickplayProvider"
- Differences between Xtream and Telegram are handled at the **data provider layer**
- No duplication of player logic for different sources

---

## 2. Trickplay Data Requirements

### 2.1 What the Player Needs

From the SIP Internal Player's perspective, Trickplay requires:

1. **Capability Flag:** A way to know if Trickplay is supported for the current media
2. **Preview Image Provider:** A way to request a preview image for a given seek position
3. **Display State:** UI state fields for showing/hiding the preview and its position

### 2.2 Minimal Trickplay (Static Preview)

The simplest form of Trickplay uses a **single, time-independent image** shown while scrubbing:

| Requirement | Description |
|-------------|-------------|
| **Image Source** | Existing poster/cover/thumbnail from content metadata |
| **When Shown** | During scrub gesture or seekbar interaction |
| **Position Text** | Target time shown alongside the static image |
| **Implementation Cost** | Low - reuses existing artwork infrastructure |

**Pros:**
- No additional infrastructure needed
- Works with existing image caching
- Provides some visual context during seeking

**Cons:**
- Does not show what's at the target position
- Less useful for long-form content
- Static image may not represent content well

### 2.3 Advanced Trickplay (Position-Based Preview)

True Trickplay shows **different images based on the seek position**:

| Requirement | Description |
|-------------|-------------|
| **Image Source** | Thumbnails extracted from video at intervals (e.g., every 10 seconds) |
| **When Shown** | During scrub gesture, image updates as position changes |
| **Resolution** | Lower resolution (e.g., 160x90 or 320x180) to reduce storage/bandwidth |
| **Implementation Cost** | High - requires thumbnail generation pipeline |

**Approaches:**

1. **Server-Side Sprite Sheets:** Some services provide pre-generated thumbnail grids (not available for Xtream/Telegram)
2. **On-Device Extraction:** Extract frames from the video file using MediaMetadataRetriever or FFmpeg
3. **Background Generation:** Pre-generate thumbnails during initial playback or download

**Pros:**
- True position-based preview
- Better user experience for long content

**Cons:**
- Significant infrastructure investment
- Storage overhead for cached thumbnails
- Performance impact during extraction
- Not all files support random access for extraction

---

## 3. Existing Trickplay Infrastructure

### 3.1 Current State Model

The SIP Internal Player already has Trickplay state fields in `InternalPlayerUiState`:

```kotlin
// From player/internal/state/InternalPlayerState.kt - InternalPlayerUiState data class

// Phase 5 Group 3: Trickplay State
val trickplayActive: Boolean = false     // Whether trickplay mode is active
val trickplaySpeed: Float = 1f           // Speed multiplier (1.0, 2.0, 3.0, 5.0, -2.0, etc.)
val seekPreviewVisible: Boolean = false  // Whether seek preview overlay is shown
val seekPreviewTargetMs: Long? = null    // Target position for seek preview
```

### 3.2 Existing UI Components

The player already renders:

**TrickplayIndicator** (in `InternalPlayerControls.kt`):
- Shows speed and direction: "2x ►►" (forward) or "◀◀ 2x" (rewind)
- AnimatedVisibility fade animations

**SeekPreviewOverlay** (in `InternalPlayerControls.kt`):
- Shows target position (large text)
- Shows delta from current position ("+10s" or "-10s")
- Shows percentage progress

### 3.3 Existing Controller Callbacks

```kotlin
// From InternalPlayerController
val onStartTrickplay: (direction: Int) -> Unit = {}
val onStopTrickplay: (applyPosition: Boolean) -> Unit = {}
val onCycleTrickplaySpeed: () -> Unit = {}
val onStepSeek: (deltaMs: Long) -> Unit = {}
```

### 3.4 What is Missing for True Trickplay

The current implementation shows **position text and speed indicators** but does NOT show **preview images**. To add visual thumbnails:

1. Need a `TrickplayProvider` interface to fetch images by position
2. Need UI components to display the preview image
3. Need integration with image loading infrastructure (Coil)
4. Need source-specific implementations (Telegram, Xtream)

---

## 4. Telegram VOD Trickplay Design

### 4.1 Available Image Sources

From `TelegramItem` (per `TELEGRAM_PARSER_CONTRACT.md`):

| Field | Type | Description | Aspect Ratio |
|-------|------|-------------|--------------|
| `posterRef` | `TelegramImageRef?` | Portrait image from photo message | width/height ≤ 0.85 |
| `backdropRef` | `TelegramImageRef?` | Landscape image from photo message | width/height ≥ 1.6 |
| `videoRef.thumbnail` | (via TDLib) | Video's own thumbnail (if present) | Varies |

**`TelegramImageRef` structure:**
```kotlin
data class TelegramImageRef(
    val remoteId: String,      // REQUIRED - stable file identifier
    val uniqueId: String,      // REQUIRED - stable unique identifier
    val fileId: Int? = null,   // OPTIONAL - volatile cache ID
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
)
```

### 4.2 Minimal Trickplay for Telegram

Use `posterRef` or `backdropRef` as a **static preview image** shown near the scrub position:

**Implementation Strategy:**

1. When building `PlaybackContext` for Telegram VOD, include the poster/backdrop URL
2. Create `TelegramTrickplayProvider` that returns the poster image for any position
3. Load image via `TelegramFileLoader` (existing infrastructure)
4. Display in `SeekPreviewOverlay` above the position text

**Pseudo-code:**
```kotlin
interface TrickplayProvider {
    suspend fun getPreviewImage(positionMs: Long): ImageDescriptor?
    fun supportsTrickplay(): Boolean
}

class TelegramTrickplayProvider(
    private val item: TelegramItem,
    private val fileLoader: TelegramFileLoader,
) : TrickplayProvider {
    
    override fun supportsTrickplay(): Boolean = 
        item.posterRef != null || item.backdropRef != null
    
    override suspend fun getPreviewImage(positionMs: Long): ImageDescriptor? {
        // Use posterRef preferentially (portrait fits preview better)
        val imageRef = item.posterRef ?: item.backdropRef ?: return null
        
        // Load image via TelegramFileLoader (handles TDLib file download)
        val localPath = fileLoader.loadThumb(
            remoteId = imageRef.remoteId,
            fileId = imageRef.fileId,
        )
        
        return localPath?.let { ImageDescriptor.LocalFile(it) }
    }
}
```

**Pros:**
- Leverages existing `TelegramFileLoader` infrastructure
- No additional parsing or extraction needed
- Low implementation cost

**Cons:**
- Static image - same for all positions
- May not have poster for all items

### 4.3 Advanced Trickplay for Telegram

Use **frame extraction** from the downloaded Telegram video file:

**How It Works:**

1. Telegram videos are downloaded to TDLib cache (file-based)
2. Use `MediaMetadataRetriever` to extract frames at intervals
3. Cache extracted thumbnails locally
4. Return appropriate thumbnail based on seek position

**Implementation Considerations:**

| Aspect | Consideration |
|--------|---------------|
| **File Access** | TDLib downloads to cache; path accessible via `TelegramFileDataSource` |
| **Extraction API** | `MediaMetadataRetriever.getFrameAtTime(positionUs, OPTION_CLOSEST)` |
| **Performance** | Extraction is CPU-intensive; must run on background thread |
| **Caching** | Generate thumbnails on first play; cache for subsequent seeks |
| **Interval** | Generate frames every 5-10 seconds for reasonable coverage |

**Pseudo-code:**
```kotlin
class TelegramAdvancedTrickplayProvider(
    private val item: TelegramItem,
    private val fileDownloader: T_TelegramFileDownloader,
    private val thumbnailCache: ThumbnailCache,
) : TrickplayProvider {
    
    override suspend fun getPreviewImage(positionMs: Long): ImageDescriptor? {
        // 1. Check if thumbnails are already cached
        val cacheKey = "${item.chatId}_${item.anchorMessageId}"
        val cached = thumbnailCache.getThumbnail(cacheKey, positionMs)
        if (cached != null) return ImageDescriptor.LocalFile(cached)
        
        // 2. Get local file path via TDLib
        val videoRef = item.videoRef ?: return null
        val localPath = fileDownloader.getLocalFilePath(videoRef.fileId ?: 0)
            ?: return null
        
        // 3. Extract frame (should be called from background)
        val bitmap = extractFrame(localPath, positionMs * 1000) // positionUs
            ?: return null
        
        // 4. Cache and return
        val thumbPath = thumbnailCache.store(cacheKey, positionMs, bitmap)
        return ImageDescriptor.LocalFile(thumbPath)
    }
    
    private fun extractFrame(path: String, positionUs: Long): Bitmap? {
        return try {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(path)
                retriever.getFrameAtTime(positionUs, OPTION_CLOSEST)
            }
        } catch (e: Exception) {
            null
        }
    }
}
```

**Pros:**
- True position-based preview
- Works for any video that TDLib can download

**Cons:**
- Significant implementation complexity
- CPU/memory cost for extraction
- Storage cost for thumbnail cache
- May not work if file not fully downloaded

### 4.4 Recommendation for Telegram

**Phase 1 (MVP):** Implement **Minimal Trickplay** using `posterRef`/`backdropRef`
- Quick to implement
- Provides visual improvement over no preview
- No infrastructure changes needed

**Phase 2 (Future):** Consider **Advanced Trickplay** if:
- User feedback indicates strong desire for position-based preview
- Performance testing shows extraction is feasible on target devices
- Storage budget allows thumbnail caching

---

## 5. Xtream VOD Trickplay Design

### 5.1 Available Image Sources

From `ObxVod` and `ObxSeries` entities:

| Field | Type | Description |
|-------|------|-------------|
| `poster` | `String?` | HTTP URL to poster image |
| `imagesJson` | `String?` | JSON with additional images (cover, backdrop, logo) |

**Example `imagesJson` structure:**
```json
{
  "movie_image": "https://...",
  "cover_big": "https://...",
  "backdrop_path": ["https://...", "https://..."]
}
```

### 5.2 Minimal Trickplay for Xtream

Use existing **poster/cover URL** as a static preview while scrubbing:

**Implementation Strategy:**

1. When building `PlaybackContext` for Xtream VOD, include the poster URL
2. Create `XtreamTrickplayProvider` that returns the poster for any position
3. Load image via Coil (existing infrastructure for HTTP images)
4. Display in `SeekPreviewOverlay`

**Pseudo-code:**
```kotlin
class XtreamTrickplayProvider(
    private val posterUrl: String?,
    private val coverUrl: String?,
) : TrickplayProvider {
    
    override fun supportsTrickplay(): Boolean = 
        posterUrl != null || coverUrl != null
    
    override suspend fun getPreviewImage(positionMs: Long): ImageDescriptor? {
        val url = posterUrl ?: coverUrl ?: return null
        return ImageDescriptor.RemoteUrl(url)
    }
}
```

**Pros:**
- Minimal implementation
- Leverages existing Coil image loading
- No Xtream API changes needed

**Cons:**
- Static image
- Poster may not be available for all content

### 5.3 Advanced Trickplay for Xtream

Xtream API does **NOT** provide thumbnail sprite sheets or position-based previews. Options:

**Option A: On-Device Frame Extraction (Same as Telegram)**

For Xtream VOD files (MP4, MKV over HTTP):

1. Use `MediaMetadataRetriever` with HTTP data source
2. Extract frames at intervals
3. Cache locally

**Note:** Modern Android versions (API 14+) support HTTP URLs in MediaMetadataRetriever via `setDataSource(String, Map<String, String>)`. However, performance depends on the server supporting HTTP range requests for random access. Some Xtream providers may not support efficient seeking for extraction.

**Option B: Server-Side Thumbnail Service (Out of Scope)**

Would require:
- Backend service to generate thumbnails
- Storage for generated thumbnails
- API to retrieve thumbnails by content ID and position

This is beyond the scope of FishIT Player application changes.

### 5.4 Xtream Playback Path Analysis

Xtream VOD is delivered as:
- HTTP streaming URL (e.g., `http://provider.com/movie/user/pass/12345.mp4`)
- Container: MP4, MKV, or other
- Some providers use HLS (.m3u8) for VOD as well

**For file-based streams (MP4/MKV):**
- Frame extraction may be possible via `MediaMetadataRetriever` with network URI
- Performance depends on provider's support for HTTP range requests

**For HLS streams:**
- Frame extraction is not straightforward
- Would need to download segments to extract frames

### 5.5 Recommendation for Xtream

**Phase 1 (MVP):** Implement **Minimal Trickplay** using poster URL
- Quick to implement
- Works for all Xtream content with images
- Consistent with Telegram minimal implementation

**Phase 2 (Future):** Advanced extraction only if:
- Demand is high
- Testing shows it works for target providers
- Performance is acceptable

---

## 6. SIP Player Integration Layer

### 6.1 Unified TrickplayProvider Interface

The SIP Internal Player should consume Trickplay through a **unified interface**:

```kotlin
/**
 * Provider interface for Trickplay preview images.
 * 
 * Implementations are source-specific (Telegram, Xtream, etc.)
 * but the player sees only this interface.
 */
interface TrickplayProvider {
    /**
     * Whether this source supports Trickplay previews.
     */
    fun supportsTrickplay(): Boolean
    
    /**
     * Get a preview image for the given seek position.
     * 
     * For minimal implementations, returns the same image for any position.
     * For advanced implementations, returns position-appropriate thumbnail.
     * 
     * @param positionMs Target seek position in milliseconds
     * @return Image descriptor or null if no preview available
     */
    suspend fun getPreviewImage(positionMs: Long): ImageDescriptor?
}

/**
 * Descriptor for preview images.
 */
sealed interface ImageDescriptor {
    /** Local file path */
    data class LocalFile(val path: String) : ImageDescriptor
    
    /** Remote HTTP URL */
    data class RemoteUrl(val url: String) : ImageDescriptor
    
    /** Bitmap in memory (for extracted frames) */
    data class InMemory(val bitmap: Bitmap) : ImageDescriptor
}
```

### 6.2 Provider Factory

Create providers based on `PlaybackContext`:

```kotlin
/**
 * Factory to create appropriate TrickplayProvider based on content source.
 */
object TrickplayProviderFactory {
    
    fun create(
        context: PlaybackContext,
        telegramRepository: TelegramContentRepository?,
        fileLoader: TelegramFileLoader?,
    ): TrickplayProvider {
        return when {
            context.isTelegramContent -> createTelegramProvider(context, telegramRepository, fileLoader)
            context.isXtreamContent -> createXtreamProvider(context)
            else -> NoOpTrickplayProvider
        }
    }
    
    private fun createTelegramProvider(
        context: PlaybackContext,
        repository: TelegramContentRepository?,
        fileLoader: TelegramFileLoader?,
    ): TrickplayProvider {
        // Load TelegramItem from repository
        // Return TelegramTrickplayProvider
        return TelegramTrickplayProvider(/* ... */)
    }
    
    private fun createXtreamProvider(context: PlaybackContext): TrickplayProvider {
        // Use poster URL from context
        return XtreamTrickplayProvider(context.posterUrl, context.coverUrl)
    }
}

/**
 * No-op provider for content that doesn't support Trickplay.
 */
object NoOpTrickplayProvider : TrickplayProvider {
    override fun supportsTrickplay(): Boolean = false
    override suspend fun getPreviewImage(positionMs: Long): ImageDescriptor? = null
}
```

### 6.3 Extended PlaybackContext

Add fields to `PlaybackContext` to carry image URLs:

```kotlin
data class PlaybackContext(
    val type: PlaybackType,
    val mediaId: Long? = null,
    // ... existing fields ...
    
    // Trickplay support
    val posterUrl: String? = null,
    val coverUrl: String? = null,
    val telegramChatId: Long? = null,
    val telegramAnchorMessageId: Long? = null,
) {
    val isTelegramContent: Boolean
        get() = telegramChatId != null
    
    val isXtreamContent: Boolean
        get() = !isTelegramContent && mediaId != null
    
    val supportsSeek: Boolean
        get() = type != PlaybackType.LIVE
}
```

### 6.4 UI State Extension

Add preview image state to `InternalPlayerUiState`:

```kotlin
data class InternalPlayerUiState(
    // ... existing fields ...
    
    // Seek preview fields
    val seekPreviewVisible: Boolean = false,
    val seekPreviewTargetMs: Long? = null,
    
    // NEW: Trickplay preview image
    val seekPreviewImageUrl: String? = null,  // For RemoteUrl
    val seekPreviewImagePath: String? = null, // For LocalFile
    val supportsTrickplayPreview: Boolean = false,
)
```

### 6.5 UI Component Enhancement

Enhance `SeekPreviewOverlay` to show image:

```kotlin
@Composable
private fun SeekPreviewOverlay(
    currentPositionMs: Long,
    targetPositionMs: Long,
    durationMs: Long,
    previewImageUrl: String? = null,
    previewImagePath: String? = null,
) {
    Box(
        modifier = Modifier
            .background(
                color = Color.Black.copy(alpha = 0.7f),
                shape = RoundedCornerShape(8.dp),
            )
            .padding(16.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Preview image (if available)
            when {
                previewImageUrl != null -> {
                    AsyncImage(
                        model = previewImageUrl,
                        contentDescription = "Preview",
                        modifier = Modifier
                            .width(160.dp)
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(4.dp)),
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                previewImagePath != null -> {
                    AsyncImage(
                        model = File(previewImagePath),
                        contentDescription = "Preview",
                        modifier = Modifier
                            .width(160.dp)
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(4.dp)),
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
            
            // Target position (large)
            Text(
                text = formatMs(targetPositionMs),
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
            )
            
            // Delta from current
            val delta = targetPositionMs - currentPositionMs
            val sign = if (delta >= 0) "+" else ""
            Text(
                text = "$sign${formatMs(abs(delta))}",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 16.sp,
            )
        }
    }
}
```

### 6.6 Session Integration

Wire `TrickplayProvider` in `InternalPlayerSession`:

```kotlin
// In rememberInternalPlayerSession or session setup
val trickplayProvider = TrickplayProviderFactory.create(
    context = playbackContext,
    telegramRepository = telegramContentRepository,
    fileLoader = telegramFileLoader,
)

// Update state when seek preview is shown
LaunchedEffect(state.seekPreviewVisible, state.seekPreviewTargetMs) {
    if (state.seekPreviewVisible && state.seekPreviewTargetMs != null) {
        val imageDescriptor = trickplayProvider.getPreviewImage(state.seekPreviewTargetMs)
        when (imageDescriptor) {
            is ImageDescriptor.RemoteUrl -> {
                updateState { copy(seekPreviewImageUrl = imageDescriptor.url, seekPreviewImagePath = null) }
            }
            is ImageDescriptor.LocalFile -> {
                updateState { copy(seekPreviewImagePath = imageDescriptor.path, seekPreviewImageUrl = null) }
            }
            null -> {
                updateState { copy(seekPreviewImageUrl = null, seekPreviewImagePath = null) }
            }
        }
    }
}
```

---

## 7. Non-Goals & Constraints

### 7.1 LIVE Streams

**Rule:** Trickplay is NEVER available for `PlaybackType.LIVE`

This includes:
- Xtream LIVE channels
- Any real-time streaming content
- DVR/timeshift is out of scope for this design

### 7.2 No Change to Phase 8 Lifecycle Ownership

Per `INTERNAL_PLAYER_PHASE8_PERFORMANCE_LIFECYCLE_CONTRACT.md`:

- `PlaybackSessionController` owns ExoPlayer lifecycle
- `TrickplayProvider` is a **data provider**, not a lifecycle component
- Trickplay does NOT affect session lifecycle states

### 7.3 No Direct TDLib/HTTP Logic in UI

Per `TELEGRAM_SIP_PLAYER_INTEGRATION.md` Section 7.1:

> **Critical: No TDLib calls from `@Composable` functions.**

All Trickplay provider logic must be in data/infrastructure layer:
- `TelegramTrickplayProvider` → in `telegram/player/` or `telegram/core/`
- `XtreamTrickplayProvider` → in `core/xtream/` or data layer
- UI components only receive image URLs/paths

### 7.4 No Thumbnail Sprite Sheet Support

Neither Xtream nor Telegram provides server-side thumbnail sprite sheets. This design does NOT include:
- BIF file parsing (Roku format)
- WebVTT thumbnail track parsing
- Server-side sprite sheet generation

### 7.5 Performance Constraints

For advanced frame extraction (if implemented):
- Extraction must run on background thread (`Dispatchers.IO`)
- Must not block seek/scrub gestures
- Cache hit should return immediately
- Cache miss may show static fallback while extracting

---

## 8. Implementation Roadmap

### Phase 1: Minimal Trickplay (MVP)

**Scope:** Static preview images for Xtream and Telegram VOD

**Tasks:**

1. **Extend `PlaybackContext`** with image URL fields
   - Add `posterUrl`, `coverUrl` for Xtream
   - Add `telegramChatId`, `telegramAnchorMessageId` for Telegram

2. **Create `TrickplayProvider` interface**
   - Define interface in `player/internal/domain/`
   - Create `ImageDescriptor` sealed class

3. **Implement `XtreamTrickplayProvider`**
   - Simple implementation returning poster URL
   - No external dependencies

4. **Implement `TelegramTrickplayProvider`**
   - Load `TelegramItem` from repository
   - Return `posterRef` or `backdropRef` image
   - Use `TelegramFileLoader` for file resolution

5. **Update `InternalPlayerUiState`**
   - Add `seekPreviewImageUrl` and `seekPreviewImagePath`
   - Add `supportsTrickplayPreview` flag

6. **Enhance `SeekPreviewOverlay`**
   - Add `AsyncImage` for preview display
   - Handle both URL and local file sources

7. **Wire in `InternalPlayerSession`**
   - Create provider via factory
   - Update state on seek preview changes

**Estimated Effort:** 2-3 days

### Phase 2: Advanced Extraction (Future)

**Scope:** Position-based thumbnails via frame extraction

**Prerequisites:**
- Minimal Trickplay working
- Performance testing on target devices
- User feedback indicating need

**Tasks:**

1. **Create `ThumbnailCache`**
   - Local storage for extracted thumbnails
   - Key by content ID and position
   - Size management / eviction

2. **Create `FrameExtractor` utility**
   - Wrapper around `MediaMetadataRetriever`
   - Support for local files and HTTP URLs
   - Error handling and fallback

3. **Implement `TelegramAdvancedTrickplayProvider`**
   - Extract from TDLib cache file
   - Background extraction with cache

4. **Implement `XtreamAdvancedTrickplayProvider`**
   - HTTP range support testing
   - Download-then-extract fallback

5. **Background thumbnail generation**
   - WorkManager job for pre-generation
   - Triggered on first play

**Estimated Effort:** 1-2 weeks

---

## 9. References

### 9.1 Contract Documents

| Document | Purpose |
|----------|---------|
| `INTERNAL_PLAYER_REFACTOR_ROADMAP.md` | Phase 5 Trickplay state definition |
| `INTERNAL_PLAYER_PLAYER_SURFACE_CONTRACT_PHASE5.md` | PlayerSurface behavior during trickplay |
| `INTERNAL_PLAYER_PLAYBACK_SESSION_CONTRACT_PHASE7.md` | PlaybackSession architecture |
| `INTERNAL_PLAYER_PHASE8_PERFORMANCE_LIFECYCLE_CONTRACT.md` | Performance and lifecycle rules |
| `TELEGRAM_PARSER_CONTRACT.md` | TelegramItem and image reference specs |
| `TELEGRAM_SIP_PLAYER_INTEGRATION.md` | Telegram playback integration |

### 9.2 Implementation Files

| File | Purpose |
|------|---------|
| `player/internal/state/InternalPlayerState.kt` | Trickplay state fields |
| `player/internal/ui/InternalPlayerControls.kt` | SeekPreviewOverlay, TrickplayIndicator |
| `player/internal/ui/PlayerSurface.kt` | Gesture handling for seek |
| `telegram/domain/TelegramDomainModels.kt` | TelegramMediaRef, TelegramImageRef |
| `data/obx/ObxEntities.kt` | ObxVod, ObxSeries image fields |

### 9.3 External References

- [Android MediaMetadataRetriever](https://developer.android.com/reference/android/media/MediaMetadataRetriever)
- [Media3 ExoPlayer](https://developer.android.com/guide/topics/media/media3)
- [Coil Image Loading](https://coil-kt.github.io/coil/)

---

## Document History

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2025-12-01 | Initial design document |

---

**Document Type:** Design Proposal  
**Review Status:** Pending Review

> LEGACY (V1) – historical document  
> Not valid for v2. For current architecture see `V2_PORTAL.md` and `docs/v2/**`.

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

### 1.5 Streaming-First Design Principles

> **CRITICAL**: This section defines mandatory constraints for Trickplay implementation.

**Core Principles:**

1. **Trickplay MUST work during progressive streaming** - not only after full file download
2. **No full-file download requirement** - Trickplay must never block on or require complete file download
3. **Lightweight implementation** - No heavy thumbnail caches or expensive preprocessing
4. **Graceful degradation** - Fall back to static preview when position-based preview is unavailable

**Prohibited Patterns:**

- ❌ Requiring `isDownloadingCompleted == true` as a precondition for Trickplay
- ❌ Caching full video files solely for preview purposes
- ❌ Blocking playback or seeking while waiting for full download
- ❌ Downloading entire file just to extract a single frame

**Allowed Patterns:**

- ✅ Using static poster/backdrop as primary Trickplay preview
- ✅ Opportunistic frame extraction from **already downloaded portions** of the file
- ✅ Returning "no preview available" when requested position is beyond downloaded data
- ✅ Using existing video thumbnails from TDLib metadata

**Provider Behavior Contract:**

```kotlin
interface TrickplayProvider {
    /**
     * Get preview image for seek position.
     * 
     * MUST return quickly (non-blocking).
     * MUST NOT trigger full file downloads.
     * SHOULD return static preview if position-based preview is unavailable.
     * MAY return null if no preview is available at all.
     */
    suspend fun getPreviewImage(positionMs: Long): ImageDescriptor?
}
```

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

### 4.3 Advanced Trickplay for Telegram (Optional Future Enhancement)

> **Note:** Advanced Trickplay is an **optional future optimization**, NOT the baseline path.
> The baseline Trickplay implementation for Telegram is **Minimal Trickplay** (static preview).

**Streaming-First Approach:**

Advanced Trickplay, if implemented, MUST follow these constraints:

1. **No full-file download requirement** - Never block on complete file download
2. **Partial-data awareness** - Only attempt extraction from already-downloaded portions
3. **Graceful fallback** - Return static preview when position-based preview is unavailable
4. **Opportunistic extraction** - Extract frames only when the target position is within downloaded range

**How It Would Work (Streaming-Compatible):**

1. Query TDLib for current file download state via `getFileInfo(fileId)`
2. Check if the requested seek position is within the downloaded byte range
3. If position is reachable, attempt frame extraction from local cache file
4. If position is NOT reachable, fall back to static preview (poster/backdrop)
5. NEVER trigger or wait for additional downloads solely for Trickplay

**Implementation Considerations:**

| Aspect | Consideration |
|--------|---------------|
| **File State Query** | Use `T_TelegramFileDownloader.getFileInfo(fileId)` to get `downloadedSize` and `path` |
| **Position Estimation** | Estimate byte position from time using bitrate (approximate) |
| **Extraction** | Only attempt if estimated byte position < `downloadedSize` |
| **Fallback** | Always have static preview ready as fallback |
| **Performance** | Extraction is CPU-intensive; must run on background thread |

**Conceptual Design (Partial-Data Aware):**
```kotlin
/**
 * Advanced Trickplay provider with partial-data awareness.
 * 
 * IMPORTANT: This is conceptual pseudo-code for future implementation.
 * The baseline implementation is TelegramMinimalTrickplayProvider.
 */
class TelegramAdvancedTrickplayProvider(
    private val item: TelegramItem,
    private val fileDownloader: T_TelegramFileDownloader,
    private val minimalProvider: TelegramTrickplayProvider, // Fallback
) : TrickplayProvider {
    
    override suspend fun getPreviewImage(positionMs: Long): ImageDescriptor? {
        val videoRef = item.videoRef ?: return minimalProvider.getPreviewImage(positionMs)
        val fileId = videoRef.fileId ?: return minimalProvider.getPreviewImage(positionMs)
        
        // 1. Get current file state from TDLib (non-blocking query)
        val fileInfo = fileDownloader.getFileInfo(fileId) 
            ?: return minimalProvider.getPreviewImage(positionMs)
        
        val localPath = fileInfo.local?.path
        val downloadedBytes = fileInfo.local?.downloadedSize?.toLong() ?: 0L
        val totalBytes = fileInfo.expectedSize?.toLong() ?: Long.MAX_VALUE
        
        // 2. Check if we have enough downloaded data for this position
        // NOTE: This is an APPROXIMATION assuming uniform bitrate.
        // For variable bitrate (VBR) videos, this may be inaccurate.
        // Erring on the side of caution (falling back to static preview) is acceptable.
        val durationMs = item.durationSeconds?.times(1000L) ?: return minimalProvider.getPreviewImage(positionMs)
        val estimatedBytePosition = if (durationMs > 0) {
            (positionMs.toDouble() / durationMs * totalBytes).toLong()
        } else {
            Long.MAX_VALUE
        }
        
        // 3. If position is beyond downloaded range, fall back to static preview
        if (localPath.isNullOrBlank() || estimatedBytePosition > downloadedBytes) {
            // Position not yet downloaded - use static preview
            return minimalProvider.getPreviewImage(positionMs)
        }
        
        // 4. Attempt frame extraction from downloaded portion (opportunistic)
        return try {
            val bitmap = extractFrame(localPath, positionMs * 1000)
            bitmap?.let { ImageDescriptor.InMemory(it) }
                ?: minimalProvider.getPreviewImage(positionMs)
        } catch (e: Exception) {
            // Extraction failed - fall back to static preview
            minimalProvider.getPreviewImage(positionMs)
        }
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

**Key Differences from Non-Streaming Approach:**

| Aspect | ❌ Non-Streaming (Wrong) | ✅ Streaming-First (Correct) |
|--------|--------------------------|------------------------------|
| **Precondition** | `isDownloadingCompleted == true` | No precondition - query current state |
| **Download trigger** | May trigger full download | Never triggers downloads |
| **Failure mode** | Block until download completes | Immediate fallback to static preview |
| **API usage** | `getLocalFilePath()` (non-existent) | `getFileInfo()` with `downloadedSize` check |

**Pros:**
- Position-based preview for already-downloaded content
- No impact on streaming performance
- Graceful degradation to static preview

**Cons:**
- Significant implementation complexity
- CPU cost for extraction (must be background)
- Only works for portions already downloaded
- Byte-to-time estimation is approximate

### 4.4 Recommendation for Telegram

**Phase 1 (MVP - REQUIRED):** Implement **Minimal Trickplay** using `posterRef`/`backdropRef`
- Quick to implement
- Provides visual improvement over no preview
- No infrastructure changes needed
- **This is the baseline and may be the only implementation needed**

**Phase 2 (OPTIONAL Future Enhancement):** Consider **Advanced Trickplay** only if:
- User feedback indicates strong desire for position-based preview
- Performance testing shows extraction is feasible on target devices
- Implementation follows streaming-first principles (no full-download requirement)
- Static fallback is always available

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

### 5.3 Advanced Trickplay for Xtream (NOT PLANNED)

> **Note:** Advanced Trickplay for Xtream is **NOT planned** for implementation.
> Xtream VOD Trickplay will remain **poster-only** (Minimal Trickplay).

**Why Advanced Trickplay is NOT Suitable for Xtream:**

Xtream API does **NOT** provide thumbnail sprite sheets or position-based previews. The theoretical options below are **rejected** due to violating streaming-first constraints:

**❌ Option A: On-Device Frame Extraction (REJECTED)**

This approach would require:
- Downloading substantial portions of the video file over HTTP
- Using `MediaMetadataRetriever` with HTTP data source
- Performance depends on provider's HTTP range request support (unreliable)

**Why it violates streaming-first principles:**
- Would cause significant additional network traffic
- Many Xtream providers don't support efficient HTTP range requests
- Would degrade streaming performance
- HLS streams (.m3u8) don't support random frame extraction at all

**❌ Option B: Server-Side Thumbnail Service (OUT OF SCOPE)**

Would require backend infrastructure changes - not applicable to FishIT Player.

### 5.4 Xtream Playback Path Analysis

Xtream VOD is delivered as:
- HTTP streaming URL (e.g., `http://provider.com/movie/user/pass/12345.mp4`)
- Container: MP4, MKV, or other
- Some providers use HLS (.m3u8) for VOD as well

**Streaming-First Implications:**
- Direct HTTP streaming doesn't provide local file access for extraction
- HLS streams are segmented and don't support random seek for extraction
- Downloading for extraction would violate "no full-file download" principle

### 5.5 Recommendation for Xtream

**Baseline Implementation (FINAL):** **Minimal Trickplay** using poster URL
- Quick to implement
- Works for all Xtream content with images
- Consistent with streaming-first design
- **No advanced Trickplay planned or recommended for Xtream**

**Rationale:**
- Xtream content is HTTP-streamed (no local file)
- Frame extraction would require downloading data
- This violates lightweight/streaming-first constraints
- Poster-based preview is sufficient for the use case

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

### 7.6 Streaming-First Constraints (MANDATORY)

> **CRITICAL**: These constraints are mandatory and apply to ALL Trickplay implementations.

**Prohibited:**
- ❌ Requiring full file download as a precondition for Trickplay
- ❌ Using `isDownloadingCompleted == true` as a hard requirement
- ❌ Caching full video files solely for preview purposes
- ❌ Heavy thumbnail caches or preprocessing pipelines
- ❌ Blocking playback/seeking while waiting for download
- ❌ Calling non-existent APIs like `getLocalFilePath()`

**Required:**
- ✅ Trickplay MUST work during progressive streaming
- ✅ Static preview (poster/backdrop) MUST always be available as fallback
- ✅ Provider MUST be able to return "no preview available" for certain positions
- ✅ Implementation MUST be lightweight and non-blocking
- ✅ Use existing TDLib APIs like `getFileInfo()` to check partial download state

**API Usage:**
```kotlin
// ✅ CORRECT: Query partial download state and check if position is within downloaded range
val fileInfo = fileDownloader.getFileInfo(fileId)
val downloadedBytes = fileInfo?.local?.downloadedSize?.toLong() ?: 0L
val localPath = fileInfo?.local?.path
val totalBytes = fileInfo?.expectedSize?.toLong() ?: Long.MAX_VALUE

// Estimate if position is within downloaded range (approximate for VBR)
val estimatedBytePosition = (positionMs.toDouble() / durationMs * totalBytes).toLong()
if (estimatedBytePosition > downloadedBytes) {
    // Position not yet downloaded - fall back to static preview
    return staticPreviewProvider.getPreviewImage(positionMs)
}
// Proceed with frame extraction from localPath...

// ❌ WRONG: Require full download completion as a hard precondition
if (fileInfo?.local?.isDownloadingCompleted != true) return null  // PROHIBITED - blocks Trickplay until full download
val localPath = fileInfo.local?.path ?: return null  // Only works after full download
```

---

## 8. Implementation Roadmap

### Phase 1: Minimal Trickplay (MVP)

**Scope:** Static preview images for Xtream and Telegram VOD

**Streaming-First Compliance:** ✅ This phase fully complies with streaming-first constraints.

**Tasks:**

1. **Extend `PlaybackContext`** with image URL fields
   - Add `posterUrl`, `coverUrl` for Xtream
   - Add `telegramChatId`, `telegramAnchorMessageId` for Telegram

2. **Create `TrickplayProvider` interface**
   - Define interface in `player/internal/domain/`
   - Create `ImageDescriptor` sealed class
   - Ensure interface contract allows "no preview available" return

3. **Implement `XtreamTrickplayProvider`**
   - Simple implementation returning poster URL
   - No external dependencies
   - **This is the FINAL implementation for Xtream** (no advanced version planned)

4. **Implement `TelegramTrickplayProvider`**
   - Load `TelegramItem` from repository
   - Return `posterRef` or `backdropRef` image
   - Use `TelegramFileLoader` for file resolution
   - **This is the baseline implementation for Telegram**

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

### Phase 2: Advanced Extraction for Telegram (OPTIONAL - Deferred)

> **IMPORTANT:** This phase is **OPTIONAL** and **DEFERRED**.
> It should only be considered if there is strong user demand AND the implementation
> can follow streaming-first principles (partial-data awareness, no full-download requirement).

**Scope:** Position-based thumbnails via opportunistic frame extraction for Telegram only

**Streaming-First Compliance:** ⚠️ Must follow partial-data-aware design (see Section 4.3)

**Prerequisites:**
- Phase 1 (Minimal Trickplay) working and deployed
- Strong user feedback indicating need for position-based preview
- Performance testing confirms extraction is feasible on target devices
- Design review confirms streaming-first compliance

**Constraints (MANDATORY):**
- ❌ NO full-file download requirement
- ❌ NO blocking on download completion
- ❌ NO heavy thumbnail caching infrastructure
- ❌ NO implementation for Xtream (poster-only is final)
- ✅ MUST fall back to static preview when position not available
- ✅ MUST use partial-data awareness (check `downloadedSize` before extraction)
- ✅ MUST be non-blocking and opportunistic

**Potential Tasks (if ever implemented):**

1. **Implement `TelegramAdvancedTrickplayProvider`**
   - Query file state via `getFileInfo(fileId)`
   - Check `downloadedSize` vs estimated byte position
   - Extract from TDLib cache ONLY if position is within downloaded range
   - Fall back to static preview otherwise

2. **Lightweight in-memory cache (optional)**
   - Small LRU cache for recently extracted frames
   - NOT a persistent thumbnail cache
   - NOT pre-generation of thumbnails

**Estimated Effort:** 3-5 days (if ever pursued)

**Decision Point:** Re-evaluate after Phase 1 is deployed and user feedback is collected.

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
| 1.1 | 2025-12-01 | Revised with streaming-first design principles. Added Section 1.5 (Streaming-First Design Principles). Updated Advanced Trickplay sections to use partial-data awareness. Removed non-existent `getLocalFilePath()` API usage. Clarified Xtream as poster-only. Updated Implementation Roadmap to reflect lightweight goals. |

---

**Document Type:** Design Proposal  
**Review Status:** Revised - Streaming-First Compliant  
**Version:** 1.1

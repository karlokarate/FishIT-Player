# PlaybackHints System & Circular Dependency Fix - Complete Diff

**Date:** 2025-01-XX  
**Branch:** `architecture/v2-bootstrap`  
**Status:** ✅ Implemented

## Summary

This diff contains two related fixes:

1. **PlaybackHints System** - Transport source-specific playback data (episodeId, containerExtension, etc.) through the entire pipeline without encoding in sourceId
2. **Circular Dependency Resolution** - Move `SourceActivationStore` and related interfaces from `core:catalog-sync` to `core:model` to break the cycle `catalog-sync → data-xtream → catalog-sync`

---

## New Files

### 1. `core/model/src/main/java/com/fishit/player/core/model/PlaybackHintKeys.kt` (NEW)

```kotlin
package com.fishit.player.core.model

/**
 * Type-safe keys for playback hints stored in [RawMediaMetadata.playbackHints].
 *
 * **Contract:**
 * - Keys are dot-namespaced: `{source}.{field}`
 * - Values are always strings (factories convert as needed)
 * - Used in Pipeline → Data → Domain → Player flow
 *
 * @see RawMediaMetadata.playbackHints
 * @see MediaSourceRef.playbackHints
 */
object PlaybackHintKeys {

    /**
     * Xtream-specific playback hint keys.
     */
    object Xtream {
        /** Content type: "live", "vod", or "series" */
        const val CONTENT_TYPE = "xtream.contentType"

        /** Stream ID for live channels */
        const val STREAM_ID = "xtream.streamId"

        /** VOD ID for movies */
        const val VOD_ID = "xtream.vodId"

        /** Series ID */
        const val SERIES_ID = "xtream.seriesId"

        /** Episode stream ID (CRITICAL for playback URL construction) */
        const val EPISODE_ID = "xtream.episodeId"

        /** Season number */
        const val SEASON_NUMBER = "xtream.seasonNumber"

        /** Episode number */
        const val EPISODE_NUMBER = "xtream.episodeNumber"

        /** Container extension (mp4, mkv, ts) for URL construction */
        const val CONTAINER_EXT = "xtream.containerExtension"

        // Content type values
        const val CONTENT_LIVE = "live"
        const val CONTENT_VOD = "vod"
        const val CONTENT_SERIES = "series"
    }

    /**
     * Telegram-specific playback hint keys.
     */
    object Telegram {
        /** Telegram chat ID */
        const val CHAT_ID = "telegram.chatId"

        /** Telegram message ID */
        const val MESSAGE_ID = "telegram.messageId"

        /** Telegram file ID (for direct access) */
        const val FILE_ID = "telegram.fileId"
    }
}
```

---

### 2. `core/model/src/main/java/com/fishit/player/core/model/source/SourceId.kt` (NEW)

```kotlin
package com.fishit.player.core.model.source

/**
 * Unique identifier for a data source (Telegram, Xtream, etc.).
 *
 * This is a value class to ensure type-safety when passing source identifiers.
 */
@JvmInline
value class SourceId(val value: String) {
    companion object {
        val TELEGRAM = SourceId("TELEGRAM")
        val XTREAM = SourceId("XTREAM")
        val IO = SourceId("IO")
        val AUDIOBOOK = SourceId("AUDIOBOOK")
    }

    override fun toString(): String = value
}
```

---

### 3. `core/model/src/main/java/com/fishit/player/core/model/source/SourceErrorReason.kt` (NEW)

```kotlin
package com.fishit.player.core.model.source

/**
 * Reason for source deactivation or failure.
 *
 * Used to provide context when a source becomes inactive.
 */
enum class SourceErrorReason {
    /** No error - source is active or was deactivated intentionally */
    NONE,

    /** Authentication failed (expired, invalid credentials) */
    AUTH_FAILED,

    /** Network error (unreachable, timeout) */
    NETWORK_ERROR,

    /** Transport layer error (API incompatibility, protocol error) */
    TRANSPORT_ERROR,

    /** Unknown or unspecified error */
    UNKNOWN,
}
```

---

### 4. `core/model/src/main/java/com/fishit/player/core/model/source/SourceActivationState.kt` (NEW)

```kotlin
package com.fishit.player.core.model.source

/**
 * Activation state for a data source.
 *
 * Represents whether a source is currently usable for operations.
 */
sealed class SourceActivationState {
    /** Source is active and ready for operations */
    data object Active : SourceActivationState()

    /** Source is inactive (not connected, disabled, or failed) */
    data class Inactive(
        val reason: SourceErrorReason = SourceErrorReason.NONE,
        val message: String? = null,
    ) : SourceActivationState()

    val isActive: Boolean
        get() = this is Active
}

/**
 * Snapshot of all source activation states.
 *
 * Used for UI display and decision-making about which sources are available.
 */
data class SourceActivationSnapshot(
    val telegram: SourceActivationState = SourceActivationState.Inactive(),
    val xtream: SourceActivationState = SourceActivationState.Inactive(),
    val io: SourceActivationState = SourceActivationState.Active, // Local IO is always active
) {
    /** Check if Telegram source is active */
    val isTelegramActive: Boolean
        get() = telegram.isActive

    /** Check if Xtream source is active */
    val isXtreamActive: Boolean
        get() = xtream.isActive

    /** Check if any source is active */
    val hasActiveSource: Boolean
        get() = isTelegramActive || isXtreamActive || io.isActive

    /** Get list of active source IDs */
    val activeSources: List<SourceId>
        get() = buildList {
            if (isTelegramActive) add(SourceId.TELEGRAM)
            if (isXtreamActive) add(SourceId.XTREAM)
            if (io.isActive) add(SourceId.IO)
        }
}
```

---

### 5. `core/model/src/main/java/com/fishit/player/core/model/source/SourceActivationStore.kt` (NEW)

```kotlin
package com.fishit.player.core.model.source

import kotlinx.coroutines.flow.StateFlow

/**
 * Store for source activation states.
 *
 * Provides read and write access to the activation state of each data source.
 * Used by:
 * - Transport adapters to update state after connect/disconnect
 * - UI to display source availability
 * - Catalog sync to determine which pipelines to run
 *
 * **Architecture Note:**
 * This interface lives in core:model to break the circular dependency between
 * core:catalog-sync and infra:data-xtream. Implementation lives in core:catalog-sync.
 */
interface SourceActivationStore {
    /** Current snapshot of all source states */
    val snapshot: StateFlow<SourceActivationSnapshot>

    /** Set Telegram as active */
    fun setTelegramActive()

    /** Set Telegram as inactive */
    fun setTelegramInactive(reason: SourceErrorReason = SourceErrorReason.NONE, message: String? = null)

    /** Set Xtream as active */
    fun setXtreamActive()

    /** Set Xtream as inactive */
    fun setXtreamInactive(reason: SourceErrorReason = SourceErrorReason.NONE, message: String? = null)
}
```

---

## Modified Files

### 6. `core/model/src/main/java/com/fishit/player/core/model/RawMediaMetadata.kt`

```diff
@@ -142,6 +142,26 @@ data class RawMediaMetadata(
      * Example: "Tom Hanks, Robin Wright, Gary Sinise"
      */
     val cast: String? = null,
+    // === Playback Hints (v2) ===
+    /**
+     * Source-specific hints required for playback but NOT part of media identity.
+     *
+     * **Contract:**
+     * - Contains data needed by PlaybackSourceFactory to build playback URL/source
+     * - Does NOT affect canonical identity or deduplication
+     * - Keys are defined in [PlaybackHintKeys] for type-safety
+     * - Values are always strings (factory converts as needed)
+     *
+     * **Use Cases:**
+     * - Xtream episodeId (stream ID distinct from episode number)
+     * - Xtream containerExtension (mp4, mkv, ts)
+     * - Telegram fileId, chatId, messageId
+     *
+     * **Flow:** Pipeline → RawMediaMetadata → MediaSourceRef → PlaybackContext.extras
+     *
+     * @see PlaybackHintKeys
+     */
+    val playbackHints: Map<String, String> = emptyMap(),
 )
```

---

### 7. `core/model/src/main/java/com/fishit/player/core/model/MediaSourceRef.kt`

```diff
@@ -44,6 +44,15 @@ data class MediaSourceRef(
     val durationMs: Long? = null, // Source-specific duration!
     val addedAt: Long = System.currentTimeMillis(),
     val priority: Int = 0,
+    /**
+     * Source-specific hints required for playback.
+     *
+     * Copied from [RawMediaMetadata.playbackHints] during source linking.
+     * Used by PlaybackSourceFactory to build playback URL/source.
+     *
+     * @see PlaybackHintKeys
+     */
+    val playbackHints: Map<String, String> = emptyMap(),
 ) {
```

---

### 8. `core/persistence/src/main/java/com/fishit/player/core/persistence/obx/ObxCanonicalEntities.kt`

```diff
@@ -194,6 +194,21 @@ data class ObxMediaSourceRef(
         var playbackUri: String? = null,
         /** Original poster URL from this source */
         var posterUrl: String? = null,
+        /**
+         * JSON-serialized playback hints (source-specific data for URL construction).
+         *
+         * Contains data needed by PlaybackSourceFactory that is NOT part of media identity.
+         * Examples:
+         * - Xtream episodeId (stream ID different from episode number)
+         * - Xtream containerExtension (mp4, mkv, ts)
+         * - Telegram fileId, chatId, messageId
+         *
+         * Format: {"key":"value",...} using keys from PlaybackHintKeys
+         * Empty map serialized as null to save space.
+         *
+         * @see com.fishit.player.core.model.PlaybackHintKeys
+         */
+        var playbackHintsJson: String? = null,
         // === Timestamps ===
         @Index var addedAt: Long = System.currentTimeMillis(),
 ) {
```

---

### 9. `pipeline/xtream/src/main/java/com/fishit/player/pipeline/xtream/mapper/XtreamRawMetadataExtensions.kt`

```diff
@@ -3,6 +3,7 @@ package com.fishit.player.pipeline.xtream.mapper
 import com.fishit.player.core.model.ExternalIds
 import com.fishit.player.core.model.MediaType
 import com.fishit.player.core.model.PipelineIdTag
+import com.fishit.player.core.model.PlaybackHintKeys
 import com.fishit.player.core.model.RawMediaMetadata
 import com.fishit.player.core.model.SourceType
 import com.fishit.player.core.model.TmdbMediaType
@@ -77,6 +78,14 @@ fun XtreamVodItem.toRawMediaMetadata(
         val externalIds =
                 tmdbId?.let { ExternalIds(tmdb = TmdbRef(TmdbMediaType.MOVIE, it)) }
                         ?: ExternalIds()
+        // Build playback hints for VOD URL construction
+        val hints = buildMap {
+                put(PlaybackHintKeys.Xtream.CONTENT_TYPE, PlaybackHintKeys.Xtream.CONTENT_VOD)
+                put(PlaybackHintKeys.Xtream.VOD_ID, id.toString())
+                containerExtension?.takeIf { it.isNotBlank() }?.let {
+                        put(PlaybackHintKeys.Xtream.CONTAINER_EXT, it)
+                }
+        }
         return RawMediaMetadata(
                 // ... existing fields ...
+                // === Playback Hints (v2) ===
+                playbackHints = hints,
         )
 }

// Similar changes for XtreamSeriesItem, XtreamEpisode, XtreamChannel, XtreamVodInfo
// Each populates appropriate hints with:
// - CONTENT_TYPE (vod/series/live)
// - ID fields (VOD_ID, STREAM_ID, SERIES_ID, EPISODE_ID)
// - CONTAINER_EXT when available
// - SEASON_NUMBER, EPISODE_NUMBER for episodes
```

---

### 10. `infra/data-xtream/src/main/java/com/fishit/player/infra/data/xtream/ObxXtreamCatalogRepository.kt`

```diff
@@ -3,6 +3,7 @@ package com.fishit.player.infra.data.xtream
 import com.fishit.player.core.model.ExternalIds
 import com.fishit.player.core.model.ImageRef
 import com.fishit.player.core.model.MediaType
+import com.fishit.player.core.model.PlaybackHintKeys
 import com.fishit.player.core.model.RawMediaMetadata
 // ...

-    private fun ObxVod.toRawMediaMetadata(): RawMediaMetadata =
-            RawMediaMetadata(
+    private fun ObxVod.toRawMediaMetadata(): RawMediaMetadata {
+            // Build playback hints for VOD URL construction
+            val hints = buildMap {
+                    put(PlaybackHintKeys.Xtream.CONTENT_TYPE, PlaybackHintKeys.Xtream.CONTENT_VOD)
+                    put(PlaybackHintKeys.Xtream.VOD_ID, vodId.toString())
+                    containerExt?.takeIf { it.isNotBlank() }?.let {
+                            put(PlaybackHintKeys.Xtream.CONTAINER_EXT, it)
+                    }
+            }
+            return RawMediaMetadata(
                     // ... existing fields ...
+                    playbackHints = hints,
             )
+    }

-    private fun ObxEpisode.toRawMediaMetadata(): RawMediaMetadata =
-            RawMediaMetadata(
+    private fun ObxEpisode.toRawMediaMetadata(): RawMediaMetadata {
+            // Build playback hints from stored episode data
+            val hints = buildMap {
+                    put(PlaybackHintKeys.Xtream.CONTENT_TYPE, PlaybackHintKeys.Xtream.CONTENT_SERIES)
+                    put(PlaybackHintKeys.Xtream.SERIES_ID, seriesId.toString())
+                    put(PlaybackHintKeys.Xtream.SEASON_NUMBER, season.toString())
+                    put(PlaybackHintKeys.Xtream.EPISODE_NUMBER, episodeNum.toString())
+                    // Episode ID (stream ID) - CRITICAL for URL construction
+                    if (episodeId != 0) {
+                            put(PlaybackHintKeys.Xtream.EPISODE_ID, episodeId.toString())
+                    }
+                    playExt?.takeIf { it.isNotBlank() }?.let {
+                            put(PlaybackHintKeys.Xtream.CONTAINER_EXT, it)
+                    }
+            }
+            return RawMediaMetadata(
                     // ... existing fields ...
+                    playbackHints = hints,
             )
+    }

     private fun RawMediaMetadata.toObxVod(): ObxVod? {
         // ...
+        // Extract containerExt from playbackHints
+        val containerExt = playbackHints[PlaybackHintKeys.Xtream.CONTAINER_EXT]
         return ObxVod(
                 // ... existing fields ...
+                containerExt = containerExt,
         )
     }

     private fun RawMediaMetadata.toObxEpisode(): ObxEpisode? {
         // ...
+        // Extract episodeId and playExt from playbackHints
+        val episodeStreamId = playbackHints[PlaybackHintKeys.Xtream.EPISODE_ID]?.toIntOrNull() ?: 0
+        val containerExt = playbackHints[PlaybackHintKeys.Xtream.CONTAINER_EXT]
         return ObxEpisode(
                 // ... existing fields ...
+                episodeId = episodeStreamId,
+                playExt = containerExt,
         )
     }
```

---

### 11. `feature/detail/src/main/java/com/fishit/player/feature/detail/PlayMediaUseCase.kt`

```diff
@@ -2,6 +2,7 @@ package com.fishit.player.feature.detail
 
 import com.fishit.player.core.model.CanonicalMediaId
 import com.fishit.player.core.model.MediaSourceRef
+import com.fishit.player.core.model.PlaybackHintKeys
 import com.fishit.player.core.model.SourceType
 // ...

     private fun buildExtrasForSource(source: MediaSourceRef): Map<String, String> = buildMap {
         val sourceIdValue = source.sourceId.value
+        val hints = source.playbackHints

         when (source.sourceType) {
             SourceType.XTREAM -> {
-                // Parse Xtream source ID: xtream:vod:123, xtream:series:123, xtream:live:123
+                // First, add all playbackHints (authoritative source)
+                putAll(hints)
+
+                // Parse sourceId for backwards compatibility and fill in missing data
                 when {
                     sourceIdValue.startsWith("xtream:vod:") -> {
-                        put("contentType", "vod")
+                        putIfAbsent(PlaybackHintKeys.Xtream.CONTENT_TYPE, PlaybackHintKeys.Xtream.CONTENT_VOD)
                         // ... rest uses putIfAbsent for fallback
                     }
                     // Similar changes for series, episode, live...
                 }
             }
             SourceType.TELEGRAM -> {
-                // Parse Telegram source ID: msg:chatId:messageId
+                // First, add all playbackHints
+                putAll(hints)
+                
+                // Parse Telegram source ID: msg:chatId:messageId (fallback)
                 if (sourceIdValue.startsWith("msg:")) {
                     // ... uses putIfAbsent for fallback
                 }
             }
             // ...
         }
     }

+    /** Helper to put value only if key is absent. */
+    private fun MutableMap<String, String>.putIfAbsent(key: String, value: String) {
+        if (!containsKey(key)) {
+            put(key, value)
+        }
+    }
```

---

### 12. `infra/data-xtream/build.gradle.kts`

```diff
 dependencies {
     // Core dependencies
     implementation(project(":core:model"))
+    // NOTE: Removed core:catalog-sync dependency to break circular dependency.
+    // Source activation interfaces now live in core:model:source package.
     // Use api() for persistence to expose generated ObjectBox cursor classes
     api(project(":core:persistence"))
```

---

### 13. `infra/data-xtream/src/main/java/com/fishit/player/infra/data/xtream/XtreamAuthRepositoryAdapter.kt`

```diff
 package com.fishit.player.infra.data.xtream
 
+import com.fishit.player.core.model.source.SourceActivationStore
+import com.fishit.player.core.model.source.SourceErrorReason
 import com.fishit.player.feature.onboarding.domain.XtreamAuthRepository
-import com.fishit.player.core.catalogsync.SourceActivationStore
-import com.fishit.player.core.catalogsync.SourceErrorReason
 // ...

     return apiClient.initialize(transportConfig)
         .map { caps ->
             // ...
+            // CRITICAL: Set XTREAM as ACTIVE source after successful connection
+            sourceActivationStore.setXtreamActive()
+            UnifiedLog.i(TAG) { "initialize: XTREAM source activated" }
             Unit
         }
         .onFailure { error ->
             // ...
+            // Deactivate XTREAM on failure
+            sourceActivationStore.setXtreamInactive(SourceErrorReason.TRANSPORT_ERROR)
         }

     override suspend fun close() {
         // ...
+        // Deactivate XTREAM when disconnecting
+        sourceActivationStore.setXtreamInactive()
+        UnifiedLog.i(TAG) { "close: XTREAM source deactivated" }
     }
```

---

### 14. `core/catalog-sync/.../SourceActivationModels.kt` (converted to typealias re-exports)

```kotlin
package com.fishit.player.core.catalogsync

// Re-export from core:model for backwards compatibility
typealias SourceId = com.fishit.player.core.model.source.SourceId
typealias SourceErrorReason = com.fishit.player.core.model.source.SourceErrorReason
typealias SourceActivationState = com.fishit.player.core.model.source.SourceActivationState
typealias SourceActivationSnapshot = com.fishit.player.core.model.source.SourceActivationSnapshot
```

---

### 15. `core/catalog-sync/.../SourceActivationStore.kt` (converted to typealias re-export)

```kotlin
package com.fishit.player.core.catalogsync

// Re-export from core:model for backwards compatibility
typealias SourceActivationStore = com.fishit.player.core.model.source.SourceActivationStore
```

---

## Architecture Impact

### Before (Circular Dependency)

```
core:catalog-sync
       ↓
infra:data-xtream (XtreamAuthRepositoryAdapter imports SourceActivationStore)
       ↓
core:catalog-sync ← CYCLE!
```

### After (Resolved)

```
core:model/source (SourceActivationStore, SourceId, etc.)
       ↑                    ↑
core:catalog-sync      infra:data-xtream
(typealias re-exports)  (imports from core:model)
```

---

## Data Flow

### PlaybackHints End-to-End

```
Xtream API Response
       ↓
XtreamEpisode { id: 12345 (stream ID), episodeNumber: 5, ... }
       ↓
XtreamRawMetadataExtensions.toRawMediaMetadata()
       ↓
RawMediaMetadata {
    sourceId: "xtream:episode:100:2:5"
    playbackHints: {
        "xtream.contentType": "series",
        "xtream.seriesId": "100",
        "xtream.episodeId": "12345",  ← CRITICAL
        "xtream.seasonNumber": "2",
        "xtream.episodeNumber": "5",
        "xtream.containerExtension": "mkv"
    }
}
       ↓
ObxXtreamCatalogRepository.upsertAll()
       ↓
ObxEpisode { episodeId: 12345, playExt: "mkv", ... }
       ↓
ObxEpisode.toRawMediaMetadata() (on read)
       ↓
MediaSourceRef { playbackHints: {...} }
       ↓
PlayMediaUseCase.buildExtrasForSource()
       ↓
PlaybackContext { extras: {...episodeId: "12345"...} }
       ↓
XtreamPlaybackSourceFactory.buildUrl(extras["xtream.episodeId"])
       ↓
http://server:port/movie/{username}/{password}/{episodeId}.mkv
```

---

## Verification

```bash
# Verify no circular dependencies
./gradlew :infra:data-xtream:dependencies --configuration debugCompileClasspath | grep catalog-sync
# Should return empty (no catalog-sync dependency)

# Verify compilation
./gradlew :core:model:compileDebugKotlin \
          :core:catalog-sync:compileDebugKotlin \
          :infra:data-xtream:compileDebugKotlin \
          :pipeline:xtream:compileDebugKotlin \
          :feature:detail:compileDebugKotlin --no-daemon
```

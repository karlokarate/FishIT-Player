# Commit: dc1f1506

**Message:** feat(data-home): Wire real data for Continue Watching + Recently Added

**Author:** karlokarate
**Date:** Mon Dec 22 11:46:47 2025 +0000

## Summary

Wire real ObjectBox data for Continue Watching and Recently Added rows in HomeContentRepositoryAdapter. Removes `emptyFlow()` placeholders and implements proper reactive queries.

## Changed Files

| File | Change | Description |
|------|--------|-------------|
| infra/data-home/build.gradle.kts | MOD | +1 dependency: core:persistence |
| HomeContentRepositoryAdapter.kt | MOD | +158/-11 lines: Real data implementation |

## Key Changes

### 1. New Dependencies
- Added `core:persistence` for ObjectBox access

### 2. Continue Watching Implementation
- Query `ObxCanonicalResumeMark` with `positionPercent > 0` AND `isCompleted = false`
- Join with `ObxCanonicalMedia` for full metadata
- Sort by `updatedAt DESC` (most recently watched first)
- Limit: 30 items (FireTV-safe)

### 3. Recently Added Implementation
- Query `ObxCanonicalMedia` sorted by `createdAt DESC`
- Limit: 60 items (FireTV-safe)
- `isNew` flag for items added within last 7 days

### 4. New Extension Functions
- `String.toMediaType()` - Converts kind string to MediaType
- `String.toSourceType()` - Converts lastSourceType to SourceType
- `ObxCanonicalMedia.toHomeMediaItem()` - Mapping for Recently Added

---

## Full Diff

```diff
diff --git a/infra/data-home/build.gradle.kts b/infra/data-home/build.gradle.kts
index a75f67c8..2b385b56 100644
--- a/infra/data-home/build.gradle.kts
+++ b/infra/data-home/build.gradle.kts
@@ -26,6 +26,7 @@ android {
 dependencies {
     // Core dependencies
     implementation(project(":core:model"))
+    implementation(project(":core:persistence"))  // For ObjectBox canonical media queries
     implementation(project(":infra:logging"))
     implementation(project(":feature:home"))  // For HomeContentRepository interface
```

```diff
diff --git a/infra/data-home/src/main/java/com/fishit/player/infra/data/home/HomeContentRepositoryAdapter.kt b/infra/data-home/src/main/java/com/fishit/player/infra/data/home/HomeContentRepositoryAdapter.kt
index d2e0c96b..0d297a5c 100644
--- a/infra/data-home/src/main/java/com/fishit/player/infra/data/home/HomeContentRepositoryAdapter.kt
+++ b/infra/data-home/src/main/java/com/fishit/player/infra/data/home/HomeContentRepositoryAdapter.kt
@@ -1,16 +1,26 @@
 package com.fishit.player.infra.data.home
 
 import com.fishit.player.core.model.ImageRef
+import com.fishit.player.core.model.MediaKind
+import com.fishit.player.core.model.MediaType
 import com.fishit.player.core.model.RawMediaMetadata
+import com.fishit.player.core.model.SourceType
+import com.fishit.player.core.persistence.obx.ObxCanonicalMedia
+import com.fishit.player.core.persistence.obx.ObxCanonicalMedia_
+import com.fishit.player.core.persistence.obx.ObxCanonicalResumeMark
+import com.fishit.player.core.persistence.obx.ObxCanonicalResumeMark_
 import com.fishit.player.feature.home.domain.HomeContentRepository
 import com.fishit.player.feature.home.domain.HomeMediaItem
 import com.fishit.player.infra.data.telegram.TelegramContentRepository
 import com.fishit.player.infra.data.xtream.XtreamCatalogRepository
 import com.fishit.player.infra.data.xtream.XtreamLiveRepository
 import com.fishit.player.infra.logging.UnifiedLog
+import io.objectbox.BoxStore
+import io.objectbox.kotlin.boxFor
+import io.objectbox.kotlin.toFlow
+import kotlinx.coroutines.ExperimentalCoroutinesApi
 import kotlinx.coroutines.flow.Flow
 import kotlinx.coroutines.flow.catch
-import kotlinx.coroutines.flow.emptyFlow
 import kotlinx.coroutines.flow.map
 import javax.inject.Inject
 import javax.inject.Singleton
@@ -38,31 +48,79 @@ import javax.inject.Singleton
  */
 @Singleton
 class HomeContentRepositoryAdapter @Inject constructor(
+    private val boxStore: BoxStore,
     private val telegramContentRepository: TelegramContentRepository,
     private val xtreamCatalogRepository: XtreamCatalogRepository,
     private val xtreamLiveRepository: XtreamLiveRepository,
 ) : HomeContentRepository {
 
+    private val canonicalMediaBox by lazy { boxStore.boxFor<ObxCanonicalMedia>() }
+    private val canonicalResumeBox by lazy { boxStore.boxFor<ObxCanonicalResumeMark>() }
+
     /**
      * Observe items the user has started but not finished watching.
-     * 
-     * TODO: Wire to WatchHistoryRepository once implemented.
-     * For now returns empty flow to enable type-safe combine in HomeViewModel.
+     *
+     * **Implementation:**
+     * - Queries ObxCanonicalResumeMark for items with positionPercent > 0 AND isCompleted = false
+     * - Joins with ObxCanonicalMedia to get full metadata
+     * - Sorted by updatedAt DESC (most recently watched first)
+     * - Limited to [CONTINUE_WATCHING_LIMIT] items (FireTV-safe)
+     *
+     * **Profile Note:**
+     * Currently uses profileId = 0 (default profile). Multi-profile support will require
+     * passing the active profileId from the UI layer.
      */
+    @OptIn(ExperimentalCoroutinesApi::class)
     override fun observeContinueWatching(): Flow<List<HomeMediaItem>> {
-        // TODO: Implement with WatchHistoryRepository
-        return emptyFlow()
+        // Query resume marks: position > 0 AND not completed, sorted by last watched
+        // Use greaterThan with Double conversion for ObjectBox Float property
+        val query = canonicalResumeBox.query()
+            .greater(ObxCanonicalResumeMark_.positionPercent, 0.0)
+            .equal(ObxCanonicalResumeMark_.isCompleted, false)
+            .orderDesc(ObxCanonicalResumeMark_.updatedAt)
+            .build()
+
+        return query.subscribe().toFlow()
+            .map { resumeMarks ->
+                resumeMarks
+                    .take(CONTINUE_WATCHING_LIMIT)
+                    .mapNotNull { resume -> mapResumeToHomeMediaItem(resume) }
+            }
+            .catch { throwable ->
+                UnifiedLog.e(TAG, throwable) { "Failed to observe continue watching" }
+                emit(emptyList())
+            }
     }
 
     /**
      * Observe recently added items across all sources.
-     * 
-     * TODO: Wire to combined query sorting by addedTimestamp.
-     * For now returns empty flow to enable type-safe combine in HomeViewModel.
+     *
+     * **Implementation:**
+     * - Queries ObxCanonicalMedia sorted by createdAt DESC
+     * - Limited to [RECENTLY_ADDED_LIMIT] items (FireTV-safe)
+     * - Maps to HomeMediaItem with isNew = true for items added in last 7 days
      */
+    @OptIn(ExperimentalCoroutinesApi::class)
     override fun observeRecentlyAdded(): Flow<List<HomeMediaItem>> {
-        // TODO: Implement with combined recently-added query
-        return emptyFlow()
+        val query = canonicalMediaBox.query()
+            .orderDesc(ObxCanonicalMedia_.createdAt)
+            .build()
+
+        return query.subscribe().toFlow()
+            .map { canonicalMediaList ->
+                val now = System.currentTimeMillis()
+                val sevenDaysAgo = now - SEVEN_DAYS_MS
+                
+                canonicalMediaList
+                    .take(RECENTLY_ADDED_LIMIT)
+                    .map { canonical -> 
+                        canonical.toHomeMediaItem(isNew = canonical.createdAt >= sevenDaysAgo)
+                    }
+            }
+            .catch { throwable ->
+                UnifiedLog.e(TAG, throwable) { "Failed to observe recently added" }
+                emit(emptyList())
+            }
     }
 
     override fun observeTelegramMedia(): Flow<List<HomeMediaItem>> {
@@ -103,6 +161,46 @@ class HomeContentRepositoryAdapter @Inject constructor(
 
     companion object {
         private const val TAG = "HomeContentRepositoryAdapter"
+        
+        /** Maximum items for Continue Watching row (FireTV-safe) */
+        private const val CONTINUE_WATCHING_LIMIT = 30
+        
+        /** Maximum items for Recently Added row (FireTV-safe) */
+        private const val RECENTLY_ADDED_LIMIT = 60
+        
+        /** Seven days in milliseconds for "new" badge */
+        private const val SEVEN_DAYS_MS = 7 * 24 * 60 * 60 * 1000L
+    }
+
+    /**
+     * Maps an ObxCanonicalResumeMark to HomeMediaItem by joining with canonical media.
+     *
+     * @param resume The resume mark from persistence
+     * @return HomeMediaItem with resume data, or null if canonical media not found
+     */
+    private fun mapResumeToHomeMediaItem(resume: ObxCanonicalResumeMark): HomeMediaItem? {
+        // Find the canonical media by key
+        val canonical = canonicalMediaBox
+            .query(ObxCanonicalMedia_.canonicalKey.equal(resume.canonicalKey))
+            .build()
+            .findFirst() ?: return null
+
+        return HomeMediaItem(
+            id = canonical.canonicalKey,
+            title = canonical.canonicalTitle,
+            poster = canonical.poster,
+            placeholderThumbnail = canonical.thumbnail,
+            backdrop = canonical.backdrop,
+            mediaType = canonical.kind.toMediaType(),
+            sourceType = resume.lastSourceType?.toSourceType() ?: SourceType.OTHER,
+            resumePosition = resume.positionMs,
+            duration = resume.durationMs,
+            isNew = false, // Continue watching items are not "new"
+            year = canonical.year,
+            rating = canonical.rating?.toFloat(),
+            navigationId = canonical.canonicalKey,
+            navigationSource = resume.lastSourceType?.toSourceType() ?: SourceType.OTHER
+        )
     }
 }
 
@@ -129,3 +227,51 @@ private fun RawMediaMetadata.toHomeMediaItem(): HomeMediaItem {
         navigationSource = sourceType
     )
 }
+
+/**
+ * Maps ObxCanonicalMedia to HomeMediaItem.
+ *
+ * Used for "Recently Added" items where we don't have resume data.
+ *
+ * @param isNew Whether to mark this item as newly added
+ */
+private fun ObxCanonicalMedia.toHomeMediaItem(isNew: Boolean = false): HomeMediaItem {
+    return HomeMediaItem(
+        id = canonicalKey,
+        title = canonicalTitle,
+        poster = poster,
+        placeholderThumbnail = thumbnail,
+        backdrop = backdrop,
+        mediaType = kind.toMediaType(),
+        sourceType = SourceType.OTHER, // Canonical items aggregate multiple sources
+        resumePosition = 0L,
+        duration = durationMs ?: 0L,
+        isNew = isNew,
+        year = year,
+        rating = rating?.toFloat(),
+        navigationId = canonicalKey,
+        navigationSource = SourceType.OTHER
+    )
+}
+
+/**
+ * Converts ObxCanonicalMedia.kind string to MediaType.
+ */
+private fun String.toMediaType(): MediaType = when (this.lowercase()) {
+    "movie" -> MediaType.MOVIE
+    "episode" -> MediaType.SERIES_EPISODE
+    "series" -> MediaType.SERIES
+    "live" -> MediaType.LIVE
+    else -> MediaType.UNKNOWN
+}
+
+/**
+ * Converts source type string (from ObxCanonicalResumeMark.lastSourceType) to SourceType.
+ */
+private fun String.toSourceType(): SourceType = when (this.uppercase()) {
+    "TELEGRAM" -> SourceType.TELEGRAM
+    "XTREAM" -> SourceType.XTREAM
+    "IO", "LOCAL" -> SourceType.IO
+    "AUDIOBOOK" -> SourceType.AUDIOBOOK
+    else -> SourceType.OTHER
+}
```

## Architecture Notes

- Uses ObjectBox `query.subscribe().toFlow()` pattern for reactive updates
- FireTV-safe limits: 30 Continue Watching, 60 Recently Added
- Proper error handling with `.catch { emit(emptyList()) }`
- No pipeline DTOs in data layer (architecture compliant)

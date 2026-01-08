# ObjectBox Compile-Time Artifacts

## Overview

ObjectBox is a high-performance embedded database used in FishIT-Player for persistent storage. During build time, ObjectBox generates Java code from Kotlin entity classes annotated with `@Entity`. This generated code includes:

- **MyObjectBox.java** - The main entry point for initializing the ObjectBox store
- **Entity Meta Classes** (`*_.java`) - Property metadata and query builders for each entity
- **Cursor Classes** (`*Cursor.java`) - Efficient data access for each entity
- **Model JSON** - Schema definition with stable IDs for entities, properties, and indexes

This document catalogs all ObjectBox compile-time generated artifacts for the v2 architecture.

## ObjectBox Configuration

- **Version:** 5.0.1
- **Modules using ObjectBox:** 
  - `:core:persistence` (primary - v2 architecture)
  - `:legacy:v1-app:app` (legacy - v1 architecture)
- **Plugin configuration:**
  - Applied via `id("io.objectbox") version "5.0.1"` in `core/persistence/build.gradle.kts`
  - KAPT processor required: `kotlin("kapt")`
  - Generated sources added to compilation: `build/generated/source/kapt/debug`
  - Plugin resolution mapping in `settings.gradle.kts`

### Build Configuration Details

From `core/persistence/build.gradle.kts`:

```kotlin
plugins {
    kotlin("kapt") // Required for ObjectBox code generation
    id("io.objectbox") version "5.0.1"
}

// Include kapt-generated ObjectBox sources in Java compilation
sourceSets {
    getByName("debug") {
        java.srcDir("build/generated/source/kapt/debug")
    }
    getByName("release") {
        java.srcDir("build/generated/source/kapt/release")
    }
}

// Force Java compilation to depend on kapt
tasks.matching { it.name == "compileDebugJavaWithJavac" }.configureEach {
    dependsOn("kaptDebugKotlin")
}
```

## Generated Files

### MyObjectBox

The main entry point for ObjectBox initialization. This file is auto-generated and contains the complete schema definition.

| File | Path | Module | Description |
|------|------|--------|-------------|
| `MyObjectBox.java` | `build/generated/source/kapt/debug/com/fishit/player/core/persistence/obx/` | `:core:persistence` | Entry point with `builder()` method, contains schema definition |

**Key Details:**
- Contains `builder()` static method that returns a `BoxStoreBuilder`
- Embeds binary model data via `getModel()` method
- Registers all 23 entity types
- Model metadata:
  - Last Entity ID: 23
  - Last Index ID: 105
  - Last Relation ID: 0

### Entity Meta Classes

ObjectBox generates a meta class (`*_.java`) for each `@Entity` annotated class. These classes contain:
- Property IDs and metadata
- Query builder methods
- Type-safe property references for queries

| File | Path | Module | Source Entity |
|------|------|--------|---------------|
| `ObxCanonicalMedia_.java` | `build/generated/source/kapt/debug/com/fishit/player/core/persistence/obx/` | `:core:persistence` | `ObxCanonicalMedia` |
| `ObxMediaSourceRef_.java` | `build/generated/source/kapt/debug/com/fishit/player/core/persistence/obx/` | `:core:persistence` | `ObxMediaSourceRef` |
| `ObxCanonicalResumeMark_.java` | `build/generated/source/kapt/debug/com/fishit/player/core/persistence/obx/` | `:core:persistence` | `ObxCanonicalResumeMark` |
| `ObxCategory_.java` | `build/generated/source/kapt/debug/com/fishit/player/core/persistence/obx/` | `:core:persistence` | `ObxCategory` |
| `ObxLive_.java` | `build/generated/source/kapt/debug/com/fishit/player/core/persistence/obx/` | `:core:persistence` | `ObxLive` |
| `ObxVod_.java` | `build/generated/source/kapt/debug/com/fishit/player/core/persistence/obx/` | `:core:persistence` | `ObxVod` |
| `ObxSeries_.java` | `build/generated/source/kapt/debug/com/fishit/player/core/persistence/obx/` | `:core:persistence` | `ObxSeries` |
| `ObxEpisode_.java` | `build/generated/source/kapt/debug/com/fishit/player/core/persistence/obx/` | `:core:persistence` | `ObxEpisode` |
| `ObxEpgNowNext_.java` | `build/generated/source/kapt/debug/com/fishit/player/core/persistence/obx/` | `:core:persistence` | `ObxEpgNowNext` |
| `ObxProfile_.java` | `build/generated/source/kapt/debug/com/fishit/player/core/persistence/obx/` | `:core:persistence` | `ObxProfile` |
| `ObxProfilePermissions_.java` | `build/generated/source/kapt/debug/com/fishit/player/core/persistence/obx/` | `:core:persistence` | `ObxProfilePermissions` |
| `ObxKidContentAllow_.java` | `build/generated/source/kapt/debug/com/fishit/player/core/persistence/obx/` | `:core:persistence` | `ObxKidContentAllow` |
| `ObxKidCategoryAllow_.java` | `build/generated/source/kapt/debug/com/fishit/player/core/persistence/obx/` | `:core:persistence` | `ObxKidCategoryAllow` |
| `ObxKidContentBlock_.java` | `build/generated/source/kapt/debug/com/fishit/player/core/persistence/obx/` | `:core:persistence` | `ObxKidContentBlock` |
| `ObxScreenTimeEntry_.java` | `build/generated/source/kapt/debug/com/fishit/player/core/persistence/obx/` | `:core:persistence` | `ObxScreenTimeEntry` |
| `ObxTelegramMessage_.java` | `build/generated/source/kapt/debug/com/fishit/player/core/persistence/obx/` | `:core:persistence` | `ObxTelegramMessage` |
| `ObxIndexProvider_.java` | `build/generated/source/kapt/debug/com/fishit/player/core/persistence/obx/` | `:core:persistence` | `ObxIndexProvider` |
| `ObxIndexYear_.java` | `build/generated/source/kapt/debug/com/fishit/player/core/persistence/obx/` | `:core:persistence` | `ObxIndexYear` |
| `ObxIndexGenre_.java` | `build/generated/source/kapt/debug/com/fishit/player/core/persistence/obx/` | `:core:persistence` | `ObxIndexGenre` |
| `ObxIndexLang_.java` | `build/generated/source/kapt/debug/com/fishit/player/core/persistence/obx/` | `:core:persistence` | `ObxIndexLang` |
| `ObxIndexQuality_.java` | `build/generated/source/kapt/debug/com/fishit/player/core/persistence/obx/` | `:core:persistence` | `ObxIndexQuality` |
| `ObxSeasonIndex_.java` | `build/generated/source/kapt/debug/com/fishit/player/core/persistence/obx/` | `:core:persistence` | `ObxSeasonIndex` |
| `ObxEpisodeIndex_.java` | `build/generated/source/kapt/debug/com/fishit/player/core/persistence/obx/` | `:core:persistence` | `ObxEpisodeIndex` |

**Total: 23 meta classes**

### Cursor Classes

ObjectBox generates cursor classes for efficient data access. Each entity gets a corresponding cursor class.

| File | Path | Module | Source Entity |
|------|------|--------|---------------|
| `ObxCanonicalMediaCursor.java` | `build/generated/source/kapt/debug/com/fishit/player/core/persistence/obx/` | `:core:persistence` | `ObxCanonicalMedia` |
| `ObxMediaSourceRefCursor.java` | `build/generated/source/kapt/debug/com/fishit/player/core/persistence/obx/` | `:core:persistence` | `ObxMediaSourceRef` |
| `ObxCanonicalResumeMarkCursor.java` | `build/generated/source/kapt/debug/com/fishit/player/core/persistence/obx/` | `:core:persistence` | `ObxCanonicalResumeMark` |
| `ObxCategoryCursor.java` | `build/generated/source/kapt/debug/com/fishit/player/core/persistence/obx/` | `:core:persistence` | `ObxCategory` |
| `ObxLiveCursor.java` | `build/generated/source/kapt/debug/com/fishit/player/core/persistence/obx/` | `:core:persistence` | `ObxLive` |
| `ObxVodCursor.java` | `build/generated/source/kapt/debug/com/fishit/player/core/persistence/obx/` | `:core:persistence` | `ObxVod` |
| `ObxSeriesCursor.java` | `build/generated/source/kapt/debug/com/fishit/player/core/persistence/obx/` | `:core:persistence` | `ObxSeries` |
| `ObxEpisodeCursor.java` | `build/generated/source/kapt/debug/com/fishit/player/core/persistence/obx/` | `:core:persistence` | `ObxEpisode` |
| `ObxEpgNowNextCursor.java` | `build/generated/source/kapt/debug/com/fishit/player/core/persistence/obx/` | `:core:persistence` | `ObxEpgNowNext` |
| `ObxProfileCursor.java` | `build/generated/source/kapt/debug/com/fishit/player/core/persistence/obx/` | `:core:persistence` | `ObxProfile` |
| `ObxProfilePermissionsCursor.java` | `build/generated/source/kapt/debug/com/fishit/player/core/persistence/obx/` | `:core:persistence` | `ObxProfilePermissions` |
| `ObxKidContentAllowCursor.java` | `build/generated/source/kapt/debug/com/fishit/player/core/persistence/obx/` | `:core:persistence` | `ObxKidContentAllow` |
| `ObxKidCategoryAllowCursor.java` | `build/generated/source/kapt/debug/com/fishit/player/core/persistence/obx/` | `:core:persistence` | `ObxKidCategoryAllow` |
| `ObxKidContentBlockCursor.java` | `build/generated/source/kapt/debug/com/fishit/player/core/persistence/obx/` | `:core:persistence` | `ObxKidContentBlock` |
| `ObxScreenTimeEntryCursor.java` | `build/generated/source/kapt/debug/com/fishit/player/core/persistence/obx/` | `:core:persistence` | `ObxScreenTimeEntry` |
| `ObxTelegramMessageCursor.java` | `build/generated/source/kapt/debug/com/fishit/player/core/persistence/obx/` | `:core:persistence` | `ObxTelegramMessage` |
| `ObxIndexProviderCursor.java` | `build/generated/source/kapt/debug/com/fishit/player/core/persistence/obx/` | `:core:persistence` | `ObxIndexProvider` |
| `ObxIndexYearCursor.java` | `build/generated/source/kapt/debug/com/fishit/player/core/persistence/obx/` | `:core:persistence` | `ObxIndexYear` |
| `ObxIndexGenreCursor.java` | `build/generated/source/kapt/debug/com/fishit/player/core/persistence/obx/` | `:core:persistence` | `ObxIndexGenre` |
| `ObxIndexLangCursor.java` | `build/generated/source/kapt/debug/com/fishit/player/core/persistence/obx/` | `:core:persistence` | `ObxIndexLang` |
| `ObxIndexQualityCursor.java` | `build/generated/source/kapt/debug/com/fishit/player/core/persistence/obx/` | `:core:persistence` | `ObxIndexQuality` |
| `ObxSeasonIndexCursor.java` | `build/generated/source/kapt/debug/com/fishit/player/core/persistence/obx/` | `:core:persistence` | `ObxSeasonIndex` |
| `ObxEpisodeIndexCursor.java` | `build/generated/source/kapt/debug/com/fishit/player/core/persistence/obx/` | `:core:persistence` | `ObxEpisodeIndex` |

**Total: 23 cursor classes**

### Model JSON

The ObjectBox model definition is stored in JSON format. This file contains stable IDs for entities, properties, indexes, and relations, ensuring schema consistency across builds.

- **Location:** `core/persistence/objectbox-models/default.json`
- **Version Control:** ✅ **MUST** be committed to VCS (Git)
- **Purpose:** Maintains stable IDs to prevent schema migration issues

#### Structure

```json
{
  "_note1": "KEEP THIS FILE! Check it into a version control system (VCS) like git.",
  "_note2": "ObjectBox manages crucial IDs for your object model. See docs for details.",
  "_note3": "If you have VCS merge conflicts, you must resolve them according to ObjectBox docs.",
  "entities": [ ... ],
  "lastEntityId": "23:3164574390035043506",
  "lastIndexId": "105:6637791208546991300",
  "lastRelationId": "0:0",
  "lastSequenceId": "0:0",
  "modelVersion": 5,
  "modelVersionParserMinimum": 5,
  "version": 1
}
```

#### Key Metadata

- **Number of entities:** 23
- **Last Entity ID:** 23 (UID: 3164574390035043506)
- **Last Index ID:** 105 (UID: 6637791208546991300)
- **Last Relation ID:** 0 (UID: 0)
- **Model Version:** 5
- **Schema Version:** 1

#### Entity List

The model defines 23 entities:

1. `ObxCanonicalMedia` - Canonical media identity (movies/episodes)
2. `ObxMediaSourceRef` - Links canonical media to pipeline sources
3. `ObxCanonicalResumeMark` - Resume positions per profile
4. `ObxCategory` - Content categories (live/vod/series)
5. `ObxLive` - Live TV streams
6. `ObxVod` - Video-on-demand content
7. `ObxSeries` - Series metadata
8. `ObxEpisode` - Episode entries
9. `ObxEpgNowNext` - EPG current/next program data
10. `ObxProfile` - User profiles (adult/kid/guest)
11. `ObxProfilePermissions` - Per-profile permissions
12. `ObxKidContentAllow` - Whitelisted content for kids
13. `ObxKidCategoryAllow` - Whitelisted categories for kids
14. `ObxKidContentBlock` - Blacklisted content for kids
15. `ObxScreenTimeEntry` - Screen time tracking per profile/day
16. `ObxTelegramMessage` - Telegram media messages
17. `ObxIndexProvider` - Aggregated provider counts
18. `ObxIndexYear` - Aggregated year counts
19. `ObxIndexGenre` - Aggregated genre counts
20. `ObxIndexLang` - Aggregated language counts
21. `ObxIndexQuality` - Aggregated quality counts
22. `ObxSeasonIndex` - Season metadata for series
23. `ObxEpisodeIndex` - Episode index for paged loading

#### Relations

Currently, the schema has **1 relation** (backlink):
- `ObxCanonicalMedia.sources` → `ToMany<ObxMediaSourceRef>` (via backlink to `canonicalMedia`)

This is tracked in the model as `lastRelationId: "0:0"` (no explicit relation tables, only backlinks).

## Build Commands

To trigger ObjectBox code generation:

```bash
# Generate ObjectBox code for debug variant
./gradlew :core:persistence:kaptDebugKotlin

# Generate ObjectBox code for release variant
./gradlew :core:persistence:kaptReleaseKotlin

# Full build (includes ObjectBox generation)
./gradlew :core:persistence:assembleDebug

# Clean and rebuild (regenerates all artifacts)
./gradlew :core:persistence:clean :core:persistence:assembleDebug
```

### Gradle Task Dependencies

ObjectBox code generation runs automatically during:
- `kaptDebugKotlin` / `kaptReleaseKotlin` - KAPT annotation processing
- `compileDebugJavaWithJavac` - Java compilation (depends on kapt)
- `assembleDebug` / `assembleRelease` - Full assembly

### Verification

To verify ObjectBox artifacts were generated:

```bash
# Check for MyObjectBox.java
ls core/persistence/build/generated/source/kapt/debug/com/fishit/player/core/persistence/obx/MyObjectBox.java

# Count generated files (should be 47: 1 MyObjectBox + 23 meta + 23 cursor)
ls core/persistence/build/generated/source/kapt/debug/com/fishit/player/core/persistence/obx/ | wc -l

# Verify model JSON exists
cat core/persistence/objectbox-models/default.json | grep lastEntityId
```

## Notes

### Version Control

⚠️ **CRITICAL:** The `objectbox-models/default.json` file **MUST** be committed to version control.

- ObjectBox uses stable UIDs to identify entities, properties, and indexes
- Deleting or losing this file will cause schema migration errors
- VCS merge conflicts in this file must be resolved according to [ObjectBox merge conflict docs](https://docs.objectbox.io/advanced/data-model-updates#resolving-merge-conflicts)

### Generated Code Location

Generated code is placed in:
- Debug: `build/generated/source/kapt/debug/`
- Release: `build/generated/source/kapt/release/`

These directories are:
- ❌ **NOT committed** to version control (in `.gitignore`)
- ✅ Automatically regenerated during build
- ✅ Added to Java source sets via `build.gradle.kts` configuration

### KAPT Performance

ObjectBox code generation adds ~5-10 seconds to clean builds. For faster incremental builds:
- KAPT uses Gradle's build cache
- Only changed entities trigger regeneration
- Use `--no-build-cache` to force full regeneration if needed

### Entity Source Files

All entity source files are located in:
- `core/persistence/src/main/java/com/fishit/player/core/persistence/obx/ObxEntities.kt`
- `core/persistence/src/main/java/com/fishit/player/core/persistence/obx/ObxCanonicalEntities.kt`

Modifying these files will trigger ObjectBox code regeneration on next build.

### Property Converters

ObjectBox supports custom property converters for complex types. This project uses:
- `ImageRefConverter` - Converts `ImageRef` sealed interface to JSON string
- `MediaTypeConverter` - Converts `MediaType` enum to string
- `PipelineIdTagConverter` - Converts `PipelineIdTag` enum to string

Converters are located in `core/persistence/src/main/java/com/fishit/player/core/persistence/obx/converter/`.

### Relations and Backlinks

ObjectBox supports:
- **ToOne** - One-to-one relation
- **ToMany** - One-to-many relation
- **@Backlink** - Reverse navigation (no extra table)

Current usage:
- `ObxCanonicalMedia.sources` - ToMany backlink to `ObxMediaSourceRef`

### Index Count

The model defines **105 indexes** across all entities, including:
- Unique indexes for primary keys
- Non-unique indexes for frequently queried properties
- Composite indexes (if defined)

Indexes improve query performance but increase storage overhead.

## Summary

- **Total Generated Files:** 47
  - 1 MyObjectBox entry point
  - 23 entity meta classes (`*_.java`)
  - 23 cursor classes (`*Cursor.java`)
- **ObjectBox Version:** 5.0.1
- **Entities:** 23
- **Indexes:** 105
- **Relations:** 1 (backlink)
- **Model JSON:** `core/persistence/objectbox-models/default.json` (✅ version controlled)

This completes Phase 1 of the ObjectBox Data Layers Map (Issue #612).

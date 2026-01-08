# ObjectBox Phase 4 - Query Patterns & Relations Extraction

**Generated:** 2026-01-08
**Issue:** #612 - ObjectBox Data Layers Map (Phase 4)

## Overview

This directory contains the intermediate outputs from Phase 4 of the ObjectBox documentation effort. The analysis extracted all ObjectBox query patterns, relations, and data join patterns from the codebase.

## Files

### 1. `query_usage.json` (63KB)
Complete inventory of all ObjectBox queries in the codebase.

**Contents:**
- **queries**: Array of 128 query patterns with:
  - Entity being queried
  - File path and line number
  - Class and method context
  - Query type (find, findFirst, findUnique, count, property)
  - Conditions (equal, contains, greater, less, oneOf)
  - Ordering (ASC/DESC)
  - Purpose description
  
- **queryStatistics**: Summary metrics
  - Total queries: 128
  - Queries by entity (14 entities)
  - Queries by type (find: 70, findFirst: 43, count: 15)
  - Indexed vs non-indexed queries
  
- **entityPatterns**: Most common query pattern per entity

**Key Findings:**
- **Most queried entity**: `ObxCanonicalMedia` (27 queries)
- **Indexed queries**: 55 (43%)
- **Non-indexed queries**: 73 (57%)
- **Most common operations**: find (55%), findFirst (34%), count (12%)

### 2. `relationships.json` (8.6KB)
Complete inventory of ObjectBox relations and manual joins.

**Contents:**
- **relations**: 2 ObjectBox relations
  - `ObxCanonicalMedia.sources` → `ObxMediaSourceRef` (ToMany)
  - `ObxMediaSourceRef.canonicalMedia` → `ObxCanonicalMedia` (ToOne)
  
- **backlinks**: 1 backlink
  - `ObxCanonicalMedia.sources` (backlinked from `ObxMediaSourceRef.canonicalMedia`)
  
- **manualJoins**: 14 foreign key join patterns
  - Series → Episodes (via seriesId)
  - Season/Episode Index → Series (via seriesId)
  - Content → Category (via categoryId)
  - EPG → Live Stream (via streamId)
  - Resume Marks → CanonicalMedia (via canonicalKey)
  - Profile Permissions → Profile (via profileId)
  - Screen Time → Profile (via kidProfileId)
  - And more...

**Key Findings:**
- Only 1 proper ToOne/ToMany relation pair in the entire schema
- Heavy reliance on manual foreign key joins (14 patterns)
- Most common join field: `seriesId` (used in 4 different joins)
- Canonical system uses `canonicalKey` as join field (not @Id)

### 3. `entity_access_patterns.json` (15KB)
Access pattern analysis per entity.

**Contents:**
- **entities**: Array of 14 entities with:
  - Read locations (files that query the entity)
  - Write locations (files that insert/update/delete)
  - Query count
  - Relation access count
  - Most common query pattern
  
- **summary**: Overall statistics
  - Total entities: 14
  - Entities with queries: 14 (100%)
  - Entities with writes: 14 (100%)
  - Most queried: `ObxCanonicalMedia`
  - Most written: varies by entity

**Key Findings:**
- All entities are actively used (100% query coverage)
- Primary write locations: Repository implementations in `infra/data-*`
- Primary read locations: Repository implementations and adapters

## Entity Inventory

| Entity | Query Count | Primary Use Case |
|--------|-------------|------------------|
| `ObxCanonicalMedia` | 27 | Cross-pipeline media unification |
| `ObxMediaSourceRef` | 7 | Source-to-canonical linking |
| `ObxCanonicalResumeMark` | 6 | Resume position tracking |
| `ObxEpisode` | 5 | Xtream series episodes |
| `ObxSeries` | 10 | Xtream series metadata |
| `ObxVod` | 20 | Xtream VOD items |
| `ObxLive` | 10 | Xtream live streams |
| `ObxSeasonIndex` | 6 | Season metadata cache |
| `ObxEpisodeIndex` | 11 | Episode metadata cache |
| `ObxCategory` | 10 | Content categorization |
| `ObxTelegramMessage` | 9 | Telegram media items |
| `ObxProfile` | 2 | User profiles |
| `ObxScreenTimeEntry` | 4 | Kids screen time tracking |
| `ObxEpgNowNext` | 1 | Live TV EPG data |

## Query Pattern Analysis

### Most Common Patterns

1. **Equal on indexed field** (55 queries)
   ```kotlin
   box.query(Entity_.field.equal(value))
   ```
   Used for: Lookups by ID, foreign key joins

2. **Order by name** (23 queries)
   ```kotlin
   box.query().order(Entity_.nameLower)
   ```
   Used for: Alphabetically sorted lists

3. **Compound queries** (18 queries)
   ```kotlin
   box.query(Entity_.field1.equal(v1).and(Entity_.field2.equal(v2)))
   ```
   Used for: Multi-field lookups (series+season+episode, chat+message)

4. **Count queries** (11 queries)
   ```kotlin
   box.query().count()
   ```
   Used for: Statistics, existence checks

### Index Utilization

**Fields with @Index annotation:**
- `canonicalKey`, `sourceId` (CanonicalMedia system)
- `seriesId`, `vodId`, `streamId` (Xtream IDs)
- `chatId`, `messageId`, `remoteId` (Telegram IDs)
- `categoryId`, `profileId`, `kidProfileId` (Foreign keys)
- `year`, `kind`, `mediaType` (Filtering fields)
- `nameLower`, `captionLower` (Search fields)

**Coverage:**
- 43% of queries use indexed fields
- 57% use non-indexed fields (mostly string searches, ordering)

## Manual Join Patterns

### Pattern 1: Parent-Child Joins
```kotlin
// Series → Episodes
episodeBox.query(ObxEpisode_.seriesId.equal(seriesId))

// Season Index → Series
seasonIndexBox.query(ObxSeasonIndex_.seriesId.equal(seriesId))
```

### Pattern 2: Category Filtering
```kotlin
// VOD by category
vodBox.query(ObxVod_.categoryId.equal(categoryId))

// Live by category
liveBox.query(ObxLive_.categoryId.equal(categoryId))
```

### Pattern 3: Composite Key Lookups
```kotlin
// Telegram message lookup
messageBox.query(
  ObxTelegramMessage_.chatId.equal(chatId)
    .and(ObxTelegramMessage_.messageId.equal(messageId))
)

// Episode by series+season+episode
episodeIndexBox.query(
  ObxEpisodeIndex_.seriesId.equal(seriesId)
    .and(ObxEpisodeIndex_.seasonNumber.equal(season))
    .and(ObxEpisodeIndex_.episodeNumber.equal(episode))
)
```

### Pattern 4: String-Based Joins
```kotlin
// Resume marks → Canonical media (via canonicalKey string)
resumeBox.query(
  ObxCanonicalResumeMark_.canonicalKey.equal(canonicalKey)
)
```

## Relation Usage

### ToOne Relation: `ObxMediaSourceRef.canonicalMedia`
**Declaration:**
```kotlin
lateinit var canonicalMedia: io.objectbox.relation.ToOne<ObxCanonicalMedia>
```

**Usage locations:**
1. `ObxCanonicalMediaRepository.kt`
   - Setting relation: `sourceRef.canonicalMedia.target = canonical`
   - Reading relation: `val canonical = sourceRef.canonicalMedia.target`

2. `HomeContentRepositoryAdapter.kt`
   - Accessing target ID: `sourceRef.canonicalMedia.targetId`

**Pattern:** Always use `.target` property to access related entity

### ToMany Backlink: `ObxCanonicalMedia.sources`
**Declaration:**
```kotlin
@Backlink(to = "canonicalMedia")
lateinit var sources: ToMany<ObxMediaSourceRef>
```

**Usage locations:**
1. `feature/detail/UnifiedDetailViewModel.kt`
   - Iterating sources: `media.sources.find { it.sourceId == key }`
   
2. `feature/detail/SourceSelection.kt`
   - Source selection logic: `sources.maxByOrNull { it.priority }`

**Pattern:** Backlink provides automatic reverse navigation without explicit queries

## Repository Hotspots

### High Query Activity
1. **ObxCanonicalMediaRepository** (27 queries)
   - Central hub for canonical media system
   - Most complex query patterns
   - Heavy use of compound conditions

2. **ObxXtreamCatalogRepository** (20 queries for ObxVod alone, plus ObxSeries: 10, ObxEpisode: 5, ObxLive: 10, ObxCategory: 10)
   - Xtream content queries
   - Category-based filtering
   - Series episode lookups

3. **ObxXtreamSeriesIndexRepository** (11 queries for ObxEpisodeIndex, plus ObxSeasonIndex: 6)
   - Season/episode index management
   - Compound key lookups
   - TTL-based cleanup queries

### Write Operations
**Primary write locations:**
- `ObxCanonicalMediaRepository`: Canonical media + source refs
- `ObxXtreamCatalogRepository`: VOD, Series, Episodes, Live
- `ObxTelegramContentRepository`: Telegram messages
- `ObxProfileRepository`: Profiles and permissions
- `ObxScreenTimeRepository`: Screen time tracking

## Performance Observations

### Strengths
1. **Indexed primary lookups**: All entity-by-ID queries use indexes
2. **Efficient joins**: Foreign key fields are indexed
3. **Sorted queries**: Name-based sorting uses indexed `nameLower` fields

### Potential Concerns
1. **57% non-indexed queries**: Many queries on non-indexed fields
2. **String-based joins**: `canonicalKey` joins use string comparison
3. **Compound queries**: Multiple AND conditions may be slow without composite indexes
4. **Full table scans**: Queries without conditions scan entire entity boxes

### Recommendations
1. Consider composite indexes for common compound queries:
   - `(seriesId, seasonNumber)` for episode lookups
   - `(chatId, messageId)` for Telegram message lookups
   - `(kidProfileId, dayYyyymmdd)` for screen time queries

2. Monitor query performance for:
   - Category filtering (multiple entity boxes)
   - String searches on `nameLower`, `captionLower`
   - Resume mark lookups by `canonicalKey`

3. Evaluate migration to proper relations for:
   - Series ↔ Episodes (currently manual join)
   - Category ↔ Content (currently manual join)
   - Profile ↔ Permissions (currently manual join)

## Next Steps

This Phase 4 output will be consumed by Task 2A to create:
1. Final comprehensive ObjectBox documentation
2. Traceability matrix (field → writer → reader)
3. Query optimization recommendations
4. Schema evolution guidelines

## Validation

All data in these files was extracted directly from the codebase using automated analysis scripts. No patterns were guessed or inferred beyond what exists in the actual code.

**Validation commands:**
```bash
# Verify query count
grep -rn "\.query(" --include="*.kt" | grep -v "test/" | wc -l

# Verify relation declarations
grep -rn "ToOne<\|ToMany<" --include="*.kt" core/persistence/

# Verify manual joins
grep -rn "\.equal(\|\.contains(" --include="*.kt" infra/data-*/
```

## Metadata

- **Analysis Tool**: Python 3.x + grep/ripgrep
- **Code Snapshot**: 2026-01-08
- **Codebase Version**: FishIT-Player v2 architecture
- **Total Files Analyzed**: ~50 Kotlin files
- **Analysis Time**: ~60 seconds

# Entity Traceability Matrix

**Version:** 1.0  
**Generated:** 2026-01-08  
**Phase:** 2A - Final Synthesis

> **Purpose:** Track all read/write operations and relationships for every ObjectBox entity.
> Enables impact analysis, refactoring safety, and query optimization.

---

## 📊 Complete Traceability Matrix

| Entity | Writers (put/remove) | Readers (query) | Query Count | Relations | Most Common Pattern |
|--------|---------------------|-----------------|-------------|-----------|---------------------|
| **ObxCanonicalMedia** | ObxCanonicalMediaRepository<br>HomeContentRepositoryAdapter<br>_Tests: ObxCanonicalMediaRepositoryTmdbKeyCompatibilityTest_ | ObxCanonicalMediaRepository<br>HomeContentRepositoryAdapter<br>UnifiedDetailViewModel<br>_Tests: ObxCanonicalMediaRepositoryTmdbKeyCompatibilityTest_ | 27 | ToMany → ObxMediaSourceRef<br>← Manual join from ObxCanonicalResumeMark | findFirst:canonicalKey (10×) |
| **ObxMediaSourceRef** | ObxCanonicalMediaRepository<br>ObxXtreamCatalogRepository<br>ObxTelegramContentRepository<br>HomeContentRepositoryAdapter | ObxCanonicalMediaRepository<br>ObxXtreamCatalogRepository<br>ObxTelegramContentRepository | 7 | ToOne → ObxCanonicalMedia<br>@Backlink from sources field | count:sourceType (3×) |
| **ObxCanonicalResumeMark** | ObxCanonicalMediaRepository<br>HomeContentRepositoryAdapter<br>_Tests: ObxCanonicalMediaRepositoryTmdbKeyCompatibilityTest_ | ObxCanonicalMediaRepository<br>_Tests: ObxCanonicalMediaRepositoryTmdbKeyCompatibilityTest_ | 6 | Manual join via canonicalKey → ObxCanonicalMedia | findFirst:profileId (2×) |
| **ObxVod** | ObxXtreamCatalogRepository | ObxXtreamCatalogRepository<br>LibraryContentRepositoryAdapter | 20 | Manual join via categoryId → ObxCategory | find: (unfiltered) (9×) |
| **ObxSeries** | ObxXtreamCatalogRepository | ObxXtreamCatalogRepository<br>LibraryContentRepositoryAdapter | 10 | Manual join to ObxEpisode via seriesId<br>Manual join to ObxSeasonIndex via seriesId<br>Manual join via categoryId → ObxCategory | find:categoryId (2×) |
| **ObxEpisode** | ObxXtreamCatalogRepository | ObxXtreamCatalogRepository | 5 | Manual join from ObxSeries via seriesId | find:season,seriesId (2×) |
| **ObxLive** | ObxXtreamLiveRepository<br>LiveContentRepositoryAdapter (remove) | ObxXtreamLiveRepository<br>LiveContentRepositoryAdapter | 10 | Manual join via categoryId → ObxCategory<br>← Manual join from ObxEpgNowNext via streamId | find:nameLower (2×) |
| **ObxTelegramMessage** | ObxTelegramContentRepository | ObxTelegramContentRepository | 9 | None (self-contained) | find:chatId (3×) |
| **ObxCategory** | LiveContentRepositoryAdapter (remove) | LiveContentRepositoryAdapter<br>LibraryContentRepositoryAdapter | 10 | ← Reverse join from ObxVod, ObxSeries, ObxLive | find:kind (6×) |
| **ObxEpgNowNext** | LiveContentRepositoryAdapter (remove) | LiveContentRepositoryAdapter | 1 | Manual join via streamId → ObxLive | findFirst:streamId,channelId (1×) |
| **ObxSeasonIndex** | ObxXtreamSeriesIndexRepository | ObxXtreamSeriesIndexRepository | 6 | Manual join via seriesId → ObxSeries<br>← Manual join from ObxEpisodeIndex | find:seriesId (2×) |
| **ObxEpisodeIndex** | ObxXtreamSeriesIndexRepository | ObxXtreamSeriesIndexRepository | 11 | Manual join via seriesId → ObxSeries<br>Manual join via (seriesId+seasonNumber) → ObxSeasonIndex | findFirst:sourceKey (5×) |
| **ObxProfile** | ObxProfileRepository | ObxProfileRepository | 2 | ← Manual join from ObxProfilePermissions<br>← Manual join from ObxScreenTimeEntry<br>← Manual join from Kid* entities | findFirst:type (1×) |
| **ObxProfilePermissions** | _None documented_ | ObxProfileRepository (inferred) | 0 | Manual join via profileId → ObxProfile | - |
| **ObxScreenTimeEntry** | ObxScreenTimeRepository | ObxScreenTimeRepository | 4 | Manual join via kidProfileId → ObxProfile | findFirst:dayYyyymmdd (3×) |
| **ObxKidContentAllow** | _None documented_ | _None documented_ | 0 | Manual join via kidProfileId → ObxProfile | - |
| **ObxKidCategoryAllow** | _None documented_ | _None documented_ | 0 | Manual join via kidProfileId → ObxProfile | - |
| **ObxKidContentBlock** | _None documented_ | _None documented_ | 0 | Manual join via kidProfileId → ObxProfile | - |
| **ObxIndexProvider** | _None documented_ | _None documented_ | 0 | Filter index (materialized view pattern) | - |
| **ObxIndexYear** | _None documented_ | _None documented_ | 0 | Filter index (materialized view pattern) | - |
| **ObxIndexGenre** | _None documented_ | _None documented_ | 0 | Filter index (materialized view pattern) | - |
| **ObxIndexLang** | _None documented_ | _None documented_ | 0 | Filter index (materialized view pattern) | - |
| **ObxIndexQuality** | _None documented_ | _None documented_ | 0 | Filter index (materialized view pattern) | - |

---

## 🔍 Detailed Access Patterns

### ObxCanonicalMedia

**Writers:**
- `ObxCanonicalMediaRepository.upsertCanonicalMediaInternal()` - Core upsert logic
- `ObxCanonicalMediaRepository.addOrUpdateSourceRef()` - Link sources
- `HomeContentRepositoryAdapter.various()` - Home content updates
- Test: `ObxCanonicalMediaRepositoryTmdbKeyCompatibilityTest`

**Readers:**
- `ObxCanonicalMediaRepository.findCanonicalByAnyKey()` - Lookup by any canonical key variant
- `ObxCanonicalMediaRepository.findByExternalId()` - Search by TMDB/IMDB/TVDB ID
- `ObxCanonicalMediaRepository.findByTitleAndYear()` - Fuzzy title search
- `ObxCanonicalMediaRepository.findBySourceId()` - Reverse lookup from source
- `ObxCanonicalMediaRepository.getSourcesForMedia()` - Get all source variants
- `ObxCanonicalMediaRepository.search()` - Full-text search
- `HomeContentRepositoryAdapter.various()` - Home content queries
- `UnifiedDetailViewModel` (feature/detail) - Detail screen data

**Access Pattern:** Primary key lookup (canonicalKey), external ID matching, title fuzzy search

---

### ObxMediaSourceRef

**Writers:**
- `ObxCanonicalMediaRepository.addOrUpdateSourceRef()` - Link source to canonical media
- `ObxXtreamCatalogRepository.various()` - Xtream source references
- `ObxTelegramContentRepository.various()` - Telegram source references
- `HomeContentRepositoryAdapter.various()` - Home aggregation

**Readers:**
- `ObxCanonicalMediaRepository.removeSourceRef()` - Unlink source
- `ObxXtreamCatalogRepository.various()` - Check source existence
- `ObxTelegramContentRepository.various()` - Check source existence

**Access Pattern:** Lookup by sourceId, count by sourceType, relation traversal from canonical media

---

### ObxCanonicalResumeMark

**Writers:**
- `ObxCanonicalMediaRepository.setCanonicalResume()` - Update resume position
- `HomeContentRepositoryAdapter.various()` - Continue watching updates
- Test: `ObxCanonicalMediaRepositoryTmdbKeyCompatibilityTest`

**Readers:**
- `ObxCanonicalMediaRepository.getCanonicalResume()` - Get resume for playback
- `ObxCanonicalMediaRepository.getContinueWatching()` - Home screen continue watching
- Test: `ObxCanonicalMediaRepositoryTmdbKeyCompatibilityTest`

**Access Pattern:** Lookup by (canonicalKey + profileId), filter incomplete for continue watching

---

### ObxVod

**Writers:**
- `ObxXtreamCatalogRepository.upsertVod()` - Sync Xtream VOD catalog
- `ObxXtreamCatalogRepository.upsertVodBatch()` - Batch inserts

**Readers:**
- `ObxXtreamCatalogRepository.observeVod()` - Reactive VOD list
- `ObxXtreamCatalogRepository.getVodById()` - Single VOD lookup
- `ObxXtreamCatalogRepository.searchVod()` - Title search
- `LibraryContentRepositoryAdapter.observeVod()` - Library screen
- `LibraryContentRepositoryAdapter.various()` - Filtering and grouping

**Access Pattern:** Category filtering, title search, browse all, year/genre filters

---

### ObxSeries

**Writers:**
- `ObxXtreamCatalogRepository.upsertSeries()` - Sync Xtream series catalog
- `ObxXtreamCatalogRepository.upsertSeriesBatch()` - Batch inserts

**Readers:**
- `ObxXtreamCatalogRepository.observeSeries()` - Reactive series list
- `ObxXtreamCatalogRepository.getSeriesById()` - Single series lookup
- `LibraryContentRepositoryAdapter.observeSeries()` - Library screen
- `LibraryContentRepositoryAdapter.various()` - Filtering

**Access Pattern:** Category filtering, title search, browse all

---

### ObxEpisode

**Writers:**
- `ObxXtreamCatalogRepository.upsertEpisode()` - Sync episodes for series

**Readers:**
- `ObxXtreamCatalogRepository.getEpisodesForSeries()` - Load all episodes
- `ObxXtreamCatalogRepository.getEpisodesForSeason()` - Load season episodes
- `ObxXtreamCatalogRepository.getEpisodeById()` - Single episode lookup

**Access Pattern:** Join from series (seriesId), filter by season

---

### ObxLive

**Writers:**
- `ObxXtreamLiveRepository.upsertLiveStream()` - Sync live channels
- `LiveContentRepositoryAdapter (remove operations)` - Cleanup

**Readers:**
- `ObxXtreamLiveRepository.observeLiveStreams()` - Reactive channel list
- `LiveContentRepositoryAdapter.observeLiveChannels()` - Live TV screen
- `LiveContentRepositoryAdapter.searchLiveChannels()` - Search

**Access Pattern:** Category filtering, name search, provider/genre filters

---

### ObxTelegramMessage

**Writers:**
- `ObxTelegramContentRepository.upsert()` - Insert/update message
- `ObxTelegramContentRepository.upsertBatch()` - Batch sync

**Readers:**
- `ObxTelegramContentRepository.observeByChat()` - Reactive messages per chat
- `ObxTelegramContentRepository.findByRemoteId()` - Lookup by Telegram file ID
- `ObxTelegramContentRepository.searchByCaption()` - Caption search

**Access Pattern:** Chat filtering (chatId), remote ID lookup, caption search

---

### ObxCategory

**Writers:**
- `LiveContentRepositoryAdapter (remove operations)` - Cleanup

**Readers:**
- `LiveContentRepositoryAdapter.observeCategories()` - Category list
- `LibraryContentRepositoryAdapter.observeCategories()` - Library filters

**Access Pattern:** Filter by kind (VOD/SERIES/LIVE), count items per category

---

### ObxEpgNowNext

**Writers:**
- `LiveContentRepositoryAdapter (remove operations)` - Cleanup/refresh

**Readers:**
- `LiveContentRepositoryAdapter.getEpgForStream()` - EPG data for channel

**Access Pattern:** Single lookup by streamId or channelId

---

### ObxSeasonIndex

**Writers:**
- `ObxXtreamSeriesIndexRepository.upsertSeasonIndex()` - Index metadata

**Readers:**
- `ObxXtreamSeriesIndexRepository.getSeasonsForSeries()` - List seasons
- `ObxXtreamSeriesIndexRepository.getSeasonMetadata()` - Season details

**Access Pattern:** Join from series (seriesId), ordered by seasonNumber

---

### ObxEpisodeIndex

**Writers:**
- `ObxXtreamSeriesIndexRepository.upsertEpisodeIndex()` - Index episodes

**Readers:**
- `ObxXtreamSeriesIndexRepository.getEpisodesForSeason()` - Episode list
- `ObxXtreamSeriesIndexRepository.findEpisodeBySourceKey()` - Reverse lookup
- `ObxXtreamSeriesIndexRepository.getEpisodePlaybackHints()` - Playback metadata

**Access Pattern:** Join from series+season, lookup by sourceKey (frequent)

---

### ObxProfile

**Writers:**
- `ObxProfileRepository.createProfile()` - Create new profile
- `ObxProfileRepository.updateProfile()` - Update profile details

**Readers:**
- `ObxProfileRepository.getAllProfiles()` - List all profiles
- `ObxProfileRepository.getProfileById()` - Single profile lookup

**Access Pattern:** List all, lookup by type (ADULT/KID/GUEST)

---

### ObxScreenTimeEntry

**Writers:**
- `ObxScreenTimeRepository.recordUsage()` - Update daily usage
- `ObxScreenTimeRepository.resetDaily()` - Daily reset

**Readers:**
- `ObxScreenTimeRepository.getTodayUsage()` - Current day check
- `ObxScreenTimeRepository.getUsageHistory()` - Historical data

**Access Pattern:** Lookup by (kidProfileId + dayYyyymmdd), daily aggregation

---

### Filter Index Entities (5 entities)

**ObxIndexProvider**, **ObxIndexYear**, **ObxIndexGenre**, **ObxIndexLang**, **ObxIndexQuality**

**Pattern:** Materialized view for filter facets

**Writers:** Computed during catalog sync (not yet implemented in scanned code)

**Readers:** Filter UI components (not yet implemented in scanned code)

**Future Usage:**
- Populate counts during sync: `UPDATE ObxIndexGenre SET count = (SELECT COUNT(*) FROM ObxVod WHERE genre LIKE '%Action%')`
- Display in filter UI: `SELECT * FROM ObxIndexGenre WHERE kind = 'VOD' ORDER BY count DESC`

---

## 🔄 Relationship Navigation Patterns

### Canonical Media → Sources (ToMany)

```kotlin
val canonical: ObxCanonicalMedia = canonicalBox.get(id)
val sources: List<ObxMediaSourceRef> = canonical.sources // Backlink access
```

**Used In:**
- Detail screen: Source selection dropdown
- Home screen: Group by canonical, show all source badges
- Resume logic: Find best quality source

### Source → Canonical Media (ToOne)

```kotlin
val source: ObxMediaSourceRef = sourceBox.get(id)
val canonical: ObxCanonicalMedia? = source.canonicalMedia.target // ToOne access
```

**Used In:**
- Source linking: Verify canonical exists before linking
- Orphan cleanup: Remove sources with no canonical parent

### Series → Episodes (Manual Join)

```kotlin
val episodes = episodeBox.query(
    ObxEpisode_.seriesId.equal(seriesId)
        .and(ObxEpisode_.season.equal(seasonNum))
).order(ObxEpisode_.episodeNum).build().find()
```

**Used In:**
- Detail screen: Load episodes for selected season
- Continue watching: Find next episode

### Category → Content (Reverse Join)

```kotlin
val vodItems = vodBox.query(ObxVod_.categoryId.equal(categoryId)).build().find()
val seriesItems = seriesBox.query(ObxSeries_.categoryId.equal(categoryId)).build().find()
```

**Used In:**
- Library screen: Browse by category
- Category counts: Count items per category

---

## 📊 Impact Analysis

### High-Risk Changes (>20 query references)

| Entity | Query Count | Impact Level | Affected Modules |
|--------|-------------|--------------|------------------|
| **ObxCanonicalMedia** | 27 | 🔴 HIGH | core/persistence, infra/data-home, feature/detail |
| **ObxVod** | 20 | 🟡 MEDIUM | infra/data-xtream |

**Recommendation:** Full integration test suite before schema changes

### Medium-Risk Changes (10-19 queries)

| Entity | Query Count | Impact Level | Affected Modules |
|--------|-------------|--------------|------------------|
| **ObxEpisodeIndex** | 11 | 🟡 MEDIUM | infra/data-xtream |
| **ObxSeries** | 10 | 🟡 MEDIUM | infra/data-xtream |
| **ObxCategory** | 10 | 🟡 MEDIUM | infra/data-xtream (multiple adapters) |
| **ObxLive** | 10 | 🟡 MEDIUM | infra/data-xtream |

**Recommendation:** Module-level testing, verify adapter compatibility

### Low-Risk Changes (<10 queries)

| Entity | Query Count | Impact Level | Affected Modules |
|--------|-------------|--------------|------------------|
| **ObxTelegramMessage** | 9 | 🟢 LOW | infra/data-telegram |
| **ObxMediaSourceRef** | 7 | 🟢 LOW | Multiple (canonical linking) |
| **ObxCanonicalResumeMark** | 6 | 🟢 LOW | core/persistence |
| **ObxSeasonIndex** | 6 | 🟢 LOW | infra/data-xtream |
| **ObxEpisode** | 5 | 🟢 LOW | infra/data-xtream |
| **ObxScreenTimeEntry** | 4 | 🟢 LOW | core/persistence |
| **ObxProfile** | 2 | 🟢 LOW | core/persistence |
| **ObxEpgNowNext** | 1 | 🟢 LOW | infra/data-xtream |

**Recommendation:** Standard testing, isolated impact

### Zero-Query Entities (Unused or Future Use)

- ObxProfilePermissions
- ObxKidContentAllow
- ObxKidCategoryAllow
- ObxKidContentBlock
- ObxIndexProvider
- ObxIndexYear
- ObxIndexGenre
- ObxIndexLang
- ObxIndexQuality

**Status:** Schema defined, implementation pending

---

## 🔗 Cross-References

- **Overview**: [OBX_DATA_LAYERS_MAP.md](./OBX_DATA_LAYERS_MAP.md)
- **Relationship Graph**: [RELATION_DEPENDENCY_GRAPH.md](./RELATION_DEPENDENCY_GRAPH.md)
- **Query Patterns**: See query_usage.json in `_intermediate/`
- **Access Patterns**: See entity_access_patterns.json in `_intermediate/`

---

**Generated by:** Phase 2A Documentation Synthesis  
**Data Sources:** entity_access_patterns.json, query_usage.json, relationships.json  
**Last Updated:** 2026-01-08

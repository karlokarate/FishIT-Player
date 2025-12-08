> LEGACY (V1) – historical document  
> Not valid for v2. For current architecture see `V2_PORTAL.md` and `docs/v2/**`.

# Phase 3+ Future Work: IO Pipeline Integration Plans

**Agent:** io-agent  
**Document:** Future integration roadmap for IO pipeline  
**Created:** 2025-12-06  
**Status:** Planning document for Phase 3+ implementation

---

## Overview

This document outlines the future integration work required to complete the IO pipeline implementation, connecting it with:
1. Real filesystem/SAF/SMB access (in infra/app modules)
2. Centralized metadata normalizer
3. Canonical media ID system
4. Cross-pipeline resume and detail screens

**Critical Architectural Boundaries:**
- `:pipeline:io` module remains pure domain logic (no Android/platform dependencies)
- Platform-specific implementations go in `:infra:*` or `:app-v2` modules
- All title cleaning/normalization happens in `:core:metadata-normalizer`
- TMDB integration is centralized, NOT in pipelines

---

## 1. Filesystem/SAF/SMB Implementation Architecture

### 1.1 Module Placement Strategy

**DO NOT implement in `:pipeline:io`:**
- Android ContentResolver calls
- Storage Access Framework (SAF) APIs
- SMB/CIFS network clients
- Direct filesystem I/O using `java.io.File`

**CORRECT placement:**

```
:infra:storage (new module)
  └── com.fishit.player.infra.storage/
      ├── local/
      │   ├── LocalFileScanner.kt          // java.io.File scanning
      │   ├── LocalFileMetadataExtractor.kt // MediaMetadataRetriever
      │   └── LocalFileDataSource.kt        // Media3 DataSource for file://
      ├── saf/
      │   ├── SafDocumentScanner.kt         // ContentResolver + DocumentFile
      │   ├── SafMetadataExtractor.kt       // content:// metadata
      │   └── SafDataSource.kt              // Media3 DataSource for content://
      └── smb/
          ├── SmbClientManager.kt           // smbj library wrapper
          ├── SmbFileScanner.kt             // SMB share scanning
          └── SmbDataSource.kt              // Media3 DataSource for smb://

:infra:playback (existing or new)
  └── com.fishit.player.infra.playback/
      └── datasource/
          └── IoDataSourceFactory.kt        // Delegates to Local/Saf/Smb DataSources

:app-v2
  └── Application-level wiring:
      ├── Dependency injection (Hilt modules)
      └── Runtime permission management
```

### 1.2 IoContentRepository Implementation Strategy

**Current state:**
- `StubIoContentRepository` returns fake/empty data

**Future implementation:**

Create a new implementation in `:infra:storage` or `:app-v2`:

```kotlin
// :infra:storage or :app-v2
class RealIoContentRepository(
    private val localScanner: LocalFileScanner,
    private val safScanner: SafDocumentScanner,
    private val smbScanner: SmbClientManager,
    private val metadataExtractor: FileMetadataExtractor,
) : IoContentRepository {
    
    override suspend fun discoverAll(): List<IoMediaItem> {
        // Scan well-known directories (Movies, Downloads, etc.)
        val localFiles = localScanner.scanDirectories()
        
        // Scan user-selected SAF directories (from preferences)
        val safFiles = safScanner.scanGrantedUris()
        
        // Scan configured SMB shares (from user settings)
        val smbFiles = smbScanner.scanShares()
        
        // Extract metadata and convert to IoMediaItem
        return (localFiles + safFiles + smbFiles)
            .map { metadataExtractor.extractToIoMediaItem(it) }
    }
    
    // ... other methods
}
```

**Key points:**
- This implementation lives in `:infra:storage` or `:app-v2`, NOT in `:pipeline:io`
- `:pipeline:io` only defines the interface and domain models
- Dependency injection wires the real implementation to the interface

---

## 2. Raw Metadata Forwarding to Normalizer

### 2.1 Current State (Phase 3 Prep Complete)

**✅ Placeholder Implementation Complete:**
- `IoMediaItem.toRawMediaMetadata()` returns `Map<String, Any?>` as a **temporary structural placeholder**
- Map keys exactly match `RawMediaMetadata` structure from MEDIA_NORMALIZATION_CONTRACT.md Section 1.1
- **This is NOT a local type definition** - IO must not define RawMediaMetadata locally
- Forwards raw filename as `originalTitle` WITHOUT any cleaning, stripping, or normalization
- Leaves `year`, `season`, `episode` as null (extraction is the normalizer's responsibility)
- Provides empty map for `externalIds` (filesystem does not provide TMDB/IMDB IDs)

**Migration Path:**
- Once `RawMediaMetadata` type is added to `:core:model`, the function will be updated to return that shared type
- The Map placeholder enables testing and validation before the shared type exists
- Seamless migration: just change return type and construct the data class instead of Map

### 2.2 Integration with Metadata Normalizer

**Once `:core:metadata-normalizer` is implemented:**

1. **Add RawMediaMetadata type to `:core:model`:**
   ```kotlin
   // :core:model
   data class RawMediaMetadata(
       val originalTitle: String,
       val year: Int? = null,
       val season: Int? = null,
       val episode: Int? = null,
       val durationMinutes: Int? = null,
       val externalIds: Map<String, String> = emptyMap(),
       val sourceType: String,
       val sourceLabel: String,
       val sourceId: String,
   )
   ```

2. **Update `IoMediaItem.toRawMediaMetadata()` to return actual type:**
   ```kotlin
   // :pipeline:io
   fun IoMediaItem.toRawMediaMetadata(): RawMediaMetadata =
       RawMediaMetadata(
           originalTitle = fileName,  // Raw filename, NO cleaning
           year = null,                // Reserved for normalizer
           season = null,              // Reserved for normalizer
           episode = null,             // Reserved for normalizer
           durationMinutes = durationMs?.let { (it / 60_000).toInt() },
           externalIds = emptyMap(),   // Not available from filesystem
           sourceType = "IO",
           sourceLabel = "Local File: $fileName",
           sourceId = toContentId(),
       )
   ```

3. **Use in normalization pipeline:**
   ```kotlin
   // :app-v2 or domain service
   class IoMediaIndexer(
       private val ioRepository: IoContentRepository,
       private val normalizer: MediaMetadataNormalizer,
       private val tmdbResolver: TmdbMetadataResolver,
       private val canonicalRepo: CanonicalMediaRepository,
   ) {
       suspend fun indexIoMedia() {
           val items = ioRepository.discoverAll()
           
           items.forEach { item ->
               // 1. Extract raw metadata (NO cleaning)
               val raw = item.toRawMediaMetadata()
               
               // 2. Normalize title (centralizer handles scene-style parsing)
               val normalized = normalizer.normalize(raw)
               
               // 3. Enrich with TMDB (optional, async)
               val enriched = tmdbResolver.enrich(normalized)
               
               // 4. Store canonical media + source reference
               val canonicalId = canonicalRepo.upsertCanonicalMedia(enriched)
               canonicalRepo.addOrUpdateSourceRef(canonicalId, item.toSourceRef())
           }
       }
   }
   ```

**Contract compliance:**
- IO pipeline provides raw data only
- Normalizer handles all cleaning, parsing, heuristics
- TMDB resolver handles external ID assignment
- Canonical repository handles cross-pipeline identity

---

## 3. Canonical ID System Integration

### 3.1 CanonicalMediaId Structure

**Definition (will be in `:core:model`):**
```kotlin
data class CanonicalMediaId(
    val kind: MediaKind,  // MOVIE or EPISODE
    val key: String,      // Unique key
)

enum class MediaKind {
    MOVIE,
    EPISODE,
}
```

**Identity rules (from MEDIA_NORMALIZATION_CONTRACT.md):**
1. If `tmdbId != null`: `key = "tmdb:<tmdbId>"`
2. If no `tmdbId`, but normalized title + year (+ S/E for episodes):
   - Movies: `key = "movie:<canonicalTitle>:<year>"`
   - Episodes: `key = "episode:<canonicalTitle>:S<season>E<episode>"`
3. If neither, item cannot be assigned stable CanonicalMediaId

### 3.2 MediaSourceRef Structure

**Definition (will be in `:core:model`):**
```kotlin
data class MediaSourceRef(
    val sourceType: String,      // "IO", "XTREAM", "TELEGRAM", etc.
    val sourceId: String,         // Pipeline-specific ID (e.g., contentId)
    val sourceLabel: String,      // Human-readable label
    val quality: MediaQuality?,   // Resolution, codec info
    val language: String?,        // Audio/subtitle language
    val format: MediaFormat?,     // MP4, MKV, AVI, etc.
)
```

### 3.3 IO Pipeline Integration

**IoMediaItem to MediaSourceRef conversion:**
```kotlin
// :pipeline:io (add to IoMediaItemExtensions.kt)
fun IoMediaItem.toMediaSourceRef(): MediaSourceRef =
    MediaSourceRef(
        sourceType = "IO",
        sourceId = toContentId(),  // "io:file:..."
        sourceLabel = "Local File: $fileName",
        quality = metadata["resolution"]?.let { parseQuality(it) },
        language = metadata["language"],
        format = mimeType?.let { parseFormat(it) },
    )
```

**Storage in canonical repository:**
```kotlin
// Once canonical media exists
val canonicalId = CanonicalMediaId(
    kind = MediaKind.MOVIE,
    key = "movie:x-men:2000",  // Generated by normalizer + TMDB
)

val sourceRef = ioMediaItem.toMediaSourceRef()

// Link IO item to canonical media
canonicalRepo.addOrUpdateSourceRef(canonicalId, sourceRef)
```

**Benefits:**
- Same movie from Telegram, Xtream, and IO all link to one CanonicalMediaId
- User selects version (best quality, preferred language, etc.)
- Resume position syncs across all sources
- Unified detail screen shows all available versions

---

## 4. Cross-Pipeline Resume Integration

### 4.1 Current Resume System

**Existing (Phase 2 Task 1):**
- `ObxResumeMark` stores resume positions
- ContentId format: `"io:file:{uri}"`
- Works per-source (each source tracks independently)

### 4.2 Future Canonical Resume

**Once canonical identity exists:**

```kotlin
// :core:persistence (future enhancement)
@Entity
data class ObxCanonicalResumeMark(
    @Id var id: Long = 0,
    var canonicalKey: String,      // "movie:x-men:2000"
    var profileId: Long,
    var positionMs: Long,
    var durationMs: Long,
    var lastPlayedUtc: Long,
    var lastSourceType: String,    // "IO", "XTREAM", "TELEGRAM"
    var lastSourceId: String,      // Last-used source for this media
)
```

**Resume behavior:**
1. User starts playing "X-Men (2000)" from IO pipeline
2. System looks up canonical ID: `movie:x-men:2000`
3. Check if canonical resume position exists → use it
4. User stops at 45:00, store resume position for canonical ID
5. Later, user browses Xtream catalog, sees "X-Men (2000)"
6. System recognizes same canonical ID
7. Resume from 45:00, even though source is different

**Fallback:**
- If no canonical ID exists (e.g., item not normalized yet), use per-source resume (current behavior)

---

## 5. Unified Detail Screen Integration

### 5.1 Current State

- Each pipeline has its own detail screens
- No cross-pipeline awareness
- User sees duplicate entries in search/browse

### 5.2 Future Unified Detail

**Once canonical media exists:**

```kotlin
// :feature:detail (new feature module)
@Composable
fun UnifiedMediaDetailScreen(
    canonicalId: CanonicalMediaId,
    viewModel: UnifiedDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    
    DetailScaffold(
        title = state.canonicalTitle,
        posterUrl = state.tmdbPosterUrl,
        backdrop = state.tmdbBackdropUrl,
        metadata = state.metadata,  // Year, runtime, genres from TMDB
    ) {
        // Version/Source Selection
        SourceVersionSelector(
            sources = state.availableSources,  // List of IO, Xtream, Telegram sources
            onSourceSelected = { source ->
                // Play from selected source
                viewModel.playFromSource(source)
            },
        )
    }
}
```

**ViewModel implementation:**
```kotlin
class UnifiedDetailViewModel(
    private val canonicalRepo: CanonicalMediaRepository,
) : ViewModel() {
    
    fun loadDetails(canonicalId: CanonicalMediaId) {
        viewModelScope.launch {
            val canonical = canonicalRepo.findByCanonicalId(canonicalId)
            
            // Load all sources (IO, Xtream, Telegram) for this canonical media
            val sources = canonical.sources.map { ref ->
                when (ref.sourceType) {
                    "IO" -> ioRepo.getItemById(ref.sourceId)
                    "XTREAM" -> xtreamRepo.getStreamById(ref.sourceId)
                    "TELEGRAM" -> telegramRepo.getMediaById(ref.sourceId)
                    else -> null
                }
            }
            
            // User picks best version (4K from IO vs 1080p from Xtream)
        }
    }
}
```

**Benefits:**
- Single detail screen for "X-Men (2000)" regardless of source
- User sees all available versions (IO, Xtream, Telegram)
- User picks preferred version (quality, language, subtitles)
- Unified resume position across all versions

---

## 6. Implementation Phases

### Phase 3: Metadata Normalizer Core (Next)
**Owner:** metadata-normalizer-agent (future agent)  
**Deliverables:**
- New module `:core:metadata-normalizer`
- `RawMediaMetadata` type in `:core:model`
- `MediaMetadataNormalizer` interface + implementation
- Scene-style parser (regex-based, inspired by Sonarr/Radarr/GuessIt)
- Comprehensive tests for normalization rules
- **NO** TMDB integration yet (Phase 4)

**IO pipeline changes:**
- Update `IoMediaItem.toRawMediaMetadata()` to return actual `RawMediaMetadata` type
- Update tests to use real type instead of Map
- No other changes needed (contract-compliant already)

---

### Phase 4: TMDB Resolver
**Owner:** tmdb-agent (future agent)  
**Deliverables:**
- `TmdbMetadataResolver` interface + implementation
- Integration with `tmdb-java` library
- TMDB search logic (title + year → tmdbId)
- Caching and rate limiting
- Tests for TMDB matching logic

**IO pipeline changes:**
- None (IO provides raw metadata only, TMDB resolution is independent)

---

### Phase 5: Canonical Media Storage
**Owner:** canonical-storage-agent (future agent)  
**Deliverables:**
- `CanonicalMediaId` type in `:core:model`
- `MediaSourceRef` type in `:core:model`
- `CanonicalMediaRepository` interface in `:core:persistence`
- ObjectBox entity for canonical media + source references
- Tests for canonical storage operations

**IO pipeline changes:**
- Add `IoMediaItem.toMediaSourceRef()` extension
- Tests for source reference conversion
- No changes to core IO logic

---

### Phase 6: Filesystem Access (Local Files)
**Owner:** io-agent (this agent, future work)  
**Deliverables:**
- New module `:infra:storage` (or use existing `:infra:*`)
- `LocalFileScanner` implementation (java.io.File API)
- `LocalFileMetadataExtractor` (MediaMetadataRetriever)
- `RealIoContentRepository` implementation
- `LocalFileDataSource` for Media3 (file:// scheme)
- Tests for file scanning and metadata extraction

**IO pipeline changes:**
- None (interface already defined, just wire up real implementation)

---

### Phase 7: SAF Integration
**Owner:** io-agent (this agent, future work)  
**Deliverables:**
- `SafDocumentScanner` implementation (ContentResolver + DocumentFile)
- `SafMetadataExtractor` (content:// metadata)
- `SafDataSource` for Media3 (content:// scheme)
- Permission management UI
- Tests for SAF operations (instrumented tests on real devices)

**IO pipeline changes:**
- None (IoSource.Saf already defined, just implement the platform adapter)

---

### Phase 8: SMB/Network Shares
**Owner:** io-agent (this agent, future work)  
**Deliverables:**
- New module `:infra:network` (or integrate into `:infra:storage`)
- `SmbClientManager` implementation (smbj library)
- `SmbFileScanner` implementation
- `SmbDataSource` for Media3 (smb:// scheme)
- Network configuration UI
- Credential storage (encrypted)
- Tests for SMB operations (requires test SMB server)

**IO pipeline changes:**
- None (IoSource.Smb already defined, just implement the platform adapter)

---

### Phase 9: Cross-Pipeline Indexing Service
**Owner:** indexing-agent (future agent)  
**Deliverables:**
- Background service that:
  1. Discovers media from all pipelines (IO, Xtream, Telegram)
  2. Extracts raw metadata via `toRawMediaMetadata()`
  3. Normalizes metadata via `MediaMetadataNormalizer`
  4. Enriches with TMDB via `TmdbMetadataResolver`
  5. Stores in canonical repository
- Progress tracking and error handling
- Incremental updates (detect new/changed/deleted media)
- Tests for indexing pipeline

**IO pipeline changes:**
- None (just used as a data source)

---

### Phase 10: Unified Detail Screens
**Owner:** ui-detail-agent (future agent)  
**Deliverables:**
- New module `:feature:detail` (or enhance existing)
- `UnifiedMediaDetailScreen` composable
- Source version selector UI
- Integration with canonical repository
- Integration with player for playback
- Tests for detail screen behavior

**IO pipeline changes:**
- None (just one of many sources displayed)

---

## 7. Critical Reminders

### 7.1 What IO Pipeline MUST NOT Do

**Never in `:pipeline:io`:**
- ❌ Define a local `RawMediaMetadata` type (must use shared type from `:core:model`)
- ❌ Title cleaning (scene-style parsing, tag stripping)
- ❌ Year/season/episode extraction or any heuristics
- ❌ TMDB lookups or external database searches
- ❌ Canonical identity computation
- ❌ Cross-pipeline matching or grouping
- ❌ Android ContentResolver calls
- ❌ Storage Access Framework (SAF) APIs
- ❌ Direct filesystem I/O (java.io.File)
- ❌ SMB/CIFS network clients
- ❌ Media3 DataSource implementations

**IO pipeline responsibilities:**
- ✅ Define domain models (`IoMediaItem`, `IoSource`)
- ✅ Define repository interfaces (`IoContentRepository`)
- ✅ Provide raw metadata via `toRawMediaMetadata()` (placeholder Map now, shared type later)
- ✅ Provide playback context via `toPlaybackContext()`
- ✅ Provide source references via `toMediaSourceRef()` (future)
- ✅ Remain pure Kotlin domain logic, testable on any JVM

### 7.2 Module Boundaries

**Clear separation:**
```
:pipeline:io          → Domain logic only (pure Kotlin)
:infra:storage        → Platform filesystem/SAF adapters (Android APIs)
:infra:network        → SMB/network clients
:infra:playback       → Media3 DataSource implementations
:core:metadata-normalizer → Title cleaning, scene parsing
:core:model           → Shared types (RawMediaMetadata, CanonicalMediaId)
:core:persistence     → Canonical storage (ObjectBox)
```

### 7.3 Latest Documentation Wins

**Always check timestamps:**
- `v2-docs/MEDIA_NORMALIZATION_CONTRACT.md` (latest: 2025-12-06 17:27)
- `v2-docs/MEDIA_NORMALIZATION_AND_UNIFICATION.md` (latest: 2025-12-06 17:27)

**If conflicts arise:**
- Latest-timestamp document is authoritative
- MEDIA_NORMALIZATION_CONTRACT.md defines formal rules (Section 1.1 for RawMediaMetadata)
- MEDIA_NORMALIZATION_AND_UNIFICATION.md provides context and overview
- All pipeline work must reference these documents, not duplicate their content

---

## 8. Summary

**Phase 3 Prep (Completed 2025-12-06):**
- ✅ `IoMediaItem.toRawMediaMetadata()` placeholder function implemented
- ✅ Returns `Map<String, Any?>` matching RawMediaMetadata structure from contract
- ✅ Documented as temporary placeholder until shared type added to `:core:model`
- ✅ Contract-compliant (no cleaning, no normalization, no TMDB, no heuristics)
- ✅ Tests added (6 new tests, 35 total)
- ✅ Documentation updated with explicit boundaries and delegation rules

**Next Steps (Phase 3+):**
1. Implement `:core:metadata-normalizer` (centralizes all title cleaning and heuristics)
2. Add `RawMediaMetadata` shared type to `:core:model`
3. Update IO placeholder to return shared type (seamless migration)
4. Implement `TmdbMetadataResolver` (centralized TMDB integration)
5. Implement canonical storage (`:core:persistence` enhancements)
6. Implement filesystem access (`:infra:storage` with Local/SAF/SMB)
7. Implement cross-pipeline indexing service
8. Implement unified detail screens

**IO pipeline is ready:**
- Contract-compliant interface and stub implementations exist
- Raw metadata placeholder mapping is implemented and tested
- Platform-specific work will happen in `:infra:*` modules as planned
- Ready for seamless migration to shared `RawMediaMetadata` type once available
- No changes needed to `:pipeline:io` core logic until Phase 5+
5. Implement cross-pipeline indexing service
6. Implement unified detail screens

**IO pipeline is ready:**
- Contract-compliant interface and stub implementations exist
- Raw metadata mapping is implemented (structure only, pending type in `:core:model`)
- Platform-specific work will happen in `:infra:*` modules as planned
- No further changes needed to `:pipeline:io` until Phase 5+

---

**Document Version:** 1.0  
**Last Updated:** 2025-12-06  
**Agent:** io-agent  
**Status:** Planning document for future phases

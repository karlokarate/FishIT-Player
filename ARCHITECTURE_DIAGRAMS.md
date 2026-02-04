# Handler/Builder Pattern Architecture Diagrams

## Sprint 1: Xtream Transport Handlers

### Before Refactoring
```
┌─────────────────────────────────────────────────────────────┐
│         DefaultXtreamApiClient.kt (2312 lines)              │
│                     CC = 52 ⚠️                              │
│                                                             │
│  - Connection management (init, ping, close)                │
│  - Capability discovery                                     │
│  - Port resolution                                          │
│  - Category fetching (Live, VOD, Series)                    │
│  - Stream fetching (Live, VOD, Series)                      │
│  - Detail endpoints (getVodInfo, getSeriesInfo)             │
│  - Count operations                                         │
│  - JSON mapping (duplicated 3x for Live/VOD/Series)         │
│  - Error handling                                           │
│  - State management                                         │
│                                                             │
│  ❌ Too many responsibilities                               │
│  ❌ High complexity                                         │
│  ❌ Difficult to test                                       │
│  ❌ Code duplication                                        │
└─────────────────────────────────────────────────────────────┘
```

### After Refactoring
```
┌─────────────────────────────────────────────────────────────┐
│      DefaultXtreamApiClient.kt (Orchestrator)               │
│                  CC ≤ 10 ✅                                 │
│  Delegates to:                                              │
└─────────────────┬───────────────────────────────────────────┘
                  │
        ┌─────────┼─────────┬─────────┬─────────┐
        │         │         │         │         │
        ▼         ▼         ▼         ▼         ▼
┌──────────┐ ┌─────────┐ ┌─────────┐ ┌─────┐ ┌─────┐
│Connection│ │Category │ │ Stream  │ │Live │ │ VOD │
│ Manager  │ │ Fetcher │ │ Fetcher │ │Mapper│ │Mapper│
│  CC ~8   │ │  CC ~4  │ │  CC ~7  │ │CC=2 │ │CC=2 │
└──────────┘ └─────────┘ └─────────┘ └─────┘ └─────┘
                                               ┌─────┐
                                               │Series│
                                               │Mapper│
                                               │CC=2 │
                                               └─────┘

✅ Single responsibility per handler
✅ Low complexity
✅ Easy to test
✅ No duplication (mappers shared)
```

## Sprint 2: NX Data Writers Builders

### Before Refactoring
```
┌─────────────────────────────────────────────────────────────┐
│            NxCatalogWriter.kt (610 lines)                   │
│                     CC = 28 ⚠️                              │
│                                                             │
│  ingest() method (150 lines):                               │
│    - Build NX_Work entity (50 lines)                        │
│    - Build NX_WorkSourceRef entity (40 lines)               │
│    - Build NX_WorkVariant entity (30 lines)                 │
│    - Error handling (30 lines)                              │
│                                                             │
│  ingestBatch() method (280 lines):                          │
│    - Build NX_Work entity (50 lines) ❌ DUPLICATE           │
│    - Build NX_WorkSourceRef entity (40 lines) ❌ DUPLICATE  │
│    - Build NX_WorkVariant entity (30 lines) ❌ DUPLICATE    │
│    - Batch processing logic (160 lines)                     │
│                                                             │
│  ingestBatchOptimized() method (180 lines):                 │
│    - Build NX_Work entity (50 lines) ❌ DUPLICATE           │
│    - Build NX_WorkSourceRef entity (40 lines) ❌ DUPLICATE  │
│    - Build NX_WorkVariant entity (30 lines) ❌ DUPLICATE    │
│    - Optimization logic (60 lines)                          │
│                                                             │
│  ❌ 220 lines duplicated 3 times                            │
│  ❌ High complexity                                         │
│  ❌ Hard to maintain consistency                            │
└─────────────────────────────────────────────────────────────┘
```

### After Refactoring
```
┌─────────────────────────────────────────────────────────────┐
│         NxCatalogWriter.kt (Orchestrator)                   │
│                  CC ≤ 10 ✅                                 │
│  Delegates to:                                              │
└─────────────────┬───────────────────────────────────────────┘
                  │
        ┌─────────┼─────────┬─────────┐
        │         │         │         │
        ▼         ▼         ▼         │
┌──────────┐ ┌─────────┐ ┌─────────┐ │
│   Work   │ │ Source  │ │ Variant │ │
│  Entity  │ │   Ref   │ │ Builder │ │
│ Builder  │ │ Builder │ │  CC ~4  │ │
│  CC ~6   │ │  CC ~5  │ └─────────┘ │
└────┬─────┘ └────┬────┘             │
     │            │                  │
     │ builds     │ builds           │ builds
     ▼            ▼                  ▼
┌─────────┐ ┌──────────┐ ┌──────────────┐
│NX_Work  │ │NX_Work   │ │NX_Work       │
│         │ │SourceRef │ │Variant       │
└─────────┘ └──────────┘ └──────────────┘

✅ Single responsibility per builder
✅ Low complexity
✅ No duplication (builders reused 3x)
✅ Easy to maintain consistency
```

## Pattern Comparison

### Handler Pattern (Transport Layer)
**Use Case:** Breaking down complex API clients with many operations

```
Orchestrator
    ├── ConnectionHandler   (lifecycle)
    ├── CategoryHandler     (category ops)
    ├── StreamHandler       (stream ops)
    └── Mapper Classes      (JSON → DTO)
```

**Benefits:**
- Isolates network/lifecycle concerns
- Each operation type has its own handler
- Mappers eliminate duplication across types
- Easy to mock for testing

### Builder Pattern (Data Layer)
**Use Case:** Eliminating duplicated entity construction logic

```
Orchestrator
    ├── EntityBuilder1      (builds Type1)
    ├── EntityBuilder2      (builds Type2)
    └── EntityBuilder3      (builds Type3)
```

**Benefits:**
- Centralizes construction logic
- Ensures consistency across methods
- Easy to update entity structure
- Reusable across batch/single operations

## Key Metrics

### Lines of Code
- **Before:** 2922 lines (2312 + 610)
- **Extracted:** 1220 lines
- **Handlers/Builders:** 9 files
- **Documentation:** 3 files

### Cyclomatic Complexity
- **Before:** CC 52 (Xtream), CC 28 (NX)
- **After:** Avg CC 5 per handler/builder
- **Reduction:** 90% complexity reduction ✅

### Code Duplication
- **Eliminated:** ~500 lines
  - 300 lines (Xtream mappers)
  - 220 lines (NX builders reused 3x)

## Usage Example

### Xtream Handler Pattern
```kotlin
// Orchestrator delegates to handlers
class DefaultXtreamApiClient @Inject constructor(
    private val connectionManager: XtreamConnectionManager,
    private val categoryFetcher: XtreamCategoryFetcher,
    private val streamFetcher: XtreamStreamFetcher,
    private val liveMapper: LiveStreamMapper,
    private val vodMapper: VodStreamMapper,
    private val seriesMapper: SeriesStreamMapper,
) : XtreamApiClient {
    
    override suspend fun initialize(config: XtreamApiConfig) =
        connectionManager.initialize(config)
    
    override suspend fun getLiveCategories() =
        categoryFetcher.fetchLiveCategories()
    
    override suspend fun getVodStreams(categoryId: String?) =
        streamFetcher.fetchVodStreams(categoryId)
            .map { vodMapper.map(it) }
}
```

### NX Builder Pattern
```kotlin
// Orchestrator delegates to builders
class NxCatalogWriter @Inject constructor(
    private val workEntityBuilder: WorkEntityBuilder,
    private val sourceRefBuilder: SourceRefBuilder,
    private val variantBuilder: VariantBuilder,
    private val workRepository: NxWorkRepository,
    private val sourceRefRepository: NxWorkSourceRefRepository,
    private val variantRepository: NxWorkVariantRepository,
) {
    suspend fun ingest(raw: RawMediaMetadata, normalized: NormalizedMediaMetadata, accountKey: String) {
        val workKey = buildWorkKey(normalized)
        
        // Build and upsert using builders (eliminates 150 lines of duplication)
        val work = workEntityBuilder.build(normalized, workKey)
        workRepository.upsert(work)
        
        val sourceKey = buildSourceKey(...)
        val sourceRef = sourceRefBuilder.build(raw, workKey, accountKey, sourceKey)
        sourceRefRepository.upsert(sourceRef)
        
        if (raw.playbackHints.isNotEmpty()) {
            val variant = variantBuilder.build(...)
            variantRepository.upsert(variant)
        }
    }
}
```

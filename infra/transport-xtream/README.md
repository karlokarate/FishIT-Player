# transport-xtream

**Purpose:** HTTP client for Xtream Codes API, provides raw API responses to Pipeline layer.

## ✅ Allowed
- HTTP calls via OkHttp
- `XtreamApiClient` for API requests
- `XtreamDiscovery` (port & capability detection)
- `XtreamUrlBuilder` for URL construction
- Mapping API JSON → transport DTOs

## ❌ Forbidden
- `XtreamVodItem`, `XtreamSeriesItem` (pipeline DTOs)
- `RawMediaMetadata`
- Repository access
- Playback logic
- UI imports
- Normalization / TMDB

## Public Surface
- `XtreamApiClient` interface
- `XtreamDiscovery`, `XtreamUrlBuilder`
- API response DTOs (`XtreamLiveStream`, `XtreamVodStream`, etc.)

## Dependencies
| May Import | Must Never Import |
|------------|-------------------|
| `core:model`, OkHttp | `pipeline/*`, `data/*`, `playback/*`, `feature/*` |

```
Transport ← Pipeline ← Data ← Domain ← UI
   ▲
  YOU
```

## Common Mistakes
1. ❌ Creating `XtreamVodItem` here (belongs in Pipeline)
2. ❌ Importing pipeline model types
3. ❌ Accessing repositories from transport

## 🏗️ Handler Pattern Architecture (PLATIN Refactoring)

To reduce Cyclomatic Complexity (CC ≤ 15), `DefaultXtreamApiClient` delegates to specialized handlers:

### Handler Classes

```
infra/transport-xtream/
├── DefaultXtreamApiClient.kt       (Orchestrator - delegates to handlers)
├── client/
│   ├── XtreamConnectionManager.kt  (init, ping, close) - CC ~8
│   ├── XtreamCategoryFetcher.kt    (category operations) - CC ~4
│   └── XtreamStreamFetcher.kt      (streaming operations) - CC ~7
└── mapper/
    ├── LiveStreamMapper.kt         (JSON → LiveStream) - CC = 2
    ├── VodStreamMapper.kt          (JSON → VodStream) - CC = 2
    └── SeriesStreamMapper.kt       (JSON → SeriesInfo) - CC = 2
```

### Benefits
1. **Reduced Complexity:** Original CC ~52 → Handler average CC ~5
2. **Testability:** Each handler can be unit tested independently
3. **Maintainability:** Single responsibility per handler
4. **Reusability:** Mappers eliminate ~300 lines of duplication

### Example Usage

```kotlin
// XtreamConnectionManager handles lifecycle
suspend fun initialize(config: XtreamApiConfig): Result<XtreamCapabilities> {
    return connectionManager.initialize(config)
}

// XtreamCategoryFetcher handles categories
suspend fun getLiveCategories(): List<XtreamCategory> {
    return categoryFetcher.fetchLiveCategories()
}

// XtreamStreamFetcher handles streaming
suspend fun getVodStreams(categoryId: String?): List<XtreamVodStream> {
    return streamFetcher.fetchVodStreams(categoryId)
}
```

For implementation details, see PR #[issue_number].

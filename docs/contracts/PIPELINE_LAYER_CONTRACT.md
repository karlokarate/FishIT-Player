# Pipeline Layer Contract

**Generated from code on 2026-02-15 — DO NOT EDIT MANUALLY**

> Regenerate with: `./gradlew :tools:doc-generator:run --args="--contracts"`

---

## Components

### XtreamCatalogMapperImpl

- **Source Type:** Xtream
- **Generic Pattern:** `{Source}CatalogMapper`
- **File:** `pipeline/xtream/src/main/java/com/fishit/player/pipeline/xtream/mapper/XtreamCatalogMapper.kt`

**Responsibilities:**
- Map XtreamVodItem to XtreamCatalogItem with RawMediaMetadata
- Map XtreamSeriesItem/XtreamEpisode to XtreamCatalogItem
- Map XtreamChannel (live) to XtreamCatalogItem

**Implements:**
- `XtreamCatalogMapper`

---

### XtreamPipelineAdapter

- **Source Type:** Xtream
- **Generic Pattern:** `{Source}PipelineAdapter`
- **File:** `pipeline/xtream/src/main/java/com/fishit/player/pipeline/xtream/adapter/XtreamPipelineAdapter.kt`

**Responsibilities:**
- Convert transport DTOs (XtreamVodStream, XtreamLiveStream) to pipeline DTOs
- Expose auth/connection state from transport layer
- Fetch and convert episodes for series

**Implements:**
- `XtreamApiClient`

**Dependencies:**
- `XtreamApiClient`

---

### XtreamCatalogPipelineImpl

- **Source Type:** Xtream
- **Generic Pattern:** `{Source}CatalogPipeline`
- **File:** `pipeline/xtream/src/main/java/com/fishit/player/pipeline/xtream/catalog/XtreamCatalogPipelineImpl.kt`

**Responsibilities:**
- Stream VOD/Series/Live items in batches via PhaseScanOrchestrator
- Map Xtream DTOs to RawMediaMetadata via phase handlers
- Emit CatalogEvents progressively (streaming-first)
- Provide category fetching for selective sync

**Implements:**
- `XtreamCatalogPipeline`

**Dependencies:**
- `PhaseScanOrchestrator`
- `XtreamPipelineAdapter`

---

### DefaultXtreamCatalogSource

- **Source Type:** Xtream
- **Generic Pattern:** `{Source}CatalogSource`
- **File:** `pipeline/xtream/src/main/java/com/fishit/player/pipeline/xtream/catalog/DefaultXtreamCatalogSource.kt`

**Responsibilities:**
- Delegate content loading to XtreamPipelineAdapter
- Load VOD, Series, Live items and stream episodes
- Wrap transport errors in XtreamCatalogSourceException

**Implements:**
- `XtreamCatalogSource`

**Dependencies:**
- `XtreamPipelineAdapter`

---

## Naming Convention

| Source Type | Pattern | Example |
|------------|---------|---------|
| Xtream | `{Source}CatalogMapper` | `XtreamCatalogMapper` |
| Xtream | `{Source}PipelineAdapter` | `XtreamPipelineAdapter` |
| Xtream | `{Source}CatalogPipeline` | `XtreamCatalogPipeline` |
| Xtream | `{Source}CatalogSource` | `XtreamCatalogSource` |

## Future Sources

When implementing new sources (Telegram, Plex, etc.), follow the same patterns:

- `TelegramCatalogMapper` (Telegram)
- `PlexCatalogMapper` (Plex)
- `TelegramPipelineAdapter` (Telegram)
- `PlexPipelineAdapter` (Plex)
- `TelegramCatalogPipeline` (Telegram)
- `PlexCatalogPipeline` (Plex)
- `TelegramCatalogSource` (Telegram)
- `PlexCatalogSource` (Plex)

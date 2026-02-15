# Pipeline Layer Contract

**Generated from code on 2026-02-15 — DO NOT EDIT MANUALLY**

> Regenerate with: `./gradlew :tools:doc-generator:run --args="--contracts"`

---

## Components

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

## Naming Convention

| Source Type | Pattern | Example |
|------------|---------|---------|
| Xtream | `{Source}CatalogPipeline` | `XtreamCatalogPipeline` |

## Future Sources

When implementing new sources (Telegram, Plex, etc.), follow the same patterns:

- `TelegramCatalogPipeline` (Telegram)
- `PlexCatalogPipeline` (Plex)

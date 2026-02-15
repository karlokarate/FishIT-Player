# Persistence Layer Contract

**Generated from code on 2026-02-15 — DO NOT EDIT MANUALLY**

> Regenerate with: `./gradlew :tools:doc-generator:run --args="--contracts"`

---

## Components

### NxCatalogWriter

- **Source Type:** All
- **Generic Pattern:** `CatalogWriter`
- **File:** `infra/data-nx/src/main/java/com/fishit/player/infra/data/nx/writer/NxCatalogWriter.kt`

**Responsibilities:**
- Ingest normalized media into the NX work graph
- Create/update NX_Work, NX_WorkSourceRef, NX_WorkVariant entities
- Delegate entity construction to WorkEntityBuilder, SourceRefBuilder, VariantBuilder

**Implements:**
- `VariantBuilder`

**Dependencies:**
- `NxWorkRepository`
- `NxWorkSourceRefRepository`
- `NxWorkVariantRepository`
- `WorkEntityBuilder`
- `SourceRefBuilder`
- `VariantBuilder`

---

## Naming Convention

| Source Type | Pattern | Example |
|------------|---------|---------|
| All | `CatalogWriter` | `CatalogWriter` |

## Future Sources

When implementing new sources (Telegram, Plex, etc.), follow the same patterns:

- `CatalogWriter` (Telegram)
- `CatalogWriter` (Plex)

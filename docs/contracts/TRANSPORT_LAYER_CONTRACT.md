# Transport Layer Contract

**Generated from code on 2026-02-15 — DO NOT EDIT MANUALLY**

> Regenerate with: `./gradlew :tools:doc-generator:run --args="--contracts"`

---

## Components

### DefaultXtreamApiClient

- **Source Type:** Xtream
- **Generic Pattern:** `{Source}ApiClient`
- **File:** `infra/transport-xtream/src/main/java/com/fishit/player/infra/transport/xtream/DefaultXtreamApiClient.kt`

**Responsibilities:**
- Provide unified API facade for Xtream Codes provider
- Delegate lifecycle/auth to ConnectionManager
- Delegate category fetching to CategoryFetcher
- Delegate stream fetching to StreamFetcher

**Implements:**
- `XtreamApiClient`

**Dependencies:**
- `XtreamConnectionManager`
- `XtreamCategoryFetcher`
- `XtreamStreamFetcher`

---

## Naming Convention

| Source Type | Pattern | Example |
|------------|---------|---------|
| Xtream | `{Source}ApiClient` | `XtreamApiClient` |

## Future Sources

When implementing new sources (Telegram, Plex, etc.), follow the same patterns:

- `TelegramApiClient` (Telegram)
- `PlexApiClient` (Plex)

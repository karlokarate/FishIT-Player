# Transport Layer Contract

**Generated from code on 2026-02-15 — DO NOT EDIT MANUALLY**

> Regenerate with: `./gradlew :tools:doc-generator:run --args="--contracts"`

---

## Components

### XtreamConnectionManager

- **Source Type:** Xtream
- **Generic Pattern:** `{Source}ConnectionManager`
- **File:** `infra/transport-xtream/src/main/java/com/fishit/player/infra/transport/xtream/client/XtreamConnectionManager.kt`

**Responsibilities:**
- Handle port resolution and service discovery
- Manage authentication state lifecycle
- Detect and cache server capabilities
- Validate connection health

**Implements:**
- `XtreamPortStore? = null`

**Dependencies:**
- `OkHttpClient`
- `Json`
- `XtreamUrlBuilder`
- `XtreamDiscovery`
- `CoroutineDispatcher`
- `XtreamCapabilityStore`
- `XtreamPortStore`

---

### XtreamCategoryFetcher

- **Source Type:** Xtream
- **Generic Pattern:** `{Source}CategoryFetcher`
- **File:** `infra/transport-xtream/src/main/java/com/fishit/player/infra/transport/xtream/client/XtreamCategoryFetcher.kt`

**Responsibilities:**
- Fetch live/VOD/series categories from Xtream API
- Resolve VOD path aliases (movie/vod/movies)

**Implements:**
- `CoroutineDispatcher = Dispatchers.IO`

**Dependencies:**
- `OkHttpClient`
- `Json`
- `XtreamUrlBuilder`
- `CoroutineDispatcher`

---

### XtreamStreamFetcher

- **Source Type:** Xtream
- **Generic Pattern:** `{Source}StreamFetcher`
- **File:** `infra/transport-xtream/src/main/java/com/fishit/player/infra/transport/xtream/client/XtreamStreamFetcher.kt`

**Responsibilities:**
- Fetch VOD/Live/Series streams from Xtream API
- Stream large result sets in batches (memory-efficient)
- Fetch detail endpoints (getVodInfo, getSeriesInfo)
- Handle VOD path alias resolution and fallback

**Implements:**
- `CoroutineDispatcher = Dispatchers.IO`

**Dependencies:**
- `OkHttpClient`
- `Json`
- `XtreamUrlBuilder`
- `CategoryFallbackStrategy`
- `CoroutineDispatcher`

---

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
| Xtream | `{Source}ConnectionManager` | `XtreamConnectionManager` |
| Xtream | `{Source}CategoryFetcher` | `XtreamCategoryFetcher` |
| Xtream | `{Source}StreamFetcher` | `XtreamStreamFetcher` |
| Xtream | `{Source}ApiClient` | `XtreamApiClient` |

## Future Sources

When implementing new sources (Telegram, Plex, etc.), follow the same patterns:

- `TelegramConnectionManager` (Telegram)
- `PlexConnectionManager` (Plex)
- `TelegramCategoryFetcher` (Telegram)
- `PlexCategoryFetcher` (Plex)
- `TelegramStreamFetcher` (Telegram)
- `PlexStreamFetcher` (Plex)
- `TelegramApiClient` (Telegram)
- `PlexApiClient` (Plex)

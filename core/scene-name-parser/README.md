# core:scene-name-parser

**Purpose:** Pure Kotlin/JVM parsing module for normalizing scene-style release names (movies + series episodes) from Telegram file/caption inputs and Xtream library inputs.

## ✅ Allowed
- Parse scene release names (title, year, season, episode, technical tags)
- Extract TMDB IDs/URLs from raw strings (no network lookups)
- Fast, deterministic, pure string parsing
- Token cleanup/sanitization based on source hints (TELEGRAM vs XTREAM)
- Return structured ParsedReleaseName or Unparsed result

## ❌ Forbidden
- Android dependencies (no android.*, no Compose, no Media3)
- Network clients, TMDB API calls, database access
- Any I/O operations
- Mutable global state

## Public Surface
- `SceneNameParser` interface
- `SceneNameInput`, `SceneNameParseResult`, `ParsedReleaseName` data classes
- `SourceHint` enum (TELEGRAM, XTREAM)
- `TmdbType` enum (MOVIE, TV)

## Dependencies
| May Import | Must Never Import |
|------------|-------------------|
| `parsikle-jvm` library | `transport/*`, `pipeline/*`, `data/*`, `playback/*`, Android SDK |

## Architecture Position
```
Pipeline ──► core:metadata-normalizer ──► core:scene-name-parser
                       ▲
                      ONLY CONSUMER
```

**HARD RULE:** Only `:core:metadata-normalizer` may depend on this module.
Pipelines stay RAW; only the central normalizer calls this parser.

## Common Mistakes
1. ❌ Using this parser directly in pipeline modules
2. ❌ Adding network/TMDB lookup logic here
3. ❌ Storing parser results to DB (Data layer responsibility)
4. ❌ Adding Android-specific dependencies

# Normalizer Layer Contract

**Generated from code on 2026-02-15 — DO NOT EDIT MANUALLY**

> Regenerate with: `./gradlew :tools:doc-generator:run --args="--contracts"`

---

## Components

### RegexMediaMetadataNormalizer

- **Source Type:** All
- **Generic Pattern:** `MediaMetadataNormalizer`
- **File:** `core/metadata-normalizer/src/main/java/com/fishit/player/core/metadata/RegexMediaMetadataNormalizer.kt`

**Responsibilities:**
- Clean and normalize media titles from raw input
- Extract year, season, episode from scene-style naming
- Determine media type from metadata heuristics
- Handle untrusted input safely with RE2J (O(n) guarantee)

**Implements:**
- `MediaMetadataNormalizer`

**Dependencies:**
- `SceneNameParser`

---

## Naming Convention

| Source Type | Pattern | Example |
|------------|---------|---------|
| All | `MediaMetadataNormalizer` | `MediaMetadataNormalizer` |

## Future Sources

When implementing new sources (Telegram, Plex, etc.), follow the same patterns:

- `MediaMetadataNormalizer` (Telegram)
- `MediaMetadataNormalizer` (Plex)

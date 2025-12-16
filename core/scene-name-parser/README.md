# :core:scene-name-parser

**Module Type:** Core / Pure Kotlin  
**Scope:** Scene release name parsing and technical metadata extraction  
**Layer:** Core (used only by `:core:metadata-normalizer`)

---

## Purpose

This module provides a **deterministic parser** for "scene-style" release names commonly found in:

- Telegram media file names and captions
- Xtream library content titles
- Local media files with structured naming

The parser extracts structured metadata such as:
- Title
- Year
- Season/Episode numbers
- Technical metadata (resolution, codec, audio, source, release group)
- TMDB IDs/URLs (when present in the raw string)

---

## Responsibilities

✅ **DOES:**
- Parse scene release names using Parsikle combinators
- Extract title, year, season, episode from structured patterns
- Extract technical metadata (resolution, codec, audio, language, source)
- Extract TMDB IDs and URLs from raw strings
- Handle both Telegram and Xtream naming conventions
- Provide deterministic, fast parsing with no side effects

❌ **DOES NOT:**
- Perform TMDB lookups or network calls
- Access databases or persistence
- Normalize metadata (that's the normalizer's job)
- Depend on Android APIs
- Know about pipelines, transport, or playback

---

## Architecture Position

```text
Pipeline → RawMediaMetadata → Normalizer → (uses SceneNameParser) → NormalizedMedia
```

**Layer Boundary:**
- Only `:core:metadata-normalizer` may depend on this module
- Pipelines MUST NOT use this parser directly
- This module has zero dependencies on other core modules

---

## Public API

See `api/SceneNameParser.kt` for the complete interface.

**Main Entry Point:**
```kotlin
interface SceneNameParser {
    fun parse(input: SceneNameInput): SceneNameParseResult
}
```

**Input:**
```kotlin
data class SceneNameInput(
    val raw: String,
    val sourceHint: SourceHint  // TELEGRAM or XTREAM
)
```

**Output:**
```kotlin
sealed class SceneNameParseResult {
    data class Parsed(val value: ParsedReleaseName) : SceneNameParseResult()
    data class Unparsed(val reason: String) : SceneNameParseResult()
}
```

**Usage Example:**
```kotlin
// Only called from :core:metadata-normalizer
val parser = DefaultSceneNameParser()
val result = parser.parse(
    SceneNameInput(
        raw = "The.Matrix.1999.1080p.BluRay.x264",
        sourceHint = SourceHint.TELEGRAM
    )
)

when (result) {
    is SceneNameParseResult.Parsed -> {
        val parsed = result.value
        // parsed.title == "The Matrix"
        // parsed.year == 1999
        // parsed.resolution == "1080p"
        // parsed.videoCodec == "x264"
    }
    is SceneNameParseResult.Unparsed -> {
        // Fallback to raw title
    }
}
```

---

## Implementation

The parser uses **Parsikle** (MIT-licensed combinator library) to implement a multi-stage pipeline:

1. **Pre-normalization**: Clean Telegram noise (emojis, channel tags, brackets)
2. **Tokenization**: Split on separators while preserving structured tokens
3. **Pattern matching**: Use Parsikle combinators to match series/movie/technical patterns
4. **TMDB extraction**: Extract TMDB IDs/URLs using lightweight string parsing
5. **Best guess**: Deterministic selection when multiple parses are possible

See `impl/` package for implementation details.

---

## Dependencies

- **Parsikle** (`io.github.gmulders:parsikle-jvm:0.0.1`): Pure Kotlin parser combinator library
- No Android dependencies
- No network libraries
- No database libraries

---

## Testing

100 test cases covering:
- 50 Telegram-style inputs
- 50 Xtream-style inputs
- TMDB URL/ID extraction
- Performance characteristics

See `SceneNameParserTest.kt` for complete test suite.

---

## Compliance

✅ Pure Kotlin/JVM (no Android)  
✅ Zero I/O or network  
✅ Deterministic and fast  
✅ Only used by normalizer (layer boundary enforced)  
✅ No pipeline dependencies

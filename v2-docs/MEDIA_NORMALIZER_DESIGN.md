# Media Metadata Normalizer Design

## 1. Executive Summary

This document describes the design and implementation of a production-grade Kotlin media filename/metadata parser for the FishIT-Player v2 architecture. The parser extracts structured metadata from scene-style filenames and integrates with the existing `MediaMetadataNormalizer` contract.

### Key Features
- **Scene-aware parsing**: Handles scene release naming conventions (resolution, codec, source, group tags)
- **Episode detection**: Robust SxxEyy pattern matching for TV series
- **Title extraction**: Cleans titles by removing technical tags while preserving the content
- **Year extraction**: Safely extracts release year with anti-false-positive rules
- **Multi-language support**: Works with German, English, and multi-language filenames
- **Deterministic**: Same input always produces same output

### Design Philosophy
- **Minimal and maintainable**: Curated regex patterns inspired by industry-standard parsers
- **Tuned to real data**: Validated against 398 Telegram chat JSON exports
- **Contract-compliant**: Fully aligned with `MEDIA_NORMALIZATION_CONTRACT.md`
- **Zero external dependencies**: Pure Kotlin/JVM implementation

---

## 2. Research Foundation

This design is informed by analysis of leading open-source media parsers:

### 2.1 External Parser Research

#### **@ctrl/video-filename-parser** (JavaScript)
- Repository: https://github.com/scttcper/video-filename-parser
- Key learnings:
  - Ordered tokenization approach (process tags first, then extract title)
  - Resolution patterns: `720p`, `1080p`, `2160p`, `4K`, `UHD`
  - Codec patterns: `x264`, `x265`, `H264`, `H265`, `HEVC`, `XviD`
  - Source patterns: `WEB-DL`, `WEBRip`, `BluRay`, `DVDRip`, `HDTV`
  - Group extraction: typically after final hyphen or in brackets

#### **guessit** (Python)
- Repository: https://github.com/guessit-io/guessit
- Documentation: https://guessit.readthedocs.io
- Key learnings:
  - Matcher-based architecture for extensibility
  - Episode vs. movie detection via structural heuristics
  - Exception lists for ambiguous titles (e.g., "OSS 117" vs "S01E17")
  - Properties: season, episode, episode title, format (Special, Pilot, Final)
  - Uses Rebulk framework for pattern registration and conflict resolution

#### **scene-release-parser-php** (PHP)
- Repository: https://github.com/pr0pz/scene-release-parser-php
- Key learnings:
  - Comprehensive regex arrays for scene tags
  - Resolution: `/(\\d{3,4}p)/`
  - Codec: `/\\b(x264|x265|h264|hevc|xvid)\\b/i`
  - Source: `/\\b(WEBDL|WEBRip|BluRay|DVDRip|HDTV|BDRip)\\b/i`
  - Audio: `/\\b(DD\\d\\.\\d|AAC\\d\\.\\d|AC3|DTS)\\b/`
  - Language: `/\\b(ENGLISH|GERMAN|FRENCH|MULTI|VOSTFR)\\b/i`
  - Flags: `/\\b(PROPER|REPACK|UNRATED|EXTENDED)\\b/i`
  - Group: `/-(\\w+)$/` (captures after last hyphen)

### 2.2 Real Data Analysis

Analyzed **398 JSON export files** from `docs/telegram/exports/exports/`:

#### Chat Classification
- **Movie chats**: 7+ (e.g., "🎬 Filme von 2011 bis 2019 🎥", "Mel Brooks 🥳", "🎬⚠️ Filme ab: 2020 ⚠️🎥")
- **Series chats**: 7+ (e.g., "Sex and the City Full HD", "Breaking Bad FULL HD", "Unbesiegbar FULL HD")
- **Mixed content**: Multiple chats with both movies and series

#### Filename Patterns Observed

**Movies with year:**
```
Die Olsenbande in feiner Gesellschaft 3D - 2010.mp4
Das Ende der Welt - Die 12 Prophezeiungen der Maya - 2012.mp4
Terrordactyl - Die Killer Saurier - 2016.mp4
Mel Brooks letzte Verrücktheit - Silent Movie - 1976.mp4
```

**Movies with quality tags:**
```
Champagne Problems - 2025 HDR DD+5.1 with Dolby Atmos.mp4
Beast of War - 2025 HDR DD+5.1.mp4
Clown - 2014 HDR DTS 5.1.mp4
After the Hunt - 2025 (AV1) HDR AAC 5.1.mp4
```

**Series with SxxEyy:**
```
Sex and the City - S06 E20 - Eine Amerikanerin in Paris (Teil 2).mp4
Breaking Bad - S05 E16 - Felina.mp4
Happy Tree Friends - S02 E27 - From A to Zoo (1).mp4
INVINCIBLE - S03 E08 - Ich dachte schon, du würdest nie die.mp4
```

**Series with compact naming:**
```
S10E26 - @ArcheMovie - PAW Patrol.mp4
Graf Duckula - S04 E07 - Wenn Zombies träumen.avi
The New Batman Adventures - S04 E24 - Der Richter.mp4
```

#### Key Insights
1. **Delimiter variety**: Both hyphen and underscore separators
2. **German/English mix**: Many German titles with English technical tags
3. **Quality-first naming**: Modern files lead with quality tags (HDR, 4K)
4. **Flexible spacing**: `S06 E20` vs `S06E20` vs `S06_E20`
5. **Episode titles**: Often preserved after season/episode marker
6. **Audio tags**: DD+5.1, AAC 5.1, DTS formats common
7. **Codec variance**: Both old (x264) and new (AV1, HEVC) codecs present

---

## 3. Architecture Design

### 3.1 Core Types

#### ParsedSceneInfo
The primary output of the scene parser:

```kotlin
/**
 * Parsed metadata from a scene-style filename.
 *
 * Represents structured information extracted from media filenames
 * using scene release naming conventions.
 */
data class ParsedSceneInfo(
    /** Cleaned title with technical tags removed */
    val title: String,
    
    /** Release year if detected (1900-2099) */
    val year: Int? = null,
    
    /** True if filename contains episode markers (SxxEyy) */
    val isEpisode: Boolean = false,
    
    /** Season number for episodes (1-99) */
    val season: Int? = null,
    
    /** Episode number for episodes (1-999) */
    val episode: Int? = null,
    
    /** Quality and technical metadata */
    val quality: QualityInfo? = null,
    
    /** Edition flags (Extended, Director's Cut, Unrated, etc.) */
    val edition: EditionInfo? = null,
    
    /** Unrecognized tags for debugging/logging */
    val extraTags: List<String> = emptyList()
)
```

#### QualityInfo
Technical quality metadata:

```kotlin
/**
 * Quality and technical metadata from filename.
 */
data class QualityInfo(
    /** Resolution: 480p, 720p, 1080p, 2160p, 4K, 8K, UHD */
    val resolution: String? = null,
    
    /** Source: WEB-DL, WEBRip, BluRay, DVDRip, HDTV, BDRip, etc. */
    val source: String? = null,
    
    /** Video codec: x264, x265, H264, H265, HEVC, AV1, XviD */
    val codec: String? = null,
    
    /** Audio codec: AAC, AC3, DD, DD+, DTS, Dolby Atmos */
    val audio: String? = null,
    
    /** HDR format: HDR, HDR10, HDR10+, Dolby Vision */
    val hdr: String? = null,
    
    /** Release group name (after final hyphen or in brackets) */
    val group: String? = null
)
```

#### EditionInfo
Special edition flags:

```kotlin
/**
 * Edition and release flags from filename.
 */
data class EditionInfo(
    /** Extended cut/edition */
    val extended: Boolean = false,
    
    /** Director's cut */
    val directors: Boolean = false,
    
    /** Unrated version */
    val unrated: Boolean = false,
    
    /** Theatrical cut */
    val theatrical: Boolean = false,
    
    /** 3D release */
    val threeD: Boolean = false,
    
    /** IMAX release */
    val imax: Boolean = false,
    
    /** Remastered version */
    val remastered: Boolean = false,
    
    /** PROPER/REPACK (scene flags) */
    val proper: Boolean = false,
    val repack: Boolean = false
)
```

### 3.2 Parser Interface

```kotlin
/**
 * Interface for parsing media filenames.
 *
 * Implementations should be deterministic: same input → same output.
 * Must not perform network calls or access external services.
 */
interface SceneNameParser {
    /**
     * Parse a filename into structured metadata.
     *
     * @param filename The filename to parse (with or without extension)
     * @return Parsed scene information
     */
    fun parse(filename: String): ParsedSceneInfo
}
```

### 3.3 Normalizer Integration

The `RegexMediaMetadataNormalizer` implementation:

```kotlin
/**
 * Regex-based media metadata normalizer.
 *
 * Uses SceneNameParser to extract structured metadata from filenames,
 * then maps to NormalizedMediaMetadata per MEDIA_NORMALIZATION_CONTRACT.md.
 */
class RegexMediaMetadataNormalizer(
    private val sceneParser: SceneNameParser = RegexSceneNameParser()
) : MediaMetadataNormalizer {
    
    override suspend fun normalize(raw: RawMediaMetadata): NormalizedMediaMetadata {
        // Parse filename
        val parsed = sceneParser.parse(raw.originalTitle)
        
        // Determine canonical title (use parsed title)
        val canonicalTitle = parsed.title
        
        // Prefer explicit metadata over parsed
        val year = raw.year ?: parsed.year
        val season = raw.season ?: parsed.season
        val episode = raw.episode ?: parsed.episode
        
        return NormalizedMediaMetadata(
            canonicalTitle = canonicalTitle,
            year = year,
            season = season,
            episode = episode,
            tmdbId = raw.externalIds.tmdbId,
            externalIds = raw.externalIds
        )
    }
}
```

---

## 4. Implementation Strategy

### 4.1 Parsing Algorithm

**Phase 1: Preprocessing**
1. Remove file extension
2. Replace common separators (`.`, `_`) with spaces
3. Normalize whitespace

**Phase 2: Tag Extraction (Order Matters)**
1. Extract edition flags (Extended, Director's Cut, Unrated, 3D, IMAX)
2. Extract quality tags (resolution, source, codec, audio, HDR)
3. Extract channel tags
4. Extract season/episode markers (SxxEyy patterns)
5. Extract year (with anti-false-positive checks)
6. Extract release group (after year to avoid conflicts; after last `-` or in brackets)

**Phase 3: Title Extraction**
1. Remove all extracted tags from filename
2. Trim remaining string
3. Clean up residual separators

**Phase 4: Validation**
1. Ensure title is non-empty
2. Validate year range (1900-2099)
3. Validate season/episode ranges

### 4.2 Regex Patterns

#### Resolution
```regex
\b(480p|576p|720p|1080p|2160p|4320p|8K|4K|UHD)\b
```

#### Codec
```regex
\b(x264|x265|H\.?264|H\.?265|HEVC|AV1|XviD|DivX|VC-?1)\b
```

#### Source
```regex
\b(WEB-?DL|WEB-?Rip|WEBRip|BluRay|Blu-Ray|BDRip|DVDRip|DVD-Rip|HDTV|PDTV|DVDSCR)\b
```

#### Audio
```regex
\b(AAC(?:\d\.\d)?|AC3|DD(?:\+)?(?:\d\.\d)?|DTS(?:-HD)?|Dolby\s+Atmos|TrueHD|FLAC|Opus)\b
```

#### HDR
```regex
\b(HDR10\+|HDR10|HDR|Dolby\s+Vision|DV)\b
```

#### Season/Episode
```regex
# Standard: S01E02, S1E2, S01 E02
[Ss](\d{1,2})\s?[Ee](\d{1,3})

# Compact: 1x02
(\d{1,2})x(\d{1,3})

# Multi-episode: S01E01-E03, S01E01E02
[Ss](\d{1,2})[Ee](\d{1,3})(?:-?[Ee](\d{1,3}))?
```

#### Year
```regex
# Standalone year with boundaries
\b(19\d{2}|20\d{2})\b

# Year in parentheses (more reliable)
\(?(19\d{2}|20\d{2})\)?
```

#### Release Group
```regex
# After last hyphen
-\s*([A-Za-z0-9]+)\s*$

# In brackets
\[([A-Za-z0-9]+)\]
```

### 4.3 Edge Cases & Heuristics

#### Titles with Numbers
**Problem**: "2001: A Space Odyssey", "1984", "300", "Apollo 13"
**Solution**: Year must be:
- Followed by extension, quality tag, or end of string
- Not preceded by colon or "part of title" indicators
- In parentheses for high confidence

#### Multi-Episode Releases
**Pattern**: `S01E01-E03`, `S01E01E02E03`
**Strategy**: Capture first episode number only, note multi-episode in extraTags

#### German Umlauts and Special Characters
**Strategy**: Preserve Unicode characters in titles, only remove technical tags

#### Ambiguous Separators
**Pattern**: `Movie.Title.2020.1080p` vs `Movie - Title - 2020.mp4`
**Strategy**: Prefer hyphen as major separator, dots as word separators

#### Year Near End vs Year in Title
**Heuristic**: Year within last 30% of filename (after tag removal) is likely release year

---

## 5. Testing Strategy

### 5.1 Test Categories

1. **Movie titles with years**
   - Simple: `Die Maske - 1994.mp4` → title="Die Maske", year=1994
   - Complex: `Das Ende der Welt - Die 12 Prophezeiungen der Maya - 2012.mp4`

2. **Series with episodes**
   - Standard: `Sex and the City - S06 E20.mp4` → season=6, episode=20
   - Compact: `S10E26 - PAW Patrol.mp4`
   - With title: `Breaking Bad - S05 E16 - Felina.mp4`

3. **Quality tags**
   - Resolution: `Movie - 2020 1080p.mp4`
   - HDR: `Movie - 2025 HDR DD+5.1.mp4`
   - Codec: `Movie - 2020 (AV1) HDR.mp4`

4. **Edge cases**
   - Titles with numbers: `2001 - A Space Odyssey - 1968.mp4`
   - Multiple years: `Movie - 1984 - 2019.mp4` (prefer later)
   - Multi-episode: `Show - S01E01-E03.mp4`
   - No year: `Random Movie.mp4`

5. **Real data validation**
   - Sample 50+ filenames from JSON exports
   - Verify title extraction correctness
   - Verify year/episode detection accuracy

### 5.2 Test Data Sources

Primary: Filenames from `docs/telegram/exports/exports/*.json`
- Validates real-world usage
- Covers German/English mixed content
- Includes modern and legacy formats

---

## 6. Known Limitations

### 6.1 Ambiguous Cases

1. **Titles containing technical-looking words**
   - Example: "The 720p Conspiracy" - might strip "720p" incorrectly
   - Mitigation: Only strip when pattern matches typical tag format

2. **Foreign language titles**
   - Non-Latin scripts may not parse correctly
   - German umlauts handled, but Cyrillic/Asian scripts need testing

3. **Non-standard naming**
   - User-uploaded files with arbitrary names won't parse well
   - Mitigation: Preserve original title as canonical if parsing fails

4. **Multiple years in title**
   - Example: "The 1984 War of 2019 - 2020.mp4"
   - Heuristic: Prefer year closest to end

5. **Anime naming conventions**
   - Anime often uses different patterns (episode ranges, OVA/ONA markers)
   - Limited support in v1, can be extended later

### 6.2 Out of Scope

- **TMDB lookups**: Handled by `TmdbMetadataResolver`
- **Subtitle detection**: Not needed for metadata normalization
- **Multi-part files**: Part 1/2 detection not critical for identity
- **Language detection**: Not part of canonical identity

---

## 7. Pipeline Integration Guide

### 7.1 Telegram Pipeline

**Current state**: `TelegramMediaItem.toRawMediaMetadata()`

```kotlin
// In TelegramMediaItem
fun toRawMediaMetadata(): RawMediaMetadata {
    return RawMediaMetadata(
        originalTitle = fileName ?: caption ?: "Unknown", // NO cleaning
        year = null, // Let normalizer extract from filename
        season = null, // Let normalizer extract from filename
        episode = null, // Let normalizer extract from filename
        durationMinutes = durationSeconds?.div(60),
        externalIds = ExternalIds(), // Telegram doesn't provide
        sourceType = SourceType.TELEGRAM,
        sourceLabel = "Telegram: $chatTitle",
        sourceId = "$chatId:$messageId"
    )
}
```

**Normalizer usage**:
```kotlin
val raw = telegramItem.toRawMediaMetadata()
val normalized = normalizer.normalize(raw) // Extracts from filename
```

### 7.2 Xtream Pipeline

**Current state**: Xtream API often provides structured metadata

```kotlin
// In XtreamMediaItem
fun toRawMediaMetadata(): RawMediaMetadata {
    return RawMediaMetadata(
        originalTitle = name ?: title ?: streamName, // NO cleaning
        year = releaseDate?.year, // Use API-provided if available
        season = seasonNumber, // Use API-provided if available
        episode = episodeNumber, // Use API-provided if available
        durationMinutes = durationSecs?.div(60),
        externalIds = ExternalIds(
            tmdbId = tmdbId?.toString() // Pass through
        ),
        sourceType = SourceType.XTREAM,
        sourceLabel = "Xtream: $serverName",
        sourceId = streamId.toString()
    )
}
```

**Normalizer behavior**:
- Uses API-provided metadata when available
- Falls back to filename parsing for missing fields

### 7.3 IO Pipeline (Local Files)

**Current state**: Only filename available

```kotlin
// In IoMediaItem
fun toRawMediaMetadata(): RawMediaMetadata {
    return RawMediaMetadata(
        originalTitle = file.name, // Just the filename
        year = null, // Let normalizer extract
        season = null, // Let normalizer extract
        episode = null, // Let normalizer extract
        durationMinutes = null, // Would need MediaMetadataRetriever
        externalIds = ExternalIds(),
        sourceType = SourceType.IO,
        sourceLabel = "Local: ${file.parent}",
        sourceId = file.absolutePath
    )
}
```

**Normalizer usage**: Primary use case - relies entirely on filename parsing

---

## 8. Future Enhancements

### 8.1 Short-term (Next 6 months)

1. **Anime support**
   - Detect OVA, ONA, Special markers
   - Handle episode ranges (01-12)
   - Recognize fansub group conventions

2. **Confidence scoring**
   - Return confidence level for each parsed field
   - Allow higher-level logic to handle low-confidence cases

3. **Multi-language title extraction**
   - Detect language tags in filename
   - Extract both original and localized titles

### 8.2 Long-term (6-12 months)

1. **Machine learning enhancement**
   - Train model on user corrections
   - Improve ambiguous case handling

2. **User feedback loop**
   - Allow users to correct parsed metadata
   - Use corrections to improve patterns

3. **Extended metadata**
   - Detect part numbers (CD1, Disc 1, Part 2)
   - Extract subtitle language tags
   - Detect bonus content markers (Extras, Deleted Scenes)

---

## 9. References

### External Projects Studied
- **@ctrl/video-filename-parser**: https://github.com/scttcper/video-filename-parser
- **guessit**: https://github.com/guessit-io/guessit (docs: https://guessit.readthedocs.io)
- **scene-release-parser-php**: https://github.com/pr0pz/scene-release-parser-php
- **thcolin/scene-release-parser-php**: https://github.com/thcolin/scene-release-parser-php
- **scene-release** (JS): https://github.com/matiassingers/scene-release
- **oleoo** (JS): https://github.com/thcolin/oleoo

### Project Documentation
- `v2-docs/MEDIA_NORMALIZATION_CONTRACT.md`: Authoritative contract
- `v2-docs/ARCHITECTURE_OVERVIEW_V2.md`: Overall v2 architecture
- `core/model/src/main/java/com/fishit/player/core/model/RawMediaMetadata.kt`
- `core/model/src/main/java/com/fishit/player/core/model/NormalizedMediaMetadata.kt`

### Data Sources
- `docs/telegram/exports/exports/*.json`: 398 JSON export files
- Real Telegram chat data with German and English content
- Mix of movies (years 1976-2025) and TV series (various seasons)

---

## 10. Usage Examples

### 10.1 Basic Usage

```kotlin
// Create parser
val parser = RegexSceneNameParser()

// Parse a movie filename
val movieInfo = parser.parse("Die Maske - 1994.mp4")
println("Title: ${movieInfo.title}")        // "Die Maske"
println("Year: ${movieInfo.year}")          // 1994
println("Is Episode: ${movieInfo.isEpisode}") // false

// Parse a series filename
val seriesInfo = parser.parse("Breaking Bad - S05 E16 - Felina.mp4")
println("Title: ${seriesInfo.title}")       // "Breaking Bad Felina"
println("Season: ${seriesInfo.season}")     // 5
println("Episode: ${seriesInfo.episode}")   // 16
println("Is Episode: ${seriesInfo.isEpisode}") // true

// Parse with quality tags
val qualityInfo = parser.parse("Movie Title 2020 1080p WEB-DL x264-GROUP.mp4")
println("Title: ${qualityInfo.title}")                  // "Movie Title"
println("Year: ${qualityInfo.year}")                    // 2020
println("Resolution: ${qualityInfo.quality?.resolution}") // "1080p"
println("Source: ${qualityInfo.quality?.source}")       // "WEB-DL"
println("Codec: ${qualityInfo.quality?.codec}")         // "x264"
println("Group: ${qualityInfo.quality?.group}")         // "GROUP"
```

### 10.2 Integration with Normalizer

```kotlin
// Create normalizer with parser
val normalizer = RegexMediaMetadataNormalizer()

// Normalize raw metadata from Telegram
val raw = RawMediaMetadata(
    originalTitle = "Champagne Problems - 2025 HDR DD+5.1.mp4",
    year = null,  // Will be extracted from filename
    season = null,
    episode = null,
    durationMinutes = 115,
    externalIds = ExternalIds(),
    sourceType = SourceType.TELEGRAM,
    sourceLabel = "Telegram: Movies",
    sourceId = "tg://message/123"
)

val normalized = normalizer.normalize(raw)
println("Canonical Title: ${normalized.canonicalTitle}") // "Champagne Problems"
println("Year: ${normalized.year}")                      // 2025
```

### 10.3 Handling Xtream API with Explicit Metadata

```kotlin
// Xtream provides structured metadata - normalizer prefers explicit values
val xtreamRaw = RawMediaMetadata(
    originalTitle = "Movie Title - 2019.mp4",  // Has year in filename
    year = 2020,  // But API says it's actually 2020
    season = null,
    episode = null,
    durationMinutes = 120,
    externalIds = ExternalIds(tmdbId = "12345"),
    sourceType = SourceType.XTREAM,
    sourceLabel = "Xtream: Provider A",
    sourceId = "xtream://vod/999"
)

val normalized = normalizer.normalize(xtreamRaw)
// Uses explicit year from API, not parsed year
println("Year: ${normalized.year}")  // 2020 (not 2019)
println("TMDB ID: ${normalized.tmdbId}")  // "12345" (preserved)
```

---

## 11. Changelog

- **2025-12-06**: Initial design document created
  - Research phase completed (external parsers + real data analysis)
  - Core types defined (ParsedSceneInfo, QualityInfo, EditionInfo)
  - Parsing strategy documented
  - Integration guide for pipelines

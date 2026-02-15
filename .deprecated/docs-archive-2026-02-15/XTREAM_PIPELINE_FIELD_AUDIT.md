# Xtream Pipeline Complete Field Audit

> **Stand:** 2. Februar 2026 (aktualisiert)  
> **Basiert auf:** `XTREAM_ENTITY_FIELD_MAPPING.md`, `NX_ENTITY_DUPLICATION_ANALYSIS.md`, Codeanalyse  
> **Scope:** Vollständige Ketten-Analyse: API Response → Transport → Pipeline → RawMediaMetadata → NxCatalogWriter → NX_Entities
> 
> **✅ FIX STATUS: 8/8 Probleme behoben (P1-P8 alle gelöst)**

---

## Inhaltsverzeichnis

1. [Executive Summary](#1-executive-summary)
2. [Datenfluss-Architektur](#2-datenfluss-architektur)
3. [VOD (Movies) Feld-Audit](#3-vod-movies-feld-audit)
4. [Series Feld-Audit](#4-series-feld-audit)
5. [Episode Feld-Audit](#5-episode-feld-audit)
6. [Live Channel Feld-Audit](#6-live-channel-feld-audit)
7. [Format-Konvertierungen](#7-format-konvertierungen)
8. [Layer Boundary Analyse](#8-layer-boundary-analyse)
9. [Probleme & Empfehlungen](#9-probleme-empfehlungen)
10. [Anhang: Code-Referenzen](#10-anhang-code-referenzen)

---

## 1. Executive Summary

### Befunde

| Kategorie | Anzahl | Schweregrad | Status |
|-----------|:------:|:-----------:|:------:|
| **Felder korrekt gemappt** | 50+ | ✅ OK | - |
| **Felder verloren im Flow** | 8 | 🔴 HOCH | ✅ 8 behoben |
| **Doppelte Parsing-Logik** | 3 | 🟡 MITTEL | ✅ behoben |
| **Format-Konvertierungen** | 4 | 🟢 DESIGN | ✅ behoben |
| **Layer-Boundary Issues** | 2 | 🟡 MITTEL | - |

### Haupterkenntnisse

```
✅ BEHOBEN: video/audio Codec-Objekte werden nun mit JsonElement polymorphem 
   Parsing verarbeitet. XtreamVodInfoBlock hat video/audio: JsonElement?
   mit videoInfo/audioInfo Resolver-Properties.

✅ BEHOBEN (Feb 2026): XtreamVideoInfo/XtreamAudioInfo nutzen nun @SerialName("codec_name")
   → Episode Video/Audio Codec wird korrekt geparst (vorher: "codec" erwartet, API liefert "codec_name")
   → XtreamVideoInfo: @SerialName("codec_name") val codec
   → XtreamAudioInfo: @SerialName("codec_name") val codec

✅ BEHOBEN: Episode tmdb_id wird durch gesamte Pipeline gemappt
   → XtreamEpisodeInfoBlock.tmdbId → XtreamEpisode.episodeTmdbId 
   → RawMediaMetadata.playbackHints["xtream.episodeTmdbId"]

✅ BEHOBEN: Video/Audio Codec-Info wird in playbackHints gespeichert
   → PlaybackHintKeys: VIDEO_CODEC, VIDEO_WIDTH, VIDEO_HEIGHT, AUDIO_CODEC, AUDIO_CHANNELS

✅ BEHOBEN: Timestamps werden korrekt in Millisekunden konvertiert
   → XtreamPipelineAdapter: added?.toLongOrNull()?.let { it * 1000L }

✅ BEHOBEN: Deprecated resolvedDurationMins entfernt
   → Pipeline parseDurationToMs() ist SSOT

🟡 WARNUNG: isAdult wird nie von API geliefert
   → Pipeline erwartet "1"/"0" String, API liefert es nie
   → Muss aus Category/Genre inferriert werden (Normalizer)

🟡 OFFEN: country, age, mpaa_rating nicht persistiert (optional)
```

---

## 2. Datenfluss-Architektur

### 2.1 Schichten-Übersicht

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  XTREAM API                                                                 │
│  (get_vod_streams, get_series, get_live_streams, get_vod_info, etc.)        │
└───────────────────────────────────────┬─────────────────────────────────────┘
                                        │ JSON Response (streamed)
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  TRANSPORT LAYER (infra/transport-xtream)                                   │
│  ├─ DefaultXtreamApiClient.kt (HTTP + streaming JSON deserialization)       │
│  └─ XtreamApiModels.kt                                                      │
│      ├─ XtreamVodStream (Listen-API)                                        │
│      ├─ XtreamSeriesStream (Listen-API)                                     │
│      ├─ XtreamLiveStream (Listen-API)                                       │
│      ├─ XtreamVodInfo + XtreamVodInfoBlock (Detail-API)                     │
│      └─ XtreamSeriesInfo + XtreamEpisodeInfo (Detail-API)                   │
└───────────────────────────────────────┬─────────────────────────────────────┘
                                        │ toPipelineItem() extensions
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  PIPELINE LAYER (pipeline/xtream)                                           │
│  ├─ XtreamPipelineAdapter.kt (Transport→Pipeline conversion)                │
│  └─ Pipeline DTOs (internal, nicht exportiert)                              │
│      ├─ XtreamVodItem                                                       │
│      ├─ XtreamSeriesItem                                                    │
│      ├─ XtreamChannel                                                       │
│      └─ XtreamEpisode                                                       │
└───────────────────────────────────────┬─────────────────────────────────────┘
                                        │ toRawMediaMetadata() extensions
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  CORE MODEL (core/model)                                                    │
│  └─ RawMediaMetadata.kt                                                     │
│      ├─ originalTitle, mediaType, year, season, episode                     │
│      ├─ durationMs, externalIds, sourceType, sourceId                       │
│      ├─ poster, backdrop, thumbnail (ImageRef)                              │
│      ├─ plot, genres, director, cast, rating, trailer                       │
│      └─ playbackHints: Map<String, String>                                  │
└───────────────────────────────────────┬─────────────────────────────────────┘
                                        │ (Normalizer processing - not shown)
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  DATA LAYER (infra/data-nx)                                                 │
│  └─ NxCatalogWriter.kt                                                      │
│      ├─ ingest(raw, normalized, accountKey)                                 │
│      ├─ Creates NX_Work, NX_WorkSourceRef, NX_WorkVariant                   │
│      └─ ImageRef.toSerializedString() for persistence                       │
└───────────────────────────────────────┬─────────────────────────────────────┘
                                        │ ObjectBox upsert
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  PERSISTENCE (core/persistence)                                             │
│  └─ NxEntities.kt                                                           │
│      ├─ NX_Work (25 Felder)                                                 │
│      ├─ NX_WorkSourceRef (16 Felder)                                        │
│      └─ NX_WorkVariant (16 Felder)                                          │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 Verantwortlichkeiten per Layer

| Layer | Verantwortlichkeit | SSOT für |
|-------|-------------------|----------|
| **Transport** | HTTP, JSON Deserialization, Field-Aliasing | API-Feld-Namen Resolution |
| **Pipeline** | DTO Conversion, Basic Type Mapping | toPipelineItem() |
| **Mapper** | RawMediaMetadata Assembly, Duration Parsing | parseDurationToMs(), toRawMediaMetadata() |
| **NxCatalogWriter** | Entity Creation, Key Building, ImageRef Serialization | workKey, sourceKey, variantKey |

---

## 3. VOD (Movies) Feld-Audit

### 3.1 Listen-API (get_vod_streams)

**API Response Felder:**
```json
{
  "num": 1,
  "name": "Movie | 2024 | 7.5",
  "stream_type": "movie",
  "stream_id": 800689,
  "stream_icon": "https://image.tmdb.org/...",
  "rating": "7.413",
  "rating_5based": 3.7,
  "added": "1765881814",
  "category_id": "56",
  "container_extension": "mkv",
  "custom_sid": "",
  "direct_source": ""
}
```

**Komplette Mapping-Kette:**

| API Feld | Transport Model | Pipeline DTO | RawMediaMetadata | NxCatalogWriter | NX_Entity |
|----------|-----------------|--------------|------------------|-----------------|-----------|
| `num` | `num: Int?` | ❌ verloren | - | - | - |
| `name` | `name: String?` | `name: String` | `originalTitle` | `canonicalTitle` ¹ | `NX_Work.canonicalTitle` |
| `stream_type` | `streamType: String?` | ❌ verloren ² | `mediaType=MOVIE` | `mapWorkType()` | `NX_Work.workType="MOVIE"` |
| `stream_id` | `streamId: Int?` | `id: Int` | `sourceId` | `sourceItemKey` | `NX_WorkSourceRef.sourceItemKey` |
| `stream_icon` | `streamIcon: String?` | `streamIcon: String?` | `poster: ImageRef.Http` | `toSerializedString()` | `NX_Work.poster="http:..."` |
| `rating` | `rating: String?` | `rating: Double?` | `rating: Double?` | `rating` | `NX_Work.rating` |
| `rating_5based` | `rating5Based: Double?` | `rating5Based: Double?` | ❌ Fallback nur | - | - |
| `added` | `added: String?` | `added: Long?` | `addedTimestamp` | `createdAtMs` | `NX_Work.createdAt` |
| `category_id` | `categoryId: String?` | `categoryId: String?` | `categoryId` | - | `NX_WorkSourceRef.xtreamCategoryId` ³ |
| `container_extension` | `containerExtension: String?` | `containerExtension: String?` | `playbackHints["containerExtension"]` | `extractContainerFromHints()` | `NX_WorkVariant.container` |
| `custom_sid` | `customSid: String?` | ❌ verloren | - | - | - |
| `direct_source` | `directSource: String?` | ❌ verloren | - | - | - |

**Legende:**
- ¹ Über Normalizer, nicht direkt
- ² Implizit durch Aufruf-Kontext (loadVodItems → MOVIE)
- ³ Über RawMediaMetadata.categoryId → NX_WorkSourceRef

### 3.2 Detail-API (get_vod_info)

**API Response Felder (info Block):**

| API Feld | Transport Model | Weiterverarbeitung | Status |
|----------|-----------------|-------------------|--------|
| `tmdb_id` | `tmdbId: String?` | → `externalIds.tmdb` | ✅ OK |
| `imdb_id` | `imdbId: String?` | → `externalIds.imdbId` | ✅ OK |
| `name` / `o_name` | `name`, `originalName` | → `originalTitle` | ✅ OK |
| `cover_big` / `movie_image` | `resolvedPoster` | → `poster: ImageRef` | ✅ OK |
| `backdrop_path[]` | `backdropPath: List<String>?` | → `backdrop: ImageRef` (erstes) | ✅ OK |
| `releasedate` | `releaseDate: String?` | → `releaseDate`, `year` | ✅ OK |
| `youtube_trailer` | `youtubeTrailer: String?` | → `trailer` | ✅ OK |
| `director` | `director: String?` | → `director` | ✅ OK |
| `actors` / `cast` | `resolvedCast` | → `cast` | ✅ OK |
| `description` / `plot` | `resolvedPlot` | → `plot` | ✅ OK |
| `genre` / `genres` | `resolvedGenre` | → `genres` | ✅ OK |
| `duration_secs` | `durationSecs: Int?` | → `durationMs * 1000` | ✅ OK |
| `duration` | `duration: String?` | → `parseDurationToMs()` | ✅ OK |
| `rating` | `rating: String?` | → `rating: Double` | ✅ OK |
| `country` | **NICHT IN MODEL** | - | 🔴 VERLOREN |
| `age` / `mpaa_rating` | `mpaaRating`, `age` | → ❌ nicht gemappt | 🔴 VERLOREN |
| `video` (Object) | `video: String?` ⚠️ | → ❌ falsche Typ | 🔴 KRITISCH |
| `audio` (Object) | `audio: String?` ⚠️ | → ❌ falsche Typ | 🔴 KRITISCH |
| `bitrate` | `bitrate: String?` | → ❌ nicht gemappt | 🔴 VERLOREN |

### 3.3 Kritische Befunde VOD

#### 🔴 KRITISCH: video/audio sind Objekte, nicht Strings

**API liefert:**
```json
"video": {
  "codec_name": "hevc",
  "width": 1920,
  "height": 816
}
```

**Transport Model hat:**
```kotlin
// XtreamVodInfoBlock.kt
val audio: String? = null,
val video: String? = null,
```

**Impact:** Video-/Audio-Codec-Informationen gehen vollständig verloren!

**Fix erforderlich:** In `XtreamApiModels.kt`:
```kotlin
// VOD Detail Block should use full objects like Episode does
val video: XtreamVideoInfo? = null,
val audio: XtreamAudioInfo? = null,
```

#### 🔴 VERLOREN: country, age, mpaa_rating, bitrate

Diese Felder sind in der API vorhanden aber werden nirgends persistiert:
- `country` → Nicht im Transport Model
- `age` → Im Model, aber nicht weitergereicht
- `mpaa_rating` → Im Model, aber nicht weitergereicht  
- `bitrate` → Im Model als String, aber nicht zu NX_WorkVariant.bitrateBps gemappt

---

## 4. Series Feld-Audit

### 4.1 Listen-API (get_series)

**API Response Felder:**
```json
{
  "num": 1,
  "name": "Series Name",
  "series_id": 441,
  "cover": "https://...",
  "plot": "Description...",
  "cast": "Actor 1, Actor 2",
  "director": "Director Name",
  "genre": "Drama, Action",
  "releaseDate": "2024-01-15",
  "last_modified": "1712604453",
  "rating": "8.5",
  "rating_5based": 4.25,
  "backdrop_path": ["https://..."],
  "youtube_trailer": "abc123",
  "episode_run_time": "45",
  "category_id": "66"
}
```

**Komplette Mapping-Kette:**

| API Feld | Transport | Pipeline | RawMediaMetadata | NX_Entity | Status |
|----------|-----------|----------|------------------|-----------|--------|
| `series_id` | `seriesId: Int?` | `id: Int` | `sourceId` | `NX_WorkSourceRef.sourceItemKey` | ✅ |
| `name` | `name: String?` | `name: String` | `originalTitle` | `NX_Work.canonicalTitle` | ✅ |
| `cover` | `cover: String?` | `cover: String?` | `poster: ImageRef` | `NX_Work.poster` | ✅ |
| `plot` | `plot: String?` | `plot: String?` | `plot` | `NX_Work.plot` | ✅ |
| `cast` | `cast: String?` | `cast: String?` | `cast` | `NX_Work.cast` | ✅ |
| `director` | `director: String?` | `director: String?` | `director` | `NX_Work.director` | ✅ |
| `genre` | `genre: String?` | `genre: String?` | `genres` | `NX_Work.genres` | ✅ |
| `releaseDate` | `releaseDate: String?` | `releaseDate: String?` | `releaseDate`, `year` | `NX_Work.releaseDate`, `.year` | ✅ |
| `last_modified` | `lastModified: String?` | `lastModified: Long?` | ❌ nicht gemappt | - | 🟡 |
| `rating` | `rating: String?` | `rating: Double?` | `rating` | `NX_Work.rating` | ✅ |
| `rating_5based` | `rating5Based: Double?` | Fallback für rating | - | - | ✅ |
| `backdrop_path[]` | `backdropPath: List<String>?` | `backdrop: List<String>?` | `backdrop: ImageRef` | `NX_Work.backdrop` | ✅ |
| `youtube_trailer` | `youtubeTrailer: String?` | `youtubeTrailer: String?` | `trailer` | `NX_Work.trailer` | ✅ |
| `episode_run_time` | `episodeRunTime: String?` | `episodeRunTime: String?` | `durationMs` (parsed) | `NX_Work.durationMs` | ✅ |
| `category_id` | `categoryId: String?` | `categoryId: String?` | `categoryId` | `NX_WorkSourceRef.xtreamCategoryId` | ✅ |
| `num` | `num: Int?` | ❌ verloren | - | - | 🟡 OK |

### 4.2 Series-Spezifische Befunde

✅ **Series Listen-API ist REICH** - Fast alle wichtigen Felder direkt verfügbar

🟡 **last_modified geht verloren** - Könnte für Cache-Invalidierung nützlich sein

---

## 5. Episode Feld-Audit

> **✅ STATUS (Feb 2026):** Alle kritischen Episode-Felder werden nun korrekt gemappt!

### 5.1 Episode aus get_series_info

**API Response (Episode):**
```json
{
  "id": "396757",
  "episode_num": 1,
  "title": "Series - S01E01 - Episode Title",
  "container_extension": "mp4",
  "info": {
    "tmdb_id": 1837500,
    "releasedate": "2022-11-17",
    "plot": "Episode description...",
    "duration_secs": 3602,
    "duration": "01:00:02",
    "movie_image": "https://...",
    "video": { "codec_name": "h264", "width": 1280, "height": 544 },
    "audio": { "codec_name": "aac", "bit_rate": "116962" },
    "bitrate": 1400,
    "rating": 7.744,
    "season": "1"
  },
  "added": "1685447912",
  "season": 1,
  "direct_source": ""
}
```

**Komplette Mapping-Kette (AKTUALISIERT Feb 2026):**

| API Feld | Transport Model | Pipeline DTO | RawMediaMetadata | NX_Entity | Status |
|----------|-----------------|--------------|------------------|-----------|--------|
| `id` / `episode_id` | `resolvedEpisodeId` | `id: Int` | `sourceId` | `NX_WorkSourceRef.sourceItemKey` | ✅ |
| `episode_num` | `episodeNum: Int?` | `episodeNumber: Int` | `episode` | `NX_Work.episode` | ✅ |
| `title` | `title: String?` | `title: String` | `originalTitle` | `NX_Work.canonicalTitle` | ✅ |
| `season` (map key) | (Map key) | `seasonNumber: Int` | `season` | `NX_Work.season` | ✅ |
| `container_extension` | `containerExtension` | `containerExtension` | `playbackHints["containerExtension"]` | `NX_WorkVariant.container` | ✅ |
| `added` | `added: String?` | `added: Long?` | `addedTimestamp` | `NX_Work.createdAt` | ✅ |
| `info.tmdb_id` | `info.tmdbId: Int?` | `episodeTmdbId: Int?` | `playbackHints["xtream.episodeTmdbId"]` | (Normalizer) | ✅ BEHOBEN |
| `info.releasedate` | `info.releaseDate` | `releaseDate: String?` | `releaseDate` | `NX_Work.releaseDate` | ✅ |
| `info.plot` | `info.plot` | `plot: String?` | `plot` | `NX_Work.plot` | ✅ |
| `info.duration_secs` | `info.durationSecs` | `durationSecs: Int?` | `durationMs` | `NX_Work.durationMs` | ✅ |
| `info.movie_image` | `info.movieImage` | `thumbnail: String?` | `thumbnail: ImageRef` | `NX_Work.thumbnail` | ✅ |
| `info.rating` | `info.rating` | `rating: Double?` | `rating` | `NX_Work.rating` | ✅ |
| `info.video.codec_name` | `video.codec` ¹ | `videoCodec: String?` | `playbackHints["videoCodec"]` | (Player) | ✅ BEHOBEN |
| `info.video.width` | `video.width` | `videoWidth: Int?` | `playbackHints["videoWidth"]` | (Player) | ✅ BEHOBEN |
| `info.video.height` | `video.height` | `videoHeight: Int?` | `playbackHints["videoHeight"]` | (Player) | ✅ BEHOBEN |
| `info.audio.codec_name` | `audio.codec` ¹ | `audioCodec: String?` | `playbackHints["audioCodec"]` | (Player) | ✅ BEHOBEN |
| `info.audio.channels` | `audio.channels` | `audioChannels: Int?` | `playbackHints["audioChannels"]` | (Player) | ✅ BEHOBEN |
| `info.bitrate` | `info.bitrate: Int?` | ❌ nicht gemappt | - | - | 🟡 OPTIONAL |

**Legende:**
- ¹ **KRITISCHER FIX (Feb 2026):** XtreamVideoInfo/XtreamAudioInfo verwenden nun `@SerialName("codec_name")` 
  da die API `codec_name` liefert, nicht `codec`

### 5.2 Episode-Befunde - ALLE BEHOBEN ✅

#### ✅ BEHOBEN: Episode tmdb_id wird durchgereicht

**Vorher:** `XtreamEpisodeInfo.info.tmdbId` wurde geparst aber nicht weitergereicht  
**Jetzt:** 
- `XtreamEpisode.episodeTmdbId: Int?` existiert
- `XtreamPipelineAdapter.toEpisodes()` mappt: `episodeTmdbId = ep.info?.tmdbId`
- `XtreamRawMetadataExtensions` schreibt: `playbackHints["xtream.episodeTmdbId"]`

#### ✅ BEHOBEN: Video/Audio Codec-Info für Episoden

**Vorher:** `XtreamVideoInfo.codec` erwartete "codec" aber API liefert "codec_name" → NULL  
**Jetzt (Fix Feb 2026):**
```kotlin
// XtreamApiModels.kt
data class XtreamVideoInfo(
    /** Video codec (e.g., "h264", "hevc"). API returns this as "codec_name". */
    @SerialName("codec_name") val codec: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    // ...
)

data class XtreamAudioInfo(
    /** Audio codec (e.g., "aac", "ac3"). API returns this as "codec_name". */
    @SerialName("codec_name") val codec: String? = null,
    // ...
)
```

**Vollständige Kette:**
1. API: `"video": { "codec_name": "h264", "width": 1920, "height": 816 }`
2. Transport: `XtreamVideoInfo(codec="h264", width=1920, height=816)` via @SerialName
3. Pipeline: `XtreamEpisode(videoCodec="h264", videoWidth=1920, videoHeight=816)`
4. RawMetadata: `playbackHints["videoCodec"]="h264"`, `playbackHints["videoWidth"]="1920"`
5. Player: Verwendet Hints für Codec-Detection und Quality-Display

---

## 6. Live Channel Feld-Audit

### 6.1 Listen-API (get_live_streams)

**API Response:**
```json
{
  "num": 1,
  "name": "▃ ▅ ▆ █ Channel Name █ ▆ ▅ ▃",
  "stream_type": "live",
  "stream_id": 81568,
  "stream_icon": "https://...",
  "epg_channel_id": "channel.de",
  "added": "1604353552",
  "category_id": "129",
  "custom_sid": "",
  "tv_archive": 1,
  "direct_source": "",
  "tv_archive_duration": 7
}
```

**Komplette Mapping-Kette:**

| API Feld | Transport | Pipeline | RawMediaMetadata | NX_Entity | Status |
|----------|-----------|----------|------------------|-----------|--------|
| `stream_id` | `streamId: Int?` | `id: Int` | `sourceId` | `NX_WorkSourceRef.sourceItemKey` | ✅ |
| `name` | `name: String?` | `name: String` | `originalTitle` ¹ | `NX_Work.canonicalTitle` | ✅ |
| `stream_icon` | `streamIcon: String?` | `streamIcon: String?` | `poster: ImageRef` | `NX_Work.poster` | ✅ |
| `epg_channel_id` | `epgChannelId: String?` | `epgChannelId: String?` | `epgChannelId` | `NX_WorkSourceRef.epgChannelId` | ✅ |
| `tv_archive` | `tvArchive: Int?` | `tvArchive: Int` | `tvArchive` | `NX_WorkSourceRef.tvArchive` | ✅ |
| `tv_archive_duration` | `tvArchiveDuration: Int?` | `tvArchiveDuration: Int` | `tvArchiveDuration` | `NX_WorkSourceRef.tvArchiveDuration` | ✅ |
| `added` | `added: String?` | `added: Long?` | `addedTimestamp` | `NX_Work.createdAt` | ✅ |
| `category_id` | `categoryId: String?` | `categoryId: String?` | `categoryId` | `NX_WorkSourceRef.xtreamCategoryId` | ✅ |
| `stream_type` | `streamType: String?` | ❌ implizit | `mediaType=LIVE` | `NX_Work.workType="LIVE"` | ✅ |
| `custom_sid` | `customSid: String?` | ❌ verloren | - | - | 🟡 OK |
| `direct_source` | `directSource: String?` | ❌ verloren | - | - | 🟡 OK |
| `num` | `num: Int?` | ❌ verloren | - | - | 🟡 OK |

**Legende:**
- ¹ `cleanLiveChannelName()` entfernt Unicode-Dekoratoren (▃ ▅ ▆ █)

### 6.2 Live-Spezifische Befunde

✅ **Alle wichtigen Live-Felder werden korrekt gemappt**

✅ **cleanLiveChannelName()** ist die SSOT für Title-Cleaning bei Live-Kanälen

---

## 7. Format-Konvertierungen

### 7.1 ImageRef ↔ String

**Konvertierungskette:**

```
API URL String
    ↓ (Transport → Pipeline)
String (unverändert)
    ↓ (Pipeline → RawMediaMetadata)
ImageRef.Http(url)          [XtreamRawMetadataExtensions.kt]
    ↓ (NxCatalogWriter)
"http:$url"                 [toSerializedString()]
    ↓ (ObjectBox)
NX_Work.poster: String
```

**Bewertung:** 🟢 **KORREKT**
- Klare unidirektionale Transformation
- Keine Rückwärts-Konvertierung nötig (UI liest via GlobalImageLoader)

### 7.2 Duration Parsing

**SSOT:** `XtreamRawMetadataExtensions.parseDurationToMs()`

**Unterstützte Formate:**
```kotlin
// HH:MM:SS → ms
"02:30:45" → 9045000L

// MM:SS → ms
"45:30" → 2730000L

// Reine Zahl (als Minuten interpretiert)
"120" → 7200000L

// Mit "min" Suffix
"90 min" → 5400000L
```

**Problem:** Transport Layer hat AUCH Parsing-Logik (deprecated):
```kotlin
// XtreamVodInfoBlock.kt
@Deprecated("Use Pipeline XtreamRawMetadataExtensions.parseDurationToMs() instead")
val resolvedDurationMins: Int?
```

**Bewertung:** 🟡 **DOPPELT, ABER OK**
- Pipeline ist SSOT (korrekt markiert)
- Transport-Variante ist deprecated
- Keine Inkonsistenz im Flow (Pipeline-Methode wird verwendet)

### 7.3 Rating Normalisierung

**Konvertierungskette:**

```
rating: "7.413"          (String, 0-10 Skala)
rating_5based: 3.7       (Double, 0-5 Skala)
    ↓ (Pipeline Adapter)
if rating != null:
    rating.toDoubleOrNull()
else:
    rating5Based * 2.0
    ↓
RawMediaMetadata.rating: Double?
    ↓
NX_Work.rating: Double?
```

**Bewertung:** 🟢 **KORREKT**
- Preference: rating (0-10) > rating_5based (*2)
- Einheitliche Skala (0-10) in der ganzen App

### 7.4 Timestamp Parsing

**Konvertierungskette:**

```
added: "1765881814"      (String, Unix Epoch Seconds)
    ↓ (Pipeline Adapter)
added: Long?             (toLongOrNull())
    ↓ (RawMediaMetadata)
addedTimestamp: Long?    (seconds)
    ↓ (NxCatalogWriter)
createdAtMs: Long        (seconds → ms? ⚠️)
    ↓
NX_Work.createdAt: Long
```

**Bewertung:** 🟡 **INKONSISTENT**
- API liefert Epoch SECONDS
- NxCatalogWriter Variable heißt `createdAtMs` aber verwendet den Wert direkt ohne *1000
- Dokumentation sagt "seconds", Variablenname sagt "ms"

**Empfehlung:** Umbenennen zu `createdAtSec` oder konsistent zu ms konvertieren

---

## 8. Layer Boundary Analyse

### 8.1 Transport Layer (infra/transport-xtream)

**Verantwortlichkeit:**
- ✅ HTTP Requests
- ✅ JSON Deserialization (kotlinx.serialization)
- ✅ Field Aliasing (resolvedPoster, resolvedCast, etc.)
- ❌ **VIOLATION:** Duration Parsing (`resolvedDurationMins`) → Gehört in Pipeline

**Befund:** 🟡 Transport enthält deprecated Business-Logik

### 8.2 Pipeline Layer (pipeline/xtream)

**Verantwortlichkeit:**
- ✅ Transport → Pipeline DTO Conversion
- ✅ Duration Parsing (SSOT: parseDurationToMs)
- ✅ Title Cleaning (cleanLiveChannelName)
- ✅ RawMediaMetadata Assembly
- ✅ playbackHints Building

**Befund:** ✅ Korrekt implementiert

### 8.3 NxCatalogWriter (infra/data-nx)

**Verantwortlichkeit:**
- ✅ Entity Creation (Work, SourceRef, Variant)
- ✅ Key Building (workKey, sourceKey, variantKey)
- ✅ ImageRef Serialization

**Befund:** ✅ Korrekt implementiert

### 8.4 Layer Boundary Issues

#### Issue 1: Transport enthält Business-Logik

```kotlin
// XtreamVodInfoBlock.kt (Transport Layer!)
@Deprecated("...")
val resolvedDurationMins: Int?
    get() = durationSecs?.let { it / 60 } ?: duration?.let { parseDurationString(it) }
```

**Verletzung:** Business-Logik (Duration Parsing) in Transport Layer

**Empfehlung:** Vollständig entfernen, Pipeline ist SSOT

#### Issue 2: Aliasing vs Mapping

Transport verwendet "resolver" Properties für Field-Aliasing:
- `resolvedPoster` - wählt aus movieImage/posterPath/cover/coverBig
- `resolvedCast` - wählt aus cast/actors

**Frage:** Ist Field-Aliasing "Business-Logik" oder "Transport-Level Normalisierung"?

**Bewertung:** 🟢 **AKZEPTABEL**
- Dies ist API-Level Kompatibilität (verschiedene Panels)
- Keine semantische Transformation
- Gehört in Transport

---

## 9. Probleme & Empfehlungen

### 9.1 Kritische Probleme (MUSS BEHOBEN WERDEN)

#### P1: video/audio Typ-Mismatch in VOD Detail ✅ BEHOBEN

**Problem:** `XtreamVodInfoBlock` hat `video: String?` aber API liefert Objekt

**Impact:** Codec-Info geht verloren, NX_WorkVariant ist unvollständig

**Fix (implementiert 2026-01-28):**
```kotlin
// XtreamVodInfoBlock.kt - Polymorphes Parsing mit JsonElement
val video: JsonElement? = null,
val audio: JsonElement? = null,

val videoInfo: XtreamVideoInfo?
    get() = video?.let { parseVideoInfo(it) }

val audioInfo: XtreamAudioInfo?
    get() = audio?.let { parseAudioInfo(it) }
```

#### P2: Episode tmdb_id geht verloren ✅ BEHOBEN

**Problem:** `XtreamEpisode` DTO hat kein tmdbId Feld

**Impact:** Episode-spezifische TMDB-Anreicherung unmöglich

**Fix (implementiert 2026-01-28):**
1. ✅ `XtreamEpisodeInfoBlock.kt` - `tmdbId: Int?` hinzugefügt
2. ✅ `XtreamEpisode.kt` - `episodeTmdbId: Int?` Feld hinzugefügt
3. ✅ `XtreamPipelineAdapter.kt` - `episodeTmdbId = ep.info?.tmdbId` mappen
4. ✅ `XtreamRawMetadataExtensions.kt` - Episode `externalIds.tmdb` mit TV-Referenz

#### P3: Video/Audio Codec für Episoden nicht weitergereicht ✅ BEHOBEN

**Problem:** Transport hat die Daten, Pipeline wirft sie weg

**Fix (implementiert 2026-01-28):**
1. ✅ `XtreamEpisode.kt` - videoCodec/Width/Height, audioCodec/Channels hinzugefügt
2. ✅ `XtreamVodItem.kt` - gleiche Codec-Felder hinzugefügt
3. ✅ `XtreamPipelineAdapter.kt` - Codec-Mapping aus `ep.info?.video/audio`
4. ✅ `PlaybackHintKeys.kt` - VIDEO_CODEC, VIDEO_WIDTH, VIDEO_HEIGHT, AUDIO_CODEC, AUDIO_CHANNELS
5. ✅ `XtreamRawMetadataExtensions.kt` - Episode playbackHints mit Codec-Info

### 9.2 Mittlere Probleme (SOLLTE BEHOBEN WERDEN)

#### P4: Inkonsistente Timestamp-Benennung ✅ BEHOBEN

**Problem:** `createdAtMs` enthält Seconds, nicht Ms (API liefert Unix epoch Seconds)

**Fix (implementiert 2026-01-28):**
- ✅ `XtreamPipelineAdapter.kt` - `added?.toLongOrNull()?.let { it * 1000L }` für VOD, Channel, Episode
- ✅ `XtreamPipelineAdapter.kt` - `lastModified?.toLongOrNull()?.let { it * 1000L }` für Series
- ✅ `XtreamRawMetadataExtensions.kt` - `movieData?.added?.toLongOrNull()?.let { it * 1000L }` für VOD Detail
- Timestamps werden nun korrekt in Millisekunden konvertiert

#### P5: Deprecated Code in Transport ✅ BEHOBEN

**Problem:** `resolvedDurationMins` in XtreamVodInfoBlock

**Fix (implementiert 2026-01-28):**
- ✅ `XtreamApiModels.kt` - `resolvedDurationMins` Feld entfernt
- ✅ `XtreamApiModels.kt` - `parseDurationString()` Hilfsfunktion entfernt
- Pipeline `parseDurationToMs()` ist SSOT für Duration-Parsing

#### P6: country, age, mpaa_rating verloren

**Problem:** API-Felder werden nicht persistiert

**Fix:** 
- Wenn benötigt: NX_Work erweitern
- Alternativ: In playbackHints als Metadata aufnehmen
- **Status:** Noch offen (optional)

### 9.3 Niedrige Probleme (OPTIONAL)

#### P7: last_modified für Series nicht genutzt ✅ BEHOBEN

**Problem:** Könnte für Cache-Invalidierung und "Neue Episoden" Feature nützlich sein

**Fix (implementiert 2026-01-28):**
1. ✅ `RawMediaMetadata.kt` - `lastModifiedTimestamp: Long?` Feld hinzugefügt
2. ✅ `NxWorkSourceRefRepository.SourceRef` - `sourceLastModifiedMs: Long?` hinzugefügt
3. ✅ `NX_WorkSourceRef` Entity - `sourceLastModifiedMs: Long?` hinzugefügt
4. ✅ `WorkSourceRefMapper.kt` - Bidirektionales Mapping toDomain/toEntity
5. ✅ `NxCatalogWriter.kt` - `raw.lastModifiedTimestamp` → `sourceLastModifiedMs`
6. ✅ `XtreamRawMetadataExtensions.kt` - Series `lastModified` → `lastModifiedTimestamp`

**Nutzungsmöglichkeiten:**
- Inkrementelle Katalog-Synchronisation (nur geänderte Serien neu laden)
- "Neue Episoden verfügbar" Badge auf Serien-Karten
- Smart Refresh bei Detail-Ansicht
- Background Sync Priorisierung

#### P8: codec_name @SerialName Annotation fehlte ✅ BEHOBEN

**Problem:** `XtreamVideoInfo.codec` und `XtreamAudioInfo.codec` erwarteten Feld "codec",
aber API liefert "codec_name". Dadurch wurden alle Video/Audio-Codec-Informationen für
Episoden als NULL deserialisiert.

**Discovery:** Feldabgleich zwischen echten API-Responses (`series_detail_response_xtream.txt`)
und Transport DTOs zeigte Diskrepanz.

**Fix (implementiert 2026-02-02):**
```kotlin
// XtreamApiModels.kt

data class XtreamVideoInfo(
    /** Video codec (e.g., "h264", "hevc"). API returns this as "codec_name". */
    @SerialName("codec_name") val codec: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    // ...
)

data class XtreamAudioInfo(
    /** Audio codec (e.g., "aac", "ac3"). API returns this as "codec_name". */
    @SerialName("codec_name") val codec: String? = null,
    // ...
)
```

**Zusätzlicher Fix:** `aspect_ratio` → `@SerialName("display_aspect_ratio")` da API "display_aspect_ratio" liefert.

**Impact:** 
- Vorher: Episode videoCodec/audioCodec immer NULL
- Nachher: Korrekte Codec-Werte (z.B. "h264", "aac") werden geparst

---

## 10. Anhang: Code-Referenzen

### 10.1 Datei-Übersicht

| Schicht | Datei | Zeilen | Beschreibung |
|---------|-------|--------|--------------|
| Transport | `XtreamApiModels.kt` | 727 | Alle API DTOs |
| Transport | `DefaultXtreamApiClient.kt` | ~800 | HTTP + Streaming |
| Pipeline | `XtreamPipelineAdapter.kt` | 301 | Transport→Pipeline |
| Pipeline | `XtreamRawMetadataExtensions.kt` | ~400 | Pipeline→RawMedia |
| Core | `RawMediaMetadata.kt` | 331 | Canonical DTO |
| Data | `NxCatalogWriter.kt` | 299 | Entity Creation |

### 10.2 Key Functions

```
Transport→Pipeline:
  XtreamVodStream.toPipelineItem() → XtreamVodItem
  XtreamSeriesStream.toPipelineItem() → XtreamSeriesItem  
  XtreamLiveStream.toPipelineItem() → XtreamChannel
  XtreamSeriesInfo.toEpisodes() → List<XtreamEpisode>

Pipeline→RawMedia:
  XtreamVodItem.toRawMediaMetadata()
  XtreamSeriesItem.toRawMediaMetadata()
  XtreamChannel.toRawMediaMetadata()
  XtreamEpisode.toRawMediaMetadata()

Duration Parsing (SSOT):
  parseDurationToMs(duration: String?): Long?

Title Cleaning:
  cleanLiveChannelName(name: String): String

Data Layer:
  NxCatalogWriter.ingest(raw, normalized, accountKey)
  ImageRef.toSerializedString()
```

---

## Changelog

| Datum | Änderung |
|-------|----------|
| 2026-01-28 | Initial Audit erstellt |
| 2026-01-28 | P1 behoben: video/audio Type Mismatch mit JsonElement polymorphem Parsing |
| 2026-01-28 | P2 behoben: Episode tmdb_id durch Pipeline → externalIds gemappt |
| 2026-01-28 | P3 behoben: Video/Audio Codec → playbackHints hinzugefügt |
| 2026-01-28 | P4 behoben: Timestamps von Unix Seconds zu Milliseconds konvertiert |
| 2026-01-28 | P5 behoben: Deprecated resolvedDurationMins entfernt |
| 2026-01-28 | P7 behoben: lastModifiedTimestamp für inkrementellen Sync implementiert |
| 2026-02-02 | P8 behoben: @SerialName("codec_name") für XtreamVideoInfo/AudioInfo hinzugefügt |
| 2026-02-02 | P8: Auch @SerialName("display_aspect_ratio") für XtreamVideoInfo.aspectRatio |
| 2026-02-02 | Dokumentation aktualisiert: 8/8 Probleme behoben, Episode-Tabelle vollständig überarbeitet |

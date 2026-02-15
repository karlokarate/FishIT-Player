# Xtream VOD Persistence Analysis

> **Stand:** 2026-01-29  
> **Scope:** `get_vod_streams` API → NX_Work Entity Chain  
> **Status:** ✅ FIXED (Jan 29, 2026)

---

## 0. Executive Summary

### Bugs Identified & Fixed

| # | Bug | Status | Fix Location |
|---|-----|--------|--------------|
| 1 | `xtreamStreamId = null` | ⚪ **NOT A BUG** | Field doesn't exist in interface - extract from `sourceItemKey` |
| 2 | `xtreamCategoryId = null` | ⚪ **NOT A BUG** | Field doesn't exist in interface - now in `playbackHints` |
| 3 | `sourceLastModifiedMs = null` | ✅ **FIXED** | `XtreamRawMetadataExtensions.kt` - added `lastModifiedTimestamp = added` |
| 4 | `containerFormat = null` | ✅ **FIXED** | `NxCatalogWriter.kt` - added `xtream.containerExtension` key lookup |

### Architecture Insight

The `xtreamStreamId` and `xtreamCategoryId` fields do NOT exist in `NxWorkSourceRefRepository.SourceRef`.
This is BY DESIGN - Xtream-specific fields are stored in:
- `sourceItemKey` = "xtream:vod:801307" (contains stream ID)
- `playbackHints` = `{"xtream.categoryId": "56", ...}` (contains category ID)

---

## 1. API Response: Was `get_vod_streams` liefert

```json
{
  "num": 1,
  "name": "Ein Sommer in Sommerby | 2025 | 7.4",
  "stream_type": "movie",
  "stream_id": 801307,
  "stream_icon": "https://image.tmdb.org/t/p/w600_and_h900_bestv2/aIsB4AtZwU1VPB0ihmbMIPahXQd.jpg",
  "rating": "7.4",
  "rating_5based": 3.7,
  "added": "1765881814",
  "category_id": "56",
  "container_extension": "mkv",
  "custom_sid": "",
  "direct_source": ""
}
```

### Feld-Klassifizierung

| Feld | Typ | Relevanz | Beschreibung |
|------|-----|----------|--------------|
| `num` | Int | ❌ Ignorieren | Server-interne Sortierungsnummer, ändert sich |
| `name` | String | ✅ Essentiell | Enthält: `Title \| Year \| Rating` (Normalizer muss parsen) |
| `stream_type` | String | ✅ Essentiell | `"movie"` → `MediaType.MOVIE` |
| `stream_id` | Int | ✅ Essentiell | Für Playback-URL: `/{stream_id}.{ext}` |
| `stream_icon` | String | ✅ Essentiell | Poster-URL (meist TMDB) |
| `rating` | String | ✅ Essentiell | Rating 0-10 Skala |
| `rating_5based` | Float | ⚠️ Fallback | Nur wenn `rating` fehlt |
| `added` | String | ✅ Essentiell | Unix Timestamp: Wann Provider Film hinzugefügt hat |
| `category_id` | String | ✅ Essentiell | Xtream-Kategorie für Gruppierung |
| `container_extension` | String | ✅ Essentiell | `mkv`, `mp4` etc. für Playback |
| `custom_sid` | String | ❌ Ignorieren | Immer leer |
| `direct_source` | String | ⚠️ Optional | Direkter Stream-Link (meist leer) |

---

## 2. IST-Zustand: Was aktuell persistiert wird

### 2.1 NX_Work Entity

| Feld | API-Quelle | Aktueller Wert | Status |
|------|------------|----------------|--------|
| `workKey` | Generated | `"movie:ein-sommer-in-sommerby:2025"` | ✅ OK |
| `workType` | `stream_type` | `"MOVIE"` | ✅ OK |
| `canonicalTitle` | `name` (parsed) | `"Ein Sommer in Sommerby"` | ✅ OK |
| `year` | `name` (parsed) | `"2025"` | ✅ OK |
| `rating` | `name` (parsed) / `rating` | `"7.4"` | ✅ OK |
| `poster` | `stream_icon` | URL korrekt | ✅ OK |
| `backdrop` | - | `null` | ⚪ N/A (API liefert nicht) |
| `plot` | - | `null` | ⚪ N/A (API liefert nicht) |
| `cast` | - | `null` | ⚪ N/A (API liefert nicht) |
| `director` | - | `null` | ⚪ N/A (API liefert nicht) |
| `genres` | - | `null` | ⚪ N/A (API liefert nicht) |
| `durationMs` | - | `null` | ⚪ N/A (API liefert nicht) |
| `releaseDate` | - | `null` | ⚪ N/A (API liefert nicht) |
| `tmdbId` | - | `null` | ⚪ N/A (API liefert nicht) |
| `imdbId` | - | `null` | ⚪ N/A (API liefert nicht) |
| `trailer` | - | `null` | ⚪ N/A (API liefert nicht) |

### 2.2 NX_WorkSourceRef Entity

| Feld | API-Quelle | Aktueller Wert | Status |
|------|------------|----------------|--------|
| `sourceType` | - | `"xtream"` | ✅ OK |
| `accountKey` | - | `"xtream:xtream"` | ✅ OK |
| `sourceId` | `stream_id` | `"xtream:vod:801307"` | ✅ OK |
| `sourceKey` | Generated | `"src:xtream:xtream:xtream:vod:xtream:vod:801307"` | ⚠️ Redundant |
| `rawTitle` | `name` | `"Ein Sommer in Sommerby \| 2025 \| 7.4"` | ✅ OK |
| `discoveredAt` | System time | `1769634458337` | ✅ OK |
| `lastSeenAt` | System time | `1769662845770` | ✅ OK |
| **`xtreamStreamId`** | `stream_id` | **`null`** | 🔴 **BUG** |
| **`xtreamCategoryId`** | `category_id` | **`null`** | 🔴 **BUG** |
| **`sourceLastModifiedMs`** | `added` | **`null`** | 🔴 **BUG** |
| `epgChannelId` | - | `null` | ⚪ N/A (nur Live) |
| `tvArchive` | - | `"0"` | ⚪ N/A (nur Live) |
| `tvArchiveDuration` | - | `"0"` | ⚪ N/A (nur Live) |
| `telegramChatId` | - | `null` | ⚪ N/A (nur Telegram) |
| `telegramMessageId` | - | `null` | ⚪ N/A (nur Telegram) |
| `fileName` | - | `null` | ⚪ N/A (nur Telegram) |
| `fileSizeBytes` | - | `null` | ⚪ N/A (nur Telegram) |
| `mimeType` | - | `null` | ⚪ Optional |

### 2.3 NX_WorkVariant Entity

| Feld | API-Quelle | Aktueller Wert | Status |
|------|------------|----------------|--------|
| `variantKey` | Generated | `"v:src:...:default"` | ✅ OK |
| `sourceKey` | - | Wie SourceRef | ⚠️ Redundant |
| `playbackMethod` | - | `"DIRECT"` | ✅ OK |
| `playbackUrl` | - | `null` | ✅ OK (wird on-demand gebaut) |
| `playbackHintsJson` | Multiple | siehe unten | ✅ OK |
| `qualityTag` | - | `"source"` | ✅ OK |
| `languageTag` | - | `"original"` | ✅ OK |
| `containerFormat` | `container_extension` | `null` | ⚠️ Nicht gesetzt |
| `videoCodec` | - | `null` | ⚪ N/A |
| `audioCodec` | - | `null` | ⚪ N/A |
| `width` | - | `null` | ⚪ N/A |
| `height` | - | `null` | ⚪ N/A |
| `bitrateBps` | - | `null` | ⚪ N/A |

**playbackHintsJson (aktuell):**
```json
{
  "xtream.contentType": "vod",
  "xtream.vodId": "801307",
  "xtream.containerExtension": "mkv"
}
```

---

## 3. Fehler-Zusammenfassung

### ✅ FIXED: Bugs wurden behoben (Jan 29, 2026)

| # | Bug | Root Cause | Fix |
|---|-----|------------|-----|
| 3 | `sourceLastModifiedMs = null` | VOD verwendet `addedTimestamp`, aber `lastModifiedTimestamp` war nicht gesetzt | `XtreamRawMetadataExtensions.kt`: `lastModifiedTimestamp = added` |
| 4 | `containerFormat = null` | Key-Mismatch: Pipeline nutzt `xtream.containerExtension`, Writer prüfte nur `containerExtension` | `NxCatalogWriter.kt`: Key `xtream.containerExtension` hinzugefügt |

### ⚪ NOT A BUG: Felder existieren nicht im Interface

| # | Feld | Analyse |
|---|------|---------|
| 1 | `xtreamStreamId` | Feld existiert nicht in `NxWorkSourceRefRepository.SourceRef` - BY DESIGN. Stream ID extrahierbar aus `sourceItemKey` = "xtream:vod:801307" |
| 2 | `xtreamCategoryId` | Feld existiert nicht in `NxWorkSourceRefRepository.SourceRef` - BY DESIGN. Jetzt in `playbackHints["xtream.categoryId"]` |

### ⚠️ WARNINGS: Suboptimale Persistierung (nicht kritisch)

| # | Feld | Problem |
|---|------|---------|
| 5 | `sourceKey` | Redundant: `"src:xtream:xtream:xtream:vod:xtream:vod:801307"` |
| 6 | `mimeType` | Könnte aus `container_extension` abgeleitet werden |

### ⚪ N/A: API liefert nicht (kein Bug)

Diese Felder sind `null` weil die VOD List API sie nicht liefert:
- `plot`, `cast`, `director`, `genres`, `durationMs`, `releaseDate`
- `tmdbId`, `imdbId`, `trailer`, `backdrop`

> **Hinweis:** Diese Felder könnten über `get_vod_info/{stream_id}` nachgeladen werden (separater API-Call pro Film).

---

## 4. Zielbild: Perfekte VOD-Persistierung

### 4.1 NX_Work (nach erstem Sync)

```kotlin
NX_Work(
    id = 955,
    workKey = "movie:ein-sommer-in-sommerby:2025",
    workType = WorkType.MOVIE,
    
    // Aus name geparst (Normalizer)
    canonicalTitle = "Ein Sommer in Sommerby",
    canonicalTitleLower = "ein sommer in sommerby",
    year = 2025,
    
    // Direkt aus API
    rating = 7.4f,
    poster = ImageRef.Http("https://image.tmdb.org/t/p/w600_and_h900_bestv2/..."),
    
    // Nicht verfügbar in VOD List API (null ist korrekt)
    plot = null,
    cast = null,
    director = null,
    genres = null,
    durationMs = null,
    releaseDate = null,
    backdrop = null,
    trailer = null,
    tmdbId = null,
    imdbId = null,
    
    // Metadata
    createdAt = Instant.now(),
    updatedAt = Instant.now(),
    isAdult = false,
    needsReview = false,
)
```

### 4.2 NX_WorkSourceRef (SOLL-Zustand)

```kotlin
NX_WorkSourceRef(
    id = 1022,
    work = ToOne(workId = 955),
    
    // Source Identification
    sourceType = SourceType.XTREAM,
    accountKey = "xtream:konigtv",  // Provider-spezifisch
    sourceId = "xtream:vod:801307",
    sourceKey = "src:xtream:konigtv:vod:801307",  // VEREINFACHT!
    
    // Xtream-spezifische IDs (NEU - aktuell null!)
    xtreamStreamId = 801307,        // ← FIX NEEDED
    xtreamCategoryId = "56",        // ← FIX NEEDED
    
    // Timestamps
    discoveredAt = 1769634458337,   // Wann WIR es gefunden haben
    lastSeenAt = 1769662845770,     // Letzter Sync
    sourceLastModifiedMs = 1765881814000,  // ← FIX: API "added" × 1000
    
    // Raw Data
    rawTitle = "Ein Sommer in Sommerby | 2025 | 7.4",
    
    // N/A für VOD
    epgChannelId = null,
    tvArchive = 0,
    tvArchiveDuration = 0,
    telegramChatId = null,
    telegramMessageId = null,
    fileName = null,
    fileSizeBytes = null,
    mimeType = "video/x-matroska",  // Optional: aus container_extension
)
```

### 4.3 NX_WorkVariant (SOLL-Zustand)

```kotlin
NX_WorkVariant(
    id = 746,
    work = ToOne(workId = 955),
    
    // Keys
    variantKey = "v:xtream:konigtv:vod:801307:source",
    sourceKey = "src:xtream:konigtv:vod:801307",
    
    // Playback
    playbackMethod = PlaybackMethod.DIRECT,
    playbackUrl = null,  // Wird on-demand gebaut
    playbackHintsJson = """
    {
        "xtream.contentType": "vod",
        "xtream.streamId": 801307,
        "xtream.containerExtension": "mkv",
        "xtream.categoryId": "56"
    }
    """,
    
    // Format Info
    containerFormat = "mkv",        // ← FIX: Direkt ins Feld
    qualityTag = "source",
    languageTag = "original",
    
    // Unbekannt ohne Probe
    videoCodec = null,
    audioCodec = null,
    width = null,
    height = null,
    bitrateBps = null,
    
    createdAt = Instant.now(),
)
```

---

## 5. Erforderliche Code-Änderungen

### 5.1 Pipeline Layer: `XtreamVodMapper.kt`

```kotlin
// AKTUELL: stream_id wird nur in RawMediaMetadata.sourceHints gesteckt
// SOLL: Auch als dediziertes Feld in SourceOrigin

data class SourceOrigin(
    // ... existing fields ...
    val xtreamStreamId: Long? = null,      // NEU
    val xtreamCategoryId: String? = null,  // NEU
    val addedTimestamp: Long? = null,      // NEU (API "added")
)
```

### 5.2 Normalizer Layer: `RawMediaMetadata`

Prüfen ob `sourceHints` alle benötigten Felder enthält:

```kotlin
data class RawMediaMetadata(
    // ... existing ...
    val sourceHints: Map<String, Any?> = emptyMap()
)

// sourceHints sollte enthalten:
// "xtream.streamId" -> 801307
// "xtream.categoryId" -> "56"
// "xtream.containerExtension" -> "mkv"
// "xtream.addedTimestamp" -> 1765881814
```

### 5.3 Data Layer: `NxCatalogWriter.kt`

```kotlin
fun writeSourceRef(raw: RawMediaMetadata, workId: Long): NX_WorkSourceRef {
    return NX_WorkSourceRef().apply {
        // ... existing ...
        
        // FIX: Xtream-spezifische Felder setzen
        xtreamStreamId = raw.sourceHints["xtream.streamId"] as? Long
        xtreamCategoryId = raw.sourceHints["xtream.categoryId"] as? String
        sourceLastModifiedMs = (raw.sourceHints["xtream.addedTimestamp"] as? Long)
            ?.times(1000)  // API liefert Sekunden, wir speichern Millis
    }
}

fun writeVariant(raw: RawMediaMetadata, workId: Long): NX_WorkVariant {
    return NX_WorkVariant().apply {
        // ... existing ...
        
        // FIX: containerFormat direkt setzen
        containerFormat = raw.sourceHints["xtream.containerExtension"] as? String
    }
}
```

### 5.4 Key Generator: `SourceKeyGenerator.kt`

```kotlin
// AKTUELL (redundant):
// "src:xtream:xtream:xtream:vod:xtream:vod:801307"

// SOLL (vereinfacht):
// "src:xtream:{accountId}:vod:{streamId}"
// Beispiel: "src:xtream:konigtv:vod:801307"

fun generateSourceKey(
    sourceType: SourceType,
    accountId: String,
    contentType: String,  // "vod", "live", "series"
    streamId: Long
): String {
    return "src:${sourceType.name.lowercase()}:$accountId:$contentType:$streamId"
}
```

---

## 6. Prioritäten

| Prio | Fix | Impact |
|------|-----|--------|
| 🔴 P0 | `xtreamStreamId` setzen | Playback-URL-Bau ohne Parsing |
| 🔴 P0 | `xtreamCategoryId` setzen | Kategorie-basierte Navigation |
| 🟡 P1 | `sourceLastModifiedMs` setzen | "Neu hinzugefügt" Sortierung |
| 🟡 P1 | `containerFormat` setzen | Codec-Erkennung, MIME-Type |
| 🟢 P2 | `sourceKey` vereinfachen | Code-Lesbarkeit, Storage |
| 🟢 P2 | `mimeType` ableiten | Optional, nice-to-have |

---

## 7. Test-Validierung

Nach dem Fix sollte ein erneuter Export folgende Werte zeigen:

```json
{
  "sourceRefs": [{
    "xtreamStreamId": 801307,
    "xtreamCategoryId": "56",
    "sourceLastModifiedMs": 1765881814000,
    "sourceKey": "src:xtream:konigtv:vod:801307"
  }],
  "variants": [{
    "containerFormat": "mkv",
    "playbackHintsJson": "{\"xtream.streamId\":801307,\"xtream.categoryId\":\"56\",\"xtream.containerExtension\":\"mkv\"}"
  }]
}
```

---

## Anhang: Komplette Feld-Matrix

| API Feld | Transport DTO | RawMediaMetadata | NX_Work | NX_WorkSourceRef | NX_WorkVariant |
|----------|---------------|------------------|---------|------------------|----------------|
| `num` | ❌ | ❌ | ❌ | ❌ | ❌ |
| `name` | `title` | `rawTitle` | `canonicalTitle` (parsed) | `rawTitle` | - |
| `stream_type` | `streamType` | `mediaType` | `workType` | - | - |
| `stream_id` | `streamId` | `sourceKey` + hints | - | `xtreamStreamId` | hints |
| `stream_icon` | `streamIcon` | `images.poster` | `poster` | - | - |
| `rating` | `rating` | `rating` | `rating` | - | - |
| `rating_5based` | `rating5based` | fallback | - | - | - |
| `added` | `added` | `addedTimestamp` | - | `sourceLastModifiedMs` | - |
| `category_id` | `categoryId` | hints | - | `xtreamCategoryId` | hints |
| `container_extension` | `containerExtension` | hints | - | - | `containerFormat` |
| `custom_sid` | ❌ | ❌ | ❌ | ❌ | ❌ |
| `direct_source` | optional | optional | - | - | optional hint |

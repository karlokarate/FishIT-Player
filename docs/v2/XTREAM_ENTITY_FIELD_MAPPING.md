# Xtream API → NX Entity Field Mapping

> **Stand:** 27. Januar 2026  
> **Quelle:** Analyse realer API-Responses aus `test-data/initial_extracted/` und `test-data/xtream-responses/`

Dieses Dokument dokumentiert die vollständige Feldmapping-Analyse zwischen Xtream Codes API-Responses und den NX-Entities (`NX_Work`, `NX_WorkSourceRef`, `NX_WorkVariant`).

---

## Inhaltsverzeichnis

1. [NX Entity Definitionen](#1-nx-entity-definitionen)
2. [Xtream API Endpoints](#2-xtream-api-endpoints)
3. [VOD (Movies) Mapping](#3-vod-movies-mapping)
4. [Series Mapping](#4-series-mapping)
5. [Live Streams Mapping](#5-live-streams-mapping)
6. [Verfügbarkeit nach Initial Scan](#6-verfügbarkeit-nach-initial-scan)
7. [Kritische Erkenntnisse](#7-kritische-erkenntnisse)
8. [Empfehlungen](#8-empfehlungen)

---

## 1. NX Entity Definitionen

### 1.1 NX_Work (25 Felder)

Die zentrale Work-Entity für alle Medientypen (MOVIE, SERIES, EPISODE, LIVE, CLIP, AUDIOBOOK).

| Feld | Typ | Index | Beschreibung |
|------|-----|:-----:|--------------|
| `id` | `Long` | @Id | ObjectBox Auto-ID |
| **Identity** | | | |
| `workKey` | `String` | @Unique @Index | Canonical Key: `<workType>:<slug>:<year>` |
| `workType` | `String` | @Index | MOVIE, EPISODE, SERIES, CLIP, LIVE, AUDIOBOOK, UNKNOWN |
| **Core Metadata** | | | |
| `canonicalTitle` | `String` | @Index | Normalisierter Titel |
| `canonicalTitleLower` | `String` | @Index | Lowercase für Suche |
| `year` | `Int?` | @Index | Erscheinungsjahr (null für LIVE) |
| `season` | `Int?` | | Staffelnummer (nur EPISODE) |
| `episode` | `Int?` | | Episodennummer (nur EPISODE) |
| **External Authority IDs** | | | |
| `authorityKey` | `String?` | @Index | `<authority>:<type>:<id>` |
| `tmdbId` | `String?` | @Index | TMDB ID (numerisch als String) |
| `imdbId` | `String?` | @Index | IMDB ID (z.B. "tt0133093") |
| `tvdbId` | `String?` | @Index | TVDB ID |
| **Display Metadata** | | | |
| `poster` | `ImageRef?` | | Poster-Bild (via Converter) |
| `backdrop` | `ImageRef?` | | Hintergrundbild |
| `thumbnail` | `ImageRef?` | | Thumbnail (für Episoden) |
| `plot` | `String?` | | Beschreibung/Handlung |
| `rating` | `Double?` | | Bewertung (0-10 Skala) |
| `durationMs` | `Long?` | | Laufzeit in Millisekunden |
| `genres` | `String?` | | Genres (kommagetrennt) |
| `director` | `String?` | | Regisseur(e) |
| `cast` | `String?` | | Besetzung |
| `releaseDate` | `String?` | | Erscheinungsdatum (YYYY-MM-DD) |
| `trailer` | `String?` | | YouTube Trailer URL/ID |
| **Classification** | | | |
| `needsReview` | `Boolean` | | Klassifikation unklar |
| `isAdult` | `Boolean` | @Index | Adult Content Flag |
| **Timestamps** | | | |
| `createdAt` | `Long` | | Erstellungszeitpunkt |
| `updatedAt` | `Long` | | Letztes Update |

### 1.2 NX_WorkSourceRef (16 Felder)

Verknüpft ein Work mit einer Pipeline-Quelle (Telegram, Xtream, Local).

| Feld | Typ | Index | Beschreibung |
|------|-----|:-----:|--------------|
| `id` | `Long` | @Id | ObjectBox Auto-ID |
| **Identity** | | | |
| `sourceKey` | `String` | @Unique @Index | `<sourceType>:<accountKey>:<sourceId>` |
| `sourceType` | `String` | @Index | telegram, xtream, local, plex |
| `accountKey` | `String` | @Index | Account-Identifier |
| `sourceId` | `String` | | Quell-spezifische Item-ID |
| **Source Metadata** | | | |
| `rawTitle` | `String?` | | Original-Titel aus Quelle |
| `fileName` | `String?` | | Dateiname (falls verfügbar) |
| `fileSizeBytes` | `Long?` | | Dateigröße in Bytes |
| `mimeType` | `String?` | | MIME-Type |
| **Telegram-Specific** | | | |
| `telegramChatId` | `Long?` | | Telegram Chat ID |
| `telegramMessageId` | `Long?` | | Telegram Message ID |
| **Xtream-Specific** | | | |
| `xtreamStreamId` | `Int?` | | Xtream Stream/Series ID |
| `xtreamCategoryId` | `Int?` | | Xtream Kategorie ID |
| **Live Channel (EPG/Catchup)** | | | |
| `epgChannelId` | `String?` | | EPG Channel ID |
| `tvArchive` | `Int` | | 0=kein Catchup, 1=Catchup verfügbar |
| `tvArchiveDuration` | `Int` | | Catchup-Fenster in Tagen |
| **Timestamps** | | | |
| `discoveredAt` | `Long` | | Entdeckungszeitpunkt |
| `lastSeenAt` | `Long` | | Letztes Vorkommen |

### 1.3 NX_WorkVariant (16 Felder)

Playback-Variante für ein Work (Qualität, Sprache, Codec).

| Feld | Typ | Index | Beschreibung |
|------|-----|:-----:|--------------|
| `id` | `Long` | @Id | ObjectBox Auto-ID |
| **Identity** | | | |
| `variantKey` | `String` | @Unique @Index | `<sourceKey>#<qualityTag>:<languageTag>` |
| `qualityTag` | `String` | @Index | source, 1080p, 720p, 480p, 4k |
| `languageTag` | `String` | @Index | original, en, de, es, etc. |
| **Playback Hints** | | | |
| `playbackUrl` | `String?` | | Direkte Playback-URL |
| `playbackMethod` | `String` | | DIRECT, STREAMING, DOWNLOAD_FIRST |
| `containerFormat` | `String?` | | mp4, mkv, avi, etc. |
| `videoCodec` | `String?` | | h264, h265, vp9, etc. |
| `audioCodec` | `String?` | | aac, ac3, dts, etc. |
| `width` | `Int?` | | Auflösung Breite |
| `height` | `Int?` | | Auflösung Höhe |
| `bitrateBps` | `Long?` | | Bitrate in bps |
| `playbackHintsJson` | `String?` | | JSON-Map für source-spezifische Daten |
| **Source Link** | | | |
| `sourceKey` | `String` | @Index | Referenz zur SourceRef |
| **Timestamps** | | | |
| `createdAt` | `Long` | | Erstellungszeitpunkt |

---

## 2. Xtream API Endpoints

### 2.1 Listen-APIs (Initial Scan)

| Endpoint | Beschreibung | Response-Typ |
|----------|--------------|--------------|
| `get_vod_streams` | VOD/Movie-Liste | `Array<VodStream>` |
| `get_series` | Serien-Liste | `Array<SeriesStream>` |
| `get_live_streams` | Live-Kanal-Liste | `Array<LiveStream>` |

### 2.2 Detail-APIs (Backfill)

| Endpoint | Beschreibung | Response-Typ |
|----------|--------------|--------------|
| `get_vod_info?vod_id=X` | VOD-Details | `{ info: VodInfo, movie_data: MovieData }` |
| `get_series_info?series_id=X` | Serien-Details | `{ info: SeriesInfo, seasons: [...], episodes: {...} }` |

### 2.3 Kategorien-APIs

| Endpoint | Beschreibung |
|----------|--------------|
| `get_vod_categories` | VOD-Kategorien |
| `get_series_categories` | Serien-Kategorien |
| `get_live_categories` | Live-Kategorien |

---

## 3. VOD (Movies) Mapping

### 3.1 Listen-API: `get_vod_streams`

**Beispiel-Response:**
```json
{
  "num": 1,
  "name": "Sisu: Road to Revenge | 2025 | 7.4",
  "stream_type": "movie",
  "stream_id": 800689,
  "stream_icon": "https://image.tmdb.org/t/p/w600_and_h900_bestv2/xxx.jpg",
  "rating": "7.413",
  "rating_5based": 3.7,
  "added": "1765881814",
  "category_id": "56",
  "container_extension": "mkv",
  "custom_sid": "",
  "direct_source": ""
}
```

**Alle Felder in Listen-API:**
- `num`, `name`, `stream_type`, `stream_id`, `stream_icon`
- `rating`, `rating_5based`, `added`, `category_id`
- `container_extension`, `custom_sid`, `direct_source`

**Mapping zu NX-Entities:**

| API Feld | Wert (Beispiel) | → NX_Work | → NX_WorkSourceRef | → NX_WorkVariant |
|----------|-----------------|-----------|-------------------|------------------|
| `stream_id` | `800689` | - | `xtreamStreamId` ✅ | - |
| `name` | `"Sisu... \| 2025 \| 7.4"` | `canonicalTitle` ✅ | `rawTitle` ✅ | - |
| `stream_icon` | TMDB URL | `poster` ✅ | - | - |
| `rating` | `"7.413"` | `rating` ✅ | - | - |
| `rating_5based` | `3.7` | `rating` (×2) ✅ | - | - |
| `added` | `"1765881814"` | - | `discoveredAt` ✅ | - |
| `category_id` | `"56"` | - | `xtreamCategoryId` ✅ | - |
| `container_extension` | `"mkv"` | - | - | `containerFormat` ✅ |
| `stream_type` | `"movie"` | `workType=MOVIE` ✅ | - | - |
| `num` | `1` | ❌ (Sortierung) | - | - |
| `custom_sid` | `""` | ❌ | - | - |
| `direct_source` | `""` | ❌ | - | - |

### 3.2 Detail-API: `get_vod_info`

**Beispiel-Response (info Block):**
```json
{
  "info": {
    "tmdb_url": "https://www.themoviedb.org/movie/20145",
    "tmdb_id": "20145",
    "name": "36 China Town | 2006 | 5.7",
    "o_name": "36 China Town | 2006 | 5.7",
    "cover_big": "https://image.tmdb.org/t/p/.../xxx.jpg",
    "movie_image": "https://image.tmdb.org/t/p/.../xxx.jpg",
    "releasedate": "2006-04-21",
    "episode_run_time": "120",
    "youtube_trailer": "",
    "director": "Mustan Alibhai Burmawalla, Abbas Alibhai Burmawalla...",
    "actors": "Akshaye Khanna, Shahid Kapoor...",
    "cast": "Akshaye Khanna, Shahid Kapoor...",
    "description": "Sonia, die reiche Besitzerin...",
    "plot": "Sonia, die reiche Besitzerin...",
    "age": "",
    "mpaa_rating": "",
    "rating_count_kinopoisk": 0,
    "country": "India",
    "genre": "Komödie, Thriller",
    "backdrop_path": ["https://image.tmdb.org/t/p/.../xxx.jpg"],
    "duration_secs": 7831,
    "duration": "02:10:31",
    "video": {
      "codec_name": "hevc",
      "width": 1920,
      "height": 816
    },
    "audio": {
      "codec_name": "ac3",
      "sample_rate": "48000",
      "channels": 2
    },
    "bitrate": 2470,
    "rating": "5.791",
    "imdb_id": "tt0477252"
  },
  "movie_data": {
    "stream_id": 399548,
    "name": "36 China Town | 2006 | 5.7",
    "added": "1685137691",
    "category_id": "223",
    "container_extension": "mkv"
  }
}
```

**Alle Felder in info Block:**
- `tmdb_url`, `tmdb_id`, `name`, `o_name`
- `cover_big`, `movie_image`, `releasedate`, `episode_run_time`
- `youtube_trailer`, `director`, `actors`, `cast`
- `description`, `plot`, `age`, `mpaa_rating`
- `rating_count_kinopoisk`, `country`, `genre`
- `backdrop_path`, `duration_secs`, `duration`
- `video` (Object), `audio` (Object), `bitrate`, `rating`, `imdb_id`

**Mapping zu NX-Entities (Backfill):**

| API Feld (info) | → NX_Work | → NX_WorkVariant |
|-----------------|-----------|------------------|
| `tmdb_id` | `tmdbId` ✅ | - |
| `imdb_id` | `imdbId` ✅ | - |
| `name` / `o_name` | `canonicalTitle` ✅ | - |
| `cover_big` / `movie_image` | `poster` ✅ | - |
| `releasedate` | `releaseDate` ✅, `year` ✅ | - |
| `youtube_trailer` | `trailer` ✅ | - |
| `director` | `director` ✅ | - |
| `actors` / `cast` | `cast` ✅ | - |
| `description` / `plot` | `plot` ✅ | - |
| `genre` | `genres` ✅ | - |
| `duration_secs` | `durationMs` ✅ (×1000) | - |
| `duration` | `durationMs` ✅ (parsed) | - |
| `rating` | `rating` ✅ | - |
| `backdrop_path[]` | `backdrop` ✅ (erstes Element) | - |
| `video.codec_name` | - | `videoCodec` ✅ |
| `video.width` | - | `width` ✅ |
| `video.height` | - | `height` ✅ |
| `audio.codec_name` | - | `audioCodec` ✅ |
| `bitrate` | - | `bitrateBps` ✅ (×1000) |
| `country` | ❌ (nicht im Entity) | - |
| `age` / `mpaa_rating` | ❌ (nicht im Entity) | - |

---

## 4. Series Mapping

### 4.1 Listen-API: `get_series`

**Beispiel-Response:**
```json
{
  "num": 1,
  "name": "Madam Secretary",
  "series_id": -441,
  "cover": "https://image.tmdb.org/t/p/.../xxx.jpg",
  "plot": "Nachdem Elizabeth McCord ihre Karriere...",
  "cast": "Téa Leoni, Tim Daly, Zeljko Ivanek...",
  "director": "",
  "genre": "Drama, War & Politics",
  "releaseDate": "2014-09-21",
  "last_modified": "1712604453",
  "rating": "7",
  "rating_5based": 3.5,
  "backdrop_path": ["https://image.tmdb.org/t/p/.../xxx.jpg"],
  "youtube_trailer": "",
  "episode_run_time": "45",
  "category_id": "66"
}
```

**Alle Felder in Listen-API:**
- `num`, `name`, `series_id`, `cover`
- `plot`, `cast`, `director`, `genre`
- `releaseDate`, `last_modified`, `rating`, `rating_5based`
- `backdrop_path`, `youtube_trailer`, `episode_run_time`, `category_id`

**Mapping zu NX-Entities:**

| API Feld | → NX_Work (SERIES) | → NX_WorkSourceRef |
|----------|-------------------|-------------------|
| `series_id` | - | `xtreamStreamId` ✅ |
| `name` | `canonicalTitle` ✅ | `rawTitle` ✅ |
| `cover` | `poster` ✅ | - |
| `plot` | `plot` ✅ | - |
| `cast` | `cast` ✅ | - |
| `director` | `director` ✅ | - |
| `genre` | `genres` ✅ | - |
| `releaseDate` | `releaseDate` ✅, `year` ✅ | - |
| `rating` | `rating` ✅ | - |
| `rating_5based` | `rating` (×2) ✅ | - |
| `backdrop_path[]` | `backdrop` ✅ | - |
| `youtube_trailer` | `trailer` ✅ | - |
| `episode_run_time` | `durationMs` ✅ | - |
| `category_id` | - | `xtreamCategoryId` ✅ |
| `last_modified` | - | `lastSeenAt` ✅ |

> **⚠️ Wichtig:** Die Series Listen-API ist deutlich REICHER als VOD Listen-API!

### 4.2 Detail-API: `get_series_info`

**Top-Level Struktur:**
```json
{
  "seasons": [...],
  "info": {...},
  "episodes": { "1": [...], "2": [...] }
}
```

**info Block:** (identisch zu Listen-API)

**seasons[] Struktur:**
```json
{
  "air_date": "2022-11-17",
  "episode_count": 8,
  "id": 126785,
  "name": "Staffel 1",
  "overview": "Die Serie folgt einer Gruppe...",
  "season_number": 1,
  "cover": "https://image.tmdb.org/t/p/.../xxx.jpg",
  "cover_big": "https://image.tmdb.org/t/p/.../xxx.jpg"
}
```

**episodes[seasonNum][] Struktur:**
```json
{
  "id": "396757",
  "episode_num": 1,
  "title": "1899 - S01E01 - Das Schiff",
  "container_extension": "mp4",
  "info": {
    "tmdb_id": 1837500,
    "releasedate": "2022-11-17",
    "plot": "Maura hilft einer schwangeren Frau...",
    "duration_secs": 3602,
    "duration": "01:00:02",
    "movie_image": "https://image.tmdb.org/t/p/.../xxx.jpg",
    "video": { "codec_name": "h264", "width": 1280, "height": 544 },
    "audio": { "codec_name": "aac", "bit_rate": "116962" },
    "bitrate": 1400,
    "rating": 7.744,
    "season": "1"
  },
  "custom_sid": "",
  "added": "1685447912",
  "season": 1,
  "direct_source": ""
}
```

**Episode-Mapping zu NX-Entities:**

| API Feld | → NX_Work (EPISODE) | → NX_WorkSourceRef | → NX_WorkVariant |
|----------|--------------------|--------------------|------------------|
| `id` | - | `xtreamStreamId` ✅ | - |
| `episode_num` | `episode` ✅ | - | - |
| `title` | `canonicalTitle` ✅ | `rawTitle` ✅ | - |
| `season` | `season` ✅ | - | - |
| `container_extension` | - | - | `containerFormat` ✅ |
| `added` | - | `discoveredAt` ✅ | - |
| `info.tmdb_id` | `tmdbId` ✅ | - | - |
| `info.releasedate` | `releaseDate` ✅ | - | - |
| `info.plot` | `plot` ✅ | - | - |
| `info.duration_secs` | `durationMs` ✅ (×1000) | - | - |
| `info.movie_image` | `thumbnail` ✅ | - | - |
| `info.rating` | `rating` ✅ | - | - |
| `info.video.codec_name` | - | - | `videoCodec` ✅ |
| `info.video.width` | - | - | `width` ✅ |
| `info.video.height` | - | - | `height` ✅ |
| `info.audio.codec_name` | - | - | `audioCodec` ✅ |
| `info.bitrate` | - | - | `bitrateBps` ✅ (×1000) |

---

## 5. Live Streams Mapping

### 5.1 Listen-API: `get_live_streams`

**Beispiel-Response:**
```json
{
  "num": 1,
  "name": "▃ ▅ ▆ █ DE HEVC █ ▆ ▅ ▃",
  "stream_type": "live",
  "stream_id": 81568,
  "stream_icon": "https://granada.choval.xyz/de/hevc/deutschflag.png",
  "epg_channel_id": null,
  "added": "1604353552",
  "category_id": "129",
  "custom_sid": "",
  "tv_archive": 0,
  "direct_source": "",
  "tv_archive_duration": 0
}
```

**Alle Felder in Listen-API:**
- `num`, `name`, `stream_type`, `stream_id`, `stream_icon`
- `epg_channel_id`, `added`, `category_id`, `custom_sid`
- `tv_archive`, `direct_source`, `tv_archive_duration`

**Mapping zu NX-Entities:**

| API Feld | → NX_Work (LIVE) | → NX_WorkSourceRef |
|----------|-----------------|-------------------|
| `stream_id` | - | `xtreamStreamId` ✅ |
| `name` | `canonicalTitle` ✅ | `rawTitle` ✅ |
| `stream_icon` | `poster` ✅ | - |
| `epg_channel_id` | - | `epgChannelId` ✅ |
| `tv_archive` | - | `tvArchive` ✅ |
| `tv_archive_duration` | - | `tvArchiveDuration` ✅ |
| `added` | - | `discoveredAt` ✅ |
| `category_id` | - | `xtreamCategoryId` ✅ |
| `stream_type` | `workType=LIVE` ✅ | - |

> **⚠️ Wichtig:** Live-Streams haben KEINE Content-Metadaten (plot, rating, cast, etc.)!  
> Es gibt auch KEINE Detail-API für Live-Streams.

---

## 6. Verfügbarkeit nach Initial Scan

### 6.1 NX_Work Felder

| Feld | VOD (List) | VOD (Detail) | Series (List) | Series (Detail) | Episode | Live |
|------|:----------:|:------------:|:-------------:|:---------------:|:-------:|:----:|
| `workKey` | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `workType` | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `canonicalTitle` | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `canonicalTitleLower` | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `year` | ⚠️¹ | ✅ | ✅ | ✅ | ✅ | ❌ |
| `season` | - | - | - | - | ✅ | - |
| `episode` | - | - | - | - | ✅ | - |
| `authorityKey` | ❌ | ✅² | ❌ | ✅² | ✅² | ❌ |
| `tmdbId` | ❌ | ✅ | ❌ | ❌³ | ✅ | ❌ |
| `imdbId` | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ |
| `tvdbId` | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| `poster` | ✅ | ✅ | ✅ | ✅ | ❌ | ✅ |
| `backdrop` | ❌ | ✅ | ✅ | ✅ | ❌ | ❌ |
| `thumbnail` | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ |
| `plot` | ❌ | ✅ | ✅ | ✅ | ✅ | ❌ |
| `rating` | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ |
| `durationMs` | ❌ | ✅ | ✅⁴ | ✅⁴ | ✅ | ❌ |
| `genres` | ❌ | ✅ | ✅ | ✅ | ❌ | ❌ |
| `director` | ❌ | ✅ | ✅ | ✅ | ❌ | ❌ |
| `cast` | ❌ | ✅ | ✅ | ✅ | ❌ | ❌ |
| `releaseDate` | ❌ | ✅ | ✅ | ✅ | ✅ | ❌ |
| `trailer` | ❌ | ✅ | ✅ | ✅ | ❌ | ❌ |
| `needsReview` | ⚠️ | ⚠️ | ⚠️ | ⚠️ | ⚠️ | ⚠️ |
| `isAdult` | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |

**Legende:**
- ✅ = Direkt verfügbar
- ⚠️ = Indirekt/teilweise verfügbar
- ❌ = Nicht verfügbar
- ¹ = Jahr wird aus Titel geparst wenn vorhanden (z.B. "Movie | 2024 | 7.5")
- ² = Wird aus tmdbId generiert wenn vorhanden
- ³ = Series-Info liefert KEINEN tmdbId für die Serie selbst, nur für Episoden
- ⁴ = episode_run_time liefert durchschnittliche Episodenlänge

### 6.2 NX_WorkSourceRef Felder

| Feld | VOD | Series | Episode | Live |
|------|:---:|:------:|:-------:|:----:|
| `sourceKey` | ✅ | ✅ | ✅ | ✅ |
| `sourceType` | ✅ | ✅ | ✅ | ✅ |
| `accountKey` | ✅ | ✅ | ✅ | ✅ |
| `sourceId` | ✅ | ✅ | ✅ | ✅ |
| `rawTitle` | ✅ | ✅ | ✅ | ✅ |
| `fileName` | ❌ | ❌ | ❌ | ❌ |
| `fileSizeBytes` | ❌ | ❌ | ❌ | ❌ |
| `mimeType` | ❌ | ❌ | ❌ | ❌ |
| `telegramChatId` | - | - | - | - |
| `telegramMessageId` | - | - | - | - |
| `xtreamStreamId` | ✅ | ✅ | ✅ | ✅ |
| `xtreamCategoryId` | ✅ | ✅ | ❌¹ | ✅ |
| `epgChannelId` | ❌ | ❌ | ❌ | ✅ |
| `tvArchive` | ❌ | ❌ | ❌ | ✅ |
| `tvArchiveDuration` | ❌ | ❌ | ❌ | ✅ |
| `discoveredAt` | ✅ | ✅ | ✅ | ✅ |
| `lastSeenAt` | ✅ | ✅ | ✅ | ✅ |

**Legende:**
- ¹ = Episode hat keine eigene category_id, erbt von Series

### 6.3 NX_WorkVariant Felder

| Feld | VOD (List) | VOD (Detail) | Episode | Live |
|------|:----------:|:------------:|:-------:|:----:|
| `variantKey` | ✅ | ✅ | ✅ | ✅ |
| `qualityTag` | ❌¹ | ⚠️² | ⚠️² | ❌ |
| `languageTag` | ❌ | ❌ | ❌ | ❌ |
| `playbackUrl` | ✅ | ✅ | ✅ | ✅ |
| `playbackMethod` | ✅ | ✅ | ✅ | ✅ |
| `containerFormat` | ✅ | ✅ | ✅ | ❌ |
| `videoCodec` | ❌ | ✅ | ✅ | ❌ |
| `audioCodec` | ❌ | ✅ | ✅ | ❌ |
| `width` | ❌ | ✅ | ✅ | ❌ |
| `height` | ❌ | ✅ | ✅ | ❌ |
| `bitrateBps` | ❌ | ✅ | ✅ | ❌ |
| `playbackHintsJson` | ✅ | ✅ | ✅ | ✅ |
| `sourceKey` | ✅ | ✅ | ✅ | ✅ |

**Legende:**
- ¹ = Immer "source" da keine Qualitätsinfo in Listen-API
- ² = Kann aus width/height inferriert werden (1080p, 720p, etc.)

---

## 7. Kritische Erkenntnisse

### 7.1 VOD Listen-API ist ARM

Die VOD Listen-API (`get_vod_streams`) liefert nur minimale Metadaten:
- ✅ Titel, Poster, Rating, Container-Format
- ❌ **FEHLEND:** plot, cast, director, genre, year, tmdbId, imdbId, duration, backdrop, trailer

**→ Backfill via `get_vod_info` ist NOTWENDIG für vollständige Metadaten!**

### 7.2 Series Listen-API ist REICH

Die Series Listen-API (`get_series`) liefert bereits umfangreiche Metadaten:
- ✅ Titel, Poster, **Plot**, **Cast**, **Director**, **Genre**, **Backdrop**, **Trailer**, Rating, Duration
- ❌ **FEHLEND:** tmdbId (auf Series-Ebene), imdbId

**→ Backfill via `get_series_info` ist nur für Episoden-Details notwendig!**

### 7.3 Live hat KEINE Content-Metadaten

Die Live-API liefert ausschließlich technische/EPG-Felder:
- ✅ Name, Icon, EPG-Channel-ID, TV-Archive-Info
- ❌ **KOMPLETT FEHLEND:** plot, rating, genre, cast, etc.

**→ Kein Backfill möglich für Content-Metadaten!**

### 7.4 isAdult wird NIE geliefert

Kein Xtream-API-Endpoint liefert Adult-Content-Klassifikation.

**→ Muss durch Normalizer aus Kategorie/Genre inferriert werden!**

### 7.5 tvdbId wird NIE geliefert

TVDB-Integration ist in Xtream-Panels nicht vorhanden.

**→ Feld bleibt immer `null` für Xtream-Quellen!**

### 7.6 Series tmdbId nur auf Episode-Ebene

`get_series_info` liefert tmdbId nur in `episodes[].info.tmdb_id`, nicht auf Series-Ebene.

**→ Series.tmdbId muss ggf. extern aufgelöst werden (TMDB-API)!**

### 7.7 Codec/Quality nur via Detail-API

Video/Audio-Codec, Auflösung und Bitrate sind nur in Detail-APIs verfügbar.

**→ `qualityTag` kann erst nach Backfill zuverlässig gesetzt werden!**

---

## 8. Empfehlungen

### 8.1 Backfill-Strategie

| Content-Typ | Backfill notwendig? | Priorität | Grund |
|-------------|:-------------------:|:---------:|-------|
| **VOD** | ✅ JA | HOCH | Listen-API liefert keine Metadaten |
| **Series** | ⚠️ TEILWEISE | MITTEL | Listen-API ist reich, nur für Episodes |
| **Live** | ❌ NEIN | - | Keine Detail-API verfügbar |

### 8.2 Normalizer-Anforderungen

1. **Title-Parsing:** Jahr aus Titel extrahieren (z.B. "Movie | 2024 | 7.5")
2. **Adult-Detection:** Aus Kategorie/Genre inferrieren
3. **Quality-Detection:** Aus width/height berechnen (1080p, 720p, 4k, etc.)
4. **Rating-Normalisierung:** rating_5based × 2 für 0-10 Skala

### 8.3 Fehlende Entity-Felder (Kandidaten für Erweiterung)

| Feld | Quelle | Empfehlung |
|------|--------|------------|
| `country` | VOD Detail | Optional hinzufügen |
| `mpaaRating` | VOD Detail | Optional hinzufügen |
| `language` | Audio-Track | Wäre nützlich für languageTag |
| `originalTitle` | o_name | Könnte relevant sein für Internationalisierung |

---

## Anhang: Testdaten-Quellen

| Datei | Endpoint | Inhalt |
|-------|----------|--------|
| `test-data/initial_extracted/4/response_body.json` | `get_live_streams` | Live-Kanal-Liste |
| `test-data/initial_extracted/5/response_body.json` | `get_series` | Serien-Liste |
| `test-data/xtream-responses/vod_streams.json` | `get_vod_streams` | VOD-Liste |
| `test-data/xtream-responses/vod_details_response_xtream.txt` | `get_vod_info` | VOD-Details |
| `test-data/xtream-responses/series_detail_response_xtream.txt` | `get_series_info` | Serien-Details |

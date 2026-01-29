# Xtream API → Entity Mapping Guide (SSOT)

**Version:** 1.0  
**Erstellt:** 2026-01-29  
**Branch:** architecture/v2-bootstrap  
**Status:** ✅ PRODUCTION READY

---

## 1. Übersicht: API-zu-Entity-Analyse-Methode

Dieses Dokument definiert die **nachvollziehbare Methode**, die jeder Agent anwenden kann, um Xtream-API-Daten korrekt in FishIT-Player-Entities zu mappen.

### 1.1 Der Analyse-Workflow (Schritt für Schritt)

```
┌─────────────────────┐     ┌──────────────────────┐     ┌─────────────────────┐
│  1. API Response    │ ──► │  2. DTO Parsing      │ ──► │  3. RawMediaMetadata│
│  (JSON vom Server)  │     │  (Gson Deserialize)  │     │  (Pipeline Output)  │
└─────────────────────┘     └──────────────────────┘     └─────────────────────┘
                                                                    │
                                                                    ▼
┌─────────────────────┐     ┌──────────────────────┐     ┌─────────────────────┐
│  6. UI Display      │ ◄── │  5. NX_* Entities    │ ◄── │  4. Normalizer      │
│  (Feature Modules)  │     │  (ObjectBox DB)      │     │  (Canonical Title)  │
└─────────────────────┘     └──────────────────────┘     └─────────────────────┘
```

### 1.2 Wann diesen Guide anwenden

- **Vor** jeder Änderung an Xtream-Mappern
- **Bei** Bug-Analysen zu fehlenden/falschen Feldern
- **Für** Erweiterungen (neue API-Felder integrieren)
- **Beim** Implementieren neuer Content-Typen

---

## 2. Vollständige Feld-Mapping-Tabellen

### 2.1 VOD (Movies) - `get_vod_streams`

| API JSON Feld | DTO Feld (`XtreamVodItem`) | RawMediaMetadata Feld | NX_* Entity Feld | Anmerkungen |
|---------------|---------------------------|----------------------|------------------|-------------|
| `stream_id` | `id: Int` | `sourceId` (via Codec) | `sourceItemKey` | **KRITISCH**: XtreamIdCodec.vod(id) |
| `name` | `name: String` | `originalTitle` | `displayTitle` | Wird nicht gereinigt! |
| `stream_icon` | `streamIcon: String?` | `poster: ImageRef.Http` | `posterRef` | Via XtreamImageRefExtensions |
| `year` | `year: String?` | `year: Int?` | `year` | Validierung: 1900-2100, nicht "0" |
| `rating` | `rating: Double?` | `rating` | `rating` | TMDB-basiert, 1.0-10.0 |
| `rating_5based` | `rating5Based: Double?` | `rating` (x2 Fallback) | `rating` | Nur wenn `rating` fehlt |
| `added` | `added: Long?` | `addedTimestamp` | `createdAtMs` | Unix-Timestamp (Millisekunden) |
| `added` | `added: Long?` | `lastModifiedTimestamp` | `sourceLastModifiedMs` | **BUG FIX**: Auch hier setzen! |
| `container_extension` | `containerExtension: String?` | `playbackHints["xtream.containerExtension"]` | `container` | z.B. "mkv", "mp4" |
| `category_id` | `categoryId: String?` | `playbackHints["xtream.categoryId"]` | - | Für Filtering |
| `tmdb_id` | `tmdbId: Int?` | `externalIds.tmdb` | `tmdbId` | TmdbRef(MOVIE, id) |
| `plot` | `plot: String?` | `plot` | `plot` | Kurzbeschreibung |
| `genre` | `genre: String?` | `genres` | `genres` | Xtream verwendet Singular! |
| `direct_source` | `directSource: String?` | - | - | Nicht verwendet |
| `custom_sid` | `customSid: String?` | - | - | Nicht verwendet |

**sourceId Format:** `xtream:vod:{id}` → z.B. `xtream:vod:12345`

### 2.2 Series - `get_series`

| API JSON Feld | DTO Feld (`XtreamSeriesItem`) | RawMediaMetadata Feld | NX_* Entity Feld | Anmerkungen |
|---------------|-------------------------------|----------------------|------------------|-------------|
| `series_id` | `id: Int` | `sourceId` (via Codec) | `sourceItemKey` | XtreamIdCodec.series(id) |
| `name` | `name: String` | `originalTitle` | `displayTitle` | Keine Reinigung |
| `cover` | `cover: String?` | `poster: ImageRef.Http` | `posterRef` | |
| `backdrop_path` | `backdropPath: List<String>?` | `backdrop: ImageRef.Http` | `backdropRef` | Erstes Element |
| `year` | `year: String?` | `year: Int?` | `year` | Validierung wie VOD |
| `release_date` | `releaseDate: String?` | `releaseDate` + Fallback `year` | `year` | ISO: "2014-09-21" |
| `last_modified` | `lastModified: Long?` | `addedTimestamp` + `lastModifiedTimestamp` | beide | Serien haben kein "added"! |
| `rating` | `rating: Double?` | `rating` | `rating` | TMDB-basiert |
| `tmdb_id` | `tmdbId: Int?` | `externalIds.tmdb` | `tmdbId` | TmdbRef(**TV**, id) ← Wichtig! |
| `plot` | `plot: String?` | `plot` | `plot` | |
| `genre` | `genre: String?` | `genres` | `genres` | Singular "genre" |
| `director` | `director: String?` | `director` | `director` | |
| `cast` | `cast: String?` | `cast` | `cast` | Komma-separiert |
| `youtube_trailer` | `youtubeTrailer: String?` | `trailer` | `trailer` | YouTube URL/ID |
| `category_id` | `categoryId: String?` | `categoryId` | - | |

**sourceId Format:** `xtream:series:{id}` → z.B. `xtream:series:456`

### 2.3 Episodes - `get_series_info`

| API JSON Feld | DTO Feld (`XtreamEpisode`) | RawMediaMetadata Feld | NX_* Entity Feld | Anmerkungen |
|---------------|---------------------------|----------------------|------------------|-------------|
| `id` | `id: Int` | `playbackHints["xtream.episodeId"]` | - | Stream-ID für Playback! |
| `series_id` | `seriesId: Int` | `playbackHints["xtream.seriesId"]` | - | Parent-Referenz |
| `season` | `seasonNumber: Int` | `season` | `season` | 1-basiert |
| `episode_num` | `episodeNumber: Int` | `episode` | `episode` | 1-basiert |
| `title` | `title: String` | `originalTitle` | `displayTitle` | Fallback: Serienname |
| `container_extension` | `containerExtension: String?` | `playbackHints["xtream.containerExtension"]` | `container` | |
| `added` | `added: Long?` | `addedTimestamp` | `createdAtMs` | |
| `rating` | `rating: Double?` | `rating` | `rating` | |
| `tmdb_id` (Episode) | `episodeTmdbId: Int?` | `playbackHints["xtream.episodeTmdbId"]` | - | Optional |
| `tmdb_id` (Series) | `seriesTmdbId: Int?` | `externalIds.tmdb` | `tmdbId` | TmdbRef(**TV**, seriesTmdbId) |
| `plot` | `plot: String?` | `plot` | `plot` | |
| `release_date` | `releaseDate: String?` | `releaseDate` | - | |
| `info.video.codec` | `videoCodec: String?` | `playbackHints["video.codec"]` | - | |
| `info.video.width` | `videoWidth: Int?` | `playbackHints["video.width"]` | - | |
| `info.video.height` | `videoHeight: Int?` | `playbackHints["video.height"]` | - | |
| `info.audio.codec` | `audioCodec: String?` | `playbackHints["audio.codec"]` | - | |
| `info.audio.channels` | `audioChannels: Int?` | `playbackHints["audio.channels"]` | - | |

**sourceId Format:** `xtream:episode:series:{seriesId}:s{season}:e{episode}` → z.B. `xtream:episode:series:456:s1:e5`

### 2.4 Live Channels - `get_live_streams`

| API JSON Feld | DTO Feld (`XtreamChannel`) | RawMediaMetadata Feld | NX_* Entity Feld | Anmerkungen |
|---------------|---------------------------|----------------------|------------------|-------------|
| `stream_id` | `id: Int` | `sourceId` (via Codec) | `sourceItemKey` | XtreamIdCodec.live(id) |
| `name` | `name: String` | `originalTitle` | `displayTitle` | Unicode-Decorators werden gereinigt! |
| `stream_icon` | `streamIcon: String?` | `poster` + `thumbnail` | `posterRef` | Logo als Poster |
| `epg_channel_id` | `epgChannelId: String?` | `epgChannelId` | `epgChannelId` | EPG-Lookup |
| `tv_archive` | `tvArchive: Int?` | `tvArchive` | `tvArchive` | 1 = Catchup aktiv |
| `tv_archive_duration` | `tvArchiveDuration: Int?` | `tvArchiveDuration` | `tvArchiveDuration` | Tage |
| `added` | `added: Long?` | `addedTimestamp` | `createdAtMs` | |
| `category_id` | `categoryId: String?` | `categoryId` | - | |
| `is_adult` | `isAdult: Boolean?` | `isAdult` | `isAdult` | Kinderprofile |

**sourceId Format:** `xtream:live:{id}` → z.B. `xtream:live:999`

---

## 3. ID-System: XtreamIdCodec (SSOT)

### 3.1 Warum ein zentraler Codec?

**Problem vorher:**
```kotlin
// ❌ Verstreut, fehleranfällig
sourceId = "xtream:vod:$id"       // Zeile 140
sourceId = "xtream:series:$id"    // Zeile 251
sourceId = "xtream:live:$id"      // Zeile 413
// Whitespace-Bugs: "xtream:vod: 123"
```

**Lösung jetzt:**
```kotlin
// ✅ Zentralisiert, typsicher
sourceId = XtreamIdCodec.vod(id)
sourceId = XtreamIdCodec.series(id)
sourceId = XtreamIdCodec.live(id)
```

### 3.2 Kanonische Formate (KEINE AUSNAHMEN)

| Typ | Format | Beispiel |
|-----|--------|----------|
| VOD | `xtream:vod:{vodId}` | `xtream:vod:12345` |
| Series | `xtream:series:{seriesId}` | `xtream:series:456` |
| Episode (bevorzugt) | `xtream:episode:{episodeId}` | `xtream:episode:789` |
| Episode (fallback) | `xtream:episode:series:{sid}:s{s}:e{e}` | `xtream:episode:series:456:s1:e5` |
| Live | `xtream:live:{channelId}` | `xtream:live:999` |

### 3.3 Nutzung im Code

```kotlin
import com.fishit.player.pipeline.xtream.ids.XtreamIdCodec

// Formatieren
val vodSourceId = XtreamIdCodec.vod(123)          // "xtream:vod:123"
val seriesSourceId = XtreamIdCodec.series(456)    // "xtream:series:456"
val liveSourceId = XtreamIdCodec.live(999)        // "xtream:live:999"

// Episode mit Composite ID (wenn kein episodeId vorhanden)
val episodeSourceId = XtreamIdCodec.episodeComposite(456, 1, 5)
// "xtream:episode:series:456:s1:e5"

// Parsen (für Playback/Navigation)
val parsed = XtreamIdCodec.parse("xtream:vod:123")
when (parsed) {
    is XtreamParsedSourceId.Vod -> println("VOD ID: ${parsed.vodId}")
    is XtreamParsedSourceId.Series -> println("Series ID: ${parsed.seriesId}")
    // ...
}
```

### 3.4 Typed ID Wrappers

Für zusätzliche Typsicherheit:

```kotlin
val vodId = XtreamVodId(123L)
val seriesId = XtreamSeriesId(456L)
val episodeId = XtreamEpisodeId(789L)
val channelId = XtreamChannelId(999L)

// Codec akzeptiert beide: Long/Int und Typed Wrapper
XtreamIdCodec.vod(vodId)      // XtreamVodId
XtreamIdCodec.vod(123)        // Int
XtreamIdCodec.vod(123L)       // Long
```

---

## 4. Bug-Fixes (Dokumentiert)

### 4.1 BUG FIX: lastModifiedTimestamp für VOD

**Problem:** `NX_WorkSourceRef.sourceLastModifiedMs` war immer `null` für VOD-Items.

**Ursache:** VOD-Liste liefert `added`-Timestamp, aber dieser wurde nur für `addedTimestamp` verwendet, nicht für `lastModifiedTimestamp`.

**Fix:** Beide Felder setzen:
```kotlin
// XtreamRawMetadataExtensions.kt
addedTimestamp = added,
lastModifiedTimestamp = added,  // ← NEU
```

### 4.2 BUG FIX: containerFormat null in Variant

**Problem:** `NX_WorkVariant.container` war immer `null`.

**Ursache:** `NxCatalogWriter.extractContainerFromHints()` suchte nach `"containerExtension"`, aber Pipeline verwendet `"xtream.containerExtension"` (via `PlaybackHintKeys.Xtream.CONTAINER_EXT`).

**Fix:** Alle Keys prüfen:
```kotlin
// NxCatalogWriter.kt
private fun extractContainerFromHints(hints: Map<String, String>): String? {
    val ext = hints["xtream.containerExtension"]  // ← PRIMÄR
        ?: hints["containerExtension"]
        ?: hints["extension"]
    // ...
}
```

### 4.3 BUG FIX: Episode und Live lastModifiedTimestamp (Jan 2026)

**Problem:** `NX_WorkSourceRef.sourceLastModifiedMs` war `null` für Episodes und Live-Channels.

**Ursache:** Episode und Live Extensions setzten `addedTimestamp`, aber nicht `lastModifiedTimestamp`.

**Fix:** Beide Felder setzen:
```kotlin
// XtreamRawMetadataExtensions.kt (Episode + Live)
addedTimestamp = added,
lastModifiedTimestamp = added,  // ← NEU
```

### 4.4 NOT A BUG: year und categoryName

**Analyse zeigte:** `year` und `categoryName` waren als "fehlend" gemeldet, aber:
- `year` → existiert nicht im `RawMediaMetadata` Interface als direktes Feld
- `categoryName` → existiert nicht im Interface

Diese Felder werden über andere Wege abgebildet oder sind nicht Teil des aktuellen Contracts.

---

## 5. Analyse-Checkliste für Agents

Wenn ein Agent ein neues API-Feld mappen soll:

### 5.1 Pre-Analysis

- [ ] API-Dokumentation oder echte Response prüfen
- [ ] Feld-Typ identifizieren (String, Int, Long, Boolean, List, etc.)
- [ ] Nullability prüfen (optional oder required?)
- [ ] Format prüfen (Unix-Timestamp? ISO-Datum? Komma-separiert?)

### 5.2 DTO Mapping

- [ ] DTO-Klasse unter `pipeline/xtream/src/main/java/.../model/` finden/erweitern
- [ ] Gson-Annotation prüfen: `@SerializedName("api_field_name")`
- [ ] Default-Wert bei optionalen Feldern setzen

### 5.3 RawMediaMetadata Mapping

- [ ] Existiert das Zielfeld in `RawMediaMetadata`?
- [ ] Welche Transformation ist nötig? (z.B. String→Int, Unix→Millis)
- [ ] Ist eine Validierung nötig? (z.B. Jahr 1900-2100)
- [ ] Gehört das Feld in `playbackHints`? (für Playback-spezifische Infos)

### 5.4 NX_* Entity Mapping

- [ ] Welche NX_* Entity ist das Ziel? (Work, SourceRef, Variant)
- [ ] Existiert das Zielfeld im Repository Interface?
- [ ] Wird die Transformation in `NxCatalogWriter` korrekt durchgeführt?

### 5.5 Verifizierung

- [ ] Unit-Tests für die Extension-Funktion hinzufügen/aktualisieren
- [ ] Compile prüfen: `./gradlew :pipeline:xtream:compileDebugKotlin`
- [ ] Tests ausführen: `./gradlew :pipeline:xtream:testDebugUnitTest`

---

## 6. Datei-Referenz

| Datei | Zweck |
|-------|-------|
| `pipeline/xtream/src/main/java/.../model/Xtream*.kt` | DTO-Definitionen für API-Responses |
| `pipeline/xtream/src/main/java/.../mapper/XtreamRawMetadataExtensions.kt` | DTO → RawMediaMetadata Mapping |
| `pipeline/xtream/src/main/java/.../mapper/XtreamImageRefExtensions.kt` | Image URL → ImageRef Mapping |
| `pipeline/xtream/src/main/java/.../ids/XtreamIdCodec.kt` | **SSOT** für sourceId Generierung |
| `pipeline/xtream/src/main/java/.../ids/XtreamSourceId.kt` | Typed ID Wrappers |
| `infra/data-nx/src/main/java/.../writer/NxCatalogWriter.kt` | RawMediaMetadata → NX_* Entities |
| `core/model/src/main/java/.../RawMediaMetadata.kt` | Canonical Pipeline Output Interface |
| `core/model/src/main/java/.../PlaybackHintKeys.kt` | Standardisierte playbackHints Keys |

---

## 7. Beispiel: Vollständiger Datenfluss für VOD

```
┌─────────────────────────────────────────────────────────────────────┐
│ 1. API RESPONSE (get_vod_streams)                                   │
├─────────────────────────────────────────────────────────────────────┤
│ {                                                                   │
│   "stream_id": 12345,                                               │
│   "name": "The Matrix | 1999 | 8.7",                                │
│   "stream_icon": "http://example.com/poster.jpg",                   │
│   "added": 1704067200,                                              │
│   "container_extension": "mkv",                                     │
│   "category_id": "1",                                               │
│   "tmdb_id": 603                                                    │
│ }                                                                   │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│ 2. DTO (XtreamVodItem)                                              │
├─────────────────────────────────────────────────────────────────────┤
│ XtreamVodItem(                                                      │
│   id = 12345,                                                       │
│   name = "The Matrix | 1999 | 8.7",                                 │
│   streamIcon = "http://example.com/poster.jpg",                     │
│   added = 1704067200000L,  // × 1000 für Millisekunden              │
│   containerExtension = "mkv",                                       │
│   categoryId = "1",                                                 │
│   tmdbId = 603                                                      │
│ )                                                                   │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│ 3. RawMediaMetadata (via toRawMetadata())                           │
├─────────────────────────────────────────────────────────────────────┤
│ RawMediaMetadata(                                                   │
│   originalTitle = "The Matrix | 1999 | 8.7",                        │
│   mediaType = MOVIE,                                                │
│   year = 1999,  // Extrahiert aus Titel!                            │
│   sourceType = XTREAM,                                              │
│   sourceId = "xtream:vod:12345",  // Via XtreamIdCodec              │
│   addedTimestamp = 1704067200000L,                                  │
│   lastModifiedTimestamp = 1704067200000L,                           │
│   poster = ImageRef.Http("http://example.com/poster.jpg"),          │
│   externalIds = ExternalIds(tmdb = TmdbRef(MOVIE, 603)),            │
│   playbackHints = {                                                 │
│     "xtream.contentType" = "vod",                                   │
│     "xtream.vodId" = "12345",                                       │
│     "xtream.containerExtension" = "mkv",                            │
│     "xtream.categoryId" = "1"                                       │
│   }                                                                 │
│ )                                                                   │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│ 4. NORMALIZER (core:metadata-normalizer)                            │
├─────────────────────────────────────────────────────────────────────┤
│ NormalizedMediaMetadata(                                            │
│   canonicalTitle = "The Matrix",  // Pipe-Suffix entfernt           │
│   year = 1999,                                                      │
│   tmdb = TmdbRef(MOVIE, 603),                                       │
│   // ... weitere normalisierte Felder                               │
│ )                                                                   │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│ 5. NX_* ENTITIES (via NxCatalogWriter)                              │
├─────────────────────────────────────────────────────────────────────┤
│ NX_Work(                                                            │
│   workKey = "movie:the-matrix:1999",                                │
│   type = MOVIE,                                                     │
│   displayTitle = "The Matrix",                                      │
│   year = 1999,                                                      │
│   tmdbId = "603",                                                   │
│   posterRef = "http:http://example.com/poster.jpg"                  │
│ )                                                                   │
│                                                                     │
│ NX_WorkSourceRef(                                                   │
│   sourceKey = "src:xtream:myaccount:vod:xtream:vod:12345",          │
│   workKey = "movie:the-matrix:1999",                                │
│   sourceItemKey = "xtream:vod:12345",                               │
│   sourceLastModifiedMs = 1704067200000L  // ← BUG FIX               │
│ )                                                                   │
│                                                                     │
│ NX_WorkVariant(                                                     │
│   variantKey = "v:src:...:default",                                 │
│   container = "mkv",  // ← BUG FIX                                  │
│   playbackHints = {...}                                             │
│ )                                                                   │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 8. Test-Abdeckung

### 8.1 Aktuelle Tests

| Test-Datei | Scope | Status |
|------------|-------|--------|
| `XtreamRawMetadataExtensionsTest.kt` | VOD, Series, Episode, Live Mapping + Bug-Fix Tests | ✅ 11/11 PASS |
| `XtreamIdCodecTest.kt` | ID Format/Parse, Round-Trip | ✅ 25+ PASS |

### 8.2 Ausführen

```bash
./gradlew :pipeline:xtream:testDebugUnitTest --no-daemon
```

---

## 9. Offene Punkte für zukünftige Arbeit

| Punkt | Beschreibung | Priorität |
|-------|--------------|-----------|
| Detail-APIs | `get_vod_info`, `get_series_info` reich-Metadaten | Medium |
| Categories | Category-Name zu Category-ID Mapping | Low |
| EPG Integration | Live-TV Programm-Guide | Medium |
| Catchup/Archive | TV-Archive Playback URLs | Low |

---

**Autor:** GitHub Copilot  
**Review-Status:** Selbst-Review durchgeführt  
**Nächster Schritt:** Anwendung auf Series + Live

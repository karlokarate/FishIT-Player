# Telegram Structured Bundles – Masterplan

**Version:** 1.0  
**Datum:** 2025-12-17  
**Status:** Entwurf – bereit zur Umsetzung  
**Scope:** Erkennung und Verarbeitung von strukturierten Telegram-Nachrichten-Clustern (PHOTO→TEXT→VIDEO)

---

## Executive Summary

Analyse von 398 Telegram-Chat-Exporten hat ergeben, dass 8 Chats **strukturierte Metadaten** enthalten, die eine drastische Optimierung der Pipeline ermöglichen:

- **Zero-Parsing-Path:** TMDB-IDs, Titel, Jahr, FSK direkt aus TEXT-Nachrichten extrahierbar
- **Zero-API-Call-Path:** Keine TMDB-API-Aufrufe für Basis-Metadaten nötig
- **Bundle-Konzept:** PHOTO→TEXT→VIDEO-Cluster mit identischem Timestamp als logische Einheit

Diese Erkenntnis ermöglicht **ultraschnelles Onboarding** für strukturierte Chats bei gleichzeitiger Unterstützung des regulären Parsing-Pfades für unstrukturierte Chats.

---

## 1. Analyseergebnisse

### 1.1 Chat-Klassifikation

| Chat-ID | Name | Pattern | TMDB | Videos | Photos |
|---------|------|---------|------|--------|--------|
| -1001434421634 | Mel Brooks 🥳 | 3er-Cluster | 9 | 9 | 7 |
| -1001452246125 | 🎬 Filme von 2001 bis 2010 🎥 | 3er-Cluster | 8 | 8+ | 8+ |
| -1001203115098 | 🎬⚠️ Filme ab: 2020 ⚠️🎥 | 3er-Cluster | 8 | 8+ | 8+ |
| -1001180440610 | 🎬 Filme von 2011 bis 2019 🎥 | 3er-Cluster | 8 | 8+ | 8+ |
| -1001491030766 | John Carpenter | 3er-Cluster | 6 | 6+ | 6+ |
| -1001326220574 | 🎬Filme kompakt!🎥 | 2er-Cluster | 8 | 8 | 8 |
| -1001545742878 | Der Frühe Vogel | Gemischt | 5 | 5+ | 5+ |
| -1001452717239 | Film & Serien JtL | Gemischt | 1 | 1+ | 1+ |

### 1.2 Nachrichtenstruktur (JSON-Export-Analyse)

**3er-Cluster (PHOTO → TEXT → VIDEO):**

```
Timestamp: 1731704712 (identisch für alle 3 Nachrichten)
├── PHOTO: content.sizes[] (mehrere Auflösungen bis 1000x1500)
├── TEXT:  tmdbUrl, tmdbRating, year, originalTitle, genres, fsk, director, lengthMinutes
└── VIDEO: content.duration, content.fileName, content.file.remoteId
```

**Typische TEXT-Felder (strukturiert):**

```json
{
  "tmdbUrl": "https://www.themoviedb.org/movie/12345-movie-name",
  "tmdbRating": 7.5,
  "year": 2020,
  "originalTitle": "The Movie",
  "genres": ["Action", "Drama"],
  "fsk": 12,
  "director": "John Doe",
  "lengthMinutes": 120,
  "productionCountry": "US"
}
```

### 1.3 ID-Korrelation

- Nachrichten im selben Cluster haben **identischen Unix-Timestamp** (`date`)
- Message-IDs differieren um exakt **1.048.576** (2²⁰) innerhalb eines Clusters
- Reihenfolge: PHOTO (niedrigste ID) → TEXT → VIDEO (höchste ID)

---

## 2. Architekturkonzept

### 2.1 Datenfluss-Übersicht (aktuell)

```
┌──────────────────────────────────────────────────────────────────────┐
│  TDLib (Native) → TdApi.Message                                      │
└───────────────────────────┬──────────────────────────────────────────┘
                            ▼
┌──────────────────────────────────────────────────────────────────────┐
│  transport-telegram                                                   │
│  DefaultTelegramTransportClient                                       │
│  TdApi.Message → TgMessage, TgContent (DTOs)                          │
└───────────────────────────┬──────────────────────────────────────────┘
                            ▼
┌──────────────────────────────────────────────────────────────────────┐
│  pipeline/telegram                                                    │
│  TelegramPipelineAdapter                                              │
│  TgMessage → TelegramMediaItem (mit toMediaItem())                    │
│                                                                       │
│  TelegramCatalogPipelineImpl                                          │
│  TelegramMediaItem → RawMediaMetadata (mit toRawMediaMetadata())      │
│  Emittiert: TelegramCatalogEvent.ItemDiscovered(TelegramCatalogItem)  │
└───────────────────────────┬──────────────────────────────────────────┘
                            ▼
┌──────────────────────────────────────────────────────────────────────┐
│  core/metadata-normalizer (ZENTRAL)                                   │
│  RawMediaMetadata → NormalizedMediaMetadata                           │
│  TMDB-Lookups, Titel-Cleaning, globalId-Berechnung                    │
└───────────────────────────┬──────────────────────────────────────────┘
                            ▼
┌──────────────────────────────────────────────────────────────────────┐
│  data-telegram                                                        │
│  NormalizedMediaMetadata → ObxTelegramItem (ObjectBox)                │
└──────────────────────────────────────────────────────────────────────┘
```

### 2.2 Erweiterter Datenfluss (Structured Bundles)

```
┌──────────────────────────────────────────────────────────────────────┐
│  transport-telegram                                                   │
│  TgMessage[] → Nachrichten mit identischem Timestamp                  │
└───────────────────────────┬──────────────────────────────────────────┘
                            ▼
┌──────────────────────────────────────────────────────────────────────┐
│  pipeline/telegram/grouper  [NEU]                                     │
│                                                                       │
│  TelegramMessageBundler                                               │
│  ├── Gruppiert Nachrichten nach identischem Timestamp                 │
│  ├── Klassifiziert: Structured (3er/2er) vs Unstructured              │
│  └── Emittiert: TelegramMessageBundle oder einzelne TgMessage         │
│                                                                       │
│  TelegramStructuredMetadataExtractor                                  │
│  ├── Extrahiert TEXT-Felder: tmdbUrl, year, fsk, genres, etc.         │
│  └── Mappt PHOTO.sizes[] auf ImageRef                                 │
└───────────────────────────┬──────────────────────────────────────────┘
                            ▼
┌──────────────────────────────────────────────────────────────────────┐
│  pipeline/telegram/mapper                                             │
│                                                                       │
│  TelegramMediaItem (erweitert um Bundle-Felder)                       │
│  ├── structuredTmdbId: String?      // aus tmdbUrl extrahiert         │
│  ├── structuredRating: Double?      // tmdbRating pass-through        │
│  ├── structuredYear: Int?           // year pass-through              │
│  ├── structuredFsk: Int?            // fsk für Kids-Filter            │
│  ├── structuredGenres: List<String>?// genres pass-through            │
│  ├── posterSizes: List<TelegramPhotoSize>? // aus PHOTO-Nachricht     │
│  └── bundleType: BundleType         // FULL_3ER, COMPACT_2ER, SINGLE  │
│                                                                       │
│  toRawMediaMetadata() (erweitert)                                     │
│  ├── externalIds.tmdbId aus structuredTmdbId                          │
│  ├── year aus structuredYear                                          │
│  ├── poster aus posterSizes (beste Auflösung)                         │
│  └── ageRating aus structuredFsk                                      │
└───────────────────────────┬──────────────────────────────────────────┘
                            ▼
┌──────────────────────────────────────────────────────────────────────┐
│  core/metadata-normalizer                                             │
│                                                                       │
│  Prüft: Hat RawMediaMetadata bereits tmdbId?                          │
│  ├── JA:  Skip TMDB-Lookup, direkt normalTitle aus TMDB-Cache         │
│  └── NEIN: Normaler Pfad (Titel-Parsing, TMDB-Search, etc.)           │
└──────────────────────────────────────────────────────────────────────┘
```

---

## 3. Contract-Compliance

### 3.1 MEDIA_NORMALIZATION_CONTRACT.md

> "Pipelines must not guess TMDB/IMDB IDs; they may only pass through IDs provided by the source."

✅ **Compliant:** Structured Bundles passieren TMDB-IDs durch, die von der Quelle (Telegram-Chat-Betreiber) bereitgestellt wurden. Die Pipeline "rät" nicht, sie liest strukturierte Felder.

### 3.2 Globale Pipeline-Regeln

| Regel | Status | Implementierung |
|-------|--------|-----------------|
| Pipeline darf nicht normalisieren | ✅ | Titel wird RAW übergeben |
| Pipeline darf keine TMDB-Lookups machen | ✅ | TMDB-ID wird pass-through |
| globalId bleibt leer | ✅ | Normalizer berechnet |
| Pipeline exportiert keine DTOs | ✅ | Nur RawMediaMetadata verlässt Pipeline |

### 3.3 Layer-Boundaries

| Layer | Erlaubt | Verboten |
|-------|---------|----------|
| transport-telegram | TgMessage, TgContent | RawMediaMetadata |
| pipeline/telegram | TelegramMediaItem (intern), RawMediaMetadata (export) | ObxTelegram*, TMDB-Client |
| data-telegram | RawMediaMetadata, ObxTelegramItem | TelegramMediaItem, TgMessage |

---

## 4. Modell-Erweiterungen

### 4.1 TelegramMediaItem (Erweiterungen)

```kotlin
// pipeline/telegram/model/TelegramMediaItem.kt

data class TelegramMediaItem(
    // ... bestehende Felder ...
    
    // === Structured Bundle Fields (NEU) ===
    
    /** TMDB-ID aus strukturierter TEXT-Nachricht (z.B. "12345" aus tmdbUrl) */
    val structuredTmdbId: String? = null,
    
    /** TMDB-Rating aus strukturierter TEXT-Nachricht */
    val structuredRating: Double? = null,
    
    /** Jahr aus strukturierter TEXT-Nachricht (überschreibt Parser-Heuristik) */
    val structuredYear: Int? = null,
    
    /** FSK-Altersfreigabe für Kids-Filter */
    val structuredFsk: Int? = null,
    
    /** Genres aus strukturierter TEXT-Nachricht */
    val structuredGenres: List<String>? = null,
    
    /** Director aus strukturierter TEXT-Nachricht */
    val structuredDirector: String? = null,
    
    /** Original-Titel aus strukturierter TEXT-Nachricht */
    val structuredOriginalTitle: String? = null,
    
    /** Produktionsland */
    val structuredProductionCountry: String? = null,
    
    /** Laufzeit in Minuten */
    val structuredLengthMinutes: Int? = null,
    
    /** Bundle-Typ für Debugging/Logging */
    val bundleType: TelegramBundleType = TelegramBundleType.SINGLE,
    
    /** Message-ID der TEXT-Nachricht im Bundle (für Debugging) */
    val textMessageId: Long? = null,
    
    /** Message-ID der PHOTO-Nachricht im Bundle (für Debugging) */
    val photoMessageId: Long? = null,
)

enum class TelegramBundleType {
    /** Vollständiger 3er-Cluster: PHOTO + TEXT + VIDEO */
    FULL_3ER,
    
    /** Kompakter 2er-Cluster: TEXT + VIDEO oder PHOTO + VIDEO */
    COMPACT_2ER,
    
    /** Einzelne Nachricht (kein Bundle) */
    SINGLE,
}
```

### 4.2 RawMediaMetadata (benötigte Erweiterungen)

```kotlin
// core/model/RawMediaMetadata.kt

data class RawMediaMetadata(
    // ... bestehende Felder ...
    
    /** Altersfreigabe (FSK/MPAA/etc.) für Kids-Filter */
    val ageRating: Int? = null,
    
    /** Rating (z.B. TMDB-Score 0.0-10.0) */
    val rating: Double? = null,
)
```

### 4.3 ExternalIds (ggf. erweitern)

```kotlin
// Prüfen: Hat ExternalIds bereits tmdbId als String?
data class ExternalIds(
    val tmdbId: String? = null,  // "12345" aus tmdbUrl
    val imdbId: String? = null,
    val tvdbId: String? = null,
)
```

---

## 5. Neue Komponenten

### 5.1 TelegramMessageBundler

**Pfad:** `pipeline/telegram/src/main/java/com/fishit/player/pipeline/telegram/grouper/TelegramMessageBundler.kt`

**Verantwortung:**

- Gruppiert TgMessage-Liste nach identischem Timestamp
- Klassifiziert Cluster nach Typ (3er, 2er, Single)
- Emittiert TelegramMessageBundle für zusammengehörige Nachrichten

```kotlin
/**
 * Gruppiert Telegram-Nachrichten nach identischem Timestamp in Bundles.
 *
 * Bundle-Erkennung:
 * - Nachrichten mit identischem `date` (Unix-Timestamp) gehören zusammen
 * - Reihenfolge in Bundles: PHOTO (niedrigste msgId) → TEXT → VIDEO (höchste msgId)
 *
 * Per MEDIA_NORMALIZATION_CONTRACT: Keine Normalisierung hier.
 * Bundle-Felder werden RAW extrahiert und an TelegramMediaItem übergeben.
 */
class TelegramMessageBundler {
    
    /**
     * Gruppiert Nachrichten nach Timestamp.
     *
     * @param messages Unsortierte Liste von TgMessage
     * @return Liste von TelegramMessageBundle (sortiert nach neuestem Timestamp)
     */
    fun groupByTimestamp(messages: List<TgMessage>): List<TelegramMessageBundle>
    
    /**
     * Klassifiziert einen Bundle-Typ basierend auf enthaltenen Nachrichtentypen.
     */
    fun classifyBundle(messages: List<TgMessage>): TelegramBundleType
}

data class TelegramMessageBundle(
    val timestamp: Long,
    val messages: List<TgMessage>,
    val bundleType: TelegramBundleType,
    val videoMessage: TgMessage?,
    val textMessage: TgMessage?,
    val photoMessage: TgMessage?,
)
```

### 5.2 TelegramStructuredMetadataExtractor

**Pfad:** `pipeline/telegram/src/main/java/com/fishit/player/pipeline/telegram/grouper/TelegramStructuredMetadataExtractor.kt`

**Verantwortung:**

- Extrahiert strukturierte Felder aus TEXT-Nachrichten
- Parst TMDB-URL zu ID
- Liefert RAW-Werte ohne Normalisierung

```kotlin
/**
 * Extrahiert strukturierte Metadaten aus Telegram TEXT-Nachrichten.
 *
 * Unterstützte Felder:
 * - tmdbUrl → tmdbId (via Regex /movie/(\d+))
 * - tmdbRating, year, fsk, genres, director, originalTitle
 * - lengthMinutes, productionCountry
 *
 * Per MEDIA_NORMALIZATION_CONTRACT: Alle Werte RAW extrahiert, keine Bereinigung.
 */
class TelegramStructuredMetadataExtractor {
    
    /**
     * Prüft, ob eine TEXT-Nachricht strukturierte Felder enthält.
     */
    fun hasStructuredFields(textMessage: TgMessage): Boolean
    
    /**
     * Extrahiert alle strukturierten Felder aus einer TEXT-Nachricht.
     *
     * @return StructuredMetadata oder null wenn keine strukturierten Felder
     */
    fun extractStructuredMetadata(textMessage: TgMessage): StructuredMetadata?
    
    /**
     * Extrahiert TMDB-ID aus URL.
     * 
     * Beispiel: "https://www.themoviedb.org/movie/12345-name" → "12345"
     */
    fun extractTmdbIdFromUrl(tmdbUrl: String?): String?
}

data class StructuredMetadata(
    val tmdbId: String?,
    val tmdbRating: Double?,
    val year: Int?,
    val fsk: Int?,
    val genres: List<String>,
    val director: String?,
    val originalTitle: String?,
    val lengthMinutes: Int?,
    val productionCountry: String?,
)
```

### 5.3 TelegramBundleToMediaItemMapper

**Pfad:** `pipeline/telegram/src/main/java/com/fishit/player/pipeline/telegram/mapper/TelegramBundleToMediaItemMapper.kt`

**Verantwortung:**

- Konvertiert TelegramMessageBundle → TelegramMediaItem
- Führt VIDEO, TEXT, PHOTO-Daten zusammen
- Wendet Tie-Breaker-Regeln an

```kotlin
/**
 * Mappt TelegramMessageBundle auf TelegramMediaItem.
 *
 * Mapping-Regeln:
 * 1. VIDEO-Nachricht liefert: remoteId, duration, fileName, mimeType, etc.
 * 2. TEXT-Nachricht liefert: structuredTmdbId, structuredYear, etc.
 * 3. PHOTO-Nachricht liefert: photoSizes für Poster
 *
 * Tie-Breaker (für Bundles mit mehreren VIDEOs):
 * - Größte Datei (sizeBytes)
 * - Längste Dauer (duration)
 * - Niedrigste messageId (deterministic)
 */
class TelegramBundleToMediaItemMapper {
    
    fun mapBundleToMediaItem(bundle: TelegramMessageBundle): TelegramMediaItem?
    
    fun selectBestVideo(videos: List<TgContent.Video>): TgContent.Video
    
    fun selectBestPhoto(photos: List<TgContent.Photo>): TgContent.Photo
}
```

---

## 6. Implementierungsplan

### Phase 1: Core-Model-Erweiterungen (Priorität: HOCH)

- [ ] **1.1** RawMediaMetadata um `ageRating: Int?` erweitern
- [ ] **1.2** RawMediaMetadata um `rating: Double?` erweitern  
- [ ] **1.3** ExternalIds prüfen/erweitern für tmdbId-String
- [ ] **1.4** Unit-Tests für RawMediaMetadata-Erweiterungen

**Geschätzter Aufwand:** 2-4 Stunden

### Phase 2: TelegramMediaItem-Erweiterungen (Priorität: HOCH)

- [ ] **2.1** TelegramMediaItem um Structured-Bundle-Felder erweitern
- [ ] **2.2** TelegramBundleType Enum erstellen
- [ ] **2.3** toRawMediaMetadata() erweitern für neue Felder
- [ ] **2.4** Unit-Tests für TelegramMediaItem-Erweiterungen

**Geschätzter Aufwand:** 4-6 Stunden

### Phase 3: Message Bundler (Priorität: MITTEL)

- [ ] **3.1** TelegramMessageBundle data class erstellen
- [ ] **3.2** TelegramMessageBundler implementieren
  - [ ] `groupByTimestamp()` mit Timestamp-Gruppierung
  - [ ] `classifyBundle()` mit Content-Type-Analyse
- [ ] **3.3** Unit-Tests mit echten JSON-Fixtures aus `/legacy/docs/telegram/exports/`
- [ ] **3.4** Edge-Cases: Einzelne Messages, unvollständige Bundles

**Geschätzter Aufwand:** 6-8 Stunden

### Phase 4: Structured Metadata Extractor (Priorität: MITTEL)

- [ ] **4.1** TelegramStructuredMetadataExtractor implementieren
  - [ ] `hasStructuredFields()` mit Field-Detection
  - [ ] `extractStructuredMetadata()` mit JSON-Parsing
  - [ ] `extractTmdbIdFromUrl()` mit Regex
- [ ] **4.2** StructuredMetadata data class erstellen
- [ ] **4.3** Unit-Tests mit echten TEXT-Messages aus JSON-Exports

**Geschätzter Aufwand:** 4-6 Stunden

### Phase 5: Bundle-to-MediaItem-Mapper (Priorität: MITTEL)

- [ ] **5.1** TelegramBundleToMediaItemMapper implementieren
  - [ ] `mapBundleToMediaItem()` mit Feld-Zusammenführung
  - [ ] `selectBestVideo()` mit Tie-Breaker
  - [ ] `selectBestPhoto()` mit Auflösungs-Priorisierung
- [ ] **5.2** Integration in TelegramPipelineAdapter
- [ ] **5.3** Unit-Tests für Mapping und Tie-Breaker

**Geschätzter Aufwand:** 6-8 Stunden

### Phase 6: Pipeline-Integration (Priorität: HOCH)

- [ ] **6.1** TelegramPipelineAdapter erweitern
  - [ ] `fetchMediaMessages()` mit Bundle-Gruppierung
  - [ ] Fallback auf Single-Message-Pfad
- [ ] **6.2** TelegramCatalogPipelineImpl anpassen
  - [ ] Bundle-aware Iteration
  - [ ] Logging für Bundle-Statistiken
- [ ] **6.3** Integration-Tests mit echten Chat-Exports

**Geschätzter Aufwand:** 8-10 Stunden

### Phase 7: Normalizer-Optimierung (Priorität: NIEDRIG)

- [ ] **7.1** metadata-normalizer: TMDB-ID-Check vor Lookup
  - [ ] Wenn `externalIds.tmdbId` vorhanden → Skip Search
  - [ ] Direkt TMDB-Details-API (wenn nötig) statt Search
- [ ] **7.2** Performance-Tests: Structured vs Unstructured Chats

**Geschätzter Aufwand:** 4-6 Stunden

### Phase 8: Dokumentation & Cleanup (Priorität: NIEDRIG)

- [ ] **8.1** Contract-Dokument finalisieren
- [ ] **8.2** README-Updates für betroffene Module
- [ ] **8.3** CHANGELOG.md aktualisieren
- [ ] **8.4** Gold-Folder um Structured-Bundle-Patterns erweitern

**Geschätzter Aufwand:** 2-4 Stunden

---

## 7. Test-Strategie

### 7.1 Unit-Tests

**TelegramMessageBundler:**

- Test: Nachrichten mit gleichem Timestamp werden gruppiert
- Test: Nachrichten mit unterschiedlichen Timestamps bleiben getrennt
- Test: Bundle-Klassifikation (3er, 2er, Single)
- Test: Sortierung innerhalb Bundle nach messageId

**TelegramStructuredMetadataExtractor:**

- Test: TMDB-URL-Parsing (verschiedene Formate)
- Test: FSK-Extraktion (numerisch)
- Test: Genre-Liste-Parsing
- Test: Fehlende Felder → null

**TelegramBundleToMediaItemMapper:**

- Test: Vollständiger 3er-Bundle → TelegramMediaItem
- Test: 2er-Bundle (TEXT+VIDEO) → TelegramMediaItem
- Test: Tie-Breaker bei mehreren Videos

### 7.2 Integration-Tests

**Fixtures:** Echte JSON-Exports aus `/legacy/docs/telegram/exports/exports/`

- Test: Chat mit 3er-Clustern (z.B. "Mel Brooks")
- Test: Chat mit 2er-Clustern (z.B. "Filme kompakt")
- Test: Chat mit gemischten Patterns
- Test: Chat ohne strukturierte Daten (Fallback)

### 7.3 Regressions-Tests

- Test: Bestehende `toRawMediaMetadata()` bleibt unverändert für Single-Messages
- Test: Keine Breaking Changes für unstrukturierte Chats

---

## 8. Risiken & Mitigationen

| Risiko | Wahrscheinlichkeit | Impact | Mitigation |
|--------|-------------------|--------|------------|
| Strukturierte Chats ändern Format | Mittel | Mittel | Feature-Flag für Bundle-Erkennung |
| TMDB-URL-Format ändert sich | Niedrig | Niedrig | Mehrere Regex-Patterns |
| Performance bei großen Chats | Niedrig | Mittel | Batch-Processing, Lazy Evaluation |
| Transport-Layer liefert andere Struktur | Niedrig | Hoch | Adapter-Pattern isoliert Pipeline |

---

## 9. Metriken & Erfolgskriterien

| Metrik | Ziel | Messmethode |
|--------|------|-------------|
| Structured-Chat-Erkennung | 100% der 8 bekannten Chats | Unit-Test-Coverage |
| TMDB-Lookups für Structured Chats | 0 | Logging-Counter |
| Ingest-Zeit pro strukturiertem Item | < 50ms | Performance-Logging |
| Regressions-Fehler | 0 | CI-Pipeline |

---

## 10. Referenzen

- [MEDIA_NORMALIZATION_CONTRACT.md](docs/v2/MEDIA_NORMALIZATION_CONTRACT.md)
- [TELEGRAM_PARSER_CONTRACT.md](contracts/TELEGRAM_PARSER_CONTRACT.md)
- [GOLD_TELEGRAM_CORE.md](legacy/gold/telegram-pipeline/GOLD_TELEGRAM_CORE.md)
- [GLOSSARY_v2_naming_and_modules.md](contracts/GLOSSARY_v2_naming_and_modules.md)
- [AGENTS.md](AGENTS.md) – Sections 4, 11, 15

---

## Anhang A: JSON-Nachrichtenbeispiele

### A.1 TEXT-Nachricht mit strukturierten Feldern

```json
{
  "id": 387973120,
  "chatId": -1001434421634,
  "date": 1731704712,
  "dateIso": "2024-11-15T19:45:12Z",
  "tmdbUrl": "https://www.themoviedb.org/movie/12345-movie-name",
  "tmdbRating": 7.5,
  "year": 2020,
  "originalTitle": "The Movie",
  "text": "🎬 The Movie (2020)\n⭐ 7.5/10",
  "genres": ["Action", "Drama"],
  "fsk": 12,
  "director": "John Doe",
  "lengthMinutes": 120,
  "productionCountry": "US"
}
```

### A.2 VIDEO-Nachricht im Bundle

```json
{
  "id": 388021760,
  "chatId": -1001434421634,
  "date": 1731704712,
  "dateIso": "2024-11-15T19:45:12Z",
  "content": {
    "type": "video",
    "duration": 7200,
    "width": 1920,
    "height": 1080,
    "fileName": "The.Movie.2020.1080p.BluRay.x264-GROUP.mkv",
    "mimeType": "video/x-matroska",
    "supportsStreaming": true,
    "file": {
      "id": 123456,
      "remoteId": "AgACAgQAAxkBAAIB...",
      "size": 5368709120
    }
  }
}
```

### A.3 PHOTO-Nachricht im Bundle

```json
{
  "id": 387924480,
  "chatId": -1001434421634,
  "date": 1731704712,
  "dateIso": "2024-11-15T19:45:12Z",
  "content": {
    "type": "photo",
    "sizes": [
      {
        "width": 90,
        "height": 135,
        "file": { "remoteId": "AgACAgQAAxk..." }
      },
      {
        "width": 320,
        "height": 480,
        "file": { "remoteId": "AgACAgQAAxk..." }
      },
      {
        "width": 1000,
        "height": 1500,
        "file": { "remoteId": "AgACAgQAAxk..." }
      }
    ]
  }
}
```

---

## Anhang B: Glossar

| Begriff | Definition |
|---------|------------|
| **Structured Bundle** | Gruppe von 2-3 Telegram-Nachrichten mit identischem Timestamp, die zusammen ein Media-Item beschreiben |
| **3er-Cluster** | Bundle aus PHOTO + TEXT + VIDEO |
| **2er-Cluster** | Bundle aus TEXT + VIDEO oder PHOTO + VIDEO |
| **Zero-Parsing-Path** | Pfad ohne Titel-Parsing dank strukturierter Metadaten |
| **Pass-Through** | TMDB-ID/Jahr/etc. werden unverändert von Quelle zu RawMediaMetadata übernommen |
| **Tie-Breaker** | Deterministische Regel zur Auswahl bei mehreren gleichwertigen Optionen |

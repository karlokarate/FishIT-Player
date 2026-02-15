# Pipeline Synchronisations-Status: Xtream vs. Telegram

> **Datum:** 2025-12-08 (Update: Phase 3+ TDLib Client Complete)  
> **Ziel:** Ausbaustand beider Pipelines synchron halten  
> **Status:** 🟢 PHASE 3+ COMPLETE – TDLib Client Implementation + Tests

---

## Executive Summary

| Aspekt | Telegram | Xtream | Delta |
|--------|----------|--------|-------|
| **Module** | `:pipeline:telegram` | `:pipeline:xtream` | ✅ Beide existieren |
| **Domain Models** | 6 DTOs + Types | 8 DTOs + 20 API Models | ✅ Pari |
| **Repository Interfaces** | 1 (TelegramContentRepository) | 2 (Catalog + Live) | ✅ Pari |
| **Playback Interface** | 1 (TelegramPlaybackSourceFactory) | 1 (XtreamPlaybackSourceFactory) | ✅ Pari |
| **Stub Impls** | 2 (ContentRepo + Playback) | 3 (Catalog + Live + Playback) | ✅ Pari |
| **API Client Abstraction** | ✅ 4 Files (Interface + Default + Provider + Mapper) | ✅ 5 Files (Interface + Default + Discovery + UrlBuilder + Models) | 🟢 PARI |
| **toRawMediaMetadata()** | ✅ Implementiert (TelegramRawMetadataExtensions.kt) | ✅ Implementiert (XtreamRawMetadataExtensions.kt) | 🟢 PARI |
| **Mapper Layer** | ✅ 3 (Mappers + Contract + TdlibMapper) | 1 (Extensions only) | ✅ |
| **Legacy Porting** | ✅ ~80% (TDLib Client Layer portiert) | ⚠️ ~60% (Client-Layer portiert) | 🟢 TELEGRAM VORAUS |
| **Real API Client** | ✅ DefaultTelegramClient (358 LOC) + TdlibMessageMapper (284 LOC) | ✅ DefaultXtreamApiClient (1100+ LOC) | 🟢 PARI |
| **UnifiedLog** | ✅ Implementiert | ✅ Implementiert | 🟢 PARI |
| **Tests** | ✅ 7 Testdateien | ✅ 6 Testdateien | 🟢 PARI |

---

## Phase 3+ Completion (2025-12-08)

### ✅ TDLib Client Implementation

Telegram Pipeline hat jetzt einen vollständigen TDLib-Client:

**Neue Dateien:**
- `DefaultTelegramClient.kt` (358 LOC) – Real TDLib implementation using g00sha AAR
- `TdlibMessageMapper.kt` (284 LOC) – Message → TelegramMediaItem conversion
- `TelegramClient.kt` (169 LOC) – Clean interface with Flow-based state
- `TdlibClientProvider.kt` (60 LOC) – Internal provider (⚠️ not exposed to upper layers)

**Features:**
- ✅ `ensureAuthorized()` – Auth state flow with retry
- ✅ `getChats(limit)` – Load available chats
- ✅ `fetchMediaMessages(chatId, limit, offset)` – Paginated message fetching
- ✅ `fetchAllMediaMessages(chatIds, limit)` – Multi-chat aggregation
- ✅ `resolveFileLocation(fileId)` – File metadata resolution
- ✅ `resolveFileByRemoteId(remoteId)` – Cross-session ID resolution
- ✅ `requestFileDownload(fileId, priority)` – Download initiation (metadata only)

**TdlibMessageMapper:**
- ✅ `toMediaItem(Message)` – Video/Document/Audio/Photo support
- ✅ Safe access helpers for g00sha DTOs (non-nullable fields)
- ✅ CONTRACT: Empty title for VIDEO/DOCUMENT/PHOTO (normalizer extracts)
- ✅ CONTRACT: Audio title = raw TDLib ID3 metadata

**Tests:**
- `TdlibMessageMapperTest.kt` (580 LOC) – 25+ test cases
- `DefaultTelegramClientTest.kt` (450 LOC) – 17 test cases

### ✅ toRawMediaMetadata() Extensions

Beide Pipelines haben jetzt `toRawMediaMetadata()` Extensions implementiert:

**Xtream:**
- `XtreamVodItem.toRawMediaMetadata()` → `MediaType.MOVIE`
- `XtreamSeriesItem.toRawMediaMetadata()` → `MediaType.SERIES_EPISODE`
- `XtreamEpisode.toRawMediaMetadata(seriesName)` → `MediaType.SERIES_EPISODE` mit season/episode
- `XtreamChannel.toRawMediaMetadata()` → `MediaType.LIVE`

**Telegram:**
- `TelegramMediaItem.toRawMediaMetadata()` → Title priority: title > episodeTitle > caption > fileName

### ✅ Contract Compliance

Beide Extensions erfüllen MEDIA_NORMALIZATION_CONTRACT.md:
- ✅ RAW metadata only (keine Cleaning/Normalization)
- ✅ ExternalIds leer (TMDB/IMDB erst nach Normalisierung)
- ✅ SourceType korrekt (XTREAM/TELEGRAM)
- ✅ Stable sourceId für Resume/Tracking

---

## 1. Detaillierter Struktur-Vergleich

### 1.1 Telegram Pipeline (`:pipeline:telegram`)

```
pipeline/telegram/src/main/java/com/fishit/player/pipeline/telegram/
├── mapper/
│   ├── TelegramMappers.kt              ✅ DTO-Konvertierungen (OBX → Domain)
│   ├── TdlibMessageMapper.kt           ✅ ⭐ NEU: TDLib Message → TelegramMediaItem
│   └── TelegramRawMetadataContract.kt  ✅ Contract-Dokumentation
├── model/
│   ├── TelegramChatSummary.kt          ✅ Chat-DTO
│   ├── TelegramMediaItem.kt            ✅ Haupt-Media-DTO
│   ├── TelegramMediaType.kt            ✅ Typ-Enum
│   ├── TelegramMessageStub.kt          ✅ Nachricht-DTO
│   ├── TelegramMetadataMessage.kt      ✅ Metadata-DTO
│   ├── TelegramPhotoSize.kt            ✅ Photo-DTO
│   └── TelegramRawMetadataExtensions.kt ✅ toRawMediaMetadata()
├── repository/
│   ├── TelegramContentRepository.kt    ✅ Interface (~110 Zeilen)
│   ├── TdlibTelegramContentRepository.kt ✅ Implementation mit UnifiedLog
│   └── TelegramPlaybackSourceFactory.kt  ✅ Interface (~45 Zeilen)
├── stub/
│   ├── StubTelegramContentRepository.kt   ✅ Test-Stub
│   └── StubTelegramPlaybackSourceFactory.kt ✅ Test-Stub
└── tdlib/
    ├── TdlibClientProvider.kt          ✅ Internal provider (not exported)
    ├── TelegramClient.kt               ✅ ⭐ NEU: Clean Interface + DTOs
    └── DefaultTelegramClient.kt        ✅ ⭐ NEU: Real TDLib Implementation (358 LOC)

pipeline/telegram/src/test/java/com/fishit/player/pipeline/telegram/
├── mapper/
│   ├── TelegramMappersTest.kt              ✅
│   └── TdlibMessageMapperTest.kt           ✅ ⭐ NEU: 25+ test cases
├── model/
│   └── TelegramRawMetadataExtensionsTest.kt ✅
├── stub/
│   ├── StubTelegramContentRepositoryTest.kt     ✅
│   └── StubTelegramPlaybackSourceFactoryTest.kt ✅
└── tdlib/
    └── DefaultTelegramClientTest.kt        ✅ ⭐ NEU: 17 test cases
```

**Dateien gesamt:** 20 Kotlin-Dateien (+2)  
**Tests:** 7 Test-Dateien (+2)

### 1.2 Xtream Pipeline (`:pipeline:xtream`)

```
pipeline/xtream/src/main/java/com/fishit/player/pipeline/xtream/
├── client/                             ✅ API Client Layer
│   ├── XtreamApiClient.kt              ✅ Interface (~320 Zeilen)
│   ├── XtreamApiModels.kt              ✅ DTOs (~680 Zeilen)
│   ├── DefaultXtreamApiClient.kt       ✅ Implementation (~1100 Zeilen) mit UnifiedLog
│   ├── XtreamUrlBuilder.kt             ✅ URL Factory (~350 Zeilen)
│   └── XtreamDiscovery.kt              ✅ Port/Capability Discovery (~380 Zeilen)
├── model/
│   ├── XtreamChannel.kt                ✅ Live-Channel-DTO
│   ├── XtreamEpgEntry.kt               ✅ EPG-DTO
│   ├── XtreamEpisode.kt                ✅ Episode-DTO
│   ├── XtreamNormalizationExtensions.kt ✅ Normalization helpers
│   ├── XtreamPlaybackExtensions.kt     ✅ Playback-Helpers
│   ├── XtreamRawMetadataExtensions.kt  ✅ toRawMediaMetadata() ⭐ NEU
│   ├── XtreamSearchResult.kt           ✅ Such-DTO
│   ├── XtreamSeriesItem.kt             ✅ Serien-DTO
│   └── XtreamVodItem.kt                ✅ VOD-DTO
├── playback/
│   ├── XtreamPlaybackSourceFactory.kt  ✅ Interface (~55 Zeilen)
│   └── stub/
│       └── StubXtreamPlaybackSourceFactory.kt ✅ Test-Stub
└── repository/
    ├── XtreamCatalogRepository.kt      ✅ Interface (~85 Zeilen)
    ├── XtreamLiveRepository.kt         ✅ Interface (~65 Zeilen)
    └── stub/
        ├── StubXtreamCatalogRepository.kt ✅ Test-Stub
        └── StubXtreamLiveRepository.kt    ✅ Test-Stub

pipeline/xtream/src/test/java/com/fishit/player/pipeline/xtream/
├── client/
│   ├── DefaultXtreamApiClientTest.kt       ✅ (~400 Zeilen)
│   ├── XtreamApiClientIntegrationTest.kt   ✅
│   ├── XtreamApiModelsTest.kt              ✅
│   ├── XtreamUrlBuilderTest.kt             ✅ (~300 Zeilen)
│   └── XtreamDiscoveryTest.kt              ✅ (~280 Zeilen)
└── model/
    └── XtreamRawMetadataExtensionsTest.kt  ✅ NEU (~120 Zeilen)
```

**Dateien gesamt:** 21 Kotlin-Dateien  
**Tests:** 6 Test-Dateien

---

## 2. Feature-Parität-Matrix

### 2.1 Domain Models ✅ PARI

| Feature | Telegram | Xtream |
|---------|----------|--------|
| Haupt-Content-DTO | TelegramMediaItem | XtreamVodItem, XtreamSeriesItem |
| Episode-DTO | (in MediaItem) | XtreamEpisode |
| Live/Channel-DTO | – | XtreamChannel |
| EPG-DTO | – | XtreamEpgEntry |
| Such-DTO | (in MediaItem) | XtreamSearchResult |
| Container-DTO | TelegramChatSummary | – (Kategorien in v1) |
| Typ-Enum | TelegramMediaType | – (implizit) |
| Photo/Thumb-DTO | TelegramPhotoSize | – (in Models) |

### 2.2 Repository Layer ✅ PARI

| Feature | Telegram | Xtream |
|---------|----------|--------|
| Content Repository | TelegramContentRepository | XtreamCatalogRepository |
| Live Repository | – | XtreamLiveRepository |
| Playback Factory | TelegramPlaybackSourceFactory | XtreamPlaybackSourceFactory |
| Stub Implementations | 2 Stubs | 3 Stubs |

### 2.3 API Client Layer ✅ SYNCHRONISIERT

| Feature | Telegram | Xtream |
|---------|----------|--------|
| Client Interface | TelegramClient ✅ | XtreamApiClient ✅ |
| Client Implementation | DefaultTelegramClient ✅ (358 LOC) | DefaultXtreamApiClient ✅ (1100 LOC) |
| Message Mapper | TdlibMessageMapper ✅ (284 LOC) | – (in Extensions) |
| Auth State Flow | ✅ TelegramAuthState sealed class | ✅ Definiert |
| Connection State Flow | ✅ TelegramConnectionState sealed class | ✅ Definiert |
| Provider Abstraction | TdlibClientProvider ✅ (internal only) | – (stateless) |
| URL Builder | – (in TDLib) | XtreamUrlBuilder ✅ |
| Discovery/Port Resolution | – (in TDLib) | XtreamDiscovery ✅ |
| Rate Limiting | ✅ (in TDLib) | ✅ (120ms per-host) |
| Response Cache | – | ✅ (60s/15s TTL) |
| Retry Logic | ✅ withRetry() (3 attempts) | ✅ |
| g00sha TDLib AAR | ✅ dev.g000sha256:tdl-coroutines-android:5.0.0 | – |

### 2.4 Normalization Integration 🟢 BEIDE PARI

| Feature | Telegram | Xtream |
|---------|----------|--------|
| toRawMediaMetadata() | ⚠️ Nur Contract-Doku | ✅ Implementiert (4 Extensions) |
| RawMediaMetadata Import | ❌ | ✅ |
| SourceType.XTREAM/TELEGRAM | ❌ | ✅ |
| ExternalIds Support | ❌ | ✅ (prepared) |

### 2.5 Mapper Layer 🟡 TELEGRAM VORAUS

| Feature | Telegram | Xtream |
|---------|----------|--------|
| DTO-Mappers | TelegramMappers.kt ✅ | ❌ FEHLT |
| Contract Documentation | TelegramRawMetadataContract.kt ✅ | – (in Extensions) |

### 2.6 Tests ✅ SYNCHRONISIERT

| Feature | Telegram | Xtream |
|---------|----------|--------|
| Model Tests | TelegramDtosTest ✅ | – (Models einfach) |
| Mapper Tests | TelegramMappersTest ✅ | – (Extensions direkt) |
| Stub Tests | StubTelegramContentRepositoryTest ✅ | – (Stubs simpel) |
| Stub Tests | StubTelegramPlaybackSourceFactoryTest ✅ | – |
| API Client Tests | – (TDLib extern) | DefaultXtreamApiClientTest ✅ |
| URL Builder Tests | – | XtreamUrlBuilderTest ✅ |
| Discovery Tests | – | XtreamDiscoveryTest ✅ |

---

## 3. Legacy v1 Portierungs-Status

### 3.1 Telegram v1 Legacy (~81 Dateien in app/telegram/)

| Komponente | Zeilen (ca.) | v2 Status | Priorität |
|------------|--------------|-----------|-----------|
| **Core** | | | |
| T_TelegramServiceClient | ~800 | ✅ **Portiert** → DefaultTelegramClient | P1 |
| T_TelegramSession | ~400 | ✅ **Portiert** → TelegramClient auth state | P1 |
| T_ChatBrowser | ~500 | ✅ **Portiert** → fetchMediaMessages() | P1 |
| T_TelegramFileDownloader | ~600 | 🟡 Teilweise → requestFileDownload() | P2 |
| TelegramFileLoader | ~300 | 🔴 Nicht portiert (gehört in :player:internal) | P2 |
| StreamingConfig | ~150 | 🔴 Nicht portiert (gehört in :player:internal) | P2 |
| **Ingestion** | | | |
| TelegramHistoryScanner | ~400 | 🟡 Basis in fetchMediaMessages() | P2 |
| TelegramIngestionCoordinator | ~300 | 🔴 Nicht portiert | P2 |
| TelegramUpdateHandler | ~250 | 🔴 Nicht portiert | P2 |
| **Parser** | | | |
| MediaParser | ~400 | ✅ **Portiert** → TdlibMessageMapper | P2 |
| TelegramMetadataExtractor | ~350 | ✅ **Portiert** → TdlibMessageMapper | P2 |
| TgContentHeuristics | ~200 | 🔴 Gehört in :core:metadata-normalizer | P3 |
| AdultHeuristics | ~150 | 🔴 Gehört in :core:metadata-normalizer | P3 |

**Telegram Legacy Status:** ~3.000 Zeilen portiert (Core + Parser), ~1.500 Zeilen verbleibend

### 3.2 Xtream v1 Legacy (~5.400 Zeilen in app/core/xtream/)

| Komponente | Zeilen | v2 Status | Priorität |
|------------|--------|-----------|-----------|
| XtreamClient.kt | 903 | ✅ **Portiert** → DefaultXtreamApiClient | P1 |
| XtreamConfig.kt | 400 | ✅ **Portiert** → XtreamUrlBuilder | P1 |
| XtreamModels.kt | 206 | ✅ **Portiert** → XtreamApiModels | P1 |
| XtreamCapabilities.kt | 630 | ✅ **Portiert** → XtreamDiscovery | P1 |
| XtreamDetect.kt | 118 | 🟡 Teilweise in XtreamDiscovery | P1 |
| XtreamSeeder.kt | 147 | 🔴 Nicht portiert | P2 |
| XtreamImportCoordinator.kt | 48 | 🔴 Nicht portiert | P2 |
| ProviderLabelStore.kt | 106 | 🔴 Nicht portiert | P2 |
| XtreamObxRepository.kt | 2829 | 🔴 Nicht portiert | P1 |

**Xtream Legacy Status:** ~2.400 Zeilen portiert (Client Layer), ~3.000 Zeilen verbleibend

---

## 4. Synchronisierungs-Aufgaben

### 4.1 ✅ Xtream aufholen zu Telegram – ABGESCHLOSSEN

| Priorität | Aufgabe | Aufwand | Status |
|-----------|---------|---------|--------|
| **P1** | XtreamApiClient Interface + Default Impl | 4h | ✅ DONE |
| **P1** | Auth/Connection State Flows | 1h | ✅ DONE |
| **P1** | XtreamUrlBuilder (aus XtreamConfig) | 2h | ✅ DONE |
| **P1** | XtreamDiscovery (aus XtreamCapabilities) | 2h | ✅ DONE |
| **P1** | Unit Tests für Client/Builder/Discovery | 2h | ✅ DONE |

### 4.2 Telegram aufholen zu Xtream

| Priorität | Aufgabe | Aufwand | Status |
|-----------|---------|---------|--------|
| **P1** | toRawMediaMetadata() implementieren | 1h | ⏳ |
| **P2** | SourceType.TELEGRAM verwenden | 30min | ⏳ |

### 4.3 Gemeinsam nächste Phase (Legacy Porting)

| Priorität | Aufgabe | Aufwand | Status |
|-----------|---------|---------|--------|
| **P1** | Xtream: XtreamClient portieren | 3h | ⏳ |
| **P1** | Xtream: XtreamConfig portieren | 1h | ⏳ |
| **P1** | Telegram: T_TelegramServiceClient portieren | 4h | ⏳ |
| **P1** | Telegram: T_TelegramSession portieren | 2h | ⏳ |

---

## 5. Empfohlene nächste Schritte (Priorisiert)

### PHASE A: Structure Sync (1-2h)

1. **Xtream: XtreamApiClient Interface erstellen**
   - Analog zu `TelegramTdlibClient`
   - Credential-Management
   - Connection State Flow

2. **Xtream: Unit Tests hinzufügen**
   - DTOs testen
   - Stubs testen
   - Normalization Extensions testen

### PHASE B: Contract Compliance (1h)

1. **Telegram: toRawMediaMetadata() implementieren**
   - Aktuell nur Contract-Dokumentation
   - Echte Extension-Funktionen erstellen

### PHASE C: Legacy Porting Start (6-8h)

1. **Parallel: Beide API-Clients portieren**
   - Xtream: `XtreamClient.kt` → `:pipeline:xtream`
   - Telegram: `T_TelegramServiceClient` → `:pipeline:telegram`

2. **Parallel: Config/Session portieren**
   - Xtream: `XtreamConfig.kt`, `XtreamCapabilities.kt`
   - Telegram: `T_TelegramSession`, `StreamingConfig`

---

## 6. Architektur-Unterschiede

### 6.1 API-Client-Modell

**Telegram:**
- TDLib als externe Bibliothek (g00sha/tdlib-coroutines)
- Session-basiert mit komplexem Auth-Flow
- Push-basierte Updates via Flow

**Xtream:**
- REST-API direkt gegen Panel
- Stateless (Credentials pro Request)
- Pull-basierte Daten

### 6.2 Content-Modell

**Telegram:**
- Nachrichten aus Chats
- Keine EPG-Daten
- Serien-Erkennung via Heuristik (S01E05)

**Xtream:**
- Katalog-basiert (VOD/Series/Live)
- EPG-Integration
- Strukturierte Serien/Episoden

### 6.3 Playback-Modell

**Telegram:**
- `tg://` URI-Schema
- TDLib-Cache-basiertes Streaming
- Zero-Copy via FileDataSource

**Xtream:**
- HTTP/HLS URLs
- Standard Media3 DataSources
- Token-basierte Auth

---

## 7. Fazit

| Metrik | Telegram | Xtream |
|--------|----------|--------|
| v2 Struktur | ✅ Gut | ✅ Gut |
| Tests | ✅ 7 Dateien | ✅ 6 Dateien |
| API Client | ✅ Interface + Real Impl (358 LOC) | ✅ Interface + Real Impl (1100 LOC) |
| Message Mapper | ✅ TdlibMessageMapper (284 LOC) | – (in Extensions) |
| Normalization | ✅ toRawMediaMetadata() | ✅ Implementiert |
| Legacy Portiert | ✅ ~80% (Core + Parser) | ⚠️ ~60% (Client-Layer) |

**Status:** 🟢 BEIDE PIPELINES SYNCHRONISIERT

**Nächste Schritte:**
1. Telegram: TelegramContentRepository mit DefaultTelegramClient verbinden
2. Xtream: Repository mit DefaultXtreamApiClient verbinden
3. Beide: Integration Tests mit echten API-Aufrufen

---

## Changelog

- **2025-12-08:** Phase 3+ Complete – TDLib Client Implementation
  - DefaultTelegramClient (358 LOC) mit g00sha AAR
  - TdlibMessageMapper (284 LOC) für Message → TelegramMediaItem
  - TdlibMessageMapperTest (580 LOC) mit 25+ tests
  - DefaultTelegramClientTest (450 LOC) mit 17 tests
- **2025-12-07:** Initial-Vergleich erstellt, toRawMediaMetadata()

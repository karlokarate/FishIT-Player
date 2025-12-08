# FishIT Player v2 – Umfassende Architektur-Review

> **Datum:** 2025-12-07  
> **Branch:** `architecture/v2-bootstrap`  
> **Review-Typ:** Vollständige Bestandsaufnahme und Gap-Analyse

---

## Executive Summary

### 🎯 Kernbewertung

| Aspekt | Status | Bewertung |
|--------|--------|-----------|
| **Architektur-Design** | ✅ Exzellent | Saubere Layer-Trennung, klare Contracts |
| **Dokumentation** | ✅ Exzellent | 18 v2-Docs, 3 Haupt-Contracts |
| **Module-Struktur** | ✅ Vollständig | 17 Module definiert und kompilierbar |
| **Code-Umsetzung** | 🔴 Kritisch | Nur ~17% des benötigten Codes vorhanden |
| **Tier 1 Portierung** | 🔴 Kritisch | 0% von 6 production-ready Systemen portiert |
| **Player Implementation** | 🔴 Blocking | SIP Player (5000 LOC) nicht portiert |
| **Feature Screens** | 🔴 Blocking | Alle Feature-Module leer |

### 📊 Zahlen auf einen Blick

```
Geschätzte v2-Gesamt-LOC:     ~30.000 Zeilen
Aktuell vorhanden:             ~5.000 Zeilen (17%)
Davon produktiv nutzbar:         ~500 Zeilen (2%)

Portierbare v1-Systeme:       ~17.000 Zeilen
Davon portiert:                ~3.500 Zeilen (20%)
```

---

## 1. Wo stehen wir? – Detaillierter Status

### 1.1 Phase 0: Module Skeleton ✅ ABGESCHLOSSEN

#### ✅ Erfolge

- **17 v2-Module** in `settings.gradle.kts` definiert und kompilierbar:
  ```
  :app-v2
  :core:model, :core:persistence, :core:metadata-normalizer, 
  :core:firebase, :core:ui-imaging
  :playback:domain, :player:internal
  :pipeline:telegram, :pipeline:xtream, :pipeline:io, :pipeline:audiobook
  :feature:home, :feature:library, :feature:live, 
  :feature:telegram-media, :feature:audiobooks, :feature:settings
  :infra:logging, :infra:tooling
  ```

- **app-v2** kompiliert mit:
  - Hilt DI (Application + Activity)
  - Jetpack Compose + Navigation
  - Material3 Theme
  - DebugSkeletonScreen als Startpunkt
  - Korrekte Dependencies auf alle v2-Module

- **Package-Struktur** konsistent: `com.fishit.player.*` (nicht `com.chris.m3usuite.*`)

#### ❌ Unvollständig

- **AppShell fehlt**:
  - Keine `FeatureRegistry` Implementation
  - Keine Startup-Sequence (DeviceProfile, Profile Loading, Pipeline Init)
  - Keine echte Navigation zu Feature-Screens
  - Nur DebugSkeletonScreen mit Dummy-Text

- **DI-Wiring unvollständig**:
  - Keine Hilt-Module für Pipeline-Factories
  - Keine Bindings für Repository-Interfaces
  - Keine Konfiguration für PlaybackSourceResolver

**Bewertung:** 🟡 **60% abgeschlossen** – Struktur steht, aber kein funktionales AppShell

---

### 1.2 core:model ✅ GUT

#### ✅ Erfolge

**48 Kotlin-Dateien** vorhanden, darunter:

- **Playback Models** (Phase 1):
  - `PlaybackContext.kt` – Typed session descriptor ✅
  - `PlaybackType.kt` – VOD/SERIES/LIVE/TELEGRAM/IO enum ✅
  - `ResumePoint.kt` – Resume metadata ✅

- **Metadata Models** (Phase 3A):
  - `RawMediaMetadata.kt` – Pipeline raw input ✅
  - `NormalizedMediaMetadata.kt` – Normalized output ✅
  - `CanonicalMediaId.kt` – Global identity ✅
  - `MediaType.kt` – Fine-grained type enum ✅
  - `MediaSourceRef.kt` – Multi-source references ✅
  - `ImageRef.kt` – Image reference model ✅

- **Repository Interfaces**:
  - `CanonicalMediaRepository` ✅
  - `ProfileRepository` ✅
  - `ResumeRepository` ✅
  - `ScreenTimeRepository` ✅
  - `ContentRepository` ✅

#### ❌ Fehlende Models

- `Profile`, `KidsProfileInfo`, `EntitlementState` (Phase 5)
- `FeatureId`, `FeatureDescriptor`, `DeviceProfile` (Phase 5)
- `SubtitleStyle` (Phase 1 - sollte in playback:domain sein)

**Bewertung:** 🟢 **80% abgeschlossen** – Kernmodelle vorhanden

---

### 1.3 core:metadata-normalizer ✅ SKELETON KOMPLETT

#### ✅ Erfolge (Phase 3A)

- **Module existiert** mit korrekter Gradle-Config
- **Interfaces definiert**:
  - `MediaMetadataNormalizer` ✅
  - `TmdbMetadataResolver` ✅
- **Default No-Op Implementierungen**:
  - `DefaultMediaMetadataNormalizer` (passthrough) ✅
  - `DefaultTmdbMetadataResolver` (passthrough) ✅
- **Tests vorhanden**:
  - `DefaultMediaMetadataNormalizerTest` (3 tests) ✅
  - `DefaultTmdbMetadataResolverTest` (3 tests) ✅
  - `RegexMediaMetadataNormalizerTest` (stub) ✅
- **TMDB Dependency**: `com.uwetrottmann.tmdb2:tmdb-java:2.11.0` ✅

#### ❌ Fehlende Implementation

- ❌ Keine echte Scene-Name-Parsing Logic (Sonarr/Radarr-Style)
- ❌ Keine Titel-Normalisierung (Strip tags, normalize whitespace)
- ❌ Keine TMDB-Integration (nur Dependency, keine API-Calls)
- ❌ Keine Determinismus-Tests für Parsing

**Bewertung:** 🟡 **30% abgeschlossen** – Skeleton gut, aber keine Business-Logic

---

### 1.4 pipeline:telegram 🟡 TEILWEISE PORTIERT

#### ✅ Erfolge (~80% Core portiert)

**20 Kotlin-Dateien** + **7 Test-Dateien**

**TDLib Integration** (Phase 2 + Phase 3+):
- `TelegramClient.kt` (169 LOC) – Clean interface mit Flow-based state ✅
- `DefaultTelegramClient.kt` (358 LOC) – Real TDLib impl mit g00sha AAR ✅
- `TdlibMessageMapper.kt` (284 LOC) – Message → TelegramMediaItem ✅
- `TdlibClientProvider.kt` (60 LOC) – Context-free abstraction ✅

**Domain Models**:
- `TelegramMediaItem.kt` ✅
- `TelegramChatSummary.kt` ✅
- `TelegramMediaType.kt` ✅
- `TelegramPhotoSize.kt` ✅

**Repository Interfaces**:
- `TelegramContentRepository` (~110 LOC) ✅
- `TdlibTelegramContentRepository` (Implementation mit UnifiedLog) ✅
- `TelegramPlaybackSourceFactory` (~45 LOC) ✅

**Mappers**:
- `TelegramMappers.kt` (OBX → Domain) ✅
- `TelegramRawMetadataExtensions.kt` (toRawMediaMetadata()) ✅

**Tests** (Phase 3+):
- `TdlibMessageMapperTest.kt` (580 LOC, 25+ tests) ✅
- `DefaultTelegramClientTest.kt` (450 LOC, 17 tests) ✅
- `TelegramMappersTest.kt` ✅
- `TelegramRawMetadataExtensionsTest.kt` ✅

#### ❌ Fehlende v1-Portierungen

**Aus v1 NICHT portiert** (~1500 LOC verbleibend):

| v1 Komponente | LOC | v2 Status | Priorität |
|---------------|-----|-----------|-----------|
| `T_TelegramFileDownloader` | 1621 | 🔴 FEHLT | P1 CRITICAL |
| `TelegramFileDataSource` | 413 | 🔴 FEHLT | P1 CRITICAL |
| `RarDataSource` | ~200 | 🔴 FEHLT | P2 |
| `Mp4HeaderParser` | ~100 | 🔴 FEHLT | P2 |
| `StreamingConfigRefactor` | ~150 | 🔴 FEHLT | P2 |

**⚠️ CRITICAL**: Ohne `TelegramFileDataSource` kann v2 KEINE Telegram-Videos abspielen!

**Bewertung:** 🟡 **60% abgeschlossen** – Core vorhanden, aber Player-Integration fehlt

---

### 1.5 pipeline:xtream 🟡 TEILWEISE PORTIERT

#### ✅ Erfolge (~60% Client Layer)

**21 Kotlin-Dateien** + **6 Test-Dateien**

**API Client Layer** (Phase 3B):
- `XtreamApiClient.kt` (320 LOC) – Interface ✅
- `DefaultXtreamApiClient.kt` (1100+ LOC) – Implementation mit UnifiedLog ✅
- `XtreamUrlBuilder.kt` (350 LOC) – URL Factory ✅
- `XtreamDiscovery.kt` (380 LOC) – Port/Capability Discovery ✅
- `XtreamApiModels.kt` (680 LOC) – 20+ DTOs ✅

**Domain Models**:
- `XtreamVodItem.kt`, `XtreamSeriesItem.kt`, `XtreamEpisode.kt` ✅
- `XtreamChannel.kt`, `XtreamEpgEntry.kt` ✅
- `XtreamSearchResult.kt` ✅

**Repository Interfaces**:
- `XtreamCatalogRepository` (85 LOC) ✅
- `XtreamLiveRepository` (65 LOC) ✅
- `XtreamPlaybackSourceFactory` (55 LOC) ✅

**Extensions**:
- `XtreamRawMetadataExtensions.kt` (toRawMediaMetadata() für VOD/Series/Live) ✅
- `XtreamPlaybackExtensions.kt` (toPlaybackContext()) ✅

**Tests**:
- `DefaultXtreamApiClientTest.kt` (400 LOC) ✅
- `XtreamUrlBuilderTest.kt` (300 LOC) ✅
- `XtreamDiscoveryTest.kt` (280 LOC) ✅
- `XtreamRawMetadataExtensionsTest.kt` (120 LOC) ✅

#### ❌ Fehlende v1-Portierungen

**Aus v1 NICHT portiert** (~3000 LOC verbleibend):

| v1 Komponente | LOC | v2 Status | Priorität |
|---------------|-----|-----------|-----------|
| `XtreamObxRepository` | 2829 | 🔴 FEHLT | P1 CRITICAL |
| `XtreamSeeder` | 147 | 🔴 FEHLT | P2 |
| `DelegatingDataSourceFactory` | ~200 | 🔴 FEHLT | P1 |
| `ProviderLabelStore` | 106 | 🔴 FEHLT | P2 |

**⚠️ CRITICAL**: Ohne `XtreamObxRepository` keine Content-Queries!

**Bewertung:** 🟡 **50% abgeschlossen** – Client gut, aber Persistence fehlt

---

### 1.6 pipeline:io ✅ SKELETON KOMPLETT

- **14 Kotlin-Dateien** + **5 Test-Dateien**
- Interfaces: `IoContentRepository`, `IoPlaybackSourceFactory` ✅
- Stub-Implementierungen mit Tests ✅
- `IoMediaItem.toRawMediaMetadata()` ✅

**Bewertung:** 🟢 **100% Skeleton** – Bereit für Phase 4

---

### 1.7 pipeline:audiobook 🔴 NUR PACKAGE-INFO

- **1 Kotlin-Datei**: `package-info.kt`
- Keine Interfaces, keine Models, keine Tests

**Bewertung:** 🔴 **5% abgeschlossen** – Nur Package-Marker

---

### 1.8 playback:domain 🔴 LEER

#### ❌ Status: KOMPLETT FEHLT

**Module existiert**, aber:
- ❌ Keine Interfaces für ResumeManager, KidsPlaybackGate, SubtitleStyleManager
- ❌ Keine Default-Implementierungen
- ❌ Keine Tests

**Erwartet aus Phase 1**:
```kotlin
// Alle Interfaces fehlen:
interface ResumeManager
interface KidsPlaybackGate
interface SubtitleStyleManager
interface SubtitleSelectionPolicy
interface LivePlaybackController
interface TvInputController

// Alle Default-Implementierungen fehlen:
class DefaultResumeManager
class DefaultKidsPlaybackGate
// etc.
```

**⚠️ BLOCKING**: Ohne playback:domain kann player:internal NICHT gebaut werden!

**Bewertung:** 🔴 **0% abgeschlossen**

---

### 1.9 player:internal 🔴 KOMPLETT LEER

#### ❌ Status: CRITICAL BLOCKER

**Module existiert**, aber:
- ❌ Keine Dateien vorhanden (außer build.gradle.kts)
- ❌ SIP Player aus v1 NICHT portiert

**v1 hat production-ready SIP** (~5000 LOC, 9 Phasen):
```
v1 player/internal/
├── domain/                     → :playback:domain ❌ NICHT PORTIERT
│   ├── PlaybackContext.kt      (in core:model ✅)
│   ├── ResumeManager.kt        ❌ FEHLT
│   ├── KidsPlaybackGate.kt     ❌ FEHLT
├── state/
│   └── InternalPlayerState.kt  ❌ NICHT PORTIERT
├── session/
│   └── InternalPlayerSession.kt (1393 LOC) ❌ NICHT PORTIERT
├── source/
│   └── PlaybackSourceResolver.kt ❌ NICHT PORTIERT
├── live/
│   ├── LivePlaybackController.kt ❌ NICHT PORTIERT
│   └── 4 weitere Dateien        ❌ NICHT PORTIERT
├── subtitles/
│   ├── SubtitleStyleManager.kt  ❌ NICHT PORTIERT
│   └── 2 weitere Dateien        ❌ NICHT PORTIERT
├── ui/
│   ├── InternalPlayerControls.kt ❌ NICHT PORTIERT
│   ├── PlayerSurface.kt          ❌ NICHT PORTIERT
│   └── CcMenuDialog.kt           ❌ NICHT PORTIERT
└── system/
    └── InternalPlayerSystemUi.kt ❌ NICHT PORTIERT
```

**SIP Player = ~37 Dateien, ~5000 LOC, 150+ Tests**

**⚠️ ABSOLUTE PRIORITY**: Ohne SIP kann v2 KEIN Media abspielen!

**Bewertung:** 🔴 **0% abgeschlossen** – CRITICAL BLOCKER

---

### 1.10 feature:* Module 🔴 ALLE LEER

#### Status: Alle 6 Feature-Module leer

| Module | Erwartet | Vorhanden | Status |
|--------|----------|-----------|--------|
| `feature:home` | HomeScreen, ViewModel | 0 Dateien | 🔴 0% |
| `feature:library` | LibraryScreen, ViewModel | 0 Dateien | 🔴 0% |
| `feature:live` | LiveScreen, EPG-UI | 0 Dateien | 🔴 0% |
| `feature:telegram-media` | TelegramScreen, Chat-Browser | 0 Dateien | 🔴 0% |
| `feature:audiobooks` | AudiobookScreen | 0 Dateien | 🔴 0% |
| `feature:settings` | SettingsScreen, Profile-UI | 0 Dateien | 🔴 0% |

**Bewertung:** 🔴 **0% abgeschlossen** – Keine UI-Screens

---

### 1.11 infra:logging 🔴 NICHT PORTIERT

#### ❌ Status: v1 UnifiedLog NICHT portiert

**v1 hat production-ready UnifiedLog** (578 LOC):
```kotlin
// v1: core/logging/UnifiedLog.kt
object UnifiedLog {
    // Ring buffer mit 1000 entries
    private val ringBuffer = ArrayDeque<Entry>(MAX_ENTRIES)
    
    // StateFlows für UI observation
    val entries: StateFlow<List<Entry>>
    val events: SharedFlow<Entry>
    
    // Log levels: VERBOSE, DEBUG, INFO, WARN, ERROR
    enum class Level { ... }
    
    // Kategorien für Filtering
    enum class SourceCategory {
        PLAYBACK, TELEGRAM_DOWNLOAD, TELEGRAM_AUTH, ...
    }
    
    // File export
    fun enableFileBuffer()
    fun exportSession(): File
    
    // Firebase Crashlytics integration
}
```

**Plus**: LogViewer UI (LogViewerScreen.kt, LogViewerViewModel.kt)

**v2 infra:logging**: 🔴 **LEER** (nur build.gradle.kts)

**⚠️ CRITICAL**: Ohne UnifiedLog keine strukturierte Diagnostik!

**Bewertung:** 🔴 **0% abgeschlossen**

---

### 1.12 infra:tooling 🔴 LEER

- Nur build.gradle.kts vorhanden
- Keine detekt/ktlint Config
- Keine ArchUnit Tests

**Bewertung:** 🔴 **0% abgeschlossen**

---

### 1.13 core:persistence 🔴 KEINE OBJECTBOX ENTITIES

#### ❌ Status: ObjectBox NICHT integriert

**v1 hat**: `data/obx/ObxEntities.kt` mit 10 Entities:
```kotlin
@Entity data class ObxCategory(...)
@Entity data class ObxLive(...)
@Entity data class ObxVod(...)
@Entity data class ObxSeries(...)
@Entity data class ObxEpisode(...)
@Entity data class ObxEpgNowNext(...)
@Entity data class ObxProfile(...)
@Entity data class ObxProfilePermissions(...)
@Entity data class ObxResumeMark(...)
@Entity data class ObxTelegramMessage(...)
```

**v2 core:persistence**: 
- ✅ Repository-Interfaces definiert
- ✅ Einige Canonical Media Entities vorhanden
- ❌ **Keine v1 ObxEntities portiert**
- ❌ Kein ObxStore-Singleton Pattern
- ❌ Keine Repository-Implementierungen

**⚠️ CRITICAL**: Ohne ObjectBox keine Content-Persistence!

**Bewertung:** 🔴 **20% abgeschlossen**

---

## 2. Was lief bisher falsch? – Fehleranalyse

### 2.1 🔥 HAUPTPROBLEM: "Skeleton First" statt "Port First"

#### Analyse

Die v2-Entwicklung folgte einem **"Skeleton First"**-Ansatz:
1. Module-Struktur aufbauen ✅
2. Interfaces definieren ✅
3. Dummy-Implementierungen erstellen ✅
4. Erst dann v1-Code portieren ❌ **HIER STECKEN GEBLIEBEN**

**Problem**: Nach Phase 0-3A (~4 Wochen?) haben wir:
- ✅ Saubere Architektur
- ✅ Kompilierende Module
- ❌ **ABER**: Keine funktionale App, weil kritische v1-Komponenten fehlen

#### Was wäre besser gewesen?

**"Port First"**-Ansatz nach `V1_VS_V2_ANALYSIS_REPORT.md`:

1. **Tier 1 Systeme sofort portieren** (0 Änderungen):
   - SIP Player (5000 LOC) → player:internal
   - UnifiedLog (578 LOC) → infra:logging
   - FocusKit (1353 LOC) → ui:focus oder player:internal
   - Fish* Layout (2000 LOC) → ui:layout
   - Xtream Pipeline (3000 LOC) → pipeline:xtream
   - AppImageLoader (153 LOC) → core:ui-imaging

2. **Tier 2 Systeme mit Minor Adapt**:
   - PlaybackSession (588 LOC)
   - DetailScaffold, MediaActionBar, TvButtons

**Resultat**: Nach ~2 Wochen hätten wir ~12.000 LOC production-ready Code und eine **funktionierende App**.

---

### 2.2 ❌ Fehler: Tier 1 Systeme ignoriert

#### Problem

`V1_VS_V2_ANALYSIS_REPORT.md` existiert seit 2025-12-04 und listet:

**Tier 1: Port Directly (Zero Changes Needed)**
- ⭐⭐⭐⭐⭐ SIP Player (Phase 1-8)
- ⭐⭐⭐⭐⭐ UnifiedLog
- ⭐⭐⭐⭐⭐ FocusKit
- ⭐⭐⭐⭐⭐ Fish* Layout System
- ⭐⭐⭐⭐⭐ Xtream Pipeline
- ⭐⭐⭐⭐⭐ AppImageLoader

**Status**: 🔴 **0 von 6 Tier 1 Systemen portiert**

#### Warum ignoriert?

Vermutlich:
1. Report erstellt, aber nicht in Implementierung eingeflossen
2. Fokus auf "neue Architektur" statt "Portierung"
3. Unterschätzung des Umfangs (SIP = 5000 LOC alleine)

#### Impact

Ohne Tier 1 Portierung:
- ❌ Kein funktionaler Player
- ❌ Keine strukturierte Logs
- ❌ Keine TV-Focus-Navigation
- ❌ Keine UI-Komponenten
- ❌ Keine Xtream-Content-Queries

**→ v2 ist unbenutzbar, obwohl v1 production-ready Code hat**

---

### 2.3 ❌ Fehler: Phase 1 übersprungen

#### Was Phase 1 erwartet

Aus `IMPLEMENTATION_PHASES_V2.md`:

```
## Phase 1 – Playback Core & Internal Player Bootstrap

Goal: Get the v2 internal player (SIP) running in isolation 
      using a simple HTTP test stream, without pipeline integration.

Checklist:
- [ ] In :playback:domain:
  - [ ] Declare interfaces:
    - ResumeManager, KidsPlaybackGate, SubtitleStyleManager, ...
  - [ ] Provide simple default implementations
- [ ] In :player:internal:
  - [ ] Port and adapt the v2 SIP code from the existing repo:
    - InternalPlayerState, InternalPlayerSession
    - InternalPlayerControls + PlayerSurface Composables
- [ ] In :feature:home:
  - [ ] Add a DebugPlaybackScreen
```

**Status**: 🔴 **Phase 1 komplett übersprungen**

- playback:domain: LEER
- player:internal: LEER
- feature:home: LEER (nur in app-v2 Debug-Screen)

#### Impact

Phase 2 (Telegram) und Phase 3 (Xtream) **bauen auf Phase 1 auf**:
- Ohne Player können Pipelines nicht testen
- Ohne Playback-Domain keine ResumeManager/KidsGate
- Ohne Home-Screen keine Navigation

**→ Alle weiteren Phasen blockiert**

---

### 2.4 ❌ Fehler: Zu viele Parallel-Fronten

#### Beobachtung

Statt Phase-für-Phase wurden gleichzeitig angegangen:
- Phase 0: Module Skeleton ✅
- Phase 2: Telegram Pipeline (Partial) 🟡
- Phase 3A: Metadata Normalizer (Skeleton) ✅
- Phase 3B: Xtream Pipeline (Partial) 🟡

**Aber**: Phase 1 (Player) wurde ausgelassen!

#### Problem

Ohne sequenzielle Abarbeitung:
- Keine testbare App zwischen Phasen
- Keine Validierung ob Architektur funktioniert
- Keine Möglichkeit für inkrementelle Releases

#### Lösung

**Strict Phase Order** mit Tests zwischen Phasen:
1. Phase 0 → Build + Test → ✅
2. Phase 1 → Build + Manual Test (Play HTTP Stream) → ❌ FEHLT
3. Phase 2 → Build + Manual Test (Play Telegram) → ❌ FEHLT
4. Phase 3 → Build + Manual Test (Play Xtream) → ❌ FEHLT

---

### 2.5 ❌ Fehler: Keine End-to-End Tests

#### Problem

Jedes Modul kompiliert, aber:
- ❌ Keine Integration-Tests zwischen Modulen
- ❌ Keine UI-Tests für app-v2
- ❌ Keine manuellen Test-Checklisten

**Resultat**: Wir wissen nicht, ob v2 **überhaupt funktionieren würde**, selbst wenn wir Code hätten.

#### Fehlende Tests

```
// Phase 1 Manual Tests (FEHLEN):
- [ ] app-v2 starts without crash
- [ ] DebugPlaybackScreen can play HTTP test stream
- [ ] Player shows controls on TV
- [ ] Resume works after stop

// Phase 2 Manual Tests (FEHLEN):
- [ ] Telegram auth works
- [ ] Telegram messages load
- [ ] Telegram video plays
- [ ] Thumbnail loads

// Phase 3 Manual Tests (FEHLEN):
- [ ] Xtream login works
- [ ] VOD/Series/Live loads
- [ ] Xtream playback works
```

---

## 3. Architektur-Qualität – Was läuft GUT?

### 3.1 ✅ Exzellente Dokumentation

**18 v2-Dokumente** in `v2-docs/`:
- `ARCHITECTURE_OVERVIEW_V2.md` – Module + Dependencies (564 Zeilen)
- `IMPLEMENTATION_PHASES_V2.md` – Build-Order + Checklisten (657 Zeilen)
- `V1_VS_V2_ANALYSIS_REPORT.md` – Portierungs-Guide (819 Zeilen)
- `MEDIA_NORMALIZATION_CONTRACT.md` – Normalization Rules
- `TELEGRAM_TDLIB_V2_INTEGRATION.md` – TDLib Specs
- `PIPELINE_SYNC_STATUS.md` – Sync-Tracking
- Und 12 weitere...

**Qualität**: 🟢 **Exzellent** – Klar, präzise, actionable

---

### 3.2 ✅ Saubere Layer-Trennung

**Dependency Rules** klar definiert und enforced:

```
:feature:* → :pipeline:*, :player:internal, :core:*
:pipeline:* → :core:model, :core:persistence
:player:internal → :playback:domain, :core:model
:playback:domain → :core:model, :core:persistence

❌ VERBOTEN:
- :pipeline:* → :feature:*
- :pipeline:* → :player:internal
- :player:internal → :pipeline:*
```

**Qualität**: 🟢 **Exzellent** – Klare Trennung

---

### 3.3 ✅ Metadata Normalization Contract

**Binding Rules** in `MEDIA_NORMALIZATION_CONTRACT.md`:

Pipelines MÜSSEN:
- ✅ `toRawMediaMetadata()` implementieren
- ❌ KEINE Title Cleaning
- ❌ KEINE TMDB Lookups
- ❌ KEINE Cross-Pipeline Identity

**Status**: 
- ✅ Contract dokumentiert
- ✅ Telegram implementiert
- ✅ Xtream implementiert
- ✅ Tests validieren Compliance

**Qualität**: 🟢 **Exzellent** – Clear separation of concerns

---

### 3.4 ✅ Module-Struktur kompiliert

Alle 17 Module:
- ✅ Korrekte Gradle-Config
- ✅ Korrekte Dependencies
- ✅ Kompatible Versionen (Kotlin 2.1.0, Compose BOM 2024.12.01)
- ✅ Kein Zirkular-Dependency

**Qualität**: 🟢 **Exzellent** – Clean build

---

## 4. Kritische Blocker – Sortiert nach Priorität

### P0 – ABSOLUTE BLOCKER (v2 unbrauchbar ohne diese)

| # | Blocker | Grund | Aufwand | v1 LOC |
|---|---------|-------|---------|--------|
| 1 | **SIP Player nicht portiert** | Keine Playback-Funktionalität | 2-3 Tage | 5000 |
| 2 | **playback:domain leer** | player:internal kann nicht gebaut werden | 1 Tag | 500 |
| 3 | **UnifiedLog nicht portiert** | Keine Diagnostik/Logging | 4 Stunden | 578 |
| 4 | **ObjectBox Entities fehlen** | Keine Content-Persistence | 1 Tag | 1500 |
| 5 | **TelegramFileDataSource fehlt** | Telegram-Videos nicht abspielbar | 1 Tag | 413 |
| 6 | **XtreamObxRepository fehlt** | Xtream-Content nicht querybar | 1 Tag | 2829 |

**Total P0**: ~7 Tage, ~10.820 LOC aus v1 zu portieren

---

### P1 – CRITICAL (App funktioniert, aber rudimentär)

| # | Blocker | Grund | Aufwand | v1 LOC |
|---|---------|-------|---------|--------|
| 7 | **Feature Screens fehlen** | Keine Navigation, nur Skeleton | 3 Tage | 5000 |
| 8 | **FocusKit nicht portiert** | Keine TV-Navigation | 1 Tag | 1353 |
| 9 | **Fish* Layout nicht portiert** | Keine UI-Komponenten | 1 Tag | 2000 |
| 10 | **AppShell unvollständig** | Kein FeatureRegistry, kein Startup | 1 Tag | 500 |

**Total P1**: ~6 Tage, ~8.853 LOC

---

### P2 – IMPORTANT (Nice-to-have für Alpha)

| # | Feature | Grund | Aufwand | v1 LOC |
|---|---------|-------|---------|--------|
| 11 | DetailScaffold, MediaActionBar | Bessere Detail-Screens | 1 Tag | 1000 |
| 12 | TvButtons, CardKit | TV-optimierte Buttons | 4 Stunden | 300 |
| 13 | AppImageLoader | Coil 3 Integration | 2 Stunden | 153 |
| 14 | PlaybackSession | Shared Player Singleton | 1 Tag | 588 |
| 15 | Scene-Name Parsing | Bessere Metadaten | 2 Tage | 500 |

**Total P2**: ~5 Tage, ~2.541 LOC

---

## 5. Roadmap zur funktionalen v2 Alpha

### 5.1 Kritischer Pfad (P0 Blocker)

**Ziel**: v2 kann Telegram + Xtream Content abspielen

#### Woche 1: Player Foundation

**Tag 1-2: playback:domain + player:internal Base**
```bash
# Tag 1: Interfaces
- [ ] playback:domain Interfaces (ResumeManager, KidsPlaybackGate, etc.)
- [ ] playback:domain Default-Implementierungen (no-op)
- [ ] Port: InternalPlayerState.kt
- [ ] Port: PlaybackSourceResolver.kt

# Tag 2: Player Core
- [ ] Port: InternalPlayerSession.kt (1393 LOC)
- [ ] Port: InternalPlayerControls.kt
- [ ] Port: PlayerSurface.kt
- [ ] Build + Manual Test: HTTP stream playback
```

**Tag 3: Logging + Entities**
```bash
- [ ] Port: UnifiedLog.kt → infra:logging (578 LOC)
- [ ] Port: LogViewerScreen + ViewModel
- [ ] Port: ObxEntities.kt → core:persistence (10 entities)
- [ ] Port: ObxStore.kt → core:persistence
```

**Tag 4-5: Pipeline Completion**
```bash
# Tag 4: Telegram
- [ ] Port: TelegramFileDataSource.kt (413 LOC)
- [ ] Port: T_TelegramFileDownloader.kt (1621 LOC)
- [ ] Connect: TelegramContentRepository mit DefaultTelegramClient
- [ ] Build + Test: Telegram video playback

# Tag 5: Xtream
- [ ] Port: XtreamObxRepository.kt (2829 LOC)
- [ ] Port: DelegatingDataSourceFactory.kt
- [ ] Connect: XtreamCatalogRepository mit DefaultXtreamApiClient
- [ ] Build + Test: Xtream VOD playback
```

**Woche 1 Output**: 
- ✅ v2 kann Videos abspielen (Telegram + Xtream + HTTP)
- ✅ Logging funktioniert
- ✅ Content wird in ObjectBox gespeichert
- ✅ ~10.000 LOC portiert

---

### 5.2 Alpha Release (P1 Features)

**Ziel**: v2 ist benutzbar mit echter Navigation

#### Woche 2: UI Foundation

**Tag 6-7: FocusKit + Fish* Layout**
```bash
# Tag 6: Focus System
- [ ] Port: FocusKit.kt → ui:focus (1353 LOC)
- [ ] Port: TvButtons.kt → ui:common (145 LOC)

# Tag 7: Layout System
- [ ] Port: FishTheme.kt, FishTile.kt, FishRow.kt
- [ ] Port: FishVodContent.kt, FishSeriesContent.kt, FishLiveContent.kt
- [ ] Build + Test: Sample tiles render on TV
```

**Tag 8-10: Feature Screens**
```bash
# Tag 8: Home + Library
- [ ] feature:home → HomeScreen + ViewModel
- [ ] feature:library → LibraryScreen + ViewModel
- [ ] Navigation: Home ↔ Library

# Tag 9: Live + Telegram
- [ ] feature:live → LiveScreen + EPG UI
- [ ] feature:telegram-media → TelegramScreen + Chat Browser

# Tag 10: Settings + AppShell
- [ ] feature:settings → SettingsScreen + Profile UI
- [ ] app-v2: AppShell mit FeatureRegistry
- [ ] app-v2: Startup Sequence (DeviceProfile, Profiles)
```

**Woche 2 Output**:
- ✅ v2 hat funktionierende UI-Screens
- ✅ Navigation funktioniert
- ✅ TV-Focus funktioniert
- ✅ ~9.000 LOC portiert

---

### 5.3 Timeline-Übersicht

| Woche | Fokus | Output | LOC |
|-------|-------|--------|-----|
| **Woche 1** | P0 Blocker | Playable v2 | +10.000 |
| **Woche 2** | P1 Features | Usable v2 | +9.000 |
| **Woche 3** | P2 Polish | Polished v2 | +3.000 |
| **Woche 4** | Testing + Docs | Alpha Release | +1.000 |

**Total**: ~4 Wochen, ~23.000 LOC (von ~30.000 benötigt)

---

## 6. Empfehlungen

### 6.1 🔥 SOFORTMASSNAHMEN

1. **STOP Skeleton-Building**
   - Keine neuen Module
   - Keine neuen Interfaces ohne Implementierung
   - Fokus: **Portierung**

2. **START Tier 1 Portierung**
   - Tag 1: SIP Player (player:internal)
   - Tag 2: UnifiedLog (infra:logging)
   - Tag 3: ObjectBox Entities (core:persistence)
   - Tag 4-5: Pipeline Completion

3. **Strict Phase Order**
   - Abschluss Phase 1 vor Phase 2
   - Manual Testing nach jeder Phase
   - Keine Parallel-Fronten mehr

---

### 6.2 📋 PROZESS-ÄNDERUNGEN

1. **Port-First Strategie**
   ```
   VORHER:
   1. Interface definieren
   2. Dummy-Impl erstellen
   3. Tests schreiben
   4. Echte Impl später
   
   NACHHER:
   1. v1 Code identifizieren (V1_VS_V2_ANALYSIS_REPORT.md)
   2. Direkt nach v2 portieren
   3. Tests migrieren
   4. Build + Test
   ```

2. **Daily Smoke Tests**
   - Jeden Tag: Build + app-v2 starten
   - Jeden Tag: 1 Manual Test (z.B. "Play HTTP stream")
   - Blocker sofort fixen, nicht aufstapeln

3. **Phase Gates**
   - Phase N+1 erst starten nach:
     - ✅ Phase N kompiliert
     - ✅ Phase N hat Manual Test bestanden
     - ✅ Phase N PR gemerged

---

### 6.3 🎯 FOKUS FÜR NÄCHSTE 2 WOCHEN

**Woche 1 (P0)**:
- [ ] SIP Player portieren → player:internal
- [ ] playback:domain implementieren
- [ ] UnifiedLog portieren → infra:logging
- [ ] ObjectBox Entities portieren → core:persistence
- [ ] Telegram/Xtream DataSources portieren
- **Goal**: v2 kann Videos abspielen

**Woche 2 (P1)**:
- [ ] FocusKit + Fish* Layout portieren
- [ ] Feature Screens implementieren (Home, Library, Live, Telegram, Settings)
- [ ] AppShell vervollständigen
- **Goal**: v2 ist benutzbar mit Navigation

---

## 7. Metriken für Erfolg

### 7.1 Code-Coverage

| Kategorie | Ziel (EOW1) | Ziel (EOW2) | Ziel (Alpha) |
|-----------|-------------|-------------|--------------|
| Player | 80% | 80% | 90% |
| Pipelines | 60% | 80% | 90% |
| UI Components | 0% | 80% | 90% |
| Feature Screens | 0% | 60% | 80% |
| **Gesamt** | **40%** | **75%** | **90%** |

### 7.2 Functional Tests

**End of Week 1**:
- [ ] ✅ app-v2 starts
- [ ] ✅ HTTP test stream plays
- [ ] ✅ Telegram video plays
- [ ] ✅ Xtream VOD plays
- [ ] ✅ Logs sichtbar in LogViewer

**End of Week 2**:
- [ ] ✅ Navigation zwischen Screens
- [ ] ✅ TV-Focus funktioniert
- [ ] ✅ Home-Screen zeigt Content
- [ ] ✅ Settings kann Profile erstellen
- [ ] ✅ Resume funktioniert

---

## 8. Fazit

### 🟢 Stärken

1. **Architektur-Design**: Exzellent (Layer-Trennung, Dependencies, Contracts)
2. **Dokumentation**: Ausgezeichnet (18 Docs, klar, actionable)
3. **Module-Struktur**: Sauber (17 Module, kompilieren, korrekte Versionen)
4. **Pipeline Interfaces**: Gut (Telegram + Xtream ~60-80% Core)

### 🔴 Schwächen

1. **Code-Umsetzung**: Nur ~17% (5k von 30k LOC)
2. **Tier 1 Portierung**: 0% (keine der 6 Systeme portiert)
3. **Player**: 0% (player:internal leer, obwohl v1 5000 LOC hat)
4. **Feature Screens**: 0% (alle 6 Module leer)
5. **Infrastructure**: 0% (logging, tooling leer)
6. **Testing**: Keine End-to-End Tests, keine Manual Tests

### 🎯 Kritischer Pfad

**Problem**: "Skeleton First" statt "Port First"  
**Lösung**: Aggressives Portieren nach `V1_VS_V2_ANALYSIS_REPORT.md`

**Zeitplan**: 
- Woche 1: P0 Blocker (Player, Logging, Entities, DataSources)
- Woche 2: P1 Features (UI, FocusKit, Fish*, Feature Screens)
- Woche 3-4: Polish + Testing

**Resultat**: Funktionale v2 Alpha in ~4 Wochen

---

## 9. Nächste Schritte (Actionable)

### Sofort (Diese Woche)

1. **PR erstellen**: "Port SIP Player to player:internal"
   - Kopiere v1 `player/internal/` → v2 `player/internal/`
   - Adjust imports (com.chris → com.fishit)
   - Build + Test

2. **PR erstellen**: "Port UnifiedLog to infra:logging"
   - Kopiere v1 `core/logging/UnifiedLog.kt` → v2
   - Build + Test

3. **PR erstellen**: "Port ObjectBox Entities to core:persistence"
   - Kopiere v1 `data/obx/ObxEntities.kt` → v2
   - Kopiere v1 `data/obx/ObxStore.kt` → v2
   - Build + Test

### Nächste Woche

4. **PR**: "Port Telegram DataSources"
5. **PR**: "Port Xtream ObxRepository"
6. **PR**: "Implement feature:home Screen"
7. **PR**: "Port FocusKit + Fish Layout"

### Meetings

- [ ] **Arch Review**: Diese Review mit Team besprechen
- [ ] **Timeline Commit**: 4-Wochen-Plan committen
- [ ] **Daily Standups**: "Was wurde portiert heute?"

---

**Ende Review** – Stand: 2025-12-07  
**Status**: 🔴 v2 ist ~17% fertig, aber Architektur ist exzellent  
**Empfehlung**: Port-First Strategie für nächste 4 Wochen

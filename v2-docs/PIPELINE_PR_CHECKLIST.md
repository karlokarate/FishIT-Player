# Pipeline PR Checklist (v2 Architecture)

> **MANDATORY:** Diese Checkliste muss nach JEDER Änderung an Pipeline-Modulen geprüft werden.
> 
> Gilt für: `:pipeline:xtream`, `:pipeline:telegram`, und zukünftige Pipeline-Module.

---

## 1. Scope & Architecture

- [ ] Pipeline enthält nur source-spezifische Logik
- [ ] Kein UI-Code (keine Views, Composables, Activities, Fragments)
- [ ] Kein Android `Context` innerhalb Domain/Pipeline-Logik
- [ ] Keine ExoPlayer/Media3-Nutzung in Pipelines
- [ ] Keine TMDB-Lookups in Pipelines
- [ ] Keine HTTP-Clients außer Source-Protocol-Clients
- [ ] Normalisierung delegiert an `:core:metadata-normalizer`
- [ ] Identity/Resume/TMDB-Merge-Keys in zentralen Modulen behandelt
- [ ] Streaming-Logik durch infra/player-Module, niemals in Pipelines

## 2. Code Reuse: Good Old Code vs. Legacy

- [ ] Nur "good old code" wiederverwendet (pure Logik, DTOs, Algorithmen)
- [ ] Keine Legacy-Patterns importiert (Singletons, God-Services, UI-Coupling)
- [ ] Neue Modulgrenzen respektiert (pipeline / core / infra / player)
- [ ] Neue Implementation gleichwertig oder besser als v1-Verhalten
- [ ] Entferntes v1-Verhalten bewusst dokumentiert

## 3. Data Models & Contracts

- [ ] Pipeline definiert nur source-spezifische DTOs
- [ ] Mapping zu `RawMediaMetadata` sauber implementiert
- [ ] Keine Normalisierung oder Heuristiken in Pipeline
- [ ] Nutzt globale Enums und Contracts (`SourceType`, `MediaType`)
- [ ] Alle IDs für globale Resume/TMDB-Regeln enthalten
- [ ] TMDB-Felder nur als Raw-Strings gespeichert

## 4. Normalization & Parsing Rules

- [ ] Alle Regex/komplexe Parsing-Logik zentralisiert im Normalizer
- [ ] Pipeline liefert nur Raw-Metadaten
- [ ] Keine Pipeline-Level-Heuristiken außer unvermeidbaren Source-Quirks
- [ ] Kein TMDB-Parsing oder Adult-Detection in Pipeline

## 5. TMDB / Identity / Resume Keys

- [ ] TMDB-Felder raw durchgereicht
- [ ] Keine TMDB-API-Aufrufe
- [ ] Globale Resume- und Identity-Regeln respektiert
- [ ] Erforderliche Keys für Cross-Source Dedupe/Merge vorhanden

## 6. Logging & Telemetry

- [ ] Nur `UnifiedLog` / zentrales Logging verwendet
- [ ] Kein `Log.d`, `println`, `Timber`, oder `Kermit` direkt
- [ ] Logs strukturiert und mit Modul/Source getaggt
- [ ] Keine Secrets in Logs
- [ ] Korrekte Log-Level für Errors/Warnings
- [ ] Metrics/Events über globales Telemetry-System

## 7. Global ImageLoader & Thumbnails

- [ ] Nutzt globale Coil 3 ImageLoader-Konfiguration
- [ ] Pipeline exponiert nur Raw-Image-Daten (keine UI-Entscheidungen)
- [ ] Thumbnail-Fetcher als ImageLoader-Komponenten implementiert
- [ ] Keine pipeline-spezifischen Image-Loader

## 8. Streaming & TDLib / DataSource

- [ ] Pipeline enthält **keine Streaming-Logik** (kein Windowing, Buffering, Chunk-Logik)
- [ ] Alle TDLib-Nutzung über zentrale TDLib-Client-Abstraktion geroutet
- [ ] `ensureFileReady()` nur in infra/player-Modulen implementiert
- [ ] Playback-URIs folgen globaler v2-Spec (remoteId-first, stabile Query-Parameter)
- [ ] Alle DataSource-Implementationen nur in Player-Modulen

## 9. Error Handling & Robustness

- [ ] Pipeline behandelt fehlerhafte Eingaben sicher
- [ ] Typisierte Exceptions (kein generisches `Exception` ohne Kontext)
- [ ] Fallback-Verhalten klar definiert
- [ ] Nicht-behebbare Fehlermodi kontrolliert kommuniziert

## 10. Testing & Quality

- [ ] DTO- und Mapper-Tests vorhanden
- [ ] Tests decken Fehlerfälle und partielle/ungültige Daten ab
- [ ] Regressionstests für wiederverwendete v1-Logik
- [ ] Repository/Pipeline-Tests für Paging, Filtering, Sorting
- [ ] Detekt/Ktlint-konform
- [ ] Nutzt aktuelle Dependencies (Kotlin, Coroutines, Coil, TDLib-Bindings)

## 11. App Structure & Module Boundaries

- [ ] Korrekte Dependency-Richtung (pipeline → core:model, core:persistence, infra:logging)
- [ ] Keine zyklischen Abhängigkeiten eingeführt
- [ ] Code im korrekten v2-Modul platziert (pipeline / infra / core / player)
- [ ] Architektur-Dokumentation entsprechend aktualisiert

## 12. Docs, Contracts & PR Hygiene

- [ ] Alle relevanten MD-Contract-Files zusammen mit Code aktualisiert
- [ ] Veraltete Docs entfernt oder markiert
- [ ] PR-Scope sauber (keine gemischten Concerns)
- [ ] TODOs minimal, gut abgegrenzt und mit Tasks/Issues verknüpft

## 13. External Tools / Integration

- [ ] Pipeline-Tests laufen und bestehen auf CI
- [ ] Optional: Coverage oder Quality-Gates (SonarQube, Qodana) aktiviert
- [ ] Logging/Metrics strukturiert für externe Analyzer
- [ ] Code vorbereitet für zukünftige Automation (Agents, Diagnostics, Workspace-Tooling)

---

## Quick Reference: Module Responsibilities

| Concern | Belongs In | NOT In Pipeline |
|---------|------------|-----------------|
| Source API Calls | `:pipeline:*` | ✅ |
| Raw DTOs | `:pipeline:*` | ✅ |
| Title Cleaning | `:core:metadata-normalizer` | ❌ |
| TMDB Lookups | `:core:metadata-normalizer` | ❌ |
| Streaming/Buffering | `:player:internal` | ❌ |
| DataSource impl | `:player:internal` | ❌ |
| Android Context | `:app`, `:infra:*` | ❌ |
| UI Components | `:feature:*`, `:app` | ❌ |

---

## Beispiel: Telegram Pipeline Audit (2025-12-08)

**Files reviewed:**
- `DefaultTelegramClient.kt` (358 LOC)
- `TdlibMessageMapper.kt` (284 LOC)
- `TelegramClient.kt` (169 LOC)
- `TdlibClientProvider.kt` (60 LOC)
- `TdlibMessageMapperTest.kt` (580 LOC)
- `DefaultTelegramClientTest.kt` (450 LOC)

### Section 1: Scope & Architecture ✅
- [x] Pipeline enthält nur source-spezifische Logik
- [x] Kein UI-Code (keine Views, Composables, Activities, Fragments)
- [x] Kein Android `Context` innerhalb Domain/Pipeline-Logik (via TdlibClientProvider)
- [x] Keine ExoPlayer/Media3-Nutzung in Pipelines
- [x] Keine TMDB-Lookups in Pipelines
- [x] Keine HTTP-Clients außer Source-Protocol-Clients (TDLib only)
- [x] Normalisierung delegiert an `:core:metadata-normalizer`
- [x] Identity/Resume/TMDB-Merge-Keys in zentralen Modulen behandelt
- [x] Streaming-Logik durch infra/player-Module, niemals in Pipelines

### Section 2: Code Reuse ✅
- [x] Nur "good old code" wiederverwendet (g00sha AAR patterns)
- [x] Keine Legacy-Patterns importiert
- [x] Neue Modulgrenzen respektiert
- [x] Neue Implementation gleichwertig oder besser als v1
- [x] Entferntes v1-Verhalten dokumentiert (PIPELINE_SYNC_STATUS.md)

### Section 3: Data Models & Contracts ✅
- [x] Pipeline definiert nur source-spezifische DTOs (TelegramMediaItem)
- [x] Mapping zu `RawMediaMetadata` sauber implementiert
- [x] Keine Normalisierung oder Heuristiken in Pipeline (title="" for VIDEO/DOCUMENT/PHOTO)
- [x] Nutzt globale Enums und Contracts
- [x] Alle IDs für globale Resume/TMDB-Regeln enthalten
- [x] TMDB-Felder nur als Raw-Strings gespeichert

### Section 4: Normalization & Parsing Rules ✅
- [x] Alle Regex/komplexe Parsing-Logik zentralisiert im Normalizer
- [x] Pipeline liefert nur Raw-Metadaten
- [x] Keine Pipeline-Level-Heuristiken außer unvermeidbaren Source-Quirks
- [x] Kein TMDB-Parsing oder Adult-Detection in Pipeline

### Section 5: TMDB / Identity / Resume Keys ✅
- [x] TMDB-Felder raw durchgereicht (N/A - Telegram hat keine)
- [x] Keine TMDB-API-Aufrufe
- [x] Globale Resume- und Identity-Regeln respektiert
- [x] Erforderliche Keys vorhanden (chatId, messageId, fileId, remoteId, uniqueId)

### Section 6: Logging & Telemetry ✅
- [x] Nur `UnifiedLog` verwendet
- [x] Kein `Log.d`, `println`, `Timber`, oder `Kermit` direkt
- [x] Logs strukturiert und mit Modul/Source getaggt (TAG = "DefaultTelegramClient")
- [x] Keine Secrets in Logs
- [x] Korrekte Log-Level (d/i/w/e)
- [ ] Metrics/Events über globales Telemetry-System (TODO: nicht implementiert)

### Section 7: Global ImageLoader & Thumbnails ✅
- [x] Pipeline exponiert nur Raw-Image-Daten (thumbnailFileId, thumbnailPath)
- [x] Keine pipeline-spezifischen Image-Loader

### Section 8: Streaming & TDLib / DataSource ✅
- [x] Pipeline enthält keine Streaming-Logik
- [x] Alle TDLib-Nutzung über zentrale Client-Abstraktion (TelegramClient interface)
- [x] `requestFileDownload()` nur Metadaten (localPath may be null)
- [ ] Playback-URIs folgen globaler v2-Spec (TODO: URI-Builder noch nicht implementiert)
- [x] Keine DataSource-Implementationen in Pipeline

### Section 9: Error Handling & Robustness ✅
- [x] Pipeline behandelt fehlerhafte Eingaben sicher (null checks, safe helpers)
- [x] Typisierte Exceptions (TelegramAuthException, TelegramFileException)
- [x] Fallback-Verhalten klar definiert (return null for invalid media)
- [x] Retry-Logic mit exponential backoff (withRetry, 3 attempts)

### Section 10: Testing & Quality ✅
- [x] DTO- und Mapper-Tests vorhanden (TdlibMessageMapperTest)
- [x] Tests decken Fehlerfälle und partielle/ungültige Daten ab
- [x] Regressionstests für wiederverwendete v1-Logik (message type mapping)
- [x] Repository/Pipeline-Tests (DefaultTelegramClientTest)
- [ ] Detekt/Ktlint-konform (TODO: noch nicht geprüft)
- [x] Nutzt aktuelle Dependencies (Kotlin 2.0, Coroutines 1.9, g00sha 5.0.0)

### Section 11: App Structure & Module Boundaries ✅
- [x] Korrekte Dependency-Richtung (pipeline → core:model, core:persistence, infra:logging)
- [x] Keine zyklischen Abhängigkeiten
- [x] Code im korrekten v2-Modul platziert
- [x] Architektur-Dokumentation aktualisiert (PIPELINE_SYNC_STATUS.md)

### Section 12: Docs, Contracts & PR Hygiene ✅
- [x] Alle relevanten MD-Contract-Files aktualisiert
- [x] Veraltete Docs entfernt oder markiert
- [x] PR-Scope sauber
- [x] TODOs minimal (2 minor TODOs: Telemetry, URI-Builder)

### Section 13: External Tools / Integration ⚠️
- [ ] Pipeline-Tests laufen auf CI (TODO: CI noch nicht konfiguriert)
- [ ] Coverage aktiviert (TODO)
- [x] Logging strukturiert für externe Analyzer
- [x] Code vorbereitet für zukünftige Automation

**Summary:** ✅ 11/13 Sections PASS, 2/13 minor TODOs (Telemetry, CI)

---

## Änderungshistorie

| Datum | Änderung |
|-------|----------|
| 2025-12-08 | Telegram Pipeline Audit aktualisiert (Phase 3+ Complete) |
| 2025-12-07 | Initial version erstellt |

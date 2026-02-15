# ⚠️ DEPRECATED DOCUMENT ⚠️

> **Deprecation Date:** 2026-01-09  
> **Status:** SUPERSEDED  
> **Reason:** This document describes the old Obx* canonical media system, which is being replaced by the NX_* entity system.
> 
> **✅ Use This Instead:**  
> - **contracts/NX_SSOT_CONTRACT.md** - Authoritative NX_* entity system contract  
> - **docs/v2/NX_SSOT_CONTRACT.md** - Detailed NX_* system documentation  
> - **docs/v2/OBX_PLATIN_REFACTOR_ROADMAP.md** - Migration roadmap (Phase 0 complete)  
> - **docs/v2/ISSUE_621_STATUS_UPDATE_2026-01-09.md** - Current implementation status
> 
> **⚠️ Warning:** The Obx* entities described below are being replaced by NX_* entities:
> - `ObxCanonicalMedia` → `NX_Work`
> - `ObxMediaSourceRef` → `NX_WorkSourceRef` + `NX_WorkVariant`
> - `ObxCanonicalResumeMark` → `NX_WorkUserState` (with percentage-based resume)

---

# ~~v2 Migration Tracker – Canonical Media System~~

> **Letzte Prüfung:** 2025-12-11
> **Status:** ~~✅ Alle v2-Dateien korrekt platziert – Migration abgeschlossen~~ ⚠️ OUTDATED - See NX_* system above

## ~~Übersicht~~

~~Diese Datei dokumentiert den Status der Migration des Canonical Media Systems in die v2-Architektur.~~

**Note:** This migration is superseded by the OBX PLATIN Refactor (Issue #621), which introduces 16 NX_* entities with improved multi-account support, percentage-based resume, and audit trails.

## Korrekte v2-Module (✅ Verifiziert)

| Modul | Package | Dateien | Status |
|-------|---------|---------|--------|
| `core/model` | `com.fishit.player.core.model` | 16 | ✅ Korrekt |
| `core/persistence` | `com.fishit.player.core.persistence` | 13 | ✅ Korrekt |
| `feature/detail` | `com.fishit.player.feature.detail` | 3 | ✅ Korrekt |
| `pipeline/telegram` | `com.fishit.player.pipeline.telegram` | 72+ | ✅ Korrekt |
| `pipeline/xtream` | `com.fishit.player.pipeline.xtream` | 16+ | ✅ Korrekt |
| `playback/domain` | `com.fishit.player.playback.domain` | 14 | ✅ Korrekt |

---

## Erstellte Dateien (v2) – Canonical Media System

### core:model (`com.fishit.player.core.model`)
- `CanonicalMediaId.kt` - Unique canonical ID generation
- `MediaSourceRef.kt` - Source-Referenz mit Quality/Language/Format/Duration
- `MediaSourceRefExtensions.kt` - Factory-Funktionen für Pipelines
- `ImageRef.kt` - Image reference model
- `NormalizedMediaMetadata.kt` - Normalized metadata with ImageRef
- `RawMediaMetadata.kt` - Raw metadata with ImageRef
- `repository/CanonicalMediaRepository.kt` - Repository-Interface

### core:persistence (`com.fishit.player.core.persistence`)
- `obx/ObxCanonicalEntities.kt` - ObjectBox Entities:
  - `ObxCanonicalMedia` - Canonical media record
  - `ObxMediaSourceRef` - Source reference with durationMs
  - `ObxCanonicalResumeMark` - Resume with **positionPercent** + **lastSourceDurationMs**
- `repository/ObxCanonicalMediaRepository.kt` - Repository-Implementierung

### feature:detail (`com.fishit.player.feature.detail`)
- `UnifiedDetailUseCases.kt` - Business Logic für Details
- `UnifiedDetailViewModel.kt` - ViewModel mit:
  - `ResumeCalculation(positionMs, isApproximate, warning)`
  - `getResumePositionForSource()` - Cross-Source Position Berechnung
- `ui/SourceBadge.kt` - UI-Komponenten:
  - `SourceBadgeChip` - Badge für Tiles
  - `SourceBadgeRow` - Row mit mehreren Sources
  - `SourceComparisonCard` - Vergleich von Sources (Duration/Size/Format)
  - `SourceResumeIndicator` - Source-spezifische Resume-Anzeige
  - `ResumeApproximationNotice` - Warnung bei Cross-Source Resume

---

## ~~AKTION ERFORDERLICH~~ ✅ ERLEDIGT

Die folgend erwähnte Datei existiert **nicht mehr** – sie wurde bereits gelöscht oder nie erstellt:

| Status | Legacy-Pfad | v2-Ersatz (KORREKT) |
|--------|-------------|---------------------|
| ✅ GELÖSCHT | ~~`app/src/main/java/com/chris/m3usuite/ui/layout/SourceBadge.kt`~~ | `feature/detail/src/main/java/com/fishit/player/feature/detail/ui/SourceBadge.kt` |

**Hinweis:** Die v2-Version in `feature/detail` ist die einzige gültige Implementierung.

---

## v1 vs v2 Package-Konvention

| Kategorie | v1 (Legacy) ❌ | v2 (Korrekt) ✅ |
|-----------|---------------|-----------------|
| **Package-Root** | `com.chris.m3usuite.*` | `com.fishit.player.*` |
| **Features** | `app/src/main/java/com/chris/.../ui/screens/` | `feature/*/src/main/java/com/fishit/.../` |
| **Core** | `app/src/main/java/com/chris/.../core/` | `core/*/src/main/java/com/fishit/.../` |
| **Pipelines** | `app/src/main/java/com/chris/.../telegram/` | `pipeline/*/src/main/java/com/fishit/.../` |
| **Playback** | `app/src/main/java/com/chris/.../playback/` | `playback/*/src/main/java/com/fishit/.../` |
| **Player** | `app/src/main/java/com/chris/.../player/` | `player/*/src/main/java/com/fishit/.../` |

---

## ⛔ STRIKTE REGELN (v2-Bootstrap Branch)

Per `AGENTS_V2.md` – **NIEMALS ignorieren:**

### ✅ ERLAUBT
- Neue Dateien in: `core/*`, `feature/*`, `pipeline/*`, `playback/*`, `player/*`, `infra/*`, `app-v2/`
- Package: `com.fishit.player.*`
- Docs in: `docs/v2/`

### ❌ VERBOTEN
- Neue Dateien in: `app/src/main/java/com/chris/*`
- Package: `com.chris.m3usuite.*`
- Änderungen an Legacy-Code (nur als Referenz lesen!)
- Merge in `main` ohne Owner-Approval

---

## Audit-Log

| Datum | Aktion | Ergebnis |
|-------|--------|----------|
| 2025-12-11 | Dokumenten-Audit | Legacy-Datei war bereits gelöscht, Status aktualisiert |
| (original) | Vollständiger v2-Pfad-Audit | 1 Legacy-Verletzung gefunden: `SourceBadge.kt` |
| (original) | v2-Module geprüft | Alle 6 Module korrekt mit `com.fishit.player.*` |
| 2026-01-09 | Document Deprecated | Superseded by NX_* entity system (Issue #621) |

---

# ⚠️ END OF DEPRECATED DOCUMENT ⚠️

> **This document is outdated.** Use the following instead:
> 
> **Current Documentation:**  
> - **contracts/NX_SSOT_CONTRACT.md** - Binding contract for NX_* entities  
> - **docs/v2/OBX_PLATIN_REFACTOR_ROADMAP.md** - Implementation roadmap  
> - **docs/v2/ISSUE_621_STATUS_UPDATE_2026-01-09.md** - Current status
> 
> **Key Improvements in NX_* System:**  
> - Multi-account ready from day one (accountKey mandatory)  
> - Percentage-based resume (cross-source playback)  
> - Audit trail (NX_IngestLedger - no silent drops)  
> - Kill-switch infrastructure (safe migration)  
> - Deterministic key generation (collision-free)

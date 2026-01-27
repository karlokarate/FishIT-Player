# NX Entity Duplication & SSOT Analysis

> **Stand:** 27. Januar 2026  
> **Basiert auf:** `XTREAM_ENTITY_FIELD_MAPPING.md` und `NxEntities.kt`

Dieses Dokument analysiert potenzielle Feld-Duplikate zwischen NX-Entities und definiert die **Single Source of Truth (SSOT)** für jedes Feld aus UI-Perspektive.

---

## Inhaltsverzeichnis

1. [Executive Summary](#1-executive-summary)
2. [Duplikationsanalyse](#2-duplikationsanalyse)
3. [SSOT pro Use Case](#3-ssot-pro-use-case)
4. [Namenskonsistenz](#4-namenskonsistenz)
5. [Empfehlungen](#5-empfehlungen)
6. [Migration Impact](#6-migration-impact)

---

## 1. Executive Summary

### Gefundene Probleme

| Kategorie | Anzahl | Schweregrad |
|-----------|:------:|:-----------:|
| **Absichtliche Denormalisierung** | 1 | 🟡 DESIGN |
| **Semantische Duplikate** (behalten) | 2 | 🟢 OK |
| **FK-Pattern** (korrekt) | 5+ | ✅ Design |
| **Namensinkonsistenzen** | 2 | 🟡 MITTEL |

### Haupterkenntnis

```
ℹ️ NX_WorkUserState.totalDurationMs ist ABSICHTLICH dupliziert
   → Designentscheidung: Performance vs. Datenkonsistenz
   → Kurzfristig behalten, langfristig entfernen erwägen
```

---

## 2. Duplikationsanalyse

### 2.1 Absichtliche Denormalisierung (DESIGN-ENTSCHEIDUNG)

#### `totalDurationMs` in NX_WorkUserState

| Entity | Feld | Typ | Verwendung |
|--------|------|-----|------------|
| `NX_Work` | `durationMs` | `Long?` | Content-Laufzeit in Millisekunden |
| `NX_WorkUserState` | `totalDurationMs` | `Long` | "Für Prozentberechnung" |

**Aktuelle Verwendung (absichtlich):**
- `NxResumeManager` liest `state.totalDurationMs` für `ResumePoint.durationMs`
- Repository-Interface fordert `durationMs` bei `updateResumePosition()`
- Ermöglicht "Continue Watching" ohne JOIN

**Vorteile der Duplikation:**
- Schnellere Abfragen für "Continue Watching" (kein JOIN nötig)
- ResumeManager kann unabhängig von Work-Entity arbeiten

**Nachteile:**
- Inkonsistenzrisiko wenn `Work.durationMs` aktualisiert wird
- Speicheroverhead (8 Bytes pro UserState-Eintrag)

**Empfehlung:** 🟡 **DESIGN-ENTSCHEIDUNG** - Behalten mit Dokumentation

---

### 2.2 Semantische Duplikate (BEHALTEN)

#### A) Title-Felder

| Entity | Feld | Semantik |
|--------|------|----------|
| `NX_Work` | `canonicalTitle` | Normalisierter, UI-freundlicher Titel |
| `NX_Work` | `canonicalTitleLower` | Lowercase für case-insensitive Suche |
| `NX_WorkSourceRef` | `rawTitle` | Original-Titel aus Quelle (unverändert) |

**Warum behalten:**
- `canonicalTitle` = für UI-Anzeige (normalisiert, bereinigt)
- `rawTitle` = für Debugging/Provenance (original wie empfangen)
- Unterschiedliche semantische Bedeutung

**Empfehlung:** 🟢 **BEHALTEN** - Unterschiedliche Zwecke

---

#### B) Season/Episode in Relation

| Entity | Feld | Semantik |
|--------|------|----------|
| `NX_Work` (EPISODE) | `season`, `episode` | Staffel/Episode des Werks selbst |
| `NX_WorkRelation` | `season`, `episode` | Staffel/Episode für Sortierung |

**Warum behalten:**
```kotlin
// Ohne Duplikat: JOIN erforderlich für Sortierung
workBox.query()
    .link(NX_Work_.childRelations)
    .order(NX_WorkRelation_.sortOrder)  // Kein season/episode!
    .build()

// Mit Duplikat: Direkte Sortierung möglich
relationBox.query()
    .order(NX_WorkRelation_.season)
    .order(NX_WorkRelation_.episode)
    .build()
```

**Risiko:** Daten können inkonsistent werden wenn `Work.season` ≠ `Relation.season`

**Empfehlung:** 🟡 **BEHALTEN mit Sync-Garantie** - Effizienz-Optimierung für Queries

---

### 2.3 Foreign Key Pattern (KORREKT)

Diese Felder sehen wie Duplikate aus, sind aber FK-Referenzen:

| FK-Feld | Referenzierende Entity | Primäre Entity |
|---------|------------------------|----------------|
| `sourceKey` | NX_WorkVariant | NX_WorkSourceRef |
| `workKey` | NX_WorkUserState | NX_Work |
| `workKey` | NX_WorkRuntimeState | NX_Work |
| `workKey` | NX_IngestLedger.resultWorkKey | NX_Work |
| `workKey` | NX_WorkEmbedding | NX_Work |
| `workKey` | NX_WorkCategoryRef | NX_Work |
| `accountKey` | NX_WorkSourceRef | NX_SourceAccount |
| `accountKey` | NX_Category | NX_SourceAccount |
| `profileId` | NX_WorkUserState | NX_Profile |
| `channelWorkKey` | NX_EpgEntry | NX_Work (LIVE) |

**Empfehlung:** ✅ **KORREKTES DESIGN** - Standard FK-Pattern

---

## 3. SSOT pro Use Case

### 3.1 Media Cards (Home Screen / Browse)

```
┌─────────────────────────────────────────────────┐
│  SSOT: NX_Work                                  │
├─────────────────────────────────────────────────┤
│  Fields: canonicalTitle, poster, workType,      │
│          year, rating, durationMs, genres       │
└─────────────────────────────────────────────────┘
```

**Query:**
```kotlin
workBox.query()
    .equal(NX_Work_.workType, "MOVIE")
    .build()
    .find()
```

---

### 3.2 Detail Screen

```
┌─────────────────────────────────────────────────┐
│  Primary SSOT: NX_Work                          │
├─────────────────────────────────────────────────┤
│  + NX_WorkVariant (Qualitätsoptionen)           │
│  + NX_WorkUserState (User-Zustand)              │
└─────────────────────────────────────────────────┘
```

| Daten | Entity |
|-------|--------|
| Titel, Plot, Cast, Director, Genres | NX_Work |
| Poster, Backdrop, Trailer | NX_Work |
| Rating, Duration, Year | NX_Work |
| Verfügbare Qualitäten | NX_WorkVariant |
| Favorit, Gesehen, Resume-Position | NX_WorkUserState |

---

### 3.3 Player (Playback)

```
┌─────────────────────────────────────────────────┐
│  Primary SSOT: NX_WorkVariant                   │
├─────────────────────────────────────────────────┤
│  + NX_Work (für durationMs!)                    │
│  + NX_WorkUserState (Resume-Position)           │
└─────────────────────────────────────────────────┘
```

| Daten | Entity | Notiz |
|-------|--------|-------|
| Playback URL | NX_WorkVariant | |
| Playback Method | NX_WorkVariant | DIRECT, STREAMING, etc. |
| Playback Hints (JSON) | NX_WorkVariant | Source-spezifische Daten |
| Video/Audio Codec | NX_WorkVariant | |
| **Content Duration** | **NX_Work** ⚠️ | NICHT aus Variant! |
| Resume Position | NX_WorkUserState | |

**Wichtig:** Der Player braucht `Work.durationMs` für Seek-Bar und Progress!

---

### 3.4 Series Navigation

```
┌─────────────────────────────────────────────────┐
│  Navigation: NX_Work → NX_WorkRelation → NX_Work│
├─────────────────────────────────────────────────┤
│  Series (NX_Work.workType=SERIES)               │
│    └── NX_WorkRelation (parentWork → childWork) │
│          └── Episode (NX_Work.workType=EPISODE) │
└─────────────────────────────────────────────────┘
```

**Query für Episoden einer Serie:**
```kotlin
// Via Backlink
val series: NX_Work = ...
val episodes = series.childRelations
    .sortedWith(compareBy({ it.season }, { it.episode }))
    .map { it.childWork.target }
```

---

### 3.5 Live TV / EPG

```
┌─────────────────────────────────────────────────┐
│  Channel: NX_Work (workType=LIVE)               │
│  EPG Link: NX_WorkSourceRef.epgChannelId        │
│  Schedule: NX_EpgEntry                          │
└─────────────────────────────────────────────────┘
```

| Daten | Entity |
|-------|--------|
| Kanal-Name, Icon | NX_Work |
| EPG Channel ID | NX_WorkSourceRef |
| TV Archive (Catchup) | NX_WorkSourceRef |
| Programmübersicht | NX_EpgEntry |

---

### 3.6 User Progress Calculation

```
┌─────────────────────────────────────────────────┐
│  Progress = resumePositionMs / durationMs       │
├─────────────────────────────────────────────────┤
│  resumePositionMs: NX_WorkUserState             │
│  durationMs: NX_Work ⚠️ (NICHT UserState!)      │
└─────────────────────────────────────────────────┘
```

**Korrekte Implementierung:**
```kotlin
fun NX_WorkUserState.progressPercent(work: NX_Work): Float {
    val duration = work.durationMs ?: return 0f
    if (duration == 0L) return 0f
    return (resumePositionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
}
```

---

## 4. Namenskonsistenz

### 4.1 Aktuelle Patterns (KORREKT)

| Pattern | Beispiele | Status |
|---------|-----------|:------:|
| `*Ms` für Zeit | durationMs, resumePositionMs, startMs, endMs | ✅ |
| `*Bytes` für Größe | fileSizeBytes | ✅ |
| `*Bps` für Bitrate | bitrateBps | ✅ |
| `*Key` für Schlüssel | workKey, sourceKey, accountKey | ✅ |
| `*Id` für IDs | tmdbId, imdbId, xtreamStreamId | ✅ |
| `is*` für Booleans | isAdult, isWatched, isFavorite | ✅ |
| `*At` für Timestamps | createdAt, updatedAt, lastWatchedAt | ✅ |

### 4.2 Inkonsistenzen

#### A) Duration-Naming

| Entity | Feld | Problem |
|--------|------|---------|
| NX_Work | `durationMs` | ✅ Korrekt |
| NX_WorkUserState | `totalDurationMs` | ⚠️ Anderer Name für gleiche Semantik |

**Empfehlung:** 
- Wenn behalten: Umbenennen zu `durationMs` für Konsistenz
- Besser: Entfernen und aus Work ableiten

#### B) Boolean-Naming

| Entity | Feld | Konsistent? |
|--------|------|:-----------:|
| NX_Work | `needsReview` | ⚠️ Sollte `isNeedsReview` oder `requiresReview` sein |
| NX_Work | `isAdult` | ✅ |

---

## 5. Empfehlungen

### 5.1 Sofortige Maßnahmen

#### � KEINE KRITISCHEN ÄNDERUNGEN ERFORDERLICH

Die aktuelle Entity-Struktur ist durchdacht. Die identifizierten "Duplikate" sind entweder:
- **FK-Pattern** (korrekt)
- **Absichtliche Denormalisierung** (Performance-Optimierung)
- **Semantisch unterschiedlich** (rawTitle vs. canonicalTitle)

#### 🟡 OPTIONAL: totalDurationMs Sync-Garantie

Falls `NX_Work.durationMs` später aktualisiert wird (z.B. durch Backfill), sollte `NX_WorkUserState.totalDurationMs` synchronisiert werden:

```kotlin
/**
 * Syncs totalDurationMs from Work to existing UserStates.
 * Call after updating Work.durationMs (e.g., backfill).
 */
suspend fun syncDurationToUserStates(workKey: String, durationMs: Long) {
    userStateBox.query()
        .equal(NX_WorkUserState_.workKey, workKey)
        .build()
        .find()
        .forEach { state ->
            if (state.totalDurationMs != durationMs) {
                userStateBox.put(state.copy(totalDurationMs = durationMs))
            }
        }
}
```

---

### 5.2 Optionale Verbesserungen

#### 🟡 season/episode Sync-Garantie

```kotlin
/**
 * Updates the relation's season/episode to match the child work.
 * Call this after creating/updating episode works.
 */
fun NX_WorkRelation.syncFromChildWork() {
    val child = this.childWork.target ?: return
    this.season = child.season
    this.episode = child.episode
}
```

#### 🟡 needsReview Umbenennung

```kotlin
// Konsistenter mit anderen Booleans:
var requiresReview: Boolean = false
// oder
var isReviewNeeded: Boolean = false
```

---

### 5.3 Helper-Funktionen für SSOT-Zugriff

```kotlin
// In NxEntityExtensions.kt

/**
 * Calculates progress percentage (0.0 - 1.0).
 * Uses NX_Work.durationMs as SSOT for duration.
 */
fun NX_WorkUserState.progressPercent(work: NX_Work): Float {
    val duration = work.durationMs ?: return 0f
    if (duration == 0L) return 0f
    return (resumePositionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
}

/**
 * Returns formatted progress string (e.g., "45:30 / 2:15:00").
 */
fun NX_WorkUserState.progressDisplay(work: NX_Work): String {
    val resumeFormatted = formatDuration(resumePositionMs)
    val totalFormatted = formatDuration(work.durationMs ?: 0L)
    return "$resumeFormatted / $totalFormatted"
}

/**
 * Checks if content was watched to completion (>90%).
 */
fun NX_WorkUserState.isEffectivelyWatched(work: NX_Work): Boolean {
    return progressPercent(work) >= 0.9f || isWatched
}
```

---

## 6. Migration Impact

### 6.1 totalDurationMs Entfernung

**Status:** ⚠️ **Designentscheidung erforderlich** - Duplikat ist aktuell ABSICHTLICH

Nach Code-Analyse zeigt sich: `totalDurationMs` wird **aktiv verwendet** in:

1. **core/model/WorkUserState.kt** - Domain-Modell enthält `totalDurationMs`
2. **NxResumeManager.kt** - Liest `state.totalDurationMs` für `ResumePoint`
3. **NxWorkUserStateRepositoryImpl.kt** - Schreibt `totalDurationMs` bei `updateResumePosition()`
4. **Repository Interface** - `updateResumePosition(profileKey, workKey, positionMs, durationMs)`

**Aktueller Datenfluss:**
```
Player speichert: updateResumePosition(..., positionMs, durationMs)
                         ↓
           NX_WorkUserState.totalDurationMs = durationMs
                         ↓
Player liest: ResumePoint.durationMs = state.totalDurationMs
```

**Betroffene Bereiche:**

| Bereich | Datei | Impact |
|---------|-------|--------|
| Domain Model | `core/model/.../WorkUserState.kt` | Feld entfernen |
| Entity | `core/persistence/.../NxEntities.kt` | Feld entfernen |
| Repository Interface | `NxWorkUserStateRepository.kt` | Signatur ändern |
| Repository Impl | `NxWorkUserStateRepositoryImpl.kt` | Impl anpassen |
| Resume Manager | `NxResumeManager.kt` | JOIN mit Work hinzufügen |

**Option A: Feld behalten (aktueller Zustand)**
- ✅ Kein JOIN nötig für Resume-Position
- ✅ Schneller READ für "Continue Watching"
- ❌ Duplikat kann inkonsistent werden
- ❌ Speicherverbrauch pro User-State-Eintrag

**Option B: Feld entfernen (sauberer)**
- ✅ Single Source of Truth in NX_Work
- ✅ Keine Inkonsistenzgefahr
- ❌ JOIN mit Work bei jeder Resume-Abfrage
- ❌ API-Änderung in Repository

**Empfehlung:**
- Kurzfristig: **Behalten** (funktioniert, keine Bugs)
- Langfristig: **Entfernen** bei nächstem großen Refactor

**Wenn entfernt:**
```kotlin
// VORHER:
interface NxWorkUserStateRepository {
    suspend fun updateResumePosition(
        profileKey: String,
        workKey: String,
        positionMs: Long,
        durationMs: Long,  // ❌ ENTFERNEN
    ): WorkUserState
}

// NACHHER:
interface NxWorkUserStateRepository {
    suspend fun updateResumePosition(
        profileKey: String,
        workKey: String,
        positionMs: Long,
        // durationMs entfernt - aus NX_Work.durationMs ableiten
    ): WorkUserState
}
```

### 6.2 Risikoanalyse

| Risiko | Wahrscheinlichkeit | Mitigation |
|--------|:------------------:|------------|
| Vergessene Code-Stellen | Mittel | grep + IDE-Suche |
| Performance (JOINs) | Niedrig | ObjectBox ist schnell |
| Alte Daten | Kein Risiko | Feld wird einfach ignoriert |

---

## Anhang: Entity-Feld-Matrix

### Felder pro Entity (Übersicht)

| Feld-Typ | NX_Work | NX_WorkSourceRef | NX_WorkVariant | NX_WorkUserState |
|----------|:-------:|:----------------:|:--------------:|:----------------:|
| ID/Keys | 4 | 5 | 3 | 3 |
| Metadata | 16 | 4 | 9 | 0 |
| Source-specific | 0 | 6 | 1 | 0 |
| User State | 0 | 0 | 0 | 8 |
| Timestamps | 2 | 2 | 1 | 3 |
| **TOTAL** | **25** | **16** | **16** | **14** |

### Duplikations-Status

| Feld | Status | Aktion |
|------|:------:|--------|
| totalDurationMs | � DENORMALISIERT | Behalten (Performance) |
| rawTitle vs canonicalTitle | 🟢 KORREKT | Behalten |
| Relation.season/episode | 🟡 DENORMALISIERT | Behalten mit Sync |
| FK-Felder (*Key) | ✅ PATTERN | Standard |

---

## Changelog

| Datum | Änderung |
|-------|----------|
| 2026-01-27 | Initiale Analyse erstellt |
| 2026-01-27 | Nach Code-Review: totalDurationMs ist absichtliche Denormalisierung |

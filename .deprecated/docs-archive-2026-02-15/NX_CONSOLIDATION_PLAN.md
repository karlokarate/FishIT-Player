# NX Mapping Consolidation Plan — Platin Edition

> **Status:** PLAN — Approved for implementation  
> **Scope:** Eliminate ALL duplicate implementations across pipeline, core, infra  
> **Goal:** Every mapping has exactly ONE SSOT, fields never get lost, changes propagate automatically  
> **Date:** 2026-02-11

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Architecture Diagnosis](#2-architecture-diagnosis)
3. [Target Architecture](#3-target-architecture)
4. [Phase 1 — Write Protection (NX_Work Field Guards)](#4-phase-1--write-protection)
5. [Phase 2 — Eliminate Dual Write Paths](#5-phase-2--eliminate-dual-write-paths)
6. [Phase 3 — RecognitionState Fix](#6-phase-3--recognitionstate-fix)
7. [Phase 4 — ImageRef Serialization Unification](#7-phase-4--imageref-serialization-unification)
8. [Phase 5 — Handler-Based Generic Mapper](#8-phase-5--handler-based-generic-mapper)
9. [Phase 6 — Enum Consolidation](#9-phase-6--enum-consolidation)
10. [Phase 7 — Duplicate Removal Checklist](#10-phase-7--duplicate-removal-checklist)
11. [Phase 8 — Automated Consistency Tooling](#11-phase-8--automated-consistency-tooling)
12. [Code Generation / KAPT Analysis](#12-code-generation--kapt-analysis)
13. [Layer Boundary Compliance Map](#13-layer-boundary-compliance-map)
14. [Field Lifecycle Matrix](#14-field-lifecycle-matrix)
15. [Risk Assessment & Rollback Strategy](#15-risk-assessment--rollback-strategy)

---

## 1. Executive Summary

### Problem

Die NX-Persistence-Chain hat **zwei unabhängige Write-Pfade** mit **unterschiedlichem Feld-Mapping**, was dazu führt, dass:

- Felder beim Enrichment überschrieben oder verloren gehen (trailer, releaseDate, isAdult)
- `RecognitionState.CONFIRMED` nach Persist→Read zu `HEURISTIC` wird
- `workType` als `"SERIES_EPISODE"` statt `"EPISODE"` gespeichert wird
- 3 verschiedene ImageRef-Serialisierungsformate koexistieren
- Slug-Generierung mit 2 divergenten Algorithmen arbeitet
- 4 SourceType-Enums und 2 WorkType-Enums existieren
- Tests korrekte Ergebnisse zeigen, die Laufzeit aber andere (fehlerhafte) Pfade nutzt

### Solution Summary

| Was | SSOT-Lösung |
|-----|-------------|
| NX_Work schreiben | `WorkEntityBuilder` → `NxWorkRepository.upsert()` — **einziger** Pfad |
| Enrichment (Detail) | `NxWorkRepository.enrichIfAbsent()` mit Field-Guard |
| workKey erzeugen | `NxKeyGenerator.workKey()` — bereits konsolidiert |
| workType mapping | `MediaTypeMapper.toWorkType()` — überall |
| Slug erzeugen | `NxKeyGenerator.toSlug()` — einziger Algorithmus |
| ImageRef speichern | `ImageRef` direkt auf Entity — kein String-Roundtrip |
| RecognitionState | Entity-Feld `recognitionState: String` statt `needsReview: Boolean` |

---

## 2. Architecture Diagnosis

### 2.1 Aktuelle Write-Pfade

```
PFAD A — Catalog Sync (korrekt, aber ImageRef-Roundtrip-Problem):
┌──────────────────────────────────────────────────────────────────────────────┐
│ Pipeline → RawMediaMetadata → Normalizer → NormalizedMediaMetadata         │
│   → WorkEntityBuilder.build() → NxWorkRepository.Work (Domain)             │
│   → NxWorkRepository.upsert() → WorkMapper.toEntity() → NX_Work (Entity)  │
│                                                                             │
│ Problem: ImageRef → String ("http:url") → parseSerializedImageRef → ImageRef│
│         = Doppelte Serialisierung, potenziell verlustbehaftet              │
│ Problem: recognitionState CONFIRMED → needsReview=false → HEURISTIC        │
└──────────────────────────────────────────────────────────────────────────────┘

PFAD B — Detail Enrichment (fehlerhaft, umgeht Builder):
┌──────────────────────────────────────────────────────────────────────────────┐
│ UnifiedDetailLoaderImpl → Xtream API Detail Call → NormalizedMediaMetadata  │
│   → NxCanonicalMediaRepositoryImpl.upsertCanonicalMedia()                  │
│   → DIREKT auf NX_Work Entity schreiben (bypass WorkEntityBuilder!)        │
│                                                                             │
│ Problem: workType = mediaType.name ("SERIES_EPISODE" statt "EPISODE")      │
│ Problem: trailer, releaseDate, isAdult, recognitionState → NICHT gesetzt   │
│ Problem: Überschreibt bereits korrekte Felder (title, year) blind          │
└──────────────────────────────────────────────────────────────────────────────┘

PFAD C — Backlog Linking (nutzt fehlerhaften Pfad B):
┌──────────────────────────────────────────────────────────────────────────────┐
│ CanonicalLinkingBacklogWorker → canonicalMediaRepository.upsertCanonical()  │
│ = Gleiche Probleme wie Pfad B                                               │
└──────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 Vollständiges Duplikat-Inventar

| Konzept | Duplikate | SSOT-Location |
|---------|-----------|---------------|
| `workKey` erzeugen | 5 `buildWorkKey()` → alle delegieren bereits an `NxKeyGenerator` | `core/persistence` ✅ |
| `WorkType` Enum | 2 Enums (8 vs 7 Werte) | `core/model` ist SSOT, `core/persistence` ist Relikt |
| `SourceType` Enum | 4 Enums über 4 Module | Kein SSOT — pro Layer eigene Definition |
| `toSlug()` | 2 Implementierungen (NFD vs non-NFD) | `NxKeyGenerator.toSlug()` — wird SSOT |
| `RecognitionState` | Enum 4 Werte → Boolean 2 Werte (verlustbehaftet) | Entity-Feld muss erweitert werden |
| ImageRef-Serialisierung | 4 Strategien (JSON-DTO, URI, Prefix, RawURL) | `ImageRefConverter` (JSON-DTO) wird SSOT |
| `kindToWorkType()` | TypeMappers + NxCanonicalMediaRepositoryImpl | `TypeMappers.kt` ist SSOT |
| `buildPlaybackHints()` | WorkVariantMapper + NxCanonicalMediaRepositoryImpl | `WorkVariantMapper` ist SSOT |
| SourceKey-Parsing | `SourceKeyParser` + 5 inline Parser in `UnifiedDetailLoaderImpl` | `SourceKeyParser` ist SSOT |
| NX_Work erstellen/updaten | `WorkEntityBuilder` + `NxCanonicalMediaRepositoryImpl` (direkt) | `WorkEntityBuilder` ist SSOT |

---

## 3. Target Architecture

### 3.1 Layer Map (mit SSOT-Verantwortlichkeiten)

```
┌─────────────────────────────────────────────────────────────┐
│  core/model                                                 │
│  ├─ NxWorkRepository.WorkType          ← ENUM SSOT         │
│  ├─ NxWorkRepository.RecognitionState  ← ENUM SSOT         │
│  ├─ NxWorkRepository.Work              ← DOMAIN MODEL      │
│  ├─ ImageRef (sealed class)            ← TYPE SSOT         │
│  ├─ NormalizedMediaMetadata            ← NORMALIZER OUTPUT  │
│  └─ RawMediaMetadata                   ← PIPELINE OUTPUT    │
├─────────────────────────────────────────────────────────────┤
│  core/persistence                                           │
│  ├─ NX_Work (Entity)                   ← PERSISTENCE SSOT  │
│  │  └─ recognitionState: String (NEW)                       │
│  ├─ NxKeyGenerator                     ← KEY SSOT          │
│  │  └─ workKey(), toSlug(), parseWorkKey()                  │
│  ├─ ImageRefConverter                  ← SERIALIZATION SSOT │
│  └─ NxEnums                            ← DEPRECATED →      │
│     (WorkType entfernen, Rest behalten)                     │
├─────────────────────────────────────────────────────────────┤
│  core/metadata-normalizer                                   │
│  ├─ RegexMediaMetadataNormalizer       ← NORMALIZER SSOT   │
│  └─ FallbackCanonicalKeyGenerator      ← DELEGATE→KeyGen   │
├─────────────────────────────────────────────────────────────┤
│  infra/data-nx                                              │
│  ├─ mapper/                                                 │
│  │  ├─ TypeMappers.kt                  ← ENUM MAPPING SSOT │
│  │  ├─ WorkMapper.kt                  ← ENTITY↔DOMAIN SSOT │
│  │  ├─ SourceKeyParser.kt             ← KEY PARSING SSOT   │
│  │  └─ (alle anderen Mapper)           ← jeweils 1× SSOT   │
│  ├─ writer/                                                 │
│  │  ├─ NxCatalogWriter.kt             ← INGEST SSOT        │
│  │  └─ builder/                                             │
│  │     ├─ WorkEntityBuilder.kt        ← BUILD SSOT         │
│  │     │  └─ ImageRef direkt, kein String-Roundtrip  (NEU) │
│  │     ├─ SourceRefBuilder.kt                               │
│  │     └─ VariantBuilder.kt                                 │
│  ├─ canonical/                                              │
│  │  └─ NxCanonicalMediaRepositoryImpl  ← READ + ENRICH     │
│  │     └─ upsertCanonicalMedia() → DELEGIERT an Builder    │
│  └─ repository/                                             │
│     └─ NxWorkRepositoryImpl.kt        ← PERSISTENCE IMPL   │
│        └─ enrichIfAbsent() NEW                              │
└─────────────────────────────────────────────────────────────┘
```

### 3.2 Einziger Write-Flow (Target)

```
[ANY CALLER]
    │
    ▼
WorkEntityBuilder.build(normalized, workKey, now)
    │  ← EINZIGER Ort der NormalizedMediaMetadata → Work mapped
    │  ← ImageRef DIREKT (kein String-Roundtrip)
    │  ← MediaTypeMapper.toWorkType() für workType
    │  ← RecognitionState korrekt gesetzt
    ▼
NxWorkRepository.Work (Domain Model)
    │
    ├──→ upsert(work)     ← Catalog Sync (Neuanlage + Full Update)
    │
    └──→ enrichIfAbsent(workKey, enrichment)  ← Detail Enrichment
         ← SCHREIBSCHUTZ: überschreibt nur NULL-Felder
         ← AUSNAHME: tmdbId, imdbId dürfen IMMER überschrieben werden
            (kommen erst beim Detail-Call)
```

---

## 4. Phase 1 — Write Protection

### 4.1 Problem

Beim Detail-Enrichment (Pfad B) werden Felder blind überschrieben, die bereits korrekt persistiert sind. Beispiel: `canonicalTitle` wird durch normalisierte API-Title ersetzt, obwohl der Catalog-Sync-Title bereits korrekt war.

### 4.2 Lösung: `enrichIfAbsent()` mit Field Guard

**Neues Interface-Method in `NxWorkRepository`:**

```kotlin
// core/model/.../NxWorkRepository.kt
interface NxWorkRepository {
    /**
     * Enrich an existing work with additional metadata.
     * 
     * WRITE PROTECTION RULES:
     * - IMMUTABLE fields (set once, never overwritten): workKey, workType, canonicalTitle, year
     * - ENRICH-ONLY fields (overwrite only if currently null): poster, backdrop, plot, genres, 
     *   director, cast, rating, durationMs, trailer, releaseDate, season, episode
     * - ALWAYS-UPDATE fields (always overwritten when new value non-null): tmdbId, imdbId, tvdbId,
     *   authorityKey, recognitionState (but only upward: HEURISTIC→CONFIRMED, never downgrade)
     */
    suspend fun enrichIfAbsent(workKey: String, enrichment: Work): Work?
}
```

**Field Guard Kategorien:**

| Kategorie | Felder | Regel |
|-----------|--------|-------|
| **IMMUTABLE** | `workKey`, `workType`, `canonicalTitle`, `canonicalTitleLower`, `year`, `createdAt` | Werden beim `upsert()` gesetzt, `enrichIfAbsent()` ignoriert diese |
| **ENRICH_ONLY** | `poster`, `backdrop`, `thumbnail`, `plot`, `genres`, `director`, `cast`, `rating`, `durationMs`, `trailer`, `releaseDate`, `season`, `episode`, `isAdult` | Nur gesetzt wenn Entity-Feld aktuell `null` / default ist |
| **ALWAYS_UPDATE** | `tmdbId`, `imdbId`, `tvdbId`, `authorityKey` | Überschreiben immer (kommen erst beim Detail-Call) |
| **MONOTONIC_UP** | `recognitionState` | Darf nur aufwärts: `HEURISTIC→CONFIRMED`. Nie `CONFIRMED→HEURISTIC` |
| **AUTO** | `updatedAt` | Immer `System.currentTimeMillis()` |

### 4.3 Implementation in `NxWorkRepositoryImpl`

```kotlin
// infra/data-nx/.../repository/NxWorkRepositoryImpl.kt
override suspend fun enrichIfAbsent(workKey: String, enrichment: Work): Work? {
    return withContext(Dispatchers.IO) {
        val existing = workBox.query(NX_Work_.workKey.equal(workKey)).build().findUnique()
            ?: return@withContext null
        
        // IMMUTABLE: workKey, workType, canonicalTitle, year, createdAt — SKIP
        
        // ENRICH_ONLY: nur wenn Entity-Feld aktuell null/default
        if (existing.poster == null && enrichment.posterRef != null)
            existing.poster = parseSerializedImageRef(enrichment.posterRef)
        if (existing.backdrop == null && enrichment.backdropRef != null)
            existing.backdrop = parseSerializedImageRef(enrichment.backdropRef)
        // ... (alle ENRICH_ONLY Felder)
        
        // ALWAYS_UPDATE: External IDs (kommen erst beim Detail-Call)
        enrichment.tmdbId?.let { existing.tmdbId = it }
        enrichment.imdbId?.let { existing.imdbId = it }
        enrichment.tvdbId?.let { existing.tvdbId = it }
        
        // MONOTONIC_UP: RecognitionState
        val newState = enrichment.recognitionState
        val currentState = RecognitionState.valueOf(
            existing.recognitionState ?: "HEURISTIC"
        )
        if (newState.ordinal < currentState.ordinal) { // CONFIRMED=0 < HEURISTIC=1
            existing.recognitionState = newState.name
        }
        
        existing.updatedAt = System.currentTimeMillis()
        workBox.put(existing)
        existing.toDomain()
    }
}
```

### 4.4 Wann `upsert()` vs `enrichIfAbsent()`?

| Aufrufer | Method | Begründung |
|----------|--------|------------|
| `NxCatalogWriter.ingest()` | `upsert()` | Erstanlage beim Catalog Sync — alle Felder setzen |
| `NxCatalogWriter.ingestBatchOptimized()` | `upsert()` | Batch-Erstanlage |
| `NxCanonicalMediaRepositoryImpl.upsertCanonicalMedia()` | `enrichIfAbsent()` | Detail-Enrichment — nur fehlende Felder ergänzen |
| `CanonicalLinkingBacklogWorker` | `enrichIfAbsent()` | Nachträgliche TMDB-Verlinkung |
| `UnifiedDetailLoaderImpl` | Ruft `canonicalMediaRepo.upsertCanonicalMedia()` → wird zu `enrichIfAbsent()` | Detail-Screen Enrichment |

### 4.5 Dateien betroffen

| Datei | Änderung |
|-------|----------|
| `core/model/.../NxWorkRepository.kt` | `enrichIfAbsent()` Signatur hinzufügen |
| `infra/data-nx/.../repository/NxWorkRepositoryImpl.kt` | `enrichIfAbsent()` implementieren |
| `infra/data-nx/.../canonical/NxCanonicalMediaRepositoryImpl.kt` | `upsertCanonicalMedia()` → `enrichIfAbsent()` |

---

## 5. Phase 2 — Eliminate Dual Write Paths

### 5.1 Problem

`NxCanonicalMediaRepositoryImpl.upsertCanonicalMedia()` schreibt **direkt** auf die ObjectBox-Entity `NX_Work` — umgeht `WorkEntityBuilder` und `NxWorkRepository`. Dabei:
- `workType = mediaType.name` statt `MediaTypeMapper.toWorkType()` → `"SERIES_EPISODE"` statt `"EPISODE"`
- `trailer`, `releaseDate`, `isAdult` nicht gesetzt
- `recognitionState`/`needsReview` nicht gesetzt
- Eigene inline ImageRef-Zuweisung statt Builder

### 5.2 Lösung

`upsertCanonicalMedia()` nutzt ab sofort `WorkEntityBuilder` + `NxWorkRepository.enrichIfAbsent()`:

```kotlin
// VORHER (direkt auf Entity):
override suspend fun upsertCanonicalMedia(normalized: NormalizedMediaMetadata): CanonicalMediaId {
    val workKey = buildWorkKey(normalized)
    val work = workBox.query(NX_Work_.workKey.equal(workKey)).build().findUnique()
        ?: NX_Work()
    work.workKey = workKey
    work.workType = normalized.mediaType.name  // ← BUG: falsch
    work.canonicalTitle = normalized.canonicalTitle
    work.poster = normalized.poster
    // ... (KEINE trailer, releaseDate, isAdult!)
    workBox.put(work)
}

// NACHHER (über Builder + Repository):
override suspend fun upsertCanonicalMedia(normalized: NormalizedMediaMetadata): CanonicalMediaId {
    val workKey = buildWorkKey(normalized)
    val now = System.currentTimeMillis()
    val enrichment = workEntityBuilder.build(normalized, workKey, now)
    
    // Versuche Enrichment (nur fehlende Felder ergänzen)
    val result = workRepository.enrichIfAbsent(workKey, enrichment)
    
    if (result == null) {
        // Work existiert noch nicht → Full Upsert
        val created = workRepository.upsert(enrichment)
        return CanonicalMediaId(created.workKey)
    }
    return CanonicalMediaId(result.workKey)
}
```

### 5.3 Zu entfernende Duplikate in NxCanonicalMediaRepositoryImpl

| Method | Zeilen | Ersetzt durch |
|--------|--------|---------------|
| `kindToWorkType()` | L772-775 | `MediaTypeMapper.toWorkType()` aus TypeMappers.kt |
| `workTypeToKind()` | L767-770 | `MediaTypeMapper.toMediaKind()` (NEU in TypeMappers) |
| `mediaTypeToKind()` | L761-765 | `MediaTypeMapper.toMediaKind()` |
| `buildPlaybackHints()` | L665-680 | `WorkVariantMapper.buildPlaybackHints()` (extrahiert als top-level) |
| `buildLegacyPlaybackHints()` | L682-689 | `WorkVariantMapper.buildLegacyPlaybackHints()` (extrahiert) |
| `calculatePriority()` | L691-710 | `SourcePriorityCalculator.calculate()` aus TypeMappers.kt |
| `buildSourceLabel()` | L641-663 | `SourceLabelBuilder.build()` aus TypeMappers.kt |

### 5.4 Dependency: WorkEntityBuilder Injection

`NxCanonicalMediaRepositoryImpl` braucht `WorkEntityBuilder` als Dependency:

```kotlin
@Singleton
class NxCanonicalMediaRepositoryImpl @Inject constructor(
    boxStore: BoxStore,
    private val workRepository: NxWorkRepository,       // NEU
    private val workEntityBuilder: WorkEntityBuilder,   // NEU
) : CanonicalMediaRepository
```

**Layer-Boundary-Check:** `infra/data-nx` hat bereits `implementation(project(":core:persistence"))` und `api(project(":core:model"))`. `WorkEntityBuilder` lebt in `infra/data-nx/writer/builder/` — gleiche Modul, keine neue Dependency nötig. ✅

---

## 6. Phase 3 — RecognitionState Fix

### 6.1 Problem

```
Entity (NX_Work): needsReview: Boolean   ← 2 Zustände (true/false)
Domain (Work):    RecognitionState Enum   ← 4 Zustände (CONFIRMED, HEURISTIC, NEEDS_REVIEW, UNPLAYABLE)

Mapping: CONFIRMED → false → HEURISTIC (❌ Information verloren!)
```

### 6.2 Lösung: Entity-Feld-Migration

**Schritt 1:** Neues String-Feld auf Entity:

```kotlin
// NxEntities.kt — NX_Work
@Index var recognitionState: String = "HEURISTIC"  // NEU: CONFIRMED|HEURISTIC|NEEDS_REVIEW|UNPLAYABLE
var needsReview: Boolean = false                    // DEPRECATED — nur noch für Migration
```

**Schritt 2:** WorkMapper aktualisieren:

```kotlin
// WorkMapper.kt
fun NX_Work.toDomain(): Work = Work(
    // ...
    recognitionState = RecognitionState.entries
        .find { it.name == this.recognitionState }
        ?: if (this.needsReview) RecognitionState.NEEDS_REVIEW   // Fallback Migration
           else RecognitionState.HEURISTIC,
)

fun Work.toEntity(existing: NX_Work? = null): NX_Work = (existing ?: NX_Work()).apply {
    // ...
    recognitionState = this@toEntity.recognitionState.name       // String SSOT
    needsReview = this@toEntity.recognitionState == RecognitionState.NEEDS_REVIEW  // Legacy compat
}
```

**Schritt 3:** Nach Migration `needsReview` aus Entity entfernen (ObjectBox ignoriert nicht mehr zugewiesene Felder ohne Datenverlust).

### 6.3 Dateien betroffen

| Datei | Änderung |
|-------|----------|
| `core/persistence/.../NxEntities.kt` | `recognitionState: String` hinzufügen |
| `infra/data-nx/.../mapper/WorkMapper.kt` | Beide Mapping-Richtungen aktualisieren |
| `infra/data-nx/.../writer/builder/WorkEntityBuilder.kt` | Bereits korrekt (setzt `RecognitionState`) |

---

## 7. Phase 4 — ImageRef Serialization Unification

### 7.1 Problem: 4 koexistierende Serialisierungsstrategien

```
1. ImageRefConverter     → JSON-DTO  {"type":"http","url":"..."}    ← ObjectBox
2. toSerializedString()  → Prefix    "http:url"                     ← WorkEntityBuilder
3. toUrlString()         → Raw URL   "https://..."                  ← WorkMapper.toDomain()
4. ImageRef.toUriString()→ URI       "tg://thumb/remoteId?chatId=x" ← Fallback
```

Der aktuelle Chain:
```
WorkEntityBuilder: ImageRef → toSerializedString() → "http:url" (String)
    → Work.posterRef = "http:url"
    → WorkMapper.toEntity(): parseSerializedImageRef("http:url") → ImageRef
    → ImageRefConverter: ImageRef → JSON-DTO → ObjectBox
```

Das ist ein **unnötiger Roundtrip**: ImageRef → String → ImageRef → JSON.

### 7.2 Lösung: Domain Model Work speichert ImageRef direkt

**Schritt 1:** `NxWorkRepository.Work` erhält `ImageRef?` statt `String?`:

```kotlin
// core/model/.../NxWorkRepository.kt
data class Work(
    // ... Identity, Core ...
    val poster: ImageRef? = null,          // WAR: posterRef: String?
    val backdrop: ImageRef? = null,        // WAR: backdropRef: String?
    val thumbnail: ImageRef? = null,       // WAR: thumbnailRef: String?
    // ... Rest ...
)
```

**Schritt 2:** `WorkEntityBuilder` setzt `ImageRef` direkt:

```kotlin
// WorkEntityBuilder.kt
fun build(normalized: NormalizedMediaMetadata, ...): Work = Work(
    // ...
    poster = normalized.poster,        // WAR: posterRef = normalized.poster?.toSerializedString()
    backdrop = normalized.backdrop,
    thumbnail = normalized.thumbnail,
    // ...
)
```

**Schritt 3:** `WorkMapper` wird trivial:

```kotlin
// WorkMapper.kt
fun NX_Work.toDomain(): Work = Work(
    poster = this.poster,              // WAR: posterRef = this.poster?.toUrlString()
    backdrop = this.backdrop,
    thumbnail = this.thumbnail,
)

fun Work.toEntity(existing: NX_Work?): NX_Work = (existing ?: NX_Work()).apply {
    poster = this@toEntity.poster ?: existing?.poster       // WAR: parseSerializedImageRef()
    backdrop = this@toEntity.backdrop ?: existing?.backdrop
    thumbnail = this@toEntity.thumbnail ?: existing?.thumbnail
}
```

**Schritt 4:** Entfernen:
- `WorkEntityBuilder.toSerializedString()` (private extension)
- `WorkMapper.parseSerializedImageRef()` (private fun)
- `WorkMapper.toUrlString()` (private extension)
- Duplicate `toUriString()` in `ImageRefConverter` (delegate an `ImageRef.toUriString()`)

### 7.3 Impact-Analyse

Alle Verbraucher von `Work.posterRef: String?` müssen auf `Work.poster: ImageRef?` umgestellt werden. Betroffene Stellen:

| Verbraucher | Aktuell | Nachher |
|-------------|---------|---------|
| `WorkEntityBuilder` | Schreibt String | Schreibt ImageRef — trivial |
| `WorkMapper.toEntity()` | Parsed String → ImageRef | Direkter Copy — einfacher |
| `WorkMapper.toDomain()` | Konvertiert ImageRef → String | Direkter Copy — einfacher |
| `NxCanonicalMediaRepositoryImpl.mapToCanonicalMediaWithSources()` | Liest `work.poster` (Entity-ImageRef direkt) | Keine Änderung nötig |
| UI-Layer (Compose) | Erhält String URL → AsyncImage | Benötigt ImageRef-Resolver (existiert bereits: `ImageRefResolver`) |

### 7.4 Dateien betroffen

| Datei | Änderung |
|-------|----------|
| `core/model/.../NxWorkRepository.kt` | `posterRef: String?` → `poster: ImageRef?` (3 Felder) |
| `infra/data-nx/.../writer/builder/WorkEntityBuilder.kt` | `toSerializedString()` entfernen, ImageRef direkt |
| `infra/data-nx/.../mapper/WorkMapper.kt` | `toUrlString()`, `parseSerializedImageRef()` entfernen |
| `core/persistence/.../converter/ImageRefConverter.kt` | Duplicate `toUriString()` entfernen → delegate |
| Alle `Work.posterRef`-Verbraucher | Suche nach `.posterRef`, `.backdropRef`, `.thumbnailRef` |

---

## 8. Phase 5 — Handler-Based Generic Mapper

### 8.1 Ziel

Statt 18 monolithische Mapper-Dateien mit jeweils `toDomain()` und `toEntity()` Extensions, die alle subtil unterschiedliche Patterns nutzen, ein **Handler-basiertes System** das:
- Gemeinsame Mapping-Logik shared (Enum-Conversion, Timestamp-Handling, ImageRef)
- Pro Entity nur die **entity-spezifischen Felder** deklariert
- Änderungen an gemeinsamer Logik automatisch alle Mapper betrifft

### 8.2 Design: FieldHandler Registry

```kotlin
// infra/data-nx/mapper/base/FieldHandler.kt

/**
 * A handler that maps a single field between domain and entity representations.
 * Shared handlers (enum conversion, timestamps, imageRef) are reused across mappers.
 */
interface FieldHandler<E, D, V> {
    /** Extract value from entity */
    fun fromEntity(entity: E): V?
    /** Apply value to domain builder */
    fun toDomain(value: V?, builder: D)
    /** Apply value to entity, respecting existing values */
    fun toEntity(value: V?, entity: E, existing: E?)
}

/**
 * Pre-built shared handlers — SSOT for common mapping logic.
 */
object SharedHandlers {
    /** Enum↔String handler factory */
    fun <T : Enum<T>> enumString(
        getter: (E) -> String?,
        setter: (E, String) -> Unit,
        valueOf: (String) -> T?,
        default: T,
    ): FieldHandler<...>
    
    /** ImageRef direct-copy handler */
    val imageRef = object : FieldHandler<...> { ... }
    
    /** Timestamp handler (preserve existing createdAt, always update updatedAt) */
    val createdAt = object : FieldHandler<...> { ... }
    val updatedAt = object : FieldHandler<...> { ... }
    
    /** Monotonic RecognitionState handler (only upgrades, never downgrades) */
    val recognitionState = object : FieldHandler<...> { ... }
}
```

### 8.3 Pragmatische Empfehlung

Nach detaillierter Analyse der 18 bestehenden Mapper ist die **Realität**: Die meisten Mapper sind einfache Property-Kopien mit 15-50 Zeilen. Ein generisches Handler-Framework würde **mehr Boilerplate** erzeugen als es einspart.

**Stattdessen empfohlenes Pattern — Shared Utility Functions:**

```kotlin
// infra/data-nx/mapper/base/MappingUtils.kt
object MappingUtils {
    /** Enum↔String mit safe fallback */
    inline fun <reified T : Enum<T>> safeEnumFromString(
        value: String?, default: T
    ): T = value?.let { 
        enumValues<T>().find { e -> e.name.equals(it, ignoreCase = true) }
    } ?: default
    
    /** Write-once guard: nur wenn Entity-Feld null */
    fun <T> enrichOnly(existing: T?, new: T?): T? = existing ?: new
    
    /** Always-update: neuer Wert überschreibt */
    fun <T> alwaysUpdate(existing: T?, new: T?): T? = new ?: existing
    
    /** Monotonic upgrade: nur aufwärts */
    fun <T : Comparable<T>> monotonicUp(existing: T?, new: T?): T? = 
        when {
            existing == null -> new
            new == null -> existing
            new < existing -> new  // Lower ordinal = higher priority
            else -> existing
        }
}
```

Dann nutzen alle Mapper diese Utils:

```kotlin
// WorkMapper.kt — VORHER:
recognitionState = if (needsReview) RecognitionState.NEEDS_REVIEW else RecognitionState.HEURISTIC

// WorkMapper.kt — NACHHER:
recognitionState = MappingUtils.safeEnumFromString(this.recognitionState, RecognitionState.HEURISTIC)
```

### 8.4 Was wird tatsächlich shared?

| Shared Logic | Genutzt von | Lines gespart |
|-------------|-------------|---------------|
| `safeEnumFromString()` | WorkMapper, SourceRefMapper, VariantMapper, RelationMapper | ~40 |
| `enrichOnly()` | `enrichIfAbsent()`, WorkMapper.toEntity() | ~20 |
| `alwaysUpdate()` | WorkMapper.toEntity() (external IDs) | ~10 |
| `monotonicUp()` für RecognitionState | WorkMapper, enrichIfAbsent() | ~15 |
| Timestamp-Handling | WorkMapper, SourceRefMapper, VariantMapper, UserStateMapper | ~30 |

**Netto-Effekt:** ~115 Zeilen Code-Reduktion + **eine SSOT für jedes Mapping-Pattern**, statt 18× leicht unterschiedliche Implementierungen.

### 8.5 Dateien betroffen

| Datei | Änderung |
|-------|----------|
| `infra/data-nx/mapper/base/MappingUtils.kt` | NEU — Shared Utilities |
| Alle 18 Mapper in `infra/data-nx/mapper/` | Import + Nutzung von MappingUtils |
| TypeMappers.kt | Enum-Mappings nutzen `safeEnumFromString()` |

---

## 9. Phase 6 — Enum Consolidation

### 9.1 WorkType

| Aktion | Detail |
|--------|--------|
| `NxWorkRepository.WorkType` (core/model) | **SSOT behalten** — 8 Werte |
| `NxEnums.WorkType` (core/persistence) | **Entfernen** — Entity speichert String, `WorkTypeMapper` mapped |
| `NxKeyGenerator.workKey()` | Bereits auf `NxWorkRepository.WorkType` umgestellt ✅ |
| Entity `NX_Work.workType: String` | Bleibt String — `WorkTypeMapper.toEntityString()` schreibt |

**Nur zu prüfen:** Hat NxEnums.WorkType noch Verbraucher? → Suche und Migration.

### 9.2 SourceType (4 Enums)

Vollständige Konsolidierung ist **nicht empfohlen**, da jeder Layer bewusst seinen eigenen Subset hat:

| Enum | Layer | Behalten? | Begründung |
|------|-------|-----------|------------|
| `RawMediaMetadata.SourceType` | core/model | ✅ | Pipeline-Input |
| `NxWorkSourceRefRepository.SourceType` | core/model | ✅ | Domain-Model → **WIRD SSOT** |
| `NxEnums.SourceType` | core/persistence | ❌ → Entfernen | Entity speichert String |
| `core.playermodel.SourceType` | core/player-model | ✅ | Playback-spezifisch (FILE, HTTP) |

**Mapping:** `SourceTypeMapper` in TypeMappers.kt mapped zwischen den 3 verbleibenden Enums.

### 9.3 RecognitionState

Siehe [Phase 3](#6-phase-3--recognitionstate-fix) — Entity-Feld wird String, Enum bleibt in `core/model`.

### 9.4 Dateien betroffen

| Datei | Änderung |
|-------|----------|
| `core/persistence/.../NxEnums.kt` | `WorkType` enum entfernen, `SourceType` enum entfernen |
| Alle Verbraucher von `NxEnums.WorkType` | Migrate zu `NxWorkRepository.WorkType` |
| Alle Verbraucher von `NxEnums.SourceType` | Migrate zu String oder `NxWorkSourceRefRepository.SourceType` |

---

## 10. Phase 7 — Duplicate Removal Checklist

### 10.1 Code-Duplikate

| # | Duplikat | Location | Ersetzt durch | Status |
|---|----------|----------|---------------|--------|
| 1 | `FallbackCanonicalKeyGenerator.toSlug()` | core/metadata-normalizer | Delegate → `NxKeyGenerator.toSlug()` | TODO |
| 2 | `NxCanonicalMediaRepositoryImpl.kindToWorkType()` | infra/data-nx/canonical | `MediaTypeMapper` (TypeMappers.kt) | TODO |
| 3 | `NxCanonicalMediaRepositoryImpl.workTypeToKind()` | infra/data-nx/canonical | `MediaTypeMapper.toMediaKind()` NEU | TODO |
| 4 | `NxCanonicalMediaRepositoryImpl.mediaTypeToKind()` | infra/data-nx/canonical | `MediaTypeMapper.toMediaKind()` | TODO |
| 5 | `NxCanonicalMediaRepositoryImpl.buildPlaybackHints()` | infra/data-nx/canonical | `WorkVariantMapper` (extrahiert) | TODO |
| 6 | `NxCanonicalMediaRepositoryImpl.buildLegacyPlaybackHints()` | infra/data-nx/canonical | `WorkVariantMapper` (extrahiert) | TODO |
| 7 | `NxCanonicalMediaRepositoryImpl.calculatePriority()` | infra/data-nx/canonical | `SourcePriorityCalculator` (TypeMappers.kt) | TODO |
| 8 | `NxCanonicalMediaRepositoryImpl.buildSourceLabel()` | infra/data-nx/canonical | `SourceLabelBuilder` (TypeMappers.kt) | TODO |
| 9 | `UnifiedDetailLoaderImpl.parseSeriesIdFromSourceKey()` | infra/data-detail | `SourceKeyParser` (infra/data-nx) | TODO |
| 10 | `UnifiedDetailLoaderImpl.parseVodIdFromSourceKey()` | infra/data-detail | `SourceKeyParser` | TODO |
| 11 | `UnifiedDetailLoaderImpl.parseAccountKeyFromSourceKey()` | infra/data-detail | `SourceKeyParser` | TODO |
| 12 | `UnifiedDetailLoaderImpl.parseServerUrlFromAccountKey()` | infra/data-detail | `SourceKeyParser` | TODO |
| 13 | `UnifiedDetailLoaderImpl.parseSourceType()` | infra/data-detail | `SourceKeyParser` | TODO |
| 14 | `ImageRefConverter.toUriString()` (private copy) | core/persistence | Delegate → `ImageRef.toUriString()` (core/model) | TODO |
| 15 | `WorkMapper.toUrlString()` (private extension) | infra/data-nx | Entfällt mit Phase 4 (ImageRef direkt) | TODO |
| 16 | `WorkMapper.parseSerializedImageRef()` (private fun) | infra/data-nx | Entfällt mit Phase 4 | TODO |
| 17 | `WorkEntityBuilder.toSerializedString()` (private ext) | infra/data-nx | Entfällt mit Phase 4 | TODO |
| 18 | `NxCanonicalMediaRepositoryImpl` direktes Entity-Schreiben | infra/data-nx/canonical | `WorkEntityBuilder` + `enrichIfAbsent()` | TODO |

### 10.2 Dokumentations-/Kommentar-Duplikate

| # | Wo | Was entfernen |
|---|-----|---------------|
| 1 | `NxCatalogWriter.buildWorkKey()` KDoc | Alter Format-Kommentar (bereits aktualisiert) |
| 2 | `NxCanonicalMediaRepositoryImpl.buildWorkKey()` KDoc | "Aligned with NxCatalogWriter" → "Delegates to NxKeyGenerator" |
| 3 | `FullChainGoldenFileTest.buildWorkKey()` KDoc | Alter heuristic-Kommentar |
| 4 | `MappingChainPropertyTest.buildWorkKey()` KDoc | Alter heuristic-Kommentar |
| 5 | Alle Files mit `"buggy contains() heuristic"` Kommentaren | Entfernen — nicht mehr relevant |

### 10.3 Layer-Boundary Dependency

Für Duplikat #1 (`FallbackCanonicalKeyGenerator` → `NxKeyGenerator.toSlug()`):

`core/metadata-normalizer` kann **NICHT** direkt auf `core/persistence` zugreifen (Layer-Verletzung). Lösung:

**Option A — toSlug() nach core/model verschieben:**
```
core/model/util/SlugGenerator.kt  ← NxKeyGenerator delegiert hierher
                                   ← FallbackCanonicalKeyGenerator delegiert hierher
```

**Option B — Interface in core/model, Impl in core/persistence:**
```kotlin
// core/model/util/SlugGenerator.kt
fun interface SlugGenerator {
    fun toSlug(title: String): String
}

// core/persistence/.../NxKeyGenerator.kt
object NxKeyGenerator : SlugGenerator { ... }
```

**Empfohlen: Option A** — `toSlug()` ist eine reine String-Transformation ohne Persistence-Abhängigkeiten. Sie gehört logisch in `core/model/util/`.

---

## 11. Phase 8 — Automated Consistency Tooling

### 11.1 Problem

Wenn ein Feld zu `NormalizedMediaMetadata` hinzugefügt wird, müssen Änderungen in 4+ Dateien erfolgen:
1. `NormalizedMediaMetadata.kt` (Feld)
2. `WorkEntityBuilder.kt` (Mapping)
3. `NX_Work` Entity (Feld)
4. `WorkMapper.kt` (Entity↔Domain)
5. Tests (Builder-Tests, Golden-Tests, Property-Tests)

### 11.2 Lösung A: KSP-basierter Mapper-Generator (EMPFOHLEN)

Ein **custom KSP Processor** der aus annotierten Feld-Deklarationen automatisch Mapper-Code generiert:

```kotlin
// core/model/.../NxWorkRepository.kt
data class Work(
    @MapField(entity = "workKey", immutable = true)
    val workKey: String,
    
    @MapField(entity = "workType", handler = WorkTypeHandler::class)
    val type: WorkType,
    
    @MapField(entity = "canonicalTitle", immutable = true)
    val displayTitle: String,
    
    @MapField(entity = "poster", enrichOnly = true)
    val poster: ImageRef? = null,
    
    @MapField(entity = "tmdbId", alwaysUpdate = true)
    val tmdbId: String? = null,
    
    @MapField(entity = "recognitionState", handler = RecognitionStateHandler::class, monotonicUp = true)
    val recognitionState: RecognitionState = RecognitionState.HEURISTIC,
)
```

Der Generator erzeugt:
- `WorkMapper_Generated.kt` — `toDomain()` und `toEntity()`
- `WorkEnricher_Generated.kt` — `enrichIfAbsent()` mit allen Field Guards
- Compile-time Fehler wenn Entity-Feld fehlt oder Typ nicht passt

**Aufwand:** ~2-3 Tage für den KSP-Processor. Danach: **Eine Annotation-Änderung → alle Mapper automatisch aktualisiert.**

### 11.3 Lösung B: Schema-basierter Code-Generator (ALTERNATIV)

Eine YAML/JSON Schema-Datei als SSOT:

```yaml
# .schema/nx-work-mapping.yaml
entity: NX_Work
domain: NxWorkRepository.Work
fields:
  - name: workKey
    entity_field: workKey
    protection: IMMUTABLE
  - name: type
    entity_field: workType
    handler: WorkTypeMapper
    protection: IMMUTABLE
  - name: poster
    entity_field: poster
    type: ImageRef
    protection: ENRICH_ONLY
  - name: tmdbId
    entity_field: tmdbId
    protection: ALWAYS_UPDATE
```

Ein Python-Script generiert daraus den Mapper-Code:

```bash
python3 tools/generate-mappers.py .schema/nx-work-mapping.yaml \
  --output infra/data-nx/src/main/java/.../mapper/
```

**Aufwand:** ~1 Tag. Weniger elegant als KSP, aber sofort nutzbar.

### 11.4 Lösung C: Architecture Test (MINIMAL, sofort umsetzbar)

Ein Test der zur Compile-Time prüft, dass alle Felder gemapped werden:

```kotlin
// infra/data-nx/src/test/java/.../MappingCompletenessTest.kt
class MappingCompletenessTest {
    @Test
    fun `all NormalizedMediaMetadata fields are mapped in WorkEntityBuilder`() {
        val normalizedFields = NormalizedMediaMetadata::class.memberProperties.map { it.name }.toSet()
        val builderMappedFields = setOf(
            "canonicalTitle", "mediaType", "year", "season", "episode",
            "tmdb", "externalIds", "poster", "backdrop", "thumbnail",
            "plot", "genres", "director", "cast", "rating", "durationMs",
            "trailer", "releaseDate", "isAdult", "addedTimestamp",
            // Explicitly unmapped (justified):
            "placeholderThumbnail", // stored in variant, not work
            "epgChannelId",        // stored in sourceRef
            "tvArchive",           // stored in sourceRef
            "tvArchiveDuration",   // stored in sourceRef
            "categoryId",         // stored separately
        )
        val unmapped = normalizedFields - builderMappedFields
        assertTrue(
            unmapped.isEmpty(),
            "Unmapped NormalizedMediaMetadata fields in WorkEntityBuilder: $unmapped\n" +
            "Add mapping or add to justified-unmapped list with reason."
        )
    }
    
    @Test
    fun `all NX_Work fields are covered by WorkMapper toDomain`() { ... }
    
    @Test  
    fun `all Work fields are covered by WorkMapper toEntity`() { ... }
}
```

**Aufwand:** 30 Minuten. Fängt sofort ab, wenn Felder hinzugefügt aber nicht gemapped werden.

### 11.5 Empfohlene Kombination

| Sofort | Kurzfristig | Langfristig |
|--------|-------------|-------------|
| **Lösung C** (Architecture Tests) | **Lösung B** (Schema-Generator) | **Lösung A** (KSP-Processor) |
| 30 Min | 1 Tag | 2-3 Tage |
| Fängt Lücken auf | Generiert Code | Vollautomatisch |

---

## 12. Code Generation / KAPT Analysis

### 12.1 Ergebnis

| Generator | Modul | Risiko |
|-----------|-------|--------|
| **Hilt (KSP)** | 40 Module | ✅ Kein Duplikat-Risiko — alle Bindings unique |
| **ObjectBox (KAPT)** | core/persistence | ✅ Kein Duplikat-Risiko — generiert Cursors/Properties in build/ |
| **Kotlin Serialization** | 3 Module | ✅ Compiler-Plugin, kein KSP/KAPT |

### 12.2 KAPT+KSP Koexistenz in core/persistence

Das Modul nutzt KAPT (ObjectBox) + KSP (Hilt) gleichzeitig. Aktuelle Workarounds:
- Manuelle Task-Dependencies (`compileDebugJavaWithJavac.dependsOn(kaptDebugKotlin)`)
- Caching deaktiviert (`outputs.cacheIf { false }`)
- Source-Sets manuell konfiguriert

**Empfehlung:** ObjectBox Plant Migration auf KSP (Issue #1285). Bis dahin: aktuelle Konfiguration beibehalten, sie funktioniert stabil.

### 12.3 Kein generierter Code verursacht Duplikate

Die generierten `_`-Klassen (ObjectBox Properties) und Hilt `_HiltModules` leben ausschließlich in `build/generated/` und überschreiben keine manuellen Implementierungen. **Kein Handlungsbedarf.**

---

## 13. Layer Boundary Compliance Map

### 13.1 Aktuelle Violations

| Von | Nach | Art | Fix |
|-----|------|-----|-----|
| `core/metadata-normalizer` | `core/persistence` (NxKeyGenerator.toSlug) | Import-Verletzung (wenn wir FallbackKeyGen konsolidieren) | `toSlug()` → `core/model/util/` verschieben |
| `infra/data-detail` | `infra/data-nx/mapper/SourceKeyParser` | Kein Import, daher inline-Duplikate | SourceKeyParser nach `core/model/util/` oder als Dependency |
| `NxCanonicalMediaRepositoryImpl` | Direkt auf `NX_Work` Entity | Umgehung des Repository-Patterns | Via `NxWorkRepository.enrichIfAbsent()` |

### 13.2 Korrigierte Architektur

```
                    ┌──────────────┐
                    │  core/model  │ ← SlugGenerator, KeyParser (SharedUtils)
                    └──────┬───────┘
                           │
              ┌────────────┼────────────┐
              ▼            ▼            ▼
    ┌─────────────┐ ┌────────────┐ ┌─────────────────┐
    │core/metadata│ │core/persist│ │core/catalog-sync │
    │ -normalizer │ │  -ence     │ │                  │
    └──────┬──────┘ └──────┬─────┘ └────────┬─────────┘
           │               │                │
           └───────┬───────┘                │
                   ▼                        │
           ┌──────────────┐                 │
           │ infra/data-nx│ ← ALL Mapper    │
           │  ├─ mapper/  │    ALL Builder  │
           │  ├─ writer/  │    ALL Repos    │
           │  └─ canonical│                 │
           └──────┬───────┘                 │
                  │                         │
                  └────────────┬────────────┘
                               ▼
                   ┌──────────────────┐
                   │  infra/data-detail│ ← UnifiedDetailLoader
                   │  (nutzt SourceKey │    (nutzt core/model Utils)
                   │   Parser aus      │
                   │   core/model)     │
                   └──────────────────┘
```

### 13.3 toSlug() Migration (Layer-konform)

```kotlin
// NEUER ORT: core/model/src/main/java/.../util/SlugGenerator.kt
package com.fishit.player.core.model.util

import java.text.Normalizer
import java.util.Locale

/**
 * SSOT for slug generation across ALL modules.
 * Uses NFD Unicode normalization for correct diacritics handling.
 */
object SlugGenerator {
    fun toSlug(title: String): String =
        Normalizer.normalize(title, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "")              // Remove diacritics
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), "-")          // Non-alphanum → dash
            .trim('-')
            .ifEmpty { "untitled" }
}
```

```kotlin
// core/persistence/.../NxKeyGenerator.kt — delegiert:
fun toSlug(title: String): String = SlugGenerator.toSlug(title)

// core/metadata-normalizer/.../FallbackCanonicalKeyGenerator.kt — delegiert:
private fun toSlug(title: String): String = SlugGenerator.toSlug(title)
```

---

## 14. Field Lifecycle Matrix

### 14.1 Wann werden welche NX_Work-Felder gesetzt?

| NX_Work Feld | Catalog Listing Sync | Detail-Enrichment | TMDB-Enrichment | Backlog-Linking |
|---|---|---|---|---|
| `workKey` | ✅ IMMUTABLE | — | — | — |
| `workType` | ✅ IMMUTABLE | — | — | — |
| `canonicalTitle` | ✅ IMMUTABLE | — | — | — |
| `year` | ✅ IMMUTABLE | — | — | — |
| `season` | ✅ (wenn bekannt) | ENRICH_ONLY | — | — |
| `episode` | ✅ (wenn bekannt) | ENRICH_ONLY | — | — |
| `poster` | ✅ (streamIcon/cover) | ENRICH_ONLY (movieImage) | ENRICH_ONLY | — |
| `backdrop` | ⚠️ oft null | ENRICH_ONLY ✅ | ENRICH_ONLY | — |
| `thumbnail` | ⚠️ oft null | ENRICH_ONLY | — | — |
| `plot` | ⚠️ manchmal | ENRICH_ONLY ✅ | ENRICH_ONLY | — |
| `genres` | ⚠️ manchmal | ENRICH_ONLY ✅ | ENRICH_ONLY | — |
| `director` | ❌ null | ENRICH_ONLY ✅ | — | — |
| `cast` | ❌ null | ENRICH_ONLY ✅ | — | — |
| `rating` | ✅ (rating5Based) | ENRICH_ONLY | ENRICH_ONLY | — |
| `durationMs` | ⚠️ manchmal | ENRICH_ONLY ✅ | — | — |
| `trailer` | ❌ null | ENRICH_ONLY ✅ | — | — |
| `releaseDate` | ❌ null | ENRICH_ONLY ✅ | — | — |
| `tmdbId` | ⚠️ manche Panels | ALWAYS_UPDATE ✅ | ALWAYS_UPDATE ✅ | ALWAYS_UPDATE ✅ |
| `imdbId` | ❌ null | ALWAYS_UPDATE ✅ | ALWAYS_UPDATE ✅ | ALWAYS_UPDATE ✅ |
| `tvdbId` | ❌ null | ALWAYS_UPDATE ✅ | — | — |
| `authorityKey` | ⚠️ wenn tmdbId | ALWAYS_UPDATE ✅ | ALWAYS_UPDATE ✅ | ALWAYS_UPDATE ✅ |
| `isAdult` | ✅ | — | — | — |
| `recognitionState` | HEURISTIC | MONOTONIC_UP | MONOTONIC_UP (→CONFIRMED) | MONOTONIC_UP |
| `createdAt` | ✅ IMMUTABLE | — | — | — |
| `updatedAt` | ✅ AUTO | AUTO | AUTO | AUTO |

### 14.2 Xtream API Field-Availability

```
get_vod_streams (Catalog Listing):
├── name, stream_icon, rating, rating_5based, category_id
├── container_extension, stream_id (für Playback-URL)
├── added (timestamp), year (MANCHMAL vorhanden)
└── tmdb_id (MANCHMAL vorhanden — nur bei Premium-Panels)

get_vod_info (Detail):
├── ALLES aus Listing PLUS:
├── info.plot/description/overview
├── info.genre/genres
├── info.director, info.cast/actors
├── info.movie_image, info.backdrop_path[]
├── info.youtube_trailer/trailer
├── info.release_date
├── info.tmdb_id ✅ (IMMER vorhanden)
├── info.imdb_id 
├── info.duration_secs
└── info.video, info.audio (codec info)
```

---

## 15. Risk Assessment & Rollback Strategy

### 15.1 Risiko-Matrix

| Phase | Risiko | Mitigation |
|-------|--------|------------|
| Phase 1 (Write Protection) | Low — Neues Interface-Method, keine bestehende API geändert | Feature Flag `NX_USE_ENRICH_IF_ABSENT` |
| Phase 2 (Dual Write) | Medium — NxCanonicalMediaRepositoryImpl signifikant geändert | Bestehende Golden-Tests müssen nach wie vor alle bestehen |
| Phase 3 (RecognitionState) | Medium — Entity-Feld-Änderung | ObjectBox unterstützt additive Felder; alte Entities erhalten Default "HEURISTIC" |
| Phase 4 (ImageRef) | High — Domain Model API-Änderung, viele Verbraucher | Schrittweise: erst Builder, dann Mapper, dann Verbraucher |
| Phase 5 (Generic Mapper) | Low — Utility Functions, kein API-Change | Architecture-Tests fangen Regressions |
| Phase 6 (Enum Cleanup) | Low — NxEnums.WorkType nur intern genutzt | Grep + Replace, compile-time verification |
| Phase 7 (Duplicate Removal) | Low — Inline-Duplikate → Delegation | Test-Coverage schützt |
| Phase 8 (Tooling) | None — Additive, kein Code geändert | — |

### 15.2 Empfohlene Reihenfolge

```
Phase 5 (MappingUtils)      ← 30 min, Fundament für alle anderen Phasen
    ↓
Phase 3 (RecognitionState)   ← 1h, Entity-Migration ist unabhängig
    ↓
Phase 1 (Write Protection)   ← 2h, enrichIfAbsent() Interface + Impl
    ↓
Phase 2 (Dual Write)         ← 3h, NxCanonicalMediaRepositoryImpl umbauen, Duplikate entfernen
    ↓
Phase 7 (Duplicate Removal)  ← 2h, alle Inline-Duplikate ersetzen (toSlug, SourceKeyParser etc.)
    ↓
Phase 6 (Enum Cleanup)       ← 1h, NxEnums.WorkType/SourceType entfernen
    ↓
Phase 4 (ImageRef)           ← 4h, Domain Model API-Change, größte Auswirkung
    ↓
Phase 8 (Tooling)            ← 1h, Architecture Tests als Guard
```

**Geschätzter Gesamtaufwand:** ~14h für Phasen 1–8

### 15.3 Rollback-Strategie

Jede Phase wird als **separater Commit** auf einem Feature-Branch implementiert:
```
git checkout -b refactor/nx-consolidation
# Phase 5: feat(data-nx): add MappingUtils shared utilities
# Phase 3: fix(persistence): replace needsReview boolean with recognitionState string
# Phase 1: feat(model): add enrichIfAbsent to NxWorkRepository
# Phase 2: refactor(data-nx): eliminate dual write path in CanonicalMediaRepo
# Phase 7: refactor: remove all inline duplicates
# Phase 6: refactor(persistence): remove NxEnums.WorkType/SourceType
# Phase 4: refactor(model): ImageRef direct in Work domain model
# Phase 8: test(data-nx): add mapping completeness architecture tests
```

Bei Problemen: `git revert <commit>` für einzelne Phase, ohne andere Phasen zu beeinflussen.

---

## Appendix A: Betroffene Dateien (Vollständig)

| Datei | Phasen | Art |
|-------|--------|-----|
| `core/model/.../NxWorkRepository.kt` | 1, 4 | Modify (enrichIfAbsent, ImageRef fields) |
| `core/model/.../util/SlugGenerator.kt` | 7 | NEW |
| `core/persistence/.../NxEntities.kt` | 3 | Modify (recognitionState field) |
| `core/persistence/.../NxEnums.kt` | 6 | Modify (remove WorkType, SourceType) |
| `core/persistence/.../NxKeyGenerator.kt` | 7 | Modify (delegate toSlug) |
| `core/persistence/.../converter/ImageRefConverter.kt` | 4 | Modify (remove duplicate toUriString) |
| `core/metadata-normalizer/.../FallbackCanonicalKeyGenerator.kt` | 7 | Modify (delegate toSlug) |
| `infra/data-nx/.../mapper/base/MappingUtils.kt` | 5 | NEW |
| `infra/data-nx/.../mapper/WorkMapper.kt` | 3, 4, 5 | Modify (RecognitionState, ImageRef, Utils) |
| `infra/data-nx/.../mapper/TypeMappers.kt` | 2, 5 | Modify (add toMediaKind, use Utils) |
| `infra/data-nx/.../mapper/WorkVariantMapper.kt` | 2 | Modify (extract buildPlaybackHints) |
| `infra/data-nx/.../writer/builder/WorkEntityBuilder.kt` | 4 | Modify (ImageRef direct) |
| `infra/data-nx/.../repository/NxWorkRepositoryImpl.kt` | 1 | Modify (enrichIfAbsent impl) |
| `infra/data-nx/.../canonical/NxCanonicalMediaRepositoryImpl.kt` | 1, 2, 7 | Major refactor (delegate to builder) |
| `infra/data-detail/.../UnifiedDetailLoaderImpl.kt` | 7 | Modify (use SourceKeyParser) |
| `app-v2/.../work/CanonicalLinkingBacklogWorker.kt` | 2 | Verify (uses enrichIfAbsent path) |
| `infra/data-nx/src/test/.../MappingCompletenessTest.kt` | 8 | NEW |
| `core/persistence/src/test/.../NxKeyGeneratorTest.kt` | 7 | Modify (verify toSlug delegation) |

## Appendix B: Automated Consistency Guard — Architecture Tests

```kotlin
// infra/data-nx/src/test/java/.../architecture/MappingCompletenessTest.kt

/**
 * Architecture tests that prevent future mapping drift.
 * These tests break at COMPILE TIME when fields are added to
 * NormalizedMediaMetadata, NX_Work, or Work without updating mappers.
 */
class MappingCompletenessTest {

    @Test
    fun `WorkEntityBuilder maps all NormalizedMediaMetadata fields`() {
        val allFields = NormalizedMediaMetadata::class.memberProperties.map { it.name }.toSet()
        val mappedFields = WORK_ENTITY_BUILDER_MAPPED_FIELDS
        val justifiedUnmapped = WORK_ENTITY_BUILDER_UNMAPPED_FIELDS
        
        val coverage = mappedFields + justifiedUnmapped
        val missing = allFields - coverage
        val extra = coverage - allFields
        
        assertTrue(missing.isEmpty(),
            "NormalizedMediaMetadata fields NOT mapped in WorkEntityBuilder: $missing\n" +
            "→ Add to WorkEntityBuilder.build() or WORK_ENTITY_BUILDER_UNMAPPED_FIELDS with justification")
        assertTrue(extra.isEmpty(),
            "Fields in mapping registry but NOT in NormalizedMediaMetadata: $extra\n" +
            "→ Remove from registry")
    }

    @Test
    fun `WorkMapper covers all NX_Work entity fields`() { ... }

    @Test
    fun `WorkMapper covers all Work domain fields`() { ... }

    @Test
    fun `enrichIfAbsent handles all enrichable fields`() { ... }

    companion object {
        val WORK_ENTITY_BUILDER_MAPPED_FIELDS = setOf(
            "canonicalTitle", "mediaType", "year", "season", "episode",
            "tmdb", "externalIds", "poster", "backdrop", "thumbnail",
            "plot", "genres", "director", "cast", "rating", "durationMs",
            "trailer", "releaseDate", "isAdult", "addedTimestamp",
        )
        val WORK_ENTITY_BUILDER_UNMAPPED_FIELDS = mapOf(
            "placeholderThumbnail" to "Stored in NX_WorkVariant, not NX_Work",
            "epgChannelId" to "Stored in NX_WorkSourceRef",
            "tvArchive" to "Stored in NX_WorkSourceRef",
            "tvArchiveDuration" to "Stored in NX_WorkSourceRef",
            "categoryId" to "Stored in NX_XtreamCategorySelection",
        )
    }
}
```

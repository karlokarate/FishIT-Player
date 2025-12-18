# Gold TMDB Ref + Typed Canonical IDs - Status Report

**Date:** 2025-12-18  
**Branch:** `architecture/v2-bootstrap`

---

## ✅ ERLEDIGT

### Part 1: Core Model (SSOT)

| Komponente | Datei | Status |
|------------|-------|--------|
| `TmdbMediaType` enum | `core/model/.../TmdbRef.kt` | ✅ `MOVIE`, `TV` only |
| `TmdbRef` data class | `core/model/.../TmdbRef.kt` | ✅ `type: TmdbMediaType`, `id: Int` |
| `ExternalIds` mit typed TMDB | `core/model/.../RawMediaMetadata.kt` | ✅ `tmdb: TmdbRef?`, `legacyTmdbId` deprecated |
| `NormalizedMedia.season/episode` | `core/model/.../NormalizedMedia.kt` | ✅ Fields hinzugefügt |
| `NormalizedMedia.tmdb` | `core/model/.../NormalizedMedia.kt` | ✅ Field hinzugefügt |

### Part 2: Pipelines

| Pipeline | Typed TMDB Ref | Status |
|----------|----------------|--------|
| **Xtream VOD** | `TmdbRef(MOVIE, id)` | ✅ Implementiert |
| **Xtream Series** | `TmdbRef(TV, seriesId)` | ✅ Implementiert |
| **Xtream Episode** | `TmdbRef(TV, seriesId)` | ✅ Implementiert |
| **Telegram Structured** | `TmdbRef(type, id)` | ⚠️ **TEILWEISE** (siehe unten) |

### Part 3: Normalizer

| Komponente | Status |
|------------|--------|
| Typed TMDB → canonical ID | ✅ `tmdb:movie:{id}` / `tmdb:tv:{id}` |
| Season/Episode preservation | ✅ Wird in `NormalizedMedia` kopiert |
| Fallback canonical ID | ✅ `movie:{slug}` / `episode:{slug}:SxxExx` |
| LIVE → null | ✅ Kein canonical ID für Live |

### Part 4: Migration

| Komponente | Status |
|------------|--------|
| `ExternalIds.fromLegacyTmdbId()` | ✅ Konvertiert basierend auf MediaType |
| Legacy `tmdbId` deprecation | ✅ `@Deprecated` annotation |

### Part 5: Tests

| Test | Status |
|------|--------|
| `FallbackCanonicalKeyGeneratorTest` | ✅ Alle Tests passen |
| `TelegramRawMetadataExtensionsTest` | ✅ TMDB typed ref Tests |
| `TelegramBundleToMediaItemMapperTest` | ✅ Bundle + TMDB Tests |
| `NormalizerBehaviorTest` | ✅ Unlinked + Live Tests |

### Part 6: Guardrails

| Check | Status |
|-------|--------|
| Kein untyped `tmdb:\d+` | ✅ Keine Funde |
| Kein UI Season/Episode Parsing | ✅ Kein `S\d\dE\d\d` in `feature/` |
| Nur MOVIE/TV in TmdbMediaType | ✅ Enum hat nur 2 Werte |

### Clustering/Bundling

| Komponente | Status |
|------------|--------|
| `TelegramMessageBundler` | ✅ Implementiert und wird genutzt |
| `TelegramPipelineAdapter.fetchMediaMessagesWithBundling()` | ✅ Nutzt Bundler aktiv |
| `TelegramBundleToMediaItemMapper` | ✅ Extrahiert structured metadata |

---

## ⚠️ NICHT FERTIG / LÜCKEN

### 1. **Telegram: `structuredTmdbType` wird nicht gesetzt**

**Problem:**  
Der `TelegramStructuredMetadataExtractor` extrahiert zwar die TMDB ID aus der URL, aber **NICHT den Typ** (MOVIE vs TV).

**Betroffene Dateien:**
- `pipeline/telegram/.../grouper/TelegramStructuredMetadataExtractor.kt`
- `pipeline/telegram/.../grouper/StructuredMetadata.kt`
- `pipeline/telegram/.../mapper/TelegramBundleToMediaItemMapper.kt`

**Aktueller Zustand:**
```kotlin
// TelegramStructuredMetadataExtractor.kt
val tmdbId = extractTmdbIdFromUrl(tmdbUrl)  // ✅ Extrahiert ID
// ❌ ABER: tmdbType wird NICHT extrahiert!

// StructuredMetadata.kt
data class StructuredMetadata(
    val tmdbId: Int?,           // ✅ vorhanden
    // ❌ FEHLT: val tmdbType: TelegramTmdbType?
    ...
)

// TelegramBundleToMediaItemMapper.kt
structuredTmdbId = structuredMetadata.tmdbId,  // ✅ gesetzt
// ❌ FEHLT: structuredTmdbType = structuredMetadata.tmdbType,
```

**Konsequenz:**  
- `TelegramMediaItem.structuredTmdbType` bleibt `null`
- In `TelegramRawMetadataExtensions.buildExternalIds()` fällt es auf `legacyTmdbId` zurück
- Kein typed `TmdbRef` wird erstellt → kein `tmdb:movie:` / `tmdb:tv:` canonical ID

**Fix erforderlich:**
1. `StructuredMetadata` um `tmdbType: TelegramTmdbType?` erweitern
2. `TelegramStructuredMetadataExtractor.extractTmdbIdFromUrl()` → `extractTmdbFromUrl()` umbenennen und `Pair<Int, TelegramTmdbType>?` zurückgeben (nutzt `TelegramTmdbType.parseFromUrl()`)
3. `TelegramBundleToMediaItemMapper` muss `structuredTmdbType` setzen

### 2. **Test für End-to-End Canonical ID Flow fehlt**

**Problem:**  
Der erstellte `PipelineCanonicalIdIntegrationTest.kt` wurde nicht fertiggestellt wegen `NormalizedMedia.season/episode` Kompilierfehlern. Nach dem Fix fehlt die Ausführung.

**Datei:**
- `core/metadata-normalizer/src/test/java/.../PipelineCanonicalIdIntegrationTest.kt`

**Status:** Existiert, aber nicht verifiziert ob alle Tests passen.

### 3. **Detekt-Regel für untyped TMDB fehlt**

**Gewünscht per Task:**
> Add grep/detekt gates: Fail if any new code generates `tmdb:\d+` without movie|tv.

**Status:** Nicht implementiert. Manuelle grep-Prüfung zeigt keine Verstöße, aber automatische CI-Prüfung fehlt.

---

## EMPFOHLENE NÄCHSTE SCHRITTE

1. **P1 - Critical:** `structuredTmdbType` Fix implementieren (30-45 min)
2. **P2 - High:** `PipelineCanonicalIdIntegrationTest` fertigstellen und ausführen
3. **P3 - Medium:** Detekt-Regel für untyped TMDB canonical IDs hinzufügen

---

## ZUSAMMENFASSUNG

| Bereich | Fertig | Offen |
|---------|--------|-------|
| Core Model | 100% | - |
| Xtream Pipeline | 100% | - |
| Telegram Pipeline | 80% | `structuredTmdbType` fehlt |
| Normalizer | 100% | - |
| Migration | 100% | - |
| Tests | 90% | Integration-Test verifizieren |
| Guardrails | 80% | Detekt-Regel fehlt |

**Gesamtfortschritt: ~90%**

Der kritische Mangel ist, dass Telegram Structured Bundles zwar TMDB IDs extrahieren, aber **nicht den Typ** (MOVIE/TV), wodurch kein typed canonical ID (`tmdb:movie:`/`tmdb:tv:`) entsteht.

# Gold TMDB Ref + Typed Canonical IDs - Status Report

**Date:** 2025-12-18  
**Branch:** `architecture/v2-bootstrap`  
**Last Update:** 2025-12-18 (structuredTmdbType fix completed)

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
| **Telegram Structured** | `TmdbRef(type, id)` | ✅ **VOLLSTÄNDIG (Fix 2025-12-18)** |

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
| `TelegramStructuredMetadataExtractorTest` | ✅ tmdbType extraction Tests |

### Part 6: Guardrails

| Check | Status |
|-------|--------|
| Kein untyped `tmdb:\d+` | ✅ Keine Funde |
| Kein UI Season/Episode Parsing | ✅ Kein `S\d\dE\d\d` in `feature/` |
| Nur MOVIE/TV in TmdbMediaType | ✅ Enum hat nur 2 Werte |
| `TelegramTmdbType.parseFromUrl()` validiert Host | ✅ Nur `themoviedb.org` URLs werden akzeptiert |

### Clustering/Bundling

| Komponente | Status |
|------------|--------|
| `TelegramMessageBundler` | ✅ Implementiert und wird genutzt |
| `TelegramPipelineAdapter.fetchMediaMessagesWithBundling()` | ✅ Nutzt Bundler aktiv |
| `TelegramBundleToMediaItemMapper` | ✅ Extrahiert structured metadata **inkl. tmdbType** |

---

## ✅ FIX COMPLETED (2025-12-18): `structuredTmdbType`

### Problem (war)

Der `TelegramStructuredMetadataExtractor` extrahierte zwar die TMDB ID aus der URL, aber NICHT den Typ (MOVIE vs TV).

### Lösung (implementiert)

**1. `TelegramTmdbType.parseFromUrl()` verbessert:**

- Prüft jetzt explizit auf `themoviedb.org` Host
- Lehnt URLs von anderen Domains ab (z.B. `example.com/movie/123`)

**2. `StructuredMetadata.kt` erweitert:**

```kotlin
data class StructuredMetadata(
    val tmdbId: Int?,
    val tmdbType: TelegramTmdbType?, // ✅ NEU
    ...
) {
    val hasTypedTmdb: Boolean get() = tmdbId != null && tmdbType != null
}
```

**3. `TelegramStructuredMetadataExtractor.extractStructuredMetadata()` nutzt jetzt:**

```kotlin
val tmdbParsed = tmdbUrl?.let { TelegramTmdbType.parseFromUrl(it) }
val tmdbType = tmdbParsed?.first
val tmdbId = tmdbParsed?.second
```

**4. `TelegramBundleToMediaItemMapper` setzt `structuredTmdbType`:**

```kotlin
structuredTmdbType = structuredMetadata.tmdbType
```

**5. Tests aktualisiert:**

- `TelegramStructuredMetadataExtractorTest`: Neue Tests für MOVIE/TV URL Extraktion
- `TelegramRawMetadataExtensionsTest`: Tests nutzen jetzt typed TMDB

### Verifikation

```bash
./gradlew :pipeline:telegram:testDebugUnitTest
# BUILD SUCCESSFUL - 101 tests, 0 failures
```

---

## VERBLEIBEND (Low Priority)

### 1. **Detekt-Regel für untyped TMDB**

**Gewünscht:**
> Add grep/detekt gates: Fail if any new code generates `tmdb:\d+` without movie|tv.

**Status:** Nicht implementiert. Manuelle grep-Prüfung zeigt keine Verstöße, aber automatische CI-Prüfung fehlt.

---

## ZUSAMMENFASSUNG

| Bereich | Status |
|---------|--------|
| Core Model | ✅ 100% |
| Xtream Pipeline | ✅ 100% |
| Telegram Pipeline | ✅ 100% |
| Normalizer | ✅ 100% |
| Tests | ✅ 100% |
| Guardrails | ⚠️ 90% (Detekt-Regel optional) |

**Gold TMDB Ref + Typed Canonical IDs ist vollständig implementiert.**
| Normalizer | 100% | - |
| Migration | 100% | - |
| Tests | 90% | Integration-Test verifizieren |
| Guardrails | 80% | Detekt-Regel fehlt |

**Gesamtfortschritt: ~90%**

Der kritische Mangel ist, dass Telegram Structured Bundles zwar TMDB IDs extrahieren, aber **nicht den Typ** (MOVIE/TV), wodurch kein typed canonical ID (`tmdb:movie:`/`tmdb:tv:`) entsteht.

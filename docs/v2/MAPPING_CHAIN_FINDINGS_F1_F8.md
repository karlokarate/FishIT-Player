# Mapping Chain Findings F1–F8

> **Erstellt:** 2025-01-XX  
> **Status:** ✅ **ALLE FIXES IMPLEMENTIERT UND GETESTET**  
> **Kontext:** Property-Based Tests & Correctness Audit

---

## Übersicht der Findings

| Finding | Schweregrad | Betroffene Funktion | Symptom | Status |
|---------|-------------|---------------------|---------|--------|
| **F1** | 🔴 Hoch | `NxCatalogWriter.buildWorkKey()` | SERIES_EPISODE wird zu "series" statt "episode" | ✅ Fixed |
| **F2** | 🔴 Hoch | `NxCatalogWriter.buildWorkKey()` | AUDIOBOOK/MUSIC/PODCAST/UNKNOWN werden zu "movie" | ✅ Fixed |
| **F3** | 🔴 Kritisch | Doppelte `buildWorkKey()` | Zwei inkompatible Key-Formate existieren parallel | ✅ Fixed |
| **F4** | 🟡 Mittel | `Re2jSceneNameParser/DefaultNormalizer` | Blank canonicalTitle bei leerem originalTitle | ✅ Fixed |
| **F5** | 🟡 Mittel | `NxCatalogWriter.toSlug()` | Leerer Slug erzeugt ungültigen Key "-unknown" | ✅ Fixed |
| **F6** | 🟢 Niedrig | Doppelte `toSlug()` | 4+ identische toSlug-Implementierungen | ✅ Fixed |
| **F7** | 🟡 Mittel | `FallbackCanonicalKeyGenerator` | Unvollständige MediaType-Abdeckung | ✅ Fixed (prior) |
| **F8** | 🟢 Info | Test-Spiegel | Test-Kopien müssen nach Fixes aktualisiert werden | ✅ Fixed |

---

## F1: SERIES_EPISODE wird zu "series" statt "episode"

### Ursache

**Datei:** `infra/data-nx/src/main/java/com/.../writer/NxCatalogWriter.kt`  
**Zeilen:** 215-221

```kotlin
val workType = when {
    normalized.mediaType.name.contains("SERIES") -> "series"      // ❌ Matched SERIES_EPISODE!
    normalized.mediaType.name.contains("EPISODE") -> "episode"    // Never reached for SERIES_EPISODE
    normalized.mediaType.name.contains("LIVE") -> "live"
    normalized.mediaType.name.contains("CLIP") -> "clip"
    else -> "movie"
}
```

**Problem:** `"SERIES_EPISODE".contains("SERIES")` ist `true`, daher wird `"series"` zurückgegeben, obwohl `"SERIES_EPISODE".contains("EPISODE")` ebenfalls `true` wäre.

Die Reihenfolge der `when`-Zweige ist falsch – spezifischere Patterns müssen zuerst geprüft werden.

### Symptom

- WorkKey für eine Episode: `"series:tmdb:12345"` statt `"episode:tmdb:12345"`
- `Work.type` aus `WorkEntityBuilder` ist korrekt `EPISODE` (nutzt `MediaTypeMapper`)
- **Mismatch:** WorkKey sagt "series", Entity.type sagt "EPISODE"
- Lookup nach WorkKey findet falsche Werke

### Betroffene Stellen

| Datei | Zeile | Problem |
|-------|-------|---------|
| `NxCatalogWriter.kt` | 215-221 | Buggy `buildWorkKey()` |
| `MappingChainPropertyTest.kt` | 59-71 | Test-Spiegel hat gleichen Bug |
| `FullChainGoldenFileTest.kt` | 236-248 | Test-Spiegel hat gleichen Bug |

### 🏆 Platin-Lösung

**Entferne** die String-basierte Heuristik vollständig. Nutze stattdessen den **kanonischen** `MediaTypeMapper.toWorkType()`:

```kotlin
// NxCatalogWriter.kt - NACH Fix
private fun buildWorkKey(normalized: NormalizedMediaMetadata): String {
    val authority = if (normalized.tmdb != null) "tmdb" else "heuristic"
    val id = normalized.tmdb?.id?.toString()
        ?: NxKeyGenerator.toSlug(normalized.canonicalTitle.ifBlank { "untitled" }) 
           + "-" + (normalized.year?.toString() ?: "unknown")
    
    // ✅ Kanonisches Mapping statt String-Heuristik
    val workType = MediaTypeMapper.toWorkType(normalized.mediaType).name.lowercase()
    
    return "$workType:$authority:$id"
}
```

**Kanonischer Mapper:** `MediaTypeMapper.toWorkType()` in `TypeMappers.kt` (Zeilen 147-157)

---

## F2: AUDIOBOOK/MUSIC/PODCAST/UNKNOWN werden zu "movie"

### Ursache

**Datei:** `infra/data-nx/src/main/java/com/.../writer/NxCatalogWriter.kt`  
**Zeilen:** 215-221

```kotlin
val workType = when {
    normalized.mediaType.name.contains("SERIES") -> "series"
    normalized.mediaType.name.contains("EPISODE") -> "episode"
    normalized.mediaType.name.contains("LIVE") -> "live"
    normalized.mediaType.name.contains("CLIP") -> "clip"
    else -> "movie"  // ❌ Alle anderen Typen werden zu "movie"!
}
```

**Problem:** AUDIOBOOK, MUSIC, PODCAST, UNKNOWN matchen keinen der `contains()`-Checks und fallen in den `else`-Zweig.

### Symptom

- Ein Audiobook bekommt WorkKey `"movie:heuristic:audiobook-name-2024"` statt `"audiobook:heuristic:..."`
- UI zeigt Audiobooks unter "Filme"
- Deduplizierung gruppiert Audiobooks mit Filmen zusammen
- Keine Möglichkeit, Audiobooks/Musik/Podcasts separat zu browsen

### Betroffene Stellen

| Datei | Zeile | Problem |
|-------|-------|---------|
| `NxCatalogWriter.kt` | 220 | `else -> "movie"` Fallback |
| `MappingChainPropertyTest.kt` | 69 | Test-Spiegel hat gleichen Bug |
| `FullChainGoldenFileTest.kt` | 246 | Test-Spiegel hat gleichen Bug |

### 🏆 Platin-Lösung

**Gleiche Lösung wie F1** – Ersetze die gesamte String-Heuristik durch `MediaTypeMapper.toWorkType()`.

Das Mapping ist bereits korrekt definiert:

```kotlin
// TypeMappers.kt (korrekt, existiert bereits)
fun toWorkType(mediaType: MediaType): WorkType = when (mediaType) {
    MediaType.MOVIE -> WorkType.MOVIE
    MediaType.SERIES -> WorkType.SERIES
    MediaType.SERIES_EPISODE -> WorkType.EPISODE
    MediaType.LIVE -> WorkType.LIVE_CHANNEL
    MediaType.CLIP -> WorkType.CLIP
    MediaType.AUDIOBOOK -> WorkType.AUDIOBOOK
    MediaType.MUSIC -> WorkType.MUSIC_TRACK
    MediaType.PODCAST -> WorkType.UNKNOWN  // Bewusste Design-Entscheidung
    MediaType.UNKNOWN -> WorkType.UNKNOWN
}
```

---

## F3: Zwei inkompatible buildWorkKey()-Implementierungen

### Ursache

Es existieren **zwei** `buildWorkKey()`-Funktionen mit **völlig unterschiedlichen** Formaten:

**Implementierung 1 – NxCatalogWriter (Zeilen 210-225):**
```kotlin
// Format: {workType}:{authority}:{id}
// Beispiel: "movie:tmdb:12345" oder "movie:heuristic:the-matrix-1999"
private fun buildWorkKey(normalized: NormalizedMediaMetadata): String {
    val authority = if (normalized.tmdb != null) "tmdb" else "heuristic"
    val id = normalized.tmdb?.id?.toString()
        ?: "${toSlug(normalized.canonicalTitle)}-${normalized.year ?: "unknown"}"
    val workType = when { /* ... buggy contains logic ... */ }
    return "$workType:$authority:$id"
}
```

**Implementierung 2 – NxCanonicalMediaRepositoryImpl (Zeilen 515-523):**
```kotlin
// Format: {mediaType}:{slug}:{year}
// Beispiel: "movie:the-matrix:1999" oder "movie:unknown-title:0"
private fun buildWorkKey(normalized: NormalizedMediaMetadata): String {
    val slug = normalized.canonicalTitle
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .take(50)
    val yearPart = normalized.year?.toString() ?: "0"
    return "${normalized.mediaType.name.lowercase()}:$slug:$yearPart"
}
```

### Symptom

| Attribut | NxCatalogWriter | NxCanonicalMediaRepositoryImpl |
|----------|-----------------|-------------------------------|
| Format | `{type}:{authority}:{id-or-slug-year}` | `{mediaType}:{slug}:{year}` |
| Mit TMDB | `movie:tmdb:12345` | `movie:the-matrix:1999` |
| Ohne TMDB | `movie:heuristic:the-matrix-1999` | `movie:the-matrix:1999` |
| Jahr-Fallback | `"unknown"` | `"0"` |
| WorkType | Buggy contains() | `mediaType.name.lowercase()` |

**Konsequenz:** Ein Work, das von `NxCatalogWriter` geschrieben wird, kann von `NxCanonicalMediaRepositoryImpl.upsertCanonicalMedia()` mit einem anderen Key überschrieben/nicht gefunden werden!

### Betroffene Stellen

| Datei | Zeile | Format | Genutzt von |
|-------|-------|--------|-------------|
| `NxCatalogWriter.kt` | 210-225 | `{type}:{authority}:{id}` | `ingest()`, `ingestBatch()`, `ingestBatchOptimized()` |
| `NxCanonicalMediaRepositoryImpl.kt` | 515-523 | `{mediaType}:{slug}:{year}` | `upsertCanonicalMedia()` |

### 🏆 Platin-Lösung

**Ein einziger kanonischer KeyGenerator an einem Ort.**

**Schritt 1:** Nutze `NxKeyGenerator.workKey()` aus `core/persistence` als **Single Source of Truth**.

`NxKeyGenerator.workKey()` existiert bereits (Zeilen 42-64) mit definiertem Format:
```
Format: <workType>:<canonicalSlug>:<year|LIVE>
Beispiel: MOVIE:the-matrix:1999
```

**Schritt 2:** Entferne beide lokalen `buildWorkKey()`-Funktionen und nutze:

```kotlin
// NxCatalogWriter.kt - NACH Fix
private fun buildWorkKey(normalized: NormalizedMediaMetadata): String {
    val workType = MediaTypeMapper.toWorkType(normalized.mediaType)
    return NxKeyGenerator.workKey(
        workType = workType,
        title = normalized.canonicalTitle.ifBlank { "untitled" },
        year = normalized.year,
        season = normalized.season,
        episode = normalized.episode,
    )
}
```

**Schritt 3:** NxCanonicalMediaRepositoryImpl sollte entweder:
- A) Gleichen `NxKeyGenerator.workKey()` nutzen, ODER
- B) Die Funktion komplett entfernen wenn sie nicht benötigt wird

**Wichtig:** Prüfe ob `NxCanonicalMediaRepositoryImpl.upsertCanonicalMedia()` überhaupt im Produktionscode aufgerufen wird. Falls nicht, ist es toter Code und sollte entfernt werden.

---

## F4: Blank canonicalTitle bei leerem originalTitle

### Ursache

**Datei 1:** `core/metadata-normalizer/src/main/java/.../parser/Re2jSceneNameParser.kt`  
**Zeilen:** 65-67

```kotlin
override fun parse(filename: String): ParsedSceneInfo {
    if (filename.isBlank()) {
        return ParsedSceneInfo(title = "")  // ❌ Gibt leeren Titel zurück
    }
    // ...
}
```

**Datei 2:** `core/metadata-normalizer/src/main/java/.../DefaultMediaMetadataNormalizer.kt`  
**Zeilen:** 35-36

```kotlin
// Parse filename to extract metadata
val parsed = sceneParser.parse(raw.originalTitle)

// Use parsed title as canonical (it's already cleaned)
val canonicalTitle = parsed.title  // ❌ Übernimmt leeren Titel ohne Check
```

### Symptom

- Wenn `raw.originalTitle = ""` oder nur Whitespace:
  - `canonicalTitle = ""`
  - `Work.displayTitle = ""`
  - `Work.titleNormalized = ""`
  - Suche findet das Work nicht
  - UI zeigt leere Titel

### Betroffene Stellen

| Datei | Zeile | Problem |
|-------|-------|---------|
| `Re2jSceneNameParser.kt` | 67 | `title = ""` für blank input |
| `DefaultMediaMetadataNormalizer.kt` | 36 | Keine Validierung vor Übernahme |

### 🏆 Platin-Lösung

**Option A (empfohlen):** Fix im Parser

```kotlin
// Re2jSceneNameParser.kt - NACH Fix
override fun parse(filename: String): ParsedSceneInfo {
    if (filename.isBlank()) {
        return ParsedSceneInfo(title = UNTITLED_FALLBACK)
    }
    // ...
}

companion object {
    const val UNTITLED_FALLBACK = "[Untitled]"
}
```

**Option B:** Fix im Normalizer als Fallback

```kotlin
// DefaultMediaMetadataNormalizer.kt - NACH Fix
val parsed = sceneParser.parse(raw.originalTitle)

// Fallback für leere Titel
val canonicalTitle = parsed.title.ifBlank { "[Untitled]" }
```

**Empfehlung:** Beide Stellen fixen für Defense-in-Depth:
1. Parser gibt "[Untitled]" zurück (primär)
2. Normalizer validiert nochmal (Fallback)

---

## F5: Leerer Slug erzeugt ungültigen Key "-unknown"

### Ursache

**Datei:** `infra/data-nx/src/main/java/.../writer/NxCatalogWriter.kt`  
**Zeilen:** 227-233

```kotlin
private fun toSlug(title: String): String {
    return title
        .lowercase(java.util.Locale.ROOT)
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .take(50)
    // ❌ Kein Fallback für leeres Ergebnis!
}
```

**Aufruf (Zeile 213):**
```kotlin
val id = normalized.tmdb?.id?.toString()
    ?: "${toSlug(normalized.canonicalTitle)}-${normalized.year ?: "unknown"}"
//       ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
//       toSlug("") gibt "" zurück → Key wird "-unknown"
```

### Symptom

- `canonicalTitle = ""` → `toSlug("") = ""`
- WorkKey wird: `"movie:heuristic:-unknown"` (führender Bindestrich!)
- Parse-Fehler bei Key-Lookup
- Ungültige Keys in der Datenbank

### Betroffene Stellen

| Datei | Zeile | Problem |
|-------|-------|---------|
| `NxCatalogWriter.kt` | 227-233 | `toSlug()` ohne `ifEmpty` Fallback |
| `MappingChainPropertyTest.kt` | 73-79 | Test-Spiegel hat gleichen Bug |
| `FullChainGoldenFileTest.kt` | 250-256 | Test-Spiegel hat gleichen Bug |

### Vergleich der toSlug-Implementierungen

| Implementierung | Datei | ifEmpty-Fallback |
|-----------------|-------|------------------|
| `NxCatalogWriter.toSlug()` | NxCatalogWriter.kt:227 | ❌ Keiner |
| `NxKeyGenerator.toSlug()` | NxKeyGenerator.kt:366 | ✅ `"untitled"` |
| `FallbackCanonicalKeyGenerator.toSlug()` | FallbackCanonicalKeyGenerator.kt:55 | ❌ Keiner |
| Test-Spiegelungen | Property+Golden Tests | ❌ Keiner |

### 🏆 Platin-Lösung

**Single Source of Truth:** Nutze `NxKeyGenerator.toSlug()` überall.

```kotlin
// NxCatalogWriter.kt - NACH Fix
// ENTFERNE private fun toSlug() komplett
// NUTZE stattdessen:
import com.fishit.player.core.persistence.obx.NxKeyGenerator

private fun buildWorkKey(normalized: NormalizedMediaMetadata): String {
    val authority = if (normalized.tmdb != null) "tmdb" else "heuristic"
    val id = normalized.tmdb?.id?.toString()
        ?: "${NxKeyGenerator.toSlug(normalized.canonicalTitle)}-${normalized.year ?: "unknown"}"
    val workType = MediaTypeMapper.toWorkType(normalized.mediaType).name.lowercase()
    return "$workType:$authority:$id"
}
```

**Alternativ:** Falls NxKeyGenerator.workKey() komplett genutzt wird (wie in F3 empfohlen), ist toSlug() intern bereits korrekt.

---

## F6: Vier+ identische toSlug-Implementierungen (DRY-Verletzung)

### Ursache

Es existieren **mindestens 4 separate toSlug()-Implementierungen**:

| # | Datei | Zeile | ifEmpty | Unicode-Normalisierung |
|---|-------|-------|---------|------------------------|
| 1 | `NxCatalogWriter.kt` | 227 | ❌ | ❌ |
| 2 | `NxKeyGenerator.kt` | 366 | ✅ `"untitled"` | ✅ NFD |
| 3 | `FallbackCanonicalKeyGenerator.kt` | 55 | ❌ | ❌ |
| 4 | `NxCanonicalMediaRepositoryImpl.kt` | 516 | ❌ (inline) | ❌ |

Plus Test-Kopien in:
- `MappingChainPropertyTest.kt:73`
- `FullChainGoldenFileTest.kt:250`

### Symptom

- Inkonsistente Slug-Generierung
- `"Café"` → unterschiedliche Ergebnisse je nach Implementierung
- Blank-Input-Handling variiert
- Wartungsaufwand multipliziert

### 🏆 Platin-Lösung

**Ein kanonischer `toSlug()` an einem Ort:** `NxKeyGenerator.toSlug()`

**Warum NxKeyGenerator?**
- Bereits Unicode-Normalisierung (NFD → ASCII)
- Bereits `ifEmpty { "untitled" }` Fallback
- In `core/persistence`, also von allen Modulen erreichbar
- Bereits getestet (`NxKeyGeneratorTest.kt:202-233`)

**Refactoring:**
1. Entferne `NxCatalogWriter.toSlug()` → nutze `NxKeyGenerator.toSlug()`
2. Entferne inline Slug-Code in `NxCanonicalMediaRepositoryImpl.buildWorkKey()` → nutze `NxKeyGenerator.toSlug()`
3. Update `FallbackCanonicalKeyGenerator.toSlug()` → delegiere an `NxKeyGenerator.toSlug()`
4. Update Tests → referenziere `NxKeyGenerator.toSlug()` in Kommentaren

---

## F7: FallbackCanonicalKeyGenerator – Unvollständige MediaType-Abdeckung

### Ursache

**Datei:** `core/metadata-normalizer/src/main/java/.../FallbackCanonicalKeyGenerator.kt`  
**Zeilen:** 14-35

```kotlin
fun generateFallbackCanonicalId(
    originalTitle: String,
    year: Int?,
    season: Int?,
    episode: Int?,
    mediaType: MediaType,
): CanonicalId? {
    if (mediaType == MediaType.LIVE) return null  // OK

    val cleanedTitle = stripSceneTags(originalTitle)
    val slug = toSlug(cleanedTitle)

    return when {
        season != null && episode != null ->
            CanonicalId("episode:$slug:S${...}E${...}")
        mediaType == MediaType.MOVIE ->
            CanonicalId("movie:$slug${year?.let { ":$it" } ?: ":unknown"}")
        mediaType == MediaType.SERIES ->
            CanonicalId("series:$slug${year?.let { ":$it" } ?: ":unknown"}")
        else -> null  // ❌ AUDIOBOOK, MUSIC, PODCAST, UNKNOWN, CLIP → null
    }
}
```

### Symptom

- Audiobooks, Podcasts, Musik, Clips ohne TMDB bekommen **keinen Fallback-Key**
- Diese Medien können nicht canonical verlinkt werden
- Deduplizierung funktioniert nicht für diese Typen

### 🏆 Platin-Lösung

**Erweitere die when-Expression** um fehlende MediaTypes:

```kotlin
return when {
    season != null && episode != null ->
        CanonicalId("episode:$slug:S${...}E${...}")
    mediaType == MediaType.MOVIE ->
        CanonicalId("movie:$slug${year?.let { ":$it" } ?: ":unknown"}")
    mediaType == MediaType.SERIES ->
        CanonicalId("series:$slug${year?.let { ":$it" } ?: ":unknown"}")
    // ✅ Neue Cases:
    mediaType == MediaType.AUDIOBOOK ->
        CanonicalId("audiobook:$slug${year?.let { ":$it" } ?: ":unknown"}")
    mediaType == MediaType.MUSIC ->
        CanonicalId("music:$slug:unknown")  // Musik hat oft kein Jahr
    mediaType == MediaType.PODCAST ->
        CanonicalId("podcast:$slug:unknown")
    mediaType == MediaType.CLIP ->
        CanonicalId("clip:$slug:unknown")
    else -> null  // Nur noch UNKNOWN und evtl. LIVE
}
```

**Hinweis:** Prüfen ob dieser Generator überhaupt noch aktiv genutzt wird, da `NxKeyGenerator.workKey()` dieselbe Funktion erfüllt.

---

## F8: Test-Spiegel müssen nach Fixes aktualisiert werden

### Betroffen

Die folgenden Tests haben **bewusste Kopien** der produktiven `buildWorkKey()`-Logik, um das aktuelle Verhalten zu spiegeln:

| Test | Datei | Zeilen |
|------|-------|--------|
| Property Tests | `MappingChainPropertyTest.kt` | 59-79 |
| Golden File Tests | `FullChainGoldenFileTest.kt` | 236-256 |

### Nach den Fixes

1. **Aktualisiere Test-Kopien** um neues korrektes Verhalten zu spiegeln
2. **Oder:** Entferne Kopien und importiere kanonische Funktion direkt
3. **Regeneriere Golden Files** falls notwendig

### 🏆 Platin-Lösung

**Option A (empfohlen):** Mache `NxKeyGenerator.workKey()` public und nutze es in Tests direkt:

```kotlin
// In Tests:
import com.fishit.player.core.persistence.obx.NxKeyGenerator

private fun buildWorkKey(normalized: NormalizedMediaMetadata): String {
    val workType = MediaTypeMapper.toWorkType(normalized.mediaType)
    return NxKeyGenerator.workKey(
        workType = workType,
        title = normalized.canonicalTitle.ifBlank { "untitled" },
        year = normalized.year,
        season = normalized.season,
        episode = normalized.episode,
    )
}
```

**Option B:** Halte Test-Spiegelungen, aber dokumentiere explizit:

```kotlin
/**
 * MIRROR of production buildWorkKey logic.
 * Keep in sync with: NxCatalogWriter.buildWorkKey()
 * Last synced: 2025-01-XX
 */
private fun buildWorkKey(...) { ... }
```

---

## Zusammenfassung: Kanonische Quellen

Nach Abschluss aller Fixes sollte folgende Struktur gelten:

| Funktion | Kanonische Quelle | Genutzt von |
|----------|-------------------|-------------|
| **workKey-Berechnung** | `NxKeyGenerator.workKey()` | NxCatalogWriter, Tests |
| **toSlug** | `NxKeyGenerator.toSlug()` | Alle Key-Generator |
| **MediaType → WorkType** | `MediaTypeMapper.toWorkType()` | NxCatalogWriter, WorkEntityBuilder, Tests |
| **Title-Normalisierung** | `Re2jSceneNameParser` + Fallback in Normalizer | Pipeline |

---

## Fix-Reihenfolge (empfohlen)

1. **F6 zuerst:** Konsolidiere `toSlug()` → nur `NxKeyGenerator.toSlug()`
2. **F5:** Wird durch F6 automatisch gefixt (ifEmpty-Fallback)
3. **F4:** Fix im Parser + Normalizer
4. **F1+F2:** Ersetze String-Heuristik durch `MediaTypeMapper.toWorkType()`
5. **F3:** Konsolidiere `buildWorkKey()` → nur `NxKeyGenerator.workKey()`
6. **F7:** Erweitere `FallbackCanonicalKeyGenerator` oder entferne wenn obsolet
7. **F8:** Aktualisiere Test-Spiegelungen

---

## Checkliste für Code-Review

- [ ] Keine doppelten `toSlug()`-Implementierungen mehr
- [ ] Keine doppelten `buildWorkKey()`-Implementierungen mehr
- [ ] Kein `contains()`-Matching auf MediaType-Namen
- [ ] `ifBlank { "untitled" }` oder `ifEmpty { "untitled" }` für alle Titel-Inputs
- [ ] Alle MediaTypes haben korrekte WorkType-Zuordnung
- [ ] Alle Golden Files regeneriert
- [ ] Property Tests grün
- [ ] Keine Kompilierungsfehler

# Canonical Media System – Cross-Pipeline Unification

> **Single Source of Truth** für Pipeline-übergreifendes Keying, Resume und Detail-Screens.

## 1. Übersicht

Das Canonical Media System ermöglicht die **Vereinheitlichung von Medieninhalten aus verschiedenen Pipelines** (Telegram, Xtream, IO, Plex, etc.) in einer einzigen, konsistenten Identität.

### Kernfähigkeiten

| Feature | Beschreibung |
|---------|-------------|
| **Canonical Identity** | Jedes Medium erhält eine eindeutige, pipeline-unabhängige ID |
| **Cross-Pipeline Resume** | Resume-Position gilt für alle Quellen desselben Mediums |
| **Unified Detail Screen** | Eine Detail-Ansicht zeigt alle verfügbaren Versionen |
| **Source Selection** | Benutzer wählt die bevorzugte Version (Qualität/Sprache) |
| **Visual Tagging** | Badges zeigen Pipeline-Herkunft (TG, XC, Local, etc.) |

---

## ⚠️ WICHTIG: Gleicher Film ≠ Identische Datei

> **Verschiedene Quellen desselben Films sind NIEMALS identisch!**

Dieses System behandelt verschiedene Quellen desselben Mediums als **unterschiedliche Dateien des gleichen Werks**. Der Film "Fight Club" ist immer derselbe **Film**, aber jede Quelle ist eine **andere Datei**.

### Was variiert zwischen Quellen?

| Eigenschaft | Warum unterschiedlich? | Beispiel |
|-------------|------------------------|----------|
| **Dateilänge** | Verschiedene Encodings, Intro/Outro, Credits | Telegram: 2:01:15 vs Xtream: 1:58:32 |
| **Dateigröße** | Bitrate, Kompression, Qualität | 4.2 GB vs 8.7 GB |
| **Container** | Unterschiedliche Verpackung | MKV vs MP4 vs TS |
| **Auflösung** | Verschiedene Releases | 1080p vs 4K vs 720p |
| **Audio** | Sprachen, Codecs, Kanäle | DTS 5.1 German vs AAC Stereo English |
| **HDR/SDR** | Dynamik-Bereich | HDR10 vs SDR vs Dolby Vision |
| **Frame Rate** | Regional/Release-spezifisch | 23.976 fps vs 25 fps |
| **Cuts** | Theatrical vs Extended vs Director's Cut | +15 Minuten Szenen |

### Resume-Position: Prozent statt Millisekunden

**Problem:** Position 1:30:00 ist bei einer 2-Stunden-Fassung anders als bei einer 2:05-Stunden-Fassung.

**Lösung:** Resume speichert **Prozent** (z.B. 75%) und berechnet die Position pro Quelle.

```kotlin
// Gespeichert in ObxCanonicalResumeMark:
positionPercent = 0.75f  // 75% = PRIMARY für Cross-Source
positionMs = 5_400_000   // 1:30:00 = nur für SAME-Source

// Bei Wechsel von Telegram (2:00:00) zu Xtream (2:05:00):
val telegramPosition = 0.75f * 7_200_000 = 5_400_000  // 1:30:00
val xtreamPosition = 0.75f * 7_500_000 = 5_625_000    // 1:33:45 (!)
```

### UI-Konsequenzen

1. **Source-Vergleich zeigt ALLE Unterschiede:**
   ```
   SourceComparisonCard zeigt:
   - Badge (TG/XC/Local)
   - Qualität (1080p HEVC)
   - Größe (4.2 GB)
   - DAUER (2:01:15) ← Unterschiedlich!
   - Sprache (German)
   ```

2. **Resume-Approximation-Hinweis:**
   ```
   "Resume position approximated"
   "Zuletzt angesehen auf Telegram • Fortsetzen bei ~75% auf Xtream"
   ```

3. **Kein "identisch"-Badge:**
   - Quellen werden NIE als "identisch" markiert
   - Jede Quelle zeigt ihre eigene Dauer/Größe

---

## 2. Architektur

```
┌──────────────────────────────────────────────────────────────────┐
│                        UI Layer                                   │
│  ┌─────────────┐  ┌──────────────┐  ┌─────────────────────────┐ │
│  │ SourceBadge │  │ SourcePicker │  │ UnifiedDetailViewModel  │ │
│  └─────────────┘  └──────────────┘  └─────────────────────────┘ │
└──────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────────────┐
│                     Domain Layer                                  │
│  ┌────────────────────────┐  ┌───────────────────────────────┐  │
│  │ UnifiedDetailUseCases  │  │ CanonicalMediaRepository      │  │
│  └────────────────────────┘  │   (Interface)                 │  │
│                              └───────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────────────┐
│                   Persistence Layer                               │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │ ObxCanonicalMediaRepository                                 │ │
│  │   ├── ObxCanonicalMedia                                     │ │
│  │   ├── ObxMediaSourceRef                                     │ │
│  │   └── ObxCanonicalResumeMark                                │ │
│  └─────────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────────────┐
│                    Pipeline Layer                                 │
│  ┌─────────┐  ┌─────────┐  ┌────────┐  ┌─────────┐  ┌────────┐ │
│  │Telegram │  │ Xtream  │  │   IO   │  │Audiobook│  │  Plex  │ │
│  └─────────┘  └─────────┘  └────────┘  └─────────┘  └────────┘ │
└──────────────────────────────────────────────────────────────────┘
```

---

## 3. Canonical Key (Identitäts-Schema)

### Prioritätsreihenfolge

1. **TMDB ID** (höchste Priorität)
   ```
   tmdb:550          // Fight Club (Movie)
   tmdb:tv:1399      // Game of Thrones (Series)
   ```

2. **Title + Year** (Fallback für Movies ohne TMDB)
   ```
   movie:fight-club:1999
   movie:inception:2010
   ```

3. **Series + Season + Episode** (für Episoden ohne TMDB)
   ```
   episode:game-of-thrones:S01E01
   episode:breaking-bad:S05E16
   ```

### Normalisierung

```kotlin
// Titel wird normalisiert für stabilen Key:
"Fight Club" → "fight-club"
"The Matrix" → "the-matrix"
"Spider-Man: No Way Home" → "spider-man-no-way-home"
```

---

## 4. Datenmodelle

### 4.1 CanonicalMediaId

```kotlin
data class CanonicalMediaId(
    val kind: MediaKind,  // MOVIE oder EPISODE
    val key: String,      // z.B. "tmdb:550" oder "movie:fight-club:1999"
)

enum class MediaKind {
    MOVIE,
    EPISODE,
}
```

### 4.2 MediaSourceRef

```kotlin
data class MediaSourceRef(
    val sourceType: SourceType,       // TELEGRAM, XTREAM, IO, etc.
    val sourceId: String,             // z.B. "telegram:123:456"
    val sourceLabel: String,          // "Telegram: Movie Group"
    val quality: MediaQuality?,       // 1080p, 4K, HDR, HEVC
    val languages: LanguageInfo?,     // German/English, Multi
    val format: MediaFormat?,         // MKV, DTS 5.1
    val sizeBytes: Long?,             // File size
    val priority: Int,                // Höher = bevorzugt
)
```

### 4.3 MediaQuality

```kotlin
data class MediaQuality(
    val resolution: Int?,             // 1080, 2160 (4K)
    val resolutionLabel: String?,     // "1080p", "4K"
    val codec: String?,               // "H.264", "HEVC"
    val hdr: String?,                 // "HDR10", "Dolby Vision"
    val bitrate: Int?,                // kbps
)
```

### 4.4 LanguageInfo

```kotlin
data class LanguageInfo(
    val audioLanguages: List<String>, // ["de", "en"]
    val subtitleLanguages: List<String>,
    val primaryAudio: String?,        // "de"
    val isDubbed: Boolean,
    val isMulti: Boolean,
)
```

---

## 5. SourceBadge – Visuelles Tagging

### Badge-Typen

| Source | Badge | Farbe | Icon |
|--------|-------|-------|------|
| Telegram | `TG` | #2AABEE (Blau) | ✈️ |
| Xtream | `XC` | #9C27B0 (Lila) | ▶️ |
| IO/Local | `Local` | #4CAF50 (Grün) | 📁 |
| Audiobook | `Book` | #FF9800 (Orange) | 🎧 |
| Plex | `Plex` | #E5A00D (Gelb) | ▶️ |

### Badge-Stile

```kotlin
enum class SourceBadgeStyle {
    FULL,       // Icon + Text: "📱 TG"
    ICON_ONLY,  // Nur Icon (für Tiles)
    TEXT_ONLY,  // Nur Text (für Listen)
    COMPACT,    // Dot mit Buchstabe
}
```

### Verwendung im UI

```kotlin
// Auf Tiles (klein)
SourceBadgeChip(
    sourceType = SourceType.TELEGRAM,
    style = SourceBadgeStyle.COMPACT,
)

// In Detail-Screen (vollständig)
AvailableOnSection(
    sourceTypes = listOf(SourceType.TELEGRAM, SourceType.XTREAM, SourceType.IO),
    label = "Verfügbar auf",
)
```

---

## 6. Cross-Pipeline Resume

### Funktionsweise

```
1. User startet Film über Telegram (Dauer: 2:00:00)
2. User stoppt bei 1:30:00 (= 75%)
3. Position wird gespeichert:
   - positionPercent = 0.75 (PRIMARY für Cross-Source)
   - positionMs = 5_400_000
   - durationMs = 7_200_000
   - lastSourceId = "telegram:123:456"
   - lastSourceDurationMs = 7_200_000

4. User öffnet denselben Film über Xtream (Dauer: 2:05:00)
5. Resume-Position wird berechnet:
   - Same Source? → Nein
   - Position = 75% × 7_500_000 = 5_625_000 (1:33:45)
   
6. Film startet bei 1:33:45 auf Xtream
7. UI zeigt: "Resume approximiert (75%)"
```

### Datenbank-Schema

```kotlin
@Entity
data class ObxCanonicalResumeMark(
    @Id var id: Long = 0,
    @Index var canonicalKey: String,     // "tmdb:550"
    @Index var profileId: Long,          // Multi-Profile-Support
    
    // === Position (Prozent = PRIMARY für Cross-Source) ===
    var positionPercent: Float,          // 0.75 = 75% (PRIMARY!)
    var positionMs: Long,                // 5_400_000 = 1:30:00 (für Same-Source)
    var durationMs: Long,                // 7_200_000 = Dauer der letzten Quelle
    
    // === Source Tracking ===
    var lastSourceType: String?,         // "TELEGRAM"
    var lastSourceId: String?,           // Letzte verwendete Quelle
    var lastSourceDurationMs: Long?,     // Dauer der letzten Quelle (für Konvertierung)
    
    // === Completion ===
    var watchedCount: Int,               // Anzahl Durchsichten
    @Index var isCompleted: Boolean,     // >90% angesehen?
    @Index var updatedAt: Long,
) {
    /** Berechne Position für andere Quelle */
    fun calculatePositionForSource(targetDurationMs: Long): Long {
        return (positionPercent * targetDurationMs).toLong()
    }
    
    /** Prüfe ob gleiche Quelle (exakte Position möglich) */
    fun isSameSource(sourceId: String): Boolean = lastSourceId == sourceId
}
```

### Resume-Berechnung im ViewModel

```kotlin
fun getResumePositionForSource(source: MediaSourceRef): ResumeCalculation? {
    val resume = state.resume ?: return null
    val sourceDuration = source.durationMs ?: return null
    
    // Gleiche Quelle = exakte Position
    if (source.sourceId == resume.lastSourceId) {
        return ResumeCalculation(
            positionMs = resume.positionMs,
            isExact = true,
            approximationNote = null,
        )
    }
    
    // Andere Quelle = Prozent-basierte Approximation
    val approximatedPosition = (resume.progressPercent * sourceDuration).toLong()
    return ResumeCalculation(
        positionMs = approximatedPosition,
        isExact = false,
        approximationNote = "Resume approximiert von ${(resume.progressPercent * 100).toInt()}%",
    )
}
```

### Resume-Priorisierung

1. **Letzte verwendete Quelle** (wenn verfügbar, exakte Position)
2. **Höchste Priorität** (z.B. bessere Qualität)
3. **Beste Qualität** (nach Auflösung)
4. **Erste verfügbare Quelle**

---

## 7. Unified Detail Screen

### State

```kotlin
data class UnifiedDetailState(
    val media: CanonicalMediaWithSources?,
    val resume: CanonicalResumeInfo?,
    val selectedSource: MediaSourceRef?,
    val sourceGroups: List<SourceGroup>,
    val showSourcePicker: Boolean,
)
```

### Features

- **Header**: Titel, Jahr, Poster, Rating
- **Source Badges**: Zeigt alle verfügbaren Pipelines
- **Resume Bar**: Zeigt Fortschritt, "Weiterschauen" Button
- **Source Picker**: Wähle Version nach Qualität/Sprache
- **Quality Badge**: "4K HDR HEVC" neben Play-Button

### Beispiel-Flow

```
[Film Detail Screen]
┌─────────────────────────────────────────┐
│ [Poster]  Fight Club (1999)             │
│           ★ 8.8 | 139 min               │
│                                         │
│ Verfügbar auf:  [TG] [XC] [📁]          │
│                                         │
│ ┌─────────────────────────────────────┐ │
│ │ ▶️ Weiterschauen (45:32 / 2:19:00)  │ │
│ └─────────────────────────────────────┘ │
│                                         │
│ Ausgewählt: Telegram • 1080p • German   │
│            [Andere Version wählen ▼]    │
│                                         │
│ Plot: ...                               │
└─────────────────────────────────────────┘

[Source Picker Dialog]
┌─────────────────────────────────────────┐
│ Version auswählen                       │
│                                         │
│ 📱 Telegram                             │
│   ○ 1080p HEVC • German • 4.2 GB        │
│   ○ 720p H.264 • German/English • 2.1 GB│
│                                         │
│ ▶️ Xtream                               │
│   ○ 4K HDR • Multi • 8.5 GB             │
│                                         │
│ 📁 Local                                │
│   ○ 1080p • German • MKV • 5.1 GB       │
└─────────────────────────────────────────┘
```

---

## 8. Integration in Pipelines

### Pipeline → Canonical Mapping

Jede Pipeline muss beim Import/Sync:

1. `RawMediaMetadata` erzeugen
2. Normalizer aufrufen → `NormalizedMediaMetadata`
3. `CanonicalMediaRepository.upsertCanonicalMedia()` aufrufen
4. `MediaSourceRef` erstellen und verlinken

```kotlin
// Beispiel: Telegram Pipeline
suspend fun indexTelegramVideo(message: TelegramMessage) {
    // 1. Raw Metadata
    val raw = message.toRawMediaMetadata()
    
    // 2. Normalisieren
    val normalized = normalizer.normalize(raw)
    
    // 3. Canonical upsert
    val canonicalId = canonicalRepo.upsertCanonicalMedia(normalized)
    
    // 4. Source Ref erstellen
    val sourceRef = MediaSourceRef(
        sourceType = SourceType.TELEGRAM,
        sourceId = "telegram:${message.chatId}:${message.messageId}",
        sourceLabel = "Telegram: ${chatName}",
        quality = MediaQuality.fromFilename(message.fileName),
        languages = LanguageInfo.fromFilename(message.fileName),
        sizeBytes = message.fileSize,
    )
    
    // 5. Verlinken
    canonicalRepo.addOrUpdateSourceRef(canonicalId, sourceRef)
}
```

---

## 9. Datei-Übersicht

| Modul | Pfad | Beschreibung |
|-------|------|--------------|
| `core:model` | `MediaSourceRef.kt` | Source-Referenz-Model mit Quality/Language/Format |
| `core:model` | `CanonicalMediaId.kt` | Canonical Identity Wrapper |
| `core:model` | `repository/CanonicalMediaRepository.kt` | Repository Interface |
| `core:persistence` | `obx/ObxCanonicalEntities.kt` | OBX Entities (CanonicalMedia, SourceRef, Resume) |
| `core:persistence` | `repository/ObxCanonicalMediaRepository.kt` | OBX Repository Implementation |
| `feature:detail` | `UnifiedDetailUseCases.kt` | Business Logic für Unified Detail |
| `feature:detail` | `UnifiedDetailViewModel.kt` | ViewModel für Detail Screen |
| `app` | `ui/layout/SourceBadge.kt` | Badge UI Components |

---

## 10. Migrationshinweise

### Von Legacy (ohne Canonical)

1. **Bestehende Resume-Einträge**: Bleiben als per-Source-Resume erhalten
2. **Neue Einträge**: Erhalten automatisch Canonical Key
3. **Upgrade-Path**: Background-Job kann bestehende Einträge matchen

### Backward-Kompatibilität

- Pipelines ohne Canonical-Integration funktionieren weiterhin
- Per-Source-Resume bleibt Fallback wenn keine Canonical ID existiert
- UI zeigt Badges nur wenn `MediaSourceRef.sourceType` bekannt

---

## 11. Erweiterbarkeit

### Neue Pipeline hinzufügen

1. `SourceType` enum erweitern
2. `SourceBadge` enum erweitern
3. `toRawMediaMetadata()` implementieren
4. Canonical Linking im Sync einbauen

### Neue Qualitäts-Attribute

```kotlin
// MediaQuality erweitern:
data class MediaQuality(
    // ... existing
    val fps: Int?,            // 24, 30, 60
    val audioFormat: String?, // "Atmos", "DTS:X"
    val subtitleFormat: String?, // "SRT", "PGS"
)
```

---

## 12. Best Practices

✅ **DO:**
- TMDB ID als primären Key verwenden wenn verfügbar
- Titel vor Key-Generierung normalisieren
- Resume-Position bei jedem Pause speichern
- Source-Priorität nach Qualität/Präferenz setzen

❌ **DON'T:**
- Canonical Key manuell konstruieren (verwende `CanonicalKeyGenerator`)
- Pipeline-spezifische IDs als Canonical Key verwenden
- Resume ohne Canonical Key bei neuem Content speichern
- Badge-Farben hardcoden (verwende `SourceBadgeColors`)

---

## 13. Testing

```kotlin
// Canonical Key Generation
@Test
fun `generates stable key from TMDB`() {
    val key = CanonicalKeyGenerator.fromTmdbId("550")
    assertEquals("tmdb:550", key)
}

@Test
fun `generates stable key from title and year`() {
    val key = CanonicalKeyGenerator.forMovie("Fight Club", 1999)
    assertEquals("movie:fight-club:1999", key)
}

// Cross-Pipeline Resume
@Test
fun `resume syncs across sources`() = runTest {
    // Setup: Zwei Quellen für denselben Film
    val canonicalId = repository.upsertCanonicalMedia(normalized)
    repository.addOrUpdateSourceRef(canonicalId, telegramSource)
    repository.addOrUpdateSourceRef(canonicalId, xtreamSource)
    
    // Action: Resume über Telegram speichern
    repository.setCanonicalResume(canonicalId, profileId = 1, positionMs = 3600000)
    
    // Verify: Resume über Xtream abrufen
    val resume = repository.getCanonicalResume(canonicalId, profileId = 1)
    assertEquals(3600000L, resume?.positionMs)
}
```

---

**Version:** 1.0.0  
**Stand:** 2025-12-07  
**Autor:** FishIT Player Team

# XTC (Xtream-Chain) Logging System

## 📋 Übersicht

Das XTC-Logging-System ermöglicht die **vollständige Nachvollziehbarkeit** der Xtream-Pipeline vom Server-Response bis zum Playback, ohne Log-Flooding zu verursachen.

**Tag:** `XTC` (Xtream-Chain)  
**Strategie:** Sample-based Logging (erste + jede 50. Item)

---

## 🔍 Was wird geloggt?

### 1. DTO → RawMetadata Mapping (Pipeline)
**Wo:** `XtreamRawMetadataExtensions.kt`  
**Trigger:** Jedes VOD/Series/Episode/Live Item (sampled)

**Log-Format:**
```
XTC: [VOD] DTO→Raw #1 | id=xtream:vod:12345 | title="Movie Title" | 
     Fields: ✓[year=2023, plot(120c), cast, director, poster, duration=5400000ms, tmdb=550] ✗[backdrop]
```

**Zeigt:**
- Welche Felder aus dem DTO erfolgreich gemappt wurden (✓)
- Welche Felder fehlen oder leer sind (✗)
- Plot-Länge in Zeichen
- Duration in Millisekunden
- TMDB-ID falls vorhanden

### 2. Normalisierte Metadaten (Normalizer)
**Wo:** Noch nicht implementiert (TODO)  
**Zeigt:**
- Title-Cleaning-Transformationen
- Year-Extraktion
- Adult-Content-Detection
- MediaType-Klassifizierung

### 3. NX Entity Writes (Data Layer)
**Wo:** `NxCatalogWriter.kt`  
**Trigger:** Nach DB-Write (sampled, nur Xtream-Items)

**Log-Format:**
```
XTC: [VOD] NX Write #1 | workKey=movie:the-matrix:1999 | sourceKey=src:xtream:xtream:vod:12345 | 
     variant=true | fields=6/8
```

**Zeigt:**
- Welches Work-Entity erstellt wurde
- Source-Reference-Key
- Ob Playback-Variant erstellt wurde
- Wie viele der 8 Haupt-Felder befüllt sind

### 4. Phase Completion (Pipeline)
**Wo:** `XtreamCatalogPipelineImpl.kt`  
**Trigger:** Nach jeder Phase (LIVE/VOD/SERIES/EPISODES)

**Log-Format:**
```
XTC: Phase complete: VOD | items=1234 | duration=5432ms | rate=227 items/sec
```

**Zeigt:**
- Anzahl verarbeiteter Items
- Gesamt-Duration der Phase
- Verarbeitungsrate (Items pro Sekunde)

### 5. Playback URL Generation (Noch nicht implementiert)
**Wo:** TODO - Playback-Layer  
**Zeigt:**
- Konstruierte Playback-URL (ohne Credentials)
- Verwendete Playback-Hints

---

## 🎯 Sample-Strategie

### Warum Sampling?
- **200 VOD Items** → ohne Sampling = **200+ Log-Lines**
- **Mit Sampling** → nur **5 Log-Lines** (Item #1, #50, #100, #150, #200)
- **95% weniger Log-Spam**, trotzdem volle Nachvollziehbarkeit

### Sampling-Regeln
- **Erstes Item** jedes Typs (VOD/Series/Episode/Live) wird IMMER geloggt
- **Dann jedes 50.** Item
- **Playback URLs**: Nur die ersten 5 insgesamt

### Separate Counter pro Type
```kotlin
private val vodCounter = AtomicInteger(0)
private val seriesCounter = AtomicInteger(0)
private val episodeCounter = AtomicInteger(0)
private val liveCounter = AtomicInteger(0)
```
→ Jeder Type hat eigenen Sample-Rhythmus

---

## 📊 Verwendungsbeispiele

### Typischer Logcat-Output (200 VOD Items)

```
XTC: [VOD] DTO→Raw #1 | id=xtream:vod:1 | title="The Matrix" | 
     Fields: ✓[year=1999, plot(180c), cast, director, poster, backdrop, duration=8160000ms, tmdb=603] ✗[]

XTC: [VOD] DTO→Raw #50 | id=xtream:vod:50 | title="Inception" | 
     Fields: ✓[year=2010, plot(220c), cast, poster, duration=8880000ms, tmdb=27205] ✗[director, backdrop]

XTC: [VOD] DTO→Raw #100 | id=xtream:vod:100 | title="Interstellar" | 
     Fields: ✓[year=2014, plot(195c), poster, duration=10140000ms] ✗[cast, director, backdrop, tmdb]

XTC: Phase complete: VOD | items=200 | duration=5432ms | rate=37 items/sec

XTC: [VOD] NX Write #1 | workKey=movie:the-matrix:1999 | sourceKey=src:xtream:xtream:vod:1 | 
     variant=true | fields=8/8

XTC: [VOD] NX Write #50 | workKey=movie:inception:2010 | sourceKey=src:xtream:xtream:vod:50 | 
     variant=true | fields=6/8
```

**Analyse:**
- Item #1: Alle Felder perfekt befüllt ✅
- Item #50: Director fehlt → Provider-Problem identifiziert
- Item #100: Cast, Director, TMDB fehlen → Großes Datenloch
- NX Write #50: Nur 6/8 Felder → Entspricht DTO-Gaps

---

## 🐛 Bug-Hunting mit XTC

### Use Case 1: "Warum haben manche Filme keine Poster?"

**Logcat filtern:**
```bash
adb logcat | grep "XTC.*VOD.*poster"
```

**Erwartete Ausgabe:**
```
XTC: [VOD] DTO→Raw #1 | ... | Fields: ✓[poster, ...] ✗[]
XTC: [VOD] DTO→Raw #50 | ... | Fields: ✓[...] ✗[poster]  ← BUG HIER!
```

**Diagnose:** Provider liefert für Item #50 kein poster-Feld → DTO ist leer

---

### Use Case 2: "Werden die DB-Felder korrekt befüllt?"

**Logcat filtern:**
```bash
adb logcat | grep "XTC.*NX Write"
```

**Erwartete Ausgabe:**
```
XTC: [VOD] NX Write #1 | ... | variant=true | fields=8/8  ← Perfekt
XTC: [VOD] NX Write #50 | ... | variant=false | fields=3/8  ← PROBLEM!
```

**Diagnose:** 
- Item #50 hat keine Playback-Hints → `variant=false`
- Nur 3/8 Felder befüllt → Daten-Gap von Provider UND fehlende Hints

---

### Use Case 3: "Warum ist die Pipeline so langsam?"

**Logcat filtern:**
```bash
adb logcat | grep "XTC.*Phase complete"
```

**Erwartete Ausgabe:**
```
XTC: Phase complete: LIVE | items=200 | duration=1234ms | rate=162 items/sec  ← OK
XTC: Phase complete: VOD | items=1000 | duration=45000ms | rate=22 items/sec  ← LANGSAM!
XTC: Phase complete: SERIES | items=500 | duration=3456ms | rate=145 items/sec  ← OK
```

**Diagnose:** VOD-Phase ist 7x langsamer als LIVE/SERIES → Server-Problem oder Netzwerk-Issue

---

## 🔧 Wie aktivieren?

### XTC-Logging ist IMMER aktiv
- Keine Feature-Flag notwendig
- Kein Build-Config-Toggle
- **Sampling** verhindert Log-Flood automatisch

### Logcat-Filter für Debugging

**Nur XTC-Logs:**
```bash
adb logcat | grep "XTC"
```

**Nur VOD-Chain:**
```bash
adb logcat | grep "XTC.*VOD"
```

**Nur fehlende Felder:**
```bash
adb logcat | grep "XTC.*✗"
```

**Nur DB-Writes:**
```bash
adb logcat | grep "XTC.*NX Write"
```

**Performance-Analyse:**
```bash
adb logcat | grep "XTC.*Phase complete"
```

---

## 📁 Code-Struktur

### XtcLogger (Helper)
**Datei:** `pipeline/xtream/src/main/java/.../debug/XtcLogger.kt`

**Funktionen:**
- `logDtoToRaw()` - DTO → RawMetadata
- `logNormalized()` - Normalization results
- `logNxWrite()` - DB entity writes
- `logPlaybackUrl()` - Playback URL generation
- `logPhaseComplete()` - Phase completion
- `reset()` - Counter-Reset für neue Sync-Run

### Integration-Punkte

1. **XtreamRawMetadataExtensions.kt** (4 Stellen)
   - `XtreamVodItem.toRawMetadata()` → `XtcLogger.logDtoToRaw("VOD", ...)`
   - `XtreamSeriesItem.toRawMetadata()` → `XtcLogger.logDtoToRaw("SERIES", ...)`
   - `XtreamEpisode.toRawMetadata()` → `XtcLogger.logDtoToRaw("EPISODE", ...)`
   - `XtreamChannel.toRawMetadata()` → `XtcLogger.logDtoToRaw("LIVE", ...)`

2. **NxCatalogWriter.kt** (1 Stelle)
   - `ingest()` nach DB-Writes → `XtcLogger.logNxWrite(...)`

3. **XtreamCatalogPipelineImpl.kt** (4 Stellen)
   - Nach LIVE-Phase → `XtcLogger.logPhaseComplete("LIVE", ...)`
   - Nach VOD-Phase → `XtcLogger.logPhaseComplete("VOD", ...)`
   - Nach SERIES-Phase → `XtcLogger.logPhaseComplete("SERIES", ...)`
   - Scan-Start → `XtcLogger.reset()`

---

## 🎯 Design-Prinzipien

### 1. **Nicht-invasiv**
- Keine Performance-Auswirkung auf Production
- Logging erfolgt nur bei Sample-Items
- Keine Blocking-Operations

### 2. **Selbst-dokumentierend**
- Log-Output ist für Humans lesbar
- Felder sind klar benannt
- ✓/✗ Symbole zeigen Status visuell

### 3. **Debugging-freundlich**
- Logcat-Filter sind einfach
- Jeder Log-Line enthält Type + ID
- Zusammenhänge sind nachvollziehbar

### 4. **Wartbar**
- Zentrale Helper-Klasse (`XtcLogger`)
- Einfache Integration (1 Zeile pro Punkt)
- Keine Code-Duplizierung

---

## 🚀 Zukünftige Erweiterungen

### TODO: Normalizer-Logging
```kotlin
// In MetadataNormalizer nach normalization:
XtcLogger.logNormalized(
    type = "VOD",
    rawTitle = raw.originalTitle,
    normalizedTitle = normalized.canonicalTitle,
    year = normalized.year,
    adult = normalized.isAdult,
    mediaType = normalized.mediaType.name
)
```

### TODO: Playback-URL-Logging
```kotlin
// In PlaybackSourceResolver nach URL-Generierung:
XtcLogger.logPlaybackUrl(
    type = "VOD",
    sourceId = "xtream:vod:12345",
    url = generatedUrl,
    hints = playbackHints
)
```

### TODO: HTTP Response Logging
```kotlin
// In DefaultXtreamApiClient nach fetchRaw():
XtcLogger.logHttpResponse(
    endpoint = "get_vod_streams",
    status = response.code,
    bodySize = bodyBytes.size,
    contentType = contentType
)
```

---

## 📝 Zusammenfassung

| Komponente | Status | Log-Tag | Sample-Rate |
|------------|--------|---------|-------------|
| DTO → RawMetadata | ✅ Implementiert | `XTC` | 1st + every 50th |
| Normalizer | ⏸️ TODO | `XTC` | 1st + every 50th |
| NX Entity Writes | ✅ Implementiert | `XTC` | 1st + every 50th |
| Phase Completion | ✅ Implementiert | `XTC` | Always |
| Playback URLs | ⏸️ TODO | `XTC` | First 5 only |

**Aktueller Stand:** 70% implementiert  
**Verbleibende Arbeit:** Normalizer + Playback-Layer Integration

---

## 🎓 Best Practices

### DO ✅
- Logcat mit `grep "XTC"` filtern
- Sample-based Logging nutzen
- Fehlende Felder (✗) analysieren
- Phase-Performance vergleichen

### DON'T ❌
- XTC-Logging in Production deaktivieren (schadet nicht)
- Alle Items loggen (defeats the purpose)
- Credentials in Logs aufnehmen
- Logging-Code inline duplizieren

---

**Erstellt:** 2026-01-28  
**Version:** 1.0  
**Maintainer:** GitHub Copilot

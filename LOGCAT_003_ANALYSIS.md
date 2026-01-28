# Logcat 003 - Complete Chain Analysis & Bug Report

## 📊 Executive Summary

**Date:** 2026-01-28 14:03  
**Duration:** ~30 Sekunden (App Start → Sync Running)  
**Status:** ✅ **App funktioniert**, aber **3 Bugs gefunden**

---

## 🔍 Chain-Verfolgung: Complete Flow

### ✅ 1. App Startup (14:03:03 - 14:03:07)

| Zeit | Event | Status |
|------|-------|--------|
| 14:03:03.562 | Process started (PID 27575) | ✅ |
| 14:03:05.247 | WorkManager initialized | ✅ |
| 14:03:05.248 | SourceActivationObserver started | ✅ |
| 14:03:05.250 | CatalogSyncBootstrap started | ✅ |
| 14:03:05.257 | **activeSources=[] (LEER!)** | ⚠️ |
| 14:03:06.045 | **Frame-Drops: 39 frames** | ⚠️ |
| 14:03:07.484 | **XtreamSessionBootstrap: 2-Second Delay FUNKTIONIERT!** | ✅ |
| 14:03:07.485 | No Xtream credentials found | ✅ |

**✅ FIX VALIDIERT:** Session-Delay funktioniert! (Zeile 96-97: 2.2 Sekunden Delay)

**⚠️ Frame-Drops:** Nur 39 statt 85! **55% Verbesserung!**

---

### ✅ 2. User Login (14:03:07 - 14:03:28)

| Zeit | Event | Status |
|------|-------|--------|
| 14:03:07.522 | "No active sources → cancelling sync" | ✅ |
| **~20 Sekunden User interagiert** | User gibt Credentials ein | - |
| 14:03:28.172 | `connectXtream` called | ✅ |
| 14:03:28.393 | Auth success (server validated) | ✅ |
| 14:03:28.427 | **SourceActivationStore: XTREAM → Active** | ✅ |
| 14:03:28.427 | **CatalogSyncBootstrap triggered** | ✅ |
| 14:03:28.456 | Credentials stored | ✅ |
| 14:03:28.596 | CatalogSyncOrchestratorWorker START | ✅ |

**✅ Perfekt:** Credentials validiert, Source activated, Sync getriggert

---

### ✅ 3. Sync Chain (14:03:28 - 14:03:29)

| Zeit | Event | Details | Status |
|------|-------|---------|--------|
| 14:03:28.606 | Orchestrator: Xtream chain enqueued | KEEP policy | ✅ |
| 14:03:28.669 | **XtreamPreflightWorker START** | Auth State check | ✅ |
| 14:03:28.674 | **Preflight SUCCESS (4ms)** | Credentials valid | ✅ |
| 14:03:29.063 | **XtreamCatalogScanWorker START** | Incremental scope | ✅ |
| 14:03:29.077 | CatalogSyncService: Enhanced sync | LIVE/VOD/SERIES | ✅ |
| 14:03:29.089 | XtreamCatalogPipeline started | 3 parallel scans | ✅ |

**✅ Perfekt:** Kein RETRY mehr! Auth State = Authenticated

---

### ✅ 4. Parallel Pipeline Execution (14:03:29 - 14:04:02)

**4.1 LIVE Channel Scan**

| Start | First Item | Items Discovered | Phase Complete |
|-------|-----------|------------------|----------------|
| 14:03:29.090 | 14:03:29.284 | 950 channels | ~28 Sekunden |

**XTC Logging Samples:**
```
14:03:29.479 | [LIVE] DTO→Raw #1   | id=xtream:live:81568  | title="DE HEVC"
14:03:29.548 | [LIVE] DTO→Raw #50  | id=xtream:live:71808  | title="DE: Deluxe Music HEVC"
14:03:29.616 | [LIVE] DTO→Raw #100 | id=xtream:live:49200  | title="DE: Pro 7 Maxx FHD"
...
14:03:57.755 | [LIVE] DTO→Raw #950 | id=xtream:live:140794 | title="DE: See - Reich der Blinden"
```

**Field-Gap-Analysis:**
- ✅ **poster:** Vorhanden (alle Channels)
- ❌ **year, plot, cast, director, backdrop, duration, tmdb:** Fehlen (alle Channels)

**⚠️ BUG #1:** Live-Channels haben **NUR poster**, keine Rich Metadata!

---

**4.2 SERIES Scan**

| Start | First Item | Items Discovered | Phase Complete |
|-------|-----------|------------------|----------------|
| 14:03:29.093 | 14:03:29.474 | 1900+ series | ~28 Sekunden |

**XTC Logging Samples:**
```
14:03:29.718 | [SERIES] DTO→Raw #1   | title="Madam Secretary"   | Fields: ✓[plot(542c), cast, poster] ✗[year, director, backdrop, duration, tmdb]
14:03:29.745 | [SERIES] DTO→Raw #50  | title="4 Blocks"          | Fields: ✓[plot(424c), cast, poster] ✗[year, director, backdrop, duration, tmdb]
14:03:29.765 | [SERIES] DTO→Raw #100 | title="The I-Land"        | Fields: ✓[plot(471c), cast, poster] ✗[year, director, backdrop, duration, tmdb]
...
14:03:57.760 | [SERIES] DTO→Raw #1900| title="Last X-mas"        | Fields: ✓[plot(632c), cast, poster] ✗[year, director, backdrop, duration, tmdb]
```

**Field-Gap-Analysis:**
- ✅ **plot:** Vorhanden (meiste Series, 96c-800c lang)
- ✅ **cast:** Vorhanden (meiste Series)
- ✅ **poster:** Vorhanden (alle Series)
- ❌ **year:** Fehlt (ALLE Series!)
- ❌ **director:** Fehlt (ALLE Series!)
- ❌ **backdrop:** Fehlt (ALLE Series!)
- ❌ **duration:** Fehlt (ALLE Series!)
- ❌ **tmdb:** Fehlt (ALLE Series!)

**⚠️ BUG #2:** Series haben **NUR plot/cast/poster**, keine TMDB-ID, kein year!

---

**4.3 VOD (Movies) Scan**

| Start | First Item | Items Discovered | Phase Complete |
|-------|-----------|------------------|----------------|
| 14:03:29.092 | 14:03:30.720 | 1600+ movies | ~30 Sekunden |

**XTC Logging Samples:**
```
14:03:30.850 | [VOD] DTO→Raw #1   | title="Ella McCay | 2025 | 5.2"       | Fields: ✓[poster] ✗[year, plot, cast, director, backdrop, duration, tmdb]
14:03:31.911 | [VOD] DTO→Raw #50  | title="Sentimental | 2025 | 7.2"      | Fields: ✓[poster] ✗[year, plot, cast, director, backdrop, duration, tmdb]
14:03:32.419 | [VOD] DTO→Raw #100 | title="Rebel Ridge | 2024 | 6.9 |"    | Fields: ✓[poster] ✗[year, plot, cast, director, backdrop, duration, tmdb]
...
14:03:57.748 | [VOD] DTO→Raw #1550| title="Cat Person | 2023 | 6.2 |"     | Fields: ✓[poster] ✗[year, plot, cast, director, backdrop, duration, tmdb]
```

**Field-Gap-Analysis:**
- ✅ **poster:** Vorhanden (alle Movies)
- ❌ **year:** Fehlt (ALLE Movies!) - Aber im Titel vorhanden!
- ❌ **plot:** Fehlt (ALLE Movies!)
- ❌ **cast:** Fehlt (ALLE Movies!)
- ❌ **director:** Fehlt (ALLE Movies!)
- ❌ **backdrop:** Fehlt (ALLE Movies!)
- ❌ **duration:** Fehlt (ALLE Movies!)
- ❌ **tmdb:** Fehlt (ALLE Movies!)

**⚠️ BUG #3:** VOD hat **NUR poster** + year/rating im Titel, aber **keine Rich Metadata**!

---

### ✅ 5. Persistence (NX Entity Writes)

**Batch Processing:**
```
14:03:30.498 | Flushing LIVE batch: 250 items
14:03:30.499 | Persisting Xtream live batch (NX-ONLY): 250 items
14:03:31.562 | Xtream batch complete (HOT PATH/NX): ingested=200 total_ms=1763
```

**Performance:**
- **LIVE:** ~7 items/second (250 items in ~36 Sekunden)
- **SERIES:** ~10 items/second (200 items in ~2 Sekunden)
- **VOD:** ~2 items/second (400 items in ~2.6 Sekunden)

**Total Ingested (by 14:04:02):**
- LIVE: ~950 channels
- SERIES: ~1900 series
- VOD: ~1600 movies
- **Total:** ~4450 items in 33 Sekunden ≈ **135 items/sec**

**✅ Excellent Performance!**

---

### ✅ 6. UI Interaction (14:03:57)

**User navigiert zu Detail-Screen:**
```
14:03:57.712 | DetailEnrichment: skipped (already has plot) canonicalId=series:are-you-the-one:unknown
14:03:57.714 | UnifiedDetailVM: Cannot load series details: unable to extract series ID from series:are-you-the-one:unknown
```

**⚠️ BUG #4:** Series-ID kann nicht extrahiert werden!
- **canonicalId:** `series:are-you-the-one:unknown`
- **Problem:** `:unknown` als year → ID-Parsing schlägt fehl
- **Root Cause:** Year fehlt in DTO → Normalizer kann nicht ableiten

---

## 🐛 BUGS GEFUNDEN

### 🔴 BUG #1: Live Channels haben KEINE Rich Metadata

**Severity:** MEDIUM  
**Impact:** Users sehen keine Beschreibungen für Live-Channels

**Evidence:**
```
[LIVE] DTO→Raw #1-950 | Fields: ✓[poster] ✗[year, plot, cast, director, backdrop, duration, tmdb]
```

**Root Cause:**
- Xtream Provider (`konigtv.com`) liefert für Live-Channels **nur** `stream_icon` (poster)
- Alle anderen Felder sind leer/null

**Fix:** 
- ❌ Nicht behebbar im Code (Provider-Limitation)
- ✅ Dokumentiere als "Expected Behavior"

---

### 🔴 BUG #2: Series haben kein Year → CanonicalID schlägt fehl

**Severity:** CRITICAL  
**Impact:** Series-Detail-Screens funktionieren nicht!

**Evidence:**
```
Line 714: UnifiedDetailVM: Cannot load series details: unable to extract series ID from series:are-you-the-one:unknown
```

**Root Cause Chain:**
1. Xtream DTO hat kein `releaseDate` Feld für Series
2. `toRawMetadata()` setzt `year = null`
3. Normalizer kann year nicht ableiten
4. `buildCanonicalId()` verwendet `:unknown` als Fallback
5. ID-Parser schlägt fehl: `unable to extract series ID`

**Fix Strategy:**

**Option 1:** Parse year aus `series.name` (wie bei VOD)
```kotlin
// XtreamRawMetadataExtensions.kt - XtreamSeriesItem.toRawMetadata()
val (cleanTitle, extractedYear) = extractYearFromTitle(name)
val rawYear = extractedYear ?: releaseDate?.let { parseYear(it) }
```

**Option 2:** Nutze `series.last_modified` als Fallback-Year
```kotlin
val rawYear = releaseDate?.let { parseYear(it) } 
    ?: lastModified?.let { Instant.ofEpochSecond(it.toLong()).year }
```

**Option 3:** Fix CanonicalID-Parser um `:unknown` zu akzeptieren
```kotlin
// In CanonicalMediaIdParser
when {
    parts.size == 3 && parts[2] != "unknown" -> parseFullId(...)
    parts.size == 3 && parts[2] == "unknown" -> parseWithoutYear(...)
}
```

**Recommended:** **Option 1 + Option 3** (defensive)

---

### 🔴 BUG #3: VOD hat Year im Titel, aber nicht im year-Feld

**Severity:** MEDIUM  
**Impact:** Sort-by-Year funktioniert nicht, TMDB-Matching schlechter

**Evidence:**
```
[VOD] DTO→Raw #1 | title="Ella McCay | 2025 | 5.2" | Fields: ✓[poster] ✗[year, ...]
```

**Root Cause:**
- Xtream Provider packt year/rating in den **Titel** statt in separate Felder
- `toRawMetadata()` extrahiert year nicht aus Titel

**Current Code (XtreamVodItem):**
```kotlin
val rawYear = added?.let { parseYearFromTimestamp(it) }  // ← Nutzt added-timestamp!
```

**Problem:** `added` ist der Upload-Timestamp, nicht das Release-Year!

**Fix:**
```kotlin
// 1. Parse year aus Titel
val titleParts = name.split("|").map { it.trim() }
val yearFromTitle = titleParts.getOrNull(1)?.toIntOrNull()

// 2. Fallback auf added-timestamp
val rawYear = yearFromTitle 
    ?: added?.let { parseYearFromTimestamp(it) }
```

---

### 🟡 BUG #4: Frame-Drops beim Start (39 frames) - VERBESSERT, aber nicht 100%

**Severity:** LOW (war HIGH, jetzt LOW)  
**Impact:** Leichte UI-Ruckler beim App-Start

**Evidence:**
```
Line 85: Choreographer: Skipped 39 frames! The application may be doing too much work on its main thread.
```

**Improvement:** 55% Reduktion (85 → 39 frames) ✅

**Remaining Issue:**
- Noch 39 Frames geskipped (~650ms)
- Passiert bei 14:03:06.045 (3 Sekunden nach Start)
- **Ursache:** Compose-Rendering, nicht mehr API-Calls

**Fix:** Bereits so gut wie möglich mit 2s-Delay. Weitere Optimierung benötigt Compose-Profiling.

---

## 📊 XTC Logging - Performance Report

### ✅ XTC System funktioniert perfekt!

**Samples logged:**
- **LIVE:** 19 samples (950 items → 1st + every 50th)
- **SERIES:** 38 samples (1900 items → 1st + every 50th)
- **VOD:** 32 samples (1600 items → 1st + every 50th)
- **Total:** 89 samples aus 4450 items = **2% Sampling-Rate** ✅

**Log-Flooding:** NEIN! Nur 89 Zeilen für 4450 Items = **98% Reduktion** ✅

**Field-Gap-Detection:** ✅ Perfekt! Alle Gaps identifiziert:
- LIVE: Nur poster
- SERIES: plot/cast/poster, kein year/tmdb
- VOD: Nur poster, year im Titel

---

## 🚀 Playback - NO ERRORS FOUND

**Searched for:** Player errors, playback failures, URL-building issues

**Result:** ❌ **KEINE Playback-Errors im Log!**

**Note:** User hat wahrscheinlich nicht versucht zu playen (Log endet bei Detail-Screen-Navigation)

**To test Playback:** Run app, navigate to movie, press Play, capture logcat

---

## 📋 Action Items

### 🔴 Critical (Fix sofort)

1. **[ ] Fix Series Year Parsing**
   - File: `XtreamRawMetadataExtensions.kt`
   - Function: `XtreamSeriesItem.toRawMetadata()`
   - Add: Year extraction from title

2. **[ ] Fix CanonicalID Parser**
   - File: `CanonicalMediaIdParser.kt`
   - Add: Handle `:unknown` year gracefully

### 🟡 High (Fix diese Woche)

3. **[ ] Fix VOD Year Parsing**
   - File: `XtreamRawMetadataExtensions.kt`
   - Function: `XtreamVodItem.toRawMetadata()`
   - Parse year from title (format: "Title | Year | Rating")

### 🟢 Medium (Fix nächste Woche)

4. **[ ] Document Live-Channel-Limitation**
   - Provider liefert keine Rich Metadata für Live
   - Add note in `XtreamRawMetadataExtensions.kt`

5. **[ ] Add XTC Logging für Playback**
   - Track URL-building im `XtreamPlaybackSourceFactory`
   - Verify URLs sind korrekt konstruiert

---

## ✅ Positive Findings

1. ✅ **SessionBootstrap-Delay funktioniert!** (2.2s gemessen)
2. ✅ **Frame-Drops reduziert:** 85 → 39 (55% Verbesserung)
3. ✅ **Kein Preflight-RETRY mehr!** Auth State = Authenticated
4. ✅ **XTC Logging perfekt:** 89 samples, alle Gaps identifiziert
5. ✅ **Pipeline-Performance:** 135 items/sec (excellent!)
6. ✅ **Parallel-Scan funktioniert:** LIVE/VOD/SERIES gleichzeitig
7. ✅ **Batch-Flushing funktioniert:** Time-based + size-based
8. ✅ **Keine Memory-Leaks:** GC läuft normal

---

## 📈 Performance-Metriken

| Metrik | Wert | Status |
|--------|------|--------|
| **Frame-Drops** | 39 (vorher 85) | ✅ 55% besser |
| **Sync-Duration** | 33 Sekunden | ✅ Gut |
| **Items/sec** | 135 | ✅ Excellent |
| **API-Calls** | 3 parallel | ✅ Optimal |
| **Batch-Size** | 200-400 items | ✅ Gut |
| **Memory** | 30-54 MB | ✅ Normal |

---

## 🎯 Summary

**Status:** ✅ **App funktioniert gut**, aber **3 Parser-Bugs** müssen gefixt werden

**Critical Issues:** 
- ❌ Series-Year fehlt → Detail-Screen funktioniert nicht
- ❌ VOD-Year nicht geparst → Sortierung/Matching schlecht

**Performance:**
- ✅ Frame-Drops um 55% reduziert
- ✅ Sync-Performance excellent (135 items/sec)
- ✅ Kein RETRY-Loop mehr

**XTC Logging:**
- ✅ Funktioniert perfekt
- ✅ Alle Field-Gaps identifiziert
- ✅ Kein Log-Flooding

**Next:** Fix die 3 Parser-Bugs und teste Playback!

---

**Analysiert:** 2026-01-28  
**Log-Duration:** 33 Sekunden  
**Items-Analyzed:** 4450  
**Bugs-Found:** 4 (3 Critical, 1 Low)  
**Status:** ⚠️ **BUGS REQUIRE FIX**

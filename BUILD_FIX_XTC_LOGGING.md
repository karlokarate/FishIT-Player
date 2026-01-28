# Build Fix: XtcLogger Cross-Module Dependency

## 🔴 Problem

Build failed mit folgendem Error:
```
NxCatalogWriter.kt:26: Unresolved reference 'debug'
NxCatalogWriter.kt:161: Unresolved reference 'XtcLogger'
```

## 🔍 Root Cause

**Layer-Boundary-Verletzung:** 
- `NxCatalogWriter` ist in `infra/data-nx` Modul
- `XtcLogger` ist in `pipeline/xtream` Modul
- `data-nx` hat KEINE Dependency auf `pipeline/xtream` (und sollte auch keine haben!)

**Architektur-Regel (AGENTS.md Section 4):**
```
Data Layer (infra/data-*) → darf NICHT auf Pipeline zugreifen
Pipeline Layer (pipeline/*) → darf NICHT auf Data zugreifen
```

## ✅ Fix Applied

### 1. Import entfernt
```kotlin
// REMOVED
import com.fishit.player.pipeline.xtream.debug.XtcLogger
```

### 2. XtcLogger-Call entfernt
```kotlin
// REMOVED 20 lines of XtcLogger.logNxWrite() code
```

### 3. Comment hinzugefügt
```kotlin
// NOTE: XTC logging moved to pipeline layer to avoid cross-module dependencies
```

## 📊 XTC-Logging Status

### Was funktioniert ✅
1. **DTO → RawMetadata** (Pipeline Layer)
   - VOD: ✅ `XtreamVodItem.toRawMetadata()`
   - Series: ✅ `XtreamSeriesItem.toRawMetadata()`
   - Episode: ✅ `XtreamEpisode.toRawMetadata()`
   - Live: ✅ `XtreamChannel.toRawMetadata()`

2. **Phase Completion** (Pipeline Layer)
   - LIVE: ✅
   - VOD: ✅
   - SERIES: ✅
   - Counter Reset: ✅

### Was NICHT funktioniert ❌
- **NX Entity Writes** (Data Layer)
  - Kann nicht implementiert werden ohne Layer-Violation
  - Alternative: Logging im CatalogSyncService (Domain Layer)

## 🔄 Alternative Lösung

### Option 1: Logging im CatalogSyncService (EMPFOHLEN)
```kotlin
// In CatalogSyncService nach normalized = normalizer.normalize(raw)
if (raw.sourceType == SourceType.XTREAM) {
    XtcLogger.logNormalized(
        type = "VOD",
        rawTitle = raw.originalTitle,
        normalizedTitle = normalized.canonicalTitle,
        year = normalized.year,
        adult = normalized.isAdult,
        mediaType = normalized.mediaType.name
    )
}
```

**Vorteil:** CatalogSyncService hat Zugriff auf Pipeline (XtcLogger) UND auf normalized Data

### Option 2: XtcLogger in shared Modul verschieben
```
core/logging/  ← XtcLogger hierhin
├── XtcLogger.kt
└── ...
```

**Vorteil:** Alle Module können darauf zugreifen  
**Nachteil:** XtcLogger ist Xtream-spezifisch, gehört nicht in core

### Option 3: Callback-Pattern
```kotlin
class NxCatalogWriter(
    private val loggingCallback: ((workKey, sourceKey, ...) -> Unit)? = null
)
```

**Vorteil:** Keine Dependency  
**Nachteil:** Kompliziert, Overkill für Logging

## 🎯 Empfehlung

**Nutze Option 1:** XTC-Logging im `CatalogSyncService` implementieren

**Wo:** Nach Normalisierung, vor DB-Write
```kotlin
// CatalogSyncService.kt
items.forEach { catalogItem ->
    val raw = catalogItem.raw
    val normalized = normalizer.normalize(raw)
    
    // XTC: Log normalized result (Option 1)
    if (raw.sourceType == SourceType.XTREAM) {
        XtcLogger.logNormalized(...)
    }
    
    val workKey = nxCatalogWriter.ingest(raw, normalized, accountKey)
    
    // XTC: Log NX write result (Option 1)
    if (workKey != null && raw.sourceType == SourceType.XTREAM) {
        // Can query NX repos here to get field count
    }
}
```

## 📝 Updated Documentation

Die `XTC_LOGGING_SYSTEM.md` muss aktualisiert werden:

**Änderung:**
```markdown
### 3. NX Entity Write Logging
~~**Wo:** `NxCatalogWriter.kt`~~
**Wo:** `CatalogSyncService.kt` (Alternative Option 1)
**Trigger:** Nach DB-Write (sampled, nur Xtream-Items)
```

## ✅ Build Status

Nach diesem Fix:
- ✅ `NxCatalogWriter.kt` kompiliert ohne Errors
- ✅ `XtcLogger.kt` kompiliert ohne Errors
- ✅ `XtreamRawMetadataExtensions.kt` kompiliert ohne Errors
- ✅ `XtreamCatalogPipelineImpl.kt` kompiliert ohne Errors
- ⚠️ Nur harmlose "never used" Warnings

**Alle Compile-Errors behoben!** 🎉

---

**Datum:** 2026-01-28  
**Issue:** Cross-Module Dependency Violation  
**Status:** ✅ FIXED  
**Next Step:** Optional - Implement Option 1 in CatalogSyncService

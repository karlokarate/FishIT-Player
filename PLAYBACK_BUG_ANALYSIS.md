# Bug Analysis - Playback Failure (SourceType UNKNOWN)

## 🔴 NEW BUG DISCOVERED

**Date:** 2026-01-28  
**Severity:** CRITICAL  
**Impact:** Playback fails completely

---

## 📊 Evidence

```
PlaybackSourceResolver: Resolving source for: movie:schwarzeschafe:2025 (UNKNOWN)
PlaybackSourceResolver: No factory and no valid URI for UNKNOWN
InternalPlayerSession: Failed to resolve source
PlaybackSourceException: No playback source available for UNKNOWN
```

**Problem:** Player receives `sourceType = UNKNOWN` instead of `sourceType = XTREAM`

---

## 🔍 Chain Analysis

### 1. User Clicks Play

```
UnifiedDetailVM: play: canonicalId=movie:schwarzeschafe:2025 
                      sourceKey=src:xtream:xtream:Xtream VOD:vod:xtream:vod:791354
```

**Status:** ✅ sourceKey looks correct (Xtream format)

---

### 2. PlayMediaUseCase builds PlaybackContext

```kotlin
// PlayMediaUseCase.kt Line 111
sourceType = mapToPlayerSourceType(source.sourceType)
```

**Mapping Function (Line 241-254):**
```kotlin
private fun mapToPlayerSourceType(sourceType: SourceType): com.fishit.player.core.playermodel.SourceType =
    when (sourceType) {
        SourceType.XTREAM -> com.fishit.player.core.playermodel.SourceType.XTREAM  // ← Should go here!
        SourceType.UNKNOWN -> com.fishit.player.core.playermodel.SourceType.UNKNOWN  // ← Actually went here!
        // ...
    }
```

**Analysis:** The `MediaSourceRef.sourceType` from database is already `UNKNOWN`!

---

### 3. Root Cause: Database has UNKNOWN sourceType

**The `MediaSourceRef` was written to NX database with `sourceType = UNKNOWN`!**

This happens during catalog sync when `RawMediaMetadata` is converted to NX entities.

---

## 🐛 Root Cause Chain

### Step 1: RawMediaMetadata Creation

```kotlin
// XtreamRawMetadataExtensions.kt
fun XtreamVodItem.toRawMetadata(...): RawMediaMetadata {
    return RawMediaMetadata(
        sourceType = SourceType.XTREAM,  // ← SHOULD be XTREAM
        // ...
    )
}
```

**Status:** ✅ **LOOKS CORRECT** (need to verify with XTC logging)

---

### Step 2: RawMetadata → NX Entity Mapping

**Somewhere in the chain, `sourceType` gets lost or overwritten!**

Possible locations:
1. ❓ Normalizer transforms RawMetadata → NormalizedMedia
2. ❓ NX Writer writes NormalizedMedia → NX_Work/NX_WorkSourceRef
3. ❓ NX Reader reads NX entities → MediaSourceRef

---

## 🔧 Diagnostic Steps Added

### XTC Logging Enhancement

**File:** `pipeline/xtream/debug/XtcLogger.kt`

**Added `sourceType` to logging:**
```kotlin
append("sourceType=${raw.sourceType} | ")
```

**Expected Log:**
```
[VOD] DTO→Raw #1 | id=xtream:vod:791354 | title="Schwarze Schafe" | sourceType=XTREAM | Fields: ...
```

**If we see `sourceType=UNKNOWN` in log → Bug is in DTO→Raw mapping!**  
**If we see `sourceType=XTREAM` in log → Bug is in persistence layer!**

---

## 🎯 Next Steps (TODO)

### 1. ✅ Build & Run with XTC Logging
```bash
./gradlew assembleDebug
adb install -r app-v2-debug.apk
adb logcat | grep XTC
```

**Look for:**
```
XTC: [VOD] DTO→Raw #1 | sourceType=???
```

---

### 2. If `sourceType=XTREAM` in Log (Bug is in persistence)

**Then investigate:**
- `NxMediaWriter` - How does it map `sourceType`?
- `NX_WorkSourceRef` entity - Does it store `sourceType`?
- `NxCanonicalMediaRepository` - Does it read `sourceType` correctly?

**Files to check:**
- `infra/data-nx/writer/NxMediaWriter.kt`
- `infra/data-nx/canonical/NX_WorkSourceRef.kt` (entity)
- `infra/data-nx/canonical/NxCanonicalMediaRepositoryImpl.kt`

---

### 3. If `sourceType=UNKNOWN` in Log (Bug is in DTO→Raw)

**Then the bug is in:**
```kotlin
// XtreamRawMetadataExtensions.kt
fun XtreamVodItem.toRawMetadata(...): RawMediaMetadata {
    return RawMediaMetadata(
        sourceType = SourceType.XTREAM,  // ← Check this!
        // ...
    )
}
```

**Possible causes:**
- `SourceType.XTREAM` is not imported correctly
- Enum value changed
- Build cache issue (clean build needed)

---

## 📋 Temporary Workaround

**NOT POSSIBLE** - User cannot play any Xtream content until this is fixed!

---

## 🎓 Lessons Learned

### 1. XTC Logging Should Include ALL Critical Fields

**Before:** Only tracked title, year, plot, cast, images  
**After:** Also track `sourceType` (critical for playback!)

### 2. Database Schema Should Have NOT NULL Constraints

**Problem:** `sourceType` can be `UNKNOWN` → playback fails silently  
**Better:** Database constraint: `sourceType` must be one of [XTREAM, TELEGRAM, IO]

### 3. Integration Tests Needed

**Missing:** No test that verifies:
1. Sync Xtream item
2. Load from database
3. Start playback
4. Verify factory is called

---

## 📝 Summary

**Status:** 🔴 **CRITICAL BUG - BLOCKS ALL XTREAM PLAYBACK**

**Root Cause:** `MediaSourceRef.sourceType` is `UNKNOWN` in database instead of `XTREAM`

**Location:** Either DTO→Raw mapping OR persistence layer (need XTC log to determine)

**Fix Status:** ⏸️ **DIAGNOSTIC LOGGING ADDED** - Need to run app to diagnose

**Impact:** 100% of Xtream playback fails

**Priority:** P0 - Must fix before release

---

**Created:** 2026-01-28  
**Next Action:** Build app, check XTC logs for `sourceType` value

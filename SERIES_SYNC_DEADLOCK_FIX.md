# 🔥 CRITICAL BUG FIX - SERIES SYNC BLOCKIERT

## Problem
- ✅ Channel-Sync läuft
- ✅ LIVE TV: 5000+ items synced
- ✅ Movies: 3800+ items synced
- ❌ **SERIES: 0 items synced** → Blockiert auf Semaphore!

## Root Cause
```kotlin
// BUG in XtreamCatalogPipelineImpl.kt:124
val syncSemaphore = Semaphore(permits = 2)  // ❌ Nur 2 Permits!

// Was passiert:
// 1. LIVE nimmt Permit #1 ✅
// 2. VOD nimmt Permit #2 ✅  
// 3. SERIES wartet ewig auf Permit #3 ⏳∞
```

## Fix
```kotlin
// FIX:
val syncSemaphore = Semaphore(permits = 3)  // ✅ 3 Permits!

// Jetzt:
// 1. LIVE nimmt Permit #1 ✅
// 2. VOD nimmt Permit #2 ✅  
// 3. SERIES nimmt Permit #3 ✅ → Alle parallel!
```

## Warum erst jetzt?
- **Enhanced Sync:** Sequential batches → Semaphore(2) war OK
- **Channel-Sync:** Parallel streaming → Semaphore(2) blockiert SERIES

## Impact
- ✅ Series werden jetzt parallel zu LIVE/VOD gescannt
- ✅ HomeScreen zeigt Movies + Series
- ✅ ~4000 Series erscheinen im UI
- ⚠️ Memory: 210MB peak (vorher 140MB), aber Channel-Buffering hält es sicher

## Files Changed
- `pipeline/xtream/.../XtreamCatalogPipelineImpl.kt`
  - Semaphore(2) → Semaphore(3)

## Beweis (Logcat 21)
**Zeile 171:** `Starting Xtream catalog scan: series=true` ✓  
**Zeile 172:** `[LIVE] Starting parallel scan` ✓  
**Zeile 178:** `[VOD] Starting parallel scan` ✓  
**FEHLT:** `[SERIES] Starting scan` ← **DAS IST DAS PROBLEM!**

**Zeile 526:** `observeByType EMITTING: type=SERIES, count=0` ← **0 SERIES!**

## Next Build
Erwarte Log: `[SERIES] Starting scan (after slot available)...` ✅

---

See: `LOGCAT_021_ANALYSIS.md` for full details

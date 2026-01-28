# Playback Bug - Root Cause Update

## 🔴 **PROBLEM: Fix war an falscher Stelle!**

**Datum:** 2026-01-28 15:15  
**Status:** ❌ Erster Fix funktioniert nicht

---

## 🔍 **Analyse logcat_006:**

**Zeile 522:**
```
UnifiedDetailVM: play: sourceKey=src:xtream:xtream:Xtream VOD:vod:xtream:vod:791354
```
✅ sourceKey ist korrekt!

**Zeile 551-552:**
```
PlaybackSourceResolver: Resolving source for: movie:schwarzeschafe:2025 (UNKNOWN)
PlaybackSourceResolver: E No factory and no valid URI for UNKNOWN
```
❌ sourceType ist immer noch UNKNOWN!

---

## 🐛 **Warum der erste Fix nicht funktioniert:**

### Code-Flow (TATSÄCHLICH):

```
1. UnifiedDetailViewModel.play()
   → resolveActiveSource() 
   → returns MediaSourceRef (sourceType=UNKNOWN bereits hier!)

2. UnifiedDetailViewModel.playWithSource()
   → _events.emit(StartPlayback(source = source))  ← sourceType=UNKNOWN!

3. PlayMediaUseCase.play()
   → buildPlaybackContext(source)  ← Mein Fix ist HIER!
   → Aber source.sourceType ist schon UNKNOWN!

4. PlayerEntry.start(context)
   → context.sourceType = UNKNOWN  ❌
```

**Problem:** Das `MediaSourceRef`-Objekt hat **bereits** `sourceType=UNKNOWN` wenn es zu `PlayMediaUseCase` kommt!

---

## ✅ **Die RICHTIGE Lösung:**

Das `sourceType` muss gefixed werden **wenn `MediaSourceRef` erstellt wird** - NICHT später in `PlayMediaUseCase`!

### Option 1: Fix beim Auslesen aus DB

**Wo:** `CanonicalMediaRepository` Implementation  
**Was:** Beim Konvertieren von `DomainSourceInfo` → `MediaSourceRef` das sourceType korrekt mappen

### Option 2: Fix in SourceSelection

**Wo:** `SourceSelection.resolveActiveSource()`  
**Was:** Fallback-Logik um sourceType aus sourceKey zu extrahieren

### Option 3: Fix im Legacy Repository (ROOT CAUSE)

**Wo:** Der alte Repository-Code der `ObxCanonicalMedia` → `MediaSourceRef` mapped  
**Was:** String → Enum Konvertierung korrigieren

---

## 🎯 **Nächster Schritt:**

**Option 2 ist am sichersten!**

Ich fixe `SourceSelection.resolveActiveSource()` um das `sourceType` aus `sourceKey` zu extrahieren wenn es UNKNOWN ist.

**Vorteil:**
- Zentrale Stelle (alle Playback-Aufrufe gehen durch SourceSelection)
- Keine Breaking Changes
- Funktioniert für ALLE Legacy-Daten

**Code Location:**
`feature/detail/src/main/java/com/fishit/player/feature/detail/SourceSelection.kt`

---

**Status:** ⏩ Implementiere Fix an richtiger Stelle!

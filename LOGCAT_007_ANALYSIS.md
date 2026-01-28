# KRITISCHE ANALYSE - logcat_007

## 🔴 **NEUE BUGS GEFUNDEN!**

### Problem 1: `.uppercase()` Fix hat neue Bugs verursacht!

**Zeile 128 in NxCanonicalMediaRepositoryImpl.kt:**
```kotlin
sourceRef.sourceType = source.sourceType.name  // ← .name gibt UPPERCASE!
```

**ABER beim Schreiben aus Pipeline:**
```kotlin
sourceRef.sourceType = "xtream"  // ← lowercase!
```

**KONFLIKT:** Die DB enthält BEIDE - lowercase UND uppercase Werte!

---

## 🐛 **Bug Details aus logcat_007:**

### 1. Series Enrichment schlägt fehl (Line 928-931):
```
DetailEnrichment: enrichFromXtream: invalid sourceId=src:xtream:xtream:Xtream Series:series:xtream:series:87
UnifiedDetailVM: Series canonical ID has no numeric ID: series:1918-1939-krieg-der-tr-ume:1918
UnifiedDetailVM: Cannot load series details: unable to extract series ID
```

**Problem:** Der sourceKey enthält "xtream" in verschiedenen Cases!

### 2. Playback error (Line 947):
```
XtreamPlaybackFactory: Missing seriesId for series content
```

**Problem:** Series kann nicht abgespielt werden weil ID fehlt!

### 3. Home Screen leer:
**Keine Logs zu Home/Movies/UI** → Movies erscheinen nicht!

---

## ✅ **LÖSUNG:**

Der `.uppercase()` Fix war richtig, ABER wir müssen auch beim **SCHREIBEN** konsistent sein!

**Option 1: Immer uppercase in DB** (Empfohlen)
```kotlin
sourceRef.sourceType = sourceType.uppercase()  // Beim Schreiben
SourceType.valueOf(sourceRef.sourceType)       // Beim Lesen (kein .uppercase()!)
```

**Option 2: Immer lowercase in DB**
```kotlin
sourceRef.sourceType = sourceType.lowercase()   // Beim Schreiben  
SourceType.valueOf(sourceRef.sourceType.uppercase())  // Beim Lesen
```

**Ich empfehle Option 2** (lowercase in DB) weil:
- Die Pipeline schreibt bereits lowercase
- Weniger Breaking Changes
- Konsistent mit bestehenden Daten

---

## 🎯 **Fix Plan:**

1. ✅ Keep `.uppercase()` beim Lesen (Line 600)
2. ✅ Add `.lowercase()` beim Schreiben (Line 128)
3. ✅ Migration: Alle uppercase Werte zu lowercase konvertieren

**Status:** Analyse komplett, Fix bereit!

# TODO/Placeholder Audit – Blocking Issues

**Audit Date:** $(date -I)  
**Branch:** `architecture/v2-bootstrap`  
**Purpose:** Identifiziere alle offenen TODOs und Platzhalter, die die App blockieren

---

## 🚨 Kritische Blocker (App-Funktionalität eingeschränkt)

### 1. Navigation – Player & Settings nicht navigierbar

**Datei:** [app-v2/src/main/java/com/fishit/player/v2/navigation/AppNavHost.kt](../../app-v2/src/main/java/com/fishit/player/v2/navigation/AppNavHost.kt#L88)

```kotlin
// Zeile 88
onSettingsClick = {
    // TODO: Navigate to Settings
},

// Zeile 119
onPlayback = { event ->
    // TODO: Navigate to player with playback context
    // navController.navigate(Routes.player(event.canonicalId.value, event.source.sourceId))
},
```

**Impact:** 
- ⚠️ Settings-Button führt zu keiner Aktion
- ⚠️ Play-Button auf Detail-Screen navigiert nicht zum Player

**Priorität:** 🔴 HOCH – User kann nichts abspielen vom Detail-Screen

---

### 2. TMDB API Key nicht konfiguriert

**Datei:** [core/metadata-normalizer/.../TmdbConfig.kt](../../core/metadata-normalizer/src/main/java/com/fishit/player/core/metadata/tmdb/TmdbConfig.kt#L69)

```kotlin
class DefaultTmdbConfigProvider @Inject constructor() : TmdbConfigProvider {
    override fun getConfig(): TmdbConfig {
        // TODO: Read from BuildConfig.TMDB_API_KEY when configured
        return TmdbConfig.DISABLED  // ← Hardcoded DISABLED!
    }
}
```

**Impact:** 
- ⚠️ Keine TMDB-Metadaten-Enrichment
- ⚠️ Keine Poster/Backdrop-Downloads von TMDB
- ⚠️ `TmdbEnrichmentBatchWorker` läuft immer ins Leere

**Priorität:** 🟡 MITTEL – App funktioniert ohne, aber Metadaten-Qualität leidet

---

### 3. TmdbEnrichmentBatchWorker – Stub-Implementierung

**Datei:** [app-v2/src/main/java/com/fishit/player/v2/work/TmdbEnrichmentBatchWorker.kt](../../app-v2/src/main/java/com/fishit/player/v2/work/TmdbEnrichmentBatchWorker.kt#L285)

```kotlin
// Zeile 285
// TODO: This needs the full normalized metadata - for now we mark as applied

// Zeile 312
// TODO: Implement full search resolution via TmdbMetadataResolver
// For now, we mark as FAILED to track the attempt and enable cooldown
```

**Impact:**
- ⚠️ TMDB-Enrichment markiert Items als "applied" ohne echte Daten
- ⚠️ Search-Resolution schlägt immer fehl (placeholder)

**Priorität:** 🟡 MITTEL – Mit TMDB_API_KEY disabled ohnehin nicht aktiv

---

### 4. IO Pipeline – Komplett Stub

**Datei:** [app-v2/src/main/java/com/fishit/player/v2/work/IoQuickScanWorker.kt](../../app-v2/src/main/java/com/fishit/player/v2/work/IoQuickScanWorker.kt#L82)

```kotlin
// TODO: Implement actual IO sync when CatalogSyncService.syncIo() is available
// For now, return success as IO pipeline is a stub
```

**Impact:**
- ⚠️ Lokale Dateien werden nicht gescannt
- ⚠️ IO-Source zeigt keine Inhalte

**Priorität:** 🟢 NIEDRIG – IO-Feature nicht primär (Telegram/Xtream haben Priorität)

---

### 5. EPG Sync Service – No-Op Interface

**Datei:** [core/catalog-sync/src/main/java/com/fishit/player/core/catalogsync/EpgSyncService.kt](../../core/catalog-sync/src/main/java/com/fishit/player/core/catalogsync/EpgSyncService.kt#L13)

```kotlin
// TODO(EPG): Implement epg_sync_global and EPG normalization per upcoming EPG contract.

interface EpgSyncService {
    fun requestEpgRefresh(reason: String)  // No-op placeholder
}
```

**Impact:**
- ⚠️ EPG-Daten (Programmführer) nicht verfügbar für Live-TV

**Priorität:** 🟢 NIEDRIG – Live-TV funktioniert, nur ohne Programmführer

---

## 🟡 Funktionale Einschränkungen (App läuft, Features fehlen)

### 6. DefaultResumeManager – In-Memory Only

**Datei:** [playback/domain/.../DefaultResumeManager.kt](../../playback/domain/src/main/java/com/fishit/player/playback/domain/defaults/DefaultResumeManager.kt#L34)

```kotlin
profileId = null // TODO: Add profile tracking in Phase 6
```

**Impact:**
- ⚠️ Resume-Points gehen bei App-Neustart verloren (nur In-Memory)
- ⚠️ Kein Multi-Profil-Support für Resume

**Status:** ObjectBox-Persistenz für Resume ist via `ObxCanonicalResumeMark` implementiert – aber `DefaultResumeManager` nutzt es nicht!

**Priorität:** 🟡 MITTEL – Workaround: `ObxCanonicalMediaRepository.updateResumePosition()` nutzen

---

### 7. TelegramContentRepository – In-Memory Stub

**Datei:** [infra/data-telegram/.../TdlibTelegramContentRepository.kt](../../infra/data-telegram/src/main/java/com/fishit/player/infra/data/telegram/TdlibTelegramContentRepository.kt#L32)

```kotlin
// In-memory storage (Phase 2 stub - will be ObjectBox in production)
private val storage = MutableStateFlow<Map<String, RawMediaMetadata>>(emptyMap())
```

**Impact:**
- ⚠️ Telegram-Medien gehen bei App-Neustart verloren

**Hinweis:** Telegram-Daten werden via `CatalogSyncService` → `ObxCanonicalMedia` persistiert. Dieses Repository ist nur ein Zwischenspeicher.

**Priorität:** 🟢 NIEDRIG – Daten werden anderweitig persistiert

---

### 8. ObxContentRepository – Minimal Placeholder

**Datei:** [core/persistence/.../ObxContentRepository.kt](../../core/persistence/src/main/java/com/fishit/player/core/persistence/repository/ObxContentRepository.kt#L24)

```kotlin
override suspend fun getContentTitle(contentId: String): String? =
    withContext(Dispatchers.IO) {
        // Placeholder implementation
        null
    }
```

**Impact:**
- ⚠️ Content-Lookup by ID gibt immer `null` zurück

**Priorität:** 🟢 NIEDRIG – Wird kaum genutzt; `ObxCanonicalMediaRepository` ist primär

---

### 9. ImagingModule – Komplett leer

**Datei:** [infra/imaging/.../ImagingModule.kt](../../infra/imaging/src/main/java/com/fishit/player/infra/imaging/ImagingModule.kt#L28)

```kotlin
// TODO: Provide @Singleton ImageLoader when ready
// TODO: Provide @Singleton OkHttpClient for imaging when ready
```

**Impact:**
- ⚠️ Kein zentraler Coil/ImageLoader
- ✅ Workaround: Coil läuft mit Default-Config

**Priorität:** 🟢 NIEDRIG – Funktioniert mit Defaults

---

### 10. Logging – Crashlytics/Sentry nicht integriert

**Datei:** [infra/logging/.../UnifiedLogInitializer.kt](../../infra/logging/src/main/java/com/fishit/player/infra/logging/UnifiedLogInitializer.kt#L77)

```kotlin
// TODO(crashlytics): Integrate with Crashlytics
// TODO(sentry): Add Sentry integration as an alternative
```

**Impact:**
- ⚠️ Keine automatische Crash-Berichterstattung

**Priorität:** 🟢 NIEDRIG – Nicht funktionskritisch

---

## 🟢 Nice-to-Have / Future Enhancements

### 11. CatalogSyncService – Metadata-Felder fehlen

**Datei:** [core/catalog-sync/.../DefaultCatalogSyncService.kt](../../core/catalog-sync/src/main/java/com/fishit/player/core/catalogsync/DefaultCatalogSyncService.kt#L429)

```kotlin
quality = null,     // TODO: Extract from RawMediaMetadata.quality when available
languages = null,   // TODO: Extract from RawMediaMetadata.languages when available
format = null,      // TODO: Extract from RawMediaMetadata.format when available
sizeBytes = null,   // TODO: Add to RawMediaMetadata
```

**Impact:** Metadaten unvollständig aber nicht blockierend

---

### 12. TelegramMessageBundler – AlbumId fehlt

**Datei:** [pipeline/telegram/.../TelegramMessageBundler.kt](../../pipeline/telegram/src/main/java/com/fishit/player/pipeline/telegram/grouper/TelegramMessageBundler.kt#L146)

```kotlin
// TODO: Add albumId to TgMessage when transport-telegram exposes it
```

**Impact:** Telegram-Album-Gruppierung nicht optimal

---

### 13. HomeViewModel – Detail Navigation

**Datei:** [feature/home/.../HomeViewModel.kt](../../feature/home/src/main/java/com/fishit/player/feature/home/HomeViewModel.kt#L236)

```kotlin
// TODO: Navigate to detail screen
```

**Impact:** ✅ Navigation funktioniert via `onItemClick` Callback

---

### 14. TelegramMediaScreen – Thumbnails & Resume

**Datei:** [feature/telegram-media/.../TelegramMediaScreen.kt](../../feature/telegram-media/src/main/java/com/fishit/player/feature/telegram/TelegramMediaScreen.kt#L174)

```kotlin
// TODO: Add thumbnail image loading with Coil

// feature/telegram-media/.../TelegramTapToPlayUseCase.kt:79
startPositionMs = 0L, // TODO: Add resume support later
```

**Impact:** Thumbnails fehlen in Telegram-Liste, kein Resume

---

## 📊 Zusammenfassung

| Kategorie | Anzahl | Kritisch |
|-----------|--------|----------|
| 🔴 Navigation Blocker | 2 | Ja |
| 🟡 Feature Incomplete | 6 | Nein |
| 🟢 Nice-to-Have | 6+ | Nein |

### Empfohlene Reihenfolge:

1. **Navigation fixieren** (AppNavHost.kt) – Settings + Player Navigation
2. **TMDB API Key** konfigurieren (BuildConfig)
3. **DefaultResumeManager** auf ObjectBox umstellen

---

## Ausgeschlossene Treffer

Die folgenden wurden bewusst NICHT als Blocker eingestuft:

- `/legacy/**` – Alter v1-Code, read-only
- `/test/**/*.kt` – Test-Code mit `TODO()` stubs
- `placeholder` in Kommentaren für UI-Blur-Thumbnails (Feature, kein Bug)
- `.toDouble()` Methodenaufrufe (false positives)


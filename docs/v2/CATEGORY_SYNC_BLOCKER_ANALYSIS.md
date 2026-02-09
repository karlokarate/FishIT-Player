# Category Sync Blocker Analysis & Implementation Plan

> **Date:** 2026-02-09  
> **Issue:** Categories nicht gefetcht/persistiert beim Onboarding; kein Overlay; kein per-category API fetch  
> **Scope:** Onboarding → Category → Sync end-to-end

---

## 1. Gewünschter Flow (User Story)

```
StartScreen: User gibt Xtream URL + Creds ein
    │
    ▼
connectXtream() → API Auth → sourceActivation.setXtreamActive()
    │
    ▼
Kategorien werden vom Server gefetcht (✅ XtreamCategoryPreloader existiert)
    │
    ▼
Kategorien werden persistiert (✅ NxCategorySelectionRepository existiert)
    │
    ▼
CategorySelectionScreen wird als OVERLAY angezeigt (❌ FEHLT)
    │
    ▼
User wählt/deselektiert Kategorien → automatisches Persistieren
    │
    ▼
User schließt Overlay → StartScreen
    │
    ▼
Sync startet nur mit gewählten Kategorien per `category_id` API-Parameter
    │
    ▼  
Pipeline fetcht pro Kategorie via `player_api.php?action=get_vod_streams&category_id=X`
```

---

## 2. Identifizierte Blocker

### BLOCKER B1: Kein Category-Preload im Onboarding-Flow

**Status:** 🔴 KRITISCH  
**Problem:** `XtreamCategoryPreloader.preloadCategories()` wird nur in `XtreamSessionBootstrap` aufgerufen (= App-Restart mit gespeicherten Credentials). Beim **erstmaligen Onboarding** fehlt der Preload komplett.

| Aktuell | Gewünscht |
|---------|-----------|
| Preload nur bei App-Restart | Preload direkt nach `connectXtream()` success |

**Dateien:**
- `OnboardingViewModel.kt` — kein `XtreamCategoryPreloader` injiziert
- `feature/onboarding/build.gradle.kts` — keine Dependency auf `core:catalog-sync`

**Fix:** 
1. `XtreamCategoryPreloader` in OnboardingViewModel injizieren
2. Nach `saveCredentials()` success → `preloadCategories()` aufrufen
3. Dependency `implementation(project(":core:catalog-sync"))` hinzufügen

---

### BLOCKER B2: Kein CategorySelection-Overlay im StartScreen

**Status:** 🔴 KRITISCH  
**Problem:** `CategorySelectionScreen` existiert in `feature/settings`, wird aber **nie** vom Onboarding aufgerufen. Es fehlt ein Overlay/BottomSheet im StartScreen.

| Aktuell | Gewünscht |
|---------|-----------|
| Kein Overlay | ModalBottomSheet/Dialog nach Preload |
| NavigationEvent.ToHome sofort | CategorySelection zuerst, dann ToHome |

**Dateien:**
- `StartScreen.kt` — kein Overlay-Composable
- `OnboardingState` — kein `showCategoryOverlay` Flag
- `CategorySelectionScreen.kt` — in `feature/settings` → muss als reusable Composable nutzbar sein

**Fix-Optionen:**
- **Option A:** `CategorySelectionScreen` als `@Composable` Dialog-Variante im `feature/settings` erstellen + als Dependency nutzen
- **Option B (empfohlen):** Neues `CategorySelectionOverlay` Composable direkt im Onboarding-Modul, das `CategorySelectionViewModel` (via Hilt) nutzt → vermeidet cross-feature Dependency

---

### BLOCKER B3: Pipeline nutzt Client-Side Filtering statt Server-Side

**Status:** 🟡 MITTEL (funktioniert, aber ineffizient)  
**Problem:** Die Pipeline fetcht ALLE Items vom Server und filtert dann client-side nach `categoryId`. Die Xtream API unterstützt aber `category_id` als Parameter:

```
GET /player_api.php?action=get_vod_streams&category_id=123&username=X&password=Y
```

| Aktuell | Gewünscht |
|---------|-----------|
| `streamVodItems(batchSize=500)` → filter | `streamVodItems(categoryId="123")` pro Kategorie |
| Download: 100% aller Streams | Download: nur gewählte Kategorien |
| Client-side filter in Phase | Server-side filter in Transport |

**Dateien:**
- `VodItemPhase.kt` — Ruft `source.streamVodItems(batchSize)` ohne categoryId auf
- `SeriesItemPhase.kt` — Gleich
- `LiveChannelPhase.kt` — Gleich
- `XtreamCatalogSource.kt` (Interface) — kein `categoryId` Parameter
- `DefaultXtreamCatalogSource.kt` — delegiert ohne categoryId
- `XtreamPipelineAdapter.kt` — `streamVodItems()` hat kein categoryId
- `XtreamApiClient.kt` — `streamVodInBatches(categoryId=...)` **UNTERSTÜTZT ES BEREITS!** ✅

**Fix:** 
1. `XtreamCatalogSource` Interface: `categoryId` Parameter hinzufügen
2. `DefaultXtreamCatalogSource`: categoryId an Adapter durchreichen
3. `XtreamPipelineAdapter`: categoryId an apiClient durchreichen
4. `VodItemPhase`/`SeriesItemPhase`/`LiveChannelPhase`: Pro categoryId loopen statt all-at-once

---

### BLOCKER B4: Navigation-Event zu früh ausgelöst

**Status:** 🟡 MITTEL  
**Problem:** `NavigationEvent.ToHome` wird sofort nach `saveCredentials()` emittiert. Muss NACH Category-Preload + Overlay warten.

**Fix:** 
- `NavigationEvent.ToHome` erst nach Overlay-Close emittieren
- `NavigationEvent.ShowCategoryOverlay` als neues Event hinzufügen

---

## 3. Betroffene Dateien pro Scope

### Scope: `catalog-sync` (READ REQUIRED)
| Datei | Änderung | Scope Guard |
|-------|----------|-------------|
| `DefaultXtreamSyncService.kt` | Consumer bereits gefixt ✅ | ALLOWED |
| `XtreamCategoryPreloader.kt` | Keine Änderung nötig ✅ | — |

### Scope: `xtream-pipeline-catalog` (READ REQUIRED)
| Datei | Änderung | Scope Guard |
|-------|----------|-------------|
| `VodItemPhase.kt` | Server-side category filter | CHECK |
| `SeriesItemPhase.kt` | Server-side category filter | CHECK |
| `LiveChannelPhase.kt` | Server-side category filter | CHECK |
| `XtreamCatalogSource.kt` | `categoryId` param hinzufügen | CHECK |
| `DefaultXtreamCatalogSource.kt` | categoryId durchreichen | CHECK |
| `XtreamPipelineAdapter.kt` | categoryId an apiClient | CHECK |
| `PhaseScanOrchestrator.kt` | Category IDs an Phases geben | CHECK |

### Bundle: `gradle-config` (READ REQUIRED)
| Datei | Änderung | Scope Guard |
|-------|----------|-------------|
| `feature/onboarding/build.gradle.kts` | + `core:catalog-sync` Dependency | BUNDLE_BLOCKED |

### UNTRACKED (kein Scope)
| Datei | Änderung | Scope Guard |
|-------|----------|-------------|
| `OnboardingViewModel.kt` | + Preloader, + Overlay-State | UNTRACKED |
| `StartScreen.kt` | + CategorySelectionOverlay | UNTRACKED |
| `OnboardingState` (in ViewModel) | + `showCategoryOverlay` | UNTRACKED |

---

## 4. Transport Layer (KEIN Change nötig!)

Die Transport-Schicht unterstützt `category_id` bereits:

```kotlin
// XtreamApiClient.kt - ALREADY EXISTS:
suspend fun streamVodInBatches(
    batchSize: Int = 500,
    categoryId: String? = null,  // ← Server-side filter
    onBatch: suspend (List<XtreamVodStream>) -> Unit,
): Int

suspend fun streamSeriesInBatches(
    batchSize: Int = 500, 
    categoryId: String? = null,  // ← Server-side filter
    onBatch: suspend (List<XtreamSeriesStream>) -> Unit,
): Int

suspend fun streamLiveInBatches(
    batchSize: Int = 500,
    categoryId: String? = null,  // ← Server-side filter
    onBatch: suspend (List<XtreamLiveStream>) -> Unit,
): Int
```

**API-Endpunkte:**
```
GET /player_api.php?action=get_vod_streams&category_id={id}
GET /player_api.php?action=get_series&category_id={id}
GET /player_api.php?action=get_live_streams&category_id={id}
```

---

## 5. Implementierungsplan (priorisiert)

### Phase 1: Category Preload im Onboarding (BLOCKER B1)
1. `feature/onboarding/build.gradle.kts` → `implementation(project(":core:catalog-sync"))` 
2. `OnboardingViewModel.kt` → `XtreamCategoryPreloader` injizieren
3. Nach `connectXtream()` success → `preloadCategories()` aufrufen
4. State: `categoryPreloadState` beobachten

### Phase 2: Category Overlay (BLOCKER B2)
1. `OnboardingState` → `showCategoryOverlay: Boolean = false`
2. `OnboardingViewModel` → `NavigationEvent.ShowCategoryOverlay` nach Preload
3. `StartScreen.kt` → `ModalBottomSheet` mit `CategorySelectionScreen`-Inhalten einbetten
4. Overlay onDismiss → `NavigationEvent.ToHome`

### Phase 3: Server-Side Category Filtering (BLOCKER B3)
1. `XtreamCatalogSource.kt` Interface → `categoryId: String?` Parameter
2. `DefaultXtreamCatalogSource.kt` → categoryId durchreichen
3. `XtreamPipelineAdapter.kt` → categoryId an apiClient
4. `VodItemPhase` → Pro categoryId loopen: `for (catId in categoryFilter) { source.streamVodItems(categoryId = catId) }`
5. `SeriesItemPhase` + `LiveChannelPhase` → analog
6. Fallback: Wenn `categoryFilter.isEmpty()` → ohne categoryId (alle)

### Phase 4: Navigation-Timing Fix (BLOCKER B4)
1. Entferne `NavigationEvent.ToHome` nach `saveCredentials()`
2. Event-Flow: `saveCredentials → preload → showOverlay → user saves → ToHome`

---

## 6. Dependency Graph

```
feature:onboarding
    ├── core:catalog-sync    (NEU - für XtreamCategoryPreloader)
    │   ├── pipeline:xtream
    │   ├── infra:data-xtream
    │   └── core:persistence
    ├── core:onboarding-domain
    ├── core:model
    └── core:feature-api

feature:settings (unverändert)
    ├── core:catalog-sync
    ├── CategorySelectionScreen
    └── CategorySelectionViewModel
```

**Circular Dependency Check:**
- `feature:onboarding` → `core:catalog-sync` ✅ (downward)
- KEIN cross-feature Dependency (`feature:onboarding` → `feature:settings`) nötig
- CategorySelectionOverlay wird direkt in onboarding implementiert, nutzt `CategorySelectionViewModel` via Hilt

---

## 7. Risiken

| Risiko | Wahrscheinlichkeit | Impact | Mitigation |
|--------|---------------------|--------|------------|
| Category Preload langsam (3+ Sekunden) | Mittel | UI wartet | Loading-Indicator im Overlay |
| Server unterstützt `category_id` nicht | Niedrig | Fetch liefert alle Items | Fallback: client-side filter (aktueller Code) |
| Overlay schließt zu früh | Niedrig | Sync startet ohne Auswahl | onDismiss prüft ob Kategorien gespeichert |
| Hilt kann XtreamCategoryPreloader nicht in Onboarding injizieren | Niedrig | Build-Fehler | Preloader bereits @Singleton + @Inject |

---

## 8. Nicht in Scope

- **Info-Backfill per Category** — Bleibt wie aktuell
- **Episode-Sync per Category** — Später (Performance-optimierung)
- **Telegram Categories** — Nicht relevant
- **Settings Screen Changes** — CategorySelectionScreen in Settings bleibt unverändert

# XTREAM_ONBOARDING_CATEGORY_SELECTION_CONTRACT.md

Version: 1.1  
Date: 2026-02-09  
Status: Binding Contract  
Scope: Xtream source onboarding, category preload, user selection, sync gating

---

## 0. Binding References

This contract MUST be implemented in compliance with:
- CATALOG_SYNC_WORKERS_CONTRACT_V2.md
- XTREAM_SCAN_PREMIUM_CONTRACT_V1.md
- GLOSSARY_v2_naming_and_modules.md
- AGENTS.md

---

## 1. Core Principle: Category-First Onboarding

### XOC-1 Category Selection BEFORE Sync (MANDATORY)

When a user adds a new Xtream source, the system MUST:
1. Validate credentials (auth preflight)
2. Fetch and persist available categories
3. Display category selection UI
4. Wait for user selection
5. ONLY THEN allow catalog sync to proceed

**NO catalog sync may start before the user has seen and interacted with category selection.**

---

## 2. Onboarding Flow (SSOT)

```
┌─────────────────────────────────────────────────────────────────────┐
│  STEP 1: User Input                                                 │
│  StartScreen: User enters Xtream URL + Username + Password          │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│  STEP 2: Auth Validation                                            │
│  connectXtream() → XtreamApiClient.getServerInfo()                  │
│  → Success: sourceActivation.setXtreamActive()                      │
│  → Failure: Show error, stay on StartScreen                         │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│  STEP 3: Category Preload (MANDATORY)                               │
│  XtreamCategoryPreloader.preloadCategories(accountKey)              │
│  → Fetches: VOD categories, Series categories, Live categories      │
│  → Persists to: NxCategorySelectionRepository                       │
│  → All categories default: isSelected = true                        │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│  STEP 4: Category Selection Overlay (MANDATORY)                     │
│  Display: ModalBottomSheet / Dialog with category list              │
│  User can: Select/Deselect individual categories                    │
│  Auto-persist: Each toggle saves to NxCategorySelectionRepository   │
│  BLOCKER: User MUST interact before proceeding                      │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│  STEP 5: Overlay Dismissal                                          │
│  User closes overlay (Confirm button or swipe down)                 │
│  → categorySelectionComplete = true                                 │
│  → NavigationEvent.ToHome emitted                                   │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│  STEP 6: Sync with Selected Categories Only                         │
│  CatalogSyncWorkScheduler.enqueueAutoSync()                         │
│  → CatalogSyncOrchestratorWorker.syncXtream() checks gate           │
│  → Pipeline fetches per category: ?action=get_vod_streams&category_id=X │
│  → Only selected categories are synced                              │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 3. Sync Gating Rules

### XOC-2 Sync Gate: categorySelectionComplete (MANDATORY)

A new flag MUST exist that gates sync execution:

```kotlin
// In OnboardingState or equivalent
data class XtreamSourceState(
    val isActive: Boolean,
    val categorySelectionComplete: Boolean,  // GATE
)
```

**Rules:**
| Condition | Sync Allowed? |
|-----------|---------------|
| `isActive = false` | ❌ No |
| `isActive = true, categorySelectionComplete = false` | ❌ No |
| `isActive = true, categorySelectionComplete = true` | ✅ Yes |

### XOC-3 Where Sync Gate is Checked (MANDATORY)

The gate MUST be checked in:
1. `CatalogSyncOrchestratorWorker.syncXtream()` - before building Xtream sync config
2. `OnboardingViewModel.confirmCategorySelection()` - before emitting NavigationEvent.ToHome

### XOC-4 Persisted Gate State (MANDATORY)

The `categorySelectionComplete` flag MUST be persisted (not just in-memory):
- Location: `NxCategorySelectionRepository` or `SourceActivationRepository`
- Key: `xtream:{accountKey}:categorySelectionComplete`
- Default for NEW sources: `false`
- Set to `true` only after user closes category overlay

---

## 4. Category Repository Contract

### XOC-5 NxCategorySelectionRepository Methods (MANDATORY)

```kotlin
interface NxCategorySelectionRepository {
    // Query
    fun observeCategories(accountKey: String): Flow<List<CategorySelection>>
    fun getSelectedCategoryIds(accountKey: String, type: XtreamCategoryType): List<String>
    
    // Mutations
    suspend fun upsertCategories(categories: List<CategorySelection>)
    suspend fun setSelected(selectionKey: String, isSelected: Boolean)
    
    // Gate
    suspend fun setCategorySelectionComplete(accountKey: String, complete: Boolean)
    suspend fun isCategorySelectionComplete(accountKey: String): Boolean
}
```

### XOC-6 CategorySelection Entity (MANDATORY)

```kotlin
data class CategorySelection(
    val selectionKey: String,      // "xtream:{account}:{type}:{catId}"
    val accountKey: String,
    val categoryId: String,
    val categoryName: String,
    val type: XtreamCategoryType,  // VOD, SERIES, LIVE
    val isSelected: Boolean,
    val sortOrder: Int,
    val itemCount: Int?,           // Optional: count of items in category
)
```

---

## 5. UI Requirements

### XOC-7 Category Selection Overlay (MANDATORY)

The overlay MUST:
- Show all categories grouped by type (VOD, Series, Live)
- Allow individual selection/deselection via checkboxes
- Show category name and optional item count
- Have a "Confirm" or "Continue" button
- Persist selections immediately on toggle (no batch save)

### XOC-8 Loading States (MANDATORY)

| State | UI Display |
|-------|------------|
| Preloading categories | Progress indicator + "Loading categories..." |
| Preload failed | Error message + Retry button |
| Categories loaded | Category list with selections |
| Saving selection | Brief loading on toggle (or optimistic) |

### XOC-9 No Skip Option (MANDATORY)

The user CANNOT skip category selection on first setup. They MUST:
- See the overlay
- Either confirm defaults or make changes
- Close the overlay via Confirm button

**Rationale:** Prevents accidental sync of 100k+ items when user only wants specific categories.

---

## 6. Pipeline Integration

### XOC-10 Server-Side Category Filtering (MANDATORY)

When syncing, the pipeline MUST use server-side filtering:

```kotlin
// VodItemPhase.kt
val selectedCategoryIds = categoryRepository.getSelectedCategoryIds(accountKey, VOD)
for (categoryId in selectedCategoryIds) {
    source.streamVodItems(categoryId = categoryId, batchSize = batchSize)
}
```

**API Endpoints with category_id:**
```
GET /player_api.php?action=get_vod_streams&category_id={id}
GET /player_api.php?action=get_series&category_id={id}
GET /player_api.php?action=get_live_streams&category_id={id}
```

### XOC-11 Fallback Behavior (MANDATORY)

If `selectedCategoryIds.isEmpty()`:
- Log warning: "No categories selected, syncing all"
- Fetch without category_id (all items)
- This should NOT happen if XOC-9 is enforced

---

## 7. Worker Integration

### XOC-12 CatalogSyncOrchestratorWorker Gate Check (MANDATORY)

```kotlin
// In CatalogSyncOrchestratorWorker.syncXtream()
private suspend fun syncXtream(): Pair<Int, String> {
    val accountKey = resolveXtreamAccountKey() ?: return 0 to "no account"

    // GATE CHECK (XOC-3)
    if (!categorySelectionRepository.isCategorySelectionComplete(accountKey)) {
        UnifiedLog.w(TAG) { "Category selection not complete, skipping Xtream sync" }
        return 0 to "category_selection_incomplete"
    }

    // Proceed with sync...
}
```

---

## 8. State Transitions

```
                    ┌──────────────────┐
                    │ XTREAM_INACTIVE  │
                    └────────┬─────────┘
                             │ connectXtream() success
                             ▼
                    ┌──────────────────────────┐
                    │ XTREAM_ACTIVE            │
                    │ categorySelectionComplete│
                    │ = false                  │
                    └────────┬─────────────────┘
                             │ preloadCategories() success
                             ▼
                    ┌──────────────────────────┐
                    │ CATEGORIES_LOADED        │
                    │ showCategoryOverlay      │
                    │ = true                   │
                    └────────┬─────────────────┘
                             │ User closes overlay
                             ▼
                    ┌──────────────────────────┐
                    │ XTREAM_READY_FOR_SYNC    │
                    │ categorySelectionComplete│
                    │ = true                   │
                    └────────┬─────────────────┘
                             │ enqueueAutoSync()
                             ▼
                    ┌──────────────────────────┐
                    │ SYNC_IN_PROGRESS         │
                    │ (with selected cats only)│
                    └──────────────────────────┘
```

---

## 9. Existing Sources (App Upgrade)

### XOC-13 Backward Compatibility (MANDATORY)

For users upgrading with existing Xtream sources:
- `categorySelectionComplete` defaults to `true` (grandfather clause)
- Existing synced content is preserved
- User can still change category selection in Settings

**Detection:**
```kotlin
if (existingXtreamSource && categories.isEmpty()) {
    // Legacy source, no category data
    setCategorySelectionComplete(accountKey, true)
}
```

---

## 10. Verification Checklist

### Pre-Implementation
- [x] NxCategorySelectionRepository has `isCategorySelectionComplete()` method
- [x] NxCategorySelectionRepository has `setCategorySelectionComplete()` method
- [x] OnboardingViewModel injects XtreamCategoryPreloader
- [x] StartScreen has CategorySelectionOverlay composable

### Post-Implementation
- [x] New Xtream source cannot sync before overlay interaction
- [x] Category selections persist across app restart
- [x] Only selected categories are fetched from server
- [x] Upgraded existing sources can still sync
- [x] CatalogSyncOrchestratorWorker.syncXtream() checks gate (XOC-3)
- [x] OnboardingViewModel.confirmCategorySelection() sets gate true (XOC-4)
- [x] Settings → CategorySelectionScreen navigable when Xtream active
- [x] CategorySelectionViewModel.saveAndSync() marks complete + triggers sync

---

## 11. Anti-Patterns (FORBIDDEN)

| Anti-Pattern | Why Forbidden |
|--------------|---------------|
| Auto-triggering sync after auth | User hasn't selected categories |
| Navigating to Home before overlay | Sync might start in background |
| Soft-skip via "Select All" button only | User must see overlay |
| In-memory only categorySelectionComplete | Lost on app restart, sync gate bypassed |
| Client-side category filtering | Wastes bandwidth, defeats purpose |

---

> **Canonical Location:** `/contracts/XTREAM_ONBOARDING_CATEGORY_SELECTION_CONTRACT.md`  
> **Related:** `CATALOG_SYNC_WORKERS_CONTRACT_V2.md`, `XTREAM_SCAN_PREMIUM_CONTRACT_V1.md`

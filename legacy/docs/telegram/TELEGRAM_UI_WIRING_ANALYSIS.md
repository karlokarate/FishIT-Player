> LEGACY (V1) – historical document  
> Not valid for v2. For current architecture see `V2_PORTAL.md` and `docs/v2/**`.

# Telegram UI Wiring Analysis

> **Created:** 2025-12-01  
> **Updated:** 2025-12-01  
> **Purpose:** Document the end-to-end wiring from TelegramItem ingestion pipeline to UI screens, and identify gaps.

---

## Status: FIXED ✅

**Date:** 2025-12-01

### Summary of Changes

The UI has been successfully wired to the new `ObxTelegramItem`-based pipeline. The following changes were made:

#### 1. Repository Layer (`TelegramContentRepository.kt`)
- Added `TelegramChatSummary` data class for UI row display
- Added `observeVodItemsByChat(): Flow<Map<Long, List<TelegramItem>>>` - queries `ObxTelegramItem`
- Added `observeAllVodItems(): Flow<List<TelegramItem>>` - flat list of VOD items from new table
- Added `observeVodChatSummaries(): Flow<List<TelegramChatSummary>>` - chat summaries for rows
- Added `isVodType()` helper to filter playable content (MOVIE, SERIES_EPISODE, CLIP)
- Deprecated legacy methods `getTelegramVodByChat()` and `getTelegramSeriesByChat()`

#### 2. ViewModel Layer (`StartViewModel.kt`)
- Added `telegramVodByChat: StateFlow<Map<Long, Pair<String, List<TelegramItem>>>>` using new API
- Added `telegramChatSummaries: StateFlow<List<TelegramChatSummary>>` for row summaries
- Updated `observeTelegramContent()` to use `observeVodItemsByChat()` and `observeVodChatSummaries()`
- Deprecated legacy `telegramContentByChat` property

#### 3. UI Layer (`StartScreen.kt`)
- Added `openTelegramItem` callback with `(chatId: Long, anchorMessageId: Long)` signature
- Replaced `FishTelegramRow` (MediaItem-based) with `FishTelegramItemRow` (TelegramItem-based)
- Updated click handlers to navigate using `(chatId, anchorMessageId)` keys
- Imported `TelegramItem` and `FishTelegramItemRow`

#### 4. Navigation (`MainActivity.kt`)
- Added new route: `telegram_item/{chatId}/{anchorMessageId}`
- Route uses `TelegramItemDetailScreen` which loads from `ObxTelegramItem` via `getItem(chatId, anchorMessageId)`
- Updated `StartScreen` and `LibraryScreen` calls to include `openTelegramItem` callback

#### 5. LibraryScreen.kt
- Added `openTelegramItem` callback parameter (same signature as StartScreen)

### Data Flow After Fix

```
                    NEW PIPELINE (Working + UI Connected)
                    =====================================
                    
TelegramSyncWorker
        │
        ▼
TelegramIngestionCoordinator
        │
        ▼
TelegramHistoryScanner → ExportMessage
        │
        ▼
TelegramBlockGrouper → MessageBlock
        │
        ▼
TelegramItemBuilder → TelegramItem
        │
        ▼
TelegramContentRepository.upsertItems()
        │
        ▼
    ┌─────────────────────┐
    │  ObxTelegramItem    │
    │  (NEW TABLE)        │
    └─────────────────────┘
            │
            │ NOW CONNECTED ✅
            │
            ▼
    ┌─────────────────────┐
    │ StartViewModel      │
    │ observeVodItemsByChat() │
    │ observeVodChatSummaries() │
    └─────────────────────┘
            │
            │
            ▼
    ┌─────────────────────┐
    │ FishTelegramItemRow │
    │ FishTelegramItemContent │
    └─────────────────────┘
            │
            │
            ▼
    ┌─────────────────────┐
    │ TelegramItemDetailScreen │
    │ (via telegram_item/{chatId}/{anchorMessageId}) │
    └─────────────────────┘
```

### Debug Logs

With the fix applied, you should see logs like:

```
telegram-ui: UI summaries: 2 chats, totalVod=45
telegram-ui: StartVM received 2 Telegram chat summaries
telegram-ui: StartVM received 2 chats, 45 total items from ObxTelegramItem (new pipeline)
telegram-ui: Rendering Telegram rows with 2 chats
```

### Legacy Code Status

- Legacy `ObxTelegramMessage`-based code remains in place but is **NOT used** by main UI paths
- Methods marked with `@Deprecated` annotation pointing to new replacements
- `TelegramDetailScreen` (legacy route `telegram/{id}`) still works for backward compatibility

---

## Original Analysis (Historical Reference)

**The refactored TDLib ingestion pipeline is running correctly but the UI is NOT connected to it.**

- **New pipeline**: `TelegramSyncWorker` → `TelegramIngestionCoordinator` → `TelegramHistoryScanner` → `ExportMessage` → `TelegramBlockGrouper` → `TelegramItemBuilder` → `TelegramItem` → `TelegramContentRepository.upsertItems()` → `ObxTelegramItem`
- **UI data source**: `StartScreen` and `LibraryScreen` use `TelegramContentRepository.getTelegramVodByChat()` and `getTelegramSeriesByChat()`, which query **`ObxTelegramMessage`** (legacy entity), NOT `ObxTelegramItem`.
- **Root cause**: The new ingestion pipeline persists to `ObxTelegramItem`, but the UI queries `ObxTelegramMessage`.

---

## STEP 1: Telegram Data Sources

### TelegramContentRepository Methods

| API                                | Returns                              | Uses Entity        | Legacy? |
|------------------------------------|--------------------------------------|-------------------|---------|
| `observeAllItems()`                | `Flow<List<TelegramItem>>`          | `ObxTelegramItem` | No      |
| `observeItemsByChat(chatId)`       | `Flow<List<TelegramItem>>`          | `ObxTelegramItem` | No      |
| `getItem(chatId, anchorMessageId)` | `TelegramItem?`                     | `ObxTelegramItem` | No      |
| `upsertItems(items)`               | Persists to `ObxTelegramItem`       | `ObxTelegramItem` | No      |
| `getTelegramItemCount()`           | `Long`                              | `ObxTelegramItem` | No      |
| `clearAllItems()`                  | Clears `ObxTelegramItem`            | `ObxTelegramItem` | No      |
| `indexChatMessages()` *@Deprecated*| Persists to `ObxTelegramMessage`    | `ObxTelegramMessage` | **Yes** |
| `getAllTelegramContent()`          | `Flow<List<MediaItem>>`             | `ObxTelegramMessage` | **Yes** |
| `getTelegramVod()`                 | `Flow<List<MediaItem>>`             | `ObxTelegramMessage` | **Yes** |
| `getTelegramSeries()`              | `Flow<List<MediaItem>>`             | `ObxTelegramMessage` | **Yes** |
| `getTelegramFeedItems()`           | `Flow<List<MediaItem>>`             | `ObxTelegramMessage` | **Yes** |
| `getTelegramContentByChat()`       | `Flow<Map<Long, Pair<...>>>`        | `ObxTelegramMessage` | **Yes** |
| `getTelegramVodByChat()`           | `Flow<Map<Long, Pair<...>>>`        | `ObxTelegramMessage` | **Yes** |
| `getTelegramSeriesByChat()`        | `Flow<Map<Long, Pair<...>>>`        | `ObxTelegramMessage` | **Yes** |

### Key Insight

The **new pipeline** (TelegramIngestionCoordinator) uses:
- `TelegramContentRepository.upsertItems(List<TelegramItem>)` → persists to **`ObxTelegramItem`**

The **UI screens** (StartScreen, LibraryScreen) use:
- `TelegramContentRepository.getTelegramVodByChat()` → queries **`ObxTelegramMessage`**
- `TelegramContentRepository.getTelegramSeriesByChat()` → queries **`ObxTelegramMessage`**

**These are two different tables!**

---

## STEP 2: ViewModels That Expose Telegram Content

| ViewModel                       | Source Repo Method(s)                 | Model Type       | Connected to UI? |
|--------------------------------|---------------------------------------|------------------|------------------|
| `TelegramLibraryViewModel`     | `observeAllItems()`, `observeItemsByChat()`, `getItem()` | `TelegramItem` | **NOT connected to main screens** |
| `TelegramActivityFeedViewModel`| `getTelegramFeedItems()`              | `MediaItem` (legacy) | Connected to TelegramActivityFeedScreen |
| `TelegramSettingsViewModel`    | No content queries (auth/settings)    | N/A              | Connected to Settings |
| `TelegramLogViewModel`         | Log entries only                      | `TgLogEntry`     | Connected to TelegramLogScreen |
| `StartViewModel`               | `tgRepo.getTelegramVodByChat()`       | `MediaItem` (legacy) | **Main Start screen** |
| (LibraryScreen direct)         | `tgRepo.getTelegramVodByChat()`, `getTelegramSeriesByChat()` | `MediaItem` (legacy) | **Main Library screen** |

### Key Insight

- **`TelegramLibraryViewModel`** is designed for the new `TelegramItem` pipeline but is **NOT used by any active screen**.
- **`StartViewModel`** and **`LibraryScreen`** use legacy methods that query `ObxTelegramMessage`.

---

## STEP 3: Composables/Screens That Use These ViewModels

| Composable/Screen             | ViewModel/Repo                      | Data Type              | Reachable Route?       |
|-------------------------------|-------------------------------------|------------------------|------------------------|
| `FishTelegramContent`         | N/A (takes `MediaItem` param)       | `MediaItem` (legacy)   | Used by StartScreen, LibraryScreen |
| `FishTelegramItemContent`     | N/A (takes `TelegramItem` param)    | `TelegramItem` (new)   | **NOT used anywhere** |
| `FishTelegramRow`             | N/A (takes `List<MediaItem>` param) | `MediaItem` (legacy)   | Used by StartScreen, LibraryScreen |
| `FishTelegramItemRow`         | N/A (takes `List<TelegramItem>` param) | `TelegramItem` (new) | **NOT used anywhere** |
| `TelegramDetailScreen`        | Direct OBX query                    | `ObxTelegramMessage`   | Yes: `telegram/{id}` |
| `TelegramItemDetailScreen`    | `TelegramContentRepository.getItem()` | `TelegramItem` (new) | **NOT wired** (no route) |
| `TelegramActivityFeedScreen`  | `TelegramActivityFeedViewModel`     | `MediaItem` (legacy)   | Yes: `telegram_feed` |
| `TelegramLogScreen`           | `TelegramLogViewModel`              | `TgLogEntry`           | Yes: `telegram_log` |

### Key Insight

- **`FishTelegramItemContent`** and **`FishTelegramItemRow`** exist but are **not used**.
- **`TelegramItemDetailScreen`** exists but has **no navigation route**.
- All active UI uses legacy `MediaItem`-based composables that ultimately query `ObxTelegramMessage`.

---

## STEP 4: Navigation Wiring

### Routes in MainActivity

| Route               | Screen                      | Data Source                  |
|---------------------|-----------------------------|------------------------------|
| `telegram/{id}`     | `TelegramDetailScreen`      | `ObxTelegramMessage` (legacy)|
| `telegram_log`      | `TelegramLogScreen`         | Logs                         |
| `telegram_feed`     | `TelegramActivityFeedScreen`| `ObxTelegramMessage` (legacy)|

### Missing Routes

| Expected Route                               | Screen                      | Data Source                 |
|---------------------------------------------|-----------------------------|-----------------------------|
| `telegram_item/{chatId}/{anchorMessageId}`  | `TelegramItemDetailScreen`  | `ObxTelegramItem` (new)     |

### StartScreen / LibraryScreen Integration

Both screens:
1. Check `tgEnabled` flag
2. Call `TelegramContentRepository.getTelegramVodByChat()` or `getTelegramSeriesByChat()`
3. These methods query **`ObxTelegramMessage`** (legacy table)
4. Render using `FishTelegramRow` → `FishTelegramContent`
5. On click: Navigate to `telegram/{id}` (legacy detail screen)

**Result**: Items persisted to `ObxTelegramItem` by the new pipeline are invisible.

---

## STEP 5: The Gap

### Data Flow Diagram

```
                    NEW PIPELINE (Working)                    LEGACY PIPELINE (Empty)
                    ========================                  =======================
                    
TelegramSyncWorker                                           (Not populated anymore)
        │                                                              │
        ▼                                                              │
TelegramIngestionCoordinator                                           │
        │                                                              │
        ▼                                                              │
TelegramHistoryScanner → ExportMessage                                 │
        │                                                              │
        ▼                                                              │
TelegramBlockGrouper → MessageBlock                                    │
        │                                                              │
        ▼                                                              │
TelegramItemBuilder → TelegramItem                                     │
        │                                                              │
        ▼                                                              │
TelegramContentRepository.upsertItems()                                │
        │                                                              │
        ▼                                                              ▼
    ┌─────────────────────┐                              ┌───────────────────────────┐
    │  ObxTelegramItem    │                              │  ObxTelegramMessage       │
    │  (NEW TABLE)        │                              │  (LEGACY TABLE - EMPTY)   │
    └─────────────────────┘                              └───────────────────────────┘
            │                                                          │
            │ NOT CONNECTED                                            │ CONNECTED
            │                                                          │
            ▼                                                          ▼
    ┌─────────────────────┐                              ┌───────────────────────────┐
    │ TelegramLibraryVM   │                              │ StartViewModel            │
    │ observeAllItems()   │                              │ LibraryScreen             │
    │                     │                              │ TelegramActivityFeedVM    │
    │ *** NOT USED ***    │                              │                           │
    └─────────────────────┘                              └───────────────────────────┘
            │                                                          │
            │                                                          │
            ▼                                                          ▼
    ┌─────────────────────┐                              ┌───────────────────────────┐
    │ FishTelegramItem*   │                              │ FishTelegramContent       │
    │ TelegramItemDetail  │                              │ FishTelegramRow           │
    │                     │                              │ TelegramDetailScreen      │
    │ *** NOT USED ***    │                              │ TelegramActivityFeedScreen│
    └─────────────────────┘                              └───────────────────────────┘
```

---

## STEP 6: Summary of Wiring Gaps

### Gap 1: Repository Methods Query Wrong Table

**Problem**: `getTelegramVodByChat()`, `getTelegramSeriesByChat()`, etc. query `ObxTelegramMessage`, not `ObxTelegramItem`.

**Fix Required**: Add new repository methods that query `ObxTelegramItem` and convert to `MediaItem` for UI compatibility:
- `getTelegramVodByChatFromItems()` 
- `getTelegramSeriesByChatFromItems()`
- Or update existing methods to query `ObxTelegramItem`

### Gap 2: TelegramLibraryViewModel Not Used

**Problem**: `TelegramLibraryViewModel.allItems` and `itemsByChat()` expose `TelegramItem` flows, but no screen uses them.

**Fix Required**: Either:
1. Wire `TelegramLibraryViewModel` to StartScreen/LibraryScreen
2. Or create adapter methods in `TelegramContentRepository` that expose `MediaItem` from `ObxTelegramItem`

### Gap 3: TelegramItemDetailScreen Not Routed

**Problem**: `TelegramItemDetailScreen` exists and uses `TelegramContentRepository.getItem()` (new pipeline), but has no navigation route.

**Fix Required**: Add route in MainActivity:
```kotlin
composable("telegram_item/{chatId}/{anchorMessageId}") { backStack ->
    val chatId = backStack.arguments?.getString("chatId")?.toLongOrNull() ?: return@composable
    val anchorMessageId = backStack.arguments?.getString("anchorMessageId")?.toLongOrNull() ?: return@composable
    TelegramItemDetailScreen(
        chatId = chatId,
        anchorMessageId = anchorMessageId,
        openInternal = { ... },
        ...
    )
}
```

### Gap 4: FishTelegramItem* Composables Not Used

**Problem**: `FishTelegramItemContent` and `FishTelegramItemRow` exist for `TelegramItem` display but are not used.

**Fix Required**: Update StartScreen/LibraryScreen to use these composables when displaying content from `ObxTelegramItem`.

---

## Recommended Fix Strategy

### Option A: Minimal Fix (Wire New Table to Existing UI)

1. Add new methods to `TelegramContentRepository`:
   - `getTelegramVodByChatFromItems(): Flow<Map<Long, Pair<String, List<MediaItem>>>>`
   - `getTelegramSeriesByChatFromItems(): Flow<Map<Long, Pair<String, List<MediaItem>>>>`
   
2. These methods:
   - Query `ObxTelegramItem`
   - Convert `TelegramItem` → `MediaItem` using a mapper
   - Return same format as legacy methods

3. Update `StartViewModel.observeTelegramContent()` to call new method:
   ```kotlin
   tgRepo.getTelegramVodByChatFromItems().collectLatest { ... }
   ```

4. Update `LibraryScreen` similarly.

### Option B: Full Migration (Use TelegramItem throughout)

1. Update `StartScreen`/`LibraryScreen` to use `TelegramLibraryViewModel`
2. Use `FishTelegramItemRow` and `FishTelegramItemContent`
3. Add route for `TelegramItemDetailScreen`
4. Update navigation to use `(chatId, anchorMessageId)` keys

**Recommendation**: Option A is quicker and maintains UI compatibility. Option B is cleaner but requires more changes.

---

## Verification

**Debug logging has been added** to confirm this analysis at runtime. Look for tag `telegram-ui`:

```
# Filter in logcat:
adb logcat -s telegram-ui:D
```

### Log Locations Added

| File | Log Message |
|------|-------------|
| `TelegramContentRepository.observeAllItems()` | `"TelegramContentRepository.observeAllItems(): X items in ObxTelegramItem (new table)"` |
| `TelegramContentRepository.getTelegramVodByChat()` | `"TelegramContentRepository.getTelegramVodByChat(): querying X chatIds from ObxTelegramMessage (legacy)"` |
| `TelegramContentRepository.buildChatMoviesMap()` | `"TelegramContentRepository: Total ObxTelegramMessage count = X (legacy table)"` |
| `TelegramContentRepository.buildChatMoviesMap()` | `"TelegramContentRepository.buildChatMoviesMap(): returning X chats from ObxTelegramMessage (legacy)"` |
| `TelegramLibraryViewModel.allItems` | `"TelegramLibraryVM received X TelegramItems from ObxTelegramItem"` |
| `StartViewModel.observeTelegramContent()` | `"StartVM received X chats, Y total items from ObxTelegramMessage (legacy)"` |
| `TelegramIngestionCoordinator.processBatch()` | `"TelegramIngestionCoordinator: Persisted X TelegramItems to ObxTelegramItem (new table)"` |

### Expected Output (if analysis is correct)

```
telegram-ui: TelegramIngestionCoordinator: Persisted 99 TelegramItems to ObxTelegramItem (new table)
telegram-ui: TelegramContentRepository.observeAllItems(): 99 items in ObxTelegramItem (new table)
telegram-ui: TelegramLibraryVM received 99 TelegramItems from ObxTelegramItem
telegram-ui: TelegramContentRepository.getTelegramVodByChat(): querying 6 chatIds from ObxTelegramMessage (legacy)
telegram-ui: TelegramContentRepository: Total ObxTelegramMessage count = 0 (legacy table)
telegram-ui: TelegramContentRepository.buildChatMoviesMap(): returning 0 chats from ObxTelegramMessage (legacy)
telegram-ui: StartVM received 0 chats, 0 total items from ObxTelegramMessage (legacy)
```

This would confirm:
- ✅ New pipeline persists items to `ObxTelegramItem`
- ❌ UI queries `ObxTelegramMessage` which is empty

---

## Files to Modify (for fix)

### Option A Files
1. `TelegramContentRepository.kt` - Add new query methods
2. `StartViewModel.kt` - Use new method
3. `LibraryScreen.kt` - Use new method

### Option B Files (additional)
4. `MainActivity.kt` - Add route for TelegramItemDetailScreen
5. `StartScreen.kt` - Use TelegramLibraryViewModel
6. `LibraryScreen.kt` - Use TelegramLibraryViewModel
7. `FishTelegramContent.kt` - Already exists, just needs usage

---

## Conclusion

**The refactored TDLib ingestion pipeline is working correctly.**

The items are being successfully:
1. Scanned from TDLib chats
2. Converted to ExportMessage → TelegramItem
3. Persisted to `ObxTelegramItem` via `upsertItems()`

**The problem is that the UI is not connected to `ObxTelegramItem`.**

The UI (StartScreen, LibraryScreen, etc.) still queries the legacy `ObxTelegramMessage` table, which is no longer being populated by the new ingestion pipeline.

**Fix Required**: Wire the UI to query `ObxTelegramItem` instead of `ObxTelegramMessage`.

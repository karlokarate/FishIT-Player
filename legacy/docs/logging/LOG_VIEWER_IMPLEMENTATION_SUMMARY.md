> LEGACY (V1) – historical document  
> Not valid for v2. For current architecture see `V2_PORTAL.md` and `docs/v2/**`.

# Log Viewer Implementation Summary

## Current Scope
- In-app viewer for the unified `AppLog` stream (bounded ring buffer + live events)
- Depends on Settings toggles (master + per-category) to receive data
- No file parsing or SAF import; everything is in-memory

## Feature Coverage
- ✅ Live and historical AppLog entries shown in a scrollable, selectable list
- ✅ Export writes the current buffer to `cacheDir/applog_*.txt` (best-effort text dump)
- ⚠️ Filters/search are not present yet; categories are controlled globally via Settings

## Key Components
- `LogViewerViewModel` (`app/src/main/java/com/chris/m3usuite/logs/LogViewerViewModel.kt`)
  - Collects `AppLog.history`, maps to display strings, surfaces loading/error state
- `LogViewerScreen` (`app/src/main/java/com/chris/m3usuite/logs/ui/LogViewerScreen.kt`)
  - Basic scaffold with back + export actions and a selectable list of log lines
- `LogExporter` (same file)
  - Writes a timestamped text file into `cacheDir`; enabled only when entries exist

## Notes / Next Steps
- Viewer reflects only what AppLog captures; ensure master/category toggles are ON for desired categories
- Future options: add simple filters (level/category) and JSON export if needed

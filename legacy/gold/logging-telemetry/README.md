# Gold: Logging & Telemetry

Curated patterns from the v1 logging and telemetry implementation.

## Documentation

✅ **`GOLD_LOGGING.md`** (506 lines) - Complete extraction of logging patterns

## Key Patterns Extracted

1. **UnifiedLog Facade** – Single entry point for all logging
2. **Ring Buffer** – 1000-entry circular buffer for in-memory logs
3. **SourceCategory System** – Predefined log categories for filtering
4. **Persistent Filters** – DataStore-backed filter settings
5. **Optional File Export** – Session log file for bug reports
6. **In-App Log Viewer** – Real-time log UI with DPAD navigation
7. **Structured Events** – JSON-serializable diagnostics events
8. **Async Processing** – Non-blocking event logging
9. **Performance Monitor** – Automatic slow operation detection
10. **Crashlytics Integration** – ERROR logs sent to Firebase

## v2 Target Modules

- `infra/logging/` - **Already exists!** Verify implementation matches v1
- `infra/telemetry/` - DiagnosticsLogger and PerformanceMonitor
- `feature/settings/ui/` - Log viewer screen

## v2 Status

✅ **Partial** - `infra/logging/UnifiedLog.kt` exists, needs verification

**Phase 0: Verify** (IMMEDIATE) - Check if existing implementation has:
- Ring buffer (1000 entries)
- StateFlow emission for UI
- Source category system
- Filter persistence

## Porting Checklist

### Phase 0: Validation (FIRST)
- [ ] Verify infra/logging/UnifiedLog.kt matches v1 API
- [ ] Check for ring buffer implementation
- [ ] Verify StateFlow emission for UI
- [ ] Document any missing features

### Phase 1: Categories & Filtering
- [ ] Port SourceCategory system
- [ ] Add DataStore for filter persistence
- [ ] Add Flow-based filter API
- [ ] Update categories for v2 modules

### Phase 2: Structured Events
- [ ] Port DiagnosticsLogger to infra/telemetry
- [ ] Add JSON serialization
- [ ] Add async processing
- [ ] Integrate with UnifiedLog

### Phase 3: UI Viewer
- [ ] Port log viewer screen to feature/settings
- [ ] Add filter chips
- [ ] Add search bar
- [ ] Add export functionality
- [ ] TV-optimize with DPAD

### Phase 4: Performance
- [ ] Port PerformanceMonitor
- [ ] Add timing utilities
- [ ] Integrate with telemetry
- [ ] Add slow operation detection

## Key Principles

1. **Single Entry Point** - One logging API for entire app
2. **Ring Buffer** - Bounded memory for log entries
3. **Reactive Filters** - Flows for UI consumption
4. **Structured Events** - JSON for analysis
5. **Async Processing** - Never block UI thread
6. **In-App Viewer** - Debug without USB
7. **No Sensitive Data** - Never log tokens, passwords, etc.

## References

- **Gold Doc:** `GOLD_LOGGING.md` (this folder)
- **v1 Source:** `/legacy/v1-app/app/src/main/java/com/chris/m3usuite/core/logging/`
- **v2 Existing:** `/infra/logging/` (verify!)
- **v2 Contract:** `/docs/v2/LOGGING_CONTRACT_V2.md`

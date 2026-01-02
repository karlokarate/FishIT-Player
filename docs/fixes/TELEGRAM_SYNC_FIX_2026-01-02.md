# Telegram Sync Fix - 2026-01-02

## Problem Statement

**User Report (German):**
> prüfe, warum der Telegram sync aktuell keine Ergebnisse liefert. In den logs sehe ich zwar das der worker gestartet sei, aber finden keine chat scans statt.

**Translation:**
> Check why Telegram sync is currently not delivering results. In the logs I see that the worker has started, but no chat scans are happening.

## Problem Analysis

The Telegram sync worker would start but never proceed with actual chat scanning. The logs showed:
- Worker started successfully
- Auth preflight checks were running
- No chat scans occurred
- Sync ended without results

## Root Cause

The issue was in `TdlibAuthSession` (infra/transport-telegram):

### The Auth State Flow Problem

1. **Flow Initialization**: `TdlibAuthSession` has a `_authState` StateFlow initialized to `Idle`
2. **Collector Not Started**: The auth state collector (`startAuthCollectorIfNeeded()`) was only called from `ensureAuthorized()`
3. **Already-Authenticated Sessions**: When TDLib had an existing valid session (user previously logged in), `ensureAuthorized()` was never called during app startup
4. **Stuck at Idle**: The `authState` Flow remained at `Idle` instead of transitioning to `Ready` when TDLib was actually authorized
5. **Activation Observer Blocked**: `TelegramActivationObserver` watches `authState` and only sets Telegram source as ACTIVE when it sees `Connected` state
6. **Workers Never Started**: `CatalogSyncOrchestratorWorker` checks active sources and skips Telegram if it's not active
7. **Pipeline Check Failed**: Even if workers somehow started, `TelegramCatalogPipelineImpl.scanCatalog()` has pre-flight checks that verify auth state is `Ready` before scanning

### The Sequence of Events (Broken)

```
App Start
  → TdlibAuthSession created (authState = Idle)
  → TelegramAuthRepositoryImpl observes transport.authState (sees Idle)
  → TelegramActivationObserver observes repo.authState (sees Idle)
  → TelegramActivationObserver maps Idle → Unchanged (doesn't change activation)
  → Source remains INACTIVE
  → CatalogSyncOrchestratorWorker skips Telegram
  → No chat scans occur
```

### Why ensureAuthorized() Wasn't Called

Looking at the app initialization flow in `FishItV2Application.onCreate()`:
1. UnifiedLog initialized
2. WorkManager initialized
3. SourceActivationObserver started
4. TelegramActivationObserver started
5. XtreamSessionBootstrap started (calls ensureAuthorized for Xtream)
6. CatalogSyncBootstrap started

There's no explicit call to initialize the Telegram auth session or call `ensureAuthorized()` during startup. The assumption was that the auth state would be automatically observed, but the observer was never started!

## Solution

### The Fix

Start the auth state collector immediately in `TdlibAuthSession.init()` instead of waiting for `ensureAuthorized()` to be called:

```kotlin
class TdlibAuthSession(
    private val client: TdlClient,
    private val config: TelegramSessionConfig,
    private val scope: CoroutineScope
) : TelegramAuthClient {
    
    init {
        // Start auth state collector immediately so auth state is always up-to-date
        // This ensures TelegramActivationObserver sees state transitions even if
        // ensureAuthorized() is never called (e.g., when resuming existing session)
        startAuthCollectorIfNeeded()
        UnifiedLog.d(TAG, "TdlibAuthSession initialized - auth state collector started")
    }
    
    // ... rest of implementation
}
```

### Why This Works

1. **Immediate Observation**: The collector starts as soon as `TdlibAuthSession` is created (via DI in `TelegramTransportModule`)
2. **TDLib State Sync**: The collector subscribes to `client.authorizationStateUpdates` which emits TDLib's current auth state
3. **Proper State Flow**: When TDLib is already authorized, it emits `AuthorizationStateReady` → `_authState` updates to `Ready`
4. **Activation Chain Works**: `TelegramAuthRepositoryImpl` sees `Ready` → `TelegramActivationObserver` sees `Connected` → sets source ACTIVE
5. **Workers Start**: `CatalogSyncOrchestratorWorker` sees Telegram is active → enqueues Telegram worker chain
6. **Scans Proceed**: `TelegramCatalogPipelineImpl` pre-flight checks pass → chat scanning begins

### The Sequence of Events (Fixed)

```
App Start
  → TdlibAuthSession created
  → init() calls startAuthCollectorIfNeeded()
  → Collector subscribes to client.authorizationStateUpdates
  → TDLib emits AuthorizationStateReady (session exists)
  → _authState updates to Ready
  → TelegramAuthRepositoryImpl observes Ready → emits Connected
  → TelegramActivationObserver sees Connected → calls setTelegramActive()
  → Source is now ACTIVE
  → CatalogSyncOrchestratorWorker enqueues Telegram chain
  → TelegramAuthPreflightWorker checks auth (passes)
  → TelegramFullHistoryScanWorker / TelegramIncrementalScanWorker starts
  → TelegramCatalogPipelineImpl.scanCatalog() pre-flight checks pass
  → Chat scanning proceeds ✅
```

## Files Changed

- `infra/transport-telegram/src/main/java/com/fishit/player/infra/transport/telegram/auth/TdlibAuthSession.kt`
  - Added `init` block to start auth state collector immediately
  - Added explanatory comment about why this is needed

## Testing

### Unit Tests
- ✅ `TelegramAuthRepositoryImplTest` - All tests pass (uses fake that doesn't require collector)

### Integration Testing Required
1. Launch app with existing Telegram auth session
2. Verify logs show:
   - "TdlibAuthSession initialized - auth state collector started"
   - "Auth state: ... → AuthorizationStateReady"
   - "Telegram auth ready → calling sourceActivationStore.setTelegramActive()"
   - "Telegram source marked ACTIVE - workers should be scheduled"
   - "Enqueued Telegram chain: ..."
   - Chat scan progress messages from TelegramCatalogPipelineImpl

3. Verify chat content appears in UI after sync completes

## Impact

### Positive
- Telegram sync now works for users with existing authenticated sessions
- Auth state is always properly tracked from app start
- No breaking changes to public APIs

### No Negative Impact
- The collector is idempotent (uses `AtomicBoolean` to ensure single start)
- The `ensureAuthorized()` method still works as before (still calls `startAuthCollectorIfNeeded()` for safety)
- No additional overhead (collector was always supposed to run, just wasn't starting early enough)

## Architecture Compliance

### Layer Boundaries (AGENTS.md Section 4)
- ✅ Fix is in transport layer (`infra/transport-telegram`)
- ✅ No changes to pipeline, data, or UI layers
- ✅ Respects SSOT principle (TelegramClient is still the single source of truth)

### Contracts
- ✅ GLOSSARY_v2_naming_and_modules.md - No naming changes
- ✅ TELEGRAM_TRANSPORT_SSOT.md - Still uses single TelegramClient instance
- ✅ CATALOG_SYNC_WORKERS_CONTRACT_V2.md - Worker behavior unchanged

## Future Improvements

Consider adding:
1. Explicit auth initialization step in app startup (for clarity)
2. Health check endpoint that verifies auth state is being tracked
3. Startup diagnostics that log auth state transitions during first 10 seconds

## References

- PR: copilot/fix-telegram-sync-issues
- Commit: 43a943d "Fix: Start TDLib auth state collector immediately on initialization"
- Related Files:
  - `app-v2/src/main/java/com/fishit/player/v2/TelegramActivationObserver.kt`
  - `infra/data-telegram/src/main/java/com/fishit/player/infra/data/telegram/auth/TelegramAuthRepositoryImpl.kt`
  - `core/catalog-sync/src/main/java/com/fishit/player/core/catalogsync/DefaultCatalogSyncService.kt`
  - `pipeline/telegram/src/main/java/com/fishit/player/pipeline/telegram/catalog/TelegramCatalogPipelineImpl.kt`

---
applyTo:
  - infra/work/**
---

# 🏆 PLATIN Instructions: infra/work

> **PLATIN STANDARD** - WorkManager Infrastructure & Source Activation Store.
>
> **Purpose:** Provides the infrastructure layer for background sync scheduling
> and source activation state persistence. This module is the SSOT for which
> sources are active/inactive and bridges the gap between core APIs and app-level workers.

---

## 🔴 ABSOLUTE HARD RULES

### 1. SourceActivationStore is SSOT for Source Status (W-1)
```kotlin
// ✅ CORRECT: Query SourceActivationStore for active sources
val activeSources = sourceActivationStore.getActiveSources()
if (SourceId.TELEGRAM in activeSources) {
    // Telegram is active - safe to sync
}

// ❌ FORBIDDEN: Direct transport checks
if (telegramClient.isConnected()) { ... }  // NO! Use SourceActivationStore

// ❌ FORBIDDEN: Hardcoded source assumptions
val sources = setOf(SourceId.XTREAM)  // NO! Always read from store
```

### 2. No Pipeline or Transport Dependencies
```kotlin
// ✅ ALLOWED
import com.fishit.player.core.sourceactivation.*
import com.fishit.player.core.catalogsync.*
import com.fishit.player.infra.logging.UnifiedLog
import androidx.datastore.preferences.*

// ❌ FORBIDDEN
import com.fishit.player.pipeline.*           // Pipeline layer
import com.fishit.player.infra.transport.*    // Transport layer
import com.fishit.player.infra.data.*         // Data layer
```

### 3. DataStore for Persistence (Not ObjectBox)
```kotlin
// ✅ CORRECT: DataStore for simple key-value state
private val Context.sourceActivationDataStore: DataStore<Preferences> 
    by preferencesDataStore(name = "source_activation")

// ❌ FORBIDDEN: ObjectBox in infra/work
@Entity class SourceStateEntity { ... }  // NO! Use DataStore
```

### 4. States Survive Process Restart (W-1)
```kotlin
// ✅ CORRECT: Persist to DataStore immediately
dataStore.edit { preferences ->
    preferences[KEY_XTREAM_STATE] = stateToString(state)
}

// ❌ FORBIDDEN: In-memory only state
private var xtreamActive = false  // Lost on restart!
```

---

## 📋 Module Contents

### DefaultSourceActivationStore.kt
```kotlin
/**
 * DataStore-backed implementation of SourceActivationStore.
 *
 * Contract: CATALOG_SYNC_WORKERS_CONTRACT_V2 (W-1)
 * - Sources are independent: Xtream, Telegram, IO can be ACTIVE/INACTIVE separately
 * - No source is ever required
 * - States survive process restart
 */
@Singleton
class DefaultSourceActivationStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : SourceActivationStore {
    
    // Observe reactive state changes
    override fun observeStates(): Flow<SourceActivationSnapshot>
    
    // Get current snapshot (synchronous)
    override fun getCurrentSnapshot(): SourceActivationSnapshot
    
    // Get set of active source IDs
    override fun getActiveSources(): Set<SourceId>
    
    // Source-specific setters
    override suspend fun setXtreamActive()
    override suspend fun setXtreamInactive(reason: SourceErrorReason?)
    override suspend fun setTelegramActive()
    override suspend fun setTelegramInactive(reason: SourceErrorReason?)
    override suspend fun setIoActive()
    override suspend fun setIoInactive(reason: SourceErrorReason?)
}
```

### Hilt Module (di/)
```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class WorkModule {
    @Binds
    @Singleton
    abstract fun bindSourceActivationStore(
        impl: DefaultSourceActivationStore
    ): SourceActivationStore
}
```

---

## ⚠️ Critical Architecture Patterns

### SourceActivationSnapshot Pattern
```kotlin
// Immutable snapshot of all source states
data class SourceActivationSnapshot(
    val xtream: SourceActivationState,
    val telegram: SourceActivationState,
    val io: SourceActivationState,
) {
    val activeSources: Set<SourceId>
        get() = buildSet {
            if (xtream == SourceActivationState.Active) add(SourceId.XTREAM)
            if (telegram == SourceActivationState.Active) add(SourceId.TELEGRAM)
            if (io == SourceActivationState.Active) add(SourceId.IO)
        }
}
```

### State Persistence Keys
```kotlin
private companion object {
    // State values (persisted strings)
    private const val STATE_INACTIVE = "inactive"
    private const val STATE_ACTIVE = "active"
    private const val STATE_ERROR = "error"

    // Preference keys (per source)
    private val KEY_XTREAM_STATE = stringPreferencesKey("xtream_state")
    private val KEY_XTREAM_REASON = stringPreferencesKey("xtream_reason")
    private val KEY_TELEGRAM_STATE = stringPreferencesKey("telegram_state")
    private val KEY_TELEGRAM_REASON = stringPreferencesKey("telegram_reason")
    private val KEY_IO_STATE = stringPreferencesKey("io_state")
    private val KEY_IO_REASON = stringPreferencesKey("io_reason")
}
```

### Initialization Pattern (runBlocking OK here)
```kotlin
init {
    // Initialize from persisted state on construction
    // runBlocking is acceptable in @Singleton init - happens once at app start
    runBlocking {
        try {
            val preferences = dataStore.data.first()
            _snapshot.value = preferencesToSnapshot(preferences)
            UnifiedLog.i(TAG) { "Initialized from persisted state: ${_snapshot.value}" }
        } catch (e: Exception) {
            UnifiedLog.e(TAG, e) { "Failed to load persisted state, using defaults" }
        }
    }
}
```

---

## 📐 Architecture Position

```
┌─────────────────────────────────────────────────────────────┐
│                        app-v2/work                          │
│  (Workers: Orchestrator, Preflight, Scan)                   │
│                            │                                │
│                    reads snapshot                           │
│                            ▼                                │
├─────────────────────────────────────────────────────────────┤
│                     ▶ infra/work ◀                          │
│  ┌─────────────────────────────────────────────────────┐    │
│  │ DefaultSourceActivationStore                        │    │
│  │  - Implements SourceActivationStore (core API)      │    │
│  │  - Persists to DataStore                            │    │
│  │  - Provides reactive Flow<Snapshot>                 │    │
│  └─────────────────────────────────────────────────────┘    │
│                            │                                │
│              implements interface from                      │
│                            ▼                                │
├─────────────────────────────────────────────────────────────┤
│                 core/source-activation-api                  │
│  (SourceActivationStore, SourceId, SourceActivationState)   │
└─────────────────────────────────────────────────────────────┘
```

**Layer Rules:**
- `infra/work` implements `core/source-activation-api`
- `app-v2/work` workers consume `SourceActivationStore` via Hilt
- No direct transport/pipeline access from this layer

---

## ✅ PLATIN Checklist

### Source Activation Store
- [ ] Implements `SourceActivationStore` interface from `core/source-activation-api`
- [ ] Uses DataStore (NOT ObjectBox) for persistence
- [ ] States survive app restart
- [ ] Each source independent (no coupling between sources)
- [ ] Provides `Flow<SourceActivationSnapshot>` for reactive observation
- [ ] Provides synchronous `getCurrentSnapshot()` for immediate reads

### Layer Boundaries
- [ ] No imports from `pipeline/*`
- [ ] No imports from `infra/transport-*`
- [ ] No imports from `infra/data-*`
- [ ] Only depends on `core/source-activation-api`, `core/catalog-sync`, `infra/logging`

### Logging (Per LOGGING_CONTRACT_V2.md)
- [ ] Uses `UnifiedLog` exclusively (no `android.util.Log`)
- [ ] TAG = class name without package
- [ ] Lambda-based logging for performance

---

## 📚 Reference Documents (Priority Order)

1. `contracts/CATALOG_SYNC_WORKERS_CONTRACT_V2.md` - W-1 through W-22
2. `contracts/LOGGING_CONTRACT_V2.md` - Logging requirements
3. `core/source-activation-api/` - Interface definitions

---

## 🚨 Common Violations & Solutions

### Violation 1: Direct Transport Status Check
```kotlin
// ❌ WRONG: Checking transport directly
if (xtreamApiClient.authState.value == XtreamAuthState.Authenticated) {
    syncXtream()
}

// ✅ CORRECT: Use SourceActivationStore
if (SourceId.XTREAM in sourceActivationStore.getActiveSources()) {
    syncXtream()
}
```

### Violation 2: ObjectBox in infra/work
```kotlin
// ❌ WRONG: ObjectBox for simple state
@Entity
data class SourceStateEntity(
    @Id var id: Long = 0,
    var xtreamActive: Boolean = false,
)

// ✅ CORRECT: DataStore for key-value state
dataStore.edit { prefs ->
    prefs[KEY_XTREAM_STATE] = STATE_ACTIVE
}
```

### Violation 3: Non-Reactive State Access
```kotlin
// ❌ WRONG: Polling for changes
while (true) {
    val snapshot = store.getCurrentSnapshot()
    delay(1000)
}

// ✅ CORRECT: Reactive observation
store.observeStates().collect { snapshot ->
    updateUI(snapshot)
}
```

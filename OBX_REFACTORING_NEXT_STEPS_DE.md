# OBX PLATIN Refactoring - Status & Nächste Schritte

**Datum:** 2026-01-12  
**Analysiert:** PR #647, PR #648, Issue #621  
**Status:** Phase 1 teilweise abgeschlossen

---

## 🎯 Executive Summary

**Frage:** "Prüfe PR 648 und 647 und sage mir, wie es jetzt weiter gehen sollte mit dem OBX Refactoring"

**Antwort:**
- ✅ **PR #648 ist erfolgreich gemerged** (2026-01-12) - Phase 1.1 abgeschlossen
- 🔄 **PR #647 hat Merge-Konflikte** und ist redundant zu #648
- 📍 **Aktueller Stand:** Phase 1.1 (Interface Definition) ✅ COMPLETE
- ⏭️ **Nächster Schritt:** Phase 1.2 (Repository Implementations) - 22 Repositories in `infra/data-nx` implementieren

---

## 📊 Detaillierte Analyse der PRs

### PR #648: ✅ MERGED (80d5fc3)

**Titel:** "Create 22 NX Repository Interfaces for Phase 1 (OBX PLATIN Refactor)"

**Status:** ✅ Erfolgreich gemerged am 2026-01-12 16:00:49 UTC

**Deliverables:**
- ✅ 22 Domain-only Repository Interfaces in `core/model/repository/`
- ✅ 1 Domain Model in `core/model/userstate/WorkUserState.kt`
- ✅ `CloudSyncState` enum definiert
- ✅ Alle Review-Findings behoben:
  - CloudSyncState von String → enum
  - WorkEmbedding.embedding von FloatArray → List<Float>
  - NxProfileRuleRepository.delete() API konsistent
  - MatchSource enum statt String

**Qualität:**
- ✅ PLATIN-compliant
- ✅ Ktlint checks passed
- ✅ Kompiliert erfolgreich
- ✅ Keine circular dependencies
- ✅ Domain-only (keine ObjectBox imports in core/model)

**Dateien erstellt:**
```
core/model/src/main/java/com/fishit/player/core/model/repository/
├── NxWorkUserStateRepository.kt
├── NxWorkUserStateDiagnostics.kt
├── NxWorkRuntimeStateRepository.kt
├── NxWorkRuntimeStateDiagnostics.kt
├── NxIngestLedgerRepository.kt
├── NxIngestLedgerDiagnostics.kt
├── NxProfileRepository.kt
├── NxProfileDiagnostics.kt
├── NxProfileRuleRepository.kt
├── NxProfileRuleDiagnostics.kt
├── NxProfileUsageRepository.kt
├── NxProfileUsageDiagnostics.kt
├── NxSourceAccountRepository.kt
├── NxSourceAccountDiagnostics.kt
├── NxCloudOutboxRepository.kt
├── NxCloudOutboxDiagnostics.kt
├── NxWorkEmbeddingRepository.kt
├── NxWorkEmbeddingDiagnostics.kt
├── NxWorkRedirectRepository.kt
├── NxWorkRedirectDiagnostics.kt
├── NxWorkAuthorityRepository.kt
└── NxWorkAuthorityDiagnostics.kt

core/model/src/main/java/com/fishit/player/core/model/userstate/
└── WorkUserState.kt (inkl. CloudSyncState enum)
```

---

### PR #647: 🔄 OPEN (merge conflicts)

**Titel:** "Phase 1.1.5: Add WorkUserStateRepository interface with domain types"

**Status:** 🔄 Open, aber `mergeable_state: dirty` (Konflikte)

**Problem:**
- Base branch ist veraltet: `8eee8cb` (vor PR #648)
- Main branch ist bei: `80d5fc3` (nach PR #648 merge)
- **Inhalt ist größtenteils identisch mit PR #648**

**Deliverables (redundant zu #648):**
- `WorkUserStateRepository.kt` → bereits via #648 gemerged
- `WorkUserState.kt` → bereits via #648 gemerged
- `CloudSyncState.kt` → bereits via #648 gemerged (als Teil von WorkUserState.kt)

**Unterschiede zu #648:**
- PR #647: WorkUserState hatte separate `CloudSyncState.kt` file
- PR #648: CloudSyncState ist im selben File wie WorkUserState
- PR #647: Etwas andere Feld-Namen (profileId vs profileKey)
- PR #648: Finale PLATIN-Version mit allen Review-Fixes

**Empfehlung:** ❌ **PR #647 schließen als duplicate/superseded by #648**

---

## 📍 Aktueller Stand: Phase 1

### Phase 1.1: ✅ COMPLETE (via PR #648)

**Ziel:** Interface Definition (Domain Contracts)

**Erreicht:**
- ✅ 22 Repository Interfaces definiert
- ✅ Domain Models erstellt (WorkUserState, CloudSyncState)
- ✅ PLATIN architecture pattern etabliert:
  - Interfaces in `core/model/repository/`
  - Domain types in `core/model/userstate/`
  - Keine ObjectBox dependencies in core/model
  - Implementation wird in `infra/data-nx/` erfolgen

**Architektur-Validierung:**
- ✅ Kein circular dependency issue
- ✅ Folgt bestehendem `ProfileRepository` pattern
- ✅ SSOT identity: (profileKey, workKey) statt DB IDs
- ✅ Cloud-ready field names (lastUpdatedAtMs, lastUpdatedByDeviceId)

### Phase 1.2: ⏳ NOT STARTED

**Ziel:** Repository Implementations

**Status:** Noch nicht begonnen

**Was fehlt:**
- ❌ `infra/data-nx` module (evtl. noch nicht existiert)
- ❌ 22 Repository Implementierungen
- ❌ Entity ↔ Domain Model Mapper
- ❌ Integration Tests
- ❌ Hilt DI Configuration

---

## 🚀 Nächste Schritte (Empfehlung)

### Immediate Actions (diese Woche)

#### 1. ✅ PR #647 schließen
```
Grund: Duplicate/superseded by PR #648
Aktion: Close ohne merge
Kommentar: "Superseded by PR #648 which was merged on 2026-01-12. 
            Content is already in main branch."
```

#### 2. 🏗️ Setup `infra/data-nx` Module

**Falls noch nicht vorhanden:**

```gradle
// settings.gradle.kts
include(":infra:data-nx")
```

```gradle
// infra/data-nx/build.gradle.kts
plugins {
    id("fishit.android.library")
    id("fishit.hilt")
}

dependencies {
    // Core dependencies
    api(project(":core:model"))
    implementation(project(":core:persistence"))
    
    // ObjectBox
    implementation(libs.objectbox.android)
    kapt(libs.objectbox.processor)
    
    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    
    // Hilt
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    
    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
}
```

**Modul-Struktur:**
```
infra/data-nx/
├── src/main/java/com/fishit/player/infra/data/nx/
│   ├── repository/
│   │   ├── WorkUserStateRepositoryImpl.kt
│   │   ├── WorkRepositoryImpl.kt
│   │   └── ... (22 implementations)
│   ├── mapper/
│   │   ├── WorkUserStateMapper.kt
│   │   └── ... (entity ↔ domain mappers)
│   └── di/
│       └── NxDataModule.kt (Hilt bindings)
└── src/test/java/
    └── ... (integration tests)
```

#### 3. 🎯 Implement Kritische Repositories zuerst

**Priority 1 (kritisch für UI):**
1. `WorkUserStateRepositoryImpl` - Resume, Favorites, Watchlist
2. `WorkRepositoryImpl` - UI SSOT für Content

**Priority 2 (wichtig):**
3. `WorkSourceRefRepositoryImpl` - Multi-source identity
4. `WorkVariantRepositoryImpl` - Playback variants

**Priority 3 (Support):**
5. Restliche 18 Repositories

#### 4. 📝 Implementation Pattern (Beispiel)

**WorkUserStateRepositoryImpl.kt:**
```kotlin
package com.fishit.player.infra.data.nx.repository

import com.fishit.player.core.model.repository.NxWorkUserStateRepository
import com.fishit.player.core.model.userstate.WorkUserState
import com.fishit.player.core.persistence.obx.NX_WorkUserState
import com.fishit.player.infra.data.nx.mapper.toEntity
import com.fishit.player.infra.data.nx.mapper.toDomain
import io.objectbox.BoxStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkUserStateRepositoryImpl @Inject constructor(
    private val boxStore: BoxStore,
) : NxWorkUserStateRepository {
    
    private val box by lazy { 
        boxStore.boxFor(NX_WorkUserState::class.java) 
    }
    
    override suspend fun get(
        profileKey: String,
        workKey: String,
    ): WorkUserState? {
        return box.query(
            NX_WorkUserState_.profileKey.equal(profileKey)
                .and(NX_WorkUserState_.workKey.equal(workKey))
        )
        .build()
        .findFirst()
        ?.toDomain()
    }
    
    override fun observe(
        profileKey: String,
        workKey: String,
    ): Flow<WorkUserState?> {
        return box.query(
            NX_WorkUserState_.profileKey.equal(profileKey)
                .and(NX_WorkUserState_.workKey.equal(workKey))
        )
        .build()
        .asFlow()
        .map { it.toDomain() }
    }
    
    // ... implement all 17 methods
}
```

**WorkUserStateMapper.kt:**
```kotlin
package com.fishit.player.infra.data.nx.mapper

import com.fishit.player.core.model.userstate.WorkUserState
import com.fishit.player.core.model.userstate.CloudSyncState
import com.fishit.player.core.persistence.obx.NX_WorkUserState

fun WorkUserState.toEntity(): NX_WorkUserState {
    return NX_WorkUserState(
        profileKey = profileKey,
        workKey = workKey,
        resumePositionMs = resumePositionMs,
        totalDurationMs = totalDurationMs,
        isWatched = isWatched,
        watchCount = watchCount,
        isFavorite = isFavorite,
        userRating = userRating,
        inWatchlist = inWatchlist,
        isHidden = isHidden,
        lastWatchedAtMs = lastWatchedAtMs,
        createdAtMs = createdAtMs,
        updatedAtMs = updatedAtMs,
        lastUpdatedByDeviceId = lastUpdatedByDeviceId,
        cloudSyncState = cloudSyncState.toEntityValue(),
    )
}

fun NX_WorkUserState.toDomain(): WorkUserState {
    return WorkUserState(
        profileKey = profileKey,
        workKey = workKey,
        resumePositionMs = resumePositionMs,
        totalDurationMs = totalDurationMs,
        isWatched = isWatched,
        watchCount = watchCount,
        isFavorite = isFavorite,
        userRating = userRating,
        inWatchlist = inWatchlist,
        isHidden = isHidden,
        lastWatchedAtMs = lastWatchedAtMs,
        createdAtMs = createdAtMs,
        updatedAtMs = updatedAtMs,
        lastUpdatedByDeviceId = lastUpdatedByDeviceId,
        cloudSyncState = cloudSyncState.toDomain(),
    )
}

private fun CloudSyncState.toEntityValue(): Int = when (this) {
    CloudSyncState.LOCAL_ONLY -> 0
    CloudSyncState.DIRTY -> 1
    CloudSyncState.SYNCED -> 2
}

private fun Int.toDomain(): CloudSyncState = when (this) {
    0 -> CloudSyncState.LOCAL_ONLY
    1 -> CloudSyncState.DIRTY
    2 -> CloudSyncState.SYNCED
    else -> CloudSyncState.LOCAL_ONLY
}
```

**Hilt Module:**
```kotlin
package com.fishit.player.infra.data.nx.di

import com.fishit.player.core.model.repository.NxWorkUserStateRepository
import com.fishit.player.infra.data.nx.repository.WorkUserStateRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NxDataModule {
    
    @Binds
    @Singleton
    abstract fun bindWorkUserStateRepository(
        impl: WorkUserStateRepositoryImpl
    ): NxWorkUserStateRepository
    
    // ... bind all 22 repositories
}
```

#### 5. ✅ Integration Tests

**WorkUserStateRepositoryTest.kt:**
```kotlin
package com.fishit.player.infra.data.nx.repository

import com.fishit.player.core.model.userstate.CloudSyncState
import com.fishit.player.core.model.userstate.WorkUserState
import io.objectbox.BoxStore
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

class WorkUserStateRepositoryTest {
    
    private lateinit var boxStore: BoxStore
    private lateinit var repository: WorkUserStateRepositoryImpl
    
    @Before
    fun setup() {
        // Setup in-memory BoxStore for testing
        boxStore = MyObjectBox.builder()
            .inMemory("test-nx")
            .build()
        repository = WorkUserStateRepositoryImpl(boxStore)
    }
    
    @After
    fun teardown() {
        boxStore.close()
    }
    
    @Test
    fun `get returns null for non-existent state`() = runTest {
        val result = repository.get("profile1", "work1")
        assertNull(result)
    }
    
    @Test
    fun `updateResumePosition creates new state`() = runTest {
        val state = repository.updateResumePosition(
            profileKey = "profile1",
            workKey = "work1",
            positionMs = 60000,
            durationMs = 3600000,
        )
        
        assertEquals("profile1", state.profileKey)
        assertEquals("work1", state.workKey)
        assertEquals(60000L, state.resumePositionMs)
        assertEquals(3600000L, state.totalDurationMs)
    }
    
    @Test
    fun `observe emits updates reactively`() = runTest {
        // Test Flow reactivity
        // ...
    }
    
    // ... more tests
}
```

---

## 📅 Zeitplan (Empfehlung)

### Woche 1 (aktuell)
- ✅ PR #647 schließen
- 🏗️ `infra/data-nx` module setup
- 🎯 Implement Priority 1 repositories (WorkUserState, Work)
- ✅ Write integration tests

### Woche 2
- 🎯 Implement Priority 2 repositories (SourceRef, Variant)
- 🎯 Implement Priority 3 repositories (restliche 18)
- ✅ Full test coverage
- ✅ Hilt DI configuration complete

### Woche 3
- ✅ Phase 1.2 acceptance criteria validation
- 📋 Prepare für Phase 2 (Ingest Path)

---

## ✅ Phase 1 Acceptance Criteria

### Current Status

**Phase 1.1 (Interface Definition):**
- ✅ 22 Repository Interfaces erstellt
- ✅ Domain Models definiert
- ✅ Keine circular dependencies
- ✅ PLATIN-compliant architecture

**Phase 1.2 (Implementation) - TODO:**
- ❌ `infra/data-nx` module existiert
- ❌ Alle 22 Repositories implementiert
- ❌ Entity ↔ Domain Mapper vorhanden
- ❌ Integration Tests geschrieben (>80% coverage)
- ❌ Hilt DI bindings konfiguriert
- ❌ ObjectBox store bootet cleanly
- ❌ Uniqueness constraints validiert
- ❌ Kein UI-Code greift direkt auf BoxStore zu (Detekt enforced)

---

## 🎓 Lessons Learned

### Was gut lief (PR #648):
1. ✅ Klare Interface-Definition ohne Implementation-Details
2. ✅ Domain-first approach (keine ObjectBox deps in core/model)
3. ✅ Alle Review-Findings systematisch addressed
4. ✅ PLATIN standards eingehalten

### Was vermieden werden sollte:
1. ❌ Duplicate PRs (wie #647 vs #648)
2. ❌ Zu lange PR-Branches ohne rebase
3. ❌ Implementation und Interface Definition in einem PR

### Best Practices für Phase 1.2:
1. ✅ **Incremental delivery:** 1-2 Repositories pro PR
2. ✅ **Test-first:** Integration tests vor Implementation
3. ✅ **Pattern once, repeat:** Erstes Repository als Template
4. ✅ **Early validation:** Regelmäßig kompilieren und testen

---

## 📚 Referenzen

- **Parent Issue:** #621 (OBX PLATIN Refactor)
- **Roadmap:** `docs/v2/OBX_PLATIN_REFACTOR_ROADMAP.md`
- **Contract:** `contracts/NX_SSOT_CONTRACT.md`
- **PR #648:** https://github.com/karlokarate/FishIT-Player/pull/648
- **PR #647:** https://github.com/karlokarate/FishIT-Player/pull/647

---

## 🤝 Empfehlungen für Agents/Contributors

### Bei Phase 1.2 Implementation:

1. **Start with mapper first:**
   ```kotlin
   NX_WorkUserState ↔ WorkUserState
   // Einfach zu testen, keine dependencies
   ```

2. **Then implement repository:**
   ```kotlin
   WorkUserStateRepositoryImpl
   // Nutzt mapper, fokussiert auf BoxStore interaction
   ```

3. **Write tests:**
   ```kotlin
   WorkUserStateRepositoryTest
   // Validiert CRUD + Flow reactivity
   ```

4. **Hilt binding:**
   ```kotlin
   @Binds in NxDataModule
   // Macht repository verfügbar für DI
   ```

5. **Repeat für nächstes Repository**

### Kritische Punkte:

- ⚠️ **Uniqueness constraints:** Testen dass (profileKey, workKey) unique ist
- ⚠️ **Flow reactivity:** ObjectBox Flows richtig wrappen
- ⚠️ **Timestamps:** lastUpdatedAtMs immer bei writes setzen
- ⚠️ **CloudSyncState:** Bei jedem write auf DIRTY setzen
- ⚠️ **Device ID:** lastUpdatedByDeviceId aus DeviceInfo holen

---

## 🎯 Fazit

**Wo stehen wir?**
- Phase 1.1 (Interface Definition): ✅ **COMPLETE** via PR #648
- Phase 1.2 (Repository Implementation): ⏳ **NOT STARTED**

**Was ist der nächste kritische Schritt?**
1. PR #647 schließen als duplicate
2. `infra/data-nx` module setup
3. Implement 22 Repository Implementations
4. Write integration tests
5. Validate acceptance criteria

**Wie lange dauert das?**
- Geschätzt: 5-7 Tage (gemäß Roadmap)
- Mit incrementellen PRs: 2-3 Wochen realistisch

**Wer kann helfen?**
- Pattern-Repos first (WorkUserState, Work)
- Parallel development möglich (verschiedene Repos)
- Code review nach jedem Repository-PR

**Risiken:**
- 🔴 Keine: Architecture ist solid, Pattern ist etabliert
- 🟡 Zeit: 22 Repositories sind viel, aber wiederholbar
- 🟢 Quality: Integration tests catchen Fehler früh

---

**Letzte Aktualisierung:** 2026-01-12  
**Status:** Ready for Phase 1.2  
**Next Review:** Nach erstem Repository-PR

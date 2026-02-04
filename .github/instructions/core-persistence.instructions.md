---
applyTo: 
  - core/persistence/**
---

# 🏆 PLATIN Instructions:  core/persistence

> **PLATIN STANDARD** - ObjectBox entities and store infrastructure.
>
> **Purpose:** Provides ObjectBox entities, store setup, converters, and reactive Flow extensions.
> This module contains the database schema and low-level persistence utilities.

---

## 🔴 ABSOLUTE HARD RULES

### 1. ObjectBox Entities Only
```kotlin
// ✅ ALLOWED
@Entity
data class ObxCanonicalMedia(
    @Id var id: Long = 0,
    @Unique @Index var canonicalKey: String = "",
    ... 
)

@Entity
data class ObxMediaSourceRef(...)
@Entity
data class ObxScreenTimeEntry(...)

// ❌ FORBIDDEN
class ObxCanonicalMediaRepository :  CanonicalMediaRepository { ... }
// Repository implementations → infra/data-* layer!
```

### 2. Allowed Dependencies
```kotlin
// ✅ ALLOWED
import io.objectbox.*
import io.objectbox.annotation.*
import io.objectbox.kotlin.*
import io.objectbox.query.*
import com.fishit.player.core.model.*           // Core model types
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.*                   // For converters

// ❌ FORBIDDEN
import com.fishit.player.pipeline.*              // Pipeline
import com.fishit.player.infra.transport.*       // Transport
import androidx.compose.*                         // UI
import org.drinkless.td.*                         // TDLib
```

### 3. Entity Naming Convention
```kotlin
// ✅ CORRECT:  Obx* prefix for all entities
@Entity data class ObxCanonicalMedia(...)
@Entity data class ObxMediaSourceRef(...)
@Entity data class ObxVod(...)
@Entity data class ObxSeries(...)
@Entity data class ObxEpisode(...)
@Entity data class ObxLiveChannel(...)
@Entity data class ObxTelegramMessage(...)
@Entity data class ObxScreenTimeEntry(...)

// ❌ FORBIDDEN:  Non-prefixed entities
@Entity data class CanonicalMedia(...)  // Wrong!
```

---

## 📋 Module Contents

### ObjectBox Entities (obx/ package)
```kotlin
// Canonical media system
ObxCanonicalMedia       // Unique media works (movies, episodes)
ObxMediaSourceRef       // Links canonical → pipeline sources
ObxResumePosition       // Resume positions per profile

// Xtream content
ObxVod, ObxSeries, ObxEpisode, ObxLiveChannel

// Telegram content
ObxTelegramMessage, ObxTelegramChat

// Profile/Kids system
ObxScreenTimeEntry      // Kids screen time tracking
```

### PropertyConverters (obx/converter/ package)
```kotlin
class ImageRefConverter :  PropertyConverter<ImageRef?, String?>
class MediaTypeConverter : PropertyConverter<MediaType, String>
class PipelineIdTagConverter : PropertyConverter<PipelineIdTag, String>
// Converters serialize complex types to/from ObjectBox storage
```

### Reactive Extensions (ObjectBoxFlow.kt)
```kotlin
object ObjectBoxFlow {
    /**
     * Convert ObjectBox Query to lifecycle-safe Flow. 
     * Re-queries on each change notification.
     */
    fun <T> Query<T>.asFlow(): Flow<List<T>>
    fun <T> Query<T>.asFlowSingle(): Flow<T?>
}

// ⚠️ CRITICAL: DataObserver is a change TRIGGER only!
// Must call query.find() inside callback to get actual data. 
```

### Store Singleton (ObxStore.kt)
```kotlin
object ObxStore {
    fun get(context: Context): BoxStore  // Thread-safe lazy init
}
```

### Hilt Module (di/ObxStoreModule.kt)
```kotlin
@Module @InstallIn(SingletonComponent::class)
object ObxStoreModule {
    @Provides @Singleton
    fun provideBoxStore(@ApplicationContext context: Context): BoxStore
}
```

### Debug Inspector (inspector/ package)
```kotlin
interface ObxDatabaseInspector {
    suspend fun listEntityTypes(): List<DbEntityTypeInfo>
    suspend fun getEntity(entityTypeId: String, id: Long): DbEntityDump?
    suspend fun updateFields(...): DbUpdateResult
}
// ⚠️ Debug only - not for production flows!
```

---

## ⚠️ Critical ObjectBox Patterns

### Correct Reactive Flow
```kotlin
// ✅ CORRECT:  Re-query on change trigger
query.subscribe().observer { _ ->
    val updated = query.find()  // Re-query! 
    trySend(updated)
}

// ❌ WRONG:  Expecting data in observer
query.subscribe().observer { data ->
    trySend(data)  // data is NOT the updated list!
}
```

### Correct Entity Relationships
```kotlin
@Entity
data class ObxCanonicalMedia(
    @Id var id: Long = 0,
    @Backlink(to = "canonicalMedia")
    lateinit var sourceRefs: ToMany<ObxMediaSourceRef>,
)

@Entity
data class ObxMediaSourceRef(
    @Id var id: Long = 0,
    lateinit var canonicalMedia: ToOne<ObxCanonicalMedia>,
)
```

---

## 📐 Architecture Position

```
core/model (types)
      ↓
core/persistence (entities, store) ← YOU ARE HERE
      ↓
infra/data-* (repository implementations)
      ↓
feature/* (UI consumption via repositories)
```

---

## ✅ PLATIN Checklist

- [ ] All entities use `Obx*` prefix
- [ ] Entities are data-only (no business logic)
- [ ] PropertyConverters handle all complex types
- [ ] ObjectBoxFlow uses re-query pattern (not DataObserver data)
- [ ] No repository implementations (→ infra/data-*)
- [ ] No pipeline or transport imports
- [ ] No UI imports
- [ ] Hilt module provides BoxStore singleton
- [ ] Debug inspector is hidden behind debug navigation

---

## 📚 Reference Documents

1. `/docs/v2/OBJECTBOX_REACTIVE_PATTERNS.md` - Flow patterns
2. `/contracts/MEDIA_NORMALIZATION_CONTRACT.md` - Canonical entities (AUTHORITATIVE)
3. `/AGENTS.md` - Section 4.5 (Layer Boundary Enforcement)
4. ObjectBox Kotlin documentation

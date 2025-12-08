# Feature System Target Model (Zielbild)

Version: 1.0  
Status: DRAFT  
Scope: v2 Feature System architecture specification

---

## 1. Overview

The v2 Feature System provides a **centralized, type-safe mechanism** for:

1. **Declaring capabilities** – What features exist in the app
2. **Discovering features** – Which modules provide which capabilities
3. **Querying availability** – Runtime checks for feature support
4. **Documenting ownership** – Clear responsibility for each feature

This enables:

- Clean dependency graphs (features depend on APIs, not implementations)
- Runtime feature toggling (flags, A/B tests, user settings)
- Clear documentation of what each module provides
- Test isolation via feature stubs

---

## 2. Core Concepts

### 2.1 FeatureId

A **value class** representing a unique, dot-separated feature identifier.

```kotlin
@JvmInline
value class FeatureId(val value: String)
```

**Naming Convention:** `<domain>.<subdomain>.<capability>`

Examples:

- `media.canonical_model`
- `telegram.full_history_streaming`
- `ui.screen.home`

### 2.2 FeatureScope

Defines the **lifecycle scope** of a feature:

```kotlin
enum class FeatureScope {
    APP,            // App-wide, lives for entire app lifecycle
    PIPELINE,       // Pipeline-scoped, lives per data source
    PLAYER_SESSION, // Player-scoped, lives per playback session
    UI_SCREEN,      // Screen-scoped, lives per navigation destination
    REQUEST         // Request-scoped, lives per single operation
}
```

### 2.3 FeatureOwner

Declares the **module and optional team** responsible for a feature:

```kotlin
data class FeatureOwner(
    val moduleName: String,
    val team: String? = null,
)
```

Examples:

- `FeatureOwner("pipeline-telegram")`
- `FeatureOwner("infra:cache", team = "platform")`

### 2.4 FeatureProvider

Interface implemented by each feature to declare its identity:

```kotlin
interface FeatureProvider {
    val featureId: FeatureId
    val scope: FeatureScope
    val owner: FeatureOwner
}
```

Each module exposes its feature providers as DI-injectable singletons.

### 2.5 FeatureRegistry

Central registry for discovering and querying features:

```kotlin
interface FeatureRegistry {
    fun isSupported(featureId: FeatureId): Boolean
    fun providersFor(featureId: FeatureId): List<FeatureProvider>
    fun ownerOf(featureId: FeatureId): FeatureOwner?
}
```

The `AppFeatureRegistry` implementation collects all `FeatureProvider` instances via DI multibindings.

---

## Current Implementation Status

**Last Updated:** Phase 1.5 (Dec 2025)

### Implemented Components

| Component | Module | Status |
|-----------|--------|--------|
| `FeatureId` | `core:feature-api` | ✅ Done |
| `FeatureScope` | `core:feature-api` | ✅ Done |
| `FeatureOwner` | `core:feature-api` | ✅ Done |
| `FeatureProvider` | `core:feature-api` | ✅ Done |
| `FeatureRegistry` | `core:feature-api` | ✅ Done |
| `Features.kt` catalog | `core:feature-api` | ✅ Done |
| `AppFeatureRegistry` | `app-v2` | ✅ Done |
| `FeatureModule` (Hilt DI) | `app-v2` | ✅ Done |

### Registered Feature Providers

| Provider | FeatureId | Module | Status |
|----------|-----------|--------|--------|
| `TelegramFullHistoryFeatureProvider` | `telegram.full_history_streaming` | `pipeline:telegram` | ✅ Done |
| `TelegramLazyThumbnailsFeatureProvider` | `telegram.lazy_thumbnails` | `pipeline:telegram` | ✅ Done |

### DI Integration

- **Hilt multibindings**: FeatureProviders are collected via `@IntoSet` bindings
- **TelegramFeatureModule**: Binds Telegram providers into the global `Set<FeatureProvider>`
- **TelegramMediaViewModel**: First ViewModel to inject and use `FeatureRegistry`

### Unit Tests

| Test Class | Location | Coverage |
|------------|----------|----------|
| `AppFeatureRegistryTest` | `app-v2/src/test/` | `isSupported`, `providersFor`, `ownerOf`, `featureCount`, `allFeatureIds` |
| `TelegramFeatureProviderTest` | `pipeline/telegram/src/test/` | Provider properties (featureId, scope, owner) |

### Next Steps

- Add more feature providers for other pipelines (Xtream, IO, Audiobook)
- Add UI screen feature providers
- Integrate feature checks in actual UI components
- Add feature flag integration layer

---

## 3. Feature ID Catalog

### 3.1 Canonical Media (`media.*`)

| FeatureId | Scope | Owner Module | Description |
|-----------|-------|--------------|-------------|
| `media.canonical_model` | APP | `core:model` | Canonical media model (MediaItem, Episode, etc.) |
| `media.normalize` | APP | `core:metadata-normalizer` | RawMediaMetadata → normalized MediaItem |
| `media.resolve.tmdb` | REQUEST | `core:metadata-normalizer` | TMDB metadata resolution |

### 3.2 Telegram (`telegram.*`)

| FeatureId | Scope | Owner Module | Description |
|-----------|-------|--------------|-------------|
| `telegram.full_history_streaming` | PIPELINE | `pipeline:telegram` | Complete chat history scanning |
| `telegram.lazy_thumbnails` | PIPELINE | `pipeline:telegram` | On-demand thumbnail loading |

### 3.3 Xtream (`xtream.*`)

| FeatureId | Scope | Owner Module | Description |
|-----------|-------|--------------|-------------|
| `xtream.live_streaming` | PIPELINE | `pipeline:xtream` | Live TV streaming |
| `xtream.vod_playback` | PIPELINE | `pipeline:xtream` | VOD playback |
| `xtream.series_metadata` | PIPELINE | `pipeline:xtream` | Series/episode metadata |

### 3.4 App / Infra (`app.*`, `infra.*`, `settings.*`)

| FeatureId | Scope | Owner Module | Description |
|-----------|-------|--------------|-------------|
| `app.cache_management` | APP | `infra:cache` | App-wide cache management |
| `infra.logging.unified` | APP | `infra:logging` | Unified logging facade |
| `settings.core_single_datastore` | APP | `core:persistence` | Single DataStore for settings |

### 3.5 UI Screens (`ui.screen.*`)

| FeatureId | Scope | Owner Module | Description |
|-----------|-------|--------------|-------------|
| `ui.screen.home` | UI_SCREEN | `feature:home` | Home screen |
| `ui.screen.library` | UI_SCREEN | `feature:library` | Library screen |
| `ui.screen.telegram` | UI_SCREEN | `feature:telegram-media` | Telegram media screen |
| `ui.screen.settings` | UI_SCREEN | `feature:settings` | Settings screen |

---

## 4. Module Ownership Map

### 4.1 Core Modules

| Module | Features |
|--------|----------|
| `core:model` | `media.canonical_model` |
| `core:metadata-normalizer` | `media.normalize`, `media.resolve.tmdb` |
| `core:persistence` | `settings.core_single_datastore` |
| `core:feature-api` | Feature system API (no features, only contracts) |

### 4.2 Infrastructure Modules

| Module | Features |
|--------|----------|
| `infra:logging` | `infra.logging.unified` |
| `infra:cache` | `app.cache_management` |
| `infra:imageloader` | (future: `infra.imageloader.coil`) |
| `infra:ffmpegkit` | (future: `infra.ffmpegkit.transcoding`) |

### 4.3 Pipeline Modules

| Module | Features |
|--------|----------|
| `pipeline:telegram` | `telegram.full_history_streaming`, `telegram.lazy_thumbnails` |
| `pipeline:xtream` | `xtream.live_streaming`, `xtream.vod_playback`, `xtream.series_metadata` |
| `pipeline:audiobook` | (future: `audiobook.chapter_detection`) |
| `pipeline:io` | (future: `io.local_file_scanning`) |

### 4.4 Player & Playback Modules

| Module | Features |
|--------|----------|
| `player:internal` | (future: `player.sip`, `player.resume_tracking`) |
| `playback:domain` | (future: `playback.session_management`) |

### 4.5 Feature (UI) Modules

| Module | Features |
|--------|----------|
| `feature:home` | `ui.screen.home` |
| `feature:library` | `ui.screen.library` |
| `feature:telegram-media` | `ui.screen.telegram` |
| `feature:settings` | `ui.screen.settings` |
| `feature:live` | (future: `ui.screen.live`) |
| `feature:audiobooks` | (future: `ui.screen.audiobooks`) |

### 4.6 App Shell

| Module | Features |
|--------|----------|
| `app-v2` | `AppFeatureRegistry` (registry, not a feature itself) |

---

## 5. FeatureProvider Location Rules

### 5.1 Directory Structure

Each module exposes its feature providers in a `feature/` package:

```
<module>/src/main/kotlin/com/fishit/player/<module-path>/feature/
    └── <FeatureName>FeatureProvider.kt
```

### 5.2 Examples

```
pipeline/telegram/src/main/kotlin/com/fishit/player/pipeline/telegram/feature/
    ├── TelegramFullHistoryFeatureProvider.kt
    └── TelegramLazyThumbnailsFeatureProvider.kt

infra/cache/src/main/kotlin/com/fishit/player/infra/cache/feature/
    └── AppCacheManagementFeatureProvider.kt

feature/home/src/main/kotlin/com/fishit/player/feature/home/feature/
    └── HomeScreenFeatureProvider.kt
```

### 5.3 DI Binding

Each `FeatureProvider` is bound via DI multibinding into `Set<FeatureProvider>`, which is collected by `AppFeatureRegistry`.

---

## 6. Feature Contract Documentation

### 6.1 Location

Each feature MUST have a contract document under:

```
docs/v2/features/<category>/FEATURE_<featureId>.md
```

### 6.2 Examples

- `docs/v2/features/telegram/FEATURE_telegram.full_history_streaming.md`
- `docs/v2/features/app/FEATURE_app.cache_management.md`
- `docs/v2/features/ui/FEATURE_ui.screen.home.md`

### 6.3 Contract Template

```markdown
# Feature: <featureId>

## Metadata
- **ID:** `<featureId>`
- **Scope:** <FeatureScope>
- **Owner:** `<module-name>`

## Dependencies
- List of FeatureIds this feature depends on

## Guarantees
- What this feature promises to deliver
- SLAs, invariants, contracts

## Failure Modes
- Known failure scenarios
- Fallback behavior
- Error codes

## Logging & Telemetry
- Log tags
- Telemetry events emitted

## Test Requirements
- Minimum test coverage
- Required test scenarios
```

---

## 7. AppFeatureRegistry

### 7.1 Implementation

```kotlin
class AppFeatureRegistry(
    providers: Set<@JvmSuppressWildcards FeatureProvider>
) : FeatureRegistry {

    private val providersById: Map<FeatureId, List<FeatureProvider>> =
        providers.groupBy { it.featureId }

    private val ownersById: Map<FeatureId, FeatureOwner> =
        providers.associate { it.featureId to it.owner }

    override fun isSupported(featureId: FeatureId): Boolean =
        providersById.containsKey(featureId)

    override fun providersFor(featureId: FeatureId): List<FeatureProvider> =
        providersById[featureId].orEmpty()

    override fun ownerOf(featureId: FeatureId): FeatureOwner? =
        ownersById[featureId]
}
```

### 7.2 DI Wiring

- `AppFeatureRegistry` is scoped as an **APP singleton**.
- It receives `Set<FeatureProvider>` via DI multibindings.
- **No global mutable singleton** – managed entirely through DI.

---

## 8. Usage Patterns

### 8.1 Checking Feature Availability

```kotlin
@Inject lateinit var featureRegistry: FeatureRegistry

fun showTelegramSection() {
    if (featureRegistry.isSupported(TelegramFeatures.FULL_HISTORY_STREAMING)) {
        // Show Telegram UI
    }
}
```

### 8.2 Getting Feature Owner (for logging/debugging)

```kotlin
val owner = featureRegistry.ownerOf(AppFeatures.CACHE_MANAGEMENT)
logger.d("Cache management owned by: ${owner?.moduleName}")
```

### 8.3 Feature Flags Integration

The feature system can integrate with remote feature flags:

```kotlin
class FeatureFlaggedRegistry(
    private val delegate: FeatureRegistry,
    private val featureFlags: FeatureFlagService,
) : FeatureRegistry {

    override fun isSupported(featureId: FeatureId): Boolean =
        delegate.isSupported(featureId) && featureFlags.isEnabled(featureId.value)
    
    // ... delegate other methods
}
```

---

## 9. Migration Strategy

### 9.1 Phase 1 (Current)

- Create `core:feature-api` with core types
- Implement `AppFeatureRegistry`
- Add first 1-2 `FeatureProvider` implementations
- Document contracts

### 9.2 Phase 2

- Add providers for all pipeline modules
- Add providers for all feature (UI) modules
- Complete feature contract docs

### 9.3 Phase 3

- Integrate with remote feature flags
- Add feature-based analytics
- Build feature dependency graph tooling

---

## 10. Related Documents

| Document | Purpose |
|----------|---------|
| [V2_PORTAL.md](../../../V2_PORTAL.md) | v2 entry point |
| [ARCHITECTURE_OVERVIEW_V2.md](../ARCHITECTURE_OVERVIEW_V2.md) | v2 architecture overview |
| [CANONICAL_MEDIA_SYSTEM.md](../CANONICAL_MEDIA_SYSTEM.md) | Canonical media model |
| [MEDIA_NORMALIZATION_CONTRACT.md](../MEDIA_NORMALIZATION_CONTRACT.md) | Normalizer contract |


# Task: v2 Naming Normalization & Glossary Generation

Repository: FishIT-Player (v2 architecture branch)  
Scope: `app-v2`, `feature/*`, `core/*`, `pipeline/*`, `infra/*`, `playback/*`, `player/*`  
Ignore: `legacy/*`

---

## 1. High-Level Goal

Normalize naming across the v2 codebase so that:

1. The vocabulary for **App Features**, **Pipelines**, **Pipeline Capabilities**, **Core models**, **Playback**, and **Infra** is consistent and predictable.
2. Equivalent responsibilities in different pipelines (Telegram, Xtream, IO, Audiobook) follow the **same module and package naming pattern**.
3. The word **"Feature"** is not misused inside pipeline modules in a way that conflicts with App-level features.
4. All imports, references, and documentation are updated accordingly.
5. A **single, machine-readable glossary document** is generated under `docs/v2/` and kept in sync with the code.

---

## 2. Canonical Vocabulary (MUST be enforced)

Use these terms as the single source of truth:

### 2.1 Product & App level

- **AppFeature**
  - Meaning: user-facing product feature, surfaced via UI.
  - Examples: Home, Library, Live TV, Telegram Media, Settings, Audiobooks.
  - Location:
    - Modules: `feature/*`
    - App: `app-v2/src/main/java/com/fishit/player/v2/feature/AppFeatureRegistry.kt`
  - Rule: When we say "App feature" in docs or code comments, we mean an `AppFeature`.

- **FeatureId**
  - Meaning: canonical ID in `:core:feature-api` representing a capability.
  - Examples:
    - `FeatureId("ui.screen.home")`
    - `FeatureId("ui.screen.telegram")`
    - `FeatureId("telegram.full_history_streaming")`
  - Location: `core/feature-api/src/main/kotlin/com/fishit/player/core/feature/FeatureId.kt`

- **FeatureProvider / FeatureRegistry / FeatureScope / Features**
  - Meaning: central feature system as already implemented in `:core:feature-api`.
  - These stay conceptually as they are, but their uses must align with the vocabulary in this document.

### 2.2 Pipelines

- **Pipeline**
  - Meaning: integration with an external or internal content source.
  - Modules:
    - `pipeline/telegram`
    - `pipeline/xtream`
    - `pipeline/io`
    - `pipeline/audiobook`

- **PipelineCapability**
  - Meaning: a concrete capability provided by a pipeline, typically represented as a `FeatureId` with a specific domain.
  - Examples (existing):
    - `TelegramFeatures.FULL_HISTORY_STREAMING` → `FeatureId("telegram.full_history_streaming")`
    - `TelegramFeatures.LAZY_THUMBNAILS` → `FeatureId("telegram.lazy_thumbnails")`
  - Rule:
    - In **pipeline code**, use the term **"capability"** in package and class names.
    - The underlying type can still be `FeatureId` and `FeatureProvider` from `:core:feature-api`.

- **Pipeline module structure** (canonical pattern):
  - `pipeline/<name>/adapter` → glue to the central core model / infrastructure.
  - `pipeline/<name>/catalog` → pipeline-specific catalog scanning and event-based export.
  - `pipeline/<name>/mapper` → mapping from pipeline DTOs to `RawMediaMetadata`.
  - `pipeline/<name>/model` → pipeline domain models (DTOs, summaries, etc.).
  - `pipeline/<name>/debug` → debug-only services for that pipeline.
  - `pipeline/<name>/capability` → capability providers that integrate with the feature system.

### 2.3 Core models & normalization

- **RawMediaMetadata**
  - Meaning: pipeline-specific raw metadata, before any normalization.
  - Location: `core/model/src/main/java/com/fishit/player/core/model/RawMediaMetadata.kt`

- **NormalizedMediaMetadata**
  - Meaning: canonical, pipeline-agnostic metadata representation.
  - Location: `core/model/src/main/java/com/fishit/player/core/model/NormalizedMediaMetadata.kt`

- **MetadataNormalizer / TmdbMetadataResolver / SceneNameParser**
  - Location: `core/metadata-normalizer/*`
  - Pipelines MUST NOT re-implement normalization or TMDB lookup; they only produce `RawMediaMetadata`.

### 2.4 Playback & Player

- **PlayerEngine / InternalPlayer**
  - Location: `player/internal/*`

- **PlaybackDomain**
  - Location: `playback/domain/*`

- **Pipeline-specific playback integration**
  - Location:
    - `playback/telegram/*`
    - `playback/xtream/*`

### 2.5 Infrastructure

- **Transport**
  - Location:
    - `infra/transport-telegram`
    - `infra/transport-xtream`

- **Data repositories**
  - Location:
    - `infra/data-telegram`
    - `infra/data-xtream`

- **Logging**
  - Location:
    - `infra/logging`

---

## 3. Known Naming Issues (MUST be fixed)

### 3.1 Telegram pipeline `feature` package

Current structure:

```text
pipeline/telegram/src/main/java/com/fishit/player/pipeline/telegram/feature/
  TelegramFullHistoryFeatureProvider.kt
  TelegramLazyThumbnailsFeatureProvider.kt
  di/TelegramFeatureModule.kt
  (and tests: TelegramFeatureProviderTest.kt)
````

These are **pipeline capabilities**, not AppFeatures.

**Required changes:**

1. **Package rename**

   - From: `com.fishit.player.pipeline.telegram.feature`
   - To:   `com.fishit.player.pipeline.telegram.capability`

   And similarly for the `di` subpackage:

   - From: `com.fishit.player.pipeline.telegram.feature.di`
   - To:   `com.fishit.player.pipeline.telegram.capability.di`

2. **Class renames**

   - `TelegramFullHistoryFeatureProvider` → `TelegramFullHistoryCapabilityProvider`
   - `TelegramLazyThumbnailsFeatureProvider` → `TelegramLazyThumbnailsCapabilityProvider`
   - `TelegramFeatureModule` → `TelegramCapabilityModule`
   - `TelegramFeatureProviderTest` → `TelegramCapabilityProviderTest`

3. **Comment / KDoc updates**

   - Replace phrasing like:

     > "Feature provider for Telegram full history streaming."
   - With something like:

     > "Pipeline capability provider for Telegram full history streaming."

4. **Update all usages**

   - Update all imports and references to the renamed classes and packages.
   - Update all references in:

     - `docs/v2/architecture/FEATURE_SYSTEM_TARGET_MODEL.md`
     - `docs/v2/Zielbild.md`
     - `ROADMAP.md`
     - Any other docs mentioning `TelegramFullHistoryFeatureProvider` or `TelegramLazyThumbnailsFeatureProvider`.

### 3.2 Align Telegram and Xtream mapper naming (Optional, but recommended)

Goal: Make it obvious where **mapping to RawMediaMetadata** happens in each pipeline.

- Telegram:

  - `pipeline/telegram/mapper/TelegramRawMetadataContract.kt`
  - `pipeline/telegram/model/TelegramRawMetadataExtensions.kt`

- Xtream:

  - `pipeline/xtream/catalog/XtreamCatalogMapper.kt`
  - `pipeline/xtream/model/XtreamRawMetadataExtensions.kt`

**Suggested alignment (high-level):**

- Ensure both pipelines use:

  - `pipeline/<name>/mapper/*` for:

    - `*RawMetadataExtensions.kt`
    - `*CatalogMapper.kt` or similar mapping types.
- Keep `catalog/` focused on:

  - API access
  - pagination / event streaming
  - high-level catalog orchestration.

Copilot should:

1. Build an inventory of all mapping-related files in `pipeline/telegram` and `pipeline/xtream`.
2. Propose a minimal set of moves/renames that:

   - Remove ambiguity about where mapping lives.
   - Keep public APIs stable where possible.
3. Apply the changes and fix imports.

This is less critical than the Telegram `feature` package renaming, but still important for long-term clarity.

---

## 4. File-to-Vocabulary Mapping (Automated Analysis)

Before performing large refactors, create a mapping table so we can see where each file lands in the vocabulary.

### 4.1 Task

For all Kotlin/Java source files in these modules:

- `app-v2`
- `feature/*`
- `core/*`
- `pipeline/*`
- `infra/*`
- `playback/*`
- `player/*`

Generate a Markdown file:

`docs/v2/NAMING_INVENTORY_v2.md`

with a table containing:

- `file_path` (relative to repo root)
- `module` (e.g. `:pipeline:telegram`, `:core:model`)
- `package_name`
- `short_role_description` (1–2 sentences)
- `glossary_category` (one of: `AppFeature`, `Pipeline`, `PipelineCapability`, `CoreModel`, `MetadataNormalizer`, `Playback`, `Player`, `InfraTransport`, `InfraData`, `Logging`, `Tooling`, `Unknown`)
- `rename_suggestion` (empty if none needed; otherwise a concrete suggestion, e.g. `TelegramFullHistoryCapabilityProvider`)

### 4.2 Rules

- Use the vocabulary in Section 2 as the source of truth.
- Prefer **specific** categories over generic ones.
- If you are unsure, set `glossary_category = "Unknown"` and document why in `short_role_description`.
- Highlight all filenames and packages where:

  - The word `Feature` is used under `pipeline/*`.
  - The module name does not match its purpose (e.g. if a module behaves like `InfraTransport` but is named differently).

---

## 5. Apply Renaming & Fix References

After the mapping is generated and the Telegram `feature → capability` rename is planned, perform the refactor.

### 5.1 Requirements

- Use safe, IDE-like refactorings (search + replace is not enough).
- Ensure:

  - All imports compile.
  - All DI modules (Hilt) are updated.
  - All test files are updated.
- Update all affected Gradle `package` declarations and manifest entries if needed.

### 5.2 Documentation updates

Update all docs referencing renamed classes or packages:

- `docs/v2/architecture/FEATURE_SYSTEM_TARGET_MODEL.md`
- `docs/v2/features/telegram/FEATURE_telegram.full_history_streaming.md`
- `docs/v2/Zielbild.md`
- `ROADMAP.md`
- Any other relevant files under `docs/v2` and root `*.md`.

When referring to pipeline-side functionality in documentation:

- Use the term **"Pipeline capability"** instead of "Feature" unless explicitly referring to the generic feature system.

---

## 6. Generate and Wire a Central Glossary

Create a new document:

`docs/v2/GLOSSARY_v2_naming_and_modules.md`

with the following structure:

1. **Vocabulary**

   - Reuse and refine the definitions from Section 2 of this task.
   - For each term (AppFeature, Pipeline, PipelineCapability, RawMediaMetadata, NormalizedMediaMetadata, PlayerEngine, etc.), provide:

     - A 1–3 sentence description.
     - Links to the main types and modules.

2. **Module taxonomy**

   - List all Gradle modules from `settings.gradle.kts`.
   - For each module:

     - Module path, e.g. `:pipeline:telegram`
     - Module type (`AppFeature`, `Pipeline`, `Core`, `InfraTransport`, `InfraData`, `Playback`, `Player`, `Tool`).
     - One-line responsibility summary.
     - Key entrypoints (e.g. main public classes).

3. **Pipeline capability overview**

   - For each pipeline (Telegram, Xtream, IO, Audiobook):

     - List all `FeatureId`s with domain prefixes for that pipeline.
     - Name them consistently as **capabilities** in the text.
     - Map each capability to:

       - its provider class (now `*CapabilityProvider`),
       - its scope (`FeatureScope.PIPELINE`),
       - and its owner (e.g. `FeatureOwner("pipeline:telegram")`).

4. **App feature overview**

   - List all App features (`feature/*` modules, `AppFeatureRegistry` entries).
   - Show:

     - Associated `FeatureId`s for screens (e.g. `ui.screen.telegram`).
     - Owning modules and main view models.

Ensure that the glossary is kept **synchronized** with the code after refactoring.

---

## 7. Quality & Tooling

While performing this task, also:

1. Add or update **Detekt** rules (latest stable version) to:

   - Flag usages of `Feature` in package names under `pipeline/*`.
   - Optionally enforce that:

     - `:feature/*` modules do not define `*Pipeline*` types.
     - `:pipeline/*` modules do not define `*Screen` classes.

2. Integrate with existing quality tools (e.g. `scripts/test-quality-tools.sh`) so the naming rules are checked in CI.

3. Prefer well-known, up-to-date libraries and plugins (e.g., latest Detekt, Ktlint, and Android/Kotlin Gradle plugin versions that are compatible with the current setup).

---

## 8. Deliverables

- Updated codebase with:

  - Renamed Telegram capability package and classes.
  - No confusing use of `Feature` inside pipeline implementations.
- `docs/v2/NAMING_INVENTORY_v2.md` with the full file-to-vocabulary mapping.
- `docs/v2/GLOSSARY_v2_naming_and_modules.md` as the central naming glossary.
- Updated Detekt configuration with naming rules that prevent regressions.

```

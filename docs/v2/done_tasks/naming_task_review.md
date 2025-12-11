
# Task: v2 Naming & Glossary Hardening (Authoritative Cleanup)

Repository: FishIT-Player (v2 architecture branch)  
Scope: `app-v2`, `feature/*`, `core/*`, `pipeline/*`, `infra/*`, `playback/*`, `player/*`  
Ignore: `legacy/*`

Goal: Make `GLOSSARY_v2_naming_and_modules.md` and `NAMING_INVENTORY_v2.md` a **fully consistent, authoritative source of truth** and bring the Xtream pipeline structure in line with Telegram. No optional branches – apply all changes described here.

---

## Step 1 – Update Glossary Core Terms

Edit: `docs/v2/GLOSSARY_v2_naming_and_modules.md`

1. **Update `FeatureId` definition**

   - Current: describes `FeatureId` as identifier for an AppFeature only.
   - Change it to:

   ```markdown
   **FeatureId** | A unique identifier in the feature system. Used for both AppFeatures and PipelineCapabilities. Defined as `@JvmInline value class FeatureId(val id: String)` in `core/feature-api`. | CoreModel | `FeatureId("ui.screen.home")`, `FeatureId("telegram.full_history")`, `FeatureId("xtream.live")`
````

* Ensure all examples include at least:

  * one UI/AppFeature ID (e.g. `ui.screen.home`),
  * one Telegram capability ID (e.g. `telegram.full_history`),
  * one Xtream capability ID (placeholder is fine for now, but keep the pattern).

2. **Update `FeatureRegistry` definition**

   * Currently marked with scope `AppFeature`.
   * Change scope to something like `CoreModel` or `Global`.
   * The definition MUST explicitly say that `FeatureRegistry`:

   ```markdown
   - collects all `FeatureProvider` implementations (both AppFeatures and PipelineCapabilities)
   - provides lookup for `FeatureId` → provider instances
   - has its interface defined in `:core:feature-api`
   - has `AppFeatureRegistry` in `app-v2` as the main application-specific implementation
   ```

3. **Introduce `AppShell` as a first-class term**

   * Add a new row under Core Terms:

   ```markdown
   **AppShell** | The application shell: entry point, main activity, navigation host and global theme setup for v2. Lives in `app-v2` (excluding feature-specific code). | AppShell | `FishItV2Application`, `MainActivity`, `AppNavHost`, `Theme`, `Type`, `AppFeatureRegistry`
   ```

   * This term will be used in the inventory as a category.

4. **Clarify `NormalizedMedia` vs `NormalizedMediaMetadata`**

   * Under Metadata Terms, add a separate entry for `NormalizedMediaMetadata` and refine `NormalizedMedia`:

   ```markdown
   **NormalizedMediaMetadata** | Normalized, pipeline-agnostic metadata describing a media item (title, overview, year, genres, people, etc.) after enrichment (e.g. TMDB). | CoreModel | `NormalizedMediaMetadata` in `core/model`
   **NormalizedMedia** | Aggregated normalized media object that contains `NormalizedMediaMetadata` plus variants, artwork, playback-related information and other derived data. | CoreModel | `NormalizedMedia` in `core/model`
   ```

   * Open both `NormalizedMedia.kt` and `NormalizedMediaMetadata.kt` in `core/model` and adjust the wording if needed so it accurately reflects the actual fields and relationships.
   * Update the `MetadataNormalizer` definition to:

   ```markdown
   **MetadataNormalizer** | Service that transforms `RawMediaMetadata` into normalized representations (`NormalizedMediaMetadata` and/or `NormalizedMedia`) using TMDB and other heuristics. Lives in `core/metadata-normalizer`. | MetadataNormalizer | `MediaMetadataNormalizer`, `TmdbMetadataResolver`
   ```

---

## Step 2 – Fix Module Taxonomy for Xtream

Still editing: `docs/v2/GLOSSARY_v2_naming_and_modules.md`, section **Module Taxonomy**.

1. In the `pipeline/xtream` subtree, ensure the structure explicitly includes a `mapper/` directory, mirroring Telegram:

   ```text
   pipeline/                        # Content ingestion pipelines
   ├── telegram/
   │   ├── adapter/
   │   ├── capability/
   │   ├── catalog/
   │   ├── debug/
   │   ├── mapper/
   │   └── model/
   └── xtream/
       ├── adapter/
       ├── catalog/
       ├── debug/
       ├── mapper/      # <-- add this
       └── model/
   ```

2. Add a short note:

   ```markdown
   Xtream uses the same structural pattern as Telegram: `catalog/` for catalog orchestration and IO, `mapper/` for mapping DTOs into `RawMediaMetadata`.
   ```

---

## Step 3 – Reclassify AppShell in the Naming Inventory

Edit: `docs/v2/NAMING_INVENTORY_v2.md`

1. In the **File-to-Vocabulary Mapping** table, change the `Category` for these entries from `AppFeature` to `AppShell`:

   * `app-v2/src/main/java/com/fishit/player/v2/FishItV2Application.kt`
   * `app-v2/src/main/java/com/fishit/player/v2/MainActivity.kt`
   * `app-v2/src/main/java/com/fishit/player/v2/feature/AppFeatureRegistry.kt`
   * `app-v2/src/main/java/com/fishit/player/v2/feature/di/FeatureModule.kt`
   * `app-v2/src/main/java/com/fishit/player/v2/navigation/AppNavHost.kt`
   * `app-v2/src/main/java/com/fishit/player/v2/ui/theme/Theme.kt`
   * `app-v2/src/main/java/com/fishit/player/v2/ui/theme/Type.kt`
   * `app-v2/src/test/java/com/fishit/player/v2/feature/AppFeatureRegistryTest.kt`

2. **Do not** change `DebugSkeletonScreen.kt` – it should remain categorized as `Tooling`.

3. In the **Summary Statistics** section at the bottom:

   * Add a new row for `AppShell` with the correct count.
   * Decrease the `AppFeature` count accordingly so that:

     * `AppFeature` only counts modules under `feature/*` (and files inside them).
     * No file under `app-v2` is counted as `AppFeature`.

4. Verify that every category used in the inventory (`AppFeature`, `AppShell`, `CoreModel`, `MetadataNormalizer`, `Pipeline`, `PipelineCapability`, `Playback`, `Player`, `InfraTransport`, `InfraData`, `Logging`, `Tooling`) has a matching term defined in the glossary.

---

## Step 4 – Align Xtream Mapper Structure with Telegram

Now refactor the Xtream pipeline code.

1. **Create `mapper` package for Xtream**

   * Create package: `com.fishit.player.pipeline.xtream.mapper`

2. **Move `XtreamCatalogMapper`**

   * File: `pipeline/xtream/src/main/java/com/fishit/player/pipeline/xtream/catalog/XtreamCatalogMapper.kt`
   * Actions:

     * Move it into: `pipeline/xtream/src/main/java/com/fishit/player/pipeline/xtream/mapper/`
     * Update the `package` declaration to `com.fishit.player.pipeline.xtream.mapper`.
     * Fix all imports in the codebase that reference `XtreamCatalogMapper` to point to the new package.

3. **Move `XtreamRawMetadataExtensions`**

   * File: `pipeline/xtream/src/main/java/com/fishit/player/pipeline/xtream/model/XtreamRawMetadataExtensions.kt`
   * Actions:

     * Move it into: `pipeline/xtream/src/main/java/com/fishit/player/pipeline/xtream/mapper/`
     * Update the `package` declaration to `com.fishit.player.pipeline.xtream.mapper`.
     * Fix all imports in the codebase that reference `XtreamRawMetadataExtensions`.

4. **Update naming inventory for Xtream**

   * In `NAMING_INVENTORY_v2.md`, adjust the Xtream pipeline section so that:

     * `XtreamCatalogMapper.kt` appears under `pipeline.xtream.mapper` with `Role: Catalog to RawMediaMetadata mapper`.
     * `XtreamRawMetadataExtensions.kt` appears under `pipeline.xtream.mapper` with `Role: RawMetadata extensions`.
   * Remove them from the old `catalog/` and `model/` listings.

5. **Update docs if needed**

   * Search in `docs/v2/` for `XtreamCatalogMapper` and `XtreamRawMetadataExtensions` and update any package paths mentioned.
   * Confirm that the Module Taxonomy in the glossary matches the actual folder structure.

---

## Step 5 – Regenerate Naming Inventory and Verify Consistency

1. Re-run the script or Gradle task that generated `docs/v2/NAMING_INVENTORY_v2.md` originally.

2. Overwrite the existing `NAMING_INVENTORY_v2.md` with the new output.

3. Verify the following conditions programmatically (or with a simple script):

   * No files under `app-v2` are categorized as `AppFeature` in the inventory.
   * All files under `feature/*` are categorized as `AppFeature`.
   * No files under `pipeline/*` are categorized as `AppFeature` or `AppShell`.
   * Any file under `pipeline/*/capability/` is categorized as `PipelineCapability`.
   * Xtream now has a `mapper` section in the inventory comparable to the Telegram `mapper` section.

4. Update the **Summary Statistics** at the bottom to reflect the new counts.

---

## Step 6 – Enforce Conventions with Static Analysis

Add or update static analysis configuration so the glossary rules cannot silently regress.

1. **Detekt**

   * Ensure the project uses a recent Detekt version (latest stable compatible with the current Kotlin Gradle plugin).
   * In the Detekt config (e.g. `config/detekt.yml`), add custom rules or pattern-based checks:

     * Forbid class names matching `*FeatureProvider` under the `pipeline` package.
     * Forbid packages named `feature` under `pipeline/*`.
     * Forbid `com.chris.m3usuite` packages outside `legacy/*`.
   * Add a Gradle task (e.g. `detekt`) wired into CI (GitHub Actions) so PRs fail when these rules are violated.

2. **Ktlint (or Spotless + ktlint)**

   * Ensure ktlint (or Spotless with ktlint) is configured and run as part of CI.
   * Use it to keep imports and formatting clean after the package moves (especially Xtream `mapper` changes).

3. **Static analysis platform (Qodana or SonarQube/SonarCloud)**

   * Add a basic configuration for one of:

     * JetBrains Qodana, or
     * SonarQube/SonarCloud (depending on what is already in use).
   * Ensure at least one job in CI runs the chosen tool against the v2 branch, focusing on:

     * package structure,
     * dead code,
     * unresolved imports (to catch refactor mistakes),
     * and potential naming anomalies.

4. **Document the quality gates**

   * In `docs/v2/GLOSSARY_v2_naming_and_modules.md` (or a separate `QUALITY_GATES_v2.md`), add a short section that:

     * lists Detekt, ktlint, and the chosen static analysis tool,
     * states that naming conventions from the glossary are enforced via these tools.

---

## Step 7 – Final Sanity Check

1. Manually open both:

   * `docs/v2/GLOSSARY_v2_naming_and_modules.md`
   * `docs/v2/NAMING_INVENTORY_v2.md`

2. Confirm that:

   * Every category used in the inventory has a corresponding term in the glossary (including `AppShell`).
   * All examples in the glossary actually exist in the codebase (update examples if any class names changed).
   * The Xtream and Telegram pipelines share the same conceptual structure (`adapter/`, `catalog/`, `debug/`, `mapper/`, `model/`, plus `capability/` for Telegram and future Xtream capabilities).

Once this is done, the glossary and inventory together should form a stable, high-quality naming contract for all future work on the v2 architecture.


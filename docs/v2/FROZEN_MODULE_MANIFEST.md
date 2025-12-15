# Frozen Module Manifest v2
**Project:** FishIT-Player v2  
**Status:** **FROZEN** (no new modules allowed after the one-time stub PR)

---

## Zero-Options Rules
1) **NO new modules** may be added after the one-time stub PR.  
   - A “module” is any directory containing a `build.gradle.kts` and/or any new `include(":...")` entry in `settings.gradle.kts`.
2) `settings.gradle.kts` **MUST NOT** be changed after the one-time stub PR, except normal version bumps inside existing files (no new includes).
3) Any PR that introduces a new module **MUST FAIL CI** (guardrail).  
4) Only the **RESERVED** modules listed below may be created in the one-time stub PR. After that, **FROZEN** again.
5) **Hilt/DI binding ownership:** a `@Module` lives in the module that **owns the implementation**. Never bind implementations from `app-v2` for infra/transport.
6) **No UI/Compose inside domain layers:** `playback:domain` and `core:*` model/contract modules must remain free of Compose APIs.
7) **No Hilt EntryPoints in player UI:** `player:ui` and later `player:ui-api` must never use `@EntryPoint` or `EntryPointAccessors`.
8) **App-v2 is composition only:** app-v2 may trigger bootstraps via interfaces, but must not contain transport/data implementations.

---

## Layer Physics (Non-Negotiable)
- **app-v2**: composition only (navigation + startup triggers). No transport/data implementations.
- **feature/**: UI + ViewModel + feature-owned domain interfaces/usecases. No transport/pipeline/player-internal imports.
- **infra/**: implementations (transport, data, logging, imaging, work).
- **pipeline/**: source ingestion/catalog → `RawMediaMetadata` only. No UI. No local normalization/lookup.
- **playback/**: source → playback sources/URIs. No pipeline DTO dependencies.
- **player/**: engine + stable UI API. `player/internal` is implementation; `player/ui` is the product UI API.

---

## Allowed Dependencies (Hard Rules)
### app-v2 may depend on
- `core/*`
- `feature/*`
- `player:ui`
- `playback:domain`
- `infra:logging`
- (Any other infra only through feature/domain interfaces; no direct transport usage in code)

### feature/* may depend on
- `core/*`
- `infra:logging`
- `player:ui`
- (Optional: `playback:domain` only if strictly necessary and contract-approved)

### feature/* must NOT depend on
- `infra:transport-*`
- `pipeline:*`
- `player:internal`

### playback/* must NOT depend on
- `pipeline:*`
- `infra:data-*`

### pipeline/* must NOT depend on
- `infra:data-*`
- `playback:*`
- `player:*`

---

# Frozen Module List (Final)

## App
### `:app-v2` — **Owner: App Composition**
- **Path:** `app-v2/`
- **Purpose:** App shell, navigation, startup triggers (interfaces only)
- **Must NOT:** contain transport/data implementations or import `player/internal` types

---

## Core
### `:core:model` — **Owner: Core Model**
- **Path:** `core/model/`
- **Purpose:** canonical model types (`SourceType`, `MediaType`, `ImageRef`, etc.)
- **Target:** keep as “pure Kotlin” where possible (long-term)

### `:core:player-model` — **Owner: Core Player Contracts**
- **Path:** `core/player-model/`
- **Purpose:** stable playback request models (`PlaybackContext`, etc.)
- **Must NOT:** depend on Compose, transport, or player internals

### `:core:metadata-normalizer` — **Owner: Normalization**
- **Path:** `core/metadata-normalizer/`
- **Purpose:** centralized normalization (Raw → Normalized)
- **Rule:** pipelines must not perform local normalization/lookup

### `:core:persistence` — **Owner: Persistence**
- **Path:** `core/persistence/`
- **Purpose:** persistence contracts + DI for storage

### `:core:feature-api` — **Owner: Feature System**
- **Path:** `core/feature-api/`
- **Purpose:** feature registry + feature provider contracts (multibindings)

### `:core:ui-layout` — **Owner: UI Primitives**
- **Path:** `core/ui-layout/`
- **Purpose:** Fish UI layout primitives (`FishRow`, `FishTile`, etc.)

### `:core:ui-imaging` — **Owner: UI Primitives**
- **Path:** `core/ui-imaging/`
- **Purpose:** UI imaging primitives (render helpers, ImageRef usage)
- **Must NOT:** contain DI/wiring (no Coil/ImageLoader provision, no OkHttp provision)

### `:core:ui-theme` — **Owner: UI Theme**
- **Path:** `core/ui-theme/`
- **Purpose:** theme, colors, dimens

### `:core:firebase` — **Owner: Core Services**
- **Path:** `core/firebase/`
- **Purpose:** Firebase integration + contracts (no app-v2 wiring hacks)

### `:core:catalog-sync` — **Owner: Catalog Sync Contracts**
- **Path:** `core/catalog-sync/`
- **Purpose:** contract-only for catalog sync (domain/types)
- **Must NOT:** contain WorkManager scheduling or infra wiring (belongs in `infra:work`)

### `:core:app-startup` — **Owner: Startup Contracts**
- **Path:** `core/app-startup/`
- **Purpose:** startup orchestration contracts (interfaces only)

---

## Features (Owner: Feature)
All feature modules are UI + feature domain. They must not import transport/pipeline/player-internal types.
- `:feature:onboarding` — connect/auth UI using feature-owned auth interfaces
- `:feature:home` — home UI + navigation triggers
- `:feature:telegram-media` — Telegram browsing UI
- `:feature:library` — library UI
- `:feature:live` — live UI
- `:feature:detail` — detail UI
- `:feature:audiobooks` — audiobooks UI
- `:feature:settings` — settings + debug UI

---

## Infra
### `:infra:logging` — **Owner: Infra**
- **Path:** `infra/logging/`
- **Purpose:** UnifiedLog + redaction enforcement
- **Rule:** the only infra module features may directly use

### `:infra:tooling` — **Owner: Infra**
- **Path:** `infra/tooling/`
- **Purpose:** dev/debug tooling (non-critical runtime)

### Data (Owner: Data)
- `:infra:data-home` — composite adapters for home feature interfaces
- `:infra:data-telegram` — Telegram feature-facing repo implementations + mapping
- `:infra:data-xtream` — Xtream feature-facing repo implementations + mapping

### Transport (Owner: Transport)
- `:infra:transport-telegram` — TDLib/Telegram transport clients + auth/connectivity
- `:infra:transport-xtream` — Xtream API client/session + connectivity

### RESERVED (allowed to be created in the one-time stub PR)
#### `:infra:imaging` — **Owner: Infra** (**RESERVED**)
- **Path:** `infra/imaging/`
- **Purpose:** global Coil/ImageLoader/OkHttp cache + provisioning (source-agnostic)
- **Rule:** may accept source-specific fetchers via narrow interfaces; transport must not own Coil config

#### `:infra:work` — **Owner: Infra** (**RESERVED**)
- **Path:** `infra/work/`
- **Purpose:** WorkManager scheduling/orchestration (catalog sync, background fetch)
- **Rule:** consumes `core:catalog-sync` contracts; app-v2 only triggers via interfaces

---

## Pipeline (Owner: Pipeline)
- `:pipeline:telegram` — Telegram catalog ingestion → `RawMediaMetadata`
- `:pipeline:xtream` — Xtream catalog ingestion → `RawMediaMetadata`
- `:pipeline:io` — local/IO ingestion
- `:pipeline:audiobook` — audiobook ingestion

Rules:
- Must remain UI-agnostic
- Must not do local normalization/lookup (central normalizer only)

---

## Playback (Owner: Playback)
- `:playback:domain` — playback contracts + resolver interfaces (no Compose)
- `:playback:telegram` — Telegram playback source factory (tg:// URIs)
- `:playback:xtream` — Xtream playback source factory (session-based URL building)

Rules:
- Must not depend on pipeline DTOs
- Must not depend on infra:data modules

---

## Player (Owner: Player)
### `:player:internal` — **Owner: Player Engine**
- **Path:** `player/internal/`
- **Purpose:** engine + internal wiring (resolver usage, codecs, gates, resume)
- **Rule:** never imported by app-v2 or feature modules

### `:player:ui` — **Owner: Player Product UI**
- **Path:** `player/ui/`
- **Purpose:** stable public API: `PlayerScreen(context, onExit)`
- **Must NOT:** use Hilt EntryPoints or reference engine wiring types directly
- **May depend on:** `playback:domain`, `core:player-model`, `infra:logging`

### `:player:miniplayer`
- **Path:** `player/miniplayer/`
- **Purpose:** optional mini-player UI/host

### `:player:nextlib-codecs`
- **Path:** `player/nextlib-codecs/`
- **Purpose:** Nextlib codec configuration module

### RESERVED (allowed to be created later, but must be reserved now)
#### `:player:ui-api` — **Owner: Player API** (**RESERVED**)
- **Path:** `player/ui-api/`
- **Purpose:** UI-facing interfaces/contracts only (no implementation, no screen)
- **Rule:** used for the later split; once created, `player:ui` depends on `player:ui-api`

---

## Tools (Owner: Tools)
### `:tools:pipeline-cli` — **Owner: Tools**
- **Path:** `tools/pipeline-cli/`
- **Purpose:** Headless CLI for pipeline/transport testing
- **Must be headless:** NO Compose plugins, NO AndroidX UI/Compose dependencies, NO Hilt/DI
- **Allowed deps:** `core:*`, `pipeline:*`, `infra:transport-*`, `infra:logging`, Kotlin stdlib, CLI frameworks (Clikt)
- **Rule:** This module must remain headless (no UI, no DI) to prevent becoming a backdoor for improper wiring
- **Note:** Uses Android library plugin due to Android dependencies, but NO Android-specific APIs allowed

---

# One-Time Stub PR Scope (Fixed)
The ONLY modules allowed to be created as new directories during the stub PR:
1) `infra/imaging`
2) `infra/work`
3) `player/ui-api`

After that PR merges:
- `settings.gradle.kts` must not change
- no new module folders may appear
- all future work is only filling TODOs in existing modules

---

# CI Guardrails (Must Exist)
CI MUST fail when:
- `settings.gradle.kts` changes (after stub PR)
- a new `build.gradle.kts` appears outside the modules listed in this manifest
- `app-v2` or any `feature/*` depends on `:player:internal`
- `app-v2` depends on `infra:transport-*` or `pipeline:*` via Gradle deps
- `player:ui` uses `@EntryPoint` or `EntryPointAccessors`
- `playback:domain` gains any Compose dependencies
- `:tools:pipeline-cli` declares Compose plugins or AndroidX UI/Compose dependencies (must remain headless)

---

# No-Deviation Clause
This manifest is the single source of truth for module structure.  
Copilot must not add or propose any new modules, and must treat this document as immutable unless explicitly updated by the repo owner in a dedicated “Manifest Update” PR.

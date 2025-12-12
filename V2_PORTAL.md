# FishIT-Player v2 – Portal (architecture/v2-bootstrap)

This file is the **entry point** for the v2 rebuild of FishIT-Player in this repository.

- Branch: `architecture/v2-bootstrap`
- Status: v2 is being rebuilt from scratch.
- v1 is fully caged under `/legacy/**` and is read-only.

If you are a human or an agent, **start here** before touching anything else.

---

## 1. What is v2?

FishIT-Player v2 is a clean rebuild of the app with:

- A clear separation between:
  - **Core / Canonical Media / Normalizer**
  - **Pipelines** (Telegram, Xtream, Audiobook, IO)
  - **Internal Player (SIP) & Playback**
  - **UI Feature modules**
  - **Infra** (logging, telemetry, cache, image loading, FFMpegKit)
- A strict boundary between:
  - v2 code and docs (active),
  - v1 code and docs (archived in `legacy/` as reference only).

The goal is to make FishIT-Player maintainable, testable and feature-extensible, without carrying forward the architectural debt of v1.

---

## 2. Where to work (v2 surface)

All **active** v2 work happens in these paths:

- `app-v2/` – v2 app shell, navigation, top-level UI host.
- `core/` – canonical media models, metadata normalizer, logging, telemetry.
- `infra/` – imageloader (Coil 3), cache, settings, FFMpegKit integration, tooling.
- `feature/` – UI feature modules (Home, Library, Live, Detail, Telegram media, Settings, Audiobooks, etc.).
- `player/` – Internal Player (SIP) and player-related implementations.
- `playback/` – playback domain, contracts between pipelines and SIP.
- `pipeline/` – v2 pipelines:
  - `pipeline/telegram`
  - `pipeline/xtream`
  - `pipeline/audiobook`
  - `pipeline/io`
- `docs/v2/` – v2 architecture, contracts and feature specifications.
- `docs/meta/` – build, quality, workspace and agent meta-docs.
- `scripts/` – build helpers, API probes, quality scripts.

Everything under:

- `legacy/**`
- `app/**`
- `docs/legacy/**`
- `docs/archive/**`

is **v1** or historical and must be treated as **read-only**.

For detailed rules, see `AGENTS.md`.

---

## 3. Architecture – High-level overview

### 3.0 Feature System

v2 uses a centralized Feature System for declaring and discovering capabilities:

- **`core/feature-api`** – Feature API types (FeatureId, FeatureScope, FeatureProvider, FeatureRegistry)
- **`Features.kt`** – Catalog of all feature IDs grouped by domain
- **`AppFeatureRegistry`** – Central registry in app-v2, populated via Hilt multibindings
- **Feature Providers** – Each module contributes providers to declare its features

Key docs:

- `docs/v2/architecture/FEATURE_SYSTEM_TARGET_MODEL.md`
- Feature contracts: `docs/v2/features/<category>/FEATURE_<featureId>.md`

### 3.1 Core & Canonical Media

- Single canonical representation of media in `core/model` and related modules.
- Pipelines emit **RawMediaMetadata** only.
- Normalization & resolution (including TMDB lookups) happen centrally in the normalizer/resolver.

Key docs:

- `docs/v2/ARCHITECTURE_OVERVIEW_V2.md`
- `docs/v2/APP_VISION_AND_SCOPE.md`
- `docs/v2/CANONICAL_MEDIA_SYSTEM.md`
- `/contracts/MEDIA_NORMALIZATION_CONTRACT.md` ⚠️ **Binding Contract**
- `docs/v2/MEDIA_NORMALIZER_DESIGN.md`
- `docs/v2/MEDIA_NORMALIZATION_AND_UNIFICATION.md`
- `docs/v2/MEDIA_PARSER_DELIVERY_SUMMARY.md`

### 3.2 Pipelines

Pipelines connect external sources to the canonical media system:

- `pipeline/telegram` – Telegram media (VOD, clips, series, history, thumbs).
- `pipeline/xtream` – Xtream live, VOD, series, EPG, catchup.
- `pipeline/audiobook` – audiobook libraries, chapters, progress.
- `pipeline/io` – local/file-based inputs (M3U, local file scan, playlist normalization).

Pipelines:

- do *not* normalize metadata or call TMDB directly,
- do *not* implement player logic,
- do *not* depend on TDLib types or `TdlibClientProvider` (use adapters that consume `TgMessage` wrapper types),
- are scoped and testable.

#### Telegram Transport Status

The v2 Telegram transport architecture uses typed interfaces instead of exposing TDLib directly:

| Interface | Purpose | Status |
|-----------|---------|--------|
| `TelegramAuthClient` | Authentication operations | 🚧 Interface defined |
| `TelegramHistoryClient` | Chat history, message fetching | 🚧 Interface defined |
| `TelegramFileClient` | File download operations | 🚧 Interface defined |
| `TelegramThumbFetcher` | Thumbnail fetching for Coil | ✅ Implemented |

**Implementation:**
- `DefaultTelegramClient` in `transport-telegram` owns TDLib state
- Maps TDLib DTOs to `TgMessage`/`TgContent`/`TgThumbnail` wrapper types
- v1 gold patterns (`T_TelegramServiceClient`, `T_TelegramSession`, `T_ChatBrowser`) extracted to `/legacy/gold/`

**NOT exposed to upper layers:**
- TDLib types (`TdApi.*`)
- `TdlibClientProvider` (v1 legacy pattern – must NOT be reintroduced)
- g00sha TDLib internals

Key docs:

- Telegram:
  - `docs/v2/telegram/TDLIB_FINAL_REVIEW_UPDATED.md`
  - `docs/v2/telegram/TELEGRAM_TDLIB_V2_INTEGRATION.md`
  - `/contracts/TELEGRAM_PARSER_CONTRACT.md` ⚠️ **Binding Contract (Draft)**
  - `docs/v2/telegram/TELEGRAM_PLAYBACK_PIPELINE_AUDIT.md`
  - `docs/v2/telegram/TDLIB_STREAMING_THUMBNAILS_SSOT.md`
  - `docs/v2/telegram/TDLIB_STREAMING_WITH_HEADER_VALIDATION.md`
- Xtream:
  - `docs/v2/XTREAM_PIPELINE_V2_REUSE_ANALYSIS.md`
- Pipeline-wide:
  - `docs/v2/PIPELINE_PR_CHECKLIST.md`
  - `docs/v2/PIPELINE_SYNC_STATUS.md`

### 3.3 Internal Player (SIP) & Playback

- Central Internal Player lives under `/player/internal` and related `/playback` modules.
- All playback (VOD, live, trickplay, subtitles/CC, TV input) must go through SIP contracts.
- No pipeline or UI should duplicate player logic.
- **The player is source-agnostic**: It knows only `PlaybackContext` and `PlaybackSourceFactory` sets.

#### Current Player Status

> ✅ **The player is test-ready without Telegram/Xtream transport.**

| Component | Status | Description |
|-----------|--------|-------------|
| `core:player-model` | ✅ Complete | `PlaybackContext`, `PlaybackState`, `SourceType` |
| `player:internal` | ✅ Test-ready | `InternalPlayerSession`, `PlaybackSourceResolver`, `InternalPlayerEntry` |
| `player:nextlib-codecs` | ✅ Integrated | FFmpeg codecs via `NextlibCodecConfigurator` |
| Debug Playback | ✅ Working | `DebugPlaybackScreen` with Big Buck Bunny test stream |
| `TelegramPlaybackSourceFactoryImpl` | ⏸️ Disabled | Awaiting `DefaultTelegramClient` in transport layer |
| `XtreamPlaybackSourceFactoryImpl` | ⏸️ Disabled | Can be enabled when Xtream transport is wired |

**Architecture:**
- Player uses `PlaybackSourceResolver` with injected `Set<PlaybackSourceFactory>` via `@Multibinds`
- Empty factory set is valid – player falls back to test stream
- Telegram/Xtream factories are optional extensions that plug in via DI

Key docs:

- `/contracts/INTERNAL_PLAYER_BEHAVIOR_CONTRACT.md` ⚠️ **Binding Contract**
- `/contracts/INTERNAL_PLAYER_BEHAVIOR_CONTRACT_FULL.md` ⚠️ **Binding Contract**
- `docs/v2/internal-player/INTERNAL_PLAYER_REFACTOR_ROADMAP.md`
- `docs/v2/internal-player/INTERNAL_PLAYER_REFACTOR_SSOT.md`
- `docs/v2/internal-player/INTERNAL_PLAYER_REFACTOR_STATUS.md`
- `docs/v2/internal-player/INTERNAL_PLAYER_PHASE*_CHECKLIST.md`
- `/contracts/INTERNAL_PLAYER_SUBTITLE_CC_CONTRACT_PHASE4.md` ⚠️ **Binding Contract**
- `/contracts/INTERNAL_PLAYER_PLAYER_SURFACE_CONTRACT_PHASE5.md` ⚠️ **Binding Contract**
- `/contracts/INTERNAL_PLAYER_TV_INPUT_CONTRACT_PHASE6.md` ⚠️ **Binding Contract**
- `/contracts/INTERNAL_PLAYER_PLAYBACK_SESSION_CONTRACT_PHASE7.md` ⚠️ **Binding Contract**
- `/contracts/INTERNAL_PLAYER_PHASE8_PERFORMANCE_LIFECYCLE_CONTRACT.md` ⚠️ **Binding Contract**
- `docs/v2/internal-player/TRICKPLAY_INTEGRATION.md`

---

## 4. Logging, Telemetry & Diagnostics

v2 has a unified logging and telemetry story:

- Logging must go through the centralized logging abstractions.
- Telemetry must capture:
  - player statistics,
  - pipeline events,
  - UI responsiveness / jank where relevant.

Key docs:

- `/contracts/LOGGING_CONTRACT_V2.md` ⚠️ **Binding Contract**
- `docs/v2/logging/LOGGING_SYSTEM_ANALYSIS.md`
- `docs/v2/logging/UNIFIED_LOGGING.md`
- `docs/v2/logging/LOG_VIEWER.md`
- Telemetry (if present):
  - `docs/v2/telemetry/**`

---

## 5. Imaging & FFMpegKit

### 5.1 Imaging (Coil 3, global ImageLoader)

- One global, well-configured ImageLoader (Coil 3) in v2.
- Pipelines provide image sources (thumb/poster/backdrop) via contracts, not ad-hoc.

Key docs:

- `docs/v2/IMAGING_SYSTEM.md`
- `docs/v2/features/imaging/**` (when populated)

### 5.2 FFMpegKit & Media Tools

FFMpegKit handling is centralized in infra, not scattered across the app.

Key docs:

- `docs/v2/ffmpegkit/FFMPEGKIT_BUILD.md`
- `docs/v2/ffmpegkit/FFMPEGKIT_IMPLEMENTATION.md`
- `docs/v2/ffmpegkit/FFMPEGKIT_INTEGRATION.md`
- `docs/v2/ffmpegkit/FFMPEGKIT_PRESETS.md`

---

## 6. Settings, Cache & Infra

Settings, cache and infra are centralized and must not be re-invented per feature.

### 6.1 Settings

- Single source of truth for app-wide and pipeline-specific settings.
- All v2 settings go through well-defined infra/settings modules.

See:

- `docs/v2/settings/**` (when present)
- `docs/meta/**` for workspace and environment guides.

### 6.2 Cache

- Log, Telegram, Xtream, FFMpegKit caches are managed via central cache modules in `/infra/cache/**`.
- Cache operations are explicit actions (e.g. “Clear logs”, “Clear Telegram cache”, etc.).

See:

- `docs/v2/cache/**` (when present)
- `docs/v2/LOGGING_CONTRACT_V2.md` and logging-related docs.

---

## 7. UI & Feature Modules

UI is organized into feature modules under `/feature/**`:

- `feature/home` – Home screen / entry point.
- `feature/library` – library browsing.
- `feature/live` – live channels.
- `feature/detail` – detail view (movies, series, episodes).
- `feature/telegram-media` – Telegram-specific media UX.
- `feature/settings` – settings and cache actions.
- `feature/audiobooks` – audiobook UX.

The UI must:

- depend on core and pipelines via contracts and feature flags,
- not reach into legacy code,
- not implement business logic that belongs in core, pipelines, or SIP.

See:

- `docs/v2/ui/**`
- Feature-specific docs under `docs/v2/features/ui/**` (when populated).

---

## 8. Agents & Automation

All automated agents must follow `AGENTS.md` in this branch.

Key points:

- v2 work is allowed under:
  - `/app-v2`, `/core`, `/infra`, `/feature`, `/player`, `/playback`, `/pipeline`, `/docs/v2`, `/docs/meta`, `/scripts`.
- Legacy is strictly read-only:
  - `/legacy/**`, `/app/**`, `/docs/legacy/**`, `/docs/archive/**`.
- No new `com.chris.m3usuite` references outside `legacy/`.
- No new global mutable singletons, except in the rare case where a hard technical limitation forces it – and then it must be clearly documented and discussed.

See:

- `AGENTS.md`

---

## 9. Roadmap & Changelog (v2-only)

The v2 rebuild has its own roadmap and changelog, starting from the creation of the `architecture/v2-bootstrap` branch.

- Roadmap:
  - `ROADMAP.md` (v2-only)
- Changelog:
  - `CHANGELOG.md` (or `CHANGELOG_V2.md` if used)

These documents:

- describe phases (Phase 0: legacy cage, Phase 1: feature system, etc.),
- track progress and decisions only in the v2 context.

---

## 10. Legacy & v1 (read-only)

Everything under:

- `legacy/v1-app/` – old v1 app implementation,
- `legacy/docs/` – v1 architecture, old bug reports, old contracts,
- `legacy/tools/` – v1-specific scripts,

is **historic**.

Use it as:

- behavioral reference,
- example of what worked (and what didn’t),
- source of ideas.

Do **not**:

- modify,
- upgrade,
- refactor,
- or “fix” legacy code or docs.


### 10.1. Gold Nuggets – Production-Tested v1 Patterns

**`/legacy/gold/`** contains curated patterns extracted from ~12,450 lines of v1 production code:

| Category | Patterns | Document |
|----------|----------|----------|
| **Telegram Pipeline** | 8 patterns (unified engine, zero-copy streaming, RemoteId URLs, priority downloads, MP4 validation) | `telegram-pipeline/GOLD_TELEGRAM_CORE.md` |
| **Xtream Pipeline** | 8 patterns (rate limiting, dual-TTL cache, alias rotation, multi-port discovery, graceful degradation) | `xtream-pipeline/GOLD_XTREAM_CLIENT.md` |
| **UI/Focus** | 10 patterns (FocusKit, focus zones, tvClickable, DPAD handling, focus memory) | `ui-patterns/GOLD_FOCUS_KIT.md` |
| **Logging** | 10 patterns (UnifiedLog facade, ring buffer, source categories, structured events, log viewer) | `logging-telemetry/GOLD_LOGGING.md` |

**How to use:**

1. **Before implementing** features in these areas, read the relevant gold document
2. Each pattern includes v2 target modules, porting checklists, and code review improvements
3. **Always re-implement** using v2 architecture – never copy/paste
4. See `GOLD_EXTRACTION_FINAL_REPORT.md` for overview and `legacy/gold/EXTRACTION_SUMMARY.md` for detailed guidance
---

## 11. If you are unsure

If you are an agent or a human and you are not sure where something belongs:

1. Check:
   - `AGENTS.md`
   - `V2_PORTAL.md` (this file)
   - `docs/v2/ARCHITECTURE_OVERVIEW_V2.md`
2. If it is still unclear:
   - propose a plan (for agents, in the PR / task),
   - wait for user feedback before performing a large architectural change.

v2 is meant to be clean, explicit and well-structured.  
When in doubt: stop, read the docs, and ask.

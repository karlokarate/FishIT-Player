# FishIT Player v2 – Vision & Scope

> This document defines what FishIT Player v2 **is** and what it **is not**.  
> It is the primary high-level reference for humans and AI assistants (Copilot, ChatGPT, etc.) when making architecture or feature decisions.

---

## Related Documents

| Document | Purpose |
|----------|--------|
| `V1_VS_V2_ANALYSIS_REPORT.md` | **v1 Quality Assessment** – Identifies production-ready v1 components for v2 porting (Tier 1/2 classification, ~17,000 lines mapped) |
| `ARCHITECTURE_OVERVIEW_V2.md` | Module structure and layer definitions |
| `IMPLEMENTATION_PHASES_V2.md` | Build order and phase checklists |
| `AGENTS_V2.md` | Execution rules for AI assistants |

> ⚠️ **For porting decisions**, always consult `V1_VS_V2_ANALYSIS_REPORT.md` first.
> It documents which v1 systems are production-quality and should NOT be rewritten.

---

## 1. Purpose

FishIT Player v2 is a **modular, offline-first, multi-pipeline media client**.

Core properties:

- The app is a **pure consumer client**:
  - It never acts as a media server.
  - It only consumes media from external pipelines and local storage.
- It is built around a **single, powerful internal player** (SIP – Structured Internal Player) that:
  - Plays content from multiple pipelines:
    - Xtream / IPTV
    - Telegram (via tdlib-coroutines, g00sha)
    - Local / IO content (device storage, storage providers, later network shares)
    - Audiobooks (future)
  - Applies consistent playback behavior across all pipelines.

From an end-user perspective v2 should feel like:

- A **universal premium media client** that can plug into many sources.
- A place where playback behaves consistently, no matter where the content comes from.

---

## 2. Non-Goals

FishIT Player v2 is **not**:

- A self-hosted streaming backend or media server.
- A social network, messenger, or content creation tool.
- A multi-tenant SaaS platform with extensive server-side business logic.
- A “UI as DSL from the cloud” system where layouts are defined remotely.

Backend-wise:

- There is **no custom backend** beyond:
  - Firebase (Auth, Remote Config, Firestore, Crashlytics, etc.).
- A custom backend is **optional**, only for future features;  
  it is not required for core playback or app startup.

---

## 3. Core Experiences

### 3.1 Unified Playback

- A single internal player (SIP) is used for all content types:
  - Xtream VOD / Series / Live
  - Telegram media
  - Local / IO media
  - Audiobooks (future)
- The player applies the same rules for:
  - Start and resume behavior
  - Kids/screen-time limits and blocking
  - Subtitles & Closed Captions
  - Live-TV controls (channels, EPG)
  - TV remote / DPAD navigation

Pipelines differ only in **how they provide content**, not in how playback behaves.

---

### 3.2 Offline-First

- The app must be usable **without any network connection** for:
  - Already downloaded Telegram content
  - Xtream recordings (if stored locally)
  - Local music, audiobooks, and video files
- Firebase is treated as **optional**:
  - If Firebase is not available or misconfigured:
    - The app still starts.
    - Locally available content is still playable.
    - The last known entitlements and feature flags are used from local storage.
- Network is required only for:
  - Online streaming from Xtream/Telegram
  - Updating remote config and entitlements
  - Crash & performance reporting

---

### 3.3 Modular Feature Shells

The app exposes several **feature shells** (user-facing entry points):

- Home (unified start screen)
- Library (VOD/Series overview)
- Live-TV (channels and EPG)
- Telegram media
- Audiobooks (future)
- IO / Local content (future)
- Settings

Each feature shell:

- Owns its **own UI and ViewModel state machine**.
- Is **source-agnostic** where possible:
  - Example: Library may mix titles from Xtream, Telegram, and IO.
- Talks only to:
  - Pipelines via **interfaces** (repositories, playback source factories).
  - The internal player via `PlaybackContext` and player entry APIs.

---

### 3.4 Profiles, Kids Mode and Entitlements

- Multiple profiles per device (adult and kids).
- Kids profiles:
  - Have screen-time limits and/or time windows.
  - May restrict access to certain pipelines (e.g. Telegram) or features (e.g. subtitles).
- Entitlements:
  - Are controlled via local storage plus optionally Firebase.
  - Are used for:
    - Trial vs. full access
    - Feature gating (enabling/disabling entire pipelines or advanced features)
- In all cases:
  - If entitlement data cannot be refreshed, cached entitlements are used.
  - The app does not crash or become unusable solely because remote entitlement checks fail.

---

### 3.5 Internationalization (i18n) & Localization

From day one:

- **All user-facing text** must be handled through Android string resources.
- The app must support at least:
  - English: `values/strings.xml`
  - German: `values-de/strings.xml`
- Additional languages must be addable by simply adding new `values-<lang>` folders.

Rules:

- No hardcoded user-facing strings in Kotlin / Compose.
- Developer-only debug UI:
  - May remain English-only,
  - But must still use string resources (no hardcoded text).
- Code, identifiers and code comments are always written **in English**.

---

## 4. Platform & Technology Scope

### 4.1 Platform

- Android (phones, tablets, TV devices).
- Single codebase, but TV-specific behavior (DPAD, focus, mini-player, live-TV) is a **first-class concern**.

### 4.2 Core Technologies

The v2 app is built with:

- **Language & Concurrency**
  - Kotlin
  - Kotlin Coroutines & Flow

- **UI**
  - Jetpack Compose
  - Jetpack Compose Navigation for navigation

- **Media**
  - AndroidX Media3 / ExoPlayer for playback

- **Telegram**
  - tdlib-coroutines (g00sha) for Telegram API integration

- **Persistence**
  - DataStore for key-value / small state
  - A local database (ObjectBox) for structured data, directly reusing v1 ObjectBox entities and store pattern

- **Dependency Injection**
  - Hilt as the DI framework

- **Backend Integration**
  - Firebase:
    - Optional, but supported and encouraged
    - Auth (optional)
    - Remote Config
    - Firestore (for profiles & entitlements, optional)
    - Crashlytics

### 4.3 Quality & Tooling

Quality tooling is a requirement, not an afterthought:

- ktlint (formatting)
- detekt (static analysis)
- LeakCanary (debug builds)
- StrictMode (debug builds)
- kotlinx-coroutines-test + Turbine for Flow testing
- Optional: ArchUnit or equivalent for enforcing architecture rules

Concrete versions and update cadence will be defined in a separate `DEPENDENCY_POLICY.md`.  
All code and build logic must use the versions defined there.

A separate `DEPENDENCY_POLICY.md` document defines the approved versions and update cadence
for all key dependencies. It MUST be kept reasonably up to date.
Dependency upgrades MUST only be performed in dedicated "dependency update" tasks,
not inside feature or refactor tasks.

---

## 5. High-Level Product Principles

1. **Pipeline-Agnostic Player**  
   The player cares about **what** to play and **how** to behave, not **where** the content comes from.  
   Pipelines adapt to the player, not the other way around.

2. **Modular Pipelines**  
   Xtream, Telegram, IO, Audiobooks are built as if they could be dropped into another app:
   - Clear interfaces
   - Isolated implementations
   - No UI inside pipelines

3. **Replaceable Experiences**  
   The visual experience (grid UI, list UI, experimental globe navigation, etc.) must be replaceable **without** touching:
   - pipeline modules
   - playback domain
   - internal player core

4. **Offline-First**  
   Offline usability is a hard requirement:
   - Locally available content must always be playable.
   - Remote services (Firebase, remote APIs) enhance the experience but are not required for basic operation.

5. **Contracts Before Code**  
   Behavior contracts and architecture docs are the **source of truth**.  
   If implementation and contract diverge:
   - update the contract first,
   - then align the code to match it.

---

## 6. Long-Term Requirements & Future Directions

The following are explicitly supported by the v2 architecture, even if not all are implemented in the first release:

- Multiple UI “skins” / experiences:
  - Classic list/grid
  - TV-first big-tile layout
  - Experimental world/globe-based navigation
- Dynamic feature flags & trials via Firebase:
  - Per user / profile / device feature toggles
  - Gradual rollout of new pipelines or features
- Reusing pipelines and the internal player in **other apps**.
- Optional external tooling integration:
  - Debug overlays
  - Player diagnostics screen
  - Developer-only tools and dashboards

These must not require structural refactors of the core architecture.

---

## 7. Out-of-Scope for v2

The following are explicitly out-of-scope for FishIT Player v2:

- A dedicated backend service beyond Firebase.
- Dynamic “UI from the cloud” systems where layout definitions are streamed as DSL.
- Cross-device playback sync (e.g. sync state across multiple phones/TVs) – may be considered later.
- Web or iOS variants (v2 focuses entirely on Android).

Any proposal that requires these must be treated as a **future extension**, not part of v2 core.

---

## 8. Instructions for AI / Agents

When AI assistants (Copilot, ChatGPT, etc.) work on this project:

1. **Read Before Coding**
   - Always read:
     - `APP_VISION_AND_SCOPE.md`
     - `ARCHITECTURE_OVERVIEW_V2.md`
     - `IMPLEMENTATION_PHASES_V2.md`
   - before doing any v2-related work.

2. **English-Only Code**
   - All code, identifiers, comments and docstrings must be in English.
   - Only UI strings go into string resources for i18n.

3. **i18n Enforcement**
   - Do not introduce new hardcoded user-facing text.
   - Use string resources with at least English and German entries.
   - Debug UI must still use string resources, even if only English is meaningful.

4. **No Architecture “Options”**
   - Do not propose multiple architecture options (e.g. “we could do A or B”) in code.
   - For architecture-level decisions, always pick a single best-practice approach that is consistent with these documents.

5. **Handling Ambiguities**
   - If you detect contradictions or unclear rules between docs:
     - Stop implementation.
     - Propose a specific change to the relevant markdown file(s) to resolve the discrepancy.
     - Only after the contract is clear, proceed with code changes.

6. **Library Versions**
   - Do not randomly bump library versions in feature tasks.
   - Use the versions defined in `DEPENDENCY_POLICY.md` once it exists.
   - Propose version upgrades only in dedicated “dependency update” tasks.

7. **Scope of Work**
   - For any given task, keep changes scoped to the specified module(s).
   - Do not modify unrelated modules “on the side”.
   - Cross-module refactors require dedicated architecture tasks.

This document is the **top-level product contract**.  
The architecture and implementation phases documents refine how this vision is realized in code.

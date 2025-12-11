# FishIT Player v2 — UI Implementation Blueprint (Tools & Modules)

> This document is the **binding implementation plan** for the v2 UI stack.  
> It is tailored to the current v2 architecture (modules, contracts, roadmap) and the UI vision.   
>  
> It describes **exactly which tools are used where** – with no alternatives – to implement the UI in **best possible quality**, on TV and mobile, for both **Classic** and **Experience** skins.

---

## 0. High-Level Context

The v2 rebuild is fully Compose-based. Active work happens in:

- `app-v2/`
- `core/**`
- `infra/**`
- `feature/**`
- `player/**`
- `playback/**`
- `pipeline/**`   

UI screens live in `feature/*`, consume **domain models** (e.g. `NormalizedMedia`, `MediaVariant`) and **never pipeline DTOs**. Pipelines produce `RawMediaMetadata` only; normalization and canonical identity live in `core/metadata-normalizer`.   

All logging must go through `UnifiedLog` (from `infra/logging`) according to `LOGGING_CONTRACT_V2`.   

This blueprint focuses exclusively on **UI tools** and **where they belong**.

---

## 1. Core UI Technology (Non-Negotiable)

### 1.1 Jetpack Compose as the Only UI Toolkit

**Decision:**  
Every v2 screen and component is implemented with **Jetpack Compose**, including TV UIs.

- Base Compose:
  - `androidx.compose.ui:*`
  - `androidx.compose.foundation:*`
  - `androidx.compose.animation:*`
  - `androidx.compose.runtime:*`

**Where:**

- `app-v2/` — App shell, `MainActivity`, top-level `NavHost`, global theme.   
- `feature/*` — All user-facing screens (onboarding, home, details, Telegram media, live, settings, audiobooks).   
- `player/internal` — Player UI chrome, mini-player overlay (only the visual layer; playback engine is headless).   

No XML layouts, no classic `View` hierarchies will be added in v2; Views exist only where strictly required for Media3 player surface, wrapped in Compose according to the internal-player contracts.   

---

## 2. Navigation

### 2.1 Navigation Compose

**Decision:**  
All navigation between screens uses **Navigation Compose**:

- Library: `androidx.navigation:navigation-compose`

**Where & How:**

- `app-v2/navigation` (or equivalent package inside `app-v2`):
  - Defines all routes:
    - `Onboarding`, `ProfileSelection`, `Home`, `Detail`, `TelegramVerse`, `LiveTv`, `Search`, `Settings`, `Audiobooks`.
  - Hosts the `NavHost` for v2.

- `feature/*` modules:
  - Use a small `Navigator` interface abstraction injected via DI.
  - Never use `NavController` directly; they call typed functions (e.g., `navigator.openDetails(canonicalId)`).

**Reasoning:**  
Keeps routing logic centralized in AppShell (`app-v2`) and ensures features remain decoupled and testable.   

---

## 3. Images & Posters

### 3.1 Coil 3 via `core/ui-imaging`

**Decision:**  
All image loading (posters, thumbnails, backdrops, channel logos) uses **Coil 3** integrated through the `core/ui-imaging` module.   

- Libraries:
  - `io.coil-kt.coil3:coil-compose`
  - `io.coil-kt.coil3:coil-network-okhttp`

**Where:**

- `core/ui-imaging`:
  - Owns the **single global ImageLoader** configuration.
  - Provides:
    - `ImageRef → ImageRequest` mapping (based on `core/model.ImageRef`).   
    - Composable wrappers like `FishImage(imageRef, modifier, contentScale)`.

- `core/ui-layout`:
  - Uses `FishImage` inside `FishTile`, `MediaMiniDetailsOverlay`, `DetailScreen` hero sections.

- `feature/*`:
  - Never talk to Coil directly.
  - Only use `FishImage` or other imaging utilities provided from `core/ui-imaging`.

**Reasoning:**  
Imaging is a core concern; centralizing it in `core/ui-imaging` respects the architecture overview and avoids diverging caching/pipeline logic.   

---

## 4. Theming & Skins

### 4.1 `core/ui-theme` – FishSkin & FishTheme

**Decision:**  
All visual styling (colors, typography, shapes, motion) is owned by a dedicated module: `core/ui-theme`.

**Where:**

- `core/ui-theme`:
  - Defines:
    - `FishSkin` data class (Classic Skin, Experience Skin).
    - Color tokens (including **source colors** for Telegram/Xtream/Local/Plex/etc.).
    - Typography tokens.
    - Shape tokens.
    - Motion tokens (focus scale, overlay fade duration, parallax coefficients).

- `app-v2`:
  - Chooses active `FishSkin` based on:
    - Start Screen skin selection,
    - profile-specific preferences (via profile data from infra/data + Firebase).   
  - Wraps the entire UI in `FishTheme(skin) { ... }`.

- `feature/*` & `core/ui-layout`:
  - Must **never** hardcode colors/fonts/shapes.
  - Always consume theme via `FishTheme.colors`, `FishTheme.typography`, `FishTheme.shapes`, `FishTheme.motion`.

**Reasoning:**  
This aligns with the module taxonomy (`core` for shared cross-cutting concerns) and supports the Classic/Experience Dual Skin concept.   

---

## 5. Layout Primitives & Rows

### 5.1 `core/ui-layout` – FishTile, FishRow, Overlays

**Decision:**  
Reusable layout components live in `core/ui-layout`. No feature module may implement its own Tile/Row primitives.

**Where:**

- `core/ui-layout` defines:

  - `FishTile`:
    - Renders:
      - Poster/image (through `core/ui-imaging`).
      - Source frame (multi-color border for Telegram / Xtream / Local / future sources).
      - Progress bar (if resume point exists).
      - Title & subtitle (type-dependent).
    - Handles focus scaling up to **1.4×** (via `FishTheme.motion`).
    - Exposes callbacks:
      - `onPlay()` (tile tap / OK).
      - It does **not** handle details or kids actions.

  - `FishRow`:
    - Wraps `LazyRow` with:
      - Focus grouping and focus restoration.
      - Consistent spacing, padding and header integration.
    - Supports DPAD and touch equally.

  - `MediaMiniDetailsOverlay`:
    - Central overlay above the focused row.
    - Displays:
      - miniature poster,
      - title,
      - year, duration,
      - season/episode,
      - rating,
      - optional source composition hints.
    - Exposes callbacks:
      - `onOpenDetails()`
      - `onKidsAction()` (e.g. “Add to Kids”, “Open in Kids profile”)
    - Triggered by focus-dwell logic (see `core/ui-focus`).

  - `RowHeader`:
    - Title e.g. “MOVIES (236)”
    - Subtitle describing the row’s context.

**Feature modules** use these primitives to compose Home, Detail, TelegramVerse, etc., but **do not reimplement** their own tiles or rows.

---

## 6. Focus & DPAD Handling

### 6.1 `core/ui-focus` – DPAD Engine

**Decision:**  
All TV DPAD and focus behavior is implemented in the module `core/ui-focus`, using Jetpack Compose focus APIs and patterns inspired by dpad-centric open-source projects.   

**Where:**

- `core/ui-focus` provides:

  - `dpadFocusableTile()`:
    - A `Modifier` extension encapsulating:
      - `Modifier.focusable()`
      - `onFocusChanged { … }` to update UI state
      - setting the active `focusedTile` for overlays

  - `dpadRowFocusManager`:
    - Helps `FishRow` coordinate:
      - left/right movement between tiles
      - up/down movement between rows & overlay
      - correct “sticky” focus when returning to a row.

  - `FocusSettlingController`:
    - Implements the delay logic (e.g. 400–600ms) before showing the Mini Detail Overlay when a Tile remains focused.
    - Hides overlay immediately on focus loss.

**Feature modules** must **not** directly intercept key events for DPAD; they use `core/ui-focus` to attach focus behavior to their rows/tiles.

**Reference Patterns:**

- Patterns adapted (not imported as dependencies) from:
  - `thesauri/dpad-compose`
  - `farmerbb/Compose-TV-Example`
  - `fudge_tv_compose_library` ideas for focus zones and DPAD design.   

---

## 7. Animation & Motion

### 7.1 Compose Animation System

**Decision:**  
All UI animations (including tile scaling, overlay transitions, Experience Skin enhancements) use Jetpack Compose’s animation APIs.

**Where:**

- `core/ui-theme`:
  - Defines `FishMotionTokens`:
    - `tileFocusScale = 1.4f`
    - `overlayFadeInDurationMs`
    - `overlayFadeOutDurationMs`
    - `parallaxEnabled` (Classic: false, Experience: true)
    - `parallaxDepth`

- `core/ui-layout`:
  - `FishTile`:
    - Uses motion tokens to scale to 1.4× on focus.
  - `MediaMiniDetailsOverlay`:
    - Uses `AnimatedVisibility` with fade/slide transitions based on `FishTheme.motion`.
  - Rows:
    - may slightly emphasize headers in Experience Skin via animation.

**No custom animation frameworks** are allowed. All motion must be expressed via Compose animation primitives.

---

## 8. Cloud & Profiles in UI

### 8.1 `infra/firebase` + Domain + Onboarding UI

**Decision:**  
Firebase (Auth + Firestore) is the backbone for:

- Google sign-in
- Profile sync across devices
- Resume state sync

**Where:**

- `infra/firebase`:
  - Wraps Firebase Auth and Firestore APIs.
  - Exposes interfaces like `AuthRepository`, `RemoteProfileRepository`, `RemotePlaybackProgressRepository`.

- `core/feature-api` / Domain:
  - UseCases like:
    - `SignInWithGoogleUseCase`
    - `SyncProfilesUseCase`
    - `SyncPlaybackProgressUseCase`
    - `DetermineStartDestinationUseCase`   

- `feature/onboarding`:
  - On Start Screen:
    - Renders Google Connect button hooked to `SignInWithGoogleUseCase`.
    - Shows whether profiles are synced / available from cloud.
    - Only displays state from Domain; does not talk to Firebase directly.

- `feature/home` & `feature/profile`:
  - Present cloud-backed profiles and cross-device resume information using Domain data.

**Reasoning:**  
This matches the architecture: infra handles Firebase, Domain orchestrates, feature UIs just reflect them.   

---

## 9. Player UI Integration

### 9.1 `player/internal` + `feature/player-ui`

**Decision:**  
The Internal Player (SIP) and the mini player appear in the UI under a specific feature module, **using strictly the player contracts** from `core/player-model` and `playback/domain`.   

**Where:**

- `player/internal`:
  - Implements `InternalPlayerSession`, `InternalPlayerState`, `PlaybackSourceResolver`, MiniPlayer state etc.

- `feature/player-ui` (or equivalent):
  - Renders:
    - full-screen player chrome (controls, scrubber, CC button)
    - MiniPlayerOverlay using `MiniPlayerState` and `MiniPlayerManager` (from `player/miniplayer`).   
  - Connects to:
    - Domain UseCases (PlayItemUseCase, Pause, Resume, KidsGate)
    - Player state flows (position, state, error state)

**Rules:**

- UI modules must **never** talk to pipelines or transport directly for playback.  
- All playback is orchestrated via Domain + `playback/*` modules and `InternalPlayerSession`.

---

## 10. Logging in UI

### 10.1 UnifiedLog

**Decision:**  
All UI modules must use the `UnifiedLog` façade from `infra/logging`.   

**Where:**

- `feature/*` and `core/ui-*`:
  - Use:
    - `UnifiedLog.d("HomeScreen", "Overlay shown for tile $id")`
    - `UnifiedLog.w("TelegramVerse", "No chats available")`
  - Never use:
    - `android.util.Log.*`
    - `println`
    - `Timber.*`

**Reasoning:**  
This respects the logging contract and centralizes logging behavior.   

---

## 11. Data & Domain Boundaries in UI

### 11.1 What UI Consumes

UI must only consume:

- Domain UI models based on:
  - `NormalizedMedia`, `MediaVariant`
  - `TelegramChatSummary`, `TelegramMediaSummary`
  - `XtreamVodSummary`, `XtreamVodDetails`  
  - `LibraryStats`, `ProfileSummary`, `KidsProfileSummary`

**No UI module may depend on**:

- `RawMediaMetadata` directly.
- `TelegramMediaItem`, `XtreamVodItem`, or any pipeline DTO.

This directly follows the **MEDIA_NORMALIZATION_CONTRACT** and layer architecture.   

---

## 12. Summary

To achieve the v2 UI vision with **best quality and full architectural compliance**, we will:

- Use **Jetpack Compose** (with tv-material) for all UI.
- Handle navigation via **Navigation Compose**.
- Implement imaging with **Coil 3** through `core/ui-imaging`.
- Provide theming and dual skins via `core/ui-theme` and `FishSkin/FishTheme`.
- Implement rows, tiles, overlays in `core/ui-layout`.
- Control DPAD/TV focus via `core/ui-focus`, using Compose focus APIs and patterns adapted from high-quality open-source TV projects.
- Express motion uniformly through Compose animation APIs and `FishMotionTokens`.
- Use Firebase integration in `infra/firebase` + Domain use cases for cloud-backed profiles and cross-device resume.
- Keep player integration within `player/internal` and `feature/player-ui`, strictly avoiding pipeline/data coupling.
- Log via `UnifiedLog` only.
- Ensure UI consumes only domain-level models, never pipeline-level types.

No other UI toolkits or competing patterns are allowed.

This document, together with:

- `UI_Vision_spec.md`,   
- `GLOSSARY_v2_naming_and_modules.md`,   
- `MEDIA_NORMALIZATION_CONTRACT.md`,   
- `LOGGING_CONTRACT_V2.md`,   
- `AGENTS.md`,   
- `ARCHITECTURE_OVERVIEW.md`,   
- `ROADMAP.md`,   

forms the **complete guide** for building and evolving the v2 UI.


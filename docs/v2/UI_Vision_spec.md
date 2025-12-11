# FishIT Player v2 — UI Vision (Experience Specification)

This document describes the **target UI experience** for FishIT Player v2.  
It defines how the app should look, feel, and behave from the first launch through everyday usage, across TV and touch devices, Classic and Experience skins, and multi-source content.

It is **implementation-agnostic**: no specific UI frameworks or libraries are referenced.  
It is the UX master plan.

---

## 1. Dual Skin Concept

FishIT Player v2 supports two visual skins:

### 1.1 Classic Skin (Default)

- Calm, clean, and information-rich
- Minimal motion (only essential focus/transition feedback)
- Matte, subtle colors
- Clear, legible typography
- Structured, predictable layouts
- No parallax, no strong glow effects
- Ideal for:
  - TV users
  - Users who prefer clarity and stability
  - Long viewing / browsing sessions

### 1.2 Experience Skin (Optional, User-Selectable)

- Same layout structure as Classic Skin, but more expressive:
  - enhanced depth (subtle parallax)
  - ambient glows and color accents
  - richer transitions and overlay motion
- Feels cinematic and futuristic, but must not overwhelm:
  - movement is soft, never frantic
  - visual effects enhance, not distract

**Key rule:** The UI structure (screens, tiles, rows, overlays) remains identical between skins.  
Only presentation (color intensity, animation, depth effects) changes.

Users choose their skin during the Start Screen flow and can change skins later in settings.

---

## 2. First Launch — Start Screen / Onboarding

On first app launch, the user is greeted with a **Start / Onboarding Screen**.  
This screen is the central entry point for:

- Connecting content sources
- Creating profiles
- Choosing UI skin
- Understanding core features
- Optionally skipping setup and using local-only mode

### 2.1 Elements on the Start Screen

The Start Screen includes:

1. **Welcome area**:
   - App name, logo/identity
   - A short description of what the app does (multi-source media player)

2. **Source connection cards**:
   - **Connect Telegram**
     - Starts Telegram login flow (phone number → code → optional password)
   - **Connect Xtream**
     - Supports M3U URL input and/or host/username/password entry
   - Optional:
     - Indicators for whether each source is already connected

3. **Profile section**:
   - Button to **Create Profile** (name, type: adult/kids, optional PIN)
   - Option to open a **Profile Manager** screen (later, for advanced flows)

4. **Cloud / Account connection**:
   - Option to **Sign in with Google** (or similar) for:
     - Cloud-synced profiles
     - Cloud-synced watch progress and preferences
   - After connection:
     - Existing profiles and progress from the cloud can be loaded and merged with local data

5. **Skin selection**:
   - Classic Skin (default)
   - Experience Skin (opt-in)
   - Short visual descriptions/previews of both

6. **Local-only option**:
   - A clearly labeled button:
     - “Continue without connecting accounts”
   - This launches the app in a **Local-Only Mode**:
     - Only local media is available at first
     - Sources can still be connected later via Settings

7. **Optional feature introduction overlay**:
   - A small, dismissible overlay describing:
     - Tiles and Rows
     - Mini detail overlays
     - Kids profiles
     - Source merging concept (one work, many origins)

### 2.2 Background Processing After Setup

As soon as:

- at least one source (Telegram or Xtream) is successfully configured
- and the user completes or skips the initial setup

the app:

- immediately starts background catalog scans for connected sources
- populates internal data structures with raw media metadata
- creates / updates the content library

**Goal:**  
By the time the user first navigates to the Home Screen, there is already a noticeable set of content available (movies, series, live channels, music, audiobooks).

---

## 3. Profiles & Cloud Sync

### 3.1 Profiles

The app supports multiple profiles:

- **Adult profiles**
  - can see all content (subject to source availability)
  - can manage kids profiles
  - can have an optional PIN for protection

- **Kids profiles**
  - see only content that is explicitly allowed or classified as kids-safe
  - have a simpler, more constrained UI (future phase)
  - cannot manage settings or other profiles

Profile creation is possible directly from the Start Screen.

### 3.2 Cloud Connection (Google + Firebase Conceptually)

User can optionally connect the app to an online account (e.g., Google sign-in with Firebase authentication and storage):

- When connected:
  - Local profiles & preferences can be uploaded to the cloud
  - Cloud-stored profiles and watch-progress can be downloaded and merged
- On a different device:
  - User signs in with the same Google account
  - Profiles and watch-progress become available
  - Resume points work across devices

### 3.3 Cross-Device Resume

- Each work has a canonical media ID
- Watch progress (position, duration, finished/unfinished) is stored per profile
- When the same user profile is active on another device:
  - The app retrieves progress and enables “Resume” tiles globally

---

## 4. Home Screen — Unified Media Rows

The Home Screen is the **central hub** of the app.

It displays all works from all sources, grouped by **media type**, not by origin:

- Row: **Movies**
- Row: **Series**
- Row: **Live TV**
- Row: **Music**
- Row: **Audiobooks / Hörspiele**

### 4.1 Source-Agnostic Category Rows

Works from Telegram, Xtream, local storage (and future sources) appear:

- in the appropriate category row (Movies, Series, etc.)
- deduplicated when a work exists in multiple sources (all sources merged into one Tile, with multi-source visualization)

### 4.2 Source Types as Origins, Not Categories

- There is no separate “Telegram row” or “Xtream row” on the main Home Screen.
- Telegram chats, Xtream playlists, and local directories are **sources**, not UI categories.
- Their role is expressed via **source frames** on Tiles (see below), not via separate rows.

This makes the Home Screen:

- clean and intuitive
- focused on the user’s mental model: “I want to watch a movie/series/live TV/etc.”

---

## 5. Tile Design — Visual & Behavioral Specification

Tiles are the **primary interaction elements** for content.

### 5.1 Tile Shapes by Media Type

Each media type has a distinct shape:

- **Movies:** vertical 2:3 posters
- **Series:** 16:9 landscape banners
- **Live TV:** 1:1 rounded squares (channel logos)
- **Music:** circular tiles (album/single artwork)
- **Audiobooks / Hörspiele:** softly rounded squares (evoking book covers)

These shapes should be consistent and immediately recognizable across the UI and skins.

### 5.2 Source Frame (Colored Multi-Source Border)

Each Tile is wrapped in a **thin, colored frame** that indicates the origin(s) of the content:

- Telegram: blue
- Xtream: red
- Local: green
- Future sources:
  - Plex (e.g., gold)
  - Jellyfin (e.g., purple)
  - etc.

When a work exists in multiple sources, the frame visualizes this as **segments**:

- 1 source → single-color frame
- 2 sources → two-part frame (50/50 distribution)
- 3 sources → three-part frame (⅓ each)
- 4+ sources → up to 4 visible segments, remaining combined or integrated into a subtle pattern

The source frame:

- is **visually integrated**, not overpowering
- grows proportionally with the Tile when focused (scales with the Tile)
- is present in both Classic and Experience skins:
  - Classic: static, matte colors
  - Experience: can have glow, gradient, or subtle animations

### 5.3 Focus Behavior & Scaling

When a Tile gains focus (via DPAD or touch-hover):

- It smoothly scales up to **1.4×** its normal size.
- Scaling is centered, avoiding clipping with neighboring tiles.
- Classic Skin:
  - pure scaling, no parallax
- Experience Skin:
  - scaling + gentle parallax and/or tilt
  - optional shadow depth animation

The Tile remains the main visual focal point when selected.

### 5.4 Direct Playback Behavior

The Tile itself acts as a **Play Button**:

- If the work has a resume point:
  - Playing from the Tile resumes from the last known position.
- If there is no resume point:
  - Playing from the Tile starts from the beginning.

This applies to:

- DPAD (OK/Enter on a focused Tile)
- Touch (simple tap on a Tile)

There is no long-press requirement for playback.  
Playback is always “one action away”.

### 5.5 Progress Indicator

If a work has been partially watched:

- Each Tile shows a **thin progress bar** along its bottom edge.
- The bar:
  - visually indicates the percentage watched
  - updates smoothly over time
  - disappears when the content is fully watched or reset

This progress bar is:

- present in both skins
- neutral in color (not tied to source colors)

### 5.6 Titles & Metadata Visibility (Touch and TV)

On TV:

- Title and essential metadata (e.g., season/episode or year) are always visible on the focused Tile.

On touch devices:

- While scrolling:
  - Titles and subtitles **fade in automatically** for the Tile under the finger / in focus.
  - No long-press is required to see the title.
- When the user stops briefly over a Tile:
  - Titles remain visible; optionally fade out after a short delay if unfocused.

Metadata shown on the Tile itself is minimal; deeper details are shown in the Mini Detail Overlay.

---

## 6. Mini Detail Overlay — Secondary Action Layer

The Mini Detail Overlay appears when a Tile remains focused, offering a richer view without leaving the Home Screen.

### 6.1 Triggering the Overlay

- TV (DPAD):
  - When a Tile stays in focus for a short duration (approx. 400–600ms), the overlay appears.
- Touch:
  - When the user pauses over a Tile or taps the Tile lightly and holds very briefly (short dwell), the overlay appears.
  - The overlay should never feel like a long-press “hack” but a natural extension of focusing on a Tile.

The overlay does **not** show for rapid scrolling; it appears when the user clearly intends to inspect a Tile.

### 6.2 Visual Appearance

- Displayed centered above the Row that contains the focused Tile.
- Comes with a semi-opaque black or very dark background:
  - underlying content remains visible but dimmed
  - overlay panel silhouette is clear and readable
- Includes:
  - miniature poster or thumbnail
  - work title
  - year
  - duration (formatted)
  - media type (e.g., Movie, Series, Live TV, Music, Audiobook)
  - for series: season/episode information
  - rating (if available)
  - optional small visual hint of source frame composition (Telegram/Xtream/Local mix)

Classic Skin:

- simple fade-in/out
- no strong motion or distortion

Experience Skin:

- overlay may slide in and slightly scale, with smooth transitions
- subtle depth/shadow effects

### 6.3 Overlay Interaction Model

The overlay acts as a **second actionable surface**, separate from the Tile:

- Clicking/tapping anywhere in the main overlay area:
  - opens the full **Details Screen** for that work.

Inside the overlay, additional buttons/controls may be placed, such as:

- **Kids action button**:
  - “Add to kids profile”
  - or “Open in kids profile”
- Future optional buttons:
  - “Add to watchlist”
  - “Mark as watched”
  - etc.

The overlay **does not** start playback.  
Playback is always triggered via the Tile itself.

### 6.4 Navigation (DPAD and Touch)

- DPAD:
  - Up from the focused Tile → moves focus into the overlay
  - Once in overlay:
    - Left/Right moves between buttons within the overlay
    - OK/Enter activates selected button (e.g., Open Details, Kids)
  - Down from overlay → returns focus to the underlying Tile/Row
  - Back → closes the overlay; if already closed, navigates back in screen hierarchy

- Touch:
  - Tap on Tile → direct Play (resume/start)
  - Tap on overlay → open Details Screen
  - Tap on Kids button → kids-specific action
  - Tap outside overlay (if appropriate) → dismiss overlay

---

## 7. Kids Mode & Kids Actions

The UI integrates Kids functionality directly into Tiles and Overlays.

### 7.1 Kids Profiles

- Users can create one or more Kids profiles.
- Kids profiles:
  - see only allowed content
  - can have restricted UI (future design)
  - cannot modify settings or other profiles

### 7.2 Kids Actions on the Overlay

The Mini Detail Overlay includes:

- a button to **make this work available in kids profile** or
- a button to **open/play this work in a chosen kids profile** (if appropriate and allowed)

Design principles:

- The Kids action must be:
  - clearly labeled and visible
  - non-intrusive for users without kids
- Domain logic determines:
  - whether content is allowed for Kids
  - which Kids profiles can be targeted
  - what happens after pressing the Kids action (e.g., open in Kids UI, mark in kids library, etc.)

---

## 8. Input Mapping & Context Awareness

The UI must support multiple input types:

- TV remote / DPAD
- Touchscreen
- Keyboard
- Gamepad

### 8.1 DPAD Behavior

DPAD mappings should be **context-sensitive**, but consistent:

- In Home Rows:
  - Left/Right → navigate between Tiles
  - Up/Down → move between Rows or into the Overlay
  - OK:
    - on Tile → Play (resume/start)
    - on Overlay → activate selected button (e.g., Details / Kids)
  - Back:
    - to close overlay if open
    - else to navigate back in navigation stack

- In Details Screen:
  - OK enters default action (Play, Next episode, Play trailer, etc.) based on context.

### 8.2 Touch Behavior

- Tap:
  - directly triggers the primary action depending on the element:
    - Tile → Play
    - Overlay → Details
    - Kids button → Kids action

- Scroll:
  - Titles and meta (inside Tiles) automatically fade in to help identify works.
  - The UI should feel “alive but not noisy” during scroll:
    - Titles appear and persist briefly as the user browses.

---

## 9. Extended Screens Beyond Home

While the Home Screen is the core, the UI vision includes dedicated screens:

### 9.1 Details Screen

After opening Details from the overlay or elsewhere:

- Full hero visual (poster/backdrop)
- Rich metadata:
  - title, type, year, duration, rating
  - genres, description, cast (if available)
- Variant / Source selection:
  - different qualities (1080p, 720p, audio-only)
  - different sources (Telegram, Xtream, Local)
- Episode navigation for series:
  - Season list
  - Episode rows
- Kids options:
  - allow/block for kids
- Action buttons:
  - Play / Resume
  - Play next episode
  - Add to watchlist
  - etc.

### 9.2 Telegram Media Screen (“TelegramVerse”)

A dedicated area for Telegram-specific browsing:

- Chat summaries grouped by “heat” classification:
  - **HOT**, **WARM**, **COLD** chats
- Each chat card:
  - shows title, media count, last activity timestamp
  - uses color or accents matched to heat level
- Within each chat:
  - rows for videos, audio (Hörspiele), documents, etc.
- Chats are **not** separate rows on the Home Screen but are accessible here and feed media into the unified Home Rows.

### 9.3 Live TV Screen

A dedicated area for live television:

- Sections:
  - My channels
  - By category
  - Currently on air
- Channel tiles:
  - square logos
- Simple zapping behavior:
  - Left/Right for channels
  - Up/Down for categories or sections
- Timeshift features are planned as a future improvement, but not initially required.

### 9.4 Search Screen

Unified search:

- Single search entry point
- Results organized in rows:
  - Movies
  - Series
  - Live channels
  - Music
  - Audiobooks
  - Possibly Telegram chats as another group
- Each result uses the same Tile + Overlay patterns as the Home Screen.

---

## 10. Counters & Stats in the UI

The UI uses small, unobtrusive counters to enhance orientation:

- Total number of works in each row:
  - “MOVIES (236)”
  - “SERIES (104)”
- Source-based totals:
  - “Telegram: 58”
  - “Xtream: 178”
  - “Local: 32”
- Category-specific stats:
  - Horror movies: count
  - Documentaries: count
- Global totals:
  - “Total works: 742” (deduplicated across sources)

These counters:

- appear primarily in row headers and filter areas
- are textual, not flashy
- must not clutter the visual design

---

## 11. Accessibility & Comfort

The UI must be:

- readability-first:
  - high contrast
  - clear fonts
- comfortable for longer sessions:
  - no sudden animation bursts
  - no rapid color/pattern strobing
- controllable in terms of motion:
  - Experience Skin can be disabled at any time
  - Classic Skin offers a low-motion, low-distraction mode

Focus outlines, tile scaling, and overlay behavior should be consistent and predictable.

---

## 12. Future-Proof Expansion

The design must remain stable while supporting:

- New sources (e.g., Plex, Jellyfin, more IPTV providers)
- New media types (e.g., podcasts, radio streams, short-form content)
- Advanced recommendations and personalized sections
- Casting support
- Synced progress and profiles across devices and platforms
- Additional kids features (Kids-specific Home, curated content)

All new features should use the existing patterns:
- Tiles
- Rows
- Overlays
- Skin system
- Profile and Kids awareness

This ensures FishIT Player v2 feels coherent and future-ready.


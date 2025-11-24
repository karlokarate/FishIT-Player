# Internal Player Refactor – ChatGPT / Agent Operating Instructions

This document describes **how ChatGPT (and related agents)** must operate on the FishIT Internal Player refactor.

---

## 1. Single Source of Truth

- The **single source of truth** for all modules during this refactor is the set of files in the **project folder** for this ChatGPT project.
- In that folder:
  - The file `m3usuite.zip` contains the **old project state**, including:
    - the full legacy `InternalPlayerScreen` implementation
    - all historical modules relevant to the player
  - All newly refactored modules (their latest versions) are uploaded **as standalone files** at the same level in the project folder.

**Rules:**

- ✅ Always treat the **uploaded project files** as canonical.
- ✅ Use the **legacy modules from `m3usuite.zip`** strictly as a **reference** to migrate all features.
- ❌ Do **not** rely on ChatGPT’s internal cache of previous code when editing modules.
- ❌ Do **not** assume older snippets from previous answers are still correct; always re-read the current module from the project folder.

---

## 2. Workflow for Refactoring a Module

Whenever ChatGPT is asked to modify or refactor a module:

1. **Read from the project folder**
   - Load the **full current version** of the target module from the project folder.
   - Load all **referenced and dependent modules** that it interacts with (as needed).

2. **Ignore internal cache**
   - Do **not** trust previously generated versions of that module stored in ChatGPT’s memory.
   - The only valid version is the file that currently exists in the project folder.

3. **Apply changes based on the current phase**
   - Use the **phase plan** from the roadmap markdown.
   - Only implement what is required for the **current phase** (and any clearly necessary prerequisites).
   - If a later phase will change or extend a feature, placeholders are allowed **only if explicitly marked as to-be-implemented in that later phase**.

4. **Output the full module**
   - Always output the **entire file content** for each affected module, even if only a minor change was made.
   - Do not use `...` or truncated sections for code that should remain functional.
   - Placeholders that would break features are permitted **only when clearly tied to a future phase** that will fill them in.

5. **Assume final package paths**
   - Even though the files live side-by-side in the project folder during the refactor, their package declarations and imports must reflect their **final location in the repository**:
     - e.g. `package com.chris.m3usuite.player.internal.session` etc.
   - The code must reference other modules (imports/packages) as if they were already in their proper locations under `com.chris.m3usuite.player.internal.*`.

6. **Build stability during phases**
   - During intermediate phases, the overall project **does not have to build**.
   - It is acceptable for partially refactored code to:
     - reference modules that do not exist yet,
     - or reference APIs that will be created in later phases.
   - **After all phases are complete**, the final goal is a fully buildable project where:
     - the Internal Player works with both Xtream and Telegram pipelines,
     - the legacy screen is no longer used.

---

## 3. Telegram / TDLib Constraints

- The Internal Player must support **Telegram streaming / file playback** using **tdlib-coroutines**.
- Integration rules:
  - Always follow the **official `tdlib` and `tdlib-coroutines` documentation**.
  - Do not introduce ad-hoc behaviours that contradict TDLib semantics.
  - Any deviations must be:
    - strictly necessary (e.g., platform limitations),
    - clearly documented in comments.

- Telegram integration modules include (non-exhaustive):
  - `T_TelegramServiceClient`
  - `T_TelegramFileDownloader`
  - `StreamingConfig`
  - `TelegramFileDataSource`
  - `TelegramContentRepository`
  - `TelegramLogRepository`

These must be treated as **existing, stable building blocks**, not re-implemented.

---

## 4. Xtream Pipeline Constraints

- The Internal Player must remain compatible with the **existing Xtream pipeline**:
  - Existing URL formats and headers
  - `DelegatingDataSourceFactory`
  - `RarDataSource` for local/remote RAR-based content
  - All legacy behaviours for VOD, series, and live Xtream playback

- When refactoring:
  - Do not break URL routing or header logic.
  - Reuse the existing data-source stack where possible.
  - Any change that might affect Xtream routing must be checked against the legacy implementation in `m3usuite.zip`.

---

## 5. Phase-driven Development

- The refactor is organized into **phases** (see the roadmap markdown).
- ChatGPT must:
  - Respect the **phase order**.
  - Work on **one phase at a time** (or a clearly delimited subset).
  - After each completed step, ensure that the markdown roadmap is updated to reflect:
    - ✅ which steps are done,
    - ⬜ which are still open.

- After each completed phase (or major step):
  - The roadmap markdown must be **re-issued in full** with updated checkboxes.
  - It must always be obvious which parts are completed and which are pending.

---

## 6. Output Policy

Whenever ChatGPT modifies the codebase as part of the refactor:

- ✅ Output **full contents** of every affected module.
- ✅ Ensure all imports and package declarations are consistent.
- ✅ Mention any temporary placeholders explicitly, tied to a specific phase and step.
- ❌ Do **not** omit unchanged code sections with comments like `// ...`.
- ❌ Do **not** provide partial diffs only; full files are required for safe copy-paste into the project folder.

---

## 7. Professionalism, Updates, and External Libraries

- Always prefer:
  - Clear separation of concerns (domain vs. session vs. UI).
  - Reuse of existing helpers and modules instead of duplicating logic.
  - Up-to-date stable versions of:
    - AndroidX Media3/ExoPlayer
    - tdlib-coroutines
    - Kotlin and coroutines
    - Static analysis tools (`ktlint`, `detekt`)

- Whenever there are **high-quality external libraries or patterns** that can improve:
  - logging or diagnostics,
  - leak detection,
  - structured architecture (e.g. modular boundaries, architecture rules),
  - testing (e.g. Robolectric, JUnit),

  ChatGPT should:
  - Clearly point them out,
  - Briefly explain how they could be integrated,
  - But not force their usage if they are out of scope for the current phase.

Examples of useful external tools:
- **LeakCanary** – for detecting memory leaks related to player/view lifetimes.
- **StrictMode** – for catching main-thread I/O or inappropriate network calls in debug builds.
- **ArchUnit** – for enforcing architecture constraints in unit tests (package/module boundaries).
- **Robolectric** – for UI and lifecycle testing without a device.

---

## 8. Goal State

At the end of all phases:

- The **InternalPlayerScreen**:
  - Is thin and purely orchestration-focused.
  - Is decoupled from TDLib, ObjectBox, Xtream, and low-level media details.
  - Talks only to:
    - domain controllers/managers,
    - session/player abstractions,
    - UI modules.

- The player:
  - Can play both Xtream and Telegram content reliably.
  - Supports all legacy features:
    - Resume, kids/screentime
    - VOD/Series/Live modes
    - Live/EPG overlays, channel switching
    - Subtitles with configurable style
    - Trickplay and seek preview
    - TV-remote-specific behaviour (DPAD & focus)
    - Mini-player or PiP on supported devices
    - Rich diagnostics and an internal debug screen.

- The **legacy InternalPlayerScreen** is no longer needed for runtime behaviour and only remains (if at all) as a historical reference.

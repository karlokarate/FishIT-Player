# m3uSuite – Agents & Architecture Guide (consolidated)

This file is the single source of truth for contributors (Codex + humans). It supersedes split/older variants and always provides a complete overview.

Update Policy (mandatory)
- Always keep these files up to date and in sync with reality:
  - `AGENTS.md` (this file)
  - `ROADMAP.md`
  - `CHANGELOG.md`
- After every commit/merge to `master` and a successful user build/test:
  - Update CHANGELOG.md with a brief note of changes (or move items from roadmap to changelog).
  - Update ROADMAP.md (remove completed items; keep near-/mid-term actionable items only).
  - If the change is substantial (new modules/flows/features or architectural impact), also update AGENTS.md so a one-stop read remains accurate.
- Diffs must be shown first; only apply after user approval.

Quick Build & Test
- JDK 17; Gradle wrapper
- Commands: `./gradlew --version`, `./gradlew build`, `./gradlew test`
- Optional: `./gradlew lint ktlintFormat detekt`

Where to find the full overview
- The complete, continuously updated architecture overview (modules, flows, responsibilities) is maintained in:
  - `ARCHITECTURE_OVERVIEW.md` (derived from AGENTS_NEW.md)
- Read `ARCHITECTURE_OVERVIEW.md` for a deep, comprehensive view. Keep it updated whenever new modules/features are added.

Short bullet summary (current highlights)
- Single-module app (`app`) with Compose UI, Room DB/DAOs, WorkManager, DataStore, Media3 player, OkHttp/Coil.
- Start/Home shows Serien, Filme, TV; Kids get filtered content (MediaQueryRepository), no settings/bottom bar, read‑only favorites.
- Backup/Restore present in Setup (Quick Import) and Settings (Quick Import + full section). Drive client optional (shim by default).
- Player fullscreen with tap-to-show overlay controls; Live favorites reorder fixed/stable.
- EPG: persistent Now/Next cache (Room) with XMLTV fallback; background refresh worker; Live tiles show title + progress.
- Unified UI polish: Accent tokens (adult/kid), carded sections (`AccentCard`), gradient + glow background with blurred app icon; kid profiles use a vibrant palette.
- Kid/Guest profiles: per‑profile permissions (Settings/Quellen, External Player, Favorites, Search, Resume, Whitelist).
- Kid filtering: Effective allow = item allows ∪ category allows − item blocks; category‑level whitelist + per‑item exceptions via admin UI.
- Favorites: Live favorites are read‑only when the profile lacks edit permission (default Kid/Guest).
- Admin tools: Whitelist editor (category badges, expandable item lists with checkboxes) and “Berechtigungen” editor per profile.

Policies (Do/Don't)
- Preserve existing flows (EPG/Xtream, player paths, list/detail) unless requested.
- Do not modify `.gradle/`, `.idea/`, or `app/build/` artifacts; avoid dep upgrades unless fixing builds.
- Unit tests prioritized; UI/instrumented tests only with running emulator.
- WSL/Ubuntu recommended; network allowed for Gradle.
- Enforce profile permissions rigorously; do not expose admin‑only affordances (whitelist/favorites/Quellen/Settings) without permission.
- For kid/guest reads, always use `MediaQueryRepository`; do not bypass via raw DAO queries in UI paths.

For the complete module-by-module guide, see `ARCHITECTURE_OVERVIEW.md`.

---

Recent
- EPG: Persistent Now/Next cache (`epg_now_next`) + XMLTV multi-index; fallback aktiv auch ohne Xtream; periodic refresh (15m) + stale cleanup.
- UI: Live tiles enriched with current programme + progress bar.
- Xtream: Detection supports compact stream URLs; import merges missing `epg_channel_id` from existing DB by `streamId`.
- UI polish: Long-press reordering for Live favorites (touch-friendly), carded look across Start/Library/Details/Setup, Accent/KidAccent tokens, background glow treatment.
- Profiles/Permissions: Added Guest profile type; per‑profile permissions with enforcement (Settings route gating, external player fallback to internal, favorites/assign UI gating, resume visibility, whitelist editing).
- Kid-mode correctness: Home refresh now uses filtered queries; favorites read‑only for restricted profiles; “Für Kinder freigeben” visible only when permitted.
- Whitelist UX: Category‑level allow with item‑level exceptions; admin sheet in ProfileManager to manage both.
- Data: New tables `kid_category_allow`, `kid_content_block`, `profile_permissions`; DB schema bumped with idempotent migrations.

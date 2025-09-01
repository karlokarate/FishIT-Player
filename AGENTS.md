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

Policies (Do/Don't)
- Preserve existing flows (EPG/Xtream, player paths, list/detail) unless requested.
- Do not modify `.gradle/`, `.idea/`, or `app/build/` artifacts; avoid dep upgrades unless fixing builds.
- Unit tests prioritized; UI/instrumented tests only with running emulator.
- WSL/Ubuntu recommended; network allowed for Gradle.

For the complete module-by-module guide, see `ARCHITECTURE_OVERVIEW.md`.

---

Recent
- EPG: Persistent Now/Next cache (`epg_now_next`) + XMLTV multi-index; fallback aktiv auch ohne Xtream; periodic refresh (15m) + stale cleanup.
- UI: Live tiles enriched with current programme + progress bar.
- Xtream: Detection supports compact stream URLs; import merges missing `epg_channel_id` from existing DB by `streamId`.
- UI polish: Long-press reordering for Live favorites (touch-friendly), carded look across Start/Library/Details/Setup, Accent/KidAccent tokens, background glow treatment.

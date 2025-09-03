# FishIT Player – Agents & Architecture Guide (consolidated)

This file is the single source of truth for contributors (Codex + humans). It supersedes split/older variants and always provides a complete overview.

Update Policy (mandatory)
- Always keep these files up to date and in sync with reality:
  - `AGENTS.md` (this file)
  - `ROADMAP.md`
  - `CHANGELOG.md`
- Immediately after a patch is fully applied (no waiting for external build/test):
  - Update CHANGELOG.md with a brief note of changes (or move items from roadmap to changelog).
  - Update ROADMAP.md (remove completed items; keep near-/mid-term actionable items only).
  - If the change is substantial (new modules/flows/features or architectural impact), also update AGENTS.md so a one-stop read remains accurate.
- Approvals: Routine repository/documentation updates are applied directly without prior diff approval. Approvals are only required for privileged/irreversible operations or external authentication.

Codex – Operating Rules (override)
- Scope: These rules override conflicting prior rules in this file. Where the runtime environment imposes sandbox/approval limits, Codex follows the intent and asks for minimal one‑shot approval when required.
- Single source of truth: `AGENTS.md` is the canonical source for workflow, architecture, and dependencies. All other documents (including `ARCHITECTURE_OVERVIEW.md`, `ROADMAP.md`, `CHANGELOG.md`) are derived/synced from here. In case of discrepancies, `AGENTS.md` prevails.
- Full context gathering: For a complete overview, Codex also reads ROADMAP.md, CHANGELOG.md, ARCHITECTURE_OVERVIEW.md, and the latest commits/PRs to understand the current state before making changes.
- Auto documentation upkeep: Immediately after patches, Codex updates the full documentation set (AGENTS.md, ROADMAP.md, CHANGELOG.md, ARCHITECTURE_OVERVIEW.md) and pushes to `master`. If the environment blocks direct writes or pushes, Codex prepares diffs and requests the smallest possible approval to finalize.
- Deep dependency awareness: When patching, Codex reads all relevant modules in appropriately sized batches and considers all dependent modules. `ARCHITECTURE_OVERVIEW.md` is maintained as a detailed, human‑friendly derivative; if it disagrees with this file, this file wins.
- Cascading fixes allowed: If additional modules must change to keep the system consistent after a patch, Codex proceeds to implement those changes directly under these rules.
- End‑to‑end execution: When the user requests a change/fix/implementation, Codex performs it end‑to‑end (no TODOs/placeholders). For major changes requiring iterative passes over the same files, Codex proceeds autonomously without waiting for intermediate applies, unless sandbox constraints force an approval.
- Minimize approvals: Routine repo/Documentation changes are applied directly. Approvals are limited to privileged ops, irreversible deletions, or external authentication.
- Pragmatic alternatives: If a request is technically not feasible, Codex proposes the best alternative solution and requests approval where appropriate.
- Respectful scope: Codex does not change/trim/expand modules or files without instruction, except where necessary to uphold these rules or maintain architectural integrity. Existing flows (EPG/Xtream, player paths, list/detail) must be preserved unless requested.
- Ongoing hygiene: Codex periodically tidies the repo, highlights obsolete files/code to the user, and removes uncritical leftovers (e.g., stale *.old files). Never touch `.gradle/`, `.idea/`, or `app/build/` artifacts, and avoid dependency upgrades unless fixing builds.
- Cross‑platform builds: Codex uses Linux/WSL for builds/tests via Gradle wrapper while keeping settings compatible with Windows. Ensure no corruption of Windows‑side project files.
- WSL build files: Projektstamm enthält Linux‑spezifische Ordner für Build/Tests: `.wsl-android-sdk`, `.wsl-gradle`, `.wsl-java-17`. Codex verwendet diese Ordner unter WSL; Windows‑seitige Einstellungen bleiben kompatibel.
- Tooling upgrades: If Codex needs additional tools or configuration to work better, it informs the user and, where possible, sets them up itself; otherwise it provides clear, copy‑pastable step‑by‑step commands for the user to establish the optimal environment.

Quick Build & Test
- JDK 17; Gradle wrapper
- Commands: `./gradlew --version`, `./gradlew build`, `./gradlew test`
- Optional: `./gradlew lint ktlintFormat detekt`

WSL/Linux Build & Test
- Android SDK (WSL): Setze `ANDROID_SDK_ROOT` auf den Repo‑lokalen SDK‑Pfad: `<repo>/.wsl-android-sdk`. Keine Windows‑`local.properties` committen; in WSL/CI per Env‑Vars arbeiten.
- Java 17 (WSL): Verwende den Repo‑lokalen JDK‑Pfad `<repo>/.wsl-java-17` (oder System‑JDK 17). Setze `JAVA_HOME` entsprechend. Prüfen mit `java -version` und `./gradlew --version`.
- Gradle (WSL): Immer den Wrapper `./gradlew` verwenden. Setze `GRADLE_USER_HOME` auf `<repo>/.wsl-gradle`, um Caches zu isolieren (keine Windows‑Caches anfassen).
- Lizenzen/SDK Tools: Lizenzen in WSL akzeptieren (`yes | sdkmanager --licenses`) und notwendige Pakete installieren (z. B. `platform-tools`, `platforms;android-34`, `build-tools;34.0.0`).
- Windows‑Kompatibilität: Windows‑Builds (Android Studio auf Windows) müssen weiterhin funktionieren. WSL‑Anpassungen dürfen `local.properties`, `.idea/` oder Windows‑Gradle‑Caches nicht beschädigen.

Empfohlene WSL‑Umgebungsvariablen (Shell)
```
# vom Projektstamm aus
export REPO="$(pwd)"
export ANDROID_SDK_ROOT="$REPO/.wsl-android-sdk"
export JAVA_HOME="$REPO/.wsl-java-17"
export GRADLE_USER_HOME="$REPO/.wsl-gradle"
export PATH="$JAVA_HOME/bin:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools:$PATH"
```

Git Push Policy (SSH, codex‑wsl)
- Transport: Alle Pushes erfolgen ausschließlich per SSH mit dem Deploy‑Key „codex‑wsl“.
- Remote‑URL: `git@github.com:karlokarate/m3uSuite.git` (kein HTTPS/PAT).
- Key‑Pfad (privat): `~/.ssh/id_ed25519_m3usuite` (Dateirechte 600; nicht auf Windows‑Mounts benutzen).
- Optionales SSH‑Config‑Snippet (`~/.ssh/config`):
  - Host github.com
    IdentityFile ~/.ssh/id_ed25519_m3usuite
    IdentitiesOnly yes
- Einmalige Fallback‑Nutzung ohne Config:
  - `GIT_SSH_COMMAND='ssh -i ~/.ssh/id_ed25519_m3usuite -o IdentitiesOnly=yes -o StrictHostKeyChecking=accept-new' git push origin HEAD:master`
- Hinweis: Private Keys niemals im Repo/versioniert ablegen. Der öffentliche Schlüssel ist als Deploy‑Key „Allow write“ im Repo hinterlegt.

Where to find the full overview
- The canonical, continuously updated source is `AGENTS.md` (this file).
- `ARCHITECTURE_OVERVIEW.md` is a detailed, human‑friendly derivative of this file. If discrepancies occur, this file prevails. Keep `ARCHITECTURE_OVERVIEW.md` updated whenever new modules/features are added.

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

Android Build Policy (WSL override)

- For Android builds, this repository defers entirely to the global policy at /AGENTS.md (and /mnt/c/Users/admin/AGENTS.md when applicable).
- Use ~/.local/bin/codex-android-build /home/chris <task> for all Android builds (assembleDebug/Release, tests, etc.).
- Do not use repo-local .wsl-* toolchains or env (e.g., .wsl-android-sdk, .wsl-java-17, .wsl-gradle). Any conflicting guidance below is obsolete for Codex and must be ignored.
- Always build with an ext4 project cache: --project-cache-dir /root/android/cache/projects/chris (avoids NTFS lock issues under /mnt/c).
- Ignore local.properties Windows sdk.dir warnings under WSL; the build uses ANDROID_HOME/ANDROID_SDK_ROOT from ~/.codex/android.env.
- Never modify project files to embed WSL- specific SDK/JDK/NDK paths. Windows Android Studio remains untouched.

# FishIT Player – Agents & Architecture Guide (consolidated)

**⚡ IMPORTANT - TDLib Integration (Priority 1)**: As of 2025-11-20, `.github/tdlibAgent.md` is the **Single Source of Truth** for all Telegram/TDLib integration work. **Note:** This file is not yet present in the repository; it will be added in an upcoming PR. All new Telegram features must follow this document once available. Legacy Telegram docs have been archived to `docs/archive/`.

TV/DPAD: ProfileGate + PIN/Numpad focus visuals fixed (tvClickable/tvFocusableItem + focusGroup, deterministic initial FocusRequester). See CHANGELOG 2025-10-03.
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
- Xtream single source: The folder `app/src/main/java/com/chris/m3usuite/core/xtream` is the canonical source for Xtream handling (detect, config, capabilities, client, models). Other usages must adapt to these APIs.
- ObjectBox primary store: ObjectBox is the primary local store for content (categories, live, vod, series, episodes, epg_now_next). Telegram metadata is now stored in ObjectBox as well (`ObxTelegramMessage`). Room has been removed from app flows.
- OBX ID bridging: OBX‑backed lists encode stable IDs into `MediaItem.id` for navigation: live=`1e12+streamId`, vod=`2e12+vodId`, series=`3e12+seriesId`. Detail screens resolve these IDs to OBX and build play URLs via `XtreamClient`. Legacy Room IDs remain supported where present (favorites/resume) during the transition.
- ObjectBox search: Search uses indexed `nameLower` fields and category-name matches with page-aware merging (no full in-memory merges). Avoid Room paging in Library. Prefer OBX queries.
- EPG fast path: Short EPG is fetched on-demand for visible live items and written to ObjectBox. Screens subscribe to `ObxEpgNowNext` (event-based). Room persistence has been removed.
- Xtream-first seeding: `XtreamSeeder` importiert die Kopf-Listen (Live/VOD/Series) sofort, sobald Xtream-Zugangsdaten vorhanden sind. Setup akzeptiert Xtream-Formulare oder `get.php`-Links und nutzt Letztere nur zur Credential-Ableitung; eine M3U-Parsing-Pipeline existiert nicht mehr.
- Lazy loading: Nach dem Kopf-Import werden Details/Poster weiterhin bedarfsgesteuert über Detail-Screens und `XtreamDetailsWorker` nachgeladen. Periodische Hintergrundjobs bleiben deaktiviert, damit Portale nicht unnötig belastet werden.
- Refresh: Die Settings-Aktion "Import aktualisieren" triggert `XtreamSeeder` (optional mit Discovery-Force) und plant Detail-Updates. Es gibt keinen strikten M3U-Rebuild und keinen Prune-Schalter mehr; Delta-Pfade löschen keine Einträge.
  - Orphan-Bereinigung passiert nur über separate Wartungsjobs; reguläre Delta-Läufe bleiben auf reine Anreicherung beschränkt.
- Cascading fixes allowed: If additional modules must change to keep the system consistent after a patch, Codex proceeds to implement those changes directly under these rules.
- End‑to‑end execution: When the user requests a change/fix/implementation, Codex performs it end‑to‑end (no TODOs/placeholders). For major changes requiring iterative passes over the same files, Codex proceeds autonomously without waiting for intermediate applies, unless sandbox constraints force an approval.
- Minimize approvals: Routine repo/Documentation changes are applied directly. Approvals are limited to privileged ops, irreversible deletions, or external authentication.
- Pragmatic alternatives: If a request is technically not feasible, Codex proposes the best alternative solution and requests approval where appropriate.
- Respectful scope: Codex does not change/trim/expand modules or files without instruction, except where necessary to uphold these rules or maintain architectural integrity. Existing flows (EPG/Xtream, player paths, list/detail) must be preserved unless requested.
- Ongoing hygiene: Codex periodically tidies the repo, highlights obsolete files/code to the user, and removes uncritical leftovers (e.g., stale *.old files). Never touch `.gradle/`, `.idea/`, or `app/build/` artifacts, and avoid dependency upgrades unless fixing builds.
- CI guard: `.github/workflows/Standart.yml` führt vor den Android-Matrix-Builds
  einen nativen Host-Lauf (`prepare_cross_compiling`) aus und benötigt die
  systemseitigen Build-Essentials. Beim Anpassen des Workflows darf dieser
  Schritt nicht entfallen, sonst fehlen TL-Autoquellen und Artefakte. Fehlt
  der in `BORINGSSL_TAG` referenzierte Commit upstream, fällt der Fallback-Build
  jetzt automatisch auf den aktuellen BoringSSL-HEAD zurück – bitte das Logging
  im Step "Fallback - build BoringSSL" beibehalten, damit der aktive Commit im
  Artefakt-Log ersichtlich bleibt. TDLib >1.8 nutzt einen gperf-basierten
  Generator (`generate_mime_types_gperf.cpp`) für `tdutils/generate/auto/*`; die
  Standart-Workflow-Hosts müssen deshalb `gperf` installiert haben, beim CMake-
  Aufruf `TDUTILS_MIME_TYPE=ON` setzen und den `tdmime_auto`-Build (CMake/Ninja)
  vor dem Android-Matrix-Lauf ausführen, damit `mime_type_to_extension.cpp` und
  `extension_to_mime_type.cpp` im Source-Tree bereitstehen.
- TV focus/DPAD audit: `tools/audit_tv_focus.sh` enforces rules (TvFocusRow for horizontal containers, tvClickable for interactives, no ad‑hoc DPAD). Wired into CI (`.github/workflows/ci.yml`) and fails PRs on violations.
- Central facade: Use `com.chris.m3usuite.ui.focus.FocusKit` as the single entry point for focus across all UIs (TV/phone/tablet). It provides primitives (`tvClickable`, `tvFocusFrame`, `tvFocusableItem`, `focusGroup`, `focusBringIntoViewOnFocus`), unified row wrappers (`TvRowLight` → `TvFocusRow`, `TvRowMedia`/`TvRowPaged` → FocusKit’s row engine), DPAD helpers (`onDpadAdjustLeftRight/UpDown`), grid neighbors (`focusNeighbors`), and re‑exports of `TvButton`/`TvTextButton`/`TvOutlinedButton`/`TvIconButton`). Avoid importing row engines or skin primitives directly in screens.

**UI Layout Centralization (Fish*)**
- Tokens: `ui/layout/FishTheme.kt`
  - `FishDimens` (editables): `tileWidthDp`, `tileHeightDp`, `tileCornerDp`, `tileSpacingDp`, `contentPaddingHorizontalDp`, `focusScale`, `focusBorderWidthDp`, `reflectionAlpha`, `enableGlow`, `showTitleWhenUnfocused`.
  - Apply via `FishTheme { ... }`; screens/rows read tokens through `LocalFishDimens`.
- Tile: `ui/layout/FishTile.kt`
  - Unified tile visuals: ContentScale.Fit, rounded corners, reflection, focus scale + frame + global glow (via FocusKit), optional selection frame, footer/title on focus.
  - No per‑tile scroll: `autoBringIntoView=false` (Row handles visibility).
- Row: `ui/layout/FishRow.kt`
  - `FishRowLight` (simple LazyRow + focus), `FishRow` (Media engine), `FishRowPaged` (Paging engine). Fixed spacing/padding from tokens; header is optional and only rendered when provided.
  - `header: FishHeaderData?` aktiviert die schwebende Beacon-Overlay (`FishHeaderHost`) und ersetzt klassische Section-Header.
  - Media/Paged support `edgeLeftExpandChrome` + `initialFocusEligible`; bring‑into‑view + DPAD handled centrally (no tile hacks).
- Content composition: `ui/layout/FishVodContent.kt`, `FishSeriesContent.kt`, `FishLiveContent.kt`
  - Build tile content per type (title, poster/logo, resume/epg, NEW badges, selection badges, bottom‑end actions, focus logging hook, click routing). Vod includes resume progress and NEW status.
- Shared helpers: `ui/layout/FishMeta.kt` (poster/title/plot/resume), `FishActions.kt` (Assign/Play actions; buttons non‑focusable), `FishLogging.kt` (focus logs with OBX resolution), `FishResumeTile.kt` (global resume card for VOD/Series episodes).
- Migration policy
  - Rows/Screens compose only `FishRow` + `FishTile`; all visuals/behavior come from tokens + FocusKit. No extra row frames/overlays.
  - Legacy cards removed: `BuildConfig.CARDS_V1` and `ui/cards/*` (PosterCard/ChannelCard/SeasonCard/EpisodeRow) are gone. Telegram badges now render via FishTile’s badge slot.
  - Central facade: Use `com.chris.m3usuite.ui.focus.FocusKit` for all focus primitives and rows (`tvClickable`, `tvFocusFrame`, `tvFocusableItem`, `focusGroup`, and `TvRow`). Avoid importing scattered helpers directly.
  - Start- und Library-Sektionen erzeugen Header (Text/Chip/Provider) nur noch über `FishHeaderData`; Inline-Texte/Chips (`SeriesNewChip`) wurden entfernt.
  - FishForms: TV-Form-Rows liegen in `ui/layout/FishForm.kt` (`FishFormSection/Switch/Select/Slider/TextField/ButtonRow`) und nutzen Fokus/DPAD aus FocusKit plus FishTheme Tokens. Die bisherigen `ui/forms/*` Wrapper delegieren nur noch und werden entfernt, sobald alle Screens migriert sind.
- Xtream workers & delta: Legacy `XtreamRefreshWorker`/`XtreamEnrichmentWorker` remain disabled (no‑op). Xtream content updates via `XtreamDeltaImportWorker`: periodic (12h, unmetered+charging) plus on‑demand one‑shot trigger.
  - Global gate: `M3U_WORKERS_ENABLED` in DataStore controls whether Xtream workers/scheduling and related API paths run. When false, workers early‑exit (no network), app‑start auto‑discovery/seed is skipped, and Settings actions for Xtream diagnostics/import are disabled.
  - One‑shot `ObxKeyBackfillWorker` fills missing `sortTitleLower`/`providerKey`/`genreKey`/`yearKey` for existing OBX rows.
- UI data flows: Start/Library/Details are ObjectBox-first.
  - Detail screens render the poster itself (fit scaling) and reuse the poster/backdrop as a 50% overlayed screen background (fallback to poster when no backdrop is stored); auxiliary still galleries are removed to keep focus on the main tile.
  - Library grouped views (Live/VOD/Series) use indexed OBX keys for headers (provider/genre/year) and page per visible row.
  - Start uses paged rows (Series/VOD) and a global paged Live row when no favorites exist; direct-play for Series navigates to details after on-demand OBX import if episodes absent.
- Start MVVM (2025-10): `StartScreen` fungiert jetzt als reines UI-Binding. `StartViewModel` bündelt alle ObjectBox/Settings-Flows (Serien/VOD/Live, Mixed/New-IDs, Favoriten, Permissions, Suchevents) und sendet One-Shot-Events; `StartUseCases` kapseln Repository-Aufrufe (`MediaQueryRepository`, `XtreamObxRepository`, `KidContentRepository`, Telegram). KidSelectSheet, Live-Favoriten-Picker und die Telegram-Service-Bindung liegen vorerst noch in Compose und wandern als nächstes in dedizierte VMs/Coordinator-Flows, damit der Screen komplett ViewModel-gesteuert wird.
- Cross‑platform builds: Codex uses Linux/WSL for builds/tests via Gradle wrapper while keeping settings compatible with Windows. Ensure no corruption of Windows‑side project files.
 - WSL build files: Projektstamm enthält Linux‑spezifische Ordner für Build/Tests: `.wsl-android-sdk`, `.wsl-gradle`, `.wsl-java-17`. Optional: `.wsl-cmake` (portable CMake), `.wsl-gperf` (portable gperf). Codex verwendet diese Ordner unter WSL; Windows‑seitige Einstellungen bleiben kompatibel.
- Tooling upgrades: If Codex needs additional tools or configuration to work better, it informs the user and, where possible, sets them up itself; otherwise it provides clear, copy‑pastable step‑by‑step commands for the user to establish the optimal environment.
- TDLib pinning: TDLib (JNI + Java bindings) is pinned to a specific upstream tag for reproducibility. The build script `scripts/tdlib-build-arm64.sh` checks out the tag and copies `TdApi.java`/`Client.java` from TDLib’s `example/java` into `libtd/src/main/java/org/drinkless/tdlib/`. Default pin: `v1.8.0` (the latest upstream tag as of now; override via env `TD_TAG`/`TD_COMMIT` or CLI `--ref <tag|commit>`). `Log.java` stays local to match JNI signatures.

Sandbox/WSL – Agent Execution Rules (Best Effort)
- Repo‑local tools only: never use `sudo` or modify system config. Install portable binaries under `.wsl-*` and prefer them in `PATH`.
  - `.wsl-android-sdk`, `.wsl-java-17`, `.wsl-gradle` (existing)
  - `.wsl-cmake` (portable CMake) via `scripts/setup-wsl-build-tools.sh`
  - `.wsl-gperf` (portable gperf) via `scripts/setup-wsl-gperf.sh`
- PATH precedence in shells and scripts: `export PATH="$REPO/.wsl-cmake/bin:$REPO/.wsl-gperf:$PATH"`
- Required env vars for Android builds: set `ANDROID_SDK_ROOT`, `ANDROID_NDK_HOME`, `JAVA_HOME`, `GRADLE_USER_HOME` to repo‑local folders.
- Shell I/O discipline: use `rg` for searches and `sed -n` chunking to avoid output truncation; don’t dump large files.
- Builds: prefer Ninja if present, otherwise let CMake fall back to Make; split long work into generate → build → verify steps.
Note: We ship arm64‑v8a by default. The build script can also produce armeabi‑v7a for legacy devices. Use `--only-arm64`, `--only-v7a`, or omit flags to build both. Shipping APK/Bundle stays arm64‑first unless explicitly configured otherwise.
- Network/downloads: use shallow `git clone` and robust `curl`; if a tool is missing, prefer portable installs (e.g., `apt-get download gperf` + `dpkg-deb -x` into `.wsl-gperf`).
- Safety: never touch `.gradle/`, `.idea/`, or Android Studio settings; don’t push or alter remotes/keys; keep secrets out of the repo.
- Documentation upkeep: after any patch, immediately update `CHANGELOG.md`, `ROADMAP.md`, and—if architecture changes—`AGENTS.md` and `ARCHITECTURE_OVERVIEW.md`.

Quick Verify (WSL)
- Env exports:
  - `export REPO="$(pwd)"`
  - `export ANDROID_SDK_ROOT="$REPO/.wsl-android-sdk"`
  - `export ANDROID_NDK_HOME="$ANDROID_SDK_ROOT/ndk/26.1.10909125"`
  - `export JAVA_HOME="$REPO/.wsl-java-17"`
  - `export GRADLE_USER_HOME="$REPO/.wsl-gradle"`
  - `export PATH="$REPO/.wsl-cmake/bin:$REPO/.wsl-gperf:$JAVA_HOME/bin:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools:$PATH"`
- Tools present:
  - `cmake --version | head -n1`
  - `gperf --version | head -n1`
  - `"$ANDROID_NDK_HOME"/build/cmake/android.toolchain.cmake` exists
  - `java -version` prints 17

One-liner: setup + verify
```
bash scripts/setup-wsl-build-tools.sh && \
bash scripts/setup-wsl-gperf.sh && \
export REPO="$(pwd)" ANDROID_SDK_ROOT="$REPO/.wsl-android-sdk" ANDROID_NDK_HOME="$ANDROID_SDK_ROOT/ndk/26.1.10909125" JAVA_HOME="$REPO/.wsl-java-17" GRADLE_USER_HOME="$REPO/.wsl-gradle" && \
export PATH="$REPO/.wsl-cmake/bin:$REPO/.wsl-gperf:$JAVA_HOME/bin:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools:$PATH" && \
cmake --version | head -n1 && gperf --version | head -n1 && test -f "$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" && java -version
```

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
 - Repo‑lokale Default‑Konfiguration (empfohlen):
   - `git config core.sshCommand "ssh -i ~/.ssh/id_ed25519_m3usuite -o IdentitiesOnly=yes -o StrictHostKeyChecking=accept-new"`
   - Danach funktionieren `git fetch`, `git pull` und `git push` ohne extra Env‑Variablen.

Where to find the full overview
- The canonical, continuously updated source is `AGENTS.md` (this file).
- `ARCHITECTURE_OVERVIEW.md` is a detailed, human‑friendly derivative of this file. If discrepancies occur, this file prevails. Keep `ARCHITECTURE_OVERVIEW.md` updated whenever new modules/features are added.

Short bullet summary (current highlights)
- Single-module app (`app`) with Compose UI, Room DB/DAOs, WorkManager, DataStore, Media3 player, OkHttp/Coil.
- Telegram integration (opt‑in, alpha → phase‑2 in progress): Login (Phone→Code→Passwort) mit auto DB‑Key‑Check; Settings‑Block mit Ordner-/Chat-Picker (Multi-Select, gemeinsames CSV) und kombiniertem Full-Sync; Sync-Worker mappt Nachrichten auf VOD (`MediaItem.source=TG`) oder Serie (Episode.tg*; SxxEyy-Heuristik). Player streamt `tg://file/<fileId>` via `TdlibRandomAccessSource` (Seek, progressive Download mit 512‑KiB-Readahead) und dekomprimiert `rar://msg/<msg>/<entry>` per `RarEntryRandomAccessSource`. Packaging über `:libtd` (arm64). Phase‑2: QR‑Login (done), dedicated TDLib service process (done), foreground switching on downloads/auth (done), lifecycle/network hooks (done), FCM push hooks (prepped), event‑driven indexing (basis done), LTO (next).
  - Detail: Mirror-only Detail-Navigation (`TelegramVideoDetailScreen`) ruft `TelegramLiveRepository.fetchMessageMediaItem(chatId, messageId)` direkt via TDLib auf, nutzt `DetailPage` + MediaAction (Play) und startet Playback über `PlayUrlHelper.tgPlayUri(...)`. Globale Telegram-Suchen (Start/Library) verwenden `searchAllVideos(query, limit)` als Mirror-Only Shortcut ohne OBX.
  - Telegram Serien Aggregation: Nach dem Sync werden Serien‑Chats vollständig paginiert (keine Obergrenze) und zu `ObxSeries` + `ObxEpisode` aggregiert (SxxEyy‑Heuristik). `ObxEpisode` enthält `tgChatId/tgMessageId/tgFileId` für direktes tg:// Playback. Library (Tab Serien) zeigt eine zusätzliche Row „Telegram Serien“, die nicht durch den Kategorisierer herausgefiltert wird. SeriesDetailScreen importiert für `providerKey = "telegram"` keine Xtream‑Details.
    - Event‑driven indexing (Basis): TDLib‑Service lauscht auf `UpdateNewMessage`/`UpdateMessageContent`/`UpdateFile` und persistiert Minimal‑Metadaten in `telegram_messages` inkl. `localPath`‑Updates per `fileId`. Backfill via `TelegramSyncWorker` bleibt erhalten.
    - Service IPC (Option B): `TelegramTdlibService` bietet Messenger-Kommandos `CMD_LIST_CHATS`, `CMD_RESOLVE_CHAT_TITLES`, `CMD_PULL_CHAT_HISTORY`. Der Service lädt Chatlisten via `loadChats`, hält ein Cache aus `updateNewChat`/`updateChat*` (Order über `chatPosition.order`) und beantwortet UI-Anfragen ohne zusätzlichen `getChat`-Spam (Fallback nur beim ersten Aufruf). `TelegramServiceClient` korreliert Requests (`reqId`) für UI/Worker, sodass Chat-Picker und Backfill dieselbe TDLib-Session nutzen und `getChatHistory` direkt im Service über `indexMessageContent(..)` persistiert.
  - Index/Cache: `telegram_messages` wird beim Sync befüllt (fileId/uniqueId, caption, supportsStreaming, date, thumbFileId); `localPath` wird durch DataSources aktualisiert. Minimaler Sync‑Fortschritt in Settings; täglicher Cache‑Trim (GB‑Limit) via `TelegramCacheCleanupWorker`. FCM Push integriert (Token‑Registrierung + `processPushNotification`), Service startet lazy bei Push.
  - Reflection/Gating: `TdLibReflection.extractBestPhotoSizeFileId(...)` liefert die größte Photo-Größe als File-ID; `extractThumbFileId(...)` nutzt denselben Resolver. `TgGate` fällt ohne BuildConfig-Flag auf Mirror-Only (`false`) zurück, OBX bleibt damit deaktiviert, bis ein explizites Opt-In gesetzt wird.
- TDLib packaging: `:libtd` bundles JNI `libtdjni.so` for `arm64-v8a` (primary). A `armeabi-v7a` slice can be built on demand via the build script. Native JNI is auto‑loaded via a static initializer in `org.drinkless.tdlib.Client`.
- TDLib secrets sourcing: `TG_API_ID`/`TG_API_HASH` are injected at build time without committing secrets.
   - Precedence: ENV vars (`TG_API_ID`, `TG_API_HASH`) → root `/.tg.secrets.properties` (not tracked) → `-P` Gradle props → default 0/empty.
   - To test locally: either set env vars for the Gradle run, or create a root‑level file `.tg.secrets.properties` with `TG_API_ID=...` and `TG_API_HASH=...`.
   - Player fallback: If BuildConfig keys are empty at runtime, the app‑process TDLib client (DataSource path) now falls back to `SettingsStore` (`tg_api_id`, `tg_api_hash`) so `tg://` playback can stream on‑demand after entering keys in Settings.
 - Default UA (secret): HTTP `User-Agent` is injected as `BuildConfig.DEFAULT_UA`.
   - Precedence: ENV var `HEADER` → root `/.ua.secrets.properties` (not tracked) → `-P HEADER` → empty.
   - Neither the repo nor the compiled APK contain the literal UA; app fallbacks read `DEFAULT_UA`.
- TDLib packaging: The app uses the TDLib coroutines AAR from Maven Central, which includes native libraries for both arm64-v8a and armeabi-v7a with static BoringSSL linking.
- Start/Home shows Serien, Filme, TV; Kids get filtered content (MediaQueryRepository), no settings/bottom bar, read‑only favorites.
- Library grouping/filter: VOD/Serien gruppieren primär nach Provider (normalisiert: Apple TV+, Netflix, Disney+, Amazon Prime, Paramount+, Max, …). Reihenfolge: Zuletzt gesehen → Neu → 2025–2024 → Anbieter‑Rows. Live gruppiert nach Provider/Genre. Textfilter pro Typ. Fallback für nicht zuordenbare Inhalte: serverseitige Kategorien (optional).
  - Aggregatindizes: Provider/Genre/Jahr werden beim Xtream-Kopfimport (`XtreamSeeder`) in `ObxIndex*` Tabellen persisted (keine Vollscans). Library liest Gruppen aus `ObxIndexProvider/Year/Genre` (Live: Provider/Genre; VOD/Series: Provider/Genre/Jahr optional), nicht mehr per `distinct()` auf den Haupttabellen.
- Backup/Restore present in Setup (Quick Import) and Settings (Quick Import + full section). Drive client optional (shim by default).
- Player fullscreen with tap-to-show overlay controls; Live favorites reorder fixed/stable.
 - HTTP layer: Singleton OkHttpClient + persistent CookieJar; 50 MiB disk HTTP cache (respects Cache-Control); headers via RequestHeadersProvider snapshot for consistency.
- Images (Coil 3): Global ImageLoader uses hardware bitmaps, ~25% memory cache, 512 MiB disk cache; requests capture slot size via `onSizeChanged`, use RGB_565 globally with WxH-aware cache keys, and fall back to the refreshed fish assets. Crossfade stays off for large list rows (on for detail/hero and small avatar/icon uses).
- Xtream enrichment: Throttled batches with retry/backoff; not auto-scheduled in scheduleAll.
- Diagnostics: Settings zeigt OBX-Zähler (Live/VOD/Series) und eine "Import aktualisieren"-Aktion, die `XtreamSeeder` erneut ausführt (optional mit Discovery-Force) und Detailjobs plant. Eine Strict-M3U-Option existiert nicht mehr; explizite Prune-Läufe sind Separate Wartungsjobs.
  - Settings MVVM (2025-10): `SettingsScreen` fungiert als Presenter für `NetworkSettingsViewModel`, `XtreamSettingsViewModel`, `PlayerSettingsViewModel`, `EpgSettingsViewModel` sowie die neuen Telegram-VMS (`TelegramSettingsViewModel`, `TelegramProxyViewModel`, `TelegramAutoDownloadViewModel`). Historische Wartungsbereiche (Quick Import, Diagnose-Werkzeuge, Backup/Restore UI) bleiben vorerst in `ui/home/backups/SettingsScreen_backup.kt` erhalten und werden sukzessive in eigenständige ViewModels/Usecases überführt.
  - Globales Debugging (schaltbar): Schalter in „Import & Diagnose“. Wenn aktiviert, protokolliert das Modul Navigationsschritte (NavController Listener), DPAD‑Eingaben (inkl. Player‑Tasten), Tile‑Focus (mit OBX‑Titel in Klammern) und OBX‑Key‑Updates (Backfill der sort/provider/genre/year Keys) nach Logcat unter dem Tag `GlobalDebug`. Default OFF.

- EPG: Now/Next dual-persist (Room + ObjectBox) with XMLTV fallback; no periodic worker. UI reads OBX; on-demand prefetch for visible tiles and favorites at app start; Live tiles show title + progress.
- Unified UI polish: Accent tokens (adult/kid), carded sections (`AccentCard`), gradient + glow background with blurred app icon; kid profiles use a vibrant palette.
 - Detail screens (centralized): VOD/Series/Live bauen den Kopfbereich über `ui/detail/DetailHeader` + `MetaChips`. Zusatz‑Chips (MPAA/Age/kompakte Audio/Video) kommen aus `ui/detail/DetailHeaderExtras`. Unter dem Header folgen fokusierbare Karten für Plot und Fakten (Expand/Collapse per DPAD) mit allen Xtream-Metadaten (Jahr, Laufzeit, Format, ★Rating, Provider/Kategorie, Genres/Länder, Regie/Cast, Release, IMDb/TMDb-IDs+Link, Technik). Legacy‑Header‑Pfade bleiben nur für `DETAIL_SCAFFOLD_V1=false` aktiv.
 - DPAD in Details: Auf Detailseiten ist DPAD‑LEFT→HomeChrome deaktiviert (Schalter `enableDpadLeftChrome=false` via `HomeChromeScaffold`). So bleibt der Fokus auf den Inhalten; Panels öffnen sich hier nicht versehentlich. BACK funktioniert via Compose `BackHandler` auf VOD/Serie wieder wie erwartet.
 - Normalisierung (Xtream): `XtreamClient.getVodDetailFull` liest den `info`‑Block primär (Poster=movie_image/poster_path/cover, Backdrops=backdrop_path als Array/String, Plot=plot/description/overview, Trailer=youtube_trailer/trailer/trailer_url/youtube/yt_trailer, Genre/Release=genre(s)/releasedate|releaseDate, Technik=audio/video/bitrate, MPAA/Age, TMDb‑IDs). `movie_data` liefert Container/Stream (z. B. `container_extension`) ergänzend.
 - On‑Demand only: Start/Home prefetchen keine Details mehr. VOD/Series laden ihre Metadaten ausschließlich beim Öffnen der Detailseite. VOD mergen OBX‑Row mit Live‑Detail (UI‑only) – kein Persistenzerfordernis in OBX für Zusatzfelder.
  - Detail backgrounds: VOD/Series hero background fills the entire screen (outside chrome paddings) using ContentScale.Crop. In TV/landscape the image covers width; in portrait it covers the screen while preserving aspect. FishBackground overlay removed on detail pages.
- TV buttons: Use `TvButton`/`TvTextButton`/`TvOutlinedButton`/`TvIconButton` (in `ui/common/TvButtons.kt`) to get focus glow + bounce by default. Avoid raw Material3 buttons in TV paths.
- Media actions: Use the centralized action model and `MediaActionBar` (`ui/actions/*`) for detail‑screen CTAs (Resume/Play/Trailer/Add/Remove/OpenEPG/Share). Gate via `BuildConfig.MEDIA_ACTIONBAR_V1` (default ON). Order and Telemetry hooks (`ui_action_*`) must be consistent across screens.
 - Detail header: Use `ui/detail` (`DetailHeader`/`MetaChips`) for VOD/Series/Live detail headers under `BuildConfig.DETAIL_SCAFFOLD_V1` (default ON). For VOD/Series set `showHeroScrim=false` (the full‑screen hero below replaces the header band); Live can keep the scrim.
 - Profile gate + PIN: On TV, the profile tiles and PIN numpad must use `tvClickable`/`TvButtons` with `focusGroup()` on their containers. The first profile tile requests initial focus via a guarded `FocusRequester`. This is implemented and audited.
   - Focus audit: `tools/audit_tv_focus.sh` covers `ui/auth/*` to ensure ProfileGate/Numpad keep DPAD focus rules (no raw clickable, no ad‑hoc DPAD handlers).
   - Initial focus: adult tile (or first visible) receives deterministic focus; header/chrome no longer steal focus on gate. Numpad focuses the first key by default and shows halo/scale on DPAD.
   - Low‑spec TV: Focus visuals respect the reduced-scale profile (≈1.03) and avoid shadow elevation; still clearly visible on TV.

- TV forms: `ui/layout/FishForm.kt` stellt DPAD‑first Form-Rows (`FishFormSection/Switch/Select/Slider/TextField/ButtonRow`) bereit. Sie nutzen FocusKit + FishTheme Tokens, und die historischen `ui/forms` Wrapper sind entfernt. Flag `BuildConfig.TV_FORMS_V1` (default ON) aktiviert die TV-spezifischen Pfade; Phone/Tablet behalten Material OutlinedTextFields.
- UiState (v1): Prefer `UiState` + `StatusViews` + `collectAsUiState` for data loading instead of ad‑hoc spinners. Gate via `BuildConfig.UI_STATE_V1` (default ON). Each migrated screen must render exactly one of Loading/Empty/Error/Success.
-- Cards (v1): **Removed** – Fish* tiles/rows are now the only path. See `CHANGELOG.md` (2025-10-07) for the cleanup entry.
 - Playback (v1): Use `playback/PlaybackLauncher` with `PlayRequest` for all playback starts. Gate via `BuildConfig.PLAYBACK_LAUNCHER_V1` (default ON). Screens provide `onOpenInternal` to route to `InternalPlayerScreen`.
- TV Lazy migration: Do not use `androidx.tv.foundation.TvLazyRow` (deprecated). Use `LazyRow` from compose.foundation with TV focus APIs: `focusGroup()` on the container and `focusable()` + bring-into-view on item focus. A reusable wrapper `com.chris.m3usuite.ui.tv.TvFocusRow` is available and should be used for horizontal TV rows (chips, carousels, overlays). Remove manual DPAD index arithmetic; prefer `moveFocus(...)` when needed.
- TV live controls: DPAD Select toggles a bottom‑right quick‑actions popup (PiP, Subtitle/Audio, Format). Popup stays until Select again or Back. When open, DPAD_DOWN focuses the first button; LEFT/RIGHT navigate; Select activates; Back saves CC settings (if open) and closes the popup.
 - TV mini‑player: Auf TV schaltet der PiP‑Button in einen globalen Mini‑Player (unten rechts) innerhalb der App. Der Mini ist per DPAD fokusierbar (FocusKit tvFocusFrame/tvClickable), fordert aber keinen initialen Fokus an. Die Burger‑Menü‑Taste (MENU) fokussiert den Mini, falls sichtbar. Beim expandierten HomeChrome bleibt der Mini sichtbar, ist aber nicht fokusierbar. Auf Phone/Tablet verwendet der PiP‑Button weiterhin das System‑PiP.
- Persistent UI state: All major lists/grids use a route‑scoped state saver so screens and rows restore their scroll/focus position when navigating away and back (Start, Library groups/rows, Details, Settings, Live/VOD/Series). Helpers: `rememberRouteListState(key)`, `rememberRouteGridState(key)` in `ui/state/ScrollStateRegistry.kt`.
- Dev UX: Compose Live Literals are compiled for debug variants (Gradle config). Use Android Studio Live Edit to tweak literals (`dp`/colors/strings) without redeploy.
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
- Provider normalization: UI-Gruppierungen nutzen `CategoryNormalizer`; Rows gruppieren nach normalisierten Schlüsseln, nicht nach rohen group‑title Strings.
  - Neu: `normalizeBucket(kind, groupTitle, tvgName, url)` begrenzt je Kind (live/vod/series) die Kategorien auf ≤10 stabile Buckets (z. B. live: sports/news/documentary/kids/music/international/entertainment/screensaver/movies; vod: netflix/amazon_prime/disney_plus/apple_tv_plus/sky_warner/anime/new/kids/german/other; series analog). Qualitätstoken (HEVC/FHD/HD/SD/4K) fließen nicht in die Buckets ein.
- Telegram gating: Keine TDLib‑Nutzung ohne aktives Flag (`tg_enabled=true`) und erfolgreichen Login (AUTHENTICATED). Worker/DataSources/Picker sind ansonsten no‑op.
 - TDLib service model: TDLib im separaten Prozess via Service; foreground nur bei Downloads/Auth; `SetInBackground` am Lifecycle; `SetNetworkType` bei Net‑Wechseln. FCM Push (hooks vorhanden), weniger Polling; WorkManager bleibt Fallback.

For the complete module-by-module guide, see `ARCHITECTURE_OVERVIEW.md`.

---

Recent
- Maintenance 2025-11-11: Telegram-Import nutzt die gemeinsame
  `tgSelectedChatsCsv`-Quelle für Filme und Serien, erkennt HLS- sowie
  Octet-Stream-Videos sicherer und sendet Sync-Feedback jetzt als Snackbar-
  Effekt aus dem SettingsViewModel.
- Maintenance 2025-11-10: Telegram-Settings feuern nur noch ViewModel-Intents;
  der ViewModel-State liefert Auth/Resend/LogDir/Chatnamen und persistiert SAF-
  Berechtigungen. `TelegramSyncWorker` startet sofort als Foreground-Service,
  nutzt `ic_sync` und fängt unerwartete Fehler als `Result.failure`. TDLib-
  Backfills deduplizieren Nachrichten, ziehen `fromId-1` sauber weiter und
  respektieren `FLOOD_WAIT` via `delay`, während das Repository zusätzliche
  Video-Container erkennt und VOD strikt von Episoden trennt.
- Maintenance 2025-11-13: Telegram-Settings behalten Proxy- und Auto-Download-Blöcke; `KeyboardOptions` kommt aus `androidx.compose.foundation.text`, damit Kotlin-2.0-Release-Builds ohne Fehler durchlaufen.
- Maintenance 2025-11-12: Telegram-Settings setzen auf den neuen `TelegramChatPickerDialog`, der Chats/Folders über `TelegramSettingsViewModel.listChats()/listFolders()` lädt, Filter für Hauptliste/Archiv/Ordner anbietet, eine Suche integriert und manuelle CSV-ID-Eingaben unterstützt. TDLib-Busy sperrt QR-Öffnen, Voll-Sync-Chip sowie den "Dieses Gerät"-Schalter, bis der Auth-Flow wieder frei ist.
- Telegram backfill: `TelegramSyncWorker` nutzt `MODE_ALL` (kombinierter Full-Sync, `fetchAll=true`) und triggert `CMD_PULL_CHAT_HISTORY` pro ausgewähltem Chat; Legacy-VOD/Serien-CSV wird einmalig in `tg_selected_chats_csv` migriert. Nach jedem Lauf wird `TelegramSeriesIndexer.rebuild*` ausgeführt, damit aggregierte Serien-Rows aktuell bleiben. Settings zeigen Chat-Namen via `CMD_LIST_CHATS`/`CMD_RESOLVE_CHAT_TITLES` an; der Service cached Chats über `loadChats` + Updates (kein doppelter TDLib-Client).
- Telegram Scheduling: `SchedulingGateway.scheduleTelegramSync(ctx, mode, refreshHome)` startet den Worker. Nach Erfolg ruft dieser `SchedulingGateway.onTelegramSyncCompleted(...)` auf und legt Cache-Cleanup + OBX-Key-Backfill neu auf; bei `refreshHome=true` wird zusätzlich `scheduleAll()` getriggert, damit HomeChrome / Rows sofort aktualisieren.
  - `SchedulingGateway.telegramSyncState` liefert Laufzeitstatus (Idle/Running/Success/Failure) inklusive aggregierter Zähler. HomeChrome nutzt diesen Flow für den globalen „Telegram Sync“-Banner und blendet Abschlussmeldungen nach kurzer Zeit automatisch aus.
- Telegram Heuristics: Parser erkennt jetzt SxxEyy-Bereiche (`E01–03`), `S1:E2`, `1x05–07`, deutsch/englische „Staffel/Folge“/„Season/Episode“-Varianten sowie reine „Episode 4“-Titel; Sprach-Tags (`[GER]`, `DE/EN`, `VOSTFR`, `ITA`, `ES`) werden vereinheitlicht.
- Telegram Login UX: Telefon-Login nutzt `PhoneNumberAuthenticationSettings` (Tokens + `is_current_phone_number`) und bietet in den Settings einen Toggle „Code auf diesem Gerät bestätigen“. Erkennt die Telegram-App und öffnet den QR-Link automatisch, sodass kein Zweitgerät nötig ist.
  - Zusatz: Der Dialog bietet jetzt einen Button „Per Code anmelden“, um nahtlos vom QR-Fluss zurück auf den klassischen SMS-Code zu wechseln.
- Telegram UI/Playback: Library VOD zeigt per‑Chat Telegram‑Rows (Tiles mit blauem „T“-Tag); der Serien-Tab behält die aggregierte Row „Telegram Serien“. Auf Start existiert zusätzlich eine globale Row „Telegram Serien“ (Aggregat) sowie Film-Rows je ausgewähltem Chat (ein gemeinsamer Picker). Start’s global search enthält eine Telegram‑Row. Player nutzt geringe RAM‑Buffer für `tg://` (`TdlibRandomAccessSource` auf TDLib-Cache) und dekomprimiert `rar://`-MP3s on-the-fly (LRU + Ringbuffer im Cache-Verzeichnis).
- Telegram Auth Flow: Login/Dialog lebt im Modul `app/src/main/java/com/chris/m3usuite/feature-tg-auth` (Domain+Data+UI+DI). `TgAuthOrchestrator`
  mappt TDLib-States, startet automatisch den Google SMS User Consent, handhabt `ResendAuthenticationCode` und mapped Fehler via
  `TgErrorMapper`. Settings/Dialog nutzen die neuen Composables (`PhoneScreen`, `CodeScreen`, `PasswordScreen`). Alle Änderungen am
  Login-Flow laufen über dieses Modul; Fehler gehen strukturiert über `TelegramServiceClient.ServiceError`.
  - `TgAuthViewModel` bindet den Orchestrator an Compose, hält Telefon/Code/Passwort-Eingaben persistent, steuert den Busy-State,
    verknüpft den SMS-Consent mit dem Lifecycle und speichert Telegram API-ID/-Hash direkt im `SettingsStore`. BuildConfig-Werte
    greifen weiterhin als Fallback, wenn keine benutzerdefinierten Schlüssel hinterlegt sind.
- Maintenance 2025-11-06: Kotlin-2.0-Build läuft wieder – Start nutzt für die
  Telegram-Serien-Row das vollständige `SeriesFishTile`-API (inkl. NEW/Assign/Play)
  und Settings importieren `contentOrNull`, halten `showTgDialog` global sowie
  opt-in'en `ModalBottomSheet` via `ExperimentalMaterial3Api`.
- Maintenance 2025-11-02: Telegram-Settings kompilieren wieder sauber mit Kotlin
  2.0. Flow-Debounces importieren aus `kotlinx.coroutines.flow`, der Telegram
  Chat-Picker ist als `@Composable` markiert und `TgSmsConsentManager` kapselt
  seinen `SupervisorJob` intern.
- Telegram Build Guard: Kotlin 2.0 Release-Builds benötigen im Indexer weiterhin `Int`-basierte ID-Mengen und einen TDLib-Schreibpfad, der konkrete `IndexedMessageOutcome` zurückliefert. Die Fixes sind angewendet; Set<Long>/Result-Mismatches dürfen nicht wieder eingeführt werden, sonst scheitert `:app:compileReleaseKotlin`.
- TV low-spec tuning (7a/TV): TV devices (detected via UiMode/Leanback/Fire TV) use a reduced-focus profile and smaller paging windows to improve smoothness on low-spec hardware. Focus effects drop shadowElevation; scales reduce to ~1.03. OkHttp dispatcher is throttled (maxRequests=16, perHost=4). Coil crossfades are disabled on TV to lower overdraw.
- TV/DPAD focus (gate): ProfileGate tiles now use `tvClickable` + `tvFocusableItem` within a `focusGroup()` container, with a guarded initial `FocusRequester`. On TV, the first profile tile is highlighted immediately; DPAD navigation shows halo/scale.
- TV/DPAD focus (PIN): The PIN numpad uses `TvButton`/`TvTextButton` for keys (0–9, backspace, OK). The grid container is a `focusGroup()` and the first key requests initial focus. Visual focus (halo + scale) is consistent with other TV buttons.
- Manifest/Gradle: Added leanback/DPAD features (non‑required) and ensured TV libraries are available. Behavior is a no‑op on phones and tablets.
- Media3 FFmpeg: Google Maven ships no FFmpeg artifacts. We vendor Jellyfin's `org.jellyfin.media3:media3-ffmpeg-decoder:1.8.0+1`
  AAR (GPL-3.0). Keep Media3 core artifacts on version `1.8.0` and ensure our ABI filters stay aligned with the bundled `.so`
  set (arm64-v8a + armeabi-v7a by default).

- Playback-aware seeding: While the internal player is active, `m3u_workers_enabled` is temporarily forced OFF and current Xtream jobs are canceled; on exit, the flag is restored if it was previously ON. This ensures background seeding does not contend with playback on low-power devices.
- Home initial focus: Start screen sets the first focus deterministically to the first tile of the topmost card (Series). Only the Series row is eligible to request initial focus; VOD/Live rows suppress initial focus on Start. Implemented via `RowConfig.initialFocusEligible` + StartScreen wiring.
- Focus logging at start: GlobalDebug prints the currently focused tile immediately at screen start (no interaction needed). Row engines announce the first tile after requesting initial focus; chrome header/bottom also log when they gain focus.
- Widget focus logging: All focusable widgets (Buttons via `focusScaleOnTv`/`TvButtons`, generic clickables via `tvClickable`, and row items via `tvFocusableItem`) emit `focus:widget` logs with `component=<type> module=<RouteTag.current> tag=<hint>` whenever they gain focus. Use `debugTag` param to override the hint if needed.
- TV chrome BACK: On TV, ESC/BACK first collapses HomeChrome (from Expanded or Visible) and consumes the event. This prevents closing the player or leaving the screen when the chrome is showing; pressing BACK twice still exits as expected.
 - TV chrome empty-start: On TV, if Start is empty (no rows yet, e.g., right after PIN on a fresh setup), HomeChrome auto-expands and the Settings button in the header receives initial focus so users can immediately open Settings. DPAD LEFT also expands HomeChrome from this empty state.
- Tile focus logging: Core row engines (MediaRowCore/MediaRowCorePaged) now emit detailed `focus:<type> id=<id> <ui title> (<OBX title>)` logs on focus, plus a `tree:` hint. Makes it visible in logcat which concrete tile currently has focus across Start/Library/Details rows.
- TV rows scroll: Minimal adjustment when the focused tile is clipped; if the target tile is already fully visible, no re-centering occurs. Debounced focus requests while scrolling; last requested index applies when motion stops. Prevents over-scrolling/jumps on single DPAD steps.
- TV rows scroll helper: Introduced `ui/tv/TvRowScroll.kt` and refactored the FocusKit row engine and TvFocusRow to use one centering implementation. Row tiles no longer invoke per-tile auto bring-into-view, avoiding conflicting scrolls. DPAD LEFT/RIGHT now navigates reliably; focused tiles are always visible.
- Stronger tile focus: VOD/Series/Live row tiles and Resume carousel use a +40% focused scale and a thicker focus halo for clear visual focus until navigation moves on.
- Navigation state: Top‑level route switches (`library?q=…` ⇄ `browse`) now use Navigation‑Compose state saving (`restoreState=true`, `popUpTo(findStartDestination()){ saveState=true }`). Combined with route‑scoped `rememberRouteListState(...)`, Start/Library lists restore scroll/focus positions reliably.
- Use `NavHostController.navigateTopLevel(route)` for top-level switches. It wraps `popUpTo(start){ saveState=true }`, `launchSingleTop=true`, and `restoreState=true` to preserve state across Home/Library/Search hops. Return from detail via `popBackStack()` only.
- Inputs/state: Prefer `rememberSaveable` for screen-visible input state (queries, text fields, toggles) and `rememberRouteListState(...)`/`rememberSaveableStateHolder()` for lists/expandables. Keys must be stable (e.g., item.id or route). Avoid saving heavy/ephemeral player state.
 - Scroll state cache: `rememberRouteListState/rememberRouteGridState` now back list/grid state with a small in‑memory cache keyed per route, so positions persist even if Navigation removes the backstack entry (useful with gate/start routes and query args).
- Xtream networking: Global per-host pacing (~120 ms minimal interval) and in-memory response cache (60s; EPG 15s) added to `XtreamClient` to prevent bursts and duplicate list calls. `importDelta` is heads-only; detail chunks run separately with strict limits.
- Xtream delta import: Listen werden weiterhin pro Kategorie (Live/VOD/Series) aggregiert, um Panel-Limits zu umgehen. Orphan-Pruning ist standardmäßig deaktiviert; reguläre Delta-Läufe ergänzen ausschließlich fehlende Felder/Posterdaten.
- Library rows stability: Horizontal rows (provider/genre/year and top rows) use route-scoped list state so navigating into details and back does not visibly rebuild cards; scroll/focus positions persist.
- Xtream Seeder: Setup nutzt `XtreamSeeder.seedListsQuick(limit=200)`, um extrem schnell sichtbare Kopf-Listen (Live/VOD/Series) zu importieren (Heads only). Playlist-Parsing (M3U) wurde entfernt; `get.php`-Links dienen ausschließlich der Credential-Ableitung.
- Seeding-Gate: `XtreamImportCoordinator` markiert `seederInFlight`, reiht parallele Seeder-/Delta-Triggers ein und gibt sie erst nach Abschluss frei. `XtreamDeltaImportWorker` wartet via `waitUntilIdle()` und das UI zeigt währenddessen einen nicht-blockierenden "Import läuft…"-Hinweis über `HomeChromeScaffold`.
- Adults toggle: Global setting "Kategorie 'For Adults' anzeigen" controls visibility of the "For Adults" category across Start, Library and search. Default OFF; when disabled, all categories whose id/label start with `For Adult` or `adult_` (and their items) are stripped at repository/UI level.
- Home rows: Serien/Filme rows merge "Zuletzt gesehen" (left) with "Neu" (right). On first start (no resume marks), rows show only new items with a NEU badge.
 - Favorites reordering (TV): DPAD LEFT/RIGHT only reorder when selection mode is active (after long-press to select). Otherwise LEFT/RIGHT perform normal focus navigation.
- Startup flow: Der bisherige Bootstrap-Screen entfällt. Sobald Xtream-Creds vorliegen, wird `XtreamSeeder.ensureSeeded` ausgelöst; UI bleibt erreichbar und Kopf-Daten erscheinen ohne Blockierung.
- TV usability: Remote seeks (DPAD/MEDIA ±10s), focusable player overlay incl. slider via D‑Pad, Settings text fields on TV are read‑only with explicit edit dialogs to avoid on‑focus keyboard traps; debounced writes prevent lag and accidental imports while typing; dynamic colors disabled on TV for consistent palette.
- TV chrome: Header + Bottom sind auf TV standardmäßig sichtbar, klappen bei Content‑Interaktion automatisch ein und lassen sich per Menu/Burger wieder expandieren. Im Expanded‑Modus Fokus‑Trap zwischen Header/Bottom (DPAD UP/DOWN springen), der Mittel‑Content ist leicht geblurred; Panels sliden animiert ein/aus und das Content‑Padding passt sich dynamisch an. Nicht‑TV bleibt unverändert.
- Xtream series playback: Episode URLs use the returned episode_id, fixing HTTP 401 on KönigTV.
- Xtream trailers: normalize youtube IDs into full URLs so trailer playback works without crashes.
- Xtream VOD import: handles `stream_id`-only panels (e.g., KönigTV), so delta diagnostics show real VOD counts instead of `vod=0`.
- Live detail: Profile gating tolerates missing/initial profile IDs (no ObjectBox crash when opening tiles).
- Start live picker: search pulls from OBX live rows, so favorites search matches Library results.
- Xtream onboarding/telemetry: After discovery, the app immediately triggers Discovery → Client → Fetch (the six reference list calls). All `player_api.php?action=...` URLs are logged at info level for quick verification. VOD alias from discovery is honored, wildcard `category_id=0` is used when no category is selected, and panels that reject both now fall back to a request without `category_id`.
- Xtream playback: `XtreamUrlFactory` reads the cached capability alias/basePath before building URLs so VOD streams hit the resolved `/vod|movie|.../` path for internal playback.
- Xtream: Port-Resolver verschärft – nur HTTP 2xx + parsebares JSON zählen als gültiger Treffer. Probes senden nun explizite `action`‑Parameter (`get_live_streams|get_series|get_vod_streams`) mit `category_id=0`, um WAF/Cloudflare 521 zu vermeiden. HTTP‑Kandidaten priorisieren `8080`; `2095` wurde entfernt. Cache‑Treffer werden einmal revalidiert; bei Fehlschlag wird der Eintrag verworfen und neu ermittelt. Wenn die Basis‑URL/Settings bereits einen Port enthalten, wird dieser unverändert übernommen und der Resolver übersprungen.
- EPG: Now/Next dual-persist (Room + OBX) + XMLTV multi-index; fallback aktiv auch ohne Xtream; kein periodischer Worker mehr (on‑demand Prefetch sichtbar/Favoriten), optional stale cleanup.
- UI: Live tiles enriched with current programme + progress bar.
- Xtream: Detection supports compact stream URLs; import merges missing `epg_channel_id` from existing DB by `streamId`.
- UI polish: Long-press reordering for Live favorites (touch-friendly), Library/Details/Setup behalten den carded Look; Start teilt sich jetzt das Library-Layout (FishRow/FishTile) samt Accent/KidAccent Tokens und Hintergrund-Glow.
- Profiles/Permissions: Added Guest profile type; per‑profile permissions with enforcement (Settings route gating, external player fallback to internal, favorites/assign UI gating, resume visibility, whitelist editing).
- Kid-mode correctness: Home refresh now uses filtered queries; favorites read‑only for restricted profiles; “Für Kinder freigeben” visible only when permitted.
- Whitelist UX: Category‑level allow with item‑level exceptions; admin sheet in ProfileManager to manage both.
- Data: New tables `kid_category_allow`, `kid_content_block`, `profile_permissions`; DB schema bumped with idempotent migrations.
- Telegram (scaffold): Global feature flag, Settings section, DB v8 with `MediaItem.source` + TG refs and `telegram_messages` table; Gradle packaging prepped for universal ABI; ProGuard keep rules added. Playback resolves local TG paths when available. Default OFF to preserve current behavior.
- UI/Library: Dynamische Gruppierung/Filter (VOD/Serien: Provider/Jahr/Genre; Live: Provider/Genre). Kategorie‑Normalisierung vereinheitlicht Provider (Apple TV+, Netflix, Disney+, Prime …). Textfilter pro Typ; "Unbekannt" fängt Reste ab.

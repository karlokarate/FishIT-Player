2025-11-14
- fix(telegram/detail): Add a dedicated Telegram detail screen and refresh the
  TDLib repository helpers (global search/message lookup, poster extraction)
  so release builds resolve the new navigation route again.
- fix(player): Update Media3 seek parameter usage, telegram data sources, and
  gating defaults so Kotlin 2.0 release builds compile without the missing
  constants or APIs.

2025-11-13
- fix(settings/telegram): Restore the proxy and auto-download settings blocks
  that disappeared during the auth refactor and import Compose
  `KeyboardOptions` from `foundation.text` so Kotlin 2.0 release builds compile
  again.

2025-11-12
- feat(settings/telegram): Rewired the Settings login block to the
  `feature-tg-auth` MVVM. A dedicated `TgAuthViewModel` now manages TDLib
  state, Google SMS consent, and persistent phone/code/password fields while
  exposing a Compose flow (`PhoneScreen`/`CodeScreen`/`PasswordScreen`) and a
  QR-state fallback. Users can edit and store Telegram API ID/Hash directly in
  Settings; BuildConfig defaults remain the fallback when no custom keys are
  provided.
- fix(settings/telegram): Surface TDLib command failures as snackbar effects,
  keep the TDLib service in foreground mode while the screen is visible, and
  block login toggles/chat selection buttons while TDLib is busy or chat names
  resolve. The chat picker now shows a progress indicator during name lookup.
- feat(settings/telegram): Ersetzt den provisorischen CSV-Dialog durch einen
  vollwertigen Chat-Picker mit Listen-/Ordner-Filtern, Suche, manueller
  ID-Erfassung und bestehender Vorauswahl. Der Dialog lädt Chats direkt über
  das ViewModel (TDLib) und verhindert Mehrfachauswahlen während des Ladens.
- fix(settings/telegram): Sperrt QR-Öffnen, Voll-Sync-Chip und den
  "Dieses Gerät"-Schalter, solange TDLib Anfragen verarbeitet oder keine
  Schlüssel verfügbar sind, damit der Auth-Flow keinen inkonsistenten Zustand
  erreicht.
- fix(ui/library): Library header shortcuts for Live/VOD/Series switch tabs
  immediately by optimistically updating local state while DataStore catches
  up, restoring responsive navigation on touch and TV.
- fix(main): Import StartScreen and ProfileManagerScreen in MainActivity so
  release builds resolve the new package locations.

2025-11-11
- fix(telegram/repo): Selected chat IDs now come from the unified
  `tgSelectedChatsCsv` list for both VOD and series flows, leaving the
  heuristics to decide the media type per message.
- fix(telegram/repo): Video detection accepts HLS MIME types and
  `application/octet-stream` payloads with a detected container so playable
  sources no longer slip through.
- feat(settings): SettingsViewModel emits one-shot snackbar effects for sync
  feedback and the Settings screen collects them via a shared flow.
- fix(worker/telegram): `TelegramSyncWorker` awaits its foreground promotion
  before touching TDLib to guarantee the notification is shown on time.

2025-11-10
- feat(settings/telegram): SettingsViewModel now publishes Telegram auth state,
  resend countdown, selected chat titles, and log directory directly. The
  Settings screen fires intents only, while the ViewModel persists SAF
  permissions, resolves chat names off the main thread, and triggers sync
  scheduling.
- fix(worker/telegram): TelegramSyncWorker promotes itself to the foreground at
  start-up with a dedicated channel/icon and wraps the whole execution in a
  fail-safe to return clean Result.failure values instead of crashing after chat
  selection.
- fix(telegram/service): Backfill pacing clamps TDLib offsets, dedupes message
  ids, and respects FLOOD_WAIT via suspend delays, eliminating the previous
  off-by-one rewind and busy sleeps.
- fix(telegram/repo): Video detection recognises more Telegram media variants,
  keeps VOD distinct from episodic content, and reuses the enhanced heuristics
  for both VOD and series lists.
- chore(ui/assets): Added a vector sync icon for worker notifications so the
  foreground notification no longer falls back to the system download glyph.

2025-11-09
- fix(telegram/settings): SettingsViewModel now resolves Telegram chat names
  off the main thread, exposes progress for the picker, and keeps snackbar
  feedback centralized so the composable stops orchestrating TDLib calls
  directly.
- fix(telegram/parser): Reworked caption heuristics with safer regexes,
  aggressive normalisation, and unit tests covering ranges, SxxEyy, colon, and
  noisy release tags.
- fix(telegram/media): posterUri() refuses to block the main thread and moves
  TDLib polling to Dispatchers.IO while reusing cached local paths.
- fix(telegram/service): listChats, resolveChatTitles, and chat backfill now
  wait for AuthorizationStateReady via awaitAuthorizationReady() before
  touching TDLib to avoid early 401/400 churn.

2025-11-08
- fix(ui/live): Remove duplicate `isTelegramItem` and local `FishTelegramBadge`
  from `FishLiveContent.kt` in favor of the shared helpers in
  `FishTelegramContent.kt`. Resolves overload ambiguity and compiles cleanly.
- fix(telegram/sync): Replace non-existent named `pageSize` argument with a
  positional `limit` when calling `pullChatHistoryAwait` in TelegramSyncWorker.
  Keeps the intended batch size semantics and restores Kotlin 2.0 compatibility.
- fix(settings/telegram): Declare `authState` and `resendLeft` via
  `collectAsStateWithLifecycle`, wiring to the repository/service flows so
  the Settings screen compiles across API levels and shows a stable resend
  countdown when waiting for a code.
- fix(telegram/auth): TelegramServiceClient exposes a simple observable auth
  state (`StateFlow<AuthState>`) and imports `cancelChildren` to resolve the
  unreferenced coroutine cancelation helper. Settings can now directly observe
  a typed auth state alongside existing auth events.
- feat(telegram/indexer): Serien fallback auf Chat-Titel, normalized keys und
  sortierte Episoden (Staffel/Episode/Datum). Serienrebuild nutzt TDLib,
  um die Chat-Namen für ausgewählte Chats aufzulösen.
- feat(telegram/parser): Film-/Serien-Heuristik reinigt Titel von Release-Tags
  und extrahiert Jahreszahlen für die weitere Verwendung.
- feat(telegram/library): Telegram-VOD/Serien verwenden die bereinigten Titel
  samt Jahr und speichern Video-Metadaten konsistent in ObjectBox.

2025-11-07
- fix(build): Bundle the `slf4j-android` binding so Junrar no longer triggers
  missing `StaticLoggerBinder` classes during release R8 minification.
- fix(ui/homechrome): Restored the Live/VOD/Serien quick actions in the
  HomeChrome header on Start by wiring `LibraryNavConfig` so the overlay renders
  the library shortcuts on phone and TV again.

2025-11-06
- fix(start/telegram): Align the aggregated "Telegram Serien" row with the
  FishRow/SeriesFishTile contract so Kotlin 2.0 compiles again (new/assign/play
  lambdas wired up, lambda signature corrected).
- fix(settings/telegram): Restore Kotlin 2.0 compatibility by importing
  `contentOrNull`, hoisting the shared login dialog state, and opting into the
  Material3 bottom sheet API used by the chat picker.

2025-11-05
- feat(telegram/settings): Konsolidiert die Chat-Auswahl in ein Multi-Select mit
  gemeinsamem `tg_selected_chats_csv`. Der Dialog zeigt die aufgelösten Namen,
  bestätigt die Auswahl mit „Übernehmen & Sync starten“ und stößt sofort einen
  kombinierten MODE_ALL-Sync an. Legacy-Felder für getrennte VOD/Serien-CSV
  werden einmalig migriert.
- feat(telegram/sync): `TelegramSyncWorker` nutzt `MODE_ALL` als Default,
  lädt alle ausgewählten Chats mit `fetchAll=true`, rebuildet den
  `TelegramSeriesIndexer` nach jedem Lauf und berichtet aggregierte Zahlen.
  `SchedulingGateway` verwaltet nun ein zusätzliches `tg_sync_all`-WorkItem.
- feat(telegram/ui): Start zeigt eine globale Row „Telegram Serien“ (Aggregat
  aus ObjectBox) plus Film-Rows je ausgewähltem Chat; Library behält die
  aggregierte Serien-Row und lädt VOD-Only pro Chat. Die Settings spiegeln die
  Auswahl als Namen wider und besitzen einen zentralen Sync-Button.
- feat(telegram/parser): Heuristiken erkennen jetzt Bereiche (`E01–03`),
  Doppelpunktformen (`S1:E2`), `1x05–07`, deutsch/englische „Staffel/Folge“-
  Varianten, reine Episodenangaben und normalisieren Sprach-Tags (`GER/ENG`,
  `VOSTFR`, `ITA`, `ES`).

2025-11-02
- fix(telegram/settings): Kotlin 2.0 debug builds compile again. Flow operators
  are imported from `kotlinx.coroutines.flow`, the Telegram chat picker is marked
  as `@Composable`, and JSON helpers are wired so the new Telegram settings
  screen compiles after the module split.
- fix(telegram/auth): `TgSmsConsentManager` now owns a `SupervisorJob`-backed
  `CoroutineScope`, so detaching the consent flow cancels pending launches
  without tripping the compiler.

2025-11-01
- feat(telegram/auth): Extrahiert den Login-Flow in das neue Modul
  `feature-tg-auth` mit klaren Domain-/Data-/UI-Schichten. Der Settings-Dialog
  nutzt nun den `TgAuthOrchestrator`, der TDLib-States und Aktionen bündelt und
  Fehler konsistent abbildet.
- feat(telegram/auth): Integriert Googles SMS User Consent API für das
  automatische Befüllen des Bestätigungscodes inklusive Jitter-Rearm bei
  ungültigen/abgelaufenen Codes sowie resendAuthenticationCode-Support über TDLib.
- fix(telegram/auth): Service und Client liefern strukturierte Fehlermetadaten,
  `TgErrorMapper` mappt Flood-Waits, App-Updates und Sperren auf UI-Fehler mit
  Backoff. Die Settings zeigen Countdown/Cooldown an und QR-Flow bietet
  einen sauberen Fallback auf den Code-Login.

2025-10-31
- fix(build): Import `java.util.Locale` in `TelegramTdlibService` so release builds
  compile again on Kotlin 2.0 after the locale-based proxy/auto-download parsing
  changes.

2025-10-30
- feat(telegram/player): Replace the legacy TelegramTdlibDataSource with a
  TDLib-backed random access source that streams `tg://file/<fileId>` URIs on
  demand. Reads now trigger range downloads with 512 KiB readahead, retry with
  exponential backoff, merge TDLib `updateFile` progress, and persist
  `localPath`/`size` updates to ObjectBox for telemetry.
- feat(rar/player): Added `rar://msg/<msgId>/<entry>` playback via a
  `RarEntryRandomAccessSource`. The entry is extracted into a ring-buffered
  cache directory, chunk reads are LRU-cached in memory, and metadata is taken
  from the Telegram message index so ExoPlayer receives the correct
  `audio/mpeg` MIME and size.
- refactor(telegram/tdlib): Allow multiple TDLib update listeners so the
  service and the new playback source can subscribe simultaneously. The
  service now registers via `addUpdateListener` and cleans up the subscription
  on shutdown.

2025-10-29
- fix(telegram/auth): Normalize phone numbers before sending from the UI, show a
  clear error when TDLib cannot be started, and immediately refresh the auth
  state so phone/code logins progress after submitting the number.
- fix(settings/telegram): Allow the TDLib log level slider to be adjusted on
  touch devices by adding a Material slider fallback while keeping DPAD
  controls on TV.

2025-10-28
- feat(telegram/debug): Added an on-screen TDLib log overlay that streams recent
  Telegram logs as snackbars whenever the new settings toggle is enabled and the
  log level is above 0.
- feat(settings/telegram): Extended the Telegram debug section with a "TDLib-Logs
  als Snackbar" switch so testers can turn the live overlay on and off without
  reconnecting the service.

2025-10-27
- fix(telegram/auth): Normalize submitted phone numbers to E.164 using the
  device region when they are missing a leading "+". Prevents TDLib from
  staying stuck in WAIT_FOR_NUMBER after entering a local-format number.
- fix(telegram/auth): Provide realistic TDLib parameters (app version, device
  model, Android release/API, language, test DC flag, unique database directory)
  so TDLib accepts code-based logins again without forcing QR on 406.
- fix(telegram/auth): Keep the phone/code flow active when TDLib returns 406,
  introduce an explicit "QR-Login anfordern" action in Settings, and clarify the
  cooldown guidance instead of auto-triggering QR requests.

2025-10-26
- fix(telegram/tdlib): Align reflection constructor parameter arrays with Kotlin
  2.0's stricter `arrayOf` inference so release builds stop reporting
  `Array<Class<out Comparable<*> & Serializable>>` mismatches.
- fix(telegram/settings): Import the FishForm slider composable so the Telegram
  Settings sliders compile again under Kotlin 2.0.
- fix(telegram/tdlib): Explicitly type the phone-auth settings constructor
  parameters as `Array<Class<*>>` so Kotlin 2.0 release builds accept the
  mixed primitive/custom class signatures TDLib expects.

2025-10-25
- feat(telegram/settings): Expose full TDLib runtime controls in Settings. Users can
  toggle IPv6 preference, persistent online status, storage optimizer, and log
  verbosity, configure SOCKS5/HTTP/MTProto proxies, tune streaming prefetch window,
  seek boost, and set parallel download limits. Auto-download profiles now have
  per-network switches for preload behaviour and call data usage, and a one-touch
  cache optimizer is available.
- feat(telegram/playback): TelegramTdlibDataSource respects the new streaming
  configuration with a semaphore for `downloadFile` requests, configurable range
  prefetching, and higher-priority seeks to keep playback responsive.

2025-10-24
- fix(telegram/build): Remove named arguments when invoking the TDLib fallback
  lambda in `TelegramTdlibDataSource`. Kotlin 2.0 forbids named parameters on
  function-type calls, so this restores `:app:compileDebugKotlin`.

2025-10-23
- fix(build/media3): Replace the missing Google Maven FFmpeg artifact with Jellyfin's
  `media3-ffmpeg-decoder` 1.8.0+1 build so Gradle resolves Media3 1.8.0 again while
  keeping the internal player wired to FFmpeg codecs.

2025-10-22
- feat(telegram/tdlib): Harden the reflection bridge with retry/backoff-aware
  `sendForResult` calls, trace tags, and explicit timeouts for every TDLib
  request. Failures now surface predictable logs and avoid stuck handlers.
- fix(telegram/playback): TelegramTdlibDataSource reads ObjectBox metadata
  before issuing TDLib calls, applies streamability heuristics, waits for full
  downloads when TDLib flags files as non-streamable, and persistently caches
  the resolved local path. Playback now recovers gracefully when TDLib stalls
  and falls back to routing with user messaging when required.
- fix(telegram/routing): Align the routing datasource and thumbnail helpers
  with the new robust TDLib invocation pattern so chat lookups and thumbnail
  downloads reuse the same retry/backoff semantics.

2025-10-21
- feat(player/media3): Upgrade the Media3 stack to 1.8.0 now that the release
  is available on Google Maven again. Keeps ExoPlayer current and aligned with
  upstream fixes.
- fix(player/ffmpeg): Centralize renderer configuration so the internal player
  and trailer preview both prefer the bundled FFmpeg extension while decoder
  fallback stays enabled. Resolves sporadic playback failures caused by
  missing platform codecs.
- refactor(ui/start): StartScreen now delegates data/side-effects to StartViewModel
  (ObjectBox/Settings flows, search paging, kid permissions) plus StartUseCases for
  repository access. Compose keeps the kid assignment sheet, live favorites picker,
  and Telegram binding temporarily; these move into dedicated VMs next.
- refactor(ui/settings): SettingsScreen binds dedicated section view models
  (network/player/xtream/epg + Telegram triad) and drops direct repository/service
  calls. Legacy maintenance/backup flows remain in `ui/home/backups/SettingsScreen_backup.kt`
  until their MVVM counterparts land.

2025-10-20
- fix(build/media3): Pin Media3 dependencies to 1.5.1 so Gradle can resolve the FFmpeg
  extension again. Version 1.8.0 is not yet published on Maven Central/Google Maven and
  broke `:app:checkDebugAarMetadata`.
- fix(telegram/heuristics): Replace the invalid `SxEy` char-class with safe patterns,
  compile heuristics via `safeRegex`, and wrap TdLib parsing in a fallback so malformed
  captions no longer crash Telegram indexing with `ExceptionInInitializerError`.
- fix(telegram/auth): Serialize TDLib auth commands with a mutex, wait for
  `AuthorizationStateReady` before applying runtime options, and disable login buttons
  while requests run. This removes 400 “Another authorization query”, early 401s on
  auto-download presets, and proxy initialization timeouts during login.
- fix(telegram/history): Clamp `GetChatHistory` offsets to TDLib’s [-99,0] window so
  backfills no longer trip 400 “Parameter offset must be greater than -100” and chats
  paginate to completion.
- fix(ui/auth): OTP dialog now remembers a `FocusRequester` and waits a frame before
  requesting focus, eliminating Compose “FocusRequester is not initialized” warnings.
- fix(telegram/thumbs): Poster mapper runs TDLib thumbnail nudges on Dispatchers.IO and
  skips work on the UI thread, so Coil no longer blocks or floods GC when fetching chat
  art.
- refactor(settings): Added `SettingsViewModel` with intents/effects; Telegram sync now
  schedules via the VM instead of Compose coroutines, and the screen reacts to VM
  effects for snackbars.
- fix(telegram/sync): Foreground WorkManager runs now emit a channelized notification with
  `ic_sync` as the small icon, preventing the `Invalid resource ID 0x00000000` crash when
  the sync worker enters foreground mode.

- fix(ui/rows): Align prefetch callback to keys-only. `OnPrefetchKeys` now takes `List<Long>` (visible item keys/ids) instead of `(indices, items)`. Updated Start and Live detail rows to decode live `streamId`s from ids and prefetch EPG reliably. Resolves Kotlin type mismatch errors around `onPrefetchKeys` and the `streamId` reference.

2025-10-19
- feat(player/ffmpeg): Internal player bundles Media3 FFmpeg codecs, prefers the extension renderers,
  and biases track selection towards premium audio/video formats at the highest supported bitrate. VOD,
  live, and series playback now automatically choose the richest streams when panels offer multiple
  variants.

2025-10-18
- fix(telegram/build): Release compile restored by aligning Telegram indexer/service Kotlin types with ObjectBox IDs and returning concrete outcomes from the TDLib writer helper. Prevents Kotlin 2.0 Set/Result mismatches from breaking the pipeline.

2025-10-17
- feat(telegram/sync): Worker now reports per-run stats (new films/series/episodes) and exposes a global `SchedulingGateway.telegramSyncState`. Home chrome shows a persistent "Telegram Sync" banner with progress and completion details until results are acknowledged.
- feat(settings/telegram): Sync toasts surface the real counts from WorkManager output and stay silent when no chat was processed. Added a fallback button in the login dialog to switch from QR to code-based authentication without leaving the flow.
- fix(ui/library): Restored the Library header bottom buttons (Live/VOD/Serien) for phone users by re-enabling the bottom chrome in `HomeChromeScaffold`.
- fix(telegram/playback): Increased TDLib polling windows in `TelegramTdlibDataSource`/`TelegramRoutingDataSource` so large downloads no longer abort with IO errors before the local path appears.
- chore(telegram/indexer): `TelegramSeriesIndexer.rebuildWithStats` returns detailed counts for new series/episodes; the sync worker consumes it to drive UI messaging and cache refreshes.

2025-10-16
- refactor(tv/homechrome): Introduced HomeChromeOverlay host wiring chrome focus locals, "prefer settings first focus" toggle, and collapse callback, so HomeChromeScaffold can drop inline overlay plumbing.
- feat(ui/header): Expanded FishHeader controller/data to support accent badge text and provider chips via a shared overlay host.
- feat(ui/live): Added FishTelegramBadge + overlay merge logic for live tiles, showing Telegram origin on Start/Library rows and keeping play vs. detail click handlers separated.
- refactor(focus/rows): Updated FocusRowEngine with suspend prefetch callbacks and chrome row focus setter integration; Start, Live detail, and library rows migrated.
- fix(forms): Normalized FishForm button row params (primaryEnabled/isBusy) so CreateProfile/Playlist screens compile on Compose 1.9.

2025-10-14
- fix(player/mini): Restored the TV mini-player overlay via MiniPlayerHost/MiniPlayerState, reusing PlaybackSession without
  resetting media items. The overlay now shows title/subtitle/progress, requests focus on MENU, and resumes the full player via
  a shared navigator hook.
- build(objectbox): Include `objectbox-kotlin` and import the Kotlin extensions so TelegramSeriesIndexer (query/put) compiles
  in release builds.
- fix(telegram/series-indexer): Rebuild the aggregated Telegram series catalog from ObjectBox messages. Episodes now map each
  SxxEyy caption reliably, persist tg:// identifiers, copy duration/container/mime/size/language metadata, and normalize
  posters/years so Library’s “Telegram Serien” row finally lists every season and episode instead of just the newest message.
- fix(telegram/meta-mapper): Share Telegram media helpers for posters/containerExt between repository and indexer, ensuring
  thumbnails resolve immediately and metadata stays consistent for playback.
- fix(telegram/auth): Phone-login requests now enable Telegram’s SMS retriever path so entering the code directly in FishIT
  matches the official client’s variant A. QR authentication remains available as the secondary option.

2025-10-13
- fix(telegram/paging): Use TDLib-compliant history pagination (`fromMessageId=oldestId` with `offset=-1`) to fetch older pages. Resolves 400 "Invalid value of parameter from_message_id specified" and ensures all files are discovered, even when many lie on the same page.
- fix(telegram/posters): Proactively resolve and download thumbnails on first load when missing (`thumbFileId` → `GetFile`/`DownloadFile`), then persist `thumbLocalPath`. Tiles now show posters immediately (e.g., Handmaid’s Tale) without waiting for later updates.
- polish(telegram/playback): Keep clear IOException message when Telegram isn’t authenticated or the file isn’t downloaded yet; routing remains as fallback. Improves diagnosability of “unspecified IO” errors in player logs.

2025-10-12
- feat(telegram/telesync): Add dedicated Logcat tag `telesync` that dumps the raw TDLib response object for chat history backfills. When selecting a Telegram chat in Settings and pressing “Sync series/films”, the service logs the full `GetChatHistory` body and each `Message` entry for that chat. Output is a JSON‑ish reflection dump (class names + fields), chunked for Logcat.
- feat(telegram/sync/series): Make series sync unbounded. TelegramSyncWorker now requests full history for series chats, and the service paginates `GetChatHistory` until exhaustion (no cap). VOD sync remains single‑page by default. New `fetchAll` flag in IPC.
  - fix(telegram/paging): Robust history paging. Subsequent pages now use `fromMessageId = oldestId - 1` with `offset=0`, plus a duplicate‑page guard. Prevents cases where only the last visible message was indexed (e.g., Handmaid's Tale).
  - feat(telegram/series-poster): Series poster now uses the chat photo (largest size) as canonical poster. We download the chat photo via TDLib during indexing and put it first in `ObxSeries.imagesJson`.
  - feat(telegram/regex): Heuristics now also recognize German patterns like “Staffel 6 Folge 10”, in addition to S06E10 / 6x10 / S6 E10.
  - fix(telegram/playback-gate): Improve routing DataSource error when Telegram is not authenticated and no local file is available — clearer IOException (“auth/download”).
- fix(telegram/sync/await): Add "pull done" IPC reply so `TelegramSyncWorker` can await per‑chat completion before running the series aggregator. Prevents indexer from running too early.
- chore(telegram/logs): Add concise logs: Service logs per‑chat backfill start/end (pages, processed), per‑message OBX upserts; Indexer logs per‑series upserts and final totals (series/episodes). Tag: `TdSvc` and `TgIndex`.
- feat(telegram/series): Aggregate Telegram messages into OBX `ObxSeries` + `ObxEpisode` using SxxEyy heuristics. Episodes carry `tgChatId/tgMessageId/tgFileId` for direct tg:// playback. Library (Series) shows a new row "Telegram Serien" with series posters from downloaded thumbnails.
- chore(start/telegram): Remove per-chat Telegram rows in Start’s Series section. Series are shown only via the single aggregated "Telegram Serien" row under Library.
 - feat(telegram/meta): Enrich `ObxEpisode` with `mimeType`, `width`, `height`, `sizeBytes`, `supportsStreaming`, `language`. Add `fileName` to `ObxTelegramMessage`. Maintain `ObxIndexLang(kind="series")` for Telegram series based on episode languages.

2025-10-10
- fix(tdlib/auth): Queue phone/code/password until TDLib explicitly requests them (WAIT_FOR_NUMBER/CODE/PASSWORD); resend TdlibParameters + DB key on 400 "Initialization parameters are needed"; prefer QR on 406 UPDATE_APP_TO_LOGIN. Prevents race causing 400 during phone login.
- fix(settings/telegram): Migrate Telegram section to FocusKit/FishForm. Text inputs no longer auto-focus or open IME on TV; API ID/HASH edits commit on confirm (no live DataStore writes while typing). Cache limit uses DPAD-friendly slider. Resolves UI hangs during typing and focus issues.
- polish(settings/telegram/focus): Convert action buttons to FocusKit.TvButton/TvTextButton and group rows with focusGroup for visible TV focus halos and predictable DPAD.
- polish(settings/chat-picker): Make Telegram Chat Picker FocusKit-compliant: group focus scopes, use TvButton/TvTextButton, tvClickable list rows, and FishFormTextField for search (prevents IME auto-pop on TV).
- feat(tdlib/auto-start): Persist auth status across app restarts by auto-starting the TDLib service on app launch when Telegram is enabled and keys are present; service also reads SettingsStore keys if BuildConfig is empty when first used.
- fix(telegram/repo): Don’t filter out items with supportsStreaming=false. Treat video MIME/container (mp4/mkv/webm/mov/avi/ts) as playable via progressive download; broaden MIME mapping and add thumbnail download/store for posters.
- chore(dev/sync): Add scripts to mirror repo to ext4 for builds: `scripts/sync-ext4.sh` (one‑shot rsync) and `scripts/watch-sync-ext4.sh` (inotify watch). Excludes build/IDE artifacts; keeps repo in `$HOME/dev/FishITplayer` by default.
- chore(repo/wsl): Purged repo-local `.wsl-*` toolchains and caches (.wsl-android-sdk, .wsl-gradle, .wsl-java-17, etc.). WSL builds use user-home bootstrap; project tree stays clean.
- chore(repo/wsl): Ignore `.wsl-home/` and remove tracked `.wsl-home/.objectbox-build.properties`. WSL bootstrap/tooling stays in user home, not in the project tree.
- fix(player/controls): Auto-collapse overlay controls after inactivity
  - Phone/Tablet: hide after 5s without interaction.
  - TV: hide after 10s without interaction; DPAD activity within the overlay resets the timer.
  - Keeps quick-actions popup (Live) persistent until user toggles it off; modal sheets (CC/Aspect) block auto-hide.
 - feat(start/preview): Added StartScreen Compose previews with hoisted UiState and a state switcher (Loading/Empty/Error/Loaded). Rows render static lists (lazy + simulated paging) using our fish placeholder. All IO/LaunchedEffect paths are avoided in preview.

2025-10-09
- feat(player/seek): Improved seeking and scrubbing on Media3 1.8.0
  - Builder: set 60s seek increments (forward/back) for controller buttons.
  - DPAD burst (VOD/Series): 60s → 120s → 360s per tap within ~600 ms.
  - Long-press: keeps accelerated repeat seeking with smooth ramp-up.
  - SeekParameters: HLS/Live use CLOSEST_SYNC; MP4 uses EXACT.
  - Controls UX: no auto-hide; Back closes controls first (playback stays).
  - player/focus: Controls and quick-actions wrapped in FocusKit.focusGroup(); use FocusKit.TvIconButton re-exports instead of direct TvIconButton.
  - TV Mini-Player: Elevated to HomeChrome level as a global overlay. On TV, the PiP button shows the mini; phone/tablet still use system PiP.
  - Mini focus: MENU key focuses the mini when visible; during Chrome Expanded state the mini remains visible but is not focusable.
  - Player session: ExoPlayer is shared via PlaybackSession; InternalPlayerScreen does not release the player when the mini is visible on TV.
- build/compose: Remove explicit `composeOptions.kotlinCompilerExtensionVersion` pin. With Kotlin Compose Gradle plugin and Compose UI 1.9.3, the compiler is managed by the plugin; avoids mismatched 1.8.x compiler.
- fix(deps): `androidx.compose.material:material-icons-extended` isn’t published at 1.9.x. Pin it to 1.7.8 while keeping UI at 1.9.3; this artifact only ships vector assets and remains compatible.
- fix(okhttp): Drop `ZstdInterceptor` import/usage. The class is not required for core OkHttp 5.2.0 usage and caused an unresolved reference. We keep OkHttp 5.2.0 and default gzip handling.
- build(kotlin): Migrate deprecated `kotlinOptions { jvmTarget = "17" }` to the Kotlin `compilerOptions` DSL in `app` and `libtd`.
 - fix(home/live): Show the Live row even when it has no items so the leading “+” add‑tile is visible for first‑time favorites. Implemented a leading‑only path in `FocusRowEngine.MediaRowCore` so `ReorderableLiveRow` renders the add tile on an empty dataset.
- fix(tv/rows/dpad): Ensure left-most tile scrolls into view when navigating with DPAD LEFT. We now skip the “no-center on first tile” optimization only for the programmatic initial focus, not for user navigation to index 0.
 - chore(tv/rows): Disable row auto-initial-focus globally in TV mode. Rows no longer auto-request focus for the first tile; focus starts wherever the screen decides (e.g., chrome/search) and moves into rows only on user DPAD.

2025-10-08
- fix(tv/chrome/focus): Stop clearing focus before requesting header targets in `HomeChromeOverlay`; DPAD highlight stays visible immediately after expand (no 80 ms gap).
- fix(tv/chrome/focus-init): Retry header focus requests each frame until attached; logs `chromeFocusInit` attempts for diagnostics.
- polish(tv/chrome/ui): Header buttons wrap `FocusKit.tvFocusFrame` over a larger 52 dp hit area so the FocusKit glow appears immediately (no fallback green circle).
- chore(deps): Bump Compose UI to 1.9.3, Media3 stack to 1.8.0 (shared variable), and OkHttp to 5.2.0 (+ zstd module via `ZstdInterceptor`).
- fix(profile/ui): Restore `Arrangement.spacedBy` on the Profile manager column (accidental typo broke compilation on Compose 1.7).
- fix(tv/chrome): Make DPAD traversal in HomeChrome linear. Removed manual left/right focusNeighbors on header/bottom buttons (no wrap-around). Keep only UP/DOWN bridging between panels (header ↔ bottom). Result: left→right order is linear (Logo → Suche → Profile → Einstellungen; Live → VOD → Serien) and edges stop instead of cycling.
  - impl: Simplified focusNeighbors in `FishITHeader.kt` and `FishITBottomPanel.kt` to only set `down`/`up` respectively; rely on `focusGroup()` natural order for horizontal traversal.
  - follow-up: Bottom panel still skipped VOD on some devices. Added explicit linear left/right neighbors (Live→VOD→Serien) without wrap-around, keeping only UP bridge to header. File: `FishITBottomPanel.kt`.
- fix(compose/focus): Wrap fallback FocusRequester instances in `remember { ... }` inside `FishITHeader.kt` and `FishITBottomPanel.kt` to satisfy Compose 1.7 `@RememberInComposition` enforcement and avoid recomposition churn/warnings.
- fix(tv/buttons): Make `AppIconButton` explicitly focusable to stabilize DPAD focus candidates for header/bottom chrome icons. Prevents skipping middle icon (VOD) on some devices/DPAD heuristics.
- refactor(tv/chrome): Replace header/bottom chrome buttons with `FocusKit.tvClickable` boxes (no `AppIconButton`/`IconButton`). Removes legacy logic, guarantees focusable + halo/scale via FocusKit, and uses explicit neighbor mapping for deterministic DPAD traversal.
 - fix(tv/chrome/dpad): Remove global DPAD interception in `HomeChromeScaffold`. Focus navigation now relies on `focusGroup` + `focusNeighbors` only; prevents duplicate `moveFocus(...)` calls and erratic jumps/closures when moving between header/bottom/content.
- fix(tv/chrome/focus-trap): While HomeChrome is Expanded, content area disables focus (`focusProperties { canFocus = false }`) so DPAD stays within header/bottom until the user clicks or presses BACK. Prevents accidental collapse when moving Down from header.
 - ux(tv/chrome): Initial focus in header now prefers Search (when present) rather than Settings. This makes the global search immediately reachable when HomeChrome expands (including auto-expand on empty Start).
- fix(tv/focuskit/overlay): Add `FocusKit.LocalForceTvFocus` and scope it around HomeChrome overlays so `tvClickable` always runs the TV focusable path there. Ensures focus halo/scale render immediately on DPAD focus without requiring a click.
 - refactor(tv/homechrome): Introduce `HomeChromeOverlay` with a single top panel containing Logo, Search, Profile, Settings, and Live/VOD/Series. FocusKit tvClickable + explicit neighbors ensure deterministic DPAD; initial focus is Search. `HomeChromeScaffold` delegates to the overlay and no longer accepts a `bottomBar` slot. Start/Library/Settings/Profile/Detail screens updated to `showBottomBar=false` and pass `selectedBottom` for coloring.
- fix(focus/start/library): Restore Compose 1.7 compatibility by annotating FocusKit modifier facades, replacing `drawOutline` with a local outline renderer, and relocating Start/Library header composables inside LazyColumn items. Resolves `compileDebugKotlin` failures introduced after the FocusKit facade merge.
- refactor(home/chrome): HomeChrome header/bottom panels now use FocusKit focusGroup/DPAD helpers, DPAD routing sits on `FocusKit.onDpadAdjust*`, and chrome stays open while navigating; DPAD order is now deterministic (Fish → Search → Profile → Settings, Live → VOD → Serien) and all seven buttons stay reachable, collapsing only on BACK.
- feat(telegram/player): TDLib‑Streaming im App‑Prozess liest API‑ID/HASH nun zur Laufzeit aus den Settings, wenn BuildConfig leer ist. Dadurch funktionieren `tg://`‑On‑Demand‑Streams auch ohne Build‑Zeit‑Keys; weiterhin Fallback auf lokale Dateien, wenn keine Auth vorliegt.
- fix(start,library): Balance unmatched braces in `StartScreen.kt` and `LibraryScreen.kt` (add missing closing brace at file end). Resolves Kotlin parser errors "Expecting '}'" during kapt stubs.
- fix(settings): Replace legacy `ui.skin` wrappers with `FocusKit.run { tvClickable(...) }` in `SettingsScreen.kt`; keep phone OutlinedTextFields and TV edit dialogs. Defers full FishForm migration for Settings to a later pass.
- refactor(ui/header): Library rows (search, curated, providers, genres, years, Telegram) now emit FishHeaderData beacons and rely exclusively on the floating overlay; removed legacy inline Text headers and the unused `SeriesNewChip` badge.
- chore(ui/forms): TvSwitchRow/TvSliderRow/TvSelectRow route DPAD LEFT/RIGHT via `FocusKit.onDpadAdjustLeftRight`, eliminating local `onPreviewKeyEvent` handlers.
- feat(ui/forms): Added FishForm components (`FishFormSection/Switch/Select/Slider/TextField/ButtonRow`) in `ui/layout`; existing `Tv*` forms now delegate to them and CreateProfileSheet/PlaylistSetup/Settings (TV mode) use the new module.
- refactor(ui/tv): StartScreen now renders all VOD/Series/Live sections via `FishRow`/`FishRowPaged` + Fish tiles, eliminating the bespoke `SeriesRow`/`VodRow`/`LiveRow` composables.
- refactor(ui/library): Library screen is fully on FishRow/FishMediaTiles for Live/VOD/Series (including search + expandable groups); Telegram rows use FishRow as well.
- refactor(focus): Row engine logic moved into `ui/focus/FocusRowEngine.kt`; legacy `ui/components/rows/RowCore.kt` is gone and FocusKit is the single entry point.
- refactor(ui/components): Removed the unused `ResumeCarousel`/`ResumeRow`/`MediaCard` composables now superseded by FishTile rows; cleaned up imports and badges accordingly.
- docs(layout): Updated Fish layout and playback launcher docs to drop references to the removed Resume carousel and card helpers.
- feat(ui/header): Introduced `FishHeaderHost` + floating beacon overlay (Text/Chip) driven via `header` parameters on `FishRow`/`FishRowPaged`. Start screen rows now use the overlay instead of inline headers.
- refactor(ui/live): Favorite Live reorder row now rides on FishRow + FocusRowEngine item modifiers, and LiveAddTile scales via Fish tokens.

2025-10-07
- fix(ui/home): Dropped the deprecated Compose pointer `consume` import so Home rows compile against 1.7+ pointer APIs.
- refactor(ui/detail): Series detail seasons & episode entries now rely solely on FocusKit (tvFocusableItem/tvFocusFrame/tvClickable) and AccentCard participates in FocusKit focus scaffolding.
- refactor(ui/start): Start screen now renders Series/VOD/Live (and Telegram) exclusively via FishRow/FishTile, removing CardKit columns + BoxWithConstraints and aligning layout with Library.
- fix(ui/home): Updated Fish rows/tiles to Compose 1.7 APIs (KeyEvent accessors, pointer consumePositionChange, fillMaxSize overlays) so Kotlin compile passes.
- fix(ui/series): Wire Series Fish tiles with assign callbacks and reuse FishActions helpers via scoped receivers to keep kid-assign focus consistent with VOD.
- fix(telegram/index): Guard TDLib content reflections against null payloads to avoid Any?/Any mismatches during ObjectBox indexing.
- chore(build): Silenced Kotlin warnings by migrating to new pointer consumption, opting into FlowPreview flows, replacing deprecated TV feature checks, tightening rememberSaveable usage, and cleaning always-true guards in VOD detail.

2025-10-03
- fix(ui/start): StartScreen composable braces fixed (LaunchedEffect block closed correctly) and `ChannelPickTile` added inline. Resolves "@Composable invocations can only happen from the context of a @Composable function" and top-level declaration errors during compile.
- feat(ui/start/assign): Added Assign Mode on Start. Toggle selection across Series/VOD (and Live) rows; pick profiles via KidSelectSheet and bulk-allow with ObjectBox batch ops. Rows reuse existing assign buttons to toggle selection during Assign Mode.
- feat(details/vod): Reintroduced kid whitelist actions in VOD detail. MediaActionBar now shows “Für Kinder freigeben” and “Freigabe entfernen” (gated by permissions), wired to KidSelectSheet with bulk allow/revoke.
- fix(ui/gate,tv): Profile selection and PIN numpad now show proper TV focus. Profile tiles moved onto TvFocusRow with `tvFocusableItem` and `focusScaleOnTv`; `tvClickable` wrappers use neutral scaling to avoid double animations. Numpad keys use the same focus skin and log focus tags. Focus persists per row state key and brings items into view.
- fix(ui/series-detail/tv): Season filter chips now use TvFocusRow + tvFocusableItem and focusScaleOnTv. They show the same TV focus glow/scale as buttons and are brought safely into view on focus; per-item bring-into-view is disabled to avoid jitter. Added focus logs (focus:widget Chip season-<n>). Updated both code paths in SeriesDetailScreen.
- refactor(details/vod): Rebuilt VOD detail screen on a stable scaffold (HomeChromeScaffold → Box background → Card + LazyColumn sections). OBX-first load with on-demand Xtream enrichment; simplified Facts/Plot sections; safe Play/Resume actions; removed brittle overlays causing brace/parse issues. This restores full metadata rendering with a single scroll container.
- feat(details/mask): Introduced unified DetailPage + DetailBackdrop + DetailSections modules. They enforce a single, shared layout (full-screen hero + gradients + AccentCard + DetailHeader + sections). VOD now uses this mask; Series/Live will migrate next for 1:1 parity.
- fix(details/vod): Build failure “Expecting '}'” in `VodDetailScreen.kt` fixed by removing an extra closing brace at the end of the composable; file now parses and kapt/compile proceed.
- fix(details/back): Replace recursive BackHandler usage on VOD/Series detail screens with safe dispatcher callbacks so BACK/ESC exits without crashing; scoped `fmt` helpers locally to resolve Compose visibility errors.
- fix(details/vod): VOD detail now uses focusable cards (plot + info) that expand/collapse on DPAD, surfaces the complete Xtream metadata (Bewertung, Release, Laufzeit, Format, MPAA/Age, Provider/Kategorie, Genres/Länder, Cast, Audio/Video/Bitrate, IMDb/TMDb Links), keeps kid actions/progress inline, ensures hero backdrops differ vom Poster, und loggt die gerenderten Abschnitte via GlobalDebug.

2025-10-04
- fix(ui/start): Startscreen rows no longer overlap at the top. Enforced per-section minimum height so Serien/Filme/Live occupy distinct vertical slots in all orientations.
- ux(start/assign): Removed the non-functional "Zuweisen" button from Start. Added a per-tile, global selection badge (+/✓) to mark items directly; selected tiles get a visible frame. A floating plus button appears when at least one item is marked and opens the profile selection to assign contents.
- fix(tv/forms): Profile creation uses TV Form rows (TvTextFieldRow/TvSwitchRow/TvButtonRow). DPAD focus works; OK/Erstellen triggers save reliably.
- fix(kids/whitelist): After assigning content to profiles (bulk from Start), Start now reloads filtered lists immediately (and also on profile switches, as before) so items become visible without app restart.

2025-10-02
- feat(telegram/service): Expose TDLib chat IPC (`CMD_LIST_CHATS`, `CMD_RESOLVE_CHAT_TITLES`, `CMD_PULL_CHAT_HISTORY`) so UI/Worker reuse the authenticated service client instead of spawning reflection clients.
- fix(telegram/settings): Chat picker binds `TelegramServiceClient`, streams auth state, loads main/archive chats via the service, and resolves stored chat IDs through the same IPC (no more "Bitte zuerst verbinden" after login).
- chore(telegram/sync): Rewire `TelegramSyncWorker` to start the service with stored API keys and trigger `pullChatHistory` per selected chat (ObjectBox indexing stays inside the service).
- chore(telegram/sched): Added `SchedulingGateway.onTelegramSyncCompleted()` so post-sync tasks (cache cleanup, OBX key backfill, optional full refresh) run automatically; Settings now pass `refreshHome=true` when a sync is triggered.
- feat(telegram/ui): Introduced `TelegramRow`/`TelegramTileCard` (blue “T” badge) and wired Start + Library to render per-chat Telegram rows with the central row engine; row state keys integrate with `rememberRouteListState` for focus persistence.
- feat(telegram/cache): Service now consumes `loadChats` + chat updates to keep a local chat cache, resolves list ordering via `chatPosition.order`, and falls back to `getChats` only when the cache is cold.
- feat(telegram/auth): Phone number submits use `PhoneNumberAuthenticationSettings` with optional authentication tokens and `isCurrentPhoneNumber`, enabling same-device confirmation when Telegram is installed.
- ux(telegram/login): Login dialog detects the Telegram app, adds a toggle for “Code auf diesem Gerät bestätigen”, and auto-opens the deep link when QR login is requested, avoiding the second-device hop.

2025-10-01
- feat(telegram/settings): Show chat names for selected Film/Series sync sources (resolves titles via TDLib when authenticated).
- feat(telegram/sync): Implement TelegramSyncWorker backfill. Fetches recent messages from selected chats (VOD/Series) and indexes minimal metadata to ObjectBox (ObxTelegramMessage), enabling tg:// playback and local-path updates.
- feat(telegram/ui): Add Telegram rows on Library VOD/Series tabs (one row per selected chat, tiles tagged with blue "T"). Start screen global search now includes Telegram results as an extra row.
- feat(telegram/playback): Tune ExoPlayer LoadControl for Telegram (tg://) for low RAM buffers and rely on TDLib on-disk caching during playback.
  - TV (v7a): small 16 KiB allocator segments, ~2–6 MiB target buffer; aggressive allocator.trim() on pause/idle/end; cancel TDLib download on player close to free IO early.
- feat(telegram/metadata): Expanded SxxExx parser (ranges S01E01-03, 1x02-05, language tags [DE]/[EN]/…). Extract and persist additional metadata via TDLib: `durationSecs`, `mimeType`, `sizeBytes`, `width`/`height`, `language`. MediaItems carry `durationSecs`, `plot` and inferred `containerExt`.

2025-09-30
- fix(live/detail): Kotlin parse error from stray brace in `LiveDetailScreen` — moved EPG/Kid dialogs inside composable and balanced braces.
- chore(start,library): Migrate remaining direct PlayerChooser calls to PlaybackLauncher (flag `PLAYBACK_LAUNCHER_V1`); internal playback opens via nav in `onOpenInternal`.
- feat(details): Wire onOpenDetails for VOD “Ähnliche Inhalte” and Live “Mehr aus Kategorie” (new lambdas `openVod`/`openLive`, wired in `MainActivity`).
- fix(compose): Build fixes — opt-in FlowRow in `DetailHeader`, use `fillMaxSize` in `HeroScrim`, import `KeyboardOptions` from foundation.text, pass named `onRetry` to `ErrorState`, remove stale `MediaItem.subtitle`.
 - fix(theme): Re-apply global dark theme via `AppTheme` using a single dark color scheme (no dynamic light variants).
 - fix(tv/chrome): DPAD LEFT expands HomeChrome when the focused row is at the very left or when no content is focused/available; also preserved row-level edge-left expand behavior.

2025-09-28
- fix(tv/rows): Consume DPAD on KeyDown in RowCore (list+paged) to prevent double traversal that skipped one tile per press.
- fix(tv/start): Ensure first tile is focusable and receives the initial FocusRequester so visual focus (scale/halo) is visible immediately at startup.
- fix(tv/rows): Remove duplicate declaration of currentFocusIdx and add it to paged rows to restore focus index tracking and edge-left chrome behavior.
- fix(tv/chrome): Default Home chrome to Collapsed on TV so header/buttons don’t steal focus on start or after returning from details; rows re-collapse chrome on focus updates.
- fix(tv/rows): Focus visuals drawn by tvFocusFrame scale horizontally only (scaleX), keeping vertical size stable to avoid jumpy vertical movement when switching rows on Library/VOD.
- fix(icon): Restore adaptive launcher icon using fish assets. Foreground uses `drawable/fisch_header.png` centered; background uses `drawable/fisch_bg.png` centered. Applies to phone and TV (no stretch, proper masking).
- chore(icon): Apply standard safe insets for adaptive foreground (18dp on all sides) via `ic_launcher_foreground_inset.xml`, optimized for common device masks (round/squircle) to avoid cropping.
- fix(tv/home): Start series/VOD/Live rows now register focus state keys so the first tile auto-focuses again and DPAD LEFT only expands Home chrome when truly at the edge.
- fix(tv/rows): MediaRowCore re-queues minimal scrolls and persists focus after LazyRow momentum settles, preventing skipped tiles and stale focus indices during DPAD navigation.
- fix(tv/tiles): Ensure `tvClickable` can share a caller-supplied `FocusRequester` so the initial tile scales/halo-highlights immediately after startup instead of waiting for the first manual DPAD move.
- fix(tv/rows): Track the currently focused index alongside pending writes so DPAD LEFT no longer expands HomeChrome while the cursor sits mid-row (e.g. after returning from details).
- fix(tv/chrome): Remove DPAD LEFT long-press to toggle HomeChrome. Long-press LEFT now has no global effect; Menu/Burger remains the only toggle. Start rows handle LEFT-at-edge by expanding chrome explicitly.
- chore(rows): Temporarily disable artwork-first reordering. Lists and paging sources no longer push tiles without images to the right; incoming order is preserved.
- fix(tv/home): deterministic initial focus on Start — only the topmost Series row is allowed to request the initial focus on TV; other rows (VOD/Live) suppress their first-focus request on Start. Adds `RowConfig.initialFocusEligible` and wires StartScreen to set VOD/Live=false, Series=true.
- feat(debug/focus): GlobalDebug now logs the initially focused tile at screen start (no interaction required). Row engines announce the expected first focused tile right after requesting initial focus; Home chrome logs header/bottom focus as well.
- fix(tv/rows): Prevent over-scrolling on DPAD focus moves. `centerItemSafely` no longer re-centers items that are already fully visible; it performs a minimal scroll only when the focused item is clipped. Avoids jumping several tiles so the focused tile stays on screen.
- fix(tv/favorites): Reorderable favorites row intercepts DPAD LEFT/RIGHT only when selection mode is active (after long-press). Without selection, LEFT/RIGHT now navigate focus normally, preventing the impression of “LEFT moved to the right tile”.
- fix(navigation): add `navigateTopLevel` extension and use `popUpToStartDestination(saveState=true)` to preserve state for top-level route switches.
- fix(tv/compat): add `ui/compat/FocusCompat.focusGroup()` shim to satisfy `focusGroup()` usages across rows and header.
- feat(tv/rows): add `ui/tv/TvRowScroll.centerItemSafely()` and wire it where referenced by `RowCore`/`TvFocusRow`.
- feat(ui/debug): add `ui/debug/safePainter()` to avoid drawable crashes and remove Icon overload ambiguity.
- feat(adults): add `core/util/Adults.kt` with `isAdultCategory`, `isAdultCategoryLabel`, and `isAdultProvider` helpers; align Library/Repository filters.
- feat(xtream): add `core/xtream/XtreamImportCoordinator` with `seederInFlight`, `runSeeding`, `enqueueWork`, and `waitUntilIdle()`; integrate with `XtreamSeeder` and `XtreamDeltaImportWorker`.
- fix(compose): import `semantics.role` in `TvModifiers.kt`; add missing `onFocusEvent` import in `HomeChromeScaffold.kt`.
- fix(compose scope): add `ui/skin/PackageScope.run { }` as a `@Composable` scope to enable `Modifier.tvClickable(...)` inside modifier chains.
- fix(compose scope): resolve `tvClickable`/`focusScaleOnTv`/`tvFocusableItem` lookups by delegating via alias imports inside `SkinScope`.
- fix(debug/log): add `GlobalDebug.logObxKey(kind, id, change: Map<...>)` overload and adapt worker calls.

2025-09-27
- fix(manifest/icon): set application icon to `@mipmap/ic_launcher` and add `android:roundIcon` (`@mipmap/ic_launcher_round`) instead of the missing `@drawable/fisch_bg`. Launcher already uses adaptive mipmaps; this aligns the manifest with actual assets.
- docs(roadmap): Priorität‑1 Tasks für TV Fokus/DPAD vereinheitlicht: alle horizontalen Container → TvFocusRow (inkl. Chips/Carousels), alle interaktiven Elemente → tvClickable/tvFocusableItem (No‑Op auf Phone), zentrale Scroll+Fokus‑Registry (ScrollStateRegistry), einheitliche Auto‑Collapse/Expand‑Trigger im HomeChromeScaffold, kein onPreviewKeyEvent außer echten Sonderfällen, Audit‑Skript erzwingt Regeln.

- feat(ui/state): finalisiere Fokus‑Gedächtnis. `ScrollStateRegistry` speichert jetzt `RowFocus(index)` pro Key inkl. `readRowFocus`/`writeRowFocus` und `rememberRowFocus(key)`. List/Grid‑Remember‑Funktionen nach `ui/state/RememberHelpers.kt` extrahiert; beide lesen/schreiben zentrale Registry.

- feat(ui/skin): `tvClickable` macht auf TV Elemente focusable + semantics(Role.Button), zeichnet Halo/Scale und bringt sie bei Fokus in Sicht; auf Phone/Tablet bleibt es ein normales clickable ohne Effekte. Neu: `tvFocusableItem(stateKey, index, ...)` markiert Items als fokusierbar, schreibt den Index in die zentrale Registry und bringt sie optional in Sicht.

- feat(ui/tv): neue vereinfachte `TvFocusRow(stateKey, itemCount, ...)` die `rememberRouteListState` + `rememberRowFocus` nutzt und pro Item `tvFocusableItem(...)` setzt. Migration begonnen: Provider‑Chips im Start‑Picker und Kategorie‑Chips im Player‑Sheet auf TvFocusRow + tvClickable/tvFocusableItem umgestellt.

- refactor(tv/migration): weitere Leisten auf `TvFocusRow(stateKey, …)` migriert: Resume‑Carousels (VOD/Series), Start‑Home Resume‑Row, SeriesDetail Season‑Strip. Interaktive Chips/Labels nutzen `tvClickable` (No‑Op auf Phone).

- refactor(settings/profile): Chip‑Leisten in Settings (Seeding‑Regionen) und Profile (Typ‑Auswahl, Tabs, Whitelist‑Kategorie‑Expander) mit `tvClickable` versehen; DPAD‑Fokus/Skins TV‑konform. Titel/Plot‑Clickables in VOD‑Details auf `tvClickable` umgestellt. Falls Leisten horizontal werden, können sie mit `TvFocusRow(stateKey, …)` eingehängt werden.
  - Fix: Vermeide leere `onClick={}` auf `FilterChip`/`AssistChip` bei zusätzlichem `tvClickable` am Modifier. Die Chip‑`onClick` spiegelt jetzt die gleiche Aktion wie `tvClickable`, um Semantik‑Konflikte/Ereignis‑Konsum zu verhindern.

- feat(tv/chrome): vereinheitlichte Auto‑Collapse/Expand‑Trigger im `HomeChromeScaffold`. Collapse sobald Fokus im Content (Rows melden `focusedRowKey` via LocalChromeRowFocusSetter) oder bei vertikalem Scroll. Expand beim DPAD‑UP auf dem ersten Item der obersten Row oder wenn der Header Fokus erhält. Inhaltspadding ist animiert mit dem Chrome‑State.

- chore(tv/dpad): repo‑weit manuelle DPAD‑Abgriffe entfernt, wo keine Sonderfälle. `RowCore` und `RowCorePaged` geben UP/DOWN nicht mehr per `onPreviewKeyEvent` vor; Standard‑Traversal übernimmt. In `HomeRows`: DPAD‑LEFT‑Long‑Press pro Tile entfernt (global in Chrome), reine Debug‑KeyUp‑Logs entfernt. Reorder‑Pfad behält gerichteten LEFT/RIGHT‑Abgriff in `LiveTileCard` bei (nur aktiv, wenn Reorder‑Handler übergeben).

- tooling(tv/audit): `tools/audit_tv_focus.sh` erweitert. Scannt horizontale Container (roh‑LazyRow etc.), listet Roh‑`clickable(` (tvClickable bevorzugen), und prüft Screens auf `onPreviewKeyEvent`/DPAD‑Nutzung. CI‑Modus via `tools/audit_tv_focus.sh ci` erzeugt einen Report (`tools/audit_tv_focus_report.txt`) und schlägt bei Findings fehl.
- CI: Audit in `.github/workflows/ci.yml` integriert (Step „TV Focus/DPAD Audit“).

- refactor(tv/clickable): restliche Roh‑clickable in TV‑sichtbaren Zeilen umgestellt auf `tvClickable` (Player‑Listensheet Rows, Settings‑Edit‑Zeilen). Audit erweitert, um auch `clickable { ... }` (Trailing‑Lambda‑Form) zu erkennen.
- feat(tv/chrome): `RowCore`/`RowCorePaged` melden nun den Row‑Fokus via `LocalChromeRowFocusSetter` (per `config.stateKey`), sodass der Header sauber einklappt, auch wenn die Row nicht `TvFocusRow` nutzt.

- polish(tv/focus): alle TV‑sichtbaren Material‑Buttons (Button/TextButton/IconButton) mit `focusScaleOnTv()` versehen (Player‑Overlays, Start‑Dialoge, Settings‑Bearbeiten/Actions, Profile‑Manager, TrailerBox, CollapsibleHeader, Resume‑Karten‑Action‑Icons). Einheitliche visuelle Hervorhebung auf Fokus.
  - audit: Erweiterung um Advisory‑Check für Buttons ohne `focusScaleOnTv` (informativ; CI schlägt nicht fehl). CI installiert `ripgrep`, um Audit stabil auszuführen.

2025-09-25
- fix(tv/focus-enter): guard custom enter focus with an explicit firstAttached flag in RowCore, RowCorePaged, and ReorderableLiveRow. Only enable `focusProperties { enter = { firstFocus } }` after the first item's FocusRequester is attached and visible. Prevents `IllegalStateException: FocusRequester is not initialized` on DPAD DOWN from header/home.
- chore(debug/tile-focus): add missing tile-focus logs for VOD tiles (VodTileCard) and add tree-path logging for Series tiles. Now all tiles log `focus:<type> id=<id> <title>` plus a `tree:` hint when focused.
- fix(build): resolve Kotlin error "This annotation is not repeatable" by merging duplicate `@file:OptIn` annotations in `app/src/main/java/com/chris/m3usuite/ui/components/rows/HomeRows.kt`.
- fix(tv/rows): remove manual item-count gating in row engine (MediaRowCore). Rely on LazyRow virtualization so adjacent DPAD traversal always has a composed neighbor; eliminates “blind” scrolling.
- fix(tv/live-tile): only intercept DPAD LEFT/RIGHT in LiveTileCard when reordering handlers are provided; otherwise let default focus traversal handle neighbors (prevents double-advance on KeyUp).
- feat(tv/focus-defaults): when a row gets focus on TV, the left-most tile is requested as the initial focus. As you navigate, the focused tile is auto-centered in the viewport (RowCore & TvFocusRow).
- fix(ui/state): replace direct rememberSaveable(LazyListState) usages with route-keyed saver (`rememberRouteListState`) to avoid IllegalArgumentException from SaveableStateRegistry.
- fix(tv/rows): auto bring-into-view on DPAD focus in core row engine (MediaRowCore & MediaRowCorePaged) so tiles highlight/scale on focus without requiring a click.
- fix(tv/focus): switch custom TV focus helpers to `onFocusEvent`, reorder focusable observers so DPAD navigation lights cards without clicking, and reroute TvFocusRow scroll handling to avoid compose layout crashes.
- feat(nav/state): add `navigateTopLevel(route)` extension that applies `popUpTo(start){ saveState=true }`, `launchSingleTop=true`, `restoreState=true`. Refactor top-level switches (header/logo/search) to use it.
- fix(start/search): persist global search dialog state and query with rememberSaveable; persist live picker query/provider via rememberSaveable.
- fix(inputs/save): make profile creation inputs and profile manager fields saveable (`CreateProfileSheet.name/isKid`, `ProfileManager.newKidName`, per-kid `name`). Persist Telegram chat picker `folder`/`search` and Xtream portal check `portal`/`info`. PIN dialog fields (`old/pin/pin2/error`) are saveable too.
- fix(tv/start): live picker provider chips now use TvFocusRow + saveable list state.
- tooling(tv/audit): add tools/audit_tv_focus.sh to scan horizontal containers, manual DPAD handlers, and clickable-without-focusable risks. Run it to locate TV rows for migration.
- feat(ui/tv): introduce TvFocusRow wrapper (compose.foundation LazyRow + TV focus) in app/src/main/java/com/chris/m3usuite/ui/tv/TvFocusRow.kt. Adds focusGroup() on TV, per-item focusable() and bring-into-view on focus.
- fix(tv/series): migrate SeriesDetailScreen season strip to TvFocusRow, preserving spacing/padding and saving a LazyListState keyed by seriesStreamId. Resolves DPAD skips (e.g., only seasons 1, 2, 9 reachable).
- fix(tv/player): migrate category chip row in the live list sheet to TvFocusRow so DPAD navigation and auto scroll work reliably.
- fix(tv/start): migrate StartScreen provider chip row in live picker to TvFocusRow with saved state; auto bring-into-view on focus.
- fix(tv/resume): migrate Resume VOD/Series carousels to TvFocusRow and make ResumeCard accept a modifier so items are focusable and scrolled into view on TV.
- polish(tv/home): add focusGroup() to ReorderableLiveRow container to scope DPAD focus within the row on TV.
- fix(tv/details): VodDetailScreen poster uses tvClickable; title/plot clicks are focusable for DPAD reachability.
- fix(tv/live-detail): make channel logo button tvClickable with Circle shape and halo; DPAD focus scales and is reachable.
- fix(tv/settings): section headers use tvClickable (role=Button) instead of raw clickable for consistent TV focus and halo.
- chore(tv/dpad): replace onPreviewKeyEvent left/right in LiveTileCard with FocusManager.moveFocus when not reordering; keeps natural DPAD behavior.
- feat(tv/chrome): add focusGroup() to FishITHeader and FishITBottomPanel containers on TV; header logo uses tvClickable. Bottom panel icon buttons remain TvIconButton‑based with halo/scale.
- polish(tv/bottom): bottom panel icons use Duotone when inactive and Primary for the selected tab (live/vod/series).
- fix(tv/rows): add focusGroup() to core row engine (RowCore/LazyRow) on TV and make tvClickable() focusable() so cards can be reached by DPAD and auto bring-into-view stays consistent.
- feat(ui/home): VOD tiles trigger a light on-demand detail import on focus when plot is missing, so Start rows show summaries sooner. Import is deduped and updates are propagated via OBX change signals.
- feat(ui/tv/chrome): TV-only chrome state in HomeChromeScaffold. Burger/Menu toggles Expanded mode (header+bottom slide in, focus-trap active, content blurred); Back/Menu collapses. Auto-collapse on content scroll. DPAD UP/DOWN jump between panels. Content padding animates to reclaim space. Non‑TV unchanged.
- feat(ui/chrome): add `showHeader` to `HomeChromeScaffold` so the top panel can be globally toggled like the bottom bar; content padding reclaims the space when hidden. Use same boolean as `showBottomBar` to collapse both together if desired.
- fix(ui/home): enforce 40/40/20 section weights on Start (Series/VOD/Live) by applying weights to the direct Column children; robust even with wrapped cards. Portrait keeps 1/1/1. Maintains inner card fill and row height overrides.
- fix(ui/state): add route-keyed in-memory scroll cache and wrap Start/Library content in SaveableStateHolder so vertical and horizontal list positions persist across deep navigation (details, settings, search, tab hopping). Cache resets on process restart as desired.
- fix(vod/details): fetch VOD details reliably across panels by trying multiple id field names (`vod_id|movie_id|id|stream_id`) for `get_<alias>_info`. Also allow UI-triggered detail imports (VOD/Series) regardless of the `M3U_WORKERS_ENABLED` gate so plots/posters load when opening detail screens.
 - Also accept panels that return VOD details under `info` instead of `movie_data`, and read plot from `plot|description|plot_outline|overview` to cover common skins.
- fix(nav/state): preserve scroll positions when switching between Start (library?q=...) and Library (browse) by enabling Navigation-Compose state saving (`restoreState=true`, `popUpTo(findStartDestination()) { saveState=true }`) and keeping `LazyListState` keyed per route/tab. Start/Library now resume exactly where you left off.
- ui/settings: collapse individual settings blocks into expandable cards; Xtream credential inputs now sit at the top and stay expanded by default while all other sections start collapsed for faster browsing.
- ui/chrome: header and bottom navigation buttons now brighten by ~40% on focus for clearer TV highlighting.
- fix(ui/tv): Header and bottom AppIconButtons share the TV interaction source, so focus halos appear as soon as focus lands (no click needed).
- chore(xtream/import): Central seeding coordinator defers queued delta jobs, blocks workers until seeding finishes, and exposes an "Import läuft…" chrome indicator so users see progress without losing UI control.
- fix(adults/gate): Filter every `For Adult`/`For Adults`/`adult_*` category across queries, search, start rows, and library when the toggle is off.
- player/tv: PiP requests on TV keep the FishIT app in the foreground (no jump to launcher) and toast the fallback; PiP/Subtitles/Resize overlays are now focusable via DPAD in both overlay rows.

2025-09-24
- ui/home: resize Start screen card heights to roughly 40/40/20 (Series/VOD/Live) so on-demand shelves keep prominence while Live TV stays visible without dominating.
- ui/library/vod: swap the 2025–2024 rail header for a neon CategoryChip to match curated rows and drop the plain text label.
- ui/profile: make the kid/guest permissions sheet scrollable so all toggles stay reachable on smaller layouts.
- fix(kid/whitelist): ensure kid/guest media queries keep pulling data until assigned whitelist items surface so allowed rows actually render.
- feat(ui/detail): use poster/backdrop as a 50% overlayed screen background, remove secondary image galleries, and render detail posters with full-fit scaling plus keyed caching so entire artwork stays visible across TV and mobile.
- fix(ui/vod-detail): reinstate the scroll container so plot text and metadata stay reachable, fetch Xtream details when the plot is missing, and surface a fallback badge when no summary exists.
- polish(ui/library/series): swap the plain “Neu” label for an animated Orbitron chip, drop the grouping toggle + provider header in Series, and restyle the “Neueste zuerst” switch with white typography.
- fix(ui/cards): stop vertical flicker by disabling auto bring-into-view on horizontal cards and keep rows stable while moving artless items to the back (also applied to ObjectBox paging sources).
- ui/assets: refresh Fish branding — replace drawable `fisch.png` with `fisch_bg.png`/`fisch_header.png`, simplify `tv_banner.xml`, and point the manifest launcher icon at the new background asset.
- ui/fx: make `FishBackground` and shimmer placeholders static (no spin), update header/logo assets, and remove `FishSpin` hooks across home chrome.
- ui/images: size-aware Coil requests now derive slot pixels via `onSizeChanged`, always enable RGB_565, add WxH-aware cache keys, and fall back to the new fish placeholders for posters/heroes.
- ui/home: remove the start-screen loading overlay, persist LazyRow positions with explicit `stateKey`s, save the search dialog state with `rememberSaveable`, and keep library navigation latched when closing search.
- ui/components/rows: `MediaRowCore` preloads up to the saved index + 20, rows accept optional `stateKey`s, and TV focus scale drops to 1.06/1.08 for steadier tiles (including reorderable rows).
- ui/screens: Library expands all provider/genre/year sections inline (Adults umbrella keeps the toggle) and wires `stateKey`s through; VOD/Series detail screens show full plots without toggles; HomeChromeScaffold keeps static chrome padding with the new background.

# 
# FishIT Player – Changelog

All notable changes to this project are documented here. Keep entries concise and tied to commits/PRs.

2025-09-23
- fix(ui/start): Search dialog now closes cleanly; closing it clears the `qs=show` flag so navigating back never reopens the sheet unexpectedly.
- fix(ui/library/vod): "For Adults" umbrella shows again when enabled (expanded state remembered); adult providers stay separate from regular buckets.
- perf/ui-state: Route-scoped scroll savers now persist via explicit keys so list/grid positions survive navigation like Netflix-style resumes.
- ui/tv: Shared focus halo via `tvClickable`/`focusScaleOnTv`; `AppIconButton` now wraps `TvIconButton` so header/bottom controls highlight immediately on focus.
- ui/chrome: FishIT header and bottom panel now render with dark gradients so top/bottom chrome no longer flashes white on light content.
- nav/settings: Header settings button wires through every screen (hidden where blocked) so jumping into settings works outside Start; Settings screen back button now pops the stack to avoid re-adding library routes.
- nav/back: Hardware/system back returns to the previous screen as expected after leaving settings by relying on `popBackStack()` instead of synthetic navigation.
- build(release): Enable R8 minify + resource shrinking; set `debuggable=false` for the release buildType and wire ProGuard with `proguard-android-optimize.txt` and `proguard-rules.pro`.
- proguard: Strip Android `Log` calls in release via `-assumenosideeffects`. Add keep rules for ObjectBox entities, and suppress warnings for Media3, OkHttp/Okio, and `kotlinx.coroutines.debug`.
- deps: Verified `kotlinx-coroutines-debug` is not included in release; ensure it remains debug-only if added in the future.
- packaging: Exclude redundant META-INF licenses/notices, Kotlin module metadata, and non-target JNI ABIs (x86/x86_64/mips/armeabi) to reduce package size and avoid duplicate resource merges.
- splits: Enable ABI splits for APKs — generates separate 32‑bit (`armeabi-v7a`) and 64‑bit (`arm64-v8a`) release APKs.
- images(coil3): Global ImageLoader caches tuned (memory ~25%, disk dynamic) and per-request NetworkHeaders wiring. OkHttp integration remains via module dependency; explicit factory injection deferred.
- cache(dyn): HTTP (OkHttp) cache size now dynamic by ABI/Low‑RAM/available space (32‑bit base 32 MiB, 64‑bit base 96 MiB; caps 64/128 MiB; min 1% free). Coil disk cache now dynamic too (32‑bit base 256 MiB, 64‑bit base 512 MiB; caps 384/768 MiB; min 2% free).
- images(coil3/rgb565): Use RGB_565 for small images (avatars, provider icons) to reduce RAM without affecting posters/hero. Implemented via `preferRgb565` flag in `AppAsyncImage` and targeted call sites.
- ui/images: Explicit ContentScale set where implicit before — ProviderIcons and StartScreen logos use `Fit` (no crop), avatars use `Crop` (fill circular). Performance-neutral but improves visual correctness and consistency.
- ui/home-rows: Throttle visible-items snapshot with `distinctUntilChanged + debounce(100ms)` for EPG/detail prefetch across Live/VOD/Series rows (paged + non-paged). Focus EPG fetch uses a 120ms cooldown to avoid churn. Preview videos remain OFF by default.
- media3/exo: Keep rules minimal — only `-dontwarn androidx.media3.**` (no global keep). Player now explicitly uses `DefaultRenderersFactory` (decoder fallback ON, extension renderers OFF) to prefer hardware decode. Internal PlayerView switched to SurfaceView (was TextureView) for better TV performance. LoadControl left at `DefaultLoadControl`.
 - build/splits: Remove `ndk.abiFilters` from app module to resolve AGP warning when ABI splits are enabled; per‑ABI release APKs remain via `splits.abi` (arm64‑v8a, armeabi‑v7a).
 - seed/prefix: Added global seeding prefix whitelist (Settings → Seeding/Regionen). Default DE/US/UK/VOD; Xtream quick seed now fetches only these categories to reduce network and DB load. Additional regions can be enabled on demand.
 - docs: Roadmap cleaned (near/mid‑term only) and Architecture Overview synced (OBX‑first, dynamic caches, Media3). AGENTS.md push policy now includes a repo‑local `core.sshCommand` so pushes work out of the box in WSL.

2025-09-22
- fix(delta): Heads-only delta now upserts new VOD/Series rows from list heads even when details are skipped. Previously, brand-new items were only created during the detail phase, leaving counts stuck at the quick-seed size until many details completed.
- change(seed): Increase quick seed per-category cap from 20 → 200 (as documented). First paint still spreads evenly across categories but shows more items immediately.
- perf(net/xtream): Add global per-host rate limiter (~120ms min interval) and 60s in-memory response cache (15s for EPG) in `XtreamClient`. Repeated list/detail calls now hit cache and respect pacing.
- perf(delta): `importDelta` is heads-only by default (no bulk details). Details are handled by `refreshDetailsChunk` with strict chunk sizes (VOD 50, Series 30). Series delta uses a single list fetch instead of repeated paging calls.
- perf(details): In-flight de-dup in `XtreamObxRepository` prevents parallel duplicate detail fetches for the same ID (VOD/Series). Detail semaphores tuned to 4.
- perf(epg): EPG prefetch keeps a 4-wide concurrency cap; combined with client caching/rate-limit this avoids hammering panels while keeping tiles fresh.
- perf(seed): XtreamSeeder uses `seedListsQuick(limit=200)` for an ultra-fast first paint (heads only), then other flows enrich in background.
- perf/ui-prefetch: Visible VOD/Series tiles prefetch missing details (posters/plots/etc.) on scroll with small batches (VOD ≤12, Series ≤8) and in-flight dedupe; keeps on-screen tiles filled quickly without bursts.
- fix(images): TMDb image wrappers `AppPosterImage`/`AppHeroImage` now auto-fallback to smaller buckets (w342→w185→w154→original; w780→w500→w342→original) if a desired size 404s, avoiding broken/blank tiles.
- revert(vod/library): Remove provider fallback in curated VOD view. Keep genre layout stable; rely on minimal genre key ["other"] until index warms.
- perf(vod/detail): Reduce neighbor prefetch from 50 → 16 and keep concurrency modest; avoids long tails of `get_vod_info` when opening one detail.
- change(worker): Default `include_live=false` in `XtreamDeltaImportWorker` to avoid heavy Live bursts in one‑shot imports. Live remains handled by periodic jobs or explicit runs.
- fix(repo/index): `indexGenreKeys(kind)` now returns a minimal fallback ["other"] when the index is not yet built but content exists, so VOD shows at least "Unkategorisiert" immediately.
- fix(worker/build): Add missing import `androidx.work.workDataOf` in `XtreamDeltaImportWorker` to fix compile error (Unresolved reference 'workDataOf').
- feat(vod/normalizer+ui): Curated VOD rows without providers: order = Zuletzt gespielt → Neu – Aktuell → alphabetisch (Abenteuer, Action, …, Western) → 4K → Kollektionen → Unkategorisiert; Adults block appended at bottom when enabled. Implemented via expanded deriveGenreKey mapping and LibraryScreen curated rendering for VOD. Added German display labels for new keys.
- feat(ui/fonts): Embedded Google Fonts TTFs for category chips (Nosifer, Bangers, Orbitron, Cinzel, Rye, Mountains of Christmas, Parisienne, Fredoka, Playfair Display, Merriweather, Teko, Advent Pro, Baloo2, M+ Rounded 1c, Yatra One, Russo One, Oswald, Inter, Staatliches) and wired per‑category FontFamily.
- feat(ui/library/vod): Add FOR ADULTS umbrella section (visible only when Adults setting is ON). Expanding it shows each adult subcategory (e.g., MILF, AMATEUR, FULL HD, …) as its own row. Adult items now bucketize to "adult_*" keys.
- feat(normalizer/vod): Preserve FOR ADULTS subcategories as distinct provider buckets (e.g., "FOR ADULTS ➾ MILF" -> bucket "adult_milf"). Added dynamic display labels ("For Adults – MILF"). Adults toggle behavior unchanged.
- fix(ui/fonts): Prevent crash (IllegalStateException: Could not load font) by remapping families with corrupted TTFs to safe fallbacks in `CategoryFonts`, and embedding valid Google Fonts TTFs for: Advent Pro, Baloo 2, Cinzel, Fredoka, Inter, Merriweather, Orbitron, Oswald, Playfair Display, Teko. "Mountains Of Christmas" remains on system default until a valid TTF is added.
- fix(vod/library): Curated VOD rows (Genres & Themen, 4K, Kollektionen, Unkategorisiert) were missing because `genreKey` wasn’t set on heads/seed imports. Now we derive and persist `genreKey` during `seedListsQuick` and `importDelta` list-only updates, then rebuild indexes. Series seeding also persists `genreKey` for consistency.
 - change(vod/ui): Remove provider fallback on VOD. We always render the curated genre layout; while the genre index warms up, sections may be temporarily sparse instead of switching to provider rows.
- change(settings/import): "Import aktualisieren" triggers a background WorkManager one‑shot (`xtream_delta_import_once`) so the import continues even if the user leaves the screen. A quick discovery runs opportunistisch; a toast indicates background start.
- change(worker/delta): `XtreamDeltaImportWorker` now runs a full list delta (`importDelta(deleteOrphans=false)`) before the detail chunk. This updates provider/genre keys for all items and rebuilds indexes, so curated VOD rows populate reliably without waiting for per‑item details.
- change(genre-normalizer): Genre keys are now derived strictly from category names only (no scanning of titles or explicit genre strings). This avoids false matches like "show" in titles and speeds up index building. 4K/Collection are taken only when indicated by the category name.
- chore(scripts): Add quick_m3u_normalize.py and quick_m3u_vod_category_mapping.py to analyze M3U files and report CategoryNormalizer bucket mappings (no app/runtime impact).
- fix(ui/library/resume): Ensure VOD/Series library rows repopulate after returning from details/player. Added lifecycle ON_RESUME tick to re-run data loads and refresh expandable provider/genre/year sections. Avoids empty rows until tab switch.
- feat(import/xtream): Replace strict M3U bootstrap and PlaylistRepository with `XtreamSeeder`. Setup/Settings now derive Xtream credentials from `get.php` links or manual input and immediately seed live/vod/series heads. No playlist parsing remains.
- change(settings/import): "Import aktualisieren" reruns `XtreamSeeder` (optional forced discovery) and schedules detail refresh; prune/strict-M3U flows removed.
- change(startup/ui): Removed blocking bootstrap screen. Start/Landing trigger `XtreamSeeder.ensureSeeded` when ObjectBox is empty and credentials exist, keeping UI responsive while heads load.
- refactor(workers): `XtreamDeltaImportWorker` limits itself to detail chunks and EPG refresh; seeding now single-sourced through `XtreamSeeder`.

2025-09-20
- fix(xtream/delta, 400-cap): Delta import now iterates per-category for live/vod/series and aggregates results before pruning. This avoids panels that cap wildcard list calls at ~400 entries. No more unintended content shrink after M3U → Xtream delta; orphan removal only happens after full aggregation.
- fix(ui/library/rebuild): Library rows (provider/year/genre and top rows) now use route-scoped LazyListState via rememberRouteListState, preserving horizontal scroll and preventing row rebuilds on navigation (open/back, tab switch). Cards no longer visibly recompose on small interactions.
 - change(m3u→delta semantics): After strict M3U import, subsequent Xtream delta runs in enrichment mode (deleteOrphans=false) so M3U skeleton stays intact; delta only fills missing fields/posters. Periodic/on-demand delta paths may still prune when explicitly requested.
- feat(settings/diagnostics): Added OBX diagnostics in Settings: shows Live/VOD/Series counters and a one-shot “Leeren + Strict M3U Refresh” action to rebuild the M3U skeleton without pruning.
- ux(import/prune): Settings “Import aktualisieren” now asks whether to prune (delete orphans) or only enrich. Playlist setup (Xtream) gets a toggle “Fehlende Einträge löschen (Prune)” default OFF.
- fix(oom/m3u): HTTP path switched to streaming parser (`byteStream` → `M3UParser.parseStreaming`). Avoids reading the full playlist into a String (previous Reader overload caused ~130MB allocations and OOM on large M3U files).
 - policy(bootstrap): Bootstrap now does strict M3U only (skeleton + keys), with no automatic Xtream delta/seeding. Xtream runs lazily at runtime for visible content only. Periodic Xtream worker scheduling is disabled globally.

2025-09-21
- refactor(m3u/import): Always build the full OBX key-skeleton from M3U on refresh (even when Xtream creds exist). Xtream delta now runs after skeleton for lazy enrichment. Removed the pre-parse Xtream short-circuit and the per-batch skip that previously left only ~400 hot-seeded items visible.
- chore(tools): Add scripts/M3UParseProbe.kt — tiny Kotlin CLI to parse M3U files with the in-repo M3UParser (no Gradle build). Helps validate large playlists offline and inspect type/category distribution.
- fix(library/index): Rebuild ObjectBox aggregate provider/genre/year indexes after M3U/Xtream imports so Library tabs render bucket rows again. PlaylistRepository triggers a rebuild after strict M3U runs; XtreamObxRepository refreshes indexes for seed/delta/full imports.
- fix(m3u/import): Assign deterministic fallback IDs for live/vod/series entries when no Xtream stream_id is present. The strict M3U import no longer drops pure-M3U items, so large playlists surface fully in the UI and exports.
- feat(settings/import): Strict M3U refreshes now record current OBX totals and the Settings screen displays live counts, so the Seed/Delta diagnostics stay accurate even without Xtream credentials.

- perf(m3u/parser): Replaced the Reader-based import with a direct `ReadableByteChannel` pipeline (1 MiB direct buffers, byte-level line scanner, coroutine worker fan-out). Strings are now materialised right before batching and `parseExtInf` uses a hand-written attribute scanner instead of Regex. PlaylistRepository passes the raw `InputStream`, enabling multi-worker decoding and preparing for chunked parallel parsing.
- perf(m3u/parser): Added ASCII transliteration fast path (ä→a, ß→ss, …), string-only type heuristics and pooled attribute/media builders. Known M3U fields land in a reusable `AttrBag`, unknown ones stream into a tiny list, and each worker reuses a `MediaItemBuilder` before emitting an immutable `MediaItem` (~35 % CPU cut + lower GC churn in local benches).
- chore(m3u/testing): Added `app/src/test/java/com/chris/m3usuite/core/m3u/M3UParserPerfTest.kt` to benchmark large playlists via the new streaming path (disabled for release builds, CLI helper only).

2025-09-19
- fix(library/groups): Preserve expanded provider/year/genre sections with saveable state and cache invalidation, so ObjectBox refreshes no longer collapse rows or trigger visible rebuild flicker across Library tabs.
- fix(epg/live): Skip short EPG prefetch for non-live tiles so VOD/Serie/Resume rows stop spamming `get_short_epg` and reuse the live cache correctly.
- fix(xtream/repo): Resolve compile error by replacing incorrect references to normalizeProvider with CategoryNormalizer-backed bucketProvider per kind (live/vod/series) in XtreamObxRepository.
- feat(images/coil): Enforce fixed TMDb sizes for consistent caching and lower IO. Added `AppPosterImage` (w342) for tiles/posters and `AppHeroImage` (w780) for hero/backdrops; wired into Home rows, MediaCard, Start and VOD/Series detail headers. Non‑TMDb URLs remain unchanged. Global ImageLoader/caches unchanged.
- perf(bootstrap/m3u): Faster strict M3U import. Reuse ObjectBox queries with setParameter (avoid per-item query rebuild), throttle byte-progress updates (~100ms/256KiB) to reduce state churn, and increase M3U parser buffer (64KiB → 1MiB). Large lists import noticeably faster on low-end devices.
- perf(bootstrap/m3u): Dynamic OBX put chunk size based on device RAM (≈2GB → 2000, ≥2GB → 4000, ≥4GB → 6000, ≥6GB → 8000). Reduces transaction overhead on higher-memory devices without risking OOM on low-end.
- perf(bootstrap/m3u): Parser batch size now RAM‑aware as well (≈<2GB → 2000, ≥2GB → 3000, ≥4GB → 4000, ≥6GB → 6000) to balance memory footprint and throughput.
- feat(xtream/details): On-demand detail open prefetches a batch of neighbors (≈50) by provider group (fallback: newest), with bounded concurrency, to improve perceived latency for subsequent tiles. VOD detail needs expanded to include missing rating/genre/duration. Series detail need check scans all episodes for small series (≤80) to avoid false negatives.
- chore(xtream/gate): All on-demand Xtream detail calls (single + batch, VOD/Series) respect the global M3U_WORKERS_ENABLED switch and early-exit when disabled.
- feat(bootstrap): Add OBX content pre-check to skip strict M3U parsing on subsequent app starts when the ObjectBox store already has content (e.g., after restore). Marks bootstrap as done and resumes background work immediately.
- fix(bootstrap): On failure, show precise debug output in the bootstrap screen (step and root cause chain) and log full stacktrace to Logcat. Ensures no hang or silent crash.
- feat(settings/adults): Global toggle to show/hide category "For Adults" across the app. Applied to Start (rows and search), Library (groups, search, recents/new), and repository flows. Defaults OFF. When disabled, items in the "For Adults" category are filtered out.
- feat(home/start): Serien/Filme rows now merge "Zuletzt gesehen" on the left and "Neu" on the right. On first app start (no resume marks), rows show only newly added items. "NEU" badge marks only the items coming from the newest set.
- fix(home/search): StartScreen search rows now use MediaQueryRepository paging, so Kid/Guest allowances and the Adults toggle are respected.
- feat(theme): Force dark palette globally in Compose (AppTheme always uses DarkFancy; dynamic color disabled; DayNight still at XML but Compose UI renders dark). Ensures identical look on phones and Fire TV regardless of system theme.
- perf(playback/series): SeriesDetailScreen nutzt jetzt direkte Episode-URLs via XtreamUrlFactory/OBX (Episode.buildPlayUrl). Kein on‑the‑fly XtreamClient‑Init, keine Redirect‑Probes.
- perf(playback/live): LiveDetailScreen baut Play‑URLs direkt aus XtreamUrlFactory/OBX (toMediaItem). Kein Client‑Init beim Öffnen.
- perf(redirects): Standardweg entfernt separate HEAD/Range Redirect‑Auflösung; OkHttp/Exo folgen Redirects. `resolveFinalUrl` bleibt optional (Debug).
- perf/home): StartScreen reduziert Snapshots von 2000 → 600 Items pro Typ; Paging‑Rows bleiben.
- perf/import/delta): Xtream `importDelta()` chunked (5k‑Seiten) statt `Int.MAX_VALUE`; Orphans werden seitenweise entfernt (keine `*.all()` Vollscans).
- perf/epg): EpgRepository cached XtreamClient (AtomicReference) statt Neuaufbau je Anfrage.
- perf/telegram): TelegramTdlibDataSource Poll‑Intervalle erhöht (100/120ms → 300/350ms); empfohlen: Event‑Wakeup über `UpdateFile`.
- refactor(vod/detail): Redirect‑Auflösung im VOD‑Detail entfernt (reduzierter Netz‑Roundtrip). Resume‑Carousel ebenfalls ohne Redirect‑Probe.
- feat(settings/m3u): Global toggle to enable/disable M3U/Xtream workers and API. When off, workers skip all network calls and any enqueued Xtream work is cancelled (periodic and one-shots). App start auto-discovery/seed is gated, and related Settings actions (Portal check/Capabilities/EPG test/Import aktualisieren) are disabled. ObjectBox browsing remains fully functional.
- feat(normalizer): Deterministic bucket normalizer per kind (live/vod/series) to cap provider/group categories to ≤10 buckets. New API `CategoryNormalizer.normalizeBucket(kind, groupTitle, tvgName, url)` maps KönigTV groups into stable buckets (e.g., live: sports/news/documentary/kids/music/international/entertainment/screensaver/movies; vod: netflix/amazon_prime/disney_plus/apple_tv_plus/sky_warner/anime/new/kids/german/other; series: netflix_series/amazon_apple_series/disney_plus_series/sky_warner_series/anime_series/kids_series/german_series/other). Integrated in M3U import and OBX key backfill.
- feat(indexes): Aggregated indexes persisted during M3U import: `ObxIndexProvider/Year/Genre/Lang/Quality`. Library now reads provider/genre/year groups from these tables (no full DB scans), and Live grouping switches to provider/genre buckets.
- feat(bootstrap): Strict M3U bootstrap importer with blocking black screen and real-time progress. While bootstrap runs, UI and workers are gated. On completion, counts are stored and normal navigation resumes.

2025-09-18
- compat(minSdk): Lower minSdk from 24 → 21 to support older Fire TV devices (e.g., Fire TV Stick 2nd gen on Fire OS 5/Android 5.1). Packaging keeps both ABIs (`arm64-v8a`, `armeabi-v7a`) so 32‑bit sticks can install.

2025-09-18
- feat(share/xtream): Detailscreens für Live/VOD/Serien bieten jetzt „Link teilen“ (nur Xtream). Teilt den direkten Xtream‑Play‑Link per System‑Share (z. B. an VLC/MX Player oder auf Geräte im selben WLAN). Kein Proxy, keine Header – nur nackte URL.
- feat(library): VOD/Serien zeigen nun Provider‑Gruppen (normalisierte Anbieterlabels wie „Apple TV+“, „Netflix“, „Disney+“, „Amazon Prime“, …). Reihenfolge: 1) Zuletzt gesehen, 2) Neu, 3) 2025–2024 (neu→alt), 4) Anbieter‑Rows. Live behält Anbieter‑Gruppierung bei.
- fix(normalizer): Entferne Sprach/Region‑Präfixe robuster (z. B. „DE=>“, "DE:", "[DE] ") bei Provider‑Erkennung. Kategorie‑Strings mit gemischten Marken („Amazon & Apple …“) bevorzugen Titel‑Heuristik → stabilere Providerkeys.
- feat(ui/state): Persist and restore scroll state across screens and rows. All major LazyColumn/LazyRow/LazyVerticalGrid instances now use route-scoped keys so navigation returns to the previous position (Start, Library groups/rows, Details, Settings, Live/VOD/Series).
- feat(tv/live): DPAD Select toggles a persistent quick‑actions popup (PiP, Subtitle/Audio, Format). While open, DPAD_DOWN focuses the first button; LEFT/RIGHT navigate; Select activates; Back saves CC settings (if open) and closes the popup.
- perf(compose/lists): Add stable keys to LazyRow/LazyColumn/LazyVerticalGrid across player sheets, Start providers, detail galleries, settings pickers to reduce recompositions and avoid flicker/loading glitches.
- perf(images/ui): Disable image crossfade in large list rows; keep crossfade enabled on detail/hero screens and small avatar/icon uses to balance smoothness and performance.
- perf(images/coil): Global ImageLoader tuned — enable hardware bitmaps, set ~25% memory cache and 512 MiB disk cache; AppAsyncImage now supplies pixel size (w×h) to ImageRequest for optimal downsampling/decoding.
- feat(http/cache): Enable 50 MiB OkHttp disk HTTP cache in HttpClientFactory (under app cache dir). Respects server Cache-Control to reduce redundant network fetches for M3U/Xtream/XMLTV requests.
- fix(settings/tg): Prevent Compose Start/End imbalance crash when opening the Telegram chat picker (Film Sync) by removing early returns in `ModalBottomSheet` content.
- feat(settings/tg): Disable “Film Sync”/“Serien Sync” buttons until Telegram is enabled and authenticated; chat picker now handles unauthenticated state inline without composition imbalance.
- feat(settings/tg): Add “In Telegram öffnen” action to open the QR/login link directly in the Telegram app on the same device (no second device needed to scan).
- chore(tdlib): Set TdlibParameters.applicationVersion from BuildConfig.VERSION_NAME for accurate version reporting.
- fix(tg/errors): Surface `TdApi.Error` results from TDLib to the UI via service broadcasts. Prevents silent stalls in WAIT_FOR_NUMBER by showing concrete error messages (e.g., invalid phone format or API key issues).
- feat(tg/phone): Sanitize phone input (strip spaces/dashes/parentheses; convert leading `00` to `+`) before calling `SetAuthenticationPhoneNumber`.
- fix(tg/result-forward): Forward function results (incl. `TdApi.Error`) from `sendSetPhoneNumber`/`sendCheckCode`/`sendCheckPassword` to the global update listener. Ensures the service can report TDLib errors to the UI.
- fix(tg/login): Eliminate Telegram login stall on “Warte auf Antwort…” by queueing IPC commands in `TelegramServiceClient` until the service is bound. Ensures `CMD_START` registers the client before auth commands (phone/code/password) and guarantees auth state broadcasts reach the UI.
- fix(telegram/tdlib): Auto-load `tdjni` in `Client.java`, send `SetTdlibParameters` and the correct database encryption key from Android Keystore, and react to `AuthorizationStateWaitEncryptionKey` in the service. Unblocks Telegram login flow (QR/Phone).
- fix(tdlib/build): Generate `TdApi.java` via `td_generate_java_api` so Java bindings exist even when not stored in the upstream repository; fix unclosed "fi" causing "unexpected end of file" in v7a block.
- chore(wsl/tools): Make `scripts/setup-wsl-build-tools.sh` idempotent (skip CMake download if the expected version is already installed; add `--force` to override).
- chore(tdlib): Add `scripts/tdlib-rebuild-latest.sh` for a one‑shot rebuild that cleans old artifacts, sets envs, auto‑selects the latest upstream TDLib tag (currently v1.8.0), builds both ABIs, syncs Java bindings, and verifies outputs.
- chore(tdlib/build): Enhance `scripts/tdlib-build-arm64.sh` to support `--only-arm64`, `--only-v7a`, and `--ref <tag|commit>`; make stripping robust across host OSes; keep Java bindings in sync with the built native. Optional v7a output now built via the same CMake path as arm64.
- docs(tdlib): Update AGENTS.md to clarify arm64 is primary and v7a builds are optional via the build script; add CLI usage hints.
- build(compose): Enable Compose Live Literals for debug KotlinCompile tasks via compiler plugin arg. Facilitates project‑wide Live Edit of literals in Android Studio.
- feat(ui/fish): Add `neutralizeUnderlay` option in FishBackground to draw a flat background under the fish and avoid gradient bleed through transparent pixels. Enabled on major screens.
- feat(tv/player): D‑Pad/Media keys mapped in internal player. DPAD_LEFT/RIGHT and MEDIA_FF/REW now seek ±10s; PLAY/PAUSE and DPAD_CENTER toggle playback. Controls overlay becomes focusable and requests focus, so slider and buttons are reachable via remote.
  - feat(player/live overlays): On playback start, a top‑left title banner shows for 4s (title/episode/channel). For Live TV, EPG (Now/Next) appears for 3s with light opacity.
  - feat(player/live navigation): Live TV channel switching via DPAD/swipe — LEFT/RIGHT switch channel; DOWN re‑shows EPG; UP opens a selectable list.
    - Context aware: Outside Live Library, navigation uses favorite channels. From the Live Library page, navigation follows the library list; UP opens a global sender list with quick category switching.
  - fix(library/providers): VOD/Series grouping now uses normalized provider keys consistently (ignores country-only categories like "DE"). Provider labels derive from canonical slugs (e.g., "Apple TV+", "Netflix"). Backfill worker upgraded to correct bad provider keys in existing OBX rows.
 - fix(tv/focus): Player overlay gains TV focus handling (default focus on center control; slider focusable). Improves accessibility of seek bar with remotes.
  - feat(home/start): Start screen’s three sections (Serien/Filme/LiveTV) now fill the full space between header and bottom bar. Section titles are centered above each card, 20% larger, in white, sitting directly on the card’s top edge to minimize vertical gaps.
- fix(tv/settings): Text fields in Settings are read‑only on TV and open explicit edit dialogs instead of popping the on‑screen keyboard on focus. Prevents getting “stuck” when scrolling.
- perf(settings/input): Debounce writes for M3U/EPG/UA/Referer and Xtream fields (~500–800ms). Avoids heavy discovery/import work on every keystroke ("hungrig" Eingabefeld/Lag).
- feat(tv/theme): Disable dynamic color on TV to keep colors consistent with the app’s defined palette across devices.
- feat(tv/ui): Global button focus mask + bounce (focusScaleOnTv) and dark focus overlay. Added wrapper API: TvButton/TvTextButton/TvOutlinedButton/TvIconButton and migrated core screens (Setup/Settings/Details/Backup/Player overlay). New buttons should use Tv* wrappers.
- feat(gate): ProfileGate focuses “Ich bin Erwachsener” by default so remote OK works immediately; wording fixed.
- fix(setup/crash): Implement `sanitizeHost(...)` return in `PlaylistSetupScreen`; removes TODO that could crash on valid input.

2025-09-17
- chore(ui/rows): Tidy `ui/components/rows/HomeRows.kt` – remove duplicate auto-center effect, extract reusable TitleBadge, introduce constants for scales/durations, and trim unused imports. Improves readability/consistency without changing behavior.
  - fix(build): Correct labeled return in `ReorderableLiveRow` (`return@itemsIndexed`).
  - fix(ui/scale): Restore multi-tile visibility per row: remove row-level 1.1x scaling, disable per-tile scale where width-scaling is applied, set neighbor scale to 1.0, and revert tile height to the prior base (no extra 1.2x). Only the focused tile grows by +30%.
  - fix(ui/posters): Use ContentScale.Fit for poster images (MediaCard/VOD/Series tiles) to keep posters 100% visible without cropping; reduce base tile height ~12% to make focus growth stand out.
- fix(tdlib/java): Add compatibility shim TdApi.AuthorizationStateWaitEncryptionKey to match native libtdjni expectations. Fixes crash in :tdlib process and unblocks Telegram login dialog stuck on “Warte auf Status…”.
- chore(build/tdlib): Pin TDLib build to a stable upstream tag by default and make the build script auto-fallback to the latest v* tag if the requested ref is missing. Always sync TdApi.java/Client.java from the same upstream checkout to keep Java/JNI aligned.

2025-09-16
  - fix(player/vod): Increase DefaultHttpDataSource timeouts for VOD/movie URLs (20s connect, 30s read), add keep-alive and identity encoding to avoid initial GET timeouts on panels like KönigTV; Live/Series unaffected.
  - fix(xtream/vod-ext): PlayUrlHelper now prefers ObjectBox VOD.containerExt (then detail API) when building VOD URLs to avoid hardcoding .mp4. Ensures MKV streams are requested as .mkv and sets the matching MIME (video/x-matroska).
  - fix(xtream/vod-url): Even when a direct `item.url` is present for VOD, adjust Xtream movie URLs to the known container extension (from OBX/detail) so `.mp4` is not forced if the panel uses `.mkv`.
 - feat(library/grouping): Library VOD and Series pages group by normalized providers only (no country/genre as main groups). Live grouping unchanged.
  - feat(library/layout): VOD/Series pages now show top rows: "Zuletzt gesehen" (resume items with progress) and "Neu" (recently imported), followed by normalized provider rows.
- fix(xtream/mime): Capture `container_extension` from Xtream lists/details, backfill ObjectBox, and have `PlayUrlHelper` fetch/cache missing values so playback requests use the correct MKV/MP4 container instead of blindly forcing `.mp4`.
- fix(xtream/bootstrap): Run Xtream seeding/refresh only once per credential change and skip quick seeding when ObjectBox already has data, avoiding the 60+ redundant player_api calls observed on app start.
- chore(xtream/details): Detail worker now refreshes at most 40 VOD / 20 series per run, reuses a single client, waits ~10 min after the delta pass, and requires battery-not-low to keep CPU/RAM/network impact minimal.
- fix(profiles/kid): Start screen lists now honour kid/guest whitelists so restricted profiles only see allowed content.
- feat(ui/detail): Surface provider/category/runtime/release metadata plus IMDB/TMDB links on VOD & Series details, including richer chips and per-episode runtime info.
- fix(playback/internal): Provide explicit mime hints for Xtream streams so Media3 no longer hits `ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED`.
- fix(ui/library): Wire tile play buttons to direct playback for Live/VOD rows instead of routing to detail screens.
- fix(xtream/playback): Reuse discovered base paths + live output prefs when building OBX/PlayUrlHelper streams so live/VOD/trailer playback succeeds again.
- fix(playback/url): Use `Dispatchers.IO` when resolving HEAD/GET redirects so `PlayUrlHelper` compiles and keeps network work off the main dispatcher.
- fix(live/detail): Guard profile lookups when no active profile is loaded so Live detail/player no longer crashes with `Illegal ID value: -1`.
- fix(ui/live-picker): Start screen live picker search now queries ObjectBox live rows (not VOD), matching Library results when adding favorites.
- fix(player/internal): Toast now shows the actual Media3 error code name (no stray `$`) so debugging playback failures is readable.
- fix(xtream/playback): `XtreamUrlFactory` loads the cached capability alias/basePath before building play URLs, keeping VOD streams on the resolved `/vod|movie|…/` path so the internal player can start.
- fix(xtream/seed): List slicing now falls back to requests without `category_id` when wildcard and `0` responses are empty; VOD quick seed works on panels that require the default endpoint.
- fix(xtream/vod): Accept `stream_id` as VOD identifier so portals like KönigTV populate ObjectBox instead of reporting `vod=0` in delta imports.
- fix(series/playback): Persist Xtream episode IDs and use them when building play URLs; KönigTV episodes no longer fail with HTTP 401.
- fix(trailer): Normalize Xtream trailer values (YouTube IDs → full URLs) so trailer playback no longer crashes with `MalformedURLException`.
- fix(playback): Align `PlayUrlHelper` with `XtreamClient.initialize(username, password)`; remove old `user/pass` args.
- fix(ui/insets): Replace deprecated padding extensions with density-based insets in `HomeChromeScaffold`.
- fix(ui/composable-scope): Avoid calling composables inside `remember {}` in `SettingsScreen`; compute `OutlinedTextFieldDefaults.colors(...)` directly in composition.
- fix(ui/state): Move `showLivePicker` declaration before first use and use `rememberUpdatedState` for scope in `StartScreen`.
- fix(ui/series): Replace reserved `_` variable from `animateFloatAsState` in `SeriesDetailScreen`.
- fix(paging/crash): Guard `LiveRowPaged` EPG prefetch against empty `LazyPagingItems`; check bounds before `peek()` and `distinctUntilChanged()` the stream IDs.
- chore(telemetry): ANR watchdog now has 10s warmup + debounce and logs `ANR.Warning` as event (not error) to avoid noisy startup false positives.
  - fix(telemetry): ANR watchdog false positives caused by tick inc/dec netting to zero. Reworked to a main-thread heartbeat (lastBeatMs) + 60s rate limit.
- refactor(ui): Deduplicate KidSelectSheet; Live/Series/Vod detail screens now import `ui.components.sheets.KidSelectSheet` instead of local duplicates.
- fix(xtream/port): Respect explicit port from Base URL/Settings and skip the port resolver when provided. Propagated `portOverride` through `XtreamClient.initialize(...)` and updated all callers to pass the stored port.
- fix(xtream/category): Use `category_id=0` consistently for list endpoints and discovery probes instead of `*`.
- feat(http/timeouts): Increase OkHttp connect/read/write timeouts to 120s to better handle slow portals and large responses.
- feat(http/retry): Add interceptor retrying HTTP 5xx with 1.5x backoff (up to 2 retries) across all HTTP calls via `HttpClientFactory`.
- refactor(http/usage): Replace ad-hoc `OkHttpClient.Builder().build()` instantiations in UI/screens and repos with `HttpClientFactory.create(...)` so timeouts/cookies/retry policy apply uniformly.

2025-09-15
- fix(xtream/ports): Prefer :8080 and remove :2095 from HTTP candidates. Avoids Cloudflare 521 traps on legacy ports.
- fix(xtream/ping): Add explicit `action` and `category_id=*` to discovery pings. Port resolver now probes `get_live_streams|get_series|get_vod_streams` with wildcard to satisfy WAF rules and ensure 2xx+JSON.
- feat(xtream/onboarding): After successful discovery, immediately kick off Discovery → Client → Fetch. `seedListsQuick(forceRefreshDiscovery=true)` is triggered on first run, firing the six reference requests: `get_live_categories`, `get_live_streams&category_id=*`, `get_<vodKind>_categories`, `get_<vodKind>_streams&category_id=*`, `get_series_categories`, `get_series&category_id=*`.
- feat(telemetry): Log all Xtream `player_api.php?action=...` URLs at info level. Helps verify that UI/worker actually start list fetches (visible in global traffic logs).
- feat(obx/compose): Add ObjectBox→Compose bridge using `query().subscribe()` wrapped in `callbackFlow` for Live/VOD/Series. Library and Start screens now reactively reload when OBX changes, removing reliance on manual refresh/poll-only flows.
- fix(xtream/wildcard): Ensure `category_id=*` is sent when no category is provided (UI passes `null`, not empty). Prevents "empty panels" on providers that require an explicit wildcard.
- fix(xtream/alias): Respect discovered VOD alias from `ResolvedAliases.vodKind` for both categories and streams (fallback order retained). Avoids hardcoded `vod` calls on `movie`/`movies` panels.
- fix(xtream/ports): Invalidate cached port if ping succeeds but no core action responds during discovery. Clears `EndpointPortStore` for the endpoint so subsequent runs can re-resolve a working port.
- fix(xtream/discovery): Make port resolver strict. CapabilityDiscoverer.tryPing now only treats 2xx responses with parseable JSON as a valid hit. Cloudflare/WAF error pages (e.g., 521) are rejected so discovery continues to alternate ports (e.g., 8080) and selects a working base. Prevents false positives on port 80 and missing content.
  - behavior(cache): Cached ports are revalidated once; failing cached entries are cleared and a fresh candidate run is executed. Avoids being stuck on a previously cached wrong port.
- fix(xtream/client): Align player_api.php query order with common Xtream clients: `action` first, then optional params (e.g., `category_id=*`), then `username` and `password`. Some panels/WAF rules are sensitive to ordering; this change mirrors known-good requests.
- fix(http/headers): Reduce headers on player_api.php to minimum. Stop auto-injecting Referer/Origin/Accept-Language; only user-specified headers (e.g., User-Agent, optional Referer) are sent. Avoids tripping WAF rules that reject unexpected Origin/Referer combinations.
- feat(xtream): Add force-refresh discovery option and use quick list seeding. On app start and in the delta-import worker, fetch a fast first page per kind (Live/VOD/Series) to populate OBX immediately; then run full delta import with details and orphan cleanup.
- fix(settings/import): Re-enable inline delta-import path by removing an early return after scheduling the one-time worker. Users now get immediate import attempts (with existing port fallback UI) in addition to the background job.
- feat(ui/start): Immediate visibility seeding — when OBX is empty but Xtream is configured, seed first pages of Live/VOD/Series and reload Start to show tiles instantly; full delta import continues in background.
- fix(ui/start): German TV filter no longer hides all tiles when category/group name is absent; fall back to unfiltered Live list if the filtered set is too small.
- feat(ui/library): Restore old grouped headers and multi-section layout (Live: Provider/Genre; Filme: Provider/Jahr/Genre; Serien: Provider/Jahr/Genre) with always-visible header and bottom panel. Bottom panel now switches pages (live/vod/series) within Library while keeping chrome visible.
- feat(ui/labels): Friendly provider labels are now derived dynamically from API category/name data and cached (`ProviderLabelStore`). No hardcoded alias list required; labels evolve with observed data.
- feat(ui/auto-refresh): Start and Library auto-reload rows when `xtream_delta_import_once` succeeds (WorkManager observer), so content enriches without manual refresh.
- fix(xtream): Ensure list endpoints include category filter for full results. When no specific category is requested, client now sends `&category_id=*` for `get_live_streams`, `get_<vodKind>_streams`, and `get_series` to accommodate panels that return empty lists without an explicit filter.
  - safety(import): Avoid deleting all OBX rows when a provider returns an empty list (network hiccup/CF). Orphan cleanup now only runs if new data had at least one item.
- feat(settings): Add button to export/share HTTP traffic logs. Zips `files/http-logs` into `cache/exports/http-logs-<timestamp>.zip` and opens system share sheet via FileProvider.
- fix(settings): Correct WorkManager unique name check for on-demand Xtream delta import. Settings now detects a running job ("xtream_delta_import_once") and avoids misleading "Import gestartet" when one is already queued/running.
- fix(epg): Remove lingering Room fallback in EpgRepository; reuse stale OBX `ObxEpgNowNext` when network+XMLTV are empty.
- feat(m3u): Support `content://` and `file://` sources in PlaylistRepository (Reader-based streaming parse + optional url-tvg extraction). HTTP path unchanged; Xtream auto-detect still applies.
- fix(auth): Ensure Adult profile exists in PIN flow; auto-create Adult profile on first PIN set/entry and select it.
- feat(xtream): Always prefer Xtream for M3U links without explicit port. Integrate `CapabilityDiscoverer` (with `EndpointPortStore`) into M3U import and app startup to resolve ports quickly; set `XT_PORT_VERIFIED=true` only after successful discovery. If discovery/import fail, fall back to M3U parsing. Fixed port resolver to avoid forcing std (80/443) fallback when probing fails.
 - fix(epg/compile): Resolve unresolved refs in `EpgRepository` (`get`, `async`, `withPermit`). Switched XMLTV fallback to `XmlTv.currentNext(...)` and added bounded concurrent prefetch with `Semaphore` + `awaitAll`.
- fix(xtream/categories): Ensure `upsertCategories` receives `Map<String, String>` by coercing nullable `category_name` to empty.
- fix(telegram/cache): Correct `TelegramCacheCleanupWorker` OBX typing to `BoxStore` for helpers and use `closeThreadResources()` safely after batch updates.
- chore(work): Add `TelegramSyncWorker` (no-op backfill stub) to satisfy Settings actions and keep manual sync entry points. Real-time indexing remains event-driven in the TDLib service.
 - chore(xtream/logging): Add detailed diagnostics in `XtreamClient` for HTTP status, content-type, non-JSON bodies (length + head snippet), and category_id fallback. Redact credentials in logged URLs.
 - chore(warnings):
   - Replace deprecated `CategoryNormalizer` import with `core.util.CategoryNormalizer` across data/work.
   - Migrate `suspendCancellableCoroutine` resumes to stable API (no internal/legacy overloads) in `PlayerChooser`.
   - Use Media3 `@UnstableApi` annotation directly where needed; remove invalid `@OptIn` usages.
   - Switch remaining GlobalScope collectors in Telegram auth/service to structured `CoroutineScope`.

2025-09-11
- feat(obx-only): Remove Room from app flows. Telegram metadata, EPG cache, and all M3U/Xtream paths now use ObjectBox exclusively. Added neutral model classes (`model.MediaItem`, `model.Episode`) to replace Room entities in UI.
- refactor(telegram): TDLib service and workers write to `ObxTelegramMessage`; cache cleanup nulls OBX `localPath`. Room `telegram_messages` no longer used.
- refactor(ui): Strip Room from Start/Library/Details and rows. Live/VOD/Series details and tiles resolve via OBX; legacy Room fallbacks removed.
- refactor(epg): EpgRepository is OBX-only (reads/writes `ObxEpgNowNext`); XMLTV fallback retained.
- refactor(import): PlaylistRepository imports to OBX; Xtream fallback uses `XtreamObxRepository.importDelta`.
- chore(settings): Replaced `XtreamRepository` usage with OBX and inline M3U→Xtream config. Removed Room-based debug/actions.
- feat(m3u/obx): Migrate M3U import to ObjectBox and switch Xtream fallback to OBX repo. Parser batches now upsert minimal OBX entities (Live/VOD/Series) using detected Xtream IDs from M3U extra JSON; Xtream creds trigger OBX delta import.
- feat(prefs): Add SettingsStore.roomEnabled feature flag (default false) to gate Room. Use OBX-only paths by default for M3U/Xtream content.
- refactor(ui/start): Remove eager DbProvider open; translate legacy favorite IDs only when roomEnabled; otherwise rely on encoded OBX IDs.
- refactor(ui/details): LiveDetailScreen/VodDetailScreen no longer open Room eagerly. Room fallback only used when roomEnabled; OBX-encoded IDs use OBX exclusively.
- refactor(epg): EpgRepository avoids Room when roomEnabled=false. Uses OBX ObxEpgNowNext and ObxLive.epgChannelId for cache/subscribe; Room dual-persist kept behind flag.
- refactor(queries): MediaQueryRepository now uses OBX for kid gating (ObxKid* tables) and OBX search/lists; removes Room lookups for M3U/Xtream paths.
- refactor(ui/rows): HomeRows removes DbProvider usage; EPG overlay subscribes to OBX when Room is disabled; Series/VOD resume overlays use OBX where possible.
- chore(setup): PlaylistSetupScreen avoids Room sampling when roomEnabled=false during output checks.
- fix(build): Resolve compile errors after OBX/Xtream refactor
  - InternalPlayerScreen: add seriesId/season/episodeNum params; update MainActivity route usage.
  - ObxPagingSources: avoid calling suspend `categories()` from non-suspend init; fetch per-load inside PagingSources.
  - XtreamObxRepository: import kotlinx-serialization JSON helpers; fix `importSeriesDetailOnce` early return; use `toList().sortedDescending()` for IntArray results; add Flow `first()` import.
  - PlaylistRepository: migrate to `XtreamDetect.detectCreds(...)` and streamline logging; keep Xtream auto-detect from first live URL.
  - PlaylistSetupScreen: remove invalid `key.scheme` argument; rely on legacy constructor.
  - LiveDetailScreen: add `DisposableEffect` import.
  - SettingsScreen/SeriesDetailScreen/VodDetailScreen: add JSON extension imports and minor log fix.
  - SchedulingGateway: pass `SettingsStore` to `XtreamObxRepository`.
  - ObxKeyBackfillWorker: correct `ObxStore` import.
  - ResumeCarousel: make `VodResume`/`SeriesResume` public to satisfy Kotlin visibility rules.
- fix(ui/library): LibraryScreen cleanup — remove duplicate state declarations (showGrantSheet/showRevokeSheet/selected), hoist `showFilters`/`showCategorySheet` before usage, unify `selectedCategoryId` naming, simplify filter sheet categories (use “Alle” + “Kategorien öffnen…”), remove undefined grid block (pagingItems/currentType), pass `snackbarHost` to scaffold, and eliminate duplicated VOD condition. Also dropped inner overshadowed `isKidProfile` re‑declaration.
- fix(ui/library): Remove stray closing brace in `LibraryScreen.kt` that prematurely ended the Composable scope and caused Kotlin parse errors around else-if chains and top-level declarations. Windows Android Studio build compiles past `LibraryScreen` again.
- fix(ui/library): Replace if/else-if chain with `when(selectedId)` and expand inline lambdas into multi-line blocks for VOD/Series/Live/All sections. This resolves KAPT stub parse errors (Unexpected tokens) on Windows by removing ambiguous one-line brace/paren combos.
- feat(obx-first/step1): Migrate content flows to ObjectBox end-to-end. Remove Room fallbacks in Start/Library and details. `MediaQueryRepository` now queries OBX (lists, search, paging) and only uses Room to evaluate kid permissions. OBX IDs are encoded into `MediaItem.id` as stable synthetic IDs: live=1e12+streamId, vod=2e12+vodId, series=3e12+seriesId. Detail screens resolve encoded IDs and build play URLs via `XtreamClient`.
  - Start: load Series/VOD/Live exclusively from OBX; favorites mapping supports legacy Room IDs by translating to OBX IDs.
  - Library: any remaining fallbacks now use OBX through the repository; category sheet remains OBX.
  - LiveDetail/VodDetail/SeriesDetail: support encoded IDs; Live/VOD play URLs come from `XtreamClient`; EPG subscribe remains on OBX.
  - Note: Resume and kid/profile tables remain Room for now; gating uses Room sets; full migration planned next.
- docs: Update AGENTS.md, ROADMAP.md, ARCHITECTURE_OVERVIEW.md to reflect OBX delta import + workers, distinct-key grouping, Start series on-demand OBX import, and global removal of legacy .old sources.
- feat(db/objectbox): Add normalized grouping keys to OBX entities: `providerKey` and `genreKey` on Live/VOD/Series (+ indices). Import fills keys using normalization/heuristics so all layers can query/group efficiently.
- feat(repo/queries): Add OBX paging by grouping keys: `liveByProviderKeyPaged`, `liveByGenreKeyPaged`, `vodByProviderKeyPaged`, `vodByGenreKeyPaged`, `seriesByProviderKeyPaged`, `seriesByGenreKeyPaged`.
- feat(import/obx): Add `XtreamObxRepository.importDelta(deleteOrphans=true)` for ID-based upserts (Live/VOD/Series) with orphan cleanup. VOD/Series details are fetched only for new/changed items; Live upserts from list only.
- feat(work): Add `XtreamDeltaImportWorker` (periodic, unmetered+charging) and SchedulingGateway hooks (`scheduleXtreamDeltaPeriodic`, `triggerXtreamRefreshNow`) to run delta imports under friendly constraints.
- feat(ui/start): StartScreen rows now lazily load Series and VOD via Paging3 over ObjectBox, preserving the horizontal row optics. Animated Material3 skeletons (fisch.png + shimmer/pulse) indicate loading for initial and appended pages.
- feat(ui/start/live): When no Live favorites are set and no search is active, Start shows a paged global Live row (ObjectBox + Paging3) with the same horizontal layout, pulsing shimmer placeholders, and EPG prefetch for visible items.
- feat(ui/library/live): When a Live category is selected in Library, render a paged horizontal Live row (ObjectBox + Paging3) with shimmer/pulse skeletons and EPG prefetch for visible tiles.
- feat(ui/library/series): When a Series category is selected in Library, render a paged horizontal Series row (ObjectBox + Paging3) with shimmer/pulse skeletons; preserves play-direct behavior (resume or first episode).
- ux(library/back): Back button now first resets the selected category (exits the category view) instead of immediately leaving the Library. A second back press returns to Start.
- feat(ui/library/live-grouped): Live grouped view (Provider/Genre) now uses lazy row activation: visible rows render paged content with shimmer placeholders immediately; additional rows activate and page in as they enter view. EPG prefetch remains per visible tile.
- feat(ui/library/vod-series-grouped): VOD/Series grouped views (Provider/Genre) now use OBX key-based paging per visible row with shimmering placeholders. Rows activate when visible; content pages in on horizontal scroll. (Year grouping remains non-keyed.)
- feat(repo/paging): Add `ObxLivePagingSource` and `ObxSeriesPagingSource` to complement VOD; ready for wiring into Live rows and tabs.
- feat(ui/library): Add ObjectBox-backed paging for VOD when a category is selected in Library. Keeps existing layout: vertical sections with a horizontally scrollable VOD row, now fed by Paging3 with Material3 shimmer placeholders using `res/drawable/fisch.png`.
- feat(repo/paging): Introduce `ObxVodPagingSource` (offset-based) built on `XtreamObxRepository` paged queries and OBX search; maps to existing `MediaItem` via `ObxAdapters.toMediaItem`.
- ux(skeleton): Enhance shimmer placeholders with a gentle pulse on the fish overlay for clearer loading activity.
- refactor(xtream): core/xtream set as single source of truth. Align `XtreamClient` package to `com.chris.m3usuite.core.xtream`; add `XtShortEPGProgramme` model.
- feat(epg): Rewire `EpgRepository` and `EpgRefreshWorker` to new `XtreamClient.initialize(...).fetchShortEpg(...)` with JSON parsing; keep XMLTV fallback.
- chore(work): Disable Xtream periodic/enrichment workers and related scheduling (no-op). Move to on-demand + lazy loading strategy.
- compat(api): Add deprecated secondary `XtreamConfig(host, port, user, pass, output[, scheme])` constructor to keep UI code compiling during transition.
- refactor(repo): Start migrating `XtreamRepository` to use new client (lists, details, play URLs). Xtream-first import with M3U fallback remains; further ObjectBox migration pending.
- feat(db/objectbox): Add ObjectBox (plugin + deps) and new entities (`ObxCategory`, `ObxLive`, `ObxVod`, `ObxSeries`, `ObxEpisode`, `ObxEpgNowNext`) + `ObxStore`. Introduce `XtreamObxRepository.importAllFull()` to ingest full content (VOD details; series seasons/episodes) into ObjectBox. UI will be wired later.
  - add(series): store imdbId/tmdbId; add live.tvArchive; add vod.trailer.
  - add(epg): persist Now/Next into ObjectBox from `EpgRepository` for fast offline startup.
  - add(queries): paged ObjectBox queries for categories, live, vod, series, and episodes.
  - add(categories): robust `category_id` assignment for live/vod/series in ObjectBox import (if provided by API).
  - add(epg/visible): `XtreamObxRepository.prefetchEpgForVisible(...)` writes short EPG directly to ObjectBox with limited concurrency.
- feat(ui/library): Switch LibraryScreen to ObjectBox-first: full `load()` path uses OBX lists and OBX search (nameLower + category match); Room only if OBX empty. Category sheet sourced from `ObxCategory` with per-category counts and search.
- feat(ui/start): StartScreen uses ObjectBox-first lists; Live favorites prefer OBX; LiveRow EPG prefetch enabled.
- feat(ui/series): SeriesDetailScreen consumes ObjectBox episodes (with playExt) when available; robust series episode play URLs; Room fallback kept.
- feat(ui/live): LiveDetailScreen uses ObjectBox query subscription for EPG (no polling). Initial Now/Next via repo; subsequent updates event-driven.

2025-09-10
- feat(tg/keys): Add optional API key overrides in Settings (API ID/HASH) that take precedence over BuildConfig. Repo rebinds when keys change.
- feat(tg/updates-first): TDLib service now listens to live updates (UpdateNewMessage/UpdateMessageContent/UpdateFile) and upserts minimal records into `telegram_messages`. File `localPath` is persisted on UpdateFile by `fileId`.
- feat(tg/qr-ux): QR login dialog shows “Warten auf anderes Gerät”, renders QR, adds “Link kopieren”, “Neu laden/Erneut versuchen”, and explicit cancel that clears inputs.
- ux(tg/settings): Show banner when Telegram is enabled but API keys are missing and disable Connect/QR actions until keys are provided.
- feat(tg/picker): Chat/Folder picker adds search box and folder toggle; remains no-op until authenticated.
- chore(db/dao): Add `updateLocalPathByFileId(fileId, path)` to `TelegramDao` for efficient localPath persistence.
- fix(build/kotlin): Address multiple Kotlin compile errors:
  - Refactor `PlaylistRepository.refreshFromM3U()` to avoid invalid `recoverCatching` chaining and suspend usage inside non-suspend lambdas; implement clean Xtream fallback without exceptions.
  - Fix `TelegramAuthRepository`: add `launch` import, correct QR links collector scope, and remove out-of-scope variable use.
  - Resolve duplicate `parseAddedAt` in `XtreamRepository` causing overload conflicts.
  - Change `TdLibReflection.extractQrLink` to block body to avoid non-local returns in expression body.
  - Add missing imports in `StartScreen` (RenderEffect/asComposeRenderEffect) and `SettingsScreen` (LocalLifecycleOwner).
  - Hoist `remember` state out of `LazyColumn` builders in `LibraryScreen` to avoid composable invocations outside @Composable.
 - fix(build/gradle): Resolve Kotlin DSL error by avoiding ambiguous `sourceSets.java.exclude(...)`. Now exclude reference sources via task-level filters: `tasks.withType<KotlinCompile>().configureEach { exclude("**/com/chris/m3usuite/reference/**") }` and the same for `JavaCompile`. Works reliably on AGP 8.5 + Kotlin 2.0.
- feat(ui/library): Dynamic grouping + filter controls in Library for VOD/Series; grouping by Provider (normalized), Year, and Genre (heuristic). Adds text filter; unmatched items remain under "Unbekannt" so nothing falls out.
- feat(ui/live): Live-TV grouping controls (Provider | Genre, no Year). Uses the same normalization to consolidate provider variants; adds text filter.
- feat(ui/categories): Provider/category normalization consolidates variants (e.g., "ALL | APPLETV+", "ALL | APPLE TV+", regional prefixes) into canonical labels (Apple TV+, Netflix, Disney+, Amazon Prime). Rows are no longer split; all items appear in the consolidated row.
- feat(import): Incremental upserts for M3U/Xtream (no table clear). Preserves addedAt to mark NEW items; adds only new/changed entries.
- feat(startup): App can start to Home without sources; no workers run until valid M3U/Xtream present.
- feat(import): OneTime start import on every app start when M3U present; overlay spinner (rotating fish) until top rows load.
- feat(fallbacks): Port/Output hardening with verified flags persisted; expanded, faster port probing; non-#EXTM3U URL attempts Xtream detect.
- feat(tg/login): Finalize login flow (phone→code→password + QR). Adds service error surfacing to Settings (snackbars) and ensures WAIT_PASSWORD is fully handled in dialog + service.
- feat(tg/push): Minimal, efficient FCM integration. `FirebasePushService` binds lightweight client, fetches token opportunistically, and forwards token/payload to `TelegramTdlibService`. Service lazily starts TDLib on first token/push using `BuildConfig` keys, then calls `RegisterDevice` / `ProcessPushNotification`. No foreground, no polling.
- fix(tdlib/java): Align `org.drinkless.tdlib.Log.setFilePath(String)` signature to return boolean to match JNI registration in packaged `libtdjni.so`. Fixes crash when tapping “Telegram verbinden” (native method registration failure: expected `Z`, Java had `void`).
 - chore(build/abi): Remove `armeabi-v7a` from app packaging; ship arm64‑only. Delete `libtd/src/main/jniLibs/armeabi-v7a/` and `scripts/tdlib-build-v7a.sh`. Exclude reference dump from sources to avoid interference.
 - docs(roadmap/agents/arch): Add Phase‑2 “Next‑gen TDLib” plan (QR‑Login, FCM push `registerDevice`/`processPushNotification`, dedicated `:tdlib` process/service, updates‑first indexing + targeted backfill, storage stats cleanup, single client + Kotlin facade, strict JNI/Java alignment, planned LTO/GC‑sections for tdjni). Sync AGENTS.md and ARCHITECTURE_OVERVIEW.md to arm64‑only packaging and upcoming improvements.
 - feat(tg/security): Keystore‑gestützter TDLib DB‑Schlüssel (32‑Byte) mit automatischer `checkDatabaseEncryptionKey`. Parameter‑Setup erweitert (use*Database, files/database dirs).
 - feat(tg/login): QR‑Login‑Pfad ergänzt (RequestQrCodeAuthentication) + UI‑Hinweise; Dialog deckt jetzt WAIT_OTHER_DEVICE ab.
 - feat(tg/push‑hooks): Reflection‑Hilfen für `registerDevice` (FCM) und `processPushNotification` vorbereitet (No‑op bis FCM angebunden ist).
 - feat(tg/service): Dedicated `TelegramTdlibService` in separate process (`:tdlib`) with Messenger IPC (start/auth commands, QR, logout, push handlers). Lightweight `TelegramServiceClient` for binding + commands. Manifest updated.
 - feat(tg/lifecycle): Settings binds TDLib service on start and unbinds on stop; explicitly informs TDLib via `SetInBackground(false/true)`.
 - feat(tg/foreground): Service enters foreground during interactive auth or active downloads (tracks `UpdateFile` → `isDownloadingActive`/`isDownloadingCompleted`) and stops when idle/authenticated.
 - feat(tg/network): Service observes connectivity and forwards `SetNetworkType(WiFi/Mobile/Other/None)` to TDLib.
 - build(deps): Add optional Firebase Messaging dependency (`com.google.firebase:firebase-messaging:24.0.0`). Added `FirebasePushService` (no-op unless google-services configured) to deliver tokens/push payloads to TDLib service.
 - feat(tg/service): Dedicated `TelegramTdlibService` in separate process (`:tdlib`) with Messenger IPC (auth commands + auth state events). Added lightweight `TelegramServiceClient` wrapper. Manifest updated.

2025-09-09
- feat(m3u/parser): Improve type detection for heterogeneous M3Us. Adds robust URL heuristics for `series`/`movie(s)`/`vod` across paths and queries, plus a compact Xtream path rule. Fixes VOD/Series not being recognized when attributes are missing.
- feat(m3u/parser): Derive language-based categories for VOD/Series when `group-title` is absent. Detects leading `DE:`, bracket tags like `[EN]`, common language names/codes in title/group, and weak hints in URL. Uses detected language as `categoryName` for grouping in Library collapse/expand tabs. Strips leading language prefix from title to keep names clean.
- chore(m3u/parser): Accept `movies` in provider pattern; keep fallback to `live` unchanged.
- fix(import/fallback): When Xtream API import yields only Live (VOD+Series empty), automatically fall back to M3U import to populate VOD/Series from provider links. Applied in Settings immediate import and periodic refresh worker.
- feat(setup): First‑run chooser in PlaylistSetupScreen. Users can start via M3U link or Xtream login; the other source is auto‑derived and saved (get.php/xmltv.php or Xtream from get.php).
- feat(xtream/ports): Automatic port fallback when import fails. Tries common ports (443, 80, 8080, 8000, 8081) with user Snackbars in Settings/Setup; persists first working port. Worker uses the same logic headlessly with logs.
 - feat(xtream/output): Output fallback check (live URL probe). If streams likely fail with current `output`, automatically probe `m3u8|ts|mp4`, show Snackbars, persist the first working one, and re‑import to update URLs.

2025-09-08
- fix(http): Switch to singleton OkHttpClient with merging in-memory CookieJar to persist Cloudflare/session cookies and reuse connections; interceptor reads non-blocking header snapshot.
- fix(work): Throttle Xtream enrichment (batch ~75 with small delays; retry/backoff on 429/513). Reduces WAF triggers.
- revert(headers): Default/fallback User-Agent restored to literal "IBOPlayer/1.4 (Android)" across runtime (SettingsStore, RequestHeadersProvider, UI fallbacks, previews). Removed reliance on secret-injected DEFAULT_UA for UA fallback.
- fix(headers): Use secret-injected default User-Agent via BuildConfig (`DEFAULT_UA`) sourced from env/CI. Removes any literal UA from code and binaries; Settings/UI fallbacks now reference the injected value.
- chore(ui): Hide User-Agent edit fields in Settings and Playlist setup by default. Toggle via `BuildConfig.SHOW_HEADER_UI` (Gradle prop `-PSHOW_HEADER_UI=true`).
- chore(build/size): Exclude `app/src/main/java/com/chris/m3usuite/reference/**` from compilation/packaging. Files remain in repo as reference but are no longer bundled.
- revert(http): Restore OkHttp default protocol negotiation (removes forced HTTP/1.1) and drop unconditional Referer/Origin on Xtream API calls to match prior baseline behavior.
- fix(build/size): Strip debug symbols from packaged TDLib JNI (arm64-v8a, armeabi-v7a). Reduces APK from ~650 MB to ~80 MB total for both ABIs. Added stripping to `scripts/tdlib-build-*.sh`.
- fix(m3u/import): Relax validation to accept BOM/whitespace before `#EXTM3U`. M3U import works again for playlists with leading BOM or comments.
- chore(naming): Replace all IBOPlayer references with "FishIT Player" across the app. Update default User-Agent to "FishIT Player/1.4 (Android)" in headers, image requests, and setup UI.
- fix(tdlib/java): Add missing `org.drinkless.tdlib.Log` class to `:libtd` so `libtdjni.so` JNI registration can find it. Fixes fatal error when clicking "Telegram verbinden" (Can't find class org/drinkless/tdlib/Log).
- chore(secrets): Add ignore for `/.tg.secrets.properties` and create local secrets file (untracked) for Telegram API keys (`TG_API_ID`/`TG_API_HASH`).
- fix(telegram/datasource): Add missing import for Flow `first()` in `TelegramRoutingDataSource` to resolve compile-time reference.
- fix(telegram/ui): Qualify `Accent` token in `TelegramChatPickerDialog` to avoid unresolved reference in nested composable scope.
- fix(telegram/cache): Import `ExistingWorkPolicy` in `TelegramCacheCleanupWorker`.

2025-09-05
- ci(actions): Add `.github/workflows/tdlib-verify.yml` to run `./gradlew verifyTdlib` on push/PR. Expects `TG_API_ID` and `TG_API_HASH` to be configured as GitHub Actions secrets.
- feat(telegram/ui): Harden UX preconditions. Chat picker now prompts to connect when not authenticated and offers a one-click path to the login dialog. Settings shows masked API key quick-check, live auth state, and adds an Abmelden/Reset action.
- feat(telegram/datasource): Clear user hint when falling back to local routing for tg:// without authentication.
- feat(telegram/sync): Soft dedupe by fileUniqueId; add exponential backoff on retries; improve progress reporting structure.
- chore(tdlib/verify): Add `scripts/verify-tdlib-readelf.sh` to assert no dynamic OpenSSL deps in packaged `libtdjni.so` for v7a/arm64.
- chore(gradle): Add `verifyTdlib` Gradle task to run the readelf verification script.
- test(android): Add instrumented smoke-test scaffold for tg:// fallback path with a notifier hook.
- fix(telegram/init): Prevent eager TDLib JNI load at app start by making `TdLibReflection.available()` use `Class.forName(..., initialize=false, ...)`. Also gate routing DataSource download triggers behind `tg_enabled` and check the flag before any TDLib presence checks in `TelegramTdlibDataSource`.
- fix(tdlib/v7a): Build and statically link BoringSSL into `libtdjni.so` for `armeabi-v7a` in `scripts/tdlib-build-v7a.sh`. Eliminates 32‑bit runtime dependency on external OpenSSL libs and fixes crashes on v7a devices.
 - docs(agents): Add explicit Sandbox/WSL execution rules for Codex (repo‑local toolchains `.wsl-*`, portable CMake/gperf setup, TDLib v7a static‑link flow, search/output discipline, documentation upkeep).
 - docs(agents): Add "Quick Verify (WSL)" snippet to validate local toolchain (env vars, cmake/gperf versions, NDK toolchain presence, Java 17).
 - docs(agents): Add one-liner to run setup scripts and verification in a single command under the new Quick Verify section.
 - feat(tdlib/arm64): Static BoringSSL linking for `arm64-v8a` via `scripts/tdlib-build-arm64.sh` (mirrors v7a flow). Produces self‑contained `libtdjni.so` in `libtd/src/main/jniLibs/arm64-v8a/`.
 - build(tdlib): Add fallback wrapper CMake for older TDLib tags without `example/android` (uses `example/java` + embedded `add_subdirectory(TD_DIR)`).

2025-09-04
- chore(tdlib/java): Bundle TDLib Java classes (`Client.java`, `TdApi.java`) in `:libtd` from TGX-Android/tdlib so reflection can find `org.drinkless.tdlib.*` at runtime. Added keep-rules for `org.drinkless.tdlib.**`.
- fix(tdlib/native): Ensure TDLib JNI is loaded by adding `System.loadLibrary("tdjni")` static initializer in `:libtd` `Client.java`. Prevents `UnsatisfiedLinkError` on first TDLib call; works with packaged `arm64-v8a` and `armeabi-v7a` `jniLibs`.
 - chore(build/telegram): Secure secrets sourcing for `TG_API_ID`/`TG_API_HASH`. Precedence: ENV → root `/.tg.secrets.properties` (untracked) → `-P` Gradle props → default. Avoids committing keys while enabling local test builds.

2025-09-04
- fix(player/routing): Default to internal when `playerMode=external` but no preferred external package is set; prevents unwanted Android app chooser and keeps playback in the internal player.

2025-09-04
- chore(tdlib/v7a): Add prebuilt `libtdjni.so` for `armeabi-v7a` (sourced from TGX-Android/tdlib) under `libtd/src/main/jniLibs/armeabi-v7a/`. Ensures v7a runtime parity alongside arm64.

2025-09-04
- feat(telegram/sync+ds): Upsert `telegram_messages` during sync; persist `localPath` from DataSource; add download trigger by fileId; gate TDLib streaming by `tg_enabled`+AUTH.
  - Worker: fixes pagination (offset=-1); records `fileUniqueId`, `supportsStreaming`, `date`, `thumbFileId`; imports thumbs/metadata (duration/container ext); uses chat photo as series poster.
  - Player: `TelegramTdlibDataSource` checks flag/auth and updates `telegram_messages.localPath` once path resolves.
  - DataSource: `TelegramRoutingDataSource` triggers `DownloadFile(fileId)` and persists `localPath` when known.
  - UI: Settings shows minimal sync progress via WorkManager polling.
  - Work: New `TelegramCacheCleanupWorker` trims TD cache by size (GB) daily.

2025-09-04
- fix(ui/library): Collect VOD/Series category collapse/expand state from SettingsStore in `LibraryScreen`; resolves unresolved references `vodCatCollapsedCsv`, `vodCatExpandedOrderCsv`, `seriesCatCollapsedCsv`, `seriesCatExpandedOrderCsv` during `:app:compileDebugKotlin`.
2025-09-04
- feat(telegram/login): Add reflection-based TDLib auth bridge + Settings login flow.
  - New: `telegram/TdLibReflection.kt` (no direct tdlib dependency; uses reflection to drive auth state + send phone/code).
  - New: `data/repo/TelegramAuthRepository.kt` managing client/auth state.
  - UI: Settings → “Telegram verbinden” button opens number/code dialog; “Status (Debug)” shows current auth state.
  - Build: `TG_API_ID` / `TG_API_HASH` BuildConfig fields (read from `gradle.properties`). TDLib libs still required at runtime.
2025-09-04
- feat(telegram/datasource): Add `TelegramRoutingDataSource` for Media3 with `tg://message?chatId=&messageId=` URIs. Internal player auto‑routes tg scheme to local cached file (Room `telegram_messages.localPath`) and falls back if missing. `VodDetailScreen` now emits tg URIs for TG items.
2025-09-04
- feat(telegram/streaming): Add `TelegramTdlibDataSource` (streaming) that uses TDLib (reflection) to download and serve Telegram message files progressively with seek support. Internal player uses this factory; falls back to routing/HTTP when unavailable.
2025-09-04
- feat(series/tg): Add per-episode TG mapping (Episode.tgChatId/tgMessageId/tgFileId + migration v8→v9). Series playback now prefers `tg://message` when episode carries TG refs; PlayerChooser forces internal for tg://. Updated StartScreen and ResumeCarousel to respect TG episodes.
2025-09-04
- feat(telegram/sync): Add `TelegramSyncWorker` with chat/folder picker UI in Settings. Film Sync/Serien Sync let users select chats as sources; worker fetches history via TDLib (reflection), maps media to VOD or Series (SxxExx heuristics), and upserts DB with TG fields.
2025-09-04
- chore(tdlib/packaging): Add `:libtd` module with `jniLibs` (libtdjni.so). App depends on `:libtd` to ensure runtime availability. BuildConfig `TG_API_ID`/`TG_API_HASH` read from `gradle.properties`.

2025-09-03
- feat(telegram/scaffold): Global feature flag in Settings; Room schema v8 adds `MediaItem.source` + TG refs and new `telegram_messages` table + DAO; Gradle packaging prepped for universal ABI (armeabi-v7a, arm64-v8a); ProGuard keep for TDLib. Added `TelegramRepository` to resolve local file paths when present and wired VOD detail playback to use it. Default OFF; no TDLib libs bundled yet.

2025-09-03
- feat(paging): Introduce Paging 3 for large lists. Room DAO now exposes `PagingSource` for type/category and FTS search; repository provides `Flow<PagingData<MediaItem>>`; `LibraryScreen` renders a paged grid with stable keys. Dependencies: `androidx.paging:paging-runtime-ktx`, `androidx.paging:paging-compose`.
- perf/metrics: Tag JankStats frames with `route` and `scroll` via `PerformanceMetricsState` in `LibraryScreen` and `StartScreen`. Log TTFV when first paged items appear in Library.
- ui/perf: Shimmer placeholders in paged grids during refresh/append (Library grid, Start Live picker) for smoother TTFV.
- ui/perf: ShimmerBox now overlays a gently rotating fish icon while loading per item.
- perf/metrics: Per-route JankStats counters via `JankReporter`; logs windowed frames/janks/rate every 5s.
- fix(build): Mark `loadPrevByType` as `suspend` in `PlaylistRepository` to call suspend DAO (`urlsWithExtraByType`) correctly; resolves "Suspension functions can only be called within coroutine body" during `:app:compileDebugKotlin`.

2025-09-03
- feat(search): Add Room FTS4 index (`mediaitem_fts`) over `name` and `sortTitle` with `unicode61` tokenizer (`remove_diacritics=2`). DAO query `globalSearchFts(...)` and repository switch to FTS with prefix `*` and `AND` across tokens. Migration v6→v7 creates table, sync triggers, and backfills.
- perf/ui: Debounce search input in `LibraryScreen` (300 ms) with `snapshotFlow + debounce + collectLatest` for smooth type‑ahead.

 - perf/lifecycle: Migrate key UI state reads to `collectAsStateWithLifecycle` in `InternalPlayerScreen`, `SettingsScreen`, `CollapsibleHeader`, `FishBackground`, and live row EPG observation; reduces leaks and off-screen work.
  - perf/metrics: Wire `JankStats.createAndTrack(window)` in `MainActivity` for lightweight jank logging; basis for start/jank dashboards.
  - perf/lifecycle: Use `repeatOnLifecycle(STARTED)` for side-effectful Flow collections (nav route changes, fish spin triggers, LiveDetail EPG DB observer, global headers snapshot). Ensures collectors stop when app is backgrounded.

2025-09-03
- docs(roadmap): Added "m3uSuite · Roadmap vNext (Q4 2025 – Q2 2026)" section with milestones M0–M3, fixes, performance levers, new implementations, build strategy, risks, metrics, and sequence.

2025-09-03
- perf(http): Remove DataStore access from OkHttp interceptor; headers now read from an in-memory snapshot (`RequestHeadersProvider` with `StateFlow` + atomic snapshot). `HttpClientFactory` seeds snapshot once and reuses it.
- perf(db/import): Add DAO projections for `url, extraJson` and switch M3U import to batch-read minimal columns in 10k chunks; minimal `addedAt` extraction via regex.
- reliability(epg): Remove UI-owned 5m loop in `MainActivity`; rely on WorkManager (`EpgRefreshWorker` periodic + on-demand once at app start) for refresh.
- ui(lifecycle): Migrate key state reads in `MainActivity` to `collectAsStateWithLifecycle`; start header snapshot updates from `MainActivity` lifecycle scope.
- i18n/sort: Normalize `sortTitle` with NFKD + combining mark removal in `M3UParser`; Room queries now `ORDER BY sortTitle COLLATE NOCASE`.

2025-09-03
- docs(agents): Make AGENTS.md canonical; remove diff-approval; doc updates immediate post-patch.
- chore(gitignore): Ignore SSH/private keys and Android signing keystores to avoid accidental commits.
- docs(consolidate): Set AGENTS.md as sole source; removed AGENTS_NEW.md. Set ROADMAP.md as sole source; removed ROADMAP_NEW.md. Updated references and ARCHITECTURE_OVERVIEW header.
- docs(agents): Add Codex operating rules (override) and WSL build guidance; document repo-local WSL folders `.wsl-android-sdk`, `.wsl-gradle`, `.wsl-java-17` and env setup.
- docs(roadmap): Add cross-reference to AGENTS.md WSL/Linux Build & Test section.
- fix(build): Add missing `animateFloat` imports in `ProfileManagerScreen` and `LibraryScreen`; resolves compile errors.
- fix(player): Define `refreshAudioOptions()` in scope before first use; track options refresh on `onTracksChanged`. No behavior change intended.
- docs(architecture): Update EPG design (persistent Now/Next cache + XMLTV fallback + periodic refresh) and permissions repo; minor player notes.

2025-09-03
- feat(http): Unified headers via `RequestHeadersProvider` + `HttpClientFactory`; applied across Internal Player, Live preview, Coil image requests; external player intent adds VLC `headers` array.
- feat(work): Introduced `SchedulingGateway`; wired Setup and Settings triggers to schedule Xtream refresh + enrichment consistently.
- feat(export): `M3UExporter` now streams to a `Writer` and reads DB in batches to reduce memory usage.
- feat(backup): Local settings backup/restore via SAF (`BackupRestoreSection`) and `SettingsBackupManager`; Quick Import row integrated in Setup and Settings.
- ui(home): Removed Quick‑Import card from Home; Quick Import exists in Setup/Settings only.
- docs(roadmap): Moved completed items from roadmap; roadmap now lists only open tasks.

2025-09-02
- feat(profiles): Gast‑Profil eingeführt (konservative Defaults).
- feat(permissions): Pro‑Profil‑Rechte (canOpenSettings, canChangeSources, canUseExternalPlayer, canEditFavorites, canSearch, canSeeResume, canEditWhitelist) + Enforcements (Settings‑Route‑Gating, Externer Player → intern bei Verbot, Favoriten/Assign‑UI‑Gating, Resume‑Sichtbarkeit).
  - Dateien: data/db/Entities.kt, data/db/AppDatabase.kt (v6 + MIGRATION_5_6), data/repo/PermissionRepository.kt, player/PlayerChooser.kt, MainActivity.kt, ui/home/StartScreen.kt, ui/screens/LibraryScreen.kt
- feat(whitelist): Kategorien‑Freigaben + Item‑Ausnahmen; Admin‑UI im ProfileManager (Badges, expandierbare Items mit Kästchen).
  - Dateien: data/db/Entities.kt (kid_category_allow, kid_content_block), data/db/AppDatabase.kt (v5 + MIGRATION_4_5), data/repo/MediaQueryRepository.kt, ui/profile/ProfileManagerScreen.kt
- fix(kid-mode): Home‑Refresh nutzt gefilterte Queries; Favoriten read‑only für eingeschränkte Profile; „Für Kinder freigeben“ nur bei Rechten sichtbar.
  - Dateien: ui/home/StartScreen.kt, ui/components/rows/HomeRows.kt
- chore(db): Schema auf v6; Migrationen idempotent und rückwärtskompatibel.

2025-09-01
- Docs: ROADMAP erweitert um „Performance & Reliability Plan (Top 5)“: Header/HTTP‑Zentralisierung, WorkManager‑Idempotenz, UI‑Lifecycle/Struktur‑Härtung, Streaming‑Exporter + Batch‑I/O, DataStore/EPG‑Hygiene. Siehe Commit 1fbbb49.

2025-08-29
- Docs: Rebuilt `ARCHITECTURE_OVERVIEW.md` from latest `AGENTS_NEW.md` overview.
- Docs: Refreshed `AGENTS.md` (policies, quick build/test, summary).
- Docs: Replaced `ROADMAP.md` with the new Import/Export plan (from `ROADMAP_NEW.md`) and fixed cross-reference to `ARCHITECTURE_OVERVIEW.md §8`.
- Docs: Initialized `CHANGELOG.md`.

2025-08-29
- feat(epg): Auto-Erkennung Xtream-Creds + streamId aus M3U/Stream-URLs; Persistenz in SettingsStore; Worker-Scheduling nach Import; Now/Next via `get_short_epg` ohne manuelle EPG-URL.
  - Dateien: `core/xtream/XtreamDetect.kt`, `core/m3u/M3UParser.kt`, `prefs/SettingsStore.kt`, `data/repo/PlaylistRepository.kt`
  - Status (vor Änderung): nicht funktionierend (Now/Next unzuverlässig)
  - Status (nach Änderung): zu testen durch Nutzer

2025-08-29
- feat(epg): Persistenter EPG-Cache (Room `epg_now_next`) mit Now/Next pro `tvg-id`, inkl. In-Memory TTL.
  - Dateien: `data/db/Entities.kt`, `data/db/AppDatabase.kt`, `data/repo/EpgRepository.kt`
- feat(epg): XMLTV Multi-Indexing (`XmlTv.indexNowNext`) für mehrere Kanäle in einem Durchlauf; EPG-Fallback auch ohne Xtream-Creds aktiv.
  - Dateien: `core/epg/XmlTv.kt`, `data/repo/EpgRepository.kt`
- feat(epg): Hintergrund-Refresh (WorkManager) alle 15 Minuten; bereinigt veraltete Cache-Daten (>24h).
  - Dateien: `work/EpgRefreshWorker.kt`, `MainActivity.kt`, `data/repo/PlaylistRepository.kt`
- feat(ui): Live-Tiles zeigen aktuellen Programmtitel + Fortschrittsbalken.
  - Datei: `ui/components/rows/HomeRows.kt`
- feat(xtream): Import fusioniert `epg_channel_id` aus bestehender DB, wenn Provider sie nicht liefert (Match per `streamId`).
  - Datei: `data/repo/XtreamRepository.kt`
- fix(xtream): Auto-Detection ergänzt kompaktes URL-Schema `http(s)://host/<user>/<pass>/<id>` (ohne `/live`/Extension).
  - Datei: `core/xtream/XtreamDetect.kt`
Status: zu testen durch Nutzer

2025-08-29
- feat(ui): Einheitliches Accent-Design (DesignTokens) + „Carded Look“ mit `AccentCard`.
  - Dateien: `ui/theme/DesignTokens.kt`, `ui/common/CardKit.kt`
- feat(ui): Hintergrund-Polish (Gradient + radialer Glow + geblurrtes App‑Icon) für Settings, Start, Library, PlaylistSetup, ProfileGate/Manager, Live/VOD/Series Detail.
  - Dateien: diverse `ui/screens/*`, `ui/home/StartScreen.kt`, `ui/auth/ProfileGate.kt`, `ui/profile/ProfileManagerScreen.kt`
- feat(ui): Kids‑Profile mit kräftigem KidAccent (#FF5E5B) und stärkerem Glow.
  - Dateien: `ui/theme/DesignTokens.kt`, Hintergründe in Screens
- feat(ui): CTAs/Chips: Akzentuierte Buttons und Chips (Assist/Filter) auf Detail‑Screens.
  - Dateien: `ui/screens/VodDetailScreen.kt`, `ui/screens/SeriesDetailScreen.kt`, `ui/screens/LiveDetailScreen.kt`
- fix(ui/touch): Reordering der Live‑Favoriten nur per Long‑Press; Scrollen auf Touch bleibt flüssig.
  - Datei: `ui/components/rows/HomeRows.kt` (detectDragGesturesAfterLongPress)
Status: zu testen durch Nutzer

2025-08-29
- fix(playback): Einheitliche Header (User-Agent/Referer) auch für VOD/Serie/Resume und Live-Preview gesetzt; Live-Tile-Preview nutzt nun MediaSource mit DefaultHttpDataSource und Default-Request-Properties.
  - Dateien: `ui/screens/VodDetailScreen.kt`, `ui/screens/SeriesDetailScreen.kt`, `ui/components/ResumeCarousel.kt`, `ui/components/rows/HomeRows.kt`
  - Status (vor Änderung): nicht funktionierend (302/Redirect ohne Header)
  - Status (nach Änderung): zu testen durch Nutzer

2025-08-29
- fix(build): Korrigierte Header-Implementierung in ResumeCarousel (Coroutine statt runBlocking; Flow.first korrekt aus Coroutine), fehlende Importe (`collectAsState`, `flow.first`) ergänzt.
  - Dateien: `ui/components/ResumeCarousel.kt`, `ui/components/rows/HomeRows.kt`, `ui/screens/VodDetailScreen.kt`
  - Status (vor Änderung): nicht funktionierend (Build-Fehler)
  - Status (nach Änderung): zu testen durch Nutzer

2025-08-29
- fix(redirect): Erlaube Cross‑Protocol Redirects (http↔https) in allen Player‑HTTP‑Factories; zusätzliche Extra‑Header (JSON aus Settings) werden bei internen Playern und Live‑Preview angewandt.
  - Dateien: `player/InternalPlayerScreen.kt`, `ui/components/rows/HomeRows.kt`
  - Status (vor Änderung): nicht funktionierend (302 bei Redirects)
  - Status (nach Änderung): zu testen durch Nutzer

2025-08-29
- feat(epg): Neuer `EpgRepository` mit kurzem TTL‑Cache (Now/Next via `get_short_epg`), UI‑Integration in Live‑Tiles und Live‑Detail.
  - Dateien: `data/repo/EpgRepository.kt`, `ui/components/rows/HomeRows.kt`, `ui/screens/LiveDetailScreen.kt`
  - Status (vor Änderung): nicht funktionierend (Now/Next inkonsistent, keine Caches)
  - Status (nach Änderung): zu testen durch Nutzer
- feat(epg): XMLTV‑Fallback für Now/Next, falls `get_short_epg` leer/fehlerhaft ist (nutzt `tvg-id` → XMLTV `programme`, streaming Parser, frühzeitiger Abbruch).
  - Dateien: `core/epg/XmlTv.kt`, `data/repo/EpgRepository.kt`
  - Status (nach Änderung): zu testen durch Nutzer
- feat(settings): Sichtbare Schaltfläche „EPG testen (Debug)“ unter Quelle.
  - Datei: `ui/screens/SettingsScreen.kt`
  - Status (nach Änderung): zu testen durch Nutzer
- feat(export): M3U Export in Settings – Teilen als Text oder als Datei speichern (Playlist aus DB generiert inkl. url-tvg, LIVE+VOD URLs).
  - Dateien: `core/m3u/M3UExporter.kt`, `ui/screens/SettingsScreen.kt`
  - Status (nach Änderung): zu testen durch Nutzer
 - fix(export): Teilen als Datei (FileProvider) statt EXTRA_TEXT, um `TransactionTooLargeException` bei großen Playlists zu vermeiden; Provider/paths korrigiert.
  - Dateien: `ui/screens/SettingsScreen.kt`, `app/src/main/res/xml/file_paths.xml`
  - Status (nach Änderung): zu testen durch Nutzer
- fix(xtream): Schonendes Port‑Update in `configureFromM3uUrl()` (passt nur Port an, wenn Host übereinstimmt); Output nur setzen, wenn leer.
  - Datei: `data/repo/XtreamRepository.kt`
  - Status (vor Änderung): ggf. falscher Port bei https‑M3U
  - Status (nach Änderung): zu testen durch Nutzer
- fix(player/chooser): Default playback mode set to "internal" and settings UI already allows override. Prevents unwanted ask-dialog on details Play.
- fix(library/rows): Show all items per category (removed hard caps of 200 in VOD/Series category rows). Keeps top rows limited but category rows complete.
- fix(kids/assign): After assigning to kid profiles from tiles, show a toast for feedback. No permissions change.
- fix(settings/profile/whitelist): Avoid state updates from background threads in ManageWhitelist; update Compose state on main thread to prevent crashes when opening/editing category contents.
- fix(player/pip): Do not pause playback on Activity onPause when entering Picture-in-Picture; continues stream instead of freezing the last frame.
- fix(vod/plot): Show plot in VOD detail page and add a short 2‑line plot snippet on focused VOD/Series tiles.
- fix(tiles/new): "NEU" badge rendering preserved for new rows; no caps.
- fix(tiles/open): Switch from double‑click to single‑click to open tiles across screens; removes leftover zoom/armed state issues after closing.
- fix(vod/title): Strip category prefixes like "4k-A+ - " from displayed titles in details and tiles.
- feat(header/logo): Fish button in header is now clickable from all major screens (Settings, Details, Profiles) to return to Home without losing in‑progress state.
2025-09-11 (cont.)
- feat(obx/step2-a): Begin migration of profiles/permissions/kids/resume/telegram to ObjectBox.
  - Add OBX entities: `ObxProfile`, `ObxProfilePermissions`, `ObxKidContentAllow`, `ObxKidCategoryAllow`, `ObxKidContentBlock`, `ObxScreenTimeEntry`, `ObxResumeMark`, `ObxTelegramMessage`.
  - Repos updated: `PermissionRepository`, `KidContentRepository`, `ScreenTimeRepository`, `ResumeRepository`, `TelegramRepository` now use ObjectBox.
  - UI wired: `ProfileGate` reads profiles/screen-time from OBX; Start/Live/VOD/Series detail screens resolve profile type from OBX; InternalPlayer uses OBX for resume (VOD) and screen-time.
 - Telegram DataSources/Worker: persist and read `localPath` via `ObxTelegramMessage` instead of Room.
  - Note: ProfileManagerScreen and some series resume paths still read Room; they will be migrated in the next step.
- chore(epg): Remove EpgRefreshWorker and periodic EPG scheduling. EPG freshness handled via on‑demand OBX prefetch (visible tiles + favorites at app start). `SchedulingGateway.scheduleEpgPeriodic` becomes no‑op; `refreshFavoritesEpgNow` now prefetches directly into ObjectBox.
- chore(xtream/delta): Re‑enable periodic Xtream delta import (12h; unmetered+charging) alongside on‑demand trigger. Docs aligned.
 - refactor(resume/obx): Unify resume to ObjectBox. Added `setSeriesResume`/`clearSeriesResume` to `ResumeRepository`; `InternalPlayerScreen` and UI (HomeRows/Library) no longer write/read Room resume marks. Carousel and details already used OBX.
- feat(player/series-obx): Remove last Room lookups from series playback. `InternalPlayerScreen` accepts series composites (`seriesId`,`season`,`episodeNum`) and uses `ObxEpisode` to resolve next episode and `ResumeRepository` for resume. `SeriesDetailScreen` and resume UI pass composites; navigation route extended with optional series keys. Kept legacy `episodeId` param for backward compatibility.
- Fix: Restore centralized Coil 3 global ImageLoader via AppImageLoader singleton; resolved Coil 3 API mismatches (no ImageLoaderFactory in v3) and kept tuned disk/memory caches with request-level headers.
- Fix: XtreamObxRepository serialization – use String.serializer() for ListSerializer to avoid unresolved builtins.serializer issues.
- DX: Add detailed playback logging (PlayerChooser decisions, ExoPlayer errors) and toast on error to speed up diagnosing playback issues.
## 2025-09-16
- UI loading order refined: lists (Live/VOD/Series) load first via OBX seeding; images/metadata follow via delta import. Tiles appear immediately.
- EPG prefetch limited to Live rows only; removed unnecessary prefetch from VOD/Series rows. EPG remains lazy and loads when Live tiles are visible; Live detail screen still fetches EPG immediately for the opened channel.
- Playback: VOD play URL builder now respects `containerExt` when reconstructing URLs (better extension fidelity if `MediaItem.url` is absent).
- Xtream seeding: added log `XtreamObxRepo seedListsQuick live=.. vod=.. series=..` to aid diagnosing empty categories (e.g., VOD).
- XtreamDeltaImportWorker tuned for speed: 6h periodic (CONNECTED + battery-not-low), expedited one-shots, exponential backoff; triggers favorite EPG prefetch after successful import.
- Import concurrency: increased detail-fetch parallelism in XtreamObxRepository from 6 → 8 for VOD/Series detail fetches.
- Safe playback diagnostics: PlayUrlHelper logs non-sensitive URL resolution info (host/port/kind/id/ext) to verify stream parsing and propagation.
- VOD container extension: capture `container_extension` from Xtream `movie_data` and persist to OBX; builders and player now honor the correct extension.
- Settings: Added “Import & Diagnose” section showing last import timestamp and seed/delta counts, plus buttons for expedited import and favorites-EPG refresh.
- Series episodes: Parse episode images from Xtream (movie_image/cover/poster_path/thumbnail/img/still_path), persist in ObjectBox, and show in Series detail list.
- Trailers: Embedded trailer player added to VOD and Series details. YouTube links render via in-app WebView (embed), others play via a lightweight in-app ExoPlayer box. Both support expanding to a full-screen dialog.
 - Performance (Start): Reduced paging sizes to show content faster. Start screen now preloads 30 items for Series, VOD, and Live rows (initialLoad=30, pageSize=30, prefetchDistance=10); further items load on scroll. Default sort for Series/VOD switched to newest-first (importedAt desc, fallback yearKey desc) with normalized title tie-breaker.
- Library grouping: For VOD and Series, grouping focuses on Providers only (Years/Genres sections hidden). Live keeps existing grouping.
- TDLib: Rebuild script now focuses on minimal size.
  - Enabled LTO/IPO, MinSizeRel, GC-sections/ICF, and aggressive strip of `libtdjni.so`.
  - Keeps static BoringSSL but compiles with size flags; no dynamic OpenSSL deps.
  - Expect a significant APK size reduction (primarily native .so).
## Unreleased
- chore(ui/focus): Replace the legacy `ui.skin` facade with native FocusKit primitives, add `FocusPrimitives`, and migrate all screens/components to `FocusKit.run`.
- chore(ui/layout): Drop `BuildConfig.CARDS_V1` and remove `ui/cards/*` (PosterCard/ChannelCard/SeasonCard/EpisodeRow + Tagged). Home rows, detail screens, and Live detail now use FishTile-only flows for "more" sections.
- Bootstrap performance: reduce UI overhead while parsing
  - Replace animated Material3 progress with lightweight bar.
  - Suppress live progress during bootstrap (single final update).
  - Increase streaming batch size during bootstrap (8000) to cut event frequency (fallback paths).
  - HTTP bootstrap path uses Reader-based parse to avoid per-batch UI/logic overhead.
- Fix deprecations and warnings (Compose progress overload, Button border, FlowPreview opt-ins, Xtream URL DEPRECATION suppression) without changing core flows.
- Guard icon lookups so missing drawables fall back instead of hitting `painterResource(0)`; removes `Invalid resource ID 0x00000000` spam on cold start.
- Disable Firebase auto-init when no google-services config is bundled and gate push service usage accordingly, avoiding startup warnings on clean installs.
- Fix(nav): use route-aware `popUpToStartDestination` helper to skip invalid resource lookups and remove `No package ID` errors when returning to Home.
2025-09-26
- fix(settings): HTTP‑Traffic‑Log Switch sichtbar und am richtigen Ort. Schalter aus der Xtream‑Quelle entfernt und in den aufklappbaren Reiter „Import & Diagnose“ verschoben. Logger‑Status wird nun beim App‑Start aus den Settings initialisiert (MainActivity → repeatOnLifecycle), damit die Einstellung Sitzungen überdauert.
- feat(debug): Globales Debugging‑Modul + Schalter in „Import & Diagnose“. Loggt Navigationsschritte, DPAD‑Eingaben (UP/DOWN/LEFT/RIGHT/CENTER/BACK, inkl. Player‑Tasten), Tile‑Focus (mit OBX‑Titel in Klammern) und OBX‑Key‑Updates (sort/provider/genre/year) via Logcat („GlobalDebug“). Listener in MainActivity (NavController), Key‑Hooks im Scaffold/Player/Tiles und Hooks im `ObxKeyBackfillWorker`; Status per Settings synchronisiert.
- feat(tv/chrome): Long‑Press DPAD‑LEFT verhält sich wie der Burger/MENU‑Knopf und toggelt die Panels (Expanded/Collapsed) im TV‑Modus; Fokus springt auf den Header beim Expand.
- feat(debug/global): RouteTag + tree‑Logs global auf allen Screens (home/browse/settings/gate/setup/profiles/xt_cfcheck/player + Detail‑Screens). Jeder DPAD‑Log hängt den letzten Fokus‑Kontext als tree‑Pfad an (z. B. route > row:vod > tile:1234). BACK/Escape wird global geloggt; Long‑Press LEFT wird zusätzlich global über Down/Up‑Dauer erkannt.
2025-09-26
- fix(tv/chrome/back): ESC/BACK now collapses HomeChrome whenever it isn’t already collapsed (Expanded or Visible) and consumes the event on TV. Prevents unintended player/activity exit when the chrome is showing; BACK once collapses chrome, a second BACK exits as expected.
- feat(debug/tile-focus): Add generic tile‑focus logs to core row engines (MediaRowCore and MediaRowCorePaged). Logs `focus:<type> id=<id> <uiTitle> (<OBX title>)` plus a matching `tree:` hint on focus. Applies to all rows using the shared engines.
- feat(debug/rows): Add `row:autoCenter idx=<n> visible=<...>` and `row:focusIdx idx=<n>` logs in RowCore/RowCorePaged when GlobalDebug is enabled. Add DPAD LEFT/RIGHT key-up logs for generic tiles (MediaCard) to diagnose skipped-focus vs. scroll.
- feat(debug/rows): Add `row:scroll:start/stop` logs (RowCore & Paged) to correlate DPAD presses, focus changes, and scroll animations for TV rows.
 - feat(tv/tiles): Stronger visual focus on tiles. Increase focused scale to +40% and keep it while focused (pressed uses same scale). Boost halo by using a thicker ring (5dp) and stronger internal fill/border on tile focus. Applied to VOD/Series/Live row tiles and Resume carousel.
 - fix(tv/rows centering): Apply single-step centering to TvFocusRow (if target is already visible). Avoid the subtle left-then-right jitter on DPAD. Debounce focus while scrolling (flush the last requested index on scroll stop) in RowCore/RowCorePaged/TvFocusRow to prevent skipped tiles.
 - fix(tv/focus layering): Elevate focused/pressed tiles via `zIndex(2f)` in tvClickable/focusScaleOnTv and disable layer clipping (`graphicsLayer { clip=false }`) so scaled tiles aren’t visually clipped by parent bounds or overlays.
2025-09-26
- fix(tv/rows/scroll): centralize bring-into-view and centering in new helper `ui/tv/TvRowScroll.kt` and use it from RowCore and TvFocusRow. Prevents jitter and ensures focused tiles are fully visible.
- fix(tv/rows/left-nav): avoid conflicting per-tile autoBringIntoView in horizontal rows (set `autoBringIntoView=false` on row tiles). Restores reliable DPAD LEFT navigation and stops hidden-but-focused tiles.
2025-09-29
- feat(tv/low-spec): Add DeviceProfile with TV heuristics and enable TV low-spec tuning.
  - tvClickable/focusScaleOnTv: reduce focus scale (1.03), drop shadowElevation on TV to cut GPU cost.
  - Paging: use smaller pages on TV (pageSize=16, prefetchDistance=1, initialLoad=32) for smoother focus scroll.
  - Coil: disable crossfade on TV to avoid overdraw; keep RGB_565 and measured sizing.
  - OkHttp: throttle dispatcher on TV (maxRequests=16, perHost=4) to curb IO contention.
- feat(player): Pause Xtream seeding while playing. When internal player is active, `m3u_workers_enabled` is forced OFF (remembering previous state), in-flight Xtream jobs are canceled, and the flag is restored on exit.
2025-09-30
- feat(tv/forms): Add TV Form Kit v1 under `ui.forms` with DPAD‑optimized rows: `TvFormSection`, `TvSwitchRow`, `TvSliderRow`, `TvTextFieldRow`, `TvSelectRow`, `TvButtonRow`, and `Validation` helpers. Consistent focus visuals and inline validation hints; text fields use dialog input on TV to avoid keyboard traps.
- feat(setup): Migrate `PlaylistSetupScreen` to use the TV Form Kit when `BuildConfig.TV_FORMS_V1` is ON (default). Legacy controls remain as fallback when the flag is OFF.
- docs(tv/forms): Add `docs/tv_forms.md` with layout/behavior guidelines and usage examples.
2025-09-30
- feat(ui/actions): Introduce centralized MediaAction model + MediaActionBar under `ui.actions` with DPAD‑friendly buttons and test tags. Telemetry hooks (`ui_action_*`) added.
- feat(details): Migrate detail screens to MediaActionBar when `BuildConfig.MEDIA_ACTIONBAR_V1` is ON (default):
  - VodDetail: Resume + Play + Trailer + Share
  - LiveDetail: Play + OpenEPG + Share (+ Add/Remove favorites when permitted)
  - SeriesDetail: Play (first episode) + Trailer; per‑episode rows now render a MediaActionBar (Resume? → Play → Share)
- feat(detail/scaffold): Add `ui/detail` with `DetailHeader`, `MetaChips`, `HeroScrim`, `DetailScaffold` and flag `BuildConfig.DETAIL_SCAFFOLD_V1` (default ON). Migrate VOD + Series + Live headers to `DetailHeader` under the flag.
- docs(actions): Add `docs/media_actions.md` with API/usage/order guidelines.
- feat(ui/state): Introduce UiState layer (`UiState`, `StatusViews`, `collectAsUiState`) gated by `BuildConfig.UI_STATE_V1` (default ON). Detail screens (VOD/Series/Live) now render a single state (Loading/Empty/Error/Success) with early-return gates; legacy spinners remain as fallback when flag is OFF.
- feat(library/search): Reintroduce Library search rows using Paging with `collectAsUiState` gating; renders Loading/Empty/Error/Success and supports retry. Start search now gates via a combined paging count across Series/VOD/Live.
- feat(ui/cards): Add unified Cards library (`ui/cards/*`) and flag `BuildConfig.CARDS_V1` (default ON). `HomeRows` delegates to `PosterCard`/`ChannelCard` under the flag; `SeriesDetail` uses `EpisodeRow` for per‑episode items.
- feat(playback): Add centralized PlaybackLauncher (`playback/*`) with `PlayRequest`/`PlayerResult` and flag `BuildConfig.PLAYBACK_LAUNCHER_V1` (default ON). Migrate VOD/Series/Live detail actions and Resume carousel to use the launcher when enabled.
2025-09-30
- fix(live/detail): Resolve Kotlin parse error “Expecting a top level declaration” by moving EPG/Kid sheet dialogs inside `LiveDetailScreen` body and correcting brace balance. Prevents premature function closure and restores release build.
- chore(start,library): Migrate remaining direct PlayerChooser starts to centralized PlaybackLauncher (flagged via PLAYBACK_LAUNCHER_V1). Start and Library now route internal playback via the nav `player` route through the launcher’s `onOpenInternal` hook for consistent resume/telemetry.
- feat(vod/live detail): Wire missing onOpenDetails handlers. VOD “Similar” now opens the selected VOD detail; Live “Mehr aus Kategorie” opens the selected channel’s detail (new `openVod`/`openLive` lambdas on detail screens, passed from MainActivity).
2025-10-02
- fix(tv/start): Auto-expand HomeChrome on first start when Start is empty (no rows) and auto-focus the Settings button in the top-right FishIT header on TV. Ensures immediate access to Settings after PIN without content. DPAD LEFT now also expands HomeChrome from the empty screen to escape the white screen.
 - ui(detail): VOD and Series hero background now fills the entire screen (outside scaffold padding) with ContentScale.Crop. TV/landscape zooms to cover width; portrait covers screen while preserving aspect ratio. Removed FishBackground overlay layer from both detail screens.
 - ui(detail/header): Removed the top HeroScrim band inside DetailHeader for VOD and Series. The full-screen hero now uses the same shared alpha as the former scrim (`HERO_SCRIM_IMAGE_ALPHA = 0.5`). `DetailHeader` gains `showHeroScrim` (default true); Live keeps scrim, VOD/Series disable it.
 - fix(vod/detail): Align VOD detail import + rendering with Series.
   - Trigger detail import when plot OR imagesJson is missing (previously only plot), so backdrops get fetched reliably.
   - Observe imagesJson changes and recompute backdrop at runtime.
   - Remove placeholder plot text; only render plot when non-blank (same as Series).
 - feat(detail/centralization): Unified detail rendering across VOD/Series/Live via `ui/detail`.
   - Add `DetailHeaderExtras` (MPAA/Age/compact Audio/Video chips) and `DetailFacts` (Jahr, Laufzeit, Container/Qualität, ★Rating, Provider/Kategorie, Genres, Länder, Regie/Cast, Release, IMDB/TMDB‑IDs+Link, Audio/Video/Bitrate).
   - Remove/deactivate legacy header paths. Start/Home rows no longer prefetch details; details load metadata on-demand only.
   - Disable DPAD‑LEFT→HomeChrome in details (`enableDpadLeftChrome=false`); re-enable BACK via Compose BackHandler on VOD/Series.
 - feat(xtream/normalize): VOD info block prioritized over `movie_data` with robust synonyms.
   - Poster: `movie_image|poster_path|cover|cover_big`; Backdrops: `backdrop_path` (array/string)
   - Plot: `plot|description|plot_outline|overview`; Trailer: `youtube_trailer|trailer|trailer_url|youtube|yt_trailer`
   - Genre/Release: `genre|genres`, `releasedate|releaseDate`; Technik: `audio|video|bitrate`; IDs: `tmdb_id|tmdb_url`, `o_name|cover_big`
 - ui(detail/tmdb): Poster/Cover/Backdrop selection follows TMDb order for VOD and Series.
   - images = [poster, cover?, backdrops...]; header poster uses cover if present (fallback poster).
   - full-screen hero/backdrop uses first backdrop (fallback poster).
 - feat(detail/metadata): Rich on-demand metadata on VOD/Series detail screens.
   - Load metadata only when a detail is opened; removed neighbor/global prefetch from Start/Home rows and details.
   - VOD shows extra fields (MPAA/Age, Audio/Video/Bitrate, TMDb link) as chips/sections; plot rendered only when non-blank.
   - Trailer field robust via synonyms for both VOD/Series (youtube_trailer/trailer/trailer_url/youtube/yt_trailer).
2025-10-04
- fix(ui/start): StartScreen sections no longer collapse at the top. Apply vertical weights to Series/Filme/Live containers and auto-size row heights in landscape (40/40/20). Result: three stable sections, no overlap, proper use of screen height on TV.
2025-10-05
- fix(ui/gate,tv): ProfileGate tiles (Adult, Add, Kids) wrapped with `tvFocusFrame` for a robust, always-visible TV focus halo. `tvClickable` on these tiles now uses neutral scaling and no extra ring to avoid double effects. Shapes aligned with tile silhouettes (rounded 28dp/22dp) so the halo matches visuals. Improves DPAD clarity on TVs and aligns with Compose TV 2025 guidance.
 - chore(roadmap/docs): Set "Prio 1 — Globale Zentralisierung Fokus/TV‑Darstellung" at the top of ROADMAP; removed verstreute TV‑Focus Roadmap‑Einträge. Added canonical guide `tools/Zentralisierung.txt` for all future focus/TV work.
 - feat(ui/focus): Added `ui/focus/FocusKit.kt` as a single import surface for focus primitives and rows. Re‑exports `tvClickable`, `tvFocusFrame`, `tvFocusableItem`, `focusGroup`, and both `TvFocusRow` variants via `TvRow(...)`. Screens can now use `FocusKit` only.
 - chore(ui/gate): ProfileGate now uses FocusKit (`tvFocusFrame`, `tvClickable`, `TvRow`) and removes duplicate per-item focus logic (`tvFocusableItem`, bringIntoViewOnFocus) inside the kids row. Keeps 1:1 visuals with centralized primitives.
 - refactor(ui/gate): Simplified Adult and Add tiles to a single top‑level Surface that is both focusable and clickable. Removed redundant borders/elevations and ensured bringIntoViewOnFocus is attached to the top wrapper so initial focus is visible and DPAD navigation highlights the correct layer.
2025-10-06
- tooling(tv/audit): Align `tools/audit_tv_focus.sh` with `tools/Zentralisierung.txt`. Added checks for forbidden TvLazyRow, centralized bring-into-view (flag per-item `bringIntoViewRequester`/`onFocusChanged`/`scrollToItem`), duplicate focus indicators (tvClickable with non-neutral scale/border outside `TvButtons`), and SSOT enforcement (no custom focus primitives outside central modules). Excludes diff folders (`a/**`, `b/**`) and `.git`; allows central facades (`ui/focus/FocusKit.kt`, `ui/skin/PackageScope.kt`). Summary now reports new categories.

- feat(ui/focus): Finalized global `FocusKit` facade. Adds a single import surface for all focus usage (TV + phone/tablet):
  - Primitives: `tvClickable`, `tvFocusFrame`, `tvFocusableItem`, `focusGroup`, `focusBringIntoViewOnFocus`, `focusScaleOnTv`.
  - Rows: `TvRowLight` (delegates to `ui/tv/TvFocusRow`), `TvRowMedia` and `TvRowPaged` (delegate to `RowCore` engines with prefetch, chrome edge, and focus memory).
  - DPAD helpers: `onDpadAdjustLeftRight/UpDown` (TV‑only by default), `focusNeighbors` for keypad/grid navigation.
  - Buttons: Re‑exports `TvButton`/`TvTextButton`/`TvOutlinedButton`/`TvIconButton` for consistent focus visuals.
  - Backward‑compat: existing top‑level wrappers remain; new `FocusKit.*` is the single recommended entry point for screens.

- docs(roadmap): Temporarily set all previously open roadmap items to OFF and added a new top priority section “FocusKit Migration (ON)” with a repo‑wide audit and concrete migration list (rows, primitives, forms) plus guidance. Aligns delivery with the finalized FocusKit facade.

- refactor(ui/auth/profilegate): Switched ProfileGate to the FocusKit facade. Replaced TextButton actions with `FocusKit.TvTextButton` and routed keypad/tiles through `FocusKit.run { Modifier.tvClickable/tvFocusFrame/focusBringIntoViewOnFocus }`. Behavior unchanged; audit recognizes button focus visuals.

- docs(ui/layout): Add centralized Fish* modules and plan
  - Tokens: `FishTheme` (FishDimens) + CompositionLocal; editables for size/spacing/corners/focus scale/glow.
  - Tiles/Rows: `FishTile` (ContentScale.Fit, no per‑tile scroll) + `FishRow(Light/Media/Paged)` (fixed spacing/padding, DPAD/edge logic in Media).
  - Content: `FishVodContent` (title/poster/resume/new/assign/play/logging/footer), `FishSeriesContent`/`FishLiveContent` (base), helpers `FishMeta`/`FishActions`/`FishLogging`, `FishResumeTile`.
  - CARDS_V1 slated for removal during porting; PosterCard/ChannelCard/PosterCardTagged will be replaced with FishTile.

- roadmap: Add Tiles/Rows Centralization (ON). Mark FocusKit Migration as dependent on this centralization.
2025-10-07
- docs(centralization): Deep-docs sweep to align with new Fish* layout. Marked legacy Cards v1 (PosterCard/ChannelCard/SeasonCard/EpisodeRow) as deprecated/replaced, removed guidance that suggested building tiles/focus per-screen, and documented FishTheme/FishTile/FishRow/FishContent (+ FishMeta/FishActions/FishLogging/FishResumeTile) as the single source of truth. Updated media_actions, detail_scaffold, tv_forms, playback_launcher to reference Fish* where relevant. Roadmap now blocks FocusKit finalization on completing Tiles/Rows centralization.
2025-10-20
- chore(warnings): Kotlin/Compose warning cleanup across app.
  - telegram/auth: Replace deprecated `Bundle.getParcelable(String)` calls in
    `TgSmsConsentManager` with `BundleCompat.getParcelable(..., Class)` for
    `EXTRA_STATUS` and `EXTRA_CONSENT_INTENT`.
  - start/search: Fix `UiState` gating to use `searchUi?.value` and remove
    impossible instance checks that were always false.
  - player/mini: Simplify progress effect guard; drop redundant `player==null`
    part that made the condition constant.
  - header: Remove redundant `firstFocus != null` checks when computing
    requesters; use non-null (`!!`) under guarded flags.
  - forms: Use `Icons.AutoMirrored.Filled.ArrowForward`, switch
    `LinearProgressIndicator` to the lambda overload, and drop the shadowed
    `IntRange.isEmpty()` extension.
  - live/detail: Remove always-true `liveLauncher != null` check; rely solely on
    the centralized `rememberPlaybackLauncher` path.
 - settings/telegram: Remove redundant `else` in exhaustive `when` for the
  dialog title.
  - settings/telegram: Chat‑Picker überarbeitet – kein BottomSheet mehr,
    stattdessen top‑angerdockter Vollbild‑Dialog ohne Drag‑Gesten. Persistente
    Aktionsleiste oben mit Auswahl‑Zähler und ständig erreichbarem
    „Übernehmen“-Button (aktiv bei Auswahl). Overscroll deaktiviert, flüssigeres
    Scrollen auf schwächeren Geräten.
 - settings: Einheitlicher TV‑Fokus. Alle Buttons/Links im Settings‑Screen
    nutzen jetzt FocusKit (TvButton/TvTextButton) statt gemischter Material3
    Buttons. Dadurch gleiche Helligkeit/Glow/Scale für alle klickbaren Elemente.
  - telegram/service: Bei Loglevel 5 werden beim Chat‑Sync die letzten 100
    Nachrichten eines Chats als RAW in Logcat ausgegeben (Tag "TdSvcRaw").
    Enthält id, datum, content‑Typ, supportsStreaming und Text/Captions.

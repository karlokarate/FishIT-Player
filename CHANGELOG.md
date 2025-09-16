# 
# FishIT Player – Changelog

All notable changes to this project are documented here. Keep entries concise and tied to commits/PRs.

2025-09-16
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

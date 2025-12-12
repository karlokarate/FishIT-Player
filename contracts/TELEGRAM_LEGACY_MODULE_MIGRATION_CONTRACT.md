# 2) Migration Contract — Telegram legacy module migration

**Save as:** `contracts/TELEGRAM_LEGACY_MODULE_MIGRATION_CONTRACT.md`

## 1. Contract header

**Title:** Telegram Legacy Module Migration Contract (Transport + Streaming + Imaging)
**Version:** 1.0
**Status:** Binding (for the migration scope defined here)
**Applies to:** `infra/transport-telegram`, `playback/telegram`, any wiring in `app-v2`, and verification in `pipeline/telegram`

This contract is subordinate to (and must not conflict with):

* v2 Naming Glossary 
* Media Normalization Contract 
* Logging Contract v2 
* AGENTS rules (layer boundaries, forbidden imports, legacy isolation) 

## 2. Goals

1. Port the proven behaviors contained in the provided legacy files into v2 modules while enforcing v2 architecture boundaries.
2. Guarantee best-quality Telegram streaming and thumbnail loading using **TDLib disk cache + zero-copy FileDataSource** (no app-level ringbuffers).
3. Ensure catalog ingestion can backfill continuously while respecting global playback priority and device constraints.

## 3. Non-goals

* Multi-account Telegram support (explicitly out of scope).
* Any UI implementation inside transport/pipeline modules.
* Any metadata normalization or TMDB access inside the Telegram pipeline. 

## 4. Fixed product decisions (binding)

1. **Single account per install**: one TDLib DB, one TDLib client per process lifetime (DI-scoped).
2. **Chat selection**:

   * bots excluded
   * active chats prioritized
   * once a chat is classified “library/media-rich”, it must not be dropped even if inactive
3. **Backfill strategy**: hybrid (UI fast path + deep history backfill).
4. **Retry policy**:

   * exponential backoff
   * max retry attempts per operation
   * circuit breaker on repeated failures
5. **Playback priority policy**:

   * playback is always top priority
   * heavy background backfill pauses during playback
   * resume after 3 seconds debounce after stop/pause
6. **Idle definition** uses multiple signals (playback, app visibility, network, battery saver).
7. **Auth policy**:

   * resume-first behavior
   * offline pauses retries
   * if TDLib requires reauth, emit event and allow UI to trigger login flow

## 5. Architecture boundaries (hard rules)

### 5.1 Transport module (`infra/transport-telegram`)

Transport may use TDLib types (`dev.g000sha256.tdl.*`) and owns:

* TDLib client lifecycle (single instance)
* auth session state machine
* chat browsing primitives
* file download primitives
* storage maintenance calls (`getStorageStatisticsFast`, `optimizeStorage`) 

Transport must not:

* depend on UI modules
* show snackbars/toasts
* implement playback or MP4 parsing decisions (belongs to playback)

### 5.2 Playback module (`playback/telegram`)

Playback owns:

* MP4 “moov atom” readiness validation
* streaming readiness thresholds and polling policies
* tg:// URI handling + stale fileId fallback using remoteId

Playback must not:

* directly manage TDLib lifecycle
* implement catalog scan logic

### 5.3 Pipeline module (`pipeline/telegram`)

Pipeline must:

* produce `RawMediaMetadata` only
* not normalize titles and not call TMDB/IMDB/TVDB directly 
* not import TDLib or OkHttp or transport internals directly (only through the transport API surface) 

## 6. Logging rules (binding)

All v2 modules must log only via `UnifiedLog.*` and must not introduce `println`, Logcat direct calls, or Timber usage outside `infra/logging`. 

## 7. Deprecated legacy behaviors that must NOT return

* Any ringbuffer-based streaming approach (deprecated in legacy) 
* Any “custom window streaming” logic (deprecated in legacy downloader) 
* Any manual singleton pattern (`getInstance`) for the TDLib engine (must be DI-scoped instead) 

## 8. Mandatory test coverage (binding minimum)

1. Auth reauth detection: Ready → WaitCode/WaitPassword triggers reauth-required event. 
2. Chat history paging rule: offset 0 then -1 anchor semantics. 
3. Download manager concurrency and queue fairness. 
4. RemoteId fallback for stale fileId.
5. MP4 moov validation gating playback start.

## 9. Compliance & enforcement

Any violation of this contract is treated as a migration blocker:

* must be fixed before merge,
* must be documented if a temporary exception is required,
* must pass detekt/ktlint gates and the layer-boundary grep audits. 



[1]: https://developer.android.com/jetpack/androidx/releases/media3?utm_source=chatgpt.com "Media3 | Jetpack - Android Developers"
[2]: https://mvnrepository.com/artifact/com.squareup.okhttp3/okhttp?utm_source=chatgpt.com "com.squareup.okhttp3 » okhttp"
[3]: https://coil-kt.github.io/coil/changelog/?utm_source=chatgpt.com "Change Log - Coil"
[4]: https://libraries.io/maven/dev.g000sha256%3Atdl-coroutines-macosarm64?utm_source=chatgpt.com "dev.g000sha256:tdl-coroutines-macosarm64 5.0.0 on Maven"

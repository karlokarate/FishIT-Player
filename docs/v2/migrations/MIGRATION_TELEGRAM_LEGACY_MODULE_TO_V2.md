# Telegram Legacy Module → v2 Migration

**Status:** ✅ Complete (Phases A-F)  
**Last Updated:** 2025-01-16  
**Current Phase:** All implementation phases complete. Phase G (Backfill Orchestration) deferred to `core/catalog-sync`.

---

## Migration Status Dashboard

| Phase | Task | Status | Notes |
|-------|------|--------|-------|
| **A** | Pre-flight checks | ✅ Complete | Branch verified, naming/logging guards passed |
| **B1** | Typed Interface Contracts | ✅ Complete | Created `TelegramAuthClient`, `TelegramHistoryClient`, `TelegramFileClient`, `TelegramThumbFetcher` |
| **B2** | TdlibAuthSession migration | ✅ Complete | Auth state machine in `auth/TdlibAuthSession.kt` |
| **B3** | TelegramChatBrowser migration | ✅ Complete | Chat browsing with offset -1 paging rule in `chat/TelegramChatBrowser.kt` |
| **C** | TelegramFileDownloadManager | ✅ Complete | Transport-only download manager in `file/TelegramFileDownloadManager.kt` |
| **D** | TelegramStreamingConfig | ✅ Complete | `TelegramStreamingConfig` + `TelegramFileReadyEnsurer` in `playback/telegram/config/` |
| **E** | TelegramThumbFetcherImpl | ✅ Complete | Coil integration in `imaging/TelegramThumbFetcherImpl.kt` |
| **F** | Pipeline verification | ✅ Complete | Layer boundary audit passed (no violations) |
| **G** | Backfill orchestration | ⏸️ Deferred | Requires `core/catalog-sync` module (cross-pipeline) |

### Phase B1 Deliverables (Completed)

| Interface | File | Description |
|-----------|------|-------------|
| `TelegramAuthClient` | `TelegramAuthClient.kt` | Auth flow: ensureAuthorized, sendCode, sendPassword, logout |
| `TelegramHistoryClient` | `TelegramHistoryClient.kt` | Chat/message fetching with paging (offset -1 rule documented) |
| `TelegramFileClient` | `TelegramFileClient.kt` | Download primitives: start, cancel, resolveRemoteId, prefixSize |
| `TelegramThumbFetcher` | `TelegramThumbFetcher.kt` | Coil integration: fetchThumbnail, prefetch, bounded error tracking |
| `TgFileUpdate` | `TelegramFileClient.kt` | Sealed class: Progress/Completed/Failed events |
| `TgStorageStats` | `TelegramFileClient.kt` | Storage statistics data class |
| `TgThumbnailRef` | `TelegramThumbFetcher.kt` | Thumbnail reference (fileId, remoteId, dimensions) |

### Phases B2-E Deliverables (Completed)

| Class | Module | Package | Description |
|-------|--------|---------|-------------|
| `TdlibAuthSession` | `infra/transport-telegram` | `.auth` | Full auth state machine with resume-first, reauth flow |
| `TelegramChatBrowser` | `infra/transport-telegram` | `.chat` | Chat/message browser with TDLib offset -1 paging |
| `TelegramFileDownloadManager` | `infra/transport-telegram` | `.file` | Transport-only download control (no playback logic) |
| `TelegramStreamingConfig` | `playback/telegram` | `.config` | SSOT for streaming constants (priorities, prefixes) |
| `TelegramFileReadyEnsurer` | `playback/telegram` | `.config` | MP4 moov validation for playback readiness |
| `TelegramThumbFetcherImpl` | `infra/transport-telegram` | `.imaging` | Coil thumbnail fetcher with bounded failed cache |

### Phase F Layer Boundary Audit (Passed ✅)

| Check | Command | Result |
|-------|---------|--------|
| No TDLib in pipeline main | `grep "dev.g000sha256.tdl" pipeline/telegram/src/main/` | ✅ No matches |
| No pipeline imports in transport | `grep "com.fishit.player.pipeline" transport-telegram/src/main/` | ✅ No matches |
| No forbidden logging | `grep "println\|printStackTrace\|android.util.Log\|Timber" transport-telegram/src/main/` | ✅ No matches |

---

## 0. Scope

This migration ports the **battle-tested behaviors** from the legacy Telegram module files into v2 **without breaking v2 architecture boundaries** and **without reintroducing legacy debt**.

Source files (legacy/v1 module extract):

* `StreamingConfig.kt` 
* `StreamingConfigRefactor.kt` 
* `T_ChatBrowser.kt` 
* `T_TelegramFileDownloader.kt` 
* `T_TelegramFileDownloaderRefactor.kt` 
* `TelegramFileLoader.kt` 
* `T_TelegramSession.kt` 
* `T_TelegramServiceClient.kt` 

## 1. Non-negotiable v2 rules to obey

### 1.1 Binding docs / contracts (must be complied with)

* Naming & modules glossary (authoritative) 
* Contract inventory / “binding contracts” rule 
* Media normalization contract: pipelines emit Raw only, never normalize / never TMDB inside pipeline 
* Logging contract: only `UnifiedLog.*`, no `println`, no `android.util.Log`, no Timber outside infra/logging 
* Agent rules: forbidden imports, layer boundaries, “no global mutable singletons” policy 
* Target module structure overview 

### 1.2 Accepted runtime/product decisions (FINAL)

1. **Single Telegram account per installation** (1 TDLib DB + 1 TDLib client per process).
2. **Chat selection:** only “active chats”, **but** “library chats” (e.g. series dump chats that don’t receive new messages) must **never be dropped** once recognized.
3. **Backfill strategy:** **hybrid**

   * UI-fast path: newest items show early
   * Background: systematic historical backfill (new → old per chat; older pages continue until exhausted)
4. **Retry/backoff:** exponential backoff + circuit breaker + hard retry caps (no infinite tight loops).
5. **Playback policy:** while playing → only “light” background tasks; after playback stop/pause → resume background after **3s debounce** (global, across pipelines).
6. **Idle definition:** not only “no playback”; must consider app visibility + network availability + device constraints.
7. **Auth:** always attempt resume; offline pauses retries; if TDLib requires reauth → user login flow must be triggered.
8. **Concurrency limits:** bounded parallelism; TDLib internal throttling respected; we do not spam TDLib.
9. **Filtering:** transport fetches messages; pipeline filters to media (no premature transport filtering).
10. **UI readiness:** first visible rows can render as soon as a small amount is available; scrolling triggers **priority fetch** ahead of deep backfill.

---

## 2. Target v2 placement (fixed mapping)

> **Hard rule:** no `com.chris.m3usuite.*` outside `legacy/`.  

### 2.1 What goes where (module + package + rename)

| Legacy class/file                   | v2 module                                           | v2 package                                           | New name                                               | Notes                                                                   |
| ----------------------------------- | --------------------------------------------------- | ---------------------------------------------------- | ------------------------------------------------------ | ----------------------------------------------------------------------- |
| `T_TelegramServiceClient`           | `infra/transport-telegram`                          | `com.fishit.player.infra.transport.telegram`         | **Replace** with DI-scoped provider + transport client | Must remove UI references (layer violation present in legacy).          |
| `T_TelegramSession`                 | `infra/transport-telegram`                          | `com.fishit.player.infra.transport.telegram.auth`    | `TdlibAuthSession`                                     | Emits auth state; no UI.                                                |
| `T_ChatBrowser`                     | `infra/transport-telegram`                          | `com.fishit.player.infra.transport.telegram.chat`    | `TelegramChatBrowser`                                  | TDLib paging rules preserved (offset -1 after first page).              |
| `T_TelegramFileDownloader`          | split                                               | split                                                | split                                                  | See below: download manager vs MP4-ready checks.                        |
| `T_TelegramFileDownloaderRefactor`  | (ref only)                                          | —                                                    | —                                                      | Do **not** port as a second implementation; merge best bits into final. |
| `StreamingConfig`                   | `playback/telegram`                                 | `com.fishit.player.playback.telegram.config`         | merged                                                 | Legacy windowing/ringbuffer stays deleted; no resurrection.             |
| `StreamingConfigRefactor`           | `playback/telegram`                                 | `com.fishit.player.playback.telegram.config`         | `TelegramStreamingConfig`                              | This becomes the **single source of truth** for streaming constants.    |
| `TelegramFileLoader`                | `infra/transport-telegram` (and wiring in `app-v2`) | `com.fishit.player.infra.transport.telegram.imaging` | `TelegramImageFileResolver`                            | Used by Coil3 component integration; UI must not call TDLib directly.   |

### 2.2 Split of `T_TelegramFileDownloader` (mandatory refactor)

Legacy currently mixes:

* transport download control + queueing + storage maintenance 
* playback-specific “file-ready + MP4 moov validation” logic 

**In v2 these must be separated:**

1. **Transport layer:** `TelegramFileDownloadManager`

* start/cancel downloads, observe `fileUpdates`, remoteId→fileId resolution, storage stats/optimize
* no MP4 parsing, no playback assumptions

2. **Playback layer:** `TelegramFileReadyEnsurer` (or integrate into `TelegramFileDataSource`)

* calls transport downloads
* waits for prefix
* validates MP4 header (moov atom) before allowing playback start (offset=0 path)

This matches the “Playback lives in playback/*, transport stays raw” boundary.  

---

## 3. Step-by-step migration checklist (do in this order)

## Phase A — Pre-flight (quality + safety)

* [ ] Confirm working on `architecture/v2-bootstrap` (or v2-derived feature branch); never target `main`. 
* [ ] Read module READMEs for all touched modules (transport-telegram, playback/telegram, pipeline/telegram). 
* [ ] Run naming guard:

  * [ ] grep: forbid `com.chris.m3usuite` outside legacy
  * [ ] ensure packages follow Glossary patterns 
* [ ] Logging guard:

  * [ ] grep: forbid `println`, `printStackTrace`, `android.util.Log`, `Timber` outside `infra/logging` 

## Phase B — Transport: replace legacy “service client” with DI-scoped v2 transport

### B1) Create/verify TDLib client ownership (single-process singleton via DI, not manual singleton)

* [ ] Ensure there is exactly one **DI-scoped** TDLib client provider in `infra/transport-telegram`.
* [ ] Port behaviors from legacy:

  * idempotent start (`ensureStarted`) pattern 
  * safe scope recreation after shutdown 
  * update distribution (`newMessageUpdates`, `fileUpdates`) stays transport-side, emitted as Flows 
* [ ] Remove **all** UI references from transport:

  * legacy calls a UI snackbar on reauth 
  * replace with: `TelegramAuthEvent.ReauthRequired` flow event (domain/UI decides presentation)

### B2) Migrate `T_TelegramSession` → `TdlibAuthSession`

* [ ] Port auth state collector + mapping to events 
* [ ] Keep `setTdlibParameters` logic here (transport responsibility) 
* [ ] Enforce “resume first” behavior:

  * if already authorized on boot → Ready without UI involvement (legacy already tries to query initial auth state) 
* [ ] Implement the accepted auth policy:

  * offline → pause retries
  * on reconnection → resume
  * if TDLib state transitions from Ready back to WaitPhone/WaitCode/WaitPassword → emit `ReauthRequired` 

### B3) Migrate `T_ChatBrowser` → `TelegramChatBrowser`

* [ ] Preserve TDLib paging rule used in `loadAllMessages()`:

  * first page: `fromMessageId=0`, `offset=0`
  * next pages: anchor on oldest msg id, `offset=-1` to avoid duplicates 
* [ ] Replace “limit=100 top chats” with **pager-based enumeration**:

  * keep TDLib list loading incremental, but never enforce an arbitrary app-level cap
* [ ] Add “active chat selection” logic:

  * no bot chats
  * keep “library chats” once identified (use persisted chat profile / media-density classification; do not rely solely on lastMessage timestamp)
* [ ] Ensure retry/backoff uses the agreed policy (see Migration Contract below), not ad-hoc sleeps.

## Phase C — Transport: file download manager (no playback logic inside)

### C1) Migrate download queueing + concurrency enforcement

Legacy implements FIFO queues + counters + two categories (VIDEO/THUMB) 

* [ ] Port to `TelegramFileDownloadManager` in `infra/transport-telegram/file/`
* [ ] Replace “settingsProvider runtime sliders” (legacy) 
  with **fixed constants** (no user overrides) + optional debug-only override hook.
* [ ] Keep remoteId→fileId resolution API (required for stale fileId recovery) 
* [ ] Keep storage maintenance hooks:

  * `getStorageStatisticsFast` → check size
  * `optimizeStorage` on threshold 

### C2) Remove legacy-only windowing/ringbuffer remnants (do not reintroduce)

* [ ] Do **not** port deprecated windowing APIs/structures 
* [ ] Do **not** port ringbuffer constants 

### C3) Logging compliance fixes

* [ ] Remove any `println` usage (exists in legacy downloader)  
* [ ] Standardize to `UnifiedLog.d/i/w/e` per logging contract 

## Phase D — Playback: streaming readiness + MP4 validation

### D1) Consolidate streaming config

* [ ] Create `playback/telegram/config/TelegramStreamingConfig.kt`
* [ ] Merge and keep **only** the refactor constants as SSOT:

  * prefix thresholds
  * polling intervals
  * ensure-ready timeouts
  * priorities 32/16 
* [ ] Explicitly delete/avoid legacy “window config” constants 

### D2) Move MP4 moov validation into playback

Legacy validates moov atom before playback start 

* [ ] Implement `TelegramMp4Validator` (or reuse existing) in `playback/telegram`
* [ ] Implement `TelegramFileReadyEnsurer` in `playback/telegram`:

  * start progressive download: `offset=0, limit=0, priority=32`
  * poll `downloaded_prefix_size`
  * validate moov completeness before returning “ready” for offset=0
  * seek path: ensure minimum read-ahead bytes (no moov validation needed)

### D3) RemoteId-first playback support

* [ ] Ensure playback URI / context always contains `remoteId` as stable identity
* [ ] On “file not found / stale fileId” → resolve via remoteId and retry (legacy already does this) 

## Phase E — Imaging: Coil3 + Telegram thumb resolution without UI → TDLib calls

### E1) Replace `TelegramFileLoader` with resolver + Coil integration

Legacy `TelegramFileLoader` performs thumb downloads + prefetch, with service readiness checks 

* [ ] Create `TelegramImageFileResolver` in `infra/transport-telegram/imaging/`

  * input: `TelegramImageRef` (remoteId-first)
  * output: local cache path or null
  * uses transport download manager + remoteId fallback 
* [ ] Wire it into Coil3 via ImageLoader component registration (app-v2 startup builds global ImageLoader)
* [ ] Keep “don’t spam logs on repeated 404 remoteIds” behavior (bounded LRU set) 

## Phase F — Pipeline/catalog integration (verification only, no layer violations)

* [ ] Confirm `pipeline/telegram` imports **no** TDLib classes (`dev.g000sha256.tdl.*`) per agent rules 
* [ ] Confirm pipeline still produces only `RawMediaMetadata` and does not normalize titles 
* [ ] Confirm chat selection logic implements:

  * no bot chats
  * active chats prioritized
  * “library chats” retained once classified (media-density profile)

## Phase G — Global backfill orchestration compliance (core/catalog-sync + player signals)

* [ ] Verify a single “BackfillScheduler” exists above pipelines (core/catalog-sync or similar), not inside pipelines.
* [ ] Enforce playback policy:

  * while playing → pause heavy backfill workers globally
  * resume after 3s debounce post stop/pause
* [ ] Idle signals include: playback, app visibility, network, battery saver.

---

## 4. Required tests (minimum set)

### Transport tests

* [ ] Auth state mapping tests (Ready → WaitCode triggers `ReauthRequired`) 
* [ ] Chat history pagination tests:

  * offset handling (0 then -1) 
* [ ] Retry/backoff tests (exponential delay schedule, max attempts)
* [ ] Download manager tests:

  * queue fairness (FIFO) 
  * concurrency caps (global/video/thumb) 
  * remoteId resolution fallback path 

### Playback tests

* [ ] MP4 validator tests: moov complete / incomplete / not found behavior  
* [ ] “seek path” ensures read-ahead bytes without moov validation

### Imaging tests

* [ ] remoteId-first thumb resolution:

  * cached fileId fails → remoteId resolves → download succeeds 
* [ ] bounded “logged 404 remoteIds” does not grow unbounded 

---

## 5. Mandatory verification commands (CI-style)

* [ ] **Layer boundary audit** (must be clean) 

  * pipeline must not import transport internals directly unless through the transport API module boundary.
* [ ] Forbidden imports:

  * [ ] `grep -R "com.chris.m3usuite" -n -- */ | grep -v "^legacy/"`
* [ ] Forbidden logging:

  * [ ] `grep -R "println\\(|printStackTrace\\(|android.util.Log|Timber" -n app-v2 core infra feature pipeline playback player | cat`
* [ ] Run: `./gradlew detekt` and ktlint task (if configured)  

---

## 6. Dependency freshness (do not blindly upgrade; propose + pin)

These are the **current upstream states** worth knowing:

* **Media3:** stable **1.8.0** (Dec 1, 2025), with 1.9.0 in RC/beta ([Android Developers][1])
* **OkHttp:** 5.3.x is out (Nov 2025) ([mvnrepository.com][2])
* **Coil 3:** 3.3.0 released July 2025 ([coil-kt.github.io][3])
* **tdl-coroutines:** Android artifact appears at 1.2.0 on Maven Central (check if your repo pins this), while some KMP targets show newer lines ([Libraries][4])

**Rule for this migration:** don’t upgrade dependencies inside the migration PR unless explicitly requested; instead add a follow-up “deps refresh” task.

---

## 7. Recommended external tools (high leverage for best quality)

* **Detekt + ktlint** as hard gates (already aligned with your glossary enforcement plan). 
* **Gradle Doctor** + **Gradle Build Scan** to catch misconfiguration + dependency bloat early.
* **LeakCanary** (TDLib + player + WorkManager = classic leak trap).
* **Perfetto / Android Studio System Trace** for backfill-vs-playback contention.
* **Macrobenchmark + Baseline Profiles** once feature screens go live (Phase 4/5 area). 



[1]: https://developer.android.com/jetpack/androidx/releases/media3?utm_source=chatgpt.com "Media3 | Jetpack - Android Developers"
[2]: https://mvnrepository.com/artifact/com.squareup.okhttp3/okhttp?utm_source=chatgpt.com "com.squareup.okhttp3 » okhttp"
[3]: https://coil-kt.github.io/coil/changelog/?utm_source=chatgpt.com "Change Log - Coil"
[4]: https://libraries.io/maven/dev.g000sha256%3Atdl-coroutines-macosarm64?utm_source=chatgpt.com "dev.g000sha256:tdl-coroutines-macosarm64 5.0.0 on Maven"

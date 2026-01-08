1) Premium Contract: /docs/v2/XTREAM_SCAN_PREMIUM_CONTRACT_V1.md

Version: 1.0
Date: 2025-12-22
Status: Binding
Scope: Xtream authentication + prioritized GET flows + timeouts + concurrency + headers + layering

0. Non-Negotiable Layer Rules

Transport lives in infra/transport-xtream: HTTP, URL building, headers, timeouts, concurrency primitives.

Pipeline lives in pipeline/xtream: scan plan + mapping to RawMediaMetadata, no normalization, no TMDB calls.

Sync orchestration lives in core/catalog-sync: consumes pipeline events, normalizes centrally, persists via repositories.

Workers live in infra/work / app-v2/.../work: execution only; never call transport directly; call CatalogSyncService.

1. Credential & Session SSOT
X-1 Credentials SSOT

Xtream credentials MUST be stored only in the source/session store (SSOT), and injected into transport/pipeline via DI.
They MUST NOT be embedded in logs or persisted in plain text.

X-2 Activation Gate

XtreamSessionBootstrap (AppShell) MUST perform a lightweight auth preflight and set SourceActivation state:

ACTIVE only when server_info indicates auth success

ERROR with reason otherwise

2. Priority Endpoint Plan (Script-equivalent, App-adapted)

This ordering is mandatory:

X-10 Preflight (Auth/Server Info)

Transport calls (infra/transport-xtream):

player_api.php (server_info + user_info)

panel_api.php (optional diagnostics, but MUST exist as endpoint wrapper)

Where:

Implement in XtreamApiClient and wire through XtreamSessionBootstrap.

Purpose: fast fail, validate auth, capture server constraints (timezone, ports).

X-11 Core Catalog Scan (No categories used for UI)

Pipeline scan order (pipeline/xtream):

get_live_streams (all)

get_vod_streams (all)

get_series (all)

get_series_info per series (bounded & batched) to obtain episodes

get_vod_info per VOD (bounded & batched) ONLY if required for missing essential fields

Note: Provider categories MAY be fetched for segmentation fallback, but MUST NOT drive UI taxonomy.

X-12 EPG (separate domain)

EPG MUST NOT be part of the Xtream catalog scan chain.
EPG is its own system (epg_sync_global) and uses get_epg as the primary source.

3. HTTP Timeouts (Premium)

These values MUST be configured in the OkHttpClient provider in infra/transport-xtream:

connectTimeout: 30s

readTimeout: 30s

writeTimeout: 30s

callTimeout: 30s (mandatory)

Rationale: Script uses --max-time 30, so the app must match “hard stop in ~30s” semantics.

4. Headers / UA (Premium)

Every Xtream HTTP request MUST include:

Accept: application/json

Accept-Encoding: gzip

User-Agent: FishIT-Player/2.x (Android) (mandatory)

Optional (provider-specific): Connection: keep-alive (OkHttp default)

Where:
Transport-level interceptor (OkHttp) or per-request builder in DefaultXtreamApiClient, but it must be consistent and centralized (not ad-hoc per endpoint).

5. Concurrency / Parallelism (Premium)
X-20 Concurrency SSOT

Parallelism must be controlled centrally in transport via:

OkHttp Dispatcher limits and coroutine Semaphore limits in the client.

X-21 Device class clamps

Phone/Tablet: target parallelism = 12

FireTV/low-RAM: target parallelism = 3

X-22 Where “MAX_PARALLEL=12” belongs

OkHttp dispatcher: maxRequests, maxRequestsPerHost

Coroutine permits: Semaphore(parallelism)

NOT in UI, NOT in pipeline

6. Large payload safety

For giant payload endpoints (get_vod_streams, get_live_streams, get_series):

parsing MUST be streaming-friendly (no duplicate buffers)

persistence must be “as-you-go” (CatalogSyncService)

no payload dumps in logs (counts only)

7. Category segmentation fallback (Premium)

If provider limits get_*_streams (timeouts/partial data), pipeline MUST fallback deterministically:

fetch categories

scan per category

still produce the same RawMediaMetadata mapping

still no UI categories used

8. Endpoint coverage requirements

Transport must expose wrappers for these endpoints at minimum:

player_api.php (base)

panel_api.php

action=get_live_categories|get_vod_categories|get_series_categories

action=get_live_streams|get_vod_streams|get_series

action=get_vod_info

action=get_series_info

action=get_short_epg

action=get_epg

xmltv.php (optional, bounded fetch later)

9. Logging (UnifiedLog only)

Transport logs only high-level events and endpoint timing (no credentials)

Pipeline logs scan phases and counts

Workers log START/PROGRESS/CHECKPOINT/SUCCESS/FAILURE
**Parallel Persistence Strategy (Issue #609 - Added Jan 2026):**

Final batch flush uses parallel persistence for maximum speed:

- Live/VOD/Series batches persist simultaneously (not sequentially)
- Device-aware parallelism limits:
  - Phone/Tablet: max 3 concurrent persist operations
  - FireTV/Low-RAM: max 2 concurrent persist operations (conservative for RAM)
- Uses Dispatchers.IO for database operations
- Structured concurrency (coroutineScope) ensures proper cancellation
- Thread safety: Each batch type writes to different repositories (no shared state)
  - Live → XtreamLiveRepository
  - VOD/Series → XtreamCatalogRepository
- Performance benefit: ~2-3x faster initial sync
  - Sequential: Live(8s) + VOD(4s) + Series(5s) = 17s
  - Parallel (max 3): max(8s, 4s, 5s) = 8s (~2.1x speedup)
- No deadlocks or race conditions (independent write targets)

Implementation: DefaultCatalogSyncService.persistXtreamBatchesParallel()


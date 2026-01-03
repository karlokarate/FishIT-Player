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

Phone/Tablet: target parallelism = 10

FireTV/low-RAM: target parallelism = 3

X-22 Where “MAX_PARALLEL=10” belongs

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
## 10. Series Episode Playback Strategy

### X-30 Series Episode URL Format

**Direct Episode ID Path (PRIMARY):**
```
/series/{username}/{password}/{episodeId}.{ext}
```

**Index-Based Path (FALLBACK):**
```
/series/{username}/{password}/{seriesId}/{seasonIndex}/{episodeIndex}.{ext}
```

**Critical Requirements:**
- Series episodes MUST use `/series/` path (NOT `/movie/` or `/vod/`)
- Episode ID (episodeId) takes precedence over index-based path when available
- Episode ID is the streamable identifier from provider API (stream_id field)
- Container extension MUST be preserved from provider metadata (SSOT)

**Why Direct Episode ID:**
- Proven by legacy traffic analysis
- Provider responds with HTTP 302 redirect to CDN/tokenized URL
- More reliable than index-based path which can fail with provider variations

### X-31 HTTP Redirect Handling

**OkHttp Configuration (Transport):**
```kotlin
OkHttpClient.Builder()
    .followRedirects(true)         // Follow 301/302/307/308
    .followSslRedirects(true)      // Allow HTTP → HTTPS
```

**ExoPlayer DataSource (Playback):**
```kotlin
XtreamHttpDataSourceFactory:
    - Uses OkHttpDataSource with redirect-capable client
    - Preserves headers across redirects
    - Tested with XtreamHttpRedirectTest
```

**Expected Flow:**
1. Request: `GET /series/user/pass/638139.mp4`
2. Response: `302 Found` with `Location: https://cdn.provider.com/token/video.mp4`
3. Follow: `GET https://cdn.provider.com/token/video.mp4`
4. Response: `200 OK` with video stream

### X-32 Credential Sanitization

**Problem:**
- User credentials may contain whitespace (spaces, tabs, newlines)
- Copy-paste from PDFs, emails, or web forms introduces artifacts
- Whitespace in URL path segments causes malformed requests (404/400)

**Solution:**
```kotlin
XtreamApiConfig.sanitizeCredential(rawCredential: String): String
    - Trim leading/trailing whitespace
    - Remove ALL internal whitespace (\s+ → "")
    - Examples:
        " user\n " → "user"
        "pass word" → "password"
```

**Application Points (ALL construction paths):**
1. User input: `XtreamAuthRepositoryAdapter.toTransportConfig()`
2. Storage read: `XtreamStoredConfig.toApiConfig()`
3. URL parsing: `XtreamUrlBuilder.parseCredentials()`
4. URL parsing: `XtreamUrlBuilder.parsePlayUrl()`
5. M3U parsing: `XtreamApiConfig.fromM3uUrl()`

**Validation:**
- `XtreamApiConfig.init` performs defensive check
- Throws `IllegalArgumentException` if whitespace detected
- Prevents silent failures downstream in URL building

### X-33 Container Extension Handling

**Series Episodes (File-Based):**
- Extension is container format: mkv, mp4, avi, mov, wmv, flv, webm
- NEVER streaming formats: m3u8, ts
- Provider's container_extension field is SSOT
- Fallback: mkv (if container_extension missing)

**Rejection:**
```kotlin
// INVALID for series:
/series/user/pass/12345.m3u8  // ❌ Streaming format
/series/user/pass/12345.ts    // ❌ Streaming format

// VALID for series:
/series/user/pass/12345.mp4   // ✅ Container format
/series/user/pass/12345.mkv   // ✅ Container format
```

**Live/VOD (Adaptive Streams):**
- Extension may be streaming format: m3u8, ts
- Or container format: mp4, mkv
- Provider's allowed_output_formats or container_extension is SSOT

### X-34 Episode Stream ID Mapping

**Pipeline Flow:**
```
XtreamEpisode.id → PlaybackHintKeys.Xtream.EPISODE_ID → episodeId parameter
```

**Provider Variations:**
- Field name varies: stream_id, episode_id, id
- Pipeline adapter extracts correct field
- Maps to `RawMediaMetadata.playbackHints[EPISODE_ID]`
- Playback layer reads from hints for URL construction

**Series vs Episode IDs:**
- `seriesId`: Container ID (for series detail fetch)
- `episodeId`: Streamable ID (for playback URL)
- Do NOT confuse the two

### X-35 Testing Requirements

**Mandatory Tests:**
1. URL format validation (episodeId priority)
2. Credential sanitization (whitespace removal)
3. HTTP redirect following (302 → 200)
4. Extension validation (reject m3u8/ts for series)
5. Fallback behavior (index-based when episodeId missing)

**Test Files:**
- `XtreamSeriesPlaybackTest.kt` - URL construction
- `XtreamCredentialSanitizationTest.kt` - Credential cleaning
- `XtreamHttpRedirectTest.kt` - Redirect handling

**Coverage:**
- Unit tests for all URL building paths
- Unit tests for all credential sanitization paths
- Integration tests for redirect handling
- Negative tests for invalid inputs

### X-36 Security Logging

**Credential Redaction:**
```kotlin
UnifiedLog.d(TAG) { "has credentials: ${hasUsername && hasPassword}" }
// ✅ Log boolean flags only

UnifiedLog.d(TAG) { "username: $username" }
// ❌ NEVER log credentials directly
```

**URL Redaction:**
```kotlin
fun String.redactCredentials(): String
    // /live/user/pass/123 → /live/***/****/123
```

**Test Logs:**
- No credentials in test snapshots
- Use sanitized test credentials
- Redact any real credentials in logs

### X-37 Implementation Status

**Completed:**
- ✅ Credential sanitization at all entry points
- ✅ Direct episodeId URL format in XtreamUrlBuilder
- ✅ Redirect handling in OkHttp/ExoPlayer
- ✅ Comprehensive test suite (32 credential tests + series tests)
- ✅ Extension validation (container vs streaming)

**Verified:**
- ✅ Episode ID flows from pipeline → playback
- ✅ Fallback to index-based path when episodeId missing
- ✅ `/series/` path enforced (not `/movie/` or `/vod/`)
- ✅ All tests pass

---

**Last Updated:** 2026-01-03  
**Implementation:** FishIT-Player v2 (architecture/v2-bootstrap branch)  
**Status:** Complete & Tested

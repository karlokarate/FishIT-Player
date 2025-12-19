Version: 1.0
Date: 2025-12-19
Status: Binding
Scope: Xtream EPG ingestion, normalization, storage, background sync, UI consumption

0. Non-Negotiable Principles
EPG-0 — EPG is a separate time-series domain

EPG is not part of RawMediaMetadata. EPG data is stored and queried as time-series events.

EPG-1 — Strict layering

Transport fetches raw EPG DTOs.

Pipeline (if used) emits raw EPG items only (no decoding/normalization).

EPG Normalization happens centrally (Base64 decode + time normalization + stable key).

Persistence is ObjectBox-based and idempotent.

UI reads only normalized/persisted EPG, never raw.

EPG-2 — No provider categories

Provider categories are never used for EPG UI navigation. EPG UI is time-based: Channel → Timeline → Events.

EPG-3 — Unified logging only

All logs use UnifiedLog only.

1. Supported Xtream EPG Endpoints (Binding)
EPG-10 — Primary endpoint

The system MUST treat Xtream get_epg as the primary canonical source.

EPG-11 — Optional endpoints

get_short_epg MAY be used only as an optimization for “Now/Next” refresh.

get_simple_data_table MUST NOT be used as a primary EPG source; it MAY be used only for explicit catchup/legacy fallback scenarios.

2. Raw Input Contract (Transport-Level)
EPG-20 — Raw Xtream EPG listing DTO

Raw fields may include:

id (event instance id, NOT stable)

epg_id (provider internal, NOT stable)

channel_id (string-based, e.g. 3sat.de)

title (Base64)

description (Base64)

lang

start, end (ISO string)

start_timestamp, stop_timestamp (epoch seconds)

now_playing, has_archive

EPG-21 — Timestamp preference

Normalization MUST treat epoch seconds (start_timestamp, stop_timestamp) as authoritative when present. ISO strings are debug-only.

3. Normalized EPG Model (Binding)
EPG-30 — Canonical channel identity

A channel must be referenced by a stable CanonicalChannelId derived from source channel id:

For Xtream: CanonicalChannelId = "xtream:<providerKey>:<channel_id>"
(where providerKey is your internal Xtream account/profile key)

EPG-31 — Normalized EPG event

Normalized event MUST contain:

channelId: CanonicalChannelId

startUtc: Instant (epoch millis in storage)

endUtc: Instant

title: String (Base64 decoded)

description: String? (Base64 decoded or null)

language: String? (normalized, lower-case, optional)

hasCatchup: Boolean

isNowPlaying: Boolean

source: EpgSource = XTREAM

sourceEventId: String? (raw id)

sourceEpgId: String? (raw epg_id)

epgKey: String (stable hash)

EPG-32 — Stable event key

Because Xtream ids are not stable, the system MUST generate a stable key:

epgKey = sha256(channelId + startEpochSec + stopEpochSec + decodedTitle)

4. Mandatory Normalization Rules (Binding)
EPG-40 — Base64 decoding

title and description MUST be Base64-decoded centrally.

Invalid Base64 MUST NOT crash the system.

On decode failure: set field to null and mark failure reason in logs (DEBUG) and event diagnostics.

EPG-41 — Time normalization

Store timestamps internally as UTC epoch millis.

Do not store ISO strings except optional debug fields (not required).

EPG-42 — Overlap & validity

endUtc MUST be > startUtc else event MUST be dropped as invalid.

5. Persistence (ObjectBox) (Binding)
EPG-50 — Bucketed storage

Events MUST be stored by:

(channelId, dayBucketUtc) where dayBucketUtc is UTC date (00:00 boundary)

EPG-51 — Retention

Default retention MUST be bounded:

Keep N=14 days (configurable later, but bounded by default)

EPG-52 — Idempotent upsert

Upsert key MUST be (channelId, epgKey).

6. Background Sync (Binding)
EPG-60 — Unique work name

EPG sync MUST run under:

uniqueWorkName = "epg_sync_global"

default KEEP, expert REPLACE

EPG-61 — Batching (FireTV safe)

Fetch/store in bounded batches.

No unbounded in-memory accumulation.

Persist after each batch.

EPG-62 — Scheduling frequency

Default: run on demand (user enters Live screen) + periodic refresh (e.g., every 6h) may be added later.

Must respect network constraints and battery guards similarly to catalog sync.

7. UI Consumption (Binding)
EPG-70 — UI must not decode/normalize

UI MUST NOT:

Base64 decode

parse timestamps

re-key events

UI reads from repository:

observeChannelTimeline(channelId, fromUtc, toUtc).
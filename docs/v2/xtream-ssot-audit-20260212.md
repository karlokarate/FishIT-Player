# Xtream SSOT / Inline-Logic Audit (2026-02-12)

> Scope: pipeline/xtream, infra/transport-xtream, playback/xtream, infra/data-xtream, core/catalog-sync (xtream).  
> Method: All referenced files were manually read (no docs-only inference).

## Confirmed SSOTs (no action needed)
- **ID codecs:** `core/model/ids/XtreamIdCodec.kt` (canonical) with legacy typealias in `pipeline/xtream/ids/XtreamIdCodec.kt`.
- **HTTP headers:** `infra/transport-xtream/XtreamHttpHeaders.kt` (API vs playback headers).
- **Transport config:** `infra/transport-xtream/XtreamTransportConfig.kt` (timeouts, UA, Accept).
- **Category fetcher:** `infra/transport-xtream/client/XtreamCategoryFetcher.kt` (alias resolution).
- **JSON mappers:** `infra/transport-xtream/mapper/*` (stream parsing).

## Duplicate / Split Responsibilities (should be consolidated to single SSOT)
1) **URL-building hints persisted in pipeline**
   - `pipeline/xtream/mapper/XtreamRawMetadataExtensions.kt`: playbackHints write `VOD_KIND`, `LIVE_KIND`, `SERIES_KIND` (URL/transport concern).
   - Expected layer: transport/playback (URL construction SSOT), not pipeline RawMediaMetadata.

2) **Duration & year parsing scattered**
   - `XtreamRawMetadataExtensions.kt`: `parseDurationToMs`, year validation/extraction across VOD/Series/Episode.
   - `XtreamPipelineAdapter.toPipelineItem` (series): applies `resolvedYear`; episodes map `durationSecs` vs `duration`.
   - Missing single SSOT for duration/year parsing in transport; parsing should not live in pipeline adapter + raw mappers.

3) **Image resolution logic duplicated**
   - `XtreamImageRefExtensions.kt` (poster/backdrop/thumbnail/logo) and `XtreamPipelineAdapter.toPipelineItem` uses `resolvedPoster` / `resolvedCover`.
   - Risk: changes to URL resolution must be updated in two places. SSOT should live in transport (URL resolution) or single mapper.

4) **Category fallback logic split**
   - `infra/transport-xtream/strategy/CategoryFallbackStrategy.kt` (fallback * → 0 → null).
   - Pipeline VOD phase (`pipeline/xtream/catalog/phase/VodItemPhase.kt`) applies its own category handling when filters exist.
   - Recommend reusing transport strategy for all phases to avoid divergence.

5) **Episode duration resolution duplicated**
   - `XtreamPipelineAdapter.toEpisodes` uses `durationSecs`; `XtreamRawMetadataExtensions.toRawMediaMetadata` also recomputes duration with `durationSecs` fallback to `parseDurationToMs`.
   - Centralize duration precedence once (transport or mapper SSOT).

## Inline / Forbidden Logic in Pipeline (should move down or be removed)
1) **Normalization-like logic in transport→pipeline adapter**
   - File: `pipeline/xtream/adapter/XtreamPipelineAdapter.kt`
   - Issues:
     - Epoch seconds → milliseconds conversions in `toPipelineItem()` (VOD/Series/Live).
     - Rating normalization: `rating5Based * 2.0` fallback for series.
     - Adult flag parsing `"1" == adult`.
   - These belong in transport DTO mapping; pipeline should receive already-normalized primitives.

2) **Business logic inside RawMetadata mapping**
   - File: `pipeline/xtream/mapper/XtreamRawMetadataExtensions.kt`
   - Issues:
     - Duration parsing (`parseDurationToMs`) and year validation (filters invalid/0/N/A).
     - Playback hints carry transport URL parameters (`*_KIND`, `DIRECT_SOURCE`) that are not raw catalog data.
     - Rating fallback for VOD detail (`rating5Based * 2`).
   - Per pipeline contract, raw mappers should pass through source data without normalization or transport URL state.

## Bug / Risk Findings
- **Persistence of transport-only hints:** Playback hints storing `*_KIND` values risk becoming stale when URL patterns change; should be computed by transport/playback factories.
- **Dual duration computation (episodes):** Duration might diverge between adapter and raw mapper, leading to inconsistent resume/UX.
- **Category fallback divergence:** Pipeline phase logic may skip transport fallback strategy, producing mismatched item counts per category.

## Suggested Consolidations (non-breaking outline)
- Create transport-level SSOT for duration/year parsing and reuse in adapter + raw mapper.
- Move stream-kind/URL-building hints to transport/playback factories; keep raw metadata free of transport concerns.
- Centralize image URL resolution in transport mapper; pipeline should only wrap into ImageRef once.
- Ensure pipeline phases reuse `CategoryFallbackStrategy` instead of ad-hoc handling.

## Files Reviewed (manual)
- `pipeline/xtream/mapper/XtreamRawMetadataExtensions.kt`
- `pipeline/xtream/mapper/XtreamImageRefExtensions.kt`
- `pipeline/xtream/adapter/XtreamPipelineAdapter.kt`
- `pipeline/xtream/catalog/phase/VodItemPhase.kt`
- `infra/transport-xtream/strategy/CategoryFallbackStrategy.kt`
- `core/catalog-sync/sources/xtream/DefaultXtreamSyncService.kt`
- Supporting ID/header/config/mappers in `infra/transport-xtream` (see SSOT list)

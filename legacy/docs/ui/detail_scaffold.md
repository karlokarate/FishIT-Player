> LEGACY (V1) – historical document  
> Not valid for v2. For current architecture see `V2_PORTAL.md` and `docs/v2/**`.

# DetailScaffold (v1)

Purpose
- Unified header for detail screens (VOD/Series/Live) with hero backdrop + scrim, poster, title, MetaChips, and MediaActionBar.
- Reduces layout duplication and ensures consistent DPAD focus (ActionBar gets initial focus on TV).

Package
- `ui/detail/`
  - `DetailScaffold.kt` — LazyColumn wrapper that renders `DetailHeader` as the first item and then `content` items
  - `DetailHeader.kt` — Hero + Poster + Title + MetaChips + MediaActionBar (+ optional `headerExtras{}`)
  - `MetaChips.kt` — `DetailMeta` model + chips with compact/expanded layout
  - `HeroScrim.kt` — shared hero image with top/bottom gradient scrim

Flags
- `BuildConfig.DETAIL_SCAFFOLD_V1` (default ON). Screens can opt‑in and keep legacy header as fallback.

Relation to Fish*
- Fish* (tiles/rows) centralizes list/grid visuals outside details. Detail screens continue to use the scaffold + MediaActionBar.
- Tile appearance (tokens, focus, content) in list views is independent from the detail scaffold.

Usage patterns
- VOD/Series: Use `DetailHeader(...)` as the first item of the screen’s LazyColumn (or `DetailScaffold` as wrapper) and pass `content` items below.
- Live: Same approach; include OpenEPG in the actions. Keep legacy blocks disabled under the flag to avoid duplicates.

Current migration (v1)
- VOD: Header migrated to `DetailHeader` under flag; legacy header gated off when enabled.
- Series: Header migrated to `DetailHeader` under flag; legacy header gated off when enabled. Per‑episode rows use MediaActionBar.
- Live: Actions already unified; header migration is prepared and will follow as an incremental step.

Focus & A11y
- `MediaActionBar(..., requestInitialFocus = true)` focuses the first action on TV.
- Poster is decorative in the header (no contentDescription). The hero image uses `contentDescription=null`.

Meta mapping
- `DetailMeta(year, durationSecs, videoQuality, hdr, audio, genres, provider, category)`
- Chips render non‑null fields and wrap nicely on TV.

# Unified Tiles & Rows (Fish*)

Status
- The legacy Cards v1 (`PosterCard`, `ChannelCard`, `SeasonCard`, `EpisodeRow`) are being retired.
- New single source of truth lives under `ui/layout/*` (Fish*). Screens should compose rows and tiles only from these modules.

Modules
- Tokens — `ui/layout/FishTheme.kt`
  - `FishDimens`: `tileWidthDp`, `tileHeightDp`, `tileCornerDp`, `tileSpacingDp`, `contentPaddingHorizontalDp`, `focusScale`, `focusBorderWidthDp`, `reflectionAlpha`, `enableGlow`, `showTitleWhenUnfocused`.
  - Apply once via `FishTheme { ... }` (global/screen/row scope). Tiles/rows read tokens via `LocalFishDimens`.

- Tile — `ui/layout/FishTile.kt`
  - Unified visuals for all content types: ContentScale.Fit, rounded corners, reflection, focus scale + frame + (optional) glow; selection frame when needed.
  - Slots: `topStartBadge`, `bottomEndActions`, `footer`; no per‑tile bring‑into‑view.

- Row — `ui/layout/FishRow.kt`
  - `FishRowLight` (simple), `FishRow` (media engine), `FishRowPaged` (paging). Fixed spacing & padding from tokens; header is optional.
  - DPAD/edge‑left behavior and paging handled centrally via FocusKit.

- Content — `ui/layout/FishVodContent.kt`, `FishSeriesContent.kt`, `FishLiveContent.kt`
  - Compose the data/slots for FishTile per type (title, image source, resume/epg, badges, actions, click routing, focus logging).

Helpers
- `FishMeta.kt` — display title (+year), poster picking, plot footer, resume fraction helper.
- `FishActions.kt` — Assign (+/✓ badge, bottom‑right assign), Play (bottom‑right). Buttons are non‑focusable (DPAD stays on tiles).
- `FishLogging.kt` — focus logs with OBX lookup (e.g., VOD).
- `FishResumeTile.kt` — generic resume card (VOD/Series episodes).

Migration
- Replace `PosterCard`/`ChannelCard`/`PosterCardTagged` with `FishTile` + appropriate FishContent and badges/actions.
- Remove `BuildConfig.CARDS_V1` gates while porting; do not wrap old Cards around FishTile.
- Ratios: FishTile uses ContentScale.Fit; size/spacing/corners come from tokens.

Notes
- Details (VOD/Series/Live) use the detail scaffold and action bar; Fish* focuses on list/grid tiles and rows.

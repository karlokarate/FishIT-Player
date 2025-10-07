# MediaActionBar & Action Model (v1)

Overview
- Centralized actions for detail screens with DPAD-friendly focus and consistent labels/test tags.
- Actions: Resume?, Play, Trailer?, Add/Remove (Live favorites), OpenEPG? (Live), Share?

API
- `MediaActionId` enum and `MediaAction` data model
- `MediaActionBar(actions)` composable renders a horizontal row using TV buttons
- `MediaActionDefaults.testTagFor(id)` returns stable tags like `Action-Play`

Usage
- Build a list of actions in screen logic and pass to `MediaActionBar`.
- Telemetry: OnClick handlers invoke `Telemetry.event("ui_action_*", attrs)` with route context.
- Gate by `BuildConfig.MEDIA_ACTIONBAR_V1` (default ON). Screens fall back to legacy chips/buttons when OFF.

Guidelines
- Order: Resume? → Play → Trailer? → Add/Remove → OpenEPG? → Share?
- Primary action uses filled TV button; others outlined/text.
- Provide accessible text labels; avoid icon-only primary actions.
- Add `badge` for small hints like resume time.

Migrated screens (v1)
- VodDetailScreen: Resume+Play+Trailer+Share
- LiveDetailScreen: Play+OpenEPG+Share (+Add/Remove favorites when allowed)
- SeriesDetailScreen: Play(+first episode) + Trailer

Notes
- Favorites currently apply to Live only (DataStore CSV). VOD/Series favorites can be added later.

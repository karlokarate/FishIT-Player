# PlaybackLauncher (v1)

Purpose
- Centralized orchestration of playback: internal/external selection via PlayerChooser, resume read, headers, and basic result reporting.

API
- `PlayRequest(type, mediaId?, url, headers, drm?, startPositionMs?, title?, subtitle?, mimeType?, seriesId?, season?, episodeNum?, episodeId?)`
- `PlayerResult` sealed (Completed/Stopped/Error)
- `@Composable rememberPlaybackLauncher(onOpenInternal: (PlayRequest)->Unit)` returns `PlaybackLauncher` with `suspend fun launch(req)`

Behavior
- Reads resume for VOD when `startPositionMs` is null (via `ResumeRepository.recentVod`).
- Delegates to `PlayerChooser.start`; internal path triggers `onOpenInternal` with normalized request.
- MIME guessed via `PlayUrlHelper.guessMimeType` if absent.
- Telemetry: emits `play.request` and `play.launch` with enriched metadata (seriesId/season/episode fields when present).

Flags
- `BuildConfig.PLAYBACK_LAUNCHER_V1` (default ON). Call-sites use the launcher when enabled and fall back to legacy code otherwise.

Migrated call-sites (v1)
- VodDetailScreen: ActionBar Play/Resume uses `PlaybackLauncher`.
- LiveDetailScreen: Play uses `PlaybackLauncher`.
- SeriesDetailScreen: Episode play uses `PlaybackLauncher`.
- ResumeCarousel: Modal “Intern” uses `PlaybackLauncher` for VOD + Series.

Notes
- Internal player persists resume periodically; external result reporting is best-effort (no strict position).
- Future extensions: pass series/episode metadata into request for richer telemetry and result handling.

Fish* relation
- FishVodContent can expose a bottom-end Play action in tiles; call sites wire `onPlayDirect` using `PlaybackLauncher` when enabled.
- Details remain the primary place for complete action sets and resume handling via `MediaActionBar`.

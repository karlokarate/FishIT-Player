---
applyTo: "**/player/**/*.kt"
---

# Player Component Guidelines

## Architecture
- The player is undergoing a modular refactor (see `docs/INTERNAL_PLAYER_REFACTOR_STATUS.md`)
- Use `PlaybackContext` for all playback sessions (Phase 1 complete)
- Use `PlaybackLauncher` for starting playback (gated by `PLAYBACK_LAUNCHER_V1`)

## Behavior Contract
- Follow `docs/INTERNAL_PLAYER_BEHAVIOR_CONTRACT.md` for resume and kids/screen-time behavior
- Resume: Only restore positions > 10 seconds; clear when remaining < 10 seconds
- LIVE content never resumes
- Kids gate: Block when quota <= 0

## Domain Layer
- `PlaybackContext` - High-level playback metadata
- `ResumeManager` - Resume position management
- `KidsPlaybackGate` - Kids mode and screen time enforcement

## TV Controls
- Use `TvKeyDebouncer` for seek operations to prevent endless scrubbing
- Provide focusable controls for DPAD navigation
- Test with real Fire TV/Android TV devices

## Telegram Playback
- Telegram URLs use `tg://file/<fileId>?chatId=...&messageId=...` scheme
- `TelegramDataSource` handles zero-copy streaming
- See `.github/tdlibAgent.md` for TDLib streaming specifications

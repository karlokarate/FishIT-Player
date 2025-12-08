> LEGACY (V1) – historical document  
> Not valid for v2. For current architecture see `V2_PORTAL.md` and `docs/v2/**`.

# Documentation Archive

This directory contains historical documentation that has been superseded by newer documents or is no longer actively maintained.

## Why These Files Were Archived (2025-11-20)

As part of the preparation for TDLib integration enhancement (Priority 1), we cleaned up legacy and redundant documentation to keep the repository focused on current work.

## TDLib/Telegram Documentation

**Current Single Source of Truth**: `.github/tdlibAgent.md`

All new Telegram/TDLib work must follow this document. The following legacy docs have been archived:

- `PHASE3_TELEGRAM_INTEGRATION.md` - Historical implementation notes (Phase 3)
- `TELEGRAM_INTEGRATION_IMPLEMENTATION.md` - Older implementation guide
- `TDLIB_INTEGRATION.md` - Superseded by tdlibAgent.md
- `TDLIB_QUICK_REFERENCE.md` - Superseded by tdlibAgent.md

**Still Active**: `tools/tdlib_coroutines_doku.md` (upstream API reference - preserved as requested)

## Implementation Summaries

These documents described completed features and have been archived for historical reference:

- `IMPLEMENTATION_SUMMARY.md` - TDLib Integration initial implementation
- `IMPLEMENTATION_SUMMARY_TV_UX.md` - TV-UX optimization work
- `PHASE3_IMPLEMENTATION_SUMMARY.md` - Phase 3 Telegram integration

## UI/UX Documentation

- `TV_UX_INTEGRATION_GUIDE.md` - TV optimization guide (info now in AGENTS.md)
- `COPILOT_TASK_HINTS.md` - Historical task hints
- `cards.md` - Superseded by `fish_layout.md` (Fish* system documentation)

## Accessing Archived Documentation

These files remain in the repository for historical reference. If you need information from them:

1. Check if the information is already in current documentation (AGENTS.md, ROADMAP.md, ARCHITECTURE_OVERVIEW.md)
2. Refer to `.github/tdlibAgent.md` for all Telegram/TDLib work
3. Consult this archive only for historical context

## Current Documentation Structure

For active documentation, see:

- **Main Guides**: `AGENTS.md`, `ROADMAP.md`, `CHANGELOG.md`, `ARCHITECTURE_OVERVIEW.md`
- **TDLib/Telegram**: `.github/tdlibAgent.md`
- **UI Systems**: `docs/fish_layout.md`, `docs/tv_forms.md`, `docs/ui_state.md`
- **Features**: `docs/detail_scaffold.md`, `docs/media_actions.md`, `docs/playback_launcher.md`
- **Migration Guides**: `docs/fish_migration_checklist.md`

# Telegram Reference Documentation

This directory contains organized Telegram reference files extracted from uploaded ZIP archives.

## Directory Structure

- `cli/` - TDLib CLI source files (extracted from `tdl_cli_source_only.zip`)
  - Contains Kotlin source files for TDLib CLI testing (`src/main/kotlin/tdltest/`)
  - Files: `ChatBrowser.kt`, `CliIo.kt`, `Config.kt`, `DebugLog.kt`, `Main.kt`, `TelegramSession.kt`

- `exports/` - TDLib export files (extracted from `tdl_exports.zip`)
  - Contains 398 JSON export files in `exports/` subdirectory
  - Each file represents chat/channel export data

## Origin

These files were extracted from:
- `docs/tdl_cli_source_only.zip` → `docs/telegram/cli/`
- `docs/tdl_exports.zip` → `docs/telegram/exports/`

Original ZIP files have been removed after extraction.

---
applyTo: "**/telegram/**/*.kt"
---

# Telegram Integration Guidelines

## Single Source of Truth
**`.github/tdlibAgent.md` is the authoritative reference for all Telegram/TDLib work.**

## Core Architecture
- Use `T_TelegramServiceClient` (Unified Telegram Engine) for all TDLib operations
- **Never** access `TdlClient` directly outside `telegram/core` package
- Use `T_TelegramSession` for auth state management
- Use `T_ChatBrowser` for chat and message browsing
- Use `T_TelegramFileDownloader` for file downloads

## Package Layout
- `telegram/core/` - Core TDLib wrappers (T_* classes)
- `telegram/config/` - Configuration loading
- `telegram/parser/` - Content parsing and heuristics
- `telegram/ui/` - ViewModels and UI components
- `telegram/work/` - Background sync workers
- `telegram/player/` - Media3 DataSource for playback
- `telegram/logging/` - Log repository

## Sync & Content
- Use `TelegramSyncWorker` for background sync (supports MODE_ALL, MODE_SELECTION_CHANGED)
- Use `TgContentHeuristics` for movie/series/episode classification
- Use `TelegramContentRepository` for persistent content storage (ObjectBox)

## Streaming
- URL scheme: `tg://file/<fileId>?chatId=...&messageId=...`
- Windowed zero-copy streaming with 16MB windows (see Section 9 of tdlibAgent.md)
- `TelegramDataSource` handles Media3 integration

## Testing
- Test parser heuristics with `TgContentHeuristicsTest`
- Test logging with `TelegramLogRepositoryTest`
- Use unit tests for parser and repository logic

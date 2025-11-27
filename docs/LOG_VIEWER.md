# Log Viewer Feature

## Overview
The Log Viewer shows the in-memory AppLog stream (live + bounded history) inside the app. It no longer parses log files from disk; all content comes from AppLog, which is controlled via the Settings master toggle and per-category chips.

## Data Source
- AppLog history (bounded ring buffer, 1,000 entries)
- Live updates emitted as AppLog events; the viewer updates automatically while open
- Entries contain level, category, message, and optional extras (rendered inline)

## Features
- Live + historical AppLog display with monospace text and selection for copy/paste
- Simple error/loading states
- Export writes the current buffer to a timestamped text file in `cacheDir` (`applog_*.txt`) for easy sharing/pull; no SAF dialog or file parsing needed

## Navigation
Settings → Telegram Tools → "Log Viewer" (route: `log_viewer`)

## Implementation
- `LogViewerViewModel`: collects `AppLog.history` as StateFlow, maps entries to display strings, and exposes loading/error state
- `LogViewerScreen`: Compose scaffold with back + export actions and a scrollable, selectable list of entries
- `LogExporter`: Best-effort writer that saves the current buffer to a cache file; export is enabled only when entries exist

## Usage Notes
- Enable runtime logging in Settings (master toggle + category chips) to see entries in the viewer
- Exports capture only the in-memory buffer; clear/rebuild history by toggling logging or restarting the app
- No file discovery or format parsing remains; AppLog is the single source of truth

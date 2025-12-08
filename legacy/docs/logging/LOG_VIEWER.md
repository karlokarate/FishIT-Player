> LEGACY (V1) – historical document  
> Not valid for v2. For current architecture see `V2_PORTAL.md` and `docs/v2/**`.

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

## Error Categories (Phase 8)

The following error categories are logged via AppLog with `bypassMaster = true` (always logged regardless of master toggle):

### PLAYER_ERROR
Logged when playback errors occur in PlaybackSession.

**Extras:**
| Key | Description |
|-----|-------------|
| `type` | Error type: Network, Http, Source, Decoder, Unknown |
| `code` | HTTP status code or network error code (if applicable) |
| `url` | URL that caused the error (for Http errors) |
| `mediaId` | Media ID being played |
| `positionMs` | Playback position at time of error |
| `durationMs` | Total duration of media |

### WORKER_ERROR
Logged when background workers (Xtream/EPG/DB) encounter failures.

**Extras:**
| Key | Description |
|-----|-------------|
| `worker` | Worker name: XtreamDeltaImportWorker, XtreamDetailsWorker, ObxKeyBackfillWorker |
| `exception` | Exception class name (e.g., SocketTimeoutException) |
| `cause` | Root cause class name, or "none" if no cause |

## Usage Notes
- Enable runtime logging in Settings (master toggle + category chips) to see entries in the viewer
- Exports capture only the in-memory buffer; clear/rebuild history by toggling logging or restarting the app
- No file discovery or format parsing remains; AppLog is the single source of truth
- Error categories (PLAYER_ERROR, WORKER_ERROR) bypass the master toggle and are always logged

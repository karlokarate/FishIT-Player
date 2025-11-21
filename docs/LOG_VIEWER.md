# Log Viewer Feature

## Overview
The Log Viewer provides an in-app interface to view, filter, and export log files stored in the app's internal storage.

## File Location
Log files are read from: `<app_internal_files_dir>/telegram_logs/*.txt`

Example path: `/data/data/com.chris.m3usuite/files/telegram_logs/`

## Supported Log Formats

The Log Viewer can parse multiple log formats:

### 1. Space-separated format (default)
```
2025-01-20T10:15:30.123Z INFO TelegramDataSource Starting data source initialization
2025-01-20T10:15:31.789Z DEBUG T_TelegramSession Session connecting to Telegram servers
2025-01-20T10:15:34.678Z ERROR T_TelegramSession Connection timeout occurred
```

Format: `<timestamp> <level> <source> <message>`

### 2. JSON format
```json
{"timestamp":1234567890,"level":"INFO","source":"TelegramDataSource","message":"Starting"}
```

### 3. Bracketed format
```
[TelegramDataSource] Starting data source initialization
[T_TelegramSession] Session connecting to Telegram servers
```

## Features

### File Selection
- Dropdown menu to select from available log files
- Files sorted by last modified date (newest first)
- Automatic selection of most recent file

### Filtering
- **Source Filter**: FilterChips for each unique source/tag found in logs
  - Click to toggle source on/off
  - Empty selection = all sources active
- **Text Search**: Real-time case-insensitive search across all log content

### Export
- Export current log file via Android Storage Access Framework (SAF)
- User chooses destination and filename
- Success/failure feedback via Snackbar

### Display
- Monospace font for readability
- Scrollable content
- Selectable text for copy/paste
- Shows first 10,000 lines (with notification if truncated)

### Refresh
- Manual refresh button in toolbar
- Reloads file list from disk

## Navigation

Access from Settings → Telegram Tools → "Log Viewer"

Navigation route: `log_viewer`

## Technical Implementation

### ViewModel (`LogViewerViewModel`)
- Manages file reading and parsing
- Maintains state via StateFlow
- Handles filtering logic
- Optimized for performance:
  - Regex patterns cached as companion object constants
  - Buffered reading to handle large files
  - 10,000 line limit to prevent memory issues

### UI (`LogViewerScreen`)
- Material3 Compose UI
- Integrates with Android SAF for export
- Snackbar feedback for user actions
- Empty state with helpful instructions

## Performance Considerations

- **Max Lines**: Limited to 10,000 lines per file to prevent memory issues
- **Buffered Reading**: Uses `BufferedReader` for efficient file I/O
- **Regex Optimization**: Patterns compiled once and reused
- **Efficient Filtering**: Streaming operations with lazy sequences

## Error Handling

- Directory creation failures
- File reading errors
- Export failures (with user notification)
- Large file warnings

## Example Usage

### Creating Test Log Files

To test the Log Viewer, create log files in the app's internal storage:

```kotlin
// In your app code
val logsDir = File(context.filesDir, "telegram_logs")
logsDir.mkdirs()

val logFile = File(logsDir, "telegram_${System.currentTimeMillis()}.txt")
logFile.printWriter().use { writer ->
    writer.println("2025-01-20T10:15:30.123Z INFO TelegramDataSource Starting initialization")
    writer.println("2025-01-20T10:15:31.456Z DEBUG TelegramDataSource Loading configuration")
    writer.println("2025-01-20T10:15:32.789Z ERROR T_TelegramSession Connection failed")
}
```

## Future Enhancements

Potential improvements:
- Auto-refresh option
- Log level filtering (INFO, DEBUG, ERROR, etc.)
- Date range filtering
- Log file size indicators
- Compression for large log exports
- Share logs via intent
- Dark/light syntax highlighting

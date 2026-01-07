# FishIT Pipeline MCP Server

Standalone MCP (Model Context Protocol) server for testing FishIT-Player pipelines with real Xtream and Telegram data.

## Architecture Decision

> **Why standalone JVM instead of reusing existing modules?**

The existing FishIT-Player modules (`infra/transport-xtream`, `pipeline/telegram`, etc.) are **Android-specific** - they use:
- `android.os.SystemClock` for timing
- `android.content.Context` for storage
- Hilt/Dagger for Android DI
- AndroidX security for credential storage

A MCP server runs in a pure JVM environment (VS Code extension host), making these dependencies unavailable.

**Solution:** This module is a **standalone JVM implementation** that:
- Uses the same API patterns as the Android modules
- Provides real API access (Xtream) with OkHttp
- Includes mock/schema data for platform-specific features (Telegram/TDLib)
- Serves as a development/testing companion to the main app

## Features

### Xtream Tools
- `xtream_server_info` - Get server info and user account status
- `xtream_vod_categories` - List VOD (movie) categories
- `xtream_live_categories` - List Live TV categories
- `xtream_series_categories` - List Series categories
- `xtream_vod_streams` - Get VOD items by category
- `xtream_live_streams` - Get Live channels by category
- `xtream_series` - Get Series by category
- `xtream_series_info` - Get Series details with episodes
- `xtream_vod_info` - Get VOD details
- `xtream_epg` - Get EPG (TV guide) for live channels
- `xtream_stream_url` - Generate playback URL
- `xtream_search` - Search across all content types

### Telegram Tools (with TDLib integration)

**Configuration & Status:**
- `telegram_config_check` - Check Telegram API configuration and TDLib status
- `telegram_tdlib_version` - Get TDLib library version
- `telegram_auth_state` - Get current authorization state

**Authentication Flow:**
- `telegram_init` - Initialize TDLib with API credentials
- `telegram_set_phone` - Set phone number for authentication
- `telegram_submit_code` - Submit SMS/Telegram authentication code
- `telegram_submit_password` - Submit 2FA password (if enabled)

**Chat & Messages:**
- `telegram_get_chats` - Get list of chats (after authentication)
- `telegram_get_chat` - Get detailed chat info by ID
- `telegram_get_messages` - Get messages from a chat

**Schema & Mocks:**
- `telegram_message_schema` - Get TgMessage structure
- `telegram_content_schema` - Get TgContent variants
- `telegram_parse_sample` - Parse sample Telegram JSON
- `telegram_mock_message` - Generate mock TgMessage for testing

### Normalizer Tools
- `normalize_raw_schema` - Get RawMediaMetadata schema
- `normalize_xtream_vod` - Convert Xtream VOD to RawMediaMetadata
- `normalize_xtream_channel` - Convert Xtream channel to RawMediaMetadata
- `normalize_telegram_message` - Convert TgMessage to RawMediaMetadata
- `normalize_parse_title` - Parse media title for metadata extraction
- `normalize_detect_type` - Detect MediaType from metadata

## Robustness Features

- **Exponential Backoff Retry:** Failed requests are retried up to 3 times with 1s→2s→4s delays
- **Pagination:** Large result sets are automatically truncated with total/returned counts
- **Error Handling:** Clear error messages for missing credentials, HTTP errors, and timeouts

## Setup

### 1. Build the Server

```bash
# Build fat JAR
./gradlew :tools:mcp-server:fatJar

# JAR location
ls -la tools/mcp-server/build/libs/mcp-server-1.0.0-all.jar
```

### 2. Configure Environment Variables

Set these as Codespace Secrets (Settings → Secrets and variables → Codespaces):

```bash
# Xtream API credentials
XTREAM_URL=http://your-server.com:8080
XTREAM_USER=your_username
XTREAM_PASS=your_password

# Telegram API (required for TDLib authentication)
TELEGRAM_API_ID=12345678
TELEGRAM_API_HASH=your_api_hash
```

**Note:** Get your Telegram API credentials from https://my.telegram.org/apps

### Telegram Authentication Flow

TDLib requires a multi-step authentication process:

1. **Initialize:** `telegram_init` (uses TELEGRAM_API_ID/API_HASH)
2. **Set Phone:** `telegram_set_phone` with your phone number
3. **Submit Code:** `telegram_submit_code` with SMS/Telegram code
4. **2FA (if enabled):** `telegram_submit_password` with your password

After authentication, the session is persisted in the `files/database` directory.

### 3. VS Code MCP Configuration

The MCP server is configured in `.vscode/mcp.json`. After building:

1. Open VS Code settings (Cmd/Ctrl+Shift+P → "Preferences: Open Settings (JSON)")
2. Add MCP server reference (or it's auto-detected from `.vscode/mcp.json`)

### 4. Verify Installation

In VS Code Copilot Chat, ask:
> "What MCP tools are available?"

Or test directly:
```bash
# Test the server manually
java -jar tools/mcp-server/build/libs/mcp-server-1.0.0-all.jar
```

## Architecture

```
VS Code Copilot
      │
      │ MCP Protocol (STDIO)
      ▼
┌─────────────────────────────┐
│ FishIT MCP Server           │
│  ├─ XtreamTools             │ ──► Real Xtream API calls
│  ├─ TelegramTools           │ ──► Schema/mock data
│  └─ NormalizerTools         │ ──► Pipeline mapping demo
└─────────────────────────────┘
```

## Usage Examples

### Test Xtream Pipeline
```
User: "Use xtream_vod_categories to get all movie categories"
Copilot: [calls xtream_vod_categories tool]
→ Returns real category list from your Xtream server

User: "Get VOD items from category 5 and convert one to RawMediaMetadata"
Copilot: [calls xtream_vod_streams, then normalize_xtream_vod]
→ Shows exact field mapping
```

### Test Telegram Schema
```
User: "Generate a mock video message and convert to RawMediaMetadata"
Copilot: [calls telegram_mock_message, then normalize_telegram_message]
→ Shows TgMessage → RawMediaMetadata mapping
```

### Test Title Parsing
```
User: "Parse this title: Movie.Title.2024.1080p.WEB-DL.x264"
Copilot: [calls normalize_parse_title]
→ Extracts: year=2024, resolution=1080p, quality=WEB-DL, codec=x264
```

## Development

### Run from Source
```bash
./gradlew :tools:mcp-server:run
```

### Module Location
```
tools/mcp-server/
├── build.gradle.kts
├── README.md
└── src/main/kotlin/com/fishit/player/tools/mcp/
    ├── Main.kt           # Server entry point
    ├── XtreamTools.kt    # Xtream API tools
    ├── TelegramTools.kt  # Telegram schema/mock tools
    └── NormalizerTools.kt # Pipeline normalization tools
```

## Notes

- **Standalone JVM:** This module is NOT an Android library. It's a pure Kotlin/JVM application.
- **No TDLib:** Telegram tools provide schema and mock data only. Full TDLib requires native libraries.
- **Codespace Secrets:** Use Codespace Secrets for API credentials - they're automatically available as environment variables.
- **STDIO Transport:** Uses standard input/output for MCP communication (Codespace compatible).

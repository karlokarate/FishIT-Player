> LEGACY (V1) – historical document  
> Not valid for v2. For current architecture see `V2_PORTAL.md` and `docs/v2/**`.

# TDLib Integration for FishIT Player

This document describes the TDLib (Telegram Database Library) integration implemented for FishIT Player, following the specifications in `tools/Telegram API readme.txt` and `tools/tdlib_coroutines_doku.md`.

## Overview

The implementation provides a complete Telegram client integration using the `tdl-coroutines-android` library, enabling the FishIT Player app to:
- Authenticate with Telegram (phone number, code, 2FA password)
- Browse Telegram chats and messages
- Parse media content from messages (movies, series, episodes)
- Extract metadata and integrate with existing ObjectBox entities

## Architecture

The integration is organized into several modules under `app/src/main/java/com/chris/m3usuite/telegram/`:

```
telegram/
├── config/          # Configuration and setup
│   ├── AppConfig.kt
│   └── ConfigLoader.kt
├── session/         # TDLib session management
│   └── TelegramSession.kt
├── models/          # Data models
│   └── MediaModels.kt
├── parser/          # Message parsing logic
│   └── MediaParser.kt
└── browser/         # Chat and message browsing
    ├── ChatBrowser.kt
    └── MessageFormatter.kt
```

## Components

### 1. Configuration (`config/`)

#### AppConfig
Data class holding TDLib initialization parameters:
- `apiId`: Telegram API ID (obtained from https://my.telegram.org)
- `apiHash`: Telegram API Hash
- `phoneNumber`: User's phone number for authentication
- `dbDir`: Database directory path
- `filesDir`: Files directory path

#### ConfigLoader
Android-specific configuration loader that:
- Creates proper Android directories using `Context.noBackupFilesDir`
- Loads API credentials from BuildConfig or settings
- Provides fallback defaults

Usage:
```kotlin
val config = ConfigLoader.load(
    context = applicationContext,
    apiId = 12345,
    apiHash = "your_api_hash",
    phoneNumber = "+1234567890"
)
```

### 2. Session Management (`session/`)

#### TelegramSession
Manages the TDLib client lifecycle and authentication flow:

**Features:**
- Flow-based authentication state management
- Automatic TdlibParameters setup
- Support for phone number, SMS/Telegram code, and 2FA password
- Event emission for UI integration

**Authentication Flow:**
1. `AuthorizationStateWaitTdlibParameters` → Auto-handled internally
2. `AuthorizationStateWaitPhoneNumber` → Call `sendPhoneNumber()`
3. `AuthorizationStateWaitCode` → Call `sendCode()`
4. `AuthorizationStateWaitPassword` → Call `sendPassword()`
5. `AuthorizationStateReady` → Authentication complete

**Usage:**
```kotlin
val client = TdlClient.create()
val session = TelegramSession(client, config, coroutineScope)

// Start login
launch {
    session.login()
}

// Listen to auth events
launch {
    session.authEvents.collect { event ->
        when (event) {
            is AuthEvent.StateChanged -> {
                when (event.state) {
                    is AuthorizationStateWaitPhoneNumber -> 
                        session.sendPhoneNumber(phoneNumber)
                    is AuthorizationStateWaitCode -> 
                        session.sendCode(code)
                    is AuthorizationStateWaitPassword -> 
                        session.sendPassword(password)
                }
            }
            is AuthEvent.Ready -> // Authentication successful
            is AuthEvent.Error -> // Handle error
        }
    }
}
```

### 3. Data Models (`models/`)

#### MediaKind Enum
Classification of media content:
- `MOVIE`: Feature film
- `SERIES`: Complete series metadata
- `EPISODE`: Single episode
- `CLIP`: Short video
- `RAR_ARCHIVE`: Compressed archive
- `PHOTO`: Image
- `TEXT_ONLY`: Text-only message
- `ADULT`: Adult content
- `OTHER`: Unknown type

#### MediaInfo
Parsed media information including:
- File metadata (name, size, mime type)
- Content metadata (title, year, genres, director, rating)
- Series information (season, episode numbers)
- TMDB ratings and votes

#### ParsedItem
Sealed class representing parsed message results:
- `Media(info: MediaInfo)`: Media content
- `SubChat(ref: SubChatRef)`: Reference to a sub-chat
- `Invite(invite: InviteLink)`: Telegram invite link
- `None`: No parseable content

### 4. Message Parser (`parser/`)

#### MediaParser
Extracts structured information from Telegram messages using regex patterns.

**Capabilities:**
- Parses German-language metadata patterns (Titel, Erscheinungsjahr, Länge, etc.)
- Extracts season/episode information (S01E02 format)
- Detects adult content using keyword patterns
- Recognizes archive files (.rar, .zip, .7z, etc.)
- Finds Telegram invite links
- Identifies sub-chat references (series folders)

**Supported Message Types:**
- `MessageVideo`: Video files with metadata
- `MessageDocument`: Documents (including archives)
- `MessagePhoto`: Images
- `MessageText`: Text with embedded metadata

**Usage:**
```kotlin
val parsedItem = MediaParser.parseMessage(
    chatId = chat.id,
    chatTitle = chat.title,
    message = message,
    recentMessages = listOf()  // Optional context
)

when (parsedItem) {
    is ParsedItem.Media -> {
        val info = parsedItem.info
        println("Title: ${info.title}, Year: ${info.year}")
    }
    is ParsedItem.SubChat -> {
        val ref = parsedItem.ref
        println("Sub-chat: ${ref.label}")
    }
    is ParsedItem.Invite -> {
        val invite = parsedItem.invite
        println("Invite link: ${invite.url}")
    }
    is ParsedItem.None -> println("No content")
}
```

### 5. Chat Browser (`browser/`)

#### ChatBrowser
Provides navigation and paging for chats and messages:

**Features:**
- Load chat list (default 200 chats)
- Load single chat by ID
- Page through message history (default 20 messages per page)
- Load all messages from a chat (with safety limits)
- Search messages within a chat

**Usage:**
```kotlin
val browser = ChatBrowser(session)

// Load chats
val chats = browser.loadChats(limit = 100)

// Load messages with paging
val messages = browser.loadChatHistory(
    chatId = chatId,
    fromMessageId = 0,  // 0 for latest
    limit = 20
)

// Search in chat
val results = browser.searchChatMessages(
    chatId = chatId,
    query = "movie",
    limit = 50
)
```

#### MessageFormatter
Utility for formatting messages for display:
- `formatMessageLine()`: Single-line preview
- `getContentPreview()`: Short content description
- `formatMessageDetails()`: Full message details
- `getMessageText()`: Extract text from any message type
- `hasMediaContent()`: Check if message contains media

## Integration with ObjectBox

The parsed media information integrates with the existing `ObxTelegramMessage` entity:

```kotlin
@Entity
data class ObxTelegramMessage(
    @Id var id: Long = 0,
    @Index var chatId: Long = 0,
    @Index var messageId: Long = 0,
    @Index var fileId: Int? = null,
    var localPath: String? = null,
    var caption: String? = null,
    var durationSecs: Int? = null,
    var mimeType: String? = null,
    // ... other fields
)
```

## Dependencies

Added to `app/build.gradle.kts`:
```kotlin
implementation("dev.g000sha256:tdl-coroutines-android:5.0.0")
```

## Build Requirements

- Android Gradle Plugin 8.5.2+
- Kotlin 2.0.21+
- Minimum SDK 24
- Target SDK 36

## Next Steps

To complete the integration:

1. **ViewModel Integration**
   - Create `TelegramViewModel` to manage session lifecycle
   - Expose auth state and chat data as StateFlows
   - Handle UI interactions (login, logout, chat selection)

2. **UI Components**
   - Login screen (phone number input, code verification, password)
   - Chat list screen
   - Message browser with media preview
   - Integration with existing detail screens

3. **ObjectBox Synchronization**
   - Worker to sync Telegram messages to ObjectBox
   - Background indexing of media content
   - Update existing entities with Telegram data

4. **Settings Integration**
   - API credentials configuration
   - Chat selection for content sources
   - Sync scheduling options

## References

- **TDLib Documentation**: [tdlib.github.io](https://tdlib.github.io/)
- **Implementation Guide**: `tools/tdlib_coroutines_doku.md`
- **API Reference**: `tools/Telegram API readme.txt`
- **tdl-coroutines Library**: [GitHub](https://github.com/g000sha256/tdl-coroutines)

## Security Considerations

- API credentials should be stored securely (EncryptedSharedPreferences or BuildConfig secrets)
- Database directory uses `noBackupFilesDir` to prevent cloud backup of local cache
- All TDLib operations run in background coroutines
- Proper error handling for network failures and auth errors

## License

This implementation follows the same license as the FishIT Player project.

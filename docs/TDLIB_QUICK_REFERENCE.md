# TDLib Integration Quick Reference

## Quick Start

### 1. Add Dependency
Already added in `app/build.gradle.kts`:
```kotlin
implementation("dev.g000sha256:tdl-coroutines-android:5.0.0")
```

### 2. Initialize Session

```kotlin
// In your ViewModel or Repository
val context = applicationContext
val client = TdlClient.create()
val config = ConfigLoader.load(
    context = context,
    apiId = YOUR_API_ID,
    apiHash = "YOUR_API_HASH",
    phoneNumber = "+1234567890"
)
val session = TelegramSession(client, config, viewModelScope)
```

### 3. Handle Authentication

```kotlin
// Collect auth events
session.authEvents.collect { event ->
    when (event) {
        is AuthEvent.StateChanged -> {
            when (event.state) {
                is AuthorizationStateWaitPhoneNumber -> {
                    session.sendPhoneNumber(phoneNumber)
                }
                is AuthorizationStateWaitCode -> {
                    // Show code input dialog
                    session.sendCode(userInputCode)
                }
                is AuthorizationStateWaitPassword -> {
                    // Show password input for 2FA
                    session.sendPassword(userInputPassword)
                }
            }
        }
        is AuthEvent.Ready -> {
            // Authentication successful, proceed to load chats
        }
        is AuthEvent.Error -> {
            // Handle error
        }
    }
}
```

### 4. Browse Chats

```kotlin
val browser = ChatBrowser(session)

// Load all chats
val chats = browser.loadChats(limit = 200)

// Load messages from a chat
val messages = browser.loadChatHistory(
    chatId = chatId,
    fromMessageId = 0,  // 0 for latest
    limit = 20
)

// Search in chat
val results = browser.searchChatMessages(
    chatId = chatId,
    query = "search term",
    limit = 100
)
```

### 5. Parse Messages

```kotlin
val parsed = MediaParser.parseMessage(
    chatId = message.chatId,
    chatTitle = chat.title,
    message = message
)

when (parsed) {
    is ParsedItem.Media -> {
        val info = parsed.info
        // Access: info.title, info.year, info.genres, etc.
    }
    is ParsedItem.SubChat -> {
        val ref = parsed.ref
        // Sub-chat reference (series folders, etc.)
    }
    is ParsedItem.Invite -> {
        val invite = parsed.invite
        // Telegram invite link
    }
    is ParsedItem.None -> {
        // No parseable content
    }
}
```

## Common Patterns

### Error Handling

```kotlin
try {
    session.sendCode(code)
} catch (e: Exception) {
    when {
        e.message?.contains("PHONE_CODE_INVALID") == true -> 
            showError("Invalid code")
        e.message?.contains("FLOOD_WAIT") == true -> 
            showError("Too many attempts, try later")
        else -> 
            showError("Error: ${e.message}")
    }
}
```

### Paging Through Chat History

```kotlin
var fromMessageId = 0L
val allMessages = mutableListOf<Message>()

while (true) {
    val batch = browser.loadChatHistory(
        chatId = chatId,
        fromMessageId = fromMessageId,
        limit = 100
    )
    
    if (batch.isEmpty()) break
    
    allMessages.addAll(batch)
    fromMessageId = batch.last().id
    
    if (batch.size < 100) break  // Last page
}
```

### Integrating with ObjectBox

```kotlin
// After parsing a message
when (val parsed = MediaParser.parseMessage(chatId, chatTitle, message)) {
    is ParsedItem.Media -> {
        val info = parsed.info
        
        // Create ObjectBox entity
        val obxMessage = ObxTelegramMessage().apply {
            this.chatId = info.chatId
            this.messageId = info.messageId
            this.caption = info.title
            this.fileName = info.fileName
            this.mimeType = info.mimeType
            this.sizeBytes = info.sizeBytes
            this.durationSecs = info.durationMinutes?.times(60)
            // ... other fields
        }
        
        // Store in ObjectBox
        objectBox.put(obxMessage)
    }
}
```

## Authentication States Flow

```
Start
  ↓
AuthorizationStateWaitTdlibParameters (auto-handled)
  ↓
AuthorizationStateWaitPhoneNumber → sendPhoneNumber()
  ↓
AuthorizationStateWaitCode → sendCode()
  ↓ (if 2FA enabled)
AuthorizationStateWaitPassword → sendPassword()
  ↓
AuthorizationStateReady ✓
```

## API Credentials

Get your API credentials from https://my.telegram.org:
1. Log in with your phone number
2. Go to "API development tools"
3. Create an application
4. Copy `api_id` and `api_hash`

Store securely:
- Use EncryptedSharedPreferences
- Or add to BuildConfig as build secrets
- Never commit credentials to git

## File Organization

```
telegram/
├── config/
│   ├── AppConfig.kt           # Config data class
│   └── ConfigLoader.kt        # Android config loader
├── session/
│   └── TelegramSession.kt     # Session management & auth
├── models/
│   └── MediaModels.kt         # Data models
├── parser/
│   └── MediaParser.kt         # Message parsing
├── browser/
│   ├── ChatBrowser.kt         # Chat/message browsing
│   └── MessageFormatter.kt    # Formatting utilities
└── ui/
    └── TelegramViewModel.kt   # Example ViewModel
```

## Supported Message Types

- **MessageVideo**: Video files with metadata
- **MessageDocument**: Documents, archives (.rar, .zip, .7z)
- **MessagePhoto**: Images
- **MessageText**: Text messages with embedded metadata

## Parsed Metadata

From German message captions:
- Titel / Title
- Originaltitel / Original Title
- Erscheinungsjahr / Year
- Länge / Duration
- Produktionsland / Country
- FSK / Age Rating
- Filmreihe / Collection
- Regie / Director
- TMDbRating / Rating
- Genres
- Episoden / Episodes
- Season/Episode numbers (S01E02 format)

## Tips

1. **Testing**: Use Telegram test environment initially
2. **Rate Limiting**: Handle FLOOD_WAIT errors with delays
3. **Background**: Run TDLib operations in background coroutines
4. **Cleanup**: Call `session.logout()` and cleanup resources properly
5. **Security**: Store credentials securely, never in code
6. **Performance**: Use paging for large chat histories
7. **Error Handling**: TDLib errors are wrapped in RuntimeException with code and message

## Next Steps

1. Create authentication UI screens (phone, code, password)
2. Integrate with existing Settings screen
3. Add chat selection UI
4. Sync parsed messages to ObjectBox
5. Add background worker for periodic sync
6. Test with real Telegram account
7. Handle edge cases and errors

## Resources

- Full Documentation: `docs/TDLIB_INTEGRATION.md`
- TDLib Docs: https://tdlib.github.io/
- Example Code: `tools/tdlib_coroutines_doku.md`
- API Reference: `tools/Telegram API readme.txt`

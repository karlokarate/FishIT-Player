# Telegram-Core Module

This module provides a coroutine-based Telegram integration using TDLib for Android applications.

## Overview

The `telegram-core` module is based on the JVM test client documented in `tools/tdlib_coroutines_doku.md` and adapted for Android using `tdl-coroutines-android:5.0.0`.

## Components

### Core Classes

#### MediaModels.kt
Defines data models for media content:
- `MediaKind` - Enum for different media types (MOVIE, SERIES, EPISODE, etc.)
- `MediaInfo` - Complete metadata for media files
- `SubChatRef` - References to sub-chats and series directories
- `InviteLink` - Telegram invite link information
- `ParsedItem` - Sealed class for parsed message results

#### MediaParser.kt
Parses Telegram messages to extract:
- Movie metadata (title, year, director, genres, TMDb ratings)
- Series information (seasons, episodes)
- Archive files (.rar, .zip, .7z)
- Adult content detection
- Sub-chat references
- Invite links

#### TelegramSession.kt
Manages Telegram authentication flow:
- Flow-based auth state machine
- Automatic handling of TDLib parameters
- Phone number and code verification
- 2FA password support
- Requires callbacks for code and password input from UI

#### ChatBrowser.kt
Provides chat browsing functionality:
- Load available chats
- Retrieve chat history with pagination
- Navigate through messages in configurable page sizes

#### Config.kt
Configuration interface for Android:
- `AppConfig` - Data class for API credentials and storage paths
- `ConfigLoader` - Interface for loading configuration from Android sources

#### MessagePrinter.kt
Utility for formatting message information for debugging and logging.

## Usage Example

### 1. Implement ConfigLoader

```kotlin
class AndroidConfigLoader(
    private val context: Context,
    private val prefs: SharedPreferences
) : ConfigLoader {
    override fun load(): AppConfig {
        return AppConfig(
            apiId = BuildConfig.TG_API_ID,
            apiHash = BuildConfig.TG_API_HASH,
            phoneNumber = prefs.getString("tg_phone_number", "") ?: "",
            dbDir = File(context.noBackupFilesDir, "td-db").apply { mkdirs() }.absolutePath,
            filesDir = File(context.filesDir, "td-files").apply { mkdirs() }.absolutePath
        )
    }
}
```

### 2. Initialize TelegramSession in ViewModel

```kotlin
class TelegramViewModel(
    private val configLoader: ConfigLoader
) : ViewModel() {
    
    private val _authCode = MutableStateFlow<String?>(null)
    private val _password = MutableStateFlow<String?>(null)
    
    suspend fun login() {
        val config = configLoader.load()
        val client = TdlClient.create()
        
        val session = TelegramSession(
            client = client,
            config = config,
            scope = viewModelScope,
            codeProvider = { 
                // Wait for code from UI
                _authCode.filterNotNull().first().also { _authCode.value = null }
            },
            passwordProvider = { 
                // Wait for password from UI
                _password.filterNotNull().first().also { _password.value = null }
            }
        )
        
        session.login()
    }
    
    fun submitCode(code: String) {
        _authCode.value = code
    }
    
    fun submitPassword(password: String) {
        _password.value = password
    }
}
```

### 3. Browse Chats

```kotlin
val browser = ChatBrowser(session)

// Load all chats
val chats = browser.loadChats()

// Load messages from a specific chat
val messages = browser.loadChatHistory(chatId = 12345L, limit = 20)

// Parse messages for media content
messages.forEach { msg ->
    val chat = browser.getChat(msg.chatId)
    val parsed = MediaParser.parseMessage(
        chatId = msg.chatId,
        chatTitle = chat.title,
        message = msg
    )
    
    when (parsed) {
        is ParsedItem.Media -> {
            // Handle media (movie, series, etc.)
            println("Found media: ${parsed.info.title}")
        }
        is ParsedItem.SubChat -> {
            // Handle sub-chat reference
            println("Found sub-chat: ${parsed.ref.label}")
        }
        is ParsedItem.Invite -> {
            // Handle invite link
            println("Found invite: ${parsed.invite.url}")
        }
        is ParsedItem.None -> {
            // Message contains no parseable content
        }
    }
}
```

## Dependencies

- `dev.g000sha256:tdl-coroutines-android:5.0.0` - TDLib Kotlin Coroutines Client
- `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2` - Coroutines for Android
- `org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2` - Core coroutines library

## Integration with FishIT Player App

To use this module in the main FishIT Player app:

1. Add dependency in `app/build.gradle.kts`:
```kotlin
dependencies {
    implementation(project(":telegram-core"))
}
```

2. Ensure TG_API_ID and TG_API_HASH are configured in BuildConfig (see app's build.gradle.kts)

3. Implement ConfigLoader using Android Context and SharedPreferences

4. Create ViewModels that use TelegramSession for authentication and ChatBrowser for content browsing

5. Use MediaParser to extract metadata from messages for display in UI

## Differences from JVM Test Client

- **No CLI Input/Output**: The `CliIo.kt` module from the JVM test is not included. Instead, code and password input are provided via callback functions that should be implemented using Android UI components.

- **Android-Specific Configuration**: `ConfigLoader` is an interface allowing Android-specific implementations using Context, BuildConfig, and SharedPreferences.

- **Coroutine Scope Management**: Uses Android-appropriate scopes like `viewModelScope` instead of `runBlocking`.

- **Library Module**: This is an Android library module meant to be used by the main app, not a standalone application.

## Next Steps

As outlined in the documentation (`tools/tdlib_coroutines_doku.md`), the next steps include:

1. Integrate telegram-core into ViewModel layer
2. Bind authorizationStateUpdates to StateFlow/LiveData for Compose UI
3. Persist MediaInfo, SubChatRef, InviteLink to database (ObjectBox/Room)
4. Build UI screens for:
   - Telegram account management
   - Chat list browsing
   - Media library (filtered by movies, series, adult content)
   - Series sub-chat drill-down navigation

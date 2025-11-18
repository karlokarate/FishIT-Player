# Integration Guide: telegram-core → FishIT Player App

This guide explains how to integrate the `telegram-core` module into the main FishIT Player application.

## Prerequisites

Before integration, ensure:
1. The `telegram-core` module is built successfully
2. TG_API_ID and TG_API_HASH are configured in the app's build configuration
3. The app has necessary permissions (INTERNET, ACCESS_NETWORK_STATE)

## Step 1: Add Module Dependency

In `app/build.gradle.kts`, add the telegram-core module dependency:

```kotlin
dependencies {
    // ... existing dependencies ...
    
    // Telegram Core module
    implementation(project(":telegram-core"))
}
```

## Step 2: Create Android ConfigLoader

Create `app/src/main/java/com/chris/m3usuite/telegram/config/AndroidTelegramConfig.kt`:

```kotlin
package com.chris.m3usuite.telegram.config

import android.content.Context
import android.content.SharedPreferences
import com.chris.m3usuite.BuildConfig
import com.chris.m3usuite.telegram.core.AppConfig
import com.chris.m3usuite.telegram.core.ConfigLoader
import java.io.File

class AndroidTelegramConfig(
    private val context: Context,
    private val prefs: SharedPreferences
) : ConfigLoader {
    
    override fun load(): AppConfig {
        // Get phone number from SharedPreferences
        val phoneNumber = prefs.getString("telegram_phone_number", "") ?: ""
        
        // Use app's private directories for TDLib storage
        val dbDir = File(context.noBackupFilesDir, "telegram-db")
            .apply { mkdirs() }
            .absolutePath
            
        val filesDir = File(context.filesDir, "telegram-files")
            .apply { mkdirs() }
            .absolutePath
        
        return AppConfig(
            apiId = BuildConfig.TG_API_ID,
            apiHash = BuildConfig.TG_API_HASH,
            phoneNumber = phoneNumber,
            dbDir = dbDir,
            filesDir = filesDir
        )
    }
    
    fun updatePhoneNumber(phoneNumber: String) {
        prefs.edit()
            .putString("telegram_phone_number", phoneNumber)
            .apply()
    }
}
```

## Step 3: Create TelegramViewModel

Create `app/src/main/java/com/chris/m3usuite/ui/models/telegram/TelegramCoreViewModel.kt`:

```kotlin
package com.chris.m3usuite.ui.models.telegram

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chris.m3usuite.telegram.config.AndroidTelegramConfig
import com.chris.m3usuite.telegram.core.*
import dev.g000sha256.tdl.TdlClient
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed class TelegramAuthState {
    object Idle : TelegramAuthState()
    object WaitingForCode : TelegramAuthState()
    object WaitingForPassword : TelegramAuthState()
    object Authenticated : TelegramAuthState()
    data class Error(val message: String) : TelegramAuthState()
}

class TelegramCoreViewModel(
    private val configLoader: AndroidTelegramConfig
) : ViewModel() {
    
    private val _authState = MutableStateFlow<TelegramAuthState>(TelegramAuthState.Idle)
    val authState: StateFlow<TelegramAuthState> = _authState.asStateFlow()
    
    private val _authCode = MutableStateFlow<String?>(null)
    private val _password = MutableStateFlow<String?>(null)
    
    private var client: TdlClient? = null
    private var session: TelegramSession? = null
    private var browser: ChatBrowser? = null
    
    fun startLogin(phoneNumber: String) {
        viewModelScope.launch {
            try {
                // Update phone number in config
                configLoader.updatePhoneNumber(phoneNumber)
                
                // Load config
                val config = configLoader.load()
                
                // Create TDLib client
                client = TdlClient.create()
                
                // Create session with UI callbacks
                session = TelegramSession(
                    client = client!!,
                    config = config,
                    scope = viewModelScope,
                    codeProvider = {
                        _authState.value = TelegramAuthState.WaitingForCode
                        _authCode.filterNotNull().first().also { _authCode.value = null }
                    },
                    passwordProvider = {
                        _authState.value = TelegramAuthState.WaitingForPassword
                        _password.filterNotNull().first().also { _password.value = null }
                    }
                )
                
                // Start login
                session!!.login()
                
                // If we get here, login succeeded
                _authState.value = TelegramAuthState.Authenticated
                
                // Initialize browser
                browser = ChatBrowser(session!!)
                
            } catch (e: Exception) {
                _authState.value = TelegramAuthState.Error(e.message ?: "Unknown error")
            }
        }
    }
    
    fun submitCode(code: String) {
        _authCode.value = code
    }
    
    fun submitPassword(password: String) {
        _password.value = password
    }
    
    suspend fun loadChats(): List<Chat> {
        return browser?.loadChats() ?: emptyList()
    }
    
    suspend fun loadChatHistory(chatId: Long, fromMessageId: Long = 0L): List<Message> {
        return browser?.loadChatHistory(chatId, fromMessageId) ?: emptyList()
    }
    
    suspend fun parseMessagesFromChat(chatId: Long): List<ParsedItem> {
        val browser = browser ?: return emptyList()
        val chat = browser.getChat(chatId)
        val messages = browser.loadChatHistory(chatId, limit = 100)
        
        return messages.map { msg ->
            MediaParser.parseMessage(
                chatId = chatId,
                chatTitle = chat.title,
                message = msg
            )
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        // Clean up TDLib client
        client = null
        session = null
        browser = null
    }
}
```

## Step 4: Create UI for Authentication

Create `app/src/main/java/com/chris/m3usuite/ui/screens/TelegramAuthScreen.kt`:

```kotlin
package com.chris.m3usuite.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chris.m3usuite.ui.models.telegram.TelegramAuthState
import com.chris.m3usuite.ui.models.telegram.TelegramCoreViewModel

@Composable
fun TelegramAuthScreen(
    viewModel: TelegramCoreViewModel
) {
    val authState by viewModel.authState.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (authState) {
            TelegramAuthState.Idle -> {
                PhoneNumberInput(
                    onSubmit = { phoneNumber ->
                        viewModel.startLogin(phoneNumber)
                    }
                )
            }
            
            TelegramAuthState.WaitingForCode -> {
                CodeInput(
                    onSubmit = { code ->
                        viewModel.submitCode(code)
                    }
                )
            }
            
            TelegramAuthState.WaitingForPassword -> {
                PasswordInput(
                    onSubmit = { password ->
                        viewModel.submitPassword(password)
                    }
                )
            }
            
            TelegramAuthState.Authenticated -> {
                Text("Authentication successful!")
            }
            
            is TelegramAuthState.Error -> {
                Text(
                    text = "Error: ${(authState as TelegramAuthState.Error).message}",
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun PhoneNumberInput(onSubmit: (String) -> Unit) {
    var phoneNumber by remember { mutableStateOf("") }
    
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Enter Phone Number", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = phoneNumber,
            onValueChange = { phoneNumber = it },
            label = { Text("Phone Number") },
            placeholder = { Text("+49123456789") }
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { onSubmit(phoneNumber) }) {
            Text("Continue")
        }
    }
}

@Composable
private fun CodeInput(onSubmit: (String) -> Unit) {
    var code by remember { mutableStateOf("") }
    
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Enter Verification Code", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = code,
            onValueChange = { code = it },
            label = { Text("Code") }
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { onSubmit(code) }) {
            Text("Verify")
        }
    }
}

@Composable
private fun PasswordInput(onSubmit: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Enter 2FA Password", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") }
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { onSubmit(password) }) {
            Text("Submit")
        }
    }
}
```

## Step 5: Use MediaParser for Content Indexing

Create a repository to process and store parsed media:

```kotlin
package com.chris.m3usuite.data.repo

import com.chris.m3usuite.telegram.core.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class TelegramMediaRepository(
    private val viewModel: TelegramCoreViewModel,
    // Add your database DAO here (ObjectBox, Room, etc.)
) {
    
    suspend fun indexChatMedia(chatId: Long): Flow<ParsedItem> = flow {
        val parsedItems = viewModel.parseMessagesFromChat(chatId)
        
        parsedItems.forEach { item ->
            emit(item)
            
            when (item) {
                is ParsedItem.Media -> {
                    // Store MediaInfo in database
                    // db.mediaDao().insert(item.info)
                }
                is ParsedItem.SubChat -> {
                    // Store SubChatRef in database
                    // db.subChatDao().insert(item.ref)
                }
                is ParsedItem.Invite -> {
                    // Store InviteLink in database
                    // db.inviteLinkDao().insert(item.invite)
                }
                is ParsedItem.None -> {
                    // Skip
                }
            }
        }
    }
    
    suspend fun getMovies(): List<MediaInfo> {
        // Return movies from database
        // return db.mediaDao().getByKind(MediaKind.MOVIE)
        return emptyList()
    }
    
    suspend fun getSeries(): List<MediaInfo> {
        // Return series from database
        // return db.mediaDao().getByKind(MediaKind.SERIES)
        return emptyList()
    }
}
```

## Step 6: Add Permissions to AndroidManifest.xml

Ensure these permissions are in `app/src/main/AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
```

## Step 7: Configure Dependency Injection (if using Hilt/Koin)

If using dependency injection, configure the module:

```kotlin
// For Hilt:
@Module
@InstallIn(SingletonComponent::class)
object TelegramModule {
    
    @Provides
    @Singleton
    fun provideSharedPreferences(
        @ApplicationContext context: Context
    ): SharedPreferences {
        return context.getSharedPreferences("telegram_prefs", Context.MODE_PRIVATE)
    }
    
    @Provides
    @Singleton
    fun provideTelegramConfig(
        @ApplicationContext context: Context,
        prefs: SharedPreferences
    ): AndroidTelegramConfig {
        return AndroidTelegramConfig(context, prefs)
    }
    
    @Provides
    fun provideTelegramCoreViewModel(
        config: AndroidTelegramConfig
    ): TelegramCoreViewModel {
        return TelegramCoreViewModel(config)
    }
}
```

## Testing

To test the integration:

1. Launch the app
2. Navigate to Telegram authentication screen
3. Enter phone number and verify with code
4. Browse chats and view parsed media
5. Check database for stored media information

## Notes

- The telegram-core module handles all TDLib interaction
- MediaParser automatically extracts metadata from messages
- Use ChatBrowser for paginated message loading
- Implement proper error handling in production
- Consider background sync using WorkManager for media indexing
- Store parsed data in your database (ObjectBox/Room)

## Troubleshooting

**Issue**: Authentication fails
- Check TG_API_ID and TG_API_HASH are correct
- Verify phone number format (E.164, e.g., +491234567890)
- Ensure internet connection is available

**Issue**: Messages not parsing
- Check message format matches German metadata format
- Verify file names contain expected patterns
- Enable debug logging with MessagePrinter

**Issue**: Build errors
- Ensure telegram-core module is in settings.gradle.kts
- Check all dependencies are resolved
- Verify Kotlin version compatibility (2.0.21)

## See Also

- `telegram-core/README.md` - Module documentation
- `telegram-core/IMPLEMENTATION_SUMMARY.md` - Implementation details
- `tools/tdlib_coroutines_doku.md` - Original specification

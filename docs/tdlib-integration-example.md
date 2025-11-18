# TDLib Android Integration Example

This document shows how to integrate the TDLib AAR (built with BoringSSL) into your Android project.

## Prerequisites

- Android Studio Arctic Fox or newer
- Gradle 7.0 or newer
- Min SDK 24 (Android 7.0)
- Java 17

## Integration Steps

### 1. Download the AAR

Go to the [Releases page](https://github.com/karlokarate/FishIT-Player/releases) and download:
- `tdlib-android-boringssl.aar`

### 2. Add to Your Project

#### Option A: Local AAR File

1. Create a `libs` directory in your app module (if it doesn't exist):
   ```
   your-project/
   └── app/
       └── libs/
           └── tdlib-android-boringssl.aar
   ```

2. Add to your app's `build.gradle.kts`:
   ```kotlin
   android {
       // ... your existing config
   }

   dependencies {
       implementation(files("libs/tdlib-android-boringssl.aar"))
       
       // Required dependencies
       implementation("androidx.annotation:annotation:1.9.1")
   }
   ```

#### Option B: Module Dependency (Development)

If you want to include the source:

1. Clone this repository
2. Copy the `libtd` directory to your project
3. Add to your `settings.gradle.kts`:
   ```kotlin
   include(":app")
   include(":libtd")
   ```

4. Add to your app's `build.gradle.kts`:
   ```kotlin
   dependencies {
       implementation(project(":libtd"))
   }
   ```

### 3. Initialize TDLib

```kotlin
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import org.drinkless.tdlib.Log

class TelegramManager {
    private var client: Client? = null
    
    fun initialize() {
        // Set log verbosity (0-5, where 5 is most verbose)
        Log.setVerbosityLevel(2)
        
        // Create client with update handler
        client = Client.create({ update ->
            handleUpdate(update)
        }, null, null)
        
        // Send initialization parameters
        val parameters = TdApi.SetTdlibParameters().apply {
            databaseDirectory = context.filesDir.absolutePath + "/tdlib"
            useMessageDatabase = true
            useSecretChats = true
            apiId = YOUR_API_ID
            apiHash = "YOUR_API_HASH"
            systemLanguageCode = "en"
            deviceModel = Build.MODEL
            systemVersion = Build.VERSION.RELEASE
            applicationVersion = "1.0"
            enableStorageOptimizer = true
        }
        
        client?.send(parameters) { result ->
            when (result) {
                is TdApi.Ok -> Log.i("TDLib", "Parameters set successfully")
                is TdApi.Error -> Log.e("TDLib", "Error: ${result.message}")
            }
        }
    }
    
    private fun handleUpdate(update: TdApi.Object) {
        when (update) {
            is TdApi.UpdateAuthorizationState -> {
                handleAuthorizationState(update.authorizationState)
            }
            is TdApi.UpdateNewMessage -> {
                val message = update.message
                Log.i("TDLib", "New message: ${message.content}")
            }
            // Handle other updates...
        }
    }
    
    private fun handleAuthorizationState(state: TdApi.AuthorizationState) {
        when (state) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> {
                // Already handled in initialize()
            }
            is TdApi.AuthorizationStateWaitPhoneNumber -> {
                // Request phone number from user
            }
            is TdApi.AuthorizationStateWaitCode -> {
                // Request verification code from user
            }
            is TdApi.AuthorizationStateReady -> {
                Log.i("TDLib", "Authorization successful")
            }
            // Handle other states...
        }
    }
}
```

### 4. ProGuard/R8 Configuration

The AAR includes ProGuard rules, but if you encounter issues, add to your `proguard-rules.pro`:

```proguard
# TDLib
-keep class org.drinkless.tdlib.** { *; }
-keepclassmembers class org.drinkless.tdlib.** { *; }
-keepnames class org.drinkless.tdlib.** { *; }

# Native methods
-keepclasseswithmembernames class * {
    native <methods>;
}
```

## Complete Example Application

```kotlin
package com.example.tdlibdemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import org.drinkless.tdlib.Log

class MainActivity : ComponentActivity() {
    private lateinit var tdlibManager: TdLibManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        tdlibManager = TdLibManager(this)
        tdlibManager.initialize()
        
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TelegramScreen(tdlibManager)
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        tdlibManager.close()
    }
}

class TdLibManager(private val context: Context) {
    private var client: Client? = null
    private val _authState = MutableStateFlow<TdApi.AuthorizationState?>(null)
    val authState: StateFlow<TdApi.AuthorizationState?> = _authState.asStateFlow()
    
    fun initialize() {
        Log.setVerbosityLevel(2)
        
        client = Client.create({ update ->
            when (update) {
                is TdApi.UpdateAuthorizationState -> {
                    _authState.value = update.authorizationState
                }
            }
        }, null, null)
    }
    
    fun sendPhoneNumber(phoneNumber: String) {
        client?.send(TdApi.SetAuthenticationPhoneNumber(phoneNumber, null)) { result ->
            // Handle result
        }
    }
    
    fun sendCode(code: String) {
        client?.send(TdApi.CheckAuthenticationCode(code)) { result ->
            // Handle result
        }
    }
    
    fun close() {
        client?.send(TdApi.Close()) {
            client = null
        }
    }
}

@Composable
fun TelegramScreen(manager: TdLibManager) {
    val authState by manager.authState.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (authState) {
            is TdApi.AuthorizationStateWaitPhoneNumber -> {
                PhoneNumberInput { phoneNumber ->
                    manager.sendPhoneNumber(phoneNumber)
                }
            }
            is TdApi.AuthorizationStateWaitCode -> {
                CodeInput { code ->
                    manager.sendCode(code)
                }
            }
            is TdApi.AuthorizationStateReady -> {
                Text("Logged in successfully!")
            }
            else -> {
                CircularProgressIndicator()
                Text("Initializing...")
            }
        }
    }
}
```

## API Keys

To use TDLib, you need Telegram API credentials:

1. Go to https://my.telegram.org/apps
2. Log in with your phone number
3. Create a new application
4. Note your `api_id` and `api_hash`

**Never commit these keys to version control!**

Use BuildConfig or environment variables:

```kotlin
// build.gradle.kts
android {
    defaultConfig {
        buildConfigField("int", "TELEGRAM_API_ID", "YOUR_API_ID")
        buildConfigField("String", "TELEGRAM_API_HASH", "\"YOUR_API_HASH\"")
    }
}

// In code
apiId = BuildConfig.TELEGRAM_API_ID
apiHash = BuildConfig.TELEGRAM_API_HASH
```

## Supported ABIs

The AAR includes native libraries for:
- **arm64-v8a** (64-bit ARM, used by most modern devices)
- **armeabi-v7a** (32-bit ARM, older devices)

If your app needs to support x86 or x86_64 (emulators), you'll need to build those separately.

## Troubleshooting

### UnsatisfiedLinkError

If you see `java.lang.UnsatisfiedLinkError: couldn't find "libtdjni.so"`:

1. Check that the AAR is properly included
2. Verify your app's ABI configuration
3. Clean and rebuild: `./gradlew clean assembleDebug`

### API Errors

Common TDLib errors:

- **`PHONE_NUMBER_INVALID`**: Phone number format is wrong (include country code)
- **`CODE_INVALID`**: Verification code is wrong
- **`API_ID_INVALID`**: Your API credentials are wrong

### Memory Issues

TDLib can use significant memory. In `AndroidManifest.xml`:

```xml
<application
    android:largeHeap="true"
    ...>
```

## Performance Tips

1. **Database location**: Store TDLib database on internal storage, not SD card
2. **Message database**: Enable it for better performance
3. **Background processing**: Handle updates in background threads
4. **Logging**: Reduce log level in production (0-1)

## Resources

- [TDLib Documentation](https://core.telegram.org/tdlib)
- [TDLib GitHub](https://github.com/tdlib/td)
- [TDLib API Methods](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1_function.html)
- [This Repository](https://github.com/karlokarate/FishIT-Player)

## License

TDLib is licensed under the Boost Software License 1.0.

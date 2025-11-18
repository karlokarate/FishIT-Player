# TDLib Android Module (BoringSSL)

This module packages TDLib (Telegram Database Library) with BoringSSL for Android as an AAR library.

## Overview

TDLib is the official Telegram client library that allows you to build Telegram clients. This module provides:

- **Native libraries** (libtdjni.so) for arm64-v8a and armeabi-v7a
- **Java bindings** (TdApi, Client, Log) in package `org.drinkless.tdlib`
- **BoringSSL** statically linked (no external OpenSSL dependencies)
- **Minimum SDK 24** (Android 7.0)

## Building

This module is built automatically by GitHub Actions workflows:

1. **Workflow 1**: `TDLib - Android Java (prebuilt BoringSSL, validated, doc-conform)` (Standart.yml)
   - Builds TDLib with BoringSSL from source
   - Generates Java bindings
   - Produces native libraries for both architectures
   - Uploads artifacts

2. **Workflow 2**: `TDLib BoringSSL AAR Release` (tdlib-boringssl-aar.yml)
   - Downloads artifacts from Workflow 1
   - Populates this module's structure
   - Builds the AAR using Gradle
   - Creates a GitHub Release

## Module Structure

```
libtd/
├── build.gradle.kts          # Gradle build configuration
├── src/main/
│   ├── AndroidManifest.xml   # Manifest with minSdk 24
│   ├── java/                 # Populated by CI workflow
│   │   └── org/drinkless/tdlib/
│   │       ├── TdApi.java    # Generated API classes
│   │       ├── Client.java   # TDLib client interface
│   │       └── Log.java      # Logging utilities
│   └── jniLibs/              # Populated by CI workflow
│       ├── arm64-v8a/
│       │   └── libtdjni.so
│       └── armeabi-v7a/
│           └── libtdjni.so
├── consumer-rules.pro        # ProGuard rules for consumers
└── proguard-rules.pro        # Module ProGuard rules
```

## Local Testing

To test the AAR build locally (after artifacts are available):

```bash
./scripts/test-aar-build.sh
```

Or manually:

```bash
./gradlew :libtd:assembleRelease
```

The AAR will be created at: `libtd/build/outputs/aar/libtd-release.aar`

## Configuration

### build.gradle.kts

- **namespace**: `org.drinkless.tdlib`
- **compileSdk**: 36
- **minSdk**: 24
- **targetSdk**: Inherited from app
- **Java compatibility**: 17

### Native Libraries

The module expects native libraries in standard Android locations:
- `src/main/jniLibs/arm64-v8a/libtdjni.so`
- `src/main/jniLibs/armeabi-v7a/libtdjni.so`

These are populated automatically by the CI workflow.

### Java Sources

The module expects Java sources in:
- `src/main/java/org/drinkless/tdlib/`

These include:
- **TdApi.java**: Auto-generated from td_api.tl schema
- **Client.java**: Native client interface from TDLib example
- **Log.java**: Logging utilities from TDLib example

## Integration

To use this AAR in another project:

### Option 1: From GitHub Release

1. Download the AAR from GitHub Releases
2. Place it in your app's `libs/` directory
3. Add to `build.gradle.kts`:

```kotlin
dependencies {
    implementation(files("libs/tdlib-android-boringssl.aar"))
}
```

### Option 2: From Local Module (Development)

In your `settings.gradle.kts`:

```kotlin
include(":app")
include(":libtd")
```

In your app's `build.gradle.kts`:

```kotlin
dependencies {
    implementation(project(":libtd"))
}
```

## Usage Example

```kotlin
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import org.drinkless.tdlib.Log

// Set log level
Log.setVerbosityLevel(2)

// Create client with update handler
val client = Client.create({ update ->
    when (update) {
        is TdApi.UpdateAuthorizationState -> {
            // Handle authorization state
        }
        is TdApi.UpdateNewMessage -> {
            // Handle new message
        }
        // ... handle other updates
    }
}, null, null)

// Send request
client.send(TdApi.GetMe()) { result ->
    when (result) {
        is TdApi.User -> {
            println("Logged in as: ${result.firstName} ${result.lastName}")
        }
        is TdApi.Error -> {
            println("Error: ${result.message}")
        }
    }
}
```

## Documentation

- [TDLib Official Documentation](https://core.telegram.org/tdlib)
- [TDLib GitHub Repository](https://github.com/tdlib/td)
- [TDLib Android Example](https://github.com/tdlib/td/tree/master/example/android)

## Architecture Details

### BoringSSL Integration

TDLib is built with BoringSSL statically linked, which means:
- No runtime OpenSSL dependencies
- Smaller APK size (compared to dynamic linking)
- Consistent crypto across Android versions
- Better security (no shared library vulnerabilities)

### Supported ABIs

Only arm64-v8a and armeabi-v7a are included because:
- These cover >99% of Android devices in use
- x86/x86_64 are rarely used in production (mainly emulators)
- Reduces AAR size significantly

If you need x86/x86_64, the build workflow can be extended to include them.

## Troubleshooting

### Missing Native Libraries

If you see errors like "couldn't find 'libtdjni.so'":
- Ensure the AAR includes the correct ABIs
- Check that your app's ABI filters match
- Verify the AAR contents: `unzip -l tdlib-android-boringssl.aar`

### Java/Kotlin Compilation Errors

If you see errors related to TdApi classes:
- Ensure you're using Java 17 or newer
- Check that the AAR is properly included in dependencies
- Clean and rebuild: `./gradlew clean :app:assembleDebug`

### ProGuard/R8 Issues

The module includes consumer ProGuard rules. If you encounter issues:
- Check `consumer-rules.pro` in the AAR
- Add explicit keep rules if needed

## License

TDLib is licensed under the Boost Software License 1.0.
This module follows the same license terms.

See: https://github.com/tdlib/td/blob/master/LICENSE_1_0.txt

# Debug Settings Module

Runtime toggle system for debug tools (Chucker HTTP Inspector, LeakCanary).

## Purpose

Provides user-controlled runtime toggles for debug tools **WITHOUT reinstall**.  
All tools are **OFF by default** to avoid performance impact.

## Features

- **DataStore-backed settings** (persisted across restarts)
- **Runtime flags** (AtomicBooleans for fast access in hot paths)
- **Gated Chucker interceptor** (minimal overhead when disabled)
- **LeakCanary runtime control** (watchers OFF by default)
- **Settings UI integration** (toggle switches in DebugScreen)

## Architecture

```
DataStore Preferences (defaults: false, false)
     ↓
DebugToolsInitializer (syncs on app startup)
     ↓
DebugFlagsHolder (AtomicBooleans)
     ↓
GatedChuckerInterceptor (OkHttp)
LeakCanary Config (watchers)
```

## Contract

### Defaults (MANDATORY)
- `debug.networkInspectorEnabled` = **false**
- `debug.leakCanaryEnabled` = **false**

### Runtime Toggle
- No reinstall needed
- No second OkHttpClient created
- No global mutable singletons (uses DI + app-scoped CoroutineScope)

### User-Agent Preservation
- User-Agent remains `FishIT-Player/2.x (Android)` at all times
- Chucker intercepts but does not modify headers

## Usage

### 1. Enable/Disable via Settings UI

```kotlin
// In DebugScreen:
settingsRepo.setNetworkInspectorEnabled(true)
settingsRepo.setLeakCanaryEnabled(true)
```

### 2. Observe State

```kotlin
@Inject lateinit var settingsRepo: DebugToolsSettingsRepository

settingsRepo.networkInspectorEnabledFlow.collect { enabled ->
    // React to state change
}
```

### 3. Fast Access in Interceptors

```kotlin
@Inject lateinit var flagsHolder: DebugFlagsHolder

override fun intercept(chain: Chain): Response {
    if (!flagsHolder.chuckerEnabled.get()) {
        return chain.proceed(chain.request()) // Fast path
    }
    // Delegate to Chucker
}
```

## Components

### DataStore Layer
- `DebugToolsSettingsRepository` - Interface for settings
- `DataStoreDebugToolsSettingsRepository` - Implementation with DataStore

### Runtime Layer
- `DebugFlagsHolder` - AtomicBooleans for fast access
- `DebugToolsInitializer` - Syncs DataStore → AtomicBooleans

### Interceptor Layer
- `GatedChuckerInterceptor` - Soft-gated Chucker with runtime toggle

### DI Layer
- `DebugSettingsModule` - Hilt module for DI

## Integration

### OkHttpClient (Xtream Transport)

```kotlin
// Debug variant: infra/networking/src/debug/.../DebugInterceptorModule.kt
@Provides
@Singleton
fun provideChuckerInterceptor(gated: GatedChuckerInterceptor): Interceptor = gated

// Release variant: infra/networking/src/release/.../DebugInterceptorModule.kt
@Provides
@Singleton
fun provideChuckerInterceptor(): Interceptor = Interceptor { chain ->
    chain.proceed(chain.request()) // No-op
}
```

### LeakCanary

```kotlin
// In DebugToolsInitializer:
settingsRepo.leakCanaryEnabledFlow.collect { enabled ->
    if (enabled) {
        // Enable watchers + heap dumps
        LeakCanary.showLeakDisplayActivityLauncherIcon(true)
    } else {
        // Disable watchers + heap dumps (DEFAULT)
        LeakCanary.showLeakDisplayActivityLauncherIcon(false)
    }
}
```

## Testing

### Unit Tests (TODO)
- DataStore defaults are false
- GatedChuckerInterceptor bypass when disabled
- GatedChuckerInterceptor delegates when enabled

### Manual Tests (TODO)
- Fresh install: both tools OFF
- Toggle Chucker: starts/stops capture immediately
- Toggle LeakCanary: starts/stops watchers immediately

## Build Variants

| Variant | Module Included | ChuckerInterceptor | LeakCanary Config |
|---------|----------------|-------------------|-------------------|
| Debug | ✅ Yes | GatedChuckerInterceptor (OFF by default) | Configured by initializer (OFF by default) |
| Release | ❌ No | No-op (inline lambda) | Not included |

## Dependencies

- `androidx.datastore:datastore-preferences:1.1.2`
- `com.chuckerteam.chucker:library:4.1.0` (debug)
- `com.squareup.leakcanary:leakcanary-android:2.14` (debug)
- `com.squareup.okhttp3:okhttp:5.0.0-alpha.14`

## Logging

All toggle state transitions are logged:

```
UnifiedLog.i("DebugToolsInitializer") { "Chucker enabled=true" }
UnifiedLog.i("DebugToolsInitializer") { "LeakCanary enabled=false" }
```

No secrets or payload bodies are logged.

## Status

✅ **Phase 1-3 Complete:**
- DataStore settings repository
- Runtime toggle holders
- Gated Chucker interceptor
- DebugToolsInitializer
- LeakCanary runtime control

⏳ **Phase 4-5 TODO:**
- Settings UI toggle switches
- "Dump heap now" button
- Warning text about profiling

⏳ **Phase 6 TODO:**
- Unit tests
- Manual testing on device

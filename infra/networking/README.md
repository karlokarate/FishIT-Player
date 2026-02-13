# infra:networking — Platform HTTP Client

**Module:** `infra/networking`  
**Package:** `com.fishit.player.infra.networking`

## Purpose

Provides the **`@PlatformHttpClient`** — the app-wide parent `OkHttpClient` instance from which all
pipeline-specific HTTP clients derive via `.newBuilder()`.

## Architecture

```
@PlatformHttpClient (this module)
  ├── Connection pool (shared across all derived clients)
  ├── Chucker interceptor (debug builds only, gated via source sets)
  ├── User-Agent interceptor ("FishIT-Player/2.x (Android)")
  └── Base timeouts: connect=30s, read=30s, write=30s
        │
        └── @XtreamHttpClient (infra/transport-xtream) — via .newBuilder()
              ├── +Accept: application/json
              ├── +callTimeout=30s
              ├── +followSslRedirects=false
              ├── +Dispatcher (parallelism control)
              │
              ├── streamingClient (lazy) — +readTimeout=120s, +callTimeout=180s
              └── playbackClient (lazy) — +followSslRedirects=true
```

## Key Files

| File | Purpose |
|------|---------|
| `PlatformHttpConfig.kt` | Shared constants: timeouts, User-Agent |
| `PlatformHttpClient.kt` | `@Qualifier` annotation for DI |
| `NetworkingModule.kt` | Hilt `@Provides @PlatformHttpClient` |
| `DebugInterceptorModule.kt` | Debug: `GatedChuckerInterceptor`; Release: no-op |

## Usage

Pipeline modules derive their specific clients:

```kotlin
@Provides @Singleton @XtreamHttpClient
fun provideXtreamOkHttpClient(
    @PlatformHttpClient platformClient: OkHttpClient,
): OkHttpClient = platformClient.newBuilder()
    .callTimeout(30, TimeUnit.SECONDS)
    .addInterceptor { /* pipeline-specific headers */ }
    .build()
```

This shares the connection pool, Chucker interceptor, and User-Agent while allowing
pipeline-specific overrides (timeouts, headers, redirects, dispatcher).

## Dependencies

- `com.squareup.okhttp3:okhttp:4.12.0` (exposed as `api`)
- `core:debug-settings` (debug builds only — for `GatedChuckerInterceptor`)
- Hilt for DI

## Rules

- **ONE parent client** — all pipeline clients MUST derive from `@PlatformHttpClient`
- **No `callTimeout`** at platform level — each pipeline sets its own
- **Chucker gating** lives HERE, not in individual pipeline modules

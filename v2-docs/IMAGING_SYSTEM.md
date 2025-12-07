# Imaging System (v2)

## Overview

The FishIT-Player v2 imaging system provides a centralized, type-safe image loading pipeline using Coil 3.

## Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                           UI Layer                                  │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  FishImage Composable                                        │   │
│  │  - Takes ImageRef (not raw URLs!)                           │   │
│  │  - Handles loading/error states                             │   │
│  └───────────────────────────┬─────────────────────────────────┘   │
│                              │                                      │
│  ┌───────────────────────────▼─────────────────────────────────┐   │
│  │  GlobalImageLoader (Coil 3 ImageLoader)                      │   │
│  │  - Single shared instance                                    │   │
│  │  - Memory cache: 25% heap                                    │   │
│  │  - Disk cache: 256-768 MB (dynamic)                         │   │
│  └───────────────────────────┬─────────────────────────────────┘   │
│                              │                                      │
│  ┌───────────────────────────▼─────────────────────────────────┐   │
│  │  ImageRefFetcher (routes by ImageRef type)                   │   │
│  │  ├─ Http → OkHttpClient                                      │   │
│  │  ├─ TelegramThumb → TelegramThumbFetcher                    │   │
│  │  └─ LocalFile → FileSystem                                   │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│                        Pipeline Layer                               │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  NormalizedMediaMetadata                                     │   │
│  │  - poster: ImageRef?                                         │   │
│  │  - backdrop: ImageRef?                                       │   │
│  │  - thumbnail: ImageRef?                                      │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
│  ⚠️ NO COIL DEPENDENCY IN PIPELINE MODULES!                        │
└─────────────────────────────────────────────────────────────────────┘
```

## Module Structure

### :core:model
- `ImageRef` sealed interface with variants:
  - `ImageRef.Http` - HTTP/HTTPS URLs with optional headers
  - `ImageRef.TelegramThumb` - TDLib file references
  - `ImageRef.LocalFile` - Local filesystem paths
- `NormalizedMediaMetadata` with imaging fields

### :core:ui-imaging
- `GlobalImageLoader` - Coil 3 setup with cache configuration
- `ImageRefFetcher` - Routes ImageRef to appropriate strategy
- `TelegramThumbFetcher` - Interface for Telegram thumb resolution
- `FishImage` - Composable for displaying ImageRef
- `ImagePreloader` - Batch preloading utility

## Usage

### Basic Image Display

```kotlin
@Composable
fun MoviePoster(movie: NormalizedMediaMetadata) {
    FishImage(
        imageRef = movie.poster,
        contentDescription = "Movie poster",
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop,
    )
}
```

### With Loading States

```kotlin
@Composable
fun MoviePosterWithStates(movie: NormalizedMediaMetadata) {
    FishImageWithStates(
        imageRef = movie.poster,
        contentDescription = "Movie poster",
        modifier = Modifier.fillMaxSize(),
        loading = { CircularProgressIndicator() },
        error = { PlaceholderImage() },
    )
}
```

### Preloading

```kotlin
// In ViewModel or UseCase
suspend fun prefetchPosters(movies: List<NormalizedMediaMetadata>) {
    val preloader = ImagePreloader(imageLoader)
    preloader.preloadFrom(context, movies, selector = { it.poster }, limit = 8)
}
```

### Setup (Application/DI)

```kotlin
// In Application.onCreate() or Hilt module
val imageLoader = GlobalImageLoader.createWithDynamicCache(
    context = applicationContext,
    okHttpClient = sharedOkHttpClient,
    telegramThumbFetcher = TelegramThumbFetcherImpl.Factory(telegramClient),
    enableCrossfade = !isTvDevice,
)

// Provide to Compose
CompositionLocalProvider(LocalImageLoader provides imageLoader) {
    AppContent()
}
```

## ImageRef Variants

### Http
```kotlin
ImageRef.Http(
    url = "https://example.com/poster.jpg",
    headers = mapOf("Authorization" to "Bearer token"),
    preferredWidth = 300,
    preferredHeight = 450,
)
```

### TelegramThumb
```kotlin
ImageRef.TelegramThumb(
    fileId = 12345,
    uniqueId = "AgACAgIAAxkB...",
    chatId = -100123456789,
    messageId = 42,
)
```

### LocalFile
```kotlin
ImageRef.LocalFile(
    path = "/data/app/cache/thumb_12345.jpg",
)
```

## Contracts

### ✅ DO
- Use `ImageRef` for all image references
- Use `FishImage` composable for display
- Let `NormalizedMediaMetadata` carry imaging fields
- Provide `TelegramThumbFetcher.Factory` at app startup

### ❌ DON'T
- Import Coil in `pipeline/*` modules
- Use raw URLs/DTOs in UI composables
- Create multiple ImageLoader instances
- Bypass GlobalImageLoader for image loading

## Cache Configuration

| Setting | Value | Notes |
|---------|-------|-------|
| Memory cache | 25% heap | Coil default |
| Disk cache (32-bit) | 256-384 MB | Dynamic |
| Disk cache (64-bit) | 512-768 MB | Dynamic |
| Connect timeout | 15s | |
| Read timeout | 30s | |
| Max concurrent requests | 16 | |
| Max requests per host | 4 | TV optimization |

## TV Optimizations

- Crossfade disabled on TV (reduces overdraw)
- Lower concurrent request limits
- Hardware bitmaps enabled by default
- FilterQuality.Low for scaling

## Testing

Run tests:
```bash
./gradlew :core:ui-imaging:test
```

## Related Documentation

- `AGENTS_V2.md` - v2 architecture rules
- `docs/fish_layout.md` - FishTile/FishRow usage
- `core/model/ImageRef.kt` - Full ImageRef API

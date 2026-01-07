---
applyTo:
  - core/ui-imaging/**
---

# 🏆 PLATIN Instructions: core/ui-imaging

> **PLATIN STANDARD** - Centralized image loading with Coil 3.
>
> **Purpose:** Provides GlobalImageLoader, custom Fetchers for ImageRef variants, and Compose integration.
> This is the ONLY module that depends on Coil. 

---

## 🔴 ABSOLUTE HARD RULES

### 1. This Module OWNS Coil
```kotlin
// ✅ ONLY ALLOWED HERE
import coil3.*
import coil3.compose.*
import coil3.request.*
import coil3.disk.*
import coil3.memory.*

// All other modules use ImageRef from core/model
// and consume images via FishImage composable
```

### 2. Custom Fetchers for Each ImageRef Variant
```kotlin
// ImageRef.Http → Standard Coil HttpUriFetcher
// ImageRef.TelegramThumb → TelegramThumbFetcher (interface here, impl in infra)
// ImageRef.LocalFile → Standard Coil FileUriFetcher
// ImageRef.InlineBytes → ImageRefFetcher (direct decode)
```

### 3. TelegramThumbFetcher is an Interface
```kotlin
// ✅ CORRECT: Interface in core/ui-imaging
interface TelegramThumbFetcher {
    suspend fun fetch(ref: ImageRef.TelegramThumb, options: Options): FetchResult

    interface Factory {
        fun create(ref: ImageRef.TelegramThumb, options: Options): TelegramThumbFetcher
    }
}

// Implementation lives in infra/transport-telegram! 
// NoOpTelegramThumbFetcher used when Telegram not configured
```

### 4. No TDLib Dependencies
```kotlin
// ❌ FORBIDDEN
import org.drinkless.td.*
import dev.g000sha256.tdl.*
import com.fishit.player.infra.transport.telegram.*

// This module defines the interface; infra implements it
```

---

## 📋 Module Contents

### GlobalImageLoader.kt
```kotlin
object GlobalImageLoader {
    object CacheConfig {
        const val MEMORY_CACHE_PERCENT = 0.25
        const val DISK_CACHE_SIZE_BYTES = 512L * 1024 * 1024  // 512 MB
        const val DISK_CACHE_DIR = "image_cache"
    }

    fun create(
        context: Context,
        okHttpClient: OkHttpClient? = null,
        telegramThumbFetcher: TelegramThumbFetcher.Factory? = null,
        enableCrossfade: Boolean = true,
    ): ImageLoader
}
```

### ImageRefFetcher.kt
```kotlin
class ImageRefFetcher(
    private val ref: ImageRef,
    private val options: Options,
    private val telegramFactory: TelegramThumbFetcher.Factory?,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        return when (ref) {
            is ImageRef.Http -> fetchHttp(ref)
            is ImageRef.TelegramThumb -> fetchTelegram(ref)
            is ImageRef.LocalFile -> fetchLocal(ref)
            is ImageRef.InlineBytes -> fetchInline(ref)
        }
    }
}
```

### Compose Integration (compose/ package)
```kotlin
@Composable
fun FishImage(
    imageRef: ImageRef?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholder: Painter? = null,
)

@Composable
fun FishImageTiered(
    primary: ImageRef?,
    fallback: ImageRef?,
    placeholder: ImageRef?,  // InlineBytes minithumbnail
    ... 
)
```

---

## ⚠️ Key Architecture Patterns

### Separation of Concerns
```
core/model           → ImageRef sealed interface (data)
core/ui-imaging      → Coil setup, Fetchers, Compose (consumption)
infra/transport-*    → TelegramThumbFetcher implementation (resolution)
```

### Tiered Loading
```kotlin
// 1. Show inline blur placeholder (ImageRef.InlineBytes) immediately
// 2. Fade in full thumbnail when downloaded
FishImageTiered(
    primary = item.thumbnail,
    fallback = item.poster,
    placeholder = item.placeholderThumbnail,  // Minithumbnail bytes
)
```

---

## 📐 Architecture Position

```
core/model (ImageRef types)
      ↓
core/ui-imaging (Coil, Fetchers) ← YOU ARE HERE
      ↓
feature/* (UI uses FishImage composable)
```

---

## ✅ PLATIN Checklist

- [ ] Only module with Coil dependencies
- [ ] TelegramThumbFetcher is interface-only (no TDLib)
- [ ] GlobalImageLoader is singleton via DI
- [ ] All ImageRef variants have corresponding Fetcher logic
- [ ] FishImage/FishImageTiered for Compose consumption
- [ ] Cache config optimized for TV/mobile (512MB disk)
- [ ] NoOpTelegramThumbFetcher fallback when Telegram disabled

---

## 📚 Reference Documents

1. `/docs/v2/IMAGING_SYSTEM.md` - Architecture overview
2. `/contracts/TELEGRAM_ID_ARCHITECTURE_CONTRACT.md` - remoteId-first
3. Coil 3 documentation
4. `core/ui-imaging/README.md`

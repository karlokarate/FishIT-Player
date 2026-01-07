---
applyTo:
  - core/player-model/**
---

# Copilot Instructions: core/player-model

> **Module Purpose:** Primitive, source-agnostic types for the player system.
> This is the BOTTOM of the player layer stack - all player modules depend on this.

---

## 🔴 HARD RULES

### 1. Primitives Only - No Behavior
```kotlin
// ✅ ALLOWED: Pure data classes and enums
data class PlaybackContext(
    val type: PlaybackType,
    val mediaId: Long?,
    val url: String,
    ... 
)
enum class PlaybackState { IDLE, BUFFERING, PLAYING, PAUSED, ENDED, ERROR }
sealed class PlaybackError { ... }

// ❌ FORBIDDEN: Any behavior or logic
fun PlaybackContext.resolveUrl() { ... }  // WRONG - belongs in player/internal
class PlaybackController { ...  }           // WRONG - belongs in player/internal
```

### 2. Zero External Dependencies
```kotlin
// ✅ ALLOWED
import kotlin.time.Duration

// ❌ FORBIDDEN - NO exceptions
import androidx.media3.*           // Media3/ExoPlayer
import com.google.android.exoplayer2.*
import dagger.hilt.*              // Hilt
import android.media.*            // Android SDK media
import okhttp3.*                  // Network
```

### 3. Source-Agnostic
```kotlin
// ❌ FORBIDDEN:  Source-specific types
data class PlaybackContext(
    val telegramFileId: Long,     // WRONG
    val xtreamStreamUrl: String,  // WRONG
)

// ✅ CORRECT:  Generic identifiers
data class PlaybackContext(
    val sourceType: SourceType,   // From core/model
    val sourceId: String,
)
```

---

## 📋 Public Surface

| Type | Purpose |
|------|---------|
| `PlaybackContext` | What to play (source-agnostic) |
| `PlaybackState` | Player state enum |
| `PlaybackError` | Error representation |
| `PlaybackType` | VOD, SERIES, LIVE |

---

## 📐 Architecture Position

```
player/internal, player/ui, playback/*
              ↓
      core/player-model (THIS)
              ↓
         core/model
```

---

## ✅ Checklist Before PR

- [ ] Only pure data classes, enums, sealed classes
- [ ] Zero external dependencies
- [ ] No business logic
- [ ] No source-specific (Telegram/Xtream) code
- [ ] No Android SDK beyond basic annotations

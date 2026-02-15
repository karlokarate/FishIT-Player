---
applyTo: 
  - core/ui-layout/**
  - core/ui-theme/**
---

# 🏆 PLATIN Instructions:  core/ui-layout + core/ui-theme

**Version:** 1.0  
**Last Updated:** 2026-02-04  
**Status:** Active

> **PLATIN STANDARD** - FishUI Design System.
>
> **Purpose:** Provides the unified UI component library (FishTile, FishRow) and theme tokens
> (FishTheme, FishColors, FishDimens) for TV and mobile platforms. 

---

## 🔴 ABSOLUTE HARD RULES

### 1. Compose-Only, Reusable Components
```kotlin
// ✅ ALLOWED
@Composable fun FishTile(...)
@Composable fun FishRow(...)
@Composable fun FishRowHeader(...)
@Composable fun FishHorizontalCard(...)
fun Modifier.tvFocusable(...)

// ❌ FORBIDDEN
// No domain logic, no network, no persistence
suspend fun fetchData() { ... }
class SomeRepository { ... }
```

### 2. Theme Tokens in core/ui-theme
```kotlin
// ✅ core/ui-theme contains:
object FishColors { val Primary = Color(0xFF2EC27E) ... }
object FishShapes { ...  }
data class FishDimens(val tileWidthDp:  Dp, .. .) { ... }
data class FishMotion(val focusScaleDurationMs: Int, .. .) { ... }
enum class FishSkin { Classic, Experience }

@Composable fun FishTheme(skin: FishSkin, content: @Composable () -> Unit)
val LocalFishDimens = staticCompositionLocalOf { ...  }
val LocalFishMotion = staticCompositionLocalOf { ...  }
```

### 3. Layout Components in core/ui-layout
```kotlin
// ✅ core/ui-layout contains:
@Composable fun FishTile(...)           // Primary content tile
@Composable fun FishRow(...)            // Horizontal scrolling row
@Composable fun FishRowHeader(...)      // Row header with count
@Composable fun FishHorizontalCard(...) // Horizontal list card
fun Modifier.tvFocusable(...)           // TV DPAD focus handling
```

### 4. No Domain Dependencies
```kotlin
// ❌ FORBIDDEN
import com.fishit.player.core.model.repository.*
import com.fishit.player.pipeline.*
import com.fishit.player.infra.*
import com.fishit.player.playback.*

// ✅ ALLOWED (for ImageRef consumption)
import com.fishit.player.core.model.ImageRef
import com.fishit.player.core.imaging.compose.FishImage
```

---

## 📋 FishTheme System

### Color Palette (Dark Moss Green)
```kotlin
object FishColors {
    // Primary (Green accents)
    val Primary = Color(0xFF2EC27E)
    val OnPrimary = Color(0xFF00140A)
    val PrimaryContainer = Color(0xFF1E8F61)

    // Background (Dark green)
    val Background = Color(0xFF0F2418)
    val Surface = Color(0xFF15301F)
    val SurfaceVariant = Color(0xFF1A3A25)

    // Focus & Glow
    val FocusGlow = Color(0xFF2EC27E)
    val FocusBorder = Color(0xFF2EC27E)

    // Source indicators
    val TelegramSource = Color(0xFF29B6F6)
    val XtreamSource = Color(0xFFFF7043)
    val LocalSource = Color(0xFF9E9E9E)
}
```

### Dimension Tokens
```kotlin
data class FishDimens(
    val tileWidthDp: Dp = 120.dp,
    val tileHeightDp:  Dp = 180.dp,
    val tileSpacing: Dp = 12.dp,
    val contentPaddingHorizontal:  Dp = 24.dp,
    val focusScale: Float = 1.05f,
    val focusBorderWidthDp: Dp = 2.dp,
    val tileCornerRadiusDp:  Dp = 8.dp,
)
```

### Skin System
```kotlin
enum class FishSkin {
    Classic,    // Standard TV/mobile experience
    Experience, // Enhanced visuals (future)
    ;
    val dimens: FishDimens get() = ...
    val motion: FishMotion get() = ...
    val isExperience: Boolean get() = this == Experience
}
```

---

## 📋 FishTile Component

```kotlin
@Composable
fun FishTile(
    title: String?,
    poster: ImageRef?,
    placeholder: ImageRef?  = null,
    modifier: Modifier = Modifier,
    sourceColors: List<Color> = emptyList(),  // Multi-source frame
    resumeFraction: Float?  = null,            // Progress bar
    aspectRatio: Float = 2f / 3f,             // Movie poster ratio
    showTitle: Boolean = true,
    isNew: Boolean = false,
    topStartBadge: (@Composable () -> Unit)? = null,
    topEndBadge:  (@Composable () -> Unit)? = null,
    overlay: (@Composable BoxScope.() -> Unit)? = null,
    onFocusChanged: ((Boolean) -> Unit)? = null,
    onClick: () -> Unit,
)
```

---

## 📋 TV Focus System

### tvFocusable Modifier
```kotlin
fun Modifier.tvFocusable(
    enabled: Boolean = true,
    onClick: () -> Unit,
    onFocusChanged: ((Boolean) -> Unit)? = null,
    focusScale: Float?  = null,          // Override from FishDimens
    enableScale: Boolean = true,
    enableGlow: Boolean = true,
    focusGlowColor: Color = FishColors.FocusGlow,
    focusGlowWidth:  Dp?  = null,
    cornerRadius: Dp?  = null,
): Modifier
```

### Focus Behavior
- **TV**:  DPAD navigation with visible focus ring and scale
- **Mobile**:  Touch with optional focus visuals
- **focusGroup()**: Container for DPAD navigation scope

---

## 📐 Architecture Position

```
core/ui-theme (colors, dimens, tokens)
      ↓
core/ui-layout (FishTile, FishRow, modifiers) ← YOU ARE HERE
      ↓
feature/* (screens compose FishUI components)
```

---

## ✅ PLATIN Checklist

- [ ] Components are pure UI (no domain logic)
- [ ] All colors defined in FishColors
- [ ] All dimensions via FishDimens/LocalFishDimens
- [ ] TV focus via tvFocusable modifier and focusGroup()
- [ ] No repository or use case imports
- [ ] No pipeline or transport imports
- [ ] FishTheme wraps all app content
- [ ] Skin system supports Classic/Experience
- [ ] Accessible focus indicators on TV

---

## 📚 Reference Documents

1. `/AGENTS.md` - UI Layout Centralization (Fish*) section
2. `/contracts/GLOSSARY_v2_naming_and_modules.md` - Full design spec
3. Material 3 design guidelines
4. Android TV focus best practices

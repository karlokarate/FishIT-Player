# Gold: UI Focus & TV Navigation Patterns

## Overview
This document captures the proven patterns from v1's TV focus handling and DPAD navigation that should be preserved in v2.

## Source Files
- `FocusKit.kt` (1389 lines) - Centralized focus management for TV
- `FocusRowEngine.kt` (267 lines) - Row-based focus navigation
- UI components using focus patterns (TvButtons, FishRow, etc.)

---

## 1. Centralized Focus Management

### Key Pattern: FocusKit as Single Entry Point
**v1 Implementation:** `FocusKit.kt`

```kotlin
/**
 * GOLD: Single Import Surface for Focus Handling
 * 
 * Why this works:
 * - All focus-related imports from one file
 * - Screens can: import com.chris.m3usuite.ui.focus.FocusKit.*
 * - Consistent API across entire app
 * - Easier to evolve without breaking screens
 */

// Usage in screens:
import com.chris.m3usuite.ui.focus.FocusKit.*

@Composable
fun MyScreen() {
    focusGroup {
        TvButton(
            modifier = Modifier.tvClickable { /* action */ }
        )
    }
}
```

**Why preserve:** Single source of truth for focus behavior prevents inconsistencies.

---

## 2. Focus Zone System

### Key Pattern: Named Focus Zones
**v1 Implementation:** `FocusZoneId` enum

```kotlin
/**
 * GOLD: Focus Zone Registry
 * 
 * Why this works:
 * - Logical zones instead of manual FocusRequester tracking
 * - TV input system can target zones by name
 * - Zones register/unregister automatically with composition
 * - Programmatic focus movement between zones
 */
enum class FocusZoneId {
    PLAYER_CONTROLS,     // Play/pause, seek bar, volume
    QUICK_ACTIONS,       // CC, aspect ratio, speed, PiP
    TIMELINE,            // Seek bar / progress indicator
    EPG_OVERLAY,         // EPG program guide
    LIVE_LIST,           // Live channel selection
    LIBRARY_ROW,         // Content rows
    SETTINGS_LIST,       // Settings items
    PROFILE_GRID,        // Profile selection
    MINI_PLAYER,         // Mini player overlay
    PRIMARY_UI,          // Main app UI area
}

// Thread-safe registry
private val focusZoneRegistry = ConcurrentHashMap<FocusZoneId, FocusRequester>()

// Automatic registration
@Composable
fun registerFocusZone(zoneId: FocusZoneId) {
    val requester = remember { FocusRequester() }
    DisposableEffect(zoneId) {
        focusZoneRegistry[zoneId] = requester
        onDispose {
            focusZoneRegistry.remove(zoneId)
        }
    }
    return requester
}

// Programmatic focus movement
fun requestFocusZone(zoneId: FocusZoneId) {
    focusZoneRegistry[zoneId]?.requestFocus()
}
```

**Why preserve:** Enables TV input system to control focus without coupling to UI structure.

### Focus Zone Tracking
**Pattern:** Track currently active zone

```kotlin
/**
 * GOLD: Active Zone Tracking
 * 
 * Why this works:
 * - Know which zone has focus
 * - Enable zone-specific key handling
 * - Support zone-based focus restoration
 */
private var _currentFocusZone: FocusZoneId? = null

fun onZoneFocused(zoneId: FocusZoneId) {
    _currentFocusZone = zoneId
    // Can emit events for analytics, logging, etc.
}

fun getCurrentFocusZone(): FocusZoneId? = _currentFocusZone
```

**Why preserve:** Essential for TV input system and focus debugging.

---

## 3. Focus Group Pattern

### Key Pattern: Scoped Focus Containers
**v1 Implementation:** `focusGroup()` modifier

```kotlin
/**
 * GOLD: Focus Group Containers
 * 
 * Why this works:
 * - Groups related focusable items
 * - Defines focus boundaries
 * - Enables initial focus on group entry
 * - Prevents focus escape during navigation
 */
fun Modifier.focusGroup(
    initialFocus: FocusRequester? = null
): Modifier = composed {
    val requester = initialFocus ?: remember { FocusRequester() }
    
    LaunchedEffect(Unit) {
        // Request focus on group when it enters composition
        requester.requestFocus()
    }
    
    this.then(
        Modifier.focusProperties {
            // Optional: constrain focus within group
            canFocus = true
        }
    )
}

// Usage:
@Composable
fun SettingsScreen() {
    Column(modifier = Modifier.focusGroup()) {
        TvButton("Option 1", modifier = Modifier.initialFocus())
        TvButton("Option 2")
        TvButton("Option 3")
    }
}
```

**Why preserve:** Natural way to structure TV navigation hierarchies.

---

## 4. TV-Specific Modifiers

### Key Pattern: tvClickable / tvFocusableItem
**v1 Implementation:** `FocusKit` modifier extensions

```kotlin
/**
 * GOLD: TV-First Interaction Modifiers
 * 
 * Why this works:
 * - Combines focus, click, and visual feedback
 * - Handles DPAD center press and Enter key
 * - Shows focus indicator automatically
 * - Triggers on key down (not key up) for responsiveness
 */
fun Modifier.tvClickable(
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    
    // Scale on press for tactile feedback
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1.0f
    )
    
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .focusable(enabled = enabled, interactionSource = interactionSource)
        .clickable(
            enabled = enabled,
            interactionSource = interactionSource,
            indication = null,  // Custom focus indicator
            onClick = onClick
        )
        .then(
            if (isFocused) Modifier.drawFocusIndicator() else Modifier
        )
}

fun Modifier.tvFocusableItem(
    onClick: (() -> Unit)? = null
): Modifier = composed {
    if (onClick != null) {
        tvClickable(onClick = onClick)
    } else {
        focusable()
    }
}
```

**Why preserve:** Consistent TV interaction behavior across all UI components.

### Focus Indicator
**Pattern:** Custom focus glow effect

```kotlin
/**
 * GOLD: TV Focus Glow
 * 
 * Why this works:
 * - Clear visual indicator of focused item
 * - Uses theme colors for consistency
 * - Smooth animation on focus change
 * - Accessible contrast ratio
 */
fun Modifier.drawFocusIndicator(
    color: Color = MaterialTheme.colorScheme.primary,
    strokeWidth: Dp = 3.dp
): Modifier = composed {
    val strokePx = with(LocalDensity.current) { strokeWidth.toPx() }
    
    drawWithContent {
        drawContent()
        drawRoundRect(
            color = color,
            style = Stroke(width = strokePx),
            cornerRadius = CornerRadius(8.dp.toPx())
        )
    }
}
```

**Why preserve:** Essential for TV navigation visibility.

---

## 5. Initial Focus Handling

### Key Pattern: Automatic Focus Restoration
**v1 Implementation:** Focus restoration on screen entry

```kotlin
/**
 * GOLD: Initial Focus on Screen Entry
 * 
 * Why this works:
 * - First focusable item gets focus automatically
 * - Works with back stack navigation
 * - Prevents "no focus" state on screen load
 * - Restores last focused item when returning
 */
@Composable
fun Modifier.initialFocus(): Modifier = composed {
    val requester = remember { FocusRequester() }
    
    LaunchedEffect(Unit) {
        // Delay to ensure layout is complete
        kotlinx.coroutines.delay(100)
        requester.requestFocus()
    }
    
    this.focusRequester(requester)
}

// Usage in screen:
@Composable
fun LibraryScreen() {
    Column {
        TvButton(
            text = "Movies",
            modifier = Modifier.initialFocus()  // Gets focus on entry
        )
        TvButton(text = "TV Shows")
        TvButton(text = "Live TV")
    }
}
```

**Why preserve:** Prevents users from being stuck without focus.

### Focus Memory
**Pattern:** Remember focus state across navigation

```kotlin
/**
 * GOLD: Focus Position Memory
 * 
 * Why this works:
 * - Remember which row/item was focused
 * - Restore on back navigation
 * - Persist across configuration changes
 * - Use rememberSaveable for process death
 */
@Composable
fun rememberFocusState(): FocusState {
    return rememberSaveable(saver = FocusStateSaver) {
        FocusState()
    }
}

data class FocusState(
    var rowIndex: Int = 0,
    var itemIndex: Int = 0
)

// Usage:
@Composable
fun HomeScreen() {
    val focusState = rememberFocusState()
    
    LazyColumn {
        itemsIndexed(rows) { rowIdx, row ->
            FishRow(
                items = row.items,
                initialFocusIndex = if (rowIdx == focusState.rowIndex) {
                    focusState.itemIndex
                } else {
                    0
                },
                onFocusChanged = { itemIdx ->
                    focusState.rowIndex = rowIdx
                    focusState.itemIndex = itemIdx
                }
            )
        }
    }
}
```

**Why preserve:** Professional TV UX expects focus memory.

---

## 6. DPAD Key Handling

### Key Pattern: Key Event Interception
**v1 Implementation:** `onPreviewKeyEvent`

```kotlin
/**
 * GOLD: Custom Key Handling
 * 
 * Why this works:
 * - Intercept keys before default handling
 * - Handle long press differently than short press
 * - Custom behavior for specific keys
 * - Prevent default behavior when needed
 */
fun Modifier.handleDpadKeys(
    onLeft: (() -> Unit)? = null,
    onRight: (() -> Unit)? = null,
    onUp: (() -> Unit)? = null,
    onDown: (() -> Unit)? = null,
    onCenter: (() -> Unit)? = null,
    consumeEvent: Boolean = true
): Modifier = this.onPreviewKeyEvent { event ->
    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
    
    when (event.key) {
        Key.DirectionLeft -> onLeft?.invoke()
        Key.DirectionRight -> onRight?.invoke()
        Key.DirectionUp -> onUp?.invoke()
        Key.DirectionDown -> onDown?.invoke()
        Key.DirectionCenter, Key.Enter -> onCenter?.invoke()
        else -> return@onPreviewKeyEvent false
    }
    consumeEvent
}
```

**Why preserve:** Enables custom navigation patterns for complex UI.

### Long Press Detection
**Pattern:** Distinguish between short and long press

```kotlin
/**
 * GOLD: Long Press Detection for TV
 * 
 * Why this works:
 * - Long press PLAY = toggle mini player focus
 * - Long press OK = context menu
 * - Track press duration in coroutine
 * - Cancel on key up
 */
@Composable
fun Modifier.handleLongPress(
    onLongPress: () -> Unit,
    onShortPress: () -> Unit,
    longPressThreshold: Long = 800L
): Modifier = composed {
    var pressJob: Job? by remember { mutableStateOf(null) }
    val scope = rememberCoroutineScope()
    
    this.onPreviewKeyEvent { event ->
        when (event.type) {
            KeyEventType.KeyDown -> {
                if (event.key == Key.DirectionCenter && pressJob == null) {
                    pressJob = scope.launch {
                        delay(longPressThreshold)
                        onLongPress()
                        pressJob = null
                    }
                    true
                } else false
            }
            KeyEventType.KeyUp -> {
                if (event.key == Key.DirectionCenter) {
                    pressJob?.let {
                        it.cancel()
                        pressJob = null
                        onShortPress()
                    }
                    true
                } else false
            }
            else -> false
        }
    }
}
```

**Why preserve:** Advanced TV interaction pattern for power users.

---

## 7. Row-Based Navigation

### Key Pattern: FocusRowEngine
**v1 Implementation:** `FocusRowEngine.kt`

```kotlin
/**
 * GOLD: Horizontal Row Navigation
 * 
 * Why this works:
 * - Handles left/right within row
 * - Handles up/down between rows
 * - Brings items into view automatically
 * - Circular navigation option
 * - Memory-efficient for large lists
 */
@Composable
fun FishRow(
    items: List<MediaItem>,
    onItemClick: (MediaItem) -> Unit,
    initialFocusIndex: Int = 0,
    circular: Boolean = false
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialFocusIndex)
    val scope = rememberCoroutineScope()
    var focusedIndex by remember { mutableStateOf(initialFocusIndex) }
    
    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        itemsIndexed(items) { index, item ->
            val bringIntoView = remember { BringIntoViewRequester() }
            val requester = remember { FocusRequester() }
            
            TvCard(
                item = item,
                modifier = Modifier
                    .focusRequester(requester)
                    .bringIntoViewRequester(bringIntoView)
                    .onFocusEvent { focusState ->
                        if (focusState.isFocused) {
                            focusedIndex = index
                            scope.launch { bringIntoView.bringIntoView() }
                        }
                    }
                    .handleDpadKeys(
                        onLeft = {
                            if (index > 0 || circular) {
                                val prevIndex = if (index > 0) index - 1 else items.lastIndex
                                // Request focus on previous item
                            }
                        },
                        onRight = {
                            if (index < items.lastIndex || circular) {
                                val nextIndex = if (index < items.lastIndex) index + 1 else 0
                                // Request focus on next item
                            }
                        }
                    )
                    .tvClickable { onItemClick(item) }
            )
            
            if (index == initialFocusIndex) {
                LaunchedEffect(Unit) {
                    delay(100)
                    requester.requestFocus()
                }
            }
        }
    }
}
```

**Why preserve:** Standard pattern for media browsing on TV.

---

## 8. Form Components for TV

### Key Pattern: TV-First Form Inputs
**v1 Implementation:** TV form components

```kotlin
/**
 * GOLD: TV-Optimized Form Controls
 * 
 * Components:
 * - TvSwitch: Toggle with left/right to change
 * - TvSlider: Horizontal slider with left/right
 * - TvTextField: On-focus shows IME or custom keyboard
 * - TvSelect: Dropdown with up/down navigation
 * 
 * Why this works:
 * - DPAD-friendly instead of touch-first
 * - Clear focus indicators
 * - Value shown before interaction
 * - Keyboard shortcuts (type to search)
 */

@Composable
fun TvSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .tvClickable { onCheckedChange(!checked) }
            .handleDpadKeys(
                onLeft = { if (checked) onCheckedChange(false) },
                onRight = { if (!checked) onCheckedChange(true) }
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label)
        Spacer(Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}
```

**Why preserve:** Forms are usable with DPAD, not just touch.

---

## 9. v2 Porting Guidance

### What to Port

1. **FocusKit Entry Point** → Port to `core/ui-focus/FocusKit.kt`
   - Keep single import surface
   - Update package imports
   - Add kdoc for v2

2. **Focus Zone System** → Port to `core/ui-focus/FocusZones.kt`
   - Keep FocusZoneId enum
   - Keep registry pattern
   - Add zone tracking

3. **tvClickable Modifiers** → Port to `core/ui-common/TvModifiers.kt`
   - Keep focus + click + scale pattern
   - Keep focus indicator
   - Update theme imports

4. **Focus Groups** → Port to `core/ui-focus/FocusGroup.kt`
   - Keep scoped container pattern
   - Keep initial focus handling
   - Add focus memory

5. **Row Navigation** → Port to `core/ui-layout/FishRow.kt` (already exists)
   - Keep horizontal navigation
   - Keep bring-into-view
   - Verify existing implementation

### What to Change

1. **Package Names**
   - v1: `com.chris.m3usuite.ui.focus`
   - v2: `com.fishit.player.core.ui.focus`
   - Reason: Module structure

2. **Logging**
   - v1: Debug logging with GlobalDebug
   - v2: Use UnifiedLog from infra
   - Reason: Centralized logging

3. **No RouteTag**
   - v1: Uses RouteTag for navigation logging
   - v2: Use feature-based analytics
   - Reason: Module boundaries

### Implementation Phases

**Phase 1: Core Focus** (PRIORITY)
- [ ] Port FocusKit entry point
- [ ] Port tvClickable/tvFocusableItem
- [ ] Port focus indicators
- [ ] Add unit tests

**Phase 2: Focus Zones**
- [ ] Port FocusZoneId enum
- [ ] Port zone registry
- [ ] Port zone tracking
- [ ] Integrate with TV input system

**Phase 3: Advanced Patterns**
- [ ] Port focus groups
- [ ] Port initial focus handling
- [ ] Port focus memory
- [ ] Add composition locals

**Phase 4: Form Components**
- [ ] Port TvSwitch
- [ ] Port TvSlider
- [ ] Port TvTextField
- [ ] Port TvSelect

---

## Key Principles

1. **Focus is First-Class** - On TV, focus is as important as layout
2. **DPAD Over Touch** - Design for DPAD, touch is secondary
3. **Visual Feedback** - Clear indicators for focused state
4. **No Dead Ends** - Always provide way to escape focus traps
5. **Memory Matters** - Remember focus position across navigation

---

## References

- v1 Source: `/legacy/v1-app/app/src/main/java/com/chris/m3usuite/ui/focus/`
- v2 Target: `/core/ui-focus/` (to be created) or `/feature/*/ui/`
- TV Contract: `/docs/v2/internal-player/INTERNAL_PLAYER_TV_INPUT_CONTRACT_PHASE6.md`
- Focus Docs: Check existing `docs/` for Fish* layout patterns

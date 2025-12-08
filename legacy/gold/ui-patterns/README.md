# Gold: UI Patterns

Curated patterns from the v1 TV/Android UI implementation.

## Documentation

✅ **`GOLD_FOCUS_KIT.md`** (516 lines) - Complete extraction of TV focus patterns

## Key Patterns Extracted

1. **FocusKit Entry Point** – Single import surface for all focus APIs
2. **Focus Zone System** – Named zones with automatic registration/unregistration
3. **Focus Group Pattern** – Scoped containers with initial focus handling
4. **tvClickable Modifiers** – Combines focus, click, scale animation, and visual feedback
5. **Focus Indicators** – Custom glow effect with theme colors
6. **Initial Focus Handling** – Automatic focus on screen entry
7. **Focus Memory** – Remember position across navigation (rememberSaveable)
8. **DPAD Key Handling** – Custom key interception with long press detection
9. **Row Navigation** – FocusRowEngine for horizontal navigation
10. **TV Form Components** – DPAD-first Switch, Slider, TextField, Select

## v2 Target Modules

- `core/ui-focus/` (to be created) - FocusKit and focus management
- `core/ui-common/` - TV modifiers and utilities
- `core/ui-layout/` - FishRow and layout components (already exists)

## v2 Status

❌ **Not Started** - **HIGH PRIORITY** for Phase 1

Essential for TV UX. Should be ported before other UI work.

## Porting Checklist

### Phase 1: Core Focus (PRIORITY)
- [ ] Port FocusKit entry point to core/ui-focus/
- [ ] Port tvClickable/tvFocusableItem modifiers
- [ ] Port focus indicators (custom glow)
- [ ] Write unit tests

### Phase 2: Focus Zones
- [ ] Port FocusZoneId enum
- [ ] Port zone registry (ConcurrentHashMap)
- [ ] Port zone tracking
- [ ] Integrate with TV input system

### Phase 3: Advanced Patterns
- [ ] Port focus groups
- [ ] Port initial focus handling
- [ ] Port focus memory (rememberSaveable)
- [ ] Add composition locals

### Phase 4: Form Components
- [ ] Port TvSwitch
- [ ] Port TvSlider
- [ ] Port TvTextField
- [ ] Port TvSelect

## Key Principles

1. **Focus is First-Class** - On TV, focus is as important as layout
2. **DPAD Over Touch** - Design for DPAD, touch is secondary
3. **Visual Feedback** - Clear indicators for focused state
4. **No Dead Ends** - Always provide way to escape focus traps
5. **Memory Matters** - Remember focus position across navigation

## References

- **Gold Doc:** `GOLD_FOCUS_KIT.md` (this folder)
- **v1 Source:** `/legacy/v1-app/app/src/main/java/com/chris/m3usuite/ui/focus/`
- **TV Contract:** `/docs/v2/internal-player/INTERNAL_PLAYER_TV_INPUT_CONTRACT_PHASE6.md`

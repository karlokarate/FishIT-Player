---
applyTo: '**'
---

# SSOT Enforcement — Zero-Duplicate Policy (MANDATORY)

**Version:** 1.0  
**Status:** ✅ Active — Applies globally to ALL files, ALL layers, ALL scopes  
**Authority:** HARD RULE — Non-negotiable, no exceptions

> **Kernregel:** Für jeden eindeutigen Zweck darf es im gesamten Codebase **genau EINE SSOT-Implementierung** geben.
> Duplikate, semantische Duplikate und legacy-Überbleibsel MÜSSEN konsolidiert oder gelöscht werden.

---

## 🚨 HARD RULE: One Purpose — One Implementation

### Definition

A "purpose" (Zweck) is any distinct functional responsibility, including but not limited to:

| Category | Examples |
|----------|----------|
| **Field Mapping** | Mapping resolution height → display label, mapping source type → priority |
| **Format Conversion** | File size → human-readable string, MIME type → container name |
| **Data Encoding/Decoding** | Playback hints JSON encode/decode, key naming schemes |
| **Sync Operations** | Triggering/starting/pausing/stopping a sync, incremental vs full sync |
| **Category Filtering** | Syncing only selected categories vs all categories |
| **UI Rendering** | Displaying a poster/backdrop/thumbnail, quality badges |
| **Key/ID Generation** | Source keys, canonical keys, variant keys, slug generation |
| **Label Building** | Source labels, quality labels, resolution labels |
| **Priority Calculation** | Source priority ranking, quality bonuses |
| **Error Handling** | Retry logic, fallback strategies for a specific operation |

### The Rule

```
FOR EVERY unique purpose P in the codebase:
  COUNT(implementations of P) MUST == 1
  
  IF COUNT > 1:
    → CONSOLIDATE into ONE SSOT implementation
    → DELETE all others — no exceptions
```

---

## 🔍 Mandatory Detection — Every Agent, Every Task

### When Analyzing or Editing Code

Every agent MUST actively watch for duplicate implementations during ANY work:

1. **During file reads:** If you see a function/class/helper that does something you've seen elsewhere → FLAG IT
2. **During issue work:** Before implementing, search for existing implementations of the same purpose
3. **During refactoring:** Audit the target area for hidden duplicates
4. **During code review:** Flag any PR that introduces a second implementation of an existing purpose

### Detection Checklist

Ask yourself for EVERY function/class you encounter:

- [ ] Does another file implement the same logic? (even with different naming)
- [ ] Does a legacy file have a version of this? (check `legacy/` for reference)
- [ ] Does another layer have its own copy? (Pipeline vs Data vs Core vs Infra)
- [ ] Does this file have multiple private helpers serving the same purpose?
- [ ] Are there fallbacks that duplicate the primary implementation?

---

## ⚡ Mandatory Action — Decision Tree

When a duplicate is found, follow this decision tree **immediately**:

```
DUPLICATE FOUND
  │
  ├─ Is there already a superior SSOT implementation?
  │   ├─ YES → DELETE the duplicate. Do NOT "align" it with SSOT.
  │   └─ NO  → CONSOLIDATE best parts of all implementations → CREATE one SSOT → DELETE all others.
  │
  ├─ Does the duplicate (e.g., legacy) contain something the SSOT is missing?
  │   ├─ YES → MERGE that capability INTO the SSOT → then DELETE the duplicate
  │   └─ NO  → DELETE the duplicate immediately
  │
  ├─ Is the duplicate a fallback?
  │   ├─ Does the SSOT already handle all error cases?
  │   │   ├─ YES → DELETE the fallback (it's dead code that may produce wrong output)
  │   │   └─ NO  → MERGE missing error handling INTO SSOT → DELETE fallback
  │   └─ Does the fallback produce different field names/formats?
  │       └─ YES → DELETE IMMEDIATELY (it produces data that downstream can't read)
  │
  └─ Is the fix too large for current scope?
      ├─ Mark as TODO with clear description
      ├─ Add to ROADMAP.md under "SSOT Consolidation Debt"
      └─ NEVER skip silently — document and roadmap EVERY finding
```

### FORBIDDEN Actions

```yaml
NEVER_DO:
  - Skip a duplicate finding ("it's not in scope")
  - Align a duplicate with SSOT instead of deleting it
  - Keep a fallback that duplicates SSOT logic "just in case"
  - Create a new implementation when an SSOT already exists
  - Import a legacy implementation when a v2 SSOT exists
  - Leave unused imports/implementations without evaluation
  - Rationalize duplicates as "not true duplicates" when they serve the same purpose
```

---

## 🏷️ Naming Clarity — Repo-Wide Disambiguation

### The Problem

The same word (e.g., `source`, `sourceId`, `sourceKey`) can mean completely different things in different contexts:

| Term | Could Mean | Actual Meaning Depends On |
|------|-----------|--------------------------|
| `source` | Xtream provider, VOD category, pipeline origin, media file | Context/layer |
| `sourceId` | Xtream stream ID, Telegram message ID, file path hash | Source type |
| `sourceKey` | Composite key `type:account:id`, or just the ID part | File location |
| `type` | WorkType (MOVIE), SourceType (XTREAM), ContentType (VOD) | Domain |
| `id` | Database ID, external API ID, composite canonical ID | Entity |

### Naming Rules

1. **Every name MUST be unambiguous without reading surrounding code.**
   - BAD: `fun getSource()` — source of what?
   - GOOD: `fun getXtreamStreamSource()` or `fun getMediaSourceRef()`

2. **When a term is used across layers, it MUST have the same meaning everywhere.**
   - If `sourceType` means `SourceType.XTREAM` in Pipeline, it must mean the same in Data layer.
   - If it means something different, use a DIFFERENT name.

3. **Prefer qualified/namespaced names over generic ones.**
   - BAD: `priority`, `label`, `hints`
   - GOOD: `sourcePriority`, `resolutionLabel`, `playbackHints`

4. **When renaming for clarity, rename repo-wide** (all usages, all layers).

5. **Reference the GLOSSARY** (`/contracts/GLOSSARY_v2_naming_and_modules.md`) as the authoritative naming contract.

---

## 📍 SSOT Placement Rules

Each SSOT implementation MUST live in the **correct architectural layer**:

| Purpose Type | Correct Layer | Example |
|-------------|---------------|---------|
| Pure data mapping (height→label) | `core/model/util/` | `ResolutionLabel.kt` |
| Entity-specific logic | `core/model/` or `core/persistence/` | `NX_Work`, `NxKeyGenerator` |
| Transport-specific encoding | `infra/transport-*/` | `XtreamApiClient` |
| Cross-source data mapping | `infra/data-*/mapper/` | `PlaybackHintsDecoder` |
| Pipeline-specific extraction | `pipeline/*/` | `TelegramMediaMapper` |
| UI presentation logic | `feature/*/ui/` or `core/ui-*/` | Composable helpers |
| Sync orchestration | `core/catalog-sync/` | `CatalogSyncWorkScheduler` |

**Rule:** A utility in `core/model/util/` can be used by ALL layers. Never duplicate it into a higher layer.

---

## 📋 SSOT Registry — Known Consolidations

These SSOTs are established and MUST be used instead of creating alternatives:

| Purpose | SSOT File | Key Function | Used By |
|---------|-----------|-------------|---------|
| Resolution → display label | `core/model/util/ResolutionLabel.kt` | `fromHeight()`, `badgeLabel()` | UI, Data, Mappers |
| File size → human string | `core/model/util/FileSizeFormatter.kt` | `format()` | UI, Data |
| MIME → container name | `core/model/util/ContainerGuess.kt` | `fromMimeType()` | Data, Mappers |
| Source priority ranking | `core/model/util/SourcePriority.kt` | `basePriority()`, `totalPriority()` | Data, CatalogSync |
| Playback hints encode/decode | `infra/data-nx/mapper/base/PlaybackHintsDecoder.kt` | `decodeFromVariant()`, `encodeToJson()` | Data repositories |
| Playback hint key constants | `core/model/PlaybackHintKeys.kt` | `Xtream.*`, `Telegram.*` | ALL layers |
| Source label building | `infra/data-nx/mapper/TypeMappers.kt` | `SourceLabelBuilder` | Data repositories |
| Quality tag from height | `core/model/MediaVariant.kt` | `QualityTags.fromResolutionHeight()` | Pipeline, Data |
| Slug generation | `core/model/util/SlugGenerator.kt` | `toSlug()` | Persistence, Data |

**When working on code that needs any of these capabilities → USE THE SSOT. Never create a new one.**

---

## 🔄 Lifecycle of SSOT Findings

```
1. DETECT  → Agent discovers duplicate during any work
2. ASSESS  → Is consolidation needed? (always YES if same purpose)
3. ACT     → Either:
   a) FIX NOW: Consolidate + delete duplicates
   b) DOCUMENT: Create TODO + add to ROADMAP.md (only if genuinely too large)
4. VERIFY  → Build compiles, no remaining duplicates for that purpose
5. REPORT  → Include in commit message / session summary
```

### TODO Format (when deferring)

```kotlin
// TODO(SSOT-CONSOLIDATION): [PURPOSE] has [N] duplicate implementations
// Files: [list of files]
// SSOT candidate: [which implementation should be the SSOT]
// Tracked in: ROADMAP.md → SSOT Consolidation Debt
```

---

## 🧪 Verification — Post-Consolidation Checklist

After consolidating any SSOT:

- [ ] Old implementations are DELETED (not commented out, not deprecated — DELETED)
- [ ] All callers use the SSOT (grep for old function names confirms zero hits outside legacy/)
- [ ] SSOT lives in the correct architectural layer
- [ ] SSOT function names follow GLOSSARY naming conventions
- [ ] Build compiles successfully
- [ ] No new imports of legacy implementations
- [ ] SSOT Registry in this document is updated if a new SSOT was established

---

## 📝 Examples — Right vs Wrong

### ❌ WRONG: Multiple implementations of file size formatting

```kotlin
// In MediaSourceRef.kt
private fun formatFileSize(bytes: Long): String { ... }

// In SourceBadge.kt 
private fun formatFileSize(bytes: Long): String { ... }

// In WorkDetailDtos.kt
val fileSizeLabel get() = bytes?.let { when { bytes >= 1_073_741_824 -> ... } }
```

### ✅ RIGHT: One SSOT, all callers delegate

```kotlin
// core/model/util/FileSizeFormatter.kt — THE SSOT
object FileSizeFormatter {
    fun format(bytes: Long): String = ...
}

// All callers:
FileSizeFormatter.format(bytes)  // single line delegation
```

### ❌ WRONG: Fallback that produces different keys

```kotlin
// Producer A writes: playbackHints["xtream.streamId"] = "123"
// Producer B writes: playbackHints["streamId"] = "123"        // ← WRONG KEY
// Consumer reads:    playbackHints[PlaybackHintKeys.Xtream.STREAM_ID]  // only reads "xtream.streamId"
// Result: Producer B's data is NEVER consumed → silent data loss
```

### ✅ RIGHT: All producers use SSOT key constants

```kotlin
// PlaybackHintKeys.Xtream.STREAM_ID is the single source for the key name
hints[PlaybackHintKeys.Xtream.STREAM_ID] = streamId  // everywhere, no exceptions
```

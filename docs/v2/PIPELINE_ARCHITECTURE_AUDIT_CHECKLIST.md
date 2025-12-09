# Pipeline Architecture Audit Checklist

> **MANDATORY** — This checklist MUST be completed after ANY changes to pipeline, transport, data, or playback modules.

---

## When to Run This Audit

Execute this audit after modifying ANY file in:

- `pipeline/**`
- `infra/transport-*/**`
- `infra/data-*/**`
- `playback/**`
- `core/model/**` (if RawMediaMetadata or related types change)

---

## Quick Reference: Layer Hierarchy

```
Transport → Pipeline → Data → Domain → UI
     ↓          ↓         ↓        ↓      ↓
   TgMessage  Internal   RawMedia  UseCases  Screens
   TgChat     DTOs       Metadata
```

**Dependencies flow UPWARD only.** No layer may import from a higher layer.

---

## 1. Transport Layer Audit

**Modules:** `infra/transport-telegram`, `infra/transport-xtream`

| Check | Command | Expected |
|-------|---------|----------|
| No pipeline imports | `grep -r "import.*pipeline\." infra/transport-*/src/main` | No matches |
| No data imports | `grep -r "import.*infra.data\." infra/transport-*/src/main` | No matches |
| No playback imports | `grep -r "import.*playback\." infra/transport-*/src/main` | No matches |
| Compiles | `./gradlew :infra:transport-telegram:compileDebugKotlin :infra:transport-xtream:compileDebugKotlin` | BUILD SUCCESSFUL |

**Transport MAY import:**
- `core/model` (for shared types like `SourceType`)
- External SDKs (TDLib, OkHttp)
- `infra/logging`

---

## 2. Pipeline Layer Audit

**Modules:** `pipeline/telegram`, `pipeline/xtream`

| Check | Command | Expected |
|-------|---------|----------|
| No persistence imports | `grep -r "import.*persistence\." pipeline/*/src/main` | No matches |
| No data imports | `grep -r "import.*infra.data\." pipeline/*/src/main` | No matches |
| No playback imports | `grep -r "import.*playback\." pipeline/*/src/main` | No matches |
| No Repository imports | `grep -r "import.*Repository" pipeline/*/src/main` | No matches |
| No TMDB/IMDB calls | `grep -r "tmdb\|imdb\|themoviedb" pipeline/*/src/main --ignore-case` | No matches |
| Produces RawMediaMetadata | Check `toRawMediaMetadata()` extensions exist | ✅ |
| Compiles | `./gradlew :pipeline:telegram:compileDebugKotlin :pipeline:xtream:compileDebugKotlin` | BUILD SUCCESSFUL |

**Pipeline MAY import:**
- `core/model` (RawMediaMetadata, ImageRef, SourceType, etc.)
- `infra/transport-*` (transport clients)
- `infra/logging`
- Own internal DTOs (TelegramMediaItem, XtreamVodItem — internal only)

**Pipeline MUST NOT export:**
- Internal DTOs to any other layer

---

## 3. Data Layer Audit

**Modules:** `infra/data-telegram`, `infra/data-xtream`

| Check | Command | Expected |
|-------|---------|----------|
| No pipeline imports | `grep -r "import.*pipeline\." infra/data-*/src/main` | **No matches** |
| No playback imports | `grep -r "import.*playback\." infra/data-*/src/main` | No matches |
| Uses RawMediaMetadata | `grep -r "RawMediaMetadata" infra/data-*/src/main` | Matches found |
| Compiles | `./gradlew :infra:data-telegram:compileDebugKotlin :infra:data-xtream:compileDebugKotlin` | BUILD SUCCESSFUL |

**Data MAY import:**
- `core/model` (RawMediaMetadata)
- `core/persistence` (ObjectBox entities)
- `infra/logging`

**Data MUST NOT import:**
- `pipeline/**` (ANY pipeline types)
- `playback/**`

---

## 4. Playback Layer Audit

**Modules:** `playback/telegram`, `playback/xtream`, `playback/domain`

| Check | Command | Expected |
|-------|---------|----------|
| No pipeline imports | `grep -r "import.*pipeline\." playback/*/src/main` | **No matches** |
| Uses RawMediaMetadata or PlaybackContext | Check factory interfaces | ✅ |
| Compiles | `./gradlew :playback:telegram:compileDebugKotlin :playback:xtream:compileDebugKotlin` | BUILD SUCCESSFUL |

**Playback MAY import:**
- `core/model` (RawMediaMetadata, PlaybackContext)
- `infra/transport-*` (for DataSource implementations)
- `player/*` (player internals)
- `infra/logging`

**Playback MUST NOT import:**
- `pipeline/**` (ANY pipeline types)

---

## 5. Full Audit Command (Copy-Paste)

Run this single command to perform all checks:

```bash
cd /workspaces/FishIT-Player && \
echo "=== TRANSPORT LAYER ===" && \
grep -r "import.*pipeline\.\|import.*infra.data\.\|import.*playback\." infra/transport-*/src/main --include="*.kt" && echo "❌ VIOLATION" || echo "✅ Clean" && \
echo "" && \
echo "=== PIPELINE LAYER ===" && \
grep -r "import.*persistence\.\|import.*infra.data\.\|import.*playback\.\|import.*Repository" pipeline/*/src/main --include="*.kt" && echo "❌ VIOLATION" || echo "✅ Clean" && \
echo "" && \
echo "=== DATA LAYER ===" && \
grep -r "import.*pipeline\.\|import.*playback\." infra/data-*/src/main --include="*.kt" && echo "❌ VIOLATION" || echo "✅ Clean" && \
echo "" && \
echo "=== PLAYBACK LAYER ===" && \
grep -r "import.*pipeline\." playback/*/src/main --include="*.kt" && echo "❌ VIOLATION" || echo "✅ Clean" && \
echo "" && \
echo "=== BUILD TEST ===" && \
./gradlew :pipeline:telegram:compileDebugKotlin :pipeline:xtream:compileDebugKotlin :infra:data-telegram:compileDebugKotlin :infra:data-xtream:compileDebugKotlin :playback:telegram:compileDebugKotlin :playback:xtream:compileDebugKotlin :infra:transport-telegram:compileDebugKotlin :infra:transport-xtream:compileDebugKotlin 2>&1 | tail -5
```

---

## 6. Common Violations & Fixes

### ❌ Data imports Pipeline DTO

```kotlin
// WRONG
import com.fishit.player.pipeline.telegram.model.TelegramMediaItem

interface TelegramContentRepository {
    fun getAll(): List<TelegramMediaItem>  // ❌
}
```

```kotlin
// CORRECT
import com.fishit.player.core.model.RawMediaMetadata

interface TelegramContentRepository {
    fun observeAll(): Flow<List<RawMediaMetadata>>  // ✅
}
```

### ❌ Playback imports Pipeline DTO

```kotlin
// WRONG
import com.fishit.player.pipeline.xtream.model.XtreamVodItem

fun XtreamVodItem.toPlaybackContext(): PlaybackContext  // ❌
```

```kotlin
// CORRECT - Move extension to pipeline layer or use RawMediaMetadata
import com.fishit.player.core.model.RawMediaMetadata

fun createPlaybackContext(metadata: RawMediaMetadata): PlaybackContext  // ✅
```

### ❌ Pipeline imports Persistence

```kotlin
// WRONG
import com.fishit.player.core.persistence.obx.ObxTelegramMessage

fun fromObxTelegramMessage(msg: ObxTelegramMessage): TelegramMediaItem  // ❌
```

```kotlin
// CORRECT - This mapping belongs in Data layer, not Pipeline
// Pipeline only maps: TgMessage → TelegramMediaItem → RawMediaMetadata
```

---

## 7. Sign-Off Template

After completing the audit, add this to your PR description:

```markdown
## Pipeline Architecture Audit

- [ ] Transport layer: No forbidden imports
- [ ] Pipeline layer: No forbidden imports, produces RawMediaMetadata
- [ ] Data layer: No pipeline imports, uses RawMediaMetadata only
- [ ] Playback layer: No pipeline imports
- [ ] All modules compile successfully

**Audit command output:**
\`\`\`
[paste output here]
\`\`\`
```

---

## 8. Escalation

If a violation cannot be fixed without breaking changes:

1. **Document** the violation clearly in the PR
2. **Create** a follow-up issue with `[ARCH-DEBT]` prefix
3. **Get approval** from project maintainer before merging
4. **Never** accept violations as "temporary" without tracking

---

*This checklist is MANDATORY per AGENTS.md Section 13.*

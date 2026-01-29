# Xtream IDs Package

**Single Source of Truth (SSOT)** for all Xtream content source IDs.

## Quick Reference

```kotlin
import com.fishit.player.pipeline.xtream.ids.XtreamIdCodec

// Format IDs (use these in pipeline mappers)
XtreamIdCodec.vod(123)                              // → "xtream:vod:123"
XtreamIdCodec.series(456)                           // → "xtream:series:456"
XtreamIdCodec.episode(789)                          // → "xtream:episode:789"
XtreamIdCodec.episodeComposite(456, season=1, ep=5) // → "xtream:episode:series:456:s1:e5"
XtreamIdCodec.live(999)                             // → "xtream:live:999"

// Parse IDs (use these in playback/navigation)
val parsed = XtreamIdCodec.parse("xtream:vod:123")  // → XtreamParsedSourceId.Vod(123)
```

## Why This Exists (xtream_290126.md Blocker #1)

Before the codec, sourceId strings were created inline:
```kotlin
// ❌ OLD (scattered, error-prone)
sourceId = "xtream:vod:$id"       // in VOD mapper
sourceId = "xtream:series:$id"    // in Series mapper
```

This led to:
- Whitespace bugs (`"xtream:vod: 123"`)
- Format inconsistencies (`"xtream:episode:1:2:3"` vs `"xtream:episode:series:1:s2:e3"`)
- No type safety

Now:
```kotlin
// ✅ NEW (centralized, type-safe)
sourceId = XtreamIdCodec.vod(id)
sourceId = XtreamIdCodec.series(id)
```

## Canonical Formats (CONTRACT - NO EXCEPTIONS)

| Type    | Format                                           | Example                              |
|---------|--------------------------------------------------|--------------------------------------|
| VOD     | `xtream:vod:{vodId}`                             | `xtream:vod:123`                     |
| Series  | `xtream:series:{seriesId}`                       | `xtream:series:456`                  |
| Episode | `xtream:episode:{episodeId}` (preferred)         | `xtream:episode:789`                 |
| Episode | `xtream:episode:series:{sid}:s{s}:e{e}` (fallback)| `xtream:episode:series:456:s1:e5`   |
| Live    | `xtream:live:{channelId}`                        | `xtream:live:999`                    |

## Files

| File                  | Purpose                                      |
|-----------------------|----------------------------------------------|
| `XtreamSourceId.kt`   | Typed ID wrappers (`XtreamVodId`, etc.)      |
| `XtreamIdCodec.kt`    | SSOT format/parse functions                  |

## Rules

1. **ALWAYS** use `XtreamIdCodec.xxx()` to create sourceIds
2. **NEVER** hardcode sourceId strings
3. **ONE** format per content type (no variations)
4. **NO** whitespace in IDs

## Related Documents

- `xtream_290126.md` - Blocker analysis that led to this design
- `MEDIA_NORMALIZATION_CONTRACT.md` Section 2.1.1 - SourceId stability contract

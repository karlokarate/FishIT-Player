# Repository Audit: Legacy "Wildwuchs" (Wild Growth) Removal

**Audit Date:** 2026-02-03
**Branch:** `architecture/v2-bootstrap`
**Status:** ✅ COMPLETED

---

## 🔴 Executive Summary

The codebase contained **significant dead code** from incremental migrations.
This audit identified and removed:
- **9 deprecated typealias files** that forwarded to core domain (now deleted)
- **3 duplicate ResumeManager implementations** → only NxResumeManager remains
- **1 duplicate LivePlaybackController** → only NxLivePlaybackController remains
- **1 deprecated TelegramMp4Validator** → replaced by Mp4MoovAtomValidator
- **1 deprecated TelegramMediaRepositoryAdapter** → replaced by NxTelegramMediaRepositoryImpl

**Total removed: 15 files, ~850 lines of dead code.**

---

## ✅ Files Removed

### Deprecated Domain Type Aliases (9 files)

| File | Reason |
|------|--------|
| `feature/home/.../domain/HomeContentRepository.kt` | Forwarded to `core/home-domain` |
| `feature/home/.../domain/HomeMediaItem.kt` | Forwarded to `core/home-domain` |
| `feature/library/.../domain/LibraryContentRepository.kt` | Forwarded to `core/library-domain` |
| `feature/library/.../domain/LibraryMediaItem.kt` | Forwarded to `core/library-domain` |
| `feature/live/.../domain/LiveContentRepository.kt` | Forwarded to `core/live-domain` |
| `feature/live/.../domain/LiveChannel.kt` | Forwarded to `core/live-domain` |
| `feature/telegram-media/.../domain/TelegramMediaItem.kt` | Forwarded to core |
| `feature/telegram-media/.../domain/TelegramMediaRepository.kt` | Forwarded to core |
| `feature/onboarding/.../domain/XtreamAuthRepository.kt` | Forwarded to core (4 typealiases) |

### Deprecated Xtream Typealiases (1 file)

| File | Reason |
|------|--------|
| `infra/data-xtream/.../XtreamSeriesIndexRepository.kt` | Forwarded to core |

### Dead Playback Implementations (4 files)

| File | Replacement |
|------|-------------|
| `playback/domain/.../defaults/DefaultResumeManager.kt` | NxResumeManager |
| `playback/domain/.../defaults/ObxResumeManager.kt` | NxResumeManager |
| `playback/domain/.../defaults/DefaultLivePlaybackController.kt` | NxLivePlaybackController |
| `playback/domain/.../TelegramMp4Validator.kt` | Mp4MoovAtomValidator |

### Deprecated Adapters (1 file)

| File | Replacement |
|------|-------------|
| `infra/data-telegram/.../TelegramMediaRepositoryAdapter.kt` | NxTelegramMediaRepositoryImpl |

---

## 📊 Impact Analysis

### Code Reduction

| Category | Files | Est. LOC |
|----------|-------|----------|
| Deprecated Typealiases | 10 | ~200 |
| Dead Playback Classes | 4 | ~400 |
| Deprecated Adapters | 1 | ~250 |
| **Total** | **15** | **~850** |

### Benefits

- ✅ **Reduced APK size** - fewer classes to load
- ✅ **Faster startup** - less class initialization
- ✅ **Cleaner code graph** - no duplicate implementations
- ✅ **Reduced Hilt complexity** - fewer binding candidates
- ✅ **Clearer architecture** - NX is the only path

---

## ⚠️ Items Kept (Migration Required)

These items were reviewed but kept as they support active data migration:

| File | Purpose | Remove When |
|------|---------|-------------|
| `core/model/.../TmdbRef.kt` | Legacy ID migration helpers | After all data migrated |
| `core/model/.../ExternalIds.kt` | `legacyTmdbId` field | After all data migrated |
| `infra/transport-telegram/.../TelegramTransportClient.kt` | Deprecated but documented | When typed interfaces complete |

---

## 🔗 Related Documents

- `docs/meta/TODO_AUDIT_BLOCKING_ISSUES.md` - Open TODOs inventory
- `contracts/NX_SSOT_CONTRACT.md` - NX entity schema
- `AGENTS.md` Section 4.3.3 - NX_Work UI SSOT rule

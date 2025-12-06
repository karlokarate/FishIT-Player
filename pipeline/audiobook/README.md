# Audiobook Pipeline Module

The audiobook pipeline provides domain models, repository interfaces, and playback source
factories for audiobook content in FishIT Player v2.

## Overview

This module establishes the foundation for audiobook playback support. Phase 2 provides
stub implementations with deterministic empty results. Future phases (4+) will implement
full audiobook functionality including RAR/ZIP archive scanning, chapter parsing, and
streaming playback.

## Key Components

### Domain Models
- `AudiobookItem` - Represents an audiobook with metadata and chapters
- `AudiobookChapter` - Represents a chapter within an audiobook

### Interfaces
- `AudiobookRepository` - Repository for loading and managing audiobooks
- `AudiobookPlaybackSourceFactory` - Factory for creating playback contexts

### Stub Implementations (Phase 2)
- `StubAudiobookRepository` - Returns empty lists/null values
- `StubAudiobookPlaybackSourceFactory` - Returns null for all playback requests

## Phase 2 Constraints

**Current limitations (by design for Phase 2):**
- No file I/O operations
- No network access
- No actual playback capability
- Stub implementations return empty/null results

**Purpose of Phase 2 stubs:**
- Establish module structure and dependencies
- Define stable API contracts
- Enable other modules to reference audiobook interfaces
- Provide test infrastructure

## Future Implementation (Phase 4+)

### RAR/ZIP Archive Support
Audiobooks are often distributed as compressed archives containing:
- Multiple audio files (one per chapter or split chapters)
- Chapter marker files (CUE sheets, chapter.txt)
- Metadata files (info.txt, NFO files)
- Cover art (cover.jpg, folder.jpg)

Future implementation will:
1. **Scan and Index**: Scan configured directories for RAR/ZIP files
2. **Parse Metadata**: Extract audiobook info without full decompression
3. **Chapter Extraction**: Parse chapter markers from metadata or audio files
4. **Streaming Playback**: Stream audio directly from archives without extraction

### Chapter-Based Navigation
- Precise seeking to chapter boundaries
- Chapter list UI for quick navigation
- Resume playback at exact chapter/position
- Chapter progress tracking

### Enhanced Features
- **Bookmarks**: Save multiple positions within audiobook
- **Speed Control**: Variable playback speed (0.5x to 2.0x)
- **Sleep Timer**: Auto-pause after specified duration
- **Resume Points**: Integration with ResumeManager
- **Kids Mode**: Filter audiobooks by content rating

## Usage Example

### Phase 2 (Current - Stub)
```kotlin
val repository: AudiobookRepository = StubAudiobookRepository()
val audiobooks = repository.getAllAudiobooks() // Returns emptyList()
```

### Future Phase (Actual Implementation)
```kotlin
// Get audiobook from repository
val audiobook = repository.getAudiobookById("book-123")

// Convert to playback context
val playbackContext = audiobook.toPlaybackContext(startChapterNumber = 3)

// Start playback
playbackLauncher.launch(playbackContext)
```

## Dependencies

- `:core:model` - Core domain models (PlaybackContext, PlaybackType)

## Testing

Unit tests verify:
- Stub implementations behave predictably
- Extension functions create valid PlaybackContext objects
- Domain models serialize/deserialize correctly
- Chapter calculations are accurate

## Related Modules

- `:pipeline:xtream` - IPTV/Xtream content pipeline
- `:pipeline:telegram` - Telegram media pipeline
- `:pipeline:io` - Local file I/O pipeline
- `:playback:domain` - Playback orchestration domain
- `:player:internal` - SIP (Stateful Internal Player)

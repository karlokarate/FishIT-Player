# FishIT-Player Chat Participants

This document describes specialized chat participants for different development contexts.

## How to Use

In VS Code Copilot Chat, use these prefixes to activate specialized context:

### @workspace
Default workspace-aware chat. Reads files and understands project structure.

### Custom Participants (via Prompt Prefixes)

#### 🔧 Pipeline Work
```
[PIPELINE MODE]
I'm working on pipeline/* modules. Apply:
- pipeline.instructions.md rules
- No Data/Persistence imports
- Output RawMediaMetadata only
- No globalId computation
- No normalization logic

Task: [your task]
```

#### 🎬 Player Work
```
[PLAYER MODE]
I'm working on player/* or playback/* modules. Apply:
- player.instructions.md / playback.instructions.md rules
- Source-agnostic code only
- No Transport imports
- Use PlaybackSourceFactory pattern

Task: [your task]
```

#### 📡 Transport Work
```
[TRANSPORT MODE]
I'm working on infra/transport-* modules. Apply:
- infra-transport-*.instructions.md rules
- Expose typed interfaces only
- Hide TDLib/SDK internals
- DTOs in api/ package

Task: [your task]
```

#### 💾 Data Layer Work
```
[DATA MODE]
I'm working on infra/data-* modules. Apply:
- infra-data.instructions.md rules
- No Pipeline DTO imports
- Use RawMediaMetadata from pipelines
- ObjectBox entities in core/persistence

Task: [your task]
```

#### 🎨 Feature/UI Work
```
[FEATURE MODE]
I'm working on feature/* modules. Apply:
- feature-*.instructions.md rules
- Compose best practices
- TV focus handling via FocusKit
- UiState pattern for loading states

Task: [your task]
```

#### 🧪 Testing
```
[TEST MODE]
Generate tests for the code. Apply:
- JUnit5 + MockK
- AAA pattern (Arrange-Act-Assert)
- coEvery/coVerify for suspend functions
- Descriptive test names with backticks

Target: [file or code to test]
```

#### 📝 Code Review
```
[REVIEW MODE]
Review this code for PLATIN quality:
- Layer boundary compliance
- Naming convention (Glossary)
- Test coverage
- Performance
- Security

Code: [paste code or file path]
```

---

## Quick Prompts

### Generate RawMediaMetadata Mapper
```
Generate a toRawMediaMetadata() extension for [DTO_NAME] following pipeline.instructions.md.
Leave globalId empty. Pass originalTitle RAW without cleaning.
```

### Generate PlaybackSourceFactory
```
Generate a PlaybackSourceFactory for [SOURCE_TYPE] following playback.instructions.md.
Use @IntoSet pattern for DI.
```

### Generate Repository
```
Generate a Repository interface and implementation for [ENTITY] following infra-data.instructions.md.
Use ObjectBox and expose Flows.
```

### Generate ViewModel
```
Generate a HiltViewModel for [FEATURE] following feature-common.instructions.md.
Use UiState pattern with Loading/Success/Error states.
```

---

## Performance Tips

1. **Be Specific:** Include file paths and module names
2. **Reference Instructions:** Mention the specific .instructions.md file
3. **Use Snippets:** Type `rawmeta`, `playbackfactory`, `hiltvm` etc.
4. **Batch Related Changes:** Ask for multiple files in one request
5. **Include Context:** Paste relevant existing code for consistency

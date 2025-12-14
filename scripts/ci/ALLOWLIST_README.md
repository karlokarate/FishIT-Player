# Architecture Guardrails Allowlist - README

## Purpose

This allowlist provides a **strict, path-based exception mechanism** for the architecture guardrail checks. It prevents false positives while maintaining enforcement of layer boundaries.

## Design Principles

1. **Path-based, not rule-based** - Exceptions are granted to specific files, not entire categories
2. **Mandatory justification** - Every entry must include a reason explaining why it's needed
3. **Minimal by design** - Only add entries when architecturally necessary (e.g., DI wiring)
4. **Version controlled** - All changes are reviewed and tracked
5. **Transparent** - The script shows which files are allowlisted during checks

## Format

```
path/to/file.kt # Reason for exception
```

- **Path**: Relative to repository root
- **Glob patterns**: Supported (`*` for any characters, `**` for directories)
- **Comments**: Lines starting with `#` are ignored
- **Reason**: Required after `#` character - explains why this file needs an exception

## When to Add an Exception

✅ **Valid reasons:**
- DI/Hilt modules that wire transport implementations to domain interfaces
- Bootstrap/initialization code that observes transport state
- Legitimate architectural patterns that don't fit the general rules

❌ **Invalid reasons:**
- "It's easier to import transport directly"
- "I don't want to create a repository interface"
- "This is temporary" (fix the code instead)
- Bypassing violations that should be refactored

## How to Add an Entry

1. **Verify the violation is legitimate**
   - Run: `./scripts/ci/check-arch-guardrails.sh`
   - Confirm the file path in the error output

2. **Add to allowlist with justification**
   ```
   app-v2/src/main/java/com/fishit/player/v2/di/MyModule.kt # Hilt DI wiring for XYZ domain repository
   ```

3. **Test locally**
   ```bash
   ./scripts/ci/check-arch-guardrails.sh
   ```
   - Verify the file is marked as "ℹ️ Allowlisted"
   - Ensure other violations are still caught

4. **Include justification in PR**
   - Explain why this specific file needs an exception
   - Link to relevant architecture decision records (ADRs) if applicable
   - Get code review approval

## Current Exceptions

See `arch-guardrails-allowlist.txt` for the current list of allowlisted files.

### App-v2 DI Modules (5 files)

These files require transport layer access for Hilt dependency injection wiring. They act as adapters between transport implementations and domain interfaces:

- `TelegramAuthModule.kt` - Binds transport to feature repositories
- `ImagingModule.kt` - Provides Coil ImageLoader with Telegram integration
- `TelegramThumbFetcherImpl.kt` - Adapter for Coil Fetcher interface
- `XtreamSessionBootstrap.kt` - Bootstrap for Xtream session
- `CatalogSyncBootstrap.kt` - Observes transport state for sync triggers

## Maintenance

**Regular review**: Periodically audit the allowlist to:
- Remove entries that are no longer needed
- Verify reasons are still valid
- Refactor code to eliminate exceptions where possible

**Goal**: Keep the allowlist as small as possible. Each entry represents technical debt.

## Anti-Patterns to Avoid

❌ **Don't add glob patterns that exclude entire directories**
```
# BAD - too broad
app-v2/**/*.kt # All app-v2 files allowed
```

❌ **Don't add entries without clear reasons**
```
# BAD - no justification
app-v2/src/main/java/MyFile.kt # needed
```

❌ **Don't bypass violations instead of fixing them**
```
# BAD - should refactor the code instead
feature/myfeature/MyViewModel.kt # imports transport (TODO: fix later)
```

✅ **Do use specific paths with clear architectural justification**
```
# GOOD - specific file with clear reason
app-v2/src/main/java/com/fishit/player/v2/di/TelegramModule.kt # Hilt DI wiring for Telegram repositories
```

## Questions?

See:
- `AGENTS.md` Section 4.5 - Layer hierarchy rules
- `docs/dev/ARCH_GUARDRAILS_IMPLEMENTATION.md` - Full implementation guide
- `LOGGING_CONTRACT_V2.md` - Logging requirements

# Architecture Guardrails Allowlist - README

## Purpose

This allowlist provides a **strict, path-based exception mechanism** for the architecture guardrail checks. It prevents false positives while maintaining enforcement of layer boundaries.

## Design Principles (Phase A1.4 Enhanced)

1. **Path-based, not rule-based** - Exceptions are granted to specific files, not entire categories
2. **Mandatory justification** - Every entry must include a reason explaining why it's needed
3. **TEMPORARY by design** - All entries must contain TEMP marker + expiry/issue (Phase A1.4)
4. **Capped growth** - Maximum 10 entries allowed to prevent abuse (Phase A1.4)
5. **Version controlled** - All changes are reviewed and tracked
6. **Transparent** - The script shows which files are allowlisted during checks
7. **Expiry enforcement** - Dates in the past cause CI failure (Phase A1.4)

## Format (Phase A1.4 Required)

```
path/to/file.kt # TEMP until YYYY-MM-DD: Reason for exception
```

**OR**

```
path/to/file.kt # TEMP until #123: Reason with issue reference
```

### Required Elements (All Mandatory)

1. **Path**: Relative to repository root, supports glob patterns (`*`, `**`)
2. **TEMP marker**: Must contain the word "TEMP" (case-insensitive)
3. **Expiry OR Issue**:
   - Expiry date in format `YYYY-MM-DD` (e.g., `2026-06-01`)
   - OR issue reference like `#123`
4. **Reason**: Clear explanation of why this exception is needed

### Examples

✅ **Valid entries:**
```
app-v2/src/main/java/MyModule.kt # TEMP until 2026-06-01: DI wiring needs refactor to infra adapter
app-v2/bootstrap/MyBootstrap.kt # TEMP until #456: Observes transport state - migrate to domain event
feature/onboarding/TempAdapter.kt # TEMP: Bridge pattern until #789 - refactor to repository interface
```

❌ **Invalid entries:**
```
app-v2/MyFile.kt # No TEMP marker or date
app-v2/MyFile.kt # TEMP but no date or issue
app-v2/MyFile.kt # Just a reason without TEMP
app-v2/MyFile.kt # TEMP until 2023-01-01: expired date
```

## Validation Rules (Phase A1.4)

The script enforces these rules automatically:

1. ✅ **TEMP marker required** - Every entry must contain "TEMP"
2. ✅ **Expiry/Issue required** - Must have either `YYYY-MM-DD` date OR `#123` issue reference
3. ✅ **No expired dates** - Dates in the past cause immediate CI failure
4. ✅ **10 entry maximum** - Hard cap to prevent allowlist abuse
5. ✅ **Clear reason** - Comment must explain the exception

## Growth Cap: Maximum 10 Entries

**Current limit: 10 entries**

This cap prevents the allowlist from becoming a loophole. If you hit the limit:

1. ❌ **DON'T** just increase the cap
2. ✅ **DO** refactor existing entries first
3. ✅ **DO** consider if you're bypassing architecture
4. ✅ **DO** get architecture review approval

**Goal**: Keep the allowlist as small as possible. Each entry is technical debt.

## When to Add an Exception

✅ **Valid reasons:**
- DI/Hilt modules that wire transport implementations to domain interfaces (TEMP with expiry)
- Bootstrap/initialization code that observes transport state (TEMP with migration plan)
- Legitimate architectural patterns that don't fit the general rules (TEMP with issue)

❌ **Invalid reasons:**
- "It's easier to import transport directly" (refactor the code instead)
- "I don't want to create a repository interface" (that's the architecture requirement)
- "This is permanent" (use TEMP with far-future date and document migration plan)
- Bypassing violations that should be refactored (fix the violation, don't allowlist it)

## How to Add an Entry (Phase A1.4)

1. **Verify the violation is legitimate**
   ```bash
   ./scripts/ci/check-arch-guardrails.sh
   ```
   Confirm the file path in the error output

2. **Determine expiry date or issue reference**
   - For known refactor: Use expiry date 3-6 months out
   - For tracked work: Reference the issue/PR number
   - Never use dates in the past

3. **Add to allowlist with TEMP + expiry/issue**
   ```
   app-v2/src/main/java/com/fishit/player/v2/di/MyModule.kt # TEMP until 2026-06-01: Hilt DI wiring - migrate to infra adapter after domain split
   ```

4. **Test locally**
   ```bash
   ./scripts/ci/check-arch-guardrails.sh
   ```
   - Verify the file is marked as "ℹ️ Allowlisted"
   - Ensure validation passes (TEMP + expiry/issue present)
   - Confirm other violations are still caught

5. **Include justification in PR**
   - Explain why this specific file needs an exception
   - Document the migration plan or issue reference
   - Link to relevant architecture decision records (ADRs) if applicable
   - Get code review approval

6. **Set a reminder to refactor**
   - Create a calendar reminder for the expiry date
   - Or link to a tracking issue that's actively monitored
   - Plan to remove the entry before expiry

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

## Anti-Patterns to Avoid (Phase A1.4 Enhanced)

❌ **Don't add entries without TEMP marker**
```
# BAD - no TEMP marker
app-v2/MyFile.kt # Permanent exception for DI wiring
```

❌ **Don't add entries without expiry/issue**
```
# BAD - no date or issue reference
app-v2/MyFile.kt # TEMP: needs fixing someday
```

❌ **Don't use expired dates**
```
# BAD - date in the past
app-v2/MyFile.kt # TEMP until 2023-01-01: old exception
```

❌ **Don't add glob patterns that exclude entire directories**
```
# BAD - too broad, bypasses enforcement
app-v2/**/*.kt # TEMP until 2026-01-01: all app-v2 files allowed
```

❌ **Don't add entries without clear reasons**
```
# BAD - no justification
app-v2/MyFile.kt # TEMP until 2026-06-01: needed
```

❌ **Don't bypass violations instead of fixing them**
```
# BAD - should refactor the code instead
feature/myfeature/MyViewModel.kt # TEMP until 2030-01-01: imports transport (TODO: fix later)
```

❌ **Don't try to increase the 10-entry cap without review**
```
# BAD - hitting the cap means you need to refactor, not add more
# Attempting to increase ALLOWLIST_MAX_ENTRIES in the script
```

✅ **Do use specific paths with TEMP + expiry/issue + clear reason**
```
# GOOD - specific file with TEMP, date, and architectural justification
app-v2/src/main/java/com/fishit/player/v2/di/TelegramModule.kt # TEMP until 2026-06-01: Hilt DI wiring - migrate to infra/data-telegram adapter after domain split
```

✅ **Do use issue references for tracked work**
```
# GOOD - links to tracking issue
app-v2/bootstrap/XtreamBootstrap.kt # TEMP until #456: Observes transport state - refactor to domain event pattern (tracked in issue)
```

✅ **Do set realistic expiry dates**
```
# GOOD - reasonable timeframe (3-6 months)
app-v2/imaging/ThumbFetcher.kt # TEMP until 2026-06-01: Coil integration - move to infra/imaging module
```

## Phase A1.4 Enforcement Summary

The allowlist mechanism now enforces:

1. ✅ **TEMP marker required** - No permanent exceptions allowed
2. ✅ **Expiry/Issue required** - Every entry must have accountability
3. ✅ **Expired entries fail CI** - Forces cleanup of old exceptions
4. ✅ **10 entry maximum** - Prevents allowlist from becoming a loophole
5. ✅ **Clear guidance** - Script discourages "allowlist-first" behavior

**Impact**: The allowlist remains a controlled, temporary mechanism - not a way to bypass architecture.

## Questions?

See:
- `AGENTS.md` Section 4.5 - Layer hierarchy rules
- `docs/dev/ARCH_GUARDRAILS_IMPLEMENTATION.md` - Full implementation guide
- `LOGGING_CONTRACT_V2.md` - Logging requirements

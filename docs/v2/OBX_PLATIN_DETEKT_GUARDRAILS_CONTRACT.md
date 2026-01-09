# OBX PLATIN Phase 0.3 - Detekt Guardrails Contract

**Version:** 1.0  
**Status:** ACTIVE  
**Phase:** 0.3 - Contracts & Guardrails  
**Parent Issue:** #621  
**Created:** 2026-01-09

## Purpose

This contract defines the architectural guardrails enforced via Detekt for the OBX PLATIN Refactor. These rules prevent architectural violations before code review.

## Rule Summary

| Rule ID | Name | Status | Enforcement | Severity |
|---------|------|--------|-------------|----------|
| OBX-01 | NoBoxStoreOutsideRepository | ✅ Active | Automated | ERROR |
| OBX-02 | NoSecretsInObx | 📝 Documented | Manual | ERROR |
| OBX-03 | IngestLedgerRequired | 📝 Documented | Manual | ERROR |

## Rule Specifications

### OBX-01: NoBoxStoreOutsideRepository

**Status:** ✅ IMPLEMENTED (via ForbiddenImport in detekt-config.yml)

**Purpose:** Enforce repository pattern - `BoxStore` must only be used in repository implementations.

**Rationale:**
- Prevents tight coupling to ObjectBox in business logic
- Enables testability via repository interfaces
- Supports future migration to different persistence layers
- Part of Clean Architecture layer isolation

**Detection:**
- Forbid `import io.objectbox.BoxStore` in non-repository files
- Allowed locations:
  - `*Repository*.kt` files (any path containing "Repository")
  - `core/persistence/di/` (DI configuration)
  - `core/persistence/obx/ObxStore.kt` (singleton)
  - `core/persistence/inspector/` (debug utilities)
  - Test files (`**/test/**`, `**/androidTest/**`)

**Example Violation:**
```kotlin
// ❌ VIOLATION in feature/home/HomeViewModel.kt
import io.objectbox.BoxStore

class HomeViewModel @Inject constructor(
    private val boxStore: BoxStore, // Direct BoxStore in ViewModel!
) {
    // ...
}
```

**Correct Pattern:**
```kotlin
// ✅ CORRECT in feature/home/HomeViewModel.kt
import com.fishit.player.core.model.repository.HomeContentRepository

class HomeViewModel @Inject constructor(
    private val homeRepository: HomeContentRepository, // Use interface
) {
    // ...
}

// ✅ CORRECT in infra/data-home/ObxHomeContentRepository.kt
import io.objectbox.BoxStore

class ObxHomeContentRepository @Inject constructor(
    private val boxStore: BoxStore, // OK in repository implementation
) : HomeContentRepository {
    // ...
}
```

**CI Enforcement:**
- Automated via `./gradlew detekt`
- Runs on all PRs via `android-quality.yml` workflow
- Build fails if violations found

**Manual Verification:**
```bash
bash tools/detekt-rules/verify-guardrails.sh
```

---

### OBX-02: NoSecretsInObx

**Status:** 📝 DOCUMENTED (Custom rule required for automation)

**Purpose:** Prevent sensitive data fields in ObjectBox `@Entity` classes.

**Rationale:**
- ObjectBox stores data in plain files on device storage
- Rooted devices or backups can expose ObjectBox data
- Sensitive data (passwords, tokens, API keys) must use EncryptedSharedPreferences or Keystore
- Detection at code review time prevents security incidents

**Detection Pattern:**
- Scan `@Entity` annotated classes
- Flag fields matching sensitive patterns (case-insensitive):
  - `password`
  - `secret`
  - `token`
  - `apiKey`
  - `auth*` (authentication-related)

**Example Violation:**
```kotlin
// ❌ VIOLATION in core/persistence/obx/ObxEntities.kt
@Entity
data class ObxUserSettings(
    @Id var id: Long = 0,
    var username: String = "",
    var password: String = "", // SECURITY RISK!
    var apiKey: String = "",   // SECURITY RISK!
)
```

**Correct Pattern:**
```kotlin
// ✅ CORRECT - Sensitive data in EncryptedSharedPreferences
@Entity
data class ObxUserSettings(
    @Id var id: Long = 0,
    var username: String = "",
    var profileId: Long = 0,
    // password and apiKey stored separately in EncryptedSharedPreferences
)

// Companion object or separate class for secure storage
class SecureCredentialsStore @Inject constructor(
    private val encryptedPrefs: EncryptedSharedPreferences,
) {
    fun savePassword(userId: Long, password: String) {
        encryptedPrefs.edit().putString("pwd_$userId", password).apply()
    }
    
    fun saveApiKey(key: String) {
        encryptedPrefs.edit().putString("api_key", key).apply()
    }
}
```

**Manual Verification:**
```bash
# Run from project root
grep -r "password\|secret\|token\|apiKey" core/persistence/src/main/java \
  --include="*.kt" -i -B 5 | grep -B 5 -i "@Entity"
```

**CI Enforcement:**
- Currently: Manual review during PR
- Future: Custom Detekt rule (see tools/detekt-rules/README.md)

---

### OBX-03: IngestLedgerRequired

**Status:** 📝 DOCUMENTED (Custom rule required for automation)

**Purpose:** Ensure all ingest/catalog processing creates IngestLedger entries - no silent drops.

**Rationale:**
- Every ingest candidate must have explicit decision: ACCEPTED | REJECTED | SKIPPED
- Enables debugging of "missing content" issues
- Supports audit trail for content curation
- Critical for OBX PLATIN Refactor goal: "no silent drops"

**Contract Reference:**  
docs/v2/OBX_PLATIN_REFACTOR_ROADMAP.md Phase 0:
> "Every ingest candidate must create exactly one IngestLedger entry (ACCEPTED | REJECTED | SKIPPED) – no silent drops."

**Detection Pattern:**
- Functions in `pipeline/**` that:
  - Accept `RawMediaMetadata` or `List<RawMediaMetadata>` as parameter
  - Process catalog items from transport layer
  - Must call `IngestLedger.record()` or similar tracking method

**Example Violation:**
```kotlin
// ❌ VIOLATION in pipeline/telegram/catalog/TelegramCatalogPipeline.kt
suspend fun processItems(items: List<RawMediaMetadata>) {
    items.forEach { item ->
        if (item.mediaType == MediaType.UNKNOWN) {
            // Silent drop - no ledger entry!
            return@forEach
        }
        repository.upsert(item)
    }
}
```

**Correct Pattern:**
```kotlin
// ✅ CORRECT - Explicit ledger tracking
suspend fun processItems(items: List<RawMediaMetadata>) {
    items.forEach { item ->
        when {
            item.mediaType == MediaType.UNKNOWN -> {
                ingestLedger.record(
                    sourceId = item.sourceId,
                    reason = IngestReasonCode.REJECTED,
                    message = "Unknown media type"
                )
            }
            item.durationMs == null || item.durationMs == 0L -> {
                ingestLedger.record(
                    sourceId = item.sourceId,
                    reason = IngestReasonCode.SKIPPED,
                    message = "Missing duration"
                )
            }
            else -> {
                repository.upsert(item)
                ingestLedger.record(
                    sourceId = item.sourceId,
                    reason = IngestReasonCode.ACCEPTED,
                    message = "Successfully ingested"
                )
            }
        }
    }
}
```

**Manual Verification:**
```bash
# Check for RawMediaMetadata processing functions
find pipeline/ -name "*.kt" -exec grep -l "RawMediaMetadata" {} \; | \
  xargs grep -n "fun.*RawMediaMetadata"

# Manual review: Verify each function calls IngestLedger.record()
```

**CI Enforcement:**
- Currently: Manual review during PR
- Future: Custom Detekt rule with control flow analysis

---

## Implementation Roadmap

### Phase 0.3 (Current) ✅
- [x] Rule 1 (BoxStore): Implemented via ForbiddenImport
- [x] Rule 2 (Secrets): Documented with manual verification
- [x] Rule 3 (Ledger): Documented with manual verification
- [x] Verification script created
- [x] Documentation in detekt-config.yml
- [x] README in tools/detekt-rules/

### Phase 0.4 (Future) 🔮
- [ ] Create custom Detekt rule module (tools/detekt-rules/build.gradle.kts)
- [ ] Implement NoSecretsInObxRule.kt
- [ ] Implement IngestLedgerRequiredRule.kt
- [ ] Add test cases for custom rules
- [ ] Wire custom rules to root build.gradle.kts
- [ ] Enable rules in detekt-config.yml
- [ ] Update CI to enforce all rules automatically

## Manual Verification

Run the verification script:
```bash
bash tools/detekt-rules/verify-guardrails.sh
```

Or verify individually:

**Rule 1 (BoxStore):**
```bash
./gradlew detekt
```

**Rule 2 (Secrets):**
```bash
grep -r "password\|secret\|token\|apiKey" core/persistence/src/main/java \
  --include="*.kt" -i -B 5 | grep -B 5 -i "@Entity"
```

**Rule 3 (Ledger):**
- Manual code review of pipeline functions
- Check for IngestLedger.record() calls

## References

- **Parent Issue:** #621 - OBX PLATIN Refactor
- **Roadmap:** docs/v2/OBX_PLATIN_REFACTOR_ROADMAP.md
- **Architecture:** AGENTS.md Section 4
- **Implementation Guide:** tools/detekt-rules/README.md
- **Detekt Config:** detekt-config.yml
- **CI Workflow:** .github/workflows/android-quality.yml

## Change Log

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2026-01-09 | Initial contract with 3 rules defined |

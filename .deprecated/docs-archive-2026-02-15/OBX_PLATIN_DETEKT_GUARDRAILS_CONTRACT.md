# OBX PLATIN Phase 0.3 - Detekt Guardrails Contract

**Version:** 1.1  
**Status:** BINDING  
**Phase:** 0.3 - Contracts & Guardrails  
**Parent Issue:** #621  
**Created:** 2026-01-09  
**Updated:** 2026-01-09

---

## SSOT References (BINDING)

> **This contract is subordinate to and must comply with:**

| Document | Location | Authority |
|----------|----------|----------|
| **AGENTS.md** | `/AGENTS.md` | PRIMARY - Architecture rules |
| **GLOSSARY** | `/contracts/GLOSSARY_v2_naming_and_modules.md` | AUTHORITATIVE - Naming |
| **Normalization Contract** | `/contracts/MEDIA_NORMALIZATION_CONTRACT.md` | BINDING - Pipeline responsibilities |
| **core/persistence instructions** | `.github/instructions/core-persistence.instructions.md` | BINDING - Persistence layer rules |
| **infra-data instructions** | `.github/instructions/infra-data.instructions.md` | BINDING - Repository layer rules |

---

## Purpose

This contract defines the architectural guardrails enforced via Detekt + verification script for the OBX PLATIN Refactor. These rules prevent architectural violations **before code review**.

---

## Rule Summary

| Rule ID | Name | Status | Enforcement | Severity | CI Fail |
|---------|------|--------|-------------|----------|----------|
| OBX-01 | NoBoxStoreOutsideRepository | ✅ Active | Automated (Detekt + Script) | ERROR | **YES** |
| OBX-02 | NoSecretsInObx | ✅ Active | Automated (Script) | ERROR | **YES** |
| OBX-03 | IngestLedgerRequired | ℹ️ Informational | Manual Review | WARNING | NO |

---

## Rule Specifications

### OBX-01: NoBoxStoreOutsideRepository

**Status:** ✅ ENFORCED (Detekt ForbiddenImport + verify-guardrails.sh)

**Purpose:** Enforce repository pattern - `BoxStore` must only be used in designated locations.

#### Allowed Locations (Exhaustive List)

| Location | Reason |
|----------|--------|
| `infra/data-*/**/*Repository*.kt` | Repository implementations |
| `core/persistence/di/**` | DI wiring (ObxStoreModule) |
| `core/persistence/obx/ObxStore.kt` | Store singleton |
| `core/persistence/inspector/**` | Debug-only database inspector |
| `**/test/**`, `**/androidTest/**` | Test code |

#### Forbidden Locations

| Location | Reason |
|----------|--------|
| `feature/**` | UI layer must use repository interfaces |
| `core/*-domain/**` | Domain layer must use repository interfaces |
| `pipeline/**` | Pipelines produce RawMediaMetadata only |
| `playback/**` | Playback uses domain interfaces |
| `player/**` | Player is source-agnostic |
| `core/persistence/*Repository*.kt` | ⚠️ **NO REPOS IN core/persistence** - schema only! |

#### ⚠️ Critical Clarification: core/persistence

Per `.github/instructions/core-persistence.instructions.md`:

> `core/persistence` is **schema-only**: Entities + Store infrastructure.
> Repository **implementations** belong in `infra/data-*/**`.

```
✅ CORRECT:
  core/persistence/obx/ObxTelegramMessage.kt  (Entity)
  core/persistence/di/ObxStoreModule.kt       (DI)
  infra/data-telegram/ObxTelegramRepository.kt (Repository impl)

❌ WRONG:
  core/persistence/ObxTelegramRepository.kt   (Repo in wrong layer!)
```

#### CI Enforcement

**Two-stage enforcement:**

1. `./gradlew detekt` - ForbiddenImport catches most violations
2. `tools/detekt-rules/verify-guardrails.sh` - Fine-grained allowlist check

Both MUST pass for PR to merge.

---

### OBX-02: NoSecretsInObx

**Status:** ✅ ENFORCED (verify-guardrails.sh)

**Purpose:** Prevent sensitive data fields in ObjectBox `@Entity` classes.

#### Security Rationale

- ObjectBox stores data in **plain files** on device storage
- Rooted devices or backups can expose ObjectBox data
- Sensitive data MUST use `EncryptedSharedPreferences` or Android Keystore

#### Forbidden Field Patterns (case-insensitive)

- `password`
- `secret`
- `token`
- `apiKey`
- `authToken`
- `accessToken`

#### Example Violation

```kotlin
// ❌ VIOLATION - SECURITY RISK!
@Entity
data class ObxUserSettings(
    @Id var id: Long = 0,
    var password: String = "",  // FORBIDDEN
    var apiKey: String = "",    // FORBIDDEN
)
```

#### Correct Pattern

```kotlin
// ✅ CORRECT - Sensitive data in secure storage
@Entity
data class ObxUserSettings(
    @Id var id: Long = 0,
    var username: String = "",
    var profileId: Long = 0,
    // password and apiKey stored in EncryptedSharedPreferences
)

// Separate secure storage
class SecureCredentialsStore @Inject constructor(
    private val encryptedPrefs: EncryptedSharedPreferences,
) {
    fun saveApiKey(key: String) {
        encryptedPrefs.edit().putString("api_key", key).apply()
    }
}
```

#### CI Enforcement

- **Automated** via `tools/detekt-rules/verify-guardrails.sh`
- Exit code 2 on violation
- **Blocks PR merge**

---

### OBX-03: IngestLedgerRequired

**Status:** ℹ️ INFORMATIONAL (Manual Review)

**Purpose:** Ensure ingest tracking exists - but in the **correct layer**.

#### ⚠️ Critical: Correct Layer Assignment

Per `contracts/MEDIA_NORMALIZATION_CONTRACT.md` and `AGENTS.md` Section 4:

> **Pipelines produce `RawMediaMetadata` only.**
> Ledger responsibility belongs to **CatalogSync/Ingest orchestration**, NOT pipelines.

| Layer | Responsibility | Ledger? |
|-------|----------------|---------|
| `pipeline/**` | Produce `RawMediaMetadata` from transport | ❌ NO |
| `core/catalog-sync/**` | Orchestrate ingest, create ledger entries | ✅ YES |
| `infra/data-*/**` | Persist normalized data | ❌ NO |

#### Why NOT in Pipeline?

1. **Single Responsibility**: Pipelines only map transport → RawMediaMetadata
2. **No Persistence Access**: Pipelines cannot access ObjectBox (per OBX-01)
3. **Normalization Contract**: Pipelines don't decide acceptance/rejection

#### Where Ledger Belongs

```kotlin
// ✅ CORRECT: Ledger in CatalogSync orchestration layer
// Location: core/catalog-sync/src/.../CatalogSyncOrchestrator.kt

class CatalogSyncOrchestrator @Inject constructor(
    private val telegramPipeline: TelegramCatalogPipeline,
    private val normalizer: MediaNormalizer,
    private val repository: MediaRepository,
    private val ledger: IngestLedger,
) {
    suspend fun sync() {
        telegramPipeline.fetchCatalog().collect { rawMedia ->
            when (val result = normalizer.normalize(rawMedia)) {
                is NormalizationResult.Success -> {
                    repository.upsert(result.media)
                    ledger.record(rawMedia.sourceId, ACCEPTED)
                }
                is NormalizationResult.Rejected -> {
                    ledger.record(rawMedia.sourceId, REJECTED, result.reason)
                }
            }
        }
    }
}
```

#### CI Enforcement

- **Not automated** - Requires control flow analysis
- **Manual review** during PR
- Does NOT block CI (informational only)

---

## CI Integration

### GitHub Actions Workflow

The `android-quality.yml` workflow must run both checks:

```yaml
- name: Detekt
  run: ./gradlew detekt

- name: OBX PLATIN Guardrails
  run: |
    chmod +x tools/detekt-rules/verify-guardrails.sh
    bash tools/detekt-rules/verify-guardrails.sh
```

### Local Development

```bash
# Run all quality checks
./gradlew detekt && bash tools/detekt-rules/verify-guardrails.sh

# Or use the combined script
bash tools/detekt-rules/verify-guardrails.sh
```

---

## Merge Criteria for OBX PLATIN PRs

A PR touching OBX/persistence code may ONLY merge if:

| Criterion | Check | Automated |
|-----------|-------|-----------|
| BoxStore allowlist correct | verify-guardrails.sh exits 0 | ✅ YES |
| No secrets in entities | verify-guardrails.sh exits 0 | ✅ YES |
| Detekt passes | ./gradlew detekt | ✅ YES |
| No repos in core/persistence | Code review | Manual |
| Ledger in correct layer | Code review | Manual |

---

## References

- **Parent Issue:** #621 - OBX PLATIN Refactor
- **AGENTS.md:** Section 4 (Layer Boundaries), Section 11 (Checklists)
- **GLOSSARY:** `/contracts/GLOSSARY_v2_naming_and_modules.md`
- **Normalization Contract:** `/contracts/MEDIA_NORMALIZATION_CONTRACT.md`
- **V2 Portal:** `/V2_PORTAL.md`
- **Roadmap:** `/docs/v2/OBX_PLATIN_REFACTOR_ROADMAP.md`

---

## Change Log

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2026-01-09 | Initial contract with 3 rules |
| 1.1 | 2026-01-09 | Corrected allowlist (no repos in core/persistence), Ledger moved to CatalogSync layer, SSOT references added, CI enforcement clarified |

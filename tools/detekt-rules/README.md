# OBX PLATIN Detekt Rules

This directory contains custom guardrails for the OBX PLATIN refactor.

## Files

| File | Purpose |
|------|----------|
| `verify-guardrails.sh` | Fine-grained guardrail verification script |

## Overview

The OBX PLATIN refactor enforces strict architectural boundaries via:

1. **Detekt ForbiddenImport** - Catches `BoxStore` imports in wrong locations
2. **verify-guardrails.sh** - Fine-grained allowlist verification + security checks

Both checks must pass for PRs to merge.

## Rules Enforced

| Rule ID | Name | Severity | CI Fail |
|---------|------|----------|----------|
| OBX-01 | NoBoxStoreOutsideRepository | ERROR | **YES** |
| OBX-02 | NoSecretsInObx | ERROR | **YES** |
| OBX-03 | IngestLedgerRequired | WARNING | NO |

## Usage

### Local Development

```bash
# Run the guardrail check
bash tools/detekt-rules/verify-guardrails.sh

# Or combined with Detekt
./gradlew detekt && bash tools/detekt-rules/verify-guardrails.sh
```

### Exit Codes

| Code | Meaning |
|------|----------|
| 0 | All checks passed |
| 1 | BoxStore in forbidden location (OBX-01) |
| 2 | Secrets found in @Entity (OBX-02) |

### CI Integration

The `android-quality.yml` workflow runs this script automatically after Detekt:

```yaml
- name: OBX PLATIN Guardrails
  run: |
    chmod +x tools/detekt-rules/verify-guardrails.sh
    bash tools/detekt-rules/verify-guardrails.sh
```

## BoxStore Allowlist (OBX-01)

BoxStore is **ONLY** allowed in:

| Location | Reason |
|----------|--------|
| `infra/data-*/**/*Repository*.kt` | Repository implementations |
| `core/persistence/di/**` | DI wiring (ObxStoreModule) |
| `core/persistence/obx/ObxStore.kt` | Store singleton |
| `core/persistence/inspector/**` | Debug database inspector |
| `**/test/**`, `**/androidTest/**` | Test code |

### ⚠️ Important: core/persistence

Per `AGENTS.md` and `.github/instructions/core-persistence.instructions.md`:

> `core/persistence` is **schema-only**: Entities + Store infrastructure.
> Repository **implementations** belong in `infra/data-*/**`.

**FORBIDDEN:**
```
core/persistence/*Repository*.kt  ← NO REPOS HERE!
```

## Security: Secrets in Entities (OBX-02)

ObjectBox stores data in **plain files**. Sensitive data patterns are forbidden:

- `password`
- `secret`
- `token`
- `apiKey`
- `authToken`
- `accessToken`

Use `EncryptedSharedPreferences` or Android Keystore for sensitive data.

## Ledger Responsibility (OBX-03)

**Important:** The ledger guard is NOT a pipeline responsibility!

| Layer | Ledger? |
|-------|----------|
| `pipeline/**` | ❌ NO - produces RawMediaMetadata only |
| `core/catalog-sync/**` | ✅ YES - orchestrates ingest |

Per `contracts/MEDIA_NORMALIZATION_CONTRACT.md`:

> Pipelines produce `RawMediaMetadata` only. Ledger responsibility belongs to CatalogSync/Ingest orchestration.

## Documentation

- **Contract:** [OBX_PLATIN_DETEKT_GUARDRAILS_CONTRACT.md](../../docs/v2/OBX_PLATIN_DETEKT_GUARDRAILS_CONTRACT.md)
- **Roadmap:** [OBX_PLATIN_REFACTOR_ROADMAP.md](../../docs/v2/OBX_PLATIN_REFACTOR_ROADMAP.md)
- **AGENTS.md:** Section 4 (Layer Boundaries)
- **GLOSSARY:** `/contracts/GLOSSARY_v2_naming_and_modules.md`

## Troubleshooting

### "BoxStore in forbidden location" Error

1. Check if the file is a repository implementation
2. If yes, move it to `infra/data-<source>/` (e.g., `infra/data-telegram/`)
3. If it's DI wiring, ensure it's in `core/persistence/di/`

### "Secrets in @Entity" Error

1. Remove sensitive fields from the entity
2. Store sensitive data in `EncryptedSharedPreferences`:

```kotlin
// In separate SecureStorage class
class SecureCredentialsStore @Inject constructor(
    private val encryptedPrefs: EncryptedSharedPreferences,
) {
    fun saveApiKey(key: String) {
        encryptedPrefs.edit().putString("api_key", key).apply()
    }
}
```

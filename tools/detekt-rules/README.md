# Detekt Custom Rules for OBX PLATIN

## Overview

This directory is reserved for custom Detekt rules to enforce architectural constraints for the OBX PLATIN Refactor (#621).

**Status:** 🚧 Custom rules NOT YET IMPLEMENTED  
**Phase:** 0.3 - Contracts & Guardrails  
**Parent Issue:** #621

## Implemented Rules (via detekt-config.yml)

### ✅ Rule 1: NoBoxStoreOutsideRepository

**Status:** Implemented via `ForbiddenImport` in `detekt-config.yml`

**Purpose:** Enforce that `BoxStore` is only used in repository implementations.

**Implementation:**
- Added `io.objectbox.BoxStore` to forbidden imports
- Excluded paths:
  - `**/infra/data-*/**` (repository implementations)
  - `**/core/persistence/**` (persistence layer)
  - Test files

**Allowed Locations:**
- `*Repository*.kt` files in `infra/data-*` and `core/persistence`
- `core/persistence/di/ObxStoreModule.kt` (DI setup)
- `core/persistence/obx/ObxStore.kt` (singleton)
- `core/persistence/inspector/` (debug utilities)

**Verification:**
```bash
# Check for violations (should return empty)
grep -r "import io.objectbox.BoxStore" --include="*.kt" | \
  grep -v "Repository" | \
  grep -v "test/" | \
  grep -v "core/persistence/di/" | \
  grep -v "core/persistence/obx/ObxStore" | \
  grep -v "inspector/"
```

## Pending Custom Rules (Require Implementation)

### 🔜 Rule 2: NoSecretsInObx

**Status:** Documented, awaiting custom rule implementation

**Purpose:** Detect sensitive field patterns in `@Entity` classes to prevent secrets in persistence layer.

**Detection Pattern:**
- Scan `@Entity` annotated classes
- Flag fields matching: `password`, `secret`, `token`, `apiKey` (case-insensitive)
- Severity: ERROR

**Manual Verification:**
```bash
# Check for sensitive fields in entities
grep -r "password\|secret\|token\|apiKey" core/persistence/src/main/java \
  --include="*.kt" -i -B 5 | grep -B 5 -i "@Entity"
```

**Why Custom Rule Needed:**
Detekt's `ForbiddenImport` and `ForbiddenMethodCall` cannot detect field naming patterns within class bodies.

### 🔜 Rule 3: IngestLedgerRequired

**Status:** Documented, awaiting custom rule implementation

**Purpose:** Ensure all ingest/catalog processing creates IngestLedger entries (no silent drops).

**Detection Pattern:**
- Functions in `pipeline/**` that process `RawMediaMetadata`
- Must call `IngestLedger.record()` or similar tracking method
- Reasons: `ACCEPTED`, `REJECTED`, `SKIPPED`

**Contract Reference:**  
docs/v2/OBX_PLATIN_REFACTOR_ROADMAP.md:
> "Every ingest candidate must create exactly one IngestLedger entry (ACCEPTED | REJECTED | SKIPPED) – no silent drops."

**Why Custom Rule Needed:**
Requires control flow analysis to verify ledger recording in processing functions.

## Implementation Guide

To implement custom rules:

### 1. Create Gradle Module

Create `tools/detekt-rules/build.gradle.kts`:

```kotlin
plugins {
    kotlin("jvm")
}

dependencies {
    compileOnly("io.gitlab.arturbosch.detekt:detekt-api:1.23.8")
    
    testImplementation("io.gitlab.arturbosch.detekt:detekt-test:1.23.8")
    testImplementation("io.kotest:kotest-assertions-core:5.8.0")
    testImplementation("junit:junit:4.13.2")
}

kotlin {
    jvmToolchain(21)
}
```

### 2. Implement Rule Classes

Example structure:

```
tools/detekt-rules/src/main/kotlin/
├── com/fishit/player/detekt/
│   ├── ObjPlatinRuleSetProvider.kt
│   ├── NoSecretsInObxRule.kt
│   ├── IngestLedgerRequiredRule.kt
│   └── ...
└── META-INF/services/
    └── io.gitlab.arturbosch.detekt.api.RuleSetProvider
```

### 3. Register RuleSet Provider

Create `META-INF/services/io.gitlab.arturbosch.detekt.api.RuleSetProvider`:

```
com.fishit.player.detekt.ObjPlatinRuleSetProvider
```

### 4. Wire to Root Build

In root `build.gradle.kts`:

```kotlin
subprojects {
    if (project.path.startsWith(":tools:")) {
        return@subprojects
    }
    
    apply(plugin = "io.gitlab.arturbosch.detekt")
    
    dependencies {
        detektPlugins(project(":tools:detekt-rules"))
    }
}
```

### 5. Enable Rules in Config

In `detekt-config.yml`:

```yaml
ObjPlatinRuleSet:
  active: true
  NoSecretsInObx:
    active: true
    sensitivePatterns:
      - 'password'
      - 'secret'
      - 'token'
      - 'apiKey'
  IngestLedgerRequired:
    active: true
    ledgerMethodNames:
      - 'record'
      - 'trackIngest'
```

### 6. Test Rules

Create test cases in `tools/detekt-rules/src/test/kotlin/`:

```kotlin
class NoSecretsInObxRuleTest {
    @Test
    fun `reports password field in Entity`() {
        val code = """
            @Entity
            data class User(
                @Id var id: Long = 0,
                var password: String = "" // Should be flagged
            )
        """.trimIndent()
        
        val findings = NoSecretsInObxRule().compileAndLintWithContext(env, code)
        assertThat(findings).hasSize(1)
    }
}
```

## CI Integration

Once custom rules are implemented, CI will automatically enforce them:

### GitHub Actions Workflow

The `android-quality.yml` workflow already includes Detekt:

```yaml
- name: Run Detekt
  if: inputs.checks_profile == 'full' || inputs.checks_profile == 'fast'
  run: ./gradlew detekt
```

### Local Development

Run all checks:
```bash
./gradlew detekt
```

Run specific module:
```bash
./gradlew :core:persistence:detekt
```

Auto-fix (where possible):
```bash
./gradlew detektFormat
```

## Current Enforcement

Until custom rules are implemented, enforcement is via:

1. **Automated (Rule 1 - BoxStore):** Via `ForbiddenImport` in detekt-config.yml
2. **Manual Review (Rules 2 & 3):** 
   - Code review checklist
   - Manual verification scripts (see above)
   - Agent instructions in `.github/instructions/`

## References

- Parent Issue: #621
- Roadmap: docs/v2/OBX_PLATIN_REFACTOR_ROADMAP.md
- Architecture: AGENTS.md Section 4
- Detekt Documentation: https://detekt.dev/docs/introduction/extensions/

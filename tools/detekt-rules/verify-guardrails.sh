#!/bin/bash
# ============================================================================
# OBX PLATIN Phase 0.3 - Guardrail Verification Script
# ============================================================================
# This script MUST run in CI alongside ./gradlew detekt to enforce guardrails
# that cannot be expressed with Detekt's rule-level excludes.
#
# EXIT CODES:
#   0 = All rules pass
#   1 = Rule 1 (BoxStore) violation
#   2 = Rule 2 (Secrets in Entity) violation
#
# USAGE:
#   bash tools/detekt-rules/verify-guardrails.sh
#
# SSOT REFERENCES:
#   - AGENTS.md Section 4 (Layer Boundaries)
#   - contracts/GLOSSARY_v2_naming_and_modules.md
#   - .github/instructions/core-persistence.instructions.md
#   - .github/instructions/infra-data.instructions.md
# ============================================================================

set -euo pipefail

# Determine repo root (works in CI and local dev)
if [[ -n "${GITHUB_WORKSPACE:-}" ]]; then
  REPO_ROOT="$GITHUB_WORKSPACE"
else
  REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
fi

cd "$REPO_ROOT"

EXIT_CODE=0
echo "========================================"
echo "OBX PLATIN Guardrail Verification"
echo "========================================"
echo "Repository root: $REPO_ROOT"
echo ""

# ============================================================================
# RULE 1: BoxStore Allowlist (OBX-01)
# ============================================================================
# BoxStore may ONLY be used in:
#   - infra/data-*/**/*Repository*.kt  (repository implementations)
#   - core/persistence/di/**            (DI wiring)
#   - core/persistence/obx/ObxStore.kt  (store singleton)
#   - core/persistence/inspector/**     (debug inspector)
#   - **/test/**, **/androidTest/**     (test code)
#
# EXPLICITLY FORBIDDEN:
#   - core/persistence/*Repository*.kt  (NO repos in schema-only layer!)
#   - feature/**, player/**, playback/** (must use interfaces)
#   - pipeline/** (produces RawMediaMetadata only, no persistence)
# ============================================================================

echo "Rule 1: BoxStore Allowlist (OBX-01)"
echo "-----------------------------------"

# Find all BoxStore usages
BOXSTORE_FILES=$(grep -rl "BoxStore" \
  --include="*.kt" \
  --exclude-dir="build" \
  --exclude-dir=".gradle" \
  --exclude-dir="legacy" \
  . 2>/dev/null || true)

VIOLATIONS=""

for file in $BOXSTORE_FILES; do
  # Normalize path
  file_rel="${file#./}"
  
  # Skip test files
  if [[ "$file_rel" == *"/test/"* ]] || [[ "$file_rel" == *"/androidTest/"* ]] || [[ "$file_rel" == *"/testFixtures/"* ]]; then
    continue
  fi
  
  # ALLOWED: infra/data-*/**/*Repository*.kt
  if [[ "$file_rel" == infra/data-*/src/* ]] && [[ "$file_rel" == *Repository*.kt ]]; then
    echo "  ✅ ALLOWED (infra/data-* repo): $file_rel"
    continue
  fi
  
  # ALLOWED: core/persistence/di/**
  if [[ "$file_rel" == core/persistence/src/*/java/*/di/* ]] || [[ "$file_rel" == core/persistence/src/*/kotlin/*/di/* ]]; then
    echo "  ✅ ALLOWED (DI wiring): $file_rel"
    continue
  fi
  
  # ALLOWED: core/persistence/obx/ObxStore.kt
  if [[ "$file_rel" == *"/obx/ObxStore.kt" ]]; then
    echo "  ✅ ALLOWED (ObxStore singleton): $file_rel"
    continue
  fi
  
  # ALLOWED: core/persistence/inspector/**
  if [[ "$file_rel" == *"/inspector/"* ]]; then
    echo "  ✅ ALLOWED (debug inspector): $file_rel"
    continue
  fi
  
  # FORBIDDEN: core/persistence/*Repository*.kt (schema-only layer!)
  if [[ "$file_rel" == core/persistence/* ]] && [[ "$file_rel" == *Repository*.kt ]]; then
    echo "  ❌ VIOLATION: Repository in core/persistence (schema-only layer!): $file_rel"
    echo "     → Move to infra/data-<source>/ per AGENTS.md Section 4"
    VIOLATIONS="$VIOLATIONS\n  - $file_rel (repo in schema-only layer)"
    EXIT_CODE=1
    continue
  fi
  
  # FORBIDDEN: Everything else
  echo "  ❌ VIOLATION: BoxStore in forbidden location: $file_rel"
  VIOLATIONS="$VIOLATIONS\n  - $file_rel"
  EXIT_CODE=1
done

if [[ -z "$VIOLATIONS" ]]; then
  echo "  ✅ All BoxStore usages are in allowed locations"
else
  echo ""
  echo "  VIOLATIONS FOUND:$VIOLATIONS"
fi

echo ""

# ============================================================================
# RULE 2: No Secrets in @Entity (OBX-02)
# ============================================================================
# ObjectBox stores data in plain files on device storage.
# Sensitive fields MUST NOT be stored in @Entity classes.
#
# FORBIDDEN PATTERNS (case-insensitive):
#   - password, secret, token, apiKey, authToken, accessToken
#
# Use EncryptedSharedPreferences or Android Keystore instead.
# ============================================================================

echo "Rule 2: No Secrets in @Entity (OBX-02)"
echo "--------------------------------------"

# Find all @Entity files
ENTITY_FILES=$(grep -rl "@Entity" \
  --include="*.kt" \
  --exclude-dir="build" \
  --exclude-dir=".gradle" \
  --exclude-dir="legacy" \
  --exclude-dir="test" \
  --exclude-dir="androidTest" \
  . 2>/dev/null || true)

SECRET_VIOLATIONS=""

for file in $ENTITY_FILES; do
  file_rel="${file#./}"
  
  # Check for sensitive field names (case-insensitive)
  if grep -iE "(password|secret|token|apiKey|authToken|accessToken)" "$file" | grep -v "//" | grep -qE "(var|val)\s+"; then
    echo "  ❌ SECURITY VIOLATION: Potential secrets in @Entity: $file_rel"
    grep -inE "(password|secret|token|apiKey|authToken|accessToken)" "$file" | grep -v "//" | head -5 || true
    SECRET_VIOLATIONS="$SECRET_VIOLATIONS\n  - $file_rel"
    EXIT_CODE=2
  fi
done

if [[ -z "$SECRET_VIOLATIONS" ]]; then
  echo "  ✅ No secrets found in @Entity classes"
else
  echo ""
  echo "  SECURITY VIOLATIONS:$SECRET_VIOLATIONS"
  echo ""
  echo "  ACTION REQUIRED: Move sensitive data to EncryptedSharedPreferences"
fi

echo ""

# ============================================================================
# RULE 3: Ingest Ledger Required (OBX-03) - INFORMATIONAL
# ============================================================================
# This is NOT a pipeline guard - ledger belongs in CatalogSync layer.
#
# Per contracts/MEDIA_NORMALIZATION_CONTRACT.md:
#   - Pipelines produce RawMediaMetadata ONLY
#   - Ledger responsibility is in CatalogSync/Ingest orchestration
#
# This rule is INFORMATIONAL and does NOT fail CI.
# ============================================================================

echo "Rule 3: Ingest Ledger (OBX-03) - INFORMATIONAL"
echo "-----------------------------------------------"
echo "  ℹ️  Ledger is a CatalogSync responsibility, NOT pipeline/**"
echo "  ℹ️  See: contracts/MEDIA_NORMALIZATION_CONTRACT.md"
echo "  ℹ️  This rule requires manual review (no automated check)"
echo ""

# ============================================================================
# Summary
# ============================================================================

echo "========================================"
echo "Summary"
echo "========================================"

if [[ $EXIT_CODE -eq 0 ]]; then
  echo "✅ All guardrails passed!"
elif [[ $EXIT_CODE -eq 1 ]]; then
  echo "❌ FAILED: BoxStore in forbidden locations (OBX-01)"
  echo "   See: AGENTS.md (Layer Boundaries)"
elif [[ $EXIT_CODE -eq 2 ]]; then
  echo "❌ FAILED: Secrets in @Entity classes (OBX-02)"
  echo "   See: AGENTS.md (Layer Boundaries)"
fi

echo ""
echo "Exit code: $EXIT_CODE"
exit $EXIT_CODE

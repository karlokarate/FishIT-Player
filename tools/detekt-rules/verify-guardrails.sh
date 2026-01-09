#!/bin/bash
# OBX PLATIN Phase 0.3 - Manual Verification Script
# Checks for architectural guardrail violations

set -e

echo "================================================"
echo "OBX PLATIN Phase 0.3 - Guardrail Verification"
echo "================================================"
echo ""

# Rule 1: NoBoxStoreOutsideRepository
echo "✓ Rule 1: NoBoxStoreOutsideRepository"
echo "  Checking for BoxStore usage outside repositories..."
violations=$(grep -r "import io.objectbox.BoxStore" --include="*.kt" \
  /home/runner/work/FishIT-Player/FishIT-Player \
  | grep -v "Repository" \
  | grep -v "test/" \
  | grep -v "legacy/" \
  | grep -v "core/persistence/di/" \
  | grep -v "core/persistence/obx/ObxStore" \
  | grep -v "inspector/" || true)

if [ -z "$violations" ]; then
  echo "  ✅ PASS: No violations found"
else
  echo "  ❌ FAIL: Found violations:"
  echo "$violations"
  exit 1
fi
echo ""

# Rule 2: NoSecretsInObx
echo "✓ Rule 2: NoSecretsInObx"
echo "  Checking for sensitive fields in @Entity classes..."
violations=$(cd /home/runner/work/FishIT-Player/FishIT-Player && \
  grep -r "password\|secret\|token\|apiKey" core/persistence/src/main/java \
  --include="*.kt" -i -B 5 2>/dev/null | grep -B 5 -i "@Entity" || true)

if [ -z "$violations" ]; then
  echo "  ✅ PASS: No sensitive fields found in entities"
else
  echo "  ⚠️  WARNING: Found potential sensitive fields:"
  echo "$violations"
  echo "  Note: Manual review required (custom rule not yet implemented)"
fi
echo ""

# Rule 3: IngestLedgerRequired
echo "✓ Rule 3: IngestLedgerRequired"
echo "  Note: Requires custom Detekt rule implementation"
echo "  Manual review required for pipeline ingest tracking"
echo "  ⚠️  NOT AUTOMATED YET - Check during code review"
echo ""

echo "================================================"
echo "Summary"
echo "================================================"
echo "Rule 1 (BoxStore): Automated via ForbiddenImport"
echo "Rule 2 (Secrets):  Manual review (custom rule pending)"
echo "Rule 3 (Ledger):   Manual review (custom rule pending)"
echo ""
echo "See: tools/detekt-rules/README.md for implementation guide"
echo "================================================"

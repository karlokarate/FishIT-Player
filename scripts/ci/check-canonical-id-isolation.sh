#!/usr/bin/env bash
# CI Guardrail: Prevent GlobalIdUtil/CanonicalIdUtil usage outside metadata-normalizer
#
# PURPOSE:
# Enforce that canonical ID generation is ONLY performed by the metadata normalizer.
# Pipelines and other layers must NOT import or use GlobalIdUtil or CanonicalIdUtil.
#
# RATIONALE:
# - Per MEDIA_NORMALIZATION_CONTRACT.md: Only the normalizer may generate canonical IDs
# - Per GLOSSARY_v2: GlobalIdUtil moved out of core:model to prevent pipeline access
# - Pipelines must provide raw metadata only, no normalization or ID generation
#
# ENFORCEMENT:
# - Fail if any file outside core/metadata-normalizer imports GlobalIdUtil or CanonicalIdUtil
# - Fail if any file outside core/metadata-normalizer calls generateCanonicalId() or canonicalHashId()
# - Allow usage only within core/metadata-normalizer module

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

cd "$REPO_ROOT"

echo "========================================="
echo "Canonical ID Isolation Check"
echo "========================================="
echo ""

VIOLATIONS=0

# Pattern 1: Check for GlobalIdUtil imports outside metadata-normalizer
echo "Checking for GlobalIdUtil imports outside metadata-normalizer..."
GLOBAL_ID_IMPORTS=$(grep -rn "^import.*GlobalIdUtil" \
    --include="*.kt" \
    --exclude-dir=".gradle" \
    --exclude-dir="build" \
    --exclude-dir=".git" \
    . | grep -v "core/metadata-normalizer/" || true)

if [[ -n "$GLOBAL_ID_IMPORTS" ]]; then
    echo "❌ VIOLATION: GlobalIdUtil imported outside core:metadata-normalizer"
    echo ""
    echo "Found forbidden imports:"
    echo "$GLOBAL_ID_IMPORTS"
    echo ""
    echo "GlobalIdUtil is deprecated and must not be used."
    echo "Use CanonicalIdUtil in core:metadata-normalizer instead."
    echo "Pipelines must NOT generate canonical IDs - leave globalId empty."
    echo ""
    VIOLATIONS=$((VIOLATIONS + 1))
else
    echo "✅ No GlobalIdUtil imports outside metadata-normalizer"
fi

# Pattern 2: Check for CanonicalIdUtil imports outside metadata-normalizer
echo ""
echo "Checking for CanonicalIdUtil imports outside metadata-normalizer..."
CANONICAL_ID_IMPORTS=$(grep -rn "^import.*CanonicalIdUtil" \
    --include="*.kt" \
    --exclude-dir=".gradle" \
    --exclude-dir="build" \
    --exclude-dir=".git" \
    . | grep -v "core/metadata-normalizer/" || true)

if [[ -n "$CANONICAL_ID_IMPORTS" ]]; then
    echo "❌ VIOLATION: CanonicalIdUtil imported outside core:metadata-normalizer"
    echo ""
    echo "Found forbidden imports:"
    echo "$CANONICAL_ID_IMPORTS"
    echo ""
    echo "CanonicalIdUtil must only be used within core:metadata-normalizer."
    echo "Pipelines must NOT generate canonical IDs - leave globalId empty."
    echo "The normalizer will generate canonical IDs during normalization."
    echo ""
    VIOLATIONS=$((VIOLATIONS + 1))
else
    echo "✅ No CanonicalIdUtil imports outside metadata-normalizer"
fi

# Pattern 3: Check for direct calls to ID generation methods outside metadata-normalizer
echo ""
echo "Checking for canonical ID generation calls outside metadata-normalizer..."
ID_GEN_CALLS=$(grep -rn "generateCanonicalId\|canonicalHashId" \
    --include="*.kt" \
    --exclude-dir=".gradle" \
    --exclude-dir="build" \
    --exclude-dir=".git" \
    . | grep -v "core/metadata-normalizer/" | grep -v "core/model/GlobalIdUtil.kt" \
    | grep -v "// " | grep -v "/\*" || true)

if [[ -n "$ID_GEN_CALLS" ]]; then
    echo "❌ VIOLATION: Canonical ID generation called outside core:metadata-normalizer"
    echo ""
    echo "Found forbidden calls:"
    echo "$ID_GEN_CALLS"
    echo ""
    echo "Only core:metadata-normalizer may generate canonical IDs."
    echo "Pipelines must provide raw metadata with empty globalId field."
    echo ""
    VIOLATIONS=$((VIOLATIONS + 1))
else
    echo "✅ No canonical ID generation calls outside metadata-normalizer"
fi

# Pattern 4: Verify pipelines are NOT populating globalId
echo ""
echo "Checking that pipelines leave globalId empty..."
PIPELINE_GLOBAL_ID=$(grep -rn 'globalId = [^""]' \
    --include="*.kt" \
    pipeline/ 2>/dev/null | grep -v 'globalId = ""' || true)

if [[ -n "$PIPELINE_GLOBAL_ID" ]]; then
    echo "❌ VIOLATION: Pipeline code is populating globalId"
    echo ""
    echo "Found violations:"
    echo "$PIPELINE_GLOBAL_ID"
    echo ""
    echo "Pipelines must leave globalId empty (\"\")."
    echo "The normalizer will generate canonical IDs during normalization."
    echo ""
    VIOLATIONS=$((VIOLATIONS + 1))
else
    echo "✅ Pipelines correctly leave globalId empty"
fi

echo ""
echo "========================================="
if [[ $VIOLATIONS -eq 0 ]]; then
    echo "✅ All canonical ID isolation checks passed"
    echo "========================================="
    exit 0
else
    echo "❌ Found $VIOLATIONS violation(s)"
    echo "========================================="
    echo ""
    echo "REQUIRED ACTIONS:"
    echo "1. Remove all imports of GlobalIdUtil and CanonicalIdUtil from pipeline code"
    echo "2. Remove all calls to generateCanonicalId() and canonicalHashId() from pipeline code"
    echo "3. Ensure pipelines set globalId = \"\" (empty string)"
    echo "4. Let the normalizer generate canonical IDs during normalization"
    echo ""
    echo "See contracts/MEDIA_NORMALIZATION_CONTRACT.md for details"
    exit 1
fi

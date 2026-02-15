#!/bin/bash
# DTO Quality Gates - Combined enforcement script
# Run this before committing DTO changes
# Usage: ./scripts/ci/dto-quality-gates.sh
set -e

echo "=========================================="
echo "  DTO Quality Gates (v2 Playbook)"
echo "=========================================="
echo ""

VIOLATIONS=0
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$REPO_ROOT"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# ------------------------------------------------------------------------------
# Gate 1: Layer Boundary Check
# ------------------------------------------------------------------------------
echo -e "${YELLOW}[Gate 1/4] Layer Boundary Check${NC}"

# Pipeline must not import from data/persistence
if grep -rn "import.*infra\.data\|import.*core\.persistence\.obx\|import.*io\.objectbox" pipeline/ --include="*.kt" 2>/dev/null | grep -v "/build/"; then
    echo -e "${RED}❌ Pipeline imports from data/persistence layer${NC}"
    VIOLATIONS=$((VIOLATIONS + 1))
fi

# Transport must not import from pipeline
if grep -rn "import.*\.pipeline\." infra/transport-*/ --include="*.kt" 2>/dev/null | grep -v "/build/"; then
    echo -e "${RED}❌ Transport imports from pipeline layer${NC}"
    VIOLATIONS=$((VIOLATIONS + 1))
fi

# Feature must not import from transport/pipeline/persistence
FEATURE_VIOLATIONS=$(grep -rn "import.*infra\.transport\|import.*\.pipeline\.\|import.*core\.persistence\.obx\|import.*io\.objectbox" feature/ --include="*.kt" 2>/dev/null | grep -v "/build/" || true)
if [ -n "$FEATURE_VIOLATIONS" ]; then
    echo "$FEATURE_VIOLATIONS"
    echo -e "${RED}❌ Feature imports from transport/pipeline/persistence${NC}"
    VIOLATIONS=$((VIOLATIONS + 1))
fi

# Player must not import from transport/pipeline/data
PLAYER_VIOLATIONS=$(grep -rn "import.*infra\.transport\|import.*\.pipeline\.\|import.*infra\.data" player/ --include="*.kt" 2>/dev/null | grep -v "/build/" || true)
if [ -n "$PLAYER_VIOLATIONS" ]; then
    echo "$PLAYER_VIOLATIONS"
    echo -e "${RED}❌ Player imports from transport/pipeline/data${NC}"
    VIOLATIONS=$((VIOLATIONS + 1))
fi

if [ $VIOLATIONS -eq 0 ]; then
    echo -e "${GREEN}✅ Layer boundary check passed${NC}"
fi
echo ""

# ------------------------------------------------------------------------------
# Gate 2: DTO Purity Check (no functions in DTOs)
# ------------------------------------------------------------------------------
echo -e "${YELLOW}[Gate 2/4] DTO Purity Check${NC}"

PURITY_VIOLATIONS=0

# Find all *Dto.kt, *UiModel.kt files (excluding build and legacy)
# Use -type f to only get files and proper exclusion syntax
DTO_FILES=$(find . -type d \( -name "legacy" -o -name "build" \) -prune -o -type f \( -name "*Dto.kt" -o -name "*UiModel.kt" \) -print 2>/dev/null)

if [ -z "$DTO_FILES" ]; then
    echo "No DTO/UiModel files found (this is OK if none exist yet)"
else
    for file in $DTO_FILES; do
        if [ -f "$file" ]; then
            # Check for "fun " outside of companion objects (but allow InlineBytes.equals/hashCode/toString)
            # Also allow toRawMediaMetadata() in Pipeline internal DTOs
            FUNCS=$(grep -n "^\s*fun\s\|^\s*suspend\s*fun\s\|^\s*override\s*fun\s" "$file" 2>/dev/null | grep -v "equals\|hashCode\|toString\|toRawMediaMetadata" || true)
            if [ -n "$FUNCS" ]; then
                echo -e "${RED}❌ Function in DTO: $file${NC}"
                echo "$FUNCS"
                PURITY_VIOLATIONS=$((PURITY_VIOLATIONS + 1))
            fi
        fi
    done
fi

if [ $PURITY_VIOLATIONS -gt 0 ]; then
    VIOLATIONS=$((VIOLATIONS + PURITY_VIOLATIONS))
else
    echo -e "${GREEN}✅ DTO purity check passed${NC}"
fi
echo ""

# ------------------------------------------------------------------------------
# Gate 3: ImageRef Check (no raw URL strings for images in feature layer)
# ------------------------------------------------------------------------------
echo -e "${YELLOW}[Gate 3/4] ImageRef Check${NC}"

IMAGEREF_VIOLATIONS=0

# Find UI models in feature layer
UI_MODEL_FILES=$(find feature/ -type d -name "build" -prune -o -type f \( -name "*UiModel.kt" -o -name "*Info.kt" -o -name "*ItemModel.kt" -o -name "*UiState.kt" \) -print 2>/dev/null)

if [ -z "$UI_MODEL_FILES" ]; then
    echo "No UI model files found in feature/ (checking later when created)"
else
    for file in $UI_MODEL_FILES; do
        if [ -f "$file" ]; then
            # Check for fields named *Url, *url, *URL that are String type (not ImageRef)
            URL_STRINGS=$(grep -En "val\s+(poster|backdrop|thumbnail|image|icon|cover|banner|logo)(Url|URL|url)\s*:\s*String" "$file" 2>/dev/null || true)
            if [ -n "$URL_STRINGS" ]; then
                echo -e "${RED}❌ URL string field (should be ImageRef): $file${NC}"
                echo "$URL_STRINGS"
                IMAGEREF_VIOLATIONS=$((IMAGEREF_VIOLATIONS + 1))
            fi
        fi
    done
fi

if [ $IMAGEREF_VIOLATIONS -gt 0 ]; then
    VIOLATIONS=$((VIOLATIONS + IMAGEREF_VIOLATIONS))
else
    echo -e "${GREEN}✅ ImageRef check passed${NC}"
fi
echo ""

# ------------------------------------------------------------------------------
# Gate 4: v1 Namespace Check
# ------------------------------------------------------------------------------
echo -e "${YELLOW}[Gate 4/4] v1 Namespace Check${NC}"

V1_VIOLATIONS=$(grep -rn "com\.chris\.m3usuite" --include="*.kt" app-v2/ core/ infra/ feature/ player/ playback/ pipeline/ 2>/dev/null | grep -v "/build/" || true)
if [ -n "$V1_VIOLATIONS" ]; then
    echo "$V1_VIOLATIONS"
    echo -e "${RED}❌ v1 namespace (com.chris.m3usuite) found outside legacy/${NC}"
    VIOLATIONS=$((VIOLATIONS + 1))
else
    echo -e "${GREEN}✅ v1 namespace check passed${NC}"
fi
echo ""

# ------------------------------------------------------------------------------
# Summary
# ------------------------------------------------------------------------------
echo "=========================================="
if [ $VIOLATIONS -gt 0 ]; then
    echo -e "${RED}❌ FAILED: $VIOLATIONS quality gate violations${NC}"
    echo ""
    echo "Fix the violations above before committing."
    echo "See contracts/GLOSSARY_v2_naming_and_modules.md for rules."
    exit 1
else
    echo -e "${GREEN}✅ ALL DTO QUALITY GATES PASSED${NC}"
    exit 0
fi

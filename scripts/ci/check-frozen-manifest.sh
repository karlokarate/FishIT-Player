#!/usr/bin/env bash
# Frozen Module Manifest Enforcement
# This script enforces the frozen module manifest rules from AGENTS.md
# NO new modules may be added after the one-time stub PR

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

cd "$REPO_ROOT"

echo "========================================="
echo "Frozen Module Manifest Enforcement"
echo "========================================="
echo ""

VIOLATIONS=0

# ======================================================================
# FROZEN MODULE LIST: Define the canonical list of allowed modules
# ======================================================================

# This is the FROZEN list from AGENTS.md
# NO additions allowed after the one-time stub PR
declare -a FROZEN_MODULES=(
    # App
    "app-v2"
    
    # Core
    "core/model"
    "core/player-model"
    "core/feature-api"
    "core/persistence"
    "core/metadata-normalizer"
    "core/catalog-sync"
    "core/firebase"
    "core/ui-imaging"
    "core/ui-theme"
    "core/ui-layout"
    "core/app-startup"
    
    # Playback & Player
    "playback/domain"
    "playback/telegram"
    "playback/xtream"
    "player/ui"
    "player/ui-api"
    "player/internal"
    "player/miniplayer"
    "player/nextlib-codecs"
    
    # Pipelines
    "pipeline/telegram"
    "pipeline/xtream"
    "pipeline/io"
    "pipeline/audiobook"
    
    # Features
    "feature/home"
    "feature/library"
    "feature/live"
    "feature/detail"
    "feature/telegram-media"
    "feature/audiobooks"
    "feature/settings"
    "feature/onboarding"
    
    # Infrastructure
    "infra/logging"
    "infra/tooling"
    "infra/transport-telegram"
    "infra/transport-xtream"
    "infra/data-telegram"
    "infra/data-xtream"
    "infra/data-home"
    "infra/imaging"
    "infra/work"
)

# ======================================================================
# CHECK 1: Verify all build.gradle.kts files are in frozen modules
# ======================================================================
echo "Checking for unauthorized build.gradle.kts files..."

# Find all build.gradle.kts files, excluding root and legacy
found_build_files=$(find . -name "build.gradle.kts" -type f \
    | grep -v "^\./build.gradle.kts" \
    | grep -v "^\./legacy/" \
    | grep -v "^\./\.gradle/" \
    | sed 's|^\./||' \
    | sed 's|/build\.gradle\.kts$||' \
    || true)

# Modules that intentionally remain outside the frozen manifest
declare -a EXCLUDED_MODULES=(
    "tools/pipeline-cli"
)

if [[ -n "$found_build_files" ]]; then
    while IFS= read -r module_path; do
        # Skip explicitly excluded modules
        for excluded in "${EXCLUDED_MODULES[@]}"; do
            if [[ "$module_path" == "$excluded" ]]; then
                continue 2
            fi
        done

        # Check if this module is in the frozen list
        is_frozen=false
        for frozen_module in "${FROZEN_MODULES[@]}"; do
            if [[ "$module_path" == "$frozen_module" ]]; then
                is_frozen=true
                break
            fi
        done
        
        if [[ "$is_frozen" == false ]]; then
            echo "❌ VIOLATION: Unauthorized module detected: $module_path"
            echo "   File: $module_path/build.gradle.kts"
            echo "   This module is not in the frozen manifest!"
            echo ""
            VIOLATIONS=$((VIOLATIONS + 1))
        fi
    done <<< "$found_build_files"
fi

# ======================================================================
# CHECK 2: Verify settings.gradle.kts has not changed (git-based)
# ======================================================================
echo "Checking for unauthorized settings.gradle.kts changes..."

# Get the base branch (architecture/v2-bootstrap or main)
BASE_BRANCH="${GITHUB_BASE_REF:-architecture/v2-bootstrap}"

# Check if we're in a git repository and can compare
if git rev-parse --git-dir > /dev/null 2>&1; then
    # Try to fetch the base branch (in CI) or use local copy
    if git rev-parse "origin/$BASE_BRANCH" > /dev/null 2>&1; then
        BASE_REF="origin/$BASE_BRANCH"
    elif git rev-parse "$BASE_BRANCH" > /dev/null 2>&1; then
        BASE_REF="$BASE_BRANCH"
    else
        echo "⚠️  Warning: Cannot find base branch $BASE_BRANCH for comparison"
        BASE_REF=""
    fi
    
    if [[ -n "$BASE_REF" ]]; then
        # Check if settings.gradle.kts has changed
        if git diff --name-only "$BASE_REF" HEAD | grep -q "^settings\.gradle\.kts$"; then
            # Settings changed - check if it's the one-time stub PR
            # Allow the change if we're adding EXACTLY the three reserved modules
            
            # Get the diff
            settings_diff=$(git diff "$BASE_REF" HEAD -- settings.gradle.kts || true)
            
            # Check if the diff adds exactly the three reserved modules
            added_imaging=$(echo "$settings_diff" | grep -c '^\+.*include(":infra:imaging")' || echo "0")
            added_work=$(echo "$settings_diff" | grep -c '^\+.*include(":infra:work")' || echo "0")
            added_ui_api=$(echo "$settings_diff" | grep -c '^\+.*include(":player:ui-api")' || echo "0")
            
            # Count total additions (lines starting with + that are not comment lines)
            total_additions=$(echo "$settings_diff" | grep '^+' | grep -v '^+++' | grep -c 'include(' || echo "0")
            
            # Allow if exactly 3 additions and they are the reserved modules
            if [[ "$added_imaging" -eq 1 && "$added_work" -eq 1 && "$added_ui_api" -eq 1 && "$total_additions" -eq 3 ]]; then
                echo "✅ One-time stub PR detected: Adding exactly 3 reserved modules (imaging, work, ui-api)"
            else
                echo "❌ VIOLATION: settings.gradle.kts changed after freeze"
                echo "   The module manifest is FROZEN. No new modules may be added."
                echo "   Expected: Exactly 3 reserved module additions (imaging, work, ui-api)"
                echo "   Found: $total_additions module additions"
                echo ""
                echo "   Added modules:"
                echo "$settings_diff" | grep '^+.*include(' | grep -v '^+++' || true
                echo ""
                VIOLATIONS=$((VIOLATIONS + 1))
            fi
        fi
    fi
fi

# ======================================================================
# CHECK 3: Verify include() statements in settings.gradle.kts match frozen list
# ======================================================================
echo "Checking settings.gradle.kts against frozen manifest..."

# Extract include() statements from settings.gradle.kts
includes=$(grep 'include(":[^"]*")' settings.gradle.kts | sed 's/.*include("://;s/")//' | sort)

# Build expected includes from frozen modules
declare -a EXPECTED_INCLUDES=()
for module in "${FROZEN_MODULES[@]}"; do
    # Convert path to include format (e.g., "app-v2" -> ":app-v2", "core/model" -> ":core:model")
    include_name=$(echo "$module" | sed 's|/|:|g')
    EXPECTED_INCLUDES+=("$include_name")
done

# Sort expected includes
IFS=$'\n' sorted_expected=($(sort <<<"${EXPECTED_INCLUDES[*]}"))
unset IFS

# Compare
while IFS= read -r include_line; do
    # Remove the leading :
    module_name="${include_line#:}"
    
    # Check if this include is expected
    is_expected=false
    for expected in "${sorted_expected[@]}"; do
        expected_name="${expected#:}"
        if [[ "$module_name" == "$expected_name" ]]; then
            is_expected=true
            break
        fi
    done
    
    if [[ "$is_expected" == false ]]; then
        echo "❌ VIOLATION: Unexpected module in settings.gradle.kts: :$module_name"
        VIOLATIONS=$((VIOLATIONS + 1))
    fi
done <<< "$includes"

# Check for missing expected modules
while IFS= read -r include_line; do
    # Check if module exists in includes
    if ! echo "$includes" | grep -q "^${include_line}$"; then
        module_path=$(echo "$include_line" | sed 's/:/\//g;s/^://')
        # Only warn if the module directory actually exists
        if [[ -d "$module_path" ]]; then
            echo "❌ VIOLATION: Module exists but not in settings.gradle.kts: $include_line"
            VIOLATIONS=$((VIOLATIONS + 1))
        fi
    fi
done < <(printf '%s\n' "${sorted_expected[@]}")

# ======================================================================
# SUMMARY
# ======================================================================
echo ""
echo "========================================="
if [ $VIOLATIONS -eq 0 ]; then
    echo "✅ Module manifest frozen - no violations"
    echo "========================================="
    exit 0
else
    echo "❌ Found $VIOLATIONS module manifest violation(s)"
    echo "========================================="
    echo ""
    echo "See AGENTS.md for rules"
    echo ""
    echo "The module manifest is FROZEN. NO new modules may be added."
    echo "The ONLY exception is the one-time stub PR adding exactly:"
    echo "  - :infra:imaging"
    echo "  - :infra:work"
    echo "  - :player:ui-api"
    echo ""
    echo "After that PR merges, the manifest is permanently frozen."
    echo ""
    exit 1
fi

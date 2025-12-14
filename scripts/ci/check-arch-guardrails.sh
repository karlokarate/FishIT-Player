#!/usr/bin/env bash
# Architecture Guardrail Checks - Grep-based fallback for layer boundaries
# This script provides additional enforcement beyond Detekt due to Detekt ForbiddenImport limitations
# See AGENTS.md Section 4.5 for layer hierarchy rules

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
ALLOWLIST_FILE="$SCRIPT_DIR/arch-guardrails-allowlist.txt"

cd "$REPO_ROOT"

echo "========================================="
echo "Architecture Guardrail Checks (Grep)"
echo "========================================="
echo ""

VIOLATIONS=0

# ======================================================================
# ALLOWLIST MECHANISM
# ======================================================================

# Load allowlist patterns from file
declare -a ALLOWLIST_PATTERNS=()

load_allowlist() {
    if [[ ! -f "$ALLOWLIST_FILE" ]]; then
        echo "⚠️  Warning: Allowlist file not found at $ALLOWLIST_FILE"
        return
    fi
    
    while IFS= read -r line || [[ -n "$line" ]]; do
        # Skip comments and empty lines
        [[ "$line" =~ ^[[:space:]]*# ]] && continue
        [[ -z "${line// }" ]] && continue
        
        # Extract path pattern (before # comment)
        pattern=$(echo "$line" | cut -d'#' -f1 | xargs)
        [[ -z "$pattern" ]] && continue
        
        ALLOWLIST_PATTERNS+=("$pattern")
    done < "$ALLOWLIST_FILE"
    
    if [[ ${#ALLOWLIST_PATTERNS[@]} -gt 0 ]]; then
        echo "📋 Loaded ${#ALLOWLIST_PATTERNS[@]} allowlist pattern(s)"
        echo ""
    fi
}

# Check if a file path matches any allowlist pattern
# Usage: is_allowlisted "path/to/file.kt"
# Returns: 0 if allowlisted, 1 if not
is_allowlisted() {
    local file_path="$1"
    
    for pattern in "${ALLOWLIST_PATTERNS[@]}"; do
        # Convert glob pattern to regex for matching
        # Simple glob support: * matches anything, ** matches across directories
        if [[ "$file_path" == $pattern ]]; then
            return 0
        fi
    done
    
    return 1
}

# Filter grep output to remove allowlisted files
# Usage: echo "violations" | filter_allowlisted
filter_allowlisted() {
    local output=""
    local filtered_output=""
    
    while IFS= read -r line; do
        # Extract file path from grep output (format: path/to/file.kt:line:content)
        local file_path=$(echo "$line" | cut -d':' -f1)
        
        if is_allowlisted "$file_path"; then
            echo "  ℹ️  Allowlisted: $file_path" >&2
        else
            filtered_output+="$line"$'\n'
        fi
    done
    
    echo -n "$filtered_output"
}

# Load the allowlist at startup
load_allowlist

# ======================================================================
# FEATURE LAYER: Check for forbidden imports
# ======================================================================
echo "Checking feature layer for forbidden imports..."

violations=$(grep -rn "import com\.fishit\.player\.pipeline\." feature/ --include="*.kt" 2>/dev/null | filter_allowlisted || true)
if [[ -n "$violations" ]]; then
    echo "$violations"
    echo "❌ VIOLATION: Feature layer imports pipeline layer"
    VIOLATIONS=$((VIOLATIONS + 1))
fi

violations=$(grep -rn "import com\.fishit\.player\.infra\.data\." feature/ --include="*.kt" 2>/dev/null | filter_allowlisted || true)
if [[ -n "$violations" ]]; then
    echo "$violations"
    echo "❌ VIOLATION: Feature layer imports data layer"
    VIOLATIONS=$((VIOLATIONS + 1))
fi

violations=$(grep -rn "import com\.fishit\.player\.infra\.transport\." feature/ --include="*.kt" 2>/dev/null | filter_allowlisted || true)
if [[ -n "$violations" ]]; then
    echo "$violations"
    echo "❌ VIOLATION: Feature layer imports transport layer"
    VIOLATIONS=$((VIOLATIONS + 1))
fi

violations=$(grep -rn "import com\.fishit\.player\.player\.internal\." feature/ --include="*.kt" 2>/dev/null | filter_allowlisted || true)
if [[ -n "$violations" ]]; then
    echo "$violations"
    echo "❌ VIOLATION: Feature layer imports player internals"
    VIOLATIONS=$((VIOLATIONS + 1))
fi

# ======================================================================
# PLAYBACK LAYER: Check for forbidden imports
# ======================================================================
echo "Checking playback layer for forbidden imports..."

violations=$(grep -rn "import com\.fishit\.player\.pipeline\." playback/ --include="*.kt" 2>/dev/null | grep -v "RawMediaMetadata" | filter_allowlisted || true)
if [[ -n "$violations" ]]; then
    echo "$violations"
    echo "❌ VIOLATION: Playback layer imports pipeline DTOs (should use RawMediaMetadata only)"
    VIOLATIONS=$((VIOLATIONS + 1))
fi

violations=$(grep -rn "import com\.fishit\.player\.infra\.data\." playback/ --include="*.kt" 2>/dev/null | filter_allowlisted || true)
if [[ -n "$violations" ]]; then
    echo "$violations"
    echo "❌ VIOLATION: Playback layer imports data layer"
    VIOLATIONS=$((VIOLATIONS + 1))
fi

# ======================================================================
# PIPELINE LAYER: Check for forbidden imports  
# ======================================================================
echo "Checking pipeline layer for forbidden imports..."

violations=$(grep -rn "import com\.fishit\.player\.infra\.data\." pipeline/ --include="*.kt" 2>/dev/null | filter_allowlisted || true)
if [[ -n "$violations" ]]; then
    echo "$violations"
    echo "❌ VIOLATION: Pipeline layer imports data layer"
    VIOLATIONS=$((VIOLATIONS + 1))
fi

violations=$(grep -rn "import com\.fishit\.player\.playback\." pipeline/ --include="*.kt" 2>/dev/null | filter_allowlisted || true)
if [[ -n "$violations" ]]; then
    echo "$violations"
    echo "❌ VIOLATION: Pipeline layer imports playback layer"
    VIOLATIONS=$((VIOLATIONS + 1))
fi

violations=$(grep -rn "import com\.fishit\.player\.player\." pipeline/ --include="*.kt" 2>/dev/null | filter_allowlisted || true)
if [[ -n "$violations" ]]; then
    echo "$violations"
    echo "❌ VIOLATION: Pipeline layer imports player layer"
    VIOLATIONS=$((VIOLATIONS + 1))
fi

# ======================================================================
# APP-V2 LAYER: Check for forbidden imports (Phase A1.2)
# ======================================================================
echo "Checking app-v2 layer for forbidden imports..."

# App-v2 allowed: core.*, feature.*, playback.domain.*, infra.logging.*
# App-v2 forbidden: player.internal.*, infra.transport.*, pipeline.*

violations=$(grep -rn "import com\.fishit\.player\.player\.internal\." app-v2/ --include="*.kt" 2>/dev/null | filter_allowlisted || true)
if [[ -n "$violations" ]]; then
    echo "$violations"
    echo "❌ VIOLATION: App-v2 imports player internals (must use playback.domain)"
    VIOLATIONS=$((VIOLATIONS + 1))
fi

violations=$(grep -rn "import com\.fishit\.player\.infra\.transport\." app-v2/ --include="*.kt" 2>/dev/null | filter_allowlisted || true)
if [[ -n "$violations" ]]; then
    echo "$violations"
    echo "❌ VIOLATION: App-v2 imports transport layer (must use domain interfaces)"
    VIOLATIONS=$((VIOLATIONS + 1))
fi

violations=$(grep -rn "import com\.fishit\.player\.pipeline\." app-v2/ --include="*.kt" 2>/dev/null | filter_allowlisted || true)
if [[ -n "$violations" ]]; then
    echo "$violations"
    echo "❌ VIOLATION: App-v2 imports pipeline layer (must use domain/feature APIs)"
    VIOLATIONS=$((VIOLATIONS + 1))
fi

# ======================================================================
# BRIDGE/STOPGAP SYMBOL BLOCKERS: Prevent workaround patterns (Phase A1.2)
# ======================================================================
echo "Checking for forbidden bridge/stopgap patterns..."

# Check for legacy v1 bridge patterns
violations=$(grep -rn "TdlibClientProvider" app-v2/ feature/ --include="*.kt" 2>/dev/null | filter_allowlisted || true)
if [[ -n "$violations" ]]; then
    echo "$violations"
    echo "❌ VIOLATION: TdlibClientProvider is a v1 legacy pattern (forbidden in v2)"
    VIOLATIONS=$((VIOLATIONS + 1))
fi

# Check for transport provider workarounds
violations=$(grep -rn "TransportProvider" app-v2/ feature/ --include="*.kt" 2>/dev/null | filter_allowlisted || true)
if [[ -n "$violations" ]]; then
    echo "$violations"
    echo "❌ VIOLATION: *TransportProvider patterns are forbidden (use domain interfaces)"
    VIOLATIONS=$((VIOLATIONS + 1))
fi

# Check for bridge/stopgap naming
violations=$(grep -rn "class.*Bridge\|interface.*Bridge" app-v2/ feature/ --include="*.kt" 2>/dev/null | filter_allowlisted || true)
if [[ -n "$violations" ]]; then
    echo "$violations"
    echo "❌ VIOLATION: *Bridge classes are forbidden workarounds"
    VIOLATIONS=$((VIOLATIONS + 1))
fi

violations=$(grep -rn "class.*Stopgap\|interface.*Stopgap" app-v2/ feature/ --include="*.kt" 2>/dev/null | filter_allowlisted || true)
if [[ -n "$violations" ]]; then
    echo "$violations"
    echo "❌ VIOLATION: *Stopgap classes are forbidden workarounds"
    VIOLATIONS=$((VIOLATIONS + 1))
fi

violations=$(grep -rn "class.*Temporary\|interface.*Temporary" app-v2/ feature/ --include="*.kt" 2>/dev/null | filter_allowlisted || true)
if [[ -n "$violations" ]]; then
    echo "$violations"
    echo "❌ VIOLATION: *Temporary classes indicate technical debt"
    VIOLATIONS=$((VIOLATIONS + 1))
fi

# Check for adapter pattern in app-v2 (should be in infra layer)
violations=$(grep -rn "class.*Adapter" app-v2/ --include="*.kt" 2>/dev/null | grep -v "RecyclerView\.Adapter" | filter_allowlisted || true)
if [[ -n "$violations" ]]; then
    echo "$violations"
    echo "❌ VIOLATION: *Adapter implementations belong in infra layer, not app-v2"
    VIOLATIONS=$((VIOLATIONS + 1))
fi

# ======================================================================
# LOGGING CONTRACT: Check for forbidden logging
# ======================================================================
echo "Checking logging contract compliance..."

# Exclude infra/logging and legacy from logging checks
violations=$(grep -rn "import android\.util\.Log" feature/ app-v2/ core/ pipeline/ playback/ player/ --include="*.kt" 2>/dev/null | filter_allowlisted || true)
if [[ -n "$violations" ]]; then
    echo "$violations"
    echo "❌ VIOLATION: Direct android.util.Log usage (use UnifiedLog)"
    VIOLATIONS=$((VIOLATIONS + 1))
fi

violations=$(grep -rn "import timber\.log\.Timber" feature/ app-v2/ core/ pipeline/ playback/ player/ --include="*.kt" 2>/dev/null | filter_allowlisted || true)
if [[ -n "$violations" ]]; then
    echo "$violations"
    echo "❌ VIOLATION: Direct Timber usage (use UnifiedLog)"
    VIOLATIONS=$((VIOLATIONS + 1))
fi

# ======================================================================
# v2 NAMESPACE: Check for v1 namespace imports
# ======================================================================
echo "Checking v2 namespace compliance..."

violations=$(grep -rn "import com\.chris\.m3usuite\." feature/ app-v2/ core/ infra/ pipeline/ playback/ player/ --include="*.kt" 2>/dev/null | filter_allowlisted || true)
if [[ -n "$violations" ]]; then
    echo "$violations"
    echo "❌ VIOLATION: v1 namespace (com.chris.m3usuite) in v2 modules"
    VIOLATIONS=$((VIOLATIONS + 1))
fi

# ======================================================================
# SUMMARY
# ======================================================================
echo ""
echo "========================================="
if [ $VIOLATIONS -eq 0 ]; then
    echo "✅ No architecture violations detected"
    echo "========================================="
    exit 0
else
    echo "❌ Found $VIOLATIONS architecture violation(s)"
    echo "========================================="
    echo ""
    echo "See AGENTS.md Section 4.5 for layer hierarchy rules"
    echo "See LOGGING_CONTRACT_V2.md for logging requirements"
    echo ""
    echo "💡 If a violation is intentional, add it to scripts/ci/arch-guardrails-allowlist.txt"
    exit 1
fi

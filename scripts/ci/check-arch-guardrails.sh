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
# ALLOWLIST MECHANISM (Phase A1.4: TEMP + expiry enforcement)
# ======================================================================

# Load allowlist patterns from file
declare -a ALLOWLIST_PATTERNS=()
ALLOWLIST_MAX_ENTRIES=10

# Validate allowlist entry format (Phase A1.4)
# Each entry must contain TEMP + (issue OR expiry date)
# Usage: validate_allowlist_entry "line_number" "full_line"
# Returns: 0 if valid, 1 if invalid
validate_allowlist_entry() {
    local line_num="$1"
    local full_line="$2"
    
    # Extract comment part (after #)
    if [[ ! "$full_line" =~ "#" ]]; then
        echo "❌ ALLOWLIST ERROR (line $line_num): Missing # comment separator"
        echo "   Line: $full_line"
        return 1
    fi
    
    local comment=$(echo "$full_line" | cut -d'#' -f2-)
    
    # Check for TEMP marker (case-insensitive)
    if [[ ! "$comment" =~ [Tt][Ee][Mm][Pp] ]]; then
        echo "❌ ALLOWLIST ERROR (line $line_num): Missing TEMP marker"
        echo "   Every allowlist entry must be temporary and contain 'TEMP'"
        echo "   Line: $full_line"
        echo "   💡 Add 'TEMP' to indicate this is a temporary exception"
        return 1
    fi
    
    # Check for issue reference (#123) OR expiry date (YYYY-MM-DD)
    local has_issue=false
    local has_expiry=false
    
    # Check for issue reference (format: #<digits>)
    if [[ "$comment" =~ \#[0-9]+ ]]; then
        has_issue=true
    fi
    
    # Check for expiry date (format: YYYY-MM-DD)
    if [[ "$comment" =~ [0-9]{4}-[0-9]{2}-[0-9]{2} ]]; then
        has_expiry=true
        # Extract the date and validate it's not in the past
        local expiry_date=$(echo "$comment" | grep -oE '[0-9]{4}-[0-9]{2}-[0-9]{2}' | head -1)
        local expiry_epoch=$(date -d "$expiry_date" +%s 2>/dev/null || echo "0")
        local today_epoch=$(date +%s)
        
        if [[ "$expiry_epoch" -eq 0 ]]; then
            echo "❌ ALLOWLIST ERROR (line $line_num): Invalid date format: $expiry_date"
            echo "   Use YYYY-MM-DD format (e.g., 2026-01-15)"
            echo "   Line: $full_line"
            return 1
        fi
        
        if [[ "$expiry_epoch" -lt "$today_epoch" ]]; then
            echo "❌ ALLOWLIST ERROR (line $line_num): Expiry date is in the past: $expiry_date"
            echo "   This entry has expired and must be removed or the code must be refactored"
            echo "   Line: $full_line"
            return 1
        fi
    fi
    
    if [[ "$has_issue" == false && "$has_expiry" == false ]]; then
        echo "❌ ALLOWLIST ERROR (line $line_num): Missing issue reference or expiry date"
        echo "   Every entry must contain either:"
        echo "   - Issue reference: #123"
        echo "   - Expiry date: YYYY-MM-DD (e.g., 2026-01-15)"
        echo "   Line: $full_line"
        echo "   💡 Example: path/file.kt # TEMP until 2026-06-01: reason here"
        return 1
    fi
    
    return 0
}

load_allowlist() {
    if [[ ! -f "$ALLOWLIST_FILE" ]]; then
        echo "⚠️  Warning: Allowlist file not found at $ALLOWLIST_FILE"
        return
    fi
    
    local line_num=0
    local entry_count=0
    local validation_failed=false
    
    while IFS= read -r line || [[ -n "$line" ]]; do
        line_num=$((line_num + 1))
        
        # Skip comments and empty lines
        [[ "$line" =~ ^[[:space:]]*# ]] && continue
        [[ -z "${line// }" ]] && continue
        
        # Validate entry format (Phase A1.4)
        if ! validate_allowlist_entry "$line_num" "$line"; then
            validation_failed=true
            continue
        fi
        
        # Extract path pattern (before # comment)
        pattern=$(echo "$line" | cut -d'#' -f1 | xargs)
        [[ -z "$pattern" ]] && continue
        
        ALLOWLIST_PATTERNS+=("$pattern")
        entry_count=$((entry_count + 1))
    done < "$ALLOWLIST_FILE"
    
    # Check for validation failures
    if [[ "$validation_failed" == true ]]; then
        echo ""
        echo "========================================="
        echo "❌ ALLOWLIST VALIDATION FAILED"
        echo "========================================="
        echo ""
        echo "The allowlist contains invalid entries. See errors above."
        echo "Each entry must contain:"
        echo "  1. TEMP marker (indicating it's temporary)"
        echo "  2. Issue reference (#123) OR expiry date (YYYY-MM-DD)"
        echo "  3. Clear reason explaining the exception"
        echo ""
        echo "See scripts/ci/ALLOWLIST_README.md for guidance."
        exit 1
    fi
    
    # Check entry count cap (Phase A1.4)
    if [[ $entry_count -gt $ALLOWLIST_MAX_ENTRIES ]]; then
        echo "❌ ALLOWLIST ERROR: Too many entries ($entry_count > $ALLOWLIST_MAX_ENTRIES)"
        echo ""
        echo "The allowlist is capped at $ALLOWLIST_MAX_ENTRIES entries to prevent abuse."
        echo "Current entries: $entry_count"
        echo ""
        echo "To add more entries, you must:"
        echo "  1. Remove or refactor existing entries"
        echo "  2. Get architecture review approval"
        echo "  3. Consider if you're bypassing proper architecture"
        echo ""
        echo "Remember: Each allowlist entry is technical debt."
        exit 1
    fi
    
    if [[ ${#ALLOWLIST_PATTERNS[@]} -gt 0 ]]; then
        echo "📋 Loaded ${#ALLOWLIST_PATTERNS[@]} allowlist pattern(s) (max: $ALLOWLIST_MAX_ENTRIES)"
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
# RAW_METADATA_GLOBALID_GUARD: Pipelines must not set/compute globalId
# ======================================================================
echo "Running RAW_METADATA_GLOBALID_GUARD..."

violations=$(grep -R -n "GlobalIdUtil" pipeline/ --include="*.kt" --exclude-dir=build --exclude-dir=generated --exclude-dir=legacy 2>/dev/null || true)
if [[ -n "$violations" ]]; then
    echo "$violations"
    echo "❌ VIOLATION: Pipeline references GlobalIdUtil (globalId is assigned centrally)"
    VIOLATIONS=$((VIOLATIONS + 1))
fi

violations=$(grep -R -n "generateCanonicalId(" pipeline/ --include="*.kt" --exclude-dir=build --exclude-dir=generated --exclude-dir=legacy 2>/dev/null || true)
if [[ -n "$violations" ]]; then
    echo "$violations"
    echo "❌ VIOLATION: Pipeline attempts canonical-id generation (forbidden)"
    VIOLATIONS=$((VIOLATIONS + 1))
fi

violations=$(grep -R -n -E 'globalId\s*=' pipeline/ --include="*.kt" --exclude-dir=build --exclude-dir=generated --exclude-dir=legacy 2>/dev/null || true)
if [[ -n "$violations" ]]; then
    echo "$violations"
    echo "❌ VIOLATION: Pipeline assigns RawMediaMetadata.globalId (must remain empty)"
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
# PLAYER UI LAYER: Check for forbidden Hilt EntryPoints and engine wiring
# ======================================================================
echo "Checking player UI layer for forbidden patterns..."

# NO ALLOWLIST FILTERING for these checks - they are hard boundaries

# Check for Hilt EntryPoint usage (anti-pattern that bypasses constructor injection)
violations=$(grep -rn "dagger\.hilt\.EntryPoint\|@EntryPoint\|EntryPointAccessors" player/ --include="*.kt" 2>/dev/null | grep -E "player/[^/]+/src/.*/ui/" || true)
if [[ -n "$violations" ]]; then
    echo "$violations"
    echo "❌ VIOLATION: Hilt EntryPoint usage forbidden in player:ui (use @HiltViewModel + constructor injection)"
    echo "   EntryPoints are an anti-pattern that bypass proper dependency injection"
    echo "   Use @HiltViewModel with constructor-injected dependencies instead"
    VIOLATIONS=$((VIOLATIONS + 1))
fi

# Check for engine wiring class references (internal implementation details)
violations=$(grep -rn "\bPlaybackSourceResolver\b\|\bResumeManager\b\|\bKidsPlaybackGate\b\|\bNextlibCodecConfigurator\b" player/ --include="*.kt" 2>/dev/null | grep -E "player/[^/]+/src/.*/ui/" || true)
if [[ -n "$violations" ]]; then
    echo "$violations"
    echo "❌ VIOLATION: Engine wiring classes forbidden in player:ui"
    echo "   player:ui must not reference internal engine components:"
    echo "   - PlaybackSourceResolver (internal wiring)"
    echo "   - ResumeManager (internal engine logic)"
    echo "   - KidsPlaybackGate (internal engine logic)"
    echo "   - NextlibCodecConfigurator (internal engine logic)"
    echo "   UI should interact through high-level interfaces only"
    VIOLATIONS=$((VIOLATIONS + 1))
fi

# Check for com.fishit.player.internal.* imports from player:ui
# UI should only use public player APIs, not internal implementation
# Exception: player/internal/ui can import from player/internal (same module)
violations=$(grep -rn "import com\.fishit\.player\.internal\." player/ --include="*.kt" 2>/dev/null | grep -E "player/[^/]+/src/.*/ui/" | grep -v "player/internal/src" | grep -v "import com\.fishit\.player\.internal\.ui\." || true)
if [[ -n "$violations" ]]; then
    echo "$violations"
    echo "❌ VIOLATION: player:ui importing internal package from other modules"
    echo "   player:ui modules must not import com.fishit.player.internal.*"
    echo "   Exception: player/internal/ui can use player/internal.* (same module)"
    echo "   This violates layer isolation and creates tight coupling"
    VIOLATIONS=$((VIOLATIONS + 1))
fi

# ======================================================================
# TELEGRAM AUTH CONTRACT & WIRING GUARDRAILS (hard fail)
# ======================================================================
echo "Running Telegram auth guardrails..."
set +e
"$SCRIPT_DIR/check-telegram-auth-guardrails.sh"
tg_status=$?
set -e
if [ $tg_status -ne 0 ]; then
    echo "❌ Telegram auth guardrails failed"
    VIOLATIONS=$((VIOLATIONS + 1))
else
    echo "✅ Telegram auth guardrails passed"
fi

# ======================================================================
# E) Duplicate Contracts & Shadow Types
# ======================================================================
echo ""
echo "Running duplicate contract guardrails..."
echo ""
set +e
"$SCRIPT_DIR/check-duplicate-contracts.sh"
dc_status=$?
set -e
if [ $dc_status -ne 0 ]; then
    echo "❌ Duplicate contract guardrails failed"
    VIOLATIONS=$((VIOLATIONS + 1))
else
    echo "✅ Duplicate contract guardrails passed"
fi

# ======================================================================
# SUMMARY
# ======================================================================
echo ""
echo "========================================="
if [ $VIOLATIONS -eq 0 ]; then
    echo "✅ No architecture violations detected"
    echo "========================================="
    echo ""
    echo "Running frozen module manifest check..."
    echo ""
    # Run the frozen manifest check
    if "$SCRIPT_DIR/check-frozen-manifest.sh"; then
        echo ""
        echo "========================================="
        echo "✅ All checks passed (architecture + manifest)"
        echo "========================================="
        exit 0
    else
        echo ""
        echo "========================================="
        echo "❌ Frozen manifest check failed"
        echo "========================================="
        exit 1
    fi
else
    echo "❌ Found $VIOLATIONS architecture violation(s)"
    echo "========================================="
    echo ""
    echo "See AGENTS.md Section 4.5 for layer hierarchy rules"
    echo "See LOGGING_CONTRACT_V2.md for logging requirements"
    echo ""
    echo "⚠️  IMPORTANT: Fix the code, don't bypass with allowlist"
    echo ""
    echo "The allowlist is for TEMPORARY exceptions only (e.g., DI wiring)."
    echo "Each entry must have TEMP marker + issue/expiry date."
    echo ""
    echo "Before adding to allowlist:"
    echo "  1. ✅ Try to fix the violation by refactoring the code"
    echo "  2. ✅ Create proper domain interfaces in the feature layer"
    echo "  3. ✅ Move adapters to infra layer, not app-v2"
    echo ""
    echo "Only if architecturally necessary:"
    echo "  - Add to scripts/ci/arch-guardrails-allowlist.txt"
    echo "  - Include TEMP + expiry date or issue reference"
    echo "  - Document clear reason and migration plan"
    echo ""
    echo "See scripts/ci/ALLOWLIST_README.md for guidance."
    exit 1
fi

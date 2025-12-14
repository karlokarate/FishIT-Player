#!/usr/bin/env bash
# Architecture Guardrail Checks - Grep-based fallback for layer boundaries
# This script provides additional enforcement beyond Detekt due to Detekt ForbiddenImport limitations
# See AGENTS.md Section 4.5 for layer hierarchy rules

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

cd "$REPO_ROOT"

echo "========================================="
echo "Architecture Guardrail Checks (Grep)"
echo "========================================="
echo ""

VIOLATIONS=0

# ======================================================================
# FEATURE LAYER: Check for forbidden imports
# ======================================================================
echo "Checking feature layer for forbidden imports..."

if grep -rn "import com\.fishit\.player\.pipeline\." feature/ --include="*.kt" 2>/dev/null; then
    echo "❌ VIOLATION: Feature layer imports pipeline layer"
    VIOLATIONS=$((VIOLATIONS + 1))
fi

if grep -rn "import com\.fishit\.player\.infra\.data\." feature/ --include="*.kt" 2>/dev/null; then
    echo "❌ VIOLATION: Feature layer imports data layer"
    VIOLATIONS=$((VIOLATIONS + 1))
fi

if grep -rn "import com\.fishit\.player\.infra\.transport\." feature/ --include="*.kt" 2>/dev/null; then
    echo "❌ VIOLATION: Feature layer imports transport layer"
    VIOLATIONS=$((VIOLATIONS + 1))
fi

if grep -rn "import com\.fishit\.player\.player\.internal\." feature/ --include="*.kt" 2>/dev/null; then
    echo "❌ VIOLATION: Feature layer imports player internals"
    VIOLATIONS=$((VIOLATIONS + 1))
fi

# ======================================================================
# PLAYBACK LAYER: Check for forbidden imports
# ======================================================================
echo "Checking playback layer for forbidden imports..."

if grep -rn "import com\.fishit\.player\.pipeline\." playback/ --include="*.kt" 2>/dev/null | grep -v "RawMediaMetadata" 2>/dev/null; then
    echo "❌ VIOLATION: Playback layer imports pipeline DTOs (should use RawMediaMetadata only)"
    VIOLATIONS=$((VIOLATIONS + 1))
fi

if grep -rn "import com\.fishit\.player\.infra\.data\." playback/ --include="*.kt" 2>/dev/null; then
    echo "❌ VIOLATION: Playback layer imports data layer"
    VIOLATIONS=$((VIOLATIONS + 1))
fi

# ======================================================================
# PIPELINE LAYER: Check for forbidden imports  
# ======================================================================
echo "Checking pipeline layer for forbidden imports..."

if grep -rn "import com\.fishit\.player\.infra\.data\." pipeline/ --include="*.kt" 2>/dev/null; then
    echo "❌ VIOLATION: Pipeline layer imports data layer"
    VIOLATIONS=$((VIOLATIONS + 1))
fi

if grep -rn "import com\.fishit\.player\.playback\." pipeline/ --include="*.kt" 2>/dev/null; then
    echo "❌ VIOLATION: Pipeline layer imports playback layer"
    VIOLATIONS=$((VIOLATIONS + 1))
fi

if grep -rn "import com\.fishit\.player\.player\." pipeline/ --include="*.kt" 2>/dev/null; then
    echo "❌ VIOLATION: Pipeline layer imports player layer"
    VIOLATIONS=$((VIOLATIONS + 1))
fi

# ======================================================================
# APP-V2 LAYER: Check for forbidden imports (Phase A1.2)
# ======================================================================
echo "Checking app-v2 layer for forbidden imports..."

# App-v2 allowed: core.*, feature.*, playback.domain.*, infra.logging.*
# App-v2 forbidden: player.internal.*, infra.transport.*, pipeline.*

if grep -rn "import com\.fishit\.player\.player\.internal\." app-v2/ --include="*.kt" 2>/dev/null; then
    echo "❌ VIOLATION: App-v2 imports player internals (must use playback.domain)"
    VIOLATIONS=$((VIOLATIONS + 1))
fi

if grep -rn "import com\.fishit\.player\.infra\.transport\." app-v2/ --include="*.kt" 2>/dev/null; then
    echo "❌ VIOLATION: App-v2 imports transport layer (must use domain interfaces)"
    VIOLATIONS=$((VIOLATIONS + 1))
fi

if grep -rn "import com\.fishit\.player\.pipeline\." app-v2/ --include="*.kt" 2>/dev/null; then
    echo "❌ VIOLATION: App-v2 imports pipeline layer (must use domain/feature APIs)"
    VIOLATIONS=$((VIOLATIONS + 1))
fi

# ======================================================================
# BRIDGE/STOPGAP SYMBOL BLOCKERS: Prevent workaround patterns (Phase A1.2)
# ======================================================================
echo "Checking for forbidden bridge/stopgap patterns..."

# Check for legacy v1 bridge patterns
if grep -rn "TdlibClientProvider" app-v2/ feature/ --include="*.kt" 2>/dev/null; then
    echo "❌ VIOLATION: TdlibClientProvider is a v1 legacy pattern (forbidden in v2)"
    VIOLATIONS=$((VIOLATIONS + 1))
fi

# Check for transport provider workarounds
if grep -rn "TransportProvider" app-v2/ feature/ --include="*.kt" 2>/dev/null; then
    echo "❌ VIOLATION: *TransportProvider patterns are forbidden (use domain interfaces)"
    VIOLATIONS=$((VIOLATIONS + 1))
fi

# Check for bridge/stopgap naming
if grep -rn "class.*Bridge\|interface.*Bridge" app-v2/ feature/ --include="*.kt" 2>/dev/null; then
    echo "❌ VIOLATION: *Bridge classes are forbidden workarounds"
    VIOLATIONS=$((VIOLATIONS + 1))
fi

if grep -rn "class.*Stopgap\|interface.*Stopgap" app-v2/ feature/ --include="*.kt" 2>/dev/null; then
    echo "❌ VIOLATION: *Stopgap classes are forbidden workarounds"
    VIOLATIONS=$((VIOLATIONS + 1))
fi

if grep -rn "class.*Temporary\|interface.*Temporary" app-v2/ feature/ --include="*.kt" 2>/dev/null; then
    echo "❌ VIOLATION: *Temporary classes indicate technical debt"
    VIOLATIONS=$((VIOLATIONS + 1))
fi

# Check for adapter pattern in app-v2 (should be in infra layer)
if grep -rn "class.*Adapter" app-v2/ --include="*.kt" 2>/dev/null | grep -v "RecyclerView\.Adapter" 2>/dev/null; then
    echo "❌ VIOLATION: *Adapter implementations belong in infra layer, not app-v2"
    VIOLATIONS=$((VIOLATIONS + 1))
fi

# ======================================================================
# LOGGING CONTRACT: Check for forbidden logging
# ======================================================================
echo "Checking logging contract compliance..."

# Exclude infra/logging and legacy from logging checks
if grep -rn "import android\.util\.Log" feature/ app-v2/ core/ pipeline/ playback/ player/ --include="*.kt" 2>/dev/null; then
    echo "❌ VIOLATION: Direct android.util.Log usage (use UnifiedLog)"
    VIOLATIONS=$((VIOLATIONS + 1))
fi

if grep -rn "import timber\.log\.Timber" feature/ app-v2/ core/ pipeline/ playback/ player/ --include="*.kt" 2>/dev/null; then
    echo "❌ VIOLATION: Direct Timber usage (use UnifiedLog)"
    VIOLATIONS=$((VIOLATIONS + 1))
fi

# ======================================================================
# v2 NAMESPACE: Check for v1 namespace imports
# ======================================================================
echo "Checking v2 namespace compliance..."

if grep -rn "import com\.chris\.m3usuite\." feature/ app-v2/ core/ infra/ pipeline/ playback/ player/ --include="*.kt" 2>/dev/null; then
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
    exit 1
fi

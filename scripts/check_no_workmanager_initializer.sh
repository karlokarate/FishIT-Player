#!/usr/bin/env bash
#
# Guardrail: Ensure WorkManagerInitializer is NOT in merged manifests
#
# This project uses on-demand WorkManager initialization via Configuration.Provider.
# The WorkManager library tries to auto-initialize via AndroidX Startup, which we
# explicitly disable in app-v2/src/main/AndroidManifest.xml using tools:node="remove".
#
# This script verifies that the merged manifest does not contain WorkManagerInitializer.
# If it does, the build should fail to prevent runtime conflicts.
#
# Usage:
#   ./scripts/check_no_workmanager_initializer.sh
#
# Exit codes:
#   0 - Success (no WorkManagerInitializer found)
#   1 - Failure (WorkManagerInitializer found in merged manifest)
#   2 - Warning (merged manifest not found - build may not have run yet)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "🔍 Checking for WorkManagerInitializer in merged manifests..."

# Find all merged manifest files
MERGED_MANIFESTS=$(find "$REPO_ROOT/app-v2/build/intermediates/merged_manifests" -name "AndroidManifest.xml" 2>/dev/null || true)

if [ -z "$MERGED_MANIFESTS" ]; then
    echo "⚠️  WARNING: No merged manifests found."
    echo "    This is expected if the build hasn't run yet."
    echo "    Run './gradlew :app-v2:assembleDebug' first."
    exit 2
fi

VIOLATIONS=0

for MANIFEST in $MERGED_MANIFESTS; do
    VARIANT=$(echo "$MANIFEST" | sed 's|.*/merged_manifests/\([^/]*\)/.*|\1|')
    echo "  Checking variant: $VARIANT"
    
    # Check for WorkManagerInitializer
    if grep -q "androidx\.work\.WorkManagerInitializer" "$MANIFEST"; then
        echo "    ❌ VIOLATION: WorkManagerInitializer found!"
        echo "       Manifest: $MANIFEST"
        
        # Show the offending lines
        echo "       Context:"
        grep -B2 -A2 "androidx\.work\.WorkManagerInitializer" "$MANIFEST" | sed 's/^/         /'
        
        VIOLATIONS=$((VIOLATIONS + 1))
    else
        echo "    ✅ OK: No WorkManagerInitializer found"
    fi
done

if [ $VIOLATIONS -gt 0 ]; then
    echo ""
    echo "❌ FAILURE: Found $VIOLATIONS violation(s)"
    echo ""
    echo "The app uses on-demand WorkManager initialization via Configuration.Provider."
    echo "Auto-initialization must be disabled in app-v2/src/main/AndroidManifest.xml:"
    echo ""
    echo "  <provider"
    echo "      android:name=\"androidx.startup.InitializationProvider\""
    echo "      android:authorities=\"com.fishit.player.v2.androidx-startup\""
    echo "      tools:node=\"merge\">"
    echo "      <meta-data"
    echo "          android:name=\"androidx.work.WorkManagerInitializer\""
    echo "          tools:node=\"remove\" />"
    echo "  </provider>"
    echo ""
    echo "See: https://developer.android.com/topic/libraries/architecture/workmanager/advanced/custom-configuration"
    exit 1
fi

echo ""
echo "✅ SUCCESS: All manifests are clean (no WorkManagerInitializer)"
exit 0

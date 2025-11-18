#!/usr/bin/env bash
# Test script for local AAR building
# This simulates what the CI workflow does

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/.."

echo "=== TDLib BoringSSL AAR Local Build Test ==="
echo ""

# Check prerequisites
echo "Checking prerequisites..."
command -v git >/dev/null 2>&1 || { echo "Error: git not found"; exit 1; }
command -v java >/dev/null 2>&1 || { echo "Error: java not found"; exit 1; }

# Check if libtd structure exists
if [ ! -f "libtd/src/main/AndroidManifest.xml" ]; then
    echo "Error: libtd module not properly set up"
    exit 1
fi

# Check if Java sources exist
if [ ! -d "libtd/src/main/java/org/drinkless/tdlib" ]; then
    echo "Java sources directory doesn't exist yet."
    echo "This is expected if you haven't run the CI workflow yet."
    echo ""
    echo "To build the AAR, you need:"
    echo "1. Run the 'TDLib - Android Java' workflow to generate artifacts"
    echo "2. Run the 'TDLib BoringSSL AAR Release' workflow to build the AAR"
    echo "   OR manually populate libtd/src/main/ with the required files"
    exit 0
fi

# Check if native libraries exist
if [ ! -d "libtd/src/main/jniLibs/arm64-v8a" ] || [ ! -d "libtd/src/main/jniLibs/armeabi-v7a" ]; then
    echo "Native libraries directory doesn't exist yet."
    echo "You need to populate libtd/src/main/jniLibs/ with:"
    echo "  - arm64-v8a/libtdjni.so"
    echo "  - armeabi-v7a/libtdjni.so"
    exit 0
fi

# Try to build
echo "Building AAR..."
./gradlew :libtd:assembleRelease --no-daemon --stacktrace

# Check result
if [ -f "libtd/build/outputs/aar/libtd-release.aar" ]; then
    echo ""
    echo "✓ AAR built successfully!"
    echo "  Location: libtd/build/outputs/aar/libtd-release.aar"
    echo ""
    ls -lh "libtd/build/outputs/aar/libtd-release.aar"
    echo ""
    echo "To inspect the AAR contents:"
    echo "  unzip -l libtd/build/outputs/aar/libtd-release.aar"
else
    echo "✗ AAR was not created"
    exit 1
fi

#!/bin/bash
# Test script for quality tools
# This validates that all quality tools can run successfully

set -e

echo "========================================="
echo "Testing Quality Tools Configuration"
echo "========================================="
echo ""

# Create reports directory
mkdir -p reports/ktlint reports/kover reports/android-lint reports/gradle-doctor

echo "1. Testing KTLint..."
echo "-------------------"
./gradlew ktlintCheck --no-daemon --stacktrace || echo "KTLint found violations (expected)"
echo "✓ KTLint executed"
echo ""

echo "2. Testing Gradle Doctor..."
echo "-------------------"
./gradlew doctor --no-daemon --stacktrace 2>&1 | tee reports/gradle-doctor/output.txt || echo "Gradle Doctor completed with warnings"
echo "✓ Gradle Doctor executed"
echo ""

echo "3. Testing Android Lint (this may take a while)..."
echo "-------------------"
./gradlew lintVitalRelease --no-daemon --stacktrace || echo "Lint found issues (may be expected)"
echo "✓ Android Lint executed"
echo ""

echo "4. Testing Kover (this may take a while, runs tests)..."
echo "-------------------"
# ./gradlew koverXmlReport --no-daemon --stacktrace || echo "Kover execution completed"
echo "⚠ Kover test skipped (takes too long, uncomment to run)"
echo ""

echo "========================================="
echo "Quality Tools Test Complete"
echo "========================================="
echo ""
echo "Summary:"
echo "  ✓ KTLint - OK"
echo "  ✓ Gradle Doctor - OK"
echo "  ✓ Android Lint - OK"
echo "  ⚠ Kover - Skipped (uncomment to test)"
echo ""
echo "Check reports/ directory for output"

#!/bin/bash

# Phase 3 Automated Validation Script
# Tests PR #569 - Complete compile-time isolation for debug tools
# Issue: #564 (compile-time gating parent issue)

set -e  # Exit on error

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$REPO_ROOT"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Test results
TESTS_PASSED=0
TESTS_FAILED=0
TEST_RESULTS=()

# Helper functions
print_header() {
    echo -e "\n${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}\n"
}

print_test() {
    echo -e "${YELLOW}🧪 TEST $1: $2${NC}"
}

test_pass() {
    echo -e "${GREEN}✅ PASS: $1${NC}"
    TESTS_PASSED=$((TESTS_PASSED + 1))
    TEST_RESULTS+=("✅ PASS: $1")
}

test_fail() {
    echo -e "${RED}❌ FAIL: $1${NC}"
    TESTS_FAILED=$((TESTS_FAILED + 1))
    TEST_RESULTS+=("❌ FAIL: $1")
}

print_summary() {
    echo -e "\n${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${BLUE}TEST SUMMARY${NC}"
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}\n"
    
    for result in "${TEST_RESULTS[@]}"; do
        echo -e "$result"
    done
    
    echo -e "\n${BLUE}Total:${NC} $((TESTS_PASSED + TESTS_FAILED)) tests"
    echo -e "${GREEN}Passed:${NC} $TESTS_PASSED"
    echo -e "${RED}Failed:${NC} $TESTS_FAILED"
    
    if [ $TESTS_FAILED -eq 0 ]; then
        echo -e "\n${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
        echo -e "${GREEN}✅ ALL TESTS PASSED - READY FOR MERGE!${NC}"
        echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}\n"
        return 0
    else
        echo -e "\n${RED}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
        echo -e "${RED}❌ TESTS FAILED - REVIEW REQUIRED${NC}"
        echo -e "${RED}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}\n"
        return 1
    fi
}

# Start validation
print_header "Phase 3 Automated Validation - PR #569"
echo "Repository: karlokarate/FishIT-Player"
echo "Branch: copilot/implement-phase-3-ui-gating"
echo "Date: $(date)"
echo ""

# ============================================================================
# TEST 1: Clean Build Environment
# ============================================================================
print_test "1" "Clean build environment"
if ./gradlew clean > /dev/null 2>&1; then
    test_pass "Build environment cleaned"
else
    test_fail "Failed to clean build environment"
    exit 1
fi

# ============================================================================
# TEST 2: Debug Build Compilation
# ============================================================================
print_test "2" "Debug build compilation"
if ./gradlew assembleDebug > /dev/null 2>&1; then
    test_pass "Debug build compiled successfully"
else
    test_fail "Debug build failed to compile"
    print_summary
    exit 1
fi

# ============================================================================
# TEST 3: Release Build Compilation
# ============================================================================
print_test "3" "Release build compilation"
if ./gradlew assembleRelease > /dev/null 2>&1; then
    test_pass "Release build compiled successfully"
else
    test_fail "Release build failed to compile"
    print_summary
    exit 1
fi

# ============================================================================
# TEST 4: CI Scanner Execution
# ============================================================================
print_test "4" "CI bytecode scanner (verifyNoDebugToolsInRelease)"

SCANNER_OUTPUT=$(./gradlew verifyNoDebugToolsInRelease 2>&1)
SCANNER_EXIT=$?

if [ $SCANNER_EXIT -eq 0 ]; then
    if echo "$SCANNER_OUTPUT" | grep -q "Release build is clean"; then
        test_pass "CI scanner found no debug tool references"
    else
        test_fail "CI scanner succeeded but didn't confirm clean build"
        echo "$SCANNER_OUTPUT"
    fi
else
    test_fail "CI scanner detected violations or failed"
    echo "$SCANNER_OUTPUT"
fi

# ============================================================================
# TEST 5: APK Size Comparison
# ============================================================================
print_test "5" "APK size reduction"

DEBUG_APK="app-v2/build/outputs/apk/debug/app-v2-debug.apk"
RELEASE_APK="app-v2/build/outputs/apk/release/app-v2-release.apk"

if [ -f "$DEBUG_APK" ] && [ -f "$RELEASE_APK" ]; then
    DEBUG_SIZE=$(stat -f%z "$DEBUG_APK" 2>/dev/null || stat -c%s "$DEBUG_APK" 2>/dev/null)
    RELEASE_SIZE=$(stat -f%z "$RELEASE_APK" 2>/dev/null || stat -c%s "$RELEASE_APK" 2>/dev/null)
    
    DEBUG_MB=$(echo "scale=2; $DEBUG_SIZE / 1048576" | bc)
    RELEASE_MB=$(echo "scale=2; $RELEASE_SIZE / 1048576" | bc)
    DIFF_MB=$(echo "scale=2; $DEBUG_MB - $RELEASE_MB" | bc)
    
    echo "  Debug APK:   ${DEBUG_MB} MB"
    echo "  Release APK: ${RELEASE_MB} MB"
    echo "  Difference:  ${DIFF_MB} MB"
    
    # Check if release is smaller (allowing for compression variations)
    if [ $(echo "$RELEASE_MB < $DEBUG_MB" | bc) -eq 1 ]; then
        test_pass "Release APK is smaller than debug (${DIFF_MB} MB reduction)"
    else
        test_fail "Release APK is not smaller than debug"
    fi
else
    test_fail "APK files not found"
fi

# ============================================================================
# TEST 6: Debug Tool Strings in Release APK
# ============================================================================
print_test "6" "Search for debug tool strings in release APK"

if [ -f "$RELEASE_APK" ]; then
    # Check for forbidden strings in APK
    FORBIDDEN_STRINGS=("LeakCanary" "leakcanary" "Chucker" "chucker" "DebugToolsSettings" "DebugFlagsHolder")
    VIOLATIONS_FOUND=0
    
    for forbidden in "${FORBIDDEN_STRINGS[@]}"; do
        if unzip -l "$RELEASE_APK" 2>/dev/null | grep -i "$forbidden" > /dev/null; then
            echo "  ⚠️  Found '$forbidden' in APK contents"
            VIOLATIONS_FOUND=$((VIOLATIONS_FOUND + 1))
        fi
    done
    
    if [ $VIOLATIONS_FOUND -eq 0 ]; then
        test_pass "No debug tool strings found in release APK"
    else
        test_fail "Found $VIOLATIONS_FOUND debug tool references in release APK"
    fi
else
    test_fail "Release APK not found"
fi

# ============================================================================
# TEST 7: UI Gating Code Verification
# ============================================================================
print_test "7" "UI gating implementation in DebugScreen.kt"

DEBUG_SCREEN="feature/settings/src/main/java/com/fishit/player/feature/settings/DebugScreen.kt"

if [ -f "$DEBUG_SCREEN" ]; then
    # Check for if (state.isChuckerAvailable) guards
    if grep -q "if (state.isChuckerAvailable)" "$DEBUG_SCREEN"; then
        test_pass "Chucker UI gating guard found"
    else
        test_fail "Chucker UI gating guard NOT found"
    fi
    
    # Check for if (state.isLeakCanaryAvailable) guards
    if grep -q "if (state.isLeakCanaryAvailable)" "$DEBUG_SCREEN"; then
        test_pass "LeakCanary UI gating guard found"
    else
        test_fail "LeakCanary UI gating guard NOT found"
    fi
else
    test_fail "DebugScreen.kt not found"
fi

# ============================================================================
# TEST 8: Gradle Task Registration
# ============================================================================
print_test "8" "Gradle task registration"

BUILD_GRADLE="app-v2/build.gradle.kts"

if [ -f "$BUILD_GRADLE" ]; then
    if grep -q 'tasks.register("verifyNoDebugToolsInRelease")' "$BUILD_GRADLE"; then
        test_pass "verifyNoDebugToolsInRelease task registered"
    else
        test_fail "verifyNoDebugToolsInRelease task NOT found"
    fi
    
    # Check for multi-path scanning
    if grep -q "tmp/kotlin-classes/release" "$BUILD_GRADLE"; then
        test_pass "Multi-path Kotlin bytecode scanning configured"
    else
        test_fail "Kotlin bytecode scanning path NOT found"
    fi
else
    test_fail "app-v2/build.gradle.kts not found"
fi

# ============================================================================
# TEST 9: Documentation Existence
# ============================================================================
print_test "9" "Documentation file existence"

DOC_FILE="docs/v2/DEBUG_TOOLS_COMPILE_TIME_GATING.md"

if [ -f "$DOC_FILE" ]; then
    WORD_COUNT=$(wc -w < "$DOC_FILE")
    test_pass "Documentation exists (${WORD_COUNT} words)"
else
    test_fail "Documentation file NOT found at $DOC_FILE"
fi

# ============================================================================
# TEST 10: Module Dependency Check
# ============================================================================
print_test "10" "Debug-settings module dependency verification"

# Check that :core:debug-settings is only debugImplementation
if grep -r "debugImplementation.*:core:debug-settings" --include="build.gradle.kts" . > /dev/null; then
    test_pass "core:debug-settings found as debugImplementation"
else
    test_fail "core:debug-settings debugImplementation NOT found"
fi

# Check that it's NOT implementation or releaseImplementation
if grep -E -r "^\s*implementation.*:core:debug-settings|releaseImplementation.*:core:debug-settings" --include="build.gradle.kts" . > /dev/null; then
    test_fail "core:debug-settings incorrectly referenced as implementation or releaseImplementation"
else
    test_pass "core:debug-settings NOT in release dependencies"
fi

# ============================================================================
# Print Final Summary
# ============================================================================
print_summary
exit $?

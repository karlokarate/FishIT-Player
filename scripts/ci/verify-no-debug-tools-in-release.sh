#!/usr/bin/env bash
#
# verify-no-debug-tools-in-release.sh
#
# Platin-Level verification script for Issue #564:
# Ensures LeakCanary and Chucker are completely absent from release builds.
#
# Exit codes:
#   0 - No debug tools found (clean release)
#   1 - Debug tools detected (build should fail)
#   2 - Script error (missing files, etc.)
#
# Usage:
#   ./scripts/ci/verify-no-debug-tools-in-release.sh [APK_PATH]
#
# If APK_PATH is not provided, searches for release APK in default locations.

set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Forbidden patterns in release builds
FORBIDDEN_PATTERNS=(
    # LeakCanary
    "leakcanary"
    "Lleakcanary/"
    "shark/"
    "Lshark/"
    "AppWatcher"
    "HeapDump"
    "LeakCanary"
    
    # Chucker
    "chucker"
    "Lchucker/"
    "ChuckerInterceptor"
    "ChuckerCollector"
    
    # Debug-settings module (should not be in release)
    "core/debugsettings"
    "DebugToolsInitializer"
    "GatedChuckerInterceptor"
    "DebugFlagsHolder"
)

# Allowed patterns (false positives to ignore)
ALLOWED_PATTERNS=(
    # Documentation references are OK
    "README"
    "CHANGELOG"
    ".md"
    
    # BuildConfig flags are OK (they're just booleans)
    "INCLUDE_LEAKCANARY"
    "INCLUDE_CHUCKER"
)

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

find_release_apk() {
    local search_paths=(
        "app-v2/build/outputs/apk/release"
        "app-v2/build/outputs/apk/*/release"
        "build/outputs/apk/release"
    )
    
    for path in "${search_paths[@]}"; do
        local apk=$(find ${path} -name "*.apk" 2>/dev/null | head -n 1)
        if [[ -n "$apk" ]]; then
            echo "$apk"
            return 0
        fi
    done
    
    return 1
}

check_apk_for_debug_tools() {
    local apk_path="$1"
    local violations=()
    local temp_dir=$(mktemp -d)
    
    log_info "Analyzing APK: $apk_path"
    
    # Extract APK
    if ! unzip -q "$apk_path" -d "$temp_dir" 2>/dev/null; then
        log_error "Failed to extract APK"
        rm -rf "$temp_dir"
        return 2
    fi
    
    # Check DEX files for forbidden patterns
    log_info "Scanning DEX files for debug tool references..."
    
    for dex in "$temp_dir"/*.dex; do
        if [[ ! -f "$dex" ]]; then
            continue
        fi
        
        # Convert DEX to text representation for searching
        # Using strings to find class/package references
        local dex_content=$(strings "$dex" 2>/dev/null || cat "$dex")
        
        for pattern in "${FORBIDDEN_PATTERNS[@]}"; do
            if echo "$dex_content" | grep -qi "$pattern"; then
                # Check if it's an allowed pattern (false positive)
                local is_allowed=false
                for allowed in "${ALLOWED_PATTERNS[@]}"; do
                    if echo "$pattern" | grep -qi "$allowed"; then
                        is_allowed=true
                        break
                    fi
                done
                
                if [[ "$is_allowed" == "false" ]]; then
                    violations+=("DEX contains forbidden pattern: $pattern")
                fi
            fi
        done
    done
    
    # Check for LeakCanary/Chucker classes in manifest
    if [[ -f "$temp_dir/AndroidManifest.xml" ]]; then
        log_info "Scanning AndroidManifest.xml..."
        
        # AndroidManifest is binary XML, use strings
        local manifest_content=$(strings "$temp_dir/AndroidManifest.xml" 2>/dev/null)
        
        if echo "$manifest_content" | grep -qi "leakcanary"; then
            violations+=("AndroidManifest references LeakCanary")
        fi
        
        if echo "$manifest_content" | grep -qi "chucker"; then
            violations+=("AndroidManifest references Chucker")
        fi
    fi
    
    # Check for LeakCanary/Chucker resources
    log_info "Scanning resources..."
    
    if [[ -d "$temp_dir/res" ]]; then
        if find "$temp_dir/res" -type f -name "*leakcanary*" 2>/dev/null | grep -q .; then
            violations+=("Resources contain LeakCanary files")
        fi
        
        if find "$temp_dir/res" -type f -name "*chucker*" 2>/dev/null | grep -q .; then
            violations+=("Resources contain Chucker files")
        fi
    fi
    
    # Cleanup
    rm -rf "$temp_dir"
    
    # Report results
    if [[ ${#violations[@]} -gt 0 ]]; then
        log_error "========================================="
        log_error "RELEASE BUILD CONTAINS DEBUG TOOLS!"
        log_error "========================================="
        for violation in "${violations[@]}"; do
            log_error "  - $violation"
        done
        log_error ""
        log_error "This violates Issue #564 requirements:"
        log_error "Release builds MUST NOT contain any LeakCanary or Chucker code."
        log_error ""
        log_error "Possible fixes:"
        log_error "  1. Ensure debug-settings module uses debugImplementation"
        log_error "  2. Check that LeakCanary/Chucker are debugImplementation only"
        log_error "  3. Verify source set separation (debug/ vs release/)"
        log_error "========================================="
        return 1
    else
        log_info "========================================="
        log_info "✅ RELEASE BUILD IS CLEAN"
        log_info "No LeakCanary or Chucker references found."
        log_info "========================================="
        return 0
    fi
}

# Advanced check using R8 mapping file
check_mapping_file() {
    local mapping_paths=(
        "app-v2/build/outputs/mapping/release/mapping.txt"
        "build/outputs/mapping/release/mapping.txt"
    )
    
    for mapping_path in "${mapping_paths[@]}"; do
        if [[ -f "$mapping_path" ]]; then
            log_info "Checking R8 mapping file: $mapping_path"
            
            local leakcanary_refs=$(grep -ci "leakcanary" "$mapping_path" 2>/dev/null || echo "0")
            local chucker_refs=$(grep -ci "chucker" "$mapping_path" 2>/dev/null || echo "0")
            
            if [[ "$leakcanary_refs" -gt 0 ]] || [[ "$chucker_refs" -gt 0 ]]; then
                log_warn "R8 mapping contains debug tool references (LeakCanary: $leakcanary_refs, Chucker: $chucker_refs)"
                log_warn "These may be stripped by R8, but verify APK analysis above."
            else
                log_info "R8 mapping is clean - no debug tool references"
            fi
            return 0
        fi
    done
    
    log_warn "No R8 mapping file found (minification may be disabled or build not complete)"
    return 0
}

# Source code static analysis
check_source_for_violations() {
    log_info "Checking source code for potential violations..."
    
    local violations=()
    
    # Check that core:debug-settings doesn't leak into release via wrong dependency types
    local debug_settings_users=$(grep -rn "implementation.*debug-settings" --include="*.gradle.kts" 2>/dev/null | grep -v "debugImplementation" || true)
    
    if [[ -n "$debug_settings_users" ]]; then
        log_error "Found modules using 'implementation' instead of 'debugImplementation' for debug-settings:"
        echo "$debug_settings_users"
        violations+=("Incorrect dependency type for debug-settings module")
    fi
    
    # Check for direct LeakCanary/Chucker imports in main/ source sets
    local main_leakcanary=$(find . -path "*/src/main/*" -name "*.kt" -exec grep -l "import leakcanary" {} \; 2>/dev/null || true)
    local main_chucker=$(find . -path "*/src/main/*" -name "*.kt" -exec grep -l "import com.chuckerteam" {} \; 2>/dev/null || true)
    
    if [[ -n "$main_leakcanary" ]]; then
        log_error "Found LeakCanary imports in main/ source sets (should be in debug/ only):"
        echo "$main_leakcanary"
        violations+=("LeakCanary imported in main/ source set")
    fi
    
    if [[ -n "$main_chucker" ]]; then
        log_error "Found Chucker imports in main/ source sets (should be in debug/ only):"
        echo "$main_chucker"
        violations+=("Chucker imported in main/ source set")
    fi
    
    if [[ ${#violations[@]} -gt 0 ]]; then
        return 1
    fi
    
    log_info "Source code analysis passed"
    return 0
}

main() {
    log_info "========================================="
    log_info "Debug Tools Release Verification"
    log_info "Issue #564: Compile-time Gating"
    log_info "========================================="
    
    local apk_path="${1:-}"
    local exit_code=0
    
    # Source code analysis (always run)
    if ! check_source_for_violations; then
        exit_code=1
    fi
    
    # APK analysis (if APK provided or found)
    if [[ -z "$apk_path" ]]; then
        log_info "No APK path provided, searching for release APK..."
        apk_path=$(find_release_apk || true)
    fi
    
    if [[ -n "$apk_path" ]] && [[ -f "$apk_path" ]]; then
        if ! check_apk_for_debug_tools "$apk_path"; then
            exit_code=1
        fi
        
        check_mapping_file
    else
        log_warn "No release APK found - skipping APK analysis"
        log_info "Run this script after 'assembleRelease' for full verification"
    fi
    
    if [[ $exit_code -eq 0 ]]; then
        log_info ""
        log_info "========================================="
        log_info "✅ ALL CHECKS PASSED"
        log_info "========================================="
    else
        log_error ""
        log_error "========================================="
        log_error "❌ VERIFICATION FAILED"
        log_error "========================================="
    fi
    
    exit $exit_code
}

main "$@"

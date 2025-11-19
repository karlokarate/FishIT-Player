#!/bin/bash
# Script to check for dependency updates and compatibility
# This script helps verify latest module versions and compatibility

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

cd "$PROJECT_ROOT"

echo "================================================"
echo "Dependency Version Checker for FishIT-Player"
echo "================================================"
echo ""

# Function to check Kotlin version compatibility
check_kotlin_compatibility() {
    echo "📋 Checking Kotlin version..."
    KOTLIN_VERSION=$(grep 'kotlin("android") version' settings.gradle.kts | grep -oP '\d+\.\d+\.\d+')
    echo "   Current Kotlin version: $KOTLIN_VERSION"
    
    KSP_VERSION=$(grep 'id("com.google.devtools.ksp") version' settings.gradle.kts | grep -oP '\d+\.\d+\.\d+-\d+\.\d+\.\d+')
    echo "   Current KSP version: $KSP_VERSION"
    
    # Extract major.minor from Kotlin version
    KOTLIN_BASE=$(echo "$KOTLIN_VERSION" | grep -oP '^\d+\.\d+')
    KSP_BASE=$(echo "$KSP_VERSION" | grep -oP '^\d+\.\d+')
    
    if [ "$KOTLIN_BASE" = "$KSP_BASE" ]; then
        echo "   ✅ KSP version is compatible with Kotlin"
    else
        echo "   ⚠️  WARNING: KSP version ($KSP_BASE) might not match Kotlin ($KOTLIN_BASE)"
    fi
    echo ""
}

# Function to extract dependency versions from build.gradle.kts
check_dependency_versions() {
    echo "📦 Current dependency versions:"
    echo ""
    
    echo "   Compose:"
    grep 'val compose = ' app/build.gradle.kts | sed 's/.*= "\(.*\)".*/   - Compose UI: \1/'
    grep 'material3:material3:' app/build.gradle.kts | sed 's/.*material3:\(.*\)".*/   - Material3: \1/'
    echo ""
    
    echo "   AndroidX Core:"
    grep 'core-ktx:' app/build.gradle.kts | sed 's/.*core-ktx:\(.*\)".*/   - Core KTX: \1/'
    grep 'activity-compose:' app/build.gradle.kts | sed 's/.*activity-compose:\(.*\)".*/   - Activity Compose: \1/'
    grep 'navigation-compose:' app/build.gradle.kts | sed 's/.*navigation-compose:\(.*\)".*/   - Navigation Compose: \1/'
    echo ""
    
    echo "   Media3:"
    grep 'val media3 = ' app/build.gradle.kts | sed 's/.*= "\(.*\)".*/   - Media3: \1/'
    echo ""
    
    echo "   Network:"
    grep 'okhttp3:okhttp:' app/build.gradle.kts | sed 's/.*okhttp:\(.*\)".*/   - OkHttp: \1/'
    grep 'okio:okio:' app/build.gradle.kts | sed 's/.*okio:\(.*\)".*/   - Okio: \1/'
    echo ""
    
    echo "   Coil:"
    grep 'coil-kt.coil3:coil:' app/build.gradle.kts | sed 's/.*coil:\(.*\)".*/   - Coil: \1/'
    echo ""
    
    echo "   Kotlin Serialization:"
    grep 'kotlinx-serialization-json:' app/build.gradle.kts | sed 's/.*json:\(.*\)".*/   - Serialization JSON: \1/'
    echo ""
    
    echo "   Coroutines:"
    grep 'kotlinx-coroutines-android:' app/build.gradle.kts | sed 's/.*android:\(.*\)".*/   - Coroutines Android: \1/'
    echo ""
    
    echo "   ObjectBox:"
    grep 'objectbox-android:' app/build.gradle.kts | sed 's/.*android:\(.*\)".*/   - ObjectBox Android: \1/'
    grep 'id("io.objectbox") version' app/build.gradle.kts | sed 's/.*version "\(.*\)".*/   - ObjectBox Plugin: \1/'
    echo ""
}

# Function to check AGP version
check_agp_version() {
    echo "🔧 Build Tools:"
    echo ""
    
    AGP_VERSION=$(grep 'id("com.android.application") version' settings.gradle.kts | grep -oP '\d+\.\d+\.\d+')
    echo "   Android Gradle Plugin: $AGP_VERSION"
    
    GRADLE_VERSION=$(./gradlew --version 2>/dev/null | grep "Gradle" | grep -oP '\d+\.\d+' || echo "Unknown")
    echo "   Gradle: $GRADLE_VERSION"
    
    echo ""
}

# Function to suggest compatibility checks
suggest_compatibility_checks() {
    echo "🔍 Compatibility Check Recommendations:"
    echo ""
    echo "   1. Verify Kotlin $KOTLIN_VERSION is compatible with:"
    echo "      - Compose dependencies (check compose-compiler-kotlin compatibility)"
    echo "      - KSP $KSP_VERSION"
    echo "      - All Kotlin stdlib-dependent libraries"
    echo ""
    echo "   2. Check that dependencies compiled with Kotlin match project Kotlin version:"
    echo "      - Coil 3.3.0"
    echo "      - OkHttp 5.2.1"
    echo "      - Okio 3.16.1"
    echo "      - kotlinx-serialization"
    echo "      - kotlinx-coroutines"
    echo ""
    echo "   3. Verify AGP $AGP_VERSION supports compileSdk 36"
    echo ""
    echo "   4. Check ObjectBox 5.0.1 compatibility with Kotlin $KOTLIN_VERSION and KSP"
    echo ""
}

# Function to check for common compatibility issues
check_compatibility_issues() {
    echo "⚠️  Known Compatibility Issues:"
    echo ""
    
    # Check if Kotlin version might cause issues
    if [[ "$KOTLIN_VERSION" < "2.2.0" ]]; then
        echo "   ⚠️  Kotlin version is < 2.2.0"
        echo "      - Some dependencies (Coil 3.3.0, OkHttp 5.2.1, Okio 3.16.1) require Kotlin 2.2.0+"
        echo "      - Consider upgrading to Kotlin 2.2.0"
        echo ""
    fi
    
    # Check compile SDK
    COMPILE_SDK=$(grep 'compileSdk = ' app/build.gradle.kts | grep -oP '\d+')
    if [ "$COMPILE_SDK" -ge 36 ]; then
        echo "   ℹ️  Using compileSdk $COMPILE_SDK"
        echo "      - Verify AGP $AGP_VERSION officially supports this SDK level"
        echo ""
    fi
}

# Main execution
main() {
    check_kotlin_compatibility
    check_agp_version
    check_dependency_versions
    check_compatibility_issues
    suggest_compatibility_checks
    
    echo "================================================"
    echo "✅ Dependency check complete!"
    echo ""
    echo "To build the project:"
    echo "  ./gradlew clean assembleRelease"
    echo ""
    echo "To check for newer versions (requires internet):"
    echo "  ./gradlew dependencyUpdates"
    echo "  (Add com.github.ben-manes.versions plugin to use this)"
    echo "================================================"
}

main

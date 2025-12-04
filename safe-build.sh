#!/bin/bash
# Safe Build Wrapper - Ensures builds never crash due to memory
# Usage: ./safe-build.sh [gradle-task] [additional-args...]

set -e

REPO_DIR="/workspaces/FishIT-Player"
cd "$REPO_DIR"

# Default task if none provided
GRADLE_TASK="${1:-assembleDebug}"
shift || true
GRADLE_ARGS="$@"

echo "============================================"
echo "Safe Build Wrapper"
echo "============================================"
echo "Task: $GRADLE_TASK"
echo "Args: $GRADLE_ARGS"
echo ""

# Check current memory
CURRENT_MEM=$(free | grep Mem | awk '{printf("%.0f", ($3/$2) * 100)}')
echo "Current memory usage: ${CURRENT_MEM}%"

# Pre-build cleanup if memory is high
if [ "$CURRENT_MEM" -gt 70 ]; then
    echo "⚠️  High memory usage detected - performing pre-build cleanup..."
    
    # Stop daemons
    ./gradlew --stop 2>/dev/null || true
    
    # Kill language servers temporarily
    pkill -f "org.javacs.kt.MainKt" 2>/dev/null || true
    
    # Clean build artifacts
    rm -rf app/build/intermediates app/build/tmp 2>/dev/null || true
    rm -rf .gradle/*/fileChanges .gradle/*/fileHashes 2>/dev/null || true
    
    sleep 3
    AFTER_CLEANUP=$(free | grep Mem | awk '{printf("%.0f", ($3/$2) * 100)}')
    echo "✓ Cleanup complete. Memory now: ${AFTER_CLEANUP}%"
fi

echo ""
echo "============================================"
echo "Starting Gradle build..."
echo "============================================"

# Set conservative memory limits for this build
export GRADLE_OPTS="-Xmx1280m -Xms256m -XX:+UseG1GC -XX:MaxMetaspaceSize=384m"
export _JAVA_OPTIONS="-Xmx1280m"

# Run build with resource constraints
./gradlew "$GRADLE_TASK" \
    --no-daemon \
    --max-workers=2 \
    -Dorg.gradle.jvmargs="-Xmx1280m -Xms256m -XX:+UseG1GC" \
    -Dkotlin.daemon.jvmargs="-Xmx512m" \
    $GRADLE_ARGS

BUILD_EXIT=$?

echo ""
echo "============================================"
echo "Build completed with exit code: $BUILD_EXIT"
echo "============================================"

FINAL_MEM=$(free | grep Mem | awk '{printf("%.0f", ($3/$2) * 100)}')
echo "Final memory usage: ${FINAL_MEM}%"

# Post-build cleanup if memory is still high
if [ "$FINAL_MEM" -gt 75 ]; then
    echo "Performing post-build cleanup..."
    ./gradlew --stop 2>/dev/null || true
    rm -rf app/build/intermediates 2>/dev/null || true
fi

exit $BUILD_EXIT

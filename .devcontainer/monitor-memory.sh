#!/bin/bash
# Memory Monitor & Auto-Cleanup Script for Codespaces
# Runs in background and prevents OOM crashes

set -e

LOG_FILE="/tmp/memory-monitor.log"
CHECK_INTERVAL=30  # Check every 30 seconds
MEMORY_THRESHOLD=85  # Trigger cleanup at 85% memory usage

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*" | tee -a "$LOG_FILE"
}

get_memory_usage() {
    free | grep Mem | awk '{printf("%.0f", ($3/$2) * 100)}'
}

cleanup_gradle() {
    log "Stopping Gradle daemons..."
    cd /workspaces/FishIT-Player && ./gradlew --stop 2>/dev/null || true
    
    log "Cleaning Gradle caches..."
    rm -rf .gradle/*/fileChanges .gradle/*/fileHashes .gradle/buildOutputCleanup 2>/dev/null || true
    rm -rf ~/.gradle/caches/transforms-* 2>/dev/null || true
}

cleanup_build() {
    log "Cleaning build artifacts..."
    cd /workspaces/FishIT-Player
    rm -rf app/build/intermediates app/build/tmp 2>/dev/null || true
    rm -rf app/.objectbox 2>/dev/null || true
    rm -rf build/reports 2>/dev/null || true
}

kill_language_servers() {
    log "Restarting language servers..."
    pkill -f "org.javacs.kt.MainKt" 2>/dev/null || true
    # Don't kill Java LS as it's needed for Gradle
}

emergency_cleanup() {
    log "!!! EMERGENCY: Memory usage critical (${1}%) - aggressive cleanup !!!"
    
    cleanup_gradle
    cleanup_build
    kill_language_servers
    
    # Clear system caches
    sync && echo 3 | sudo tee /proc/sys/vm/drop_caches >/dev/null 2>&1 || true
    
    sleep 5
    AFTER=$(get_memory_usage)
    log "Memory after cleanup: ${AFTER}%"
}

log "Memory monitor started (threshold: ${MEMORY_THRESHOLD}%)"

while true; do
    USAGE=$(get_memory_usage)
    
    if [ "$USAGE" -ge "$MEMORY_THRESHOLD" ]; then
        emergency_cleanup "$USAGE"
    fi
    
    # Log every 5 minutes if normal
    if [ $(($(date +%s) % 300)) -eq 0 ]; then
        log "Memory usage: ${USAGE}% (threshold: ${MEMORY_THRESHOLD}%)"
    fi
    
    sleep "$CHECK_INTERVAL"
done

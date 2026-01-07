#!/bin/bash
# Build MCP Server and capture output
cd /workspaces/FishIT-Player
./gradlew :tools:mcp-server:compileKotlin --no-daemon 2>&1 | tee /tmp/mcp-build.log
echo ""
echo "=== Build output saved to /tmp/mcp-build.log ==="

#!/usr/bin/env bash
#
# create-cli-distribution.sh
#
# Creates a distributable ZIP archive containing:
# - All required module sources
# - Build configuration
# - CLI runner scripts
# - Documentation
#
# The ZIP can be used locally with Android SDK installed.
#

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$SCRIPT_DIR/.."
DIST_DIR="$PROJECT_ROOT/dist"
ZIP_NAME="fishit-pipeline-cli-$(date +%Y%m%d).zip"

echo "📦 Creating FishIT CLI Distribution..."
echo

# Create dist directory
rm -rf "$DIST_DIR"
mkdir -p "$DIST_DIR/fishit-cli"

cd "$PROJECT_ROOT"

echo "📋 Copying project files..."

# Copy essential files
cp -r gradle "$DIST_DIR/fishit-cli/"
cp gradlew gradlew.bat "$DIST_DIR/fishit-cli/"
cp build.gradle.kts settings.gradle.kts gradle.properties "$DIST_DIR/fishit-cli/"
cp local.properties "$DIST_DIR/fishit-cli/" 2>/dev/null || true

# Copy required modules
MODULES=(
    "core/model"
    "core/player-model"
    "core/feature-api"
    "core/metadata-normalizer"
    "core/app-startup"
    "infra/logging"
    "infra/transport-telegram"
    "infra/transport-xtream"
    "pipeline/telegram"
    "pipeline/xtream"
    "tools/pipeline-cli"
)

for module in "${MODULES[@]}"; do
    echo "  📁 $module"
    mkdir -p "$DIST_DIR/fishit-cli/$module"
    
    # Copy source files
    if [ -d "$module/src" ]; then
        cp -r "$module/src" "$DIST_DIR/fishit-cli/$module/"
    fi
    
    # Copy build file
    if [ -f "$module/build.gradle.kts" ]; then
        cp "$module/build.gradle.kts" "$DIST_DIR/fishit-cli/$module/"
    fi
    
    # Copy README if exists
    if [ -f "$module/README.md" ]; then
        cp "$module/README.md" "$DIST_DIR/fishit-cli/$module/"
    fi
done

# Copy CLI wrapper and setup script
cp fishit-cli "$DIST_DIR/fishit-cli/"
cp tools/fishit-cli-setup.sh "$DIST_DIR/fishit-cli/"

# Copy documentation
echo "📄 Copying documentation..."
mkdir -p "$DIST_DIR/fishit-cli/docs"
cp tools/pipeline-cli/README.md "$DIST_DIR/fishit-cli/README.md"
cp docs/v2/Pipeline_test_cli.md "$DIST_DIR/fishit-cli/docs/" 2>/dev/null || true

# Create settings.gradle.kts for distribution (only needed modules)
cat > "$DIST_DIR/fishit-cli/settings.gradle.kts" << 'EOF'
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("com.android.application") version "8.6.1" apply false
        id("com.android.library") version "8.6.1" apply false
        kotlin("android") version "2.1.0" apply false
        kotlin("plugin.serialization") version "2.1.0" apply false
        id("com.google.devtools.ksp") version "2.1.0-1.0.29" apply false
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "FishITCli"

include(":core:model")
include(":core:feature-api")
include(":core:metadata-normalizer")
include(":core:app-startup")
include(":infra:logging")
include(":infra:transport-telegram")
include(":infra:transport-xtream")
include(":pipeline:telegram")
include(":pipeline:xtream")
include(":tools:pipeline-cli")
EOF

# Create README for distribution
cat > "$DIST_DIR/fishit-cli/README.md" << 'EOF'
# FishIT Pipeline CLI - Distribution

This is a standalone distribution of the FishIT Pipeline CLI.

## Requirements

- JDK 21
- Android SDK (for TDLib dependencies)
- Environment variables for credentials

## Setup

1. Extract this archive
2. Set up Android SDK path in `local.properties`:
   ```
   sdk.dir=/path/to/android/sdk
   ```

3. Set environment variables:
   ```bash
   # Telegram (optional)
   export TG_API_ID=your_api_id
   export TG_API_HASH=your_api_hash
   export TDLIB_DATABASE_DIR=/path/to/tdlib/db
   export TDLIB_FILES_DIR=/path/to/tdlib/files
   
   # Xtream (optional)
   export XTREAM_BASE_URL=http://your-server:8080
   export XTREAM_USERNAME=your_username
   export XTREAM_PASSWORD=your_password
   ```

## Usage

```bash
# Build and run
./fishit-cli tg status
./fishit-cli xt list-vod --limit 20
./fishit-cli meta normalize-sample --source xc

# Or via Gradle directly
./gradlew :tools:pipeline-cli:run --args="tg status"
```

## Available Commands

### Telegram (`tg`)
- `tg status` - Show Telegram pipeline status
- `tg list-chats [--class hot|warm|cold] [--limit N]` - List chats
- `tg sample-media --chat-id ID [--limit N]` - Sample media from chat

### Xtream (`xt`)
- `xt status` - Show Xtream pipeline status
- `xt list-vod [--limit N]` - List VOD items
- `xt list-series [--limit N]` - List series
- `xt list-live [--limit N]` - List live channels
- `xt inspect-id --id ID` - Inspect VOD item details

### Metadata (`meta`)
- `meta normalize-sample --source tg|xc [--limit N]` - Sample normalized metadata

All commands support `--json` for JSON output.
EOF

# Create the ZIP
echo "🗜️ Creating ZIP archive..."
cd "$DIST_DIR"
zip -r "$ZIP_NAME" fishit-cli -x "*.DS_Store" -x "*/.git/*"

# Cleanup
rm -rf "$DIST_DIR/fishit-cli"

echo
echo "✅ Distribution created: $DIST_DIR/$ZIP_NAME"
echo "   Size: $(du -h "$DIST_DIR/$ZIP_NAME" | cut -f1)"

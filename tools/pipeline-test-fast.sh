#!/usr/bin/env bash
#
# pipeline-test-fast.sh
#
# Run fast pipeline-related unit tests.
# Covers core model, metadata normalizer, and both pipelines.
#

set -e

echo "🧪 Running fast pipeline tests..."
echo

cd "$(dirname "$0")/.."

./gradlew \
    :core:model:test \
    :core:metadata-normalizer:test \
    :pipeline:telegram:test \
    :pipeline:xtream:test \
    --no-daemon \
    "$@"

echo
echo "✅ Fast pipeline tests complete!"

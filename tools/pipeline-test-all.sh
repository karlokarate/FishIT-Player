#!/usr/bin/env bash
#
# pipeline-test-all.sh
#
# Run all pipeline-related unit tests including transport and playback layers.
#

set -e

echo "🧪 Running all pipeline tests..."
echo

cd "$(dirname "$0")/.."

./gradlew \
    :core:model:test \
    :core:metadata-normalizer:test \
    :infra:transport-telegram:test \
    :infra:transport-xtream:test \
    :pipeline:telegram:test \
    :pipeline:xtream:test \
    :playback:domain:test \
    :player:internal:test \
    --no-daemon \
    "$@"

echo
echo "✅ All pipeline tests complete!"

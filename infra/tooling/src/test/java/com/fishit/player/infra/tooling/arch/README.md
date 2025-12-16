# Architecture Tests for TDLib ID Contract

This package contains architecture tests that enforce the **remoteId-First TDLib ID Architecture** contract.

## Purpose

These tests validate that:
1. **Forbidden patterns** (`fileId`, `fileUniqueId`, `localPath` fields) are NOT used in v2 modules
2. **Required patterns** (`remoteId`, `thumbRemoteId`, `posterRemoteId`) ARE used correctly
3. The architecture contract is enforced across the entire codebase

## Test Classes

### `TelegramIdArchitectureTest`
Detailed tests for individual modules:
- `no fileId fields in pipeline telegram module`
- `no fileId fields in core persistence module`
- `ImageRef TelegramThumb uses remoteId only`
- `ObxTelegramMessage has remoteId fields`
- `TelegramMediaItem uses remoteId and thumbRemoteId`
- `TelegramPhotoSize uses remoteId only`
- `TgThumbnailRef uses remoteId only`
- `all Telegram modules use consistent remoteId naming`
- `ImageRefKeyer uses remoteId for cache key`
- `ImageRef URI format uses remoteId`

### `TelegramIdArchitectureScanTest`
Full codebase scan tests:
- `full v2 codebase scan for forbidden TDLib ID patterns`
- `verify required remoteId fields exist in key files`
- `count total remoteId vs fileId usage for metrics`
- `TELEGRAM_ID_ARCHITECTURE_CONTRACT exists and is valid`

## Running the Tests

```bash
# Run all architecture tests
./gradlew :infra:tooling:testDebugUnitTest --tests "*TelegramIdArchitecture*"

# Run only the scan test
./gradlew :infra:tooling:testDebugUnitTest --tests "*TelegramIdArchitectureScanTest*"

# Run only the detailed tests
./gradlew :infra:tooling:testDebugUnitTest --tests "*TelegramIdArchitectureTest*"
```

## Using as Build Guards

### Option 1: CI Pipeline Integration

Add to your GitHub Actions workflow:

```yaml
jobs:
  architecture-guard:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'microsoft'
      - name: Run Architecture Guard
        run: ./gradlew :infra:tooling:testDebugUnitTest --tests "*TelegramIdArchitecture*"
```

### Option 2: Custom Gradle Task

Add to your root `build.gradle.kts`:

```kotlin
tasks.register("architectureGuard") {
    group = "verification"
    description = "Runs architecture tests to enforce TDLib ID contract"
    dependsOn(":infra:tooling:testDebugUnitTest")
}
```

Then run:
```bash
./gradlew architectureGuard
```

### Option 3: Pre-commit Hook

Add to `.git/hooks/pre-commit`:

```bash
#!/bin/bash
echo "Running architecture guard..."
./gradlew :infra:tooling:testDebugUnitTest --tests "*TelegramIdArchitectureScanTest*" --quiet
if [ $? -ne 0 ]; then
    echo "❌ Architecture violation detected! Fix before committing."
    exit 1
fi
echo "✅ Architecture guard passed"
```

## Contract Reference

See `contracts/TELEGRAM_ID_ARCHITECTURE_CONTRACT.md` for the binding rules.

## Allowed Exceptions

The following files are **allowed** to use `fileId` because they're in the transport layer:
- `TelegramFileClient.kt`
- `TelegramThumbFetcher.kt`
- `TelegramThumbFetcherImpl.kt`
- `TelegramFileDownloadManager.kt`
- `TelegramFileDataSource.kt`
- `TelegramFileReadyEnsurer.kt`
- `TelegramTransportClient.kt`
- `TgFile.kt`
- `TgContent.kt`
- `TgMessage.kt`

These files handle runtime file operations where `fileId` is resolved on-demand.

## Extending the Tests

To add new architecture rules:

1. Add forbidden patterns to `PERSISTENT_FILEID_PATTERNS` in `TelegramIdArchitectureScanTest`
2. Add required patterns to the appropriate test method
3. Update `ALLOWED_FILEID_FILES` if new transport layer files need exceptions

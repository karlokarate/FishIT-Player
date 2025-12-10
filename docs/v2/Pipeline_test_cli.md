````markdown
# FishIT v2 – Codespace Pipeline Test & Console Design

**Target:** Branch `architecture/v2-bootstrap` of `FishIT-Player`.  
**Audience:** GitHub Copilot Agents + human devs in a Codespace.  
**Goal:** Provide a **single, high-value workflow** to:

- Reuse a **real Telegram user TDLib session** in Codespaces (no interactive auth).
- Boot **Telegram + Xtream pipelines** exactly like the app.
- Run **pipeline, normalizer and variant** checks from a **friendly console**.
- Inspect **Telegram Hot/Warm/Cold chats**, **normalized titles**, **variants**, and **fallback behavior**.
- Keep everything safe, reproducible, and aligned with our v2 architecture.

No optional branches, no “maybe”. Every step below is **mandatory**.

---

## 1. Codespace Telegram Auth: Real User Session Reuse

### 1.1. Local one-time TDLib user login

**Objective:** Create a TDLib user session that can be reused in Codespaces and CI.

1. Create or use an existing **local auth CLI** (desktop/laptop, not Codespace), e.g. module:

   - `tools/telegram-auth-cli`

2. This CLI must:

   - Initialize TDLib with `TG_API_ID` and `TG_API_HASH`.
   - Run a full **user login** flow:
     - Ask for phone number, confirmation code, and 2FA password if required.
   - On successful `authorizationStateReady`, write:

     ```text
     tdlib/
       db/
       files/
     ```

   - into a local directory, e.g. `~/fishit-tdlib-session/tdlib`.

3. This TDLib folder is the **user session root** and must later be zipped and encrypted.

### 1.2. Archive & encrypt the session

On your local machine:

1. Create an archive:

   ```bash
   cd ~/fishit-tdlib-session
   tar -czf tdlib-session.tar.gz tdlib
````

2. Encrypt it (mandatory):

   ```bash
   openssl enc -aes-256-cbc -salt \
     -in tdlib-session.tar.gz \
     -out tdlib-session.enc
   ```

3. Do **not** commit `tdlib-session.tar.gz` or `tdlib-session.enc` to the repository.

### 1.3. Store the encrypted blob and secrets

1. Upload `tdlib-session.enc` to a secure remote (mandatory):

   * Example choices (one of these):

     * private S3/GCS bucket
     * 1Password Secure File
     * Vault / Doppler / other secret file store

2. In GitHub for the FishIT repo, add **Codespaces Secrets**:

   * `TDLIB_SESSION_URL` – URL or secret path to download `tdlib-session.enc`.
   * `TDLIB_SESSION_PASS` – the OpenSSL passphrase used to decrypt.
   * `TG_API_ID` – numeric Telegram API ID.
   * `TG_API_HASH` – Telegram API hash.

These secrets **must** be present for the Codespace pipeline tests to work.

### 1.4. Codespace bootstrap: `.devcontainer/devcontainer.json`

Add or extend:

```jsonc
{
  "postStartCommand": "/bin/bash .devcontainer/postStartImportTdlibSession.sh"
}
```

Create `.devcontainer/postStartImportTdlibSession.sh`:

```bash
#!/usr/bin/env bash
set -e

SESSION_ROOT="/workspace/.cache/tdlib-user"
mkdir -p "$SESSION_ROOT"

if [ -z "$TDLIB_SESSION_URL" ] || [ -z "$TDLIB_SESSION_PASS" ]; then
  echo "TDLIB_SESSION_URL or TDLIB_SESSION_PASS not set – Telegram tests will be disabled."
  exit 0
fi

echo "[tdlib] Downloading encrypted session…"
curl -sSL "$TDLIB_SESSION_URL" -o /tmp/tdlib-session.enc

echo "[tdlib] Decrypting session…"
openssl enc -d -aes-256-cbc \
  -in /tmp/tdlib-session.enc \
  -out /tmp/tdlib-session.tar.gz \
  -pass env:TDLIB_SESSION_PASS

echo "[tdlib] Extracting session…"
tar -xzf /tmp/tdlib-session.tar.gz -C "$SESSION_ROOT"

rm /tmp/tdlib-session.enc /tmp/tdlib-session.tar.gz

echo "[tdlib] Session ready at $SESSION_ROOT/tdlib"
```

**Constraint:** All Telegram pipeline tests in Codespaces MUST use:

* `databaseDir = "$SESSION_ROOT/tdlib/db"`
* `filesDir = "$SESSION_ROOT/tdlib/files"`

No interactive auth in Codespaces is allowed.

### 1.5. TelegramClientFactory.fromExistingSession

Create:

* `infra/transport-telegram/src/main/java/com/fishit/player/infra/transport/telegram/TelegramClientFactory.kt`

with:

```kotlin
data class TelegramSessionConfig(
    val apiId: Int,
    val apiHash: String,
    val databaseDir: String,
    val filesDir: String
)

object TelegramClientFactory {
    fun fromExistingSession(config: TelegramSessionConfig): TelegramTransportClient
}
```

Implementation requirements:

* Set full `TdlibParameters` including:

  * `database_directory = config.databaseDir`
  * `files_directory = config.filesDir`
  * API ID & hash from config
  * `use_message_database = true`
  * `use_file_database = true`

* Call `checkDatabaseEncryptionKey()` appropriately.

* Skip interactive auth: if TDLib reports `authorizationStateReady`, consider the client ready; **do not** prompt for phone, code, or password.

* If the session is invalid (e.g. `authorizationStateClosed` or “PHONE_NUMBER_INVALID”), log an error and fail tests clearly.

---

## 2. Shared App Startup (App + CLI + Tests)

### 2.1. Shared startup abstraction

Create in a core/infra module (e.g. `core/infra-appstartup`):

* `AppStartupConfig.kt`
* `AppStartup.kt`
* `AppStartupImpl.kt`

`AppStartupConfig`:

```kotlin
data class AppStartupConfig(
    val telegram: TelegramPipelineConfig?,
    val xtream: XtreamPipelineConfig?,
)

data class TelegramPipelineConfig(
    val sessionConfig: TelegramSessionConfig,
    val useHotWarmColdClassification: Boolean = true
)

data class XtreamPipelineConfig(
    val baseUrl: String,
    val username: String,
    val password: String
)
```

`AppStartup`:

```kotlin
interface AppStartup {
    suspend fun startPipelines(config: AppStartupConfig): Pipelines
}

data class Pipelines(
    val telegram: TelegramPipelineAdapter?,  // from v2 pipeline
    val xtream: XtreamPipelineAdapter?
)
```

`AppStartupImpl` must:

* Construct `TelegramTransportClient` via `TelegramClientFactory.fromExistingSession` when `config.telegram` is present.
* Build `TelegramPipelineAdapter` using the v2 adapter code (from your current project).
* Build `XtreamApiClient` and `XtreamPipelineAdapter` when `config.xtream` is present.
* Wire `core:model` and `core:metadata-normalizer` where required.

**MANDATORY:** Both Android app and CLI/tests must use **the same** `AppStartupImpl` so behavior is consistent.

---

## 3. Pipeline CLI (`tools/pipeline-cli`)

### 3.1. Module setup

Create module:

```text
tools/pipeline-cli/
  build.gradle.kts
  src/main/kotlin/com/fishit/player/tools/cli/Main.kt
  src/main/kotlin/com/fishit/player/tools/cli/...
```

Requirements:

* Kotlin JVM.
* Dependencies:

  * `org.jetbrains.kotlinx:kotlinx-coroutines-core`
  * CLI framework: `com.github.ajalt.clikt:clikt` (latest 4.x)
  * Logging: SLF4J + Logback.
* No Android dependencies.

In `build.gradle.kts`, set `application` plugin with `mainClass`:

```kotlin
application {
    mainClass.set("com.fishit.player.tools.cli.MainKt")
}
```

### 3.2. CLI config loader

Create `CliConfigLoader.kt`:

```kotlin
object CliConfigLoader {

    fun loadAppStartupConfig(): AppStartupConfig {
        val telegramCfg = if (System.getenv("TG_API_ID") != null) {
            TelegramPipelineConfig(
                sessionConfig = TelegramSessionConfig(
                    apiId = getenvInt("TG_API_ID"),
                    apiHash = getenv("TG_API_HASH"),
                    databaseDir = "/workspace/.cache/tdlib-user/tdlib/db",
                    filesDir = "/workspace/.cache/tdlib-user/tdlib/files"
                ),
                useHotWarmColdClassification = true
            )
        } else null

        val xtCfg = if (System.getenv("XTREAM_BASE_URL") != null) {
            XtreamPipelineConfig(
                baseUrl = getenv("XTREAM_BASE_URL"),
                username = getenv("XTREAM_USERNAME"),
                password = getenv("XTREAM_PASSWORD")
            )
        } else null

        return AppStartupConfig(
            telegram = telegramCfg,
            xtream = xtCfg
        )
    }

    private fun getenv(name: String) =
        System.getenv(name) ?: error("Missing env $name")

    private fun getenvInt(name: String) =
        getenv(name).toIntOrNull() ?: error("Env $name must be int")
}
```

Codespaces env vars (must be set as Codespace secrets or `devcontainer` env):

* `TG_API_ID`
* `TG_API_HASH`
* `XTREAM_BASE_URL`
* `XTREAM_USERNAME`
* `XTREAM_PASSWORD`

### 3.3. CLI main and subcommands

`Main.kt`:

* Initialize coroutine scope.
* Load `AppStartupConfig` via `CliConfigLoader`.
* Call `AppStartupImpl.startPipelines(config)` once.
* Pass resulting `Pipelines` to all subcommands.

CLIs must be structured as:

```bash
fishit-cli tg status
fishit-cli tg list-chats
fishit-cli tg list-chats --class hot
fishit-cli tg sample-media --chat-id <id> --limit 10

fishit-cli xt status
fishit-cli xt list-vod --limit 20
fishit-cli xt inspect-id --id <streamId>

fishit-cli meta normalize-sample --source tg --limit 20

fishit-cli play simulate --global-id <globalId>
```

All commands must:

* Print **human-readable tables** by default.
* Accept `--json` to print raw JSON (serialized DTOs).

---

## 4. Telegram Debug Functions (CLI + Pipeline)

### 4.1. TelegramDebugService facade

Create in `pipeline/telegram`:

* `TelegramDebugService.kt`

```kotlin
interface TelegramDebugService {
    suspend fun getStatus(): TelegramStatus
    suspend fun listChats(filter: ChatFilter, limit: Int): List<TelegramChatSummary>
    suspend fun sampleMedia(chatId: Long, limit: Int): List<TelegramMediaSummary>
}

enum class ChatFilter { ALL, HOT, WARM, COLD }

data class TelegramStatus(
    val isAuthenticated: Boolean,
    val sessionDir: String,
    val chatCount: Int,
    val hotChats: Int,
    val warmChats: Int,
    val coldChats: Int
)

data class TelegramChatSummary(
    val chatId: Long,
    val title: String,
    val mediaClass: String,     // "HOT"/"WARM"/"COLD"
    val mediaCountEstimate: Int
)

data class TelegramMediaSummary(
    val messageId: Long,
    val timestampMillis: Long,
    val mimeType: String?,
    val sizeBytes: Long?,
    val normalizedTitle: String?,
    val normalizedMediaType: MediaType
)
```

Implementation must:

* Use `TelegramPipelineAdapter` and the Hot/Warm/Cold classifier already planned in the v2 architecture.
* Filter chats based on `ChatFilter`:

  * `HOT` → only `MEDIA_HOT`
  * `WARM` → only `MEDIA_WARM`
  * `COLD` → only `MEDIA_COLD`
* `sampleMedia` must:

  * Take `TgMessage` via media-only flow.
  * Map to `TelegramMediaItem`.
  * Then to `RawMediaMetadata`.
  * Call `Normalizer.normalize(...)` on a small sample (e.g. just this one message) and expose `normalizedTitle` and `normalizedMediaType`.

### 4.2. CLI Telegram commands

Implement with clikt:

* `tg status`

  * Calls `TelegramDebugService.getStatus()`.
  * Prints:

    * Authenticated?
    * Session dir path.
    * Total chats.
    * Hot/Warm/Cold counts.

* `tg list-chats`:

  * Options:

    * `--class` = `all` | `hot` | `warm` | `cold` (default `all`).
    * `--limit` (default 20).
  * Uses `listChats` with appropriate filter.
  * Prints an aligned table: `chatId`, `title`, `mediaClass`, `mediaCountEstimate`.

* `tg sample-media`:

  * Options:

    * `--chat-id` (required).
    * `--limit` (default 10).
  * Uses `sampleMedia`.
  * Prints:

    * `messageId`, human-formatted date, `mime`, `sizeBytes`, `normalizedTitle`, `normalizedMediaType`.

---

## 5. Xtream Debug Functions (CLI + Pipeline)

### 5.1. XtreamDebugService

Create in `pipeline/xtream`:

```kotlin
interface XtreamDebugService {
    suspend fun getStatus(): XtreamStatus
    suspend fun listVod(limit: Int): List<XtreamVodSummary>
    suspend fun inspectVod(streamId: Int): XtreamVodDetails
}

data class XtreamStatus(
    val baseUrl: String,
    val vodCountEstimate: Int,
    val liveCountEstimate: Int
)

data class XtreamVodSummary(
    val streamId: Int,
    val title: String,
    val year: Int?,
    val categoryName: String?,
    val durationMinutes: Int?,
    val normalizedMediaType: MediaType
)

data class XtreamVodDetails(
    val raw: XtreamVodItem,
    val rawMedia: RawMediaMetadata,
    val normalized: NormalizedMedia
)
```

Implementation must:

* Use existing `XtreamApiClient` and `XtreamPipelineAdapter`.
* For `inspectVod`:

  * Fetch raw DTO (`XtreamVodItem`).
  * Map to `RawMediaMetadata` via `XtreamRawMetadataExtensions`.
  * Call `Normalizer.normalize(listOf(rawMedia), prefs)` to get one `NormalizedMedia`.

### 5.2. CLI Xtream commands

* `xt status`

  * Prints:

    * baseUrl
    * approximate VOD count
    * approximate Live count.

* `xt list-vod --limit N`

  * Prints a table: `streamId`, `title`, `year`, `category`, `duration`, `normalizedMediaType`.

* `xt inspect-id --id <streamId>`

  * Prints:

    * brief raw DTO summary (one line)
    * RawMediaMetadata fields
    * NormalizedMedia canonical title/year and variant list.

---

## 6. Cross-Pipeline & Normalizer CLI Commands

### 6.1. meta normalize-sample

Add command:

```bash
fishit-cli meta normalize-sample --source tg --limit 20
fishit-cli meta normalize-sample --source xc --limit 20
```

Behavior:

* `--source tg`:

  * Get `RawMediaMetadata` from Telegram pipeline (e.g. via debug service, picking first `limit` entries).
* `--source xc`:

  * Get `RawMediaMetadata` from Xtream pipeline.
* In both cases:

  * Call `Normalizer.normalize(rawItems, prefs)`.
  * Print:

    * list of `globalId` with their titles, years, mediaTypes.
    * number of variants per `globalId`.
    * for each variant: pipeline, sourceId, qualityTag, language, resolutionHeight.

### 6.2. play simulate

Add command:

```bash
fishit-cli play simulate --global-id <globalId>
```

Behavior:

1. Query the catalog for `NormalizedMedia` by `globalId` (through a small `CatalogDebugService` you define).
2. Print:

   * canonical title, year, mediaType.
   * sorted variants according to `VariantSelector.sortByPreference`.
3. Simulate fallback:

   * mark the best variant as “failed”.
   * re-run selection and print which variant would be next.
4. Do **not** actually start playback; this is a dry-run.

---

## 7. Codespace Test Tasks

### 7.1. Shell scripts

Add:

* `tools/pipeline-test-fast.sh`:

  ```bash
  #!/usr/bin/env bash
  set -e
  ./gradlew \
    :core:model:test \
    :core:metadata-normalizer:test \
    :pipeline:telegram:test \
    :pipeline:xtream:test
  ```

* `tools/pipeline-test-all.sh`:

  ```bash
  #!/usr/bin/env bash
  set -e
  ./gradlew \
    :core:model:test \
    :core:metadata-normalizer:test \
    :infra:transport-telegram:test \
    :pipeline:telegram:test \
    :pipeline:xtream:test \
    :playback:domain:test \
    :player:internal:test
  ```

### 7.2. VS Code task integration

Add `.vscode/tasks.json` entries:

* `"Pipeline: Test Fast"` → `tools/pipeline-test-fast.sh`
* `"Pipeline: Test All"` → `tools/pipeline-test-all.sh`

---

## 8. Copilot Agent Task (machine-readable)

Use the following as a Copilot Agent task to implement this whole concept step by step:

```text
Title: Implement Codespace-friendly pipeline tests and console for FishIT v2

Goal:
In the `architecture/v2-bootstrap` branch of FishIT-Player, implement:
1) Real-user Telegram TDLib session reuse in Codespaces,
2) A shared AppStartup layer for pipelines,
3) A JVM-based CLI (`tools/pipeline-cli`) that provides Telegram and Xtream debug commands,
4) Cross-pipeline normalization and variant inspection commands,
5) Shell scripts and VSCode tasks to run pipeline tests in a Codespace.

Steps:
1) Implement TDLib session reuse:
   - Create .devcontainer/postStartImportTdlibSession.sh as specified.
   - Ensure TG_API_ID, TG_API_HASH, TDLIB_SESSION_URL and TDLIB_SESSION_PASS are used.
   - Implement TelegramSessionConfig and TelegramClientFactory.fromExistingSession(config)
     in infra/transport-telegram.

2) Implement a shared AppStartup layer:
   - Create AppStartupConfig, AppStartup, AppStartupImpl in a core/infra module.
   - AppStartupImpl must wire TelegramTransportClient + TelegramPipelineAdapter and
     XtreamApiClient + XtreamPipelineAdapter exactly once and return a Pipelines object.

3) Create the tools/pipeline-cli module:
   - Add build.gradle.kts with Kotlin JVM + clikt + SLF4J/Logback.
   - Implement CliConfigLoader that reads env vars (TG_API_ID, TG_API_HASH,
     XTREAM_BASE_URL, XTREAM_USERNAME, XTREAM_PASSWORD) and builds AppStartupConfig.
   - Implement Main.kt that calls AppStartupImpl and passes Pipelines into subcommands.

4) Implement TelegramDebugService and XtreamDebugService:
   - TelegramDebugService must expose getStatus(), listChats(filter, limit),
     and sampleMedia(chatId, limit), using the Hot/Warm/Cold chat classifier, media-only
     flow and the Normalizer to provide normalized titles/types.
   - XtreamDebugService must expose getStatus(), listVod(limit), inspectVod(streamId),
     mapping Xtream items to RawMediaMetadata and then to NormalizedMedia via the Normalizer.

5) Implement CLI commands:
   - tg status, tg list-chats (with --class all/hot/warm/cold), tg sample-media.
   - xt status, xt list-vod, xt inspect-id.
   - meta normalize-sample --source tg/xc.
   - play simulate --global-id <globalId> to show variant ordering and fallback simulation.

6) Add pipeline test scripts and VSCode tasks:
   - tools/pipeline-test-fast.sh and tools/pipeline-test-all.sh as specified.
   - .vscode/tasks.json entries "Pipeline: Test Fast" and "Pipeline: Test All".

7) Run in a Codespace:
   - Validate that `fishit-cli tg status`, `fishit-cli xt status`, and pipeline tests
     run without interactive prompts, using the imported TDLib session.
   - Ensure logs never leak secrets and that Telegram non-media messages are ignored
     in the media pipeline.

The implementation must strictly follow the described file names, structures, and behavior.
No interactive Telegram auth is allowed inside Codespaces or CI.
```

When all of the above is implemented, you will have:

* A real-user Telegram environment in Codespaces,
* Pipelines bootstrapped exactly like in the app,
* A powerful pipeline console (`fishit-cli`) for Telegram and Xtream,
* And a single, repeatable path to validate your v2 pipeline architecture end-to-end.

```
```

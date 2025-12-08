# V2 Logging Contract

This contract defines how **logging must be done in the v2 architecture**, which modules may use which APIs, and what all Copilot agents must respect when adding or modifying log statements.

It is **binding** for:

- All v2 modules (`:app-v2`, `:pipeline:*`, `:player:internal`, `:core:*`, `:infra:*`),
- All Copilot agents working on the `architecture/v2-bootstrap` branch.

---

## 1. Ownership and Responsibilities

### 1.1 Logging Backend Ownership

- `:infra:logging` is the **only module** allowed to:
  - depend on `Timber`,
  - depend on `android.util.Log`,
  - define logging backends such as Crashlytics, Sentry, etc.

- All other modules must use the logical logging façade:

  ```kotlin
  UnifiedLog.d(tag, message)
  UnifiedLog.i(tag, message)
  UnifiedLog.w(tag, message)
  UnifiedLog.e(tag, message)
  UnifiedLog.v(tag, message)
  ```

### 1.2 Application Initialization

- `:app-v2` is responsible for calling:

  ```kotlin
  UnifiedLogInitializer.init(isDebug = BuildConfig.DEBUG)
  ```

- This must happen **early** in the application lifecycle (e.g., in `Application.onCreate()`), before any modules start logging.

---

## 2. Allowed and Forbidden APIs

### 2.1 Allowed in v2 Modules

In all v2 code (except `:infra:logging`):

- ✅ `UnifiedLog.d(tag, msg)`
- ✅ `UnifiedLog.i(tag, msg)`
- ✅ `UnifiedLog.w(tag, msg)`
- ✅ `UnifiedLog.e(tag, msg)`
- ✅ `UnifiedLog.v(tag, msg)`

With optional `Throwable` parameter where appropriate:

```kotlin
UnifiedLog.e(TAG, "Failed to load stream", throwable)
```

### 2.2 Forbidden in v2 Modules

Except inside `:infra:logging`, the following are **forbidden** in v2 code:

- ❌ `android.util.Log.*`
- ❌ `Timber.*`
- ❌ `System.out.println`, `printStackTrace()`
- ❌ Custom one-off logging wrappers

Strict enforcement:

- Detekt `ForbiddenImport` MUST be configured to:
  - disallow `android.util.Log` imports in all modules except `infra/logging/**`,
  - optionally disallow `Timber` imports outside `infra/logging/**`.

---

## 3. Log Levels and Usage Guidelines

### 3.1 Levels

Use log levels consistently:

- `VERBOSE`:
  - Very detailed logs, used only for temporary debugging during development.
  - MUST NOT remain in long-term v2 code unless clearly justified and documented.

- `DEBUG`:
  - Development-time diagnostics (state changes, non-critical flows).
  - Safe to keep in debug builds, but consider gating behind `minLevel`.

- `INFO`:
  - High-level application events:
    - app start/stop,
    - successful major operations (e.g., “Xtream catalog synced (1234 items)”).

- `WARN`:
  - Recoverable issues or unexpected conditions:
    - missing optional configuration,
    - retryable network failures.

- `ERROR`:
  - Failures that affect user experience:
    - exceptions that abort an operation,
    - critical misconfigurations or unrecoverable states.

### 3.2 Choosing the Right Level

**Agents MUST:**

- Use `ERROR` only for real failures, not for control flow.
- Avoid logging routine events at `WARN`/`ERROR` level.
- Use `DEBUG`/`VERBOSE` for noisy details.

**Example:**

```kotlin
// Good:
UnifiedLog.w(TAG, "Token expired, will re-authenticate")

// Bad:
UnifiedLog.e(TAG, "User clicked button") // No error here
```

---

## 4. Tags, Messages, and Structure

### 4.1 Tags

- Use a `private const val TAG = "ModuleName/ClassName"` convention at the top of a file.
- Tags must be:
  - short,
  - stable,
  - meaningful (e.g., `TelegramRepo`, `XtreamClient`, `IOPipeline`, `SIPPlayer`).

### 4.2 Message Content

**Agents MUST:**

- Write messages that clearly describe:
  - what happened,
  - what went wrong (for WARN/ERROR),
  - any relevant identifiers (contentId, chatId, streamId) but not raw secrets.

**Agents MUST NOT:**

- Log secrets:
  - access tokens,
  - passwords,
  - private keys,
  - full URLs with sensitive query parameters.
- Log excessive PII (personally identifiable information).

Example (OK):

```kotlin
UnifiedLog.e(TAG, "Failed to fetch Telegram media for chatId=$chatId", throwable)
```

Not OK:

```kotlin
UnifiedLog.e(TAG, "Failed with token=$authToken and password=$password", throwable)
```

---

## 5. Performance and Noise Control

**Agents MUST:**

- Avoid tight-loop logging (inside per-frame, per-byte, or per-packet loops).
- Avoid dumping huge payloads (full JSON exports, full media lists) at INFO/WARN/ERROR.

If large payloads must be logged (e.g., for debugging), they must:

- be gated behind a conditional,
- or logged at `DEBUG`/`VERBOSE`,
- and removed or disabled in production builds.

---

## 6. Testing and CI Enforcement

### 6.1 Tests

- Logging behavior should be tested in `:infra:logging`:
  - `UnifiedLog` respects `minLevel` correctly.
  - Initializer chooses DebugTree vs. ProductionTree appropriately.
- Other modules should not depend on logging behavior for their logic; tests should:
  - not assert on specific log messages from non-logging modules.

### 6.2 Static Enforcement

- Detekt MUST enforce:
  - no `android.util.Log` imports outside `infra/logging/**`.
  - optionally, no `Timber` imports outside `infra/logging/**`.
- Future CI checks MUST run `./gradlew detekt` as part of v2 pipelines.

If a new log statement violates this contract, it must be:

- either removed,
- or rewritten to use `UnifiedLog` correctly.

---

## 7. Agent-Specific Rules

Any Copilot Agent working in the `architecture/v2-bootstrap` branch MUST:

1. Use only `UnifiedLog` for logging in v2 modules (except when modifying `:infra:logging` itself).
2. Never introduce `android.util.Log` or `Timber` imports outside `infra/logging`.
3. Respect log levels and avoid logging secrets/PII.
4. When migrating or adding logs:
   - ensure tags are meaningful,
   - keep messages understandable and concise,
   - do not introduce heavy or noisy logging in hot code paths.
5. Update `legacy/docs/UNIFIED_LOGGING.md` and this contract only when the logging architecture itself changes, not for one-off log statements.

This contract is the authoritative reference for logging in FishIT-Player v2.

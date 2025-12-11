# V2 Logging Contract

**Version:** 1.1  
**Last Updated:** 2025-12-11

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
  // Primary, lazy API (RECOMMENDED)
  UnifiedLog.d(tag) { "debug message $value" }
  UnifiedLog.i(tag) { "info message" }
  UnifiedLog.w(tag) { "warn message" }
  UnifiedLog.e(tag, throwable) { "error message" }
  UnifiedLog.v(tag) { "verbose message" }

  // Convenience overloads (allowed, but not preferred in hot paths)
  UnifiedLog.d(tag, "debug message")
  UnifiedLog.e(tag, "error message", throwable)
  ```

> **Important:** The **lambda-based overloads** are the **primary** logging API in v2.
> String-based overloads exist for convenience but **MUST NOT** be used for logs that
> involve string interpolation, expensive computations, or large payloads.

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

**Primary API (lazy, preferred):**
- ✅ `UnifiedLog.d(tag) { "message $value" }`  — **preferred (lazy)**
- ✅ `UnifiedLog.i(tag) { "message" }`
- ✅ `UnifiedLog.w(tag) { "message" }`
- ✅ `UnifiedLog.e(tag, throwable) { "message" }`
- ✅ `UnifiedLog.v(tag) { "message" }`

**Convenience API (allowed for constant messages):**
- ✅ `UnifiedLog.d(tag, "constant message")`
- ✅ `UnifiedLog.i(tag, "constant message")`
- ✅ `UnifiedLog.w(tag, "constant message")`
- ✅ `UnifiedLog.e(tag, "constant message", throwable)`
- ✅ `UnifiedLog.v(tag, "constant message")`

**Agents MUST:**
- Prefer lambda-based overloads (`UnifiedLog.x(tag) { ... }`) whenever the log message uses string templates, heavy `toString()` calls, or any non-trivial computation.
- Only use string-based overloads for constant messages or non-critical code paths.

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
  - **VERBOSE logs in hot paths MUST use the lambda-based API** to avoid unnecessary allocations when the log level is disabled.

- `DEBUG`:
  - Development-time diagnostics (state changes, non-critical flows).
  - Safe to keep in debug builds, but consider gating behind `minLevel`.
  - **DEBUG logs in hot paths MUST use the lambda-based API** to avoid unnecessary allocations when the log level is disabled.

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
UnifiedLog.w(TAG) { "Token expired, will re-authenticate" }

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
UnifiedLog.e(TAG) { "Failed to fetch Telegram media for chatId=$chatId" }
```

With throwable:

```kotlin
UnifiedLog.e(TAG, throwable) { "Failed to fetch Telegram media for chatId=$chatId" }
```

Not OK:

```kotlin
UnifiedLog.e(TAG, "Failed with token=$authToken and password=$password", throwable)
```

---

## 5. Performance and Noise Control

### 5.1 Lazy Logging (Lambda-based)

Agents **MUST** use the lambda-based `UnifiedLog` APIs in code paths that:
- are performance sensitive (player, transport, pipelines),
- involve string interpolation or expensive computations.

**Example (GOOD):**

```kotlin
UnifiedLog.d(TAG) { "loading item $id took ${measureMs()} ms" }
```

**Example (BAD in hot paths):**

```kotlin
UnifiedLog.d(TAG, "loading item $id took ${measureMs()} ms")
// measureMs() is evaluated even if DEBUG logs are disabled!
```

> **Why it matters:** Lambda-based logging defers message construction until after the log level check.
> This means expensive `toString()` calls, string interpolation, and computation only happen
> when the log will actually be emitted.

### 5.2 General Performance Rules

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
4. When adding or migrating logs in v2 modules:
   - **Prefer the lambda-based UnifiedLog API** (`UnifiedLog.x(tag) { ... }`) in hot paths (player, transport, pipelines).
   - Only use string-based overloads for constant messages or non-critical code paths.
   - Ensure tags are meaningful.
   - Keep messages understandable and concise.
   - Do not introduce heavy or noisy logging in hot code paths.
5. Update this contract only when the logging architecture itself changes, not for one-off log statements.

---

## 8. Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2025-12-01 | Initial contract: UnifiedLog façade, allowed/forbidden APIs, log levels |
| 1.1 | 2025-12-11 | Introduced lambda-based lazy logging API as primary; clarified performance rules |

---

This contract is the authoritative reference for logging in FishIT-Player v2.

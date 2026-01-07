---
applyTo: 
  - infra/logging/**
---

# 🏆 PLATIN Instructions: infra/logging

**Version:** 1.0  
**Last Updated:** 2026-01-07  
**Status:** Active

> **PLATIN STANDARD** - Unified Logging System (SSOT).
>
> **Purpose:** Central logging facade for ALL v2 modules. Every log statement in the codebase
> MUST use UnifiedLog. Zero exceptions.
>
> **Binding Contract:** `contracts/LOGGING_CONTRACT_V2.md` (Version 1.1)
>
> **Critical Principle:** UnifiedLog is the SINGLE SOURCE OF TRUTH for logging.
> `println`, `Log.d`, `Timber`, `System.out` are FORBIDDEN everywhere except `:infra:logging`.

---

## 🔴 ABSOLUTE HARD RULES

### 1. UnifiedLog is MANDATORY Everywhere (Except :infra:logging)

```kotlin
// ✅ PRIMARY API: Lambda-based (RECOMMENDED for all code)
import com.fishit.player.infra.logging.UnifiedLog

private const val TAG = "MyComponent"

UnifiedLog.d(TAG) { "Starting operation: $operationId" }
UnifiedLog.i(TAG) { "User logged in: userId=${user.id}" }
UnifiedLog.w(TAG) { "Cache miss for key: $key" }
UnifiedLog.e(TAG, exception) { "Failed to load data: itemId=$itemId" }
UnifiedLog.v(TAG) { "Verbose debug: $details" }

// ✅ CONVENIENCE API: String-based (ONLY for constant messages)
UnifiedLog.d(TAG, "Operation started")           // OK - constant string
UnifiedLog.e(TAG, "Critical failure", exception) // OK - constant string

// ❌ FORBIDDEN: String-based with interpolation (eager evaluation!)
UnifiedLog.d(TAG, "Computed: ${expensiveFunction()}")  // WRONG - always evaluated!

// ❌ FORBIDDEN: ANY other logging mechanism
println("Debug: $value")                        // WRONG
Log.d(TAG, "message")                           // WRONG
Log.e(TAG, "error", exception)                  // WRONG
Timber.d("message")                             // WRONG
System.out.println("debug")                     // WRONG
android.util.Log.v(TAG, "verbose")              // WRONG
```

**Per Contract Section 1.1:**
- `:infra:logging` is the ONLY module allowed to depend on `Timber` and `android.util.Log`
- All other modules MUST use `UnifiedLog` exclusively

---

### 2. Lambda-Based API is PRIMARY (Performance Rule)

```kotlin
// ✅ CORRECT: Lazy lambda - message only built if log level enabled
UnifiedLog.d(TAG) { "Expensive computation: ${expensiveFunction()}" }
UnifiedLog.d(TAG) { "loading item $id took ${measureMs()} ms" }

// ❌ WRONG: Eager evaluation in hot paths (player, transport, pipelines)
UnifiedLog.d(TAG, "Expensive computation: ${expensiveFunction()}")  // WRONG!
// ↑ expensiveFunction() and measureMs() ALWAYS evaluated, even if DEBUG disabled!
```

**Per Contract Section 5.1:**
> Lambda-based logging defers message construction until after the log level check.
> This means expensive `toString()` calls, string interpolation, and computation only happen
> when the log will actually be emitted.

**MANDATORY in these modules (per Contract Section 7):**
- `player/**` (player internals)
- `playback/**` (playback sources)
- `pipeline/**` (catalog pipelines)
- `infra/transport-*` (transport layers)

---

### 3. TAG Convention (MANDATORY)

```kotlin
// ✅ CORRECT: Companion object or package-level constant
class MyViewModel @Inject constructor() : ViewModel() {
    companion object {
        private const val TAG = "MyViewModel"
    }
    
    fun doSomething() {
        UnifiedLog.d(TAG) { "Doing something" }
    }
}

// ✅ CORRECT: Package-level constant (recommended for extensions/utils)
private const val TAG = "TelegramSync"

suspend fun syncChat(chatId: Long) {
    UnifiedLog.i(TAG) { "Starting chat sync: chatId=$chatId" }
}

// ❌ WRONG: Inline TAG string
UnifiedLog.d("MyClass") { "message" }  // WRONG - no constant!

// ❌ WRONG: Runtime computation
UnifiedLog.d(this::class.simpleName) { "message" }  // WRONG - runtime overhead!
```

**Per Contract Section 4.1 - TAG Rules:**
- Use `private const val TAG = "ModuleName/ClassName"` convention
- Tags must be: **short, stable, meaningful**
- Examples: `TelegramRepo`, `XtreamClient`, `IOPipeline`, `SIPPlayer`
- Max 23 characters (Android Logcat limit)

---

### 4. Exception Logging Pattern

```kotlin
// ✅ CORRECT: Exception with context message (lambda API)
try {
    riskyOperation()
} catch (e: Exception) {
    UnifiedLog.e(TAG, e) { "Failed to process item: itemId=$itemId, context=$context" }
}

// ✅ CORRECT: Exception with constant message (string API)
try {
    riskyOperation()
} catch (e: Exception) {
    UnifiedLog.e(TAG, "Operation failed", e)
}

// ✅ CORRECT: Warning without exception
if (invalidState) {
    UnifiedLog.w(TAG) { "Invalid state detected: state=$currentState" }
}

// ❌ WRONG: Exception in message string (stack trace LOST!)
UnifiedLog.e(TAG) { "Error: ${e.message}" }  // WRONG - no exception object!

// ❌ WRONG: Exception without context
UnifiedLog.e(TAG, e) { e.message ?: "Unknown error" }  // WRONG - no context!
```

---

### 5. No Sensitive Data in Logs (Security Rule)

```kotlin
// ❌ FORBIDDEN: PII, credentials, tokens
UnifiedLog.d(TAG) { "User email: ${user.email}" }           // GDPR violation!
UnifiedLog.d(TAG) { "API key: ${credentials.apiKey}" }      // Security issue!
UnifiedLog.d(TAG) { "Password: ${credentials.password}" }   // CRITICAL!
UnifiedLog.d(TAG) { "Token: $authToken" }                   // Security issue!
UnifiedLog.d(TAG) { "URL: $urlWithQueryParams" }            // May contain secrets!

// ✅ CORRECT: Use IDs, redact, or boolean flags
UnifiedLog.d(TAG) { "User: userId=${user.id}" }
UnifiedLog.d(TAG) { "API key: configured=${credentials.apiKey != null}" }
UnifiedLog.d(TAG) { "Auth: hasPassword=${credentials.password.isNotEmpty()}" }
UnifiedLog.d(TAG) { "Token: present=${authToken != null}" }
```

**Per Contract Section 4.2:**
> Agents MUST NOT log secrets: access tokens, passwords, private keys, 
> full URLs with sensitive query parameters, or excessive PII.

---

## 📋 Log Level Guidelines (Per Contract Section 3)

### VERBOSE (`UnifiedLog.v`)

**Use for:**
- Very detailed logs for temporary debugging
- High-frequency events (frame rendering, network packets)
- Detailed internal state dumps

**Rules:**
- MUST NOT remain in long-term code unless clearly justified
- **MUST use lambda-based API in hot paths**
- NEVER shipped in release builds

```kotlin
UnifiedLog.v(TAG) { "Buffer updated: position=$position, size=$size" }
```

---

### DEBUG (`UnifiedLog.d`)

**Use for:**
- Development-time diagnostics
- State changes, non-critical flows
- Method entry/exit points

**Rules:**
- Safe in debug builds
- Consider gating behind `minLevel`
- **MUST use lambda-based API in hot paths**

```kotlin
UnifiedLog.d(TAG) { "resolve: canonicalId=$canonicalId, sourceType=$sourceType" }
UnifiedLog.d(TAG) { "State changed: $oldState -> $newState" }
```

---

### INFO (`UnifiedLog.i`)

**Use for:**
- High-level application events
- App start/stop
- Successful major operations
- User actions (login, logout)

```kotlin
UnifiedLog.i(TAG) { "Catalog sync complete: source=TELEGRAM, items=$itemCount" }
UnifiedLog.i(TAG) { "User authenticated: userId=$userId, source=TELEGRAM" }
UnifiedLog.i(TAG) { "Xtream catalog synced (1234 items)" }
```

---

### WARN (`UnifiedLog.w`)

**Use for:**
- Recoverable issues (fallback used)
- Missing optional configuration
- Retryable network failures
- Deprecated API usage

```kotlin
UnifiedLog.w(TAG) { "No factory for sourceType=$sourceType, using fallback" }
UnifiedLog.w(TAG) { "Token expired, will re-authenticate" }
UnifiedLog.w(TAG) { "TMDB enrichment skipped: no API key configured" }
```

---

### ERROR (`UnifiedLog.e`)

**Use for:**
- Failures that affect user experience
- Exceptions that abort an operation
- Critical misconfigurations
- Unrecoverable states

**MUST include exception object when available:**

```kotlin
// ✅ CORRECT
UnifiedLog.e(TAG, exception) { "Failed to load canonical media: id=$canonicalId" }

// ❌ WRONG: Using ERROR for non-errors
UnifiedLog.e(TAG) { "User clicked button" }  // Not an error!
```

---

## 📋 Module Implementation

### Application Initialization (Per Contract Section 1.2)

`:app-v2` MUST call initialization early in `Application.onCreate()`:

```kotlin
class FishItApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // MUST be called BEFORE any modules start logging
        UnifiedLogInitializer.init(isDebug = BuildConfig.DEBUG)
    }
}
```

---

### infra/logging Structure

```
infra/logging/
├── src/main/java/com/fishit/player/infra/logging/
│   ├── UnifiedLog.kt              # Main facade
│   ├── UnifiedLogInitializer.kt   # Application init
│   ├── LogLevel.kt                # Log level enum
│   ├── LogBackend.kt              # Backend abstraction
│   ├── LogcatBackend.kt           # Default backend (uses android.util.Log)
│   ├── LogBufferProvider.kt       # In-memory buffer for debug UI
│   └── di/
│       └── LoggingModule.kt       # Hilt DI
└── build.gradle.kts
```

---

### UnifiedLog API (Per Contract Section 2.1)

```kotlin
object UnifiedLog {
    // ═══════════════════════════════════════════════════════════════
    // PRIMARY API: Lambda-based (RECOMMENDED)
    // ═══════════════════════════════════════════════════════════════
    
    /** Debug log with lazy evaluation. */
    fun d(tag: String, message: () -> String)
    
    /** Info log with lazy evaluation. */
    fun i(tag: String, message: () -> String)
    
    /** Warning log with lazy evaluation. */
    fun w(tag: String, message: () -> String)
    
    /** Error log with exception and lazy message. */
    fun e(tag: String, exception: Throwable? = null, message: () -> String)
    
    /** Verbose log with lazy evaluation. */
    fun v(tag: String, message: () -> String)
    
    // ═══════════════════════════════════════════════════════════════
    // CONVENIENCE API: String-based (for constant messages ONLY)
    // ═══════════════════════════════════════════════════════════════
    
    /** Debug log with constant message. */
    fun d(tag: String, message: String)
    
    /** Info log with constant message. */
    fun i(tag: String, message: String)
    
    /** Warning log with constant message. */
    fun w(tag: String, message: String)
    
    /** Error log with constant message and exception. */
    fun e(tag: String, message: String, exception: Throwable? = null)
    
    /** Verbose log with constant message. */
    fun v(tag: String, message: String)
    
    // ═══════════════════════════════════════════════════════════════
    // UTILITY
    // ═══════════════════════════════════════════════════════════════
    
    /** Check if a log level is enabled (for expensive operations). */
    fun isLoggable(level: LogLevel): Boolean
}
```

---

## 📐 Architecture Position

```
ALL v2 MODULES (except :infra:logging)
         ↓
    UnifiedLog (facade) ← YOU ARE HERE
         ↓
    LogBackend (interface)
         ├── LogcatBackend (android.util.Log - ONLY HERE)
         ├── LogBufferProvider (in-memory for debug UI)
         ├── FirebaseBackend (future - Crashlytics)
         └── SentryBackend (future - error tracking)
```

**Dependency Rule:**
- `:infra:logging` MAY depend on `android.util.Log`, `Timber`
- ALL other modules MUST NOT import these (enforced via Detekt)

---

## 🔍 CI Enforcement (Per Contract Section 6)

### Pre-Change Verification

```bash
# 1. No println anywhere
grep -rn "println(" --include="*.kt" . | grep -v "build/" | grep -v ".gradle/"

# 2. No android.util.Log outside infra/logging
grep -rn "import android.util.Log" --include="*.kt" . | grep -v "infra/logging/"
grep -rn "Log\.[diwev](" --include="*.kt" . | grep -v "infra/logging/" | grep -v "build/"

# 3. No Timber outside infra/logging
grep -rn "import timber.log.Timber" --include="*.kt" . | grep -v "infra/logging/"
grep -rn "Timber\." --include="*.kt" . | grep -v "infra/logging/" | grep -v "build/"

# 4. No System.out anywhere
grep -rn "System\.out\.print" --include="*.kt" . | grep -v "build/"

# All should return empty!
```

### Detekt Configuration (Per Contract Section 2.2)

```yaml
# detekt-config.yml
ForbiddenImport:
  active: true
  imports:
    - value: 'android.util.Log'
      reason: 'Use UnifiedLog. See contracts/LOGGING_CONTRACT_V2.md'
    - value: 'timber.log.Timber'
      reason: 'Use UnifiedLog. See contracts/LOGGING_CONTRACT_V2.md'
  # Exception for :infra:logging module configured via baseline or module-specific config
```

---

## ✅ PLATIN Checklist

### For ALL v2 Modules (Except :infra:logging)

- [ ] Uses UnifiedLog exclusively (no println, Log.d, Timber, System.out)
- [ ] Lambda-based API in hot paths (player, transport, pipelines)
- [ ] String-based API ONLY for constant messages
- [ ] Stable TAG constant (companion object or package-level)
- [ ] TAG max 23 characters, meaningful name
- [ ] Exception passed as parameter (not in message string)
- [ ] No sensitive data (PII, credentials, tokens, passwords)
- [ ] Structured logging with key-value pairs where applicable
- [ ] Appropriate log level (not ERROR for non-errors)

### For :infra:logging Module

- [ ] UnifiedLog facade is thread-safe
- [ ] LogBackend abstraction for extensibility
- [ ] LogcatBackend as default implementation
- [ ] LogBufferProvider for debug UI integration
- [ ] UnifiedLogInitializer for app-level setup
- [ ] Hilt DI module provides all components
- [ ] Lazy evaluation enforced by API design
- [ ] Both lambda and string overloads available

---

## 📚 Reference Documents (Priority Order)

1. **`/contracts/LOGGING_CONTRACT_V2.md`** - AUTHORITATIVE binding contract (v1.1)
2. **`/AGENTS.md`** - Section 5 (Logging, Telemetry & Cache)
3. **`/docs/dev/ARCH_GUARDRAILS.md`** - CI enforcement rules
4. **`/infra/logging/README.md`** - Module-specific documentation
5. Android Logcat documentation

---

## 🚨 Common Violations & Solutions

### Violation 1: Eager Evaluation in Hot Path

```kotlin
// ❌ WRONG (in player/transport/pipeline)
UnifiedLog.d(TAG, "Position: ${player.currentPosition}")  // Always computed!

// ✅ CORRECT
UnifiedLog.d(TAG) { "Position: ${player.currentPosition}" }  // Only if DEBUG enabled
```

### Violation 2: android.util.Log Import

```kotlin
// ❌ WRONG
import android.util.Log
fun logSomething() {
    Log.d(TAG, "message")  // WRONG!
}

// ✅ CORRECT
import com.fishit.player.infra.logging.UnifiedLog
fun logSomething() {
    UnifiedLog.d(TAG) { "message" }
}
```

### Violation 3: Exception as String

```kotlin
// ❌ WRONG - Stack trace lost!
catch (e: Exception) {
    UnifiedLog.e(TAG) { "Error: ${e.message}" }
}

// ✅ CORRECT - Stack trace preserved
catch (e: Exception) {
    UnifiedLog.e(TAG, e) { "Operation failed: context=$context" }
}
```

### Violation 4: ERROR for Non-Errors

```kotlin
// ❌ WRONG - Not an error
UnifiedLog.e(TAG) { "User clicked button" }

// ✅ CORRECT - Use appropriate level
UnifiedLog.d(TAG) { "User clicked button" }
```

### Violation 5: Secrets in Logs

```kotlin
// ❌ WRONG - Security issue!
UnifiedLog.d(TAG) { "Token: $authToken, Password: $password" }

// ✅ CORRECT - Redacted
UnifiedLog.d(TAG) { "Token: present=${authToken != null}, hasPassword=true" }
```

---

**End of PLATIN Instructions for infra/logging**

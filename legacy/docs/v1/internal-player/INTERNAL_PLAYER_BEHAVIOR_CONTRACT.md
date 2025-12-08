> LEGACY (V1) – historical document  
> Not valid for v2. For current architecture see `V2_PORTAL.md` and `docs/v2/**`.

# INTERNAL PLAYER BEHAVIOR CONTRACT – RESUME & KIDS MODE

Version: 1.0  
Scope: Resume behavior & kids/screen-time enforcement for FishIT Player’s internal player (legacy + SIP).  
Status: Drafted during Phase 3 (shadow mode). This document is the **functional specification**; legacy behavior is informative, not authoritative.

---

## 1. Design Goals

1. Provide a **clear, testable contract** for resume and kids/screen-time behavior.
2. Allow the **modular SIP implementation** to intentionally fix legacy bugs without “locking in” incorrect legacy behavior.
3. Enable **shadow-mode comparison** (legacy vs SIP) to classify differences as:
   - **Spec-compliant SIP vs legacy bug**
   - **Spec violation in SIP**
   - **Irrelevant / tolerated divergence**
4. Keep behavior intuitive and aligned with common video UX and parental-control patterns.

This contract applies to:
- `DefaultResumeManager`
- `DefaultKidsPlaybackGate`
- `Phase2Integration`
- Shadow/diagnostic tools that interpret resume/kids behavior.

Legacy `InternalPlayerScreen` is treated as a reference implementation, not as the source of truth.

---

## 2. Terminology

- **VOD**: Movie or single, non-episodic video.
- **Series episode**: Video that belongs to a series (seriesId + season + episodeNumber).
- **LIVE**: Live TV channel or stream.
- **Position**: Current playback position in milliseconds.
- **Duration**: Known media duration in milliseconds.
- **Remaining time**: `remainingMs = max(0, durationMs - positionMs)`.
- **Resume entry**: Persisted data describing where to continue playback.

---

## 3. Resume Behavior Contract

### 3.1. Global Rules

1. Resume behavior is defined **only** for:
   - VOD
   - Series episodes
2. LIVE content **never** resumes from a saved position.
3. Resume logic must be **idempotent** and **safe**:
   - No crashes on missing data, negative duration, or malformed IDs.
   - Failing repositories must result in "no resume", not in a crash.

> **Note (LIVE Playback & Contract Enforcer):**
> LIVE playback is excluded from resume rules and is not affected by Contract Enforcer
> beyond diagnostics. The `LivePlaybackController` handles channel navigation, EPG overlay,
> and auto-hide timing, but does NOT integrate with `ResumeManager` or `BehaviorContractEnforcer`.
> Shadow diagnostics may observe LIVE sessions for validation purposes without affecting runtime behavior.

### 3.2. Identification Rules

#### 3.2.1. VOD

- VOD resume entries are keyed by:
  - `mediaId: Long`
- A VOD resume entry must be independent of URL, provider, or chat origin.

#### 3.2.2. Series Episodes

- Series episodes may be identified by:
  - Preferred key: `(seriesId: Int, season: Int?, episodeNumber: Int?)`
  - Fallback key: `episodeId: Int?` (for legacy sources)
- If both composite key and episodeId are available, composite key should be used as **primary**, episodeId as **migration fallback**.

### 3.3. Resume Load (On Start)

When a playback session is created, `DefaultResumeManager.loadResumePositionMs(playbackContext)` is used to load a resume point.

**Contract:**

1. If no entry exists → return `null` (no resume).
2. If the stored position is **less than or equal to 10 seconds** → return `null` (treat as “never started”).
3. If the stored position is **greater than 10 seconds** and **less than duration - nearEndThreshold** (see 3.4) → return the stored position.
4. If a stored position exists, but the duration is unknown or implausible:
   - Use the stored position if it is > 10 seconds and < some upper bound (e.g. 24h).
   - If position is implausible (e.g. > 48h), return `null` and log a diagnostic.

This ensures that trivial “zaps” do not create resume entries.

### 3.4. Near-End Behavior

To avoid resuming at a point where the content is practically finished, define:

- `nearEndThresholdSeconds = 10`
- `nearEndThresholdMs = 10_000`

**Contract:**

- If `remainingMs <= nearEndThresholdMs` at the time the media is considered ended or last saved:
  - The resume entry must be **cleared**.
  - Future sessions must **start from 0**.

This mirrors common media player behavior where files are considered “watched” near the end, but uses a **fixed time threshold** instead of percentages for determinism.

### 3.5. Periodic Tick (During Playback)

The player periodically calls:  
`DefaultResumeManager.handlePeriodicTick(playbackContext, positionMs, durationMs)`.

**Contract:**

1. The caller is responsible for invoking this method approximately every **3 seconds** for VOD/Series playback.
2. `handlePeriodicTick` must:
   - Ignore calls with invalid data (negative duration, position > duration).
   - Store the current position if:
     - `positionMs > 10_000` (10 seconds)
     - and `remainingMs > nearEndThresholdMs`
   - If `remainingMs <= nearEndThresholdMs`:
     - Clear any existing resume entry.
3. LIVE playback:
   - `handlePeriodicTick` must be a **no-op** (except for optional diagnostics).

### 3.6. End of Playback

When playback reaches a true `STATE_ENDED` event, `handleEnded(playbackContext)` is invoked.

**Contract:**

- For VOD and Series:
  - If the final position satisfies `remainingMs <= nearEndThresholdMs` → clear resume entry.
- For LIVE:
  - No resume entry should exist, but if one does (due to bugs), it must be cleared.

This guarantees that finishing a video does not leave a stale resume marker.

---

## 4. Kids / Screen-Time Contract

### 4.1. Goals

1. Provide a simple, predictable **daily screen-time quota** per kid profile.
2. Ensure the logic is **defensible** and aligns with typical parental control patterns.
3. Make failure behavior **explicit** (error vs fail-open vs fail-closed).

### 4.2. Identification

- Kids logic is only active when:
  - A current profile is set, and
  - That profile is marked as a “kid” profile.
- A profile is considered a “kid” if:
  - `profile.type == "kid"` (or equivalent semantics in ObxProfile).

### 4.3. Daily Quota

- Each kid profile has a **remaining daily quota** in minutes.
- The source of truth is typically a persistent store (e.g. `ScreenTimeRepository`).

**Contract:**

1. Quota is decremented **only** when:
   - Playback is active (VOD, Series, or LIVE), and
   - A kid profile is active.
2. Quota is not decremented when:
   - Playback is paused for a “long enough” period (exact definition may be handled by caller and spec’d later).
3. If the remaining quota is `<= 0`:
   - The kid state must be considered **blocked**.

### 4.4. Tick Mechanics

`DefaultKidsPlaybackGate.onPlaybackTick(currentState, deltaSecs)` is called periodically.

**Contract:**

1. `deltaSecs` is the amount of time since the last tick and should normally be **3 seconds**.
2. The gate implementation must accumulate seconds internally and only deduct from the daily quota when it reaches (or exceeds) **60 seconds**.
   - Example:
     - Accumulate: `accumulator += deltaSecs`
     - If `accumulator >= 60`:
       - Decrement quota by 1 minute.
       - Subtract 60 from accumulator (carry over remainder).
3. When the last remaining minute is consumed (quota transitions from 1 → 0):
   - The state must transition to “blocked”.
4. If the quota is already `<= 0` at tick time:
   - The state remains blocked.

### 4.5. Start Evaluation

`DefaultKidsPlaybackGate.evaluateStart()` is called when playback begins (or when profile changes).

**Contract:**

1. It must:

   - Evaluate whether a current profile is a kid.
   - Fetch the remaining daily quota for that profile.
   - Return a state that indicates:
     - `kidActive: Boolean`
     - `kidBlocked: Boolean`
     - `remainingMinutes: Int?`

2. If any error occurs during evaluation (repository failures, I/O, etc.):
   - The implementation must **not crash**.
   - Fail-open vs fail-closed behavior must be selectable:
     - Default: **fail-open** (allow playback, but report diagnostics).
     - Future: A config flag may allow a stricter fail-closed mode.

This avoids bricking playback due to temporary data issues.

### 4.6. Blocked State Semantics

When kids’ gate decides the user is blocked:

1. `kidBlocked == true` must remain true until:
   - The current playback session stops, or
   - A profile change occurs, or
   - The quota is explicitly reset (new day, manual override).
2. It is the **responsibility of the caller** (player UI/session) to:
   - Pause playback as soon as `kidBlocked` becomes true.
   - Surface an appropriate UI (overlay, dialog, navigation) to inform the user.
3. `DefaultKidsPlaybackGate` must not attempt to control the player directly.
   - It is a **pure policy component**.

This matches the separation of concerns: KidsGate decides “allowed vs blocked”, the session/UI decides “what to do when blocked”.

---

## 5. Spec vs Legacy vs SIP – Classification

Because the legacy `InternalPlayerScreen` may contain historical bugs or inconsistent behavior, this contract uses a classification model for differences.

For any observed behavior (legacy vs SIP):

1. **Spec-compliant SIP**  
   - SIP behavior matches this contract.  
   - Legacy may differ.  
   - Classification: `SpecPreferredSIP`.  
   - Action: Keep SIP behavior. Document legacy behavior as a historical bug.

2. **Spec violation in SIP**  
   - SIP does not follow the contract.  
   - Legacy may or may not be correct.  
   - Classification: `SpecPreferredLegacy` or `SpecViolation`.  
   - Action: Fix SIP implementation to match the spec (or adjust spec if spec is wrong).

3. **Both match the spec**  
   - Classification: `ExactMatch`.  
   - Action: No change needed.

4. **Spec says “don’t care” or “tolerated variance”**  
   - For example: minor drift in reported remaining minutes, minor time offset within an allowed tolerance window.  
   - Classification: `DontCare`.  
   - Action: No change required; difference is accepted.

Shadow-mode tooling (e.g. `ShadowComparisonService`) should use this classification model when reporting mismatches, so that known legacy bugs can be explicitly documented and SIP behavior can intentionally improve on legacy.

---

## 6. Testing Requirements

### 6.1. ResumeManager Tests

Tests must cover at least:

- No resume when stored position `<= 10s`.
- Resume when stored position `> 10s` and not near end.
- Clear resume when remaining time `< 10s` (both on tick and on end).
- VOD vs Series ID mapping (mediaId vs composite key vs episodeId fallback).
- Robustness against invalid data (negative duration, position > duration).

### 6.2. KidsPlaybackGate Tests

Tests must cover at least:

- Kid profile detection vs adult profile.
- 60-second accumulation logic with various delta patterns (3s, 5s, etc.).
- Transition from allowed → blocked when quota hits 0.
- Behavior when repositories fail (errors): fail-open by default, with correct diagnostics.
- Block state persistence until end-of-session or profile change.

### 6.3. Integration / Shadow Tests

- Combined behavior with `Phase2Integration` must demonstrate that SIP can adhere to this contract in isolation.
- Differences against legacy must be logged and classified, not blindly treated as errors.

---

## 7. Evolution and Ownership

- This contract is the **living source of truth** for resume and kids behavior.
- Any change in behavior must be reflected here **before** being implemented in SIP or legacy code.
- Legacy behavior may diverge from this specification; such divergences must be:
  - Documented
  - Classified
  - Eventually removed when the modular player becomes the active implementation.

Once the modular InternalPlayerScreen becomes the runtime default, this contract governs actual user-facing behavior.

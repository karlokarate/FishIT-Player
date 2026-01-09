# OBX PLATIN Kill-Switch Guide

**Version:** 1.0  
**Status:** BINDING  
**Created:** 2026-01-09  
**Parent Issue:** #621 (OBX PLATIN Refactor)

---

## 1. Overview

### 1.1 Purpose of Kill-Switch Mechanism

The kill-switch provides a **safe rollback path** during the OBX PLATIN migration. It allows instant reversion to legacy behavior without app reinstallation or data loss.

**Key Benefits:**
- Zero-downtime rollback
- No data migration needed for rollback
- Instant switch via DataStore preference
- Testable in production without risk

### 1.2 When to Use Rollback

**Trigger Conditions:**

| Condition | Action | Priority |
|-----------|--------|----------|
| App crashes on startup after NX migration | Immediate rollback | CRITICAL |
| UI shows empty lists where data should exist | Investigate, then rollback if persistent | HIGH |
| Performance degradation >50% | Rollback and report | HIGH |
| Resume positions lost/incorrect | Rollback, verify data | MEDIUM |
| Favorites not appearing | Check mode config first | LOW |

**Do NOT Rollback For:**
- Expected empty states during migration
- Temporary network issues
- Single-item display bugs (report instead)

---

## 2. Mode Configuration

### 2.1 CatalogReadMode

Controls where the app reads catalog data from.

```kotlin
enum class CatalogReadMode {
    LEGACY,     // Read from Obx* entities only (safe default)
    NX_ONLY,    // Read from NX_* entities only (target state)
    DUAL_READ   // Read from both, prefer NX_* (validation mode)
}
```

| Mode | Use Case | Risk Level |
|------|----------|------------|
| `LEGACY` | Pre-migration, rollback | ✅ Safe |
| `DUAL_READ` | Migration validation | ⚠️ Medium |
| `NX_ONLY` | Post-migration target | ⚠️ Requires complete migration |

### 2.2 CatalogWriteMode

Controls where new catalog data is written.

```kotlin
enum class CatalogWriteMode {
    LEGACY,     // Write to Obx* entities only (safe default)
    NX_ONLY,    // Write to NX_* entities only (target state)
    DUAL_WRITE  // Write to both for validation
}
```

| Mode | Use Case | Risk Level |
|------|----------|------------|
| `LEGACY` | Pre-migration, rollback | ✅ Safe |
| `DUAL_WRITE` | Migration validation | ⚠️ Medium (2x writes) |
| `NX_ONLY` | Post-migration target | ⚠️ Requires UI on NX_* |

### 2.3 Default Values at Launch

```kotlin
// In CatalogModePreferences.kt
object CatalogModeDefaults {
    val READ_MODE = CatalogReadMode.LEGACY   // Safe: use proven legacy path
    val WRITE_MODE = CatalogWriteMode.LEGACY // Safe: don't touch NX_ yet
}
```

**Migration Progression:**

```
Phase 0-3: LEGACY / LEGACY (default)
     ↓
Phase 4:   DUAL_READ / DUAL_WRITE (validation)
     ↓
Phase 5:   NX_ONLY / NX_ONLY (target)
     ↓
Phase 6:   NX_ONLY / NX_ONLY (cleanup legacy)
```

---

## 3. Rollback Procedure

### 3.1 Step-by-Step Rollback

#### Step 1: Detect Issue Requiring Rollback

**Symptoms to watch:**
```
- App crash on launch (check Logcat for "NX_" or "ObjectBox" errors)
- Empty Home screen after sync completes
- "No content available" in Library with known data
- Resume positions showing 0% for watched content
- Slow UI response (>3 seconds for list load)
```

**Diagnostic command:**
```bash
adb logcat -s FishIT:* ObjectBox:* | grep -E "(NX_|CatalogMode|FATAL)"
```

#### Step 2: Trigger Rollback via ADB

**Option A: Via Debug Settings (if app opens)**
```
Settings → Debug Menu → Catalog Mode → Set to LEGACY
```

**Option B: Via ADB (if app crashes)**
```bash
# Set read mode to LEGACY
adb shell "run-as com.fishit.player cat /data/data/com.fishit.player/shared_prefs/catalog_mode_prefs.xml"

# Force LEGACY mode
adb shell "run-as com.fishit.player sh -c 'echo \"<?xml version=\\\"1.0\\\" encoding=\\\"utf-8\\\"?><map><string name=\\\"catalog_read_mode\\\">LEGACY</string><string name=\\\"catalog_write_mode\\\">LEGACY</string></map>\" > /data/data/com.fishit.player/shared_prefs/catalog_mode_prefs.xml'"

# Force stop and restart
adb shell am force-stop com.fishit.player
adb shell monkey -p com.fishit.player -c android.intent.category.LAUNCHER 1
```

**Option C: Clear app data (last resort)**
```bash
adb shell pm clear com.fishit.player
```
⚠️ Warning: This clears all local data including legacy!

#### Step 3: Verify App Functionality

After rollback, verify:

- [ ] App launches without crash
- [ ] Home screen shows Continue Watching (if data exists)
- [ ] Library shows VOD/Series/Live content
- [ ] Playback works for at least one item
- [ ] Resume position is preserved

#### Step 4: Report Issue for Investigation

Create GitHub issue with:
```markdown
## OBX PLATIN Rollback Report

**Date:** YYYY-MM-DD
**App Version:** X.Y.Z
**Previous Mode:** [NX_ONLY / DUAL_READ / etc.]

### Symptoms
- [Describe what went wrong]

### Logs
```
[Paste relevant Logcat output]
```

### Steps to Reproduce
1. [Step 1]
2. [Step 2]

### Rollback Successful?
- [ ] Yes, app works in LEGACY mode
- [ ] No, additional issues found
```

---

## 4. DataStore Implementation

### 4.1 Location

```
core/persistence/config/CatalogModePreferences.kt
```

### 4.2 Key Names and Defaults

```kotlin
object CatalogModeKeys {
    const val PREFS_NAME = "catalog_mode_prefs"
    const val KEY_READ_MODE = "catalog_read_mode"
    const val KEY_WRITE_MODE = "catalog_write_mode"
    const val KEY_MIGRATION_STATE = "migration_state"
    const val KEY_LAST_MODE_CHANGE = "last_mode_change_timestamp"
}
```

### 4.3 Read/Write Modes

```kotlin
@Singleton
class CatalogModePreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences(
        CatalogModeKeys.PREFS_NAME, 
        Context.MODE_PRIVATE
    )
    
    var readMode: CatalogReadMode
        get() = CatalogReadMode.valueOf(
            prefs.getString(CatalogModeKeys.KEY_READ_MODE, null) 
                ?: CatalogModeDefaults.READ_MODE.name
        )
        set(value) {
            prefs.edit()
                .putString(CatalogModeKeys.KEY_READ_MODE, value.name)
                .putLong(CatalogModeKeys.KEY_LAST_MODE_CHANGE, System.currentTimeMillis())
                .apply()
            logModeChange("READ", value)
        }
    
    var writeMode: CatalogWriteMode
        get() = CatalogWriteMode.valueOf(
            prefs.getString(CatalogModeKeys.KEY_WRITE_MODE, null)
                ?: CatalogModeDefaults.WRITE_MODE.name
        )
        set(value) {
            prefs.edit()
                .putString(CatalogModeKeys.KEY_WRITE_MODE, value.name)
                .putLong(CatalogModeKeys.KEY_LAST_MODE_CHANGE, System.currentTimeMillis())
                .apply()
            logModeChange("WRITE", value)
        }
    
    fun rollbackToLegacy() {
        readMode = CatalogReadMode.LEGACY
        writeMode = CatalogWriteMode.LEGACY
        Log.w("CatalogMode", "🚨 ROLLBACK TO LEGACY TRIGGERED")
    }
    
    private fun logModeChange(type: String, value: Any) {
        Log.i("CatalogMode", "Mode change: $type -> $value")
    }
}
```

### 4.4 Usage in Repositories

```kotlin
class HomeRepository @Inject constructor(
    private val catalogMode: CatalogModePreferences,
    private val legacyRepo: ObxCanonicalMediaRepository,
    private val nxRepo: NxWorkRepository
) {
    fun getContinueWatching(): Flow<List<MediaItem>> {
        return when (catalogMode.readMode) {
            CatalogReadMode.LEGACY -> legacyRepo.getContinueWatching()
            CatalogReadMode.NX_ONLY -> nxRepo.getContinueWatching()
            CatalogReadMode.DUAL_READ -> {
                // Prefer NX, fallback to legacy
                nxRepo.getContinueWatching().map { nxItems ->
                    if (nxItems.isEmpty()) {
                        legacyRepo.getContinueWatchingSync()
                    } else {
                        nxItems
                    }
                }
            }
        }
    }
}
```

---

## 5. Testing Rollback

### 5.1 QA Checklist for Rollback Verification

**Pre-Rollback State:**
- [ ] App is running in NX_ONLY mode
- [ ] Content is visible in Home/Library
- [ ] At least one item has resume progress

**Rollback Execution:**
- [ ] Switch to LEGACY mode via Debug Settings
- [ ] App does NOT crash
- [ ] No ANR (Application Not Responding)

**Post-Rollback Verification:**
- [ ] Home screen loads within 3 seconds
- [ ] Continue Watching shows same items
- [ ] Library VOD count matches pre-rollback
- [ ] Library Series count matches pre-rollback
- [ ] Live TV channels visible
- [ ] Playback works (test 1 VOD, 1 Live)
- [ ] Resume position preserved (test watched item)
- [ ] Search returns results
- [ ] Favorites still marked

### 5.2 Automated Test for Mode Switching

```kotlin
@Test
fun `rollback from NX_ONLY to LEGACY preserves functionality`() = runTest {
    // Given: App in NX_ONLY mode with data
    catalogModePrefs.readMode = CatalogReadMode.NX_ONLY
    catalogModePrefs.writeMode = CatalogWriteMode.NX_ONLY
    
    val nxHomeItems = homeRepository.getContinueWatching().first()
    assertThat(nxHomeItems).isNotEmpty()
    
    // When: Rollback to LEGACY
    catalogModePrefs.rollbackToLegacy()
    
    // Then: Legacy data is accessible
    val legacyHomeItems = homeRepository.getContinueWatching().first()
    assertThat(legacyHomeItems).isNotEmpty()
    
    // And: Mode is correctly set
    assertThat(catalogModePrefs.readMode).isEqualTo(CatalogReadMode.LEGACY)
    assertThat(catalogModePrefs.writeMode).isEqualTo(CatalogWriteMode.LEGACY)
}

@Test
fun `mode switch does not cause crash`() = runTest {
    val modes = listOf(
        CatalogReadMode.LEGACY,
        CatalogReadMode.DUAL_READ,
        CatalogReadMode.NX_ONLY
    )
    
    modes.forEach { mode ->
        // Should not throw
        catalogModePrefs.readMode = mode
        
        // Should still return data (empty list OK, crash not OK)
        val result = runCatching { 
            homeRepository.getContinueWatching().first() 
        }
        assertThat(result.isSuccess).isTrue()
    }
}
```

---

## 6. Monitoring

### 6.1 Logs to Watch for NX Issues

**Logcat Tags:**
```bash
adb logcat -s CatalogMode:* NxWork:* NxIngest:* ObjectBox:* FishIT:E
```

**Error Patterns:**

| Pattern | Meaning | Action |
|---------|---------|--------|
| `NxWorkRepository: Query returned 0 results` | NX data not migrated | Check migration status |
| `ObjectBox: Box not found: NX_Work` | Schema mismatch | Rebuild app |
| `CatalogMode: ROLLBACK TO LEGACY TRIGGERED` | Auto-rollback occurred | Investigate cause |
| `NxIngest: Ledger insert failed` | Invariant violation | Check disk space |
| `FATAL EXCEPTION: NX migration` | Critical failure | Immediate rollback |

### 6.2 Metrics to Track Post-Migration

**DataStore Counters (future implementation):**

```kotlin
object MigrationMetrics {
    var nxWorkCount: Int = 0
    var nxSourceRefCount: Int = 0
    var nxVariantCount: Int = 0
    var ingestAcceptedCount: Int = 0
    var ingestRejectedCount: Int = 0
    var rollbackCount: Int = 0
    var lastSuccessfulNxRead: Long = 0
}
```

**Health Check Query:**
```kotlin
fun checkNxHealth(): NxHealthStatus {
    val workCount = nxWorkBox.count()
    val sourceRefCount = nxSourceRefBox.count()
    val variantCount = nxVariantBox.count()
    
    return NxHealthStatus(
        isHealthy = workCount > 0 && sourceRefCount >= workCount,
        workCount = workCount,
        sourceRefCount = sourceRefCount,
        variantCount = variantCount,
        incompleteWorks = nxWorkBox.query()
            .apply(NX_Work_.isComplete.equal(false))
            .build().count()
    )
}
```

---

## 7. Emergency Contacts

If rollback fails or data loss occurs:

1. **Check GitHub Issues** for known problems
2. **Create urgent issue** with `priority:critical` label
3. **Include full Logcat** from crash/issue
4. **Document exact steps** that led to failure

---

## 8. References

- **Parent Issue:** #621
- **SSOT Contract:** `/contracts/NX_SSOT_CONTRACT.md`
- **Roadmap:** `docs/v2/OBX_PLATIN_REFACTOR_ROADMAP.md`
- **Architecture:** `AGENTS.md` Section 4

---

## 9. Change Log

| Date | Version | Changes |
|------|---------|---------|
| 2026-01-09 | 1.0 | Initial guide creation |

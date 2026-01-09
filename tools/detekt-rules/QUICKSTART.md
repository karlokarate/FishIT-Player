# OBX PLATIN Detekt Rules - Quick Start Guide

## For Developers

### What These Rules Do

Prevent architectural violations in the OBX PLATIN refactor:

1. **BoxStore Isolation** - BoxStore only in repositories
2. **No Secrets in DB** - No passwords/tokens in ObjectBox entities
3. **Track Ingest** - Pipeline must log all ingest decisions

### How to Check Your Code

Run the verification script:

```bash
bash tools/detekt-rules/verify-guardrails.sh
```

Or run Detekt:

```bash
./gradlew detekt
```

### If You Get a BoxStore Violation

**Error:**
```
ForbiddenImport: io.objectbox.BoxStore must only be used in repositories
```

**Fix:**

❌ **Don't do this:**
```kotlin
class MyViewModel @Inject constructor(
    private val boxStore: BoxStore,  // WRONG!
) {
    fun loadData() {
        val box = boxStore.boxFor(MyEntity::class.java)
        return box.all
    }
}
```

✅ **Do this instead:**
```kotlin
// Create a repository interface
interface MyRepository {
    fun getAll(): List<MyItem>
}

// Implement in infra/data-* module
class ObxMyRepository @Inject constructor(
    private val boxStore: BoxStore,  // OK in repository!
) : MyRepository {
    override fun getAll(): List<MyItem> {
        return boxStore.boxFor(ObxMyEntity::class.java).all.map { it.toDomain() }
    }
}

// Use in ViewModel
class MyViewModel @Inject constructor(
    private val repository: MyRepository,  // Use interface!
) {
    fun loadData() = repository.getAll()
}
```

### If You Need to Store Sensitive Data

**Don't use ObjectBox entities:**

❌ **Wrong:**
```kotlin
@Entity
data class ObxUser(
    @Id var id: Long = 0,
    var password: String = "",  // SECURITY RISK!
)
```

✅ **Correct:**
```kotlin
// Use EncryptedSharedPreferences or Android Keystore
class SecureStorage @Inject constructor(
    private val encryptedPrefs: EncryptedSharedPreferences,
) {
    fun savePassword(userId: Long, password: String) {
        encryptedPrefs.edit()
            .putString("pwd_$userId", password)
            .apply()
    }
}
```

## For Code Reviewers

### What to Check

1. **BoxStore Usage**
   - ✅ Automated - CI will fail
   - Check: No direct BoxStore in ViewModels/UseCases

2. **Sensitive Fields**
   - 📝 Manual review
   - Check: No password/secret/token/apiKey in `@Entity` classes
   - Run: `bash tools/detekt-rules/verify-guardrails.sh`

3. **Ingest Tracking**
   - 📝 Manual review
   - Check: Pipeline functions create IngestLedger entries
   - Look for: Functions that drop content silently

### Quick Verification

```bash
# Check all rules
bash tools/detekt-rules/verify-guardrails.sh

# Should output:
# ✓ Rule 1 (BoxStore):  ✅ PASS
# ✓ Rule 2 (Secrets):   ✅ PASS  
# ✓ Rule 3 (Ledger):    ⚠️  Manual review
```

## Documentation

- **Full Guide:** [README.md](README.md)
- **Contract:** [docs/v2/OBX_PLATIN_DETEKT_GUARDRAILS_CONTRACT.md](../../docs/v2/OBX_PLATIN_DETEKT_GUARDRAILS_CONTRACT.md)
- **Implementation:** [IMPLEMENTATION_SUMMARY.md](IMPLEMENTATION_SUMMARY.md)
- **Tests:** [TEST_REPORT.md](TEST_REPORT.md)

## Getting Help

1. Check example code: `tools/detekt-rules/examples/ExampleViolation.kt`
2. Read the contract: `docs/v2/OBX_PLATIN_DETEKT_GUARDRAILS_CONTRACT.md`
3. Ask in PR review or team chat

## FAQ

**Q: Can I use BoxStore in my ViewModel?**  
A: No. Use a repository interface instead.

**Q: Can I temporarily disable the rule?**  
A: No. Fix the architectural issue instead.

**Q: Where should I put my repository?**  
A: In `infra/data-<module>` (e.g., `infra/data-home`)

**Q: What if I need custom queries?**  
A: Add methods to your repository interface.

**Q: Can I store API tokens in ObjectBox?**  
A: No. Use EncryptedSharedPreferences.

**Q: How do I test code that uses repositories?**  
A: Mock the repository interface in tests.

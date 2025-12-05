# Build Output Cleanup - Phase 2

**Date:** 2025-12-05  
**Branch:** `copilot/begin-phase-2-tasks`  
**Issue:** Accidental commit of Gradle build outputs  
**Resolution:** Cleanup commit with enforced ignore rules

---

## What Was Removed

The following build output directories were accidentally committed in the Phase 2 persistence implementation and have been removed from version control:

### Affected Modules

1. **`core/model/build/`** - ~600 generated files
   - Compiled classes, JAR files, R files
   - Annotation processor outputs
   - Lint intermediates and reports
   - Merged manifests and resources

2. **`core/persistence/build/`** - ~618 generated files
   - ObjectBox-generated code
   - Kotlin compiled classes
   - KSP/KAPT stubs and annotations
   - Hilt DI generated code
   - Build intermediates

### Total Impact

- **1,218 build artifact files** removed from Git tracking
- These were machine-generated outputs from the Gradle build process
- Total commit size reduced by multiple megabytes

---

## Why Build Outputs Must Not Be Tracked

Build outputs should **never** be committed to version control for several reasons:

### 1. Repository Bloat
- Build directories contain hundreds to thousands of files per module
- Binary files (`.class`, `.jar`, `.dex`) are not diff-friendly
- Each build generates new outputs, causing massive repository growth

### 2. Merge Conflicts
- Generated files change with every build
- Different developers' machines produce slightly different outputs
- Causes frequent, meaningless merge conflicts

### 3. Redundancy
- All build outputs can be regenerated from source code
- Gradle handles dependency resolution and compilation
- No value in storing generated artifacts

### 4. Platform Differences
- Build outputs may differ across:
  - Operating systems (Windows, macOS, Linux)
  - JDK versions
  - Gradle daemon states
- Creates "phantom" changes in PRs

### 5. Security Concerns
- Build directories may contain sensitive information
- API keys, signing configs could leak into compiled code
- Better to regenerate locally

---

## New Ignore Rules

The `.gitignore` has been updated with a comprehensive pattern to prevent future issues:

### Before (Multiple Specific Patterns)
```gitignore
/build
app/build/
libtd/build/
*/build/
```

### After (Universal Pattern)
```gitignore
/build
**/build/
```

### What This Covers

The `**/build/` pattern matches:
- Root-level build directories: `/build/`
- Module build directories: `app/build/`, `core/model/build/`
- Nested module builds: `core/persistence/build/`, `feature/home/build/`
- Any depth: `a/b/c/d/build/` (future nested modules)

---

## Expectations for v2 Branch

### ✅ ALLOWED in Version Control

**Source Files:**
- `*.kt`, `*.java` - Kotlin and Java source code
- `*.xml` - Layouts, manifests, resources
- `build.gradle.kts` - Build configuration
- `proguard-rules.pro` - ProGuard rules

**Schema Definitions:**
- `objectbox-models/default.json` - ObjectBox schema evolution tracking
- Database migration scripts

**Configuration:**
- Hilt modules (`*Module.kt`)
- Data models and DTOs
- Repository interfaces and implementations

**Documentation:**
- README files
- KDoc comments in source
- Architecture diagrams

### ❌ NEVER Commit

**Generated Code:**
- `build/**` - All Gradle build outputs
- `.gradle/**` - Gradle daemon and cache
- `**/generated/**` - Annotation processor outputs
- `**/tmp/**` - Temporary build files

**IDE Files:**
- `.idea/workspace.xml` - IntelliJ workspace
- `*.iml` - IntelliJ module files (most)
- `.DS_Store` - macOS metadata

**Binary Artifacts:**
- `*.apk` - Compiled Android applications
- `*.aab` - Android App Bundles
- `*.jar` - Library archives (unless vendored)
- `*.so` - Native libraries (unless vendored)

**Credentials:**
- `local.properties` - SDK paths, API keys
- `*.jks`, `*.keystore` - Signing keys
- `*.pem`, `*.p12` - Certificates

---

## How This Was Fixed

### Step 1: Remove from Git (Keep Local Files)
```bash
git rm -r --cached core/model/build core/persistence/build
```

This removes the files from Git tracking without deleting them locally. The `--cached` flag ensures developers can still build the project.

### Step 2: Update .gitignore
```bash
# Changed from multiple specific patterns to universal pattern
**/build/
```

### Step 3: Commit the Cleanup
```bash
git add .gitignore
git commit -m "Remove build outputs and enforce universal build ignore rules"
```

---

## Verification

After this cleanup:

1. **Git Status Clean:**
   ```bash
   git status
   # Should not show any build/ directories as untracked
   ```

2. **No Build Files in History:**
   ```bash
   git log --all --full-history -- "**/build/**"
   # Should only show the removal commit
   ```

3. **Local Build Still Works:**
   ```bash
   ./gradlew :core:persistence:build
   # Build outputs regenerate locally, not tracked by Git
   ```

---

## Prevention for Future PRs

All contributors and automated tooling working on the `architecture/v2-bootstrap` branch must:

1. **Before Committing:**
   - Run `git status` and verify no `build/` directories are staged
   - Review `git diff --cached` to ensure only source files
   - Use `git add` selectively for source files only

2. **PR Reviews:**
   - Reviewers must reject PRs containing build outputs
   - GitHub Actions can add a check for `**/build/**` patterns
   - Bot reviews should flag generated code

3. **IDE Configuration:**
   - Ensure `.gitignore` is respected by your IDE
   - IntelliJ: File → Project Structure → Modules → Excluded Folders
   - VS Code: Configure files.exclude for `**/build`

---

## Related Documentation

- [Gradle Build Cache](https://docs.gradle.org/current/userguide/build_cache.html)
- [Git Ignore Patterns](https://git-scm.com/docs/gitignore)
- [ObjectBox Schema Management](https://docs.objectbox.io/advanced/data-model-updates)

---

**Last Updated:** 2025-12-05  
**Applies To:** All v2 architecture branches  
**Enforcement:** Required for all future PRs

# Quality Tools Quick Reference

## Running the Enhanced Release Workflow

### Basic Release Build (No Quality Tools)
1. Go to **Actions** → **Release Build – APK**
2. Click **Run workflow**
3. Fill in:
   - Version name: `v1.0.0`
   - Version code: `1`
   - Leave all quality tool checkboxes **unchecked**
4. Click **Run workflow**

**Result**: Fast build (~10-15 min), APKs only

---

### Quick Quality Check (Recommended for Code Review)
Enable these tools:
- ✅ **run_ktlint** - Code style (30-60s)
- ✅ **run_semgrep** - Security scan (1-3 min)
- ✅ **run_android_lint** - Android issues (2-5 min)

**Result**: Moderate build (~15-25 min), basic quality insights

---

### Comprehensive Quality Check (Release Candidate)
Enable these tools:
- ✅ **run_ktlint**
- ✅ **run_kover** (if you have tests)
- ✅ **run_semgrep**
- ✅ **run_gradle_doctor**
- ✅ **run_android_lint**
- ✅ **run_r8_analysis**
- ⬜ **run_leakcanary** (leave unchecked - very slow)

**Result**: Longer build (~25-35 min), comprehensive quality data

---

### Full Quality Audit (Major Release)
Enable **ALL** tools including:
- ✅ **run_leakcanary** - Memory leak detection (~10-15 min extra)

**Result**: Very long build (~40-50 min), full quality audit

---

## Understanding the Results

### GitHub Step Summary
After the workflow completes, check the **Summary** tab for:
- 📊 Quality Tools Summary table
- ✅/⚠️ Pass/Fail indicators for each tool
- 📈 Issue counts and metrics

### Artifacts
Download the **quality-artifacts** artifact to see:
- `quality-report.txt` - Consolidated report
- `reports/ktlint/` - Style violations
- `reports/kover/` - Code coverage
- `reports/semgrep.json` - Security findings
- `reports/gradle-doctor.txt` - Build health
- `reports/android-lint/` - Lint reports
- `reports/leakcanary/` - Memory leaks
- `reports/r8/` - ProGuard mappings

---

## Tool Descriptions

### 🎨 KTLint (Code Style)
**What it does**: Checks Kotlin code style consistency  
**When it fails**: Style violations found  
**Fix**: Run `./gradlew ktlintFormat` locally  
**Blocker**: No (advisory only)

### 📊 Kover (Code Coverage)
**What it does**: Measures test coverage  
**When it fails**: Coverage generation issues  
**Fix**: Ensure tests run successfully  
**Blocker**: No (advisory only)

### 🔍 Semgrep (Security Scan)
**What it does**: Finds security vulnerabilities and code smells  
**When it fails**: ERROR-level findings detected  
**Fix**: Review findings in reports/semgrep.json  
**Blocker**: **YES** (fails on ERROR severity)

### 🏥 Gradle Doctor (Build Health)
**What it does**: Checks Gradle configuration health  
**When it fails**: Configuration issues found  
**Fix**: Follow prescriptions in report  
**Blocker**: No (advisory only)

### 🔎 Android Lint (Android Issues)
**What it does**: Detects Android-specific problems  
**When it fails**: Fatal lint errors found  
**Fix**: Review and fix issues in Android Studio  
**Blocker**: **YES** (fails on fatal issues)

### 💧 LeakCanary (Memory Leaks)
**What it does**: Detects memory leaks in app  
**When it fails**: Leaks detected or test failures  
**Fix**: Review heap dumps in reports  
**Blocker**: No (advisory only)  
**Note**: Requires emulator, adds 10-20 minutes

### 🗜️ R8 Analysis (Code Shrinking)
**What it does**: Analyzes code shrinking effectiveness  
**When it fails**: R8 disabled or build failed  
**Fix**: Ensure release build succeeds  
**Blocker**: No (informational only)

---

## Troubleshooting

### "Tool shows 0s or Unknown"
**Cause**: Tool was not enabled in workflow inputs  
**Fix**: Check the tool's checkbox when running workflow

### "Tool ran but no report"
**Cause**: Tool may have crashed or wrong path  
**Fix**: Check workflow logs for errors

### "Semgrep failed the build"
**Cause**: ERROR-level security findings detected  
**Fix**: Review semgrep.json and fix critical issues

### "LeakCanary timeout"
**Cause**: Emulator boot took too long  
**Fix**: Run again or disable LeakCanary

### "ktlintFormat doesn't fix all issues"
**Cause**: Some violations require manual fixes  
**Fix**: Review ktlint report and fix manually

---

## Best Practices

### 🎯 For Pull Requests
Enable: ktlint, semgrep, android_lint

### 🚀 For Release Candidates  
Enable: All except leakcanary

### 🏆 For Major Releases
Enable: All tools including leakcanary

### ⚡ For Quick Iterations
Enable: None (just build and test manually)

---

## Performance Tips

1. **Cache is your friend**: First run is slow, subsequent runs are faster
2. **Selective enabling**: Only enable tools you need for this build
3. **Parallel builds**: Quality tools run in parallel with build steps where possible
4. **Local testing**: Use `scripts/test-quality-tools.sh` to test locally first

---

## Version Information

All tools are pinned to latest stable versions (as of Dec 2024):
- KTLint: 12.1.2 (ktlint 1.5.0)
- Kover: 0.9.0
- Semgrep: 1.107.0
- Gradle Doctor: 0.10.0
- LeakCanary: 2.14
- Android Lint: AGP 8.6.1

---

## Need Help?

- See `QUALITY_TOOLS_FIX_SUMMARY.md` for detailed documentation
- Check workflow logs for specific error messages
- Review `quality-report.txt` in artifacts for tool output

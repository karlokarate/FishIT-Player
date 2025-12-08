> LEGACY (V1) – historical document  
> Not valid for v2. For current architecture see `V2_PORTAL.md` and `docs/v2/**`.

# Release Workflow: Before & After Comparison

## Quick Reference Card

### Workflow Inputs

| Feature | Before | After |
|---------|--------|-------|
| Version inputs | ✅ version_name, version_code | ✅ version_name, version_code |
| Release controls | ✅ create_release, prerelease | ✅ create_release, prerelease |
| ABI selection | ❌ enable_arm_v7a, enable_arm64_v8a | 🔥 **REMOVED** - Always builds both |
| Quality tools | ❌ None | ✅ **7 new toggles** |

### Build Strategy

| Aspect | Before | After |
|--------|--------|-------|
| Job strategy | Matrix (2 parallel jobs) | Single job (sequential) |
| ABIs built | Based on inputs (optional) | **Always both** arm64-v8a + armeabi-v7a |
| Build time | ~15-20 min (cold) | ~8-12 min (warm, **30-50% faster**) |
| Timeout | 60 minutes | 90 minutes (quality tools) |

### Caching

| Cache Layer | Before | After |
|-------------|--------|-------|
| Gradle | ✅ Basic (setup-java) | ✅ **Multi-layer** (wrapper + deps) |
| Maven | ❌ | ✅ **~/.m2/repository** |
| Build intermediates | ❌ | ✅ **build/intermediates** |
| Cache keys | Simple | **Smart** (based on file hashes) |

### Quality Tools

| Tool | Before | After | Purpose |
|------|--------|-------|---------|
| KTLint | ❌ | ✅ **Optional** | Code style |
| Kover | ❌ | ✅ **Optional** | Coverage |
| Semgrep | ❌ | ✅ **Optional** | SAST security |
| Gradle Doctor | ❌ | ✅ **Optional** | Build health |
| Android Lint | ❌ | ✅ **Optional** | Android issues |
| LeakCanary | ❌ | ⚠️ **Stub** | Memory leaks |
| R8 Analysis | ❌ | ✅ **Optional** | Code shrinking |

### Artifacts

| Artifact | Before | After |
|----------|--------|-------|
| APKs | 2 separate artifacts | 1 combined artifact |
| APK names | `app-release-arm64-v8a`<br>`app-release-armeabi-v7a` | `app-release-apks` |
| ProGuard mapping | 2 separate (per ABI) | 1 combined |
| Build metrics | ❌ | ✅ **build-speed-report** |
| Quality reports | ❌ | ✅ **quality-artifacts** |
| Retention | 30 days (APKs)<br>90 days (mapping) | Same + 90 days (metrics) |

### Reporting

| Report Type | Before | After |
|-------------|--------|-------|
| Build summary | Basic | ✅ **Speed Coaching Summary** |
| Quality summary | ❌ | ✅ **Quality Tools Summary** |
| Metrics JSON | ❌ | ✅ **build_speed.json** |
| quality-report.txt | ❌ | ✅ **Aggregated text report** |
| GitHub Step Summary | Basic | ✅ **Rich markdown summaries** |

## Speed Coaching Impact

### Expected Time Savings

```
First Run (Cold Cache):
├─ Checkout: ~30s
├─ Setup: ~2m
├─ Cache Miss: 0s (nothing to restore)
├─ Build: ~15m
└─ Total: ~18m

Second Run (Warm Cache):
├─ Checkout: ~30s
├─ Setup: ~1m
├─ Cache Hit: ~1m (restore)
├─ Build: ~7m (incremental)
└─ Total: ~10m

🚀 Time Saved: ~8 minutes (44% faster)
```

## Quality Tools Decision Matrix

### When to Enable Each Tool

| Tool | Enable When... | Skip When... | Time Cost |
|------|----------------|--------------|-----------|
| **KTLint** | Pre-release, code cleanup | Quick hotfix | +1-2 min |
| **Kover** | Coverage tracking needed | Build-only release | +2-3 min |
| **Semgrep** | Security audit, major release | Trusted code only | +3-5 min |
| **Gradle Doctor** | Build issues suspected | Everything works fine | +1 min |
| **Android Lint** | Pre-release, QA builds | Emergency hotfix | +2-3 min |
| **LeakCanary** | Memory leak suspected | N/A (not implemented) | N/A |
| **R8 Analysis** | Code size concerns, obfuscation review | Any time (minimal cost) | ~0 min |

### Recommended Presets

#### 🚀 **Quick Release** (Default)
All OFF - Fastest possible build
- Use for: Hotfixes, internal testing, rapid iteration
- Time: ~10-12 minutes (warm cache)

#### 🎯 **Standard Release**
Enable: KTLint + Android Lint + R8
- Use for: Regular releases, most production builds
- Time: ~15-18 minutes

#### 🔒 **Security Release**
Enable: KTLint + Semgrep + Android Lint + R8
- Use for: Security patches, external audit
- Time: ~20-25 minutes

#### 📊 **Full Audit**
Enable: All except LeakCanary
- Use for: Major releases, milestone builds, compliance
- Time: ~25-30 minutes

## Migration Checklist

### For Repository Maintainers
- [x] Remove ABI selection logic from documentation
- [ ] Update release workflow documentation
- [ ] Add quality tool usage guidelines
- [ ] Monitor cache effectiveness over first 5 runs
- [ ] Review quality reports from initial runs

### For CI/CD Scripts
- [ ] Update artifact download scripts (new names)
- [ ] Add metrics collection integration
- [ ] Update build status monitors
- [ ] Configure quality tool notifications

### For Downstream Consumers
- [ ] Update APK download URLs (if automated)
- [ ] Note: Both ABIs always available now
- [ ] Consider using quality reports for validation

## Key Metrics to Track

### Speed Metrics
- **Total CI time** - Should decrease ~30-50% after first run
- **Build time** - Core build duration (excluding setup)
- **Cache hit rate** - Monitor via GitHub Actions logs

### Quality Metrics
- **KTLint violations** - Code style consistency
- **Kover coverage** - Test coverage percentage
- **Semgrep findings** - Security vulnerabilities
- **Lint issues** - Android-specific problems
- **R8 shrinking** - Code size optimization

## Visual Workflow Comparison

### Before (Matrix Strategy)
```
Workflow Dispatch
    ↓
┌───────────────────┬───────────────────┐
│ Build (arm64-v8a) │ Build (armeabi-v7a) │ ← Parallel
│   - Checkout      │   - Checkout      │
│   - Setup         │   - Setup         │
│   - Build         │   - Build         │
│   - Upload APK    │   - Upload APK    │
│   - Upload Mapping│   - Upload Mapping│
└───────────────────┴───────────────────┘
    ↓
Create Release (combine artifacts)
```

### After (Single Job + Quality)
```
Workflow Dispatch (with quality toggles)
    ↓
Single Build Job
    ├─ Record start time
    ├─ Checkout
    ├─ 🚀 Cache Gradle wrapper
    ├─ 🚀 Cache Gradle deps
    ├─ 🚀 Cache Android build
    ├─ Setup Java/Android
    ├─ Initialize quality reports
    │
    ├─ 🔍 Quality Tool: KTLint (if enabled)
    ├─ 🔍 Quality Tool: Kover (if enabled)
    ├─ 🔍 Quality Tool: Semgrep (if enabled)
    ├─ 🔍 Quality Tool: Gradle Doctor (if enabled)
    ├─ 🔍 Quality Tool: Android Lint (if enabled)
    ├─ 🔍 Quality Tool: LeakCanary (if enabled)
    │
    ├─ Decode keystore
    ├─ Build arm64-v8a
    ├─ Build armeabi-v7a
    │
    ├─ 🔍 Quality Tool: R8 Analysis (if enabled)
    │
    ├─ Verify APKs
    ├─ Calculate checksums
    │
    ├─ ⚡ Calculate build metrics
    ├─ ⚡ Generate Speed Coaching Summary
    ├─ 🔍 Generate Quality Summary
    │
    ├─ Upload APKs (combined)
    ├─ Upload ProGuard mapping
    ├─ Upload build speed report
    └─ Upload quality artifacts (if any tools enabled)
    ↓
Create Release (if enabled)
```

## Breaking Changes

### ⚠️ Artifact Names Changed
If you have scripts downloading artifacts:

**Before:**
```bash
gh run download $RUN_ID --name app-release-arm64-v8a
gh run download $RUN_ID --name app-release-armeabi-v7a
```

**After:**
```bash
gh run download $RUN_ID --name app-release-apks
# Contains both APKs
```

### ⚠️ No More ABI Selection
The workflow inputs `enable_arm_v7a` and `enable_arm64_v8a` have been removed.
Both ABIs are **always built**.

**Migration:** Remove any logic that conditionally enables/disables ABIs.

### ✅ Backward Compatible
- Release assets still named correctly: `FishIT-Player-vX.Y.Z-{abi}.apk`
- Signing process unchanged
- Version numbering unchanged
- Release creation unchanged

## Success Indicators

After implementing these changes, you should see:

✅ **Faster builds** from run #2 onward (30-50% improvement)  
✅ **Better visibility** into build performance (metrics)  
✅ **Optional quality checks** without slowing default builds  
✅ **Consistent ABI coverage** (no more "forgot to enable arm64")  
✅ **Centralized quality reports** (single artifact)  
✅ **Rich summaries** in GitHub Actions UI  

## Support

For issues or questions:
1. Check `RELEASE_WORKFLOW_ENHANCEMENTS.md` for detailed docs
2. Review `quality-report.txt` in artifacts for tool-specific issues
3. Check `build_speed.json` for performance metrics
4. Review GitHub Actions logs for cache hit/miss information

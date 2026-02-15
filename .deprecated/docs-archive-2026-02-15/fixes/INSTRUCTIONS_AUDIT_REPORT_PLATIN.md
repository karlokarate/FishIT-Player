
# FishIT-Player v2 — PLATIN Instruction Audit Report
**Date:** 2026-01-07  
**Scope:** `.github/instructions/**` + cross-check against contracts, portal, glossary

---

## Executive Summary (TL;DR)

The instruction set is conceptually strong and close to PLATIN-grade, but **several critical issues must be fixed** to prevent silent CI failures, architectural drift, and agent misbehavior.

**Top blockers:**
1. **Broken CI/grep checks** due to dot-space typos (`infra\. transport`, `core\.persistence\. obx`, etc.).
2. **Broken references** (`AGENTS. md`, `README. md`) causing agents to read non-existent files.
3. **Ownership contradiction** (`extractSeasonEpisode` assigned to pipeline in one file, normalizer in another).
4. **Contract SSOT path inconsistency** (`/docs/v2` vs `/contracts`).
5. **Over-absolute network rule** conflicting with imaging (Coil) behavior.

Fixing P0 items moves the project from “good” to **PLATIN-robust**.

---

## Audit Methodology

Each instruction file was manually read and evaluated against PLATIN criteria:

- Unambiguous MUST/FORBIDDEN rules
- No contradictory ownership or layer rules
- Machine-checkable CI rules that actually match code
- Clear Single Source of Truth (SSOT) for contracts
- No copy/paste traps or misleading examples

Referenced documents:
- AGENTS.md
- V2_PORTAL.md
- GLOSSARY_v2_naming_and_modules.md
- MEDIA_NORMALIZATION_CONTRACT.md
- LOGGING_CONTRACT_V2.md

---

## File Inventory Audited

- _index.instructions.md
- app-work.instructions.txt
- core-catalog-sync.instructions.txt
- core-domain.instructions.txt
- core-imaging.instructions.txt
- core-model.instructions.txt
- core-normalizer.instructions.txt
- core-persistence.instructions.txt
- core-player-model.instructions.txt
- core-ui.instructions.txt
- feature-common.instructions.txt
- feature-detail.instructions.txt
- feature-settings.instructions.txt
- infra-data.instructions.txt
- infra-logging.instructions.txt
- infra-transport.instructions.txt
- infra-transport-telegram.instructions.txt
- infra-transport-xtream.instructions.txt
- infra-work.instructions.txt
- pipeline.instructions.txt
- playback.instructions.txt
- player.instructions.txt

---

## Critical Findings (PLATIN Blockers)

### CR-01: CI / grep checks are broken (dot-space typos)
**Severity:** 🔥 Critical  
**Impact:** Forbidden imports can slip through CI undetected.

Examples found:
- `infra\. transport`
- `infra\. data`
- `core\.persistence\. obx`
- `org.drinkless. td`

**Why this matters:**  
Regex patterns with spaces after dots **never match real package names**, creating a false sense of safety.

**Required Fix:**  
Global replacement to remove dot-space patterns and validate via CI.

---

### CR-02: Broken file references (`AGENTS. md`, `README. md`)
**Severity:** 🔥 Critical  
**Impact:** Agents and humans follow dead paths → wrong assumptions.

Occurrences across multiple instruction files.

**Required Fix:**  
Global replace:
- `AGENTS. md` → `AGENTS.md`
- `README. md` → `README.md`

Add CI rule to forbid `\. md` substrings.

---

### CR-03: Ownership contradiction — `extractSeasonEpisode`
**Severity:** 🔥 Critical  
**Impact:** Normalization logic risks leaking into pipelines (contract violation).

Conflict:
- Assigned to **pipeline** in `infra-transport.instructions`
- Assigned to **core-normalizer** elsewhere (correct per contract)

**Required Fix:**  
`extractSeasonEpisode` MUST belong to `core/metadata-normalizer` only.

---

### CR-04: Contract SSOT path inconsistency
**Severity:** 🔥 Critical  
**Impact:** Agents cannot reliably determine which contract is binding.

Observed:
- `/contracts/MEDIA_NORMALIZATION_CONTRACT.md`
- `/docs/v2/MEDIA_NORMALIZATION_CONTRACT.md`

**Required Fix:**  
Decide once:
- **Binding contracts → `/contracts/**`**
- Docs/guides → `/docs/v2/**`

Update all references accordingly.

---

### CR-05: Absolute network rule conflicts with imaging
**Severity:** 🔥 Critical  
**Impact:** Overly strict wording contradicts valid Coil usage.

Rule states:
> “Transport is the ONLY layer allowed to use network libraries.”

But:
- `core-imaging` uses Coil (network via OkHttp).

**Required Fix:**  
Refine rule:
- Transport = only **business/source IO**
- Imaging = explicit exception for image fetching

---

## High-Severity Findings

### HS-01: Invalid import examples (dot-space typos)
**Impact:** Copy/paste bugs and incorrect mental models.

Fix all examples:
- `org.drinkless. td` → `org.drinkless.td`
- `com.fishit.player. infra` → `com.fishit.player.infra`
- `td. TdApi` → `td.TdApi`

---

### HS-02: Logging TAG length inconsistency
**Impact:** Android log truncation risk.

Example exceeds 23 chars:
- `CatalogSyncOrchestratorWorker`

**Fix:**  
Define a standard: short stable TAG or auto-truncate helper.

---

### HS-03: Catalog sync normalization semantics unclear
**Impact:** Canonical media may be partially or never normalized.

**Fix:**  
Define invariant clearly:
- Raw always stored
- Canonical normalization either mandatory or queued with guaranteed worker

---

### HS-04: Telegram transport error semantics inconsistent
**Impact:** `null` vs exception causes ambiguous handling.

**Fix:**  
Adopt a single model:
- `Result<T>` / `Either<DomainError, T>` preferred

---

### HS-05: Destructive guidance (“clear TDLib database”)
**Impact:** Potential data loss.

**Fix:**  
Only via explicit user action (Settings), never automatic.

---

## Per-File Summary (Short)

Legend: ✅ OK | 🟡 Needs Fix | 🔴 Critical

- _index.instructions.md — 🟡 (contract path drift, versioning)
- core-model.instructions.txt — 🔴 (broken refs, contract path)
- feature-common.instructions.txt — 🔴 (broken CI regex, typos)
- infra-data.instructions.txt — 🔴 (broken refs, typos, contract path)
- infra-transport.instructions.txt — 🔴 (ownership contradiction)
- core-normalizer.instructions.txt — 🟡 (wording + contract path)
- core-imaging.instructions.txt — 🟡/🔴 (layer exception not explicit)
- app-work.instructions.txt — 🟡 (TAG length)
- playback.instructions.txt — 🟡 (broken refs)
- player.instructions.txt — 🔴 (multiple dot-space typos)
- Remaining files — mostly 🟡 to ✅ with localized fixes

---

## Priority Fix Plan

### P0 (Immediate)
1. Dot-space purge (regex & examples)
2. Fix all `AGENTS. md` / `README. md`
3. Fix `extractSeasonEpisode` ownership

### P1 (This week)
4. Contract path SSOT consolidation
5. Telegram error semantics + DB reset policy
6. Catalog sync normalization invariant

### P2 (PLATIN hardening)
7. Instruction-lint CI (markdownlint + regex rules)
8. Detekt forbidden-import + dependency rules
9. Instruction header template (version/owner/status)

---

## Recommended CI Additions

- markdownlint on `.github/instructions/**`
- Link/path checker (dead references)
- Regex guardrails:
  - forbid `\. md`
  - forbid `\.\s+[A-Za-z]`
  - forbid `/docs/v2/*CONTRACT*.md` for binding contracts

---

## Conclusion

The instruction set is **architecturally sound but operationally fragile**.  
After fixing the listed blockers, the system will be:

- Safer for agents
- Enforceable by CI
- Resistant to silent architectural decay

This is a textbook case where **small textual errors cause large systemic risk** — and therefore exactly where PLATIN discipline pays off.

---

*End of Report*

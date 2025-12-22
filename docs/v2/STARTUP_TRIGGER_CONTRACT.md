# App Startup & Sync Trigger Contract — Premium Gold

**Version:** 1.0
**Date:** 2025-12-22
**Status:** Binding
**Scope:** App startup sequencing, auto-sync triggering, warm-up vs reliable sync, phone/tablet + FireTV 32-bit

> This contract is binding. Any deviation is a bug unless explicitly documented and approved.

---

## 1) Goals

* **Fast startup** (no jank, especially on FireTV 32-bit)
* **Immediate UI clarity** (never "empty and silent")
* **Deterministic background sync** (SSOT WorkManager queue only)
* **No duplicate bootstraps** and no parallel sync paths
* **Optional sources** (Xtream/Telegram/IO independent)

---

## 2) Startup Order (MANDATORY)

### S-1 Unified logging first

`UnifiedLogInitializer.init(isDebug = BuildConfig.DEBUG)` MUST be called in `Application.onCreate()` before any other subsystem starts.

### S-2 AppShell starts observers, not heavy work

The following MAY start in `Application.onCreate()`:

* `SourceActivationObserver`
* `TelegramActivationObserver`

These MUST be **lightweight** (no full scans, no large network calls).

### S-3 Exactly one bootstrap location

Each bootstrap MUST be started in exactly one place:

* either `Application.onCreate()` **or** `MainActivity.onCreate()`
* never both.

Duplicate `.start()` calls for the same bootstrap are forbidden.

---

## 3) UI First Principles (MANDATORY)

### U-1 UI must render meaningful state immediately

On first render of HOME/LIVE:

* If no sources are active: show "Add source" actions.
* If sources active but library empty: show "Sync pending" state + passive indicator.
* UI MUST remain usable while sync runs.

### U-2 UI never performs sync work

UI MUST NOT:

* call transport
* call pipelines
* call `CatalogSyncService.sync*()` directly
  UI triggers sync only through the SSOT scheduler.

---

## 4) Sync Execution Paths (MANDATORY)

### X-1 Single SSOT background path

All reliable sync MUST run through WorkManager unique works only:

* `catalog_sync_global`
* `tmdb_enrichment_global`
* (if EPG exists) `epg_sync_global`

No other unique work names for these concerns may exist.

### X-2 Warm-up is allowed but must be bounded and non-critical

A **WarmUpMode** MAY exist to improve perceived responsiveness:

* runs only while the app is in foreground
* performs small, bounded batches only
* MUST NOT be required for correctness
* MUST NOT write large amounts in one run
* MUST NOT bypass persistence rules (still writes via repositories if it writes)

WarmUpMode MUST never replace WorkManager reliable sync.

---

## 5) Auto-Sync Trigger Policy (Premium Gold)

### T-1 Auto-sync is deferred by design

Auto-sync MUST NOT start heavy work immediately at process start.

Auto-sync MAY be triggered only by one of these events:

1. **Source activation transition** (INACTIVE → ACTIVE)
2. **User reaches a content screen** (HOME or LIVE becomes visible)
3. **Idle warm-up window** (app in foreground, user idle) with a short delay

### T-2 Mandatory delay gate (to protect startup)

When auto-sync is triggered, it MUST be scheduled with a short delay window:

* **Default delay:** 3–10 seconds (implementation chooses one constant)
* Purpose: ensure UI has stabilized and first frame is rendered.

### T-3 No auto-sync if nothing is active

If `activeSources` is empty:

* auto-sync MUST NOT enqueue any work.

### T-4 Cancel when sources become empty

If `activeSources` transitions to empty:

* `catalog_sync_global` MUST be cancelled by name.

---

## 6) Source Order & Serial Execution (MANDATORY)

### Q-1 Serial queue only

At most one catalog sync chain runs at a time.

### Q-2 Fixed source order

If multiple sources are active, catalog sync order MUST be:

1. Xtream
2. Telegram
3. IO (if active)

---

## 7) TMDB Trigger Policy (Premium Gold)

### M-1 TMDB details-first is mandatory

If typed `tmdbRef` exists for an item:

* TMDB enrichment MUST prioritize DETAILS_BY_ID to fill SSOT images fast.

### M-2 Search is fallback only

TMDB search/resolution for missing IDs is:

* lower priority than DETAILS_BY_ID
* bounded by attempt/cooldown policy

### M-3 UI image SSOT is upgrade-only

* `tmdbRef present` ≠ images ready
* Primary image selection MUST be:

  * `canonical.tmdbPosterRef ?: source.bestPosterRef ?: placeholder`
* Once `canonical.tmdbPosterRef` exists, UI MUST NOT revert to source poster automatically.

---

## 8) FireTV 32-bit Safety Rules (MANDATORY)

### F-1 No heavy work during cold start

No heavy scans may run during the cold-start window.

### F-2 Bounded memory in all background execution

All background work must:

* use bounded batches
* persist frequently
* avoid payload logging

---

## 9) Observability (MANDATORY)

### O-1 Minimal passive indicator for sync

UI MUST provide a small passive sync indicator:

* IDLE / RUNNING / SUCCESS / FAILED
* No blocking dialogs.

### O-2 UnifiedLog only

All logs MUST use UnifiedLog; no secrets.

---

## 10) Acceptance Criteria (Binding)

This contract is satisfied only if:

* App first frame renders quickly on FireTV and phone.
* No duplicate bootstraps start from multiple locations.
* Auto-sync never starts heavy work immediately at process start.
* Sync triggers only via SSOT scheduler and unique work names.
* Users can run the app with zero, one, or multiple sources.
* TMDB enrichment upgrades posters without flicker.

---

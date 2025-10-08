"""
Allowed-targets & execution guardrails shared across bots.
"""
from __future__ import annotations
import os, json, fnmatch
from pathlib import Path
from typing import Dict, Any, List, Tuple

def _load_allowed_from_plan_or_task() -> tuple[dict, dict]:
    allowed, execution = {}, {}
    # Prefer solver_task.json (new)
    ptask = Path(".github/codex/context/solver_task.json")
    if ptask.exists():
        try:
            data = json.loads(ptask.read_text(encoding="utf-8"))
            allowed = (data.get("scope") or {})
            execution = {"strict_mode": bool((data.get("scope") or {}).get("strict_mode", True)),
                         "dir_rewrite_allowed": bool((data.get("scope") or {}).get("dir_rewrite_allowed", False))}
        except Exception:
            pass
    # Fallback: solver_plan.json (legacy)
    pplan = Path(".github/codex/solver_plan.json")
    if pplan.exists():
        try:
            plan = json.loads(pplan.read_text(encoding="utf-8"))
            allowed = plan.get("allowed_targets") or allowed
            execution = plan.get("execution") or execution
        except Exception:
            pass
    # sane defaults
    if not isinstance(allowed, dict):
        allowed = {}
    for k in ("modify","create","tests"):
        if k not in allowed or not isinstance(allowed[k], list):
            allowed[k] = []
    if not isinstance(execution, dict):
        execution = {}
    execution.setdefault("strict_mode", True)
    execution.setdefault("dir_rewrite_allowed", False)
    return allowed, execution

def _match_any(path: str, globs: list[str]) -> bool:
    return any(fnmatch.fnmatch(path, g) for g in (globs or []))

def filter_paths_by_allowed(create_paths: list[str], existing_paths: list[str]) -> tuple[list[str], list[str], list[str]]:
    allowed, execution = _load_allowed_from_plan_or_task()
    rej: List[str] = []

    create_ok = [p for p in create_paths if _match_any(p, allowed.get("create"))]
    rej += [p for p in create_paths if p not in create_ok]

    existing_ok = []
    for p in existing_paths:
        if p.endswith('/') or Path(p).is_dir():
            if execution.get("dir_rewrite_allowed", False):
                existing_ok.append(p)
            else:
                rej.append(p)
            continue
        if _match_any(p, allowed.get("modify")):
            existing_ok.append(p)
        else:
            rej.append(p)

    return create_ok, existing_ok, rej

def _extract_patch_targets(patch_text: str) -> list[str]:
    import re
    if not patch_text:
        return []
    out = []
    for m in re.finditer(r"^diff --git a/(\S+)\s+b/(\S+)\s*$", patch_text, re.M):
        out.append(m.group(2))
    return out

def validate_patch_against_allowed(patch_text: str) -> tuple[bool, list[str], dict]:
    allowed, execution = _load_allowed_from_plan_or_task()
    if not (patch_text or "").strip():
        return True, [], execution
    targets = _extract_patch_targets(patch_text)
    bad = []
    for t in targets:
        if not (_match_any(t, allowed.get("modify")) or _match_any(t, allowed.get("create"))):
            bad.append(t)
    if bad and execution.get("strict_mode", False):
        return False, bad, execution
    return True, bad, execution

\
"""
solver_task.json schema and helpers.
"""
from __future__ import annotations
import json
from typing import Dict, Any, List, Optional
from pathlib import Path

SCHEMA: Dict[str, Any] = {
    "type": "object",
    "required": ["issue","problem","scope","plan","impacted_modules","language_profile"],
    "properties": {
        "issue": {
            "type": "object",
            "required": ["number","title"],
            "properties": {"number":{"type":"integer"},"title":{"type":"string"},"url":{"type":"string"}}
        },
        "problem": {
            "type": "object",
            "required": ["summary","acceptance_criteria"],
            "properties": {
                "summary": {"type":"string"},
                "root_cause": {"type":"array","items":{"type":"string"}},
                "acceptance_criteria": {"type":"array","items":{"type":"string"}}
            }
        },
        "scope": {
            "type": "object",
            "required": ["modify","create","tests","strict_mode","dir_rewrite_allowed"],
            "properties": {
                "modify": {"type":"array","items":{"type":"string"}},
                "create": {"type":"array","items":{"type":"string"}},
                "tests": {"type":"array","items":{"type":"string"}},
                "strict_mode": {"type":"boolean"},
                "dir_rewrite_allowed": {"type":"boolean"}
            }
        },
        "plan": {"type":"array","items":{
            "type":"object",
            "required":["id","title","paths","steps"],
            "properties": {
                "id":{"type":"string"},
                "title":{"type":"string"},
                "paths":{"type":"array","items":{"type":"string"}},
                "steps":{"type":"array","items":{"type":"string"}}
            }
        }},
        "impacted_modules": {"type":"array","items":{"type":"string"}},
        "language_profile": {"type":"string"},
        "build": {"type":"object"},
        "test": {"type":"object"},
        "notes": {"type":"array","items":{"type":"string"}}
    }
}

def validate(data: Dict[str, Any]) -> List[str]:
    """Lightweight validation without jsonschema dependency."""
    errs: List[str] = []
    def need(k): 
        if k not in data: errs.append(f"missing key: {k}")
    for k in ("issue","problem","scope","plan","impacted_modules","language_profile"):
        need(k)
    if "issue" in data and not isinstance(data["issue"].get("number",None), int):
        errs.append("issue.number must be integer")
    return errs

def build(issue: Dict[str, Any],
          problem_summary: str,
          acceptance_criteria: List[str],
          scope: Dict[str, Any],
          plan: List[Dict[str, Any]],
          impacted_modules: List[str],
          language_profile: str,
          build_cfg: Dict[str, Any] | None = None,
          test_cfg: Dict[str, Any] | None = None,
          notes: List[str] | None = None) -> Dict[str, Any]:
    data = {
        "issue": issue,
        "problem": {"summary": problem_summary, "acceptance_criteria": acceptance_criteria},
        "scope": {
            "modify": scope.get("modify", []),
            "create": scope.get("create", []),
            "tests": scope.get("tests", []),
            "strict_mode": bool(scope.get("strict_mode", True)),
            "dir_rewrite_allowed": bool(scope.get("dir_rewrite_allowed", False)),
        },
        "plan": plan,
        "impacted_modules": impacted_modules,
        "language_profile": language_profile,
        "build": build_cfg or {},
        "test": test_cfg or {},
        "notes": notes or [],
    }
    return data

def write(path: str | Path, data: Dict[str, Any]):
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    Path(path).write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")

"""
Shared GitHub helper utilities (requests-based) for all bots.
"""
from __future__ import annotations
import os, json, time, textwrap
from pathlib import Path
from typing import Any, Dict, List, Optional

import requests

# ---------- Core helpers ----------

def repo() -> str:
    return os.environ.get("GITHUB_REPOSITORY", "")

def event_payload() -> Dict[str, Any]:
    path = os.environ.get("GITHUB_EVENT_PATH") or ""
    try:
        if path and Path(path).exists():
            return json.loads(Path(path).read_text(encoding="utf-8"))
    except Exception:
        pass
    return {}

def evname() -> str:
    return os.environ.get("GH_EVENT_NAME") or os.environ.get("GITHUB_EVENT_NAME") or ""

# ---------- HTTP / API ----------

def _token() -> str:
    return os.environ.get("GITHUB_TOKEN") or os.environ.get("INPUT_TOKEN") or ""

def gh_api(method: str, path: str, payload: dict | None = None, raw: bool=False) -> dict | bytes:
    token = _token()
    url = f"https://api.github.com{path}"
    headers = {"Authorization": f"Bearer {token}", "Accept": "application/vnd.github+json"}
    if raw:
        r = requests.request(method, url, headers=headers, timeout=60)
        if r.status_code >= 300:
            raise RuntimeError(f"GitHub API {method} {path} failed: {r.status_code} {r.text[:500]}")
        return r.content
    r = requests.request(method, url, headers=headers, json=payload, timeout=60)
    if r.status_code >= 300:
        raise RuntimeError(f"GitHub API {method} {path} failed: {r.status_code} {r.text[:1200]}")
    try:
        return r.json()
    except Exception:
        return {}

def default_branch() -> str:
    info = gh_api("GET", f"/repos/{repo()}")
    return info.get("default_branch", "main") if isinstance(info, dict) else "main"

def list_workflows() -> List[dict]:
    info = gh_api("GET", f"/repos/{repo()}/actions/workflows")
    if isinstance(info, dict):
        return info.get("workflows", []) or []
    return info if isinstance(info, list) else []

def dispatch_workflow(workflow_filename: str, ref: str, inputs: Optional[dict] = None):
    return gh_api("POST", f"/repos/{repo()}/actions/workflows/{workflow_filename}/dispatches",
                  {"ref": ref, "inputs": inputs or {}})

def dispatch_repo_event(event_type: str, client_payload: dict):
    return gh_api("POST", f"/repos/{repo()}/dispatches",
                  {"event_type": event_type, "client_payload": client_payload})

# ---------- Issues / PRs ----------

def get_issue(num: int) -> dict:
    return gh_api("GET", f"/repos/{repo()}/issues/{num}")

def list_issue_comments(num: int) -> List[dict]:
    res = gh_api("GET", f"/repos/{repo()}/issues/{num}/comments")
    return res if isinstance(res, list) else []

def post_comment(num: int, body: str):
    gh_api("POST", f"/repos/{repo()}/issues/{num}/comments", {"body": body})

def upsert_comment_by_marker(num: int, marker: str, markdown: str):
    body = (marker + "\n" + markdown).strip()
    for c in list_issue_comments(num):
        if (c.get("body") or "").strip().startswith(marker):
            gh_api("PATCH", f"/repos/{repo()}/issues/comments/{c.get('id')}", {"body": body})
            return
    post_comment(num, body)

def add_labels(num: int, labels: List[str]):
    gh_api("POST", f"/repos/{repo()}/issues/{num}/labels", {"labels": labels})

def remove_label(num: int, label: str):
    gh_api("DELETE", f"/repos/{repo()}/issues/{num}/labels/{label}")

def get_labels(num: int) -> List[str]:
    iss = get_issue(num) or {}
    return [l.get("name","") for l in iss.get("labels", [])]

# ---------- Step summary ----------

def add_step_summary(text: str):
    path = os.environ.get("GITHUB_STEP_SUMMARY")
    if path:
        try:
            with open(path, "a", encoding="utf-8") as f:
                f.write(text + "\n")
        except Exception:
            pass

# ---------- Retry ----------
def retry(fn, tries: int = 3, delay: float = 2.0):
    last = None
    for i in range(tries):
        try:
            return fn()
        except Exception as e:
            last = e
            if i == tries - 1:
                raise
            time.sleep(delay * (i + 1))
    raise last or RuntimeError("retry exhausted")

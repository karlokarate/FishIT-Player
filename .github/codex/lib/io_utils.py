"""
Shared I/O utilities and artifact path helpers.
"""
from __future__ import annotations
import os, json, gzip, base64, hashlib, mimetypes
from pathlib import Path
from typing import Any, Dict, Tuple, Optional, List

# Defaults
DEFAULT_EXCLUDE_DIRS = {".git", ".github", ".gradle", "build", "out", ".idea", ".vscode", ".venv", "node_modules", ".dart_tool"}
WORK_DIR = os.path.abspath(".")
CTX_DIR = os.path.join(".github", "codex", "context")

ALLOWED_ATTACHMENT_HOSTS = {
    "user-images.githubusercontent.com",
    "objects.githubusercontent.com",
    "github.com",
    "raw.githubusercontent.com"
}

try:
    import chardet
    HAVE_CHARDET = True
except Exception:
    HAVE_CHARDET = False

# ---------- Encoding / Heuristics ----------

def is_probably_text(data: bytes) -> bool:
    if b"\x00" in data[:4096]:
        return False
    sample = data[:4096]
    text_chars = sum(c >= 9 and c <= 13 or (32 <= c <= 126) for c in sample)
    return (text_chars / max(1, len(sample))) > 0.85

def detect_encoding(data: bytes) -> str:
    if not HAVE_CHARDET:
        return "utf-8"
    res = chardet.detect(data)
    return (res.get("encoding") or "utf-8")

# ---------- Path helpers ----------

def safe_relpath(p: str) -> str:
    try:
        return os.path.relpath(p, WORK_DIR).replace("\\", "/")
    except Exception:
        return p.replace("\\", "/")

def ensure_dir(p: str | Path):
    Path(p).mkdir(parents=True, exist_ok=True)

def file_sha256(b: bytes) -> str:
    import hashlib
    h = hashlib.sha256(); h.update(b); return h.hexdigest()

# ---------- Read / Write ----------

def read_bytes(path: str | Path) -> bytes:
    return Path(path).read_bytes()

def write_bytes(path: str | Path, data: bytes):
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    Path(path).write_bytes(data)

def read_text(path: str | Path, encoding: str = "utf-8", errors="replace") -> str:
    return Path(path).read_text(encoding=encoding, errors=errors)

def write_text(path: str | Path, text: str, encoding: str = "utf-8"):
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    Path(path).write_text(text, encoding=encoding)

def read_json(path: str | Path) -> Any:
    return json.loads(Path(path).read_text(encoding="utf-8"))

def write_json(path: str | Path, obj: Any, indent: int = 2):
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    Path(path).write_text(json.dumps(obj, ensure_ascii=False, indent=indent), encoding="utf-8")

def gzip_write_json(path: str | Path, obj: Any):
    data = json.dumps(obj, ensure_ascii=False).encode("utf-8")
    with gzip.open(str(path), "wb") as gz:
        gz.write(data)

# ---------- Context run paths ----------

def context_paths(issue_no: int | None, run_id: str, short_sha: str) -> Dict[str, str]:
    safe_issue = f"{issue_no}" if issue_no is not None else "noissue"
    run_dir = os.path.join(CTX_DIR, f"run-{run_id}-issue-{safe_issue}-{short_sha}")
    paths = {
        "RUN_DIR": run_dir,
        "SOLVER_INPUT": os.path.join(run_dir, "solver_input.json"),
        "SOLVER_TASK": os.path.join(run_dir, "solver_task.json"),
        "SOLVER_PLAN": os.path.join(run_dir, "solver_plan.json"),
        "SUMMARY": os.path.join(run_dir, "summary.md"),
        "ATTACH_DIR": os.path.join(run_dir, "attachments"),
        "LAST_RUN": os.path.join(CTX_DIR, "last_run.json"),
    }
    for p in ("RUN_DIR","ATTACH_DIR",):
        ensure_dir(paths[p])
    ensure_dir(CTX_DIR)
    return paths

#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
ContextMap Bot (generic, Bot 1) – mit optionaler Deep-Reasoning-Phase + Run-Ordner + Profilsteuerung

- Schreibt Outputs in run-spezifischen Ordner: .github/codex/context/run-{GITHUB_RUN_ID}-issue-{#}-{$SHA7}
- Legt last_run.json im Basispfad ab, damit der Workflow gezielt den aktuellen Run verifizieren kann.
- Profilsteuerung per Issue-Befehl: /codex fast | /codex bal | /codex deep | /codex  (=> deep)
"""

import os
import re
import sys
import json
import time
import gzip
import base64
import hashlib
import logging
import mimetypes
import subprocess
from collections import Counter
from typing import List, Dict, Any, Optional, Tuple

try:
    import requests
except Exception as e:
    print("ERROR: requests not installed. Please `pip install -r .github/codex/requirements.txt`", file=sys.stderr)
    raise

try:
    import chardet
    HAVE_CHARDET = True
except Exception:
    HAVE_CHARDET = False

# ---------- Config ----------
DEFAULT_EXCLUDE_DIRS = {
    ".git", ".github", ".gradle", "build", "out", ".idea", ".vscode", ".venv", "node_modules", ".dart_tool"
}
DEFAULT_MAX_TEXT_EMBED_BYTES = int(os.getenv("CODEX_MAX_TEXT_EMBED_BYTES", "1048576"))  # 1 MiB/file
DEFAULT_MAX_BIN_BASE64_BYTES = int(os.getenv("CODEX_MAX_BIN_BASE64_BYTES", "524288"))   # 512 KiB/file
DEFAULT_ALWAYS_EMBED_REFERENCED = True

WORK_DIR = os.path.abspath(".")
CTX_DIR = os.path.join(".github", "codex", "context")
CTX_DIR_ABS = os.path.abspath(CTX_DIR)

# Run-ident
RUN_ID = os.getenv("GITHUB_RUN_ID", str(int(time.time())))
SHORT_SHA = (os.getenv("GITHUB_SHA", "")[:7] or "nosha")

# Pfade (werden nach Ermittlung der Issue-Nummer konkret gesetzt)
RUN_DIR = None
OUT_JSON = None
OUT_PLAN = None
OUT_SUMMARY = None
ATTACH_DIR = None

POST_ISSUE_COMMENT = os.getenv("CODEX_POST_ISSUE_COMMENT", "true").strip().lower() in {"1", "true", "yes"}
ADD_LABEL = os.getenv("CODEX_ADD_LABEL", "true").strip().lower() in {"1", "true", "yes"}
NOTIFY_SOLVER = os.getenv("CODEX_NOTIFY_SOLVER", "true").strip().lower() in {"1", "true", "yes"}

# Deep Reasoning
DEEP_REASONING = os.getenv("CODEX_DEEP_REASONING", "true").strip().lower() in {"1", "true", "yes"}
OPENAI_API_KEY = os.getenv("OPENAI_API_KEY", "").strip()
OPENAI_BASE_URL = (os.getenv("OPENAI_BASE_URL") or "https://api.openai.com").rstrip("/")
REASONING_MODEL = os.getenv("CODEX_REASONING_MODEL", "gpt-5").strip()
REASONING_EFFORT = os.getenv("OPENAI_REASONING_EFFORT", "high").strip()
MAX_FILES_FOR_REASONER = int(os.getenv("CODEX_MAX_FILES_FOR_REASONER", "25"))
MAX_BYTES_PER_FILE_FOR_REASONER = int(os.getenv("CODEX_MAX_BYTES_PER_FILE_FOR_REASONER", "100000"))

ISSUE_NUMBER_OVERRIDE = os.getenv("CODEX_ISSUE_NUMBER", "").strip()

ALLOWED_ATTACHMENT_HOSTS = {
    "user-images.githubusercontent.com",
    "objects.githubusercontent.com",
    "github.com",
    "raw.githubusercontent.com"
}

logging.basicConfig(level=logging.INFO, format="%(levelname)s: %(message)s")

# --- Profile per Issue command (/codex fast|bal|deep) -------------------------
def _detect_codex_profile(text: str) -> Optional[str]:
    if not text:
        return None
    t = re.sub(r"\s+", " ", text.lower()).strip()
    for m in re.finditer(r"/codex(?:\s+\w+)?", t):
        cmd = m.group(0)
        if " fast" in cmd:
            return "fast"
        if " bal" in cmd or " balanced" in cmd:
            return "balanced"
        return "deep"
    return None

def _apply_profile_overrides(profile: str) -> Tuple[int, int, str]:
    if profile == "fast":
        return (20, 120_000, "medium")
    if profile == "balanced":
        return (50, 250_000, "high")
    return (100, 500_000, "high")

# ---------- Utilities ----------
def run(cmd: List[str], cwd: Optional[str] = None) -> Tuple[int, str, str]:
    p = subprocess.Popen(cmd, cwd=cwd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    out, err = p.communicate()
    return p.returncode, out, err

def git_ls_files() -> List[str]:
    code, out, err = run(["git", "ls-files"])
    if code != 0:
        logging.warning("git ls-files failed: %s", err.strip())
        return []
    return [line.strip() for line in out.splitlines() if line.strip()]

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

def read_file_bytes(path: str) -> bytes:
    with open(path, "rb") as f:
        return f.read()

def safe_relpath(p: str) -> str:
    try:
        return os.path.relpath(p, WORK_DIR).replace("\\", "/")
    except Exception:
        return p.replace("\\", "/")

def file_sha256(b: bytes) -> str:
    h = hashlib.sha256(); h.update(b); return h.hexdigest()

def ensure_dirs():
    os.makedirs(CTX_DIR, exist_ok=True)

# ---------- Event / API ----------
def load_event_payload() -> Dict[str, Any]:
    p = os.getenv("GITHUB_EVENT_PATH")
    if not p or not os.path.exists(p):
        logging.warning("GITHUB_EVENT_PATH not set or not found.")
        return {}
    with open(p, "r", encoding="utf-8") as f:
        return json.load(f)

def get_repo_env() -> Tuple[str, str]:
    return os.getenv("GITHUB_REPOSITORY", ""), os.getenv("GITHUB_TOKEN", "")

def github_api_get(url: str, token: str, accept: str = "application/vnd.github+json") -> requests.Response:
    headers = {"Accept": accept}
    if token: headers["Authorization"] = f"Bearer {token}"
    r = requests.get(url, headers=headers, allow_redirects=True, timeout=60)
    r.raise_for_status()
    return r

def github_api_post_json(url: str, token: str, payload: dict) -> Optional["requests.Response"]:
    headers = {"Accept": "application/vnd.github+json", "Content-Type": "application/json"}
    if token: headers["Authorization"] = f"Bearer {token}"
    try:
        return requests.post(url, headers=headers, json=payload, timeout=60)
    except Exception as e:
        logging.warning("HTTP POST failed to %s: %s", url, e)
        return None

def post_issue_comment(issue_number: Optional[int], repo: str, token: str, body: str):
    if not (issue_number and repo and token and body): return
    url = f"https://api.github.com/repos/{repo}/issues/{issue_number}/comments"
    r = github_api_post_json(url, token, {"body": body})
    if r is None or getattr(r, "status_code", 599) >= 300:
        logging.warning("Failed to post issue comment.")

def add_contextmap_ready_label(issue_number: Optional[int], repo: str, token: str):
    if not (issue_number and repo and token and ADD_LABEL and issue_number): return
    url = f"https://api.github.com/repos/{repo}/issues/{issue_number}/labels"
    r = github_api_post_json(url, token, {"labels": ["contextmap-ready"]})
    if r is None or getattr(r, "status_code", 599) >= 300:
        logging.warning("Adding 'contextmap-ready' failed.")

def notify_solverbot(repo: str, token: str, payload: Dict[str, Any]):
    if not (NOTIFY_SOLVER and repo and token): return
    url = f"https://api.github.com/repos/{repo}/dispatches"
    event = {"event_type": "codex-solver-context-ready", "client_payload": payload}
    r = github_api_post_json(url, token, event)
    if r is None or getattr(r, "status_code", 599) >= 300:
        logging.warning("repository_dispatch failed.")

# ---------- References / Attachments ----------
PATH_PATTERN = re.compile(r"""(?P<path>
    (?:[A-Za-z0-9_\-./ ]+/)*[A-Za-z0-9_\-./ ]+\.(?:kt|java|kts|gradle|xml|md|txt|yml|yaml|json|tf|properties|py|sh|bat|ps1|tl|proto|cc|cpp|hpp|c|h|dart|swift|go|rs|ini|cfg|csv|ts|tsx|jsx|html|css|scss|sql)
)""", re.VERBOSE)
URL_PATTERN = re.compile(r"""https?://[^\s)>'"]+""")

def extract_paths_and_urls(text: str) -> Tuple[List[str], List[str]]:
    text = text or ""
    paths, urls = set(), set()
    for m in re.finditer(r"^\+\+\+ b/([^\n\r]+)$", text, re.MULTILINE): paths.add(m.group(1).strip())
    for m in re.finditer(r"^--- a/([^\n\r]+)$", text, re.MULTILINE): paths.add(m.group(1).strip())
    for m in PATH_PATTERN.finditer(text): paths.add(m.group("path").strip())
    for m in URL_PATTERN.finditer(text): urls.add(m.group(0).strip())
    return sorted(paths), sorted(urls)

def _host_allowed(url: str) -> bool:
    try:
        from urllib.parse import urlparse
        host = urlparse(url).netloc.lower()
        return any(h in host for h in ALLOWED_ATTACHMENT_HOSTS)
    except Exception:
        return False

def download_attachment(url: str, token: str) -> Dict[str, Any]:
    meta = {"url": url, "saved_as": None, "status": "error", "size": 0, "sha256": None,
            "is_text": None, "encoding": None}
    if not _host_allowed(url):
        meta["status"] = "external-reference"; return meta
    try:
        r = github_api_get(url, token, accept="application/octet-stream")
    except Exception:
        headers = {"Authorization": f"Bearer {token}"} if token else {}
        try:
            import requests as _rq
            r = _rq.get(url, headers=headers, allow_redirects=True, timeout=60)
            r.raise_for_status()
        except Exception as e2:
            meta["status"] = "error"
            meta["error"] = str(e2)
            return meta
    data = r.content
    sha = file_sha256(data)
    meta["sha256"] = sha
    meta["size"] = len(data)
    name = url.split("/")[-1]
    if not name or "." not in name:
        cd = r.headers.get("Content-Disposition", ""); mt = r.headers.get("Content-Type", "")
        if "filename=" in cd: name = cd.split("filename=")[-1].strip('"; ')
        else:
            ext = mimetypes.guess_extension(mt or "") or ".bin"
            name = f"attachment_{sha[:8]}{ext}"
    os.makedirs(ATTACH_DIR, exist_ok=True)
    save_path = os.path.join(ATTACH_DIR, name)
    with open(save_path, "wb") as f: f.write(data)
    meta["saved_as"] = save_path.replace("\\", "/")
    if is_probably_text(data):
        enc = detect_encoding(data)
        meta["is_text"] = True; meta["encoding"] = enc
        meta["text"] = data.decode(enc, errors="replace")
    else:
        meta["is_text"] = False
        meta["base64"] = base64.b64encode(data).decode("ascii")
    meta["status"] = "ok"; return meta

# ---------- Repo scan ----------
def list_all_files() -> List[str]:
    files = []
    for root, dirs, fnames in os.walk(WORK_DIR):
        for d in list(dirs):
            absd = os.path.abspath(os.path.join(root, d))
            if absd == CTX_DIR_ABS or absd.startswith(CTX_DIR_ABS + os.sep): dirs.remove(d); continue
            if d in DEFAULT_EXCLUDE_DIRS and absd != os.path.abspath(".github"): dirs.remove(d)
        for fn in fnames:
            p = os.path.join(root, fn); absp = os.path.abspath(p)
            if absp == CTX_DIR_ABS or absp.startswith(CTX_DIR_ABS + os.sep): continue
            rel = safe_relpath(p)
            if rel.startswith(".git/"): continue
            files.append(rel)
    return sorted(set(files))

def read_repo_file(path: str, always_embed: bool=False, tracked_set: Optional[set]=None) -> Dict[str, Any]:
    info = {"path": path, "size": None, "sha1": None, "sha256": None, "is_tracked": None,
            "is_text": None, "encoding": None, "lfs_pointer": False, "submodule_hint": False}
    abspath = os.path.abspath(path)
    try:
        b = read_file_bytes(abspath)
    except Exception as e:
        info["error"] = f"read_error: {type(e).__name__}: {e}"; return info
    info["size"] = len(b)
    info["sha1"] = hashlib.sha1(b).hexdigest()
    info["sha256"] = file_sha256(b)
    if tracked_set is None: tracked_set = set(git_ls_files())
    info["is_tracked"] = path in tracked_set
    head = b[:200].decode("utf-8", errors="ignore")
    if head.startswith("version https://git-lfs.github.com/spec/v1") and "git-lfs.github.com/spec/v1" in head:
        info["lfs_pointer"] = True
    if path == ".gitmodules": info["submodule_hint"] = True

    text_like_ext = path.endswith((
        ".gradle",".kts",".xml",".md",".txt",".json",".yml",".yaml",".kt",".java",
        ".py",".sh",".bat",".ps1",".properties",".cfg",".ini",".csv",".proto",".tl",
        ".go",".rs",".swift",".dart",".html",".css",".scss",".ts",".tsx",".jsx",
        ".c",".h",".cc",".cpp",".hpp",".sql"
    ))
    if is_probably_text(b) or text_like_ext:
        info["is_text"] = True
        enc = detect_encoding(b); txt = b.decode(enc, errors="replace"); info["encoding"] = enc
        if always_embed or len(b) <= DEFAULT_MAX_TEXT_EMBED_BYTES:
            info["text"] = txt
        else:
            chunk_size = int(os.getenv("CODEX_CHUNK_SIZE_BYTES", str(256*1024)))
            info["chunks"] = [{"index": i // chunk_size, "text": txt[i:i+chunk_size]} for i in range(0, len(txt), chunk_size)]
    else:
        info["is_text"] = False
        if always_embed or len(b) <= DEFAULT_MAX_BIN_BASE64_BYTES:
            info["base64"] = base64.b64encode(b).decode("ascii")
        else:
            info["binary_summary"] = {"note": "Binary file too large to embed; saved on runner workspace only.", "size": len(b)}
    return info

# ---------- Generic analysis ----------
STOPWORDS = {
    "the","and","for","with","that","this","from","are","was","were","will","would","you","your","have","has","had",
    "ein","eine","einer","eines","einem","einen","der","die","das","und","oder","aber","nicht","kein","keine","den","dem",
    "ist","sind","war","waren","wird","würde","wurde","zu","zum","zur","auf","im","in","am","beim","vom","von","mit","ohne",
    "einfach","problem","issue","bug","fix","fehler","error","fail","failing","läuft","laufen","run","running","open","opened"
}

def extract_keywords(text: str, limit: int = 15) -> List[str]:
    text = (text or "").lower()
    words = re.findall(r"[a-z0-9_][a-z0-9_\-]{2,}", text, flags=re.IGNORECASE)
    counts = Counter(w for w in words if w not in STOPWORDS)
    return [w for w, _ in counts.most_common(limit)]

def language_stats(files: List[str]) -> Dict[str, float]:
    exts = {}
    for p in files:
        ext = os.path.splitext(p)[1].lower().lstrip(".") or "(none)"
        exts.setdefault(ext, 0)
        try: exts[ext] += os.path.getsize(p)
        except Exception: pass
    return {k: round(v/1024, 2) for k, v in sorted(exts.items(), key=lambda kv: kv[1], reverse=True)}

def detect_stack_files(files: List[str]) -> Dict[str, List[str]]:
    patterns = {
        "python": ["requirements.txt", "pyproject.toml", "setup.py", "Pipfile", "poetry.lock"],
        "nodejs": ["package.json", "pnpm-lock.yaml", "yarn.lock", "package-lock.json", "tsconfig.json"],
        "java_gradle": ["build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts", "gradlew", "gradlew.bat"],
        "java_maven": ["pom.xml", "mvnw", "mvnw.cmd"],
        "android": ["AndroidManifest.xml", "gradle.properties"],
        "ios_swiftpm": ["Package.swift"],
        "ios_cocoapods": ["Podfile"],
        "rust": ["Cargo.toml", "Cargo.lock"],
        "go": ["go.mod", "go.sum"],
        "php": ["composer.json", "composer.lock"],
        "ruby": ["Gemfile", "Gemfile.lock"],
        ".net": [".csproj", ".fsproj", ".vbproj", ".sln", "global.json", "Directory.Build.props", "Directory.Build.targets"],
        "docker": ["Dockerfile", "docker-compose.yml", "compose.yml"],
        "k8s": ["k8s/", "kubernetes/", "manifests/"],
        "terraform": [".tf", ".tf.json"],
        "bazel": ["WORKSPACE", "BUILD", "BUILD.bazel"],
        "cmake": ["CMakeLists.txt"],
        "flutter": ["pubspec.yaml"],
        "swift": ["Project.swift"],
        "ios_project": [".xcodeproj", ".xcworkspace"],
        "make": ["Makefile"],
    }
    found: Dict[str, List[str]] = {k: [] for k in patterns}
    for p in files:
        lp = p.lower(); bn = os.path.basename(lp)
        for key, pats in patterns.items():
            for pat in pats:
                if pat.startswith(".") and bn.endswith(pat): found[key].append(p); break
                elif pat.endswith("/") and pat[:-1] in lp:  found[key].append(p); break
                else:
                    if bn == pat or lp.endswith("/" + pat) or bn.endswith(pat): found[key].append(p); break
    return {k: sorted(set(v)) for k, v in found.items() if v}

def largest_files(files: List[str], n: int = 15) -> List[Dict[str, Any]]:
    sizes = []
    for p in files:
        try: sizes.append((p, os.path.getsize(p)))
        except Exception: pass
    sizes.sort(key=lambda t: t[1], reverse=True)
    return [{"path": p, "bytes": s} for p, s in sizes[:n]]

def top_dirs_by_count(files: List[str], n: int = 10) -> List[Dict[str, Any]]:
    from collections import Counter as C
    cnt = C(os.path.dirname(p) or "." for p in files)
    most = cnt.most_common(n)
    return [{"dir": d, "files": c} for d, c in most]

# ---------- Issue parsing & candidates ----------
HEADER_PATTERNS = [
    (r"(?im)^#{1,6}\s*expected(?:\s+behavior)?|^erwartetes\s+verhalten\s*:?", "expected"),
    (r"(?im)^#{1,6}\s*actual(?:\s+behavior)?|^tatsächliches\s+verhalten\s*:?|^ist\s*:?", "actual"),
    (r"(?im)^#{1,6}\s*steps(?:\s+to\s+repro(duce)?)?|^schritte\s*(zur|zum)\s*reproduktion|^repro\s*steps\s*:?", "steps"),
    (r"(?im)^#{1,6}\s*environment|^umgebung\s*:?", "env"),
]

def segment_sections(body: str) -> Dict[str, str]:
    body = body or ""
    sections = {"expected":"", "actual":"", "steps":"", "env":""}
    indices = []
    for pat, key in HEADER_PATTERNS:
        for m in re.finditer(pat, body): indices.append((m.start(), key))
    indices.sort()
    if not indices: return sections
    indices.append((len(body), "_end"))
    for i in range(len(indices)-1):
        start, key = indices[i]; end = indices[i+1][0]
        start_line = body.rfind("\n", 0, start) + 1
        sec_text = body[start_line:end].strip()
        sec_text = re.sub(r"(?im)^#{1,6}\s*[^\n]*\n?", "", sec_text, count=1).strip()
        if key in sections and sec_text: sections[key] = sec_text
    return sections

def extract_signals(body: str) -> Dict[str, List[str]]:
    lines = (body or "").splitlines()
    errors, traces, codeblocks = [], [], []
    for m in re.finditer(r"```(?:[a-zA-Z0-9_\-]+)?\n(.*?)\n```", body or "", flags=re.DOTALL):
        codeblocks.append(m.group(1).strip())
    for ln in lines:
        l = ln.strip()
        if re.search(r"\b(exception|traceback|stacktrace|error|fatal|panic)\b", l, re.IGNORECASE):
            errors.append(l)
        if l.startswith("at ") or l.startswith("File ") or re.match(r".*\(\w+\.\w+:\d+\)", l):
            traces.append(l)
    return {"errors": errors[:20], "traces": traces[:50], "codeblocks": codeblocks[:5]}

def score_file_for_keywords(path: str, keywords: List[str]) -> int:
    lp = path.lower(); base = os.path.basename(lp); segments = set(lp.split("/"))
    score = 0
    for k in keywords:
        if k in base: score += 6
        if k in lp: score += 2
        if k in segments: score += 3
    if any(s in lp for s in ("/src/", "/lib/", "/app/", "/core/", "/common/", "/server/", "/client/")): score += 2
    return score

def pick_candidate_files(files: List[str], keywords: List[str], limit: int = 30) -> List[str]:
    scored = [(score_file_for_keywords(p, keywords), p) for p in files]
    scored.sort(key=lambda t: (t[0], -len(t[1])), reverse=True)
    return [p for s, p in scored if s > 0][:limit]

def suggest_new_files(keywords: List[str], stack: Dict[str, List[str]], top_langs: List[str], issue_no: Optional[int]) -> List[str]:
    base = []
    key = keywords[0] if keywords else "feature"
    n = issue_no or int(time.time())
    base += [f"tests/{key}_spec.md", f"docs/issue-{n}-decision-record.md"]
    if "python" in stack or "py" in top_langs: base.append(f"tests/test_{key}.py")
    if "nodejs" in stack or "ts" in top_langs or "js" in top_langs: base.append(f"tests/{key}.spec.ts")
    if "java_gradle" in stack or "android" in stack: base.append(f"{key}/src/test/java/.../{key.capitalize()}Test.java")
    if ".net" in stack: base.append(f"tests/{key}.Tests.cs")
    if "go" in stack or "go" in top_langs: base.append(f"{key}/{key}_test.go")
    return sorted(set(base))

# ---------- Reasoning integration ----------
def prepare_reasoner_bundle(issue: Dict[str, Any], analysis: Dict[str, Any],
                            file_index: Dict[str, Any], candidates: List[str],
                            max_files: int, max_bytes: int) -> Dict[str, Any]:
    sel = []
    for p in candidates[:max_files]:
        info = file_index.get(p) or {}
        content = ""
        if info.get("is_text"):
            if "text" in info:
                content = info["text"][:max_bytes]
            elif "chunks" in info and info["chunks"]:
                content = "".join(ch["text"] for ch in info["chunks"])[:max_bytes]
        sel.append({"path": p, "content": content})
    bundle = {
        "issue": {
            "number": issue.get("number"),
            "title": issue.get("title"),
            "body": issue.get("body"),
            "sections": analysis.get("issue_sections"),
            "keywords": analysis.get("keywords"),
            "signals": analysis.get("issue_signals"),
        },
        "repo_profile": {
            "languages_kb": analysis.get("language_bytes_kb"),
            "stack_indicators": analysis.get("stack_indicators"),
            "top_dirs_by_file_count": analysis.get("top_dirs_by_file_count"),
            "largest_files": analysis.get("largest_files")[:8],
        },
        "candidates": candidates[:max_files],
        "file_samples": sel
    }
    return bundle

def call_reasoner_openai(system_prompt: str, bundle: Dict[str, Any]) -> Optional[Dict[str, Any]]:
    """
    Ruft das OpenAI-API auf, um auf Basis des vom ContextMap-Bot erstellten Bundles
    (Issue + Repoanalyse) eine tiefe JSON-Analyse zu erzeugen.

    - Unterstützt sowohl klassische Chat-Completions-Modelle als auch Reasoning-Modelle.
    - Entfernt inkompatible Parameter wie 'reasoning' oder 'temperature', wenn das Modell sie nicht akzeptiert.
    - Gibt ein strukturiertes JSON zurück (Problem-Breakdown, Ursachen, Actions, Tests etc.)
    """

    if not OPENAI_API_KEY:
        logging.info("No OPENAI_API_KEY provided; skipping deep reasoning.")
        return None

    url = f"{OPENAI_BASE_URL}/v1/chat/completions"
    headers = {
        "Content-Type": "application/json",
        "Authorization": f"Bearer {OPENAI_API_KEY}"
    }

    # --- System- und User-Prompts ---
    user_prompt = (
        "You will receive a JSON bundle with issue/context and selected file contents.\n"
        "Produce a STRICT JSON object with these keys:\n"
        "{\n"
        "  \"problem_breakdown\": [string...],\n"
        "  \"root_cause_hypotheses\": [string...],\n"
        "  \"affected_areas\": [{\"path\": string, \"reason\": string}],\n"
        "  \"concrete_actions\": [ {\"title\": string, \"steps\": [string...] } ],\n"
        "  \"tests_to_add\": [string...],\n"
        "  \"risks\": [string...],\n"
        "  \"migration_notes\": [string...]\n"
        "}\n"
        "Do NOT add commentary outside JSON. Be precise and actionable.\n"
        f"Reasoning effort level: {REASONING_EFFORT.upper()}.\n"
    )

    # --- Basisdaten ohne riskante Felder ---
    data = {
        "model": REASONING_MODEL,
        "messages": [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_prompt},
            {"role": "user", "content": json.dumps(bundle, ensure_ascii=False)},
        ],
    }

    # Nur hinzufügen, wenn Modell "reasoning" ausdrücklich unterstützt
    if re.search(r"reasoning", REASONING_MODEL, re.I):
        data["reasoning"] = {"effort": REASONING_EFFORT}
    else:
        # Für klassische Modelle keine Temperaturabweichung setzen → Default (1)
        # Das vermeidet "Unsupported value: 'temperature'" Fehler
        pass

    try:
        resp = requests.post(url, headers=headers, json=data, timeout=180)
        if resp.status_code >= 300:
            logging.warning("Reasoner HTTP error %s: %s",
                            resp.status_code, resp.text[:400])
            return None

        payload = resp.json()
        content = payload["choices"][0]["message"]["content"]

        # JSON innerhalb von Markdown-Backticks extrahieren (```json ... ```)
        m = re.search(r"```(?:json)?\s*(\{.*\})\s*```", content, flags=re.DOTALL)
        raw = m.group(1) if m else content

        try:
            return json.loads(raw)
        except json.JSONDecodeError as je:
            logging.warning("Reasoner returned non-JSON content: %s", str(je))
            return None

    except Exception as e:
        logging.warning("Reasoner call failed: %s", e)
        return None

# ---------- Main ----------
def main():
    ensure_dirs()

    event = load_event_payload()
    repo, token = get_repo_env()

    evname = os.getenv("GITHUB_EVENT_NAME", "")
    ictx = {"event_name": evname, "issue_number": None, "title": "", "body": ""}
    if evname == "issues":
        issue = event.get("issue") or {}
        ictx["issue_number"] = issue.get("number")
        ictx["title"] = issue.get("title", "") or ""
        ictx["body"] = issue.get("body", "") or ""
    elif evname == "issue_comment":
        issue = event.get("issue", {}) or {}
        comment = event.get("comment", {}) or {}
        ictx["issue_number"] = issue.get("number")
        ictx["title"] = issue.get("title", "") or ""
        ictx["body"] = comment.get("body", "") or ""
    else:
        inputs = event.get("inputs", {}) or {}
        ictx["title"] = inputs.get("title", "") or ""
        ictx["body"] = inputs.get("body", "") or ""

    body = ictx["body"]; title = ictx["title"]; issue_no = ictx.get("issue_number")

    # Override für Issue-Nummer (z. B. aus Workflow)
    if ISSUE_NUMBER_OVERRIDE:
        try:
            issue_no = int(ISSUE_NUMBER_OVERRIDE)
        except ValueError:
            logging.warning("CODEX_ISSUE_NUMBER='%s' is not numeric; ignoring.", ISSUE_NUMBER_OVERRIDE)

    if issue_no is None and os.getenv("GITHUB_EVENT_NAME","") in ("issues","issue_comment"):
        m = re.search(r"(?:^|\s)#(\d+)\b", (title + " " + body))
        if m:
            issue_no = int(m.group(1))
            logging.info("Issue number parsed from text: #%s", issue_no)

    # --- Run-spezifische Pfade initialisieren ---
    global RUN_DIR, OUT_JSON, OUT_PLAN, OUT_SUMMARY, ATTACH_DIR
    safe_issue = f"{issue_no}" if issue_no is not None else "noissue"
    RUN_DIR = os.path.join(CTX_DIR, f"run-{RUN_ID}-issue-{safe_issue}-{SHORT_SHA}")
    os.makedirs(RUN_DIR, exist_ok=True)
    OUT_JSON = os.path.join(RUN_DIR, "solver_input.json")
    OUT_PLAN = os.path.join(RUN_DIR, "solver_plan.json")
    OUT_SUMMARY = os.path.join(RUN_DIR, "summary.txt")
    ATTACH_DIR = os.path.join(RUN_DIR, "attachments")
    os.makedirs(ATTACH_DIR, exist_ok=True)
    with open(os.path.join(CTX_DIR, "last_run.json"), "w", encoding="utf-8") as fh:
        json.dump(
            {"run_id": RUN_ID, "issue_number": issue_no, "short_sha": SHORT_SHA,
             "run_dir": RUN_DIR.replace("\\", "/"),
             "context": "solver_input.json", "plan": "solver_plan.json"},
            fh, ensure_ascii=False, indent=2
        )

    # --- Profil per Issue-Befehl (/codex ...) anwenden ---
    selected_profile = _detect_codex_profile((title or "") + "\n" + (body or ""))
    if selected_profile:
        pf_files, pf_bytes, pf_effort = _apply_profile_overrides(selected_profile)
        global MAX_FILES_FOR_REASONER, MAX_BYTES_PER_FILE_FOR_REASONER, REASONING_EFFORT, DEEP_REASONING
        MAX_FILES_FOR_REASONER = pf_files
        MAX_BYTES_PER_FILE_FOR_REASONER = pf_bytes
        REASONING_EFFORT = pf_effort
        DEEP_REASONING = True  # Profile implizieren Deep-Analyse
        logging.info("Codex profile from issue: %s (files=%s, bytes/file=%s, effort=%s)",
                     selected_profile, MAX_FILES_FOR_REASONER, MAX_BYTES_PER_FILE_FOR_REASONER, REASONING_EFFORT)

    # References & attachments
    ref_paths, ref_urls = extract_paths_and_urls(body)
    attachments = []
    for u in ref_urls:
        if _host_allowed(u):
            logging.info("Downloading attachment: %s", u)
            attachments.append(download_attachment(u, token))
        else:
            attachments.append({"url": u, "status": "external-reference"})

    # Repo scan
    allfiles = list_all_files()
    tracked_set = set(git_ls_files())

    file_index: Dict[str, Any] = {}
    total_bytes_embedded = 0
    for p in allfiles:
        always_embed = DEFAULT_ALWAYS_EMBED_REFERENCED and (p in ref_paths)
        info = read_repo_file(p, always_embed=always_embed, tracked_set=tracked_set)
        file_index[p] = info
        if info.get("is_text") and "text" in info:
            total_bytes_embedded += len(info["text"].encode("utf-8", errors="replace"))
        elif info.get("is_text") and "chunks" in info:
            total_bytes_embedded += sum(len(ch["text"].encode("utf-8", errors="replace")) for ch in info["chunks"])
        elif not info.get("is_text") and "base64" in info:
            total_bytes_embedded += len(info["base64"])

    # Attachments virtuell einhängen
    for att in attachments:
        if att.get("status") == "ok":
            vpath = f"__attachments__/{os.path.basename(att['saved_as'])}"
            idx = {"path": vpath, "size": att.get("size"), "sha256": att.get("sha256"),
                   "is_text": att.get("is_text"), "encoding": att.get("encoding")}
            if att.get("is_text"): idx["text"] = att.get("text", "")
            else: idx["base64"] = att.get("base64", "")
            file_index[vpath] = idx

    # Analyse
    try:
        repo_size_kb = round(sum(os.path.getsize(p) for p in allfiles if os.path.exists(p))/1024, 2)
    except Exception:
        repo_size_kb = None

    lang_stats = language_stats(allfiles)
    stack = detect_stack_files(allfiles)
    largest = largest_files(allfiles, 15)
    topdirs = top_dirs_by_count(allfiles, 10)

    sections = segment_sections(body)
    signals = extract_signals(body)
    keywords = extract_keywords(title + " " + body, limit=15)

    candidates = pick_candidate_files(allfiles, keywords, limit=30)
    top_lang_keys = [k for k, _ in list(lang_stats.items())[:5]]
    new_files = suggest_new_files(keywords, stack, top_lang_keys, issue_no)

    analysis = {
        "language_bytes_kb": lang_stats,
        "stack_indicators": stack,
        "largest_files": largest,
        "top_dirs_by_file_count": topdirs,
        "repo_size_kb": repo_size_kb,
        "file_count": len(allfiles),
        "issue_sections": sections,
        "issue_signals": signals,
        "keywords": keywords,
        "candidate_files": candidates,
        "suggested_new_files": new_files,
    }

    # -------- Kontext JSON --------
    context_out = {
        "meta": {
            "generated_at": time.strftime("%Y-%m-%d %H:%M:%S %z"),
            "repo": repo,
            "event_name": os.getenv("GITHUB_EVENT_NAME", ""),
            "ref": os.getenv("GITHUB_REF", ""),
            "sha": os.getenv("GITHUB_SHA", ""),
            "runner_os": os.getenv("RUNNER_OS", ""),
            "max_text_embed_bytes": DEFAULT_MAX_TEXT_EMBED_BYTES,
            "max_bin_base64_bytes": DEFAULT_MAX_BIN_BASE64_BYTES,
            "total_bytes_embedded_estimate": total_bytes_embedded,
        },
        "issue_context": {
            "number": issue_no,
            "title": title,
            "body": body,
            "referenced_paths": ref_paths,
            "referenced_urls": ref_urls,
        },
        "attachments": attachments,
        "repo": {"files": allfiles, "file_index": file_index},
        "analysis": analysis,
    }
    with open(OUT_JSON, "w", encoding="utf-8") as f:
        json.dump(context_out, f, ensure_ascii=False)
    with gzip.open(OUT_JSON + ".gz", "wb") as gz:
        gz.write(json.dumps(context_out, ensure_ascii=False).encode("utf-8"))

    # -------- Solver-Fahrplan (baseline) --------
    plan = {
        "issue": {
            "number": issue_no,
            "title": title,
            "summary": (body or "").strip()[:1200],
            "sections": sections,
            "keywords": keywords,
            "signals": signals,
        },
        "repo_profile": {
            "languages_kb": lang_stats,
            "stack_indicators": stack,
            "top_dirs": topdirs,
            "size_kb": repo_size_kb,
            "file_count": len(allfiles)
        },
        "scope_hypothesis": {
            "likely_affected_files": candidates,
            "suggested_new_files": new_files
        },
        "proposed_workplan": {
            "phase_1_understanding": [
                "Reproduktion gemäß Issue-Steps; falls fehlen, Minimal-Repro entwerfen.",
                "Kandidaten-Dateien querlesen; Laufzeitpfad grob skizzieren.",
                "Vorhandene Tests/CI prüfen; lokale Build-/Testbefehle festhalten."
            ],
            "phase_2_change_design": [
                "Konkrete Änderungspunkte pro Kandidaten-Datei (API, Logik, I/O, UI) definieren.",
                "Fehlende Strukturen anlegen (siehe suggested_new_files).",
                "Abwärtskompatibilität/Side-Effects bewerten; Feature-Flags/Migrationspfade festlegen."
            ],
            "phase_3_implementation": [
                "Änderungen iterativ umsetzen; kleinteilige Commits mit sauberen Messages.",
                "Unit-/Integrationstests ergänzen/aktualisieren.",
                "Logging/Telemetry an heiklen Stellen ergänzen."
            ],
            "phase_4_verification": [
                "Lokal Tests + Linter/Formatter ausführen.",
                "Repro-Szenario erneut durchspielen; Edge-Cases prüfen.",
                "Review-Checkliste/Changelog erstellen."
            ],
            "phase_5_delivery": [
                "PR mit Problemursache, Lösung und Risiken.",
                "Akzeptanzkriterien gegenprüfen."
            ]
        },
        "acceptance_criteria": [
            "Repro aus dem Issue verläuft fehlerfrei.",
            "CI grün, keine Regression.",
            "Neue/angepasste Tests decken Änderungen ab.",
            "Dokumentation/Changelog aktualisiert."
        ],
        "ci_build_hints": [
            "Abhängigkeiten aus Stack-Indikatoren ableiten (Node/Python/Gradle etc.).",
            "Format/Lint Hooks nutzen, um CI-Noise zu vermeiden."
        ],
        "risks": [
            "Unvollständige Issue-Infos → Annahmen im PR sichtbar dokumentieren.",
            "Cross-Cutting-Änderungen in Kernmodulen mit Seiteneffekten."
        ],
        "artifacts": {
            "context_json_path": os.path.relpath(OUT_JSON, WORK_DIR).replace("\\", "/"),
            "plan_json_path": os.path.relpath(OUT_PLAN, WORK_DIR).replace("\\", "/"),
            "attachments_dir": os.path.relpath(ATTACH_DIR, WORK_DIR).replace("\\", "/"),
            "artifact_bundle": "codex-context"
        }
    }

    # -------- Deep Reasoning (optional) --------
    llm_result = None
    if DEEP_REASONING and OPENAI_API_KEY:
        bundle = prepare_reasoner_bundle(
            {"number": issue_no, "title": title, "body": body},
            analysis, file_index, candidates,
            MAX_FILES_FOR_REASONER, MAX_BYTES_PER_FILE_FOR_REASONER
        )
        system_prompt = (
            "You are a senior repo analyst bot (Bot 1) that prepares context for a solver bot.\n"
            "Identify root causes, impacted areas, and propose concrete, testable actions."
        )
        llm_result = call_reasoner_openai(system_prompt, bundle)
        if llm_result:
            plan["llm_deep_dive"] = llm_result
        else:
            plan["llm_deep_dive"] = {"note": "Reasoner call failed or was skipped."}
    else:
        plan["llm_deep_dive"] = {"note": "Deep reasoning disabled or no OPENAI_API_KEY."}

    with open(OUT_PLAN, "w", encoding="utf-8") as f:
        json.dump(plan, f, ensure_ascii=False, indent=2)

    # -------- Kurz-Zusammenfassung/Kommentar --------
    top_lang_preview = ", ".join(f"{k}:{v}KB" for k, v in list(lang_stats.items())[:3]) or "n/a"
    preview_files = "\n".join(f"- `{p}`" for p in candidates[:10]) or "- (keine Kandidaten erkannt)"
    llm_head = ""
    if llm_result:
        pb = llm_result.get("problem_breakdown") or []
        ac = (llm_result.get("concrete_actions") or [])[:3]
        head_list = [f"- {x}" for x in pb[:3]] or ["- (kein Breakdown geliefert)"]
        act_list = [f"- {a.get('title','(ohne Titel)')}" for a in ac] or ["- (keine Aktionen geliefert)"]
        llm_head = f"\n**LLM-Breakdown (Top 3)**\n" + "\n".join(head_list) + "\n**LLM-Aktionen (Top 3)**\n" + "\n".join(act_list)

    profile_line = ""
    if selected_profile:
        profile_line = f"\n- Profil: **{selected_profile}** (files={MAX_FILES_FOR_REASONER}, bytes/file={MAX_BYTES_PER_FILE_FOR_REASONER}, effort={REASONING_EFFORT})"

    summary_lines = [
        f"Issue #{issue_no} — {title.strip()}",
        f"Keywords: {', '.join(keywords) if keywords else '(keine)'}",
        f"Top-Languages: {top_lang_preview}",
        f"Kandidaten (Top 10):\n{preview_files}",
        f"Artefakte: solver_input.json, solver_plan.json"
    ]
    with open(OUT_SUMMARY, "w", encoding="utf-8") as f:
        f.write("\n".join(summary_lines))

    print("\n".join(summary_lines))
    print(f"Wrote solver_input.json → {OUT_JSON}")
    print(f"Wrote solver_plan.json  → {OUT_PLAN}")
    print(f"Gzipped copy            → {OUT_JSON}.gz")
    print(f"Summary                 → {OUT_SUMMARY}")

    if POST_ISSUE_COMMENT and issue_no:
        short_problem = (plan["issue"]["summary"] or "(kein Body)").strip()
        short_problem = (short_problem[:600] + "…") if len(short_problem) > 600 else short_problem
        comment = (
f"""**ContextMap ready** ✅{profile_line}

**Kurz-Zusammenfassung**
- Keywords: {', '.join(keywords) if keywords else '(keine)'}
- Kandidaten (Top 10):
{preview_files}{llm_head}

**Kurzproblem (aus dem Issue)**
> {short_problem}

**Hinweis für Bot 2**: Vollständiger Fahrplan im Artefakt **`codex-context`** → `solver_plan.json`."""
        )
        post_issue_comment(issue_no, repo, token, comment)

    # Label/Dispatch optional (best effort – no crash if offline)
    add_contextmap_ready_label(issue_no, repo, token)

    pointer = {
        "issue_number": issue_no,
        "repo": repo,
        "sha": os.getenv("GITHUB_SHA", ""),
        "artifact": "codex-context",
        "context_json_path": os.path.relpath(OUT_JSON, WORK_DIR).replace("\\", "/"),
        "plan_json_path": os.path.relpath(OUT_PLAN, WORK_DIR).replace("\\", "/"),
        "keywords": keywords[:10],
        "profile": selected_profile or "env/default"
    }
    if llm_result:
        pointer["plan_excerpt"] = {
            "likely_affected_files": candidates[:15],
            "acceptance_criteria": plan["acceptance_criteria"],
            "llm_problem_breakdown_top3": (llm_result.get("problem_breakdown") or [])[:3]
        }
    notify_solverbot(repo, token, pointer)

if __name__ == "__main__":
    main()

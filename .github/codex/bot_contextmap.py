#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
ContextMap Bot (generic)
- Scans ALL files in the repository working tree (tracked + untracked), excluding common build/VCS dirs
  and the generated context folder itself.
- Reads the triggering Issue/Comment via GitHub event payload, extracts referenced paths and URLs.
- Downloads attachments from GitHub/user-images and embeds them 1:1.
- Builds solver_input.json with:
  * Issue text (title, body)
  * Repo file index (size, hashes, tracked flag, LFS pointer/submodule hint)
  * Embedded content for referenced/attached files (always), text files chunked if large
  * Generic analysis: language bytes, stack/build indicators, largest files, top directories
- Writes outputs to .github/codex/context/ and emits a compact summary.
- Optional: adds 'contextmap-ready' label and posts a generic pre-analysis comment (toggled via env).
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

# ------------------------- Config / Defaults -------------------------

DEFAULT_EXCLUDE_DIRS = {
    ".git", ".github", ".gradle", "build", "out", ".idea", ".vscode", ".venv", "node_modules", ".dart_tool"
}
DEFAULT_MAX_TEXT_EMBED_BYTES = int(os.getenv("CODEX_MAX_TEXT_EMBED_BYTES", "1048576"))  # 1 MiB per file
DEFAULT_MAX_BIN_BASE64_BYTES = int(os.getenv("CODEX_MAX_BIN_BASE64_BYTES", "524288"))   # 512 KiB per file
DEFAULT_ALWAYS_EMBED_REFERENCED = True

WORK_DIR = os.path.abspath(".")
CTX_DIR = os.path.join(".github", "codex", "context")
CTX_DIR_ABS = os.path.abspath(CTX_DIR)
OUT_JSON = os.path.join(CTX_DIR, "solver_input.json")
OUT_SUMMARY = os.path.join(CTX_DIR, "summary.txt")
ATTACH_DIR = os.path.join(CTX_DIR, "attachments")

POST_ISSUE_COMMENT = os.getenv("CODEX_POST_ISSUE_COMMENT", "false").strip().lower() in {"1", "true", "yes"}
ADD_LABEL = os.getenv("CODEX_ADD_LABEL", "true").strip().lower() in {"1", "true", "yes"}

# Hosts, von denen Attachments geladen werden dürfen
ALLOWED_ATTACHMENT_HOSTS = {
    "user-images.githubusercontent.com",
    "objects.githubusercontent.com",
    "github.com",    # für Releases/Assets Redirects
    "raw.githubusercontent.com"
}

# ------------------------- Utilities -------------------------

logging.basicConfig(level=logging.INFO, format="%(levelname)s: %(message)s")

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
    enc = res.get("encoding") or "utf-8"
    return enc

def read_file_bytes(path: str) -> bytes:
    with open(path, "rb") as f:
        return f.read()

def safe_relpath(p: str) -> str:
    try:
        return os.path.relpath(p, WORK_DIR).replace("\\", "/")
    except Exception:
        return p.replace("\\", "/")

def file_sha256(b: bytes) -> str:
    h = hashlib.sha256()
    h.update(b)
    return h.hexdigest()

def ensure_dirs():
    os.makedirs(CTX_DIR, exist_ok=True)
    os.makedirs(ATTACH_DIR, exist_ok=True)

# ------------------------- Event helpers -------------------------

def load_event_payload() -> Dict[str, Any]:
    p = os.getenv("GITHUB_EVENT_PATH")
    if not p or not os.path.exists(p):
        logging.warning("GITHUB_EVENT_PATH not set or not found. Running in local mode (no issue body).")
        return {}
    with open(p, "r", encoding="utf-8") as f:
        return json.load(f)

def get_repo_env() -> Tuple[str, str]:
    repo = os.getenv("GITHUB_REPOSITORY", "")
    token = os.getenv("GITHUB_TOKEN", "")
    return repo, token

def github_api_get(url: str, token: str, accept: str = "application/vnd.github+json") -> requests.Response:
    headers = {"Accept": accept}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    r = requests.get(url, headers=headers, allow_redirects=True, timeout=60)
    r.raise_for_status()
    return r

def github_api_post_json(url: str, token: str, payload: dict):
    headers = {"Accept": "application/vnd.github+json", "Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    r = requests.post(url, headers=headers, json=payload, timeout=30)
    return r

def fetch_issue_context(event: Dict[str, Any], repo: str, token: str) -> Dict[str, Any]:
    evname = os.getenv("GITHUB_EVENT_NAME", "")
    ctx = {"event_name": evname, "issue_number": None, "title": "", "body": ""}
    if evname == "issues":
        issue = event.get("issue") or {}
        ctx["issue_number"] = issue.get("number")
        ctx["title"] = issue.get("title", "")
        ctx["body"] = issue.get("body", "") or ""
    elif evname == "issue_comment":
        issue = event.get("issue", {})
        comment = event.get("comment", {})
        ctx["issue_number"] = issue.get("number")
        ctx["title"] = issue.get("title", "")
        ctx["body"] = comment.get("body", "") or ""
    else:  # workflow_dispatch or others
        inputs = event.get("inputs", {}) or {}
        ctx["title"] = inputs.get("title", "")
        ctx["body"] = inputs.get("body", "")
    return ctx

# ------------------------- Reference parsing -------------------------

PATH_PATTERN = re.compile(r"""(?P<path>
    (?:[A-Za-z0-9_\-./ ]+/)*[A-Za-z0-9_\-./ ]+\.(?:kt|java|kts|gradle|xml|md|txt|yml|yaml|json|tf|properties|py|sh|bat|ps1|tl|proto|cc|cpp|hpp|c|h|dart|swift|go|rs|ini|cfg|csv|ts|tsx|jsx|html|css|scss|sql)
)""", re.VERBOSE)

URL_PATTERN = re.compile(r"""https?://[^\s)>'"]+""")

def extract_paths_and_urls(text: str) -> Tuple[List[str], List[str]]:
    paths = set()
    urls = set()
    text = text or ""

    for m in re.finditer(r"^\+\+\+ b/([^\n\r]+)$", text, re.MULTILINE):
        paths.add(m.group(1).strip())
    for m in re.finditer(r"^--- a/([^\n\r]+)$", text, re.MULTILINE):
        paths.add(m.group(1).strip())

    for m in PATH_PATTERN.finditer(text):
        paths.add(m.group("path").strip())

    for m in URL_PATTERN.finditer(text):
        urls.add(m.group(0).strip())

    return sorted(paths), sorted(urls)

# ------------------------- Attachments -------------------------

def _host_allowed(url: str) -> bool:
    try:
        from urllib.parse import urlparse
        host = urlparse(url).netloc.lower()
        # raw.githubusercontent.com liefert Dateien direkt
        return any(h in host for h in ALLOWED_ATTACHMENT_HOSTS)
    except Exception:
        return False

def download_attachment(url: str, token: str) -> Dict[str, Any]:
    meta = {"url": url, "saved_as": None, "status": "error", "size": 0, "sha256": None,
            "is_text": None, "encoding": None}
    if not _host_allowed(url):
        meta["status"] = "external-reference"
        return meta

    try:
        r = github_api_get(url, token, accept="application/octet-stream")
    except Exception as e:
        try:
            headers = {"Authorization": f"Bearer {token}"} if token else {}
            r = requests.get(url, headers=headers, allow_redirects=True, timeout=60)
            r.raise_for_status()
        except Exception as e2:
            meta["error"] = f"{type(e).__name__}: {e} / fallback: {type(e2).__name__}: {e2}"
            return meta

    data = r.content
    sha = file_sha256(data)
    meta["sha256"] = sha
    meta["size"] = len(data)

    name = url.split("/")[-1]
    if not name or "." not in name:
        cd = r.headers.get("Content-Disposition", "")
        mt = r.headers.get("Content-Type", "")
        if "filename=" in cd:
            name = cd.split("filename=")[-1].strip('"; ')
        else:
            ext = mimetypes.guess_extension(mt or "") or ".bin"
            name = f"attachment_{sha[:8]}{ext}"

    os.makedirs(ATTACH_DIR, exist_ok=True)
    save_path = os.path.join(ATTACH_DIR, name)
    with open(save_path, "wb") as f:
        f.write(data)
    meta["saved_as"] = save_path.replace("\\", "/")

    if is_probably_text(data):
        enc = detect_encoding(data)
        try:
            text = data.decode(enc, errors="replace")
        except Exception:
            enc = "utf-8"
            text = data.decode(enc, errors="replace")
        meta["is_text"] = True
        meta["encoding"] = enc
        meta["text"] = text
    else:
        meta["is_text"] = False
        meta["base64"] = base64.b64encode(data).decode("ascii")

    meta["status"] = "ok"
    return meta

# ------------------------- Repo scanning -------------------------

def list_all_files() -> List[str]:
    """Tracked + untracked; excludes generated context dir and common build/VCS dirs."""
    files = []
    for root, dirs, fnames in os.walk(WORK_DIR):
        for d in list(dirs):
            absd = os.path.abspath(os.path.join(root, d))
            if absd == CTX_DIR_ABS or absd.startswith(CTX_DIR_ABS + os.sep):
                dirs.remove(d)
                continue
            if d in DEFAULT_EXCLUDE_DIRS and absd != os.path.abspath(".github"):
                dirs.remove(d)

        for fn in fnames:
            p = os.path.join(root, fn)
            absp = os.path.abspath(p)
            if absp == CTX_DIR_ABS or absp.startswith(CTX_DIR_ABS + os.sep):
                continue
            rel = safe_relpath(p)
            if rel.startswith(".git/"):
                continue
            files.append(rel)
    return sorted(set(files))

def read_repo_file(path: str, always_embed: bool=False, tracked_set: Optional[set]=None) -> Dict[str, Any]:
    info = {"path": path, "size": None, "sha1": None, "sha256": None, "is_tracked": None,
            "is_text": None, "encoding": None, "lfs_pointer": False, "submodule_hint": False}

    abspath = os.path.abspath(path)
    try:
        b = read_file_bytes(abspath)
    except Exception as e:
        info["error"] = f"read_error: {type(e).__name__}: {e}"
        return info

    info["size"] = len(b)
    info["sha1"] = hashlib.sha1(b).hexdigest()
    info["sha256"] = file_sha256(b)

    if tracked_set is None:
        tracked_set = set(git_ls_files())
    info["is_tracked"] = path in tracked_set

    head = b[:200].decode("utf-8", errors="ignore")
    if head.startswith("version https://git-lfs.github.com/spec/v1") and "git-lfs.github.com/spec/v1" in head:
        info["lfs_pointer"] = True

    if path == ".gitmodules":
        info["submodule_hint"] = True

    text_like_ext = path.endswith((
        ".gradle",".kts",".xml",".md",".txt",".json",".yml",".yaml",".kt",".java",
        ".py",".sh",".bat",".ps1",".properties",".cfg",".ini",".csv",".proto",".tl",
        ".go",".rs",".swift",".dart",".html",".css",".scss",".ts",".tsx",".jsx",
        ".c",".h",".cc",".cpp",".hpp",".sql"
    ))
    if is_probably_text(b) or text_like_ext:
        info["is_text"] = True
        enc = detect_encoding(b)
        try:
            text = b.decode(enc, errors="replace")
        except Exception:
            enc = "utf-8"
            text = b.decode(enc, errors="replace")
        info["encoding"] = enc

        if always_embed or len(b) <= DEFAULT_MAX_TEXT_EMBED_BYTES:
            info["text"] = text
        else:
            chunk_size = 256 * 1024
            chunks = []
            for i in range(0, len(text), chunk_size):
                chunks.append({"index": i // chunk_size, "text": text[i:i+chunk_size]})
            info["chunks"] = chunks
    else:
        info["is_text"] = False
        if always_embed or len(b) <= DEFAULT_MAX_BIN_BASE64_BYTES:
            info["base64"] = base64.b64encode(b).decode("ascii")
        else:
            info["binary_summary"] = {
                "note": "Binary file too large to embed; saved on runner workspace only.",
                "size": len(b)
            }

    return info

# ------------------------- Generic analysis -------------------------

def language_stats() -> Dict[str, float]:
    exts = {}
    for p in list_all_files():
        ext = os.path.splitext(p)[1].lower().lstrip(".") or "(none)"
        exts.setdefault(ext, 0)
        try:
            exts[ext] += os.path.getsize(p)
        except Exception:
            pass
    return {k: round(v/1024, 2) for k, v in sorted(exts.items(), key=lambda kv: kv[1], reverse=True)}

def detect_stack_files() -> Dict[str, List[str]]:
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

    files = list_all_files()
    found: Dict[str, List[str]] = {k: [] for k in patterns}
    for p in files:
        lp = p.lower()
        bn = os.path.basename(lp)
        for key, pats in patterns.items():
            for pat in pats:
                if pat.startswith(".") and bn.endswith(pat):  # extensions like .tf
                    if bn.endswith(pat):
                        found[key].append(p)
                        break
                elif pat.endswith("/") and pat[:-1] in lp:    # directory hint
                    found[key].append(p)
                    break
                else:
                    if bn == pat or lp.endswith("/" + pat) or bn.endswith(pat):
                        found[key].append(p)
                        break
    # Clean empty
    return {k: sorted(set(v)) for k, v in found.items() if v}

def largest_files(n: int = 15) -> List[Dict[str, Any]]:
    sizes = []
    for p in list_all_files():
        try:
            sizes.append((p, os.path.getsize(p)))
        except Exception:
            pass
    sizes.sort(key=lambda t: t[1], reverse=True)
    return [{"path": p, "bytes": s} for p, s in sizes[:n]]

def top_dirs_by_count(n: int = 10) -> List[Dict[str, Any]]:
    cnt = Counter(os.path.dirname(p) or "." for p in list_all_files())
    most = cnt.most_common(n)
    return [{"dir": d, "files": c} for d, c in most]

# ------------------------- Generic preanalysis / comment -------------------------

STOPWORDS = {
    # de + en minimal
    "the","and","for","with","that","this","from","are","was","were","will","would","you","your","have","has","had",
    "ein","eine","einer","eines","einem","einen","der","die","das","und","oder","aber","nicht","kein","keine","den","dem",
    "ist","sind","war","waren","wird","würde","wurde","zu","zum","zur","auf","im","in","am","beim","vom","von","mit","ohne",
    "einfach","problem","issue","bug","fix","fehler","error","fail","failing","läuft","laufen","run","running","open","opened"
}

def extract_keywords(text: str, limit: int = 12) -> List[str]:
    text = (text or "").lower()
    words = re.findall(r"[a-z0-9_][a-z0-9_\-]{2,}", text, flags=re.IGNORECASE)
    counts = Counter(w for w in words if w not in STOPWORDS)
    return [w for w, _ in counts.most_common(limit)]

def find_related_files_by_keywords(keywords: List[str], limit: int = 60) -> List[str]:
    acc = []
    keys = [k.lower() for k in keywords if k]
    for p in list_all_files():
        lp = p.lower()
        if any(k in lp for k in keys):
            if "/build/" in lp or "/.gradle/" in lp or lp.startswith(".github/"):
                continue
            acc.append(p)
            if len(acc) >= limit:
                break
    return acc

def summarize_issue_problem(body: str) -> str:
    body = (body or "").strip()
    return body[:1200]

def post_issue_comment(issue_number: Optional[int], repo: str, token: str, body: str):
    if not (issue_number and repo and token and body):
        return
    url = f"https://api.github.com/repos/{repo}/issues/{issue_number}/comments"
    r = github_api_post_json(url, token, {"body": body})
    if r is None or r.status_code >= 300:
        logging.warning("Failed to post issue comment: %s", getattr(r, "text", "")[:300])

def add_contextmap_ready_label(issue_number: Optional[int], repo: str, token: str):
    try:
        if not issue_number or not repo:
            return
        url = f"https://api.github.com/repos/{repo}/issues/{issue_number}/labels"
        r = github_api_post_json(url, token, {"labels": ["contextmap-ready"]})
        if r is None or r.status_code >= 300:
            logging.warning("Adding 'contextmap-ready' failed: %s", getattr(r, 'text', '')[:300])
        else:
            logging.info("Label 'contextmap-ready' added to issue #%s", issue_number)
    except Exception as e:
        logging.warning("Labeling failed: %s", e)

# ------------------------- Main -------------------------

def main():
    ensure_dirs()

    event = load_event_payload()
    repo, token = get_repo_env()
    ictx = fetch_issue_context(event, repo, token)
    body = ictx.get("body", "") or ""
    title = ictx.get("title", "") or ""

    # Extract paths and URLs from the triggering body
    ref_paths, ref_urls = extract_paths_and_urls(body)

    # Download attachments
    attachments = []
    for u in ref_urls:
        if _host_allowed(u):
            logging.info("Downloading attachment: %s", u)
            attachments.append(download_attachment(u, token))
        else:
            attachments.append({"url": u, "status": "external-reference"})

    # Build repo index
    tracked_set = set(git_ls_files())
    allfiles = list_all_files()

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

    # Virtual index entries for successfully downloaded attachments
    for att in attachments:
        if att.get("status") == "ok":
            vpath = f"__attachments__/{os.path.basename(att['saved_as'])}"
            finfo = {
                "path": vpath,
                "size": att.get("size"),
                "sha256": att.get("sha256"),
                "is_text": att.get("is_text"),
                "encoding": att.get("encoding")
            }
            if att.get("is_text"):
                finfo["text"] = att.get("text", "")
            else:
                finfo["base64"] = att.get("base64", "")
            file_index[vpath] = finfo

    # Generic analysis
    try:
        repo_size_kb = round(sum(os.path.getsize(p) for p in allfiles if os.path.exists(p)) / 1024, 2)
    except Exception:
        repo_size_kb = None

    analysis = {
        "language_bytes_kb": language_stats(),
        "stack_indicators": detect_stack_files(),
        "largest_files": largest_files(15),
        "top_dirs_by_file_count": top_dirs_by_count(10),
        "repo_size_kb": repo_size_kb,
        "file_count": len(allfiles),
    }

    # Build output JSON
    out = {
        "meta": {
            "generated_at": time.strftime("%Y-%m-%d %H:%M:%S %z"),
            "repo": repo,
            "cwd": os.getcwd(),
            "event_name": os.getenv("GITHUB_EVENT_NAME", ""),
            "ref": os.getenv("GITHUB_REF", ""),
            "sha": os.getenv("GITHUB_SHA", ""),
            "runner_os": os.getenv("RUNNER_OS", ""),
            "max_text_embed_bytes": DEFAULT_MAX_TEXT_EMBED_BYTES,
            "max_bin_base64_bytes": DEFAULT_MAX_BIN_BASE64_BYTES,
            "total_bytes_embedded_estimate": total_bytes_embedded,
        },
        "issue_context": {
            "number": ictx.get("issue_number"),
            "title": title,
            "body": body,
            "referenced_paths": ref_paths,
            "referenced_urls": ref_urls,
        },
        "attachments": attachments,
        "repo": {
            "files": allfiles,
            "file_index": file_index
        },
        "analysis": analysis,
    }

    # Write outputs (plain + gz)
    with open(OUT_JSON, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False)

    with gzip.open(OUT_JSON + ".gz", "wb") as gz:
        gz.write(json.dumps(out, ensure_ascii=False).encode("utf-8"))

    # Summary
    # Top 3 languages for compact summary
    lang = list(analysis.get("language_bytes_kb", {}).items())[:3]
    lang_str = ", ".join(f"{k}: {v} KB" for k, v in lang) if lang else "n/a"
    summary_lines = [
        f"Issue #{ictx.get('issue_number')} — {title.strip()}",
        f"Referenced paths: {len(ref_paths)} | URLs: {len(ref_urls)} | Attachments: {sum(1 for a in attachments if a.get('status')=='ok')}",
        f"Repo files indexed: {len(allfiles)} | Embedded bytes (est.): {total_bytes_embedded}",
        f"Repo size: {analysis.get('repo_size_kb')} KB | Top languages: {lang_str}",
    ]
    with open(OUT_SUMMARY, "w", encoding="utf-8") as f:
        f.write("\n".join(summary_lines))

    print("\n".join(summary_lines))
    print(f"Wrote solver_input.json → {OUT_JSON}")
    print(f"Gzipped copy          → {OUT_JSON}.gz")
    print(f"Summary               → {OUT_SUMMARY}")

    # -------- Optional: generic pre-analysis comment --------
    issue_no = ictx.get("issue_number")
    if POST_ISSUE_COMMENT and issue_no:
        keywords = extract_keywords(title + " " + body, limit=12)
        related = find_related_files_by_keywords(keywords, limit=30)
        stack = analysis.get("stack_indicators", {})
        stack_preview = "\n".join(f"- {k}: {len(v)} Hinweise" for k, v in stack.items()) or "- (keine gefunden)"

        problem = summarize_issue_problem(body)
        preview_related = "\n".join(f"- `{p}`" for p in related[:20]) or "- (keine offensichtlichen Dateien gefunden)"

        comment_body = (
f"""**ContextMap ready** ✅

### Problem (aus dem Issue)
{problem}

### Voranalyse (generisch)
- Schlüsselwörter (aus Titel/Body): {", ".join(keywords) if keywords else "(keine)"}  
- Referenzierte Pfade: {len(ref_paths)}  
- Mögliche relevante Dateien (Heuristik):
{preview_related}

- Build/Stack‑Hinweise (erkannt):
{stack_preview}

**Artefakte**: `solver_input.json`, `summary.txt`."""
        )
        repo_env, token_env = get_repo_env()
        post_issue_comment(issue_no, repo_env, token_env, comment_body)

    # Auto-label
    evn = os.getenv("GITHUB_EVENT_NAME", "")
    if ADD_LABEL and evn in ("issues", "issue_comment"):
        repo_env, token_env = get_repo_env()
        add_contextmap_ready_label(issue_no, repo_env, token_env)

if __name__ == "__main__":
    main()
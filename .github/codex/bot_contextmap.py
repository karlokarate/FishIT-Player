#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
ContextMap Bot (enhanced)
- Scans ALL files in the repository working tree (tracked + untracked), excluding only common build and VCS dirs.
- Fetches the triggering Issue/Comment via GitHub API, extracts referenced paths and URLs.
- Downloads *attached* files from the Issue body (GitHub assets / user-images) and embeds them 1:1.
- Builds a solver_input.json containing:
  * Complete issue/context text
  * Full file index of the repo (size, sha1, tracked/untracked, LFS pointer, submodule hint)
  * 1:1 contents for referenced/attached files (always embedded)
  * 1:1 contents for all repo text files, chunked if large (binaries base64 if small, or summarized + artifact hint)
  * Deep analysis (modules, Compose/@Composable counts, Focus heuristics, Gradle + AndroidManifest overview)
- Writes outputs to .github/codex/context/ and prints a compact summary for the job log.

Environment:
- Requires GITHUB_TOKEN, GITHUB_REPOSITORY, GITHUB_EVENT_NAME, GITHUB_EVENT_PATH provided by GitHub Actions.
- Assumes actions/checkout with lfs:true and submodules:recursive.

Notes:
- Extremely large repos can produce huge JSON. This bot embeds everything by default to honor "1:1 availability".
  You can tune limits via CLI args or environment variables if needed (see DEFAULTS below).
"""

import os
import re
import io
import sys
import json
import time
import gzip
import math
import glob
import base64
import shutil
import hashlib
import logging
import mimetypes
import subprocess
from dataclasses import dataclass, asdict
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
DEFAULT_MAX_TOTAL_JSON_MB = int(os.getenv("CODEX_MAX_TOTAL_JSON_MB", "200"))            # 200 MB safety
DEFAULT_ALWAYS_EMBED_REFERENCED = True  # Always embed referenced/attached files 1:1 regardless of size

WORK_DIR = os.path.abspath(".")
CTX_DIR = os.path.join(".github", "codex", "context")
OUT_JSON = os.path.join(CTX_DIR, "solver_input.json")
OUT_SUMMARY = os.path.join(CTX_DIR, "summary.txt")
ATTACH_DIR = os.path.join(CTX_DIR, "attachments")

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

def git_hash_object(path: str) -> Optional[str]:
    code, out, err = run(["git", "hash-object", path])
    if code != 0:
        return None
    return out.strip()

def is_probably_text(data: bytes) -> bool:
    if b"\x00" in data[:4096]:
        return False
    # Heuristic: if mostly printable
    text_chars = sum(c >= 9 and c <= 13 or (32 <= c <= 126) for c in data[:4096])
    return (text_chars / max(1, len(data[:4096]))) > 0.85

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

# ------------------------- Issue / Event handling -------------------------

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

def fetch_issue_context(event: Dict[str, Any], repo: str, token: str) -> Dict[str, Any]:
    """Return dict with: type ('issues'|'issue_comment'|'workflow_dispatch'), issue_number, body, title, urls."""
    evname = os.getenv("GITHUB_EVENT_NAME", "")
    ctx = {"event_name": evname, "issue_number": None, "title": "", "body": ""}
    if evname == "issues":
        issue = event.get("issue") or event.get("data", {}).get("issue") or {}
        number = issue.get("number")
        ctx["issue_number"] = number
        ctx["title"] = issue.get("title", "")
        ctx["body"] = issue.get("body", "") or ""
    elif evname == "issue_comment":
        issue = event.get("issue", {})
        comment = event.get("comment", {})
        number = issue.get("number")
        ctx["issue_number"] = number
        ctx["title"] = issue.get("title", "")
        ctx["body"] = comment.get("body", "") or ""
    else:
        # e.g., workflow_dispatch
        # Try to fetch last opened issue with /codex trigger? Otherwise empty.
        ctx["title"] = event.get("inputs", {}).get("title", "")
        ctx["body"] = event.get("inputs", {}).get("body", "")
    return ctx

# ------------------------- Reference parsing -------------------------

PATH_PATTERN = re.compile(r"""(?P<path>
    (?:[A-Za-z0-9_\-./]+/)*[A-Za-z0-9_\-./]+\.(?:kt|java|kts|gradle|xml|md|txt|yml|yaml|json|tf|properties|py|sh|bat|ps1|tl|proto|cc|cpp|hpp|c|h|dart|swift|go|rs|ini|cfg|csv|ts|tsx|jsx|html|css|scss)
)""", re.VERBOSE)

URL_PATTERN = re.compile(r"""https?://[^\s)>'"]+""")

def extract_paths_and_urls(text: str) -> Tuple[List[str], List[str]]:
    paths = set()
    urls = set()

    # From diff headers
    for m in re.finditer(r"^\+\+\+ b/([^\n\r]+)$", text, re.MULTILINE):
        paths.add(m.group(1).strip())
    for m in re.finditer(r"^--- a/([^\n\r]+)$", text, re.MULTILINE):
        paths.add(m.group(1).strip())

    # From code blocks/backticks and plain text
    for m in PATH_PATTERN.finditer(text):
        paths.add(m.group("path").strip())

    for m in URL_PATTERN.finditer(text):
        urls.add(m.group(0).strip())

    return sorted(paths), sorted(urls)

# ------------------------- Downloader for attachments -------------------------

def download_attachment(url: str, token: str) -> Dict[str, Any]:
    """Download a URL (GitHub assets/user-images supported). Returns metadata + content (text/base64)."""
    meta = {"url": url, "saved_as": None, "status": "error", "size": 0, "sha256": None,
            "is_text": None, "encoding": None}

    try:
        r = github_api_get(url, token, accept="application/octet-stream")
    except Exception as e:
        # If direct GET fails (e.g., needs redirect), try without API accept
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

    # decide file name
    name = url.split("/")[-1]
    if not name or "." not in name:
        # try to guess from headers
        cd = r.headers.get("Content-Disposition", "")
        mt = r.headers.get("Content-Type", "")
        if "filename=" in cd:
            name = cd.split("filename=")[-1].strip('"; ')
        else:
            ext = mimetypes.guess_extension(mt or "") or ".bin"
            name = f"attachment_{sha[:8]}{ext}"

    # save raw attachment
    os.makedirs(ATTACH_DIR, exist_ok=True)
    save_path = os.path.join(ATTACH_DIR, name)
    with open(save_path, "wb") as f:
        f.write(data)
    meta["saved_as"] = save_path.replace("\\", "/")

    # embed content 1:1
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
    """Walk the working tree to include tracked + untracked files. Exclude common build/VCS dirs."""
    files = []
    for root, dirs, fnames in os.walk(WORK_DIR):
        # prune dirs
        pruned = []
        for d in list(dirs):
            if d in DEFAULT_EXCLUDE_DIRS and os.path.abspath(os.path.join(root, d)) != os.path.abspath(".github"):
                pruned.append(d)
        for d in pruned:
            dirs.remove(d)

        for fn in fnames:
            p = os.path.join(root, fn)
            rel = safe_relpath(p)
            if rel.startswith(".git/"):
                continue
            files.append(rel)
    return sorted(set(files))

def read_repo_file(path: str, always_embed: bool=False) -> Dict[str, Any]:
    """Read file metadata + content (text/binary). May chunk or base64 depending on size and type.
       For referenced/attached files: set always_embed=True to bypass size guards.
    """
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

    tracked = path in set(git_ls_files())
    info["is_tracked"] = tracked

    # LFS pointer check
    head = b[:200].decode("utf-8", errors="ignore")
    if "git-lfs.github.com/spec/v1" in head and head.startswith("version https://git-lfs.github.com/spec/v1"):
        info["lfs_pointer"] = True

    # submodule hint: entries in .gitmodules
    if path == ".gitmodules":
        info["submodule_hint"] = True

    # content embedding
    if is_probably_text(b) or path.endswith((".gradle",".kts",".xml",".md",".txt",".json",".yml",".yaml",".kt",".java",".py",".sh",".bat",".ps1",".properties",".cfg",".ini",".csv",".proto",".tl",".go",".rs",".swift",".dart",".html",".css",".scss",".ts",".tsx",".jsx",".c",".h",".cc",".cpp",".hpp")):
        info["is_text"] = True
        enc = detect_encoding(b)
        try:
            text = b.decode(enc, errors="replace")
        except Exception:
            enc = "utf-8"
            text = b.decode(enc, errors="replace")
        info["encoding"] = enc

        # Always embed referenced files; otherwise chunk if too large
        if always_embed or len(b) <= DEFAULT_MAX_TEXT_EMBED_BYTES:
            info["text"] = text
        else:
            # chunk into ~256 KiB chunks
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

# ------------------------- Deep Analysis -------------------------

def analyze_gradle_modules() -> Dict[str, Any]:
    mods = {"settings_files": [], "includes": [], "build_files": [], "warnings": []}
    for f in ["settings.gradle", "settings.gradle.kts"]:
        if os.path.exists(f):
            mods["settings_files"].append(f)
            try:
                with open(f, "r", encoding="utf-8") as fh:
                    s = fh.read()
                inc = re.findall(r'include\((?P<q>"|\'):(?P<name>[^"\']+)(?P=q)\)', s)
                inc2 = re.findall(r'include\s+(?P<q>"|\'):(?P<name>[^"\']+)(?P=q)', s)
                names = sorted(set([m[1] for m in inc] + [m[1] for m in inc2]))
                mods["includes"].extend(names)
            except Exception as e:
                mods["warnings"].append(f"{f}: {type(e).__name__}: {e}")
    # collect build files
    for path in list_all_files():
        if path.endswith(("build.gradle", "build.gradle.kts")):
            mods["build_files"].append(path)
    return mods

def analyze_android_project() -> Dict[str, Any]:
    manis = []
    for path in list_all_files():
        if path.endswith("AndroidManifest.xml"):
            try:
                with open(path, "r", encoding="utf-8") as f:
                    t = f.read()
                app_name = None
                m = re.search(r'android:name\s*=\s*"([^"]+)"', t)
                if m:
                    app_name = m.group(1)
                manis.append({"path": path, "application_name": app_name})
            except Exception as e:
                manis.append({"path": path, "error": f"{type(e).__name__}: {e}"})
    return {"manifests": manis}

def analyze_compose_and_focus() -> Dict[str, Any]:
    counts = {
        "kotlin_files": 0,
        "composables": 0,
        "focusable_usages": 0,
        "onFocusChanged": 0,
        "bringIntoViewRequester": 0,
        "chips": 0,
        "indication_params": 0,
    }
    samples = {"composable_decl_paths": [], "chip_paths": [], "focus_paths": []}
    for path in list_all_files():
        if path.endswith(".kt"):
            counts["kotlin_files"] += 1
            try:
                with open(path, "r", encoding="utf-8") as f:
                    t = f.read()
                c = t.count("@Composable")
                counts["composables"] += c
                if c and len(samples["composable_decl_paths"]) < 20:
                    samples["composable_decl_paths"].append(path)

                # focus heuristics
                fc = t.count("Modifier.focusable(") + t.count(".focusable(")
                counts["focusable_usages"] += fc
                of = t.count("onFocusChanged(")
                counts["onFocusChanged"] += of
                bi = t.count("bringIntoViewRequester(")
                counts["bringIntoViewRequester"] += bi
                ic = t.count("indication =")
                counts["indication_params"] += ic

                # chips
                if "FilterChip" in t or "AssistChip" in t or "SuggestionChip" in t or "InputChip" in t:
                    counts["chips"] += 1
                    if len(samples["chip_paths"]) < 20:
                        samples["chip_paths"].append(path)

                if fc or of or bi:
                    if len(samples["focus_paths"]) < 20:
                        samples["focus_paths"].append(path)

            except Exception:
                pass
    return {"counts": counts, "samples": samples}

def language_stats() -> Dict[str, Any]:
    exts = {}
    for p in list_all_files():
        ext = os.path.splitext(p)[1].lower().lstrip(".") or "(none)"
        exts.setdefault(ext, 0)
        try:
            exts[ext] += os.path.getsize(p)
        except Exception:
            pass
    # sizes in KB
    return {k: round(v/1024, 2) for k, v in sorted(exts.items(), key=lambda kv: kv[1], reverse=True)}

# ------------------------- Main -------------------------

def main():
    ensure_dirs()

    event = load_event_payload()
    repo, token = get_repo_env()
    ictx = fetch_issue_context(event, repo, token)
    body = ictx.get("body", "") or ""

    # Extract paths and URLs from the triggering body
    ref_paths, ref_urls = extract_paths_and_urls(body)

    # Download attachments
    attachments = []
    for u in ref_urls:
        if "github.com" in u or "user-images.githubusercontent.com" in u or "/assets/" in u:
            logging.info("Downloading attachment: %s", u)
            meta = download_attachment(u, token)
            attachments.append(meta)
        else:
            # leave external links as references only
            attachments.append({"url": u, "status": "external-reference"})

    # Build repo index
    tracked = set(git_ls_files())
    allfiles = list_all_files()

    # Read files, embedding full contents (text chunked if large)
    file_index = {}
    total_bytes_embedded = 0
    for p in allfiles:
        always_embed = DEFAULT_ALWAYS_EMBED_REFERENCED and (p in ref_paths)
        info = read_repo_file(p, always_embed=always_embed)
        file_index[p] = info

        # approximate counting for safety logging
        if info.get("is_text") and "text" in info:
            total_bytes_embedded += len(info["text"].encode("utf-8", errors="replace"))
        elif info.get("is_text") and "chunks" in info:
            total_bytes_embedded += sum(len(ch["text"].encode("utf-8", errors="replace")) for ch in info["chunks"])
        elif not info.get("is_text") and "base64" in info:
            total_bytes_embedded += len(info["base64"])

    # For each referenced path that doesn't exist on disk (e.g., provided only as attachment),
    # if we downloaded an attachment, also expose it under a virtual path in the index.
    for att in attachments:
        if att.get("status") == "ok":
            vpath = f"__attachments__/{os.path.basename(att['saved_as'])}"
            # embed 1:1
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

    # Deep analysis
    analysis = {
        "gradle_modules": analyze_gradle_modules(),
        "android_project": analyze_android_project(),
        "compose_focus": analyze_compose_and_focus(),
        "language_bytes_kb": language_stats(),
        "repo_size_kb": round(sum(os.path.getsize(p) for p in allfiles if os.path.exists(p))/1024, 2),
        "file_count": len(allfiles),
    }

    # Build output JSON for solver
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
            "title": ictx.get("title"),
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

    # Safety: compress JSON if huge but still write the uncompressed as requested
    with open(OUT_JSON, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False)

    with gzip.open(OUT_JSON + ".gz", "wb") as gz:
        gz.write(json.dumps(out, ensure_ascii=False).encode("utf-8"))

    # Summary text
    summary_lines = []
    summary_lines.append(f"Issue #{ictx.get('issue_number')} — {ictx.get('title','').strip()}")
    summary_lines.append(f"Referenced paths: {len(ref_paths)} | URLs: {len(ref_urls)} | Attachments: {sum(1 for a in attachments if a.get('status')=='ok')}")
    summary_lines.append(f"Repo files indexed: {len(allfiles)} | Embedded bytes (est.): {total_bytes_embedded}")
    comp = analysis.get("compose_focus", {}).get("counts", {})
    summary_lines.append(f"Kotlin files: {comp.get('kotlin_files',0)} | @Composable: {comp.get('composables',0)} | focusable(): {comp.get('focusable_usages',0)} | chips: {comp.get('chips',0)}")
    with open(OUT_SUMMARY, "w", encoding="utf-8") as f:
        f.write("\n".join(summary_lines))

    print("\n".join(summary_lines))
    print(f"Wrote solver_input.json → {OUT_JSON}")
    print(f"Gzipped copy          → {OUT_JSON}.gz")
    print(f"Summary               → {OUT_SUMMARY}")

if __name__ == "__main__":
    main()

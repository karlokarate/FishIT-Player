#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
Codex Bot – Build+Fix Edition (+ Shortcuts) — robust apply + savestates + auto-merge to main

Fähigkeiten:
- Liest /codex-Aufträge (Issues/Kommentare)
- Erzeugt/Appliziert Patches robust (3-way, Section-fallback), bricht HART ab wenn nichts angewendet wurde
- Savestates: vor/nach Änderungen Snapshot-Branch + Tags + History-Ordner (.github/codex/history/<ts>/…)
- Öffnet PR gegen main und versucht Auto-Merge (squash->merge), kommentiert Status
- Ausführliche Shell-Logs (Annotations) + Step-Summary

Benötigte Python-Dependencies: requests, unidiff, openai>=1.40.0
"""

from __future__ import annotations

import io
import json
import os
import random
import re
import shlex
import subprocess
import sys
import time
import zipfile
import traceback
import textwrap
from datetime import datetime
from pathlib import Path
from typing import Any, Iterable

import requests
from unidiff import PatchSet

# ------------------------ Konfiguration ------------------------

DOC_CANDIDATES = [
    "AGENTS.md", "ARCHITECTURE_OVERVIEW.md", "ROADMAP.md", "CHANGELOG.md",
    "CONTRIBUTING.md", "CODE_OF_CONDUCT.md"
]

MAX_DOC_CHARS     = 24000
MAX_CONTEXT_CHARS = 220000
MAX_USER_CHARS    = 4000

FORBIDDEN_PATHS = [".github/workflows/"]           # nur mit --allow-ci ändern
MAX_PATCH_LINES = 3000

DEFAULT_WORKFLOW_FILE = "release-apk.yml"
DEFAULT_MAX_ATTEMPTS  = 4
DEFAULT_WAIT_TIMEOUTS = (30, 1800)

WARN_PAT = re.compile(r"\b(warning|warn|w:)\b", re.I)
ERR_PAT  = re.compile(r"\b(error|fail(ed)?|e:)\b", re.I)

# ------------------------ Logging & Errors ------------------------

def _write_step_summary(md: str) -> None:
    path = os.environ.get("GITHUB_STEP_SUMMARY")
    if not path:
        return
    try:
        with open(path, "a", encoding="utf-8") as f:
            f.write(md.rstrip() + "\n")
    except Exception:
        pass

def _redact(s: str) -> str:
    if not s: return s
    for key in ["OPENAI_API_KEY", "GITHUB_TOKEN"]:
        v = os.environ.get(key)
        if v and isinstance(s, str): s = s.replace(v, "***")
    return s

def log_group(title: str) -> None:   print(f"::group::{title}")
def log_end_group() -> None:         print("::endgroup::")
def log_info(msg: str) -> None:      print(f"::notice::{msg}")
def log_warn(msg: str) -> None:      print(f"::warning::{msg}")
def log_error(title: str, details: str = "", hint: str = "") -> None:
    lines = [title]
    if details: lines += ["", _redact(details)]
    if hint:    lines += ["", f"HINT: {hint}"]
    sys.stderr.write(f"::error::" + "\n".join(lines) + "\n")

def die(title: str, details: str = "", hint: str = "", exit_code: int = 1) -> None:
    log_error(title, details, hint)
    _write_step_summary(f"### ❌ {title}\n\n" + (f"```\n{_redact(details)}\n```\n\n" if details else "") + (f"**Hint:** {hint}\n" if hint else ""))
    sys.exit(exit_code)

from contextlib import contextmanager
@contextmanager
def step(title: str):
    t0 = time.time()
    log_group(title)
    try:
        yield
        log_info(f"{title} — OK in {int(time.time()-t0)}s")
    except Exception:
        log_error(f"{title} — FAILED after {int(time.time()-t0)}s", traceback.format_exc())
        raise
    finally:
        log_end_group()

# ------------------------ Shell / Git / HTTP Utils ------------------------

def sh(cmd: str, check: bool = True) -> str:
    res = subprocess.run(cmd, shell=True, text=True, capture_output=True)
    if check and res.returncode != 0:
        stdout = (res.stdout or "").strip()
        stderr = (res.stderr or "").strip()
        details = f"""Command: {cmd}
Exit code: {res.returncode}
--- STDOUT ---
{stdout[:4000]}
--- STDERR ---
{stderr[:4000]}"""
        log_error("Shell command failed", details, "Pfade/Branch/Gradle/Permissions prüfen.")
        raise RuntimeError(f"cmd failed: {cmd}\nSTDOUT:\n{stdout}\nSTDERR:\n{stderr}")
    return (res.stdout or "").strip()

def gh_api(method: str, path: str, payload=None):
    url = f"https://api.github.com{path}"
    headers = {"Authorization": f"Bearer {os.environ.get('GITHUB_TOKEN','')}", "Accept": "application/vnd.github+json"}
    try:
        resp = requests.request(method, url, headers=headers, json=payload, timeout=45)
    except Exception as e:
        log_error("GitHub API request failed (network)", f"{method} {url}\n{type(e).__name__}: {e}", "Runner-Network/DNS/Firewall prüfen.")
        raise
    if resp.status_code >= 300:
        snippet = (resp.text or "")[:4000]
        details = f"""{method} {path}
Status: {resp.status_code}
Payload: {_redact(json.dumps(payload, ensure_ascii=False)[:2000]) if payload else '—'}
Response (truncated):
{snippet}"""
        log_error("GitHub API returned an error", details, "Repo-Permissions (Actions write), Pfad/ID/Branch prüfen.")
        raise RuntimeError(f"GitHub API {method} {path} failed: {resp.status_code}")
    if resp.text and resp.headers.get("content-type","").startswith("application/json"):
        return resp.json()
    return {}

def gh_api_raw(method: str, url: str, allow_redirects=True):
    headers = {"Authorization": f"Bearer {os.environ.get('GITHUB_TOKEN','')}", "Accept": "application/vnd.github+json"}
    try:
        resp = requests.request(method, url, headers=headers, allow_redirects=allow_redirects, stream=True, timeout=60)
    except Exception as e:
        log_error("GitHub RAW request failed (network)", f"{method} {url}\n{type(e).__name__}: {e}")
        raise
    if resp.status_code >= 300 and resp.status_code not in (301, 302):
        log_error("GitHub RAW returned an error", f"{method} {url}\nStatus: {resp.status_code}\n{(resp.text or '')[:800]}")
        raise RuntimeError(f"GitHub RAW {method} {url} failed: {resp.status_code}")
    return resp

def comment_reply(body: str):
    try:
        event = json.load(open(os.environ["GITHUB_EVENT_PATH"], "r", encoding="utf-8"))
    except Exception as e:
        log_warn(f"Issue-Kommentar konnte Event nicht lesen: {type(e).__name__}: {e}")
        return
    repo = os.environ.get("GITHUB_REPOSITORY", "")
    num = (event.get("issue") or {}).get("number") or (event.get("pull_request") or {}).get("number")
    if not num:
        log_warn("Kein Issue/PR zum Kommentieren gefunden.")
        return
    try:
        gh_api("POST", f"/repos/{repo}/issues/{num}/comments", {"body": body})
    except Exception as e:
        log_warn(f"Issue-Kommentar fehlgeschlagen: {type(e).__name__}: {e}")

def read_comment() -> str:
    ev = os.environ.get("GH_EVENT_NAME", "")
    if ev == "workflow_dispatch":
        return os.environ.get("DISPATCH_COMMENT", "").strip()
    try:
        event = json.load(open(os.environ["GITHUB_EVENT_PATH"], "r", encoding="utf-8"))
    except Exception as e:
        log_error("GITHUB_EVENT_PATH konnte nicht gelesen werden", f"path={os.environ.get('GITHUB_EVENT_PATH')}\n{type(e).__name__}: {e}")
        return ""
    if ev == "issue_comment": return (event.get("comment") or {}).get("body","").strip()
    if ev == "pull_request_review_comment": return (event.get("comment") or {}).get("body","").strip()
    if ev == "issues": return (event.get("issue") or {}).get("body","").strip()
    return ""

def actor_handle() -> str:
    ev = os.environ.get("GH_EVENT_NAME", "")
    if ev == "workflow_dispatch":
        repo = os.environ.get("GITHUB_REPOSITORY", "")
        return repo.split("/")[0] if "/" in repo else repo
    try:
        event = json.load(open(os.environ["GITHUB_EVENT_PATH"], "r", encoding="utf-8"))
        return (event.get("sender") or {}).get("login", "")
    except Exception:
        return ""

def is_allowed(actor: str) -> bool:
    allowlist = os.environ.get("CODEX_ALLOWLIST", "").strip()
    if allowlist:
        return actor in [a.strip() for a in allowlist.split(",") if a.strip()]
    repo = os.environ.get("GITHUB_REPOSITORY", "")
    try:
        perms = gh_api("GET", f"/repos/{repo}/collaborators/{actor}/permission")
        return perms.get("permission") in ("write","maintain","admin")
    except Exception:
        return False

def _now_iso(): return datetime.utcnow().strftime("%Y-%m-%dT%H:%M:%SZ")
def _ts():      return datetime.utcnow().strftime("%Y%m%d-%H%M%S")

# ------------------------ Repo Index / Tree ------------------------

IGNORES = {
    ".git", ".gradle", ".idea", "build", "app/build", "gradle/build",
    ".github/codex/codex.patch", ".github/codex/tree.txt"
}

def _is_ignored(path: Path) -> bool:
    p = str(path).replace("\\", "/")
    if any(part.startswith(".git") for part in p.split("/")): return True
    if any(p == ig or p.startswith(ig + "/") for ig in IGNORES): return True
    return False

def build_repo_index():
    try:    files = [Path(p) for p in sh("git ls-files").splitlines() if p.strip()]
    except Exception: files = [p for p in Path(".").rglob("*") if p.is_file()]
    files = [p for p in files if not _is_ignored(p)]
    full_list = "\n".join(sorted(str(p).replace("\\", "/") for p in files))
    def compress(path_strs: Iterable[str], max_depth=4):
        out = []
        for s in sorted(path_strs):
            parts = s.split("/")
            out.append("/".join(parts[:max_depth]) + ("/…" if len(parts) > max_depth else ""))
        dedup, last = [], None
        for line in out:
            if line != last: dedup.append(line); last = line
        return "\n".join(dedup)
    compact = compress((str(p).replace("\\", "/") for p in files), max_depth=4)
    os.makedirs(".github/codex", exist_ok=True)
    Path(".github/codex/tree.txt").write_text(full_list, encoding="utf-8")
    return files, compact, full_list

def post_repo_tree_comment(compact_tree: str):
    comment_reply("📁 **Repo-Verzeichnisbaum (kompakt, Tiefe ≤ 4)**\n```\n" + compact_tree[:6000] + "\n```\n_Vollständige Liste_: `.github/codex/tree.txt`")

# ------------------------ Prompt/Doku Utils ------------------------

def trim_to_chars(s: str, limit: int) -> str:
    if s is None: return ""
    return s if len(s) <= limit else s[:limit]

def load_docs(max_chars: int = MAX_DOC_CHARS) -> str:
    blobs, remain = [], max_chars
    for name in DOC_CANDIDATES:
        if os.path.exists(name) and remain > 0:
            try:
                txt = Path(name).read_text(encoding="utf-8", errors="replace")
                cut = txt[:min(len(txt), remain)]
                blobs.append(f"\n--- {name} ---\n{cut}")
                remain -= len(cut)
            except Exception as e:
                log_warn(f"Doku konnte nicht gelesen werden: {name}: {type(e).__name__}")
    return "".join(blobs)

# ------------------------ Relevanz & OpenAI ------------------------

def top_relevant_files(instruction: str, all_files: list[str], k: int = 80):
    words = [w.lower() for w in re.findall(r"[A-Za-z_][A-Za-z0-9_]+", instruction) if len(w) >= 3]
    if not words: return all_files[:k]
    scores = []
    for f in all_files:
        score = 0
        fl = f.lower()
        for w in words:
            if w in fl: score += 2
        try:
            head = sh(f"sed -n '1,140p' {shlex.quote(f)} | tr '\\t' ' '", check=False).lower()
            for w in words:
                if w in head: score += 1
        except Exception:
            pass
        scores.append((score, f))
    scores.sort(key=lambda x: x[0], reverse=True)
    res = [f for s,f in scores if s>0][:k]
    return res or all_files[:k]

def _compute_base_url() -> str:
    raw = os.environ.get("OPENAI_BASE_URL", "").strip()
    if not raw: return "https://api.openai.com/v1"
    if not raw.startswith(("http://","https://")): raw = "https://" + raw
    if not re.search(r"/v\d+/?$", raw): raw = raw.rstrip("/") + "/v1"
    return raw

def _env_int(name: str, default: int) -> int:
    try: return int(os.environ.get(name, str(default)))
    except Exception: return default

class MinuteRateLimiter:
    def __init__(self, tpm_budget: int, rpm_budget: int):
        self.tpm_budget=tpm_budget; self.rpm_budget=rpm_budget
        self.tokens_used=0; self.calls_made=0; self.window_start=time.time()
    def _maybe_reset(self):
        if time.time()-self.window_start>=60.0:
            self.tokens_used=0; self.calls_made=0; self.window_start=time.time()
    def wait_for(self, tokens_needed: int):
        self._maybe_reset()
        if self.tpm_budget<=0 and self.rpm_budget<=0: return
        while True:
            self._maybe_reset()
            ok_tokens = (self.tpm_budget<=0) or (self.tokens_used+tokens_needed<=self.tpm_budget)
            ok_calls  = (self.rpm_budget<=0) or (self.calls_made+1<=self.rpm_budget)
            if ok_tokens and ok_calls: break
            time.sleep( min(60.0, 60.0-(time.time()-self.window_start)) )
    def book(self, tokens_used: int):
        self._maybe_reset()
        self.tokens_used += max(0, tokens_used); self.calls_made += 1

_RATE_LIMITER = MinuteRateLimiter(_env_int("OPENAI_TPM_BUDGET", 400000), _env_int("OPENAI_RPM_BUDGET", 300))

def _estimate_tokens_from_text(*parts: str) -> int:
    total_chars = sum(len(p or "") for p in parts)
    return int(total_chars/4) + 256

def openai_generate(model: str, system_prompt: str, user_prompt: str) -> str:
    try:
        from openai import OpenAI
    except Exception as e:
        die("OpenAI SDK nicht installiert", f"{type(e).__name__}: {e}", "pip install openai>=1.40.0")
    client = OpenAI(api_key=os.environ.get("OPENAI_API_KEY",""), base_url=_compute_base_url())
    if not os.environ.get("OPENAI_API_KEY"):
        log_warn("OPENAI_API_KEY ist nicht gesetzt – Generierung könnte fehlschlagen.")
    effort = os.environ.get("OPENAI_REASONING_EFFORT","high").lower()
    if effort not in ("high","medium","low"): effort="high"
    MAX_RETRIES=_env_int("OPENAI_MAX_RETRIES",8); BASE_SLEEP=float(os.environ.get("OPENAI_BASE_SLEEP","1.5")); MAX_SLEEP=float(os.environ.get("OPENAI_MAX_SLEEP","60"))
    est_in = _estimate_tokens_from_text(system_prompt, user_prompt); _RATE_LIMITER.wait_for(est_in)
    def _retryable(m:str)->bool:
        m=m.lower(); return any(k in m for k in ["rate","429","quota","temporar","timeout","connection","gateway","server","502","503","504"])
    def _try(fn):
        last=None
        for i in range(MAX_RETRIES):
            try: return fn()
            except Exception as e:
                last=e; msg=str(e)
                if _retryable(msg):
                    sl=min(MAX_SLEEP, BASE_SLEEP*(2**i))+random.uniform(0,0.4)
                    log_warn(f"OpenAI transient error, retry {i+1}/{MAX_RETRIES} after {sl:.1f}s: {msg[:160]}"); time.sleep(sl); continue
                log_error("OpenAI call failed (non-retryable)", traceback.format_exc()); raise
        log_error("OpenAI call failed after retries", str(last)); raise last
    def _responses():
        resp = client.responses.create(model=model, input=[{"role":"system","content":system_prompt},{"role":"user","content":user_prompt}], reasoning={"effort":effort})
        text = getattr(resp,"output_text","") or ""
        usage = getattr(resp,"usage",None); total = (usage.get("total_tokens") if isinstance(usage,dict) else None) or est_in+_estimate_tokens_from_text(text)
        _RATE_LIMITER.book(total); return text
    def _chat():
        cc=client.chat.completions.create(model=model, messages=[{"role":"system","content":system_prompt},{"role":"user","content":user_prompt}])
        text = cc.choices[0].message.content or ""; usage=getattr(cc,"usage",None); total = (getattr(usage,"total_tokens",None) if usage else None) or est_in+_estimate_tokens_from_text(text)
        _RATE_LIMITER.book(total); return text
    try: return _try(_responses)
    except Exception: 
        try: return _try(_chat)
        except Exception as ee:
            comment_reply(f"❌ OpenAI-Fehler:\n```\n{ee}\n```")
            die("OpenAI-Aufruf endgültig fehlgeschlagen", f"{type(ee).__name__}: {ee}", "API-Key/Quota/Region/Modell prüfen.")
            raise

# ------------------------ Diff Sanitize / Parse / Remap ------------------------

def sanitize_patch_text(raw: str) -> str:
    if not raw: return raw
    m = re.search(r"```(?:diff)?\s*(.*?)```", raw, re.S)
    if m: raw = m.group(1)
    i = raw.find("diff --git ")
    if i != -1: raw = raw[i:]
    raw = raw.replace("\r\n","\n").replace("\r","\n")
    if not raw.endswith("\n"): raw += "\n"
    lines = raw.split("\n")
    if not lines or not lines[0].startswith("diff --git "):
        for idx, ln in enumerate(lines):
            if ln.startswith("diff --git "): lines = lines[idx:]; break
        raw = "\n".join(lines)
    return raw

def parse_patch_sections(patch_text: str):
    sections=[]
    for m in re.finditer(r"^diff --git a/(\S+)\s+b/(\S+)\s*$", patch_text, re.M):
        start=m.start(); next_m=re.search(r"^diff --git ", patch_text[m.end():], re.M)
        end=m.end() + (next_m.start() if next_m else len(patch_text)-m.end())
        body=patch_text[start:end]; oldp, newp = m.group(1), m.group(2)
        is_add = bool(re.search(r"(?m)^--- /dev/null\s*$", body))
        is_del = bool(re.search(r"(?m)^\+\+\+ /dev/null\s*$", body))
        is_rename = bool(re.search(r"(?m)^rename (from|to) ", body) or re.search(r"(?m)^similarity index ", body))
        sections.append({"old":oldp,"new":newp,"body":body,"is_add":is_add,"is_del":is_del,"is_rename":is_rename})
    return sections

def best_match_path(target_path: str, repo_files):
    target = target_path.replace("\\","/"); tbase = os.path.basename(target)
    repo_norm = [str(p).replace("\\","/") for p in repo_files]
    candidates = [p for p in repo_norm if os.path.basename(p)==tbase]
    if candidates:
        tparts=target.split("/")
        def score(p):
            parts=p.split("/"); suf=0
            for a,b in zip(reversed(tparts), reversed(parts)):
                if a==b: suf+=1
                else: break
            import difflib as _df
            ratio=_df.SequenceMatcher(a="/".join(tparts[-4:]), b="/".join(parts[-4:])).ratio()
            return (suf, ratio, -len(parts))
        return sorted(candidates, key=score, reverse=True)[0]
    import difflib as _df
    scored=sorted(((_df.SequenceMatcher(a=target,b=p).ratio(),p) for p in repo_norm), reverse=True)
    return scored[0][1] if scored and scored[0][0]>=0.6 else None

def rewrite_section_paths(section: str, new_path: str):
    section=re.sub(r"^(diff --git a/)\S+(\s+b/)\S+", rf"\1{new_path}\2{new_path}", section, count=1, flags=re.M)
    section=re.sub(r"(?m)^(--- )a/\S+$", rf"\1a/{new_path}", section)
    section=re.sub(r"(?m)^(\+\+\+ )b/\S+$", rf"\1b/{new_path}", section)
    return section

def try_remap_patch(patch_text: str, repo_files):
    sections=parse_patch_sections(patch_text)
    if not sections: return patch_text, [], []
    remapped, unresolved, new_sections = [], [], []
    for s in sections:
        body,newp = s["body"], s["new"]
        if s["is_add"] or s["is_rename"]: new_sections.append(body); continue
        if os.path.exists(newp): new_sections.append(body); continue
        cand=best_match_path(newp, repo_files)
        if cand:
            new_sections.append(rewrite_section_paths(body, cand)); remapped.append((newp,cand))
        else:
            unresolved.append(newp)
    return "".join(new_sections), remapped, unresolved

# ------------------------ Savestates & History ------------------------

def make_savestate_pre(default_branch: str, label: str) -> dict:
    ts=_ts()
    pre_branch=f"codex/savestate/{ts}-{label}"
    pre_tag=f"codex-pre-{ts}"
    sh(f"git fetch origin {shlex.quote(default_branch)}", check=False)
    sh(f"git checkout -b {pre_branch} origin/{default_branch}")
    sh(f"git tag -a {pre_tag} -m 'Pre-codex savestate {ts} on {default_branch}'", check=False)
    os.makedirs(f".github/codex/history/{ts}", exist_ok=True)
    Path(f".github/codex/history/{ts}/_meta.json").write_text(json.dumps({"ts":ts,"phase":"pre","base":default_branch}, indent=2), encoding="utf-8")
    return {"ts":ts, "pre_branch":pre_branch, "pre_tag":pre_tag}

def make_savestate_post(state: dict, head_branch: str, applied: list[str], rejected: list[str], skipped: list[str], instruction: str):
    ts=state["ts"]
    post_tag=f"codex-post-{ts}"
    # persist artifacts
    hist=f".github/codex/history/{ts}"
    try:
        if Path(".github/codex/codex.patch").exists():
            Path(hist+"/codex.patch").write_text(Path(".github/codex/codex.patch").read_text(encoding="utf-8"), encoding="utf-8")
    except Exception: pass
    Path(hist+"/apply_result.json").write_text(json.dumps({"applied":applied,"rejected":rejected,"skipped":skipped,"instruction":instruction}, indent=2), encoding="utf-8")
    # add & commit history files on head branch
    sh("git add -A")
    sh(f"git commit -m 'codex: attach history artifacts ({ts})'", check=False)
    sh(f"git tag -a {post_tag} -m 'Post-codex savestate {ts}'", check=False)
    return {"post_tag":post_tag}

# ------------------------ Apply Sections ------------------------

def try_apply_sections_file(patch_full_text: str) -> tuple[list[str], list[str], list[str]]:
    sections = parse_patch_sections(patch_full_text)
    if not sections:
        tmp=".github/codex/_tmp_all.patch"; Path(tmp).write_text(patch_full_text, encoding="utf-8")
        sh(f"git apply -p0 --whitespace=fix {shlex.quote(tmp)}")
        return (["<whole>"], [], [])
    applied, rejected, skipped = [], [], []
    for idx, sec in enumerate(sections, 1):
        tmp = f".github/codex/section_{idx}.patch"
        Path(tmp).write_text(sec["body"], encoding="utf-8")
        ok3 = subprocess.run(f"git apply --check -3 -p0 {shlex.quote(tmp)}", shell=True, text=True, capture_output=True)
        if ok3.returncode == 0:
            sh(f"git apply -3 -p0 --whitespace=fix {shlex.quote(tmp)}"); applied.append(sec["new"]); continue
        ok = subprocess.run(f"git apply --check -p0 {shlex.quote(tmp)}", shell=True, text=True, capture_output=True)
        if ok.returncode == 0:
            sh(f"git apply -p0 --whitespace=fix {shlex.quote(tmp)}"); applied.append(sec["new"]); continue
        # normalize line endings and retry
        data = Path(tmp).read_text(encoding="utf-8", errors="replace").replace("\r\n","\n").replace("\r","\n")
        Path(tmp).write_text(data, encoding="utf-8")
        ok3b = subprocess.run(f"git apply --check -3 -p0 {shlex.quote(tmp)}", shell=True, text=True, capture_output=True)
        if ok3b.returncode == 0:
            sh(f"git apply -3 -p0 --whitespace=fix {shlex.quote(tmp)}"); applied.append(sec["new"]); continue
        ok2 = subprocess.run(f"git apply --check -p0 {shlex.quote(tmp)}", shell=True, text=True, capture_output=True)
        if ok2.returncode == 0:
            sh(f"git apply -p0 --whitespace=fix {shlex.quote(tmp)}"); applied.append(sec["new"]); continue
        try:
            sh(f"git apply -p0 --reject --whitespace=fix {shlex.quote(tmp)}"); rejected.append(sec["new"])
        except Exception:
            skipped.append(sec["new"])
    return applied, rejected, skipped

# ------------------------ Workflow Dispatch & Logs ------------------------

def get_default_branch(repo_full: str) -> str:
    return gh_api("GET", f"/repos/{repo_full}").get("default_branch", "main")

def dispatch_workflow(repo_full: str, workflow_ident: str, ref_branch: str, inputs: dict | None) -> str:
    path = f"/repos/{repo_full}/actions/workflows/{workflow_ident}/dispatches"
    payload = {"ref": ref_branch}; 
    if inputs: payload["inputs"]=inputs
    gh_api("POST", path, payload)
    log_info(f"Workflow dispatched: {workflow_ident} @ {ref_branch}")
    return _now_iso()

def find_latest_run(repo_full: str, workflow_ident: str, branch: str, since_iso: str, timeout_s=900) -> dict:
    base = f"/repos/{repo_full}/actions/workflows/{workflow_ident}/runs"
    started = time.time()
    while True:
        runs = gh_api("GET", f"{base}?event=workflow_dispatch&branch={branch}")
        items = runs.get("workflow_runs", []) or []
        cand = items[0] if items else {}
        if cand and cand.get("created_at") and cand.get("created_at") >= since_iso:
            rid = cand.get("id")
            while True:
                run = gh_api("GET", f"/repos/{repo_full}/actions/runs/{rid}")
                if run.get("status") == "completed": return run
                if time.time() - started > timeout_s: return run
                time.sleep(5)
        if time.time() - started > timeout_s:
            die("Kein Workflow-Run gefunden (Timeout)",
                f"workflow={workflow_ident}\nbranch={branch}\nsince={since_iso}\nbase={base}",
                "Dateiname/ID & on: workflow_dispatch prüfen.")
        time.sleep(3)

def download_and_parse_logs(repo_full: str, run_id: int) -> dict:
    resp = gh_api_raw("GET", f"https://api.github.com/repos/{repo_full}/actions/runs/{run_id}/logs", allow_redirects=False)
    loc = resp.headers.get("Location"); data = resp.content if not loc and resp.status_code==200 else gh_api_raw("GET", loc).content
    errors, warnings, files, lines = [], [], 0, 0
    with zipfile.ZipFile(io.BytesIO(data)) as zf:
        for name in zf.namelist():
            if not name.lower().endswith(".txt"): continue
            files += 1
            try: content = zf.read(name).decode("utf-8", errors="replace").splitlines()
            except Exception: continue
            for ln in content:
                lines += 1
                if ERR_PAT.search(ln): errors.append(ln.strip())
                elif WARN_PAT.search(ln): warnings.append(ln.strip())
    def dedup(ar, cap):
        out=[]; seen=set()
        for x in ar:
            k=x.strip()
            if k not in seen: seen.add(k); out.append(k)
            if len(out)>=cap: break
        return out
    return {"errors":dedup(errors,300), "warnings":dedup(warnings,600), "files":files, "lines":lines}

def summarize_run_and_comment(run: dict, logs: dict | None, want_comment: bool):
    status, conclusion, url = run.get("status"), run.get("conclusion"), run.get("html_url")
    body = f"🧪 Build: **{status}/{conclusion or '—'}** — {url}\n"
    if logs:
        body += f"\nLogs: {logs['files']} files / {logs['lines']} lines — Errors: {len(logs['errors'])}, Warnings: {len(logs['warnings'])}\n"
        if logs["errors"]: body += "### Errors (top)\n```\n" + "\n".join(logs["errors"][:80]) + "\n```\n"
        if logs["warnings"]: body += "### Warnings (top)\n```\n" + "\n".join(logs["warnings"][:120]) + "\n```\n"
    if want_comment: comment_reply(body)
    log_info(f"Build result: {status}/{conclusion or '—'} — {url}")

# ------------------------ Auto-PR & Auto-Merge ------------------------

def create_pr_and_try_merge(repo: str, head_branch: str, base_branch: str, title: str, body: str) -> tuple[int, str]:
    pr = gh_api("POST", f"/repos/{repo}/pulls", {"title": title, "head": head_branch, "base": base_branch, "body": body})
    pr_num = pr.get("number"); pr_url = pr.get("html_url")
    log_info(f"PR erstellt: #{pr_num} — {pr_url}")
    # poll mergeability
    try:
        for _ in range(20):
            pr_data = gh_api("GET", f"/repos/{repo}/pulls/{pr_num}")
            m = pr_data.get("mergeable")
            if m is not None: break
            time.sleep(2)
        # try squash, then merge
        try:
            gh_api("PUT", f"/repos/{repo}/pulls/{pr_num}/merge", {"merge_method":"squash", "commit_title":title})
            comment_reply(f"✅ PR #{pr_num} automatisch **gesquasht** in `{base_branch}`.")
        except Exception:
            try:
                gh_api("PUT", f"/repos/{repo}/pulls/{pr_num}/merge", {"merge_method":"merge", "commit_title":title})
                comment_reply(f"✅ PR #{pr_num} automatisch **gemerged** in `{base_branch}`.")
            except Exception as e:
                comment_reply(f"ℹ️ Auto-Merge nicht möglich (Schutzregeln/Checks?). Bitte manuell mergen.\nPR: {pr_url}")
    except Exception as e:
        log_warn(f"Auto-Merge fehlgeschlagen: {type(e).__name__}: {e}")
    return pr_num, pr_url

# ------------------------ Shortcuts (vereinfacht) ------------------------

def _clean_arg(s: str) -> str:
    return (s or "").strip().strip("*`'\"").strip()

def _parse_abis(arg: str) -> str:
    txt=(arg or "").lower(); tokens=re.findall(r"[a-z0-9\-]+", txt); out=[]
    def _add(x): 
        if x not in out: out.append(x)
    for t in tokens:
        if t in ("arm64","arm64-v8a","v8a"): _add("arm64-v8a")
        elif t in ("v7a","armeabi-v7a","armeabi"): _add("armeabi-v7a")
        elif t in ("universal","fat","both"): _add("universal")
    return ",".join(out) if out else ""

def parse_shortcuts(comment_text: str) -> dict:
    t=comment_text or ""
    res={"trigger_build":False,"build_fix_loop":False,"build_inputs":{},"directives":[],"allow_ci":False,"force":False}
    m=re.search(r"--buildrelease(?:\s+([^\n]+))?", t, re.I)
    if m: res["trigger_build"]=True; res["build_fix_loop"]=True; abis=_parse_abis(m.group(1) or ""); res["build_inputs"].update({"build_type":"release"}); 
    if m and abis: res["build_inputs"]["abis"]=abis
    m=re.search(r"--builddebug(?:\s+([^\n]+))?", t, re.I)
    if m: res["trigger_build"]=True; res["build_fix_loop"]=True; abis=_parse_abis(m.group(1) or ""); res["build_inputs"].update({"build_type":"debug"}); 
    if m and abis: res["build_inputs"]["abis"]=abis
    if re.search(r"--allow-ci\b", t, re.I): res["allow_ci"]=True
    if re.search(r"--force\b", t, re.I): res["force"]=True
    return res

# ------------------------ Build-Fix Loop ------------------------

def infer_build_request_from_text(text: str) -> dict:
    t=(text or "").lower(); want_release=("release" in t or "relase" in t)
    abis=[]
    if any(x in t for x in ["arm64","arm64-v8a","v8a"]): abis.append("arm64-v8a")
    if any(x in t for x in ["v7a","armeabi-v7a","armeabi"]): abis.append("armeabi-v7a")
    if "universal" in t or "fat" in t or "both" in t: abis.append("universal")
    abis=list(dict.fromkeys(abis)) or ["arm64-v8a","armeabi-v7a"]
    return {"build_type":("release" if want_release else "debug"), "abis":",".join(abis), "ignore_missing_tdlib_v7a":"false"}

def generate_fix_patch(model: str, docs: str, compact_tree: str, context: str, instruction: str, logs: dict) -> str:
    SYSTEM=("You are an expert Android/Kotlin/Gradle engineer. "
            "Create ONE git unified diff to FIX the build based on the provided build logs. "
            "Minimal but complete; include Gradle/deps/Kotlin options/SDK/resource/code fixes as needed. "
            "Patch must apply at repo root with 'git apply -p0'. No explanations.")
    errors_txt="\n".join(logs.get("errors",[])[:180]); warnings_txt="\n".join(logs.get("warnings",[])[:160])
    USER=f"""Repository documents (trimmed):
{docs or '(no docs found)'}
Repository tree (compact, depth≤4):
{compact_tree}
Focused file heads (top relevance):
{context}
Task:
{instruction}

Build logs (errors top):
{errors_txt}

Build logs (warnings top):
{warnings_txt}

Output:
- Exactly ONE unified diff starting with: diff --git a/... b/...
- Apply at repo root with: git apply -p0
- Include new files with proper headers (new file mode 100644)
- Avoid forbidden paths unless allowed: {', '.join(FORBIDDEN_PATHS)}
"""
    return openai_generate(os.environ.get("OPENAI_MODEL_DEFAULT","gpt-5"), SYSTEM, USER)

def run_build_once_and_maybe_fix(repo: str, default_branch: str, workflow_ident: str,
                                 inputs: dict, instruction: str, docs: str, compact_tree: str, context: str,
                                 allow_ci_changes: bool, force: bool, attempt_idx: int, max_attempts: int) -> tuple[bool, dict]:
    t0 = dispatch_workflow(repo, workflow_ident, default_branch, inputs)
    comment_reply(f"🚀 Versuch {attempt_idx}/{max_attempts}: Workflow **{workflow_ident}** gestartet (inputs={json.dumps(inputs)})")
    run = find_latest_run(repo, workflow_ident, default_branch, t0, timeout_s=DEFAULT_WAIT_TIMEOUTS[1])
    if not run: return False, {}
    logs = download_and_parse_logs(repo, run.get("id"))
    summarize_run_and_comment(run, logs, want_comment=True)
    if run.get("conclusion") == "success": return True, run

    # Build fehlgeschlagen -> Patch generieren
    repo_files, _, _ = build_repo_index()
    all_paths=[str(p).replace("\\","/") for p in repo_files]
    focused=top_relevant_files(instruction, all_paths, k=80)
    preview=[]
    for f in focused:
        try:
            head=sh(f"sed -n '1,160p' {shlex.quote(f)} | sed 's/\\t/    /g' || true", check=False)
            if head.strip(): preview.append(f"\n--- file: {f} ---\n{head}")
        except Exception: pass
    sub_context=trim_to_chars("".join(preview), MAX_CONTEXT_CHARS)

    raw = generate_fix_patch(os.environ.get("OPENAI_MODEL_DEFAULT","gpt-5"), docs, compact_tree, sub_context, instruction, logs)
    patch_text = sanitize_patch_text(raw)
    if "diff --git " not in patch_text:
        comment_reply(f"⚠️ Kein gültiger Diff erkannt. Antwort war:\n```\n{patch_text[:1200]}\n```")
        log_error("Ungültiger OpenAI-Diff", patch_text[:2000]); return False, run
    if not force and patch_text.count("\n") > MAX_PATCH_LINES:
        line_count=patch_text.count("\n")
        log_error("Patch zu groß", f"Zeilen: {line_count}; Limit: {MAX_PATCH_LINES}", "Aufgabe eingrenzen oder --force setzen.")
        return False, run

    # Remap
    remapped_patch, remaps, unresolved = try_remap_patch(patch_text, [Path(p) for p in all_paths])
    if remaps: comment_reply("🧭 Pfad-Remap:\n" + "\n".join([f"- `{a}` → `{b}`" for a,b in remaps]))
    if unresolved: comment_reply("⚠️ Nicht zuordenbare Sektionen (ausgelassen):\n" + "\n".join([f"- {p}" for p in unresolved]))

    # Filter FORBIDDEN unless allowed
    if not allow_ci_changes:
        secs=parse_patch_sections(remapped_patch); kept=[]
        for s in secs:
            if any(s["new"].startswith(fp) or s["old"].startswith(fp) for fp in FORBIDDEN_PATHS): continue
            kept.append(s["body"])
        if kept: remapped_patch="".join(kept)

    # Apply sections (robust)
    applied, rejected, skipped = try_apply_sections_file(remapped_patch)
    if not applied and not rejected and not skipped:
        die("Patch nicht anwendbar (0 Sektionen)", "Kein Abschnitt konnte angewendet werden.", "Diff/Drift prüfen.")

    # Commit & push branch, PR & auto-merge handled by caller after aggregation
    return False, run

# ------------------------ Core Logic ------------------------

def _env_summary():
    keys = ["GITHUB_REPOSITORY","GITHUB_EVENT_PATH","GH_EVENT_NAME","OPENAI_MODEL_DEFAULT","OPENAI_REASONING_EFFORT","OPENAI_BASE_URL","OPENAI_API_KEY","GITHUB_TOKEN"]
    rows=[]
    for k in keys:
        v=os.environ.get(k); shown="set" if (v and k not in ("OPENAI_API_KEY","GITHUB_TOKEN")) else ("set" if v else "missing")
        if k in ("OPENAI_API_KEY","GITHUB_TOKEN") and v: shown="set (hidden)"
        rows.append(f"- {k}: {shown}")
    _write_step_summary("### Environment-Check\n" + "\n".join(rows) + "\n")
    log_info("Environment geprüft:\n" + "\n".join(rows))

def main():
    with step("Environment prüfen"):
        _env_summary()
        if not os.environ.get("GITHUB_TOKEN"):
            die("GITHUB_TOKEN fehlt", hint="Workflow permissions: contents/pull-requests/actions → write")

    with step("Kommentar lesen & Berechtigungen prüfen"):
        comment = read_comment()
        if not comment or "/codex" not in comment:
            log_info("Kein /codex Auftrag — exit 0"); sys.exit(0)
        actor = actor_handle() or "unknown"
        if not is_allowed(actor):
            comment_reply(f"⛔ Sorry @{actor}, du bist nicht berechtigt.")
            die("Permission denied", f"actor={actor}", "Gib Schreibrechte oder setze CODEX_ALLOWLIST.")

        # Flags
        m_model  = re.search(r"(?:^|\s)(?:-m|--model)\s+([A-Za-z0-9._\-]+)", comment)
        if m_model: os.environ["OPENAI_MODEL_DEFAULT"]=m_model.group(1).strip()
        m_reason = re.search(r"(?:^|\s)--reason\s+(high|medium|low)\b", comment, re.I)
        if m_reason: os.environ["OPENAI_REASONING_EFFORT"]=m_reason.group(1).lower()
        allow_ci  = bool(re.search(r"(?:^|\s)--allow-ci\b", comment))
        force_big = bool(re.search(r"(?:^|\s)--force\b", comment))
        m_workflow = re.search(r"(?:^|\s)--workflow\s+([^\s]+)", comment)
        workflow_flag = m_workflow.group(1).strip() if m_workflow else ""
        m_inputs = re.search(r"(?:^|\s)--inputs\s+(\{.*?\}|'.*?'|\".*?\")", comment, re.S)
        inputs_raw = (m_inputs.group(1).strip() if m_inputs else "")

        sc = parse_shortcuts(comment)
        allow_ci = allow_ci or sc["allow_ci"]; force_big = force_big or sc["force"]

        instruction_raw = re.sub(r"^.*?/codex", "", comment, flags=re.S).strip()
        for pat in [
            r"(?:^|\s)(?:-m|--model)\s+[A-Za-z0-9._\-]+", r"(?:^|\s)--reason\s+(?:high|medium|low)\b",
            r"(?:^|\s)--allow-ci\b", r"(?:^|\s)--force\b", r"(?:^|\s)--workflow\s+[^\s]+",
            r"(?:^|\s)--inputs\s+(\{.*?\}|'.*?'|\".*?\")",
        ]: instruction_raw = re.sub(pat, "", instruction_raw, flags=re.I)
        instruction = trim_to_chars(instruction_raw.strip(), MAX_USER_CHARS)

    with step("Repo-Index erstellen"):
        repo_files, compact_tree, _ = build_repo_index()
        post_repo_tree_comment(compact_tree)

    repo = os.environ.get("GITHUB_REPOSITORY"); default_branch = get_default_branch(repo)

    # Savestate PRE
    with step("Savestate (pre)"):
        state = make_savestate_pre(default_branch, actor_handle() or "actor")

    # Build-Pfad?
    natural = (comment or "").lower()
    wants_build = sc["trigger_build"] or bool(workflow_flag) or any(k in natural for k in ["release build","apk","aab","build","erstelle","rebuild"])

    head_branch = f"codex/{_ts()}-{(actor_handle() or 'actor')}"
    with step("Arbeitsbranch vorbereiten"):
        sh("git config user.name 'codex-bot'"); sh("git config user.email 'actions@users.noreply.github.com'")
        sh(f"git checkout -b {head_branch}")

    applied_total=[]; rejected_total=[]; skipped_total=[]

    if wants_build:
        with step("Build/Dispatch & Fix-Loop"):
            workflow_ident = (workflow_flag or DEFAULT_WORKFLOW_FILE)
            if not (workflow_ident.endswith(".yml") or workflow_ident.endswith(".yaml") or workflow_ident.isdigit()):
                workflow_ident += ".yml"
            # Inputs
            if inputs_raw:
                try:
                    j=inputs_raw
                    if (j.startswith("'") and j.endswith("'")) or (j.startswith('"') and j.endswith('"')): j=j[1:-1]
                    inputs=json.loads(j) if j.strip() else None
                except Exception as e:
                    log_warn(f"--inputs JSON parse error: {type(e).__name__}: {e}"); inputs=None
            else:
                inputs = infer_build_request_from_text(natural); inputs.update(sc["build_inputs"])

            docs = trim_to_chars(load_docs(MAX_DOC_CHARS), MAX_DOC_CHARS)
            all_paths=[str(p).replace("\\","/") for p in repo_files]
            preview=[]
            for f in top_relevant_files(instruction or "android build", all_paths, k=80):
                try:
                    head=sh(f"sed -n '1,160p' {shlex.quote(f)} | sed 's/\\t/    /g' || true", check=False)
                    if head.strip(): preview.append(f"\n--- file: {f} ---\n{head}")
                except Exception: pass
            context = trim_to_chars("".join(preview), MAX_CONTEXT_CHARS)

            for attempt in range(1, DEFAULT_MAX_ATTEMPTS+1):
                success,_ = run_build_once_and_maybe_fix(
                    repo=repo, default_branch=default_branch, workflow_ident=workflow_ident,
                    inputs=inputs or {}, instruction=instruction or "Fixiere Buildfehler und baue Release-APKs.",
                    docs=docs, compact_tree=compact_tree, context=context,
                    allow_ci_changes=allow_ci, force=force_big,
                    attempt_idx=attempt, max_attempts=DEFAULT_MAX_ATTEMPTS
                )
                # tally applied files from working tree status
                status = sh("git status --porcelain")
                if status.strip():
                    sh("git add -A"); sh(f"git commit -m 'codex: build fix attempt {attempt}'")
                if success:
                    break
            # Ende Build Loop

    else:
        with step("Patch aus OpenAI erzeugen & anwenden"):
            all_paths=[str(p).replace("\\","/") for p in repo_files]
            preview=[]
            for f in top_relevant_files(instruction, all_paths, k=80):
                try:
                    head=sh(f"sed -n '1,160p' {shlex.quote(f)} | sed 's/\\t/    /g' || true", check=False)
                    if head.strip(): preview.append(f"\n--- file: {f} ---\n{head}")
                except Exception: pass
            context=trim_to_chars("".join(preview), MAX_CONTEXT_CHARS)
            docs=trim_to_chars(load_docs(MAX_DOC_CHARS), MAX_DOC_CHARS)

            SYSTEM=("You are an expert Android/Kotlin engineer. Produce ONE git unified diff applying at repo root.")
            USER=f"""Docs:
{docs or '(none)'}
Tree:
{compact_tree}
Focus:
{context}
Task:
{instruction}
Output: exactly ONE unified diff (apply with git apply -p0)."""
            raw = openai_generate(os.environ.get("OPENAI_MODEL_DEFAULT","gpt-5"), SYSTEM, USER)
            patch_text=sanitize_patch_text(raw)
            if "diff --git " not in patch_text:
                comment_reply(f"⚠️ Kein gültiger Diff erkannt. Antwort war:\n```\n{patch_text[:1400]}\n```")
                die("Ungültiger Diff", patch_text[:2000])
            if not force_big and patch_text.count("\n") > MAX_PATCH_LINES:
                die("Patch zu groß", f"Zeilen: {patch_text.count('\n')}; Limit: {MAX_PATCH_LINES}", "Eingrenzen oder --force")
            try: PatchSet.from_string(patch_text)
            except Exception as e: log_warn(f"Unidiff warnte: {type(e).__name__}: {e}")

            # Remap & filter
            remapped, remaps, unresolved = try_remap_patch(patch_text, [Path(p) for p in repo_files])
            if remaps: comment_reply("🧭 Pfad-Remap:\n" + "\n".join([f"- `{a}` → `{b}`" for a,b in remaps]))
            if unresolved: comment_reply("⚠️ Nicht zuordenbare Sektionen (ausgelassen):\n" + "\n".join([f"- {p}" for p in unresolved]))
            if not allow_ci:
                secs=parse_patch_sections(remapped); kept=[]
                for s in secs:
                    if any(s["new"].startswith(fp) or s["old"].startswith(fp) for fp in FORBIDDEN_PATHS): continue
                    kept.append(s["body"])
                if kept: remapped="".join(kept)

            applied, rejected, skipped = try_apply_sections_file(remapped)
            if not applied and not rejected and not skipped:
                die("Patch nicht anwendbar (0 Sektionen)", "Kein Abschnitt angewendet.", "Diff/Drift prüfen.")
            applied_total += applied; rejected_total += rejected; skipped_total += skipped

            status = sh("git status --porcelain")
            if not status.strip():
                die("Keine Änderungen nach Patch", "Alle Sektionen verworfen?", "Evtl. --allow-ci nötig.")
            sh("git add -A"); sh("git commit -m 'codex: apply requested changes'")

    # Savestate POST + History
    with step("Savestate (post) & History"):
        post = make_savestate_post(state, head_branch, applied_total, rejected_total, skipped_total, instruction)
        sh("git push --set-upstream origin HEAD")  # push head_branch with history

    # PR & Auto-Merge -> main
    with step("PR & Auto-Merge → main"):
        title = (f"codex changes: {instruction[:60]}" if instruction else f"codex changes")
        body = f"Automatisch erstellt aus Kommentar von @{actor_handle()}.\n\n_Repo-Tree unter `.github/codex/tree.txt`._"
        pr_num, pr_url = create_pr_and_try_merge(repo, head_branch, default_branch, title, body)
        comment_reply(f"✅ PR erstellt: #{pr_num} — {pr_url}")

    log_info("done")

if __name__ == "__main__":
    try:
        main()
    except SystemExit:
        raise
    except Exception:
        die("Unerwarteter Fehler im Codex-Bot", traceback.format_exc(), "Siehe Trace oben; häufig Rechte/Netzwerk/Workflow-ID.")
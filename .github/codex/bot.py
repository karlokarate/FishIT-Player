#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
Codex Bot – Build+Fix Edition (+ Shortcuts)  —  Shell-Error-First

Neu:
- Ausführliche Shell-Fehlermeldungen (GitHub Actions annotations ::error/::warning/::notice)
- Gruppierung der Logs (::group:: / ::endgroup::), Step-Dauer
- Einheitliche die()/exit_with_error() + Step-Summary in GITHUB_STEP_SUMMARY
- sh()/gh_api() geben bei Fehlern Befehl/Pfad/Status/STDOUT/STDERR/Antwort-Snippets aus + Fix-Hinweise

Bestehendes:
- /codex-Aufträge aus Issues/Kommentaren
- 20+ Shortcuts (wire, tests, buildrelease, gif, depsync, gradleopt, lintfix, strict, graph, migrate-kotlin, integrate, autofix, doctor, tdlib-ignore, bump-sdk, telemetry)
- Build-Dispatch + Auto-Fix-Loop (Logs analysieren -> Patch -> PR -> Rebuild) bis success oder Max-Versuche
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

# Prompt-Budgets (ca. chars; grob ~ 4 chars ≈ 1 Token)
MAX_DOC_CHARS     = 24000
MAX_CONTEXT_CHARS = 220000
MAX_USER_CHARS    = 4000

# Safeguards
FORBIDDEN_PATHS = [".github/workflows/"]           # nur mit --allow-ci ändern
MAX_PATCH_LINES = 3000                             # zu große Patches blocken, außer --force

# Build/Dispatch defaults
DEFAULT_WORKFLOW_FILE = "release-apk.yml"          # unter .github/workflows/
DEFAULT_MAX_ATTEMPTS  = 4
DEFAULT_WAIT_TIMEOUTS = (30, 1800)  # (no-wait, wait) Sekunden

# Log-Erkennung
WARN_PAT = re.compile(r"\b(warning|warn|w:)\b", re.I)
ERR_PAT  = re.compile(r"\b(error|fail(ed)?|e:)\b", re.I)

# ------------------------ Logging & Errors (Shell-first) ------------------------

def _event_name() -> str:
    """Prefer native GITHUB_EVENT_NAME; allow GH_EVENT_NAME override for local tests."""
    return os.environ.get("GITHUB_EVENT_NAME") or os.environ.get("GH_EVENT_NAME", "")

def _is_ci() -> bool:
    return os.environ.get("GITHUB_ACTIONS") == "true"

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
    if not s:
        return s
    for key in ["OPENAI_API_KEY", "GITHUB_TOKEN"]:
        v = os.environ.get(key)
        if v and isinstance(s, str):
            s = s.replace(v, "***")
    return s

def log_group(title: str) -> None:
    print(f"::group::{title}")

def log_end_group() -> None:
    print("::endgroup::")

def log_info(msg: str) -> None:
    print(f"::notice::{msg}")

def log_warn(msg: str) -> None:
    print(f"::warning::{msg}")

def log_error(title: str, details: str = "", hint: str = "") -> None:
    lines = [title]
    if details:
        lines.append("")
        lines.append(_redact(details))
    if hint:
        lines.append("")
        lines.append(f"HINT: {hint}")
    text = "\n".join(lines)
    sys.stderr.write(f"::error::{text}\n")

def die(title: str, details: str = "", hint: str = "", exit_code: int = 1) -> None:
    """Print rich error to shell (and to GITHUB_STEP_SUMMARY), then exit."""
    log_error(title, details, hint)
    _write_step_summary(f"### ❌ {title}\n\n"
                        + (f"```\n{_redact(details)}\n```\n\n" if details else "")
                        + (f"**Hint:** {hint}\n" if hint else ""))
    sys.exit(exit_code)

from contextlib import contextmanager
@contextmanager
def step(title: str):
    """Group logs; on error, print rich diagnostics."""
    t0 = time.time()
    log_group(title)
    try:
        yield
        dt = int(time.time() - t0)
        log_info(f"{title} — OK in {dt}s")
    except Exception as e:
        dt = int(time.time() - t0)
        tb = traceback.format_exc()
        log_error(f"{title} — FAILED after {dt}s", details=tb)
        raise
    finally:
        log_end_group()

# ------------------------ Shell / Git / HTTP Utils ------------------------

def sh(cmd: str, check: bool = True) -> str:
    """Run shell command; on failure print full diagnostics to shell."""
    res = subprocess.run(cmd, shell=True, text=True, capture_output=True)
    if check and res.returncode != 0:
        stdout = (res.stdout or "").strip()
        stderr = (res.stderr or "").strip()
        details = textwrap.dedent(f"""
            Command: {cmd}
            Exit code: {res.returncode}
            --- STDOUT (truncated) ---
            {stdout[:4000]}
            --- STDERR (truncated) ---
            {stderr[:4000]}
        """).strip()
        log_error("Shell command failed", details=details,
                  hint="Prüfe den Befehl und Umgebung (Pfade, Rechte, Tooling). "
                       "Bei Git-Fehlern: existiert Branch/Remote? Bei Gradle: stimmt Pfad/Task?")
        raise RuntimeError(f"cmd failed: {cmd}\nSTDOUT:\n{stdout}\nSTDERR:\n{stderr}")
    return (res.stdout or "").strip()

def gh_api(method: str, path: str, payload=None):
    url = f"https://api.github.com{path}"
    headers = {
        "Authorization": f"Bearer {os.environ.get('GITHUB_TOKEN','')}",
        "Accept": "application/vnd.github+json"
    }
    try:
        resp = requests.request(method, url, headers=headers, json=payload, timeout=30)
    except Exception as e:
        log_error("GitHub API request failed (network)",
                  details=f"{method} {url}\n{type(e).__name__}: {e}",
                  hint="Ist die Runner-Network-Connectivity ok? DNS/Firewall prüfen.")
        raise
    if resp.status_code >= 300:
        snippet = (resp.text or "")[:4000]
        details = textwrap.dedent(f"""
            {method} {path}
            Status: {resp.status_code}
            Payload: {_redact(json.dumps(payload, ensure_ascii=False)[:2000]) if payload else '—'}
            Response (truncated):
            {snippet}
        """).strip()
        hint = "Repo-Token-Permissions prüfen (Settings → Actions → Workflow permissions → Read and write). "\
               "Stimmt der API-Pfad (Workflow-Datei/ID, Branch, Rechte)?"
        log_error("GitHub API returned an error", details=details, hint=hint)
        raise RuntimeError(f"GitHub API {method} {path} failed: {resp.status_code} {snippet}")
    if resp.text and resp.headers.get("content-type","").startswith("application/json"):
        return resp.json()
    return {}

def gh_api_raw(method: str, url: str, allow_redirects=True):
    headers = {
        "Authorization": f"Bearer {os.environ.get('GITHUB_TOKEN','')}",
        "Accept": "application/vnd.github+json"
    }
    try:
        resp = requests.request(method, url, headers=headers,
                                allow_redirects=allow_redirects, stream=True, timeout=60)
    except Exception as e:
        log_error("GitHub RAW request failed (network)",
                  details=f"{method} {url}\n{type(e).__name__}: {e}")
        raise
    if resp.status_code >= 300 and resp.status_code not in (301, 302):
        snippet = (resp.text or "")[:500]
        log_error("GitHub RAW returned an error",
                  details=f"{method} {url}\nStatus: {resp.status_code}\n{snippet}")
        raise RuntimeError(f"GitHub RAW {method} {url} failed: {resp.status_code} {snippet}")
    return resp

def comment_reply(body: str):
    try:
        event = json.load(open(os.environ["GITHUB_EVENT_PATH"], "r", encoding="utf-8"))
    except Exception as e:
        log_warn(f"Issue-Kommentar konnte Event nicht lesen: {type(e).__name__}: {e}")
        return
    repo = os.environ.get("GITHUB_REPOSITORY", "")
    issue_number = None
    if "issue" in event and "number" in event["issue"]:
        issue_number = event["issue"]["number"]
    elif "pull_request" in event and "number" in event["pull_request"]:
        issue_number = event["pull_request"]["number"]
    if issue_number is None:
        log_warn("Kein Issue/PR zum Kommentieren gefunden.")
        return
    try:
        gh_api("POST", f"/repos/{repo}/issues/{issue_number}/comments", {"body": body})
    except Exception as e:
        log_warn(f"Issue-Kommentar fehlgeschlagen: {type(e).__name__}: {e}")

# ------------------------ Diff Sanitize / Parse / Remap ------------------------

def sanitize_patch_text(raw: str) -> str:
    """Strip code fences, cut prelude, normalize newlines, ensure trailing NL."""
    if not raw:
        return raw
    m = re.search(r"```(?:diff)?\s*(.*?)```", raw, re.S)
    if m:
        raw = m.group(1)
    i = raw.find("diff --git ")
    if i != -1:
        raw = raw[i:]
    raw = raw.replace("\r\n", "\n").replace("\r", "\n")
    if not raw.endswith("\n"):
        raw += "\n"
    lines = raw.split("\n")
    if not lines or not lines[0].startswith("diff --git "):
        for idx, ln in enumerate(lines):
            if ln.startswith("diff --git "):
                lines = lines[idx:]; break
        raw = "\n".join(lines)
    return raw

def parse_patch_sections(patch_text: str):
    sections = []
    for m in re.finditer(r"^diff --git a/(\S+)\s+b/(\S+)\s*$", patch_text, re.M):
        start = m.start()
        next_m = re.search(r"^diff --git ", patch_text[m.end():], re.M)
        end = m.end() + (next_m.start() if next_m else len(patch_text) - m.end())
        body = patch_text[start:end]
        oldp, newp = m.group(1), m.group(2)
        is_add = bool(re.search(r"(?m)^--- /dev/null\s*$", body))
        is_del = bool(re.search(r"(?m)^\+\+\+ /dev/null\s*$", body))
        is_rename = bool(re.search(r"(?m)^rename (from|to) ", body) or re.search(r"(?m)^similarity index ", body))
        sections.append({"old": oldp, "new": newp, "body": body, "is_add": is_add, "is_del": is_del, "is_rename": is_rename})
    return sections

def best_match_path(target_path: str, repo_files):
    target = target_path.replace("\\", "/")
    tbase = os.path.basename(target)
    repo_norm = [str(p).replace("\\", "/") for p in repo_files]

    candidates = [p for p in repo_norm if os.path.basename(p) == tbase]
    if candidates:
        tparts = target.split("/")
        def score(p):
            parts = p.split("/")
            suf = 0
            for a, b in zip(reversed(tparts), reversed(parts)):
                if a == b: suf += 1
                else: break
            import difflib as _df
            ratio = _df.SequenceMatcher(
                a="/".join(tparts[-4:]),
                b="/".join(parts[-4:])
            ).ratio()
            return (suf, ratio, -len(parts))
        return sorted(candidates, key=score, reverse=True)[0]

    import difflib as _df
    scored = sorted(((_df.SequenceMatcher(a=target, b=p).ratio(), p) for p in repo_norm), reverse=True)
    return scored[0][1] if scored and scored[0][0] >= 0.6 else None

def rewrite_section_paths(section: str, new_path: str):
    section = re.sub(r"^(diff --git a/)\S+(\s+b/)\S+", rf"\1{new_path}\2{new_path}", section, count=1, flags=re.M)
    section = re.sub(r"(?m)^(--- )a/\S+$", rf"\1a/{new_path}", section)
    section = re.sub(r"(?m)^(\+\+\+ )b/\S+$", rf"\1b/{new_path}", section)
    return section

def try_remap_patch(patch_text: str, repo_files):
    sections = parse_patch_sections(patch_text)
    if not sections:
        return patch_text, [], []

    remapped, unresolved, new_sections = [], [], []

    for s in sections:
        body, newp = s["body"], s["new"]
        if s["is_add"] or s["is_rename"]:
            new_sections.append(body); continue
        if os.path.exists(newp):
            new_sections.append(body); continue
        cand = best_match_path(newp, repo_files)
        if cand:
            new_sections.append(rewrite_section_paths(body, cand))
            remapped.append((newp, cand))
        else:
            unresolved.append(newp)

    return "".join(new_sections), remapped, unresolved

# ------------------------ Fallback: Snippet → gültiger Unified-Diff ------------------------

def _extract_code_block(raw: str) -> tuple[str, str]:
    """Liefert (code, lang) aus ```lang ...``` oder erkennt erste Zeile als Sprache."""
    if not raw:
        return "", ""
    m = re.search(r"```([A-Za-z0-9.+_-]*)\s*([\s\S]*?)```", raw)
    if m:
        return m.group(2), (m.group(1) or "").lower().strip()
    lines = (raw or "").strip().splitlines()
    if not lines:
        return "", ""
    first = lines[0].strip().lower()
    if first in ("kotlin", "java", "groovy", "gradle", "gradle.kts", "kts", "xml", "json", "md"):
        return "\n".join(lines[1:]), first
    return raw.strip(), ""

def _strip_quasi_diff_markers(code: str) -> str:
    """Entfernt führende '+'/'-' in Snippet-Zeilen (behält Inhalt). '-' Zeilen werden standardmäßig verworfen."""
    out = []
    for ln in (code or "").splitlines():
        if ln.startswith("+++ ") or ln.startswith("--- "):
            # Header aus echten Diffs ignorieren
            continue
        if ln.startswith("+"):
            out.append(ln[1:])
        elif ln.startswith("-"):
            # Entferne 'Entfernen'-Zeilen im reinen Snippet-Kontext
            continue
        else:
            out.append(ln)
    text = "\n".join(out).rstrip("\n") + "\n"
    return text

def _slugify(s: str) -> str:
    s = re.sub(r"\s+", "-", s.strip())
    s = re.sub(r"[^A-Za-z0-9._-]", "-", s)
    return s.strip("-")[:40] or "snippet"

def _ext_for_lang(lang: str) -> str:
    m = {
        "kotlin": "kt", "kt": "kt",
        "java": "java",
        "groovy": "gradle",
        "gradle": "gradle", "gradle.kts": "kts", "kts": "kts",
        "xml": "xml", "json": "json", "md": "md",
    }
    return m.get((lang or "").lower(), "txt")

def _make_add_file_diff(path: str, content: str) -> str:
    content = content if content.endswith("\n") else content + "\n"
    lines = content.splitlines()
    plus_block = "\n".join("+" + ln for ln in lines)
    n = len(lines)
    return (
        f"diff --git a/{path} b/{path}\n"
        f"new file mode 100644\n"
        f"index 0000000..1111111\n"
        f"--- /dev/null\n"
        f"+++ b/{path}\n"
        f"@@ -0,0 +{n} @@\n"
        f"{plus_block}\n"
    )

def coerce_snippet_to_diff(raw: str) -> tuple[str, str]:
    """
    Versucht aus einer Nicht-Diff-Antwort einen gültigen Unified-Diff zu bauen.
    Gibt (diff_text, target_path) zurück oder ("", "") bei Fehlschlag.
    """
    code, lang = _extract_code_block(raw or "")
    code = (code or "").strip("\n")
    if not code:
        return "", ""
    clean = _strip_quasi_diff_markers(code)
    # Dateiname aus erster nicht-leerer Codezeile ableiten
    first_line = next((ln for ln in clean.splitlines() if ln.strip()), "snippet")
    slug = _slugify(first_line)
    ext = _ext_for_lang(lang)
    target = f".github/codex/generated/{datetime.utcnow().strftime('%Y%m%d-%H%M%S')}-{slug}.{ext}"
    diff = _make_add_file_diff(target, clean)
    return diff, target

# ------------------------ Robust Section-wise Apply ------------------------

def try_apply_sections(patch_file: str, original_text: str) -> tuple[list[str], list[str], list[str]]:
    sections = parse_patch_sections(original_text)
    if not sections:
        sh(f"git apply -p0 --whitespace=fix {shlex.quote(patch_file)}")
        return (["<whole>"], [], [])

    applied, rejected, skipped = [], [], []

    for idx, sec in enumerate(sections, 1):
        tmp = f".github/codex/section_{idx}.patch"
        with open(tmp, "w", encoding="utf-8") as f:
            f.write(sec["body"])

        ok3 = subprocess.run(f"git apply --check -3 -p0 {shlex.quote(tmp)}",
                             shell=True, text=True, capture_output=True)
        if ok3.returncode == 0:
            sh(f"git apply -3 -p0 --whitespace=fix {shlex.quote(tmp)}")
            applied.append(sec["new"]); continue

        ok = subprocess.run(f"git apply --check -p0 {shlex.quote(tmp)}",
                            shell=True, text=True, capture_output=True)
        if ok.returncode == 0:
            sh(f"git apply -p0 --whitespace=fix {shlex.quote(tmp)}")
            applied.append(sec["new"]); continue

        with open(tmp, "r", encoding="utf-8", errors="replace") as src:
            data = src.read().replace("\r\n", "\n").replace("\r", "\n")
        with open(tmp, "w", encoding="utf-8") as dst:
            dst.write(data)

        ok3b = subprocess.run(f"git apply --check -3 -p0 {shlex.quote(tmp)}",
                              shell=True, text=True, capture_output=True)
        if ok3b.returncode == 0:
            sh(f"git apply -3 -p0 --whitespace=fix {shlex.quote(tmp)}")
            applied.append(sec["new"]); continue

        ok2 = subprocess.run(f"git apply --check -p0 {shlex.quote(tmp)}",
                             shell=True, text=True, capture_output=True)
        if ok2.returncode == 0:
            sh(f"git apply -p0 --whitespace=fix {shlex.quote(tmp)}")
            applied.append(sec["new"]); continue

        try:
            sh(f"git apply -p0 --reject --whitespace=fix {shlex.quote(tmp)}")
            rejected.append(sec["new"])
        except Exception:
            skipped.append(sec["new"])

    return applied, rejected, skipped

# ------------------------ Workflow Dispatch & Log Reporting ------------------------

def get_default_branch(repo_full: str) -> str:
    return gh_api("GET", f"/repos/{repo_full}").get("default_branch", "main")

def dispatch_workflow(repo_full: str, workflow_file: str, ref_branch: str, inputs: dict | None) -> str:
    """Trigger workflow_dispatch. Returns dispatch timestamp (utc iso) used for run correlation."""
    path = f"/repos/{repo_full}/actions/workflows/{workflow_file}/dispatches"
    payload = {"ref": ref_branch}
    if inputs:
        payload["inputs"] = inputs
    gh_api("POST", path, payload)
    log_info(f"Workflow dispatch gesendet: {workflow_file} @ {ref_branch} (inputs={_redact(json.dumps(inputs) if inputs else '—')})")
    return _now_iso()

def find_latest_run(repo_full: str, workflow_file: str, branch: str, since_iso: str, timeout_s=900) -> dict:
    """Poll die Workflow-Runs, bis ein Run nach since_iso auftaucht; warte auf Completion."""
    base = f"/repos/{repo_full}/actions/workflows/{workflow_file}/runs"
    started = time.time()
    first_seen = None
    while True:
        try:
            runs = gh_api("GET", f"{base}?event=workflow_dispatch&branch={branch}")
        except Exception:
            # gh_api hat bereits in die Shell geloggt
            return {}
        items = runs.get("workflow_runs", []) or []
        cand = items[0] if items else {}
        if cand and cand.get("created_at") and cand.get("created_at") >= since_iso:
            run_id = cand.get("id")
            if not first_seen:
                first_seen = cand.get("html_url")
                log_info(f"Run erkannt: {first_seen} — warte auf Abschluss …")
            while True:
                run = gh_api("GET", f"/repos/{repo_full}/actions/runs/{run_id}")
                if run.get("status") == "completed":
                    return run
                if time.time() - started > timeout_s:
                    log_warn(f"Wartezeit überschritten ({timeout_s}s) — gebe aktuellen Status zurück.")
                    return run
                time.sleep(5)
        if time.time() - started > timeout_s:
            die("Kein Workflow-Run gefunden (Timeout)",
                details=f"Workflow: {workflow_file}\nBranch: {branch}\nSeit: {since_iso}\nURL-Base: {base}",
                hint="Stimmt Workflow-Dateiname/ID? Ist on: workflow_dispatch gesetzt? Ist der Branch korrekt?")
        time.sleep(3)

def download_and_parse_logs(repo_full: str, run_id: int) -> dict:
    """Download logs ZIP für einen Run, liefere {errors: [...], warnings: [...], files, lines}."""
    resp = gh_api_raw("GET", f"https://api.github.com/repos/{repo_full}/actions/runs/{run_id}/logs", allow_redirects=False)
    loc = resp.headers.get("Location")
    if not loc:
        if resp.status_code == 200:
            data = resp.content
        else:
            log_warn("Keine Logs-Location im Header – möglicherweise zu früh oder fehlende Berechtigung.")
            return {"errors": ["No logs location from GitHub"], "warnings": [], "files": 0, "lines": 0}
    else:
        z = gh_api_raw("GET", loc)
        data = z.content
    errors, warnings = [], []
    files = lines = 0
    with zipfile.ZipFile(io.BytesIO(data)) as zf:
        for name in zf.namelist():
            if not name.lower().endswith(".txt"):
                continue
            files += 1
            try:
                content = zf.read(name).decode("utf-8", errors="replace").splitlines()
            except Exception as e:
                log_warn(f"Logdatei konnte nicht gelesen werden ({name}): {type(e).__name__}")
                continue
            for ln in content:
                lines += 1
                if ERR_PAT.search(ln):
                    errors.append(ln.strip())
                elif WARN_PAT.search(ln):
                    warnings.append(ln.strip())
    def _dedup_trim(ar, cap):
        seen, out = set(), []
        for x in ar:
            key = x.strip()
            if key not in seen:
                seen.add(key); out.append(key)
            if len(out) >= cap:
                break
        return out
    return {
        "errors": _dedup_trim(errors, 300),
        "warnings": _dedup_trim(warnings, 600),
        "files": files,
        "lines": lines
    }

def summarize_run_and_comment(run: dict, logs: dict | None, want_comment: bool, header: str = ""):
    status = run.get("status")
    conclusion = run.get("conclusion")
    html_url = run.get("html_url")
    head_branch = run.get("head_branch")
    dur = None
    try:
        st = datetime.fromisoformat(run.get("run_started_at").replace("Z","+00:00"))
        en = datetime.fromisoformat(run.get("updated_at").replace("Z","+00:00"))
        dur = f"{int((en-st).total_seconds())}s"
    except Exception:
        pass
    body = ""
    if header:
        body += header + "\n\n"
    body += f"🧪 **Build (`workflow_dispatch`)** → branch `{head_branch}` → **{status}/{conclusion or '—'}** in {dur or '—'}\n\n"
    body += f"Run: {html_url}\n\n"
    if logs:
        body += f"**Logs** (parsed {logs['files']} files / {logs['lines']} lines)\n"
        body += f"- Errors: {len(logs['errors'])}\n- Warnings: {len(logs['warnings'])}\n\n"
        if logs["errors"]:
            body += "### Errors (Top, deduped)\n```\n" + "\n".join(logs["errors"][:120]) + "\n```\n"
        if logs["warnings"]:
            body += "### Warnings (Top, deduped)\n```\n" + "\n".join(logs["warnings"][:160]) + "\n```\n"
    if want_comment:
        comment_reply(body)
    # Auch in die Shell eine kompakte Zusammenfassung
    log_info(f"Build-Result: {status}/{conclusion or '—'} — {html_url}")

# ------------------------ Shortcuts Parsing ------------------------

def _clean_arg(s: str) -> str:
    return (s or "").strip().strip("*`'\"").strip()

def _parse_abis(arg: str) -> str:
    txt = (arg or "").lower()
    tokens = re.findall(r"[a-z0-9\-]+", txt)
    out = []
    def _add(x):
        if x not in out:
            out.append(x)
    for t in tokens:
        if t in ("arm64", "arm64-v8a", "v8a"):
            _add("arm64-v8a")
        elif t in ("v7a", "armeabi-v7a", "armeabi"):
            _add("armeabi-v7a")
        elif t in ("universal", "fat", "both"):
            _add("universal")
    return ",".join(out) if out else ""

def parse_shortcuts(comment_text: str) -> dict:
    """Erkennt Major-Kurzbefehle und liefert Steuer-Flags + zusammengesetzte Instruktionen."""
    t = comment_text or ""
    res = {
        "trigger_build": False,
        "build_fix_loop": False,
        "build_inputs": {},
        "set_workflow": None,
        "directives": [],
        "allow_ci": False,
        "force": False,
    }

    # 1) --wire <module|path>
    m = re.search(r"--wire\s+([^\n]+)", t, re.I)
    if m:
        target = _clean_arg(m.group(1))
        res["directives"].append(
            f"Prüfe und repariere die komplette Verdrahtung des Moduls/Pfads `{target}`: "
            f"Gradle-Dependencies (api/implementation), Imports, Package/visibility, DI/Wiring, "
            f"call graph (alle aufgerufenen Funktionen existieren & Signaturen passen), Ressourcen/IDs. "
            f"Füge Integrations- und Unit-Tests hinzu. Minimal-invasive Patches, aber build-fähig."
        )

    # 2) --wire-all
    if re.search(r"--wire-all\b", t, re.I):
        res["directives"].append(
            "Führe eine modulweite Verdrahtungsprüfung für ALLE Module durch (Dependencies, DI, Imports, "
            "öffentliche API/Oberflächen) und behebe die wichtigsten Befunde. Füge Smoke-Tests hinzu."
        )

    # 3) --tests [path]
    m = re.search(r"--tests(?:\s+([^\n]+))?", t, re.I)
    if m:
        scope = _clean_arg(m.group(1) or "app,core")
        res["directives"].append(
            f"Erzeuge und integriere Unit-Tests für `{scope}` (Edge Cases, Fehlerpfade, Contracts). "
            f"Gradle test deps aktualisieren (junit, mockk/turbine)."
        )

    # 4) --tests-ui [screens]
    m = re.search(r"--tests-ui(?:\s+([^\n]+))?", t, re.I)
    if m:
        screens = _clean_arg(m.group(1) or "Home,Library,Player")
        res["directives"].append(
            f"Schreibe Compose UI-Tests (auch TV/DPAD) für `{screens}`. Stabil (idling), keine flakey Wartezeiten."
        )

    # 5) --tests-int [scope]
    m = re.search(r"--tests-int(?:\s+([^\n]+))?", t, re.I)
    if m:
        scope = _clean_arg(m.group(1) or "core↔data↔ui")
        res["directives"].append(
            f"Schreibe Integrations-Tests für `{scope}` (Fake Repos, In-Memory DB, deterministische Scheduler)."
        )

    # 6) --buildrelease [abis]
    m = re.search(r"--buildrelease(?:\s+([^\n]+))?", t, re.I)
    if m:
        abis = _parse_abis(m.group(1) or "")
        res["trigger_build"] = True
        res["build_fix_loop"] = True
        res["build_inputs"].update({"build_type": "release"})
        if abis:
            res["build_inputs"]["abis"] = abis

    # 7) --builddebug [abis]
    m = re.search(r"--builddebug(?:\s+([^\n]+))?", t, re.I)
    if m:
        abis = _parse_abis(m.group(1) or "")
        res["trigger_build"] = True
        res["build_fix_loop"] = True
        res["build_inputs"].update({"build_type": "debug"})
        if abis:
            res["build_inputs"]["abis"] = abis

    # 8) --gif [screens]
    m = re.search(r"--gif(?:\s+([^\n]+))?", t, re.I)
    if m:
        screens = _clean_arg(m.group(1) or "Home,Library,Player")
        res["allow_ci"] = True
        res["directives"].append(
            f"Füge ein Skript `tools/record_ui.sh` (adb screenrecord + ffmpeg→GIF) und Workflow "
            f"`.github/workflows/ui-preview.yml` hinzu. Erzeuge Aufnahmen für `{screens}` und speichere GIFs in artifacts."
        )

    # 9) --depsync
    if re.search(r"--depsync\b", t, re.I):
        res["directives"].append(
            "Synchronisiere Dependencies/Plugins (Gradle/AGP/Kotlin/Libraries), fixe Versionskonflikte (BOMs), "
            "aktualisiere Repositories, ersetze veraltete Artefakte."
        )

    # 10) --gradleopt
    if re.search(r"--gradleopt\b", t, re.I):
        res["directives"].append(
            "Optimiere Gradle-Buildzeiten: Build-Cache, Konfiguration on demand, KSP statt KAPT, R8/Proguard Setup, "
            "Parallelisierung. Miss Top-10 Tasks und dokumentiere Verbesserungen."
        )

    # 11) --lintfix
    if re.search(r"--lintfix\b", t, re.I):
        res["directives"].append(
            "Führe Lint/detekt/ktlint ein (Gradle Tasks, Baselines). Behebe die wichtigsten 20 Findings automatisch."
        )

    # 12) --strict
    if re.search(r"--strict\b", t, re.I):
        res["directives"].append(
            "Aktiviere Compiler warnings-as-errors, strikte Lint-Profile (fatal ausgewählte Checks) "
            "und behebe relevante Warnungen."
        )

    # 13) --graph
    if re.search(r"--graph\b", t, re.I):
        res["directives"].append(
            "Erzeuge einen Modul-Dependency-Graphen (GraphViz .dot + .svg) und lege ihn unter docs/architecture/ ab. "
            "Füge Gradle-Task/Skript zum Aktualisieren hinzu."
        )

    # 14) --migrate-kotlin [path]
    m = re.search(r"--migrate-kotlin(?:\s+([^\n]+))?", t, re.I)
    if m:
        target = _clean_arg(m.group(1) or "app/src/main/java")
        res["directives"].append(
            f"Wandle `{target}` idiomatisch von Java nach Kotlin um (Nullability, data/sealed, extensions), "
            f"passe Build an (kotlinOptions, stdlib), ergänze Unit-Tests."
        )

    # 15) --integrate [modules]
    m = re.search(r"--integrate(?:\s+([^\n]+))?", t, re.I)
    if m:
        mods = _clean_arg(m.group(1) or "core,data,ui")
        res["directives"].append(
            f"Prüfe das Zusammenspiel der Module `{mods}`: API-Verträge, Exceptions, Threading, Ressourcen-IDs. "
            f"Fixe Diskrepanzen, füge Integrations-Checks hinzu."
        )

    # 16) --autofix
    if re.search(r"--autofix\b", t, re.I):
        res["directives"].append(
            "Erweiterte statische Analyse (detekt/ktlint/Lint streng) und auto-Fixes für häufige Probleme."
        )

    # 17) --doctor
    if re.search(r"--doctor\b", t, re.I):
        res["directives"].append(
            "Richte Health-Checks ein: StrictMode, LeakCanary (Debug), ANR/Crash-Probes. README/CI ergänzen."
        )

    # 18) --tdlib-ignore
    if re.search(r"--tdlib-ignore\b", t, re.I):
        res["build_inputs"]["ignore_missing_tdlib_v7a"] = "true"

    # 19) --bump-sdk [34|35|…]
    m = re.search(r"--bump-sdk(?:\s+(\d+))?", t, re.I)
    if m:
        api = _clean_arg(m.group(1) or "")
        res["directives"].append(
            f"Erhöhe compileSdk/targetSdk{(' auf ' + api) if api else ''}; passe Bibliotheken & Manifeste an."
        )

    # 20) --telemetry
    if re.search(r"--telemetry\b", t, re.I):
        res["directives"].append(
            "Integriere konsistentes Telemetry/Logging (z. B. Timber), strukturierte Logs, Crash/Analytics (optional)."
        )

    return res

# ------------------------ Build-Fix Loop ------------------------

def _now_iso():
    return datetime.utcnow().strftime("%Y-%m-%dT%H:%M:%SZ")

def infer_build_request_from_text(text: str) -> dict:
    """Erkennt aus natürlichem Text die Build-Anforderung."""
    t = (text or "").lower()
    want_release = "release" in t or "relase" in t
    abis = []
    if any(x in t for x in ["arm64", "arm64-v8a", "v8a"]):
        abis.append("arm64-v8a")
    if any(x in t for x in ["v7a", "armeabi-v7a", "armeabi"]):
        abis.append("armeabi-v7a")
    if "universal" in t or "fat" in t or "both" in t:
        abis.append("universal")
    abis = list(dict.fromkeys(abis)) or ["arm64-v8a", "armeabi-v7a"]  # default
    return {
        "build_type": "release" if want_release else "debug",
        "abis": ",".join(abis),
        "ignore_missing_tdlib_v7a": "false"
    }

def openai_generate(model: str, system_prompt: str, user_prompt: str) -> str:
    """Responses API bevorzugt; Fallback Chat Completions. Mit Rate-Limit/Retry + Shell-Fehlerausgabe."""
    try:
        from openai import OpenAI
    except Exception as e:
        die("OpenAI SDK nicht installiert",
            details=f"{type(e).__name__}: {e}",
            hint="Füge `pip install openai>=1.40.0` in deinen Action-Setup-Schritt ein.")

    client = OpenAI(api_key=os.environ.get("OPENAI_API_KEY",""), base_url=_compute_base_url())
    if not os.environ.get("OPENAI_API_KEY"):
        log_warn("OPENAI_API_KEY ist nicht gesetzt – Generierung könnte fehlschlagen.")

    effort = os.environ.get("OPENAI_REASONING_EFFORT", "high").lower()
    if effort not in ("high", "medium", "low"):
        effort = "high"

    MAX_RETRIES = _env_int("OPENAI_MAX_RETRIES", 8)
    BASE_SLEEP  = float(os.environ.get("OPENAI_BASE_SLEEP", "1.5"))
    MAX_SLEEP   = float(os.environ.get("OPENAI_MAX_SLEEP", "60"))

    est_in_tokens = _estimate_tokens_from_text(system_prompt, user_prompt)
    _RATE_LIMITER.wait_for(est_in_tokens)

    def _is_retryable_error(e_msg: str) -> bool:
        m = e_msg.lower()
        return any(k in m for k in [
            "rate", "429", "insufficient_quota", "quota", "temporar", "overloaded",
            "timeout", "timed out", "connection", "gateway", "server", "502", "503", "504"
        ])

    def _try_call(fn):
        last_err = None
        for i in range(MAX_RETRIES):
            try:
                return fn()
            except Exception as e:
                last_err = e
                msg = str(e)
                if _is_retryable_error(msg):
                    sleep_s = min(MAX_SLEEP, BASE_SLEEP * (2 ** i)) + random.uniform(0, 0.4)
                    log_warn(f"OpenAI transient error, retry {i+1}/{MAX_RETRIES} after {sleep_s:.1f}s: {msg[:160]}")
                    time.sleep(sleep_s); continue
                log_error("OpenAI call failed (non-retryable)", details=traceback.format_exc())
                raise
        log_error("OpenAI call failed after retries", details=str(last_err))
        raise last_err

    def _responses():
        resp = client.responses.create(
            model=model,
            input=[{"role": "system", "content": system_prompt},
                   {"role": "user",   "content": user_prompt}],
            reasoning={"effort": effort},
        )
        text = getattr(resp, "output_text", "") or ""
        try:
            usage = getattr(resp, "usage", None)
            if usage and isinstance(usage, dict):
                total = usage.get("total_tokens") or (usage.get("input_tokens", 0) + usage.get("output_tokens", 0))
            else:
                total = est_in_tokens + _estimate_tokens_from_text(text)
        except Exception:
            total = est_in_tokens + _estimate_tokens_from_text(text)
        _RATE_LIMITER.book(total)
        return text

    def _chat():
        cc = client.chat.completions.create(
            model=model,
            messages=[{"role": "system", "content": system_prompt},
                      {"role": "user",   "content": user_prompt}],
        )
        text = cc.choices[0].message.content or ""
        try:
            usage = getattr(cc, "usage", None)
            if usage:
                total = getattr(usage, "total_tokens", None)
                if total is None and isinstance(usage, dict):
                    total = usage.get("total_tokens") or (usage.get("prompt_tokens", 0) + usage.get("completion_tokens", 0))
            else:
                total = None
            if not total:
                total = est_in_tokens + _estimate_tokens_from_text(text)
        except Exception:
            total = est_in_tokens + _estimate_tokens_from_text(text)
        _RATE_LIMITER.book(total)
        return text

    try:
        return _try_call(_responses)
    except Exception:
        try:
            return _try_call(_chat)
        except Exception as ee:
            comment_reply(f"❌ OpenAI-Fehler:\n```\n{ee}\n```")
            die("OpenAI-Aufruf endgültig fehlgeschlagen",
                details=f"{type(ee).__name__}: {ee}",
                hint="API-Key/Quota/Region prüfen; ggf. OPENAI_BASE_URL oder Modellnamen anpassen.")
            raise  # unreachable

def _compute_base_url() -> str:
    raw = os.environ.get("OPENAI_BASE_URL", "").strip()
    if not raw:
        return "https://api.openai.com/v1"
    if not raw.startswith(("http://", "https://")):
        raw = "https://" + raw
    if not re.search(r"/v\d+/?$", raw):
        raw = raw.rstrip("/") + "/v1"
    return raw

def _env_int(name: str, default: int) -> int:
    try:
        return int(os.environ.get(name, str(default)))
    except Exception:
        return default

def _estimate_tokens_from_text(*parts: str) -> int:
    total_chars = sum(len(p or "") for p in parts)
    return int(total_chars / 4) + 256

# ------------------------ Patch-Generator ------------------------

def generate_fix_patch(model: str, docs: str, compact_tree: str, context: str,
                       instruction: str, logs: dict) -> str:
    SYSTEM = (
        "You are an expert Android/Kotlin/Gradle engineer. "
        "Create a SINGLE git unified diff to FIX the build based on the provided build logs. "
        "Minimal changes, but complete. Include Gradle, dependencies, Kotlin options, Android SDK versions, "
        "resource fixes or code fixes as needed. The diff must apply at repo root with 'git apply -p0'. "
        "Do not include explanations. Only one diff."
    )
    errors_txt = "\n".join(logs.get("errors", [])[:180])
    warnings_txt = "\n".join(logs.get("warnings", [])[:160])
    USER = f"""Repository documents (trimmed):
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

Output requirements:
- Return exactly ONE unified diff starting with: diff --git a/... b/...
- It must apply at repo root using: git apply -p0
- Include new files with proper headers (e.g. 'new file mode 100644')
- Avoid forbidden paths unless explicitly allowed: {', '.join(FORBIDDEN_PATHS)}
"""
    return openai_generate(model, SYSTEM, USER)

# ------------------------ Build once & maybe fix ------------------------

def run_build_once_and_maybe_fix(repo: str, default_branch: str, workflow_file: str,
                                 inputs: dict, model: str, instruction: str,
                                 docs: str, compact_tree: str, context: str,
                                 allow_ci_changes: bool, force: bool,
                                 attempt_idx: int, max_attempts: int) -> tuple[bool, dict]:
    """Dispatch Build; wenn failed → Logs ziehen, Patch generieren & anwenden; PR pushen; return (success, last_run)."""
    t0 = dispatch_workflow(repo, workflow_file, default_branch, inputs)
    comment_reply(f"🚀 Versuch {attempt_idx}/{max_attempts}: Workflow **{workflow_file}** gestartet "
                  f"mit Inputs `{json.dumps(inputs)}`.")
    run = find_latest_run(repo, workflow_file, default_branch, t0, timeout_s=DEFAULT_WAIT_TIMEOUTS[1])
    if not run:
        comment_reply("⚠️ Konnte den gestarteten Run nicht finden. Prüfe Actions manuell.")
        return False, {}
    logs = download_and_parse_logs(repo, run.get("id"))
    summarize_run_and_comment(run, logs, want_comment=True)
    if run.get("conclusion") == "success":
        return True, run

    # Build fehlgeschlagen → Patch generieren
    repo_files, _, _ = build_repo_index()
    all_paths = [str(p).replace("\\", "/") for p in repo_files]
    focused_files = top_relevant_files(instruction, all_paths, k=80)

    preview_parts = []
    for f in focused_files:
        try:
            head = sh(f"sed -n '1,160p' {shlex.quote(f)} | sed 's/\\t/    /g' || true", check=False)
            if head.strip():
                preview_parts.append(f"\n--- file: {f} ---\n{head}")
        except Exception:
            pass
    sub_context = trim_to_chars("".join(preview_parts), MAX_CONTEXT_CHARS)

    patch_text = generate_fix_patch(model, docs, compact_tree, sub_context, instruction, logs)
    patch_text = sanitize_patch_text(patch_text)
    if "diff --git " not in patch_text:
        # NEU: Fallback – Nicht-Diff in validen Diff verpacken
        fallback_diff, target = coerce_snippet_to_diff(patch_text)
        if fallback_diff and "diff --git " in fallback_diff:
            comment_reply(f"ℹ️ Modell lieferte keinen Unified-Diff. Fallback angewandt → Datei erzeugt: `{target}`.")
            log_warn(f"Kein Diff vom Modell – Fallback erstellt: {target}")
            patch_text = fallback_diff
        else:
            comment_reply(f"⚠️ Kein gültiger Diff aus Logs generiert. Antwort war:\n```\n{patch_text[:1200]}\n```")
            log_error("Generierter Patch war ungültig", details=patch_text[:2000])
            return False, run

    line_count = patch_text.count("\n")
    if not force and line_count > MAX_PATCH_LINES:
        comment_reply(f"⛔ Generierter Patch zu groß (> {MAX_PATCH_LINES} Zeilen). Breche ab.")
        log_error("Patch zu groß",
                  details=f"Zeilen: {line_count}, Limit: {MAX_PATCH_LINES}",
                  hint="Aufgabe eingrenzen oder --force setzen.")
        return False, run

    try:
        PatchSet.from_string(patch_text)
    except Exception as e:
        comment_reply(f"ℹ️ Unidiff-Warnung: `{type(e).__name__}: {e}` – versuche Patch trotzdem.")
        log_warn(f"Unidiff-Parser Warnung: {type(e).__name__}: {e}")

    remapped_patch, remaps, unresolved = try_remap_patch(patch_text, [Path(p) for p in all_paths])
    if remaps:
        bullets = "\n".join([f"- `{src}` → `{dst}`" for src, dst in remaps])
        comment_reply(f"🧭 Pfad-Remap angewandt:\n{bullets}")
        log_info("Pfad-Remap angewandt:\n" + bullets)
    if unresolved:
        bullets = "\n".join([f"- {p}" for p in unresolved])
        comment_reply("⚠️ Nicht zuordenbare Sektionen (ausgelassen):\n" + bullets)
        log_warn("Nicht zuordenbare Sektionen:\n" + bullets)

    if not allow_ci_changes:
        sec = parse_patch_sections(remapped_patch)
        kept, dropped = [], []
        for s in sec:
            if any(s["new"].startswith(fp) or s["old"].startswith(fp) for fp in FORBIDDEN_PATHS):
                dropped.append(s["new"])
            else:
                kept.append(s["body"])
        if dropped:
            msg = "🚫 Sektionen in geschützten Pfaden verworfen:\n" + "\n".join(f"- {d}" for d in dropped)
            comment_reply(msg)
            log_warn(msg)
        remapped_patch = "".join(kept) if kept else remapped_patch

    os.makedirs(".github/codex", exist_ok=True)
    with open(".github/codex/codex.patch", "w", encoding="utf-8") as f:
        f.write(remapped_patch)

    # Apply + PR
    branch = f"codex/buildfix-{datetime.utcnow().strftime('%Y%m%d-%H%M%S')}-{actor_handle() or 'actor'}"
    sh("git config user.name 'codex-bot'"); sh("git config user.email 'actions@users.noreply.github.com'")
    sh(f"git checkout -b {branch}"); sh("chmod +x ./gradlew", check=False)

    try:
        with open(".github/codex/codex.patch", "r", encoding="utf-8", errors="replace") as _pf:
            whole_text = _pf.read()
        applied, rejected, skipped = try_apply_sections(".github/codex/codex.patch", whole_text)
    except Exception as e:
        comment_reply(f"❌ Patch-Apply fehlgeschlagen:\n```\n{e}\n```")
        log_error("Patch-Apply fehlgeschlagen", details=traceback.format_exc(),
                  hint="Ggf. 3-way Merge-Konflikte manuell prüfen; Pfade stimmen?")
        return False, run

    status = sh("git status --porcelain")
    if not status.strip():
        comment_reply("ℹ️ Keine Änderungen nach Patch-Anwendung gefunden (alle Sektionen übersprungen?).")
        log_warn("Keine Änderungen nach Patch-Anwendung (möglicherweise alles verworfen oder --allow-ci nötig).")
        return False, run

    sh("git add -A")
    sh("git commit -m 'codex: fix build (auto)'")
    sh("git push --set-upstream origin HEAD")

    default_branch = get_default_branch(repo)
    body = (
        f"Automatisch erstellt aus Build-Fix-Versuch {attempt_idx}.\n\n"
        f"Doku berücksichtigt: {', '.join([n for n in DOC_CANDIDATES if os.path.exists(n)]) or '–'}\n"
        f"Angewandt: {len(applied)}, .rej: {len(rejected)}, übersprungen: {len(skipped)}\n"
    )
    pr = gh_api("POST", f"/repos/{repo}/pulls", {
        "title": f"codex: build fix attempt {attempt_idx}",
        "head": branch,
        "base": default_branch,
        "body": body
    })
    pr_num = pr.get("number")
    try:
        gh_api("POST", f"/repos/{repo}/issues/{pr_num}/labels", {"labels": ["codex", "build-fix"]})
    except Exception as e:
        log_warn(f"Labels setzen fehlgeschlagen: {type(e).__name__}: {e}")

    comment_reply(f"🔧 PR erstellt (Versuch {attempt_idx}): #{pr['number']} – {pr['html_url']}\n"
                  f"Starte Build erneut …")
    log_info(f"PR erstellt: #{pr['number']} – {pr['html_url']}")

    return False, run

# ------------------------ Core Logic ------------------------

def _env_summary():
    keys = ["GITHUB_REPOSITORY","GITHUB_EVENT_PATH","GITHUB_EVENT_NAME","GH_EVENT_NAME",
            "OPENAI_MODEL_DEFAULT",
            "OPENAI_REASONING_EFFORT","OPENAI_BASE_URL","OPENAI_API_KEY","GITHUB_TOKEN"]
    rows = []
    for k in keys:
        v = os.environ.get(k)
        shown = "set" if (v and k not in ("OPENAI_API_KEY","GITHUB_TOKEN")) else ("set" if v else "missing")
        if k in ("OPENAI_API_KEY","GITHUB_TOKEN") and v:
            shown = "set (hidden)"
        rows.append(f"- {k}: {shown}")
    md = "### Environment-Check\n" + "\n".join(rows) + "\n"
    _write_step_summary(md)
    log_info("Environment geprüft:\n" + "\n".join(rows))

def read_comment() -> str:
    evname = _event_name()
    if evname == "workflow_dispatch":
        return os.environ.get("DISPATCH_COMMENT", "").strip()
    try:
        event = json.load(open(os.environ["GITHUB_EVENT_PATH"], "r", encoding="utf-8"))
    except Exception as e:
        log_error("GITHUB_EVENT_PATH konnte nicht gelesen werden",
                  details=f"path={os.environ.get('GITHUB_EVENT_PATH')}\n{type(e).__name__}: {e}",
                  hint="Wird das Script als Action-Step mit events (issues/issue_comment) ausgeführt?")
        return ""
    if evname == "issue_comment" and "comment" in event and "body" in event["comment"]:
        return (event["comment"]["body"] or "").strip()
    if evname == "pull_request_review_comment" and "comment" in event and "body" in event["comment"]:
        return (event["comment"]["body"] or "").strip()
    if evname == "issues" and "issue" in event and "body" in event["issue"]:
        return (event["issue"]["body"] or "").strip()
    return ""

def actor_handle() -> str:
    evname = _event_name()
    if evname == "workflow_dispatch":
        repo = os.environ.get("GITHUB_REPOSITORY", "")
        return repo.split("/")[0] if "/" in repo else repo
    try:
        event = json.load(open(os.environ["GITHUB_EVENT_PATH"], "r", encoding="utf-8"))
        return event.get("sender", {}).get("login", "")
    except Exception:
        return ""

def is_allowed(actor: str) -> bool:
    allowlist = os.environ.get("CODEX_ALLOWLIST", "").strip()
    if allowlist:
        return actor in [a.strip() for a in allowlist.split(",") if a.strip()]
    repo = os.environ.get("GITHUB_REPOSITORY", "")
    try:
        perms = gh_api("GET", f"/repos/{repo}/collaborators/{actor}/permission")
        return perms.get("permission") in ("write", "maintain", "admin")
    except Exception as e:
        log_warn(f"Konnte Collaborator-Permission nicht abfragen: {type(e).__name__}: {e}")
        return False

def main():
    with step("Environment prüfen"):
        _env_summary()
        if not os.environ.get("GITHUB_TOKEN"):
            die("GITHUB_TOKEN fehlt",
                hint="Setze im Workflow: permissions: contents: write, pull-requests: write, actions: write")

    with step("Kommentar lesen & Berechtigungen prüfen"):
        comment = read_comment()
        if not comment or "/codex" not in comment:
            log_info("Kein /codex Auftrag im Event – nichts zu tun.")
            sys.exit(0)

        actor = actor_handle() or "unknown"
        if not is_allowed(actor):
            comment_reply(f"⛔ Sorry @{actor}, du bist nicht berechtigt.")
            die("Berechtigung fehlt",
                details=f"Actor: {actor}",
                hint="Füge den Benutzer zu CODEX_ALLOWLIST hinzu oder gib Repo-Schreibrechte.")

        # Flags (explizit)
        m_model  = re.search(r"(?:^|\s)(?:-m|--model)\s+([A-Za-z0-9._\-]+)", comment)
        model    = m_model.group(1).strip() if m_model else (os.environ.get("OPENAI_MODEL_DEFAULT") or "gpt-5")
        m_reason = re.search(r"(?:^|\s)--reason\s+(high|medium|low)\b", comment, re.I)
        if m_reason:
            os.environ["OPENAI_REASONING_EFFORT"] = m_reason.group(1).lower()
        allow_ci  = bool(re.search(r"(?:^|\s)--allow-ci\b", comment))
        force_big = bool(re.search(r"(?:^|\s)--force\b", comment))
        # Optionale explizite Workflow/Inputs
        m_workflow = re.search(r"(?:^|\s)--workflow\s+([^\s]+)", comment)
        workflow_file_flag = m_workflow.group(1).strip() if m_workflow else ""
        m_inputs = re.search(r"(?:^|\s)--inputs\s+(\{.*?\}|'.*?'|\".*?\")", comment, re.S)
        inputs_raw = (m_inputs.group(1).strip() if m_inputs else "")

        # Shortcuts interpretieren
        sc = parse_shortcuts(comment)
        allow_ci = allow_ci or sc["allow_ci"]
        force_big = force_big or sc["force"]
        if sc["set_workflow"]:
            workflow_file_flag = sc["set_workflow"]

        # Instruction (nach Entfernen Flag-Texte – wir lassen Kurzbefehle im Text; Instruktionen aus sc)
        instruction_raw = re.sub(r"^.*?/codex", "", comment, flags=re.S).strip()
        for pat in [
            r"(?:^|\s)(?:-m|--model)\s+[A-Za-z0-9._\-]+",
            r"(?:^|\s)--reason\s+(?:high|medium|low)\b",
            r"(?:^|\s)--allow-ci\b",
            r"(?:^|\s)--force\b",
            r"(?:^|\s)--workflow\s+[^\s]+",
            r"(?:^|\s)--inputs\s+(\{.*?\}|'.*?'|\".*?\")",
        ]:
            instruction_raw = re.sub(pat, "", instruction_raw, flags=re.I)
        instruction = "\n".join(sc["directives"] + ([instruction_raw.strip()] if instruction_raw.strip() else []))
        instruction = trim_to_chars(instruction, MAX_USER_CHARS)

    with step("Repo-Index erstellen"):
        repo_files, compact_tree, _ = build_repo_index()
        post_repo_tree_comment(compact_tree)

    # --- Build-Pfad? (natürlich oder per Shortcut/Flag) ---
    natural_text = (comment or "").lower()
    natural_build = any(k in natural_text for k in [
        "release build", "release-build", "apk", "aab", "baue", "build", "erstelle", "rebuild"
    ])
    is_build_request = natural_build or bool(workflow_file_flag) or sc["trigger_build"]

    if is_build_request:
        with step("Build/Dispatch vorbereiten"):
            repo_full = os.environ.get("GITHUB_REPOSITORY")
            default_branch = get_default_branch(repo_full)
            # Workflow-File (als Dateiname/ID erwartet)
            workflow_file = (workflow_file_flag or DEFAULT_WORKFLOW_FILE)
            if not (workflow_file.endswith(".yml") or workflow_file.endswith(".yaml") or workflow_file.isdigit()):
                workflow_file += ".yml"

            # Inputs bestimmen
            if inputs_raw:
                try:
                    j = inputs_raw
                    if (j.startswith("'") and j.endswith("'")) or (j.startswith('"') and j.endswith('"')):
                        j = j[1:-1]
                    inputs = json.loads(j) if j.strip() else None
                except Exception as e:
                    comment_reply(f"⚠️ `--inputs` ist kein gültiges JSON: `{e}`. Nutze heuristische Erkennung.")
                    log_warn(f"--inputs JSON parse error: {type(e).__name__}: {e}")
                    inputs = None
            else:
                inputs = infer_build_request_from_text(natural_text)
            inputs = inputs or {}
            inputs.update(sc["build_inputs"])

            # Kontext für Fixer
            docs = trim_to_chars(load_docs(MAX_DOC_CHARS), MAX_DOC_CHARS)
            all_paths = [str(p).replace("\\", "/") for p in repo_files]
            focused_files = top_relevant_files(instruction or "android build", all_paths, k=80)
            preview_parts = []
            for f in focused_files:
                try:
                    head = sh(f"sed -n '1,160p' {shlex.quote(f)} | sed 's/\\t/    /g' || true", check=False)
                    if head.strip():
                        preview_parts.append(f"\n--- file: {f} ---\n{head}")
                except Exception:
                    pass
            context = trim_to_chars("".join(preview_parts), MAX_CONTEXT_CHARS)

        with step("Build/Dispatch & Auto-Fix-Loop"):
            max_attempts = DEFAULT_MAX_ATTEMPTS if (sc["build_fix_loop"] or natural_build or workflow_file_flag) else 1
            for attempt in range(1, max_attempts + 1):
                success, _ = run_build_once_and_maybe_fix(
                    repo=repo_full,
                    default_branch=default_branch,
                    workflow_file=workflow_file,
                    inputs=inputs or {},
                    model=model,
                    instruction=instruction or "Fixiere Buildfehler und baue Release-APKs für angegebene ABIs.",
                    docs=docs,
                    compact_tree=compact_tree,
                    context=context,
                    allow_ci_changes=allow_ci,
                    force=force_big,
                    attempt_idx=attempt,
                    max_attempts=max_attempts
                )
                if success:
                    comment_reply(f"✅ Build erfolgreich (Versuch {attempt}).")
                    log_info(f"Build erfolgreich in Versuch {attempt}.")
                    sys.exit(0)
            comment_reply("❌ Build weiterhin fehlerhaft nach maximalen Reparaturversuchen.")
            die("Build weiterhin fehlerhaft",
                hint="Siehe oben stehende Fehler/Warnungen im Log und im Issue-Kommentar (zusammengefasst).")

    # --- Fallback: Klassischer Patch/PR-Flow (ohne Build) ---

    with step("Patch/PR-Flow"):
        if not instruction:
            comment_reply("⚠️ Bitte gib eine Aufgabe an, z. B. `/codex --wire app:player` oder `/codex --buildrelease arm64,v7a,universal`")
            die("Keine Aufgabe erkannt",
                hint="Füge nach /codex eine Anweisung oder einen Shortcut hinzu.")
        all_paths = [str(p).replace("\\", "/") for p in repo_files]
        focused_files = top_relevant_files(instruction, all_paths, k=80)

        preview_parts = []
        for f in focused_files:
            try:
                head = sh(f"sed -n '1,160p' {shlex.quote(f)} | sed 's/\\t/    /g' || true", check=False)
                if head.strip():
                    preview_parts.append(f"\n--- file: {f} ---\n{head}")
            except Exception:
                pass
        context = trim_to_chars("".join(preview_parts), MAX_CONTEXT_CHARS)
        docs = trim_to_chars(load_docs(MAX_DOC_CHARS), MAX_DOC_CHARS)

        SYSTEM = (
            "You are an expert Android/Kotlin code-change generator (Gradle, Jetpack Compose, Unit/Instrumented tests). "
            "Use the repository documents (agents/architecture/roadmap/changelog) as authoritative guidance. "
            "Return ONLY one git-compatible unified diff (no explanations). Keep changes minimal; include build/test edits if required. "
            "All paths must be repo-root relative and must exist in the provided repo tree."
        )
        USER = f"""Repository documents (trimmed):
{docs or '(no docs found)'}
Repository tree (compact, depth≤4):
{compact_tree}
Focused file heads (top relevance):
{context}
Task from @{actor_handle()}:
{instruction}
Output requirements:
- Return exactly ONE unified diff starting with: diff --git a/... b/...
- It must apply at repo root using: git apply -p0
- Include new files with proper headers (e.g. 'new file mode 100644')
- Avoid forbidden paths unless explicitly allowed: {', '.join(FORBIDDEN_PATHS)}
"""
        try:
            patch_text = openai_generate(model, SYSTEM, USER)
        except Exception as e:
            comment_reply(f"❌ OpenAI-Fehler:\n```\n{e}\n```")
            die("Patch-Generierung fehlgeschlagen", details=traceback.format_exc())
            return

        patch_text = sanitize_patch_text(patch_text)
        if "diff --git " not in patch_text:
            # NEU: Fallback – Code/Snippet in gültigen Diff verwandeln
            fallback_diff, target = coerce_snippet_to_diff(patch_text)
            if fallback_diff and "diff --git " in fallback_diff:
                comment_reply(f"ℹ️ Modell lieferte keinen Unified-Diff. Fallback angewandt → Datei erzeugt: `{target}`.")
                log_warn(f"Kein Diff vom Modell – Fallback erstellt: {target}")
                patch_text = fallback_diff
            else:
                comment_reply(f"⚠️ Kein gültiger Diff erkannt. Antwort war:\n```\n{patch_text[:1400]}\n```")
                die("Ungültiger Diff aus OpenAI", details=patch_text[:2000],
                    hint="Bitte Aufgabe eingrenzen oder erneut probieren (SPEC-first mit --spec erwägen).")

        line_count2 = patch_text.count("\n")
        if not force_big and line_count2 > MAX_PATCH_LINES:
            die("Patch zu groß",
                details=f"Zeilen: {line_count2}, Limit: {MAX_PATCH_LINES}",
                hint="Aufgabe eingrenzen oder `--force` setzen.")

        try:
            PatchSet.from_string(patch_text)
        except Exception as e:
            comment_reply(f"ℹ️ Hinweis: Unidiff-Parser warnte: `{type(e).__name__}: {e}` – versuche Patch trotzdem (3-way / per-Sektion / --reject).")
            log_warn(f"Unidiff-Parser warnte: {type(e).__name__}: {e}")

        remapped_patch, remaps, unresolved = try_remap_patch(patch_text, [Path(p) for p in all_paths])
        if remaps:
            bullets = "\n".join([f"- `{src}` → `{dst}`" for src, dst in remaps])
            comment_reply(f"🧭 Pfad-Remap angewandt (fehlende → existierende Dateien):\n{bullets}")
            log_info("Pfad-Remap angewandt:\n" + bullets)
        if unresolved:
            bullets = "\n".join([f"- {p}" for p in unresolved])
            comment_reply("⚠️ Nicht zuordenbare Sektionen (ausgelassen):\n" + bullets)
            log_warn("Nicht zuordenbare Sektionen:\n" + bullets)

        sec = parse_patch_sections(remapped_patch)
        kept = []
        if not allow_ci:
            for s in sec:
                if any(s["new"].startswith(fp) or s["old"].startswith(fp) for fp in FORBIDDEN_PATHS):
                    continue
                kept.append(s["body"])
            if kept:
                remapped_patch = "".join(kept)

        os.makedirs(".github/codex", exist_ok=True)
        with open(".github/codex/codex.patch", "w", encoding="utf-8") as f:
            f.write(remapped_patch)

        branch = f"codex/{datetime.utcnow().strftime('%Y%m%d-%H%M%S')}-{actor_handle() or 'actor'}"
        sh("git config user.name 'codex-bot'"); sh("git config user.email 'actions@users.noreply.github.com'")
        sh(f"git checkout -b {branch}"); sh("chmod +x ./gradlew", check=False)

        try:
            with open(".github/codex/codex.patch", "r", encoding="utf-8", errors="replace") as _pf:
                whole_text = _pf.read()
            applied, rejected, skipped = try_apply_sections(".github/codex/codex.patch", whole_text)
        except Exception as e:
            comment_reply(f"❌ Patch-Apply fehlgeschlagen:\n```\n{e}\n```")
            die("Patch-Apply fehlgeschlagen", details=traceback.format_exc(),
                hint="Evtl. Merge-Konflikt oder Pfadproblem. Prüfe die betroffenen Dateien.")

        status = sh("git status --porcelain")
        if not status.strip():
            comment_reply("ℹ️ Keine Änderungen nach Patch-Anwendung gefunden (alle Sektionen übersprungen?).")
            log_warn("Keine Änderungen nach Patch-Anwendung – beende ohne Commit.")
            sys.exit(0)

        sh("git add -A")
        sh("git commit -m 'codex: apply requested changes'")
        sh("git push --set-upstream origin HEAD")

        repo = os.environ["GITHUB_REPOSITORY"]
        default_branch = get_default_branch(repo)

        body = (
            f"Automatisch erstellt aus Kommentar von @{actor_handle()}:\n\n> {instruction}\n\n"
            f"Doku berücksichtigt: {', '.join([n for n in DOC_CANDIDATES if os.path.exists(n)]) or '–'}\n"
            f"_Repo-Tree gespeichert unter `.github/codex/tree.txt`._"
        )
        pr = gh_api("POST", f"/repos/{repo}/pulls", {
            "title": f"codex changes: {instruction[:60]}",
            "head": branch,
            "base": default_branch,
            "body": body
        })

        pr_num = pr.get("number")
        try:
            gh_api("POST", f"/repos/{repo}/issues/{pr_num}/labels", {"labels": ["codex", "ci:generated"]})
        except Exception as e:
            log_warn(f"Labels setzen fehlgeschlagen: {type(e).__name__}: {e}")
        try:
            owners = []
            for path in [".github/CODEOWNERS", "CODEOWNERS"]:
                if os.path.exists(path):
                    txt = Path(path).read_text(encoding="utf-8", errors="replace")
                    for line in txt.splitlines():
                        line = line.strip()
                        if not line or line.startswith("#"):
                            continue
                        parts = line.split()
                        owners += [p.lstrip("@") for p in parts[1:] if p.startswith("@")]
            owners = list(dict.fromkeys(owners))[:5]
            if owners:
                gh_api("POST", f"/repos/{repo}/pulls/{pr_num}/requested_reviewers", {"reviewers": owners})
        except Exception as e:
            log_warn(f"Reviewer anfragen fehlgeschlagen: {type(e).__name__}: {e}")

        comment_reply(f"✅ PR erstellt: #{pr['number']} — {pr['html_url']}")
        log_info(f"PR erstellt: #{pr['number']} — {pr['html_url']}")
        print("done")

if __name__ == "__main__":
    try:
        main()
    except SystemExit:
        raise
    except Exception:
        tb = traceback.format_exc()
        die("Unerwarteter Fehler im Codex-Bot", details=tb,
            hint="Siehe Trace oben. Häufige Ursachen: fehlende Rechte, Netzwerkprobleme, ungültige Workflow-ID/Datei.")
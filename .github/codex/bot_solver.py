#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
Bot 2 – Solver (Code-Umsetzung)

Startbedingungen:
- Issue hat Label `contextmap-ready`
- Ein Kommentar existiert, der mit "### contextmap-ready" beginnt (von Bot 1)
- Trigger via issues:labeled (Label gesetzt) oder workflow_dispatch

Vorgehen:
- ContextMap einlesen, "(potentiell) betroffene Module" parsen (nur existierende Pfade)
- Rekursiv Abhängigkeiten einbeziehen (Gradle/Manifest/Imports)
- GPT-5 (high) erzeugt EINEN Unified-Diff (zentralisieren wo sinnvoll; Stelle verifizieren)
- Patch robust anwenden (sanitize, 3-way, .rej Fallback)
- Branch pushen, PR gegen default branch erstellen
- Build-Workflow dispatchen (PR-Branch) und Ergebnis abwarten:
  - success  → Label `solver-done` (und `contextmap-ready` entfernen)
  - failure  → Label `solver-error` (→ Bot 3 reagiert im Anschluss)

ENV:
  OPENAI_API_KEY
  OPENAI_MODEL_DEFAULT (gpt-5)
  OPENAI_REASONING_EFFORT (high)
  OPENAI_BASE_URL (optional)
  GITHUB_TOKEN
  GITHUB_REPOSITORY
  GITHUB_EVENT_PATH
  GH_EVENT_NAME
  ISSUE_NUMBER (optional; via workflow_dispatch)
  SOLVER_BUILD_WORKFLOW (optional, default release-apk.yml)

Python-Deps: openai, requests, unidiff
"""

from __future__ import annotations
import os
import re
import json
import time
import subprocess
import sys
import glob
from pathlib import Path
from typing import List, Tuple, Optional, Dict, Set

import requests
from unidiff import PatchSet
from unidiff.errors import UnidiffParseError

# ---------------- GitHub / Shell Utils ----------------

def repo() -> str:
    return os.environ["GITHUB_REPOSITORY"]

def event() -> dict:
    return json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text(encoding="utf-8"))

def gh_api(method: str, path: str, payload: dict | None = None) -> dict:
    url = f"https://api.github.com{path}"
    headers = {"Authorization": f"Bearer {os.environ['GITHUB_TOKEN']}",
               "Accept": "application/vnd.github+json"}
    r = requests.request(method, url, headers=headers, json=payload, timeout=45)
    if r.status_code >= 300:
        raise RuntimeError(f"GitHub API {method} {path} failed: {r.status_code} {r.text[:500]}")
    try:
        return r.json()
    except Exception:
        return {}

def gh_api_raw(method: str, url: str, allow_redirects=True):
    headers = {"Authorization": f"Bearer {os.environ['GITHUB_TOKEN']}",
               "Accept": "application/vnd.github+json"}
    r = requests.request(method, url, headers=headers, allow_redirects=allow_redirects, stream=True, timeout=60)
    if r.status_code >= 300 and r.status_code not in (301, 302):
        raise RuntimeError(f"GitHub RAW {method} {url} failed: {r.status_code} {r.text[:500]}")
    return r

def issue_number() -> Optional[int]:
    """
    Issue-Nummer ermitteln:
    1) klassisch: aus issues-Event
    2) workflow_dispatch: inputs.issue
    3) ENV ISSUE_NUMBER (vom Workflow gesetzt)
    """
    ev = event()
    if "issue" in ev:
        n = (ev.get("issue") or {}).get("number")
        if n: return int(n)
    inputs = ev.get("inputs") or {}
    if inputs.get("issue"):
        try: return int(inputs["issue"])
        except: pass
    env_issue = os.environ.get("ISSUE_NUMBER")
    if env_issue:
        try: return int(env_issue)
        except: pass
    return None

def list_issue_comments(num: int) -> List[dict]:
    res = gh_api("GET", f"/repos/{repo()}/issues/{num}/comments")
    return res if isinstance(res, list) else []

def get_labels(num: int) -> Set[str]:
    issue = gh_api("GET", f"/repos/{repo()}/issues/{num}")
    return {l.get("name","") for l in issue.get("labels",[])}

def add_label(num: int, label: str):
    gh_api("POST", f"/repos/{repo()}/issues/{num}/labels", {"labels":[label]})

def remove_label(num: int, label: str):
    try:
        requests.delete(f"https://api.github.com/repos/{repo()}/issues/{num}/labels/{label}",
                        headers={"Authorization": f"Bearer {os.environ['GITHUB_TOKEN']}",
                                 "Accept": "application/vnd.github+json"},
                        timeout=30)
    except Exception:
        pass

def post_comment(num: int, body: str):
    gh_api("POST", f"/repos/{repo()}/issues/{num}/comments", {"body": body})

def sh(cmd: str, check=True) -> str:
    p = subprocess.run(cmd, shell=True, text=True, capture_output=True)
    if check and p.returncode != 0:
        raise RuntimeError(f"cmd failed: {cmd}\nSTDOUT:\n{p.stdout}\nSTDERR:\n{p.stderr}")
    return (p.stdout or "")

def ls_files() -> List[str]:
    try:
        out = sh("git ls-files")
        return [l.strip() for l in out.splitlines() if l.strip()]
    except Exception:
        return []

# ---------------- ContextMap & Kandidaten ----------------

def fetch_contextmap_comment(num: int) -> Optional[str]:
    for c in list_issue_comments(num):
        b = (c.get("body") or "").strip()
        if b.startswith("### contextmap-ready"):
            return b
    return None

def parse_affected_modules(contextmap_md: str, all_files: List[str]) -> List[str]:
    """
    extrahiert Liste aus Abschnitt '#### (potentiell) betroffene Module'
    nur existierende Pfade (Dateien/Ordner) werden akzeptiert
    """
    m = re.search(r"####\s*\(potentiell\)\s*betroffene\s*Module\s*(.*?)\n####", contextmap_md, re.S | re.I)
    if not m:
        m = re.search(r"####\s*\(potentiell\)\s*betroffene\s*Module\s*(.*)$", contextmap_md, re.S | re.I)
        if not m:
            return []
    block = m.group(1)
    items = re.findall(r"^\s*-\s+(.+)$", block, re.M)
    items = [i.strip().strip("`").strip() for i in items if i.strip()]
    sset = set(all_files)
    existing = set()
    for it in items:
        if it in sset:
            existing.add(it)
        else:
            if any(f.startswith(it.rstrip("/") + "/") for f in sset):
                existing.add(it.rstrip("/"))
    return sorted(existing)

def expand_dependencies(seed_paths: List[str], all_files: List[str], max_extra: int = 80) -> List[str]:
    """
    Rekursive Erweiterung:
      - Module: build.gradle(.kts), AndroidManifest.xml ergänzen
      - Import-Heuristik (com.chris.m3usuite) → zusammenhängende Module
    """
    sset = set(all_files)
    out: Set[str] = set(seed_paths)
    # Gradle/Manifest je Modul
    for p in list(out):
        g1 = p.rstrip("/") + "/build.gradle"
        g2 = p.rstrip("/") + "/build.gradle.kts"
        m1 = p.rstrip("/") + "/src/main/AndroidManifest.xml"
        if g1 in sset: out.add(g1)
        if g2 in sset: out.add(g2)
        if m1 in sset: out.add(m1)
    # Import-Scan
    pkg_root = "com.chris.m3usuite"
    extra=set()
    for f in [x for x in all_files if x.endswith(".kt") or x.endswith(".java")]:
        try:
            txt = Path(f).read_text(encoding="utf-8", errors="ignore")
        except Exception:
            continue
        if "import " in txt and pkg_root in txt:
            root = f.split("/")[0]
            if root not in out and any(x.startswith(root+"/") or x==root for x in sset):
                extra.add(root)
                if len(extra) >= max_extra: break
    out.update(extra)
    return sorted(out)

# ---------------- OpenAI Diff-Erzeugung ----------------

def sanitize_patch(raw: str) -> str:
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

def apply_patch_unidiff(patch_text: str) -> Tuple[List[str], List[str], List[str]]:
    sections=[]
    for m in re.finditer(r"^diff --git a/(\S+)\s+b/(\S+)\s*$", patch_text, re.M):
        start=m.start()
        next_m=re.search(r"^diff --git ", patch_text[m.end():], re.M)
        end=m.end() + (next_m.start() if next_m else len(patch_text)-m.end())
        sections.append(patch_text[start:end])
    if not sections:
        tmp = ".github/codex/_solver_all.patch"
        Path(tmp).write_text(patch_text, encoding="utf-8")
        subprocess.run(f"git apply -p0 --whitespace=fix {tmp}", shell=True, check=True, text=True)
        return (["<whole>"], [], [])
    applied, rejected, skipped = [], [], []
    for idx, body in enumerate(sections,1):
        tmp=f".github/codex/_solver_sec_{idx}.patch"
        Path(tmp).write_text(body, encoding="utf-8")
        if subprocess.run(f"git apply --check -3 -p0 {tmp}", shell=True, text=True, capture_output=True).returncode==0:
            subprocess.run(f"git apply -3 -p0 --whitespace=fix {tmp}", shell=True, check=True, text=True)
            applied.append(f"section_{idx}"); continue
        if subprocess.run(f"git apply --check -p0 {tmp}", shell=True, text=True, capture_output=True).returncode==0:
            subprocess.run(f"git apply -p0 --whitespace=fix {tmp}", shell=True, check=True, text=True)
            applied.append(f"section_{idx}"); continue
        # Normalize & retry
        data = Path(tmp).read_text(encoding="utf-8", errors="replace").replace("\r\n","\n").replace("\r","\n")
        Path(tmp).write_text(data, encoding="utf-8")
        if subprocess.run(f"git apply --check -3 -p0 {tmp}", shell=True, text=True, capture_output=True).returncode==0:
            subprocess.run(f"git apply -3 -p0 --whitespace=fix {tmp}", shell=True, check=True, text=True)
            applied.append(f"section_{idx}"); continue
        if subprocess.run(f"git apply --check -p0 {tmp}", shell=True, text=True, capture_output=True).returncode==0:
            subprocess.run(f"git apply -p0 --whitespace=fix {tmp}", shell=True, check=True, text=True)
            applied.append(f"section_{idx}"); continue
        try:
            subprocess.run(f"git apply -p0 --reject --whitespace=fix {tmp}", shell=True, check=True, text=True)
            rejected.append(f"section_{idx}")
        except Exception:
            skipped.append(f"section_{idx}")
    return applied, rejected, skipped

def openai_diff(contextmap: str, docs: str, target_paths: List[str]) -> str:
    from openai import OpenAI
    api_key = os.environ.get("OPENAI_API_KEY")
    if not api_key: raise RuntimeError("OPENAI_API_KEY not set")
    client = OpenAI(api_key=api_key, base_url=(os.environ.get("OPENAI_BASE_URL") or "https://api.openai.com/v1"))
    model = os.environ.get("OPENAI_MODEL_DEFAULT","gpt-5")
    effort = "high"

    target_preview = "\n".join(sorted(target_paths))[:8000]
    SYSTEM = (
        "You are an expert Android/Kotlin/Gradle engineer. "
        "Generate ONE unified diff that applies at repo root (git apply -p0). "
        "Prefer centralized fixes where possible; verify correct location; refactor helpers/modules to ease future maintenance."
    )
    USER = f"""ContextMap:
{contextmap}

Docs:
{docs or '(no docs present)'}

Target files/modules (primary and recursively inferred):
{target_preview}

Output rules:
- Exactly ONE unified diff, starting with: diff --git a/... b/...
- Include new files with 'new file mode 100644' if needed
- No prose besides the diff.
"""
    resp = client.responses.create(
        model=model,
        input=[{"role":"system","content":SYSTEM},{"role":"user","content":USER}],
        reasoning={"effort": effort},
    )
    return getattr(resp, "output_text", "")

# ---------------- Build Dispatch & Wait ----------------

def default_branch() -> str:
    return gh_api("GET", f"/repos/{repo()}").get("default_branch","main")

def dispatch_build(workflow_ident: str, ref_branch: str, inputs: dict | None) -> str:
    gh_api("POST", f"/repos/{repo()}/actions/workflows/{workflow_ident}/dispatches",
           {"ref": ref_branch, "inputs": inputs or {}})
    # ISO8601 UTC now
    return time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())

def wait_build_result(workflow_ident: str, ref_branch: str, since_iso: str, timeout_s=1800) -> dict:
    base=f"/repos/{repo()}/actions/workflows/{workflow_ident}/runs"
    t0=time.time()
    while True:
        runs=gh_api("GET", f"{base}?event=workflow_dispatch&branch={ref_branch}")
        arr=runs.get("workflow_runs",[])
        cand=arr[0] if arr else {}
        if cand and cand.get("created_at","")>=since_iso:
            rid=cand.get("id")
            while True:
                run=gh_api("GET", f"/repos/{repo()}/actions/runs/{rid}")
                if run.get("status")=="completed": return run
                if time.time()-t0>timeout_s: return run
                time.sleep(6)
        if time.time()-t0>timeout_s: return {}
        time.sleep(3)

# ---------------- Fallbacks bei Unidiff-Warnungen ----------------

def whole_patch_fallback(patch_text: str) -> Tuple[bool, str]:
    """Versuche den gesamten Patch in robusten Modi zu applizieren."""
    tmp = ".github/codex/_solver_all.patch"
    Path(tmp).write_text(patch_text, encoding="utf-8")
    tries = [
        f"git apply -3 -p0 --whitespace=fix {tmp}",
        f"git apply -p0 --whitespace=fix {tmp}",
        f"git apply -p0 --reject --ignore-whitespace --whitespace=fix {tmp}",
    ]
    last_err = ""
    for cmd in tries:
        pr = subprocess.run(cmd, shell=True, text=True, capture_output=True)
        if pr.returncode == 0:
            return True, cmd
        last_err = (pr.stderr or pr.stdout or "")[:800]
    return False, last_err

def summarize_rejects(num: int):
    """Wenn .rej Dateien existieren, kurze Liste + Ausschnitte ins Issue kommentieren."""
    rej_files = glob.glob("**/*.rej", recursive=True)
    if not rej_files:
        return
    preview = []
    for rf in rej_files[:10]:
        try:
            txt = Path(rf).read_text(encoding="utf-8", errors="replace")
            preview.append(f"\n--- {rf} ---\n" + txt[:500])
        except Exception:
            preview.append(f"- {rf}")
    body = ("⚠️ Solver: Einige Hunks wurden als `.rej` abgelegt (manuelle Nacharbeit nötig):\n"
            + "\n".join(f"- {r}" for r in rej_files[:50]))
    if preview:
        body += "\n\n```diff\n" + "".join(preview) + "\n```"
    post_comment(num, body)

# ---------------- Main ----------------

def main():
    num = issue_number()
    if not num:
        print("::error::No issue number in event or inputs"); sys.exit(1)

    labels = get_labels(num)
    if "contextmap-ready" not in labels:
        print("::notice::No 'contextmap-ready' label; skipping"); sys.exit(0)

    cm = fetch_contextmap_comment(num)
    if not cm or not cm.strip().startswith("### contextmap-ready"):
        print("::notice::No contextmap comment; skipping"); sys.exit(0)

    # Docs laden
    docs=[]
    for name in ["AGENTS.md","ARCHITECTURE_OVERVIEW.md","ROADMAP.md","CHANGELOG.md"]:
        if Path(name).exists():
            try: docs.append(f"\n--- {name} ---\n" + Path(name).read_text(encoding="utf-8", errors="replace"))
            except Exception: pass
    docs_txt="".join(docs)

    # Kandidaten
    all_files=ls_files()
    seeds = parse_affected_modules(cm, all_files)
    if not seeds:
        add_label(num, "solver-error")
        post_comment(num, "❌ Solver: Keine gültigen Module in der ContextMap gefunden.")
        sys.exit(1)

    targets = expand_dependencies(seeds, all_files, max_extra=80)

    # Branch vorbereiten
    base = default_branch()
    branch = f"codex/solve-{int(time.time())}"
    sh("git config user.name 'codex-bot'"); sh("git config user.email 'actions@users.noreply.github.com'")
    sh(f"git fetch origin {base}", check=False)
    sh(f"git checkout -b {branch} origin/{base}")

    # Diff erzeugen
    try:
        raw = openai_diff(cm, docs_txt, targets)
    except Exception as e:
        add_label(num, "solver-error")
        post_comment(num, f"❌ Solver: OpenAI-Fehler\n```\n{e}\n```")
        sys.exit(1)

    patch = sanitize_patch(raw)
    if "diff --git " not in patch:
        add_label(num, "solver-error")
        post_comment(num, f"❌ Solver: Kein gültiger Diff\n```\n{patch[:1200]}\n```")
        sys.exit(1)

    # Unidiff-Validierung + Fallback-Flag
    parse_warn = False
    try:
        PatchSet.from_string(patch)
    except UnidiffParseError as e:
        parse_warn = True
        post_comment(num, f"ℹ️ Solver: Unidiff parser warning: `{type(e).__name__}: {e}` – erweitere Fallbacks.")
    except Exception as e:
        parse_warn = True
        post_comment(num, f"ℹ️ Solver: Unidiff parser warning (generisch): `{type(e).__name__}: {e}` – erweitere Fallbacks.")

    # Apply
    applied = rejected = skipped = []
    if parse_warn:
        ok, info = whole_patch_fallback(patch)
        if ok:
            post_comment(num, f"✅ Solver: Patch via Whole-Apply erfolgreich (`{info}`)")
        else:
            post_comment(num, f"❌ Solver: Whole-Apply Fallback scheiterte.\n```\n{info}\n```")
            # danach klassisch section-wise versuchen
            try:
                applied, rejected, skipped = apply_patch_unidiff(patch)
            except Exception as e:
                add_label(num, "solver-error")
                post_comment(num, f"❌ Solver: Patch-Apply fehlgeschlagen\n```\n{e}\n```")
                summarize_rejects(num)
                sys.exit(1)
    else:
        try:
            applied, rejected, skipped = apply_patch_unidiff(patch)
        except Exception as e:
            add_label(num, "solver-error")
            post_comment(num, f"❌ Solver: Patch-Apply fehlgeschlagen\n```\n{e}\n```")
            summarize_rejects(num)
            sys.exit(1)

    # Cleanup temporärer Patchdateien
    for f in glob.glob(".github/codex/_solver_sec_*.patch"):
        try: os.remove(f)
        except: pass
    if os.path.exists(".github/codex/_solver_all.patch"):
        try: os.remove(".github/codex/_solver_all.patch")
        except: pass

    status = sh("git status --porcelain")
    if not status.strip():
        add_label(num, "solver-error")
        post_comment(num, "❌ Solver: Keine Änderungen im Working Tree nach Apply.")
        summarize_rejects(num)
        sys.exit(1)

    # Commit + Push + PR
    sh("git add -A")
    sh("git commit -m 'codex: solver apply'")
    sh("git push --set-upstream origin HEAD")

    pr = gh_api("POST", f"/repos/{repo()}/pulls", {
        "title": "codex: solver changes",
        "head": branch,
        "base": base,
        "body": f"Automatisch erzeugte Änderungen basierend auf der ContextMap. (issue #{num})"
    })
    pr_num = pr.get("number"); pr_url = pr.get("html_url")
    post_comment(num, f"🔧 PR erstellt: #{pr_num} — {pr_url}")

    # Build anstoßen: auf PR-Branch bauen + Issue-Nummer mitgeben (für Bot 3)
    wf = os.environ.get("SOLVER_BUILD_WORKFLOW", "release-apk.yml")
    try:
        since = dispatch_build(wf, branch, {"build_type":"debug", "issue": str(num)})
        run = wait_build_result(wf, branch, since, timeout_s=1800)
        concl = (run or {}).get("conclusion","")
        if concl == "success":
            remove_label(num, "contextmap-ready")
            add_label(num, "solver-done")
            post_comment(num, "✅ Build erfolgreich – Label `solver-done` gesetzt.")
        else:
            add_label(num, "solver-error")
            post_comment(num, "❌ Build fehlgeschlagen – Label `solver-error` gesetzt (Bot 3 wird reagieren).")
    except Exception as e:
        add_label(num, "solver-error")
        post_comment(num, f"❌ Build-Dispatch fehlgeschlagen\n```\n{e}\n```")
        sys.exit(1)

if __name__ == "__main__":
    try:
        main()
    except SystemExit:
        raise
    except Exception as e:
        try:
            add_label(issue_number() or 0, "solver-error")
        except Exception:
            pass
        print("::error::Unexpected solver error:", e)
        sys.exit(1)

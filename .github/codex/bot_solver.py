#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
Bot 2 – Solver
Startbedingungen:
- Label `contextmap-ready` gesetzt
- Ein Kommentar existiert, der mit "### contextmap-ready" beginnt
- Trigger per labeled-Event ODER workflow_dispatch

Aktion:
- ContextMap einlesen, "betroffene Module" parsen, nur existierende Pfade verwenden
- Rekursive Abhängigkeits-Erweiterung (Gradle/Manifest/Imports)
- OpenAI (gpt-5, high) → Unified-Diff erzeugen (gezielt + zentralisieren, wenn sinnvoll)
- Patch robust anwenden (sanitize, 3-way), Branch pushen, PR erstellen
- Build-Workflow dispatchen & auf Ergebnis warten
  - success → Label `solver-done`
  - failure → Label `solver-error`  (Bot 3 reagiert später darauf)

Deps: openai, requests, unidiff
"""

from __future__ import annotations
import os, re, json, subprocess, sys, time, io, zipfile, traceback, textwrap
from pathlib import Path
from typing import List, Tuple, Optional, Dict, Set
import requests
from unidiff import PatchSet

# ---------------- Utilities ----------------

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
        raise RuntimeError(f"GitHub API {method} {path} failed: {r.status_code} {r.text[:400]}")
    try: return r.json()
    except Exception: return {}

def gh_api_raw(method: str, url: str, allow_redirects=True):
    headers = {"Authorization": f"Bearer {os.environ['GITHUB_TOKEN']}",
               "Accept": "application/vnd.github+json"}
    r = requests.request(method, url, headers=headers, allow_redirects=allow_redirects, stream=True, timeout=60)
    if r.status_code >= 300 and r.status_code not in (301, 302):
        raise RuntimeError(f"GitHub RAW {method} {url} failed: {r.status_code} {r.text[:400]}")
    return r

def issue_number() -> int:
    ev = event()
    return (ev.get("issue") or {}).get("number")

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
                                 "Accept": "application/vnd.github+json"}, timeout=30)
    except Exception:
        pass

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

# ---------------- ContextMap & Auswahl ----------------

def fetch_contextmap_comment(num: int) -> Optional[str]:
    for c in list_issue_comments(num):
        b = (c.get("body") or "").strip()
        if b.startswith("### contextmap-ready"):
            return b
    return None

def parse_affected_modules(contextmap_md: str, all_files: List[str]) -> List[str]:
    # finde Abschnitt: #### (potentiell) betroffene Module
    m = re.search(r"####\s*\(potentiell\)\s*betroffene\s*Module\s*(.*?)\n####", contextmap_md, re.S | re.I)
    if not m:
        # bis Dokumentende, wenn letzter Abschnitt ist
        m = re.search(r"####\s*\(potentiell\)\s*betroffene\s*Module\s*(.*)", contextmap_md, re.S | re.I)
    if not m: return []
    block = m.group(1)
    items = re.findall(r"^\s*-\s+(.+)$", block, re.M)
    items = [i.strip().strip("`").strip() for i in items]
    # Nur existierende Pfade/Dateien akzeptieren
    sset = set(all_files)
    existing = set()
    for it in items:
        # akzeptiere exakten Pfad oder Verzeichnisse mit build.gradle(.kts)
        if it in sset:
            existing.add(it)
        else:
            # wenn es ein Ordner ist, checke ob Dateien darunter existieren
            if any(f.startswith(it.rstrip("/") + "/") for f in sset):
                existing.add(it.rstrip("/"))
    return sorted(existing)

def expand_dependencies(seed_paths: List[str], all_files: List[str], max_extra: int = 50) -> List[str]:
    """
    Erweitert um:
      - zugehörige build.gradle(.kts), AndroidManifest.xml
      - offensichtliche Abhängigkeiten über simple Import/Package-Heuristik
    Limit: max_extra zusätzliche Einträge
    """
    sset = set(all_files)
    out = set(seed_paths)

    # Gradle/Manifest hinzufügen
    for p in list(out):
        # Modulordner
        if any((p + "/build.gradle") == f or (p + "/build.gradle.kts") == f for f in sset):
            out.add(p + "/build.gradle") if (p + "/build.gradle") in sset else None
            out.add(p + "/build.gradle.kts") if (p + "/build.gradle.kts") in sset else None
        # Manifest unter p/src/main/AndroidManifest.xml
        mf = p.rstrip("/") + "/src/main/AndroidManifest.xml"
        if mf in sset: out.add(mf)

    # simple Import-Scanner: finde Module, die von seed-Pfaden typischen Packages nutzen
    # Package-Prefix grob aus Pfad ableiten: com/chris/m3usuite/<mod>
    candidates = []
    for f in sset:
        if f.endswith(".kt") or f.endswith(".java"):
            candidates.append(f)

    # Wir sammeln Module, deren Pfade (Ordner) gemeinsam vorkommen
    # Heuristik: wenn seed-Pfad 'app/src/..' -> wir scannen nach imports 'com.chris.m3usuite'
    pkg_root = "com.chris.m3usuite"
    extra=set()
    for cf in candidates:
        try:
            txt = Path(cf).read_text(encoding="utf-8", errors="ignore")
        except Exception:
            continue
        if "import " in txt and pkg_root in txt:
            # prüfe, ob Datei in einem noch nicht enthaltenen Modul liegt
            root = cf.split("/")[0]
            if root not in out and any(f.startswith(root + "/") for f in sset):
                extra.add(root)
        if len(extra) >= max_extra: break

    out.update(extra)
    return sorted(out)

# ---------------- OpenAI Patch-Erzeugung ----------------

def sanitize_patch(raw: str) -> str:
    if not raw: return raw
    m = re.search(r"```(?:diff)?\s*(.*?)```", raw, re.S)
    if m: raw = m.group(1)
    i = raw.find("diff --git ")
    if i != -1: raw = raw[i:]
    raw = raw.replace("\r\n","\n").replace("\r","\n")
    if not raw.endswith("\n"): raw += "\n"
    # Kopf ausrichten
    lines = raw.split("\n")
    if not lines or not lines[0].startswith("diff --git "):
        for idx, ln in enumerate(lines):
            if ln.startswith("diff --git "): lines = lines[idx:]; break
        raw = "\n".join(lines)
    return raw

def apply_patch_unidiff(patch_text: str) -> Tuple[List[str], List[str], List[str]]:
    # Section-wise with 3-way fallback
    sections=[]
    for m in re.finditer(r"^diff --git a/(\S+)\s+b/(\S+)\s*$", patch_text, re.M):
        start=m.start()
        next_m=re.search(r"^diff --git ", patch_text[m.end():], re.M)
        end=m.end() + (next_m.start() if next_m else len(patch_text)-m.end())
        body=patch_text[start:end]
        sections.append(body)
    if not sections:
        tmp = ".github/codex/_solver_all.patch"
        Path(tmp).write_text(patch_text, encoding="utf-8")
        sh(f"git apply -p0 --whitespace=fix {tmp}")
        return (["<whole>"], [], [])
    applied, rejected, skipped = [], [], []
    for idx, body in enumerate(sections,1):
        tmp=f".github/codex/_solver_sec_{idx}.patch"
        Path(tmp).write_text(body, encoding="utf-8")
        ok3=subprocess.run(f"git apply --check -3 -p0 {tmp}", shell=True, text=True, capture_output=True)
        if ok3.returncode==0:
            sh(f"git apply -3 -p0 --whitespace=fix {tmp}"); applied.append(f"section_{idx}"); continue
        ok=subprocess.run(f"git apply --check -p0 {tmp}", shell=True, text=True, capture_output=True)
        if ok.returncode==0:
            sh(f"git apply -p0 --whitespace=fix {tmp}"); applied.append(f"section_{idx}"); continue
        # normalize & retry
        dat=Path(tmp).read_text(encoding="utf-8", errors="replace").replace("\r\n","\n").replace("\r","\n")
        Path(tmp).write_text(dat, encoding="utf-8")
        ok3b=subprocess.run(f"git apply --check -3 -p0 {tmp}", shell=True, text=True, capture_output=True)
        if ok3b.returncode==0:
            sh(f"git apply -3 -p0 --whitespace=fix {tmp}"); applied.append(f"section_{idx}"); continue
        ok2=subprocess.run(f"git apply --check -p0 {tmp}", shell=True, text=True, capture_output=True)
        if ok2.returncode==0:
            sh(f"git apply -p0 --whitespace=fix {tmp}"); applied.append(f"section_{idx}"); continue
        try:
            sh(f"git apply -p0 --reject --whitespace=fix {tmp}"); rejected.append(f"section_{idx}")
        except Exception:
            skipped.append(f"section_{idx}")
    return applied, rejected, skipped

def openai_diff(contextmap: str, docs: str, target_paths: List[str]) -> str:
    from openai import OpenAI
    # key mapping
    if "OPENAI_API_KEY" not in os.environ and "OPEN_API_KEY" in os.environ:
        os.environ["OPENAI_API_KEY"] = os.environ["OPEN_API_KEY"]
    client = OpenAI(api_key=os.environ["OPENAI_API_KEY"],
                    base_url=(os.environ.get("OPENAI_BASE_URL") or "https://api.openai.com/v1"))
    model = os.environ.get("OPENAI_MODEL_DEFAULT","gpt-5")
    effort = "high"
    # kompakter Tree-Auszug nur für Zielpfade
    target_preview = "\n".join(sorted(target_paths))[:8000]

    SYSTEM = (
        "You are an expert Android/Kotlin/Gradle engineer. "
        "Generate ONE unified diff that applies at repo root (git apply -p0). "
        "Prefer centralized fixes when possible; if not, refactor into helpers/modules to ease future maintenance. "
        "Verify the correct location before editing; do not patch random first matches."
    )
    USER = f"""ContextMap (from Bot1):
{contextmap}

Docs (AGENTS → ARCHITECTURE_OVERVIEW → ROADMAP → CHANGELOG):
{docs or '(no docs present)'}

Target files/modules (only modify among these or clearly central shared code if required):
{target_preview}

Output rules:
- Exactly ONE unified diff, starting with: diff --git a/... b/...
- Include new files with 'new file mode 100644' as needed
- No commentary or prose besides the diff.
"""
    resp = client.responses.create(
        model=model,
        input=[{"role":"system","content":SYSTEM},{"role":"user","content":USER}],
        reasoning={"effort": effort},
    )
    return getattr(resp, "output_text", "")

# ---------------- Build Dispatch ----------------

def default_branch() -> str:
    return gh_api("GET", f"/repos/{repo()}").get("default_branch","main")

def dispatch_build(workflow_ident: str, ref_branch: str, inputs: dict | None) -> str:
    gh_api("POST", f"/repos/{repo()}/actions/workflows/{workflow_ident}/dispatches",
           {"ref": ref_branch, "inputs": inputs or {}})
    return time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())

def wait_build_result(workflow_ident: str, ref_branch: str, since_iso: str, timeout_s=1800) -> dict:
    base=f"/repos/{repo()}/actions/workflows/{workflow_ident}/runs"
    started=time.time()
    while True:
        runs=gh_api("GET", f"{base}?event=workflow_dispatch&branch={ref_branch}")
        arr=runs.get("workflow_runs",[])
        cand=arr[0] if arr else {}
        if cand and cand.get("created_at","")>=since_iso:
            rid=cand.get("id")
            while True:
                run=gh_api("GET", f"/repos/{repo()}/actions/runs/{rid}")
                if run.get("status")=="completed": return run
                if time.time()-started>timeout_s: return run
                time.sleep(6)
        if time.time()-started>timeout_s: return {}
        time.sleep(3)

# ---------------- Main ----------------

def main():
    # Map OPEN_API_KEY -> OPENAI_API_KEY (falls nötig)
    if "OPENAI_API_KEY" not in os.environ and "OPEN_API_KEY" in os.environ:
        os.environ["OPENAI_API_KEY"] = os.environ["OPEN_API_KEY"]

    num = issue_number()
    if not num:
        print("::error::No issue number."); sys.exit(1)

    labels = get_labels(num)
    if "contextmap-ready" not in labels:
        print("::notice::No 'contextmap-ready' label; skip."); sys.exit(0)

    cm = fetch_contextmap_comment(num)
    if not cm or not cm.strip().startswith("### contextmap-ready"):
        print("::notice::No contextmap comment; skip."); sys.exit(0)

    # Doku laden (nur für Diff Prompt)
    docs=[]
    for name in ["AGENTS.md","ARCHITECTURE_OVERVIEW.md","ROADMAP.md","CHANGELOG.md"]:
        if Path(name).exists():
            try: docs.append(f"\n--- {name} ---\n" + Path(name).read_text(encoding="utf-8", errors="replace"))
            except Exception: pass
    docs_txt="".join(docs)

    files=ls_files()
    seeds = parse_affected_modules(cm, files)
    if not seeds:
        add_label(num, "solver-error")
        print("::error::No valid modules in ContextMap."); sys.exit(1)

    targets = expand_dependencies(seeds, files, max_extra=80)

    # Arbeitsbranch
    base = default_branch()
    branch = f"codex/solve-{int(time.time())}"
    sh(f"git config user.name 'codex-bot'"); sh("git config user.email 'actions@users.noreply.github.com'")
    sh(f"git checkout -b {branch} origin/{base}")

    # OpenAI Diff erzeugen
    try:
        raw = openai_diff(cm, docs_txt, targets)
    except Exception as e:
        add_label(num, "solver-error")
        gh_api("POST", f"/repos/{repo()}/issues/{num}/comments", {"body": f"❌ Solver: OpenAI-Fehler\n```\n{e}\n```"})
        sys.exit(1)

    patch = sanitize_patch(raw)
    if "diff --git " not in patch:
        add_label(num, "solver-error")
        gh_api("POST", f"/repos/{repo()}/issues/{num}/comments", {"body": f"❌ Solver: Kein gültiger Diff\n```\n{patch[:1200]}\n```"})
        sys.exit(1)

    # Anwenden
    try:
        applied, rejected, skipped = apply_patch_unidiff(patch)
    except Exception as e:
        add_label(num, "solver-error")
        gh_api("POST", f"/repos/{repo()}/issues/{num}/comments", {"body": f"❌ Solver: Patch-Apply fehlgeschlagen\n```\n{e}\n```"})
        sys.exit(1)

    # Wenn keine Änderung → Fehler
    if not (applied or rejected or skipped):
        add_label(num, "solver-error")
        gh_api("POST", f"/repos/{repo()}/issues/{num}/comments", {"body": "❌ Solver: 0 Sektionen konnten angewendet werden."})
        sys.exit(1)

    # Commit & push & PR
    status = sh("git status --porcelain")
    if status.strip():
        sh("git add -A"); sh("git commit -m 'codex: solver apply'"); sh("git push --set-upstream origin HEAD")
    else:
        # Falls trotzdem nichts im Arbeitsdir
        add_label(num, "solver-error")
        gh_api("POST", f"/repos/{repo()}/issues/{num}/comments", {"body": "❌ Solver: Keine Änderungen im Working Tree nach Apply."})
        sys.exit(1)

    pr = gh_api("POST", f"/repos/{repo()}/pulls", {
        "title": "codex: solver changes",
        "head": branch,
        "base": base,
        "body": "Automatisch erzeugte Änderungen basierend auf der ContextMap."
    })
    pr_num = pr.get("number"); pr_url = pr.get("html_url")
    gh_api("POST", f"/repos/{repo()}/issues/{num}/comments", {"body": f"🔧 PR erstellt: #{pr_num} — {pr_url}"})

    # Build workflow dispatchen (ersetze ggf. durch deinen Build-Workflow)
    wf = os.environ.get("SOLVER_BUILD_WORKFLOW", "release-apk.yml")
    try:
        since = dispatch_build(wf, base, {"build_type":"debug"})
        run = wait_build_result(wf, base, since, timeout_s=1800)
        concl = (run or {}).get("conclusion","")
        if concl == "success":
            remove_label(num, "contextmap-ready")
            add_label(num, "solver-done")
            gh_api("POST", f"/repos/{repo()}/issues/{num}/comments", {"body": "✅ Build erfolgreich – Label `solver-done` gesetzt."})
        else:
            add_label(num, "solver-error")
            gh_api("POST", f"/repos/{repo()}/issues/{num}/comments", {"body": "❌ Build fehlgeschlagen – Label `solver-error` gesetzt (Bot 3 wird reagieren)."})
    except Exception as e:
        add_label(num, "solver-error")
        gh_api("POST", f"/repos/{repo()}/issues/{num}/comments", {"body": f"❌ Build-Dispatch fehlgeschlagen\n```\n{e}\n```"})
        sys.exit(1)

if __name__ == "__main__":
    try:
        main()
    except SystemExit:
        raise
    except Exception as e:
        add_label(issue_number() or 0, "solver-error")
        print("::error::Unexpected solver error:", e)
        sys.exit(1)

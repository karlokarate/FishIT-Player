#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
Bot 3 – Fehlerdoktor / Triage

Reagiert auf:
- Issue Labels: contextmap-error, solver-error
- Workflow-Run failures: workflow_run (conclusion == failure)

Aufgaben:
- komplette Logs (ZIP) herunterladen
- Fehler extrahieren (Fehlermeldungen, Gradle/AGP/SDK/Deps, Python-Tracebacks, git apply, API-Fehler)
- mit GPT-5 (Reasoning: high) eine Analyse erstellen:
  - Zusammenfassung
  - vermutete Ursache
  - konkrete Schritte (für User)
  - Checklisten (ToDo-Boxen)
  - falls Ursache unklar: Vorschläge, wie Bot 1/2 zu ändern sind, um Fehler lokalisierbar zu machen
- Kommentar ins Issue: Marker '### bot3-analysis' (wird aktualisiert)
- Labels setzen: 'bot3-analysis' + 'triage-needed'

ENV (Secrets/Vars):
  OPENAI_API_KEY
  OPENAI_MODEL_DEFAULT  (z. B. gpt-5)
  OPENAI_REASONING_EFFORT (high)
  OPENAI_BASE_URL (optional)
  GITHUB_TOKEN
  GITHUB_REPOSITORY
  GITHUB_EVENT_PATH
  GH_EVENT_NAME or GITHUB_EVENT_NAME
"""

from __future__ import annotations
import os, re, io, json, sys, time, zipfile, textwrap, traceback
from pathlib import Path
from typing import Optional, List, Dict, Tuple
import requests

# ---------------- GitHub helpers ----------------

def repo() -> str:
    return os.environ["GITHUB_REPOSITORY"]

def evname() -> str:
    return os.environ.get("GH_EVENT_NAME") or os.environ.get("GITHUB_EVENT_NAME","")

def event() -> dict:
    return json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text(encoding="utf-8"))

def gh_api(method: str, path: str, payload: dict | None = None) -> dict:
    url = f"https://api.github.com{path}"
    headers = {"Authorization": f"Bearer {os.environ['GITHUB_TOKEN']}",
               "Accept": "application/vnd.github+json"}
    r = requests.request(method, url, headers=headers, json=payload, timeout=60)
    if r.status_code >= 300:
        raise RuntimeError(f"GitHub API {method} {path} failed: {r.status_code} {r.text[:600]}")
    try:
        return r.json()
    except Exception:
        return {}

def gh_api_raw(method: str, url: str, allow_redirects=True):
    headers = {"Authorization": f"Bearer {os.environ['GITHUB_TOKEN']}",
               "Accept": "application/vnd.github+json"}
    r = requests.request(method, url, headers=headers, allow_redirects=allow_redirects, stream=True, timeout=60)
    if r.status_code >= 300 and r.status_code not in (301,302):
        raise RuntimeError(f"GitHub RAW {method} {url} failed: {r.status_code} {r.text[:600]}")
    return r

def issue_number_from_label_event() -> Optional[int]:
    ev = event()
    if evname() == "issues":
        return (ev.get("issue") or {}).get("number")
    return None

def list_issue_comments(num: int) -> List[dict]:
    res = gh_api("GET", f"/repos/{repo()}/issues/{num}/comments")
    return res if isinstance(res, list) else []

def upsert_analysis_comment(num: int, markdown: str):
    marker = "### bot3-analysis"
    cid = None
    for c in list_issue_comments(num):
        if (c.get("body") or "").strip().startswith(marker):
            cid = c.get("id"); break
    if cid:
        gh_api("PATCH", f"/repos/{repo()}/issues/comments/{cid}", {"body": markdown})
    else:
        gh_api("POST", f"/repos/{repo()}/issues/{num}/comments", {"body": markdown})

def add_labels(num: int, labels: List[str]):
    gh_api("POST", f"/repos/{repo()}/issues/{num}/labels", {"labels": labels})

def default_branch() -> str:
    return gh_api("GET", f"/repos/{repo()}").get("default_branch","main")

# ---------------- Mapping: Workflow-Run → Issue ----------------

def find_issue_for_workflow_run() -> Optional[int]:
    """Robust: 1) Marker in Logs (issue:#123 / ISSUE_NUMBER=123), 2) PR Body (#123), 3) jüngstes Issue mit passenden Labels."""
    ev = event()
    run = ev.get("workflow_run") or {}
    # 1) Pull Requests am Run
    prs = run.get("pull_requests") or []
    pr_num = None
    if prs:
        pr_num = prs[0].get("number")
    # 1a) Versuche Inputs/Name zu parsen (nicht immer verfügbar)
    # → nicht überall zuverlässig, daher direkt Logs nach Marker durchsuchen
    try:
        # Logs-URL → ZIP
        logs_url = (gh_api_raw("GET", (run.get("logs_url") or ""), allow_redirects=False).headers.get("Location")
                    if run.get("logs_url") else None)
        data = gh_api_raw("GET", logs_url) .content if logs_url else None
        if data:
            with zipfile.ZipFile(io.BytesIO(data)) as zf:
                for name in zf.namelist():
                    if not name.lower().endswith(".txt"): continue
                    txt = zf.read(name).decode("utf-8", errors="replace")
                    m = re.search(r"(?:ISSUE_NUMBER\s*=\s*|issue:#)(\d+)", txt)
                    if m:
                        return int(m.group(1))
    except Exception:
        pass

    # 2) PR Body → '#123'
    if pr_num:
        pr = gh_api("GET", f"/repos/{repo()}/pulls/{pr_num}")
        body = (pr.get("body") or "") + "\n" + (pr.get("title") or "")
        m = re.search(r"#(\d+)", body)
        if m: return int(m.group(1))

    # 3) Heuristik: jüngstes Issue mit relevanten Labels
    issues = gh_api("GET", f"/repos/{repo()}/issues?state=open&per_page=20")
    cand = [i for i in issues if any(l.get("name") in ("contextmap-ready","contextmap-error","solver-error") for l in i.get("labels",[]))]
    if cand:
        cand.sort(key=lambda x: x.get("updated_at",""), reverse=True)
        return cand[0].get("number")
    return None

# ---------------- Logs sammeln ----------------

def collect_run_logs(run: dict) -> Tuple[str, Dict[str,int]]:
    """
    lädt ZIP mit allen Logs; gibt großen Text-Blob zurück + einfache Metriken
    """
    logs_url = run.get("logs_url","")
    if not logs_url:
        return ("", {})
    loc = gh_api_raw("GET", logs_url, allow_redirects=False).headers.get("Location")
    data = gh_api_raw("GET", loc).content
    big = []
    stats = {"files":0, "lines":0}
    with zipfile.ZipFile(io.BytesIO(data)) as zf:
        for name in zf.namelist():
            if not name.lower().endswith(".txt"): continue
            stats["files"] += 1
            try:
                txt = zf.read(name).decode("utf-8", errors="replace")
                stats["lines"] += txt.count("\n") + 1
                big.append(f"\n\n===== FILE: {name} =====\n{txt}")
            except Exception:
                continue
    return ("".join(big), stats)

def fetch_latest_failed_run_for_issue(num: int) -> Optional[dict]:
    """
    Heuristik: finde den jüngsten fehlgeschlagenen Run auf dem Repository,
    der (über PR-Body, Log-Marker oder Aktualität) dem Issue zuordenbar ist.
    """
    # schnelle Suche: letzte 20 workflow runs (completed)
    runs = gh_api("GET", f"/repos/{repo()}/actions/runs?status=completed&per_page=20")
    items = runs.get("workflow_runs", []) or runs.get("runs", []) or []
    # pick first failure
    for r in items:
        if r.get("conclusion") == "failure":
            # Versuch: PR->Issue
            prs = r.get("pull_requests") or []
            if prs:
                pr = gh_api("GET", f"/repos/{repo()}/pulls/{prs[0].get('number')}")
                m = re.search(r"#(\d+)", (pr.get("body") or "") + "\n" + (pr.get("title") or ""))
                if m and int(m.group(1)) == num:
                    return r
            # Logs nach Marker durchsuchen
            try:
                blob, _ = collect_run_logs(r)
                if re.search(fr"(?:ISSUE_NUMBER\s*=\s*|issue:#){num}\b", blob):
                    return r
            except Exception:
                pass
    return None

# ---------------- OpenAI Analyse ----------------

def analyze_with_openai(log_blob: str, context_hint: str) -> str:
    from openai import OpenAI
    api_key = os.environ.get("OPENAI_API_KEY")
    if not api_key: raise RuntimeError("OPENAI_API_KEY not set")
    client = OpenAI(api_key=api_key, base_url=(os.environ.get("OPENAI_BASE_URL") or "https://api.openai.com/v1"))
    model = os.environ.get("OPENAI_MODEL_DEFAULT","gpt-5")
    effort = "high"

    SYSTEM = (
        "You are a senior CI/build failure triage assistant. "
        "Given full workflow logs, write a concise, actionable incident analysis in German. "
        "Be specific, cite exact log snippets (short) and line anchors if possible."
    )
    USER = f"""Kontext-Hinweis:
{context_hint or '(kein zusätzlicher Kontext)'}

Vollständige Logs (gekürzt nur wenn nötig, aber analysiere alle Stellen):
{log_blob[:250000]}  # (Begrenzung um Request-Größen zu vermeiden)

Erzeuge Markdown mit diesem Aufbau:

### bot3-analysis
**Zusammenfassung**
- ein Satz

**Vermutete Ursache(n)**
- kurze Punkte mit Belegen (Zeilen oder kurze Snippets)

**Konkrete Schritte zur Behebung**
- Schritt 1 …
- Schritt 2 …

**Checkliste**
- [ ] Schritt 1 verifiziert
- [ ] Schritt 2 verifiziert

**Falls Ursache unklar**
- so änderst du Bot 1/2 (Logging, Marker, Inputs), damit der Fehler eindeutig lokalisierbar wird

**Gradle/Build-spezifisch (falls zutreffend)**
- AGP/Gradle/SDK/Deps Diagnose + exakte Maßnahmen

Halte es präzise und umsetzbar."""
    resp = client.responses.create(
        model=model,
        input=[{"role":"system","content":SYSTEM},{"role":"user","content":USER}],
        reasoning={"effort": effort},
    )
    return getattr(resp, "output_text", "").strip()

# ---------------- Hauptlogik ----------------

def main():
    # 1) Fall A: Label-Trigger (issues:labeled)
    if evname() == "issues":
        num = issue_number_from_label_event()
        if not num:
            print("::error::no issue"); sys.exit(1)
        labels = [l.get("name") for l in (event().get("issue") or {}).get("labels",[])]
        if not any(x in ("contextmap-error","solver-error") for x in labels):
            print("::notice::label not relevant"); sys.exit(0)

        run = fetch_latest_failed_run_for_issue(num)
        log_blob = ""
        if run:
            blob, stats = collect_run_logs(run)
            log_blob = blob
        analysis = analyze_with_openai(log_blob or "(Keine Logs greifbar – bitte Build erneut ausführen.)",
                                       context_hint="Label-Trigger vom Issue (contextmap-error / solver-error).")
        upsert_analysis_comment(num, analysis)
        # Labels setzen
        add_labels(num, ["bot3-analysis","triage-needed"])
        print("::notice::bot3-analysis posted (label trigger)")
        return

    # 2) Fall B: workflow_run failure
    if evname() == "workflow_run":
        ev = event()
        wr = ev.get("workflow_run") or {}
        if wr.get("conclusion") != "failure":
            print("::notice::workflow_run not failure; skip"); return

        # Issue zuordnen
        num = find_issue_for_workflow_run()
        if not num:
            print("::error::could not map workflow run to an issue"); sys.exit(1)

        blob, stats = collect_run_logs(wr)
        analysis = analyze_with_openai(blob, context_hint=f"Workflow '{wr.get('name')}' failed on branch '{wr.get('head_branch')}'.")
        upsert_analysis_comment(num, analysis)
        add_labels(num, ["bot3-analysis","triage-needed"])
        print("::notice::bot3-analysis posted (workflow_run)")
        return

    print("::notice::Event not handled by Bot 3")

if __name__ == "__main__":
    try:
        main()
    except SystemExit:
        raise
    except Exception as e:
        # letztes Netz – ohne Issue kontext nur Log
        print("::error::Bot3 unexpected failure:", e)
        print(traceback.format_exc())
        sys.exit(1)

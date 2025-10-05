#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
Bot 3 – Triage

- Reagiert auf issues:labeled (solver-error / contextmap-error) UND workflow_dispatch (mit ISSUE_NUMBER).
- Holt letzte fehlgeschlagene Runs zum Issue, saugt Logs (ZIP), fasst zusammen.
- OpenAI-Analyse (moderner Client): konkrete Ursachen-/Fix-Vorschläge.
- Auto-Retry: setzt 'triage-attempted' und stößt den Solver via dispatch erneut an.
- Bei erneutem Fehlschlag: 'triage-needed' und sauberer Abschluss.
"""
from __future__ import annotations
import os, re, sys, time, io, zipfile, requests
from pathlib import Path
from typing import Optional, Tuple, List, Dict

# ---------- GH helpers ----------
def repo() -> str: return os.environ["GITHUB_REPOSITORY"]

def gh_api(method: str, path: str, data=None):
    token = os.environ.get("GITHUB_TOKEN") or os.environ.get("INPUT_TOKEN")
    headers = {"Authorization": f"Bearer {token}", "Accept": "application/vnd.github+json"}
    url = f"https://api.github.com{path}"
    func = getattr(requests, method.lower(), None)
    if not func: raise RuntimeError(f"No requests method for {method}")
    resp = func(url, headers=headers, json=data) if data is not None else func(url, headers=headers)
    if resp.status_code >= 400:
        print(f"::error::GH {method} {path} -> {resp.status_code} {resp.text[:800]}")
    try: return resp.json()
    except Exception: return {}

def event() -> dict:
    path = os.environ.get("GITHUB_EVENT_PATH")
    if not path or not Path(path).exists(): return {}
    import json
    return json.loads(Path(path).read_text(encoding="utf-8"))

def evname() -> str:
    return os.environ.get("GH_EVENT_NAME") or os.environ.get("GITHUB_EVENT_NAME") or ""

def default_branch() -> str:
    info = gh_api("GET", f"/repos/{repo()}")
    return info.get("default_branch", "main")

# ---------- Issue / labels ----------
def list_issue_comments(num: int) -> list:
    return gh_api("GET", f"/repos/{repo()}/issues/{num}/comments") or []

def post_comment(num: int, body: str):
    gh_api("POST", f"/repos/{repo()}/issues/{num}/comments", {"body": body})

def add_labels(num: int, labels: list):
    gh_api("POST", f"/repos/{repo()}/issues/{num}/labels", {"labels": labels})

def remove_label(num: int, label: str):
    try:
        requests.delete(
            f"https://api.github.com/repos/{repo()}/issues/{num}/labels/{label}",
            headers={"Authorization": f"Bearer {os.environ['GITHUB_TOKEN']}", "Accept": "application/vnd.github+json"}
        )
    except Exception:
        pass

def issue_number_from_label_event() -> Optional[int]:
    ev = event()
    if ev.get("issue") and ev["issue"].get("number"): return ev["issue"]["number"]
    return None

# ---------- Runs / logs ----------
def workflow_run_issue_from_commit_message(run: dict) -> Optional[int]:
    msg = (run.get("head_commit") or {}).get("message") or ""
    m = re.search(r"\(issue\s+#(\d+)\)", msg)
    if m: return int(m.group(1))
    items = run.get("pull_requests") or []
    if items:
        pr = gh_api("GET", f"/repos/{repo()}/pulls/{items[0].get('number')}")
        body = pr.get("body") or ""
        m2 = re.search(r"\(issue\s+#(\d+)\)", body)
        if m2: return int(m2.group(1))
    return None

def fetch_latest_failed_run_for_issue(issue_num: int) -> dict:
    runs = gh_api("GET", f"/repos/{repo()}/actions/runs?status=failure&per_page=30") or {}
    items = runs.get("workflow_runs", []) or runs.get("runs", []) or []
    for r in items:
        num = workflow_run_issue_from_commit_message(r)
        if num == issue_num:
            return r
    return {}

def collect_run_logs(run: dict) -> Tuple[str, dict]:
    logs_url = run.get("logs_url")
    if not logs_url: return "", {}
    resp = requests.get(logs_url, headers={"Authorization": f"Bearer {os.environ.get('GITHUB_TOKEN')}"}, timeout=60)
    if resp.status_code != 200: raise RuntimeError(f"Logs download failed: {resp.status_code}")
    log_text = ""
    with zipfile.ZipFile(io.BytesIO(resp.content)) as z:
        for name in z.namelist():
            if name.endswith(".txt"):
                try: content = z.read(name).decode("utf-8", errors="ignore")
                except Exception: content = z.read(name).decode("latin-1", errors="ignore")
                log_text += f"\n----- {name} -----\n{content}"
    lines = log_text.splitlines()
    if len(lines) > 1000: log_text = "\n".join(lines[-1000:])
    return log_text, {"lines": len(log_text.splitlines())}

# ---------- Heuristics / OpenAI ----------
def gradle_heuristics(log_txt: str) -> str:
    hints=[]
    for ln in log_txt.splitlines()[-120:]:
        if " error:" in ln or "FAILURE:" in ln or "Exception" in ln:
            hints.append(ln.strip())
    return ("\n".join(hints))[:1000] if hints else ""

def analyze_with_openai(log_snippet: str, context_hint: str = "", heuristics: str = "") -> str:
    from openai import OpenAI
    client = OpenAI(api_key=os.environ.get("OPENAI_API_KEY"), base_url=(os.environ.get("OPENAI_BASE_URL") or "https://api.openai.com/v1"))
    model = os.environ.get("OPENAI_MODEL_DEFAULT") or "gpt-5"
    try:
        rsp = client.chat.completions.create(
            model=model,
            messages=[
                {"role": "system", "content": "Du analysierst CI-Fehler präzise und gibst konkrete Fix-Schritte."},
                {"role": "user", "content": f"{context_hint}\n\n```logs\n{log_snippet or 'Keine Logs'}\n```\n\nBekannte Hinweise:\n{heuristics or '-'}\n\nBitte: Ursachenanalyse + präzise Fix-Schritte (Markdown, kurz & konkret)."}
            ],
            temperature=0
        )
        return rsp.choices[0].message.content
    except Exception as e:
        return f"**Analyse nicht möglich:** {e}"

# ---------- Solver dispatch ----------
def dispatch_solver(issue_num: int):
    gh_api("POST", f"/repos/{repo()}/actions/workflows/codex-solve.yml/dispatches",
           {"ref": default_branch(), "inputs": {"issue": str(issue_num)}})

# ---------- Analysis comment ----------
def upsert_analysis_comment(num: int, markdown: str):
    marker = "### bot3-analysis"
    cid=None
    for c in list_issue_comments(num):
        if (c.get("body") or "").strip().startswith(marker):
            cid=c.get("id"); break
    body=(marker+"\n"+markdown).strip()
    if cid:
        gh_api("PATCH", f"/repos/{repo()}/issues/comments/{cid}", {"body": body})
    else:
        post_comment(num, body)

# ---------- Main ----------
def main():
    evn = evname()
    # 1) Manual dispatch: ISSUE_NUMBER muss gesetzt sein
    if evn == "workflow_dispatch":
        issue_str = os.environ.get("ISSUE_NUMBER") or ""
        if not issue_str.strip():
            print("::error::workflow_dispatch ohne ISSUE_NUMBER"); sys.exit(1)
        num = int(issue_str.strip())
        # Wir versuchen direkt eine knappe Analyse + Re-Dispatch des Solvers
        run = fetch_latest_failed_run_for_issue(num)
        blob, _ = collect_run_logs(run) if run else ("", {})
        analysis = analyze_with_openai(blob or "(keine Logs vom letzten Run)", context_hint="Manueller Triage-Start", heuristics=gradle_heuristics(blob or ""))
        upsert_analysis_comment(num, analysis)
        # Auto-Retry (einmalig): triage-attempted Label
        issue = gh_api("GET", f"/repos/{repo()}/issues/{num}")
        labels = [l.get("name") for l in issue.get("labels",[])]
        if "triage-attempted" in labels:
            remove_label(num,"solver-error"); remove_label(num,"contextmap-error"); remove_label(num,"contextmap-ready"); remove_label(num,"triage-attempted")
            add_labels(num, ["triage-needed"])
            post_comment(num, "⚠️ Triage: Automatischer Fix bereits versucht – `triage-needed` gesetzt.")
            return
        add_labels(num, ["triage-attempted"])
        dispatch_solver(num)
        post_comment(num, "🔁 Triage: Analyse abgeschlossen, Solver erneut gestartet (workflow_dispatch).")
        return

    # 2) Label-Trigger
    if evn == "issues":
        ev = event()
        num = (ev.get("issue") or {}).get("number")
        if not num: print("::error::No issue number"); sys.exit(1)
        labels = [l.get("name") for l in (ev.get("issue") or {}).get("labels", [])]
        if not any(x in ("contextmap-error","solver-error") for x in labels):
            print("::notice::irrelevantes Label"); sys.exit(0)

        run = fetch_latest_failed_run_for_issue(num)
        blob, _ = collect_run_logs(run) if run else ("", {})
        heur = gradle_heuristics(blob or "")
        analysis = analyze_with_openai(blob or "(keine Logs gefunden)", context_hint="Label-Trigger", heuristics=heur)
        upsert_analysis_comment(num, analysis)

        if "triage-attempted" in labels:
            remove_label(num,"solver-error"); remove_label(num,"contextmap-error"); remove_label(num,"contextmap-ready"); remove_label(num,"triage-attempted")
            add_labels(num, ["triage-needed"])
            post_comment(num, "⚠️ Triage: Automatischer Fix nicht erfolgreich – `triage-needed` gesetzt.")
            return

        # Erstversuch: Solver erneut starten
        remove_label(num,"solver-error"); remove_label(num,"contextmap-error"); remove_label(num,"contextmap-ready")
        add_labels(num, ["triage-attempted"])
        dispatch_solver(num)
        post_comment(num, "🔁 Triage: Fehler analysiert, Solver wird erneut ausgeführt.")
        return

    # 3) Andere Events ignorieren
    print("::notice::Triage: Kein relevanter Event-Typ.")
    return

if __name__ == "__main__":
    main()
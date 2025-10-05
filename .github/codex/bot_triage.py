#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
Bot 3 – Triage (Fehlerdoktor / Triage)

Reagiert auf:
- Issue labels: contextmap-error (Bot 1) und solver-error (Bot 2)
- workflow_run: completed mit conclusion=failure (alle fehlgeschlagenen Workflows)
Aufgaben:
- Analysiert Fehlermeldungen (Parser-/Buildfehler, Apply-Fails)
- Postet Analyse als Kommentar im Issue (Markdown mit "### bot3-analysis")
- Versucht automatischen Fix:
  - Bei erstem Fehler: Fix-Vorschlag ermitteln und Bot 2 erneut anstoßen (Issue neu labeln mit contextmap-ready)
  - Bei erneutem Fehler: Abbruch mit Label triage-needed (manuelle Nacharbeit)
"""
import os
import sys
import time
import requests
import base64

# ---------------- GitHub Access ----------------

def repo() -> str:
    return os.environ["GITHUB_REPOSITORY"]

def gh_api(method: str, path: str, data=None):
    """GitHub API Helferfunktion (bearer token Auth)"""
    token = os.environ.get("GITHUB_TOKEN") or os.environ.get("INPUT_TOKEN")
    headers = {
        "Authorization": f"Bearer {token}",
        "Accept": "application/vnd.github+json"
    }
    url = f"https://api.github.com{path}"
    func = getattr(requests, method.lower(), None)
    if not func:
        raise RuntimeError(f"No requests method for {method}")
    resp = func(url, headers=headers, json=data) if data is not None else func(url, headers=headers)
    if resp.status_code >= 400:
        msg = resp.text[:1000]
        print(f"::error::GH API {method} {path} failed: {resp.status_code} {msg}")
    try:
        return resp.json()
    except Exception:
        return {}

def event() -> dict:
    """Lädt GitHub Event Payload"""
    path = os.environ.get("GITHUB_EVENT_PATH")
    if not path or not os.path.isfile(path):
        return {}
    with open(path, "r", encoding="utf-8") as f:
        import json
        return json.load(f)

def evname() -> str:
    return os.environ.get("GH_EVENT_NAME") or ""

# ---------------- Utility Functions ----------------

def issue_number_from_label_event() -> int:
    """Extrahiert Issue-Nummer aus dem Event (Issue-Label-Trigger)"""
    ev = event()
    # Im Label-Event liegt die Issue-Nummer hier:
    if ev.get("issue") and ev["issue"].get("number"):
        return ev["issue"]["number"]
    # Alternativer Fallback (nicht erwartet):
    return None

def workflow_run_issue_from_commit_message(run: dict) -> int:
    """Versucht aus Commit-/PR-Infos die Issue-Nummer zu ermitteln."""
    # commit message "codex: solver apply" mit "(issue #123)" am Ende
    msg = (run.get("head_commit") or {}).get("message") or ""
    import re
    m = re.search(r"\(issue\s+#(\d+)\)", msg)
    if m:
        return int(m.group(1))
    # PR body evtl. mit "(issue #123)"
    items = run.get("pull_requests") or []
    if items:
        pr = gh_api("GET", f"/repos/{repo()}/pulls/{items[0].get('number')}")
        body = pr.get("body") or ""
        m2 = re.search(r"\(issue\s+#(\d+)\)", body)
        if m2:
            return int(m2.group(1))
    return None

def fetch_latest_failed_run_for_issue(issue_num: int) -> dict:
    """Lädt den letzten fehlgeschlagenen Actions-Workflow-Run, der zu einem Issue gehört (PR/commit referenziert Issue)."""
    runs = gh_api("GET", f"/repos/{repo()}/actions/runs?status=failure&per_page=20") or {}
    items = runs.get("workflow_runs", []) or runs.get("runs", []) or []
    for r in items:
        # Suche nach Issue-Referenz im Commit-Message oder PR-Text
        num = workflow_run_issue_from_commit_message(r)
        if num == issue_num:
            return r
    return {}

def list_issue_comments(num: int) -> list:
    res = gh_api("GET", f"/repos/{repo()}/issues/{num}/comments")
    return res if isinstance(res, list) else []

def upsert_analysis_comment(num: int, markdown: str):
    """Erstellt oder aktualisiert einen Issue-Kommentar für die Analyse (beginnt mit '### bot3-analysis')."""
    marker = "### bot3-analysis"
    cid = None
    for c in list_issue_comments(num):
        if (c.get("body") or "").strip().startswith(marker):
            cid = c.get("id")
            break
    body = f"{marker}\n{markdown}".strip()
    if cid:
        # Update bestehenden Kommentar
        gh_api("PATCH", f"/repos/{repo()}/issues/comments/{cid}", {"body": body})
    else:
        # Neuen Kommentar erstellen
        gh_api("POST", f"/repos/{repo()}/issues/{num}/comments", {"body": body})

def add_labels(num: int, labels: list):
    gh_api("POST", f"/repos/{repo()}/issues/{num}/labels", {"labels": labels})

def remove_label(num: int, label: str):
    try:
        requests.delete(f"https://api.github.com/repos/{repo()}/issues/{num}/labels/{label}",
                        headers={"Authorization": f"Bearer {os.environ['GITHUB_TOKEN']}", "Accept": "application/vnd.github+json"})
    except Exception as e:
        print(f"::warning::Failed to remove label {label}: {e}")

# ---------------- Analysis / AI Functions ----------------

def gradle_heuristics(log_txt: str) -> str:
    """
    Versucht typische Build/Parserfehler im Log zu erkennen und gibt einen kurzen Hinweistext zurück.
    (z.B. Zeilennummer bei Syntaxfehler, fehlende Symbole etc.)
    """
    lines = log_txt.splitlines()
    hints = []
    for ln in lines[-50:]:  # nur Ende des Logs betrachten
        if " error:" in ln or "FAILURE:" in ln:
            hints.append(ln.strip())
    return ("\n".join(hints))[:500] if hints else ""

def analyze_with_openai(log_snippet: str, context_hint: str = "", heuristics: str = "") -> str:
    """
    Fragt OpenAI nach einer Analyse des Fehlers und einem Lösungsvorschlag.
    Nutzt gegebenenfalls Ausschnitte aus Logs und Heuristiken.
    """
    import openai
    system_prompt = "Du bist ein hilfreicher Assistent, der Software-Fehlermeldungen analysiert und Lösungsvorschläge gibt."
    user_prompt = f"{context_hint}\n\n```logs\n{log_snippet or 'Keine Log-Ausgabe verfügbar.'}\n```\n{('Bekannte Hinweise: ' + heuristics) if heuristics else ''}\n\nAnalyse und Lösungsvorschlag (in Markdown):"
    model = os.environ.get("OPENAI_MODEL_DEFAULT") or "gpt-4"
    try:
        rsp = openai.ChatCompletion.create(
            model=model,
            messages=[
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_prompt}
            ],
            temperature=0
        )
        answer = rsp['choices'][0]['message']['content']
        return answer
    except Exception as e:
        print(f"::error::OpenAI API request failed: {e}")
        return f"**Fehler bei der automatischen Analyse:** {e}"

# ---------------- Main Logic ----------------

def main():
    # A) Label-Trigger
    if evname() == "issues":
        num = issue_number_from_label_event()
        if not num:
            print("::error::No issue number"); sys.exit(1)
        labels = [l.get("name") for l in (event().get("issue") or {}).get("labels", [])]
        if not any(x in ("contextmap-error", "solver-error") for x in labels):
            print("::notice::Label not relevant"); sys.exit(0)

        # Hole letzten fehlgeschlagenen Run (z.B. Build) zum Issue, falls vorhanden
        run = fetch_latest_failed_run_for_issue(num)
        blob = ""
        if run:
            blob, stats = collect_run_logs(run)
        heur = gradle_heuristics(blob or "")

        # OpenAI-Analyse des Fehlers (Log-Auszug + Heuristik)
        analysis = analyze_with_openai(blob or "(Keine Logs greifbar – bitte Build erneut ausführen.)",
                                       context_hint="Label-Trigger vom Issue (contextmap-error / solver-error).",
                                       heuristics=heur)
        # Kommentar mit Analyse im Issue erstellen/aktualisieren
        upsert_analysis_comment(num, analysis)

        # Automatische Triage (Fehlerbehebung versuchen)
        if "triage-attempted" in labels:
            # Bereits ein Versuch unternommen, gebe auf
            remove_label(num, "solver-error")
            remove_label(num, "contextmap-error")
            remove_label(num, "contextmap-ready")
            remove_label(num, "triage-attempted")
            add_labels(num, ["triage-needed"])
            # Kommentar: manuelle Intervention nötig
            try:
                gh_api("POST", f"/repos/{repo()}/issues/{num}/comments", {
                    "body": "⚠️ Triage: Automatische Fehlerbehebung nicht erfolgreich – manuelle Überprüfung erforderlich (Label `triage-needed` gesetzt)."
                })
            except Exception as e:
                print(f"::warning::Failed to post final triage comment: {e}")
            print("::notice::Triage failed, human intervention required.")
            return
        else:
            # Erster automatischer Korrekturversuch
            remove_label(num, "solver-error")
            remove_label(num, "contextmap-error")
            remove_label(num, "contextmap-ready")
            add_labels(num, ["triage-attempted", "contextmap-ready"])
            try:
                gh_api("POST", f"/repos/{repo()}/issues/{num}/comments", {
                    "body": "🔁 Triage: Fehler analysiert, Korrekturversuch eingeleitet – Bot 2 wird erneut ausgeführt."
                })
            except Exception as e:
                print(f"::warning::Failed to post triage restart comment: {e}")
            print("::notice::Triage fix attempt posted, re-triggering Bot 2.")
            return

    # B) (Optional) Workflow_run Trigger – in der aktuellen Version nicht genutzt
    if evname() == "workflow_run":
        wr = (event().get("workflow_run") or {})
        if wr.get("conclusion","") != "failure":
            print("::notice::Workflow run concluded not failure -> no action"); sys.exit(0)
        num = workflow_run_issue_from_commit_message(wr) or workflow_run_issue_from_commit_message(wr)  # fallback doppelt
        if not num:
            print("::warning::Konnte Issue zu fehlgeschlagenem Workflow nicht ermitteln."); sys.exit(0)
        # Logs holen
        run = wr or {}
        blob = ""
        try:
            blob, stats = collect_run_logs(run)
        except Exception as e:
            print(f"::error::Log fetch failed: {e}")
        heur = gradle_heuristics(blob or "")
        # OpenAI-Analyse
        analysis = analyze_with_openai(blob or "(Keine Logs verfügbar.)",
                                       context_hint=f"workflow_run Trigger: Workflow '{wr.get('name')}' ist fehlgeschlagen.",
                                       heuristics=heur)
        upsert_analysis_comment(num, analysis)
        # Label setzen, damit Label-Trigger auch feuert (sofern nicht schon vorhanden)
        existing = [l.get("name") for l in (event().get("workflow_run") or {}).get("labels",[])]
        if "solver-error" not in existing:
            add_labels(num, ["solver-error"])
        print("::notice::Failure workflow_run analysis posted, solver-error label added for issue.")
        # (Bot 3 wird dann über Label-Trigger erneut aufgerufen)
        return

if __name__ == "__main__":
    main()

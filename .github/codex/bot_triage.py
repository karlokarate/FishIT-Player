#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
Bot 3 – Triage (Fehlerdoktor / Triage)

Reagiert auf:
- Issue-Labels: contextmap-error (Bot 1) **und** solver-error (Bot 2)
- workflow_run: completed mit conclusion=failure (alle fehlgeschlagenen Workflows)

Aufgaben:
- Analysiert Fehlermeldungen (Parser-/Buildfehler, Apply-Fails)
- Postet Analyse als Kommentar im Issue (Markdown-Bereich beginnend mit "### bot3-analysis")
- Versucht automatischen Fix:
  - Beim **ersten** Fehler: Lösungsvorschlag ermitteln und Bot 2 erneut anstoßen (früher per Label contextmap-ready, jetzt via Dispatch)
  - Bei **erneutem** Fehler: Abbruch mit Label `triage-needed` (manuelle Nacharbeit erforderlich)
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
    """GitHub API Helferfunktion (Bearer Token Authentifizierung)"""
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
    """Lädt das GitHub-Event-Payload (JSON)"""
    path = os.environ.get("GITHUB_EVENT_PATH")
    if not path or not os.path.isfile(path):
        return {}
    with open(path, "r", encoding="utf-8") as f:
        import json
        return json.load(f)

def evname() -> str:
    return os.environ.get("GH_EVENT_NAME") or ""

# ---------------- Utility Functions ----------------

def issue_number_from_label_event() -> Optional[int]:
    """Extrahiert die Issue-Nummer aus dem Event (für issues:labeled Trigger)"""
    ev = event()
    if ev.get("issue") and ev["issue"].get("number"):
        return ev["issue"]["number"]
    return None

def workflow_run_issue_from_commit_message(run: dict) -> Optional[int]:
    """Versucht aus Commit-/PR-Infos die Issue-Nummer zu ermitteln."""
    # Erwartet im Commit-Message Ende: "(issue #123)"
    msg = (run.get("head_commit") or {}).get("message") or ""
    import re
    m = re.search(r"\(issue\s+#(\d+)\)", msg)
    if m:
        return int(m.group(1))
    # Alternativ: PR-Body nach "(issue #123)" durchsuchen
    items = run.get("pull_requests") or []
    if items:
        pr = gh_api("GET", f"/repos/{repo()}/pulls/{items[0].get('number')}")
        body = pr.get("body") or ""
        m2 = re.search(r"\(issue\s+#(\d+)\)", body)
        if m2:
            return int(m2.group(1))
    return None

def fetch_latest_failed_run_for_issue(issue_num: int) -> dict:
    """Lädt den letzten fehlgeschlagenen Actions-Workflow-Run, der zu einem Issue gehört (Commit/PR referenziert die Issue-Nummer)."""
    runs = gh_api("GET", f"/repos/{repo()}/actions/runs?status=failure&per_page=20") or {}
    items = runs.get("workflow_runs", []) or runs.get("runs", []) or []
    for r in items:
        # Prüfe, ob Commit-Message oder PR-Text die Issue-Nummer erwähnt
        num = workflow_run_issue_from_commit_message(r)
        if num == issue_num:
            return r
    return {}

def list_issue_comments(num: int) -> list:
    res = gh_api("GET", f"/repos/{repo()}/issues/{num}/comments")
    return res if isinstance(res, list) else []

def upsert_analysis_comment(num: int, markdown: str):
    """Erstellt oder aktualisiert einen Issue-Kommentar mit der Analyse (beginnt mit '### bot3-analysis')."""
    marker = "### bot3-analysis"
    existing_comment_id = None
    for c in list_issue_comments(num):
        if (c.get("body") or "").strip().startswith(marker):
            existing_comment_id = c.get("id")
            break
    body = f"{marker}\n{markdown}".strip()
    if existing_comment_id:
        # Bestehenden Kommentar aktualisieren
        gh_api("PATCH", f"/repos/{repo()}/issues/comments/{existing_comment_id}", {"body": body})
    else:
        # Neuen Kommentar erstellen
        gh_api("POST", f"/repos/{repo()}/issues/{num}/comments", {"body": body})

def add_labels(num: int, labels: list):
    gh_api("POST", f"/repos/{repo()}/issues/{num}/labels", {"labels": labels})

def remove_label(num: int, label: str):
    try:
        requests.delete(
            f"https://api.github.com/repos/{repo()}/issues/{num}/labels/{label}",
            headers={
                "Authorization": f"Bearer {os.environ['GITHUB_TOKEN']}",
                "Accept": "application/vnd.github+json"
            }
        )
    except Exception as e:
        print(f"::warning::Failed to remove label {label}: {e}")

# ---------------- Analysis / AI Functions ----------------

def gradle_heuristics(log_txt: str) -> str:
    """
    Sucht typische Build- oder Parser-Fehler im Log (z.B. " error:" oder "FAILURE:") und gibt die letzten Fundstellen zurück.
    """
    lines = log_txt.splitlines()
    hints = []
    for ln in lines[-50:]:  # nur die letzten Zeilen betrachten
        if " error:" in ln or "FAILURE:" in ln:
            hints.append(ln.strip())
    return ("\n".join(hints))[:500] if hints else ""

def analyze_with_openai(log_snippet: str, context_hint: str = "", heuristics: str = "") -> str:
    """
    Fragt OpenAI nach einer Analyse des Fehlers und einem Lösungsvorschlag (in Markdown).
    Nutzt Ausschnitte aus Logs (`log_snippet`) und erkannte Hinweise (`heuristics`).
    """
    import openai
    system_prompt = "Du bist ein hilfreicher Assistent, der Software-Fehlermeldungen analysiert und Lösungsvorschläge gibt."
    user_prompt = f"{context_hint}\n\n```logs\n{log_snippet or 'Keine Log-Ausgabe verfügbar.'}\n```\n" \
                  f"{('Bekannte Hinweise: ' + heuristics) if heuristics else ''}\n\nAnalyse und Lösungsvorschlag (in Markdown):"
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
        return rsp['choices'][0]['message']['content']
    except Exception as e:
        print(f"::error::OpenAI API request failed: {e}")
        return f"**Fehler bei der automatischen Analyse:** {e}"

# ---------------- Log Retrieval ----------------

def collect_run_logs(run: dict) -> Tuple[str, dict]:
    """
    Lädt die Logs eines Workflow-Runs (ZIP) herunter und gibt den konsolidierten Log-Text (und einfache Statistiken) zurück.
    """
    logs_url = run.get("logs_url")
    if not logs_url:
        return "", {}
    resp = requests.get(logs_url, headers={"Authorization": f"Bearer {os.environ.get('GITHUB_TOKEN')}"})
    if resp.status_code != 200:
        raise RuntimeError(f"Failed to download logs: {resp.status_code}")
    import io, zipfile
    log_text = ""
    with zipfile.ZipFile(io.BytesIO(resp.content)) as z:
        for name in z.namelist():
            if name.endswith(".txt"):
                try:
                    content = z.read(name).decode("utf-8", errors="ignore")
                except Exception:
                    content = z.read(name).decode("latin-1", errors="ignore")
                # Anhängen der einzelnen Job-Logs, jeweils mit Trenner:
                log_text += f"\n----- {name} -----\n{content}"
    # Bei sehr langen Logs: nur die letzten 1000 Zeilen behalten
    lines = log_text.splitlines()
    if len(lines) > 1000:
        lines = lines[-1000:]
        log_text = "\n".join(lines)
    return log_text, {"lines": len(lines)}

# ---------------- Solver dispatch helper ----------------

def default_branch() -> str:
    info = gh_api("GET", f"/repos/{repo()}")
    return info.get("default_branch", "main")

def dispatch_solver(issue_num: int):
    """Trigger den Solver (Bot 2) Workflow via workflow_dispatch."""
    gh_api("POST", f"/repos/{repo()}/actions/workflows/codex-solve.yml/dispatches",
           {"ref": default_branch(), "inputs": {"issue": str(issue_num)}})

# ---------------- Main Logic ----------------

def main():
    # A) Trigger durch Issue-Label (solver-error oder contextmap-error)
    if evname() == "issues":
        num = issue_number_from_label_event()
        if not num:
            print("::error::No issue number")
            sys.exit(1)
        labels = [l.get("name") for l in (event().get("issue") or {}).get("labels", [])]
        if not any(x in ("contextmap-error", "solver-error") for x in labels):
            print("::notice::Label not relevant")
            sys.exit(0)

        # Hole den letzten fehlgeschlagenen Workflow-Run zum Issue (falls vorhanden, z.B. Build oder Solver-Run)
        run = fetch_latest_failed_run_for_issue(num)
        blob = ""
        if run:
            blob, stats = collect_run_logs(run)
        heur = gradle_heuristics(blob or "")

        # Analyse des Fehlers mit OpenAI (Log-Auszug + erkannte Hinweise)
        analysis = analyze_with_openai(
            blob or "(Keine Logs greifbar – bitte Build erneut ausführen.)",
            context_hint="Label-Trigger vom Issue (contextmap-error/solver-error).",
            heuristics=heur
        )
        upsert_analysis_comment(num, analysis)

        # Automatische Fehlerbehebung versuchen
        if "triage-attempted" in labels:
            # Bereits ein automatischer Versuch unternommen -> Abbruch
            remove_label(num, "solver-error")
            remove_label(num, "contextmap-error")
            remove_label(num, "contextmap-ready")
            remove_label(num, "triage-attempted")
            add_labels(num, ["triage-needed"])
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
            add_labels(num, ["triage-attempted"])
            try:
                dispatch_solver(num)  # Bot 2 erneut starten
                gh_api("POST", f"/repos/{repo()}/issues/{num}/comments", {
                    "body": "🔁 Triage: Fehler analysiert, Korrekturversuch eingeleitet – Bot 2 wird erneut ausgeführt."
                })
            except Exception as e:
                # Solver-Bot konnte nicht gestartet werden
                remove_label(num, "triage-attempted")
                add_labels(num, ["triage-needed"])
                try:
                    gh_api("POST", f"/repos/{repo()}/issues/{num}/comments", {
                        "body": "⚠️ Triage: Korrekturversuch **konnte nicht gestartet werden** – manuelle Überprüfung erforderlich (Label `triage-needed` gesetzt)."
                    })
                except Exception as e2:
                    print(f"::warning::Failed to post triage failure comment: {e2}")
                print(f"::error::Failed to dispatch solver: {e}")
                return
            print("::notice::Triage fix attempt posted, re-triggering Bot 2.")
            return

    # B) (Optional) Trigger durch fehlgeschlagenen Workflow-Run (momentan nicht genutzt)
    if evname() == "workflow_run":
        wr = event().get("workflow_run") or {}
        if wr.get("conclusion", "") != "failure":
            print("::notice::Workflow run concluded not failure -> no action")
            sys.exit(0)
        num = workflow_run_issue_from_commit_message(wr)
        if not num:
            print("::warning::Konnte Issue für fehlgeschlagenen Workflow nicht ermitteln.")
            sys.exit(0)
        run = wr  # nutze den aktuellen fehlgeschlagenen Run
        blob = ""
        try:
            blob, stats = collect_run_logs(run)
        except Exception as e:
            print(f"::error::Log fetch failed: {e}")
        heur = gradle_heuristics(blob or "")
        analysis = analyze_with_openai(
            blob or "(Keine Logs verfügbar.)",
            context_hint=f"Workflow_run Trigger: Workflow '{wr.get('name')}' ist fehlgeschlagen.",
            heuristics=heur
        )
        upsert_analysis_comment(num, analysis)
        # Label setzen, um den Label-Trigger (oben) auszulösen, falls nicht schon gesetzt
        existing = [l.get("name") for l in wr.get("labels", [])]
        if "solver-error" not in existing:
            add_labels(num, ["solver-error"])
        print("::notice::Failure workflow_run analysis posted, solver-error label added for issue.")
        # (Bot 3 wird dann über den Label-Trigger erneut gestartet)
        return

if __name__ == "__main__":
    main()
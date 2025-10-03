#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
Bot 1 – ContextMap (Planner)
- Trigger: Issue-Body beginnt mit '/codex' (nicht Kommentar, nicht Titel)
- Liest Doku: AGENTS.md, ARCHITECTURE_OVERVIEW.md, ROADMAP.md, CHANGELOG.md
- Ermittelt nur tatsächlich existierende Module/Dateien (Manifest/Gradle inklusive)
- Erzeugt kompakte ContextMap (<= 10.000 Zeichen), Markdown:
  Problemzusammenfassung / (potentiell) betroffene Module / Annahmen / Unklarheiten / Risiken / Nächste Schritte (5-Punkte-Plan)
- Aktualisiert bestehenden ContextMap-Kommentar oder erstellt neuen
- Labels: contextmap-ready (Erfolg), contextmap-error (Fehler)
- Dispatcht anschließend Bot 2 (codex-solve.yml) via workflow_dispatch (mit Issue-Nummer)

Deps: openai, requests
"""

from __future__ import annotations
import os, json, re, subprocess, sys
from pathlib import Path
from typing import List, Optional
import requests

# ---------- GitHub Utils ----------

def gh_repo() -> str:
    return os.environ["GITHUB_REPOSITORY"]

def gh_event_path() -> str:
    return os.environ["GITHUB_EVENT_PATH"]

def gh_event_name() -> str:
    return os.environ.get("GH_EVENT_NAME") or os.environ.get("GITHUB_EVENT_NAME","")

def gh_api(method: str, path: str, payload: dict | None = None) -> dict:
    url = f"https://api.github.com{path}"
    headers = {
        "Authorization": f"Bearer {os.environ['GITHUB_TOKEN']}",
        "Accept": "application/vnd.github+json",
    }
    resp = requests.request(method, url, headers=headers, json=payload, timeout=45)
    resp.raise_for_status()
    try:
        return resp.json()
    except Exception:
        return {}

def issue_number_from_event() -> Optional[int]:
    ev = json.loads(Path(gh_event_path()).read_text(encoding="utf-8"))
    if gh_event_name() in ("issues", "issue_comment"):
        issue = ev.get("issue") or {}
        return issue.get("number")
    if gh_event_name() == "workflow_dispatch":
        return None
    return None

def read_issue_body() -> str:
    ev = json.loads(Path(gh_event_path()).read_text(encoding="utf-8"))
    if gh_event_name() in ("issues","issue_comment"):
        return (ev.get("issue") or {}).get("body","") or ""
    if gh_event_name() == "workflow_dispatch":
        return os.environ.get("DISPATCH_COMMENT","") or ""
    return ""

def list_issue_comments(issue_number: int) -> List[dict]:
    data = gh_api("GET", f"/repos/{gh_repo()}/issues/{issue_number}/comments")
    return data if isinstance(data, list) else []

def upsert_contextmap_comment(issue_number: int, markdown: str) -> None:
    comments = list_issue_comments(issue_number)
    marker = "### contextmap-ready"
    target_id = None
    for c in comments:
        body = c.get("body","") or ""
        if body.strip().startswith(marker):
            target_id = c.get("id"); break
    if target_id:
        gh_api("PATCH", f"/repos/{gh_repo()}/issues/comments/{target_id}", {"body": markdown})
    else:
        gh_api("POST", f"/repos/{gh_repo()}/issues/{issue_number}/comments", {"body": markdown})

def add_label(issue_number: int, label: str) -> None:
    gh_api("POST", f"/repos/{gh_repo()}/issues/{issue_number}/labels", {"labels":[label]})

def remove_label(issue_number: int, label: str) -> None:
    try:
        requests.delete(
            f"https://api.github.com/repos/{gh_repo()}/issues/{issue_number}/labels/{label}",
            headers={"Authorization": f"Bearer {os.environ['GITHUB_TOKEN']}","Accept":"application/vnd.github+json"},
            timeout=30
        )
    except Exception:
        pass

# ---------- Repo scan ----------

def git_ls_files() -> List[str]:
    try:
        out = subprocess.run("git ls-files", shell=True, text=True, capture_output=True, check=True)
        return [l.strip() for l in out.stdout.splitlines() if l.strip()]
    except Exception:
        return []

def discover_modules_and_important_paths(files: List[str]) -> List[str]:
    paths=set(); pset=set(files)
    for f in files:
        if f.endswith(("build.gradle","build.gradle.kts","settings.gradle","settings.gradle.kts")):
            paths.add(f)
        if f.endswith("AndroidManifest.xml"):
            paths.add(f)
    for f in files:
        if f.endswith(("build.gradle","build.gradle.kts")):
            paths.add(Path(f).parent.as_posix())
    for d in ("app","core","data","domain","ui"):
        if any(x==d or x.startswith(d+"/") for x in pset):
            paths.add(d)
    return sorted(paths)

# ---------- OpenAI ----------

def load_docs_text() -> str:
    docs=[]
    for name in ["AGENTS.md","ARCHITECTURE_OVERVIEW.md","ROADMAP.md","CHANGELOG.md"]:
        if Path(name).exists():
            t = Path(name).read_text(encoding="utf-8", errors="replace")
            docs.append(f"\n--- {name} ---\n{t}")
    return "".join(docs) if docs else ""

def call_openai_contextmap(issue_text: str, allowed_paths: List[str]) -> str:
    from openai import OpenAI
    api_key = os.environ.get("OPENAI_API_KEY")
    if not api_key: raise RuntimeError("OPENAI_API_KEY not set")
    base = os.environ.get("OPENAI_BASE_URL") or "https://api.openai.com/v1"
    client = OpenAI(api_key=api_key, base_url=base)
    model = os.environ.get("OPENAI_MODEL_DEFAULT","gpt-5")
    effort = "low"

    docs = load_docs_text()
    allowed_preview = "\n".join(allowed_paths[:200])

    system = (
        "You are a planning assistant. Read the issue and repository docs to create a CONTEXT MAP for a developer.\n"
        "STRICT RULES:\n"
        "- Output <= 9500 characters.\n"
        "- The '(potentiell) betroffene Module' list MUST ONLY contain paths from the provided 'Allowed paths' list.\n"
        "- Do NOT invent modules. No code, planning only."
    )
    user = f"""Issue (body starting with /codex):
{issue_text}

Allowed paths (only pick from these; include Gradle/Manifest as needed):
{allowed_preview}

Docs (AGENTS → ARCHITECTURE_OVERVIEW → ROADMAP → CHANGELOG):
{docs if docs else '(no docs present)'}

Create a concise Markdown comment in German with sections, exactly in this order:

### contextmap-ready
#### Problemzusammenfassung
(kurz)

#### (potentiell) betroffene Module
- <pfad1>
- <pfad2>
(verwende nur Pfade aus 'Allowed paths')

#### Annahmen
- …

#### Unklarheiten
- …

#### Risiken
- …

#### Nächste Schritte (5-Punkte-Plan)
1. Reihenfolge: Modul X, Modul Y, Modul Z bearbeiten (konkret aus den betroffenen Pfaden)
2. Module komplett batchweise einlesen vor dem Bearbeiten
3. Weitere Module, die sich aus dem Einlesen ergeben, miteinbeziehen und ebenfalls komplett einlesen
4. Bearbeiten: Code fixen/ändern; neue Module/Helper bauen
5. Gegencheck im Gesamtkontext: 
   - Sind alle Importe vorhanden? 
     a) innerhalb des bearbeiteten Moduls 
     b) in abhängigen/mitbetroffenen Modulen
   - Sind alle Functions korrekt definiert, oder Nacharbeit nötig?

Remember: do not exceed 9500 characters total.
"""
    resp = client.responses.create(
        model=model,
        input=[{"role":"system","content":system},{"role":"user","content":user}],
        reasoning={"effort": effort},
    )
    return getattr(resp,"output_text","").strip()

def ensure_under_hard_limit(s: str, hard_limit: int=10000) -> bool:
    return len(s) <= hard_limit

# ---------- Main ----------

def main():
    os.environ.setdefault("OPENAI_REASONING_EFFORT","low")
    issue_text = read_issue_body()
    if not issue_text.strip().startswith("/codex"):
        print("::notice::Issue body does not start with /codex – skipping."); return

    num = issue_number_from_event()
    if not num:
        print("::warning::No issue number in event; cannot comment."); return

    files = git_ls_files()
    allowed_paths = discover_modules_and_important_paths(files)

    try:
        cm = call_openai_contextmap(issue_text=issue_text, allowed_paths=allowed_paths)
        # Header robust erzwingen
        clean = cm.strip()
        marker = "### contextmap-ready"
        if not clean.startswith(marker):
            if clean.lower().startswith("contextmap-ready"):
                clean = clean[len("contextmap-ready"):].lstrip()
            clean = f"{marker}\n\n{clean}"
        cm = clean

        if not ensure_under_hard_limit(cm):
            cm = call_openai_contextmap(issue_text=issue_text + "\n\n(Hinweis: Bitte kürzer fassen, max. 9000 Zeichen.)",
                                        allowed_paths=allowed_paths)
            clean = cm.strip()
            if not clean.startswith(marker):
                if clean.lower().startswith("contextmap-ready"):
                    clean = clean[len("contextmap-ready"):].lstrip()
                clean = f"{marker}\n\n{clean}"
            cm = clean

        if not ensure_under_hard_limit(cm):
            add_label(num, "contextmap-error")
            upsert_contextmap_comment(num, f"{marker}\n\nFehler: Die ContextMap überschreitet 10 000 Zeichen. Bitte Issue präzisieren.")
            print("::error::ContextMap exceeds limit"); return

        upsert_contextmap_comment(num, cm)
        # Label setzen
        remove_label(num, "contextmap-error")
        add_label(num, "contextmap-ready")
        print("::notice::ContextMap posted/updated and labeled contextmap-ready")

        # >>> Bot 2 aktiv dispatchen (mit Issue-Nummer) <<<
        try:
            default = "main"  # falls anderes default-branch, hier anpassen oder dynamisch abfragen
            gh_api("POST", f"/repos/{gh_repo()}/actions/workflows/codex-solve.yml/dispatches",
                   {"ref": default, "inputs": {"issue": str(num)}})
            print("::notice::Triggered codex-solve via workflow_dispatch")
        except Exception as e:
            upsert_contextmap_comment(num, f"{marker}\n\nHinweis: Konnte Bot 2 nicht automatisch starten.\n```\n{e}\n```")

    except Exception as e:
        add_label(num, "contextmap-error")
        upsert_contextmap_comment(num, f"### contextmap-ready\n\nFehler beim Erzeugen der ContextMap:\n\n```\n{e}\n```")
        print(f"::error::ContextMap failed: {e}")
        sys.exit(1)

if __name__ == "__main__":
    main()

#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Bot 3 - Triage (KI-gestützt)
Analysiert fehlgeschlagene Läufe (ContextMap/Solver/Build), sammelt Logs + Artefakte + Repo-Kontext
(.github/workflows + .github/codex + relevante Build-/Gradle-Dateien), erzeugt konkrete Fix-Vorschläge
und postet diese als Kommentar ins Issue. Optional kann der Solver erneut gestartet werden.

Erfordert:
- GITHUB_TOKEN (automatisch vorhanden)
- OPENAI_API_KEY (Secret)
- optional: OPENAI_BASE_URL (Var) + OPENAI_MODEL_DEFAULT (Var)

Hinweise:
- Keine Auto-Commits standardmäßig; nur Analyse & Vorschläge in Issues.
- Re-Run des Solvers ist opt-in (Label / Kommentar / workflow_dispatch).
"""
import os, sys, json, re, io, zipfile, subprocess, fnmatch, textwrap
from pathlib import Path
from typing import Optional, Tuple, Dict, List

import requests

# ==================== GitHub Helpers ====================

def repo() -> str:
    return os.environ.get("GITHUB_REPOSITORY", "")

def gh_api(method: str, path: str, data=None, raw: bool=False) -> dict | bytes:
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
        msg = (resp.text or "")[:2000]
        print(f"::error::GH API {method} {path} failed: {resp.status_code} {msg}")
    if raw:
        return resp.content
    try:
        return resp.json()
    except Exception:
        return {}

def event_payload() -> dict:
    path = os.environ.get("GITHUB_EVENT_PATH")
    if not path or not os.path.isfile(path):
        return {}
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)

def evname() -> str:
    return os.environ.get("GH_EVENT_NAME") or os.environ.get("GITHUB_EVENT_NAME") or ""

def default_branch() -> str:
    infos = gh_api("GET", f"/repos/{repo()}")
    return infos.get("default_branch", "main") if isinstance(infos, dict) else "main"

# Issue IO
def post_comment(num: int, body: str):
    gh_api("POST", f"/repos/{repo()}/issues/{num}/comments", {"body": body})

def list_issue_comments(num: int) -> list:
    res = gh_api("GET", f"/repos/{repo()}/issues/{num}/comments")
    return res if isinstance(res, list) else []

def upsert_analysis_comment(num: int, markdown: str):
    """Creates or updates a comment in the issue with the analysis from this bot."""
    marker = "### bot3-analysis"
    cid = None
    for c in list_issue_comments(num):
        if (c.get("body") or "").strip().startswith(marker):
            cid = c.get("id")
            break
    body = (marker + "\n" + markdown).strip()
    if cid:
        gh_api("PATCH", f"/repos/{repo()}/issues/comments/{cid}", {"body": body})
    else:
        post_comment(num, body)

def add_labels(num: int, labels: list[str]):
    gh_api("POST", f"/repos/{repo()}/issues/{num}/labels", {"labels": labels})

def remove_label(num: int, label: str):
    gh_api("DELETE", f"/repos/{repo()}/issues/{num}/labels/{label}")

# ==================== Workflow Run & Logs/Artifacts ====================

def _extract_issue_number_from_run(run: dict) -> Optional[int]:
    # Heuristiken: Commit-Message oder PR-Body enthält "(issue #123)" o.ä.
    msg = (run.get("head_commit") or {}).get("message") or ""
    m = re.search(r"\(issue\s*#(\d+)\)", msg, re.I)
    if m:
        return int(m.group(1))
    # PR-Body prüfen
    prs = run.get("pull_requests") or []
    if prs:
        pr_num = prs[0].get("number")
        if pr_num:
            pr = gh_api("GET", f"/repos/{repo()}/pulls/{pr_num}")
            if isinstance(pr, dict):
                body = pr.get("body") or ""
                m2 = re.search(r"\(issue\s*#(\d+)\)", body, re.I)
                if m2:
                    return int(m2.group(1))
    return None

def latest_failed_run_for_issue(issue_num: int) -> dict:
    runs = gh_api("GET", f"/repos/{repo()}/actions/runs?status=failure&per_page=50") or {}
    items = runs.get("workflow_runs", []) or runs.get("runs", []) or []
    for r in items:
        if _extract_issue_number_from_run(r) == issue_num:
            return r
    return {}

def download_logs(run: dict) -> str:
    if not run:
        return ""
    logs_url = run.get("logs_url")
    if not logs_url:
        return ""
    raw = requests.get(
        logs_url,
        headers={
            "Authorization": f"Bearer {os.environ.get('GITHUB_TOKEN') or os.environ.get('INPUT_TOKEN')}",
            "Accept": "application/vnd.github+json",
        },
    )
    if raw.status_code >= 400:
        print(f"::warning::Failed to download logs: {raw.status_code}")
        return ""
    try:
        z = zipfile.ZipFile(io.BytesIO(raw.content))
    except Exception as e:
        print(f"::warning::Log zip invalid: {e}")
        return ""

    out = []
    for name in z.namelist():
        try:
            content = z.read(name).decode("utf-8", errors="ignore")
        except Exception:
            content = z.read(name).decode("latin-1", errors="ignore")
        out.append(f"\n----- {name} -----\n{content}")
    text = "\n".join(out)
    # Kürzen (Prompt Budget)
    lines = text.splitlines()
    if len(lines) > 3000:
        text = "\n".join(lines[-3000:])
    return text

def list_artifacts(run_id: int) -> list[dict]:
    data = gh_api("GET", f"/repos/{repo()}/actions/runs/{run_id}/artifacts")
    return data.get("artifacts", []) if isinstance(data, dict) else []

def download_and_extract_artifact(artifact: dict) -> dict[str, str]:
    """
    Lädt ein Artefakt (ZIP) und gibt ein Mapping {pfad: text_inhalt} zurück.
    Nur Textdateien (heuristisch) werden gelesen; Binärdateien werden ignoriert.
    """
    url = artifact.get("archive_download_url")
    if not url:
        return {}
    blob = gh_api("GET", f"/repos/{repo()}/actions/artifacts/{artifact['id']}/zip", raw=True)
    try:
        z = zipfile.ZipFile(io.BytesIO(blob))
    except Exception:
        return {}
    extracted = {}
    for name in z.namelist():
        # Nur „sinnvolle“ Textkandidaten
        if not name or name.endswith("/") or len(name) > 300:
            continue
        if not any(name.lower().endswith(ext) for ext in (".txt", ".log", ".json", ".yml", ".yaml", ".xml", ".gradle", ".kts", ".py", ".patch", ".rej")):
            # Heuristik: kleine dateien < 200KB evtl. trotzdem lesen
            try:
                if z.getinfo(name).file_size > 200_000:
                    continue
            except Exception:
                pass
        try:
            content = z.read(name)
            try:
                txt = content.decode("utf-8", errors="ignore")
            except Exception:
                txt = content.decode("latin-1", errors="ignore")
            # begrenzen
            if len(txt) > 200_000:
                txt = txt[:200_000]
            extracted[name] = txt
        except Exception:
            continue
    return extracted

def collect_artifacts_text(run: dict) -> Tuple[str, Dict[str, int]]:
    if not run:
        return "", {}
    arts = list_artifacts(run.get("id"))
    acc_parts = []
    stats = {}
    for art in arts:
        files = download_and_extract_artifact(art)
        count = 0
        for path, txt in files.items():
            count += 1
            # Datei-Header zur Orientierung
            acc_parts.append(f"\n### [artifact] {art.get('name')}/{path}\n{txt}")
        stats[art.get("name")] = count
    # Begrenzen
    joined = "\n".join(acc_parts)
    if len(joined) > 400_000:
        joined = joined[-400_000:]
    return joined, stats

# ==================== Repo-Kontext sammeln ====================

REPO_GLOB_DEFAULTS = [
    ".github/workflows/**/*.yml",
    ".github/codex/**/*.py",
    "app/**/build.gradle",
    "app/**/build.gradle.kts",
    "build.gradle",
    "build.gradle.kts",
    "settings.gradle",
    "settings.gradle.kts",
    "gradle.properties",
]

def collect_repo_context(extra_globs: Optional[List[str]] = None, max_bytes: int = 400_000) -> str:
    globs = REPO_GLOB_DEFAULTS + (extra_globs or [])
    root = Path(".")
    parts = []
    total = 0

    def add_file(p: Path):
        nonlocal total
        try:
            data = p.read_bytes()
        except Exception:
            return
        if not data:
            return
        # Heuristisch als Text behandeln
        try:
            txt = data.decode("utf-8", errors="ignore")
        except Exception:
            txt = data.decode("latin-1", errors="ignore")
        header = f"\n### [repo] {p.as_posix()}\n"
        chunk = header + txt
        if total + len(chunk) > max_bytes:
            remaining = max_bytes - total
            if remaining <= len(header) + 1000:
                return
            # Noch etwas kontext behalten
            chunk = header + txt[: remaining - len(header)]
        parts.append(chunk)
        total += len(chunk)

    # expand globs
    for pattern in globs:
        for p in root.glob(pattern):
            if p.is_file():
                add_file(p)

    return "".join(parts)

# ==================== Heuristiken & KI ====================

def gradle_heuristics(log_txt: str) -> str:
    hints = []
    for ln in log_txt.splitlines()[-500:]:
        if any(k in ln for k in (" error:", "FAILURE:", "Exception", "FAILED", "Undefined", "Traceback")):
            hints.append(ln.strip())
    # Kürzen
    return ("\n".join(hints))[:4000] if hints else ""

def openai_analyze(content_blocks: dict) -> str:
    """
    Baut einen Prompt aus:
    - Logs (letzte ~3000 Zeilen)
    - Artefakte (alle Textdateien, gekürzt)
    - Repo-Kontext (Workflows, Bots, Gradle)
    und fordert: Ursachen + konkrete Fixvorschläge je Datei (Markdown, präzise).
    """
    from openai import OpenAI
    client = OpenAI(
        api_key=os.environ.get("OPENAI_API_KEY"),
        base_url=(os.environ.get("OPENAI_BASE_URL") or "https://api.openai.com/v1")
    )
    model = os.environ.get("OPENAI_MODEL_DEFAULT") or "gpt-5"

    sys_msg = (
        "Du bist ein CI-Triage-Assistent. Liefere knappe, präzise Ursachenanalysen "
        "und konkrete Fixschritte je betroffener Datei (Pfad + Änderung). "
        "Verweise auf Workflows/Bots/Gradle-Dateien, wenn relevant. "
        "Antworte in Markdown mit Überschriften und Aufzählungen."
    )
    # Compose a single user message (large, but within limits due to truncations above)
    user_msg = textwrap.dedent(f"""
    ## Kontext
    - Repo: {repo()}
    - Default-Branch: {default_branch()}
    - Event: {evname()}

    ## Logs (gekürzt)
    ```text
    {content_blocks.get('logs','(keine)')}
    ```

    ## Artefakte (Textauszüge; gekürzt)
    {content_blocks.get('artifacts','(keine)')}

    ## Repo-Kontext (wichtige Dateien; gekürzt)
    {content_blocks.get('repo','(keine)')}

    ## Aufgabe
    1. Identifiziere die wahrscheinlichste(n) Ursache(n) für den Fehler.
    2. Mache **konkrete** Fix-Vorschläge, pro Datei mit Pfad und diff-ähnlichen Snippets.
    3. Gib **Prioritäten** an (High/Medium/Low).
    4. Erwähne, wenn ein **Re-Run** (Solver/Build) sinnvoll ist.
    """)

    try:
        rsp = client.chat.completions.create(
            model=model,
            messages=[
                {"role": "system", "content": sys_msg},
                {"role": "user", "content": user_msg}
            ],
            temperature=0
        )
        return rsp.choices[0].message.content
    except Exception as e:
        return f"**Analyse nicht möglich:** {e}"

# ==================== Solver re-dispatch ====================

def dispatch_solver(issue_num: int):
    gh_api(
        "POST",
        f"/repos/{repo()}/actions/workflows/codex-solve.yml/dispatches",
        {"ref": default_branch(), "inputs": {"issue": str(issue_num)}}
    )

# ==================== Main ====================

def main():
    ev = evname()
    payload = event_payload()

    # Determine issue number
    issue_num = None
    if ev in ("issues", "issue_comment"):
        issue_num = (payload.get("issue") or {}).get("number")
    if not issue_num:
        # workflow_dispatch path
        env_issue = os.environ.get("ISSUE_NUMBER") or os.environ.get("INPUT_ISSUE") or ""
        if env_issue.strip().isdigit():
            issue_num = int(env_issue.strip())

    if not issue_num:
        print("::error::No issue number resolved.")
        sys.exit(1)

    # Only act on relevant labels (unless manual dispatch)
    if ev == "issues":
        labels = [l.get("name") for l in (payload.get("issue") or {}).get("labels", [])]
        if not any(x in ("contextmap-error", "solver-error") for x in labels):
            print("::notice::Label not relevant for Triage.")
            sys.exit(0)

    # 1) find most recent failed run (by heuristic link to issue)
    failed_run = latest_failed_run_for_issue(issue_num)
    logs_text = download_logs(failed_run) if failed_run else ""
    artifacts_text, _ = collect_artifacts_text(failed_run) if failed_run else ("", {})
    repo_ctx = collect_repo_context()

    # 2) heuristics (esp. Gradle) for hints
    hints = gradle_heuristics(logs_text)

    # 3) build analysis blocks
    blocks = {
        "logs": logs_text or "(keine Logs gefunden)",
        "artifacts": artifacts_text or "(keine Artefakte gefunden)",
        "repo": repo_ctx or "(kein Repo-Kontext geladen)",
        "hints": hints or "-",
    }

    # 4) ask OpenAI for diagnosis & actionable fix plan
    analysis = openai_analyze(blocks)

    # 5) upsert comment to issue
    upsert_analysis_comment(issue_num, analysis)

    # 6) Optionale Automatik: Falls Label "triage-rerun" vorhanden, Solver erneut starten
    if ev in ("issues", "issue_comment"):
        labels = [l.get("name") for l in (payload.get("issue") or {}).get("labels", [])]
        if "triage-rerun" in labels:
            dispatch_solver(issue_num)
            post_comment(issue_num, "🔁 Triage: Analyse gepostet, Solver per Label `triage-rerun` erneut gestartet.")

    print("Triage analysis completed.")

if __name__ == "__main__":
    main()
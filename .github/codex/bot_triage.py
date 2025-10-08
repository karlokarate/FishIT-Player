#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Bot 3 — Triage (projektneutral, Shared‑Lib)
=================================================
Aufgaben:
- Liest die neuesten Context-/Solver‑Artefakte (solver_task.json, solver_input.json, solver_state.json)
- Findet den letzten fehlgeschlagenen Run von Bot 2 (Solver) heuristisch, lädt Logs + Artefakte
- Erstellt eine präzise Analyse (OpenAI) mit konkreten Fix‑Vorschlägen
- (Optional) AUTO‑FIX: Erlaubte CI/Bot‑Dateien patchen (Workflows + .github/codex/*.py), Branch **TRIAGEBOT/<issue>/<ts>**
- Erstellt PR und kommentiert im Issue; kann den Solver erneut anstoßen

Voraussetzungen: Shared‑Lib unter `.github/codex/lib/` (gh, io_utils, repo_utils, logging_utils, patching, ai_utils).
"""

from __future__ import annotations
import os, sys, re, json, io, zipfile, textwrap, time, subprocess, fnmatch, shutil
from pathlib import Path
from typing import Any, Dict, List, Tuple, Optional

# ---------- Shared‑Lib laden ----------
LIB_CANDIDATES = [
    os.path.join(os.getcwd(), ".github", "codex", "lib"),
    os.path.join(os.path.dirname(__file__), "lib"),
]
for _lib in LIB_CANDIDATES:
    if os.path.isdir(_lib) and _lib not in sys.path:
        sys.path.insert(0, _lib)

try:
    import gh, io_utils, repo_utils, logging_utils, patching, ai_utils
except Exception as e:
    print("::error::Shared‑Library nicht gefunden. Stelle sicher, dass `.github/codex/lib/` vorhanden ist.", flush=True)
    raise

# ---------- Konfiguration ----------
TRIAGE_WRITE = os.getenv("TRIAGE_WRITE", "0").strip().lower() in {"1","true","yes"}
TRIAGE_OPEN_PR = os.getenv("TRIAGE_OPEN_PR", "true").strip().lower() in {"1","true","yes"}
TRIAGE_RERUN_SOLVER = os.getenv("TRIAGE_RERUN_SOLVER", "true").strip().lower() in {"1","true","yes"}

TRIAGE_ALLOWED_GLOBS = (os.getenv("TRIAGE_ALLOWED_GLOBS") or
    ".github/workflows/**.yml,.github/workflows/**.yaml,.github/codex/**/*.py,.github/codex/requirements*.txt"
).split(",")

# ---------- Hilfsfunktionen (Git/GH) ----------

def sh(cmd: str, check=True) -> str:
    p = subprocess.run(cmd, shell=True, text=True, capture_output=True)
    if check and p.returncode != 0:
        raise RuntimeError(f"cmd failed: {cmd}\n--- stdout ---\n{p.stdout}\n--- stderr ---\n{p.stderr}")
    return p.stdout

def ensure_repo_cwd_and_git():
    ws = os.environ.get("GITHUB_WORKSPACE")
    if ws and Path(ws).exists():
        os.chdir(ws)
    for k, v in [
        ("user.name", "triagebot"),
        ("user.email", "actions@users.noreply.github.com"),
        ("core.autocrlf", "false"),
        ("apply.whitespace", "nowarn"),
        ("core.safecrlf", "false"),
        ("merge.renamelimit", "999999"),
        ("diff.renames", "true"),
    ]:
        try:
            sh(f"git config {k} {v}", check=False)
        except Exception:
            pass
    tok = os.environ.get("GITHUB_TOKEN", "") or os.environ.get("INPUT_TOKEN", "")
    if tok:
        sh('git config url."https://x-access-token:%s@github.com/".insteadOf "https://github.com/"' % tok, check=False)

def create_triage_branch(issue_no: int) -> str:
    base = gh.default_branch()
    ts = int(time.time())
    name = f"TRIAGEBOT/{issue_no}/{ts}"
    sh(f"git fetch origin {base}", check=False)
    sh(f"git checkout -B {name} origin/{base}", check=True)
    return name

def commit_and_push(paths: List[str], issue_no: int, msg: str):
    if not paths:
        return False
    for p in paths:
        sh(f"git add -- {p}", check=False)
    st = Path(".github/codex/solver_state.json")
    if st.exists():
        sh(f"git add -- {st.as_posix()}", check=False)

    staged_empty = subprocess.run("git diff --cached --quiet", shell=True)
    if staged_empty.returncode == 0:
        return False
    sh(f"git commit -m '{msg} (issue #{issue_no})'", check=True)
    sh(f"git push --set-upstream origin $(git rev-parse --abbrev-ref HEAD)", check=True)
    return True

# ---------- Context‑Artefakte ----------

def _resolve_run_dir() -> Path:
    p = Path(".github/codex/context/last_run.json")
    if p.exists():
        try:
            lr = json.loads(p.read_text(encoding="utf-8"))
            rd = lr.get("run_dir")
            if rd and Path(rd).exists():
                return Path(rd)
        except Exception:
            pass
    ctx = Path(".github/codex/context")
    cands = sorted([d for d in ctx.glob("run-*") if d.is_dir()], key=lambda x: x.stat().st_mtime, reverse=True)
    return cands[0] if cands else ctx

def _read_json_safe(p: Path) -> dict:
    try:
        return json.loads(p.read_text(encoding="utf-8"))
    except Exception:
        return {}

def load_context() -> Tuple[dict, dict, dict, Path]:
    run_dir = _resolve_run_dir()
    solver_task = _read_json_safe(run_dir / "solver_task.json")
    solver_input = _read_json_safe(run_dir / "solver_input.json")
    solver_state = {}
    st = Path(".github/codex/solver_state.json")
    if st.exists():
        solver_state = _read_json_safe(st)
    return solver_task, solver_input, solver_state, run_dir

# ---------- Workflow‑Runs / Logs / Artefakte ----------

def _find_latest_failed_solver_run(issue_no: int) -> dict:
    wf_list = gh.list_workflows()
    wf_solve = None
    for wf in wf_list:
        if (wf.get("name") or "").lower() == "codex-solve" or (wf.get("path") or "").endswith("codex-solve.yml"):
            wf_solve = wf
            break
    if not wf_solve:
        return {}
    wf_id = wf_solve.get("id")
    runs = gh.gh_api("GET", f"/repos/{gh.repo()}/actions/workflows/{wf_id}/runs")
    arr = (runs.get("workflow_runs") if isinstance(runs, dict) else []) or []
    arr.sort(key=lambda r: r.get("created_at",""), reverse=True)
    for r in arr:
        if r.get("conclusion") == "failure":
            head_branch = (r.get("head_branch") or "")
            if head_branch.startswith(f"SOLVERBOT/{issue_no}/"):
                return r
            prs = r.get("pull_requests") or []
            if prs and f"(issue #{issue_no})" in ((prs[0].get("title") or "") + (prs[0].get("body") or "")):
                return r
    return arr[0] if arr else {}

def _download_logs_zip(run: dict) -> str:
    url = run.get("logs_url") if isinstance(run, dict) else ""
    if not url:
        return ""
    try:
        blob = gh.gh_api("GET", url.replace("https://api.github.com",""), raw=True)
        z = zipfile.ZipFile(io.BytesIO(blob))
    except Exception:
        return ""
    out = []
    for name in z.namelist():
        try:
            txt = z.read(name).decode("utf-8", errors="ignore")
        except Exception:
            txt = z.read(name).decode("latin-1", errors="ignore")
        out.append(f"\n----- {name} -----\n{txt}")
    text = "\n".join(out)
    lines = text.splitlines()
    if len(lines) > 3000:
        text = "\n".join(lines[-3000:])
    return text

def _list_artifacts(run: dict) -> List[dict]:
    rid = run.get("id")
    if not rid:
        return []
    arts = gh.gh_api("GET", f"/repos/{gh.repo()}/actions/runs/{rid}/artifacts") or {}
    return arts.get("artifacts", []) if isinstance(arts, dict) else []

def _download_artifacts_text(run: dict) -> str:
    arts = _list_artifacts(run)
    parts = []
    for a in arts[:10]:
        try:
            blob = gh.gh_api("GET", f"/repos/{gh.repo()}/actions/artifacts/{a['id']}/zip", raw=True)
            z = zipfile.ZipFile(io.BytesIO(blob))
        except Exception:
            continue
        for name in z.namelist():
            if not name or name.endswith("/"):
                continue
            if len(name) > 300:
                continue
            try:
                content = z.read(name)
                try:
                    txt = content.decode("utf-8", errors="ignore")
                except Exception:
                    txt = content.decode("latin-1", errors="ignore")
                if len(txt) > 200_000:
                    txt = txt[:200_000]
                if any(name.lower().endswith(ext) for ext in (".txt",".log",".json",".yml",".yaml",".gradle",".kts",".py",".patch",".rej")):
                    parts.append(f"\n### [artifact] {a.get('name')}/{name}\n{txt}")
            except Exception:
                pass
    joined = "\n".join(parts)
    if len(joined) > 400_000:
        joined = joined[-400_000:]
    return joined

# ---------- Analyse & Patches ----------

def _collect_repo_docs_for_ci() -> str:
    globs = [
        ".github/workflows/**/*.yml",
        ".github/workflows/**/*.yaml",
        ".github/codex/**/*.py",
        ".github/codex/requirements*.txt",
    ]
    root = Path(".")
    parts = []
    total = 0
    max_bytes = 350_000
    def add_file(p: Path):
        nonlocal total
        try:
            data = p.read_bytes()
        except Exception:
            return
        try:
            txt = data.decode("utf-8", errors="ignore")
        except Exception:
            txt = data.decode("latin-1", errors="ignore")
        chunk = f"\n### [repo] {p.as_posix()}\n{txt}"
        if total + len(chunk) > max_bytes:
            return
        parts.append(chunk); total += len(chunk)
    for g in globs:
        for p in root.glob(g):
            if p.is_file():
                add_file(p)
    return "".join(parts) or "(no CI/bot files found)"

def _validate_triage_patch_allowed(patch_text: str) -> Tuple[bool, List[str]]:
    if not (patch_text or "").strip():
        return True, []
    bad = []
    for m in re.finditer(r"^diff --git a/(\S+)\s+b/(\S+)\s*$", patch_text, re.M):
        tgt = m.group(2)
        if not any(fnmatch.fnmatch(tgt, g) for g in TRIAGE_ALLOWED_GLOBS):
            bad.append(tgt)
    return (len(bad)==0), bad

def _apply_patch_or_explain(issue_no: int, patch_text: str) -> List[str]:
    if not (patch_text or "").strip():
        return []
    clean = patching.sanitize_patch(patch_text)
    ok, bad = _validate_triage_patch_allowed(clean)
    if not ok:
        gh.post_comment(issue_no, "❌ Triage‑Patch enthält Ziele außerhalb des erlaubten Bereichs:\n" + "\n".join(f"- `{b}`" for b in bad[:50]))
        return []
    applied, info = patching.whole_git_apply(clean)
    if applied:
        gh.post_comment(issue_no, f"✅ Triage‑Patch applied (whole): `{info}`")
    else:
        gh.post_comment(issue_no, f"ℹ️ Whole‑apply scheiterte, versuche sections.")
        changed_before = set(_changed_paths_porcelain())
        sections = patching.split_sections(clean)
        any_ok = False
        for i, sec in enumerate(sections, 1):
            if patching.apply_section(sec, i):
                any_ok = True
        if not any_ok:
            gh.post_comment(issue_no, "ℹ️ Section‑wise Apply fehlgeschlagen – kein Patch angewendet.")
            return []
    return _changed_paths_porcelain()

def _changed_paths_porcelain() -> List[str]:
    out = sh("git status --porcelain", check=False)
    paths = []
    for ln in out.splitlines():
        ln = ln.strip()
        if not ln: continue
        parts = ln.split(maxsplit=1)
        if len(parts)==2:
            p = parts[1].strip().strip('"')
            paths.append(p)
    return paths

# ---------- Hauptlogik ----------

def _extract_issue_number(task: Dict[str, Any], ctx: Dict[str, Any]) -> Optional[int]:
    n = (task.get("issue") or {}).get("number") if isinstance(task, dict) else None
    if n: return int(n)
    n = (ctx.get("issue_context") or {}).get("number") if isinstance(ctx, dict) else None
    if n: return int(n)
    env_issue = os.getenv("ISSUE_NUMBER") or os.getenv("INPUT_ISSUE") or ""
    if env_issue.isdigit(): return int(env_issue)
    return None

def _open_pr(issue_no: int, title: str, body: str) -> Dict[str, Any]:
    head = sh("git rev-parse --abbrev-ref HEAD", check=False).strip()
    return gh.gh_api("POST", f"/repos/{gh.repo()}/pulls", {
        "title": title, "head": head, "base": gh.default_branch(), "body": body
    })

def _safe_upsert_comment(issue_no: int, marker: str, md: str):
    """Try shared-lib's upsert; fallback to simple post if not present."""
    try:
        func = getattr(gh, "upsert_comment_by_marker", None)
        if func:
            func(issue_no, marker, md)
            return
    except Exception as e:
        pass
    gh.post_comment(issue_no, f"{marker}\n{md}")

def main():
    ensure_repo_cwd_and_git()
    logging_utils.heartbeat("triage", every_s=60)

    task, ctx, state, run_dir = load_context()
    issue_no = _extract_issue_number(task, ctx)
    if not issue_no:
        print("::error::Triage: Issue‑Nummer nicht ermittelbar."); sys.exit(1)

    failed_run = _find_latest_failed_solver_run(issue_no)
    logs_text = _download_logs_zip(failed_run) if failed_run else ""
    arts_text = _download_artifacts_text(failed_run) if failed_run else ""

    repo_docs = _collect_repo_docs_for_ci()

    content_blocks = {
        "logs": logs_text or "(keine Logs)",
        "artifacts": arts_text or "(keine Artefakte)",
        "repo": repo_docs or "(keine CI/Bot‑Dateien)",
        "task": json.dumps(task, ensure_ascii=False)[:12000] if task else "(kein solver_task.json)",
    }
    try:
        from openai import OpenAI  # lazy
        client = OpenAI(api_key=os.getenv("OPENAI_API_KEY"), base_url=(os.getenv("OPENAI_BASE_URL") or "https://api.openai.com/v1"))
        model = os.getenv("OPENAI_MODEL_DEFAULT") or "gpt-5"
        SYS = "Du bist ein CI‑Triage‑Assistent. Diagnose + konkrete Fixvorschläge für Workflows und Bot‑Skripte. Antworte kurz und präzise in Markdown."
        USER = textwrap.dedent(f"""
        ## Logs
        ```text
        {content_blocks['logs']}
        ```

        ## Artifacts
        {content_blocks['artifacts']}

        ## Repo (CI/Bot files)
        {content_blocks['repo'][:280000]}

        ## solver_task.json (Kurz)
        ```json
        {content_blocks['task']}
        ```

        ### Aufgabe
        1) Diagnose: Was ist wahrscheinlich kaputt? (konkret)  
        2) Fixplan: Welche Dateien (Pfade) müssen wie geändert werden? (Diff‑Snippets)  
        3) Danach: Solver erneut starten?
        """)
        rsp = client.chat.completions.create(
            model=model,
            messages=[{"role":"system","content":SYS},{"role":"user","content":USER}],
            temperature=0
        )
        analysis_md = rsp.choices[0].message.content
    except Exception as e:
        analysis_md = f"**Analyse nicht möglich:** {e}"

    # FIX: use safe upsert (previous version had a typo and crashed)
    _safe_upsert_comment(issue_no, "### bot3-analysis", analysis_md)

    changed_paths: List[str] = []
    pr_url = ""
    if TRIAGE_WRITE:
        br = create_triage_branch(issue_no)
        gh.add_step_summary(f"- TRIAGE Branch: `{br}`")

        summary_ctx = "Triage fix for CI/Bot failures; allowed: " + ", ".join(TRIAGE_ALLOWED_GLOBS)
        targets_preview = "\n".join(TRIAGE_ALLOWED_GLOBS)
        try:
            patch = ai_utils.generate_diff(summary_ctx, repo_docs, targets_preview)
        except Exception as e:
            patch = ""
            gh.post_comment(issue_no, f"⚠️ Konnte keinen Auto‑Patch generieren: `{e}`")

        changed_paths = _apply_patch_or_explain(issue_no, patch)
        if changed_paths:
            ok = commit_and_push(changed_paths, issue_no, "TRIAGEBOT: CI/Bot Fixes")
            if ok and TRIAGE_OPEN_PR:
                title = f"TRIAGEBOT: CI/Bot Fixes für Issue #{issue_no}"
                body = "Automatische CI/Bot‑Behebungen basierend auf Triage‑Analyse.\n\n" + analysis_md[:5000]
                pr = _open_pr(issue_no, title, body)
                pr_url = pr.get("html_url","")
                if pr_url:
                    gh.post_comment(issue_no, f"🧰 Triage‑PR erstellt: {pr_url}")

    if TRIAGE_RERUN_SOLVER:
        try:
            gh.dispatch_workflow("codex-solve.yml", gh.default_branch(), inputs={"issue": str(issue_no)})
            gh.post_comment(issue_no, "🔁 Triage: Solver erneut gestartet.")
        except Exception as e:
            gh.post_comment(issue_no, f"⚠️ Konnte Solver nicht neu starten: `{e}`")

    print("Triage completed. Changed paths:", changed_paths)
    if pr_url:
        print("PR:", pr_url)

if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        print(f"::error::Triage fatal: {e}")
        raise

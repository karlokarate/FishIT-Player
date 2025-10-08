#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Bot 2 — Solver (projektneutral, Shared‑Lib)
=================================================
Ziele (gemäß Vorgabe):
- Verarbeitet Arbeitsauftrag aus `.github/codex/context/run-*/solver_task.json` (NEU, Vertrag von Bot 1)
- Erzwingt Allowed‑Targets & Execution aus `solver_task.json` (oder Fallback: legacy `solver_plan.json`)
- Arbeitet im Branch **SOLVERBOT/<issue>/<ts>**
- **Pro geänderter Datei** sofort **commit & push**, bevor die nächste Datei angefasst wird
- Kommentiert **je Änderung** im Issue mit kurzem "WARUM" (ai_utils.explain_change)
- Akzeptiert zusätzlich nur Dateien, die **direkt im Zusammenhang** stehen (Scope‑Guardrails)
- Erzeugt PR am Ende; optional PR pro Datei via ENV

Optionale ENV‑Flags:
- SOLVER_SEPARATE_PRS: 'true' → je Datei einen separaten PR (default: false, ein PR mit mehreren Commits)
- SOLVER_RUN_TESTS: 'true' → nach Änderungen Tests gemäß Profile ausführen (default: false)
- SOLVER_CHECKPOINT_BATCH: Zahl > 0; beeinflusst Step‑Summary (Commit‑Batching ist IMMER 1 pro Datei)
- OPENAI_MODEL_DEFAULT / OPENAI_BASE_URL / OPENAI_API_KEY: für ai_utils

Voraussetzungen: Shared‑Lib unter `.github/codex/lib/` (gh, io_utils, patching, repo_utils, logging_utils, scopes, profiles, ai_utils, task_schema).
"""

from __future__ import annotations
import os, sys, json, re, time, textwrap, subprocess, shutil
from pathlib import Path
from typing import Any, Dict, List, Tuple, Optional, Set

# ---------- Shared‑Lib laden ----------
LIB_CANDIDATES = [
    os.path.join(os.getcwd(), ".github", "codex", "lib"),
    os.path.join(os.path.dirname(__file__), "lib"),
]
for _lib in LIB_CANDIDATES:
    if os.path.isdir(_lib) and _lib not in sys.path:
        sys.path.insert(0, _lib)

try:
    import gh, io_utils, repo_utils, patching, logging_utils, scopes, profiles, ai_utils, task_schema
except Exception as e:
    print("::error::Shared‑Library nicht gefunden. Stelle sicher, dass `.github/codex/lib/` vorhanden ist.", flush=True)
    raise

# ---------- Konfiguration ----------
BATCH = int(os.getenv("SOLVER_CHECKPOINT_BATCH", "1"))  # Commit/PUSH ist sowieso pro Datei
SEPARATE_PRS = os.getenv("SOLVER_SEPARATE_PRS", "false").strip().lower() in {"1","true","yes"}
RUN_TESTS = os.getenv("SOLVER_RUN_TESTS", "false").strip().lower() in {"1","true","yes"}

# ---------- Git / Shell ----------

def sh(cmd: str, check=True) -> str:
    p = subprocess.run(cmd, shell=True, text=True, capture_output=True)
    if check and p.returncode != 0:
        raise RuntimeError(f"cmd failed: {cmd}\n--- stdout ---\n{p.stdout}\n--- stderr ---\n{p.stderr}")
    return p.stdout

def ensure_repo_cwd_and_git():
    ws = os.environ.get("GITHUB_WORKSPACE")
    if ws and Path(ws).exists():
        os.chdir(ws)
    # Git Identity + Safe Defaults
    for k, v in [
        ("user.name", "solverbot"),
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
    # Auth rewrite for pushes
    tok = os.environ.get("GITHUB_TOKEN", "") or os.environ.get("INPUT_TOKEN", "")
    if tok:
        sh('git config url."https://x-access-token:%s@github.com/".insteadOf "https://github.com/"' % tok, check=False)

def current_branch() -> str:
    return (sh("git rev-parse --abbrev-ref HEAD", check=False).strip() or "HEAD")

def create_solver_branch(issue_no: int) -> str:
    base = gh.default_branch()
    ts = int(time.time())
    name = f"SOLVERBOT/{issue_no}/{ts}"
    sh(f"git fetch origin {base}", check=False)
    sh(f"git checkout -B {name} origin/{base}", check=True)
    return name

def commit_and_push(path: str, issue_no: int, why_markdown: Optional[str] = None):
    # Stage only the given path + solver_state.json (if present)
    if Path(path).is_dir():
        sh(f"git add -A -- {path}", check=True)
    else:
        sh(f"git add -- {path}", check=True)
    state = Path(".github/codex/solver_state.json")
    if state.exists():
        sh(f"git add -- {state.as_posix()}", check=False)

    # Avoid empty commit
    staged_empty = subprocess.run("git diff --cached --quiet", shell=True)
    if staged_empty.returncode == 0:
        return False

    # Commit
    sh(f"git commit -m 'SOLVERBOT: {path} (issue #{issue_no})'", check=True)

    # Push
    br = current_branch()
    sh(f"git push --set-upstream origin {br}", check=True)

    # Optional WHY comment already posted by caller per‑file
    return True

# ---------- Context/Task Laden ----------

def _load_last_run() -> Dict[str, Any]:
    p = Path(".github/codex/context/last_run.json")
    if p.exists():
        try:
            return json.loads(p.read_text(encoding="utf-8"))
        except Exception:
            return {}
    return {}

def _resolve_run_dir() -> Path:
    lr = _load_last_run()
    if lr.get("run_dir"):
        rd = Path(lr["run_dir"])
        if rd.exists():
            return rd
    # Fallback: wähle jüngsten run-Ordner
    ctx = Path(".github/codex/context")
    cands = sorted([p for p in ctx.glob("run-*") if p.is_dir()], key=lambda p: p.stat().st_mtime, reverse=True)
    return cands[0] if cands else ctx

def _copy_task_for_scopes(run_dir: Path):
    """Kopiert solver_task.json in den erwarteten Pfad von scopes.py, damit Guardrails greifen."""
    src = run_dir / "solver_task.json"
    dst = Path(".github/codex/context/solver_task.json")
    if src.exists():
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(src, dst)
    # Legacy plan (falls vorhanden)
    src_plan = run_dir / "solver_plan.json"
    if src_plan.exists():
        shutil.copyfile(src_plan, Path(".github/codex/solver_plan.json"))

def load_task_and_context() -> Tuple[Dict[str, Any], Dict[str, Any], Path]:
    run_dir = _resolve_run_dir()
    task_path = run_dir / "solver_task.json"
    ctx_path  = run_dir / "solver_input.json"

    task = {}
    ctx = {}
    if task_path.exists():
        try:
            task = json.loads(task_path.read_text(encoding="utf-8"))
        except Exception:
            task = {}
    if ctx_path.exists():
        try:
            ctx = json.loads(ctx_path.read_text(encoding="utf-8"))
        except Exception:
            ctx = {}

    # Für scopes.py Guardrails die Kopie ablegen
    _copy_task_for_scopes(run_dir)
    return task, ctx, run_dir

# ---------- Allowed‑Targets / Scope ----------

def filter_targets(create_paths: List[str], modify_paths: List[str]) -> Tuple[List[str], List[str], List[str]]:
    """Nutzt shared scopes.py (liest solver_task.json/solver_plan.json)."""
    c_ok, m_ok, rej = scopes.filter_paths_by_allowed(create_paths, modify_paths)
    return c_ok, m_ok, rej

def ensure_allowed_or_explain(issue_no: int, paths: List[str], kind: str):
    """Kommentiert im Issue, wenn Pfade außerhalb des Scopes liegen (strict_mode aktiv)."""
    ok_c, ok_m, rej = filter_targets(paths if kind=="create" else [], paths if kind=="modify" else [])
    if rej:
        msg = "Einige Ziele liegen **außerhalb des erlaubten Scopes** (strict mode aktiv):\n" + "\n".join(f"- `{r}`" for r in rej[:50])
        gh.post_comment(issue_no, "⚠️ " + msg + "\n\nBitte passe `scope.modify/create` in `solver_task.json` an, falls diese Dateien nötig sind.")
    return ok_c if kind=="create" else ok_m

# ---------- Diff & Apply ----------

def _changed_paths_porcelain() -> List[str]:
    out = sh("git status --porcelain", check=False)
    paths = []
    for ln in out.splitlines():
        ln = ln.strip()
        if not ln: continue
        parts = ln.split(maxsplit=1)
        if len(parts) == 2:
            p = parts[1]
            if p.startswith('"') and p.endswith('"'):
                p = p.strip('"')
            paths.append(p)
    return paths

def _file_diff_against_head(path: str) -> str:
    return sh(f"git diff HEAD -- {path}", check=False)

def _apply_unified_patch_if_any(issue_no: int, raw_patch: str) -> List[str]:
    """Versucht Whole‑Apply → section‑wise → zero‑context/gnu. Gibt Liste der geänderten Pfade zurück (oder [])."""
    raw_patch = (raw_patch or "").strip()
    if not raw_patch:
        return []
    clean = patching.sanitize_patch(raw_patch)
    ok, bad_targets, exec_cfg = scopes.validate_patch_against_allowed(clean)
    if not ok:
        gh.post_comment(issue_no, "❌ Patch enthält verbotene Ziele (strict_mode aktiv). Ignoriere Patch und wechsle zu Spec‑to‑File.\n\n" +
                        "\n".join(f"- `{b}`" for b in bad_targets[:30]))
        return []

    # Apply whole
    applied, info = patching.whole_git_apply(clean)
    if applied:
        gh.post_comment(issue_no, f"✅ Patch applied (whole): `{info}`")
    else:
        gh.post_comment(issue_no, f"ℹ️ Whole‑apply scheiterte, versuche section‑wise. Fehler:\n```\n{info}\n```")
        changed_before = set(_changed_paths_porcelain())
        sections = patching.split_sections(clean)
        any_ok = False
        for i, sec in enumerate(sections, 1):
            if patching.apply_section(sec, i):
                any_ok = True
        if not any_ok:
            gh.post_comment(issue_no, "ℹ️ Section‑wise Apply fehlgeschlagen – wechsle zu Spec‑to‑File.")
            return []
    changed = _changed_paths_porcelain()
    return changed

# ---------- AI‑gestützte Erstellung/Umschreiben ----------

def _gather_symbols_if_needed(limit:int=1200) -> Dict[str, Any]:
    try:
        return repo_utils.gather_symbols(repo_utils.list_all_files(), limit=limit)
    except Exception:
        return {"count":0,"items":[]}

def _spec_create_file(path: str, ctx_short: str, ctx_full: str, symbols: Dict[str, Any]) -> str:
    content = ai_utils.generate_full_file(path, json.dumps(symbols, ensure_ascii=False), ctx_full, ctx_short)
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    Path(path).write_text(content, encoding="utf-8")
    return content

def _spec_rewrite_file(path: str, ctx_short: str, ctx_full: str, symbols: Dict[str, Any]) -> str:
    cur = ""
    p = Path(path)
    if p.exists():
        try:
            cur = p.read_text(encoding="utf-8", errors="replace")
        except Exception:
            cur = ""
    content = ai_utils.rewrite_full_file(path, cur, json.dumps(symbols, ensure_ascii=False), ctx_full, ctx_short)
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    Path(path).write_text(content, encoding="utf-8")
    return content

# ---------- Build/Test ----------

def _resolve_commands(task: Dict[str, Any]) -> Tuple[List[str], List[str], List[str]]:
    files = repo_utils.list_all_files()
    prof = (task.get("language_profile") or profiles.detect_language_profile(files))
    cmds = profiles.resolve_commands(prof)
    fmt = (task.get("build") or {}).get("fmt") or cmds.get("fmt") or []
    build = (task.get("build") or {}).get("cmd") or cmds.get("build") or []
    test = (task.get("test") or {}).get("cmd") or cmds.get("test") or []
    return fmt, build, test

def _run_cmd_list(cmd: List[str], title: str, issue_no: int):
    if not cmd: return
    try:
        out = sh(" ".join(cmd), check=False)
        gh.add_step_summary(f"**{title}**\n\n```\n{out[-1200:]}\n```")
    except Exception as e:
        gh.post_comment(issue_no, f"⚠️ {title} fehlgeschlagen:\n```\n{e}\n```")

# ---------- Hauptlogik ----------

def _extract_issue_number(task: Dict[str, Any], ctx: Dict[str, Any]) -> Optional[int]:
    n = (task.get("issue") or {}).get("number")
    if n: return int(n)
    n = (ctx.get("issue_context") or {}).get("number")
    if n: return int(n)
    env_issue = os.getenv("ISSUE_NUMBER") or os.getenv("INPUT_ISSUE") or ""
    if env_issue.isdigit(): return int(env_issue)
    return None

def _get_issue_raw_diff(num: int) -> str:
    try:
        bodies = []
        issue = gh.get_issue(num)
        bodies.append(issue.get("body") or "")
        for c in gh.list_issue_comments(num):
            bodies.append(c.get("body") or "")
        raw = "\n\n".join(bodies)
        pos = raw.find("diff --git ")
        return raw[pos:] if pos != -1 else ""
    except Exception:
        return ""

def _ai_explain_and_comment(issue_no: int, path: str, diff_text: str, task_hint: Dict[str, Any]):
    try:
        why = ai_utils.explain_change(path, diff_text or f"created/rewritten: {path}", task_hint or {})
    except Exception as e:
        why = f"(keine AI‑Begründung verfügbar: {e})"
    gh.post_comment(issue_no, f"🛠️ **Änderung** `{path}`\n\n{('```diff\\n'+diff_text+'\\n```') if diff_text else ''}\n\n**Warum:** {why}")

def _open_pr(issue_no: int, title: str, body: str) -> Dict[str, Any]:
    head = current_branch()
    pr = gh.gh_api("POST", f"/repos/{gh.repo()}/pulls", {
        "title": title,
        "head": head,
        "base": gh.default_branch(),
        "body": body
    })
    return pr if isinstance(pr, dict) else {}

def main():
    ensure_repo_cwd_and_git()
    logging_utils.heartbeat("solver", every_s=60)

    task, ctx, run_dir = load_task_and_context()
    issue_no = _extract_issue_number(task, ctx)
    if not issue_no:
        print("::error::Konnte Issue‑Nummer nicht ermitteln.")
        sys.exit(1)

    branch = create_solver_branch(issue_no)
    gh.add_step_summary(f"- Branch: `{branch}` (base: {gh.default_branch()})")

    raw_patch = _get_issue_raw_diff(issue_no)
    changed_from_patch = _apply_unified_patch_if_any(issue_no, raw_patch)

    total_commits = 0
    for path in changed_from_patch:
        diff_text = _file_diff_against_head(path)
        _ai_explain_and_comment(issue_no, path, diff_text, task)
        if commit_and_push(path, issue_no):
            total_commits += 1
            gh.add_step_summary(f"- Commit/PUSH: `{path}`")

    allowed_scope = (task.get("scope") or {})
    create_targets = list(allowed_scope.get("create") or [])
    modify_targets = list(allowed_scope.get("modify") or [])
    create_targets = ensure_allowed_or_explain(issue_no, create_targets, "create")
    modify_targets = ensure_allowed_or_explain(issue_no, modify_targets, "modify")

    short_ctx = (task.get("problem") or {}).get("summary") or (ctx.get("issue_context") or {}).get("title") or ""
    full_ctx = ""
    try:
        full_ctx = (run_dir / "summary.md").read_text(encoding="utf-8", errors="ignore")
    except Exception:
        try:
            full_ctx = (run_dir / "summary.txt").read_text(encoding="utf-8", errors="ignore")
        except Exception:
            full_ctx = json.dumps({"issue": ctx.get("issue_context"), "analysis": ctx.get("analysis")}, ensure_ascii=False)

    symbols = _gather_symbols_if_needed()

    for new_path in (create_targets or []):
        p = Path(new_path)
        if p.exists():
            continue
        try:
            _spec_create_file(new_path, short_ctx, full_ctx, symbols)
            diff_text = _file_diff_against_head(new_path)
            _ai_explain_and_comment(issue_no, new_path, diff_text, task)
            if commit_and_push(new_path, issue_no):
                total_commits += 1
        except Exception as e:
            gh.post_comment(issue_no, f"⚠️ Konnte Datei nicht erzeugen `{new_path}`:\n```\n{e}\n```")

    dir_rewrite_allowed = bool((task.get("scope") or {}).get("dir_rewrite_allowed", False))
    for tgt in (modify_targets or []):
        p = Path(tgt)
        if p.is_dir():
            if not dir_rewrite_allowed:
                gh.post_comment(issue_no, f"ℹ️ Verzeichnisse sind nicht erlaubt (dir_rewrite_allowed=false): `{tgt}`")
                continue
            for f in p.rglob("*"):
                if not f.is_file(): continue
                if not any(f.suffix.lower() == ext for ext in (".kt",".java",".py",".ts",".tsx",".js",".cs",".go",".rs")):
                    continue
                try:
                    _spec_rewrite_file(f.as_posix(), short_ctx, full_ctx, symbols)
                    diff_text = _file_diff_against_head(f.as_posix())
                    _ai_explain_and_comment(issue_no, f.as_posix(), diff_text, task)
                    if commit_and_push(f.as_posix(), issue_no):
                        total_commits += 1
                except Exception as e:
                    gh.post_comment(issue_no, f"⚠️ Umschreiben fehlgeschlagen `{f.as_posix()}`:\n```\n{e}\n```")
        else:
            if not p.exists():
                gh.post_comment(issue_no, f"ℹ️ Datei in `scope.modify` existiert nicht (skipping): `{tgt}`")
                continue
            try:
                _spec_rewrite_file(tgt, short_ctx, full_ctx, symbols)
                diff_text = _file_diff_against_head(tgt)
                _ai_explain_and_comment(issue_no, tgt, diff_text, task)
                if commit_and_push(tgt, issue_no):
                    total_commits += 1
            except Exception as e:
                gh.post_comment(issue_no, f"⚠️ Umschreiben fehlgeschlagen `{tgt}`:\n```\n{e}\n```")

    if RUN_TESTS:
        fmt, build, test = _resolve_commands(task)
        _run_cmd_list(fmt, "Formatierung", issue_no)
        _run_cmd_list(build, "Build", issue_no)
        _run_cmd_list(test, "Tests", issue_no)

    status = sh("git status --porcelain", check=False).strip()
    if total_commits == 0 and not status:
        gh.add_labels(issue_no, ["solver-error"])
        gh.post_comment(issue_no, "❌ Solver: Keine Änderungen vorgenommen. Triage wird ausgelöst.")
        try:
            gh.dispatch_workflow("codex-triage.yml", gh.default_branch(), inputs={"issue": str(issue_no)})
        except Exception:
            pass
        sys.exit(1)

    pr_title = f"SOLVERBOT: Änderungen für Issue #{issue_no}"
    pr_body = textwrap.dedent(f"""
    Automatisch erzeugte Änderungen durch **Bot 2 (Solver)**.
    
    **Hinweise**
    - Branch: `{current_branch()}` (Basis: `{gh.default_branch()}`)
    - Allowed‑Targets: aus `solver_task.json` (strict_mode enforced)
    - Commits: pro Datei (ein Commit pro Änderung)
    - Auswahl einzelner Patches: Nutze *Rebase & Merge* um Commits selektiv zu übernehmen; alternativ `SOLVER_SEPARATE_PRS=true`, um je Datei einen separaten PR zu erstellen.
    
    **Artefakte**
    - Kontext: `{(run_dir/'solver_input.json').as_posix()}`
    - Auftrag: `{(run_dir/'solver_task.json').as_posix()}`
    """).strip()

    pr = _open_pr(issue_no, pr_title, pr_body)
    pr_url = pr.get("html_url", "")
    if pr_url:
        gh.post_comment(issue_no, f"🔧 PR erstellt: {pr_url}")

    if SEPARATE_PRS:
        gh.post_comment(issue_no, "ℹ️ `SOLVER_SEPARATE_PRS=true`: separierte PRs sind noch nicht aktiviert (v1 öffnet einen PR).")

if __name__ == "__main__":
    try:
        main()
    except SystemExit:
        raise
    except Exception as e:
        try:
            task, ctx, _ = load_task_and_context()
            issue_no = (task.get("issue") or {}).get("number") or (ctx.get("issue_context") or {}).get("number")
            if issue_no:
                gh.add_labels(int(issue_no), ["solver-error"])
                try:
                    gh.dispatch_workflow("codex-triage.yml", gh.default_branch(), inputs={"issue": str(issue_no)})
                except Exception:
                    pass
        except Exception:
            pass
        print(f"::error::Solver fatal: {e}")
        raise

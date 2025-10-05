#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
Bot 2 - Solver (Code-Umsetzung, robust gegen fragile Unified Diffs)

Verbesserungen ggü. Original:
- Zusätzliche Apply-Strategien:
  * git apply --unidiff-zero / --inaccurate-eof / --ignore-whitespace / --reject / -3
  * GNU patch Fallback (-p1/-p0 -f -N --follow-symlinks)
- Zero-Context-Transformation der Hunks als letzter Git-Fallback (kontextfreie Anwendung)
- Aggressiveres Sanitizing (Codefences, CRLF->LF, trailing newline, Brace-Rename-Normalisierung)
- Saubere Kommentierung & Labeling bleibt kompatibel zu Bot 3
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

# ---------------- GitHub helpers ----------------

def repo() -> str:
    return os.environ["GITHUB_REPOSITORY"]

def event() -> dict:
    return json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text(encoding="utf-8"))

def gh_api(method: str, path: str, payload: dict | None = None) -> dict:
    url = f"https://api.github.com{path}"
    headers = {
        "Authorization": f"Bearer {os.environ['GITHUB_TOKEN']}",
        "Accept": "application/vnd.github+json"
    }
    r = requests.request(method, url, headers=headers, json=payload, timeout=60)
    if r.status_code >= 300:
        raise RuntimeError(f"GitHub API {method} {path} failed: {r.status_code} {r.text[:800]}")
    try:
        return r.json()
    except Exception:
        return {}

def gh_delete_label(num: int, label: str):
    try:
        requests.delete(
            f"https://api.github.com/repos/{repo()}/issues/{num}/labels/{label}",
            headers={
                "Authorization": f"Bearer {os.environ['GITHUB_TOKEN']}",
                "Accept": "application/vnd.github+json"
            },
            timeout=30
        )
    except Exception:
        pass

def issue_number() -> Optional[int]:
    ev = event()
    if "issue" in ev:
        n = (ev.get("issue") or {}).get("number")
        if n:
            return int(n)
    inputs = ev.get("inputs") or {}
    if inputs.get("issue"):
        try:
            return int(inputs["issue"])
        except Exception:
            pass
    env_issue = os.environ.get("ISSUE_NUMBER")
    if env_issue:
        try:
            return int(env_issue)
        except Exception:
            pass
    return None

def list_issue_comments(num: int) -> List[dict]:
    res = gh_api("GET", f"/repos/{repo()}/issues/{num}/comments")
    return res if isinstance(res, list) else []

def get_labels(num: int) -> Set[str]:
    issue = gh_api("GET", f"/repos/{repo()}/issues/{num}")
    return {l.get("name", "") for l in issue.get("labels", [])}

def add_label(num: int, label: str):
    gh_api("POST", f"/repos/{repo()}/issues/{num}/labels", {"labels": [label]})

def remove_label(num: int, label: str):
    gh_delete_label(num, label)

def post_comment(num: int, body: str):
    gh_api("POST", f"/repos/{repo()}/issues/{num}/comments", {"body": body})

# ---------------- Shell / Git helpers ----------------

def sh(cmd: str, check=True) -> str:
    p = subprocess.run(cmd, shell=True, text=True, capture_output=True)
    if check and p.returncode != 0:
        msg = f"cmd failed: {cmd}\nSTDOUT:\n{p.stdout}\nSTDERR:\n{p.stderr}"
        raise RuntimeError(msg)
    return p.stdout or ""

def ensure_repo_cwd():
    ws = os.environ.get("GITHUB_WORKSPACE")
    if ws and Path(ws).exists():
        os.chdir(ws)
    else:
        try:
            root = sh("git rev-parse --show-toplevel").strip()
            if root:
                os.chdir(root)
        except Exception:
            pass
    try:
        sh("git config user.name 'codex-bot'", check=False)
        sh("git config user.email 'actions@users.noreply.github.com'", check=False)
        sh("git config core.autocrlf false", check=False)
        sh("git config apply.whitespace nowarn", check=False)
        sh("git config core.safecrlf false", check=False)
        sh("git config merge.renamelimit 999999", check=False)
        sh("git config diff.renames true", check=False)
    except Exception:
        pass

def ls_files() -> List[str]:
    try:
        out = sh("git ls-files")
        return [l.strip() for l in out.splitlines() if l.strip()]
    except Exception:
        return []

# ---------------- Context & Targets ----------------

def fetch_contextmap_comment(num: int) -> Optional[str]:
    for c in list_issue_comments(num):
        b = (c.get("body") or "").strip()
        if b.startswith("### contextmap-ready"):
            return b
    return None

def parse_affected_modules(contextmap_md: str, all_files: List[str]) -> List[str]:
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
            if any(f.startswith(it.rstrip('/') + '/') for f in sset):
                existing.add(it.rstrip('/'))
    return sorted(existing)

def expand_dependencies(seed_paths: List[str], all_files: List[str], max_extra: int = 80) -> List[str]:
    sset = set(all_files)
    out: Set[str] = set(seed_paths)
    for p in list(out):
        g1 = p.rstrip("/") + "/build.gradle"
        g2 = p.rstrip("/") + "/build.gradle.kts"
        m1 = p.rstrip("/") + "/src/main/AndroidManifest.xml"
        if g1 in sset:
            out.add(g1)
        if g2 in sset:
            out.add(g2)
        if m1 in sset:
            out.add(m1)
    pkg_root = "com.chris.m3usuite"
    extra = set()
    for f in [x for x in all_files if x.endswith(".kt") or x.endswith(".java")]:
        try:
            txt = Path(f).read_text(encoding="utf-8", errors="ignore")
        except Exception:
            continue
        if "import " in txt and pkg_root in txt:
            root = f.split("/")[0]
            if root not in out and any(x.startswith(root + "/") or x == root for x in sset):
                extra.add(root)
                if len(extra) >= max_extra:
                    break
    out.update(extra)
    return sorted(out)

# ---------------- Patch normalization ----------------

_BRACE_RE = re.compile(r"\{([^{}]*?)\s*=>\s*([^{}]*?)\}")

def _rewrite_brace_path(path: str) -> str:
    # handle AI brace shorthand: {old => new}
    while True:
        m = _BRACE_RE.search(path)
        if not m:
            break
        right = (m.group(2) or "").strip()
        repl = right if right else ""
        path = path[:m.start()] + repl + path[m.end():]
    path = re.sub(r"/{2,}", "/", path)
    path = path.lstrip("/")
    return path

def _normalize_brace_renames(patch_text: str) -> str:
    lines = patch_text.splitlines()
    for i, ln in enumerate(lines):
        if ln.startswith("diff --git "):
            m = re.match(r"diff --git a/(.+?)\s+b/(.+?)\s*$", ln)
            if m:
                a_path = _rewrite_brace_path(m.group(1))
                b_path = _rewrite_brace_path(m.group(2))
                lines[i] = f"diff --git a/{a_path} b/{b_path}"
        elif ln.startswith("--- ") or ln.startswith("+++ "):
            m = re.match(r"([+-]{3})\s+(\S+)", ln)
            if m:
                prefix, path = m.group(1), m.group(2)
                if path not in ("/dev/null",):
                    ab = ""
                    if path.startswith("a/"):
                        ab, core = "a/", path[2:]
                    elif path.startswith("b/"):
                        ab, core = "b/", path[2:]
                    else:
                        core = path
                    core = _rewrite_brace_path(core)
                    path = f"{ab}{core}" if ab else core
                lines[i] = f"{prefix} {path}"
    text = "\n".join(lines)
    if not text.endswith("\n"):
        text += "\n"
    return text

def _strip_code_fences(text: str) -> str:
    m = re.search(r"```(?:diff)?\s*(.*?)```", text, re.S)
    return m.group(1) if m else text

def sanitize_patch(raw: str) -> str:
    if not raw:
        return raw
    raw = _strip_code_fences(raw)
    raw = raw.replace("\r\n", "\n").replace("\r", "\n")
    pos = raw.find("diff --git ")
    if pos != -1:
        raw = raw[pos:]
    raw = _normalize_brace_renames(raw)
    if not raw.endswith("\n"):
        raw += "\n"
    return raw

def patch_uses_ab_prefix(patch_text: str) -> bool:
    return bool(re.search(r"^diff --git a/[\S]+ b/[\S]+", patch_text, re.M))

# ---------------- Apply mechanics ----------------

def _git_apply_try(tmp: str, pstrip: int, three_way: bool, extra_flags: str = "") -> subprocess.CompletedProcess:
    cmd = f"git apply {'-3' if three_way else ''} -p{pstrip} --whitespace=fix {extra_flags} {tmp}".strip()
    return subprocess.run(cmd, shell=True, text=True, capture_output=True)

def _git_apply_matrix(tmp: str, uses_ab: bool) -> List[Tuple[int, bool, str]]:
    # Versuchsmatrix (in Reihenfolge):
    # -p1/0 × 3-way ja/nein × Flags-Kombinationen
    pseq = [1, 0] if uses_ab else [0, 1]
    combos: List[Tuple[int, bool, str]] = []
    for p in pseq:
        combos.append((p, True, ""))   # 3-way
        combos.append((p, False, ""))  # normal
        combos.append((p, False, "--ignore-whitespace"))
        combos.append((p, False, "--inaccurate-eof"))
        combos.append((p, False, "--unidiff-zero"))              # toleranter Kontext
        combos.append((p, False, "--ignore-whitespace --inaccurate-eof"))
        combos.append((p, False, "--reject --ignore-whitespace"))
        combos.append((p, False, "--reject --unidiff-zero"))
    return combos

def whole_patch_fallback(patch_text: str) -> Tuple[bool, str]:
    tmp = ".github/codex/_solver_all.patch"
    Path(tmp).write_text(patch_text, encoding="utf-8")
    last_err = ""
    uses_ab = patch_uses_ab_prefix(patch_text)
    tries = _git_apply_matrix(tmp, uses_ab)
    for p, three, extra in tries:
        pr = _git_apply_try(tmp, p, three, extra)
        if pr.returncode == 0:
            return True, f"git apply {'-3' if three else ''} -p{p} --whitespace=fix {extra}".strip()
        last_err = (pr.stderr or pr.stdout or "")[:1600]

    # GNU patch Fallback (manche Diffs werden von 'patch' oft akzeptiert)
    for pp in (1, 0):
        pr = subprocess.run(f"patch -p{pp} -f -N --follow-symlinks < {tmp}",
                            shell=True, text=True, capture_output=True)
        if pr.returncode == 0:
            return True, f"patch -p{pp} -f -N --follow-symlinks"
        last_err = (pr.stderr or pr.stdout or last_err)[:1600]

    return False, last_err

def _split_sections(patch_text: str) -> List[str]:
    sections = []
    for m in re.finditer(r"^diff --git a/(\S+)\s+b/(\S+)\s*$", patch_text, re.M):
        start = m.start()
        next_m = re.search(r"^diff --git ", patch_text[m.end():], re.M)
        end = m.end() + (next_m.start() if next_m else len(patch_text) - m.end())
        sections.append(patch_text[start:end])
    return sections

def _make_zero_context(patch_text: str) -> str:
    """
    Transformiert alle Hunks in einen "Zero-Context"-Patch:
    - Entfernt Kontextzeilen (' ').
    - Setzt Hunk-Header auf ,0 (Kontextzeilenanzahl 0) – lässt Minus/Plus-Zählungen bestehen.
    Erhöht die Chance für git apply mit --unidiff-zero.
    """
    out_lines = []
    for line in patch_text.splitlines():
        if line.startswith("@@"):
            # @@ -a,b +c,d @@ (optional text)
            line = re.sub(r"(@@\s+-\d+)(?:,\d+)?(\s+\+\d+)(?:,\d+)?(\s+@@.*)",
                          r"\1,0\2,0\3", line)
            out_lines.append(line)
            continue
        if line.startswith(" "):
            # Kontextzeilen verwerfen
            continue
        out_lines.append(line)
    text = "\n".join(out_lines)
    if not text.endswith("\n"):
        text += "\n"
    return text

def apply_patch_unidiff(patch_text: str) -> Tuple[List[str], List[str], List[str]]:
    sections = _split_sections(patch_text)
    if not sections:
        ok, info = whole_patch_fallback(patch_text)
        if not ok:
            raise RuntimeError(f"git/patch apply failed: {info}")
        return (["<whole>"], [], [])
    applied, rejected, skipped = [], [], []
    for idx, body in enumerate(sections, start=1):
        tmp = f".github/codex/_solver_sec_{idx}.patch"
        Path(tmp).write_text(body.replace("\r\n", "\n").replace("\r", "\n"), encoding="utf-8")
        uses_ab = patch_uses_ab_prefix(body)
        success = False
        # 1) Git apply Matrix
        for p, three, extra in _git_apply_matrix(tmp, uses_ab):
            pr = _git_apply_try(tmp, p, three, extra)
            if pr.returncode == 0:
                applied.append(f"section_{idx}")
                success = True
                break
        if success:
            continue
        # 2) GNU patch Fallback
        for pp in (1, 0):
            pr = subprocess.run(f"patch -p{pp} -f -N --follow-symlinks < {tmp}",
                                shell=True, text=True, capture_output=True)
            if pr.returncode == 0:
                applied.append(f"section_{idx}")
                success = True
                break
        if success:
            continue
        # 3) Zero-Context-Rewrite und erneuter Git-Versuch
        zero_tmp = f".github/codex/_solver_sec_{idx}_zc.patch"
        Path(zero_tmp).write_text(_make_zero_context(Path(tmp).read_text(encoding='utf-8')), encoding='utf-8')
        for p in ([1, 0] if uses_ab else [0, 1]):
            pr = _git_apply_try(zero_tmp, p, False, "--unidiff-zero --ignore-whitespace")
            if pr.returncode == 0:
                applied.append(f"section_{idx}")
                success = True
                break
        if success:
            continue
        # 4) Als *rejected* markieren (zur manuellen Nacharbeit)
        rejected.append(f"section_{idx}")
    return applied, rejected, skipped

# ---------------- OpenAI diff generation ----------------

def openai_diff(contextmap: str, docs: str, target_paths: List[str]) -> str:
    from openai import OpenAI
    api_key = os.environ.get("OPENAI_API_KEY")
    if not api_key:
        raise RuntimeError("OPENAI_API_KEY not set")
    client = OpenAI(api_key=api_key, base_url=(os.environ.get("OPENAI_BASE_URL") or "https://api.openai.com/v1"))
    model = os.environ.get("OPENAI_MODEL_DEFAULT", "gpt-5")
    effort = os.environ.get("OPENAI_REASONING_EFFORT", "high")
    target_preview = "\n".join(sorted(target_paths))[:8000]
    SYSTEM = (
        "You are an expert Android/Kotlin/Gradle engineer. "
        "Generate ONE unified diff that applies at repo root (git apply prefers -p1 with a/b prefixes). "
        "Headers required: 'diff --git', 'index', '--- a/...', '+++ b/...', hunks '@@'. "
        "For new files add 'new file mode 100644' and '/dev/null' header. "
        "Do NOT use brace-rename shorthand like {old => new}. "
        "Ensure LF line endings and a trailing newline at EOF."
    )
    USER = (
        f"ContextMap:\n{contextmap}\n\n"
        f"Docs:\n{docs or '(no docs present)'}\n\n"
        f"Target files/modules (primary and inferred):\n{target_preview}\n\n"
        "Output rules:\n"
        "- Exactly ONE unified diff, starting with: diff --git a/... b/...\n"
        "- Include new files with 'new file mode 100644' and correct ---/+++ headers\n"
        "- No prose besides the diff.\n"
    )
    resp = client.responses.create(
        model=model,
        input=[{"role": "system", "content": SYSTEM}, {"role": "user", "content": USER}],
        reasoning={"effort": effort},
    )
    return getattr(resp, "output_text", "")

# ---------------- Build dispatch ----------------

def default_branch() -> str:
    return gh_api("GET", f"/repos/{repo()}").get("default_branch", "main")

def dispatch_build(workflow_ident: str, ref_branch: str, inputs: dict | None) -> str:
    gh_api("POST", f"/repos/{repo()}/actions/workflows/{workflow_ident}/dispatches",
           {"ref": ref_branch, "inputs": inputs or {}})
    return time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())

def wait_build_result(workflow_ident: str, ref_branch: str, since_iso: str, timeout_s=1800) -> dict:
    base = f"/repos/{repo()}/actions/workflows/{workflow_ident}/runs"
    t0 = time.time()
    while True:
        try:
            runs = gh_api("GET", f"{base}?event=workflow_dispatch&branch={ref_branch}")
            arr = runs.get("workflow_runs", []) or []
            cand = arr[0] if arr else {}
            if cand and cand.get("created_at", "") >= since_iso:
                rid = cand.get("id")
                while True:
                    run = gh_api("GET", f"/repos/{repo()}/actions/runs/{rid}")
                    if run.get("status") == "completed":
                        return run
                    if time.time() - t0 > timeout_s:
                        return run
                    time.sleep(6)
        except Exception:
            if time.time() - t0 > timeout_s:
                return {}
        if time.time() - t0 > timeout_s:
            return {}
        time.sleep(3)

# ---------------- Post-failure helpers ----------------

def summarize_rejects(num: int):
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
    body = "⚠️ Solver: Einige Hunks wurden als `.rej` abgelegt (manuelle Nacharbeit nötig):\n" + \
           "\n".join(f"- {r}" for r in rej_files[:50])
    if preview:
        body += "\n\n```diff\n" + "".join(preview) + "\n```"
    post_comment(num, body)

# ---------------- Triage dispatch helper ----------------

def dispatch_triage(issue_num: int):
    """Trigger the triage (Bot 3) workflow via workflow_dispatch event."""
    try:
        gh_api("POST", f"/repos/{repo()}/actions/workflows/codex-triage.yml/dispatches",
               {"ref": default_branch(), "inputs": {"issue": str(issue_num)}})
    except Exception as e:
        print(f"::warning::Failed to dispatch triage workflow: {e}")

# ---------------- Main ----------------

def main():
    ensure_repo_cwd()
    num = issue_number()
    if not num:
        print("::error::No issue number in event or inputs")
        sys.exit(1)

    labels = get_labels(num)
    if "contextmap-ready" not in labels:
        print("::notice::No 'contextmap-ready' label; skipping")
        sys.exit(0)

    cm = fetch_contextmap_comment(num)
    if not cm or not cm.strip().startswith("### contextmap-ready"):
        print("::notice::No contextmap comment; skipping")
        sys.exit(0)

    docs = []
    for name in ["AGENTS.md", "ARCHITECTURE_OVERVIEW.md", "ROADMAP.md", "CHANGELOG.md"]:
        if Path(name).exists():
            try:
                docs.append(f"\n--- {name} ---\n" + Path(name).read_text(encoding="utf-8", errors="replace"))
            except Exception:
                pass
    docs_txt = "".join(docs)

    all_files = ls_files()
    seeds = parse_affected_modules(cm, all_files)
    if not seeds:
        add_label(num, "solver-error")
        post_comment(num, "❌ Solver: Keine gültigen Module in der ContextMap gefunden.")
        dispatch_triage(num)  # Triage bot trigger
        sys.exit(1)

    targets = expand_dependencies(seeds, all_files, max_extra=80)
    base_branch = default_branch()
    sh(f"git fetch origin {base_branch}", check=False)
    sh(f"git checkout -B codex/solve-{int(time.time())} origin/{base_branch}")
    branch = sh("git rev-parse --abbrev-ref HEAD").strip()

    # 1) Diff generieren
    try:
        raw = openai_diff(cm, docs_txt, targets)
    except Exception as e:
        add_label(num, "solver-error")
        post_comment(num, f"❌ Solver: OpenAI-Fehler\n```\n{e}\n```")
        dispatch_triage(num)
        sys.exit(1)

    patch = sanitize_patch(raw)
    if "diff --git " not in patch:
        add_label(num, "solver-error")
        post_comment(num, f"❌ Solver: Kein gültiger Diff\n```\n{patch[:1200]}\n```")
        dispatch_triage(num)
        sys.exit(1)

    # 2) Unidiff-Validierung nur als Hinweis, nicht als Blocker
    try:
        PatchSet.from_string(patch)
    except (UnidiffParseError, Exception) as e:
        post_comment(num, f"ℹ️ Solver: Unidiff Parser Warning: `{type(e).__name__}: {e}` – weiche auf robuste Apply-Fallbacks aus.")

    # 3) Apply versuchen: Whole, dann section-wise (inkl. Zero-Context & GNU patch)
    ok, info = whole_patch_fallback(patch)
    applied, rejected, skipped = [], [], []
    if ok:
        post_comment(num, f"✅ Solver: Patch via Whole-Apply erfolgreich (`{info}`)")
    else:
        post_comment(num, f"ℹ️ Solver: Whole-Apply scheiterte, versuche section-wise.\n```\n{info}\n```")
        try:
            applied, rejected, skipped = apply_patch_unidiff(patch)
        except Exception as e:
            add_label(num, "solver-error")
            post_comment(num, f"❌ Solver: Patch-Apply fehlgeschlagen\n```\n{e}\n```")
            summarize_rejects(num)
            dispatch_triage(num)
            sys.exit(1)

    # Aufräumen temporärer Patch-Dateien
    for f in glob.glob(".github/codex/_solver_sec_*.patch"):
        try:
            os.remove(f)
        except Exception:
            pass
    if os.path.exists(".github/codex/_solver_all.patch"):
        try:
            os.remove(".github/codex/_solver_all.patch")
        except Exception:
            pass

    status = sh("git status --porcelain")
    if not status.strip():
        add_label(num, "solver-error")
        post_comment(num, "❌ Solver: Keine Änderungen im Working Tree nach Apply.")
        summarize_rejects(num)
        dispatch_triage(num)
        sys.exit(1)

    sh("git add -A")
    sh(f"git commit -m 'codex: solver apply (issue #{num})'")
    sh("git push --set-upstream origin HEAD")

    pr = gh_api("POST", f"/repos/{repo()}/pulls", {
        "title": "codex: solver changes",
        "head": branch,
        "base": base_branch,
        "body": f"Automatisch erzeugte Änderungen basierend auf der ContextMap. (issue #{num})"
    })
    pr_num = pr.get("number")
    pr_url = pr.get("html_url")
    post_comment(num, f"🔧 PR erstellt: #{pr_num} — {pr_url}")

    # 4) Optional: Downstream Build (Build und Test auf dem erstellten Branch)
    wf = os.environ.get("SOLVER_BUILD_WORKFLOW", "release-apk.yml")
    try:
        since = dispatch_build(wf, branch, {"build_type": "debug", "issue": str(num)})
        run = wait_build_result(wf, branch, since, timeout_s=1800)
        concl = (run or {}).get("conclusion", "")
        if concl == "success":
            remove_label(num, "contextmap-ready")
            add_label(num, "solver-done")
            post_comment(num, "✅ Build erfolgreich – Label `solver-done` gesetzt.")
        else:
            add_label(num, "solver-error")
            post_comment(num, "❌ Build fehlgeschlagen – Label `solver-error` gesetzt (Bot 3 wird reagieren).")
            dispatch_triage(num)
    except Exception as e:
        add_label(num, "solver-error")
        post_comment(num, f"❌ Build-Dispatch fehlgeschlagen\n```\n{e}\n```")
        dispatch_triage(num)
        sys.exit(1)

if __name__ == "__main__":
    try:
        main()
    except SystemExit:
        raise
    except Exception as e:
        try:
            num = issue_number()
            if num:
                add_label(num, "solver-error")
                dispatch_triage(num)
        except Exception:
            pass
        print("::error::Unexpected solver error:", e)
        sys.exit(1)
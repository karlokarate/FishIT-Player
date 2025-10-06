# -*- coding: utf-8 -*-
"""
Bot 2 - Solver (robust, supports workflow_dispatch without label gating)

- Bei workflow_dispatch: nutzt ISSUE_NUMBER und überspringt 'contextmap-ready'.
- Nimmt vorhandene Diffs aus dem Issue (falls vorhanden) bevorzugt; sonst AI-Diff.
- Robuste Apply-Strategien (git apply Matrix, zero-context, gnu patch pro Section).
- Kein .orig-Müll; .rej wird kommentiert.
- Startet Build standardmäßig mit build_type=debug.
"""
from __future__ import annotations
import os, re, json, time, subprocess, sys, glob
from pathlib import Path
from typing import List, Tuple, Optional, Set

import requests
from unidiff import PatchSet
from unidiff.errors import UnidiffParseError

# ---------- GitHub helpers ----------
def repo() -> str:
    return os.environ["GITHUB_REPOSITORY"]

def event() -> dict:
    path = os.environ["GITHUB_EVENT_PATH"]
    return json.loads(Path(path).read_text(encoding="utf-8")) if path and Path(path).exists() else {}

def gh_api(method: str, path: str, payload: dict | None = None) -> dict:
    token = os.environ.get("GITHUB_TOKEN") or os.environ.get("INPUT_TOKEN") or ""
    url = f"https://api.github.com{path}"
    headers = {
        "Authorization": f"Bearer {token}",
        "Accept": "application/vnd.github+json"
    }
    r = requests.request(method, url, headers=headers, json=payload, timeout=60)
    if r.status_code >= 300:
        raise RuntimeError(f"GitHub API {method} {path} failed: {r.status_code} {r.text[:800]}")
    try:
        return r.json()
    except Exception:
        return {}

def list_issue_comments(num: int) -> List[dict]:
    res = gh_api("GET", f"/repos/{repo()}/issues/{num}/comments")
    return res if isinstance(res, list) else []

def get_issue(num: int) -> dict:
    return gh_api("GET", f"/repos/{repo()}/issues/{num}")

def get_labels(num: int) -> Set[str]:
    issue = get_issue(num)
    return {l.get("name", "") for l in issue.get("labels", [])}

def add_label(num: int, label: str):
    gh_api("POST", f"/repos/{repo()}/issues/{num}/labels", {"labels": [label]})

def remove_label(num: int, label: str):
    try:
        requests.delete(
            f"https://api.github.com/repos/{repo()}/issues/{num}/labels/{label}",
            headers={
                "Authorization": f"Bearer {os.environ.get('GITHUB_TOKEN') or os.environ.get('INPUT_TOKEN')}",
                "Accept": "application/vnd.github+json"
            },
            timeout=30
        )
    except Exception:
        pass

def post_comment(num: int, body: str):
    gh_api("POST", f"/repos/{repo()}/issues/{num}/comments", {"body": body})

# ---------- Shell / Git ----------
def sh(cmd: str, check=True) -> str:
    p = subprocess.run(cmd, shell=True, text=True, capture_output=True)
    if check and p.returncode != 0:
        raise RuntimeError(f"cmd failed: {cmd}\nSTDOUT:\n{p.stdout}\nSTDERR:\n{p.stderr}")
    return p.stdout or ""

def ensure_repo_cwd():
    ws = os.environ.get("GITHUB_WORKSPACE")
    if ws and Path(ws).exists():
        os.chdir(ws)
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
        return [l.strip() for l in sh("git ls-files").splitlines() if l.strip()]
    except Exception:
        return []

# ---------- Context ----------
def issue_number() -> Optional[int]:
    ev = event()
    # priority: explicit input (workflow_dispatch)
    inputs = ev.get("inputs") or {}
    if inputs.get("issue"):
        try:
            return int(inputs["issue"])
        except:
            pass
    # label-based events
    if "issue" in ev:
        n = (ev.get("issue") or {}).get("number")
        if n:
            return int(n)
    # env fallback
    env_issue = os.environ.get("ISSUE_NUMBER")
    if env_issue:
        try:
            return int(env_issue)
        except:
            pass
    return None

def is_dispatch() -> bool:
    # Works across runners
    return (os.environ.get("GH_EVENT_NAME") or os.environ.get("GITHUB_EVENT_NAME") or "") == "workflow_dispatch"

def fetch_contextmap_comment(num: int) -> Optional[str]:
    for c in list_issue_comments(num):
        b = (c.get("body") or "").strip()
        if b.startswith("### contextmap-ready"):
            return b
    return None

def extract_issue_raw_diff(num: int) -> str:
    """Concatenates issue + comments, returns text from first 'diff --git' onward (if any)."""
    bodies = []
    issue = get_issue(num)
    bodies.append(issue.get("body") or "")
    for c in list_issue_comments(num):
        bodies.append(c.get("body") or "")
    raw = "\n\n".join(bodies)
    pos = raw.find("diff --git ")
    return raw[pos:] if pos != -1 else ""

def parse_affected_modules(contextmap_md: str, all_files: List[str]) -> List[str]:
    # Parse the "potentiell betroffene Module" list from contextmap comment (if present)
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
        elif any(f.startswith(it.rstrip('/') + '/') for f in sset):
            existing.add(it.rstrip('/'))
    return sorted(existing)

def expand_dependencies(seed_paths: List[str], all_files: List[str], max_extra: int = 80) -> List[str]:
    sset = set(all_files)
    out: Set[str] = set(seed_paths)
    for p in list(out):
        g1 = p.rstrip("/") + "/build.gradle"
        g2 = p.rstrip("/") + "/build.gradle.kts"
        m1 = p.rstrip("/") + "/src/main/AndroidManifest.xml"
        if g1 in sset: out.add(g1)
        if g2 in sset: out.add(g2)
        if m1 in sset: out.add(m1)
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

# ---------- Patch normalization & split ----------
_VALID_LINE = re.compile(r"^(diff --git |index |new file mode |deleted file mode |similarity index |rename (from|to) |--- |\+\+\+ |@@ |[\+\- ]|\\ No newline at end of file)")
def _strip_code_fences(text: str) -> str:
    m = re.search(r"```(?:diff)?\s*(.*?)```", text, re.S)
    return (m.group(1) if m else text)

def sanitize_patch(raw: str) -> str:
    if not raw:
        return raw
    txt = _strip_code_fences(raw).replace("\r\n", "\n").replace("\r", "\n")
    pos = txt.find("diff --git ")
    if pos != -1:
        txt = txt[pos:]
    clean = []
    for ln in txt.splitlines():
        # remove list numbering or separator lines between diff sections
        if re.match(r"^\s*\d+\.\s*:?\s*$", ln):
            continue
        if re.match(r"^\s*[–—\-]\s*$", ln):
            continue
        if _VALID_LINE.match(ln):
            clean.append(ln)
    out = "\n".join(clean)
    if not out.endswith("\n"):
        out += "\n"
    return out

def patch_uses_ab_prefix(patch_text: str) -> bool:
    return bool(re.search(r"^diff --git a/[\S]+ b/[\S]+", patch_text, re.M))

def _split_sections(patch_text: str) -> List[str]:
    sections = []
    it = list(re.finditer(r"^diff --git a/(\S+)\s+b/(\S+)\s*$", patch_text, re.M))
    for i, m in enumerate(it):
        start = m.start()
        end = it[i+1].start() if i+1 < len(it) else len(patch_text)
        sections.append(patch_text[start:end].rstrip() + "\n")
    return sections

# ---------- Apply mechanics ----------
def _git_apply_try(tmp: str, pstrip: int, three_way: bool, extra_flags: str = "") -> subprocess.CompletedProcess:
    cmd = f"git apply {'-3' if three_way else ''} -p{pstrip} --whitespace=fix {extra_flags} {tmp}".strip()
    return subprocess.run(cmd, shell=True, text=True, capture_output=True)

def _git_apply_matrix(tmp: str, uses_ab: bool) -> List[Tuple[int, bool, str]]:
    pseq = [1, 0] if uses_ab else [0, 1]
    combos = []
    for p in pseq:
        combos += [
            (p, True, ""), (p, False, ""), (p, False, "--ignore-whitespace"),
            (p, False, "--inaccurate-eof"), (p, False, "--unidiff-zero"),
            (p, False, "--ignore-whitespace --inaccurate-eof"),
            (p, False, "--reject --ignore-whitespace"),
            (p, False, "--reject --unidiff-zero")
        ]
    return combos

def _make_zero_context(patch_text: str) -> str:
    out = []
    for ln in patch_text.splitlines():
        if ln.startswith("@@"):
            ln = re.sub(r"(@@\s+-\d+)(?:,\d+)?(\s+\+\d+)(?:,\d+)?(\s+@@.*)", r"\1,0\2,0\3", ln)
            out.append(ln)
        elif ln.startswith(" "):
            continue
        else:
            out.append(ln)
    z = "\n".join(out)
    if not z.endswith("\n"):
        z += "\n"
    return z

def _gnu_patch_apply(tmp: str, p: int, rej_path: str) -> subprocess.CompletedProcess:
    # No backups (.orig)
    cmd = f"patch -p{p} -f -N --follow-symlinks --no-backup-if-mismatch -r {rej_path} < {tmp}"
    return subprocess.run(cmd, shell=True, text=True, capture_output=True)

def whole_git_apply(patch_text: str) -> Tuple[bool, str]:
    tmp = ".github/codex/_solver_all.patch"
    Path(tmp).write_text(patch_text, encoding="utf-8")
    uses_ab = patch_uses_ab_prefix(patch_text)
    last_err = ""
    for p, three, extra in _git_apply_matrix(tmp, uses_ab):
        pr = _git_apply_try(tmp, p, three, extra)
        if pr.returncode == 0:
            return True, f"git apply {'-3' if three else ''} -p{p} --whitespace=fix {extra}".strip()
        last_err = (pr.stderr or pr.stdout or "")[:1600]
    # GNU patch (whole) – avoid .orig files (we output rejects separately)
    return False, last_err

def apply_section(sec_text: str, idx: int) -> bool:
    tmp = f".github/codex/_solver_sec_{idx}.patch"
    Path(tmp).write_text(sec_text, encoding="utf-8")
    uses_ab = patch_uses_ab_prefix(sec_text)
    # 1) git apply Matrix
    for p, three, extra in _git_apply_matrix(tmp, uses_ab):
        pr = _git_apply_try(tmp, p, three, extra)
        if pr.returncode == 0:
            return True
    # 2) zero-context git apply
    ztmp = f".github/codex/_solver_sec_{idx}_zc.patch"
    Path(ztmp).write_text(_make_zero_context(sec_text), encoding="utf-8")
    for p in ([1, 0] if uses_ab else [0, 1]):
        pr = _git_apply_try(ztmp, p, False, "--unidiff-zero --ignore-whitespace")
        if pr.returncode == 0:
            return True
    # 3) gnu patch (per section, no backups)
    for pp in (1, 0):
        pr = _gnu_patch_apply(tmp, pp, f".github/codex/_solver_sec_{idx}.rej")
        if pr.returncode == 0:
            return True
    return False

# ---------- Diff generation (OpenAI) ----------
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
        "Generate ONE unified diff at repo root. Use a/b prefixes. "
        "Headers: diff --git, index, --- a/..., +++ b/..., @@. "
        "New files must use /dev/null header. LF only; trailing newline."
    )
    USER = (
        f"ContextMap:\n{contextmap}\n\nDocs:\n{docs or '(no docs present)'}\n\n"
        f"Targets:\n{target_preview}\n\n"
        "Output: ONE unified diff, no prose."
    )
    resp = client.responses.create(
        model=model,
        input=[{"role": "system", "content": SYSTEM}, {"role": "user", "content": USER}],
        reasoning={"effort": effort},
    )
    return getattr(resp, "output_text", "")

# ---------- Build dispatch ----------
def default_branch() -> str:
    return gh_api("GET", f"/repos/{repo()}").get("default_branch", "main")

def dispatch_build(workflow_ident: str, ref_branch: str, inputs: dict | None) -> str:
    gh_api("POST", f"/repos/{repo()}/actions/workflows/{workflow_ident}/dispatches", {"ref": ref_branch, "inputs": inputs or {}})
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
                # Wait for specific run to complete
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

# ---------- Reject summary ----------
def summarize_rejects(num: int):
    rej_files = glob.glob("**/*.rej", recursive=True)
    if not rej_files:
        return
    preview = []
    for rf in rej_files[:10]:
        try:
            txt = Path(rf).read_text(encoding="utf-8", errors="replace")
        except Exception:
            txt = ""
        preview.append(f"\n--- {rf} ---\n{txt[:500]}")
    body = "⚠️ Solver: Einige Hunks wurden als `.rej` abgelegt (manuelle Nacharbeit möglich):\n" + \
           "\n".join(f"- {r}" for r in rej_files[:50])
    if preview:
        body += "\n\n```diff\n" + "".join(preview) + "\n```"
    post_comment(num, body)

# ---------- Main ----------
def main():
    ensure_repo_cwd()
    num = issue_number()
    if not num:
        print("::error::No issue number")
        sys.exit(1)

    # Label-gate only for label events; skip check on workflow_dispatch
    if not is_dispatch():
        labels = get_labels(num)
        if "contextmap-ready" not in labels:
            print("::notice::No 'contextmap-ready' label; skipping")
            sys.exit(0)

    # 0) Optional: use diff provided directly in issue
    raw_issue_diff = extract_issue_raw_diff(num)
    patch = sanitize_patch(raw_issue_diff) if raw_issue_diff else ""

    # 1) Fallback: ContextMap -> AI-generated diff
    if not patch or "diff --git " not in patch:
        cm = fetch_contextmap_comment(num)
        if not cm and not is_dispatch():
            print("::notice::No contextmap comment; skipping")
            sys.exit(0)
        docs = []
        for n in ["AGENTS.md", "ARCHITECTURE_OVERVIEW.md", "ROADMAP.md", "CHANGELOG.md"]:
            if Path(n).exists():
                try:
                    docs.append(f"\n--- {n} ---\n" + Path(n).read_text(encoding="utf-8", errors="replace"))
                except Exception:
                    pass
        docs_txt = "".join(docs)
        all_files = ls_files()
        seeds = parse_affected_modules(cm or "", all_files) if cm else (["app"] if "app" in all_files or any(p.startswith("app/") for p in all_files) else [])
        targets = expand_dependencies(seeds, all_files, max_extra=80) if seeds else all_files[:80]
        try:
            raw_ai = openai_diff(cm or "(manual dispatch; no contextmap)", docs_txt, targets)
        except Exception as e:
            add_label(num, "solver-error")
            post_comment(num, f"❌ Solver: OpenAI-Fehler\n```\n{e}\n```")
            dispatch_triage(num)
            sys.exit(1)
        patch = sanitize_patch(raw_ai)

    if "diff --git " not in patch:
        add_label(num, "solver-error")
        post_comment(num, f"❌ Solver: Kein gültiger Diff\n```\n{patch[:1200]}\n```")
        dispatch_triage(num)
        sys.exit(1)

    # Note: Non-blocking warning if unidiff parse fails (we still attempt apply)
    try:
        PatchSet.from_string(patch)
    except (UnidiffParseError, Exception) as e:
        post_comment(num, f"ℹ️ Solver: Unidiff Parser Warning: `{type(e).__name__}: {e}` – nutze Fallbacks.")

    base = default_branch()
    sh(f"git fetch origin {base}", check=False)
    sh(f"git checkout -B codex/solve-{int(time.time())} origin/{base}")
    branch = sh("git rev-parse --abbrev-ref HEAD").strip()

    # 2) Apply patch: first whole apply, then section-wise fallback
    ok, info = whole_git_apply(patch)
    if ok:
        post_comment(num, f"✅ Solver: Patch via Whole-Apply erfolgreich (`{info}`)")
    else:
        post_comment(num, f"ℹ️ Solver: Whole-Apply scheiterte, section-wise Fallback.\n```\n{info}\n```")
        sections = _split_sections(patch)
        if not sections:
            add_label(num, "solver-error")
            post_comment(num, "❌ Solver: Keine diff --git Sections gefunden.")
            dispatch_triage(num)
            sys.exit(1)
        applied_any = False
        for i, sec in enumerate(sections, 1):
            if apply_section(sec, i):
                applied_any = True
        if not applied_any:
            add_label(num, "solver-error")
            post_comment(num, "❌ Solver: Section-wise Apply fehlgeschlagen.")
            summarize_rejects(num)
            dispatch_triage(num)
            sys.exit(1)

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
        "base": base,
        "body": f"Automatisch erzeugte Änderungen basierend auf dem Issue/Context. (issue #{num})"
    })
    pr_num = pr.get("number"); pr_url = pr.get("html_url")
    post_comment(num, f"🔧 PR erstellt: #{pr_num} — {pr_url}")

    # 3) Trigger build (always debug build)
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
            post_comment(num, "❌ Build fehlgeschlagen – Label `solver-error` gesetzt (Bot 3 wird reagieren).")
            dispatch_triage(num)
    except Exception as e:
        add_label(num, "solver-error")
        post_comment(num, f"❌ Build-Dispatch fehlgeschlagen\n```\n{e}\n```")
        dispatch_triage(num)
        sys.exit(1)

def dispatch_triage(issue_num: int):
    try:
        gh_api("POST", f"/repos/{repo()}/actions/workflows/codex-triage.yml/dispatches",
               {"ref": default_branch(), "inputs": {"issue": str(issue_num)}})
    except Exception as e:
        print(f"::warning::Failed to dispatch triage workflow: {e}")

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
        print(f"::error::Unexpected solver error: {e}")
        sys.exit(1)
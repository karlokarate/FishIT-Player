#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
Bot 2 - Solver (robust gegen fragile Unified Diffs, keine .orig-Backups)

Kernfeatures:
- Diff-Sanitizer: entfernt "1.:" / "2.:" / sonstige Non-Diff-Zeilen zwischen Sections
- Whole git-apply zuerst; dann section-wise git-apply; dann Zero-Context; dann GNU patch per Section
- GNU patch Fallback OHNE Backups: --no-backup-if-mismatch, eigenes .rej-File
- Verifikation: erwartete Zieldateien wirklich geändert/angelegt? Sonst Direct-Reconstruct aus Diff
- Sauberes Labeling/Kommentare kompatibel zu Bot 3
"""

from __future__ import annotations
import os, re, json, time, subprocess, sys, glob
from pathlib import Path
from typing import List, Tuple, Optional, Dict, Set
import requests
from unidiff import PatchSet
from unidiff.errors import UnidiffParseError

# ---------- GitHub helpers ----------
def repo() -> str: return os.environ["GITHUB_REPOSITORY"]
def event() -> dict: return json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text(encoding="utf-8"))
def gh_api(method: str, path: str, payload: dict | None = None) -> dict:
    url = f"https://api.github.com{path}"
    headers = {"Authorization": f"Bearer {os.environ['GITHUB_TOKEN']}", "Accept": "application/vnd.github+json"}
    r = requests.request(method, url, headers=headers, json=payload, timeout=60)
    if r.status_code >= 300:
        raise RuntimeError(f"GitHub API {method} {path} failed: {r.status_code} {r.text[:800]}")
    try: return r.json()
    except Exception: return {}

def list_issue_comments(num: int) -> List[dict]:
    res = gh_api("GET", f"/repos/{repo()}/issues/{num}/comments")
    return res if isinstance(res, list) else []
def get_labels(num: int) -> Set[str]:
    issue = gh_api("GET", f"/repos/{repo()}/issues/{num}")
    return {l.get("name","") for l in issue.get("labels",[])}
def add_label(num: int, label: str): gh_api("POST", f"/repos/{repo()}/issues/{num}/labels", {"labels":[label]})
def remove_label(num: int, label: str):
    try:
        requests.delete(f"https://api.github.com/repos/{repo()}/issues/{num}/labels/{label}",
                        headers={"Authorization": f"Bearer {os.environ['GITHUB_TOKEN']}", "Accept": "application/vnd.github+json"}, timeout=30)
    except Exception: pass
def post_comment(num: int, body: str):
    gh_api("POST", f"/repos/{repo()}/issues/{num}/comments", {"body": body})

def issue_number() -> Optional[int]:
    ev = event()
    if "issue" in ev:
        n = (ev.get("issue") or {}).get("number")
        if n: return int(n)
    inputs = ev.get("inputs") or {}
    if inputs.get("issue"):
        try: return int(inputs["issue"])
        except: pass
    env_issue = os.environ.get("ISSUE_NUMBER")
    if env_issue:
        try: return int(env_issue)
        except: pass
    return None

# ---------- Shell / Git ----------
def sh(cmd: str, check=True) -> str:
    p = subprocess.run(cmd, shell=True, text=True, capture_output=True)
    if check and p.returncode != 0:
        raise RuntimeError(f"cmd failed: {cmd}\nSTDOUT:\n{p.stdout}\nSTDERR:\n{p.stderr}")
    return p.stdout or ""

def ensure_repo_cwd():
    ws = os.environ.get("GITHUB_WORKSPACE")
    if ws and Path(ws).exists(): os.chdir(ws)
    try:
        sh("git config user.name 'codex-bot'", check=False)
        sh("git config user.email 'actions@users.noreply.github.com'", check=False)
        sh("git config core.autocrlf false", check=False)
        sh("git config apply.whitespace nowarn", check=False)
        sh("git config core.safecrlf false", check=False)
        sh("git config merge.renamelimit 999999", check=False)
        sh("git config diff.renames true", check=False)
    except Exception: pass

def ls_files() -> List[str]:
    try: return [l.strip() for l in sh("git ls-files").splitlines() if l.strip()]
    except Exception: return []

# ---------- Context ----------
def fetch_contextmap_comment(num: int) -> Optional[str]:
    for c in list_issue_comments(num):
        b = (c.get("body") or "").strip()
        if b.startswith("### contextmap-ready"): return b
    return None

def parse_affected_modules(contextmap_md: str, all_files: List[str]) -> List[str]:
    m = re.search(r"####\s*\(potentiell\)\s*betroffene\s*Module\s*(.*?)\n####", contextmap_md, re.S | re.I)
    if not m: m = re.search(r"####\s*\(potentiell\)\s*betroffene\s*Module\s*(.*)$", contextmap_md, re.S | re.I)
    if not m: return []
    block = m.group(1)
    items = re.findall(r"^\s*-\s+(.+)$", block, re.M)
    items = [i.strip().strip("`").strip() for i in items if i.strip()]
    sset = set(all_files); existing = set()
    for it in items:
        if it in sset: existing.add(it)
        elif any(f.startswith(it.rstrip('/') + '/') for f in sset): existing.add(it.rstrip('/'))
    return sorted(existing)

def expand_dependencies(seed_paths: List[str], all_files: List[str], max_extra: int = 80) -> List[str]:
    sset = set(all_files); out: Set[str] = set(seed_paths)
    for p in list(out):
        g1 = p.rstrip("/") + "/build.gradle"
        g2 = p.rstrip("/") + "/build.gradle.kts"
        m1 = p.rstrip("/") + "/src/main/AndroidManifest.xml"
        if g1 in sset: out.add(g1)
        if g2 in sset: out.add(g2)
        if m1 in sset: out.add(m1)
    pkg_root = "com.chris.m3usuite"
    extra=set()
    for f in [x for x in all_files if x.endswith(".kt") or x.endswith(".java")]:
        try: txt = Path(f).read_text(encoding="utf-8", errors="ignore")
        except Exception: continue
        if "import " in txt and pkg_root in txt:
            root = f.split("/")[0]
            if root not in out and any(x.startswith(root + "/") or x == root for x in sset):
                extra.add(root)
                if len(extra) >= max_extra: break
    out.update(extra)
    return sorted(out)

# ---------- Diff Sanitizer ----------
_VALID_LINE = re.compile(r"^(diff --git |index |new file mode |deleted file mode |similarity index |rename (from|to) |--- |\+\+\+ |@@ |[\+\- ]|\\ No newline at end of file)")

def _strip_code_fences(text: str) -> str:
    m = re.search(r"```(?:diff)?\s*(.*?)```", text, re.S)
    return (m.group(1) if m else text)

def sanitize_patch(raw: str) -> str:
    if not raw: return raw
    txt = _strip_code_fences(raw)
    txt = txt.replace("\r\n","\n").replace("\r","\n")
    # Drop any leading junk before first diff
    pos = txt.find("diff --git ")
    if pos != -1: txt = txt[pos:]
    lines = txt.splitlines()
    clean = []
    for ln in lines:
        # remove markers like "1.:" / "2.:" / "—" bullets etc.
        if re.match(r"^\s*\d+\.\s*:?\s*$", ln): continue
        if re.match(r"^\s*[–—-]\s*$", ln): continue
        if _VALID_LINE.match(ln):
            clean.append(ln)
        # everything else is silently dropped (prevents 'malformed patch ... diff --git' mid-hunk)
    clean_txt = "\n".join(clean)
    if not clean_txt.endswith("\n"): clean_txt += "\n"
    return clean_txt

# ---------- Section split / helpers ----------
def patch_uses_ab_prefix(patch_text: str) -> bool:
    return bool(re.search(r"^diff --git a/[\S]+ b/[\S]+", patch_text, re.M))

def _split_sections(patch_text: str) -> List[str]:
    sections=[]; it = list(re.finditer(r"^diff --git a/(\S+)\s+b/(\S+)\s*$", patch_text, re.M))
    for i, m in enumerate(it):
        start=m.start()
        end = it[i+1].start() if i+1 < len(it) else len(patch_text)
        sections.append(patch_text[start:end].rstrip() + "\n")
    return sections

def _targets_from_sections(sections: List[str]) -> List[str]:
    targets=[]
    for sec in sections:
        m = re.match(r"^diff --git a/(\S+)\s+b/(\S+)\s*$", sec.splitlines()[0])
        if m:
            b = m.group(2)
            targets.append(b)
    return targets

# ---------- Apply mechanics ----------
def _git_apply_try(tmp: str, pstrip: int, three_way: bool, extra_flags: str = "") -> subprocess.CompletedProcess:
    t = f"git apply {'-3' if three_way else ''} -p{pstrip} --whitespace=fix {extra_flags} {tmp}".strip()
    return subprocess.run(t, shell=True, text=True, capture_output=True)

def _git_apply_matrix(tmp: str, uses_ab: bool) -> List[Tuple[int, bool, str]]:
    pseq = [1,0] if uses_ab else [0,1]
    combos: List[Tuple[int, bool, str]] = []
    for p in pseq:
        combos.append((p, True, ""))   # 3-way
        combos.append((p, False, ""))  # normal
        combos.append((p, False, "--ignore-whitespace"))
        combos.append((p, False, "--inaccurate-eof"))
        combos.append((p, False, "--unidiff-zero"))
        combos.append((p, False, "--ignore-whitespace --inaccurate-eof"))
        combos.append((p, False, "--reject --ignore-whitespace"))
        combos.append((p, False, "--reject --unidiff-zero"))
    return combos

def _make_zero_context(patch_text: str) -> str:
    out=[]
    for ln in patch_text.splitlines():
        if ln.startswith("@@"):
            ln = re.sub(r"(@@\s+-\d+)(?:,\d+)?(\s+\+\d+)(?:,\d+)?(\s+@@.*)", r"\1,0\2,0\3", ln)
            out.append(ln)
        elif ln.startswith(" "):
            continue
        else:
            out.append(ln)
    z = "\n".join(out)
    if not z.endswith("\n"): z += "\n"
    return z

def _gnu_patch_apply(tmp: str, p: int, rej_path: str) -> subprocess.CompletedProcess:
    # Wichtig: keine .orig Backups mehr
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

    # 2) Zero-Context git apply
    ztmp = f".github/codex/_solver_sec_{idx}_zc.patch"
    Path(ztmp).write_text(_make_zero_context(sec_text), encoding="utf-8")
    for p in ([1,0] if uses_ab else [0,1]):
        pr = _git_apply_try(ztmp, p, False, "--unidiff-zero --ignore-whitespace")
        if pr.returncode == 0:
            return True

    # 3) GNU patch (keine Backups)
    for pp in (1, 0):
        rej = f".github/codex/_solver_sec_{idx}.rej"
        pr = _gnu_patch_apply(tmp, pp, rej)
        if pr.returncode == 0:
            return True

    return False

# ---------- Direct reconstruction (new-file / forced write) ----------
_NEWFILE_HEADER_RE = re.compile(r"^--- /dev/null\s*\n\+\+\+ b/(\S+)\s*$", re.M)
_FILE_HEADER_RE = re.compile(r"^--- a/(\S+)\s*\n\+\+\+ b/(\S+)\s*$", re.M)

def _reconstruct_new_file_from_section(sec_text: str) -> Optional[Tuple[str,str]]:
    m = _NEWFILE_HEADER_RE.search(sec_text)
    if not m: return None
    path = m.group(1)
    content_lines=[]
    capture=False
    for ln in sec_text.splitlines():
        if ln.startswith("@@"): capture=True; continue
        if not capture: continue
        if ln.startswith("+") and not ln.startswith("+++ "):
            content_lines.append(ln[1:])
        elif ln.startswith("-") or ln.startswith(" "):
            # ignore removed/context
            continue
    return path, ("\n".join(content_lines) + ("\n" if content_lines and not content_lines[-1].endswith("\n") else ""))

def _ensure_path_dir(p: str):
    Path(p).parent.mkdir(parents=True, exist_ok=True)

def _verify_targets_changed(expect_paths: List[str]) -> Tuple[List[str], List[str]]:
    changed = [l.strip() for l in sh("git diff --name-only").splitlines()]
    created = [p for p in expect_paths if not Path(p).exists() and p.startswith("app/")]  # hint
    actually = set(changed) | set([p for p in expect_paths if Path(p).exists()])
    missing = [p for p in expect_paths if p not in actually]
    return sorted(list(actually)), missing

# ---------- OpenAI diff ----------
def openai_diff(contextmap: str, docs: str, target_paths: List[str]) -> str:
    from openai import OpenAI
    api_key = os.environ.get("OPENAI_API_KEY")
    if not api_key: raise RuntimeError("OPENAI_API_KEY not set")
    client = OpenAI(api_key=api_key, base_url=(os.environ.get("OPENAI_BASE_URL") or "https://api.openai.com/v1"))
    model = os.environ.get("OPENAI_MODEL_DEFAULT","gpt-5")
    effort = os.environ.get("OPENAI_REASONING_EFFORT","high")
    target_preview = "\n".join(sorted(target_paths))[:8000]
    SYSTEM = (
        "You are an expert Android/Kotlin/Gradle engineer. "
        "Generate ONE unified diff that applies at repo root; prefer a/b prefixes (-p1). "
        "Headers: 'diff --git', 'index', '--- a/...', '+++ b/...', '@@'. "
        "New files must use '/dev/null' header. LF only; trailing newline."
    )
    USER = (
        f"ContextMap:\n{contextmap}\n\n"
        f"Docs:\n{docs or '(no docs present)'}\n\n"
        f"Targets:\n{target_preview}\n\n"
        "Output: ONE unified diff, no prose."
    )
    resp = client.responses.create(
        model=model,
        input=[{"role":"system","content":SYSTEM},{"role":"user","content":USER}],
        reasoning={"effort": effort},
    )
    return getattr(resp, "output_text", "")

# ---------- Build dispatch ----------
def default_branch() -> str: return gh_api("GET", f"/repos/{repo()}").get("default_branch","main")
def dispatch_build(workflow_ident: str, ref_branch: str, inputs: dict | None) -> str:
    gh_api("POST", f"/repos/{repo()}/actions/workflows/{workflow_ident}/dispatches", {"ref": ref_branch, "inputs": inputs or {}})
    return time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
def wait_build_result(workflow_ident: str, ref_branch: str, since_iso: str, timeout_s=1800) -> dict:
    base=f"/repos/{repo()}/actions/workflows/{workflow_ident}/runs"; t0=time.time()
    while True:
        try:
            runs=gh_api("GET", f"{base}?event=workflow_dispatch&branch={ref_branch}")
            arr=runs.get("workflow_runs",[])
            cand=arr[0] if arr else {}
            if cand and cand.get("created_at","")>=since_iso:
                rid=cand.get("id")
                while True:
                    run=gh_api("GET", f"/repos/{repo()}/actions/runs/{rid}")
                    if run.get("status")=="completed": return run
                    if time.time()-t0>timeout_s: return run
                    time.sleep(6)
        except Exception:
            if time.time()-t0>timeout_s: return {}
        if time.time()-t0>timeout_s: return {}
        time.sleep(3)

# ---------- Main ----------
def main():
    ensure_repo_cwd()
    num = issue_number()
    if not num: print("::error::No issue number"); sys.exit(1)
    labels = get_labels(num)
    if "contextmap-ready" not in labels:
        print("::notice::No 'contextmap-ready' label"); sys.exit(0)

    cm = fetch_contextmap_comment(num)
    if not cm or not cm.strip().startswith("### contextmap-ready"):
        print("::notice::No contextmap comment"); sys.exit(0)

    docs=[]
    for n in ["AGENTS.md","ARCHITECTURE_OVERVIEW.md","ROADMAP.md","CHANGELOG.md"]:
        if Path(n).exists():
            try: docs.append(f"\n--- {n} ---\n"+Path(n).read_text(encoding="utf-8", errors="replace"))
            except Exception: pass
    docs_txt="".join(docs)
    all_files=ls_files()
    seeds = parse_affected_modules(cm, all_files)
    if not seeds:
        add_label(num, "solver-error"); post_comment(num, "❌ Solver: Keine gültigen Module in der ContextMap gefunden."); sys.exit(1)
    targets = expand_dependencies(seeds, all_files, max_extra=80)

    base = default_branch()
    sh(f"git fetch origin {base}", check=False)
    sh(f"git checkout -B codex/solve-{int(time.time())} origin/{base}")
    branch = sh("git rev-parse --abbrev-ref HEAD").strip()

    # 1) Diff generieren und SANITIZEN
    try: raw = openai_diff(cm, docs_txt, targets)
    except Exception as e:
        add_label(num, "solver-error"); post_comment(num, f"❌ Solver: OpenAI-Fehler\n```\n{e}\n```"); sys.exit(1)
    patch = sanitize_patch(raw)
    if "diff --git " not in patch:
        add_label(num, "solver-error"); post_comment(num, f"❌ Solver: Kein gültiger Diff\n```\n{patch[:1200]}\n```"); sys.exit(1)

    # Hinweis, aber kein Blocker
    try: PatchSet.from_string(patch)
    except (UnidiffParseError, Exception) as e:
        post_comment(num, f"ℹ️ Solver: Unidiff warning: `{type(e).__name__}: {e}` – nutze Fallbacks.")

    # 2) Erst Whole git apply; DANN section-wise (kein globaler GNU patch!)
    ok, info = whole_git_apply(patch)
    if not ok:
        post_comment(num, f"ℹ️ Whole-Apply scheiterte, wechsle auf section-wise.\n```\n{info}\n```")
        sections = _split_sections(patch)
        if not sections:
            add_label(num, "solver-error"); post_comment(num, "❌ Solver: Keine diff --git Sections gefunden."); sys.exit(1)
        expect_paths = [hdr.replace("b/","",1) if hdr.startswith("b/") else hdr for hdr in _targets_from_sections(sections)]
        applied_any=False
        for i, sec in enumerate(sections, 1):
            if apply_section(sec, i): applied_any=True
        if not applied_any:
            add_label(num, "solver-error"); post_comment(num, "❌ Solver: Section-wise Apply fehlgeschlagen (alle Fallbacks)."); sys.exit(1)
    else:
        post_comment(num, f"✅ Whole-Apply OK (`{info}`)")

    # 3) Verifikation + Rekonstruktion neuer Dateien bei Bedarf
    sections = _split_sections(patch)
    expect_paths = [hdr.replace("b/","",1) if hdr.startswith("b/") else hdr for hdr in _targets_from_sections(sections)]
    actually, missing = _verify_targets_changed(expect_paths)

    # Für neue Dateien ggf. direkt aus Section konstruieren
    rebuilt=[]
    for i, sec in enumerate(sections, 1):
        nf = _reconstruct_new_file_from_section(sec)
        if nf:
            path, content = nf
            if not Path(path).exists():
                _ensure_path_dir(path)
                Path(path).write_text(content, encoding="utf-8")
                rebuilt.append(path)
    if rebuilt:
        sh("git add -A")
        sh("git commit -m 'codex: create missing files from diff (auto)'")

    status = sh("git status --porcelain")
    if not status.strip():
        add_label(num, "solver-error")
        post_comment(num, "❌ Solver: Keine Änderungen im Working Tree nach Apply/Rebuild.")
        sys.exit(1)

    sh("git add -A")
    sh("git commit -m 'codex: solver apply (issue #{})'".format(num))
    sh("git push --set-upstream origin HEAD")

    pr = gh_api("POST", f"/repos/{repo()}/pulls", {
        "title": "codex: solver changes",
        "head": branch,
        "base": base,
        "body": "Automatisch erzeugte Änderungen basierend auf der ContextMap. (issue #{})".format(num)
    })
    pr_num = pr.get("number"); pr_url = pr.get("html_url")
    post_comment(num, f"🔧 PR erstellt: #{pr_num} — {pr_url}")

    # optional Build
    wf = os.environ.get("SOLVER_BUILD_WORKFLOW", "release-apk.yml")
    try:
        since = dispatch_build(wf, branch, {"build_type":"debug", "issue": str(num)})
        run = wait_build_result(wf, branch, since, timeout_s=1800)
        concl = (run or {}).get("conclusion","")
        if concl == "success":
            remove_label(num, "contextmap-ready"); add_label(num, "solver-done")
            post_comment(num, "✅ Build erfolgreich – Label `solver-done` gesetzt.")
        else:
            add_label(num, "solver-error")
            post_comment(num, "❌ Build fehlgeschlagen – Label `solver-error` gesetzt (Bot 3 wird reagieren).")
    except Exception as e:
        add_label(num, "solver-error"); post_comment(num, f"❌ Build-Dispatch fehlgeschlagen\n```\n{e}\n```"); sys.exit(1)

if __name__ == "__main__":
    try: main()
    except SystemExit: raise
    except Exception as e:
        try:
            num = issue_number()
            if num: add_label(num, "solver-error")
        except Exception: pass
        print("::error::Unexpected solver error:", e); sys.exit(1)

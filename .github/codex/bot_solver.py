#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
Bot 2 - Solver (Code-Umsetzung)
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

def repo() -> str:
    return os.environ["GITHUB_REPOSITORY"]

def event() -> dict:
    return json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text(encoding="utf-8"))

def gh_api(method: str, path: str, payload: dict | None = None) -> dict:
    url = "https://api.github.com{path}".format(path=path)
    headers = {"Authorization": "Bearer {t}".format(t=os.environ['GITHUB_TOKEN']),
               "Accept": "application/vnd.github+json"}
    r = requests.request(method, url, headers=headers, json=payload, timeout=45)
    if r.status_code >= 300:
        raise RuntimeError("GitHub API {m} {p} failed: {c} {t}".format(m=method, p=path, c=r.status_code, t=r.text[:500]))
    try:
        return r.json()
    except Exception:
        return {}

def gh_api_raw(method: str, url: str, allow_redirects=True):
    headers = {"Authorization": "Bearer {t}".format(t=os.environ['GITHUB_TOKEN']),
               "Accept": "application/vnd.github+json"}
    r = requests.request(method, url, headers=headers, allow_redirects=allow_redirects, stream=True, timeout=60)
    if r.status_code >= 300 and r.status_code not in (301, 302):
        raise RuntimeError("GitHub RAW {m} {u} failed: {c} {t}".format(m=method, u=url, c=r.status_code, t=r.text[:500]))
    return r

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

def list_issue_comments(num: int) -> List[dict]:
    res = gh_api("GET", "/repos/{r}/issues/{n}/comments".format(r=repo(), n=num))
    return res if isinstance(res, list) else []

def get_labels(num: int) -> Set[str]:
    issue = gh_api("GET", "/repos/{r}/issues/{n}".format(r=repo(), n=num))
    return {l.get("name","") for l in issue.get("labels",[])}

def add_label(num: int, label: str):
    gh_api("POST", "/repos/{r}/issues/{n}/labels".format(r=repo(), n=num), {"labels":[label]})

def remove_label(num: int, label: str):
    try:
        requests.delete(
            "https://api.github.com/repos/{r}/issues/{n}/labels/{lab}".format(r=repo(), n=num, lab=label),
            headers={"Authorization": "Bearer {t}".format(t=os.environ['GITHUB_TOKEN']),
                     "Accept": "application/vnd.github+json"},
            timeout=30
        )
    except Exception:
        pass

def post_comment(num: int, body: str):
    gh_api("POST", "/repos/{r}/issues/{n}/comments".format(r=repo(), n=num), {"body": body})

def sh(cmd: str, check=True) -> str:
    p = subprocess.run(cmd, shell=True, text=True, capture_output=True)
    if check and p.returncode != 0:
        msg = "cmd failed: {cmd}\nSTDOUT:\n{out}\nSTDERR:\n{err}".format(cmd=cmd, out=p.stdout, err=p.stderr)
        raise RuntimeError(msg)
    return (p.stdout or "")

def ensure_repo_cwd():
    ws = os.environ.get("GITHUB_WORKSPACE")
    if ws and Path(ws).exists():
        os.chdir(ws)
    else:
        try:
            root = sh("git rev-parse --show-toplevel").strip()
            if root: os.chdir(root)
        except Exception:
            pass
    try:
        sh("git config user.name 'codex-bot'", check=False)
        sh("git config user.email 'actions@users.noreply.github.com'", check=False)
        sh("git config core.autocrlf false", check=False)
        sh("git config apply.whitespace nowarn", check=False)
        sh("git config core.safecrlf false", check=False)
    except Exception:
        pass

def ls_files() -> List[str]:
    try:
        out = sh("git ls-files")
        return [l.strip() for l in out.splitlines() if l.strip()]
    except Exception:
        return []

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
        if g1 in sset: out.add(g1)
        if g2 in sset: out.add(g2)
        if m1 in sset: out.add(m1)
    pkg_root = "com.chris.m3usuite"
    extra=set()
    for f in [x for x in all_files if x.endswith(".kt") or x.endswith(".java")]:
        try:
            txt = Path(f).read_text(encoding="utf-8", errors="ignore")
        except Exception:
            continue
        if "import " in txt and pkg_root in txt:
            root = f.split("/")[0]
            if root not in out and any(x.startswith(root + "/") or x == root for x in sset):
                extra.add(root)
                if len(extra) >= max_extra: break
    out.update(extra)
    return sorted(out)

_BRACE_RE = re.compile(r"\{([^{}]*?)\s*=>\s*([^{}]*?)\}")

def _rewrite_brace_path(path: str) -> str:
    while True:
        m = _BRACE_RE.search(path)
        if not m: break
        right = m.group(2)
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
                lines[i] = "diff --git a/{a} b/{b}".format(a=a_path, b=b_path)
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
                    path = "{ab}{core}".format(ab=ab, core=core) if ab else core
                lines[i] = "{p} {path}".format(p=prefix, path=path)
    text = "\n".join(lines)
    if not text.endswith("\n"): text += "\n"
    return text

def sanitize_patch(raw: str) -> str:
    if not raw: return raw
    m = re.search(r"```(?:diff)?\s*(.*?)```", raw, re.S)
    if m: raw = m.group(1)
    raw = raw.replace("\r\n","\n").replace("\r","\n")
    pos = raw.find("diff --git ")
    if pos != -1:
        raw = raw[pos:]
    raw = _normalize_brace_renames(raw)
    if not raw.endswith("\n"): raw += "\n"
    return raw

def patch_uses_ab_prefix(patch_text: str) -> bool:
    return bool(re.search(r"^diff --git a/[\S]+ b/[\S]+", patch_text, re.M))

def _git_apply_try(tmp: str, pstrip: int, three_way: bool, extra_flags: str = "") -> subprocess.CompletedProcess:
    t = "git apply {three} -p{p} --whitespace=fix {extra} {tmp}".format(
        three="-3" if three_way else "", p=pstrip, extra=extra_flags, tmp=tmp).strip()
    return subprocess.run(t, shell=True, text=True, capture_output=True)

def whole_patch_fallback(patch_text: str) -> Tuple[bool, str]:
    tmp = ".github/codex/_solver_all.patch"
    Path(tmp).write_text(patch_text, encoding="utf-8")
    tries = []
    pseq = [1,0] if patch_uses_ab_prefix(patch_text) else [0,1]
    for p in pseq:
        tries.append((p, True, ""))
        tries.append((p, False, ""))
        tries.append((p, False, "--ignore-whitespace"))
        tries.append((p, False, "--reject --ignore-whitespace"))
    last_err = ""
    for p, three, extra in tries:
        pr = _git_apply_try(tmp, p, three, extra)
        if pr.returncode == 0:
            return True, "git apply {three} -p{p} --whitespace=fix {extra}".format(
                three="-3" if three else "", p=p, extra=extra).strip()
        last_err = (pr.stderr or pr.stdout or "")[:1200]
    return False, last_err

def _split_sections(patch_text: str) -> List[str]:
    sections=[]
    for m in re.finditer(r"^diff --git a/(\S+)\s+b/(\S+)\s*$", patch_text, re.M):
        start=m.start()
        next_m=re.search(r"^diff --git ", patch_text[m.end():], re.M)
        end=m.end() + (next_m.start() if next_m else len(patch_text)-m.end())
        sections.append(patch_text[start:end])
    return sections

def apply_patch_unidiff(patch_text: str) -> Tuple[List[str], List[str], List[str]]:
    sections = _split_sections(patch_text)
    if not sections:
        ok, info = whole_patch_fallback(patch_text)
        if not ok:
            raise RuntimeError("git apply failed: {i}".format(i=info))
        return (["<whole>"], [], [])

    applied, rejected, skipped = [], [], []
    for idx, body in enumerate(sections,1):
        tmp=".github/codex/_solver_sec_{i}.patch".format(i=idx)
        Path(tmp).write_text(body.replace("\r\n","\n").replace("\r","\n"), encoding="utf-8")
        pseq = [1,0] if patch_uses_ab_prefix(body) else [0,1]
        success=False
        for p in pseq:
            for three in (True, False):
                pr = _git_apply_try(tmp, p, three, "")
                if pr.returncode == 0:
                    applied.append("section_{i}".format(i=idx)); success=True; break
            if success: break
        if not success:
            for p in pseq:
                pr = _git_apply_try(tmp, p, False, "--ignore-whitespace")
                if pr.returncode == 0:
                    applied.append("section_{i}".format(i=idx)); success=True; break
        if not success:
            pr = _git_apply_try(tmp, pseq[0], False, "--reject --ignore-whitespace")
            if pr.returncode == 0:
                rejected.append("section_{i}".format(i=idx))
            else:
                skipped.append("section_{i}".format(i=idx))
    return applied, rejected, skipped

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
        "Generate ONE unified diff that applies at repo root (git apply -p1 preferred; a/b prefixes). "
        "Include proper headers: 'diff --git', 'index', '--- a/', '+++ b/', hunks '@@'. "
        "For new files add 'new file mode 100644' and '/dev/null' header. Avoid brace-rename shorthand."
    )
    USER = "ContextMap:\n{cm}\n\nDocs:\n{docs}\n\nTarget files/modules (primary and recursively inferred):\n{t}\n\nOutput rules:\n- Exactly ONE unified diff, starting with: diff --git a/... b/...\n- Include new files with 'new file mode 100644' and correct ---/+++ headers\n- No prose besides the diff.\n".format(
        cm=contextmap, docs=(docs or '(no docs present)'), t=target_preview
    )
    resp = client.responses.create(
        model=model,
        input=[{"role":"system","content":SYSTEM},{"role":"user","content":USER}],
        reasoning={"effort": effort},
    )
    return getattr(resp, "output_text", "")

def default_branch() -> str:
    return gh_api("GET", "/repos/{r}".format(r=repo())).get("default_branch","main")

def dispatch_build(workflow_ident: str, ref_branch: str, inputs: dict | None) -> str:
    gh_api("POST", "/repos/{r}/actions/workflows/{w}/dispatches".format(r=repo(), w=workflow_ident),
           {"ref": ref_branch, "inputs": inputs or {}})
    return time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())

def wait_build_result(workflow_ident: str, ref_branch: str, since_iso: str, timeout_s=1800) -> dict:
    base="/repos/{r}/actions/workflows/{w}/runs".format(r=repo(), w=workflow_ident)
    t0=time.time()
    while True:
        try:
            runs=gh_api("GET", "{base}?event=workflow_dispatch&branch={b}".format(base=base, b=ref_branch))
            arr=runs.get("workflow_runs",[])
            cand=arr[0] if arr else {}
            if cand and cand.get("created_at","")>=since_iso:
                rid=cand.get("id")
                while True:
                    run=gh_api("GET", "/repos/{r}/actions/runs/{id}".format(r=repo(), id=rid))
                    if run.get("status")=="completed": return run
                    if time.time()-t0>timeout_s: return run
                    time.sleep(6)
        except Exception:
            if time.time()-t0>timeout_s: return {}
        if time.time()-t0>timeout_s: return {}
        time.sleep(3)

def summarize_rejects(num: int):
    rej_files = glob.glob("**/*.rej", recursive=True)
    if not rej_files:
        return
    preview = []
    for rf in rej_files[:10]:
        try:
            txt = Path(rf).read_text(encoding="utf-8", errors="replace")
            preview.append("\n--- {rf} ---\n".format(rf=rf) + txt[:500])
        except Exception:
            preview.append("- {rf}".format(rf=rf))
    body = "⚠️ Solver: Einige Hunks wurden als `.rej` abgelegt (manuelle Nacharbeit nötig):\n" + "\n".join("- {r}".format(r=r) for r in rej_files[:50])
    if preview:
        body += "\n\n```diff\n" + "".join(preview) + "\n```"
    post_comment(num, body)

def main():
    ensure_repo_cwd()

    num = issue_number()
    if not num:
        print("::error::No issue number in event or inputs"); sys.exit(1)

    labels = get_labels(num)
    if "contextmap-ready" not in labels:
        print("::notice::No 'contextmap-ready' label; skipping"); sys.exit(0)

    cm = fetch_contextmap_comment(num)
    if not cm or not cm.strip().startswith("### contextmap-ready"):
        print("::notice::No contextmap comment; skipping"); sys.exit(0)

    docs=[]
    for name in ["AGENTS.md","ARCHITECTURE_OVERVIEW.md","ROADMAP.md","CHANGELOG.md"]:
        if Path(name).exists():
            try: docs.append("\n--- {n} ---\n".format(n=name) + Path(name).read_text(encoding="utf-8", errors="replace"))
            except Exception: pass
    docs_txt="".join(docs)

    all_files=ls_files()
    seeds = parse_affected_modules(cm, all_files)
    if not seeds:
        add_label(num, "solver-error")
        post_comment(num, "❌ Solver: Keine gültigen Module in der ContextMap gefunden.")
        sys.exit(1)

    targets = expand_dependencies(seeds, all_files, max_extra=80)

    base = default_branch()
    sh("git fetch origin {b}".format(b=base), check=False)
    sh("git checkout -B codex/solve-{ts} origin/{b}".format(ts=int(time.time()), b=base))
    branch = sh("git rev-parse --abbrev-ref HEAD").strip()

    try:
        raw = openai_diff(cm, docs_txt, targets)
    except Exception as e:
        add_label(num, "solver-error")
        post_comment(num, "❌ Solver: OpenAI-Fehler\n```\n{e}\n```".format(e=e))
        sys.exit(1)

    patch = sanitize_patch(raw)
    if "diff --git " not in patch:
        add_label(num, "solver-error")
        post_comment(num, "❌ Solver: Kein gültiger Diff\n```\n{p}\n```".format(p=patch[:1200]))
        sys.exit(1)

    try:
        PatchSet.from_string(patch)
    except (UnidiffParseError, Exception) as e:
        post_comment(num, "ℹ️ Solver: Unidiff parser warning: `{t}: {e}` – nutze robuste Apply-Fallbacks.".format(t=type(e).__name__, e=e))

    applied, rejected, skipped = [], [], []
    ok, info = whole_patch_fallback(patch)
    if ok:
        post_comment(num, "✅ Solver: Patch via Whole-Apply erfolgreich (`{i}`)".format(i=info))
    else:
        post_comment(num, "ℹ️ Solver: Whole-Apply scheiterte, versuche section-wise.\n```\n{info}\n```".format(info=info))
        try:
            applied, rejected, skipped = apply_patch_unidiff(patch)
        except Exception as e:
            add_label(num, "solver-error")
            post_comment(num, "❌ Solver: Patch-Apply fehlgeschlagen\n```\n{e}\n```".format(e=e))
            summarize_rejects(num)
            sys.exit(1)

    for f in glob.glob(".github/codex/_solver_sec_*.patch"):
        try: os.remove(f)
        except Exception: pass
    if os.path.exists(".github/codex/_solver_all.patch"):
        try: os.remove(".github/codex/_solver_all.patch")
        except Exception: pass

    status = sh("git status --porcelain")
    if not status.strip():
        add_label(num, "solver-error")
        post_comment(num, "❌ Solver: Keine Änderungen im Working Tree nach Apply.")
        summarize_rejects(num)
        sys.exit(1)

    sh("git add -A")
    sh("git commit -m 'codex: solver apply'")
    sh("git push --set-upstream origin HEAD")

    pr = gh_api("POST", "/repos/{r}/pulls".format(r=repo()), {
        "title": "codex: solver changes",
        "head": branch,
        "base": base,
        "body": "Automatisch erzeugte Änderungen basierend auf der ContextMap. (issue #{n})".format(n=num)
    })
    pr_num = pr.get("number"); pr_url = pr.get("html_url")
    post_comment(num, "🔧 PR erstellt: #{n} — {u}".format(n=pr_num, u=pr_url))

    wf = os.environ.get("SOLVER_BUILD_WORKFLOW", "release-apk.yml")
    try:
        since = dispatch_build(wf, branch, {"build_type":"debug", "issue": str(num)})
        run = wait_build_result(wf, branch, since, timeout_s=1800)
        concl = (run or {}).get("conclusion","")
        if concl == "success":
            remove_label(num, "contextmap-ready")
            add_label(num, "solver-done")
            post_comment(num, "✅ Build erfolgreich – Label `solver-done` gesetzt.")
        else:
            add_label(num, "solver-error")
            post_comment(num, "❌ Build fehlgeschlagen – Label `solver-error` gesetzt (Bot 3 wird reagieren).")
    except Exception as e:
        add_label(num, "solver-error")
        post_comment(num, "❌ Build-Dispatch fehlgeschlagen\n```\n{e}\n```".format(e=e))
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
        except Exception:
            pass
        print("::error::Unexpected solver error:", e)
        sys.exit(1)
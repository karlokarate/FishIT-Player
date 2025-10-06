# -*- coding: utf-8 -*-
"""
Bot 2 – Solver (robust, self-healing)

Fähigkeiten:
- Liest Context-Artefakte (.codex/context.json, .codex/context_full.md), falls vorhanden.
- Nutzt Diffs aus Issue/Kommentare; bei fehlerhaften/abgeschnittenen Diffs: mehrstufige Apply-Fallbacks.
- Scheitern die Apply-Versuche oder fehlen Dateien: Spec-to-File (Create/Replace) mit vollständigen Dateien.
- Directory-Targets werden rekursiv behandelt (alle .kt Dateien).
- Lint/Compile-Gate vor Push (spotless + compileDebugKotlin).
- PR-Erstellung; Build-Workflow-Dispatch und Statuskommentar.
- Keine harten Abbrüche bei Teilerfolgen; .rej wird gesammelt kommentiert.

Env:
- OPENAI_API_KEY, OPENAI_BASE_URL, OPENAI_MODEL_DEFAULT, OPENAI_REASONING_EFFORT
- GITHUB_TOKEN, GITHUB_REPOSITORY, GITHUB_EVENT_PATH, GH_EVENT_NAME/GITHUB_EVENT_NAME
- ISSUE_NUMBER (vom Workflow gesetzt)
- SOLVER_BUILD_WORKFLOW (optional; z.B. release-apk.yml)

Deps: openai, requests, unidiff
"""

from __future__ import annotations
import os, re, json, time, subprocess, sys, glob, textwrap
from pathlib import Path
from typing import List, Tuple, Optional, Set, Dict, Any

import requests
from unidiff import PatchSet
from unidiff.errors import UnidiffParseError

# ---------- Utility: Retry ----------
def _retry(fn, tries: int = 3, delay: float = 2.0):
    for i in range(tries):
        try:
            return fn()
        except Exception as e:
            if i == tries - 1:
                raise
            time.sleep(delay * (i + 1))

# ---------- GitHub helpers ----------
def repo() -> str:
    return os.environ["GITHUB_REPOSITORY"]

def event() -> dict:
    path = os.environ.get("GITHUB_EVENT_PATH") or ""
    return json.loads(Path(path).read_text(encoding="utf-8")) if path and Path(path).exists() else {}

def gh_api(method: str, path: str, payload: dict | None = None) -> dict:
    def _do():
        token = os.environ.get("GITHUB_TOKEN") or os.environ.get("INPUT_TOKEN") or ""
        url = f"https://api.github.com{path}"
        headers = {"Authorization": f"Bearer {token}", "Accept": "application/vnd.github+json"}
        r = requests.request(method, url, headers=headers, json=payload, timeout=60)
        if r.status_code >= 300:
            raise RuntimeError(f"GitHub API {method} {path} failed: {r.status_code} {r.text[:800]}")
        try:
            return r.json()
        except Exception:
            return {}
    return _retry(_do)

def issue_number() -> Optional[int]:
    env_issue = os.environ.get("ISSUE_NUMBER")
    if env_issue:
        try:
            return int(env_issue)
        except:
            pass
    ev = event()
    if "issue" in ev:
        n = (ev.get("issue") or {}).get("number")
        if n:
            return int(n)
    return None

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
    # Tolerante Git-Settings
    for k, v in [
        ("user.name", "codex-bot"),
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

def ls_files() -> List[str]:
    try:
        return [l.strip() for l in sh("git ls-files", check=False).splitlines() if l.strip()]
    except Exception:
        return []

# ---------- Context / Artifacts ----------
def read_context_json() -> Dict[str, Any]:
    p = Path(".codex/context.json")
    if p.exists():
        try:
            return json.loads(p.read_text(encoding="utf-8"))
        except Exception:
            return {}
    return {}

def read_context_full_md() -> str:
    p = Path(".codex/context_full.md")
    if p.exists():
        try:
            return p.read_text(encoding="utf-8")
        except Exception:
            return ""
    return ""

def fetch_contextmap_comment(num: int) -> Optional[str]:
    for c in list_issue_comments(num):
        b = (c.get("body") or "").strip()
        if b.startswith("### contextmap-ready"):
            return b
    return None

def extract_issue_raw_diff(num: int) -> str:
    """Concat issue + comments, return text from first 'diff --git' onward (if any)."""
    bodies = []
    issue = get_issue(num)
    bodies.append(issue.get("body") or "")
    for c in list_issue_comments(num):
        bodies.append(c.get("body") or "")
    raw = "\n\n".join(bodies)
    pos = raw.find("diff --git ")
    return raw[pos:] if pos != -1 else ""

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
        elif any(f.startswith(it.rstrip('/') + '/') for f in sset):
            existing.add(it.rstrip('/'))
    return sorted(existing)

# ---------- Patch normalization ----------
_VALID_LINE = re.compile(
    r"^(diff --git |index |new file mode |deleted file mode |old mode |new mode |"
    r"similarity index |rename (from|to) |Binary files |--- |\+\+\+ |@@ |[\+\- ]|\\ No newline at end of file)"
)

def _strip_code_fences(text: str) -> str:
    """Entfernt äußere ```...``` code fences (mit/ohne Sprache) ein Mal."""
    if not text:
        return text
    m = re.match(r"^```[a-zA-Z0-9_-]*\s*\n(.*?)\n```$", text.strip(), re.S)
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
        if re.match(r"^\s*\d+\.\s*:?\s*$", ln):  # numerierte leere Zeilen
            continue
        if re.match(r"^\s*[–—\-]\s*$", ln):     # reine Separator-Linien
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
        end = it[i + 1].start() if i + 1 < len(it) else len(patch_text)
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
            (p, True, ""),
            (p, False, ""),
            (p, False, "--ignore-whitespace"),
            (p, False, "--inaccurate-eof"),
            (p, False, "--unidiff-zero"),
            (p, False, "--ignore-whitespace --inaccurate-eof"),
            (p, False, "--reject --ignore-whitespace"),
            (p, False, "--reject --unidiff-zero"),
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
    for p, three, extra in _git_apply_matrix(tmp, uses_ab):
        pr = _git_apply_try(tmp, p, three, extra)
        if pr.returncode == 0:
            return True
    # Zero-Context-Versuch
    ztmp = f".github/codex/_solver_sec_{idx}_zc.patch"
    Path(ztmp).write_text(_make_zero_context(sec_text), encoding="utf-8")
    for p in ([1, 0] if uses_ab else [0, 1]):
        pr = _git_apply_try(ztmp, p, False, "--unidiff-zero --ignore-whitespace")
        if pr.returncode == 0:
            return True
    # GNU patch Fallback
    for pp in (1, 0):
        pr = _gnu_patch_apply(tmp, pp, f".github/codex/_solver_sec_{idx}.rej")
        if pr.returncode == 0:
            return True
    return False

# ---------- OpenAI helpers ----------
def _openai_client():
    from openai import OpenAI
    api_key = os.environ.get("OPENAI_API_KEY")
    if not api_key:
        raise RuntimeError("OPENAI_API_KEY not set")
    base = os.environ.get("OPENAI_BASE_URL") or "https://api.openai.com/v1"
    return OpenAI(api_key=api_key, base_url=base)

def _strip_outer_code_fences(txt: str) -> str:
    if not txt:
        return txt
    m = re.match(r"^```[a-zA-Z0-9_-]*\s*\n(.*?)\n```$", txt.strip(), re.S)
    return (m.group(1) if m else txt)

def ai_generate_diff(contextmap: str, docs: str, target_paths: List[str]) -> str:
    client = _openai_client()
    model = os.environ.get("OPENAI_MODEL_DEFAULT", "gpt-5")
    effort = os.environ.get("OPENAI_REASONING_EFFORT", "high")
    target_preview = "\n".join(sorted(target_paths))[:8000]
    SYSTEM = (
        "You are an expert Android/Kotlin/Gradle engineer. "
        "Generate ONE unified diff at repo root. Use a/b prefixes. "
        "Headers: diff --git, index, --- a/..., +++ b/..., @@. "
        "New files must use /dev/null header. LF only; trailing newline. Output ONLY the diff."
    )
    USER = (
        f"ContextMap:\n{contextmap}\n\nDocs:\n{docs or '(no docs)'}\n\n"
        f"Targets:\n{target_preview}\n\n"
        "Output: ONE unified diff, no prose."
    )
    resp = _retry(lambda: client.responses.create(
        model=model,
        input=[{"role": "system", "content": SYSTEM}, {"role": "user", "content": USER}],
        reasoning={"effort": effort},
    ))
    out = getattr(resp, "output_text", "") or ""
    return sanitize_patch(_strip_outer_code_fences(out))

def ai_generate_full_file(path: str, repo_symbols: Dict[str, Any], context_full: str, summary_ctx: str) -> str:
    client = _openai_client()
    model = os.environ.get("OPENAI_MODEL_DEFAULT", "gpt-5")
    effort = os.environ.get("OPENAI_REASONING_EFFORT", "high")
    # Package ableiten
    pkg = ""
    if path.endswith(".kt") and "/src/main/java/" in path:
        pkg = path.split("/src/main/java/")[1]
        pkg = os.path.dirname(pkg).replace("/", ".")
        pkg = re.sub(r"[^a-zA-Z0-9_.]", "", pkg)

    symbols_preview = ""
    try:
        items = repo_symbols.get("items", [])[:120]
        symbols_preview = "\n".join(f"{it.get('kind')} {it.get('name')} ({it.get('package')}) @ {it.get('file')}" for it in items)
    except Exception:
        pass

    SYSTEM = (
        "You are an expert Kotlin/Android engineer. "
        "Produce a COMPLETE file content for the given path. "
        "If it's a Kotlin source, include the correct package line derived from the path. "
        "Honor repository conventions inferred from symbols. "
        "No placeholders, no ellipses. Output ONLY the file content (no code fences)."
    )
    USER = f"""Target path:
{path}

Inferred package (if Kotlin): {pkg or '(n/a)'}

Repository symbols (preview):
{symbols_preview or '(none)'}

Short Context:
{summary_ctx or '(none)'}

Full Context (excerpt):
{textwrap.shorten(context_full or '', width=8000, placeholder=' …')}

Constraints:
- File must compile against typical Android/Kotlin setup.
- If file is new, include necessary imports and minimal implementation as per context.
"""

    resp = _retry(lambda: client.responses.create(
        model=model,
        input=[{"role": "system", "content": SYSTEM}, {"role": "user", "content": USER}],
        reasoning={"effort": effort},
    ))
    out = getattr(resp, "output_text", "") or ""
    out = _strip_outer_code_fences(out)
    if not out.strip():
        raise RuntimeError("AI full-file generation returned empty content")
    return out

def ai_rewrite_full_file(path: str, current: str, repo_symbols: Dict[str, Any], context_full: str, summary_ctx: str) -> str:
    client = _openai_client()
    model = os.environ.get("OPENAI_MODEL_DEFAULT", "gpt-5")
    effort = os.environ.get("OPENAI_REASONING_EFFORT", "high")
    SYSTEM = (
        "You are an expert Kotlin/Android engineer. "
        "Rewrite the given file completely to satisfy the context (no partial diffs). "
        "Maintain public API compatibility if possible; otherwise, adjust per context. "
        "Output ONLY the new file content (no code fences)."
    )
    USER = f"""Path:
{path}

Current file content:
```kotlin
{current}
Symbols preview:
{textwrap.shorten(json.dumps(repo_symbols, ensure_ascii=False), width=6000, placeholder=' …')}

Short Context:
{summary_ctx or '(none)'}

Full Context (excerpt):
{textwrap.shorten(context_full or '', width=8000, placeholder=' …')}
"""
resp = _retry(lambda: client.responses.create(
model=model,
input=[{"role": "system", "content": SYSTEM}, {"role": "user", "content": USER}],
reasoning={"effort": effort},
))
out = getattr(resp, "output_text", "") or ""
out = _strip_outer_code_fences(out)
if not out.strip():
raise RuntimeError("AI rewrite returned empty content")
return out

---------- Build dispatch ----------

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
cands = [r for r in arr if r.get("head_branch") == ref_branch and r.get("created_at", "") >= since_iso]
cand = cands[0] if cands else {}
if cand:
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

---------- Summaries ----------

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
body = "⚠️ Solver: Einige Hunks wurden als .rej abgelegt (manuelle Nacharbeit möglich):\n" +
"\n".join(f"- {r}" for r in rej_files[:50])
if preview:
body += "\n\ndiff\n" + "".join(preview) + "\n"
post_comment(num, body)

---------- Solver strategy ----------

def choose_targets_from_context(ctx: Dict[str, Any], all_files: List[str]) -> Tuple[List[str], List[str]]:
"""
Liefert (create_paths, existing_paths) aus context.json + existence_map.
Fallback: Context-Kommentar-Module oder 'app'.
"""
create_paths: List[str] = []
existing_paths: List[str] = []
if ctx:
    mentioned = ctx.get("mentioned_paths") or []
    exists_map = ctx.get("mentioned_paths_exists") or {}
    for p in mentioned:
        if exists_map.get(p) is False:
            create_paths.append(p)
        elif exists_map.get(p) is True:
            existing_paths.append(p)
    if not mentioned:
        if any(f.startswith("app/") for f in all_files) or "app" in all_files:
            existing_paths.append("app")
else:
    cm = fetch_contextmap_comment(issue_number() or 0) or ""
    seeds = parse_affected_modules(cm, all_files)
    if seeds:
        existing_paths.extend(seeds)
    else:
        if any(f.startswith("app/") for f in all_files) or "app" in all_files:
            existing_paths.append("app")

# dedupe
def norm_unique(arr: List[str]) -> List[str]:
    seen = set(); out=[]
    for x in arr:
        if x not in seen:
            seen.add(x); out.append(x)
    return out

return norm_unique(create_paths), norm_unique(existing_paths)
def gather_repo_symbols(limit:int=1600) -> Dict[str, Any]:
"""
Leichtgewichtige Kotlin-Symbolsammlung für Prompts.
"""
files = ls_files()
symbols=[]; cnt=0
for f in files:
if not f.endswith(".kt"): continue
if "/build/" in f or "/generated/" in f: continue
if cnt >= limit: break
try:
t = Path(f).read_text(encoding="utf-8", errors="replace")
except Exception:
continue
pkg = re.search(r"^\s*package\s+([a-zA-Z0-9_.]+)", t, flags=re.MULTILINE)
pkgname = pkg.group(1) if pkg else ""
for line in t.splitlines():
line=line.strip()
m_class = re.match(r"(?:public\s+)?(?:data\s+)?class\s+([A-Za-z0-9_]+)", line)
m_obj = re.match(r"(?:public\s+)?object\s+([A-Za-z0-9_]+)", line)
m_comp = re.match(r"@Composable\s+fun\s+([A-Za-z0-9_]+)(", line)
m_fun = re.match(r"(?:public\s+)?fun\s+([A-Za-z0-9_]+)(", line)
if m_class:
symbols.append({"file":f,"package":pkgname,"kind":"class","name":m_class.group(1)})
elif m_obj:
symbols.append({"file":f,"package":pkgname,"kind":"object","name":m_obj.group(1)})
elif m_comp:
symbols.append({"file":f,"package":pkgname,"kind":"composable","name":m_comp.group(1)})
elif m_fun:
symbols.append({"file":f,"package":pkgname,"kind":"fun","name":m_fun.group(1)})
cnt += 1
if cnt >= limit:
break
return {"count": len(symbols), "items": symbols}

def _write_text_file(path: str, content: str):
Path(os.path.dirname(path)).mkdir(parents=True, exist_ok=True)
Path(path).write_text(content, encoding="utf-8")

def apply_or_spec_to_file(num: int, patch: str, ctx_json: Dict[str, Any], cm_comment: str):
"""
1) Versuche Patch anzuwenden (whole → section → zero-context → GNU).
2) Scheitert dies/ist kein Patch vorhanden:
- Create-Targets: komplette neue Dateien generieren.
- Existing-Targets: komplette Dateien ersetzen.
- Directory-Targets: rekursiv alle .kt-Dateien ersetzen.
"""
base = default_branch()
sh(f"git fetch origin {base}", check=False)
sh(f"git checkout -B codex/solve-{int(time.time())} origin/{base}")
branch = sh("git rev-parse --abbrev-ref HEAD").strip()
ok, info = whole_git_apply(patch) if patch else (False, "no patch text")
applied_any = False

if ok:
    post_comment(num, f"✅ Solver: Patch via Whole-Apply erfolgreich (`{info}`)")
    applied_any = True
else:
    sections = _split_sections(patch) if patch else []
    if patch:
        post_comment(num, f"ℹ️ Solver: Whole-Apply scheiterte, section-wise Fallback.\n```\n{info}\n```")
    for i, sec in enumerate(sections, 1):
        if apply_section(sec, i):
            applied_any = True
    if patch and not applied_any:
        post_comment(num, "ℹ️ Solver: Section-wise Apply fehlgeschlagen – wechsle zu Spec-to-File.")

    # ---- Spec-to-File Pfad ----
    repo_syms = gather_repo_symbols()
    context_full = read_context_full_md()
    summary_ctx = (ctx_json.get("contextmap_markdown_summary") if ctx_json else cm_comment) or ""
    create_paths, exist_targets = choose_targets_from_context(ctx_json, ls_files())

    # 1) Neue Dateien erzeugen
    for pth in create_paths:
        try:
            if Path(pth).exists():
                continue
            gen = ai_generate_full_file(pth, repo_syms, context_full, summary_ctx)
            _write_text_file(pth, gen)
            post_comment(num, f"🆕 Datei erzeugt (Spec-to-File): `{pth}`")
            applied_any = True
        except Exception as e:
            post_comment(num, f"⚠️ Konnte Datei nicht erzeugen `{pth}`:\n```\n{e}\n```")

    # 2) Bestehende Dateien: ersetzen (nur konkrete Dateien)
    for target in exist_targets:
        p = Path(target)
        if p.is_dir():
            continue
        if not (target.endswith(".kt") or target.endswith(".kts") or target.endswith(".gradle")):
            continue
        try:
            cur = p.read_text(encoding="utf-8", errors="replace") if p.exists() else ""
        except Exception:
            cur = ""
        try:
            newc = ai_rewrite_full_file(target, cur, repo_syms, context_full, summary_ctx)
            _write_text_file(target, newc)
            post_comment(num, f"🔁 Datei ersetzt (Spec-to-File): `{target}`")
            applied_any = True
        except Exception as e:
            post_comment(num, f"⚠️ Konnte Datei nicht ersetzen `{target}`:\n```\n{e}\n```")

    # 2b) Directory-Targets: rekursiv alle .kt verarbeiten
    for target in exist_targets:
        p = Path(target)
        if p.is_dir():
            for f in p.rglob("*.kt"):
                try:
                    cur = f.read_text(encoding="utf-8", errors="replace")
                    newc = ai_rewrite_full_file(f.as_posix(), cur, repo_syms, context_full, summary_ctx)
                    f.write_text(newc, encoding="utf-8")
                    post_comment(num, f"🔁 Datei ersetzt (Dir-Target): `{f.as_posix()}`")
                    applied_any = True
                except Exception as e:
                    post_comment(num, f"⚠️ Ersetzen fehlgeschlagen `{f.as_posix()}`:\n```\n{e}\n```")

    # 3) Wenn gar nichts angewendet und kein Patch existierte → generiere Gesamt-Diff
    if not applied_any and not patch:
        docs = []
        for n in ["AGENTS.md", "ARCHITECTURE_OVERVIEW.md", "ROADMAP.md", "CHANGELOG.md"]:
            if Path(n).exists():
                try:
                    docs.append(f"\n--- {n} ---\n" + Path(n).read_text(encoding="utf-8", errors="replace"))
                except Exception:
                    pass
        docs_txt = "".join(docs)
        all_files = ls_files()
        seeds = parse_affected_modules(cm_comment or "", all_files) if cm_comment else (["app"] if ("app" in all_files or any(p.startswith("app/") for p in all_files)) else [])
        targets = seeds or all_files[:80]
        try:
            raw_ai = ai_generate_diff(cm_comment or "(no contextmap; artifact missing)", docs_txt, targets)
            new_patch = sanitize_patch(raw_ai)
            ok2, info2 = whole_git_apply(new_patch)
            if not ok2:
                post_comment(num, f"⚠️ Generierter Diff konnte nicht vollständig angewendet werden.\n```\n{info2}\n```")
            else:
                post_comment(num, f"✅ Generierter Diff angewendet (`{info2}`)")
                applied_any = True
        except Exception as e:
            post_comment(num, f"⚠️ AI-Diff-Erzeugung fehlgeschlagen:\n```\n{e}\n```")

# Keine Änderungen?
status = sh("git status --porcelain", check=False)
if not status.strip():
    add_label(num, "solver-error")
    post_comment(num, "❌ Solver: Keine Änderungen im Working Tree nach Verarbeitung.")
    summarize_rejects(num)
    return None

# Auto-Format & Schnell-Compile (best effort)
try:
    sh("./gradlew -q :app:spotlessApply", check=False)
    sh("./gradlew -q :app:compileDebugKotlin --no-daemon", check=False)
except Exception:
    pass

# Nach evtl. Formatierung erneut prüfen
status = sh("git status --porcelain", check=False)
if not status.strip():
    add_label(num, "solver-error")
    post_comment(num, "❌ Solver: Nach Formatierung keine Änderungen übrig.")
    summarize_rejects(num)
    return None

# Commit & Push
sh("git add -A", check=False)
# Leeren Commit vermeiden
staged_diff = subprocess.run("git diff --cached --quiet", shell=True)
if staged_diff.returncode == 0:
    add_label(num, "solver-error")
    post_comment(num, "❌ Solver: Keine Änderungen zum Commit (staged diff leer).")
    summarize_rejects(num)
    return None

sh(f"git commit -m 'codex: solver changes (issue #{num})'", check=False)
sh("git push --set-upstream origin HEAD", check=False)

pr = gh_api("POST", f"/repos/{repo()}/pulls", {
    "title": f"codex: solver changes (issue #{num})",
    "head": sh('git rev-parse --abbrev-ref HEAD').strip(),
    "base": default_branch(),
    "body": (
        f"Automatisch erzeugte Änderungen basierend auf ContextMap/Artefakt (issue #{num}).\n\n"
        f"- Kontext: `.codex/context_full.md` / `.codex/context.json`\n"
        f"- Modus: Patch → Fallbacks → Spec-to-File\n"
        f"- Bitte nach Merge Branch löschen (`codex/solve-*`)."
    )
})
pr_num = pr.get("number"); pr_url = pr.get("html_url")
post_comment(num, f"🔧 PR erstellt: #{pr_num} — {pr_url}")

return pr_url
---------- Main ----------

def dispatch_triage(issue_num: int):
try:
wfs = gh_api("GET", f"/repos/{repo()}/actions/workflows")
names = { (w.get("path") or "").split("/")[-1] for w in (wfs.get("workflows") or []) }
target = "codex-triage.yml"
if target in names:
gh_api("POST", f"/repos/{repo()}/actions/workflows/{target}/dispatches",
{"ref": default_branch(), "inputs": {"issue": str(issue_num)}})
else:
print("::notice::No codex-triage.yml found; skipping triage dispatch")
except Exception as e:
print(f"::warning::Failed to dispatch triage workflow: {e}")

def main():
ensure_repo_cwd()
num = issue_number()
if not num:
print("::error::No issue number")
sys.exit(1)
ev_name = (os.environ.get("GH_EVENT_NAME") or os.environ.get("GITHUB_EVENT_NAME") or "")
if ev_name != "workflow_dispatch":
    labels = get_labels(num)
    if "contextmap-ready" not in labels:
        print("::notice::No 'contextmap-ready' label; skipping")
        sys.exit(0)

# Kontext laden
ctx_json = read_context_json()
cm_comment = fetch_contextmap_comment(num) or ""
# Diff ggf. aus Issue lesen
raw_issue_diff = extract_issue_raw_diff(num)
patch = sanitize_patch(raw_issue_diff) if raw_issue_diff else ""

# Parser-Warnung tolerieren → Fallbacks nutzen
if patch and "diff --git " in patch:
    try:
        PatchSet.from_string(patch)
    except (UnidiffParseError, Exception) as e:
        post_comment(num, f"ℹ️ Solver: Unidiff Parser Warning: `{type(e).__name__}: {e}` – nutze Fallbacks.")

else:
    patch = ""  # erzwinge Spec-to-File/AI-Diff-Pfad

# Anwenden oder Spec-to-File
pr_url = apply_or_spec_to_file(num, patch, ctx_json, cm_comment)
if pr_url is None:
    add_label(num, "solver-error")
    summarize_rejects(num)
    dispatch_triage(num)
    sys.exit(1)

# Optionaler Build
try:
    wf = os.environ.get("SOLVER_BUILD_WORKFLOW", "release-apk.yml")
    branch = sh('git rev-parse --abbrev-ref HEAD').strip()
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
if name == "main":
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

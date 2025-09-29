#!/usr/bin/env python3
# -*- coding: utf-8 -*-

""
Codex Bot: Erzeugt AI-Patches (Unified Diff) unter Nutzung deiner Repo-Doku (AGENTS.md, ARCHITECTURE_OVERVIEW.md,
ROADMAP.md, CHANGELOG.md …), baut relevanzbasierten Code-Kontext, wendet Patches ROBUST an (3-way / per Sektion / --reject),
pusht Branch, erstellt PR, labelt optional und kann im SPEC-FIRST-Modus erst eine Änderungs-Spezifikation liefern.

Slash-Command-Beispiele:
  /codex -m gpt-5 --reason high Fix Fokusnavigation in Staffeln, schreibe UI-Tests
  /codex --paths app/src,core/ui Refactor MediaSession State Machine
  /codex --verify Erzeuge Unit-Tests für PlayerService (Errors/Buffering)
  /codex --dry Nur Patch erzeugen und als Kommentar anhängen (kein Apply)
  /codex --spec Schreibe erst eine Spezifikation (Design-Diff/Tasks), kein Code
  /codex --apply Spezifikation ist freigegeben → jetzt Code-Diff erzeugen & anwenden
  /codex --paths . Nur Repo-Tree posten (kein Patch)
"""

import json
import os
import re
import shlex
import subprocess
import sys
import time
import difflib
from pathlib import Path
from datetime import datetime

import requests
from unidiff import PatchSet

# ------------------------ Konfiguration ------------------------

DOC_CANDIDATES = [
    "AGENTS.md", "ARCHITECTURE_OVERVIEW.md", "ROADMAP.md", "CHANGELOG.md",
    "CONTRIBUTING.md", "CODE_OF_CONDUCT.md"
]

# Prompt-Budgets (ca. chars; grob ~ 4 chars ≈ 1 Token)
MAX_DOC_CHARS     = 24000
MAX_CONTEXT_CHARS = 220000
MAX_USER_CHARS    = 4000

# Safeguards
FORBIDDEN_PATHS = [".github/workflows/"]           # nur mit --allow-ci ändern
MAX_PATCH_LINES = 3000                             # zu große Patches blocken, außer --force

# ------------------------ Shell / Git / HTTP Utils ------------------------

def sh(cmd: str, check: bool = True) -> str:
    res = subprocess.run(cmd, shell=True, text=True, capture_output=True)
    if check and res.returncode != 0:
        raise RuntimeError(f"cmd failed: {cmd}\nSTDOUT:\n{res.stdout}\nSTDERR:\n{res.stderr}")
    return res.stdout.strip()

def gh_api(method: str, path: str, payload=None):
    url = f"https://api.github.com{path}"
    headers = {
        "Authorization": f"Bearer {os.environ['GITHUB_TOKEN']}",
        "Accept": "application/vnd.github+json"
    }
    resp = requests.request(method, url, headers=headers, json=payload)
    if resp.status_code >= 300:
        raise RuntimeError(f"GitHub API {method} {path} failed: {resp.status_code} {resp.text}")
    return resp.json() if resp.text else {}

def comment_reply(body: str):
    try:
        event = json.load(open(os.environ["GITHUB_EVENT_PATH"], "r", encoding="utf-8"))
    except Exception:
        return
    repo = os.environ.get("GITHUB_REPOSITORY", "")
    issue_number = None
    if "issue" in event and "number" in event["issue"]:
        issue_number = event["issue"]["number"]
    elif "pull_request" in event and "number" in event["pull_request"]:
        issue_number = event["pull_request"]["number"]
    if issue_number is None:
        return
    gh_api("POST", f"/repos/{repo}/issues/{issue_number}/comments", {"body": body})

def read_comment() -> str:
    evname = os.environ.get("GH_EVENT_NAME", "")
    if evname == "workflow_dispatch":
        return os.environ.get("DISPATCH_COMMENT", "").strip()
    try:
        event = json.load(open(os.environ["GITHUB_EVENT_PATH"], "r", encoding="utf-8"))
    except Exception:
        return ""
    if evname == "issue_comment" and "comment" in event and "body" in event["comment"]:
        return (event["comment"]["body"] or "").strip()
    if evname == "pull_request_review_comment" and "comment" in event and "body" in event["comment"]:
        return (event["comment"]["body"] or "").strip()
    if evname == "issues" and "issue" in event and "body" in event["issue"]:
        return (event["issue"]["body"] or "").strip()
    return ""

def actor_handle() -> str:
    evname = os.environ.get("GH_EVENT_NAME", "")
    if evname == "workflow_dispatch":
        repo = os.environ.get("GITHUB_REPOSITORY", "")
        return repo.split("/")[0] if "/" in repo else repo
    try:
        event = json.load(open(os.environ["GITHUB_EVENT_PATH"], "r", encoding="utf-8"))
        return event.get("sender", {}).get("login", "")
    except Exception:
        return ""

def is_allowed(actor: str) -> bool:
    allowlist = os.environ.get("CODEX_ALLOWLIST", "").strip()
    if allowlist:
        return actor in [a.strip() for a in allowlist.split(",") if a.strip()]
    repo = os.environ.get("GITHUB_REPOSITORY", "")
    try:
        perms = gh_api("GET", f"/repos/{repo}/collaborators/{actor}/permission")
        return perms.get("permission") in ("write", "maintain", "admin")
    except Exception:
        return False

# ------------------------ Repo Index / Tree ------------------------

IGNORES = {
    ".git", ".gradle", ".idea", "build", "app/build", "gradle/build",
    ".github/codex/codex.patch", ".github/codex/tree.txt"
}

def _is_ignored(path: Path) -> bool:
    p = str(path).replace("\\", "/")
    if any(part.startswith(".git") for part in p.split("/")):
        return True
    if any(p == ig or p.startswith(ig + "/") for ig in IGNORES):
        return True
    return False

def build_repo_index():
    """Return (files_list[pathlib.Path], tree_str_compact, tree_str_full)."""
    try:
        files = [Path(p) for p in sh("git ls-files").splitlines() if p.strip()]
    except Exception:
        files = [p for p in Path(".").rglob("*") if p.is_file()]
    files = [p for p in files if not _is_ignored(p)]

    full_list = "\n".join(sorted(str(p).replace("\\", "/") for p in files))

    def compress(path_strs, max_depth=4):
        out = []
        for s in sorted(path_strs):
            parts = s.split("/")
            out.append("/".join(parts[:max_depth]) + ("/…" if len(parts) > max_depth else ""))
        # dedup
        dedup, last = [], None
        for line in out:
            if line != last:
                dedup.append(line); last = line
        return "\n".join(dedup)

    compact = compress((str(p).replace("\\", "/") for p in files), max_depth=4)

    os.makedirs(".github/codex", exist_ok=True)
    with open(".github/codex/tree.txt", "w", encoding="utf-8") as f:
        f.write(full_list)

    return files, compact, full_list

def post_repo_tree_comment(compact_tree: str):
    comment_reply(
        "📁 **Repo-Verzeichnisbaum (kompakt, Tiefe ≤ 4)**\n"
        "```\n" + compact_tree[:6000] + "\n```\n"
        "Vollständige Liste wurde nach `.github/codex/tree.txt` geschrieben."
    )

# ------------------------ Prompt-Budget & Doku ------------------------

def trim_to_chars(s: str, limit: int) -> str:
    if s is None:
        return ""
    if len(s) <= limit:
        return s
    return s[:limit]

def load_docs(max_chars: int = MAX_DOC_CHARS) -> str:
    blobs, remain = [], max_chars
    for name in DOC_CANDIDATES:
        if os.path.exists(name) and remain > 0:
            try:
                txt = Path(name).read_text(encoding="utf-8", errors="replace")
                cut = txt[:min(len(txt), remain)]
                blobs.append(f"\n--- {name} ---\n{cut}")
                remain -= len(cut)
            except Exception:
                pass
    return "".join(blobs)

# ------------------------ Relevanzbasierter Code-Kontext ------------------------

def top_relevant_files(instruction: str, all_files: list[str], k: int = 80):
    """Einfache Keyword-Relevanz: Wörter aus Instruction in Pfad + Datei-Header."""
    words = [w.lower() for w in re.findall(r"[A-Za-z_][A-Za-z0-9_]+", instruction) if len(w) >= 3]
    if not words:
        return all_files[:k]

    scores = []
    for f in all_files:
        score = 0
        path_l = f.lower()
        for w in words:
            if w in path_l:
                score += 2
        try:
            head = sh(f"sed -n '1,140p' {shlex.quote(f)} | tr '\\t' ' '", check=False).lower()
            for w in words:
                if w in head:
                    score += 1
        except Exception:
            pass
        scores.append((score, f))

    scores.sort(key=lambda x: x[0], reverse=True)
    res = [f for s, f in scores if s > 0][:k]
    return res or all_files[:k]

# ------------------------ OpenAI ------------------------

def _compute_base_url() -> str:
    raw = os.environ.get("OPENAI_BASE_URL", "").strip()
    if not raw:
        return "https://api.openai.com/v1"
    if not raw.startswith(("http://", "https://")):
        raw = "https://" + raw
    if not re.search(r"/v\d+/?$", raw):
        raw = raw.rstrip("/") + "/v1"
    return raw

def openai_generate(model: str, system_prompt: str, user_prompt: str, want_spec: bool = False) -> str:
    """Responses API bevorzugt; Fallback Chat Completions. Kein temperature-Param (Reasoning-Modelle)."""
    from openai import OpenAI

    client = OpenAI(api_key=os.environ["OPENAI_API_KEY"], base_url=_compute_base_url())

    effort = os.environ.get("OPENAI_REASONING_EFFORT", "high").lower()
    if effort not in ("high", "medium", "low"):
        effort = "high"

    def _try(fn, tries=3, base_sleep=2.0):
        last_err = None
        for i in range(tries):
            try:
                return fn()
            except Exception as e:
                last_err = e
                msg = str(e)
                if "429" in msg or "insufficient_quota" in msg or "Connection error" in msg:
                    time.sleep(base_sleep * (2 ** i)); continue
                raise
        raise last_err

    def _responses():
        resp = client.responses.create(
            model=model,
            input=[{"role": "system", "content": system_prompt},
                   {"role": "user",   "content": user_prompt}],
            reasoning={"effort": effort},
        )
        return getattr(resp, "output_text", "") or ""

    def _chat():
        cc = client.chat.completions.create(
            model=model,
            messages=[{"role": "system", "content": system_prompt},
                      {"role": "user",   "content": user_prompt}],
        )
        return cc.choices[0].message.content or ""

    try:
        return _try(_responses)
    except Exception as e:
        try:
            return _try(_chat)
        except Exception as ee:
            raise RuntimeError(f"OpenAI call failed: {e}\nFallback also failed: {ee}")

# ------------------------ Diff Sanitize / Parse / Remap ------------------------

def sanitize_patch_text(raw: str) -> str:
    """Strip code fences, cut prelude, normalize newlines, ensure trailing NL."""
    if not raw:
        return raw
    m = re.search(r"```(?:diff)?\s*(.*?)```", raw, re.S)
    if m:
        raw = m.group(1)
    i = raw.find("diff --git ")
    if i != -1:
        raw = raw[i:]
    raw = raw.replace("\r\n", "\n").replace("\r", "\n")
    if not raw.endswith("\n"):
        raw += "\n"
    lines = raw.split("\n")
    if not lines or not lines[0].startswith("diff --git "):
        for idx, ln in enumerate(lines):
            if ln.startswith("diff --git "):
                lines = lines[idx:]; break
        raw = "\n".join(lines)
    return raw

def parse_patch_sections(patch_text: str):
    sections = []
    for m in re.finditer(r"^diff --git a/(\S+)\s+b/(\S+)\s*$", patch_text, re.M):
        start = m.start()
        next_m = re.search(r"^diff --git ", patch_text[m.end():], re.M)
        end = m.end() + (next_m.start() if next_m else len(patch_text) - m.end())
        body = patch_text[start:end]
        oldp, newp = m.group(1), m.group(2)
        is_add = bool(re.search(r"(?m)^--- /dev/null\s*$", body))
        is_del = bool(re.search(r"(?m)^\+\+\+ /dev/null\s*$", body))
        is_rename = bool(re.search(r"(?m)^rename (from|to) ", body) or re.search(r"(?m)^similarity index ", body))
        sections.append({"old": oldp, "new": newp, "body": body, "is_add": is_add, "is_del": is_del, "is_rename": is_rename})
    return sections

def best_match_path(target_path: str, repo_files):
    target = target_path.replace("\\", "/")
    tbase = os.path.basename(target)
    repo_norm = [str(p).replace("\\", "/") for p in repo_files]

    candidates = [p for p in repo_norm if os.path.basename(p) == tbase]
    if candidates:
        tparts = target.split("/")
        def score(p):
            parts = p.split("/")
            suf = 0
            for a, b in zip(reversed(tparts), reversed(parts)):
                if a == b: suf += 1
                else: break
            ratio = difflib.SequenceMatcher(a="/".join(tparts[-4:]), b="/".join(parts[-4:])).ratio()
            return (suf, ratio, -len(parts))
        return sorted(candidates, key=score, reverse=True)[0]

    scored = sorted(((difflib.SequenceMatcher(a=target, b=p).ratio(), p) for p in repo_norm), reverse=True)
    return scored[0][1] if scored and scored[0][0] >= 0.6 else None

def rewrite_section_paths(section: str, new_path: str):
    section = re.sub(r"^(diff --git a/)\S+(\s+b/)\S+", rf"\1{new_path}\2{new_path}", section, count=1, flags=re.M)
    section = re.sub(r"(?m)^(--- )a/\S+$", rf"\1a/{new_path}", section)
    section = re.sub(r"(?m)^(\+\+\+ )b/\S+$", rf"\1b/{new_path}", section)
    return section

def try_remap_patch(patch_text: str, repo_files):
    sections = parse_patch_sections(patch_text)
    if not sections:
        return patch_text, [], []

    remapped, unresolved, new_sections = [], [], []

    for s in sections:
        body, newp = s["body"], s["new"]
        if s["is_add"] or s["is_rename"]:
            new_sections.append(body); continue
        if os.path.exists(newp):
            new_sections.append(body); continue
        cand = best_match_path(newp, repo_files)
        if cand:
            new_sections.append(rewrite_section_paths(body, cand))
            remapped.append((newp, cand))
        else:
            unresolved.append(newp)

    return "".join(new_sections), remapped, unresolved

# ------------------------ Robust Section-wise Apply ------------------------

def try_apply_sections(patch_file: str, original_text: str) -> tuple[list[str], list[str], list[str]]:
    sections = parse_patch_sections(original_text)
    if not sections:
        sh(f"git apply -p0 --whitespace=fix {shlex.quote(patch_file)}")
        return (["<whole>"], [], [])

    applied, rejected, skipped = [], [], []

    for idx, sec in enumerate(sections, 1):
        tmp = f".github/codex/section_{idx}.patch"
        with open(tmp, "w", encoding="utf-8") as f:
            f.write(sec["body"])

        ok3 = subprocess.run(f"git apply --check -3 -p0 {shlex.quote(tmp)}",
                             shell=True, text=True, capture_output=True)
        if ok3.returncode == 0:
            sh(f"git apply -3 -p0 --whitespace=fix {shlex.quote(tmp)}")
            applied.append(sec["new"]); continue

        ok = subprocess.run(f"git apply --check -p0 {shlex.quote(tmp)}",
                            shell=True, text=True, capture_output=True)
        if ok.returncode == 0:
            sh(f"git apply -p0 --whitespace=fix {shlex.quote(tmp)}")
            applied.append(sec["new"]); continue

        with open(tmp, "r", encoding="utf-8", errors="replace") as src:
            data = src.read().replace("\r\n", "\n").replace("\r", "\n")
        with open(tmp, "w", encoding="utf-8") as dst:
            dst.write(data)

        ok3b = subprocess.run(f"git apply --check -3 -p0 {shlex.quote(tmp)}",
                              shell=True, text=True, capture_output=True)
        if ok3b.returncode == 0:
            sh(f"git apply -3 -p0 --whitespace=fix {shlex.quote(tmp)}")
            applied.append(sec["new"]); continue

        ok2 = subprocess.run(f"git apply --check -p0 {shlex.quote(tmp)}",
                             shell=True, text=True, capture_output=True)
        if ok2.returncode == 0:
            sh(f"git apply -p0 --whitespace=fix {shlex.quote(tmp)}")
            applied.append(sec["new"]); continue

        try:
            sh(f"git apply -p0 --reject --whitespace=fix {shlex.quote(tmp)}")
            rejected.append(sec["new"])
        except Exception:
            skipped.append(sec["new"])

    return applied, rejected, skipped

# ------------------------ Core Logic ------------------------

def main():
    comment = read_comment()
    if not comment or "/codex" not in comment:
        print("No codex command found."); sys.exit(0)

    actor = actor_handle() or "unknown"
    if not is_allowed(actor):
        comment_reply(f"⛔ Sorry @{actor}, du bist nicht berechtigt."); sys.exit(1)

    m_model  = re.search(r"(?:^|\s)(?:-m|--model)\s+([A-Za-z0-9._\-]+)", comment)
    model    = m_model.group(1).strip() if m_model else (os.environ.get("OPENAI_MODEL_DEFAULT") or "gpt-5")
    m_reason = re.search(r"(?:^|\s)--reason\s+(high|medium|low)\b", comment, re.I)
    if m_reason:
        os.environ["OPENAI_REASONING_EFFORT"] = m_reason.group(1).lower()
    m_paths  = re.search(r"(?:^|\s)--paths\s+([^\n]+)", comment)
    path_filter = [p.strip() for p in (m_paths.group(1) if m_paths else "").split(",") if p.strip()]

    request_tree_only = bool(re.search(r"nur\s+(repo-)?tree|nur\s+verzeichnisbaum", comment, re.I))
    dry_run   = bool(re.search(r"(?:^|\s)--dry\b", comment))
    allow_ci  = bool(re.search(r"(?:^|\s)--allow-ci\b", comment))
    want_spec = bool(re.search(r"(?:^|\s)--spec\b", comment))
    want_apply= bool(re.search(r"(?:^|\s)--apply\b", comment))
    force_big = bool(re.search(r"(?:^|\s)--force\b", comment))
    do_verify = bool(re.search(r"(?:^|\s)--verify\b", comment))

    instruction = re.sub(r"^.*?/codex", "", comment, flags=re.S).strip()
    for pat in [
        r"(?:^|\s)(?:-m|--model)\s+[A-Za-z0-9._\-]+",
        r"(?:^|\s)--reason\s+(?:high|medium|low)\b",
        r"(?:^|\s)--paths\s+[^\n]+",
        r"(?:^|\s)--verify\b",
        r"(?:^|\s)--dry\b",
        r"(?:^|\s)--allow-ci\b",
        r"(?:^|\s)--spec\b",
        r"(?:^|\s)--apply\b",
        r"(?:^|\s)--force\b",
    ]:
        instruction = re.sub(pat, "", instruction, flags=re.I)
    instruction = trim_to_chars(instruction.strip(), MAX_USER_CHARS)
    if not instruction and not request_tree_only:
        comment_reply("⚠️ Bitte gib eine Aufgabe an, z. B. `/codex --paths app/src Tests für PlayerService hinzufügen`")
        sys.exit(1)

    repo_files, compact_tree, _ = build_repo_index()
    post_repo_tree_comment(compact_tree)
    if request_tree_only:
        print("Tree-only request handled."); sys.exit(0)

    all_paths = [str(p).replace("\\", "/") for p in repo_files]
    if path_filter:
        keep = []
        for f in all_paths:
            for g in path_filter:
                try:
                    ok = subprocess.run(f"bash -lc '[[ {shlex.quote(f)} == {shlex.quote(g)} ]]'",
                                        shell=True, text=True, capture_output=True)
                    if ok.returncode == 0:
                        keep.append(f); break
                except Exception:
                    pass
        all_paths = keep or all_paths

    focused_files = top_relevant_files(instruction, all_paths, k=80)

    preview_parts = []
    for f in focused_files:
        try:
            head = sh(f"sed -n '1,160p' {shlex.quote(f)} | sed 's/\\t/    /g' || true", check=False)
            if head.strip():
                preview_parts.append(f"\n--- file: {f} ---\n{head}")
        except Exception:
            pass
    context = trim_to_chars("".join(preview_parts), MAX_CONTEXT_CHARS)

    docs = trim_to_chars(load_docs(MAX_DOC_CHARS), MAX_DOC_CHARS)

    if want_spec and not want_apply:
        SYSTEM = (
            "You are a senior Android/Kotlin architect. Write a crisp change SPECIFICATION only (no code), "
            "including: goals, affected modules/files, risks, test plan, and a bullet list of concrete edits. "
            "Keep it executable by a code-change agent in a next step."
        )
        USER = f"""Repository documents (trimmed):
{docs or '(no docs found)'}
Repository tree (compact, depth≤4):
{compact_tree}
Focused file heads (top relevance):
{context}
Task from @{actor_handle()}:
{instruction}
Output: Markdown SPEC only. No code. No diff.
"""
        spec = openai_generate(model, SYSTEM, USER, want_spec=True).strip()
        spec = trim_to_chars(spec, 48000)
        if not spec:
            comment_reply("⚠️ Konnte keine Spezifikation generieren.")
            sys.exit(1)
        comment_reply(f"📝 **Spezifikation (Review-Phase)**\n\n{spec}")
        print("spec-only done")
        sys.exit(0)

    SYSTEM = (
        "You are an expert Android/Kotlin code-change generator (Gradle, Jetpack Compose, Unit/Instrumented tests). "
        "Use the repository documents (agents/architecture/roadmap/changelog) as authoritative guidance. "
        "Return ONLY one git-compatible unified diff (no explanations). Keep changes minimal; include build/test edits if required. "
        "All paths must be repo-root relative and must exist in the provided repo tree."
    )
    USER = f"""Repository documents (trimmed):
{docs or '(no docs found)'}
Repository tree (compact, depth≤4):
{compact_tree}
Focused file heads (top relevance):
{context}
Task from @{actor_handle()}:
{instruction}
Output requirements:
- Return exactly ONE unified diff starting with: diff --git a/... b/...
- It must apply at repo root using: git apply -p0
- Include new files with proper headers (e.g. 'new file mode 100644')
- Avoid forbidden paths unless explicitly allowed: {', '.join(FORBIDDEN_PATHS)}
"""

    try:
        patch_text = openai_generate(model, SYSTEM, USER)
    except Exception as e:
        comment_reply(f"❌ OpenAI-Fehler:\n```\n{e}\n```"); raise

    patch_text = sanitize_patch_text(patch_text)
    if "diff --git " not in patch_text:
        comment_reply(f"⚠️ Kein gültiger Diff erkannt. Antwort war:\n```\n{patch_text[:1400]}\n```")
        sys.exit(1)

    if not bool(re.search(r"(?:^|\s)--force\b", comment)):
        if patch_text.count("\n") > MAX_PATCH_LINES:
            comment_reply(f"⛔ Patch zu groß (> {MAX_PATCH_LINES} Zeilen). Bitte Aufgabe eingrenzen oder `--force` setzen.")
            sys.exit(1)

    try:
        PatchSet.from_string(patch_text)
    except Exception as e:
        comment_reply(f"ℹ️ Hinweis: Unidiff-Parser warnte: `{type(e).__name__}: {e}` – "
                      f"versuche Patch trotzdem (3-way / per-Sektion / --reject).")

    remapped_patch, remaps, unresolved = try_remap_patch(patch_text, [Path(p) for p in all_paths])
    if remaps:
        bullets = "\n".join([f"- `{src}` → `{dst}`" for src, dst in remaps])
        comment_reply(f"🧭 Pfad-Remap angewandt (fehlende → existierende Dateien):\n{bullets}")
    if unresolved:
        bullets = "\n".join([f"- {p}" for p in unresolved])
        comment_reply("⚠️ Nicht zuordenbare Sektionen (ausgelassen):\n" + bullets)

    if not allow_ci:
        sec = parse_patch_sections(remapped_patch)
        kept, dropped = [], []
        for s in sec:
            if any(s["new"].startswith(fp) or s["old"].startswith(fp) for fp in FORBIDDEN_PATHS):
                dropped.append(s["new"])
            else:
                kept.append(s["body"])
        if dropped:
            comment_reply("🚫 Sektionen in geschützten Pfaden verworfen (nutze `--allow-ci`, wenn beabsichtigt):\n" +
                          "\n".join(f"- {d}" for d in dropped))
        remapped_patch = "".join(kept) if kept else remapped_patch

    os.makedirs(".github/codex", exist_ok=True)
    with open(".github/codex/codex.patch", "w", encoding="utf-8") as f:
        f.write(remapped_patch)

    if dry_run:
        snippet = remapped_patch[:1800]
        comment_reply("🧪 **Dry-Run** – Patch nicht angewendet. Ausschnitt:\n```diff\n" + snippet + "\n```")
        print("dry-run done"); sys.exit(0)

    branch = f"codex/{datetime.utcnow().strftime('%Y%m%d-%H%M%S')}-{actor_handle() or 'actor'}"
    sh("git config user.name 'codex-bot'"); sh("git config user.email 'actions@users.noreply.github.com'")
    sh(f"git checkout -b {branch}"); sh("chmod +x ./gradlew", check=False)

    try:
        with open(".github/codex/codex.patch", "r", encoding="utf-8", errors="replace") as _pf:
            whole_text = _pf.read()
        applied, rejected, skipped = try_apply_sections(".github/codex/codex.patch", whole_text)
    except Exception as e:
        comment_reply(f"❌ Patch-Apply fehlgeschlagen:\n```\n{e}\n```"); raise

    status = sh("git status --porcelain")
    if not status.strip():
        comment_reply("ℹ️ Keine Änderungen nach Patch-Anwendung gefunden (alle Sektionen übersprungen?).")
        print("nothing to commit"); sys.exit(0)

    sh("git add -A"); sh("git commit -m 'codex: apply requested changes'"); sh("git push --set-upstream origin HEAD")

    repo = os.environ["GITHUB_REPOSITORY"]
    default_branch = gh_api("GET", f"/repos/{repo}").get("default_branch", "main")

    body = (
        f"Automatisch erstellt aus Kommentar von @{actor_handle()}:\n\n> {instruction}\n\n"
        f"Doku berücksichtigt: {', '.join([n for n in DOC_CANDIDATES if os.path.exists(n)]) or '–'}\n"
        f"Angewandt: {len(applied)}, .rej: {len(rejected)}, übersprungen: {len(skipped)}\n"
        f"_Repo-Tree gespeichert unter `.github/codex/tree.txt`._"
    )
    pr = gh_api("POST", f"/repos/{repo}/pulls", {
        "title": f"codex changes: {instruction[:60]}",
        "head": branch,
        "base": default_branch,
        "body": body
    })

    pr_num = pr.get("number")
    try:
        gh_api("POST", f"/repos/{repo}/issues/{pr_num}/labels", {"labels": ["codex", "ci:generated"]})
    except Exception:
        pass
    try:
        owners = []
        for path in [".github/CODEOWNERS", "CODEOWNERS"]:
            if os.path.exists(path):
                txt = Path(path).read_text(encoding="utf-8", errors="replace")
                for line in txt.splitlines():
                    line = line.strip()
                    if not line or line.startswith("#"):
                        continue
                    parts = line.split()
                    owners += [p.lstrip("@") for p in parts[1:] if p.startswith("@")]
        owners = list(dict.fromkeys(owners))[:5]
        if owners:
            gh_api("POST", f"/repos/{repo}/pulls/{pr_num}/requested_reviewers", {"reviewers": owners})
    except Exception:
        pass

    comment_reply(f"✅ PR erstellt: #{pr['number']} — {pr['html_url']}")
    if do_verify:
        comment_reply("🧪 `--verify` erkannt – **Tests laufen nur auf Anforderung**. "
                      "Starte sie z. B. mit `/codex test` oder `/codex gradle testDebugUnitTest`.")
    print("done")

if __name__ == "__main__":
    main()

#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
Codex Bot: Erzeugt AI-Patches (Unified Diff), validiert gegen Repo-Tree,
mapped fehlende Pfade intelligent, wendet Patch robust an (3-way/--reject),
pusht Branch, erstellt PR. Postet vorab einen kompakten Verzeichnisbaum
und speichert die volle Dateiliste unter .github/codex/tree.txt.

Slash-Command-Beispiele:
  /codex -m gpt-5 --reason high Refactor X, füge Tests hinzu
  /codex --paths app/src,core/ui Fix Fokusnavigation in Staffeln
  /codex --verify Korrigiere Fehler und führe anschließend Unit-Tests aus
  /codex --paths . nur Repo-Tree posten, keinen Patch anwenden.
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


def openai_generate_diff(model: str, system_prompt: str, user_prompt: str) -> str:
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
                    time.sleep(base_sleep * (2 ** i))
                    continue
                raise
        raise last_err

    def _responses_call():
        resp = client.responses.create(
            model=model,
            input=[
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_prompt},
            ],
            reasoning={"effort": effort},
        )
        txt = getattr(resp, "output_text", None)
        if not txt:
            out = getattr(resp, "output", None)
            if out and len(out) and getattr(out[0], "content", None):
                c0 = out[0].content
                if c0 and len(c0) and hasattr(c0[0], "text"):
                    txt = c0[0].text
        if txt:
            return txt.strip()
        raise RuntimeError("OpenAI Responses returned no text.")

    def _chat_call():
        cc = client.chat.completions.create(
            model=model,
            messages=[
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_prompt},
            ],
        )
        return cc.choices[0].message.content.strip()

    try:
        return _try(_responses_call)
    except Exception as e:
        try:
            return _try(_chat_call)
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
    # ensure starts at a diff header
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
        # bevorzugt längeren Suffix-Match / kürzeren Pfad
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
            # Abschnitt auslassen

    return "".join(new_sections), remapped, unresolved


# ------------------------ Core Logic ------------------------

def main():
    comment = read_comment()
    if not comment or "/codex" not in comment:
        print("No codex command found."); sys.exit(0)

    actor = actor_handle() or "unknown"
    if not is_allowed(actor):
        comment_reply(f"⛔ Sorry @{actor}, du bist nicht berechtigt."); sys.exit(1)

    m_model = re.search(r"(?:^|\s)(?:-m|--model)\s+([A-Za-z0-9._\-]+)", comment)
    model = m_model.group(1).strip() if m_model else (os.environ.get("OPENAI_MODEL_DEFAULT") or "gpt-5")

    m_reason = re.search(r"(?:^|\s)--reason\s+(high|medium|low)\b", comment, re.I)
    if m_reason:
        os.environ["OPENAI_REASONING_EFFORT"] = m_reason.group(1).lower()

    m_paths = re.search(r"(?:^|\s)--paths\s+([^\n]+)", comment)
    path_filter = [p.strip() for p in (m_paths.group(1) if m_paths else "").split(",") if p.strip()]

    request_tree_only = bool(re.search(r"nur\s+(repo-)?tree|nur\s+verzeichnisbaum", comment, re.I))
    do_verify = bool(re.search(r"(?:^|\s)--verify\b", comment))

    instruction = re.sub(r"^.*?/codex", "", comment, flags=re.S).strip()
    for pat in [
        r"(?:^|\s)(?:-m|--model)\s+[A-Za-z0-9._\-]+",
        r"(?:^|\s)--reason\s+(?:high|medium|low)\b",
        r"(?:^|\s)--paths\s+[^\n]+",
        r"(?:^|\s)--verify\b",
    ]:
        instruction = re.sub(pat, "", instruction, flags=re.I)
    instruction = instruction.strip()
    if not instruction and not request_tree_only:
        comment_reply("⚠️ Bitte gib eine Aufgabe an, z. B. `/codex --paths app/src Tests für PlayerService hinzufügen`")
        sys.exit(1)

    # ---------- Repo-Index + Tree ----------
    repo_files, compact_tree, _ = build_repo_index()
    post_repo_tree_comment(compact_tree)
    if request_tree_only:
        print("Tree-only request handled."); sys.exit(0)

    # ---------- Kontext ----------
    files = [str(p).replace("\\", "/") for p in repo_files]
    if path_filter:
        keep = []
        for f in files:
            for g in path_filter:
                try:
                    ok = subprocess.run(f"bash -lc '[[ {shlex.quote(f)} == {shlex.quote(g)} ]]'",
                                        shell=True, text=True, capture_output=True)
                    if ok.returncode == 0:
                        keep.append(f); break
                except Exception:
                    pass
        files = keep or files

    code_ext = (".kt", ".kts", ".java", ".xml", ".gradle", ".gradle.kts",
                ".md", ".yml", ".yaml", ".properties", ".pro", ".conf", ".sh")
    prio = [f for f in files if f.endswith(code_ext)]
    files = (prio + [f for f in files if f not in prio])[:400]

    preview_parts = []
    for f in files:
        try:
            head = sh(f"sed -n '1,160p' {shlex.quote(f)} | sed 's/\\t/    /g' || true", check=False)
            if head.strip():
                preview_parts.append(f"\n--- file: {f} ---\n{head}")
        except Exception:
            pass
    context = "".join(preview_parts)
    if len(context) > 200_000:
        context = context[:200_000]

    SYSTEM = (
        "You are an expert code-change generator for Android/Kotlin (Gradle, Jetpack Compose, Unit + Instrumented tests). "
        "Return ONLY one git-compatible unified diff (no explanations). Keep changes minimal and consistent so Gradle build/test passes. "
        "Include edits to build/test files as needed. All file paths are repo-root relative. "
        "Use ONLY files and paths that exist in the provided repo tree."
    )

    USER = f"""Repository tree (compact, depth≤4):
{compact_tree}

Repository context (truncated file heads):
{context}

Task from @{actor}:
{instruction}

Output requirements:
- Return exactly ONE unified diff starting with lines like: diff --git a/... b/...
- Ensure it applies at repo root using: git apply -p0
- Include new files with proper headers (e.g. 'new file mode 100644')
- Do NOT include binary blobs; use code stubs/placeholders where needed.
"""

    # ---------- OpenAI ----------
    try:
        patch_text = openai_generate_diff(model, SYSTEM, USER)
    except Exception as e:
        comment_reply(f"❌ OpenAI-Fehler:\n```\n{e}\n```"); raise

    # ---------- Sanitize + Remap ----------
    patch_text = sanitize_patch_text(patch_text)
    if "diff --git " not in patch_text:
        comment_reply(f"⚠️ Kein gültiger Diff erkannt. Antwort war:\n```\n{patch_text[:1400]}\n```")
        sys.exit(1)

    # Parser nur informativ nutzen
    try:
        PatchSet.from_string(patch_text)
    except Exception as e:
        comment_reply(f"ℹ️ Hinweis: Unidiff-Parser warnte: `{type(e).__name__}: {e}` – versuche den Patch trotzdem (3-way/--reject).")

    remapped_patch, remaps, unresolved = try_remap_patch(patch_text, repo_files)
    if remaps:
        bullets = "\n".join([f"- `{src}` → `{dst}`" for src, dst in remaps])
        comment_reply(f"🧭 Pfad-Remap angewandt (fehlende → existierende Dateien):\n{bullets}")
    if unresolved:
        bullets = "\n".join([f"- {p}" for p in unresolved])
        comment_reply("⚠️ Nicht zuordenbare Sektionen (ausgelassen):\n" + bullets)

    os.makedirs(".github/codex", exist_ok=True)
    with open(".github/codex/codex.patch", "w", encoding="utf-8") as f:
        f.write(remapped_patch)

    # ---------- Branch, Apply (3-way/normal/reject), Commit, PR ----------
    branch = f"codex/{datetime.utcnow().strftime('%Y%m%d-%H%M%S')}-{actor}"
    sh("git config user.name 'codex-bot'"); sh("git config user.email 'actions@users.noreply.github.com'")
    sh(f"git checkout -b {branch}"); sh("chmod +x ./gradlew", check=False)

    def try_apply(patch_file: str):
        # 1) 3-way versuchen
        ok3 = subprocess.run(f"git apply --check -3 -p0 {shlex.quote(patch_file)}",
                             shell=True, text=True, capture_output=True)
        if ok3.returncode == 0:
            sh(f"git apply -3 -p0 --whitespace=fix {shlex.quote(patch_file)}"); return
        # 2) normal
        ok = subprocess.run(f"git apply --check -p0 {shlex.quote(patch_file)}",
                            shell=True, text=True, capture_output=True)
        if ok.returncode == 0:
            sh(f"git apply -p0 --whitespace=fix {shlex.quote(patch_file)}"); return
        # 3) sanitize erneut und retry 3-way/normal
        sanitized = ".github/codex/codex_sanitized.patch"
        with open(patch_file, "r", encoding="utf-8", errors="replace") as src:
            data = src.read().replace("\r\n", "\n").replace("\r", "\n")
        with open(sanitized, "w", encoding="utf-8") as dst:
            dst.write(data)
        ok3b = subprocess.run(f"git apply --check -3 -p0 {shlex.quote(sanitized)}",
                              shell=True, text=True, capture_output=True)
        if ok3b.returncode == 0:
            sh(f"git apply -3 -p0 --whitespace=fix {shlex.quote(sanitized)}"); return
        ok2 = subprocess.run(f"git apply --check -p0 {shlex.quote(sanitized)}",
                             shell=True, text=True, capture_output=True)
        if ok2.returncode == 0:
            sh(f"git apply -p0 --whitespace=fix {shlex.quote(sanitized)}"); return
        # 4) Letzter Versuch: --reject
        sh(f"git apply -p0 --reject --whitespace=fix {shlex.quote(sanitized)}")
        rej = sh("git ls-files -o --exclude-standard | grep -E '\\.rej$' || true", check=False)
        if rej.strip():
            comment_reply("⚠️ Einige Hunks konnten nicht automatisch angewendet werden ('.rej' erstellt):\n```\n"
                          + rej[:1800] + "\n```")

    try:
        try_apply(".github/codex/codex.patch")
    except Exception as e:
        comment_reply(f"❌ Patch-Apply fehlgeschlagen:\n```\n{e}\n```"); raise

    sh("git add -A"); sh("git commit -m 'codex: apply requested changes'"); sh("git push --set-upstream origin HEAD")

    repo = os.environ["GITHUB_REPOSITORY"]
    default_branch = gh_api("GET", f"/repos/{repo}").get("default_branch", "main")
    pr = gh_api("POST", f"/repos/{repo}/pulls", {
        "title": f"codex changes: {instruction[:60]}",
        "head": branch,
        "base": default_branch,
        "body": (
            f"Automatisch erstellt aus Kommentar von @{actor}:\n\n> {instruction}\n\n"
            f"Patch generiert via OpenAI ({model}).\n\n"
            f"_Repo-Tree gespeichert unter `.github/codex/tree.txt`._"
        )
    })

    with open(".github/codex/.last_branch", "w", encoding="utf-8") as f:
        f.write(branch)
    with open(".github/codex/.last_pr", "w", encoding="utf-8") as f:
        f.write(str(pr['number']))

    comment_reply(f"✅ PR erstellt: #{pr['number']} — {pr['html_url']}")

    if do_verify:
        try:
            out = sh("./gradlew -S --no-daemon testDebugUnitTest", check=False)
            snippet = out[-1800:] if out else "(no output)"
            comment_reply(f"🧪 Tests für `{branch}`:\n```\n{snippet}\n```")
        except Exception as e:
            comment_reply(f"❌ Gradle Tests fehlgeschlagen:\n```\n{e}\n```")

    print("done")


if __name__ == "__main__":
    main()

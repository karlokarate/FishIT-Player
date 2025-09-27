#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
Codex Bot: Erzeugt AI-Patches (Unified Diff), validiert gegen Repo-Tree,
mapped fehlende Pfade intelligent, wendet Patch an, pusht Branch, erstellt PR.
Postet vorab einen kompakten Verzeichnisbaum und speichert die volle Liste.
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
    """Return (files_list, tree_str_compact, tree_str_full)."""
    # nur getrackte Dateien (schnell & robust)
    try:
        files = [Path(p) for p in sh("git ls-files").splitlines() if p.strip()]
    except Exception:
        # Fallback: vollständiger Walk (sollte nicht nötig sein)
        files = [p for p in Path(".").rglob("*") if p.is_file()]
    files = [p for p in files if not _is_ignored(p)]

    # Vollständige Liste als Text
    full_list = "\n".join(sorted(str(p).replace("\\", "/") for p in files))

    # Kompakter Tree (Tiefe 4)
    def compress(path_strs, max_depth=4):
        # Kürzt tiefe Pfade: /a/b/c/d/e → /a/b/c/d/…
        out = []
        for s in sorted(path_strs):
            parts = s.split("/")
            if len(parts) > max_depth:
                out.append("/".join(parts[:max_depth]) + "/…")
            else:
                out.append(s)
        # Entferne Duplikat-Zeilen nach Kompression
        dedup = []
        last = None
        for line in out:
            if line != last:
                dedup.append(line)
                last = line
        return "\n".join(dedup)

    compact = compress((str(p).replace("\\", "/") for p in files), max_depth=4)

    # Speichere volle Liste als Artefakt-Datei
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


# ------------------------ Patch Preflight / Path Remap ------------------------

def parse_patch_sections(patch_text: str):
    """Split unified diff into per-file sections with metadata."""
    sections = []
    for m in re.finditer(r"^diff --git a/(\S+)\s+b/(\S+)\s*$", patch_text, re.M):
        start = m.start()
        # next 'diff --git' or end
        next_m = re.search(r"^diff --git ", patch_text[m.end():], re.M)
        end = m.end() + (next_m.start() if next_m else len(patch_text) - m.end())
        body = patch_text[start:end]

        oldp, newp = m.group(1), m.group(2)
        is_add = bool(re.search(r"(?m)^--- /dev/null\s*$"))
        is_del = bool(re.search(r"(?m)^\+\+\+ /dev/null\s*$"))
        is_rename = bool(re.search(r"(?m)^rename (from|to) ", body) or re.search(r"(?m)^similarity index ", body))
        sections.append({
            "old": oldp, "new": newp, "body": body,
            "is_add": is_add, "is_del": is_del, "is_rename": is_rename
        })
    return sections

def best_match_path(target_path: str, repo_files):
    """Find best existing file path for a target (by basename + suffix similarity)."""
    target = target_path.replace("\\", "/")
    tbase = os.path.basename(target)
    candidates = [str(p).replace("\\", "/") for p in repo_files if os.path.basename(str(p)) == tbase]
    if not candidates:
        # fuzzy on entire path
        scored = sorted(
            ((difflib.SequenceMatcher(a=target, b=str(p)).ratio(), str(p)) for p in repo_files),
            reverse=True
        )
        return scored[0][1] if scored and scored[0][0] >= 0.6 else None

    tparts = target.split("/")
    def suffix_score(p):
        parts = p.split("/")
        # count equal suffix segments
        suf = 0
        for a, b in zip(reversed(tparts), reversed(parts)):
            if a == b:
                suf += 1
            else:
                break
        ratio = difflib.SequenceMatcher(a="/".join(tparts[-4:]), b="/".join(parts[-4:])).ratio()
        # Prefer longer suffix match, then ratio, then shorter path
        return (suf, ratio, -len(parts))
    return sorted(candidates, key=suffix_score, reverse=True)[0]

def rewrite_section_paths(section: str, new_path: str):
    """Rewrite a single file-section to use 'new_path' for both a/ and b/ (modify-in-place)."""
    # Header
    section = re.sub(r"^(diff --git a/)\S+(\s+b/)\S+",
                     rf"\1{new_path}\2{new_path}",
                     section, count=1, flags=re.M)
    # File markers
    section = re.sub(r"(?m)^(--- )a/\S+$", rf"\1a/{new_path}", section)
    section = re.sub(r"(?m)^(\+\+\+ )b/\S+$", rf"\1b/{new_path}", section)
    return section

def try_remap_patch(patch_text: str, repo_files):
    """Return (new_patch_text, remapped_list, unresolved_list)."""
    sections = parse_patch_sections(patch_text)
    if not sections:
        return patch_text, [], []

    remapped = []
    unresolved = []
    new_sections = []

    for s in sections:
        newp = s["new"]
        body = s["body"]
        # Additions and renames lassen wir unverändert (oder separat behandeln)
        if s["is_add"] or s["is_rename"]:
            new_sections.append(body)
            continue
        # Ziel existiert?
        if os.path.exists(newp):
            new_sections.append(body)
            continue
        # Versuch: beste Übereinstimmung im Repo
        cand = best_match_path(newp, repo_files)
        if cand:
            new_body = rewrite_section_paths(body, cand)
            new_sections.append(new_body)
            remapped.append((newp, cand))
        else:
            # Sektion weglassen, aber reporten
            unresolved.append(newp)
            # nicht anhängen → Patch-Apply soll nicht daran scheitern

    # Baue neuen Patch zusammen
    # Achtung: Originaltext kann evtl. Header/Trailer außerhalb Sektionen enthalten – hier sind die
    # Sektionen schon vollständig (inkl. Headers), also einfach konkatenieren:
    new_text = "".join(new_sections)
    return new_text, remapped, unresolved


# ------------------------ Core Logic ------------------------

def main():
    comment = read_comment()
    if not comment or "/codex" not in comment:
        print("No codex command found.")
        sys.exit(0)

    actor = actor_handle() or "unknown"
    if not is_allowed(actor):
        comment_reply(f"⛔ Sorry @{actor}, du bist nicht berechtigt.")
        sys.exit(1)

    # Flags
    m_model = re.search(r"(?:^|\s)(?:-m|--model)\s+([A-Za-z0-9._\-]+)", comment)
    model = m_model.group(1).strip() if m_model else (os.environ.get("OPENAI_MODEL_DEFAULT") or "gpt-5")

    m_reason = re.search(r"(?:^|\s)--reason\s+(high|medium|low)\b", comment, re.I)
    if m_reason:
        os.environ["OPENAI_REASONING_EFFORT"] = m_reason.group(1).lower()

    m_paths = re.search(r"(?:^|\s)--paths\s+([^\n]+)", comment)
    path_filter = [p.strip() for p in (m_paths.group(1) if m_paths else "").split(",") if p.strip()]

    request_tree_only = bool(re.search(r"nur\s+(repo-)?tree|nur\s+verzeichnisbaum", comment, re.I))

    do_verify = bool(re.search(r"(?:^|\s)--verify\b", comment))

    # Instruction bereinigen
    instruction = re.sub(r"^.*?/codex", "", comment, flags=re.S).strip()
    instruction = re.sub(r"(?:^|\s)(?:-m|--model)\s+[A-Za-z0-9._\-]+", "", instruction)
    instruction = re.sub(r"(?:^|\s)--reason\s+(?:high|medium|low)\b", "", instruction, flags=re.I)
    instruction = re.sub(r"(?:^|\s)--paths\s+[^\n]+", "", instruction)
    instruction = re.sub(r"(?:^|\s)--verify\b", "", instruction)
    instruction = instruction.strip()
    if not instruction and not request_tree_only:
        comment_reply("⚠️ Bitte gib eine Aufgabe an, z. B. `/codex --paths app/src Tests für PlayerService hinzufügen`")
        sys.exit(1)

    # ---------------- Repo-Index + Tree ----------------
    repo_files, compact_tree, _full = build_repo_index()
    post_repo_tree_comment(compact_tree)

    # Optional: Nur Tree gewünscht
    if request_tree_only:
        print("Tree-only request handled.")
        sys.exit(0)

    # ---------------- Kontext aufbauen ----------------
    files = [str(p).replace("\\", "/") for p in repo_files]
    if path_filter:
        # simple glob-match pro Pattern via bash [[ ]]
        keep = []
        for f in files:
            for g in path_filter:
                try:
                    ok = subprocess.run(
                        f"bash -lc '[[ {shlex.quote(f)} == {shlex.quote(g)} ]]'",
                        shell=True, text=True
                    )
                    if ok.returncode == 0:
                        keep.append(f); break
                except Exception:
                    pass
        files = keep or files

    # priorisiere Code-Dateien
    code_ext = (".kt", ".kts", ".java", ".xml", ".gradle", ".gradle.kts",
                ".md", ".yml", ".yaml", ".properties", ".pro", ".conf", ".sh")
    prio = [f for f in files if f.endswith(code_ext)]
    files = (prio + [f for f in files if f not in prio])[:400]

    preview_parts = []
    for f in files:
        try:
            # max 160 Zeilen pro Datei
            head = sh(f"sed -n '1,160p' {shlex.quote(f)} | sed 's/\\t/    /g' || true", check=False)
            if head.strip():
                preview_parts.append(f"\n--- file: {f} ---\n{head}")
        except Exception:
            pass
    context = "".join(preview_parts)
    if len(context) > 200_000:
        context = context[:200_000]

    # --------------- Prompts ----------------
    SYSTEM = (
        "You are an expert code-change generator for Android/Kotlin (Gradle, Jetpack Compose, "
        "Unit + Instrumented tests). Return ONLY one git-compatible unified diff (no explanations). "
        "Keep changes minimal and consistent so Gradle build/test passes. Include edits to build/test files as needed. "
        "All file paths are repo-root relative. Use ONLY files and paths that exist in the provided repo tree."
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

    # --------------- OpenAI ---------------
    try:
        patch_text = openai_generate_diff(model, SYSTEM, USER)
    except Exception as e:
        comment_reply(f"❌ OpenAI-Fehler:\n```\n{e}\n```")
        raise

    # Codefence entfernen
    m = re.search(r"```(?:diff)?\s*(.*?)```", patch_text, re.S)
    if m:
        patch_text = m.group(1).strip()
    if "diff --git " not in patch_text:
        comment_reply(f"⚠️ Kein gültiger Diff erkannt. Antwort war:\n```\n{patch_text[:1400]}\n```")
        sys.exit(1)

    # --------------- Patch validieren & ggf. remappen ---------------
    try:
        PatchSet.from_string(patch_text)
    except Exception as e:
        comment_reply(f"⚠️ Diff ließ sich nicht parsen:\n```\n{str(e)}\n```")
        raise

    # Remap fehlender Pfade auf beste Matches; nicht-zuordenbare Sektionen werden weggelassen
    remapped_patch, remaps, unresolved = try_remap_patch(patch_text, repo_files)

    # Bericht
    if remaps:
        bullets = "\n".join([f"- `{src}` → `{dst}`" for src, dst in remaps])
        comment_reply(f"🧭 Pfad-Remap angewandt (fehlende → existierende Dateien):\n{bullets}")
    if unresolved:
        bullets = "\n".join([f"- {p}" for p in unresolved])
        comment_reply(
            "⚠️ Einige Diff-Sektionen konnten keinem existierenden Pfad zugeordnet werden und wurden "
            "übersprungen. Bitte `--paths` einschränken oder Aufgabe präziser formulieren.\n"
            "Nicht zuordenbar:\n" + bullets
        )

    os.makedirs(".github/codex", exist_ok=True)
    with open(".github/codex/codex.patch", "w", encoding="utf-8") as f:
        f.write(remapped_patch)

    # --------------- Branch, Apply, Commit, PR ---------------
    branch = f"codex/{datetime.utcnow().strftime('%Y%m%d-%H%M%S')}-{actor}"
    try:
        sh("git config user.name 'codex-bot'")
        sh("git config user.email 'actions@users.noreply.github.com'")
        sh(f"git checkout -b {branch}")
        # Sicherheitsnetz für gradlew
        sh("chmod +x ./gradlew", check=False)

        # Erst normal versuchen
        try:
            sh("git apply -p0 --whitespace=fix .github/codex/codex.patch")
        except Exception as e1:
            # Dann mit --reject, damit wenigstens anwendbare Hunks durchgehen
            sh("git apply -p0 --reject --whitespace=fix .github/codex/codex.patch")
            rej = sh("git ls-files -o --exclude-standard | grep -E '\\.rej$' || true", check=False)
            if rej.strip():
                comment_reply("⚠️ Einige Hunks konnten nicht automatisch angewendet werden ('.rej' erstellt):\n```\n"
                              + rej[:1800] + "\n```")

    except Exception as e:
        comment_reply(f"❌ Patch-Apply fehlgeschlagen:\n```\n{e}\n```")
        raise

    sh("git add -A")
    sh("git commit -m 'codex: apply requested changes'")
    sh("git push --set-upstream origin HEAD")

    repo = os.environ["GITHUB_REPOSITORY"]
    default_branch = gh_api("GET", f"/repos/{repo}").get("default_branch", "main")
    pr = gh_api("POST", f"/repos/{repo}/pulls", {
        "title": f"codex changes: {instruction[:60]}",
        "head": branch,
        "base": default_branch,
        "body": f"Automatisch erstellt aus Kommentar von @{actor}:\n\n> {instruction}\n\n"
                f"Patch generiert via OpenAI ({model}).\n\n"
                f"_Repo-Tree gespeichert unter `.github/codex/tree.txt`._"
    })

    with open(".github/codex/.last_branch", "w", encoding="utf-8") as f:
        f.write(branch)
    with open(".github/codex/.last_pr", "w", encoding="utf-8") as f:
        f.write(str(pr["number"]))

    comment_reply(f"✅ PR erstellt: #{pr['number']} — {pr['html_url']}")

    # Optional schneller Test
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

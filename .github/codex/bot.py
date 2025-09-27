#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
Codex Bot: Erzeugt AI-Patches (Unified Diff), wendet sie an, pusht Branch, erstellt PR.
Kompatibel mit den Workflows in .github/workflows/codex-bot.yml

Erwartete Umgebungsvariablen (werden im Workflow gesetzt):
- OPENAI_API_KEY             : OpenAI API Key (Secret)
- OPENAI_BASE_URL            : optional, falls Proxy/Enterprise (muss mit https:// beginnen)
- OPENAI_MODEL_DEFAULT       : z. B. "gpt-5" (Var)
- OPENAI_REASONING_EFFORT    : "high" | "medium" | "low" (Var; default "high")
- GITHUB_TOKEN               : GitHub Actions Token (Secret)
- GITHUB_REPOSITORY          : owner/repo
- GITHUB_EVENT_PATH          : Pfad zur GitHub-Event-JSON
- GH_EVENT_NAME              : event name (issue_comment, issues, pull_request_review_comment, workflow_dispatch)
- DISPATCH_COMMENT           : (nur workflow_dispatch) Kommentartext mit /codex …
- CODEX_ALLOWLIST            : optionale Kommaliste erlaubter Handles (Var)

Slash-Command-Syntax (Beispiele):
/codex -m gpt-5 --reason high Refactor X, füge Tests hinzu
/codex --paths app/src,core/ui Fix Fokusnavigation in Staffeln
/codex --verify Korrigiere Fehler und führe anschließend Unit-Tests aus
"""

import json
import os
import re
import shlex
import subprocess
import sys
from datetime import datetime

import requests
from unidiff import PatchSet


# ------------------------ Utilities ------------------------

def sh(cmd: str, check: bool = True) -> str:
    """Run shell command and return stdout (str)."""
    res = subprocess.run(cmd, shell=True, text=True, capture_output=True)
    if check and res.returncode != 0:
        raise RuntimeError(f"cmd failed: {cmd}\nSTDOUT:\n{res.stdout}\nSTDERR:\n{res.stderr}")
    return res.stdout.strip()


def gh_api(method: str, path: str, payload=None):
    """Call GitHub REST API with repo-scoped token."""
    url = f"https://api.github.com{path}"
    headers = {
        "Authorization": f"Bearer {os.environ['GITHUB_TOKEN']}",
        "Accept": "application/vnd.github+json"
    }
    resp = requests.request(method, url, headers=headers, json=payload)
    if resp.status_code >= 300:
        raise RuntimeError(f"GitHub API {method} {path} failed: {resp.status_code} {resp.text}")
    if resp.text:
        return resp.json()
    return {}


def comment_reply(body: str):
    """Post a comment back to the originating Issue/PR (if available)."""
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
        # workflow_dispatch: kein Thread zum Antworten
        return

    gh_api("POST", f"/repos/{repo}/issues/{issue_number}/comments", {"body": body})


def read_comment() -> str:
    """Extract the user instruction that triggered the workflow."""
    evname = os.environ.get("GH_EVENT_NAME", "")
    if evname == "workflow_dispatch":
        return os.environ.get("DISPATCH_COMMENT", "").strip()

    try:
        event = json.load(open(os.environ["GITHUB_EVENT_PATH"], "r", encoding="utf-8"))
    except Exception:
        return ""

    # 1) Issue comment
    if evname == "issue_comment" and "comment" in event and "body" in event["comment"]:
        return (event["comment"]["body"] or "").strip()

    # 2) PR review comment
    if evname == "pull_request_review_comment" and "comment" in event and "body" in event["comment"]:
        return (event["comment"]["body"] or "").strip()

    # 3) Issues event -> Body des Issues
    if evname == "issues" and "issue" in event and "body" in event["issue"]:
        return (event["issue"]["body"] or "").strip()

    return ""


def actor_handle() -> str:
    """Return the GitHub handle (login) of the actor who triggered the workflow."""
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
    """Allow only allowlisted users (if set) or collaborators with write+ permission."""
    allowlist = os.environ.get("CODEX_ALLOWLIST", "").strip()
    if allowlist:
        allowed = [a.strip() for a in allowlist.split(",") if a.strip()]
        return actor in allowed

    # Fallback: check collaborator permission
    repo = os.environ.get("GITHUB_REPOSITORY", "")
    try:
        perms = gh_api("GET", f"/repos/{repo}/collaborators/{actor}/permission")
        return perms.get("permission") in ("write", "maintain", "admin")
    except Exception:
        return False


# ------------------------ OpenAI ------------------------

def _compute_base_url() -> str:
    """
    Liefert eine garantiert gültige Base-URL:
    - Wenn OPENAI_BASE_URL leer/nicht gesetzt: https://api.openai.com/v1
    - Wenn ohne Protokoll: https:// voranstellen
    - Wenn ohne /v1: anhängen
    """
    raw = os.environ.get("OPENAI_BASE_URL", "")
    raw = (raw or "").strip()
    if not raw:
        return "https://api.openai.com/v1"
    if not raw.startswith(("http://", "https://")):
        raw = "https://" + raw
    if not re.search(r"/v\d+/?$", raw):
        raw = raw.rstrip("/") + "/v1"
    return raw


def openai_generate_diff(model: str, system_prompt: str, user_prompt: str) -> str:
    """
    Prefer OpenAI Responses API; fallback to Chat Completions for compatibility.
    Returns a string containing a git-unified diff.
    """
    from openai import OpenAI

    # WICHTIG: Base-URL IMMER EXPLIZIT setzen, um leere Env-Werte zu übersteuern.
    base_url = _compute_base_url()
    client = OpenAI(api_key=os.environ["OPENAI_API_KEY"], base_url=base_url)

    # Reasoning effort (high|medium|low); default high
    effort = os.environ.get("OPENAI_REASONING_EFFORT", "high").lower()
    if effort not in ("high", "medium", "low"):
        effort = "high"

    # Try Responses API first
    try:
        resp = client.responses.create(
            model=model,
            input=[
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_prompt}
            ],
            temperature=0.2,
            reasoning={"effort": effort},
        )
        txt = getattr(resp, "output_text", None)
        if not txt:
            # Attempt to navigate structure if SDK returns segments
            out = getattr(resp, "output", None)
            if out and len(out) and getattr(out[0], "content", None):
                c0 = out[0].content
                if c0 and len(c0) and hasattr(c0[0], "text"):
                    txt = c0[0].text
        if txt:
            return txt.strip()
    except Exception as e:
        # Fallback to Chat Completions
        try:
            cc = client.chat.completions.create(
                model=model,
                messages=[
                    {"role": "system", "content": system_prompt},
                    {"role": "user", "content": user_prompt}
                ],
                temperature=0.2,
            )
            return cc.choices[0].message.content.strip()
        except Exception as ee:
            raise RuntimeError(f"OpenAI call failed: {e}\nFallback also failed: {ee}")

    raise RuntimeError("OpenAI returned no text.")


# ------------------------ Core Logic ------------------------

def main():
    # 1) Read & validate command
    comment = read_comment()
    if not comment:
        print("No comment body found.")
        sys.exit(0)

    if "/codex" not in comment:
        print("No /codex command. Skipping.")
        sys.exit(0)

    actor = actor_handle() or "unknown"
    if not is_allowed(actor):
        comment_reply(f"⛔ Sorry @{actor}, du bist nicht berechtigt, den Codex-Bot auszuführen.")
        sys.exit(1)

    # 2) Parse flags: -m/--model, --paths, --reason, --verify
    m_model = re.search(r"(?:^|\s)(?:-m|--model)\s+([A-Za-z0-9._\-]+)", comment)
    model = m_model.group(1).strip() if m_model else (os.environ.get("OPENAI_MODEL_DEFAULT") or "gpt-5")

    m_reason = re.search(r"(?:^|\s)--reason\s+(high|medium|low)\b", comment, re.I)
    if m_reason:
        os.environ["OPENAI_REASONING_EFFORT"] = m_reason.group(1).lower()

    m_paths = re.search(r"(?:^|\s)--paths\s+([^\n]+)", comment)
    path_filter = [p.strip() for p in (m_paths.group(1) if m_paths else "").split(",") if p.strip()]

    do_verify = bool(re.search(r"(?:^|\s)--verify\b", comment))

    # instruction = content after '/codex' minus flags
    instruction = re.sub(r"^.*?/codex", "", comment, flags=re.S).strip()
    instruction = re.sub(r"(?:^|\s)(?:-m|--model)\s+[A-Za-z0-9._\-]+", "", instruction)
    instruction = re.sub(r"(?:^|\s)--reason\s+(?:high|medium|low)\b", "", instruction, flags=re.I)
    instruction = re.sub(r"(?:^|\s)--paths\s+[^\n]+", "", instruction)
    instruction = re.sub(r"(?:^|\s)--verify\b", "", instruction)
    instruction = instruction.strip()
    if not instruction:
        comment_reply("⚠️ Bitte gib eine Aufgabe an, z. B. `/codex -m gpt-5 --reason high --verify Tests für PlayerService hinzufügen …`")
        sys.exit(1)

    # 3) Build lightweight repo context
    files = sh("git ls-files").splitlines()
    if path_filter:
        # filter by simple globs (bash [[ pattern ]])
        keep = []
        for f in files:
            for g in path_filter:
                try:
                    ok = subprocess.run(
                        f"bash -lc '[[ {shlex.quote(f)} == {shlex.quote(g)} ]]'",
                        shell=True, text=True
                    )
                    if ok.returncode == 0:
                        keep.append(f)
                        break
                except Exception:
                    pass
        files = keep

    # prioritize code files
    code_ext = (".kt", ".kts", ".java", ".xml", ".gradle", ".gradle.kts", ".md",
                ".yml", ".yaml", ".properties", ".pro", ".conf", ".sh")
    prio = [f for f in files if f.endswith(code_ext)]
    others = [f for f in files if f not in prio]
    files = (prio + others)[:400]

    preview_parts = []
    for f in files:
        if f.startswith(".git/"):
            continue
        try:
            head = sh(f"sed -n '1,160p' {shlex.quote(f)} | sed 's/\\t/    /g' || true", check=False)
            if head.strip():
                preview_parts.append(f"\n--- file: {f} ---\n{head}")
        except Exception:
            pass
    context = "".join(preview_parts)
    if len(context) > 220_000:
        context = context[:220_000]

    # 4) Prepare prompts
    SYSTEM = (
        "You are an expert code-change generator for Android/Kotlin projects (Gradle, Jetpack Compose, "
        "Unit + Instrumented tests). Return ONLY one git-compatible unified diff (no explanations). "
        "Keep changes minimal and consistent so Gradle build/test passes. Include edits to build/test files as needed. "
        "All file paths are repo-root relative."
    )

    USER = f"""Repository context (truncated):
{context}

Task from @{actor} for fishit-player:
{instruction}

Output requirements:
- Return exactly ONE unified diff starting with lines like: diff --git a/... b/...
- Ensure it applies at repo root using: git apply -p0
- Include new files with proper headers (e.g. 'new file mode 100644')
- Do NOT include binary blobs; use code stubs/placeholders where needed.
"""

    # 5) Call OpenAI
    try:
        patch_text = openai_generate_diff(model, SYSTEM, USER)
    except Exception as e:
        comment_reply(f"❌ OpenAI-Fehler:\n```\n{e}\n```")
        raise

    # unwrap code fence if present
    m = re.search(r"```(?:diff)?\s*(.*?)```", patch_text, re.S)
    if m:
        patch_text = m.group(1).strip()

    if "diff --git " not in patch_text:
        comment_reply(f"⚠️ Konnte keinen gültigen Diff erkennen. Antwort war:\n```\n{patch_text[:1400]}\n```")
        sys.exit(1)

    # 6) Validate & apply patch on new branch
    try:
        PatchSet.from_string(patch_text)
    except Exception as e:
        comment_reply(f"⚠️ Diff ließ sich nicht parsen:\n```\n{str(e)}\n```")
        raise

    os.makedirs(".github/codex", exist_ok=True)
    with open(".github/codex/codex.patch", "w", encoding="utf-8") as f:
        f.write(patch_text)

    branch = f"codex/{datetime.utcnow().strftime('%Y%m%d-%H%M%S')}-{actor}"
    try:
        sh("git config user.name 'codex-bot'")
        sh("git config user.email 'actions@users.noreply.github.com'")
        sh(f"git checkout -b {branch}")
        sh("git apply -p0 --whitespace=fix .github/codex/codex.patch")
    except Exception as e:
        comment_reply(f"❌ Patch-Apply fehlgeschlagen:\n```\n{e}\n```")
        raise

    # 7) Commit, push, open PR
    sh("git add -A")
    sh("git commit -m 'codex: apply requested changes'")
    sh("git push --set-upstream origin HEAD")

    repo = os.environ["GITHUB_REPOSITORY"]
    default_branch = gh_api("GET", f"/repos/{repo}").get("default_branch", "main")

    pr = gh_api("POST", f"/repos/{repo}/pulls", {
        "title": f"codex changes: {instruction[:60]}",
        "head": branch,
        "base": default_branch,
        "body": f"Automatisch erstellt aus Kommentar von @{actor}:\n\n> {instruction}\n\nPatch generiert via OpenAI ({model})."
    })

    with open(".github/codex/.last_branch", "w", encoding="utf-8") as f:
        f.write(branch)
    with open(".github/codex/.last_pr", "w", encoding="utf-8") as f:
        f.write(str(pr["number"]))

    comment_reply(f"✅ PR erstellt: #{pr['number']} — {pr['html_url']}\n\nBranch: `{branch}`")

    # Optional verification step (quick unit test gate on the PR branch)
    if do_verify:
        try:
            # Safety: ensure gradlew is executable
            try:
                sh("chmod +x ./gradlew", check=False)
            except Exception:
                pass
            out = sh("./gradlew -S --no-daemon testDebugUnitTest", check=False)
            snippet = out[-1800:] if out else "(no output)"
            comment_reply(f"🧪 Gradle Tests für `{branch}` ausgeführt:\n```\n{snippet}\n```")
        except Exception as e:
            comment_reply(f"❌ Gradle Testlauf fehlgeschlagen:\n```\n{e}\n```")

    print("done")


if __name__ == "__main__":
    main()

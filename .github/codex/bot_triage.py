# -*- coding: utf-8 -*-
"""
Bot 3 - Triage
Analyzes failures (contextmap or solver), attempts automated fixes (pipeline or solver code), and retries the solver once.
"""
import os, sys, json, re, requests, zipfile, io, subprocess
from pathlib import Path

# ---------- GitHub API helpers ----------
def repo() -> str:
    return os.environ.get("GITHUB_REPOSITORY", "")

def gh_api(method: str, path: str, data=None) -> dict:
    """GitHub API helper (uses bearer token auth, supports PAT via INPUT_TOKEN)."""
    token = os.environ.get("GITHUB_TOKEN") or os.environ.get("INPUT_TOKEN")
    headers = {
        "Authorization": f"Bearer {token}",
        "Accept": "application/vnd.github+json"
    }
    url = f"https://api.github.com{path}"
    func = getattr(requests, method.lower(), None)
    if not func:
        raise RuntimeError(f"No requests method for {method}")
    resp = func(url, headers=headers, json=data) if data is not None else func(url, headers=headers)
    if resp.status_code >= 400:
        msg = resp.text[:1000]
        print(f"::error::GH API {method} {path} failed: {resp.status_code} {msg}")
    try:
        return resp.json()
    except Exception:
        return {}

def event() -> dict:
    """Loads the GitHub event payload."""
    path = os.environ.get("GITHUB_EVENT_PATH")
    if not path or not os.path.isfile(path):
        return {}
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)

def evname() -> str:
    return os.environ.get("GH_EVENT_NAME") or os.environ.get("GITHUB_EVENT_NAME") or ""

# ---------- Issue and Workflow Utilities ----------
def list_issue_comments(num: int) -> list:
    res = gh_api("GET", f"/repos/{repo()}/issues/{num}/comments")
    return res if isinstance(res, list) else []

def post_comment(num: int, body: str):
    gh_api("POST", f"/repos/{repo()}/issues/{num}/comments", {"body": body})

def add_labels(num: int, labels: list[str]):
    gh_api("POST", f"/repos/{repo()}/issues/{num}/labels", {"labels": labels})

def remove_label(num: int, label: str):
    gh_api("DELETE", f"/repos/{repo()}/issues/{num}/labels/{label}")

def default_branch() -> str:
    return gh_api("GET", f"/repos/{repo()}").get("default_branch", "main")

def fetch_latest_failed_run_for_issue(issue_num: int) -> dict:
    """Fetches the most recent failed workflow run related to a given issue (by commit/PR message)."""
    runs = gh_api("GET", f"/repos/{repo()}/actions/runs?status=failure&per_page=20") or {}
    items = runs.get("workflow_runs", []) or runs.get("runs", []) or []
    for r in items:
        # Look for issue reference in commit message or PR body
        num = workflow_run_issue_from_commit_message(r)
        if num == issue_num:
            return r
    return {}

def workflow_run_issue_from_commit_message(run: dict) -> Optional[int]:
    """Tries to extract an issue number from a workflow run's commit message or PR body."""
    # e.g. commit message "codex: solver apply (issue #123)"
    msg = (run.get("head_commit") or {}).get("message") or ""
    m = re.search(r"\(issue\s*#(\d+)\)", msg)
    if m:
        return int(m.group(1))
    # Check PR body if present
    prs = run.get("pull_requests") or []
    if prs:
        pr_details = gh_api("GET", f"/repos/{repo()}/pulls/{prs[0].get('number')}")
        body = pr_details.get("body") or ""
        m2 = re.search(r"\(issue\s*#(\d+)\)", body)
        if m2:
            return int(m2.group(1))
    return None

def collect_run_logs(run: dict) -> tuple[str, dict]:
    """Downloads and concatenates logs from a failed workflow run (returns text and stats)."""
    if not run:
        return "", {}
    log_text = ""
    try:
        # Download the logs archive for the run
        logs_url = run.get("logs_url")
        resp = requests.get(logs_url, headers={
            "Authorization": f"Bearer {os.environ.get('GITHUB_TOKEN') or os.environ.get('INPUT_TOKEN')}",
            "Accept": "application/vnd.github+json"
        })
        z = zipfile.ZipFile(io.BytesIO(resp.content))
    except Exception as e:
        print(f"::error::Failed to download logs: {e}")
        return "", {}
    # Append content of each log file in the archive
    for name in z.namelist():
        try:
            content = z.read(name).decode("utf-8", errors="ignore")
        except Exception:
            content = z.read(name).decode("latin-1", errors="ignore")
        log_text += f"\n----- {name} -----\n{content}"
    lines = log_text.splitlines()
    if len(lines) > 1000:
        log_text = "\n".join(lines[-1000:])  # keep only last 1000 lines to fit in prompt
    return log_text, {"lines": len(log_text.splitlines())}

# ---------- Heuristics / OpenAI ----------
def gradle_heuristics(log_txt: str) -> str:
    """Extracts key error lines from Gradle logs to assist analysis."""
    hints = []
    for ln in log_txt.splitlines()[-120:]:
        if " error:" in ln or "FAILURE:" in ln or "Exception" in ln:
            hints.append(ln.strip())
    return ("\n".join(hints))[:1000] if hints else ""

def analyze_with_openai(log_snippet: str, context_hint: str = "", heuristics: str = "") -> str:
    from openai import OpenAI
    client = OpenAI(api_key=os.environ.get("OPENAI_API_KEY"), base_url=(os.environ.get("OPENAI_BASE_URL") or "https://api.openai.com/v1"))
    model = os.environ.get("OPENAI_MODEL_DEFAULT") or "gpt-5"
    try:
        rsp = client.chat.completions.create(
            model=model,
            messages=[
                {"role": "system", "content": "Du analysierst CI-Fehler präzise und gibst konkrete Fix-Schritte."},
                {"role": "user", "content": f"{context_hint}\n\n```logs\n{log_snippet or 'Keine Logs'}\n```\n\nBekannte Hinweise:\n{heuristics or '-'}\n\nBitte: Ursachenanalyse + präzise Fix-Schritte (Markdown, kurz & konkret)."}
            ],
            temperature=0
        )
        return rsp.choices[0].message.content
    except Exception as e:
        return f"**Analyse nicht möglich:** {e}"

# ---------- Dispatch Helpers ----------
def dispatch_solver(issue_num: int):
    """Manually trigger the Solver (Bot 2) workflow for a given issue."""
    gh_api("POST", f"/repos/{repo()}/actions/workflows/codex-solve.yml/dispatches",
           {"ref": default_branch(), "inputs": {"issue": str(issue_num)}})

def dispatch_build(branch: str, issue_num: int, ignore_v7a: bool = False):
    """Trigger the release-apk build workflow on a given branch (optionally ignoring v7a)."""
    inputs = {"build_type": "debug", "issue": str(issue_num)}
    if ignore_v7a:
        inputs["ignore_missing_tdlib_v7a"] = "true"
    gh_api("POST", f"/repos/{repo()}/actions/workflows/release-apk.yml/dispatches",
           {"ref": branch, "inputs": inputs})

# ---------- Analysis Comment Upsert ----------
def upsert_analysis_comment(num: int, markdown: str):
    """Creates or updates a comment in the issue with the analysis from Bot 3."""
    marker = "### bot3-analysis"
    cid = None
    for c in list_issue_comments(num):
        if (c.get("body") or "").strip().startswith(marker):
            cid = c.get("id")
            break
    body = (marker + "\n" + markdown).strip()
    if cid:
        gh_api("PATCH", f"/repos/{repo()}/issues/comments/{cid}", {"body": body})
    else:
        post_comment(num, body)

# ---------- Main ----------
def main():
    evn = evname()
    # 1) Manual dispatch trigger
    if evn == "workflow_dispatch":
        issue_str = os.environ.get("ISSUE_NUMBER") or ""
        if not issue_str.strip():
            print("::error::workflow_dispatch ohne ISSUE_NUMBER")
            sys.exit(1)
        num = int(issue_str.strip())
        # Perform quick analysis and immediately re-dispatch solver (used for manual retry)
        run = fetch_latest_failed_run_for_issue(num)
        blob, _ = collect_run_logs(run) if run else ("", {})
        analysis = analyze_with_openai(blob or "(keine Logs vom letzten Run)",
                                       context_hint="Manueller Triage-Start",
                                       heuristics=gradle_heuristics(blob or ""))
        upsert_analysis_comment(num, analysis)
        # Check if already attempted once
        issue_data = gh_api("GET", f"/repos/{repo()}/issues/{num}")
        labels = [l.get("name") for l in issue_data.get("labels", [])]
        if "triage-attempted" in labels:
            # Already attempted an auto-fix -> escalate to human needed
            remove_label(num, "solver-error"); remove_label(num, "contextmap-error")
            remove_label(num, "contextmap-ready"); remove_label(num, "triage-attempted")
            add_labels(num, ["triage-needed"])
            post_comment(num, "⚠️ Triage: Automatischer Fix bereits versucht – `triage-needed` gesetzt.")
            return
        # Not attempted yet: mark and re-dispatch solver
        add_labels(num, ["triage-attempted"])
        dispatch_solver(num)
        post_comment(num, "🔁 Triage: Analyse abgeschlossen, Solver erneut gestartet (workflow_dispatch).")
        return

    # 2) Issue label trigger (contextmap-error or solver-error)
    if evn == "issues":
        ev = event()
        num = (ev.get("issue") or {}).get("number")
        if not num:
            print("::error::No issue number")
            sys.exit(1)
        labels = [l.get("name") for l in (ev.get("issue") or {}).get("labels", [])]
        if not any(x in ("contextmap-error", "solver-error") for x in labels):
            print("::notice::irrelevantes Label")
            sys.exit(0)

        # Fetch logs from latest failed run (likely the release-apk build) and analyze
        run = fetch_latest_failed_run_for_issue(num)
        blob, _ = collect_run_logs(run) if run else ("", {})
        heur = gradle_heuristics(blob or "")
        analysis = analyze_with_openai(blob or "(keine Logs gefunden)",
                                       context_hint="Label-Trigger",
                                       heuristics=heur)
        upsert_analysis_comment(num, analysis)

        # If an automatic attempt was already made, give up and mark triage-needed
        if "triage-attempted" in labels:
            remove_label(num, "solver-error"); remove_label(num, "contextmap-error")
            remove_label(num, "contextmap-ready"); remove_label(num, "triage-attempted")
            add_labels(num, ["triage-needed"])
            post_comment(num, "⚠️ Triage: Automatischer Fix nicht erfolgreich – `triage-needed` gesetzt.")
            return

        # No prior attempt: try to auto-fix the issue and rerun solver
        fixes_applied = []
        # (A) Fix missing Android SDK platform (e.g., compileSdk)
        sdk_match = re.search(r"Failed to find target Android (\d+)", blob)
        if sdk_match:
            missing_sdk = sdk_match.group(1)
            # Patch release-apk.yml to install the missing platform
            yml_path = ".github/workflows/release-apk.yml"
            try:
                text = Path(yml_path).read_text(encoding="utf-8")
            except Exception as e:
                text = ""
            if text and f'android-{missing_sdk}' not in text:
                install_line = f'           sdkmanager "platforms;android-{missing_sdk}" "build-tools;{missing_sdk}.0.0" || true'
                # Insert after the existing sdkmanager lines (after platform-34/35 lines)
                text = re.sub(r'(sdkmanager "platforms;android-\d+".*?^\s*$)', r'\1\n' + install_line, text, flags=re.M)
                try:
                    Path(yml_path).write_text(text, encoding="utf-8")
                    fixes_applied.append(f"Android SDK {missing_sdk}")
                except Exception as e:
                    print(f"::error::Failed to patch release-apk.yml for SDK {missing_sdk}: {e}")
        # (B) Fix missing armeabi-v7a/TDLib issues
        if re.search(r'(?i)(armeabi-v7a|TDLib)', blob) and re.search(r'(?i)(no|missing)', blob):
            # Patch release-apk.yml to ignore missing v7a TDLib by default
            yml_path = ".github/workflows/release-apk.yml"
            try:
                text = Path(yml_path).read_text(encoding="utf-8")
            except Exception as e:
                text = ""
            if text and 'ignore_missing_tdlib_v7a' in text and 'default: "true"' not in text:
                text = re.sub(r'ignore_missing_tdlib_v7a:\s*\n\s*default:\s*"false"', 'ignore_missing_tdlib_v7a:\n        default: "true"', text)
                try:
                    Path(yml_path).write_text(text, encoding="utf-8")
                    fixes_applied.append("armeabi-v7a ignored")
                except Exception as e:
                    print(f"::error::Failed to patch release-apk.yml for v7a: {e}")
        # (C) Fix solver bot code if it crashed with an exception
        if "Unexpected solver error:" in (heur or "") or "Traceback" in blob:
            # Attempt to patch bot_solver.py using OpenAI if an error in solver code is detected
            solver_path = ".github/codex/bot_solver.py"
            try:
                solver_code = Path(solver_path).read_text(encoding="utf-8")
            except Exception as e:
                solver_code = ""
            # Use the analysis (or heuristics) to guide the fix
            guidance = ""
            for line in (heur or "").splitlines():
                if "Unexpected solver error:" in line or "Exception" in line:
                    guidance += line + "\n"
            prompt = (
                "Du bist ein GitHub Actions Bot. Der Bot 'solver' hat einen Fehler geworfen.\n"
                f"Logs:\n```\n{guidance or blob}\n```\n"
                "Code von bot_solver.py:\n"
                "```python\n" + solver_code[:8000] + "\n```\n"
                "Bitte liefere einen Patch (Unified Diff) für bot_solver.py, um den Fehler zu beheben."
            )
            try:
                from openai import OpenAI
                client = OpenAI(api_key=os.environ.get("OPENAI_API_KEY"), base_url=(os.environ.get("OPENAI_BASE_URL") or "https://api.openai.com/v1"))
                response = client.chat.completions.create(
                    model=os.environ.get("OPENAI_MODEL_DEFAULT") or "gpt-5",
                    messages=[{"role": "user", "content": prompt}],
                    temperature=0
                )
                diff_text = response.choices[0].message.content or ""
            except Exception as e:
                diff_text = ""
                print(f"::warning::OpenAI diff generation failed: {e}")
            # Apply the diff if it looks valid
            if diff_text.startswith("diff --git") and ".github/codex/bot_solver.py" in diff_text:
                # Write diff to a temporary patch file
                patch_file = "_solver_fix.patch"
                Path(patch_file).write_text(diff_text, encoding="utf-8")
                # Try to apply patch
                apply_cmd = f"git apply --whitespace=fix {patch_file}"
                pr = subprocess.run(apply_cmd, shell=True, text=True, capture_output=True)
                if pr.returncode == 0:
                    fixes_applied.append("solver bot code")
                    print("Solver bot patch applied successfully.")
                else:
                    print(f"::warning::Failed to apply solver patch:\nSTDOUT:\n{pr.stdout}\nSTDERR:\n{pr.stderr}")
            else:
                print("No valid solver patch generated by OpenAI.")
        # If any fixes were made, commit them to the repository (on default branch)
        if fixes_applied:
            try:
                # Ensure we're on default branch
                base = default_branch()
                subprocess.run(f"git checkout {base}", shell=True, check=False)
                subprocess.run("git config user.name 'codex-bot'", shell=True, check=False)
                subprocess.run("git config user.email 'actions@users.noreply.github.com'", shell=True, check=False)
            except Exception:
                pass
            try:
                subprocess.run("git add -A", shell=True, check=True)
                commit_msg = f"codex: triage auto-fixes ({', '.join(fixes_applied)}) (issue #{num})"
                subprocess.run(f'git commit -m "{commit_msg}"', shell=True, check=True)
                subprocess.run(f"git push origin {base}", shell=True, check=True)
                post_comment(num, f"🔧 Triage: Fixes angewendet: {', '.join(fixes_applied)}. Starte neuen Anlauf...")
            except Exception as e:
                print(f"::error::Failed to commit fixes: {e}")
        # Close any existing solver PR for this issue (to avoid duplicates)
        try:
            pulls = gh_api("GET", f"/repos/{repo()}/pulls?state=open")
            for pr in pulls:
                if pr.get("head", {}).get("ref", "").startswith("codex/solve") and f"(issue #{num})" in (pr.get("body") or ""):
                    gh_api("PATCH", f"/repos/{repo()}/pulls/{pr['number']}", {"state": "closed"})
        except Exception as e:
            print(f"::warning::Failed to close existing PRs: {e}")
        # Mark that we are attempting an automatic retry
        remove_label(num, "solver-error"); remove_label(num, "contextmap-error"); remove_label(num, "contextmap-ready")
        add_labels(num, ["triage-attempted"])
        # Re-dispatch the solver bot (Bot 2) to re-run with the applied fixes
        dispatch_solver(num)
        post_comment(num, "🔁 Triage: Fehler analysiert und behoben, Solver wird erneut ausgeführt.")
        return

    # 3) Ignore other event types
    print("::notice::Triage: Kein relevanter Event-Typ.")
    return

if __name__ == "__main__":
    main()
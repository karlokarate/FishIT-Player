#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
Bot 3 – Triage (Auto-Fixer):
- Reagiert auf Labels solver-error / contextmap-error
- Holt letzte Logs (falls vorhanden), postet Analyse
- SANITIZET Issue-Diffs und versucht section-wise Apply (wie Solver), commit + Rebuild
- Genau 1 Auto-Retry (Label triage-attempted); danach triage-needed
"""

import os, sys, time, re, subprocess, requests
from pathlib import Path

def repo() -> str: return os.environ["GITHUB_REPOSITORY"]
def evname() -> str: return os.environ.get("GH_EVENT_NAME") or ""
def event() -> dict:
    p=os.environ.get("GITHUB_EVENT_PATH"); import json
    return json.loads(Path(p).read_text(encoding="utf-8")) if p and Path(p).exists() else {}

def gh_api(method: str, path: str, data=None):
    token = os.environ.get("GITHUB_TOKEN") or os.environ.get("INPUT_TOKEN")
    headers = {"Authorization": f"Bearer {token}", "Accept": "application/vnd.github+json"}
    url = f"https://api.github.com{path}"
    func = getattr(requests, method.lower(), None)
    r = func(url, headers=headers, json=data) if data is not None else func(url, headers=headers)
    try: r.raise_for_status()
    except Exception as e: print(f"::error::GH {method} {path}: {r.status_code} {r.text[:800]}")
    try: return r.json()
    except Exception: return {}

def list_issue_comments(num: int):
    return gh_api("GET", f"/repos/{repo()}/issues/{num}/comments") or []
def post_comment(num: int, body: str):
    gh_api("POST", f"/repos/{repo()}/issues/{num}/comments", {"body": body})
def add_labels(num: int, labels: list):
    gh_api("POST", f"/repos/{repo()}/issues/{num}/labels", {"labels": labels})
def remove_label(num: int, label: str):
    try:
        requests.delete(f"https://api.github.com/repos/{repo()}/issues/{num}/labels/{label}",
                        headers={"Authorization": f"Bearer {os.environ['GITHUB_TOKEN']}", "Accept": "application/vnd.github+json"})
    except Exception: pass

def issue_number_from_label_event() -> int:
    ev = event()
    if ev.get("issue") and ev["issue"].get("number"): return ev["issue"]["number"]
    return None

# --------- small git helpers ----------
def sh(cmd: str, check=True) -> str:
    p = subprocess.run(cmd, shell=True, text=True, capture_output=True)
    if check and p.returncode != 0:
        raise RuntimeError(f"cmd failed: {cmd}\nSTDOUT:\n{p.stdout}\nSTDERR:\n{p.stderr}")
    return p.stdout or ""

def ensure_repo():
    ws=os.environ.get("GITHUB_WORKSPACE")
    if ws and Path(ws).exists(): os.chdir(ws)

# --------- Sanitizer identical rules ----------
_VALID_LINE = re.compile(r"^(diff --git |index |new file mode |deleted file mode |similarity index |rename (from|to) |--- |\+\+\+ |@@ |[\+\- ]|\\ No newline at end of file)")
def _strip_code_fences(text: str) -> str:
    m = re.search(r"```(?:diff)?\s*(.*?)```", text, re.S)
    return (m.group(1) if m else text)

def sanitize_patch(raw: str) -> str:
    if not raw: return raw
    txt = _strip_code_fences(raw).replace("\r\n","\n").replace("\r","\n")
    pos = txt.find("diff --git ")
    if pos != -1: txt = txt[pos:]
    clean=[]
    for ln in txt.splitlines():
        if re.match(r"^\s*\d+\.\s*:?\s*$", ln): continue
        if re.match(r"^\s*[–—-]\s*$", ln): continue
        if _VALID_LINE.match(ln): clean.append(ln)
    if not clean: return ""
    out = "\n".join(clean)
    if not out.endswith("\n"): out += "\n"
    return out

def _split_sections(patch_text: str):
    sections=[]; it=list(re.finditer(r"^diff --git a/(\S+)\s+b/(\S+)\s*$", patch_text, re.M))
    for i,m in enumerate(it):
        start=m.start(); end=it[i+1].start() if i+1<len(it) else len(patch_text)
        sections.append(patch_text[start:end].rstrip()+"\n")
    return sections

def _gnu_patch_apply(tmp: str, p: int, rej: str):
    cmd = f"patch -p{p} -f -N --follow-symlinks --no-backup-if-mismatch -r {rej} < {tmp}"
    return subprocess.run(cmd, shell=True, text=True, capture_output=True)

def _git_apply_try(tmp: str, pstrip: int, extra: str):
    cmd = f"git apply -p{pstrip} --whitespace=fix {extra} {tmp}"
    return subprocess.run(cmd, shell=True, text=True, capture_output=True)

def _apply_one_section(sec: str, idx: int) -> bool:
    tmp=f".github/codex/_triage_sec_{idx}.patch"; Path(tmp).write_text(sec, encoding="utf-8")
    uses_ab = bool(re.search(r"^diff --git a/[\S]+ b/[\S]+", sec, re.M))
    pseq=[1,0] if uses_ab else [0,1]
    # git apply variants
    for p in pseq:
        for extra in ("", "--ignore-whitespace", "--inaccurate-eof", "--unidiff-zero", "--ignore-whitespace --inaccurate-eof"):
            r=_git_apply_try(tmp, p, extra)
            if r.returncode==0: return True
    # zero-context rewrite
    z=[]
    for ln in sec.splitlines():
        if ln.startswith("@@"): ln=re.sub(r"(@@\s+-\d+)(?:,\d+)?(\s+\+\d+)(?:,\d+)?(\s+@@.*)", r"\1,0\2,0\3", ln)
        elif ln.startswith(" "): continue
        z.append(ln)
    ztmp=f".github/codex/_triage_sec_{idx}_zc.patch"; Path(ztmp).write_text("\n".join(z)+"\n", encoding="utf-8")
    for p in pseq:
        r=_git_apply_try(ztmp, p, "--unidiff-zero --ignore-whitespace")
        if r.returncode==0: return True
    # gnu patch last
    for pp in (1,0):
        r=_gnu_patch_apply(tmp, pp, f".github/codex/_triage_sec_{idx}.rej")
        if r.returncode==0: return True
    return False

def _extract_issue_raw_diff(num: int) -> str:
    # concatenate all comment bodies (issue + comments) looking for first "diff --git"
    out=[]
    issue = gh_api("GET", f"/repos/{repo()}/issues/{num}")
    bodies=[issue.get("body") or ""]
    for c in list_issue_comments(num):
        bodies.append(c.get("body") or "")
    raw="\n\n".join(bodies)
    pos=raw.find("diff --git ")
    return raw[pos:] if pos!=-1 else raw

def main():
    ensure_repo()
    if evname()!="issues": print("::notice::not an 'issues' label event"); return
    ev=event(); num = (ev.get("issue") or {}).get("number")
    if not num: print("::error::no issue number"); return
    labels=[l.get("name") for l in (ev.get("issue") or {}).get("labels",[])]
    if not any(x in labels for x in ("solver-error","contextmap-error")):
        print("::notice::irrelevant label"); return

    # Analyse + Auto-Retry gate
    if "triage-attempted" in labels:
        remove_label(num, "solver-error"); remove_label(num, "contextmap-error"); remove_label(num, "contextmap-ready"); remove_label(num, "triage-attempted")
        add_labels(num, ["triage-needed"])
        post_comment(num, "⚠️ Triage: Automatischer Fix fehlgeschlagen – `triage-needed` gesetzt.")
        return

    # Sanitize Raw Diff aus Issue/Comments
    raw = _extract_issue_raw_diff(num)
    sanitized = sanitize_patch(raw)
    if "diff --git " not in sanitized:
        # Nichts zu tun – nur Analyse
        add_labels(num, ["triage-needed"])
        post_comment(num, "⚠️ Triage: Kein gültiger Diff im Issue gefunden – manuelle Nacharbeit nötig.")
        return

    # Section-wise Apply (wie Solver, ohne .orig)
    sections = _split_sections(sanitized)
    if not sections:
        add_labels(num, ["triage-needed"]); post_comment(num, "⚠️ Triage: Keine Sections im Diff."); return

    applied=False
    for i, sec in enumerate(sections, 1):
        if _apply_one_section(sec, i): applied=True

    if not applied:
        add_labels(num, ["triage-needed"])
        post_comment(num, "⚠️ Triage: Konnte keinen Abschnitt anwenden – manuelle Nacharbeit nötig.")
        return

    # Commit + retry build pipeline via Solver (Label)
    try:
        sh("git add -A"); sh("git commit -m 'codex: triage auto-apply (retry)'"); sh("git push")
    except Exception as e:
        add_labels(num, ["triage-needed"]); post_comment(num, f"⚠️ Triage: Commit fehlgeschlagen\n```\n{e}\n```"); return

    # Re-trigger Solver sauber
    remove_label(num, "solver-error"); remove_label(num, "contextmap-error"); remove_label(num, "contextmap-ready")
    add_labels(num, ["triage-attempted", "contextmap-ready"])
    post_comment(num, "🔁 Triage: Sanitized Diff angewendet, Solver wird erneut gestartet.")
    return

if __name__ == "__main__":
    main()

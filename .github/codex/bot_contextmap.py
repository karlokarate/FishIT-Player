#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Bot 1 — ContextMap (projektneutral, Shared-Lib)
-----------------------------------------------------------------
Erzeugt:
- .github/codex/context/run-<id>-issue-<#>-<sha7>/solver_input.json
- .github/codex/context/run-.../solver_task.json    (NEU: ausführbares Mandat für Bot 2)
- .github/codex/context/run-.../summary.md          (Problem/Lösungsweg in eigenen Worten)
- (Legacy) .github/codex/context/run-.../solver_plan.json (nur minimale Kompatibilität für alten Solver)

Funktionen:
- Issue-Parse (issues/issue_comment/workflow_dispatch)
- Repo-Scan & Stack-/Keyword-Analyse
- Kandidaten-/Modul-Erkennung (heuristisch, projektneutral)
- Allowed-Targets & Execution-Guardrails (strict by default)
- Dispatch an Bot 2 (direkt via workflow_dispatch: codex-solve.yml)
- Kommentar im Issue (eigene Zusammenfassung; kein Copy-Paste)
- Fallback: Wenn kein Issue-Kontext vorhanden ist, wird NICHT kommentiert, aber Artefakte/Step-Summary werden geschrieben.

Benötigt: requests, openai (für AI-Zusammenfassung optional), chardet (optional)
Kompatibel mit der Shared-Lib in `.github/codex/lib/`.
"""

from __future__ import annotations
import os, re, sys, json, time, logging
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

# ---------- Shared-Lib import ----------
LIB_CANDIDATES = [
    os.path.join(os.getcwd(), ".github", "codex", "lib"),
    os.path.join(os.path.dirname(__file__), "lib"),
]
for _lib in LIB_CANDIDATES:
    if os.path.isdir(_lib) and _lib not in sys.path:
        sys.path.insert(0, _lib)

try:
    import gh, io_utils, repo_utils, profiles, task_schema, logging_utils
    try:
        import ai_utils
        HAVE_AI = True
    except Exception:
        HAVE_AI = False
except Exception as e:
    print("::error::Shared-Library nicht gefunden. Lege `.github/codex/lib/` an und füge die Module hinzu.")
    raise

# ---------- Konfiguration ----------
logging.basicConfig(level=logging.INFO, format="%(levelname)s: %(message)s")

POST_ISSUE_COMMENT = os.getenv("CODEX_POST_ISSUE_COMMENT", "true").strip().lower() in {"1", "true", "yes"}
ADD_LABEL          = os.getenv("CODEX_ADD_LABEL", "true").strip().lower() in {"1", "true", "yes"}
NOTIFY_SOLVER      = os.getenv("CODEX_NOTIFY_SOLVER", "true").strip().lower() in {"1", "true", "yes"}
DEEP_REASONING     = os.getenv("CODEX_DEEP_REASONING", "true").strip().lower() in {"1", "true", "yes"}

# Größenlimits (Text-/Binär-Einbettung für solver_input.json)
MAX_TEXT_BYTES     = int(os.getenv("CODEX_MAX_TEXT_EMBED_BYTES", "1048576"))  # 1 MiB
MAX_BIN_B64_BYTES  = int(os.getenv("CODEX_MAX_BIN_BASE64_BYTES", "524288"))   # 512 KiB

RUN_ID   = os.getenv("GITHUB_RUN_ID", str(int(time.time())))
SHORT_SHA = (os.getenv("GITHUB_SHA", "")[:7] or "nosha")

# ---------- Hilfsfunktionen ----------

def _event_ctx() -> Dict[str, Any]:
    ev = os.getenv("GITHUB_EVENT_NAME", "") or os.getenv("GH_EVENT_NAME", "")
    payload = gh.event_payload()
    ictx = {"event_name": ev, "issue_number": None, "title": "", "body": ""}

    if ev == "issues":
        issue = payload.get("issue") or {}
        ictx["issue_number"] = issue.get("number")
        ictx["title"] = issue.get("title", "") or ""
        ictx["body"] = issue.get("body", "") or ""
    elif ev == "issue_comment":
        issue = payload.get("issue", {}) or {}
        comment = payload.get("comment", {}) or {}
        ictx["issue_number"] = issue.get("number")
        ictx["title"] = issue.get("title", "") or ""
        ictx["body"] = comment.get("body", "") or ""
    else:  # workflow_dispatch or other
        inputs = payload.get("inputs", {}) or {}
        ictx["title"] = inputs.get("title", "") or ""
        ictx["body"] = inputs.get("body", "") or ""
        # Allow manual override by input "issue"
        try:
            if (inputs.get("issue") or "").strip().isdigit():
                ictx["issue_number"] = int(inputs.get("issue"))
        except Exception:
            pass
    return ictx

_TEXT_LIKE_EXT = (".gradle",".kts",".xml",".md",".txt",".json",".yml",".yaml",".kt",".java",
                  ".py",".sh",".bat",".ps1",".properties",".cfg",".ini",".csv",".proto",".tl",
                  ".go",".rs",".swift",".dart",".html",".css",".scss",".ts",".tsx",".jsx",
                  ".c",".h",".cc",".cpp",".hpp",".sql")

def _read_repo_file(path: str, always_embed: bool = False, tracked_set: Optional[set] = None) -> Dict[str, Any]:
    p = Path(path)
    info: Dict[str, Any] = {"path": path, "size": None, "sha256": None, "is_text": None, "encoding": None}
    try:
        b = p.read_bytes()
    except Exception as e:
        info["error"] = f"read_error: {type(e).__name__}: {e}"
        return info
    info["size"] = len(b)
    info["sha256"] = io_utils.file_sha256(b)
    text_like = p.suffix.lower() in _TEXT_LIKE_EXT
    is_text = io_utils.is_probably_text(b) or text_like
    info["is_text"] = bool(is_text)
    if is_text:
        enc = io_utils.detect_encoding(b)
        info["encoding"] = enc
        if always_embed or len(b) <= MAX_TEXT_BYTES:
            info["text"] = b.decode(enc, errors="replace")
        else:
            info["text"] = b.decode(enc, errors="replace")[:MAX_TEXT_BYTES]
    else:
        if always_embed or len(b) <= MAX_BIN_B64_BYTES:
            import base64
            info["base64"] = base64.b64encode(b).decode("ascii")
        else:
            info["binary_summary"] = {"note": "binary too large", "size": len(b)}
    return info

def _detect_profile_and_commands(files: List[str]) -> Tuple[str, Dict[str, Any]]:
    prof = profiles.detect_language_profile(files)
    cmds = profiles.resolve_commands(prof)
    return prof, cmds

def _build_allowed_targets(candidates: List[str], suggested_new: List[str]) -> Tuple[Dict[str, Any], Dict[str, Any]]:
    modify: List[str] = []
    create: List[str] = []
    tests:  List[str] = ["tests/**", "test/**"]

    seen = set()
    for f in candidates[:120]:
        if "/" in f and not f.endswith("/"):
            parent = str(Path(f).parent).replace("\\", "/")
            if f not in seen:
                modify.append(f); seen.add(f)
            if parent and parent not in seen:
                modify.append(parent + "/**"); seen.add(parent + "/**")

    for nf in suggested_new[:60]:
        if nf not in create:
            create.append(nf)

    allowed = {"modify": sorted(set(modify)), "create": sorted(set(create)), "tests": sorted(set(tests))}
    execution = {"strict_mode": True, "dir_rewrite_allowed": False}
    return allowed, execution

def _summarize_issue_own_words(issue_ctx: Dict[str, Any], analysis: Dict[str, Any]) -> str:
    if HAVE_AI and (os.getenv("OPENAI_API_KEY") or "").strip():
        try:
            return ai_utils.summarize_issue_in_own_words(issue_ctx, analysis)
        except Exception as e:
            logging.warning("AI-Summary fehlgeschlagen, Fallback: %s", e)

    title = (issue_ctx.get("title") or "").strip() or "(no title)"
    candidates = analysis.get("candidate_files") or []
    lines = [
        "## Problem (condensed)",
        f"{title}",
        "",
        "## Proposed solution path",
        "- Reproduce based on provided steps or minimal repro.",
        "- Focus changes on the most likely affected files:",
    ] + [f"  - `{c}`" for c in candidates[:10]] + [
        "- Add/adjust tests to prove the fix.",
    ]
    return "\n".join(lines)

def _keywords_from_ctx(title: str, body: str) -> List[str]:
    txt = (title or "") + " " + (body or "")
    kws = repo_utils.extract_keywords(txt, limit=15)
    # Safety: ensure we don't pick nonsense if both are empty
    return kws or ["bug", "error", "crash"]

# ---------- Main ----------

def main():
    ictx = _event_ctx()
    issue_no = ictx.get("issue_number")
    title = ictx.get("title", "") or ""
    body  = ictx.get("body", "") or ""

    paths = io_utils.context_paths(issue_no, RUN_ID, SHORT_SHA)
    RUN_DIR = paths["RUN_DIR"]
    SOLVER_INPUT = paths["SOLVER_INPUT"]
    SOLVER_TASK  = paths["SOLVER_TASK"]
    SOLVER_PLAN  = paths["SOLVER_PLAN"]
    SUMMARY_MD   = paths["SUMMARY"]

    all_files = repo_utils.list_all_files()
    lang_stats = repo_utils.language_stats(all_files)
    stack = repo_utils.detect_stack_files(all_files)
    largest = sorted(all_files, key=lambda p: (Path(p).stat().st_size if Path(p).exists() else 0), reverse=True)[:15]
    topdirs = repo_utils.top_dirs_by_count(all_files, 10)

    # robust: keywords even if title/body empty
    keywords = _keywords_from_ctx(title, body)
    candidates = repo_utils.pick_candidate_files(all_files, keywords, limit=30)

    def _suggest_new_files(keywords: List[str], stack: Dict[str, List[str]], top_langs: List[str], issue_no: Optional[int]) -> List[str]:
        base = []
        key = keywords[0] if keywords else "feature"
        n = issue_no or int(time.time())
        base += [f"tests/{key}_spec.md", f"docs/issue-{n}-decision-record.md"]
        if "python" in stack or "py" in top_langs: base.append(f"tests/test_{key}.py")
        if "nodejs" in stack or "ts" in top_langs or "js" in top_langs: base.append(f"tests/{key}.spec.ts")
        if "java_gradle" in stack or "android" in stack: base.append(f"{key}/src/test/java/.../{key.capitalize()}Test.java")
        if ".net" in stack: base.append(f"tests/{key}.Tests.cs")
        if "go" in stack or "go" in top_langs: base.append(f"{key}/{key}_test.go")
        return sorted(set(base))

    top_lang_keys = [k for k, _ in list(lang_stats.items())[:5]]
    new_files = _suggest_new_files(keywords, stack, top_lang_keys, issue_no)

    tracked_set = set()
    file_index: Dict[str, Any] = {}
    total_bytes_embedded = 0
    for p in all_files:
        info = _read_repo_file(p, always_embed=False, tracked_set=tracked_set)
        file_index[p] = info
        if info.get("is_text") and "text" in info:
            total_bytes_embedded += len((info["text"] or "").encode("utf-8", errors="replace"))
        elif not info.get("is_text") and "base64" in info:
            total_bytes_embedded += len(info.get("base64",""))

    def _segment_sections(body: str) -> Dict[str, str]:
        header_patterns = [
            (r"(?im)^#{1,6}\s*expected(?:\s+behavior)?|^erwartetes\s+verhalten\s*:?", "expected"),
            (r"(?im)^#{1,6}\s*actual(?:\s+behavior)?|^tatsächliches\s+verhalten\s*:?|^ist\s*:?", "actual"),
            (r"(?im)^#{1,6}\s*steps(?:\s+to\s+repro(duce)?)?|^schritte\s*(zur|zum)\s*reproduktion|^repro\s*steps\s*:?", "steps"),
            (r"(?im)^#{1,6}\s*environment|^umgebung\s*:?", "env"),
        ]
        sections = {"expected":"", "actual":"", "steps":"", "env":""}
        idx = []
        for pat, key in header_patterns:
            for m in re.finditer(pat, body or ""):
                idx.append((m.start(), key))
        idx.sort()
        if not idx:
            return sections
        idx.append((len(body), "_end"))
        for i in range(len(idx)-1):
            start, key = idx[i]; end = idx[i+1][0]
            start_line = body.rfind("\n", 0, start) + 1
            sec_text = body[start_line:end].strip()
            sec_text = re.sub(r"(?im)^#{1,6}\s*[^\n]*\n?", "", sec_text, count=1).strip()
            if key in sections and sec_text:
                sections[key] = sec_text
        return sections

    def _extract_signals(body: str) -> Dict[str, List[str]]:
        lines = (body or "").splitlines()
        errors, traces, codeblocks = [], [], []
        for m in re.finditer(r"```(?:[a-zA-Z0-9_\-]+)?\n(.*?)\n```", body or "", flags=re.DOTALL):
            codeblocks.append(m.group(1).strip())
        for ln in lines:
            l = ln.strip()
            if re.search(r"\b(exception|traceback|stacktrace|error|fatal|panic)\b", l, re.IGNORECASE):
                errors.append(l)
            if l.startswith("at ") or l.startswith("File ") or re.match(r".*\(\w+\.\w+:\d+\)", l):
                traces.append(l)
        return {"errors": errors[:20], "traces": traces[:50], "codeblocks": codeblocks[:5]}

    sections = _segment_sections(body)
    signals  = _extract_signals(body)

    analysis = {
        "language_bytes_kb": lang_stats,
        "stack_indicators": stack,
        "largest_files": [{"path": p, "bytes": (Path(p).stat().st_size if Path(p).exists() else 0)} for p in largest],
        "top_dirs_by_file_count": topdirs,
        "repo_size_kb": round(sum(Path(p).stat().st_size for p in all_files if Path(p).exists())/1024, 2) if all_files else 0,
        "file_count": len(all_files),
        "issue_sections": sections,
        "issue_signals": signals,
        "keywords": keywords,
        "candidate_files": candidates,
        "suggested_new_files": new_files,
    }

    issue_ctx = {
        "number": issue_no,
        "title": title,
        "body": body,
        "sections": sections,
        "signals": signals,
    }
    summary_md = _summarize_issue_own_words(issue_ctx, analysis)

    language_profile, cmds = _detect_profile_and_commands(all_files)

    allowed_targets, execution = _build_allowed_targets(candidates, new_files)

    ak = [
        "Repro-Szenario verläuft fehlerfrei.",
        "Alle CI-Checks grün, keine Regression.",
        "Neue/angepasste Tests decken die Änderung(en) ab."
    ]
    if language_profile == "node":
        ak.append("Linter & Typecheck ohne Fehler.")
    if language_profile == "java-gradle":
        ak.append("Gradle build & Unit-Tests ohne Fehler.")

    plan_items: List[Dict[str, Any]] = []
    for i, path in enumerate(candidates[:10], 1):
        plan_items.append({
            "id": f"S{i}",
            "title": f"Überarbeite {Path(path).name}",
            "paths": [path],
            "steps": [
                "Ursache isolieren (Logs & Repro).",
                "Minimal-invasive Korrektur implementieren.",
                "Regressionstest(e) ergänzen/aktualisieren."
            ]
        })

    def _top_module(p: str) -> str:
        return (Path(p).parts[0] if "/" in p else Path(p).name)
    impacted_modules = sorted({ _top_module(p) for p in candidates if p })

    issue_info = {"number": issue_no or 0, "title": title or "", "url": f"https://github.com/{gh.repo()}/issues/{issue_no}" if issue_no else ""}
    build_cfg = {"fmt": cmds.get("fmt") or [], "cmd": cmds.get("build") or []}
    test_cfg  = {"cmd": cmds.get("test") or []}

    solver_task = task_schema.build(
        issue=issue_info,
        problem_summary=summary_md.split("\n")[1] if "## Problem" in summary_md else (title or "Problem summary"),
        acceptance_criteria=ak,
        scope={**allowed_targets, **execution},
        plan=plan_items,
        impacted_modules=impacted_modules or list((stack.keys())),
        language_profile=language_profile,
        build_cfg=build_cfg,
        test_cfg=test_cfg,
        notes=["Generated by Bot 1 (ContextMap)."]
    )

    context_out = {
        "meta": {
            "generated_at": time.strftime("%Y-%m-%d %H:%M:%S %z"),
            "repo": gh.repo(),
            "event_name": os.getenv("GITHUB_EVENT_NAME", ""),
            "ref": os.getenv("GITHUB_REF", ""),
            "sha": os.getenv("GITHUB_SHA", ""),
            "runner_os": os.getenv("RUNNER_OS", ""),
            "max_text_embed_bytes": MAX_TEXT_BYTES,
            "max_bin_base64_bytes": MAX_BIN_B64_BYTES,
            "total_bytes_embedded_estimate": total_bytes_embedded,
        },
        "issue_context": issue_ctx,
        "attachments": [],
        "repo": {"files": all_files, "file_index": file_index},
        "analysis": analysis,
    }

    solver_plan = {
        "issue": {"number": issue_no, "title": title, "summary": (body or "").strip()[:800]},
        "allowed_targets": allowed_targets,
        "execution": execution,
        "artifacts": {
            "context_json_path": os.path.relpath(SOLVER_INPUT, os.getcwd()).replace("\\", "/"),
            "plan_json_path": os.path.relpath(SOLVER_PLAN, os.getcwd()).replace("\\", "/"),
            "artifact_bundle": "codex-context"
        }
    }

    io_utils.write_json(SOLVER_INPUT, context_out)
    io_utils.write_json(SOLVER_PLAN, solver_plan)
    task_schema.write(SOLVER_TASK, solver_task)
    io_utils.write_text(SUMMARY_MD, summary_md, encoding="utf-8")

    io_utils.write_json(paths["LAST_RUN"], {
        "run_id": RUN_ID, "issue_number": issue_no, "short_sha": SHORT_SHA,
        "run_dir": RUN_DIR.replace("\\", "/"),
        "context": "solver_input.json", "task": "solver_task.json", "plan": "solver_plan.json"
    })

    # Kommentare nur, wenn Issue-Nummer bekannt
    if POST_ISSUE_COMMENT and issue_no:
        head = f"### ContextMap ready ✅\n\n"
        body_md = head + summary_md + "\n\n" + "#### Impacted modules\n" + "".join(f"- `{m}`\n" for m in (impacted_modules or [])) + \
                  "\n\n" + "#### Candidate files (Top 10)\n" + "".join(f"- `{p}`\n" for p in candidates[:10])
        gh.post_comment(issue_no, body_md)
        if ADD_LABEL:
            try:
                gh.add_labels(issue_no, ["contextmap-ready"])
            except Exception:
                pass
    else:
        # Kein Issue-Kontext (z. B. workflow_dispatch ohne inputs.issue) → Step Summary informieren
        logging_utils.add_step_summary("ℹ️ Bot 1 lief im workflow_dispatch ohne Issue-Nummer. Es wurden Artefakte erzeugt, aber kein Kommentar gepostet.")
        print("No issue number provided; skipping issue comment/label.")

    # Solver triggern: direktes workflow_dispatch (kein repository_dispatch, um 403 zu vermeiden)
    if NOTIFY_SOLVER and issue_no:
        try:
            gh.dispatch_workflow("codex-solve.yml", gh.default_branch(), inputs={"issue": str(issue_no)})
            logging.info("workflow_dispatch → codex-solve.yml gestartet")
        except Exception as e:
            logging.warning("Solver-Dispatch fehlgeschlagen: %s", e)

    # Step-Summary
    top_lang_preview = ", ".join(f"{k}:{v}KB" for k, v in list(lang_stats.items())[:3]) or "n/a"
    preview_files = "\n".join(f"- `{p}`" for p in candidates[:10]) or "- (keine Kandidaten erkannt)"
    summary_lines = [
        f"Issue #{issue_no} — {title.strip()}",
        f"Top-Languages: {top_lang_preview}",
        f"Kandidaten (Top 10):\n{preview_files}",
        f"Artefakte: solver_input.json, solver_task.json, solver_plan.json, summary.md"
    ]
    logging_utils.add_step_summary("\n".join(summary_lines))
    print("\n".join(summary_lines))

if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        print(f"::error::Bot 1 failed: {e}")
        raise

# -*- coding: utf-8 -*-
"""
OpenAI client wrappers shared across bots.
"""

from __future__ import annotations
import os, re, textwrap, json
from typing import Dict, Any

# Optionales Debug (GITHUB_STEP_SUMMARY/Log): CODEX_DEBUG=true
DEBUG = os.getenv("CODEX_DEBUG", "false").strip().lower() in {"1","true","yes"}

def _client():
    from openai import OpenAI
    api_key = os.environ.get("OPENAI_API_KEY")
    if not api_key:
        raise RuntimeError("OPENAI_API_KEY not set")
    base = os.environ.get("OPENAI_BASE_URL") or "https://api.openai.com/v1"
    if DEBUG:
        print(f"[ai_utils] base={base}, key_present={bool(api_key)}")
    return OpenAI(api_key=api_key, base_url=base)

def _model() -> str:
    return os.environ.get("OPENAI_MODEL_DEFAULT", "gpt-5")

def _effort() -> str:
    return os.environ.get("OPENAI_REASONING_EFFORT", "high")

def strip_code_fences(txt: str) -> str:
    if not txt:
        return txt
    m = re.match(r"^```[a-zA-Z0-9_-]*\s*\n(.*?)\n```$", txt.strip(), re.S)
    return (m.group(1) if m else txt)

def _call(system: str, user: str) -> str:
    client = _client()
    rsp = client.responses.create(
        model=_model(),
        input=[{"role":"system","content":system},{"role":"user","content":user}],
        reasoning={"effort": _effort()}
    )
    # v1 SDK convenience:
    txt = getattr(rsp, "output_text", "") or ""
    if not txt:
        # Vorsichtiger Fallback, falls output_text leer ist
        try:
            chunks = []
            for item in getattr(rsp, "output", []) or []:
                for part in getattr(item, "content", []) or []:
                    t = getattr(part, "text", None)
                    if t:
                        chunks.append(t)
            txt = "\n".join(chunks).strip()
        except Exception:
            pass
    return strip_code_fences(txt or "")

def summarize_issue_in_own_words(issue_context: Dict[str, Any], analysis: Dict[str, Any]) -> str:
    """
    Returns a concise problem statement and solution path written by the bot (no copy-paste).
    """
    SYSTEM = "You are a senior triage engineer. Summarize the problem in your own words and propose a solution path. Keep it concise and concrete."
    USER = textwrap.dedent(f"""
    Issue Context (title/body/sections/signals):
    {json.dumps(issue_context, ensure_ascii=False)}

    Repo Analysis (langs/stack/candidates):
    {json.dumps(analysis, ensure_ascii=False)}

    Output: Markdown. Headings: 'Problem (condensed)' and 'Proposed solution path'.
    """).strip()
    return _call(SYSTEM, USER)

def explain_change(path: str, diff_or_desc: str, task_hint: Dict[str, Any]) -> str:
    SYSTEM = "You are an engineering assistant. Explain briefly WHY this change is necessary to resolve the issue."
    USER = f"Path: {path}\nChange:\n{diff_or_desc[:5000]}\n\nTask hint:\n{json.dumps(task_hint, ensure_ascii=False)[:4000]}\n\nOutput: 1-3 sentences."
    return _call(SYSTEM, USER)

def generate_diff(context_snippet: str, repo_docs: str, targets_preview: str) -> str:
    SYSTEM = (
        "You are a senior engineer. Generate ONE unified diff at repo root. Use a/b prefixes. "
        "Headers: diff --git, index, --- a/..., +++ b/..., @@. New files must use /dev/null header. LF only; trailing newline."
    )
    USER = f"Context:\n{context_snippet}\n\nDocs:\n{repo_docs}\n\nTargets:\n{targets_preview}\n\nOutput: ONE unified diff, no prose."
    return _call(SYSTEM, USER)

def generate_full_file(path: str, symbols_preview: str, context_full: str, summary_ctx: str) -> str:
    SYSTEM = "Produce a COMPLETE file content for the given path. No placeholders. No fences. Use inferred package if language suggests."
    USER = f"Path: {path}\nSymbols:\n{symbols_preview[:6000]}\n\nShort Context:\n{summary_ctx[:4000]}\n\nFull Context:\n{context_full[:8000]}"
    return _call(SYSTEM, USER)

def rewrite_full_file(path: str, current: str, symbols_preview: str, context_full: str, summary_ctx: str) -> str:
    SYSTEM = "Rewrite the entire file to satisfy the context. Output only the new file content (no fences)."
    USER = f"Path: {path}\nCurrent:\n```text\n{current[:8000]}\n```\nSymbols:\n{symbols_preview[:6000]}\n\nShort Context:\n{summary_ctx[:4000]}\n\nFull Context:\n{context_full[:8000]}"
    return _call(SYSTEM, USER)
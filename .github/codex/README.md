# Codex Shared Library (drop-in for `.github/codex/lib`)

This folder contains shared, **project-neutral** helpers for the 3-bot workflow:

- `gh.py` — GitHub API helpers (issues, labels, dispatch, step summary).
- `io_utils.py` — filesystem, encoding, artifact run-path helpers.
- `patching.py` — diff sanitization and robust apply strategies.
- `repo_utils.py` — repo scan, stack detection, symbols, keyword candidates.
- `logging_utils.py` — heartbeat + step summary.
- `scopes.py` — allowed-targets + execution guardrails (reads `solver_task.json` or legacy `solver_plan.json`).
- `profiles.py` — language build/test/fmt profiles + detection.
- `ai_utils.py` — OpenAI wrappers (summaries, explain change, generate diffs/files).
- `task_schema.py` — `solver_task.json` schema & builder.

## How to install

Copy the folder to your repo:
```
.github/codex/lib/
```

Then import from your bots, e.g.:
```python
from .github.codex.lib import gh, io_utils, patching, repo_utils, logging_utils, scopes, profiles, ai_utils, task_schema
```

All modules are pure-Python and rely only on `requests`, `openai`, and optionally `chardet` (already in your requirements).

#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
Bot 1 – ContextMap (Planner)  [HIGH reasoning]
- Trigger: Issue-Body beginnt mit '/codex' (nicht Kommentar, nicht Titel)
- Liest optional Doku: AGENTS.md, ARCHITECTURE_OVERVIEW.md, ROADMAP.md, CHANGELOG.md
- Ermittelt existierende Dateien/Module + Gradle/Manifest
- Extrahiert aus dem Issue erwähnte Dateipfade und prüft deren Existenz
- Baut Symbol-Index (Kotlin: package, class/object/data class, @Composable, public fun)
- Erzeugt:
    1) kurze ContextMap (Kommentar-tauglich)
    2) vollständigen ungekürzten Kontext (.codex/context_full.md)
    3) strukturierte JSON (.codex/context.json) für den Solver
- Aktualisiert bestehenden ContextMap-Kommentar oder erstellt neuen
- Labels: contextmap-ready (Erfolg), contextmap-error (Fehler)
- Optional: triggert Bot 2 (Solver) via workflow_dispatch (auskommentiert)

Deps: openai, requests
"""

from __future__ import annotations
import os, json, re, subprocess, sys
from pathlib import Path
from typing import List, Optional, Dict, Any
import requests

# ---------------- GitHub Utils ----------------

def gh_repo() -> str:
    return os.environ["GITHUB_REPOSITORY"]

def gh_event_path() -> str:
    return os.environ["GITHUB_EVENT_PATH"]

def gh_event_name() -> str:
    return os.environ.get("GH_EVENT_NAME") or os.environ.get("GITHUB_EVENT_NAME","")

def gh_api(method: str, path: str, payload: dict | None = None) -> dict:
    url = f"https://api.github.com{path}"
    headers = {
        "Authorization": f"Bearer {os.environ['GITHUB_TOKEN']}",
        "Accept": "application/vnd.github+json",
    }
    resp = requests.request(method, url, headers=headers, json=payload, timeout=60)
    resp.raise_for_status()
    try:
        return resp.json()
    except Exception:
        return {}

def issue_number_from_event() -> Optional[int]:
    ev = json.loads(Path(gh_event_path()).read_text(encoding="utf-8"))
    if gh_event_name() in ("issues", "issue_comment"):
        issue = ev.get("issue") or {}
        return issue.get("number")
    if gh_event_name() == "workflow_dispatch":
        return None
    return None

def read_issue_body() -> str:
    ev = json.loads(Path(gh_event_path()).read_text(encoding="utf-8"))
    if gh_event_name() in ("issues","issue_comment"):
        return (ev.get("issue") or {}).get("body","") or ""
    if gh_event_name() == "workflow_dispatch":
        return os.environ.get("DISPATCH_COMMENT","") or ""
    return ""

def list_issue_comments(issue_number: int) -> List[dict]:
    data = gh_api("GET", f"/repos/{gh_repo()}/issues/{issue_number}/comments")
    return data if isinstance(data, list) else []

def upsert_contextmap_comment(issue_number: int, markdown: str) -> None:
    comments = list_issue_comments(issue_number)
    marker = "### contextmap-ready"
    target_id = None
    for c in comments:
        body = c.get("body","") or ""
        if body.strip().startswith(marker):
            target_id = c.get("id"); break
    if target_id:
        gh_api("PATCH", f"/repos/{gh_repo()}/issues/comments/{target_id}", {"body": markdown})
    else:
        gh_api("POST", f"/repos/{gh_repo()}/issues/{issue_number}/comments", {"body": markdown})

def add_label(issue_number: int, label: str) -> None:
    gh_api("POST", f"/repos/{gh_repo()}/issues/{issue_number}/labels", {"labels":[label]})

def remove_label(issue_number: int, label: str) -> None:
    try:
        requests.delete(
            f"https://api.github.com/repos/{gh_repo()}/issues/{issue_number}/labels/{label}",
            headers={"Authorization": f"Bearer {os.environ['GITHUB_TOKEN']}","Accept":"application/vnd.github+json"},
            timeout=60
        )
    except Exception:
        pass

# ---------------- Repo scan ----------------

def run(cmd: str) -> str:
    try:
        out = subprocess.run(cmd, shell=True, text=True, capture_output=True, check=True)
        return out.stdout
    except Exception:
        return ""

def git_ls_files() -> List[str]:
    out = run("git ls-files")
    return [l.strip() for l in out.splitlines() if l.strip()]

def discover_modules_and_important_paths(files: List[str]) -> List[str]:
    paths=set(); pset=set(files)
    for f in files:
        if f.endswith(("build.gradle","build.gradle.kts","settings.gradle","settings.gradle.kts")):
            paths.add(f)
        if f.endswith("AndroidManifest.xml"):
            paths.add(f)
    # typische Modulwurzeln
    for f in files:
        if f.endswith(("build.gradle","build.gradle.kts")):
            paths.add(Path(f).parent.as_posix())
    for d in ("app","core","data","domain","ui"):
        if any(x==d or x.startswith(d+"/") for x in pset):
            paths.add(d)
    return sorted(paths)

def extract_issue_paths(issue_text: str) -> List[str]:
    """
    Sammelt verdächtige/erwähnte Pfade aus dem Issue:
    - diff --git a/... b/...
    - +++ b/... / --- a/...
    - wörtliche Pfade in Codeblöcken/Backticks
    """
    candidates=set()
    for m in re.finditer(r"diff --git a/([^\s]+) b/([^\s]+)", issue_text):
        candidates.add(m.group(1)); candidates.add(m.group(2))
    for m in re.finditer(r"^\+\+\+ b/([^\s]+)", issue_text, flags=re.MULTILINE):
        candidates.add(m.group(1))
    for m in re.finditer(r"^--- a/([^\s]+)", issue_text, flags=re.MULTILINE):
        candidates.add(m.group(1))
    for m in re.finditer(r"[`\"]([A-Za-z0-9_\-./]+\.kt)[`\"]", issue_text):
        candidates.add(m.group(1))
    return sorted(p for p in candidates if not re.match(r"^https?://", p))

def build_symbol_index(files: List[str], limit:int=1200) -> Dict[str, Any]:
    """
    Liest Kotlin-Dateien und extrahiert:
      - package
      - class/object/data class Namen
      - @Composable fun Namen
      - public fun Signaturen (abgespeckt)
    Limitiert, damit Prompt klein bleibt.
    """
    symbols=[]
    for f in files:
        if not f.endswith(".kt"): continue
        if "/build/" in f or "/generated/" in f: continue
        p = Path(f)
        try:
            t = p.read_text(encoding="utf-8", errors="replace")
        except Exception:
            continue
        pkg = re.search(r"^\s*package\s+([a-zA-Z0-9_.]+)", t, flags=re.MULTILINE)
        pkgname = pkg.group(1) if pkg else ""
        for line in t.splitlines():
            line=line.strip()
            m_class = re.match(r"(?:public\s+)?(?:data\s+)?class\s+([A-Za-z0-9_]+)", line)
            m_obj   = re.match(r"(?:public\s+)?object\s+([A-Za-z0-9_]+)", line)
            m_comp  = re.match(r"@Composable\s+fun\s+([A-Za-z0-9_]+)\(", line)
            m_fun   = re.match(r"(?:public\s+)?fun\s+([A-Za-z0-9_]+)\(", line)
            if m_class:
                symbols.append({"file":f,"package":pkgname,"kind":"class","name":m_class.group(1)})
            elif m_obj:
                symbols.append({"file":f,"package":pkgname,"kind":"object","name":m_obj.group(1)})
            elif m_comp:
                symbols.append({"file":f,"package":pkgname,"kind":"composable","name":m_comp.group(1)})
            elif m_fun:
                symbols.append({"file":f,"package":pkgname,"kind":"fun","name":m_fun.group(1)})
            if len(symbols) >= limit:
                break
        if len(symbols) >= limit:
            break
    return {"count": len(symbols), "items": symbols}

# ---------------- OpenAI ----------------

def load_docs_text() -> str:
    docs=[]
    for name in ["AGENTS.md","ARCHITECTURE_OVERVIEW.md","ROADMAP.md","CHANGELOG.md"]:
        if Path(name).exists():
            t = Path(name).read_text(encoding="utf-8", errors="replace")
            docs.append(f"\n--- {name} ---\n{t}")
    return "".join(docs) if docs else ""

def call_openai_context_full(issue_text: str, allowed_paths: List[str], existence_table: str, symbol_preview: str) -> str:
    """
    Liefert den VOLLEN Kontext (unkürzer), der anschließend als .codex/context_full.md gespeichert wird.
    """
    from openai import OpenAI
    api_key = os.environ.get("OPENAI_API_KEY")
    if not api_key:
        raise RuntimeError("OPENAI_API_KEY not set")
    base = os.environ.get("OPENAI_BASE_URL") or "https://api.openai.com/v1"
    client = OpenAI(api_key=api_key, base_url=base)
    model = os.environ.get("OPENAI_MODEL_DEFAULT","gpt-5")
    effort = os.environ.get("OPENAI_REASONING_EFFORT","high")

    docs = load_docs_text()
    allowed_preview = "\n".join(allowed_paths[:500])

    system = (
        "You are a senior planning assistant for repository refactoring and solver preparation.\n"
        "Create a thorough, exhaustive CONTEXT for the Solver with HIGH reasoning.\n"
        "No code patches. Provide analysis, file targets (existing vs missing), risks, assumptions, and a step-by-step solver-prep plan."
    )

    user = f"""Issue (body starting with /codex):
{issue_text}

Allowed paths (subset preview):
{allowed_preview}

Existenz-Check der im Issue erwähnten Pfade:
{existence_table}

Symbol-Vorschau (gekürzt):
{symbol_preview}

Docs (AGENTS → ARCHITECTURE_OVERVIEW → ROADMAP → CHANGELOG):
{docs if docs else '(no docs present)'}
"""
    resp = client.responses.create(
        model=model,
        input=[{"role":"system","content":system},{"role":"user","content":user}],
        reasoning={"effort": effort},
    )
    return getattr(resp,"output_text","").strip()

def call_openai_context_summary(issue_text: str, allowed_paths: List[str], existence_table: str, symbol_preview: str) -> str:
    """
    Liefert eine KOMPAKTE ContextMap (Kommentar-tauglich) – strikt unter ~9.5k Zeichen.
    """
    from openai import OpenAI
    api_key = os.environ.get("OPENAI_API_KEY")
    if not api_key:
        raise RuntimeError("OPENAI_API_KEY not set")
    base = os.environ.get("OPENAI_BASE_URL") or "https://api.openai.com/v1"
    client = OpenAI(api_key=api_key, base_url=base)
    model = os.environ.get("OPENAI_MODEL_DEFAULT","gpt-5")
    effort = os.environ.get("OPENAI_REASONING_EFFORT","high")

    docs = load_docs_text()
    allowed_preview = "\n".join(allowed_paths[:300])

    system = (
        "You are a senior planning assistant for repository refactoring and solver preparation.\n"
        "Create a concise CONTEXT MAP in German for the Solver with HIGH reasoning.\n"
        "STRICT RULES:\n"
        "- Output <= 9500 characters total.\n"
        "- The '(potentiell) betroffene Module' list MUST ONLY contain paths from the provided 'Allowed paths'.\n"
        "- Prefer modules/files that actually exist; if the issue mentions missing files, call that out in 'Unklarheiten' and 'Nächste Schritte'.\n"
        "- No code output. Planning only."
    )

    user = f"""Issue (body starting with /codex):
{issue_text}

Allowed paths (choose only from these; include Gradle/Manifest as needed):
{allowed_preview}

Existenz-Check der im Issue erwähnten Pfade:
{existence_table}

Symbol-Vorschau (gekürzt):
{symbol_preview}

Docs (AGENTS → ARCHITECTURE_OVERVIEW → ROADMAP → CHANGELOG):
{docs if docs else '(no docs present)'}

Erzeuge eine prägnante deutsche Markdown-ContextMap in genau dieser Struktur:

### contextmap-ready
#### Problemzusammenfassung
(max. 8 Zeilen, nur Kernthema und Scope)

#### (potentiell) betroffene Module
- <pfad1>
- <pfad2>
(nur Pfade aus 'Allowed paths', priorisiert nach Relevanz)

#### Annahmen
- …

#### Unklarheiten
- (z. B. Pfade, die im Issue genannt, aber im Repo nicht vorhanden sind; Open-Points)

#### Risiken
- …

#### Nächste Schritte (5-Punkte-Plan)
1. Konkrete Reihenfolge: Module/Dateien X → Y → Z einlesen/prüfen (aus betroffenen Pfaden)
2. Solver-Prep: Existierende Dateien komplett einlesen; fehlende Dateien als 'Create-Targets' markieren (Spec-to-File)
3. Schnittstellen klären (Signaturen, Imports, Package) und Mini-Specs je Datei erstellen
4. Validierung: spotless/ktlint & compileDebugKotlin; fehlende Importe oder Packages ergänzen
5. Handover an Solver mit .codex/context.json (Files, Symbols, Missing-Files, Plan)

Wichtig: keine 9.5k Zeichen überschreiten.
"""
    resp = client.responses.create(
        model=model,
        input=[{"role":"system","content":system},{"role":"user","content":user}],
        reasoning={"effort": effort},
    )
    return getattr(resp,"output_text","").strip()

# ---------------- Helpers ----------------

GITHUB_COMMENT_SOFT_LIMIT = 60000  # Reserve unter 65k

def ensure_under_limit(s: str, hard_limit: int=10000) -> bool:
    return len(s) <= hard_limit

# ---------------- Main ----------------

def main():
    # Default auf HIGH
    os.environ.setdefault("OPENAI_REASONING_EFFORT","high")

    issue_text = read_issue_body()
    if not issue_text.strip().startswith("/codex"):
        print("::notice::Issue body does not start with /codex – skipping.")
        return

    num = issue_number_from_event()
    if not num:
        print("::warning::No issue number in event; cannot comment.")
        return

    files = git_ls_files()
    allowed_paths = discover_modules_and_important_paths(files)

    # Symbol-Index (gekürzt) & Issue-Pfade mit Existenz-Check
    sym = build_symbol_index(files, limit=1200)
    mentioned_paths = extract_issue_paths(issue_text)
    exists_map = {p: (p in files) for p in mentioned_paths}
    existence_table_lines = []
    for p in mentioned_paths:
        existence_table_lines.append(f"- {p} → {'EXISTS' if exists_map[p] else 'MISSING'}")
    existence_table = "\n".join(existence_table_lines) if existence_table_lines else "(Issue nennt keine konkreten Pfade.)"

    # Symbol Preview
    def short_item(it: Dict[str,Any]) -> str:
        return f"{it.get('kind')} {it.get('name')} ({it.get('package')}) @ {it.get('file')}"
    preview_items = "\n".join(short_item(x) for x in sym["items"][:80]) if sym.get("items") else "(keine Symbole gefunden)"
    symbol_preview = f"Symbole: {sym.get('count',0)}\n{preview_items}"

    try:
        # 1) VOLLKONTEXT (ohne Kürzung) – geht in context_full.md
        context_full_md = call_openai_context_full(
            issue_text=issue_text,
            allowed_paths=allowed_paths,
            existence_table=existence_table,
            symbol_preview=symbol_preview
        )

        # 2) KURZFASSUNG (Kommentar) – strikt limitiert
        context_summary_md = call_openai_context_summary(
            issue_text=issue_text,
            allowed_paths=allowed_paths,
            existence_table=existence_table,
            symbol_preview=symbol_preview
        )
        marker = "### contextmap-ready"
        if not context_summary_md.startswith(marker):
            context_summary_md = f"{marker}\n\n{context_summary_md}"

        # 3) Strukturierte JSON für Solver
        ctx: Dict[str,Any] = {
            "issue_number": num,
            "issue_body": issue_text,
            "allowed_paths": allowed_paths,
            "all_files": files,
            "mentioned_paths": mentioned_paths,
            "mentioned_paths_exists": exists_map,
            "symbols": sym,
            "existence_table": existence_table,
            "contextmap_markdown_summary": context_summary_md,
            "model": os.environ.get("OPENAI_MODEL_DEFAULT","gpt-5"),
            "reasoning_effort": os.environ.get("OPENAI_REASONING_EFFORT","high"),
        }

        Path(".codex").mkdir(parents=True, exist_ok=True)
        Path(".codex/context.json").write_text(json.dumps(ctx, indent=2, ensure_ascii=False), encoding="utf-8")
        Path(".codex/context_full.md").write_text(context_full_md, encoding="utf-8")

        # 4) Kommentar (kurz) + Artefakt-Hinweis
        summary_with_note = (
            f"{context_summary_md}\n\n"
            f"> Vollständiger Kontext & strukturierte Daten wurden als Workflow-Artefakt hochgeladen:\n"
            f"> **codex-context-{num}** → `.codex/context_full.md` & `.codex/context.json`"
        )
        upsert_contextmap_comment(num, summary_with_note)
        remove_label(num, "contextmap-error")
        add_label(num, "contextmap-ready")
        print("::notice::ContextMap posted/updated and labeled contextmap-ready")

        # 5) (Optional) Solver direkt triggern – auskommentiert
        # try:
        #     repo_data = gh_api("GET", f"/repos/{gh_repo()}")
        #     default_branch = repo_data.get("default_branch","main")
        #     gh_api("POST", f"/repos/{gh_repo()}/actions/workflows/codex-solve.yml/dispatches",
        #            {"ref": default_branch, "inputs": {"issue": str(num)}})
        #     print("::notice::Triggered codex-solve via workflow_dispatch")
        # except Exception as e:
        #     upsert_contextmap_comment(num, f"{marker}\n\nHinweis: Konnte Bot 2 nicht automatisch starten.\n```\n{e}\n```")

    except Exception as e:
        add_label(num, "contextmap-error")
        upsert_contextmap_comment(num, f"### contextmap-ready\n\nFehler beim Erzeugen der ContextMap:\n\n```\n{e}\n```")
        print(f"::error::ContextMap failed: {e}")
        sys.exit(1)

if __name__ == "__main__":
    main()

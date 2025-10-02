#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
Bot 3 – Fehlerdoktor / Triage

Reagiert auf:
- Issue labels: contextmap-error (Bot 1) und solver-error (Bot 2)
- workflow_run: completed mit conclusion=failure (alle fehlgeschlagenen Workflows)

Aufgaben:
- Alle Job-Logs (ZIP) des Runs laden und analysieren.
- GPT-5 (Reasoning: high) erzeugt eine Diagnose:
  - Zusammenfassung
  - vermutete Ursache(n) mit kurzen Log-Belegen
  - konkrete Schritte zur Behebung
  - Checkliste (ToDos)
  - falls Ursache unklar: Vorschläge, wie Bot 1/2 anzupassen sind, um den Fehler lokalisierbar zu machen
- Zusätzlich: Gradle/AGP/Android-spezifische Heuristiken extrahieren und konkrete Text-Lösungen beilegen.
- Kommentar ins Issue (Marker "### bot3-analysis" – existierende Analyse wird aktualisiert).
- Labels setzen: "bot3-analysis", "triage-needed".

ENV:
  OPENAI_API_KEY (Secret)
  OPENAI_MODEL_DEFAULT (z. B. gpt-5)
  OPENAI_REASONING_EFFORT (high)
  OPENAI_BASE_URL (optional)
  GITHUB_TOKEN
  GITHUB_REPOSITORY
  GITHUB_EVENT_PATH
  GH_EVENT_NAME | GITHUB_EVENT_NAME
"""

from __future__ import annotations
import os, re, io, json, sys, time, zipfile, traceback
from pathlib import Path
from typing import Optional, List, Dict, Tuple
import requests

# ---------------- GitHub Helpers ----------------

def repo() -> str:
    return os.environ["GITHUB_REPOSITORY"]

def evname() -> str:
    return os.environ.get("GH_EVENT_NAME") or os.environ.get("GITHUB_EVENT_NAME","")

def event() -> dict:
    return json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text(encoding="utf-8"))

def gh_api(method: str, path: str, payload: dict | None = None) -> dict:
    url = f"https://api.github.com{path}"
    headers = {"Authorization": f"Bearer {os.environ['GITHUB_TOKEN']}",
               "Accept": "application/vnd.github+json"}
    r = requests.request(method, url, headers=headers, json=payload, timeout=60)
    if r.status_code >= 300:
        raise RuntimeError(f"GitHub API {method} {path} failed: {r.status_code} {r.text[:800]}")
    try:
        return r.json()
    except Exception:
        return {}

def gh_api_raw(method: str, url: str, allow_redirects=True):
    headers = {"Authorization": f"Bearer {os.environ['GITHUB_TOKEN']}",
               "Accept": "application/vnd.github+json"}
    r = requests.request(method, url, headers=headers, allow_redirects=allow_redirects, stream=True, timeout=60)
    if r.status_code >= 300 and r.status_code not in (301,302):
        raise RuntimeError(f"GitHub RAW {method} {url} failed: {r.status_code} {r.text[:800]}")
    return r

def issue_number_from_label_event() -> Optional[int]:
    ev = event()
    if evname() == "issues":
        return (ev.get("issue") or {}).get("number")
    return None

def list_issue_comments(num: int) -> List[dict]:
    res = gh_api("GET", f"/repos/{repo()}/issues/{num}/comments")
    return res if isinstance(res, list) else []

def upsert_analysis_comment(num: int, markdown: str):
    marker = "### bot3-analysis"
    cid = None
    for c in list_issue_comments(num):
        if (c.get("body") or "").strip().startswith(marker):
            cid = c.get("id"); break
    if cid:
        gh_api("PATCH", f"/repos/{repo()}/issues/comments/{cid}", {"body": markdown})
    else:
        gh_api("POST", f"/repos/{repo()}/issues/{num}/comments", {"body": markdown})

def add_labels(num: int, labels: List[str]):
    gh_api("POST", f"/repos/{repo()}/issues/{num}/labels", {"labels": labels})

def default_branch() -> str:
    return gh_api("GET", f"/repos/{repo()}").get("default_branch","main")

# ---------------- Mapping: workflow_run → Issue ----------------

def collect_run_logs(run: dict) -> Tuple[str, Dict[str,int]]:
    logs_url = run.get("logs_url","")
    if not logs_url:
        return ("", {})
    loc = gh_api_raw("GET", logs_url, allow_redirects=False).headers.get("Location")
    data = gh_api_raw("GET", loc).content
    big = []
    stats = {"files":0, "lines":0}
    with zipfile.ZipFile(io.BytesIO(data)) as zf:
        for name in zf.namelist():
            if not name.lower().endswith(".txt"): continue
            stats["files"] += 1
            try:
                txt = zf.read(name).decode("utf-8", errors="replace")
                stats["lines"] += txt.count("\n") + 1
                big.append(f"\n\n===== FILE: {name} =====\n{txt}")
            except Exception:
                continue
    return ("".join(big), stats)

def find_issue_for_workflow_run() -> Optional[int]:
    """
    Robuste Heuristik:
    1) Logs nach Markern: ISSUE_NUMBER=123 oder issue:#123
    2) PR-Body/Titel nach #123
    3) jüngstes offenes Issue mit contextmap-ready|contextmap-error|solver-error
    """
    ev = event()
    run = ev.get("workflow_run") or {}
    # 1) Marker in Logs
    try:
        blob, _ = collect_run_logs(run)
        if blob:
            m = re.search(r"(?:ISSUE_NUMBER\s*=\s*|issue:#)(\d+)", blob)
            if m:
                return int(m.group(1))
    except Exception:
        pass
    # 2) PR-Referenz
    prs = run.get("pull_requests") or []
    if prs:
        pr = gh_api("GET", f"/repos/{repo()}/pulls/{prs[0].get('number')}")
        body = (pr.get("body") or "") + "\n" + (pr.get("title") or "")
        m = re.search(r"#(\d+)", body)
        if m: return int(m.group(1))
    # 3) jüngstes relevantes Issue
    issues = gh_api("GET", f"/repos/{repo()}/issues?state=open&per_page=20")
    cand = [i for i in issues if any(l.get("name") in ("contextmap-ready","contextmap-error","solver-error") for l in i.get("labels",[]))]
    if cand:
        cand.sort(key=lambda x: x.get("updated_at",""), reverse=True)
        return cand[0].get("number")
    return None

def fetch_latest_failed_run_for_issue(num: int) -> Optional[dict]:
    runs = gh_api("GET", f"/repos/{repo()}/actions/runs?status=completed&per_page=20")
    items = runs.get("workflow_runs", []) or runs.get("runs", []) or []
    for r in items:
        if r.get("conclusion") == "failure":
            # PR → Issue
            prs = r.get("pull_requests") or []
            if prs:
                pr = gh_api("GET", f"/repos/{repo()}/pulls/{prs[0].get('number')}")
                body = (pr.get("body") or "") + "\n" + (pr.get("title") or "")
                m = re.search(r"#(\d+)", body)
                if m and int(m.group(1)) == num:
                    return r
            # Logs → Marker
            try:
                blob, _ = collect_run_logs(r)
                if re.search(fr"(?:ISSUE_NUMBER\s*=\s*|issue:#){num}\b", blob):
                    return r
            except Exception:
                pass
    return None

# ---------------- Gradle/AGP Heuristiken ----------------

def gradle_heuristics(log_blob: str) -> Dict[str, List[str]]:
    """
    Erkennung häufiger Gradle/Android-Fehler und konkrete Vorschläge.
    Rückgabe: {"causes":[...], "actions":[...], "snippets":[...]}
    """
    causes, actions, snippets = [], [], []

    def add(c=None, a=None, s=None):
        if c: causes.append(c)
        if a: actions.append(a)
        if s: snippets.append(s)

    # Compile/Target/Min SDK / Namespace
    if re.search(r"AndroidManifest\.xml.*package.*is not specified|Namespace not specified", log_blob, re.I):
        add("Fehlende Namespace/Package-Angabe (Manifest/Gradle).",
            "In module build.gradle(.kts) `namespace = \"com.example\"` setzen oder im Manifest `package` definieren.",
            "android {\n    namespace = \"com.chris.m3usuite\"\n}")
    if re.search(r"uses-sdk:minSdkVersion [0-9]+ cannot be smaller than version [0-9]+|minSdkVersion .* is greater than device SDK", log_blob, re.I):
        add("minSdk passt nicht (zu klein/groß).",
            "minSdk in build.gradle(.kts) auf kompatiblen Wert setzen; Abhängigkeiten prüfen.",
            "defaultConfig { minSdk = 21 }")
    if re.search(r"compileSdkVersion \d+.* not found|failed to find Build Tools|failed to find target", log_blob, re.I):
        add("compileSdk/Build-Tools fehlen im Runner.",
            "Im Workflow vor dem Build Android SDK/Build-Tools installieren (z. B. API 35; Fallback 34).",
            "echo \"y\" | sdkmanager \"platforms;android-35\" \"build-tools;35.0.0\"")

    # Dependency resolution
    if re.search(r"Could not resolve .*|Failed to transform .*|Could not find .* in repositories", log_blob, re.I):
        add("Dependency-Resolution fehlgeschlagen.",
            "Repos-Reihenfolge (google, mavenCentral) prüfen; Versionen/BOM abstimmen; ggf. --refresh-dependencies.",
            "repositories { google(); mavenCentral() }")
    if re.search(r"Duplicate class .* found in modules", log_blob, re.I):
        add("Duplicate class – doppelte Abhängigkeiten.",
            "Konflikte über Ausschlüsse oder BOM lösen; nur eine Implementierung behalten.",
            "implementation(platform(\"androidx.compose:compose-bom:2024.xx\"))\nconfigurations.all { exclude(group=\"org.jetbrains\", module=\"annotations\") }")

    # Kotlin/Java/AGP
    if re.search(r"Kotlin version .* is not compatible|The Kotlin Gradle plugin was loaded multiple times", log_blob, re.I):
        add("Kotlin/Plugin Version inkompatibel.",
            "Gradle Plugin + Kotlin Version harmonisieren (AGP Matrix beachten).",
            "plugins { id(\"org.jetbrains.kotlin.android\") version \"<passende-version>\" }")
    if re.search(r"Unsupported class file major version|invalid target release", log_blob, re.I):
        add("JDK-Target inkompatibel.",
            "JAVA_HOME/JDK-Version und kotlinOptions.jvmTarget/javac release angleichen (z. B. 17).",
            "kotlinOptions { jvmTarget = \"17\" }")

    # KAPT/KSP
    if re.search(r"KAPT error|error: \[kapt\]", log_blob, re.I):
        add("KAPT Fehler.",
            "Annotation Processor Pfade/Versionen prüfen; ggf. auf KSP migrieren.",
            "plugins { id(\"com.google.devtools.ksp\") }\nksp { arg(\"room.incremental\", \"true\") }")
    if re.search(r"Symbol processing|KSP", log_blob, re.I) and re.search(r"error:", log_blob):
        add("KSP Fehler.",
            "KSP Ausgaben/Args prüfen; inkompatible Processor-Versionen anpassen.",
            None)

    # R8/Proguard
    if re.search(r"R8: Program type already present|Missing class .* referenced from method", log_blob, re.I):
        add("R8/Proguard Fehler.",
            "Keep-Regeln ergänzen und/oder Abhängigkeiten bereinigen; 'program type already present' durch Deduplizieren lösen.",
            "-keep class com.yourlib.** { *; }")

    # Ressourcen/AAPT
    if re.search(r"A failure occurred while executing com.android.build.gradle.internal.res", log_blob):
        add("Ressourcenfehler (AAPT).",
            "Ressourcennamen/duplikate prüfen; nicht-ASCII/ungültige Dateinamen; vectorDrawables/eigene Attrs checken.",
            None)

    # Signing/Keystore
    if re.search(r"Keystore was tampered with|Invalid keystore format|Failed to read key", log_blob, re.I):
        add("Signing/Keystore Problem.",
            "release-keystore prüfen (Passwort/Alias/Stufe); debug-Builds ohne Release-Signatur bauen.",
            None)

    # Speicher
    if re.search(r"OutOfMemoryError|Java heap space", log_blob, re.I):
        add("OutOfMemory beim Build.",
            "Gradle-Heap erhöhen (org.gradle.jvmargs), parallele Worker reduzieren.",
            "org.gradle.jvmargs=-Xmx4g -Dkotlin.daemon.jvm.options=-Xmx2g")

    # Manifest Merger
    if re.search(r"Manifest merger failed", log_blob, re.I):
        add("Manifest-Merger Fehler.",
            "Konflikte auflösen: <application> / <provider> / <queries>; tools:node verwenden.",
            "tools:node=\"merge|remove|replace\"")

    # aapt2 spezifisch
    if re.search(r"aapt2.*error", log_blob, re.I):
        add("aapt2 Fehler.",
            "Ressourcen-Pfade/fehlerhafte XML prüfen; aapt2-Fehlermeldung genau lesen und Datei anpassen.",
            None)

    return {"causes": causes, "actions": actions, "snippets": snippets}

# ---------------- OpenAI Analyse ----------------

def analyze_with_openai(log_blob: str, context_hint: str, heuristics: Dict[str, List[str]]) -> str:
    from openai import OpenAI
    api_key = os.environ.get("OPENAI_API_KEY")
    if not api_key: raise RuntimeError("OPENAI_API_KEY not set")
    client = OpenAI(api_key=api_key, base_url=(os.environ.get("OPENAI_BASE_URL") or "https://api.openai.com/v1"))
    model = os.environ.get("OPENAI_MODEL_DEFAULT","gpt-5")
    effort = "high"

    # Heuristik-Text für zusätzlichen Kontext
    heur_txt = ""
    if heuristics and (heuristics.get("causes") or heuristics.get("actions")):
        hc = "\n".join(f"- {c}" for c in heuristics.get("causes",[])[:10]) or "(keine)"
        ha = "\n".join(f"- {a}" for a in heuristics.get("actions",[])[:10]) or "(keine)"
        hs = "\n".join(f"```text\n{s}\n```" for s in heuristics.get("snippets",[])[:5]) or ""
        heur_txt = f"\n\n[Lokale Gradle-Heuristik]\nUrsachen:\n{hc}\n\nSofortmaßnahmen:\n{ha}\n\nBeispiele:\n{hs}\n"

    SYSTEM = (
        "You are a senior CI/build failure triage assistant. "
        "Given full workflow logs, write a concise, actionable incident analysis in German. "
        "Be specific, cite short log snippets (<= 2 lines) where helpful."
    )
    USER = f"""Kontext-Hinweis:
{context_hint or '(kein zusätzlicher Kontext)'}{heur_txt}

Vollständige Logs (ausgewählte Ausschnitte; analysiere den gesamten Text):
{log_blob[:250000]}

Erzeuge Markdown mit diesem Aufbau:

### bot3-analysis
**Zusammenfassung**
- genau ein Satz mit dem Hauptproblem

**Vermutete Ursache(n)**
- kurze Punkte mit knappen Log-Belegen (Zeile/Dateiname, wenn erkennbar)

**Konkrete Schritte zur Behebung**
- Schritt 1 …
- Schritt 2 …
- Schritt 3 …

**Checkliste**
- [ ] Schritt 1 durchgeführt
- [ ] Schritt 2 verifiziert
- [ ] Schritt 3 ohne neue Fehler

**Falls Ursache unklar**
- Konkrete Änderungen an Bot 1/2 vorschlagen (zusätzliche Logs/Marker/Inputs), um die genaue Fehlerquelle beim nächsten Lauf zu isolieren

**Gradle/Build-spezifisch (falls zutreffend)**
- Diagnose zu AGP/Gradle/SDK/Dependencies inkl. spezifischer Maßnahmen
"""
    resp = client.responses.create(
        model=model,
        input=[{"role":"system","content":SYSTEM},{"role":"user","content":USER}],
        reasoning={"effort": effort},
    )
    return getattr(resp, "output_text", "").strip()

# ---------------- Hauptlogik ----------------

def main():
    # A) Label-Trigger
    if evname() == "issues":
        num = issue_number_from_label_event()
        if not num:
            print("::error::No issue number"); sys.exit(1)
        labels = [l.get("name") for l in (event().get("issue") or {}).get("labels",[])]
        if not any(x in ("contextmap-error","solver-error") for x in labels):
            print("::notice::Label not relevant"); sys.exit(0)

        run = fetch_latest_failed_run_for_issue(num)
        blob = ""
        if run:
            blob, stats = collect_run_logs(run)
        heur = gradle_heuristics(blob or "")
        analysis = analyze_with_openai(blob or "(Keine Logs greifbar – bitte Build erneut ausführen.)",
                                       context_hint="Label-Trigger vom Issue (contextmap-error / solver-error).",
                                       heuristics=heur)
        upsert_analysis_comment(num, analysis)
        add_labels(num, ["bot3-analysis","triage-needed"])
        print("::notice::bot3-analysis posted (label trigger)")
        return

    # B) workflow_run failure
    if evname() == "workflow_run":
        wr = (event().get("workflow_run") or {})
        if wr.get("conclusion") != "failure":
            print("::notice::workflow_run not failure; skip"); return
        num = find_issue_for_workflow_run()
        if not num:
            print("::error::could not map workflow run to an issue"); sys.exit(1)
        blob, stats = collect_run_logs(wr)
        heur = gradle_heuristics(blob or "")
        analysis = analyze_with_openai(blob, context_hint=f"Workflow '{wr.get('name')}' failed on '{wr.get('head_branch')}'.", heuristics=heur)
        upsert_analysis_comment(num, analysis)
        add_labels(num, ["bot3-analysis","triage-needed"])
        print("::notice::bot3-analysis posted (workflow_run)")
        return

    print("::notice::Event not handled by Bot 3")

if __name__ == "__main__":
    try:
        main()
    except SystemExit:
        raise
    except Exception as e:
        print("::error::Bot3 unexpected failure:", e)
        print(traceback.format_exc())
        sys.exit(1)

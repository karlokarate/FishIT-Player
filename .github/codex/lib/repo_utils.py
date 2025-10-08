\
"""
Repository discovery, file listing, stack detection, keywording, and simple symbol extraction.
"""
from __future__ import annotations
import os, re, json
from pathlib import Path
from typing import Any, Dict, List, Tuple, Optional
from collections import Counter

DEFAULT_EXCLUDE_DIRS = {".git", ".github", ".gradle", "build", "out", ".idea", ".vscode", ".venv", "node_modules", ".dart_tool"}

def git_ls_files() -> List[str]:
    import subprocess
    try:
        out = subprocess.check_output(["git", "ls-files"], text=True)
        return [l.strip() for l in out.splitlines() if l.strip()]
    except Exception:
        return []

def list_all_files(exclude_dirs: Optional[set] = None) -> List[str]:
    exclude = exclude_dirs or DEFAULT_EXCLUDE_DIRS
    files: List[str] = []
    root = Path(".").resolve()
    for r, dirs, fnames in os.walk(root):
        # mutate dirs to prune
        pruned = []
        for d in list(dirs):
            if d in exclude and (root / d).resolve() != (root / ".github").resolve():
                pruned.append(d)
        for d in pruned:
            dirs.remove(d)
        for fn in fnames:
            p = (Path(r) / fn).resolve()
            rel = p.relative_to(root).as_posix()
            if rel.startswith(".git/"):
                continue
            files.append(rel)
    return sorted(set(files))

def language_stats(files: List[str]) -> Dict[str, float]:
    exts = {}
    for p in files:
        ext = Path(p).suffix.lower().lstrip(".") or "(none)"
        exts.setdefault(ext, 0)
        try:
            exts[ext] += Path(p).stat().st_size
        except Exception:
            pass
    return {k: round(v/1024, 2) for k, v in sorted(exts.items(), key=lambda kv: kv[1], reverse=True)}

def detect_stack_files(files: List[str]) -> Dict[str, List[str]]:
    patterns = {
        "python": ["requirements.txt","pyproject.toml","setup.py","Pipfile","poetry.lock"],
        "nodejs": ["package.json","pnpm-lock.yaml","yarn.lock","package-lock.json","tsconfig.json"],
        "java_gradle": ["build.gradle","build.gradle.kts","settings.gradle","settings.gradle.kts","gradlew","gradlew.bat"],
        "java_maven": ["pom.xml","mvnw","mvnw.cmd"],
        "android": ["AndroidManifest.xml","gradle.properties"],
        "go": ["go.mod","go.sum"],
        "rust": ["Cargo.toml","Cargo.lock"],
        ".net": [".csproj",".fsproj",".vbproj",".sln","global.json","Directory.Build.props","Directory.Build.targets"],
        "docker": ["Dockerfile","docker-compose.yml","compose.yml"],
        "k8s": ["k8s/","kubernetes/","manifests/"],
        "terraform": [".tf",".tf.json"],
        "cmake": ["CMakeLists.txt"],
        "make": ["Makefile"]
    }
    found: Dict[str, List[str]] = {k: [] for k in patterns}
    for p in files:
        lp = p.lower(); bn = Path(lp).name
        for key, pats in patterns.items():
            for pat in pats:
                if pat.endswith("/") and pat[:-1] in lp:
                    found[key].append(p); break
                if bn == pat or lp.endswith("/"+pat) or bn.endswith(pat):
                    found[key].append(p); break
    return {k: sorted(set(v)) for k, v in found.items() if v}

STOPWORDS = set("""\
the and for with that this from are was were will would you your have has had
ein eine einer eines einem einen der die das und oder aber nicht kein keine den dem
ist sind war waren wird würde wurde zu zum zur auf im in am beim vom von mit ohne
einfach problem issue bug fix fehler error fail failing läuft laufen run running open opened
""".split())

def extract_keywords(text: str, limit: int = 15) -> List[str]:
    text = (text or "").lower()
    words = re.findall(r"[a-z0-9_][a-z0-9_\-]{2,}", text, flags=re.IGNORECASE)
    counts = Counter(w for w in words if w not in STOPWORDS)
    return [w for w, _ in counts.most_common(limit)]

def score_file_for_keywords(path: str, keywords: List[str]) -> int:
    lp = path.lower(); base = Path(lp).name; segments = set(lp.split("/"))
    score = 0
    for k in keywords:
        if k in base: score += 6
        if k in lp: score += 2
        if k in segments: score += 3
    if any(s in lp for s in ("/src/","/lib/","/app/","/core/","/common/","/server/","/client/")):
        score += 2
    return score

def pick_candidate_files(files: List[str], keywords: List[str], limit: int = 30) -> List[str]:
    scored = [(score_file_for_keywords(p, keywords), p) for p in files]
    scored.sort(key=lambda t: (t[0], -len(t[1])), reverse=True)
    return [p for s, p in scored if s > 0][:limit]

def top_dirs_by_count(files: List[str], n: int = 10) -> List[Dict[str, Any]]:
    from collections import Counter as C
    cnt = C(Path(p).parent.as_posix() or "." for p in files)
    most = cnt.most_common(n)
    return [{"dir": d, "files": c} for d, c in most]

# --- Simple symbol gathering (generic heuristics) ---

def gather_symbols(files: List[str], limit:int=1200) -> Dict[str, Any]:
    symbols=[]; cnt=0
    for f in files:
        if any(seg in f for seg in ("/build/","/dist/","/generated/")):
            continue
        if not any(f.endswith(ext) for ext in (".kt",".java",".py",".ts",".tsx",".js",".jsx",".go",".rs",".cs")):
            continue
        try:
            t = Path(f).read_text(encoding="utf-8", errors="replace")
        except Exception:
            continue
        # Extremely light extraction
        pkg = ""
        m_pkg = re.search(r"^\s*(?:package|namespace)\s+([a-zA-Z0-9_.]+)", t, re.M)
        if m_pkg: pkg = m_pkg.group(1)
        for rx, kind in [
            (r"(?:class|interface)\s+([A-Za-z0-9_]+)", "type"),
            (r"(?:object)\s+([A-Za-z0-9_]+)", "object"),
            (r"(?:def|fun|function)\s+([A-Za-z0-9_]+)\(", "fun"),
            (r"@Composable\s+fun\s+([A-Za-z0-9_]+)\(", "composable"),
        ]:
            for m in re.finditer(rx, t):
                symbols.append({"file":f,"package":pkg,"kind":kind,"name":m.group(1)})
                cnt += 1
                if cnt >= limit:
                    return {"count": len(symbols), "items": symbols}
    return {"count": len(symbols), "items": symbols}

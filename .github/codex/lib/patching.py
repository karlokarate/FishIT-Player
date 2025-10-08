\
"""
Unified diff sanitization and robust apply matrix.
"""
from __future__ import annotations
import re, subprocess
from pathlib import Path
from typing import List, Tuple

_VALID_LINE = re.compile(
    r"^(diff --git |index |new file mode |deleted file mode |old mode |new mode |"
    r"similarity index |rename (from|to) |Binary files |--- |\+\+\+ |@@ |[\+\- ]|\\ No newline at end of file)"
)

def strip_code_fences(text: str) -> str:
    if not text:
        return text
    m = re.match(r"^```[a-zA-Z0-9_-]*\s*\n(.*?)\n```$", text.strip(), re.S)
    return (m.group(1) if m else text)

def sanitize_patch(raw: str) -> str:
    if not raw:
        return raw or ""
    txt = strip_code_fences(raw).replace("\r\n", "\n").replace("\r", "\n")
    pos = txt.find("diff --git ")
    if pos != -1:
        txt = txt[pos:]
    clean = []
    for ln in txt.splitlines():
        if re.match(r"^\s*\d+\.\s*:?\s*$", ln):
            continue
        if re.match(r"^\s*[–—\-]\s*$", ln):
            continue
        if _VALID_LINE.match(ln):
            clean.append(ln)
    out = "\n".join(clean)
    if not out.endswith("\n"):
        out += "\n"
    return out

def patch_uses_ab_prefix(patch_text: str) -> bool:
    return bool(re.search(r"^diff --git a/[\S]+ b/[\S]+", patch_text, re.M))

def split_sections(patch_text: str) -> List[str]:
    sections = []
    it = list(re.finditer(r"^diff --git a/(\S+)\s+b/(\S+)\s*$", patch_text, re.M))
    for i, m in enumerate(it):
        start = m.start()
        end = it[i + 1].start() if i + 1 < len(it) else len(patch_text)
        sections.append(patch_text[start:end].rstrip() + "\n")
    return sections

def make_zero_context(patch_text: str) -> str:
    out = []
    for ln in patch_text.splitlines():
        if ln.startswith("@@"):
            ln = re.sub(r"(@@\s+-\d+)(?:,\d+)?(\s+\+\d+)(?:,\d+)?(\s+@@.*)", r"\1,0\2,0\3", ln)
            out.append(ln)
        elif ln.startswith(" "):
            continue
        else:
            out.append(ln)
    z = "\n".join(out)
    if not z.endswith("\n"):
        z += "\n"
    return z

def _git_apply_try(tmp: str, pstrip: int, three_way: bool, extra_flags: str = "") -> subprocess.CompletedProcess:
    cmd = f"git apply {'-3' if three_way else ''} -p{pstrip} --whitespace=fix {extra_flags} {tmp}".strip()
    return subprocess.run(cmd, shell=True, text=True, capture_output=True)

def _gnu_patch_apply(tmp: str, p: int, rej_path: str) -> subprocess.CompletedProcess:
    cmd = f"patch -p{p} -f -N --follow-symlinks --no-backup-if-mismatch -r {rej_path} < {tmp}"
    return subprocess.run(cmd, shell=True, text=True, capture_output=True)

def _git_apply_matrix(tmp: str, uses_ab: bool) -> List[tuple[int, bool, str]]:
    pseq = [1, 0] if uses_ab else [0, 1]
    combos = []
    for p in pseq:
        combos += [
            (p, True, ""),
            (p, False, ""),
            (p, False, "--ignore-whitespace"),
            (p, False, "--inaccurate-eof"),
            (p, False, "--unidiff-zero"),
            (p, False, "--ignore-whitespace --inaccurate-eof"),
            (p, False, "--reject --ignore-whitespace"),
            (p, False, "--reject --unidiff-zero"),
        ]
    return combos

def whole_git_apply(patch_text: str) -> tuple[bool, str]:
    tmp = ".github/codex/_solver_all.patch"
    Path(tmp).parent.mkdir(parents=True, exist_ok=True)
    Path(tmp).write_text(patch_text, encoding="utf-8")
    uses_ab = patch_uses_ab_prefix(patch_text)
    last_err = ""
    for p, three, extra in _git_apply_matrix(tmp, uses_ab):
        pr = _git_apply_try(tmp, p, three, extra)
        if pr.returncode == 0:
            return True, f"git apply {'-3' if three else ''} -p{p} --whitespace=fix {extra}".strip()
        last_err = (pr.stderr or pr.stdout or "")[:1600]
    return False, last_err

def apply_section(sec_text: str, idx: int) -> bool:
    tmp = f".github/codex/_solver_sec_{idx}.patch"
    Path(tmp).parent.mkdir(parents=True, exist_ok=True)
    Path(tmp).write_text(sec_text, encoding="utf-8")
    uses_ab = patch_uses_ab_prefix(sec_text)
    for p, three, extra in _git_apply_matrix(tmp, uses_ab):
        pr = _git_apply_try(tmp, p, three, extra)
        if pr.returncode == 0:
            return True
    # Zero context fallback
    ztmp = f".github/codex/_solver_sec_{idx}_zc.patch"
    Path(ztmp).write_text(make_zero_context(sec_text), encoding="utf-8")
    for p in ([1, 0] if uses_ab else [0, 1]):
        pr = _git_apply_try(ztmp, p, False, "--unidiff-zero --ignore-whitespace")
        if pr.returncode == 0:
            return True
    # GNU patch fallback
    for pp in (1, 0):
        pr = _gnu_patch_apply(tmp, pp, f".github/codex/_solver_sec_{idx}.rej")
        if pr.returncode == 0:
            return True
    return False

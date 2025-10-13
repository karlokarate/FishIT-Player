"""
Language/build/test profiles and auto-detection.
"""
from __future__ import annotations
from pathlib import Path
from typing import Dict, Any, List

def detect_language_profile(files: List[str]) -> str:
    fl = [f.lower() for f in files]
    def has(any_of: List[str]) -> bool:
        return any(any(x in f for x in any_of) for f in fl)
    if any(f.endswith((".gradle",".gradle.kts")) for f in fl) or has(["gradlew","gradlew.bat"]):
        return "java-gradle"
    if any(f.endswith(("package.json","tsconfig.json")) for f in fl):
        return "node"
    if any(f.endswith(("pyproject.toml","requirements.txt","setup.py","Pipfile")) for f in fl):
        return "python"
    if any(f.endswith((".sln",".csproj")) for f in fl):
        return "dotnet"
    if any(f.endswith(("go.mod","go.sum")) for f in fl):
        return "go"
    if any(f.endswith(("Cargo.toml","Cargo.lock")) for f in fl):
        return "rust"
    return "auto"

def resolve_commands(profile: str) -> Dict[str, Any]:
    p = (profile or "auto").lower()
    if p == "java-gradle":
        return {"fmt": ["./gradlew", "spotlessApply"], "build": ["./gradlew", "assemble"], "test": ["./gradlew", "test"]}
    if p == "node":
        return {"fmt": ["npm", "run", "lint", "--", "--fix"], "build": ["npm", "run", "build"], "test": ["npm", "test"]}
    if p == "python":
        return {"fmt": ["python", "-m", "ruff", "check", "--fix", "."], "build": [], "test": ["python", "-m", "pytest", "-q"]}
    if p == "dotnet":
        return {"fmt": ["dotnet", "format"], "build": ["dotnet", "build", "--nologo"], "test": ["dotnet", "test", "--nologo"]}
    if p == "go":
        return {"fmt": ["gofmt", "-w", "."], "build": ["go", "build", "./..."], "test": ["go", "test", "./..."]}
    if p == "rust":
        return {"fmt": ["cargo", "fmt"], "build": ["cargo", "build"], "test": ["cargo", "test"]}
    return {"fmt": [], "build": [], "test": []}

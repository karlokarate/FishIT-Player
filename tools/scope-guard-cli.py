#!/usr/bin/env python3
"""
Scope Guard CLI - Enforces scope boundaries at commit time.

This is a HARD BARRIER - commits will be rejected if files violate scope rules.

Usage:
  # Check single file
  python scope-guard-cli.py check <file-path>
  
  # Check multiple files (e.g., from git diff)
  python scope-guard-cli.py check-batch <file1> <file2> ...
  
  # Check all staged files (for pre-commit hook)
  python scope-guard-cli.py check-staged
  
  # Show status of a file
  python scope-guard-cli.py status <file-path>

Exit Codes:
  0 - All files ALLOWED
  1 - At least one file BLOCKED (READ_ONLY or scope violation)
  2 - Configuration error
"""

import argparse
import fnmatch
import json
import os
import subprocess
import sys
from datetime import datetime
from pathlib import Path
from typing import Optional, Literal

# ============================================================================
# Constants
# ============================================================================

WORKSPACE_ROOT = Path(__file__).parent.parent
SCOPE_DIR = WORKSPACE_ROOT / ".scope"
CONFIG_FILE = SCOPE_DIR / "scope-guard.config.json"
AUDIT_LOG = SCOPE_DIR / "audit.log"

# Exit codes
EXIT_ALLOWED = 0
EXIT_BLOCKED = 1
EXIT_ERROR = 2

# Status types
Status = Literal[
    "ALLOWED",      # File can be edited
    "EXCLUDED",     # Build artifact, auto-allowed
    "READ_ONLY",    # Protected path, HARD BLOCK
    "UNTRACKED",    # Not in any scope, WARNING but allowed
    "BUNDLE",       # In a bundle, allowed with warning
    "SCOPE",        # In a scope, allowed
]

# ============================================================================
# Configuration Loading
# ============================================================================

def load_config() -> dict:
    """Load scope guard configuration."""
    if not CONFIG_FILE.exists():
        print(f"ERROR: Config not found: {CONFIG_FILE}", file=sys.stderr)
        sys.exit(EXIT_ERROR)
    
    with open(CONFIG_FILE) as f:
        return json.load(f)


def load_scopes() -> dict[str, dict]:
    """Load all scope files."""
    scopes = {}
    for scope_file in SCOPE_DIR.glob("*.scope.json"):
        try:
            with open(scope_file) as f:
                scope = json.load(f)
                scopes[scope["scopeId"]] = scope
        except Exception as e:
            print(f"WARNING: Failed to load {scope_file}: {e}", file=sys.stderr)
    return scopes


# ============================================================================
# Path Matching
# ============================================================================

def matches_glob(file_path: str, pattern: str) -> bool:
    """Check if file matches glob pattern."""
    # Normalize path
    file_path = file_path.replace("\\", "/")
    pattern = pattern.replace("\\", "/")
    
    # Handle ** patterns
    if "**" in pattern:
        # Convert ** to regex-like matching
        parts = pattern.split("**")
        if len(parts) == 2:
            prefix, suffix = parts
            prefix = prefix.rstrip("/")
            suffix = suffix.lstrip("/")
            
            # Check if path starts with prefix (if any) and ends with suffix pattern
            if prefix and not file_path.startswith(prefix):
                return False
            if suffix:
                # Check if any part of remaining path matches suffix
                remaining = file_path[len(prefix):].lstrip("/") if prefix else file_path
                return fnmatch.fnmatch(remaining, f"*{suffix}") or fnmatch.fnmatch(remaining, suffix)
            return True
    
    # Standard glob
    return fnmatch.fnmatch(file_path, pattern)


def is_excluded(file_path: str, config: dict) -> bool:
    """Check if file is in global excludes (build artifacts, etc.)."""
    for pattern in config.get("globalExcludes", []):
        if matches_glob(file_path, pattern):
            return True
    return False


def is_read_only(file_path: str, config: dict) -> tuple[bool, Optional[str]]:
    """Check if file is in read-only paths. Returns (is_read_only, reason)."""
    for pattern in config.get("readOnlyPaths", []):
        if matches_glob(file_path, pattern):
            # Check if it's a generated file
            for bundle_id, bundle in config.get("bundles", {}).items():
                for gen_pattern in bundle.get("generated", []):
                    if matches_glob(file_path, gen_pattern):
                        ssot = bundle.get("ssot", "unknown")
                        return True, f"GENERATED file. Edit source: {ssot}"
            return True, f"Protected by readOnlyPaths: {pattern}"
    return False, None


def find_bundle(file_path: str, config: dict) -> Optional[tuple[str, dict]]:
    """Find matching bundle for file."""
    for bundle_id, bundle in config.get("bundles", {}).items():
        for pattern in bundle.get("patterns", []):
            if matches_glob(file_path, pattern):
                return bundle_id, bundle
    return None


def find_scope(file_path: str, scopes: dict[str, dict]) -> Optional[tuple[str, dict]]:
    """Find matching scope for file."""
    for scope_id, scope in scopes.items():
        for module_path in scope.get("modules", {}).keys():
            if file_path.startswith(module_path) or file_path.startswith(module_path + "/"):
                return scope_id, scope
    return None


# ============================================================================
# File Checking
# ============================================================================

def check_file(file_path: str, config: dict, scopes: dict) -> tuple[Status, str, Optional[str]]:
    """
    Check if a file can be edited.
    
    Returns:
        (status, message, scope_or_bundle_id)
    """
    # Normalize path
    rel_path = file_path
    if rel_path.startswith(str(WORKSPACE_ROOT)):
        rel_path = rel_path[len(str(WORKSPACE_ROOT)):].lstrip("/\\")
    rel_path = rel_path.replace("\\", "/")
    
    # 1. Check if excluded (build artifacts)
    if is_excluded(rel_path, config):
        return "EXCLUDED", "Build artifact - auto-allowed", None
    
    # 2. Check if read-only (HARD BLOCK)
    is_ro, reason = is_read_only(rel_path, config)
    if is_ro:
        return "READ_ONLY", reason or "Protected path", None
    
    # 3. Check bundles
    bundle_match = find_bundle(rel_path, config)
    if bundle_match:
        bundle_id, bundle = bundle_match
        return "BUNDLE", f"In bundle '{bundle_id}': {bundle.get('description', '')}", bundle_id
    
    # 4. Check scopes
    scope_match = find_scope(rel_path, scopes)
    if scope_match:
        scope_id, scope = scope_match
        return "SCOPE", f"In scope '{scope_id}': {scope.get('description', '')}", scope_id
    
    # 5. Untracked
    return "UNTRACKED", "Not in any scope or bundle", None


def audit(action: str, file_path: str, status: str, details: str = ""):
    """Write to audit log."""
    entry = {
        "timestamp": datetime.now().isoformat(),
        "source": "cli",
        "action": action,
        "filePath": file_path,
        "status": status,
        "details": details,
    }
    
    try:
        with open(AUDIT_LOG, "a") as f:
            f.write(json.dumps(entry) + "\n")
    except Exception:
        pass  # Don't fail on audit errors


# ============================================================================
# Commands
# ============================================================================

def cmd_check(file_path: str, config: dict, scopes: dict) -> int:
    """Check a single file. Returns exit code."""
    status, message, scope_id = check_file(file_path, config, scopes)
    
    audit("cli_check", file_path, status, message)
    
    if status == "READ_ONLY":
        print(f"❌ BLOCKED: {file_path}")
        print(f"   Reason: {message}")
        return EXIT_BLOCKED
    elif status == "EXCLUDED":
        print(f"✓ ALLOWED (excluded): {file_path}")
        return EXIT_ALLOWED
    elif status == "UNTRACKED":
        print(f"⚠ WARNING (untracked): {file_path}")
        print(f"   File not in any scope - consider adding to a scope")
        return EXIT_ALLOWED  # Warning only, don't block
    else:
        print(f"✓ ALLOWED: {file_path}")
        if scope_id:
            print(f"   {message}")
        return EXIT_ALLOWED


def cmd_check_batch(files: list[str], config: dict, scopes: dict) -> int:
    """Check multiple files. Returns EXIT_BLOCKED if any blocked."""
    blocked = []
    allowed = []
    warnings = []
    
    for file_path in files:
        status, message, scope_id = check_file(file_path, config, scopes)
        audit("cli_check_batch", file_path, status, message)
        
        if status == "READ_ONLY":
            blocked.append((file_path, message))
        elif status == "UNTRACKED":
            warnings.append((file_path, message))
        else:
            allowed.append(file_path)
    
    # Report results
    if blocked:
        print(f"\n❌ BLOCKED ({len(blocked)} files):")
        for path, reason in blocked:
            print(f"   {path}")
            print(f"      → {reason}")
    
    if warnings:
        print(f"\n⚠ WARNINGS ({len(warnings)} untracked files):")
        for path, _ in warnings:
            print(f"   {path}")
    
    if allowed:
        print(f"\n✓ ALLOWED: {len(allowed)} files")
    
    if blocked:
        print(f"\n🚫 COMMIT BLOCKED - Fix {len(blocked)} read-only violations above")
        return EXIT_BLOCKED
    
    return EXIT_ALLOWED


def cmd_check_staged(config: dict, scopes: dict) -> int:
    """Check all git staged files."""
    try:
        result = subprocess.run(
            ["git", "diff", "--cached", "--name-only", "--diff-filter=ACMR"],
            capture_output=True,
            text=True,
            cwd=WORKSPACE_ROOT
        )
        
        if result.returncode != 0:
            print(f"ERROR: git command failed: {result.stderr}", file=sys.stderr)
            return EXIT_ERROR
        
        files = [f.strip() for f in result.stdout.strip().split("\n") if f.strip()]
        
        if not files:
            print("No staged files to check")
            return EXIT_ALLOWED
        
        print(f"Checking {len(files)} staged files...")
        return cmd_check_batch(files, config, scopes)
        
    except FileNotFoundError:
        print("ERROR: git not found", file=sys.stderr)
        return EXIT_ERROR


def cmd_status(file_path: str, config: dict, scopes: dict) -> int:
    """Show detailed status of a file."""
    status, message, scope_id = check_file(file_path, config, scopes)
    
    print(f"File: {file_path}")
    print(f"Status: {status}")
    print(f"Message: {message}")
    
    if status == "READ_ONLY":
        # Check if it's a generated file with a source
        for bundle_id, bundle in config.get("bundles", {}).items():
            for gen_pattern in bundle.get("generated", []):
                if matches_glob(file_path, gen_pattern):
                    ssot = bundle.get("ssot", "unknown")
                    print(f"\nEdit source: {ssot}")
                    print(f"Sync command: scripts/sync-agent-rules.sh")
                    break
    elif scope_id:
        if status == "BUNDLE":
            bundle = config.get("bundles", {}).get(scope_id, {})
            print(f"\nBundle: {scope_id}")
            print(f"Description: {bundle.get('description', 'N/A')}")
            
            if bundle.get("invariants"):
                print(f"Invariants:")
                for inv in bundle.get("invariants", []):
                    print(f"  • {inv}")
            
            if bundle.get("mandatoryReadBeforeEdit"):
                print(f"Must read before edit:")
                for file in bundle.get("mandatoryReadBeforeEdit", []):
                    print(f"  → {file}")
                    
        elif status == "SCOPE":
            scope = scopes.get(scope_id, {})
            print(f"\nScope: {scope_id}")
            print(f"Description: {scope.get('description', 'N/A')}")
            
            if scope.get("globalInvariants"):
                print(f"Invariants:")
                for inv in scope.get("globalInvariants", []):
                    print(f"  • {inv}")
            
            if scope.get("mandatoryReadBeforeEdit"):
                print(f"Must read before edit:")
                for file in scope.get("mandatoryReadBeforeEdit", []):
                    print(f"  → {file}")
    
    return EXIT_ALLOWED if status != "READ_ONLY" else EXIT_BLOCKED


# ============================================================================
# Main
# ============================================================================

def main():
    parser = argparse.ArgumentParser(
        description="Scope Guard CLI - Enforces scope boundaries",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__
    )
    
    subparsers = parser.add_subparsers(dest="command", help="Commands")
    
    # check command
    check_parser = subparsers.add_parser("check", help="Check single file")
    check_parser.add_argument("file", help="File path to check")
    
    # check-batch command
    batch_parser = subparsers.add_parser("check-batch", help="Check multiple files")
    batch_parser.add_argument("files", nargs="+", help="File paths to check")
    
    # check-staged command
    subparsers.add_parser("check-staged", help="Check all git staged files")
    
    # status command
    status_parser = subparsers.add_parser("status", help="Show file status details")
    status_parser.add_argument("file", help="File path to check")
    
    args = parser.parse_args()
    
    if not args.command:
        parser.print_help()
        sys.exit(EXIT_ERROR)
    
    # Load configuration
    config = load_config()
    scopes = load_scopes()
    
    # Execute command
    if args.command == "check":
        sys.exit(cmd_check(args.file, config, scopes))
    elif args.command == "check-batch":
        sys.exit(cmd_check_batch(args.files, config, scopes))
    elif args.command == "check-staged":
        sys.exit(cmd_check_staged(config, scopes))
    elif args.command == "status":
        sys.exit(cmd_status(args.file, config, scopes))
    else:
        parser.print_help()
        sys.exit(EXIT_ERROR)


if __name__ == "__main__":
    main()

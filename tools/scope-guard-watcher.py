#!/usr/bin/env python3
"""
Scope Guard File Watcher (Python version)

Cross-platform file watcher that monitors workspace changes and warns about scope violations.
This is a WARNING system - it doesn't block edits, just notifies.

Usage:
  python3 tools/scope-guard-watcher.py [OPTIONS]

Options:
  --aggressive    Auto-revert READ_ONLY violations (DANGEROUS)
  --verbose       Show SCOPE and BUNDLE details with invariants
  --quiet         Only show BLOCKED (no UNTRACKED warnings)
  --interval N    Poll interval in seconds (default: 1)

Requirements:
  - watchdog: pip install watchdog
"""

import argparse
import os
import subprocess
import sys
import time
from datetime import datetime
from pathlib import Path

# Check for watchdog
try:
    from watchdog.observers import Observer
    from watchdog.events import FileSystemEventHandler
    WATCHDOG_AVAILABLE = True
except ImportError:
    WATCHDOG_AVAILABLE = False
    FileSystemEventHandler = object  # Fallback base class

# Colors (ANSI)
RED = '\033[0;31m'
GREEN = '\033[0;32m'
YELLOW = '\033[1;33m'
BLUE = '\033[0;34m'
MAGENTA = '\033[0;35m'
CYAN = '\033[0;36m'
NC = '\033[0m'  # No Color

WORKSPACE_ROOT = Path(__file__).parent.parent
CLI_TOOL = WORKSPACE_ROOT / "tools" / "scope-guard-cli.py"

# Patterns to exclude
EXCLUDE_PATTERNS = {
    '.git', 'build', '.gradle', 'node_modules', '.idea', 
    '__pycache__', '.pyc', '.kotlin', 'dist', '.DS_Store'
}

# Directories to watch
WATCH_DIRS = [
    '',  # Root (for AGENTS.md, README.md)
    'core',
    'infra', 
    'pipeline',
    'playback',
    'player',
    'feature',
    'app-v2',
    'legacy',
    'contracts',
    '.scope',
    '.github',
    'docs',
]


def should_ignore(path: Path) -> bool:
    """Check if path should be ignored."""
    parts = path.parts
    for pattern in EXCLUDE_PATTERNS:
        if pattern in parts:
            return True
    
    # Skip temp files
    name = path.name
    if name.endswith('~') or name.endswith('.swp') or name.endswith('.swo'):
        return True
    if name.startswith('.#'):  # Emacs lock files
        return True
        
    return False


def get_relative_path(path: Path) -> str:
    """Get workspace-relative path."""
    try:
        return str(path.relative_to(WORKSPACE_ROOT))
    except ValueError:
        return str(path)


def check_file(rel_path: str, verbose: bool = False) -> dict:
    """Check file status using CLI tool."""
    cmd = [sys.executable, str(CLI_TOOL)]
    if verbose:
        cmd.extend(['status', rel_path])
    else:
        cmd.extend(['check', rel_path])
    
    try:
        result = subprocess.run(
            cmd,
            capture_output=True,
            text=True,
            cwd=WORKSPACE_ROOT,
            timeout=5
        )
        return {
            'output': result.stdout + result.stderr,
            'returncode': result.returncode
        }
    except subprocess.TimeoutExpired:
        return {'output': 'Timeout checking file', 'returncode': 2}
    except Exception as e:
        return {'output': str(e), 'returncode': 2}


def revert_file(rel_path: str) -> bool:
    """Revert file using git checkout."""
    try:
        result = subprocess.run(
            ['git', 'checkout', rel_path],
            capture_output=True,
            cwd=WORKSPACE_ROOT,
            timeout=5
        )
        return result.returncode == 0
    except Exception:
        return False


class ScopeGuardHandler(FileSystemEventHandler):
    """Handler for file system events."""
    
    def __init__(self, aggressive: bool = False, verbose: bool = False, quiet: bool = False):
        super().__init__()
        self.aggressive = aggressive
        self.verbose = verbose
        self.quiet = quiet
        self._last_event = {}  # Debounce duplicate events
    
    def _debounce(self, path: str) -> bool:
        """Returns True if we should process this event (not a duplicate)."""
        now = time.time()
        last = self._last_event.get(path, 0)
        if now - last < 0.5:  # 500ms debounce
            return False
        self._last_event[path] = now
        return True
    
    def on_modified(self, event):
        if event.is_directory:
            return
        self._handle_event(event.src_path)
    
    def on_created(self, event):
        if event.is_directory:
            return
        self._handle_event(event.src_path)
    
    def _handle_event(self, src_path: str):
        path = Path(src_path)
        
        if should_ignore(path):
            return
        
        if not path.is_file():
            return
        
        rel_path = get_relative_path(path)
        
        if not self._debounce(rel_path):
            return
        
        result = check_file(rel_path, self.verbose)
        output = result['output']
        
        # Determine status from output
        if 'BLOCKED' in output or result['returncode'] == 1:
            status = 'READ_ONLY'
        elif 'UNTRACKED' in output or 'WARNING' in output:
            status = 'UNTRACKED'
        elif 'Status: SCOPE' in output or 'In scope' in output:
            status = 'SCOPE'
        elif 'Status: BUNDLE' in output or 'In bundle' in output:
            status = 'BUNDLE'
        else:
            status = 'ALLOWED'
        
        # Handle based on status
        if status == 'READ_ONLY':
            print(f"{RED}🚫 BLOCKED: {rel_path}{NC}")
            # Extract relevant info
            for line in output.split('\n'):
                if any(x in line for x in ['Reason:', 'Message:', '→', 'Edit source:']):
                    print(f"   {line.strip()}")
            
            if self.aggressive:
                print(f"{YELLOW}   ↩ Auto-reverting...{NC}")
                if revert_file(rel_path):
                    print(f"{GREEN}   Reverted successfully{NC}")
                else:
                    print(f"{RED}   Failed to revert (file may be new){NC}")
            print()
            
        elif status == 'UNTRACKED' and not self.quiet:
            print(f"{YELLOW}⚠️  UNTRACKED: {rel_path}{NC}")
            print("   Consider adding to a scope with scope-manager.py")
            print()
            
        elif status == 'SCOPE' and self.verbose:
            print(f"{CYAN}📁 SCOPE: {rel_path}{NC}")
            # Extract scope name and invariants
            for line in output.split('\n'):
                if line.startswith('Scope:') or '•' in line:
                    print(f"   {line.strip()}")
            print()
            
        elif status == 'BUNDLE' and self.verbose:
            print(f"{MAGENTA}📦 BUNDLE: {rel_path}{NC}")
            for line in output.split('\n'):
                if line.startswith('Bundle:') or '•' in line or '→' in line:
                    print(f"   {line.strip()}")
            print()


def run_with_watchdog(args):
    """Run watcher using watchdog library."""
    event_handler = ScopeGuardHandler(
        aggressive=args.aggressive,
        verbose=args.verbose,
        quiet=args.quiet
    )
    
    observer = Observer()
    
    # Add watches for each directory
    for dir_name in WATCH_DIRS:
        watch_path = WORKSPACE_ROOT / dir_name if dir_name else WORKSPACE_ROOT
        if watch_path.exists():
            # For root, don't recurse (we handle subdirs separately)
            recursive = bool(dir_name)
            observer.schedule(event_handler, str(watch_path), recursive=recursive)
    
    observer.start()
    
    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        observer.stop()
    
    observer.join()


def run_polling_fallback(args):
    """Simple polling fallback when watchdog is not available."""
    print(f"{YELLOW}Note: Using polling mode. Install watchdog for better performance:{NC}")
    print(f"  pip install watchdog")
    print()
    
    # Track file mtimes
    mtimes = {}
    
    def get_files():
        """Get all files to monitor."""
        files = []
        for dir_name in WATCH_DIRS:
            watch_path = WORKSPACE_ROOT / dir_name if dir_name else WORKSPACE_ROOT
            if not watch_path.exists():
                continue
            
            if dir_name:
                # Recursive for subdirs
                for f in watch_path.rglob('*'):
                    if f.is_file() and not should_ignore(f):
                        files.append(f)
            else:
                # Non-recursive for root
                for f in watch_path.iterdir():
                    if f.is_file() and not should_ignore(f):
                        files.append(f)
        return files
    
    # Initialize mtimes
    for f in get_files():
        try:
            mtimes[str(f)] = f.stat().st_mtime
        except Exception:
            pass
    
    handler = ScopeGuardHandler(
        aggressive=args.aggressive,
        verbose=args.verbose,
        quiet=args.quiet
    )
    
    try:
        while True:
            time.sleep(args.interval)
            
            for f in get_files():
                path = str(f)
                try:
                    mtime = f.stat().st_mtime
                except Exception:
                    continue
                
                if path not in mtimes:
                    mtimes[path] = mtime
                    handler._handle_event(path)
                elif mtimes[path] != mtime:
                    mtimes[path] = mtime
                    handler._handle_event(path)
                    
    except KeyboardInterrupt:
        pass


def main():
    parser = argparse.ArgumentParser(
        description='Scope Guard File Watcher',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__
    )
    
    parser.add_argument('--aggressive', action='store_true',
                        help='Auto-revert READ_ONLY violations')
    parser.add_argument('--verbose', action='store_true',
                        help='Show SCOPE and BUNDLE details')
    parser.add_argument('--quiet', action='store_true',
                        help='Only show BLOCKED files')
    parser.add_argument('--interval', type=float, default=1.0,
                        help='Poll interval in seconds (polling mode only)')
    
    args = parser.parse_args()
    
    # Check CLI tool exists
    if not CLI_TOOL.exists():
        print(f"{RED}ERROR: CLI tool not found: {CLI_TOOL}{NC}")
        sys.exit(1)
    
    # Banner
    print(f"{BLUE}═══════════════════════════════════════════════════════════════{NC}")
    print(f"{BLUE}  🔒 Scope Guard File Watcher (Python){NC}")
    print(f"{BLUE}═══════════════════════════════════════════════════════════════{NC}")
    print()
    print(f"Watching workspace for changes...")
    
    if args.aggressive:
        print(f"{RED}⚠️  AGGRESSIVE MODE: READ_ONLY violations will be auto-reverted!{NC}")
    if args.verbose:
        print(f"{CYAN}📖 VERBOSE MODE: Showing scope/bundle invariants{NC}")
    if args.quiet:
        print(f"{YELLOW}🔇 QUIET MODE: Only showing BLOCKED files{NC}")
    
    print()
    print("Press Ctrl+C to stop.")
    print()
    print(f"{MAGENTA}───────────────────────────────────────────────────────────────{NC}")
    
    # Run watcher
    if WATCHDOG_AVAILABLE:
        run_with_watchdog(args)
    else:
        run_polling_fallback(args)


if __name__ == '__main__':
    main()

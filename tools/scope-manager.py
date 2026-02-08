#!/usr/bin/env python3
"""
Scope Guard Manager - Add files to scopes or create new scopes.

Usage:
  # Add files to existing scope
  python scope-manager.py add-files <scope-id> <file1> [file2 ...]
  
  # Add files with ownership
  python scope-manager.py add-files <scope-id> <file1> --ownership CONSUMER --shared-with other-scope
  
  # Create new scope
  python scope-manager.py create <scope-id> <description> --module <module-path> [--files <file1> ...]
  
  # Set ownership for existing file
  python scope-manager.py set-ownership <scope-id> <file-path> <OWNER|CONSUMER|SHARED> [--shared-with scope1,scope2]
  
  # List all scopes
  python scope-manager.py list
  
  # Validate all scopes
  python scope-manager.py validate
  
  # Show scope info
  python scope-manager.py info <scope-id>

Examples:
  python scope-manager.py create telegram-transport "Telegram TDLib transport layer" \\
    --module infra/transport-telegram/src/main/java/com/fishit/player/infra/transport/telegram
  
  python scope-manager.py add-files xtream-transport-core \\
    infra/transport-xtream/src/main/java/.../NewFile.kt
  
  python scope-manager.py add-files xtream-pipeline-catalog ApiClient.kt \\
    --ownership CONSUMER --shared-with xtream-transport-core
"""

import argparse
import json
import os
import sys
from datetime import date
from pathlib import Path
from typing import Optional, Literal

# Constants
SCOPE_DIR = Path(__file__).parent.parent / ".scope"
SCHEMA_FILE = SCOPE_DIR / "scope-guard.schema.json"
DEFAULT_VERSION = "1.0.0"
VALID_OWNERSHIP = ["OWNER", "CONSUMER", "SHARED"]


def load_schema() -> dict:
    """Load the scope guard schema."""
    with open(SCHEMA_FILE) as f:
        return json.load(f)


def load_scope(scope_id: str) -> Optional[dict]:
    """Load an existing scope file."""
    scope_file = SCOPE_DIR / f"{scope_id}.scope.json"
    if not scope_file.exists():
        return None
    with open(scope_file) as f:
        return json.load(f)


def save_scope(scope_id: str, scope_data: dict) -> Path:
    """Save scope data to file."""
    scope_file = SCOPE_DIR / f"{scope_id}.scope.json"
    with open(scope_file, "w") as f:
        json.dump(scope_data, f, indent=2)
        f.write("\n")  # Trailing newline
    return scope_file


def list_scopes() -> list[str]:
    """List all scope IDs."""
    return [
        f.stem.replace(".scope", "")
        for f in SCOPE_DIR.glob("*.scope.json")
    ]


def validate_scope(scope_data: dict) -> list[str]:
    """Validate scope against required fields. Returns list of errors."""
    errors = []
    required = ["scopeId", "version"]
    
    for field in required:
        if field not in scope_data:
            errors.append(f"Missing required field: {field}")
    
    # Either modules or filePatterns must exist
    has_modules = "modules" in scope_data and scope_data["modules"]
    has_file_patterns = "filePatterns" in scope_data and scope_data["filePatterns"]
    
    if not has_modules and not has_file_patterns:
        errors.append("Either 'modules' or 'filePatterns' must be non-empty")
    
    return errors


def count_files_in_module(module_data: dict) -> int:
    """Count files in a module definition."""
    count = 0
    if "criticalFiles" in module_data:
        count += len(module_data["criticalFiles"])
    if "handlers" in module_data:
        count += len(module_data["handlers"])
    if "diModules" in module_data:
        count += len(module_data["diModules"])
    return count


def create_new_scope(
    scope_id: str,
    description: str,
    module_path: str,
    files: Optional[list[str]] = None,
    mandatory_reads: Optional[list[str]] = None,
    invariants: Optional[list[str]] = None,
    related_scopes: Optional[list[str]] = None
) -> dict:
    """Create a new scope with proper schema compliance."""
    
    # Build critical files list
    critical_files = []
    if files:
        for file_path in files:
            purpose = input(f"Purpose for {Path(file_path).name} (or Enter to skip): ").strip()
            if not purpose:
                purpose = f"Part of {scope_id} scope"
            critical_files.append({
                "path": file_path,
                "purpose": purpose
            })
    
    # Default mandatory reads based on scope type
    default_reads = [
        "AGENTS.md Section 4 (Layer Boundaries)",
        f".github/instructions/{scope_id.replace('-', '.')}.instructions.md"
    ]
    
    scope_data = {
        "$schema": "./scope-guard.schema.json",
        "scopeId": scope_id,
        "version": DEFAULT_VERSION,
        "description": description,
        "mandatoryReadBeforeEdit": mandatory_reads or default_reads,
        "modules": {
            module_path: {
                "fileCount": len(critical_files),
                "totalLOC": 0,  # Will be calculated later
                "criticalFiles": critical_files
            }
        },
        "relatedScopes": related_scopes or [],
        "globalInvariants": invariants or [
            f"Follow layer boundaries defined in AGENTS.md",
            f"Use naming conventions from GLOSSARY contract"
        ],
        "forbiddenPatterns": [],
        "lastVerified": date.today().isoformat(),
        "auditedBy": "scope-manager.py"
    }
    
    return scope_data


def add_files_to_scope(
    scope_id: str,
    files: list[str],
    as_handlers: bool = False,
    as_di_modules: bool = False,
    ownership: Optional[str] = None,
    shared_with: Optional[list[str]] = None
) -> dict:
    """Add files to an existing scope without losing data."""
    
    scope_data = load_scope(scope_id)
    if not scope_data:
        print(f"Error: Scope '{scope_id}' not found", file=sys.stderr)
        sys.exit(1)
    
    # Validate ownership
    if ownership and ownership not in VALID_OWNERSHIP:
        print(f"Error: Invalid ownership '{ownership}'. Must be one of: {VALID_OWNERSHIP}", file=sys.stderr)
        sys.exit(1)
    
    # Get the first module or let user choose
    modules = list(scope_data["modules"].keys())
    if len(modules) == 1:
        module_key = modules[0]
    else:
        print("Available modules:")
        for i, mod in enumerate(modules, 1):
            print(f"  {i}. {mod}")
        choice = input(f"Select module (1-{len(modules)}): ").strip()
        module_key = modules[int(choice) - 1]
    
    module_data = scope_data["modules"][module_key]
    
    # Track existing paths to avoid duplicates
    existing_paths = set()
    if "criticalFiles" in module_data:
        existing_paths.update(f["path"] for f in module_data["criticalFiles"])
    if "handlers" in module_data:
        existing_paths.update(module_data["handlers"])
    if "diModules" in module_data:
        existing_paths.update(module_data["diModules"])
    
    # Add files to appropriate section
    added = []
    skipped = []
    
    for file_path in files:
        if file_path in existing_paths:
            skipped.append(file_path)
            continue
        
        if as_handlers:
            if "handlers" not in module_data:
                module_data["handlers"] = []
            module_data["handlers"].append(file_path)
            added.append(file_path)
        elif as_di_modules:
            if "diModules" not in module_data:
                module_data["diModules"] = []
            module_data["diModules"].append(file_path)
            added.append(file_path)
        else:
            # Add as critical file
            if "criticalFiles" not in module_data:
                module_data["criticalFiles"] = []
            
            purpose = input(f"Purpose for {Path(file_path).name} (or Enter for default): ").strip()
            if not purpose:
                purpose = f"Part of {scope_id} scope"
            
            critical_file = {
                "path": file_path,
                "purpose": purpose
            }
            
            # Add ownership if specified
            if ownership:
                critical_file["ownership"] = ownership
            if shared_with:
                critical_file["sharedWith"] = shared_with
            
            module_data["criticalFiles"].append(critical_file)
            added.append(file_path)
    
    # Update file count
    module_data["fileCount"] = count_files_in_module(module_data)
    
    # Update timestamp
    scope_data["lastVerified"] = date.today().isoformat()
    
    print(f"Added {len(added)} files, skipped {len(skipped)} duplicates")
    if skipped:
        print(f"  Skipped: {', '.join(Path(p).name for p in skipped)}")
    
    return scope_data


def add_module_to_scope(scope_id: str, module_path: str, files: list[str]) -> dict:
    """Add a new module section to an existing scope."""
    
    scope_data = load_scope(scope_id)
    if not scope_data:
        print(f"Error: Scope '{scope_id}' not found", file=sys.stderr)
        sys.exit(1)
    
    if module_path in scope_data["modules"]:
        print(f"Module '{module_path}' already exists in scope", file=sys.stderr)
        sys.exit(1)
    
    critical_files = []
    for file_path in files:
        purpose = input(f"Purpose for {Path(file_path).name} (or Enter for default): ").strip()
        if not purpose:
            purpose = f"Part of {scope_id} scope"
        critical_files.append({
            "path": file_path,
            "purpose": purpose
        })
    
    scope_data["modules"][module_path] = {
        "fileCount": len(critical_files),
        "totalLOC": 0,
        "criticalFiles": critical_files
    }
    
    scope_data["lastVerified"] = date.today().isoformat()
    
    return scope_data


def set_file_ownership(
    scope_id: str,
    file_path: str,
    ownership: str,
    shared_with: Optional[list[str]] = None
) -> dict:
    """Set ownership for an existing file in a scope."""
    
    if ownership not in VALID_OWNERSHIP:
        print(f"Error: Invalid ownership '{ownership}'. Must be one of: {VALID_OWNERSHIP}", file=sys.stderr)
        sys.exit(1)
    
    scope_data = load_scope(scope_id)
    if not scope_data:
        print(f"Error: Scope '{scope_id}' not found", file=sys.stderr)
        sys.exit(1)
    
    # Find the file in any module
    file_found = False
    for module_path, module_data in scope_data["modules"].items():
        if "criticalFiles" not in module_data:
            continue
        
        for cf in module_data["criticalFiles"]:
            if cf["path"] == file_path or cf["path"].endswith(f"/{file_path}"):
                cf["ownership"] = ownership
                if shared_with:
                    cf["sharedWith"] = shared_with
                elif "sharedWith" in cf and ownership != "SHARED":
                    # Remove sharedWith if not SHARED or CONSUMER
                    del cf["sharedWith"]
                file_found = True
                print(f"✓ Set ownership={ownership} for {cf['path']}")
                if shared_with:
                    print(f"  sharedWith: {', '.join(shared_with)}")
                break
        
        if file_found:
            break
    
    if not file_found:
        print(f"Error: File '{file_path}' not found in scope '{scope_id}'", file=sys.stderr)
        print("  Use 'add-files' first to add the file to the scope", file=sys.stderr)
        sys.exit(1)
    
    scope_data["lastVerified"] = date.today().isoformat()
    return scope_data


def show_scope_info(scope_id: str) -> None:
    """Display detailed info about a scope."""
    
    scope_data = load_scope(scope_id)
    if not scope_data:
        print(f"Error: Scope '{scope_id}' not found", file=sys.stderr)
        sys.exit(1)
    
    print(f"\n{'='*60}")
    print(f"Scope: {scope_data['scopeId']}")
    print(f"Version: {scope_data.get('version', 'N/A')}")
    print(f"Description: {scope_data.get('description', 'N/A')}")
    print(f"{'='*60}")
    
    print(f"\nModules ({len(scope_data.get('modules', {}))})")
    for module_path, module_data in scope_data.get("modules", {}).items():
        file_count = module_data.get("fileCount", 0)
        loc = module_data.get("totalLOC", 0)
        print(f"  • {module_path}")
        print(f"    Files: {file_count}, LOC: {loc}")
        
        if "criticalFiles" in module_data:
            print(f"    Critical Files:")
            for cf in module_data["criticalFiles"][:5]:  # Show first 5
                print(f"      - {Path(cf['path']).name}: {cf.get('purpose', 'N/A')}")
            if len(module_data["criticalFiles"]) > 5:
                print(f"      ... and {len(module_data['criticalFiles']) - 5} more")
    
    if scope_data.get("relatedScopes"):
        print(f"\nRelated Scopes: {', '.join(scope_data['relatedScopes'])}")
    
    if scope_data.get("globalInvariants"):
        print(f"\nInvariants ({len(scope_data['globalInvariants'])})")
        for inv in scope_data["globalInvariants"][:3]:
            print(f"  • {inv}")
    
    print(f"\nLast Verified: {scope_data.get('lastVerified', 'N/A')}")
    print()


def batch_add_files(scope_id: str, file_list_path: str) -> dict:
    """Add files from a text file (one per line) to a scope."""
    
    with open(file_list_path) as f:
        files = [line.strip() for line in f if line.strip() and not line.startswith("#")]
    
    return add_files_to_scope(scope_id, files)


def interactive_create() -> None:
    """Interactive scope creation wizard."""
    
    print("\n=== Scope Guard - Create New Scope ===\n")
    
    scope_id = input("Scope ID (e.g., telegram-transport): ").strip()
    if not scope_id:
        print("Error: Scope ID required", file=sys.stderr)
        sys.exit(1)
    
    existing = load_scope(scope_id)
    if existing:
        print(f"Error: Scope '{scope_id}' already exists", file=sys.stderr)
        sys.exit(1)
    
    description = input("Description: ").strip()
    if not description:
        description = f"Scope for {scope_id}"
    
    module_path = input("Primary module path (e.g., infra/transport-telegram): ").strip()
    if not module_path:
        print("Error: Module path required", file=sys.stderr)
        sys.exit(1)
    
    print("\nFiles (one per line, empty line to finish):")
    files = []
    while True:
        file_path = input("  File: ").strip()
        if not file_path:
            break
        files.append(file_path)
    
    print("\nMandatory reads (one per line, empty line to finish):")
    mandatory_reads = []
    while True:
        read = input("  Read: ").strip()
        if not read:
            break
        mandatory_reads.append(read)
    
    print("\nRelated scopes (comma separated or empty):")
    related = input("  Related: ").strip()
    related_scopes = [s.strip() for s in related.split(",")] if related else []
    
    scope_data = create_new_scope(
        scope_id=scope_id,
        description=description,
        module_path=module_path,
        files=files if files else None,
        mandatory_reads=mandatory_reads if mandatory_reads else None,
        related_scopes=related_scopes if any(related_scopes) else None
    )
    
    # Validate
    errors = validate_scope(scope_data)
    if errors:
        print(f"\nValidation errors:", file=sys.stderr)
        for err in errors:
            print(f"  - {err}", file=sys.stderr)
        sys.exit(1)
    
    # Save
    output_path = save_scope(scope_id, scope_data)
    print(f"\n✓ Created scope: {output_path}")


def validate_all_scopes() -> None:
    """Validate all scope files."""
    
    scopes = list_scopes()
    all_valid = True
    
    print(f"\nValidating {len(scopes)} scopes...\n")
    
    for scope_id in scopes:
        scope_data = load_scope(scope_id)
        errors = validate_scope(scope_data)
        
        if errors:
            print(f"✗ {scope_id}:")
            for err in errors:
                print(f"    - {err}")
            all_valid = False
        else:
            print(f"✓ {scope_id}")
    
    print()
    if all_valid:
        print("All scopes valid!")
        sys.exit(0)
    else:
        print("Some scopes have errors")
        sys.exit(1)


def main():
    parser = argparse.ArgumentParser(
        description="Scope Guard Manager - Manage scope files",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__
    )
    
    subparsers = parser.add_subparsers(dest="command", help="Commands")
    
    # List command
    subparsers.add_parser("list", help="List all scopes")
    
    # Info command
    info_parser = subparsers.add_parser("info", help="Show scope info")
    info_parser.add_argument("scope_id", help="Scope ID")
    
    # Validate command
    subparsers.add_parser("validate", help="Validate all scopes")
    
    # Create command
    create_parser = subparsers.add_parser("create", help="Create new scope")
    create_parser.add_argument("scope_id", nargs="?", help="Scope ID")
    create_parser.add_argument("description", nargs="?", help="Description")
    create_parser.add_argument("--module", "-m", help="Module path")
    create_parser.add_argument("--files", "-f", nargs="*", help="Files to add")
    create_parser.add_argument("--interactive", "-i", action="store_true", help="Interactive mode")
    
    # Add files command
    add_parser = subparsers.add_parser("add-files", help="Add files to existing scope")
    add_parser.add_argument("scope_id", help="Scope ID")
    add_parser.add_argument("files", nargs="*", help="Files to add")
    add_parser.add_argument("--from-file", help="Read files from text file")
    add_parser.add_argument("--as-handlers", action="store_true", help="Add as handlers")
    add_parser.add_argument("--as-di-modules", action="store_true", help="Add as DI modules")
    add_parser.add_argument("--ownership", choices=VALID_OWNERSHIP, help="File ownership: OWNER (default), CONSUMER, or SHARED")
    add_parser.add_argument("--shared-with", help="Comma-separated list of scope IDs that share this file")
    
    # Set ownership command
    ownership_parser = subparsers.add_parser("set-ownership", help="Set ownership for existing file in scope")
    ownership_parser.add_argument("scope_id", help="Scope ID")
    ownership_parser.add_argument("file_path", help="File path to update")
    ownership_parser.add_argument("ownership", choices=VALID_OWNERSHIP, help="OWNER, CONSUMER, or SHARED")
    ownership_parser.add_argument("--shared-with", help="Comma-separated list of scope IDs that share this file")
    
    # Add module command
    add_mod_parser = subparsers.add_parser("add-module", help="Add module to existing scope")
    add_mod_parser.add_argument("scope_id", help="Scope ID")
    add_mod_parser.add_argument("module_path", help="Module path to add")
    add_mod_parser.add_argument("files", nargs="*", help="Files in the module")
    
    args = parser.parse_args()
    
    if args.command == "list":
        scopes = list_scopes()
        print(f"\nFound {len(scopes)} scopes:\n")
        for scope_id in sorted(scopes):
            scope_data = load_scope(scope_id)
            desc = scope_data.get("description", "")[:60] if scope_data else ""
            print(f"  • {scope_id}: {desc}")
        print()
    
    elif args.command == "info":
        show_scope_info(args.scope_id)
    
    elif args.command == "validate":
        validate_all_scopes()
    
    elif args.command == "create":
        if args.interactive or not args.scope_id:
            interactive_create()
        else:
            if not args.module:
                print("Error: --module required for non-interactive create", file=sys.stderr)
                sys.exit(1)
            
            scope_data = create_new_scope(
                scope_id=args.scope_id,
                description=args.description or f"Scope for {args.scope_id}",
                module_path=args.module,
                files=args.files
            )
            
            errors = validate_scope(scope_data)
            if errors:
                for err in errors:
                    print(f"Error: {err}", file=sys.stderr)
                sys.exit(1)
            
            output_path = save_scope(args.scope_id, scope_data)
            print(f"✓ Created scope: {output_path}")
    
    elif args.command == "add-files":
        # Parse shared_with from comma-separated string
        shared_with = None
        if args.shared_with:
            shared_with = [s.strip() for s in args.shared_with.split(",")]
        
        if args.from_file:
            scope_data = batch_add_files(args.scope_id, args.from_file)
        elif args.files:
            scope_data = add_files_to_scope(
                args.scope_id, 
                args.files,
                as_handlers=args.as_handlers,
                as_di_modules=args.as_di_modules,
                ownership=args.ownership,
                shared_with=shared_with
            )
        else:
            print("Error: Provide files or --from-file", file=sys.stderr)
            sys.exit(1)
        
        output_path = save_scope(args.scope_id, scope_data)
        print(f"✓ Updated scope: {output_path}")
    
    elif args.command == "set-ownership":
        shared_with = None
        if args.shared_with:
            shared_with = [s.strip() for s in args.shared_with.split(",")]
        
        scope_data = set_file_ownership(
            args.scope_id,
            args.file_path,
            args.ownership,
            shared_with
        )
        output_path = save_scope(args.scope_id, scope_data)
        print(f"✓ Updated file ownership in: {output_path}")
    
    elif args.command == "add-module":
        scope_data = add_module_to_scope(args.scope_id, args.module_path, args.files or [])
        output_path = save_scope(args.scope_id, scope_data)
        print(f"✓ Updated scope: {output_path}")
    
    else:
        parser.print_help()


if __name__ == "__main__":
    main()

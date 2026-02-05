#!/usr/bin/env python3
"""
Update CHANGELOG.md with a new entry.

This script is used by the copilot-doc-maintenance.yml GitHub Actions workflow
to automatically add changelog entries based on conventional commit messages.

Usage:
    python3 update-changelog.py <section> <commit_date> <entry>

Arguments:
    section     - Changelog section name (e.g., "Added", "Fixed", "Changed")
    commit_date - Date string in YYYY-MM-DD format
    entry       - The changelog entry text (single line)

Behavior:
    1. If a section for today's date exists, append to it
    2. If an [Unreleased] section exists with that section type, add there
    3. If section doesn't exist under Unreleased, create it
    4. If no Unreleased section exists, create one at the top

Exit Codes:
    0 - Success
    1 - Invalid arguments or CHANGELOG.md not found
"""
import re
import sys


def main():
    if len(sys.argv) != 4:
        print("Usage: update-changelog.py <section> <commit_date> <entry>")
        sys.exit(1)
    
    section = sys.argv[1]
    commit_date = sys.argv[2]
    entry = sys.argv[3]
    
    try:
        with open('CHANGELOG.md', 'r', encoding='utf-8') as f:
            content = f.read()
    except FileNotFoundError:
        print("CHANGELOG.md not found")
        sys.exit(1)
    
    # Check if there's already an entry for today with this section
    section_header = f"### {section} ({commit_date})"
    if section_header in content:
        # Add to existing section
        pattern = rf'(### {re.escape(section)} \({re.escape(commit_date)}\)[^\n]*\n)'
        replacement = rf'\1{entry}\n'
        content = re.sub(pattern, replacement, content, count=1)
    elif "## [Unreleased]" in content:
        # Extract Unreleased block to constrain matching
        unreleased_match = re.search(r'## \[Unreleased\].*?(?=\n## |\Z)', content, re.DOTALL)
        if unreleased_match:
            unreleased_block = unreleased_match.group(0)
            
            # Check if section exists within Unreleased block only
            if f"### {section}" in unreleased_block:
                # Add to existing section within Unreleased block
                pattern = rf'(## \[Unreleased\].*?)(### {re.escape(section)}[^\n]*\n)'
                replacement = rf'\1\2{entry}\n'
                content = re.sub(pattern, replacement, content, count=1, flags=re.DOTALL)
            else:
                # Add new section after Unreleased header
                pattern = r'(## \[Unreleased\][^\n]*\n)'
                replacement = rf'\1\n### {section} ({commit_date})\n{entry}\n'
                content = re.sub(pattern, replacement, content, count=1)
        else:
            # Unreleased header exists but no block found, add after header
            pattern = r'(## \[Unreleased\][^\n]*\n)'
            replacement = rf'\1\n### {section} ({commit_date})\n{entry}\n'
            content = re.sub(pattern, replacement, content, count=1)
    else:
        # No Unreleased section, add at top
        content = f"## [Unreleased]\n\n### {section} ({commit_date})\n{entry}\n\n{content}"
    
    with open('CHANGELOG.md', 'w', encoding='utf-8') as f:
        f.write(content)
    
    print(f"Added changelog entry: {entry}")


if __name__ == "__main__":
    main()

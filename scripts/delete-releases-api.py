#!/usr/bin/env python3
"""
Delete all GitHub releases using direct API calls.
This script uses the GitHub REST API to delete releases.
Requires GITHUB_TOKEN environment variable to be set.
"""

import os
import sys
import json
from urllib.request import Request, urlopen
from urllib.error import HTTPError, URLError

def delete_all_releases():
    """Delete all releases from the repository."""
    repo = "karlokarate/FishIT-Player"
    token = os.environ.get('GITHUB_TOKEN')
    
    if not token:
        print("❌ Error: GITHUB_TOKEN environment variable not set")
        print("Please set GITHUB_TOKEN with a token that has repo permissions")
        sys.exit(1)
    
    headers = {
        'Authorization': f'Bearer {token}',
        'Accept': 'application/vnd.github.v3+json',
        'User-Agent': 'FishIT-Player-Release-Deleter'
    }
    
    # Get all releases
    print(f"📋 Fetching all releases from {repo}...")
    url = f"https://api.github.com/repos/{repo}/releases"
    
    try:
        req = Request(url, headers=headers)
        with urlopen(req) as response:
            releases = json.loads(response.read().decode('utf-8'))
    except HTTPError as e:
        print(f"❌ Failed to fetch releases: {e.code}")
        print(e.read().decode('utf-8'))
        sys.exit(1)
    except URLError as e:
        print(f"❌ Network error: {e.reason}")
        sys.exit(1)
    
    if not releases:
        print("✅ No releases found in the repository")
        return
    
    print(f"Found {len(releases)} release(s) to delete\n")
    
    # List all releases
    print("Releases to be deleted:")
    for release in releases:
        print(f"  - {release['tag_name']}")
    print()
    
    # Delete each release
    print("🗑️  Deleting releases...\n")
    deleted = 0
    failed = 0
    
    for release in releases:
        tag = release['tag_name']
        release_id = release['id']
        print(f"Deleting release: {tag}... ", end='', flush=True)
        
        delete_url = f"https://api.github.com/repos/{repo}/releases/{release_id}"
        
        try:
            req = Request(delete_url, headers=headers)
            req.get_method = lambda: 'DELETE'
            with urlopen(req) as response:
                if response.status == 204:
                    print("✅ Deleted")
                    deleted += 1
                else:
                    print(f"❌ Failed (status: {response.status})")
                    failed += 1
        except HTTPError as e:
            print(f"❌ Failed (status: {e.code})")
            failed += 1
        except URLError as e:
            print(f"❌ Network error: {e.reason}")
            failed += 1
    
    print()
    print("━" * 40)
    print("✅ Deletion complete!")
    print(f"   Deleted: {deleted}")
    print(f"   Failed: {failed}")
    print("━" * 40)

if __name__ == "__main__":
    delete_all_releases()

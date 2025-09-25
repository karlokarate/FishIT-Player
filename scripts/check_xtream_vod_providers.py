#!/usr/bin/env python3
import json
import re
import sys
import urllib.request
import urllib.error
from collections import Counter

BASE = 'http://konigtv.com:8080'
USER = 'Christoph10'
PASS = 'JQ2rKsQ744'

ENDPOINTS = [
    'get_vod_streams',
    'get_movie_streams',
    'get_movies',
]

def fetch_json(action):
    url = f"{BASE}/player_api.php?username={USER}&password={PASS}&action={action}&category_id=0"
    req = urllib.request.Request(url, headers={'User-Agent': 'curl/7.81'})
    with urllib.request.urlopen(req, timeout=30) as resp:
        data = resp.read()
        return json.loads(data.decode('utf-8', 'ignore'))

def detect_providers(text):
    t = text.lower()
    # Base 6 providers with abbreviation/prefix heuristics
    base = {
        'NETFLIX': bool(re.search(r'\bnetflix\b|^\s*[\[(]?\s*nf(?:x)?\b', t)),
        'PARAMOUNT+': bool(re.search(r'\bparamount\b|paramount\+|paramount plus|^\s*[\[(]?\s*pmnt\b', t)),
        'DISNEY': bool(re.search(r'disney\+|disney plus|\bdisney\b', t)) and ('disney channel' not in t),
        'APPLE+': bool(re.search(r'apple tv\+|apple tv plus|\batv\+\b|\bapple tv\b|^\s*[\[(]?\s*atv\+\b', t)),
        'PRIME': bool(re.search(r'\bprime\b|\bamazon\b|\bamzn\b', t)),
        'SKY/WARNER': bool(re.search(r'\bsky\b|sky cinema|\bwarner\b|\bhbo\b|\bmax\b', t)),
    }
    more = {
        'PLUTO TV': 'pluto tv' in t,
        'WOW': bool(re.search(r'\bwow\b', t)),
        'DISCOVERY+': ('discovery+' in t) or ('discovery plus' in t),
        'MUBI': 'mubi' in t,
        'JOYNN?': 'joyn' in t,
        'RTL+': ('rtl+' in t) or ('rtl plus' in t),
        'MGM+': bool(re.search(r'\bmgm\+\b', t)),
        'STARZ': 'starz' in t,
        'PEACOCK': 'peacock' in t,
    }
    return base, {k:v for k,v in more.items() if v}

def main():
    # Try endpoints in order until one returns a list
    items = None
    used_action = None
    for action in ENDPOINTS:
        try:
            data = fetch_json(action)
        except Exception as e:
            continue
        if isinstance(data, list) and data and isinstance(data[0], dict):
            items = data
            used_action = action
            break
    if items is None:
        print('ERROR: No VOD list endpoint responded with JSON list.', file=sys.stderr)
        sys.exit(1)

    total = len(items)
    six = Counter()
    extra = Counter()
    unassigned = 0

    for it in items:
        name = str(it.get('name') or '')
        # Consider also stream_icon hints
        icon = str(it.get('stream_icon') or '')
        combined = f"{name} {icon}"
        base, more = detect_providers(combined)
        matched = False
        for k, v in base.items():
            if v:
                six[k] += 1
                matched = True
        for k in more.keys():
            extra[k] += 1
            matched = True
        if not matched:
            unassigned += 1

    print(f"Endpoint: {used_action}")
    print(f"VOD total: {total}")
    print("Base providers:")
    for k in ['NETFLIX','PARAMOUNT+','DISNEY','APPLE+','PRIME','SKY/WARNER']:
        print(f"  - {k}: {six.get(k,0)}")
    print("Additional providers detected:")
    for k, v in sorted(extra.items(), key=lambda kv: (-kv[1], kv[0])):
        print(f"  - {k}: {v}")
    print(f"Unassigned: {unassigned}")

if __name__ == '__main__':
    main()


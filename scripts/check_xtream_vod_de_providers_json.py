#!/usr/bin/env python3
import json
import re
import sys
import urllib.request
import urllib.error

BASE = 'http://konigtv.com:8080'
USER = 'Christoph10'
PASS = 'JQ2rKsQ744'

def fetch_json(url: str):
    req = urllib.request.Request(url, headers={
        'User-Agent': 'IBOPlayer/1.4 (Android)',
        'Accept': '*/*',
        'Accept-Encoding': 'gzip',
    })
    with urllib.request.urlopen(req, timeout=45) as resp:
        data = resp.read()
        enc = resp.headers.get('Content-Encoding', '')
        if enc.lower() == 'gzip':
            import gzip, io
            data = gzip.decompress(data)
        return json.loads(data.decode('utf-8', 'ignore'))

def detect(text: str):
    t = text.lower()
    base = {
        # include common prefixes/abbr at start or in brackets
        'NETFLIX': bool(re.search(r'\bnetflix\b|^[\s\[\(\-]*nf(?:x|lx)?\b|\bnfx\b|\bnflx\b', t)),
        'PARAMOUNT+': bool(re.search(r'\bparamount\b|paramount\+|paramount plus|^[\s\[\(\-]*p\+\b|^[\s\[\(\-]*pmnt\b|\bpmnt\b|\bp\+\b', t)),
        'DISNEY': (('disney channel' not in t) and bool(re.search(r'disney\+|disney plus|\bdisney\b|^[\s\[\(\-]*d\+\b|\bdsnp\b', t))),
        'APPLE+': bool(re.search(r'apple tv\+|apple tv plus|\batv\+\b|\bapple tv\b|^[\s\[\(\-]*atv\+\b', t)),
        'PRIME': bool(re.search(r'\bprime\b|\bamazon\b|\bamzn\b|\bapv\b', t)),
        'SKY/WARNER': bool(re.search(r'\bsky\b|sky cinema|\bwarner\b|\bwbd\b|\bhbo\b|\bmax\b', t)),
    }
    extra = {
        'WOW': bool(re.search(r'\bwow\b', t)),
        'PLUTO TV': 'pluto tv' in t,
        'DISCOVERY+': ('discovery+' in t) or ('discovery plus' in t),
        'MUBI': 'mubi' in t,
        'JOYNN?': 'joyn' in t,
        'RTL+': ('rtl+' in t) or ('rtl plus' in t),
        'MGM+': bool(re.search(r'\bmgm\+\b', t)),
        'STARZ': 'starz' in t,
        'PEACOCK': 'peacock' in t,
    }
    return base, {k:v for k,v in extra.items() if v}

def main():
    vod = fetch_json(f"{BASE}/player_api.php?username={USER}&password={PASS}&action=get_vod_streams&category_id=0")
    cats = fetch_json(f"{BASE}/player_api.php?username={USER}&password={PASS}&action=get_vod_categories")
    de_ids = {c.get('category_id') for c in cats if str(c.get('category_name') or '').upper().startswith('DE')}
    de_items = [it for it in vod if str(it.get('category_id')) in de_ids]
    total = len(de_items)
    base_counts = {k:0 for k in ['NETFLIX','PARAMOUNT+','DISNEY','APPLE+','PRIME','SKY/WARNER']}
    extra_counts = {}
    assigned_any = 0
    for it in de_items:
        s = f"{it.get('name') or ''} {it.get('stream_icon') or ''}"
        b, ex = detect(s)
        hit = False
        for k, v in b.items():
            if v:
                base_counts[k] += 1
                hit = True
        for k in ex.keys():
            extra_counts[k] = extra_counts.get(k, 0) + 1
            hit = True
        if hit:
            assigned_any += 1
    print(f"DE-VOD total: {total}")
    print("Bekannte Provider (6er-Set):")
    for k in ['NETFLIX','PARAMOUNT+','DISNEY','APPLE+','PRIME','SKY/WARNER']:
        print(f"  - {k}: {base_counts.get(k,0)}")
    print("Weitere Provider (Treffer):")
    for k,v in sorted(extra_counts.items(), key=lambda kv: (-kv[1], kv[0])):
        print(f"  - {k}: {v}")
    print(f"Zugeordnet (mind. 1 Treffer): {assigned_any}")
    print(f"Ohne Bezug: {total - assigned_any}")

if __name__ == '__main__':
    main()


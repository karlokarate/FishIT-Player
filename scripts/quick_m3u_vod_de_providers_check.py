#!/usr/bin/env python3
import re
import sys
from collections import Counter, defaultdict

extinf_re = re.compile(r'^#EXTINF:-1\s+(?P<attrs>[^,]*),(?P<title>.*)$')
attr_re = re.compile(r'(\w[\w-]*)="([^"]*)"')

def parse_m3u(path):
    with open(path, 'r', encoding='utf-8', errors='ignore') as f:
        pending = None
        for line in f:
            line = line.rstrip('\n')
            if not line:
                continue
            if line.startswith('#EXTINF'):
                m = extinf_re.match(line)
                if not m:
                    pending = None
                    continue
                attrs = dict(attr_re.findall(m.group('attrs') or ''))
                title = m.group('title').strip()
                pending = {
                    'tvg_name': attrs.get('tvg-name', ''),
                    'group_title': attrs.get('group-title', ''),
                    'tvg_logo': attrs.get('tvg-logo', ''),
                    'title': title,
                }
            elif pending is not None and not line.startswith('#'):
                pending['url'] = line.strip()
                yield pending
                pending = None

def classify_kind(group_title, tvg_name, url):
    s = ' '.join([group_title or '', tvg_name or '', url or '']).lower()
    if re.search(r'\b(series|serien|serie)\b', s):
        return 'series'
    if re.search(r'\b(vod|movie|kino|film|cinema|movies)\b', s):
        return 'vod'
    return 'live'

def is_de_category(group_title):
    gt = (group_title or '').strip()
    return gt.upper().startswith('DE')

def detect_providers(text_lower):
    # Base six providers
    prov = {
        'NETFLIX': bool(re.search(r'\bnetflix\b', text_lower)),
        'PARAMOUNT+': bool(re.search(r'\bparamount\+?|\bparamount\b', text_lower)),
        'DISNEY': bool(re.search(r'disney\+|disney plus|\bdisney\b', text_lower)) and not ('disney channel' in text_lower),
        'APPLE+': bool(re.search(r'apple tv\+|apple tv plus|apple tv|\bapple\b', text_lower)),
        'PRIME': bool(re.search(r'\bprime\b|\bamazon\b', text_lower)),
        'SKY/WARNER': bool(re.search(r'\bsky\b|sky cinema|\bwarner\b|\bhbo\b|\bmax\b', text_lower)),
    }
    # Additional providers (common in DE)
    more = {
        'PLUTO TV': 'pluto tv' in text_lower,
        'WOW': bool(re.search(r'\bwow\b', text_lower)),
        'DISCOVERY+': 'discovery+' in text_lower or 'discovery plus' in text_lower,
        'MUBI': 'mubi' in text_lower,
        'JOYNN?': 'joyn' in text_lower,
        'RTL+': 'rtl+' in text_lower or 'rtl plus' in text_lower,
        'ARD MEDIATHEK': bool(re.search(r'\bard\b mediathek|\bard mediathek\b', text_lower)),
        'ZDF MEDIATHEK': 'zdf mediathek' in text_lower,
    }
    return prov, {k:v for k,v in more.items() if v}

def main():
    if len(sys.argv) < 2:
        print('Usage: quick_m3u_vod_de_providers_check.py <path-to-m3u>', file=sys.stderr)
        sys.exit(2)
    path = sys.argv[1]

    total_de_vod = 0
    six_counts = Counter()
    additional_counts = Counter()
    unassigned = 0

    for it in parse_m3u(path):
        if classify_kind(it.get('group_title'), it.get('tvg_name'), it.get('url')) != 'vod':
            continue
        if not is_de_category(it.get('group_title')):
            continue
        total_de_vod += 1
        text = ' '.join([it.get('group_title') or '', it.get('tvg_name') or '', it.get('tvg_logo') or '', it.get('title') or '', it.get('url') or '']).lower()
        six, more = detect_providers(text)
        matched_any = False
        for k, v in six.items():
            if v:
                six_counts[k] += 1
                matched_any = True
        for k in more.keys():
            additional_counts[k] += 1
            matched_any = True
        if not matched_any:
            unassigned += 1

    # Output
    print(f"DE-VOD gesamt: {total_de_vod}")
    print("Bekannte Provider (6er-Set):")
    for name in ['NETFLIX','PARAMOUNT+','DISNEY','APPLE+','PRIME','SKY/WARNER']:
        print(f"  - {name}: {six_counts.get(name,0)}")
    print("Weitere gefundene Provider (DE):")
    for name, cnt in sorted(additional_counts.items(), key=lambda kv: (-kv[1], kv[0])):
        print(f"  - {name}: {cnt}")
    print(f"Ohne Provider-Bezug (keiner der o.g. Treffer): {unassigned}")

if __name__ == '__main__':
    main()

